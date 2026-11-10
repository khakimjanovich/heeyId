package uz.heeyid.myidadapter.mapper;

import static uz.heeyid.myidadapter.mapper.MyidJsonMapper.imageIssue;
import static uz.heeyid.myidadapter.mapper.MyidJsonMapper.parse;
import static uz.heeyid.myidadapter.mapper.MyidJsonMapper.reasonKey;
import static uz.khakimjanovich.contracts.myidadapter.api.model.MyidAdapterStatusCode.SUCCESS;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.heeyid.myidadapter.client.MyidClient.Answer;
import uz.khakimjanovich.contracts.myidadapter.api.model.CompareResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.CompareResultResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.JobResult;

/** MyID's compare answers as the contract's results. */
@Component
@RequiredArgsConstructor
public class MyidCompareMapper {

  private final MyidJsonMapper json;

  public JobResult job(Answer answer) {
    return json.job(answer);
  }

  /** Empty while MyID still processes the task (its 202). */
  public Optional<CompareResultResult> result(Answer answer) {
    if (answer.status() == 202) {
      return Optional.empty();
    }
    var result =
        parse(
            answer,
            b -> {
              var read = json.read(b, CompareResult.class);
              return read.reasonKey(reasonKey(read.getResultCode()))
                  .imageIssue(imageIssue(read.getResultCode()));
            });
    return Optional.of(new CompareResultResult(answer.requestId(), SUCCESS, result));
  }
}
