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

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;

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

    private long senderGID;
    private long receiverGID;
    private Date sentDate;
    private String text;
    private MessageStatus messageStatus;
    private String detailInfo;
    private int hopCount;

    public enum MessageStatus
    {
        SENDING,
        SENT_SUCCESSFULLY,
        ERROR_SENDING
    }

    //==============================================================================================
    // Constructor
    //==============================================================================================

    public  Message(long senderGID, long receiverGID, Date sentDate, String text, MessageStatus messageStatus, String detailInfo)
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

    public long getSenderGID()
    {
        return senderGID;
    }

    public long getReceiverGID()
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

    public void setHopCount(int hopCount)
    {
        this.hopCount = hopCount;
    }

    public int getHopCount()
    {
        return hopCount;
    }

    //==============================================================================================
    // Static Helper Methods
    //==============================================================================================

    public static Message createReadyToSendMessage(long senderGID, long receiverGID, String text)
    {
        return new Message(senderGID, receiverGID, new Date(), text, MessageStatus.SENDING, null);
    }

    public static Message createMessageFromData(GTBinaryMessageData gtBinaryMessageData) {
        try {
            long senderGID = gtBinaryMessageData.getSenderGID();
            String phoneNumber = null;
            String textMessage = null;
            ByteArrayInputStream bais = new ByteArrayInputStream(gtBinaryMessageData.serializeToBytes());
            List<DataItem> dataItems = new CborDecoder(bais).decode();
            Validate.isTrue(dataItems.size() == 1);
            DataItem dataItem = dataItems.get(0);
            // "knowing"
            Validate.isTrue(dataItem instanceof Map);
            Map map = (Map) dataItem;

            DataItem stringThing = map.get(new UnicodeString("phone_number"));
            if (stringThing != null) {
                Validate.isTrue(stringThing instanceof UnicodeString);
                phoneNumber = ((UnicodeString) stringThing).getString();
            }
            stringThing = map.get(new UnicodeString("text_message"));
            if (stringThing != null) {
                Validate.isTrue(stringThing instanceof UnicodeString);
                textMessage = ((UnicodeString) stringThing).getString();
            }

            if (gtBinaryMessageData.getSenderGID() == 555555555) {
                senderGID = Long.parseLong(phoneNumber);
            }

            return new SMSMessage(senderGID,
                    gtBinaryMessageData.getRecipientGID(),
                    gtBinaryMessageData.getMessageSentDate(),
                    phoneNumber,
                    textMessage,
                    MessageStatus.SENT_SUCCESSFULLY,
                    "");
        } catch (CborException e) {
            Log.w(LOG_TAG, e);
            return null;
        }
        //getDetailInfo(gtTextOnlyMessageData));
    }

    public static Message createMessageFromData(GTTextOnlyMessageData gtTextOnlyMessageData) {
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
        return new Message(senderGID,
                gtTextOnlyMessageData.getRecipientGID(),
                gtTextOnlyMessageData.getMessageSentDate(),
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
