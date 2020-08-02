package org.thoughtcrime.securesms.mesh.models;

import android.util.Log;

import com.gotenna.sdk.data.messages.GTBinaryMessageData;
import com.gotenna.sdk.exceptions.GTDataMissingException;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;

public class TxMessage extends Message {
    private static final String LOG_TAG = "TxMessage";
    private static final long BYTE_STRING_CBOR_TAG = 24;
    private static final long BITCOIN_NETWORK_CBOR_TAG = 27;
    private static final long SEGMENT_NUMBER_CBOR_TAG = 28;
    private static final long SEGMENT_COUNT_CBOR_TAG= 29;
    private static final long SHORT_TXID_CBOR_TAG = 30;
    private static final long TXID_CBOR_TAG = 31;

    private static final int PAYLOAD_SIZE = 200;

    private byte[] byteArray;
    private byte segmentNumber = 0;
    private byte segmentCount = 1;
    private Integer byteOffset = 0;
    private byte[] txid = new byte[32];
    private byte[] short_txid = new byte[8];

    public static byte[] hexStringToByteArray(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) {
            int index = i * 2;
            int v = Integer.parseInt(s.substring(index, index + 2), 16);
            b[i] = (byte) v;
        }
        return b;
    }

    public static byte[] reverseBytes(byte[] bytes) {
        // We could use the XOR trick here but it's easier to understand if we don't. If we find this is really a
        // performance issue the matter can be revisited.
        byte[] buf = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++)
            buf[i] = bytes[bytes.length - 1 - i];
        return buf;
    }

    public TxMessage(byte[] uuid, long senderGID, long receiverGID, Date sentDate, String hexString, String network, MessageStatus messageStatus, String detailInfo) {
        super(senderGID, receiverGID, sentDate, network, messageStatus, detailInfo);
        byteArray = hexStringToByteArray(hexString);

        try {
            Transaction tx = new Transaction(network == "m" ? MainNetParams.get() : TestNet3Params.get(), byteArray);
            System.arraycopy(tx.getTxId().getBytes(), 0, txid, 0, txid.length);
        } catch(VerificationException ve) {
            Log.d("TxMessage", "Invalid transaction, hash:" + txid.toString());
            return;
        }
        System.arraycopy(txid, 0, short_txid, 0, short_txid.length);
        Integer byteArrayLength = byteArray.length;

        byteArrayLength -= (PAYLOAD_SIZE - txid.length - 19);
        while (byteArrayLength > 0 ) {
            segmentCount++;
            byteArrayLength -= (PAYLOAD_SIZE - short_txid.length - 11);
        }
    }

    public byte getSegmentCount() {
        return segmentCount;
    }

    @Override
    public byte[] toBytes() {

        GTBinaryMessageData gtBinaryMessageData = null;
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            List<DataItem> data = null;
            if (segmentNumber == 0) {
                int buffer_size = PAYLOAD_SIZE - txid.length - 26;
                byte [] subArray = Arrays.copyOfRange(byteArray, 0, buffer_size);
                data = new CborBuilder()
                        .addMap()
                        .put(SHORT_TXID_CBOR_TAG, short_txid)
                        .put(TXID_CBOR_TAG, txid)
                        .put(SEGMENT_COUNT_CBOR_TAG, segmentCount)
                        .put(BITCOIN_NETWORK_CBOR_TAG, getText().charAt(0))
                        .put(BYTE_STRING_CBOR_TAG, subArray)
                        .end()
                        .build();
                byteOffset += buffer_size;
                segmentNumber++;
            }
            else {
                int buffer_size = PAYLOAD_SIZE - short_txid.length - 11;
                byte [] subArray = Arrays.copyOfRange(byteArray, byteOffset, Math.min(byteOffset+buffer_size, byteArray.length));
                data = new CborBuilder()
                        .addMap()
                        .put(SHORT_TXID_CBOR_TAG, short_txid)
                        .put(SEGMENT_NUMBER_CBOR_TAG, segmentNumber)
                        .put(BYTE_STRING_CBOR_TAG, subArray)
                        .end()
                        .build();
                byteOffset += buffer_size;
                segmentNumber++;
            }
            new CborEncoder(output).encode(data);
            gtBinaryMessageData = new GTBinaryMessageData(output.toByteArray());
        } catch (CborException | GTDataMissingException e) {
            Log.w(LOG_TAG, e);
            return null;
        }

        return gtBinaryMessageData.serializeToBytes();
    }
}
