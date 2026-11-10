package uz.heeyid.smsadapter.vendor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * One vendor's breaker (SAD §8): after {@code failures} failures in a row (no answer, no
 * connection, a 5xx) it opens, and the vendor is skipped without a call. After {@code probeAfter}
 * one send goes through as a probe: an answer closes the breaker, a failure opens it again. Each
 * adapter instance has its own breakers.
 */
public final class VendorBreaker {

  private final int failures;
  private final Duration probeAfter;
  private final Clock clock;
  private int failuresInARow;
  private @Nullable Instant openedAt;
  private boolean probing;

  public VendorBreaker(int failures, Duration probeAfter, Clock clock) {
    this.failures = failures;
    this.probeAfter = probeAfter;
    this.clock = clock;
  }

  /** True when a send may go to the vendor now. */
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

  /** The vendor answered. */
  public synchronized void answered() {
    failuresInARow = 0;
    openedAt = null;
    probing = false;
  }

  /** The vendor did not answer, refused the connection or answered with its own fault. */
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
