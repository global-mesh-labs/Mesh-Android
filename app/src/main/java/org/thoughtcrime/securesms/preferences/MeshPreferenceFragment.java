package org.thoughtcrime.securesms.preferences;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.gotenna.sdk.GoTenna;
import com.gotenna.sdk.connection.GTConnectionManager;
import com.gotenna.sdk.connection.GTConnectionState;
import com.gotenna.sdk.connection.GTConnectionError;
import com.gotenna.sdk.data.GTCommand.GTCommandResponseListener;
import com.gotenna.sdk.data.GTCommandCenter;
import com.gotenna.sdk.data.GTError;
import com.gotenna.sdk.data.GTErrorListener;
import com.gotenna.sdk.data.GTResponse;
import com.gotenna.sdk.data.Place;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mesh.managers.GTMeshManager;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Arrays;

public class MeshPreferenceFragment extends ListSummaryPreferenceFragment implements Preference.OnPreferenceClickListener, SharedPreferences.OnSharedPreferenceChangeListener,
        GTConnectionManager.GTConnectionListener {
    private static final String KITKAT_DEFAULT_PREF = "pref_set_default";
    private static final String TAG = MeshPreferenceFragment.class.getSimpleName();
    private static final int SCAN_TIMEOUT = 25000; // 25 seconds
    private static final int REQUEST_ENABLE_BT=1;

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
                Log.w(TAG, "no connection, scanning timed out (" + msg.what + ").");
                if (msg.what == 0) {
                    initializeDefaultPreference();
                }
                else if (msg.what == 1) {
                    // re-click on the button to connect to the mesh device after bluetooth enabled
                    Preference defaultPreference = findPreference(KITKAT_DEFAULT_PREF);
                    onPreferenceClick(defaultPreference);
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
        GTConnectionManager.getInstance().removeGtConnectionListener(this);
        handler.removeCallbacks(scanTimeoutRunnable);
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

    private void internalConnect() {
        // connect to goTenna
        GTMeshManager gtMeshManager = GTMeshManager.getInstance();
        gtMeshManager.connect(this);

        // start timeout handler
        handler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT);

        // disable button while scanning
        Preference defaultPreference = findPreference(KITKAT_DEFAULT_PREF);
        defaultPreference.setEnabled(false);
        defaultPreference.setTitle(getString(R.string.ApplicationPreferencesActivity_mesh_scanning));
        defaultPreference.setSummary(getString(R.string.ApplicationPreferencesActivity_turn_on_your_mesh_device));
    }

    private void internalDisconnect() {
        GTMeshManager gtMeshManager = GTMeshManager.getInstance();
        gtMeshManager.disconnect(this);

        // start timeout handler
        handler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT);

        // disable button while disconnecting
        Preference defaultPreference = findPreference(KITKAT_DEFAULT_PREF);
        defaultPreference.setEnabled(false);
        defaultPreference.setTitle(getString(R.string.ApplicationPreferencesActivity_mesh_disconnecting));
        defaultPreference.setSummary(getString(R.string.ApplicationPreferencesActivity_turn_off_your_mesh_device));
    }

        @Override
    public boolean onPreferenceClick(Preference preference) {

        // prompt first to turn on bluetooth adapter is off
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null)
        {
            return true;
        }
        if(!bluetoothAdapter.isEnabled())
        {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return true;
        }

        // connect or disconnect bluetooth mesh device
        Permissions.with(this)
                .request(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH)
                .ifNecessary()
                .withPermanentDenialDialog(this.getString(R.string.preferences__signal_needs_bluetooth_permissions_to_connect_to_mesh))
                .onAnyResult(() -> {
                    if (GoTenna.tokenIsVerified()) {
                        final String localNumber = TextSecurePreferences.getLocalNumber(this.getContext());
                        final String profileName = "foo"; //TextSecurePreferences.getLocalUsername(this.getContext());
                        long theGID = GTMeshManager.getGidFromPhoneNumber(localNumber);

                        // if NOT already paired, try to connect to a goTenna
                        GTMeshManager gtMeshManager = GTMeshManager.getInstance();
                        if (!gtMeshManager.getInstance().isPaired()) {
                           // connect to goTenna
                            internalConnect();
                        } else {
                            // disconnect from goTenna
                            internalDisconnect();
                        }

                        // set new random GID every time we recreate the main activity
                        GTCommandCenter.getInstance().setGoTennaGID(theGID, profileName, new GTCommandResponseListener()
                        {
                            @Override
                            public void onResponse(GTResponse response)
                            {
                                /*
                                if (view == null)
                                {
                                    return;
                                }

                                if (response.getResponseCode() == GTResponse.GTCommandResponseCode.POSITIVE)
                                {
                                    view.showSetGidSuccessMessage();
                                }
                                else
                                {
                                    view.showSetGidFailureMessage();
                                }
                                */
                            }
                        }, new GTErrorListener()
                        {
                            @Override
                            public void onError(GTError error)
                            {
                                    /*
                                    if (view != null)
                                    {
                                        view.showSetGidFailureMessage();
                                    }
                                    */
                            }
                        });
                    }
                })
                .execute();
        return true;
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

        if (paired) {
            String address = GTConnectionManager.getInstance().getConnectedGotennaAddress();
            String[] regionEntries = context.getResources().getStringArray(R.array.region_entries);
            return context.getString(meshSummaryResId, address, regionEntries[regionIndex]);
        } else {
            return offCaps;
        }
    }

    @Override
    public void onConnectionStateUpdated(@NonNull GTConnectionState gtConnectionState) {
        if (gtConnectionState != GTConnectionState.SCANNING) {
            handler.removeCallbacks(scanTimeoutRunnable);
            initializeDefaultPreference();
        }
    }

    @Override
    public void onConnectionError(@NonNull GTConnectionState connectionState, @NonNull GTConnectionError error)
    {
        handler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT);
        //view.stopTimeoutCountdown();
        //view.dismissScanningProgressDialog();

        switch (error.getErrorState())
        {
            case X_UPGRADE_CHECK_FAILED:
                /*
                    This error gets passed when we failed to check if the device is goTenna X. This
                    could happen due to connectivity issues with the device or error checking if the
                    device has been remotely upgraded.
                 */
                //view.showXCheckError();
                break;
            case NOT_X_DEVICE_ERROR:
                /*
                    This device is confirmed not to be a goTenna X device. Using error.getDetailString()
                    you can pull the serial number of the connected device.
                 */
                //view.showNotXDeviceWarning(error.getDetailString());
                break;
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
            GTMeshManager gtMeshManager = GTMeshManager.getInstance();
            gtMeshManager.setGeoloc();

            ListPreference listPref = (ListPreference) findPreference(TextSecurePreferences.MESH_REGION_PREF);
            String[] regionValues = getResources().getStringArray(R.array.region_values);
            int regionIndex = Arrays.asList(regionValues).indexOf(TextSecurePreferences.getMeshRegion(getContext()));
            if (regionIndex < 0 || regionIndex >= listPref.getEntries().length)
                regionIndex = 0;
            listPref.setSummary(listPref.getEntries()[regionIndex]);
        }
    }

    @Override
    public void onActivityResult(int reqCode, int resultCode, final Intent data) {
        super.onActivityResult(reqCode, resultCode, data);

        if (resultCode != Activity.RESULT_OK)
            return;

        switch (reqCode) {
            case REQUEST_ENABLE_BT:
                // re-click on the button to connect to the mesh device after bluetooth enabled
                handler.sendEmptyMessage(1);
                break;
        }
    }
}