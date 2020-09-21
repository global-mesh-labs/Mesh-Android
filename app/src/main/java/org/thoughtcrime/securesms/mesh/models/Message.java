package org.thoughtcrime.securesms.mesh.models;

import android.util.Log;

import com.gotenna.sdk.data.messages.GTBinaryMessageData;
import com.gotenna.sdk.exceptions.GTDataMissingException;
// import com.gotenna.sdk.data.messages.GTBaseMessageData;
import com.gotenna.sdk.data.messages.GTTextOnlyMessageData;
// import com.gotenna.sdk.sample.managers.ContactsManager;
import android.telephony.PhoneNumberUtils;

import org.jsoup.helper.Validate;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.util.Hex;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;

/**
 * A model class that represents a sent or received message.
 *
 * Created on 2/10/16
 *
 * @author ThomasColligan
 */

public class Message
{
    //==============================================================================================
    // Class Properties
    //==============================================================================================

    private static final String LOG_TAG = "Message";

    private Long senderGID;
    private Long receiverGID;
    private Date sentDate;
    private String text;
    private MessageStatus messageStatus;
    private String detailInfo;
    private Integer hopCount;

    private static final UnsignedInteger PHONE_NUMBER_CBOR_TAG = new UnsignedInteger(25);
    private static final UnsignedInteger MESSAGE_TEXT_CBOR_TAG = new UnsignedInteger(26);

    private static final UnsignedInteger BYTE_STRING_CBOR_TAG = new UnsignedInteger(24);
    private static final UnsignedInteger SEGMENT_NUMBER_CBOR_TAG = new UnsignedInteger(28);
    private static final UnsignedInteger SEGMENT_COUNT_CBOR_TAG = new UnsignedInteger(29);
    private static final UnsignedInteger SOCKET_ID_CBOR_TAG = new UnsignedInteger(34);

    public enum MessageStatus
    {
        SENDING,
        SENT_SUCCESSFULLY,
        ERROR_SENDING
    }

    //==============================================================================================
    // Constructor
    //==============================================================================================

    public  Message(Long senderGID, Long receiverGID, Date sentDate, String text, MessageStatus messageStatus, String detailInfo)
    {
        this.senderGID = senderGID;
        this.receiverGID = receiverGID;
        this.sentDate = sentDate;
        this.text = text;
        this.messageStatus = messageStatus;
        this.detailInfo = detailInfo;
    }

    //==============================================================================================
    // Class Instance Methods
    //==============================================================================================

    public Long getSenderGID() { return senderGID; }

    public Long getReceiverGID()
    {
        return receiverGID;
    }

    public Date getSentDate()
    {
        return sentDate;
    }

    public String getText()
    {
        return text;
    }

    public MessageStatus getMessageStatus()
    {
        return messageStatus;
    }

    public void setMessageStatus(MessageStatus messageStatus)
    {
        this.messageStatus = messageStatus;
    }

    public String getDetailInfo()
    {
        return detailInfo;
    }

    public byte[] toBytes()
    {
        // Use the goTenna SDK's helper classes to format the text data
        // in a way that is easily parsable
        GTTextOnlyMessageData gtTextOnlyMessageData = null;

        try
        {
            gtTextOnlyMessageData = new GTTextOnlyMessageData(text);
        }
        catch (GTDataMissingException e)
        {
            Log.w(LOG_TAG, e);
        }

        if (gtTextOnlyMessageData == null)
        {
            return null;
        }

        return gtTextOnlyMessageData.serializeToBytes();
    }

    public void setHopCount(Integer hopCount)
    {
        this.hopCount = hopCount;
    }

    public Integer getHopCount()
    {
        return hopCount;
    }

    //==============================================================================================
    // Static Helper Methods
    //==============================================================================================

    public static Message createReadyToSendMessage(Long senderGID, Long receiverGID, String text)
    {
        return new Message(senderGID, receiverGID, new Date(), text, MessageStatus.SENDING, null);
    }

