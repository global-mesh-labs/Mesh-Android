package org.thoughtcrime.securesms.mesh.scan;

import com.gotenna.sdk.GoTenna;
import com.gotenna.sdk.bluetooth.BluetoothAdapterManager;
import com.gotenna.sdk.bluetooth.BluetoothAdapterManager.BluetoothStatus;
import com.gotenna.sdk.bluetooth.GTConnectionManager;
import com.gotenna.sdk.bluetooth.GTConnectionManager.GTConnectionListener;
import com.gotenna.sdk.bluetooth.GTConnectionManager.GTConnectionState;
import com.gotenna.sdk.bluetooth.GTConnectionManager.GTDeviceType;
import com.gotenna.sdk.user.UserDataStore;

/**
 * A presenter for {\@link ScanActivity}.
 * <p>
 * Created on: 7/10/17.
 *
 * @author Thomas Colligan
 */

class ScanPresenter implements GTConnectionListener
{
    //==============================================================================================
    // Class Properties
    //==============================================================================================

    private ScanView view;
    private final UserDataStore userDataStore;
    private final BluetoothAdapterManager bluetoothAdapterManager;
    private final GTConnectionManager gtConnectionManager;

    //==============================================================================================
    // Constructor
    //==============================================================================================

    ScanPresenter()
    {
        userDataStore = UserDataStore.getInstance();
        bluetoothAdapterManager = BluetoothAdapterManager.getInstance();
        gtConnectionManager = GTConnectionManager.getInstance();
    }

    //==============================================================================================
    // Class Instance Methods
    //==============================================================================================

    void attachView(ScanView view)
    {
        this.view = view;
        gtConnectionManager.addGtConnectionListener(this);
    }

    void detachView()
    {
        this.view = null;
        gtConnectionManager.removeGtConnectionListener(this);
    }

    private void checkBluetoothStatus()
    {
        BluetoothStatus bluetoothStatus = bluetoothAdapterManager.getBluetoothStatus();

        switch (bluetoothStatus)
        {
            case SUPPORTED_AND_ENABLED:
                view.showGotennaDeviceTypeSelectionDialog();
                break;
            case SUPPORTED_NOT_ENABLED:
                view.requestEnableBluetooth();
                break;
            case NOT_SUPPORTED:
                view.showBluetoothNotSupportedMessage();
                break;
        }
    }

    private void scanForGotenna(GTDeviceType deviceType)
    {
        gtConnectionManager.scanAndConnect(deviceType);

        view.showScanningInProgressDialog();
        view.startTimeoutCountdown();
    }

    //==============================================================================================
    // Presenter Action Methods
    //==============================================================================================

    void onCreate()
    {
        if (!GoTenna.tokenIsVerified())
        {
            view.showInvalidTokenAlert();
            return;
        }

        //if (!PermissionsUtils.hasLocationPermission())
        //{
        //    view.requestLocationPermission();
        //}

        if (userDataStore.hasValidUser())
        {
            view.enableScanPreviousButton();
        }
        else
        {
            view.disableScanPreviousButton();
        }
    }

    void onRequestPermissionsResult()
    {
        //if (!PermissionsUtils.hasLocationPermission())
        //{
        //    view.requestLocationPermission();
        //}
    }

    void onBluetoothEnabled()
    {
        checkBluetoothStatus();
    }

    void onScanForNewGotenna()
    {
        //if (!PermissionsUtils.hasLocationPermission())
        //{
        //    view.requestLocationPermission();
        //    return;
        //}

        // Clear old connected Bluetooth MAC address of previous goTenna we were connected to
        gtConnectionManager.clearConnectedGotennaAddress();

        checkBluetoothStatus();
    }

    void onScanForPreviousGotenna()
    {
        //if (!PermissionsUtils.hasLocationPermission())
        //{
        //    view.requestLocationPermission();
        //    return;
        //}

        checkBluetoothStatus();
    }

    void onScanForV1()
    {
        scanForGotenna(GTDeviceType.V1);
    }

    void onScanForMesh()
    {
        scanForGotenna(GTDeviceType.MESH);
    }

    void onScanTimeoutOccurred()
    {
        gtConnectionManager.disconnect();
        view.dismissScanningProgressDialog();
        view.showScanningTimeoutDialog();
    }

    // ================================================================================
    // GTConnectionListener Implementation
    // ================================================================================

    @Override
    public void onConnectionStateUpdated(GTConnectionState gtConnectionState)
    {
        switch (gtConnectionState)
        {
            case CONNECTED:
            {
                view.stopTimeoutCountdown();
                view.dismissScanningProgressDialog();

                view.showSdkOptionsScreen();
            }
            break;
        }
    }

    //==============================================================================================
    // ScanView Interface
    //==============================================================================================

    interface ScanView
    {
        void showInvalidTokenAlert();

        void enableScanPreviousButton();

        void disableScanPreviousButton();

        void requestLocationPermission();

        void requestEnableBluetooth();

        void showBluetoothNotSupportedMessage();

        void showGotennaDeviceTypeSelectionDialog();

        void showScanningInProgressDialog();

        void startTimeoutCountdown();

        void stopTimeoutCountdown();

        void dismissScanningProgressDialog();

        void showScanningTimeoutDialog();

        void showSdkOptionsScreen();
    }
}
