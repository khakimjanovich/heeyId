package uz.heeyid.myidadapter.mapper;

import static uz.heeyid.myidadapter.mapper.MyidJsonMapper.imageIssue;
import static uz.heeyid.myidadapter.mapper.MyidJsonMapper.parse;
import static uz.heeyid.myidadapter.mapper.MyidJsonMapper.reasonKey;
import static uz.khakimjanovich.contracts.myidadapter.api.model.MyidAdapterStatusCode.SUCCESS;

import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;
import uz.heeyid.myidadapter.client.MyidClient.Answer;
import uz.khakimjanovich.contracts.myidadapter.api.model.Address;
import uz.khakimjanovich.contracts.myidadapter.api.model.AddressResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.JobAddress;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenSessionResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.ProofingResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.ProofingResultResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.Reuid;
import uz.khakimjanovich.contracts.myidadapter.api.model.SessionStatus;
import uz.khakimjanovich.contracts.myidadapter.api.model.SessionStatusResult;

/** MyID's Mobile SDK answers as the contract's results. */
@Component
@RequiredArgsConstructor
public class MyidSdkMapper {

  private final MyidJsonMapper json;

  public OpenSessionResult session(Answer answer) {
    return json.session(answer);
  }

  /** {@code reuid} is beside {@code data} in MyID's answer; the contract has it in the result. */
  public ProofingResultResult result(Answer answer) {
    var result =
        parse(
            answer,
            b -> {
              var data = json.read(b.required("data"), ProofingResult.class);
              var reuid = b.path("reuid");
              if (data.getReuid() == null && reuid.isObject()) {
                data.setReuid(json.read(reuid, Reuid.class));
              }
              return data;
            });
    return new ProofingResultResult(answer.requestId(), SUCCESS, result);
  }

  /** MyID's status is lower case; each attempt gets its reason. */
  public SessionStatusResult sessionStatus(Answer answer) {
    var status =
        parse(
            answer,
            b -> {
              var body = ((ObjectNode) b).deepCopy();
              body.put("status", body.required("status").asString().toUpperCase(Locale.ROOT));
              var read = json.read(body, SessionStatus.class);
              read.getAttempts()
                  .forEach(
                      a ->
                          a.reasonKey(
                                  a.getReasonCode() == null ? null : reasonKey(a.getReasonCode()))
                              .imageIssue(imageIssue(a.getReasonCode())));
              return read;
            });
    return new SessionStatusResult(answer.requestId(), SUCCESS, status);
  }

  /** MyID's {@code {response_id, profile: {address}}}. */
  public AddressResult address(Answer answer) {
    var address =
        parse(
            answer,
            b ->
                new JobAddress(
                    UUID.fromString(b.required("response_id").asString()),
                    json.read(b.required("profile").required("address"), Address.class)));
    return new AddressResult(answer.requestId(), SUCCESS, address);
  }
}
