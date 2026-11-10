package uz.heeyid.myidadapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uz.heeyid.myidadapter.client.MyidClient;
import uz.heeyid.myidadapter.client.MyidClient.Answer;
import uz.heeyid.myidadapter.mapper.MyidJsonMapper;
import uz.heeyid.myidadapter.utils.MyidPhotoUtils;
import uz.khakimjanovich.contracts.myidadapter.api.model.ImageIssue;
import uz.khakimjanovich.contracts.myidadapter.api.model.ReasonKey;
import uz.khakimjanovich.platform.error.GeneralException;

/** MyID's codes and HTTP statuses as the contract names them. */
class MyidMappingTest {

  static final JsonMapper MAPPER = JsonMapper.builder().build();

  @Test
  void eachOutcomeCodeHasItsGroup() {
    assertThat(MyidJsonMapper.reasonKey(-1)).isEqualTo(ReasonKey.CONSENT_REFUSED);
    assertThat(MyidJsonMapper.reasonKey(1)).isEqualTo(ReasonKey.PASSED);
    assertThat(MyidJsonMapper.reasonKey(23)).isEqualTo(ReasonKey.POOR_IMAGE);
    assertThat(MyidJsonMapper.reasonKey(38)).isEqualTo(ReasonKey.SERVICE_UNAVAILABLE);
    assertThat(MyidJsonMapper.reasonKey(10)).isEqualTo(ReasonKey.TIMED_OUT);
    assertThat(MyidJsonMapper.reasonKey(122)).isEqualTo(ReasonKey.BLOCKED_BY_PROVIDER);
    assertThat(MyidJsonMapper.reasonKey(999)).isEqualTo(ReasonKey.UNKNOWN);
    assertThat(MyidJsonMapper.imageIssue(29)).isEqualTo(ImageIssue.ARTIFACT_OR_REFLECTION);
    assertThat(MyidJsonMapper.imageIssue(31)).isEqualTo(ImageIssue.OTHER);
    assertThat(MyidJsonMapper.imageIssue(3)).isNull();
    assertThat(MyidJsonMapper.reasonKey(33)).isEqualTo(ReasonKey.POOR_IMAGE);
    assertThat(MyidJsonMapper.imageIssue(33)).isEqualTo(ImageIssue.OTHER);
  }

  @Test
  void aPngIsKnownByItsWholeSignature() {
    var png = new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0};
    assertThat(MyidPhotoUtils.isPng(png)).isTrue();
    assertThat(MyidPhotoUtils.dataUri(png)).startsWith("data:image/png;base64,");
    var notPng = new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4, 5};
    assertThat(MyidPhotoUtils.isPng(notPng)).isFalse();
    assertThat(MyidPhotoUtils.dataUri(notPng)).startsWith("data:image/jpeg;base64,");
  }

  @Test
  void anAnswerOfAnotherShapeIsAClientErrorWithTheRequestId() {
    var answer = new Answer(MAPPER.readTree("\"html\""), "r-9", 200);
    assertThatThrownBy(() -> MyidJsonMapper.parse(answer, b -> b.required("status").asString()))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> {
              assertThat(e.getStatus().getType()).isEqualTo("MYID_ADAPTER_MYID_CLIENT_ERROR");
              assertThat(e.getSubErrors().get(1).code()).isEqualTo("X-Request-ID");
              assertThat(e.getSubErrors().get(1).message()).isEqualTo("r-9");
            });
  }

  @Test
  void eachRefusalIsTheContractsError() {
    assertThat(code(400, true)).isEqualTo("MYID_ADAPTER_CODE_NOT_VALID");
    assertThat(code(404, true)).isEqualTo("MYID_ADAPTER_CODE_NOT_VALID");
    assertThat(code(404, false)).isEqualTo("MYID_ADAPTER_NOT_FOUND");
    assertThat(code(400, false)).isEqualTo("MYID_ADAPTER_MYID_CLIENT_ERROR");
    assertThat(code(403, true)).isEqualTo("MYID_ADAPTER_UNAUTHORIZED");
    assertThat(code(422, true)).isEqualTo("MYID_ADAPTER_MYID_CLIENT_ERROR");
    assertThat(code(422, false)).isEqualTo("MYID_ADAPTER_MYID_CLIENT_ERROR");
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
