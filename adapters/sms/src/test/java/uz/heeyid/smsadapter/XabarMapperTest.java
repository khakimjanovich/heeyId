package uz.heeyid.smsadapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uz.heeyid.smsadapter.vendor.xabar.mapper.XabarMapper;
import uz.khakimjanovich.contracts.smsadapter.api.model.MessageStatus;

/** Xabar's status words, dates, parts and error bodies. */
class XabarMapperTest {

  final XabarMapper mapper = new XabarMapper(JsonMapper.builder().build());

  @Test
  void statusWords() {
    assertThat(mapper.status("DELIVERED")).isEqualTo(MessageStatus.DELIVERED);
    assertThat(mapper.status("expired")).isEqualTo(MessageStatus.EXPIRED);
    assertThat(mapper.status("Rejected")).isEqualTo(MessageStatus.FAILED);
    assertThat(mapper.status("transmitted")).isEqualTo(MessageStatus.REPORTED);
  }

  @Test
  void datesWithOrWithoutAnOffsetAreUtc() {
    var at = Instant.parse("2026-11-10T10:15:30Z");
    assertThat(mapper.at("2026-11-10T10:15:30Z")).isEqualTo(at);
    assertThat(mapper.at("2026-11-10T15:15:30+05:00")).isEqualTo(at);
    assertThat(mapper.at("2026-11-10 10:15:30")).isEqualTo(at);
    assertThat(mapper.at("yesterday")).isNull();
    assertThat(mapper.at("")).isNull();
  }

  @Test
  void partsAndErrors() {
    assertThat(mapper.parts("2")).isEqualTo(2);
    assertThat(mapper.parts("")).isNull();
    var error =
        mapper.error(
            "{\"error_code\":\"404\",\"error_description\":\"Empty content\"}"
                .getBytes(StandardCharsets.UTF_8));
    assertThat(error).isNotNull();
    assertThat(error.errorCode()).isEqualTo("404");
    assertThat(mapper.error("<html>".getBytes(StandardCharsets.UTF_8))).isNull();
  }
}
