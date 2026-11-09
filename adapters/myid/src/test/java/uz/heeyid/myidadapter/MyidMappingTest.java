package uz.heeyid.myidadapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uz.heeyid.myidadapter.client.MyidClient;
import uz.heeyid.myidadapter.client.MyidClient.Answer;
import uz.heeyid.myidadapter.client.MyidJson;
import uz.khakimjanovich.contracts.myidadapter.api.model.ImageIssue;
import uz.khakimjanovich.contracts.myidadapter.api.model.ReasonKey;
import uz.khakimjanovich.platform.error.GeneralException;

/** MyID's codes and HTTP statuses as the contract names them. */
class MyidMappingTest {

  static final JsonMapper MAPPER = JsonMapper.builder().build();

  @Test
  void eachOutcomeCodeHasItsGroup() {
    assertThat(MyidJson.reasonKey(-1)).isEqualTo(ReasonKey.CONSENT_REFUSED);
    assertThat(MyidJson.reasonKey(1)).isEqualTo(ReasonKey.PASSED);
    assertThat(MyidJson.reasonKey(23)).isEqualTo(ReasonKey.POOR_IMAGE);
    assertThat(MyidJson.reasonKey(38)).isEqualTo(ReasonKey.SERVICE_UNAVAILABLE);
    assertThat(MyidJson.reasonKey(10)).isEqualTo(ReasonKey.TIMED_OUT);
    assertThat(MyidJson.reasonKey(122)).isEqualTo(ReasonKey.BLOCKED_BY_PROVIDER);
    assertThat(MyidJson.reasonKey(999)).isEqualTo(ReasonKey.UNKNOWN);
    assertThat(MyidJson.imageIssue(29)).isEqualTo(ImageIssue.ARTIFACT_OR_REFLECTION);
    assertThat(MyidJson.imageIssue(31)).isEqualTo(ImageIssue.OTHER);
    assertThat(MyidJson.imageIssue(3)).isNull();
  }

  @Test
  void eachRefusalIsTheContractsError() {
    assertThat(code(400, true)).isEqualTo("MYID_ADAPTER_CODE_NOT_VALID");
    assertThat(code(404, true)).isEqualTo("MYID_ADAPTER_CODE_NOT_VALID");
    assertThat(code(404, false)).isEqualTo("MYID_ADAPTER_NOT_FOUND");
    assertThat(code(400, false)).isEqualTo("MYID_ADAPTER_MYID_CLIENT_ERROR");
    assertThat(code(403, true)).isEqualTo("MYID_ADAPTER_UNAUTHORIZED");
    assertThat(code(429, true)).isEqualTo("MYID_ADAPTER_USER_BLOCKED");
    assertThat(code(503, false)).isEqualTo("MYID_ADAPTER_SERVICE_UNAVAILABLE");
  }

  static String code(int status, boolean exchange) {
    var answer = new Answer(MAPPER.readTree("{\"detail\":\"x\"}"), "r", status);
    var refusal = new GeneralException[1];
    assertThatThrownBy(() -> MyidClient.checked(answer, exchange))
        .isInstanceOfSatisfying(GeneralException.class, e -> refusal[0] = e);
    return refusal[0].getStatus().getType();
  }
}
