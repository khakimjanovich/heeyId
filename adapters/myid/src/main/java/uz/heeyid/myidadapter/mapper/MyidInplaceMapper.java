package uz.heeyid.myidadapter.mapper;

import static uz.heeyid.myidadapter.mapper.MyidJsonMapper.imageIssue;
import static uz.heeyid.myidadapter.mapper.MyidJsonMapper.parse;
import static uz.heeyid.myidadapter.mapper.MyidJsonMapper.reasonKey;
import static uz.khakimjanovich.contracts.myidadapter.api.model.MyidAdapterStatusCode.SUCCESS;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.heeyid.myidadapter.client.MyidClient.Answer;
import uz.khakimjanovich.contracts.myidadapter.api.model.InplaceJobResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.InplaceJobResultResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.InplaceMedia;
import uz.khakimjanovich.contracts.myidadapter.api.model.InplaceMediaResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.JobResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenSessionResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.Reuid;

/** MyID's Inplace answers as the contract's results. */
@Component
@RequiredArgsConstructor
public class MyidInplaceMapper {

  private final MyidJsonMapper json;

  public OpenSessionResult session(Answer answer) {
    return json.session(answer);
  }

  public InplaceMediaResult media(Answer answer) {
    return new InplaceMediaResult(
        answer.requestId(), SUCCESS, parse(answer, b -> json.read(b, InplaceMedia.class)));
  }

  public JobResult job(Answer answer) {
    return json.job(answer);
  }

  /** Empty while MyID still processes the job (its 202). */
  public Optional<InplaceJobResultResult> jobResult(Answer answer) {
    if (answer.status() == 202) {
      return Optional.empty();
    }
    var result =
        parse(
            answer,
            b -> {
              var data = json.read(b.required("data"), InplaceJobResult.class);
              var reuid = b.path("reuid");
              if (data.getReuid() == null && reuid.isObject()) {
                data.setReuid(json.read(reuid, Reuid.class));
              }
              return data.reasonKey(reasonKey(data.getResultCode()))
                  .imageIssue(imageIssue(data.getResultCode()));
            });
    return Optional.of(new InplaceJobResultResult(answer.requestId(), SUCCESS, result));
  }
}
