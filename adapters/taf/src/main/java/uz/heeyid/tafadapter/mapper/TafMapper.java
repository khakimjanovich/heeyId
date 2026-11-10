package uz.heeyid.tafadapter.mapper;

import static uz.heeyid.tafadapter.constant.TafAdapterExceptionStatus.TAF_ADAPTER_TAF_CLIENT_ERROR;
import static uz.heeyid.tafadapter.utils.ConstantUtils.HEALTH_UP;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import uz.heeyid.tafadapter.client.TafClient;
import uz.heeyid.tafadapter.client.TafClient.Answer;
import uz.khakimjanovich.contracts.tafadapter.api.model.BasicVerdict;
import uz.khakimjanovich.contracts.tafadapter.api.model.Decision;
import uz.khakimjanovich.contracts.tafadapter.api.model.Health;
import uz.khakimjanovich.contracts.tafadapter.api.model.PhoneOwner;
import uz.khakimjanovich.contracts.tafadapter.api.model.RiskVerdict;

/**
 * TAF's answers as the contract's models. A value TAF does not send, or sends of another type, is
 * TAF_CLIENT_ERROR, never a guessed false: the caller decides on these values.
 */
@Component
public class TafMapper {

  public BasicVerdict basicVerdict(Answer answer) {
    var b = answer.body();
    return new BasicVerdict(
        flag(answer, b, "isActive"),
        flag(answer, b, "codeUsable"),
        flag(answer, b, "smsSent"),
        flag(answer, b, "validStatus"),
        flag(answer, b, "verified"),
        flag(answer, b, "deviceSimMatch"),
        flag(answer, b, "noCloneDetected"));
  }

  public RiskVerdict riskVerdict(Answer answer) {
    var b = answer.body();
    var ready = flag(answer, b, "verdictReady");
    var decision = b.path("decision");
    var reason = b.path("mainReason");
    try {
      return new RiskVerdict(ready)
          .decision(decision.isString() ? Decision.fromValue(decision.asString()) : null)
          .mainReason(reason.isString() ? reason.asString() : null);
    } catch (IllegalArgumentException e) {
      throw shape(answer, "decision");
    }
  }

  public PhoneOwner phoneOwner(Answer answer) {
    return new PhoneOwner(flag(answer, answer.body(), "owner"));
  }

  /** UP only when the agent says UP. */
  public Health health(Answer answer) {
    var up = HEALTH_UP.equals(answer.body().path("status").asString(""));
    return new Health(up ? Health.StatusEnum.UP : Health.StatusEnum.DOWN);
  }

  private static boolean flag(Answer answer, JsonNode body, String name) {
    var value = body.path(name);
    if (!value.isBoolean()) {
      throw shape(answer, name);
    }
    return value.asBoolean();
  }

  private static RuntimeException shape(Answer answer, String field) {
    return TafClient.refusal(TAF_ADAPTER_TAF_CLIENT_ERROR, answer)
        .subError(field, "not of the documented shape");
  }
}
