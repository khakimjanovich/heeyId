package uz.heeyid.myidadapter.client;

import static uz.heeyid.myidadapter.constant.MyidAdapterExceptionStatus.MYID_ADAPTER_SERVICE_UNAVAILABLE;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import uz.heeyid.myidadapter.config.MyidProperties;
import uz.khakimjanovich.platform.error.GeneralException;

/**
 * The MyID breaker (SAD §8): after {@code myid.breaker.failures} failures in a row (no answer in
 * time, or a 5xx) it opens, and every call is SERVICE_UNAVAILABLE without a call to MyID. After
 * {@code myid.breaker.probe-after} one call goes through as a probe: an answer closes the breaker,
 * a failure opens it again. The gauge myid.breaker.open (1 open, 0 closed) drives the alert
 * "dependency down". Each adapter instance has its own breaker.
 */
@Component
public final class MyidBreaker {

  private final int failures;
  private final java.time.Duration probeAfter;
  private final Clock clock;
  private int failuresInARow;
  private @Nullable Instant openedAt;
  private boolean probing;

  public MyidBreaker(MyidProperties properties, Clock clock, MeterRegistry registry) {
    this.failures = properties.breaker().failures();
    this.probeAfter = properties.breaker().probeAfter();
    this.clock = clock;
    Gauge.builder("myid.breaker.open", this, b -> b.isOpen() ? 1 : 0)
        .description("1 while the MyID breaker is open")
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
    throw new GeneralException(MYID_ADAPTER_SERVICE_UNAVAILABLE)
        .subError("breaker", "MyID failed " + failures + " times in a row; the breaker is open");
  }

  /** MyID answered (any status below 500). */
  public synchronized void answered() {
    failuresInARow = 0;
    openedAt = null;
    probing = false;
  }

  /** MyID did not answer in time, or answered with a 5xx. */
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