    public static Message createMessageFromData(GTBinaryMessageData gtBinaryMessageData) {
        try {
            long senderGID = gtBinaryMessageData.getSenderGID();
            long receiverGID = gtBinaryMessageData.getRecipientGID();
            Date sentDate = gtBinaryMessageData.getMessageSentDate();
            byte[] byteData = gtBinaryMessageData.getBinaryData();
            String hexstr = Hex.toString(byteData);
            ByteArrayInputStream bais = new ByteArrayInputStream(byteData);
            List<DataItem> dataItems = new CborDecoder(bais).decode();
            Validate.isTrue(dataItems.size() == 1);
            DataItem dataItem = dataItems.get(0);
            // "knowing"
            Validate.isTrue(dataItem instanceof Map);
            Map map = (Map) dataItem;

            Collection<DataItem> keys = map.getKeys();
            if (keys.contains(PHONE_NUMBER_CBOR_TAG) && keys.contains(MESSAGE_TEXT_CBOR_TAG)) {
                String phoneNumber = null;
                String textMessage = null;

                DataItem stringThing = map.get(PHONE_NUMBER_CBOR_TAG);
                if (stringThing != null) {
                    Validate.isTrue(stringThing instanceof UnicodeString);
                    phoneNumber = ((UnicodeString) stringThing).getString();
                }
                stringThing = map.get(MESSAGE_TEXT_CBOR_TAG);
                if (stringThing != null) {
                    Validate.isTrue(stringThing instanceof UnicodeString);
                    textMessage = ((UnicodeString) stringThing).getString();
                }

                if (gtBinaryMessageData.getSenderGID() == 555555555) {
                    senderGID = Long.parseLong(phoneNumber);
                }

                return new SMSMessage(senderGID,
                        receiverGID,
                        sentDate,
                        phoneNumber,
                        textMessage,
                        MessageStatus.SENT_SUCCESSFULLY,
                        "");
            }
            else if (keys.contains(SOCKET_ID_CBOR_TAG) && keys.contains(BYTE_STRING_CBOR_TAG)) {
                byte[] socket_id = null;
                byte[] data_buffer = null;
                byte segment_number = 0;
                byte segment_count = 1;
                DataItem byteThing = map.get(SOCKET_ID_CBOR_TAG);
                if (byteThing != null) {
                    Validate.isTrue(byteThing instanceof ByteString);
                    socket_id = ((ByteString) byteThing).getBytes();
                }
                DataItem integerThing = map.get(SEGMENT_NUMBER_CBOR_TAG);
                if (integerThing != null) {
                    Validate.isTrue(integerThing instanceof UnsignedInteger);
                    segment_number = ((UnsignedInteger) integerThing).getValue().byteValue();
                }
                integerThing = map.get(SEGMENT_COUNT_CBOR_TAG);
                if (integerThing != null) {
                    Validate.isTrue(integerThing instanceof UnsignedInteger);
                    segment_count = ((UnsignedInteger) integerThing).getValue().byteValue();
                }
                byteThing = map.get(BYTE_STRING_CBOR_TAG);
                if (byteThing != null) {
                    Validate.isTrue(byteThing instanceof ByteString);
                    data_buffer = ((ByteString) byteThing).getBytes();
                }
                return new SocketMessage(senderGID,
                        receiverGID,
                        sentDate,
                        data_buffer,
                        socket_id,
                        segment_number,
                        segment_count,
                        MessageStatus.SENT_SUCCESSFULLY,
                        "");
            }
        } catch (CborException e) {
            Log.w(LOG_TAG, e);
            return null;
        }
        return null;
    }

    public static SMSMessage createMessageFromData(GTTextOnlyMessageData gtTextOnlyMessageData) {
        String textPayload = gtTextOnlyMessageData.getText();
        long senderGID = gtTextOnlyMessageData.getSenderGID();
        if (gtTextOnlyMessageData.getSenderGID() == 555555555) {
            // parse first space separated phone number as the senders phone number
            String parts[] = gtTextOnlyMessageData.getText().split(" ", 2);
            if (parts.length == 2 && PhoneNumberUtils.isGlobalPhoneNumber(parts[0])) {
                senderGID = Long.parseLong(parts[0], 10);
                textPayload = parts[1];
            }
        }
        return new SMSMessage(senderGID,
                gtTextOnlyMessageData.getRecipientGID(),
                gtTextOnlyMessageData.getMessageSentDate(),
                Long.toString(senderGID),
                textPayload,
                MessageStatus.SENT_SUCCESSFULLY,
                "");
        //getDetailInfo(gtTextOnlyMessageData));
    }

    /*
    private static String getDetailInfo(GTBaseMessageData gtBaseMessageData)
    {
        Contact contact = ContactsManager.getInstance().findContactWithGid(gtBaseMessageData.getSenderGID());
        String senderInitials = gtBaseMessageData.getSenderInitials();

        if (contact != null)
        {
            return contact.getName();
        }
        else if (senderInitials != null)
        {
            return senderInitials;
        }
        else
        {
            return Long.toString(gtBaseMessageData.getSenderGID());
        }
    }
    */
}
