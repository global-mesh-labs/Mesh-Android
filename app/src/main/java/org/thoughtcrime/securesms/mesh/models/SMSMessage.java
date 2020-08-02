package org.thoughtcrime.securesms.mesh.models;

import android.util.Log;

import com.gotenna.sdk.data.messages.GTBinaryMessageData;
import com.gotenna.sdk.exceptions.GTDataMissingException;

import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.List;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;

public class SMSMessage extends Message {
    private static final String LOG_TAG = "SMSMessage";
    private static final long PHONE_NUMBER_CBOR_TAG = 25;
    private static final long MESSAGE_TEXT_CBOR_TAG = 26;

    private String phoneNumber;

    public SMSMessage(long senderGID, long receiverGID, Date sentDate, String phoneNumber, String text, MessageStatus messageStatus, String detailInfo) {
        super(senderGID, receiverGID, sentDate, text, messageStatus, detailInfo);
        this.phoneNumber = phoneNumber;
    }

    @Override
    public byte[] toBytes() {
        GTBinaryMessageData gtBinaryMessageData = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            List<DataItem> data = new CborBuilder()
                    .addMap()
                    .put(PHONE_NUMBER_CBOR_TAG, Long.parseLong(phoneNumber))
                    .put(MESSAGE_TEXT_CBOR_TAG, getText())
                    .end()
                    .build();
            new CborEncoder(baos).encode(data);
            gtBinaryMessageData = new GTBinaryMessageData(baos.toByteArray());
        } catch (CborException | GTDataMissingException e) {
            Log.w(LOG_TAG, e);
            return null;
        }

        return gtBinaryMessageData.serializeToBytes();
    }
}
