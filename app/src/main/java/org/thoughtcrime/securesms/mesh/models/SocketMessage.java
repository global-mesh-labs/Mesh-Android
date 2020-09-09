package org.thoughtcrime.securesms.mesh.models;

import android.util.Log;

import com.gotenna.sdk.data.messages.GTBinaryMessageData;
import com.gotenna.sdk.exceptions.GTDataMissingException;

import org.whispersystems.libsignal.util.Hex;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;

public class SocketMessage extends Message {
    private static final String LOG_TAG = "SocketMessage";
    private static final long BYTE_STRING_CBOR_TAG = 24;
    private static final long SEGMENT_NUMBER_CBOR_TAG = 28;
    private static final long SEGMENT_COUNT_CBOR_TAG = 29;
    private static final long HOST_CBOR_TAG = 32;
    private static final long PORT_CBOR_TAG = 33;
    private static final long SOCKET_ID_CBOR_TAG = 34;

    private byte[] buffer;
    private byte[] socketId = new byte[8];
    private String destinationHost;
    private Integer destinationPort;
    private Boolean isFirst = true;
    private byte segment_count;
    private byte segment_number;

    public SocketMessage(Long senderGID, Long receiverGID, Date sentDate, byte[] buffer, String host, Integer port, byte segment_count, MessageStatus messageStatus, String detailInfo) {
        super(senderGID, receiverGID, sentDate, senderGID + ":" + host + ":" + port, messageStatus, detailInfo);
        this.buffer = buffer.clone();
        this.destinationHost = host;
        this.destinationPort = port;
        this.segment_number = 0;
        this.segment_count = segment_count;
        this.isFirst = true;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(getText().getBytes(), 0, getText().length());
            System.arraycopy(digest.digest(),0,socketId,0,socketId.length);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        Log.w(LOG_TAG, "SocketMessage(), server:" + host + ":" + String.valueOf(port) + " socket_id: " + Hex.toStringCondensed(socketId) + " buffer.length: " + String.valueOf(buffer.length) + " " + String.valueOf(segment_number) + "/" + String.valueOf(segment_count));
    }

    public SocketMessage(Long senderGID, Long receiverGID, Date sentDate, byte[] buffer, byte[] socketId, byte segment_number, byte segment_count, MessageStatus messageStatus, String detailInfo) {
        super(senderGID, receiverGID, sentDate, "", messageStatus, detailInfo);
        this.buffer = buffer.clone();
        this.destinationHost = "";
        this.destinationPort = 0;
        this.isFirst = false;
        this.segment_number = segment_number;
        this.segment_count = segment_count;
        System.arraycopy(socketId,0, this.socketId,0,this.socketId.length);
        Log.w(LOG_TAG, "SocketMessage(), socket_id: " + Hex.toStringCondensed(socketId) + " buffer.length: " + String.valueOf(buffer.length) + " " + String.valueOf(segment_number) + "/" + String.valueOf(segment_count));
    }

    public byte[] getBuffer() {
        return buffer;
    }
    public byte[] getSocketId() { return socketId; }
    public byte getIndex() { return segment_number; }
    public byte getCount() { return segment_count; }

    @Override
    public byte[] toBytes() {

        GTBinaryMessageData gtBinaryMessageData = null;
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            List<DataItem> data = null;
            if (isFirst) {
                data = new CborBuilder()
                        .addMap()
                        .put(SOCKET_ID_CBOR_TAG, socketId)
                        .put(HOST_CBOR_TAG, destinationHost)
                        .put(PORT_CBOR_TAG, destinationPort)
                        .put(SEGMENT_COUNT_CBOR_TAG, segment_count)
                        .put(BYTE_STRING_CBOR_TAG, buffer)
                        .end()
                        .build();
            }
            else {
                data = new CborBuilder()
                        .addMap()
                        .put(SOCKET_ID_CBOR_TAG, socketId)
                        .put(SEGMENT_COUNT_CBOR_TAG, segment_count)
                        .put(SEGMENT_NUMBER_CBOR_TAG, segment_number)
                        .put(BYTE_STRING_CBOR_TAG, buffer)
                        .end()
                        .build();
            }
            new CborEncoder(output).encode(data);
            gtBinaryMessageData = new GTBinaryMessageData(output.toByteArray());
        } catch (CborException | GTDataMissingException e) {
            Log.w(LOG_TAG, e);
            return null;
        }
        catch(Exception e) {
            Log.w(LOG_TAG, e);
            return null;
        }

        return gtBinaryMessageData.serializeToBytes();
    }
}

