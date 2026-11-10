package uz.heeyid.smsadapter.vendor;

import uz.khakimjanovich.contracts.smsadapter.api.model.MessageType;

/**
 * An SMS to send.
 *
 * @param messageId the adapter's id, also the vendor's message id
 * @param phone 998901234567
 * @param text the final text
 * @param ttlSeconds after this the vendor stops trying
 */
public record OutgoingSms(
    String messageId, String phone, String text, MessageType type, int ttlSeconds) {}
