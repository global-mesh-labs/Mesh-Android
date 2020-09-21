package org.thoughtcrime.securesms.sms;

import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;

public class OutgoingTextMessage {

  private final Recipient recipient;
  private final String    message;
  private final int       subscriptionId;
  private final long      expiresIn;
  private final long      messageType;

  public OutgoingTextMessage(Recipient recipient, String message, int subscriptionId) {
    this(recipient, message, 0, subscriptionId, MmsSmsColumns.Types.getOutgoingSmsMessageType());
  }

  public OutgoingTextMessage(Recipient recipient, String message, long expiresIn, int subscriptionId, long messageType) {
    this.recipient      = recipient;
    this.message        = message;
    this.expiresIn      = expiresIn;
    this.subscriptionId = subscriptionId;
    this.messageType    = messageType;
  }

  protected OutgoingTextMessage(OutgoingTextMessage base, String body) {
    this.recipient      = base.getRecipient();
    this.subscriptionId = base.getSubscriptionId();
    this.expiresIn      = base.getExpiresIn();
    this.messageType  = base.getMessageType();
    this.message        = body;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public String getMessageBody() {
    return message;
  }

  public Recipient getRecipient() {
    return recipient;
  }

  public long getMessageType() {
    return messageType;
  }

  public boolean isKeyExchange() {
    return false;
  }

  public boolean isSecureMessage() {
    return false;
  }

  public boolean isEndSession() {
    return false;
  }

  public boolean isPreKeyBundle() {
    return false;
  }

  public boolean isIdentityVerified() {
    return false;
  }

  public boolean isIdentityDefault() {
    return false;
  }

  public static OutgoingTextMessage from(SmsMessageRecord record) {
    if (record.isSecure()) {
      return new OutgoingEncryptedMessage(record.getRecipient(), record.getBody(), record.getExpiresIn());
    } else if (record.isKeyExchange()) {
      return new OutgoingKeyExchangeMessage(record.getRecipient(), record.getBody());
    } else if (record.isEndSession()) {
      return new OutgoingEndSessionMessage(new OutgoingTextMessage(record.getRecipient(), record.getBody(), 0, -1, record.getType()));
    } else {
      return new OutgoingTextMessage(record.getRecipient(), record.getBody(), record.getExpiresIn(), record.getSubscriptionId(), record.getType());
    }
  }

  public OutgoingTextMessage withBody(String body) {
    return new OutgoingTextMessage(this, body);
  }
}
