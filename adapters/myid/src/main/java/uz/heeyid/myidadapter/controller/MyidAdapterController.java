package uz.heeyid.myidadapter.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.heeyid.myidadapter.service.MyidCompareService;
import uz.heeyid.myidadapter.service.MyidInplaceService;
import uz.heeyid.myidadapter.service.MyidOAuthService;
import uz.heeyid.myidadapter.service.MyidSdkService;
import uz.heeyid.myidadapter.service.MyidValidationService;
import uz.heeyid.myidadapter.service.MyidWebService;
import uz.khakimjanovich.contracts.myidadapter.api.MyidCompareServiceApi;
import uz.khakimjanovich.contracts.myidadapter.api.MyidInplaceServiceApi;
import uz.khakimjanovich.contracts.myidadapter.api.MyidOAuthServiceApi;
import uz.khakimjanovich.contracts.myidadapter.api.MyidSdkServiceApi;
import uz.khakimjanovich.contracts.myidadapter.api.MyidValidationServiceApi;
import uz.khakimjanovich.contracts.myidadapter.api.MyidWebServiceApi;
import uz.khakimjanovich.contracts.myidadapter.api.model.AddressResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.AuthorizationUrlRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.AuthorizationUrlResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.CloseWebSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.CompareResultByExternalIdRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.CompareResultRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.CompareResultResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.CreateCompareTaskRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.CreateInplaceJobRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.EmptyResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.ExchangeAuthorizationCodeRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.GetAddressRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.GetResultRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.InplaceJobResultResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.InplaceMediaResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.JobResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenFullProofingSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenInplaceSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenReCheckSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenSessionResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenWebSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.ProofingResultResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.QrContentRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.QrContentResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.RefreshUserDataRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.SessionStatusResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.UploadInplaceMediaRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.UserDataResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.VerifyIdentificationRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.VerifyIdentificationResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.WebSessionResultResult;

/** Every operation of myid-adapter-api, each served by the service of its tag. */
@RestController
@RequiredArgsConstructor
public class MyidAdapterController
    implements MyidSdkServiceApi,
        MyidInplaceServiceApi,
        MyidOAuthServiceApi,
        MyidWebServiceApi,
        MyidCompareServiceApi,
        MyidValidationServiceApi {

  private final MyidSdkService sdk;
  private final MyidInplaceService inplace;
  private final MyidOAuthService oauth;
  private final MyidWebService web;
  private final MyidCompareService compare;
  private final MyidValidationService validation;

  @Override
  public ResponseEntity<OpenSessionResult> openFullProofingSession(
      String tenant, OpenFullProofingSessionRequest request) {
    return ResponseEntity.ok(sdk.openFullProofingSession(tenant, request));
  }

  @Override
  public ResponseEntity<OpenSessionResult> openReCheckSession(
      String tenant, OpenReCheckSessionRequest request) {
    return ResponseEntity.ok(sdk.openReCheckSession(tenant, request));
  }

  @Override
  public ResponseEntity<ProofingResultResult> getResult(String tenant, GetResultRequest request) {
    return ResponseEntity.ok(sdk.getResult(tenant, request));
  }

  @Override
  public ResponseEntity<SessionStatusResult> getSessionStatus(String tenant, UUID sessionId) {
    return ResponseEntity.ok(sdk.getSessionStatus(tenant, sessionId));
  }

  @Override
  public ResponseEntity<AddressResult> getAddressByJob(String tenant, GetAddressRequest request) {
    return ResponseEntity.ok(sdk.getAddressByJob(tenant, request));
  }

  @Override
  public ResponseEntity<OpenSessionResult> openInplaceSession(
      String tenant, OpenInplaceSessionRequest request) {
    return ResponseEntity.ok(inplace.openInplaceSession(tenant, request));
  }

  @Override
  public ResponseEntity<InplaceMediaResult> uploadInplaceMedia(
      String tenant, UploadInplaceMediaRequest request) {
    return ResponseEntity.ok(inplace.uploadInplaceMedia(tenant, request));
  }

  @Override
  public ResponseEntity<JobResult> createInplaceJob(
      String tenant, CreateInplaceJobRequest request) {
    return ResponseEntity.ok(inplace.createInplaceJob(tenant, request));
  }

  @Override
  public ResponseEntity<InplaceJobResultResult> getInplaceJobResult(String tenant, UUID jobId) {
    return inplace
        .getInplaceJobResult(tenant, jobId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.accepted().build());
  }

  @Override
  public ResponseEntity<AuthorizationUrlResult> getAuthorizationUrl(
      String tenant, AuthorizationUrlRequest request) {
    return ResponseEntity.ok(oauth.getAuthorizationUrl(tenant, request));
  }

  @Override
  public ResponseEntity<QrContentResult> getQrContent(String tenant, QrContentRequest request) {
    return ResponseEntity.ok(oauth.getQrContent(tenant, request));
  }

  @Override
  public ResponseEntity<UserDataResult> exchangeAuthorizationCode(
      String tenant, ExchangeAuthorizationCodeRequest request) {
    return ResponseEntity.ok(oauth.exchangeAuthorizationCode(tenant, request));
  }

  @Override
  public ResponseEntity<UserDataResult> refreshUserData(
      String tenant, RefreshUserDataRequest request) {
    return ResponseEntity.ok(oauth.refreshUserData(tenant, request));
  }

  @Override
  public ResponseEntity<OpenSessionResult> openWebSession(
      String tenant, OpenWebSessionRequest request) {
    return ResponseEntity.ok(web.openWebSession(tenant, request));
  }

  @Override
  public ResponseEntity<EmptyResult> closeWebSession(
      String tenant, UUID sessionId, CloseWebSessionRequest request) {
    return ResponseEntity.ok(web.closeWebSession(tenant, sessionId, request));
  }

  @Override
  public ResponseEntity<WebSessionResultResult> getWebSessionResult(String tenant, UUID sessionId) {
    return ResponseEntity.ok(web.getWebSessionResult(tenant, sessionId));
  }

  @Override
  public ResponseEntity<JobResult> createCompareTask(
      String tenant, CreateCompareTaskRequest request) {
    return ResponseEntity.ok(compare.createCompareTask(tenant, request));
  }

  @Override
  public ResponseEntity<CompareResultResult> getCompareResult(
      String tenant, CompareResultRequest request) {
    return compare
        .getCompareResult(tenant, request)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.accepted().build());
  }

  @Override
  public ResponseEntity<CompareResultResult> getCompareResultByExternalId(
      String tenant, CompareResultByExternalIdRequest request) {
    return compare
        .getCompareResultByExternalId(tenant, request)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.accepted().build());
  }

  @Override
  public ResponseEntity<VerifyIdentificationResult> verifyIdentification(
      String tenant, VerifyIdentificationRequest request) {
    return ResponseEntity.ok(validation.verifyIdentification(tenant, request));
  }
}
