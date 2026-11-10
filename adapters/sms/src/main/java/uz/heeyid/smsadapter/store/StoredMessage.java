package uz.heeyid.smsadapter.store;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import uz.khakimjanovich.contracts.smsadapter.api.model.MessageStatus;
import uz.khakimjanovich.contracts.smsadapter.api.model.MessageType;

/** A message as Valkey keeps it: no text and no phone number, only the status. */
public record StoredMessage(
    String messageId,
    String vendor,
    MessageType type,
    MessageStatus status,
    @Nullable String vendorStatus,
    @Nullable Integer parts,
    Instant statusAt) {}
