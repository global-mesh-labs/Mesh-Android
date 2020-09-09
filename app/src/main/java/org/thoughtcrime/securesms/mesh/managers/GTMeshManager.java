package org.thoughtcrime.securesms.mesh.managers;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.gotenna.sdk.GoTenna;
import com.gotenna.sdk.connection.GTConnectionManager;
import com.gotenna.sdk.data.GTCommand;
import com.gotenna.sdk.data.GTCommandCenter;
import com.gotenna.sdk.data.GTError;
import com.gotenna.sdk.data.Place;
import com.gotenna.sdk.data.messages.GTBinaryMessageData;
import com.gotenna.sdk.exceptions.GTInvalidAppTokenException;
import com.gotenna.sdk.data.GTErrorListener;
import com.gotenna.sdk.data.messages.GTBaseMessageData;
import com.gotenna.sdk.data.messages.GTGroupCreationMessageData;
import com.gotenna.sdk.data.messages.GTMessageData;
import com.gotenna.sdk.data.messages.GTTextOnlyMessageData;
import com.gotenna.sdk.data.GTResponse;
import com.gotenna.sdk.data.GTDeviceType;
import com.gotenna.sdk.connection.GTConnectionState;
import com.gotenna.sdk.connection.GTConnectionError;

import org.thoughtcrime.securesms.socksserver.SocksServer;
import org.thoughtcrime.securesms.mesh.models.SMSMessage;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.mesh.models.Message;
import org.thoughtcrime.securesms.mesh.models.SendMessageInteractor;
import org.thoughtcrime.securesms.mesh.models.TxMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;


// TEST TEST TEST
import java.security.Security;
import java.util.Random;

/**
 * A singleton that manages listening for incoming messages from the SDK and parses them into
 * usable data classes.
 * <p>
 * Created on 2/10/16
 * Modified on 10/20/2018
 *
 * @author ThomasColligan, RichardMyers
 */
public class GTMeshManager implements GTCommandCenter.GTMessageListener, GTCommand.GTCommandResponseListener, GTErrorListener, GTConnectionManager.GTConnectionListener
{
    //==============================================================================================
    // Class Properties
    //==============================================================================================

    private static final String GOTENNA_APP_TOKEN = "";// TODO: Insert your token
    private static Context applicationContext;
    private static final String TAG = GTMeshManager.class.getSimpleName();
    private static final boolean WILL_ENCRYPT_MESSAGES = true; // Can optionally encrypt messages using SDK

    private GTConnectionManager gtConnectionManager = null;
    private static final int SCAN_TIMEOUT = 25000; // 25 seconds

    private Handler handler;
    // private NetworkingActivity callbackActivity;

    // set in ConversationActivity
    private boolean isSecureText          = false;
    private boolean isDefaultSms          = false;
    private boolean isSecurityInitialized = true;
    private Recipient recipient;

    private final ArrayList<IncomingMessageListener> incomingMessageListeners;

    private SendMessageInteractor sendMessageInteractor;

    // set in
    private PendingIntent gtSentIntent;
    private PendingIntent gtDeliveryIntent;

    // socks4/5 proxy server
    SocksServer proxyServer = null;

    // LND (test) mesh socket server
    MeshSocketServer meshSocketServer = null;

    //==============================================================================================
    // Singleton Methods
    //==============================================================================================

    private GTMeshManager()
    {
        incomingMessageListeners = new ArrayList<>();
    }

    private static class SingletonHelper
    {
        private static final GTMeshManager INSTANCE = new GTMeshManager();
    }

    public static GTMeshManager getInstance()
    {
        return SingletonHelper.INSTANCE;
    }

    public static long getGidFromPhoneNumber(final String phoneNumber) { return Long.parseLong(phoneNumber.replaceAll("[^0-9]", "")); }

