package org.thoughtcrime.securesms.preferences;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.gotenna.sdk.GoTenna;
import com.gotenna.sdk.bluetooth.GTConnectionManager;
import com.gotenna.sdk.commands.GTCommandCenter;
import com.gotenna.sdk.commands.GTError;
import com.gotenna.sdk.commands.Place;
import com.gotenna.sdk.interfaces.GTErrorListener;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.MessagingDatabase;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mesh.managers.GTMeshManager;
import org.thoughtcrime.securesms.mesh.models.Message;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Arrays;

import static com.gotenna.sdk.commands.Place.NORTH_AMERICA;

public class MeshPreferenceFragment extends ListSummaryPreferenceFragment implements Preference.OnPreferenceClickListener, SharedPreferences.OnSharedPreferenceChangeListener,
        GTMeshManager.IncomingMessageListener, GTConnectionManager.GTConnectionListener {
    private static final String KITKAT_DEFAULT_PREF = "pref_set_default";
    private static final String TAG = MeshPreferenceFragment.class.getSimpleName();
    private static final int SCAN_TIMEOUT = 25000; // 25 seconds

    // private Handler handler;
    private Handler handler;

    @Override
    public void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        Preference defaultPreference = findPreference(KITKAT_DEFAULT_PREF);
        defaultPreference
                .setOnPreferenceClickListener(this);

        initializeListSummary((ListPreference) findPreference(TextSecurePreferences.MESH_REGION_PREF));

        initializePlatformSpecificOptions();

        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(android.os.Message msg) {
                if (msg.what == 0) {
                    // no connection, scanning timed out
                }
            }
        };
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_mesh);
    }

    @Override
    public void onStart() {
        super.onStart();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__mesh);

        initializeDefaultPreference();
    }

    @Override
    public void onStop() {
        super.onStop();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    private void initializePlatformSpecificOptions() {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        Preference defaultPreference = findPreference(KITKAT_DEFAULT_PREF);
        Preference meshAddressPreference = findPreference(TextSecurePreferences.MESH_ADDRESS_PREF);
        Preference meshRegionPreference = findPreference(TextSecurePreferences.MESH_REGION_PREF);

        if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
            if (meshAddressPreference != null)
                preferenceScreen.removePreference(meshAddressPreference);
        } else if (defaultPreference != null) {
            preferenceScreen.removePreference(defaultPreference);
        }
    }

    private void initializeDefaultPreference() {
        if (VERSION.SDK_INT < VERSION_CODES.KITKAT) return;

        Preference defaultPreference = findPreference(KITKAT_DEFAULT_PREF);
        defaultPreference.setEnabled(true);

        if (GTMeshManager.getInstance().isPaired()) {
            defaultPreference.setTitle(getString(R.string.ApplicationPreferencesActivity_mesh_enabled));
            defaultPreference.setSummary(getString(R.string.ApplicationPreferencesActivity_touch_to_unpair_your_mesh_device));
        } else {
            defaultPreference.setTitle(getString(R.string.ApplicationPreferencesActivity_mesh_disabled));
            defaultPreference.setSummary(getString(R.string.ApplicationPreferencesActivity_touch_to_pair_your_mesh_device));
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        // connect to bluetooth mesh device
        Permissions.with(this)
                .request(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH)
                .ifNecessary()
                .withPermanentDenialDialog(this.getString(R.string.preferences__signal_needs_bluetooth_permissions_to_connect_to_mesh))
                .onAnyResult(() -> {
                    if (GoTenna.tokenIsVerified()) {
                        final String localNumber = TextSecurePreferences.getLocalNumber(this.getContext());
                        final String profileName = TextSecurePreferences.getProfileName(this.getContext());
                        long theGID = GTMeshManager.getGidFromPhoneNumber(localNumber);

                        // set new random GID every time we recreate the main activity
                        GTCommandCenter.getInstance().setGoTennaGID(theGID, profileName, new GTErrorListener() {
                            @Override
                            public void onError(GTError error) {
                                android.util.Log.d(TAG, error.toString() + "," + error.getCode());
                            }
                        });

                        Preference defaultPreference = findPreference(KITKAT_DEFAULT_PREF);
                        defaultPreference.setEnabled(false);

                        GTMeshManager gtMeshManager = GTMeshManager.getInstance();

                        // if NOT already paired, try to connect to a goTenna
                        if (!gtMeshManager.getInstance().isPaired()) {
                            // connect to goTenna
                            gtMeshManager.connect(this);
                            handler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT);

                            defaultPreference.setTitle(getString(R.string.ApplicationPreferencesActivity_mesh_scanning));
                            defaultPreference.setSummary(getString(R.string.ApplicationPreferencesActivity_turn_on_your_mesh_device));
                        } else {
                            // connect to goTenna
                            gtMeshManager.disconnect(this);

                            defaultPreference.setTitle(getString(R.string.ApplicationPreferencesActivity_mesh_disconnecting));
                            defaultPreference.setSummary(getString(R.string.ApplicationPreferencesActivity_turn_off_your_mesh_device));
                        }
                    }
                })
                .execute();
        return true;
    }

    @Override
    public void onIncomingMessage(Message incomingMessage) {
        Optional<MessagingDatabase.InsertResult> insertResult = GTMeshManager.getInstance().storeMessage(incomingMessage);
        if (insertResult.isPresent()) {
            MessageNotifier.updateNotification(this.getContext(), insertResult.get().getThreadId());
        } else {
            Log.w(TAG, "*** Failed to insert mesh message!");
        }
    }

    public static CharSequence getSummary(Context context) {
        final String onCaps = context.getString(R.string.ApplicationPreferencesActivity_On);
        final String offCaps = context.getString(R.string.ApplicationPreferencesActivity_Off);
        final int meshSummaryResId = R.string.ApplicationPreferencesActivity_mesh_summary;

        String[] regionValues = context.getResources().getStringArray(R.array.region_values);
        int regionIndex = Arrays.asList(regionValues).indexOf(TextSecurePreferences.getMeshRegion(context));
        if (regionIndex == -1)
            regionIndex = 0;

        boolean paired = GTMeshManager.getInstance().isPaired();
        String address = GTConnectionManager.getInstance().getConnectedGotennaAddress();

        if (paired) {
            String[] regionEntries = context.getResources().getStringArray(R.array.region_entries);
            return context.getString(meshSummaryResId, address, regionEntries[regionIndex]);
        } else {
            return offCaps;
        }
    }

    protected void setGeoloc() {
        // set the geoloc region to current preference
        String meshRegion = TextSecurePreferences.getMeshRegion(getContext());
        GTMeshManager gtMeshManager = GTMeshManager.getInstance();
        Place place = Place.valueOf((String)meshRegion);
        android.util.Log.d(TAG, "Set Geoloc Region:" + place.getName());
        gtMeshManager.setGeoloc(place);
    }

    @Override
    public void onConnectionStateUpdated(GTConnectionManager.GTConnectionState gtConnectionState) {
        switch (gtConnectionState) {
            case CONNECTED: {
                android.util.Log.d(TAG, "Connected to device.");
                // set the geoloc region to current preference
                setGeoloc();
            }
            break;
            case DISCONNECTED: {
                android.util.Log.d(TAG, "Disconnected from device.");
            }
            break;
            case SCANNING: {
                android.util.Log.d(TAG, "Scanning for device.");
            }
            break;
        }
        if (gtConnectionState != GTConnectionManager.GTConnectionState.SCANNING) {
            GTConnectionManager.getInstance().removeGtConnectionListener(this);
            handler.removeCallbacks(scanTimeoutRunnable);
            initializeDefaultPreference();
        }
    }

    private final Runnable scanTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            handler.removeCallbacks(scanTimeoutRunnable);
            handler.sendEmptyMessage(0);
        }
    };

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(TextSecurePreferences.MESH_REGION_PREF)) {
            // set the geoloc region to current preference
            setGeoloc();

            ListPreference listPref = (ListPreference) findPreference(TextSecurePreferences.MESH_REGION_PREF);
            String[] regionValues = getResources().getStringArray(R.array.region_values);
            int regionIndex = Arrays.asList(regionValues).indexOf(TextSecurePreferences.getMeshRegion(getContext()));
            if (regionIndex < 0 || regionIndex >= listPref.getEntries().length)
                regionIndex = 0;
            listPref.setSummary(listPref.getEntries()[regionIndex]);
        }
    }

}