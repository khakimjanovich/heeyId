package uz.heeyid.pushadapter.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * The FCM breaker (SAD §8): after {@code failures} failures in a row (no answer, no connection, a
 * 5xx) it opens, and a send fails without a call. After {@code probeAfter} one send goes through as
 * a probe: an answer closes the breaker, a failure opens it again. Each adapter instance has its
 * own breaker.
 */
public final class FcmBreaker {

  private final int failures;
  private final Duration probeAfter;
  private final Clock clock;
  private int failuresInARow;
  private @Nullable Instant openedAt;
  private boolean probing;

  public FcmBreaker(int failures, Duration probeAfter, Clock clock) {
    this.failures = failures;
    this.probeAfter = probeAfter;
    this.clock = clock;
  }

  /** True when a send may go to FCM now. */
  public synchronized boolean allows() {
    if (openedAt == null) {
      return true;
    }
    if (!probing && !clock.instant().isBefore(openedAt.plus(probeAfter))) {
      probing = true;
      return true;
    }
    return false;
  }

  /** FCM answered, with a message id or a refusal of the message. */
  public synchronized void answered() {
    failuresInARow = 0;
    openedAt = null;
    probing = false;
  }

  /** FCM did not answer in time, refused the connection or answered with its own fault. */
  public synchronized void failed() {
    failuresInARow++;
    if (probing || failuresInARow >= failures) {
      openedAt = clock.instant();
      probing = false;
    }
  }

  public synchronized boolean isOpen() {
    return openedAt != null;
  }
}