    static {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    //==============================================================================================
    // Class Instance Methods
    //==============================================================================================

    public void initToken(Context context) {
        applicationContext = context;
        try {
            GoTenna.setApplicationToken(applicationContext, GOTENNA_APP_TOKEN);
            if(GoTenna.tokenIsVerified()) {
                Log.d(TAG, "goTenna token is verified:" + GoTenna.tokenIsVerified());

                gtConnectionManager = GTConnectionManager.getInstance();
                startListening();

                sendMessageInteractor = new SendMessageInteractor();

                // automatically connect to mesh
                if (!isPaired()) {
                    connect(this);
                }
            }
        }
        catch(GTInvalidAppTokenException e) {
            e.printStackTrace();
        }

        // forward traffic on port 1337 to the mesh Internet gateway
        try {
            // to create SOCKS server proxy to mesh gateway
            proxyServer = new SocksServer();
            proxyServer.start(8888, new MeshProxyHandlerFactory(sendMessageInteractor));

            // to test LND between two mesh nodes laptop IP = 192.168.86.56, where LND node 2 listens on port 9734 for p2p connections
            // meshSocketServer = new MeshSocketServer(8888, "192.168.86.56", 9734, sendMessageInteractor);
        }
        catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public void startListening()
    {
        GTCommandCenter.getInstance().setMessageListener(this);
    }

    public void addIncomingMessageListener(IncomingMessageListener incomingMessageListener)
    {
        synchronized (incomingMessageListeners)
        {
            if (incomingMessageListener != null)
            {
                incomingMessageListeners.remove(incomingMessageListener);
                incomingMessageListeners.add(incomingMessageListener);
            }
        }
    }

    public void removeIncomingMessageListener(IncomingMessageListener incomingMessageListener)
    {
        synchronized (incomingMessageListeners)
        {
            if (incomingMessageListener != null)
            {
                incomingMessageListeners.remove(incomingMessageListener);
            }
        }
    }

    private void notifyIncomingMessage(final Message incomingMessage)
    {
        synchronized (incomingMessageListeners)
        {
            for (IncomingMessageListener incomingMessageListener : incomingMessageListeners)
            {
                incomingMessageListener.onIncomingMessage(incomingMessage);
            }
        }
    }

   //==============================================================================================
    // GTMessageListener Implementation
    //==============================================================================================

    @Override
    public void onIncomingMessage(GTMessageData messageData)
    {
        // We do not send any custom formatted messages in this app,
        // but if you wanted to send out messages with your own format, this is where
        // you would receive those messages.
        // TODO: parse custom messages?
    }

    @Override
    public void onIncomingMessage(GTBaseMessageData gtBaseMessageData)
    {
        if (gtBaseMessageData instanceof GTBinaryMessageData)
        {
            // Somebody sent us a message, try to parse it
            GTBinaryMessageData gtBaseBinaryData = (GTBinaryMessageData) gtBaseMessageData;
            Message incomingMessage = Message.createMessageFromData(gtBaseBinaryData);
            notifyIncomingMessage(incomingMessage);
        }
        if (gtBaseMessageData instanceof GTTextOnlyMessageData)
        {
            // Somebody sent us a message, try to parse it
            GTTextOnlyMessageData gtTextOnlyMessageData = (GTTextOnlyMessageData) gtBaseMessageData;
            Message incomingMessage = Message.createMessageFromData(gtTextOnlyMessageData);
            notifyIncomingMessage(incomingMessage);
        }
        else if (gtBaseMessageData instanceof GTGroupCreationMessageData)
        {
            // Somebody invited us to a group!
            GTGroupCreationMessageData gtGroupCreationMessageData = (GTGroupCreationMessageData) gtBaseMessageData;
            // showGroupInvitationToast(gtGroupCreationMessageData.getGroupGID());
        }
    }

    //==============================================================================================
    // IncomingMessageListener Interface
    //==============================================================================================

    public interface IncomingMessageListener
    {
        void onIncomingMessage(Message incomingMessage);
    }

    // derived from SmsReceiveJob.storeMessage
    public Optional<MessagingDatabase.InsertResult> storeMessage(final Message gtMessage)
    {
        if (gtMessage instanceof SMSMessage) {
            SmsDatabase database = DatabaseFactory.getSmsDatabase(applicationContext);
            database.ensureMigration();

            if (TextSecurePreferences.getNeedsSqlCipherMigration(applicationContext)) {
                // TODO: throw new SmsReceiveJob.MigrationPendingException();
            }

            final String senderGID = "+" + Long.toString(gtMessage.getSenderGID());
            Log.d(TAG, "storeMessage from sender GID:" + senderGID);

            Recipient recipient = Recipient.external(applicationContext, senderGID);
            RecipientId sender = recipient.getId();
            IncomingTextMessage message = new IncomingTextMessage(sender, 0, System.currentTimeMillis(), gtMessage.getText(), Optional.absent(), 0, false);

            if (message.isSecureMessage()) {
                IncomingTextMessage placeholder = new IncomingTextMessage(message, "");
                Optional<MessagingDatabase.InsertResult> insertResult = database.insertMessageInbox(placeholder);
                database.markAsLegacyVersion(insertResult.get().getMessageId());

                return insertResult;
            } else {
                return database.insertMessageInbox(message);
            }
        }
        return Optional.absent();
    }

    public boolean isPaired()  {

        if(gtConnectionManager != null && gtConnectionManager.getGtConnectionState() == GTConnectionState.CONNECTED) {
            return (gtConnectionManager.getConnectedGotennaAddress() != null);
        }
        return false;
    }

    public void setGeoloc(Place place){
        if (isPaired()) {
            GTCommandCenter.getInstance().sendSetGeoRegion(place, new GTCommand.GTCommandResponseListener() {
                @Override
                public void onResponse(GTResponse response) {
                    if (response.getResponseCode() == GTResponse.GTCommandResponseCode.POSITIVE) {
                        Log.d(TAG, "Region set OK");
                    } else {
                        Log.d(TAG, "Region set:" + response.toString());
                    }
                }
            }, new GTErrorListener() {
                @Override
                public void onError(GTError error) {
                    Log.d(TAG, error.toString() + "," + error.getCode());
                }
            });
        }
    }

    private boolean hasBluetoothPermisson() {
        return ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationpermission() {
        return ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public void disconnect(GTConnectionManager.GTConnectionListener listener) {
        if (listener != null) {
            gtConnectionManager.addGtConnectionListener(listener);
        }
        gtConnectionManager.addGtConnectionListener(this);
        gtConnectionManager.disconnect();
    }

    public void connect(GTConnectionManager.GTConnectionListener listener) {

        if(gtConnectionManager != null && hasBluetoothPermisson() && hasLocationpermission()) {
            if (listener != null) {
                gtConnectionManager.addGtConnectionListener(listener);
            }
            gtConnectionManager.addGtConnectionListener(this);
            gtConnectionManager.clearConnectedGotennaAddress();
            gtConnectionManager.scanAndConnect(GTDeviceType.MESH);
        }
    }

    @Override
    public void onResponse(final GTResponse gtResponse) {

        Log.d(TAG, "onResponse: " + gtResponse.toString());
        try {
            if (gtResponse.getResponseCode() == GTResponse.GTCommandResponseCode.POSITIVE) {
                gtSentIntent.send(Activity.RESULT_OK); // Sent!
                gtDeliveryIntent.send();  // Delivered!
            }
            else if (gtResponse.getResponseCode() == GTResponse.GTCommandResponseCode.NEGATIVE) {
                // TODO: map responses properly to intent parsing
                gtSentIntent.send(SmsManager.RESULT_ERROR_NO_SERVICE); // FAILED_TYPE
            }
            else {
                // TODO: map responses properly to intent parsing
                gtSentIntent.send(SmsManager.RESULT_ERROR_GENERIC_FAILURE); // FAILED_TYPE
            }
        }
        catch(PendingIntent.CanceledException e) {
            // ignore
        }
    }

    @Override
    public void onError(GTError gtError){
        Log.d(TAG, "onError: " + gtError.toString());
        if (gtDeliveryIntent != null) {
            try {
                // TODO: map responses properly to intent parsing
                gtSentIntent.send(SmsManager.RESULT_ERROR_GENERIC_FAILURE); // FAILED_TYPE
            } catch (PendingIntent.CanceledException e) {
                // ignore
            }
        }
    }

    public void sendTextMessageInternal(String destinationAddress, String phoneNumber,
                                         String textMessage, PendingIntent sentIntent, PendingIntent deliveryIntent,
                                         boolean persistMessage) {

        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }

        if (TextUtils.isEmpty(textMessage)) {
            throw new IllegalArgumentException("Invalid message body");
        }

        // TODO: set willEncrypt to true
        final String localAddress = TextSecurePreferences.getLocalNumber(applicationContext);
        long senderGID = getGidFromPhoneNumber(localAddress);
        long receiverGID = getGidFromPhoneNumber(destinationAddress);

        Date localDateTime = new Date();
        SMSMessage gtMessage = new SMSMessage(senderGID, receiverGID, localDateTime, phoneNumber, textMessage, Message.MessageStatus.SENDING, "");

        sendMessageInteractor.sendMessage(gtMessage, false,
                new SendMessageInteractor.SendMessageListener()
                {
                    @Override
                    public void onMessageResponseReceived()
                    {
                        /*
                        if (view != null)
                        {
                            view.showMessages(createMessageViewModels());
                        }
                        */
                    }
                });

        gtSentIntent = sentIntent;
        gtDeliveryIntent = deliveryIntent;

        //iccISms.sendTextForSubscriber(getSubscriptionId(), ActivityThread.currentPackageName(),
        //        destinationAddress,
        //        scAddress, text, sentIntent, deliveryIntent,
        //        persistMessage);
    }

    public void sendBitcoinTxInternal(String destinationAddress, String hexString, String network) {

        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }

        if (hexString.isEmpty()) {
            throw new IllegalArgumentException("Invalid message body");
        }

        // TODO: set willEncrypt to true
        final String localAddress = TextSecurePreferences.getLocalNumber(applicationContext);
        long senderGID = getGidFromPhoneNumber(localAddress);
        long receiverGID = getGidFromPhoneNumber(destinationAddress);

        // TODO: save/increment nonce instead of using Random() ?
        //String _id = PrefsUtil.getInstance(context).getValue(PrefsUtil.GOTENNA_UID, "");
        String _id = Long.toString(senderGID);
        _id = _id + "|" + (new Random()).nextInt();

        byte uuid[] = null;
        try {
            byte[] buf = _id.getBytes("UTF-8");
            MessageDigest md = MessageDigest.getInstance("MD5");
            uuid = md.digest(buf);
        }
        catch(UnsupportedEncodingException | NoSuchAlgorithmException e) {
            ;
        }

        Date localDateTime = new Date();

        TxMessage gtMessage = new TxMessage(uuid, senderGID, receiverGID, localDateTime, hexString,
                network, Message.MessageStatus.SENDING, "");

        for (byte index = 0; index < gtMessage.getSegmentCount(); index++) {
            sendMessageInteractor.sendMessage(gtMessage, false,
                new SendMessageInteractor.SendMessageListener() {
                    @Override
                    public void onMessageResponseReceived() {
                    }
                });
        }

        gtSentIntent = null;
        gtDeliveryIntent = null;
    }

    public void setGeoloc() {
        // set the geoloc region to current preference
        String meshRegion = TextSecurePreferences.getMeshRegion(applicationContext);
        Place place = Place.valueOf((String)meshRegion);
        android.util.Log.d(TAG, "Set Geoloc Region:" + place.getName());
        setGeoloc(place);
    }

    @Override
    public void onConnectionStateUpdated(@NonNull GTConnectionState gtConnectionState) {
        switch (gtConnectionState) {
            case CONNECTED: {
                android.util.Log.d(TAG, "Connected to device.");
                setGeoloc();
                //meshSocketServer.startServer();
            }
            break;
            case DISCONNECTED: {
                android.util.Log.d(TAG, "Disconnected from device.");
                //meshSocketServer.stopServer();
                GTConnectionManager.getInstance().removeGtConnectionListener(this);
            }
            break;
            case SCANNING: {
                android.util.Log.d(TAG, "Scanning for device.");
            }
            break;
        }
    }

    @Override
    public void onConnectionError(@NonNull GTConnectionState connectionState, @NonNull GTConnectionError error)
    {
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
}