package uz.heeyid.tafadapter.client;

import static uz.heeyid.tafadapter.constant.TafAdapterExceptionStatus.TAF_ADAPTER_SERVICE_UNAVAILABLE;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import uz.heeyid.tafadapter.property.TafProperties;
import uz.khakimjanovich.platform.error.GeneralException;

/**
 * The TAF breaker (SAD §8): after {@code taf.breaker.failures} failures in a row (no answer in
 * time, or a 5xx) it opens, and every call is SERVICE_UNAVAILABLE without a call to TAF. After
 * {@code taf.breaker.probe-after} one call goes through as a probe: an answer closes the breaker, a
 * failure opens it again. The gauge taf.breaker.open (1 open, 0 closed) drives the alert
 * "dependency down". Each adapter instance has its own breaker.
 */
@Component
public final class TafBreaker {

  private final int failures;
  private final java.time.Duration probeAfter;
  private final Clock clock;
  private int failuresInARow;
  private @Nullable Instant openedAt;
  private boolean probing;

  public TafBreaker(TafProperties properties, Clock clock, MeterRegistry registry) {
    this.failures = properties.breaker().failures();
    this.probeAfter = properties.breaker().probeAfter();
    this.clock = clock;
    Gauge.builder("taf.breaker.open", this, b -> b.isOpen() ? 1 : 0)
        .description("1 while the TAF breaker is open")
        .register(registry);
  }

  /** Lets a call through, or refuses it while the breaker is open. */
  public synchronized void before() {
    if (openedAt == null) {
      return;
    }
    if (!probing && !clock.instant().isBefore(openedAt.plus(probeAfter))) {
      probing = true;
      return;
    }
    throw new GeneralException(TAF_ADAPTER_SERVICE_UNAVAILABLE)
        .subError("breaker", "TAF failed " + failures + " times in a row; the breaker is open");
  }

  /** TAF answered (any status below 500). */
  public synchronized void answered() {
    failuresInARow = 0;
    openedAt = null;
    probing = false;
  }

  /** TAF did not answer in time, or answered with a 5xx. */
  public synchronized void failed() {
    failuresInARow++;
    if (probing || failuresInARow >= failures) {
      openedAt = clock.instant();
      probing = false;
    }
  }

  synchronized boolean isOpen() {
    return openedAt != null;
  }
}
