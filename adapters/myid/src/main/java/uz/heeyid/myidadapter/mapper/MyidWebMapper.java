package uz.heeyid.myidadapter.mapper;

import static uz.heeyid.myidadapter.mapper.MyidJsonMapper.imageIssue;
import static uz.heeyid.myidadapter.mapper.MyidJsonMapper.parse;
import static uz.heeyid.myidadapter.mapper.MyidJsonMapper.reasonKey;
import static uz.khakimjanovich.contracts.myidadapter.api.model.MyidAdapterStatusCode.SUCCESS;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.heeyid.myidadapter.client.MyidClient.Answer;
import uz.khakimjanovich.contracts.myidadapter.api.model.EmptyResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenSessionResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.WebSessionResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.WebSessionResultResult;

/** MyID's WebSDK answers as the contract's results. */
@Component
@RequiredArgsConstructor
public class MyidWebMapper {

  private final MyidJsonMapper json;

  public OpenSessionResult session(Answer answer) {
    return json.session(answer);
  }

  public EmptyResult closed(Answer answer) {
    return new EmptyResult(answer.requestId(), SUCCESS);
  }

  /** Every attempt gets its reason. */
  public WebSessionResultResult result(Answer answer) {
    var result =
        parse(
            answer,
            b -> {
              var read = json.read(b, WebSessionResult.class);
              read.getJobs()
                  .forEach(
                      j ->
                          j.reasonKey(reasonKey(j.getResultCode()))
                              .imageIssue(imageIssue(j.getResultCode())));
              return read;
            });
    return new WebSessionResultResult(answer.requestId(), SUCCESS, result);
  }
}
