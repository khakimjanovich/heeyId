package uz.heeyid.myidadapter.mapper;

import static uz.heeyid.myidadapter.mapper.MyidJsonMapper.parse;
import static uz.khakimjanovich.contracts.myidadapter.api.model.MyidAdapterStatusCode.SUCCESS;

import org.springframework.stereotype.Component;
import uz.heeyid.myidadapter.client.MyidClient.Answer;
import uz.khakimjanovich.contracts.myidadapter.api.model.Verification;
import uz.khakimjanovich.contracts.myidadapter.api.model.VerifyIdentificationResult;

/** MyID's BNPL validation answer as the contract's result. */
@Component
public class MyidValidationMapper {

  public VerifyIdentificationResult verification(Answer answer) {
    var verification =
        parse(
            answer,
            b -> {
              var status = b.required("status").asInt();
              return new Verification(
                  b.required("job_id").asString(),
                  b.required("bnpl_id").asString(),
                  MyidJsonMapper.verificationStatus(status),
                  status,
                  b.required("result").asBoolean());
            });
    return new VerifyIdentificationResult(answer.requestId(), SUCCESS, verification);
  }
}
