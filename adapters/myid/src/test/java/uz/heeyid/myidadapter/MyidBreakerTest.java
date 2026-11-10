package uz.heeyid.myidadapter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import uz.heeyid.myidadapter.client.MyidBreaker;
import uz.heeyid.myidadapter.property.MyidProperties;
import uz.khakimjanovich.platform.error.GeneralException;

/** SAD §8: open after 5 failures in a row, one probe each 30 s. */
class MyidBreakerTest {

  /** A clock the test moves. */
  static final class TestClock extends Clock {
    Instant now = Instant.parse("2026-11-09T10:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  final TestClock clock = new TestClock();
  final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  final MyidBreaker breaker =
      new MyidBreaker(
          new MyidProperties(
              "http://myid",
              "http://media",
              Duration.ofSeconds(1),
              Duration.ofSeconds(1),
              Duration.ofSeconds(30),
              new MyidProperties.Breaker(5, Duration.ofSeconds(30)),
              "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
              new MyidProperties.Credential("client", "secret", null, null)),
          clock,
          registry);

  @Test
  void opensAfterFiveFailuresInARowAndProbesAfter30Seconds() {
    for (var i = 0; i < 4; i++) {
      breaker.before();
      breaker.failed();
    }
    breaker.answered(); // an answer starts the count again
    for (var i = 0; i < 5; i++) {
      breaker.before();
      breaker.failed();
    }
    assertThatThrownBy(breaker::before).isInstanceOf(GeneralException.class);
    assertGauge(1);

    clock.now = clock.now.plusSeconds(30);
    assertThatCode(breaker::before).doesNotThrowAnyException(); // the probe
    assertThatThrownBy(breaker::before).isInstanceOf(GeneralException.class); // only one
    breaker.failed(); // the probe failed: open again for 30 s
    clock.now = clock.now.plusSeconds(29);
    assertThatThrownBy(breaker::before).isInstanceOf(GeneralException.class);

    clock.now = clock.now.plusSeconds(1);
    breaker.before();
    breaker.answered();
    assertThatCode(breaker::before).doesNotThrowAnyException();
    assertGauge(0);
  }

  void assertGauge(double value) {
    org.assertj.core.api.Assertions.assertThat(registry.get("myid.breaker.open").gauge().value())
        .isEqualTo(value);
  }
}
