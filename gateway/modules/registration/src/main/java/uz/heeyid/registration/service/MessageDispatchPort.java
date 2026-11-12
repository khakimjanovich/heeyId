package uz.heeyid.registration.service;

import java.time.Duration;

/** Sends a one-time code by SMS: a port of registration, implemented in {@code client}. */
public interface MessageDispatchPort {

  void sendCode(String phone, String text, String idempotencyKey, Duration ttl);
}
