package uz.heeyid.myidadapter.controller;

import static uz.heeyid.myidadapter.client.MyidClient.form;
import static uz.heeyid.myidadapter.client.MyidJson.body;
import static uz.heeyid.myidadapter.client.MyidJson.dataUri;
import static uz.heeyid.myidadapter.client.MyidJson.imageIssue;
import static uz.heeyid.myidadapter.client.MyidJson.reasonKey;
import static uz.khakimjanovich.contracts.myidadapter.api.model.MyidAdapterStatusCode.SUCCESS;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import uz.heeyid.myidadapter.client.MyidClient;
import uz.heeyid.myidadapter.client.MyidClient.Answer;
import uz.heeyid.myidadapter.client.MyidClient.Grant;
import uz.heeyid.myidadapter.client.MyidJson;
import uz.heeyid.myidadapter.config.MyidProperties;
import uz.khakimjanovich.contracts.myidadapter.api.MyidCompareServiceApi;
import uz.khakimjanovich.contracts.myidadapter.api.MyidInplaceServiceApi;
import uz.khakimjanovich.contracts.myidadapter.api.MyidOAuthServiceApi;
import uz.khakimjanovich.contracts.myidadapter.api.MyidSdkServiceApi;
import uz.khakimjanovich.contracts.myidadapter.api.MyidValidationServiceApi;
import uz.khakimjanovich.contracts.myidadapter.api.MyidWebServiceApi;
import uz.khakimjanovich.contracts.myidadapter.api.model.Address;
import uz.khakimjanovich.contracts.myidadapter.api.model.AddressResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.AuthorizationUrl;
import uz.khakimjanovich.contracts.myidadapter.api.model.AuthorizationUrlRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.AuthorizationUrlResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.CloseWebSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.CompareResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.CompareResultByExternalIdRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.CompareResultRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.CompareResultResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.CreateCompareTaskRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.CreateInplaceJobRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.DataScope;
import uz.khakimjanovich.contracts.myidadapter.api.model.EmptyResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.ExchangeAuthorizationCodeRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.GetAddressRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.GetResultRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.InplaceJobResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.InplaceJobResultResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.InplaceMedia;
import uz.khakimjanovich.contracts.myidadapter.api.model.InplaceMediaResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.Job;
import uz.khakimjanovich.contracts.myidadapter.api.model.JobAddress;
import uz.khakimjanovich.contracts.myidadapter.api.model.JobResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenFullProofingSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenInplaceSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenReCheckSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenSessionResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.OpenWebSessionRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.ProofingResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.ProofingResultResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.QrContent;
import uz.khakimjanovich.contracts.myidadapter.api.model.QrContentRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.QrContentResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.RefreshUserDataRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.Reuid;
import uz.khakimjanovich.contracts.myidadapter.api.model.Session;
import uz.khakimjanovich.contracts.myidadapter.api.model.SessionStatus;
import uz.khakimjanovich.contracts.myidadapter.api.model.SessionStatusResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.UploadInplaceMediaRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.UserData;
import uz.khakimjanovich.contracts.myidadapter.api.model.UserDataResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.UserProfile;
import uz.khakimjanovich.contracts.myidadapter.api.model.Verification;
import uz.khakimjanovich.contracts.myidadapter.api.model.VerifyIdentificationRequest;
import uz.khakimjanovich.contracts.myidadapter.api.model.VerifyIdentificationResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.WebSessionResult;
import uz.khakimjanovich.contracts.myidadapter.api.model.WebSessionResultResult;
import uz.khakimjanovich.platform.error.ErrorExceptionStatus;
import uz.khakimjanovich.platform.error.GeneralException;

/** Every operation of myid-adapter-api: one MyID call (two for a code exchange) each. */
@RestController
public class MyidAdapterController
    implements MyidSdkServiceApi,
        MyidInplaceServiceApi,
        MyidOAuthServiceApi,
        MyidWebServiceApi,
        MyidCompareServiceApi,
        MyidValidationServiceApi {

  private final MyidClient myid;
  private final MyidJson json;
  private final MyidProperties properties;

  public MyidAdapterController(MyidClient myid, MyidJson json, MyidProperties properties) {
    this.myid = myid;
    this.json = json;
    this.properties = properties;
  }

  // Mobile SDK, new flow

  @Override
  public ResponseEntity<OpenSessionResult> openFullProofingSession(
      String tenant, OpenFullProofingSessionRequest request) {
    if (request.getPinfl() != null && request.getPassData() != null) {
      throw invalid("pinfl", "give pinfl or passData, not both");
    }
    if (request.getPassData() != null && request.getBirthDate() == null) {
      throw invalid("birthDate", "required with passData");
    }
    var session =
        body(
            "threshold", request.getThreshold(),
            "pinfl", request.getPinfl(),
            "pass_data", request.getPassData(),
            "birth_date", text(request.getBirthDate()),
            "is_resident", request.getIsResident(),
            "phone_number", request.getPhoneNumber());
    return openSdkSession(tenant, session);
  }

  @Override
  public ResponseEntity<OpenSessionResult> openReCheckSession(
      String tenant, OpenReCheckSessionRequest request) {
    var session =
        body(
            "reuid", request.getReuid().toString(),
            "threshold", request.getThreshold(),
            "phone_number", request.getPhoneNumber());
    return openSdkSession(tenant, session);
  }

  private ResponseEntity<OpenSessionResult> openSdkSession(
      String tenant, Map<String, Object> session) {
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT,
            c ->
                c.post()
                    .uri("/api/v2/sdk/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(session));
    return ResponseEntity.ok(
        new OpenSessionResult(
            answer.requestId(), SUCCESS, json.read(answer.body(), Session.class)));
  }

  @Override
  public ResponseEntity<ProofingResultResult> getResult(String tenant, GetResultRequest request) {
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT,
            true,
            c -> c.get().uri("/api/v1/sdk/data?code={code}", request.getCode()));
    var result = json.read(answer.body().path("data"), ProofingResult.class);
    var reuid = answer.body().path("reuid");
    if (result.getReuid() == null && reuid.isObject()) {
      result.setReuid(json.read(reuid, Reuid.class));
    }
    return ResponseEntity.ok(new ProofingResultResult(answer.requestId(), SUCCESS, result));
  }

  @Override
  public ResponseEntity<SessionStatusResult> getSessionStatus(String tenant, UUID sessionId) {
    var answer =
        myid.withToken(
            tenant, Grant.CLIENT, c -> c.get().uri("/api/v1/sdk/sessions/{id}", sessionId));
    var body = ((ObjectNode) answer.body()).deepCopy();
    body.put("status", body.path("status").asString().toUpperCase(Locale.ROOT));
    var status = json.read(body, SessionStatus.class);
    status
        .getAttempts()
        .forEach(
            a ->
                a.reasonKey(a.getReasonCode() == null ? null : reasonKey(a.getReasonCode()))
                    .imageIssue(imageIssue(a.getReasonCode())));
    return ResponseEntity.ok(new SessionStatusResult(answer.requestId(), SUCCESS, status));
  }

  @Override
  public ResponseEntity<AddressResult> getAddressByJob(String tenant, GetAddressRequest request) {
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT,
            c ->
                c.post()
                    .uri("/api/v1/profile/address")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body("job_id", request.getJobId().toString())));
    var address =
        new JobAddress(
            UUID.fromString(answer.body().path("response_id").asString()),
            json.read(answer.body().path("profile").path("address"), Address.class));
    return ResponseEntity.ok(new AddressResult(answer.requestId(), SUCCESS, address));
  }

  // Inplace, new flow

  @Override
  public ResponseEntity<OpenSessionResult> openInplaceSession(
      String tenant, OpenInplaceSessionRequest request) {
    var byDocument =
        (request.getPassData() != null || request.getPinfl() != null)
            && request.getBirthDate() != null;
    if (!byDocument && request.getReuid() == null) {
      throw invalid("passData", "give passData or pinfl with birthDate, or reuid");
    }
    var session =
        body(
            "pass_data", request.getPassData(),
            "pinfl", request.getPinfl(),
            "birth_date", text(request.getBirthDate()),
            "is_resident", request.getIsResident(),
            "reuid", text(request.getReuid()));
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT,
            c ->
                c.post()
                    .uri("/api/v1/inplace/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(session));
    return ResponseEntity.ok(
        new OpenSessionResult(
            answer.requestId(), SUCCESS, json.read(answer.body(), Session.class)));
  }

  @Override
  public ResponseEntity<InplaceMediaResult> uploadInplaceMedia(
      String tenant, UploadInplaceMediaRequest request) {
    myid.tenant(tenant);
    var parts = new LinkedMultiValueMap<String, Object>();
    parts.add("session_id", request.getSessionId().toString());
    parts.add("photo_front", file(request.getPhotoFront(), "photo_front"));
    if (request.getPhotoFocus() != null) {
      parts.add("photo_focus", file(request.getPhotoFocus(), "photo_focus"));
    }
    var answer =
        myid.media(
            c ->
                c.post()
                    .uri("/api/v1/inplace/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts));
    return ResponseEntity.ok(
        new InplaceMediaResult(
            answer.requestId(), SUCCESS, json.read(answer.body(), InplaceMedia.class)));
  }

  @Override
  public ResponseEntity<JobResult> createInplaceJob(
      String tenant, CreateInplaceJobRequest request) {
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT,
            c ->
                c.post()
                    .uri("/api/v1/inplace/jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        body(
                            "session_id", request.getSessionId().toString(),
                            "media_id", request.getMediaId().toString())));
    return job(answer);
  }

  @Override
  public ResponseEntity<InplaceJobResultResult> getInplaceJobResult(String tenant, UUID jobId) {
    var answer =
        myid.withToken(tenant, Grant.CLIENT, c -> c.get().uri("/api/v1/inplace/jobs/{id}", jobId));
    if (answer.status() == 202) {
      return ResponseEntity.accepted().build();
    }
    var result = json.read(answer.body().path("data"), InplaceJobResult.class);
    var reuid = answer.body().path("reuid");
    if (result.getReuid() == null && reuid.isObject()) {
      result.setReuid(json.read(reuid, Reuid.class));
    }
    result
        .reasonKey(reasonKey(result.getResultCode()))
        .imageIssue(imageIssue(result.getResultCode()));
    return ResponseEntity.ok(new InplaceJobResultResult(answer.requestId(), SUCCESS, result));
  }

  // Redirect and QR inplace

  @Override
  public ResponseEntity<AuthorizationUrlResult> getAuthorizationUrl(
      String tenant, AuthorizationUrlRequest request) {
    var url =
        UriComponentsBuilder.fromUriString(properties.baseUrl())
            .path("/api/v1/oauth2/authorization")
            .queryParam("client_id", myid.tenant(tenant).clientId())
            .queryParam("response_type", "code")
            .queryParamIfPresent("redirect_uri", Optional.ofNullable(request.getRedirectUri()))
            .queryParam("scope", scope(request.getScope()))
            .queryParam("method", request.getMethod().getValue())
            .queryParam("state", request.getState())
            .encode()
            .toUriString();
    return ResponseEntity.ok(new AuthorizationUrlResult("", SUCCESS, new AuthorizationUrl(url)));
  }

  @Override
  public ResponseEntity<QrContentResult> getQrContent(String tenant, QrContentRequest request) {
    var qr =
        UriComponentsBuilder.newInstance()
            .queryParam("client_id", myid.tenant(tenant).clientId())
            .queryParam("method", request.getMethod().getValue());
    request.getExtraInfo().forEach(qr::queryParam);
    return ResponseEntity.ok(
        new QrContentResult("", SUCCESS, new QrContent(qr.encode().build().getQuery())));
  }

  @Override
  public ResponseEntity<UserDataResult> exchangeAuthorizationCode(
      String tenant, ExchangeAuthorizationCodeRequest request) {
    var credential = myid.tenant(tenant);
    var token =
        myid.plain(
            true,
            c ->
                c.post()
                    .uri("/api/v1/oauth2/access-token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(
                        form(
                            "grant_type",
                            "authorization_code",
                            "code",
                            request.getCode(),
                            "client_id",
                            credential.clientId(),
                            "client_secret",
                            credential.clientSecret(),
                            "method",
                            request.getMethod() == null ? null : request.getMethod().getValue(),
                            "scope",
                            request.getScope().isEmpty() ? null : scope(request.getScope()))));
    return userData(token);
  }

  @Override
  public ResponseEntity<UserDataResult> refreshUserData(
      String tenant, RefreshUserDataRequest request) {
    var credential = myid.tenant(tenant);
    var token =
        myid.plain(
            true,
            c ->
                c.post()
                    .uri("/api/v1/oauth2/refresh-token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(
                        form(
                            "refresh_token", request.getRefreshToken(),
                            "client_id", credential.clientId())));
    return userData(token);
  }

  /** The person's data with the person's token of a code or a refresh token. */
  private ResponseEntity<UserDataResult> userData(Answer token) {
    var accessToken = token.body().path("access_token").asString();
    var me =
        myid.plain(
            false, c -> c.get().uri("/api/v1/users/me").headers(h -> h.setBearerAuth(accessToken)));
    var profile = ((ObjectNode) me.body().path("profile")).deepCopy();
    var method = profile.remove("authentication_method");
    var data =
        new UserData()
            .jobId(json.jobIdOf(accessToken))
            .scope(stringOrNull(token.body().path("scope")))
            .authenticationMethod(method == null ? null : stringOrNull(method))
            .refreshToken(stringOrNull(token.body().path("refresh_token")))
            .profile(json.read(profile, UserProfile.class));
    return ResponseEntity.ok(new UserDataResult(me.requestId(), SUCCESS, data));
  }

  // WebSDK

  @Override
  public ResponseEntity<OpenSessionResult> openWebSession(
      String tenant, OpenWebSessionRequest request) {
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT_CREDENTIALS,
            c ->
                c.post()
                    .uri("/api/v1/web/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        body(
                            "max_retries", request.getMaxRetries(),
                            "external_id", request.getExternalId().toString(),
                            "ip_address", request.getIpAddress())));
    return ResponseEntity.ok(
        new OpenSessionResult(
            answer.requestId(), SUCCESS, json.read(answer.body(), Session.class)));
  }

  @Override
  public ResponseEntity<EmptyResult> closeWebSession(
      String tenant, UUID sessionId, CloseWebSessionRequest request) {
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT_CREDENTIALS,
            c ->
                c.post()
                    .uri("/api/v1/web/sessions/{id}/client/close", sessionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body("code", request.getCode())));
    return ResponseEntity.ok(new EmptyResult(answer.requestId(), SUCCESS));
  }

  @Override
  public ResponseEntity<WebSessionResultResult> getWebSessionResult(String tenant, UUID sessionId) {
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT_CREDENTIALS,
            c -> c.post().uri("/api/v1/web/sessions/{id}/result", sessionId));
    var result = json.read(answer.body(), WebSessionResult.class);
    result
        .getJobs()
        .forEach(
            j ->
                j.reasonKey(reasonKey(j.getResultCode()))
                    .imageIssue(imageIssue(j.getResultCode())));
    return ResponseEntity.ok(new WebSessionResultResult(answer.requestId(), SUCCESS, result));
  }

  // Backend API without personal data

  @Override
  public ResponseEntity<JobResult> createCompareTask(
      String tenant, CreateCompareTaskRequest request) {
    var clientId = myid.tenant(tenant).clientId();
    var task =
        body(
            "photo_from_camera", Map.of("front", dataUri(request.getPhotoFront())),
            "photo_from_passport",
                request.getPhotoFromPassport() == null
                    ? null
                    : dataUri(request.getPhotoFromPassport()),
            "agreed_on_terms", request.getAgreedOnTerms(),
            "client_id", clientId,
            "liveness", request.getLiveness(),
            "comparison", request.getComparison(),
            "external_id", request.getExternalId(),
            "threshold", request.getThreshold(),
            "pinfl", request.getPinfl(),
            "pass_data", request.getPassData(),
            "phone", request.getPhone());
    var answer =
        myid.withToken(
            tenant,
            Grant.PASSWORD,
            c ->
                c.post()
                    .uri("/api/v1/authentication/compare-photos-task")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(task));
    return job(answer);
  }

  @Override
  public ResponseEntity<CompareResultResult> getCompareResult(
      String tenant, CompareResultRequest request) {
    return compareResult(
        tenant, "/api/v1/authentication/compare-photos-status", body("job_id", request.getJobId()));
  }

  @Override
  public ResponseEntity<CompareResultResult> getCompareResultByExternalId(
      String tenant, CompareResultByExternalIdRequest request) {
    return compareResult(
        tenant,
        "/api/v1/authentication/compare-photos-status-by-external",
        body("external_id", request.getExternalId(), "client_id", myid.tenant(tenant).clientId()));
  }

  private ResponseEntity<CompareResultResult> compareResult(
      String tenant, String path, Map<String, Object> query) {
    var answer =
        myid.withToken(
            tenant,
            Grant.PASSWORD,
            c -> c.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(query));
    var result = json.read(answer.body(), CompareResult.class);
    result
        .reasonKey(reasonKey(result.getResultCode()))
        .imageIssue(imageIssue(result.getResultCode()));
    return ResponseEntity.ok(new CompareResultResult(answer.requestId(), SUCCESS, result));
  }

  // Identification validation (BNPL)

  @Override
  public ResponseEntity<VerifyIdentificationResult> verifyIdentification(
      String tenant, VerifyIdentificationRequest request) {
    // ponytail: MyID's BNPL page names no token; the client_credentials one is assumed until MyID
    // confirms it.
    var answer =
        myid.withToken(
            tenant,
            Grant.CLIENT_CREDENTIALS,
            c ->
                c.post()
                    .uri("/api/v1/bnpl/verify")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(
                        form(
                            "job_id", request.getJobId(),
                            "bank_id", request.getBankId(),
                            "bnpl_id", request.getBnplId(),
                            "pinfl", request.getPinfl())));
    var body = answer.body();
    var status = body.path("status").asInt();
    var verification =
        new Verification(
            body.path("job_id").asString(),
            body.path("bnpl_id").asString(),
            MyidJson.verificationStatus(status),
            status,
            body.path("result").asBoolean());
    return ResponseEntity.ok(
        new VerifyIdentificationResult(answer.requestId(), SUCCESS, verification));
  }

  private ResponseEntity<JobResult> job(Answer answer) {
    return ResponseEntity.ok(
        new JobResult(answer.requestId(), SUCCESS, json.read(answer.body(), Job.class)));
  }

  private static ByteArrayResource file(byte[] photo, String name) {
    return new ByteArrayResource(photo) {
      @Override
      public String getFilename() {
        return name + (photo.length > 1 && photo[0] == (byte) 0x89 ? ".png" : ".jpg");
      }
    };
  }

  private static String scope(Collection<DataScope> scope) {
    return scope.stream().map(DataScope::getValue).collect(Collectors.joining(","));
  }

  private static @Nullable String text(@Nullable Object value) {
    return value == null ? null : value.toString();
  }

  private static @Nullable String stringOrNull(JsonNode node) {
    return node.isString() ? node.asString() : null;
  }

  private static GeneralException invalid(String field, String message) {
    return new GeneralException(ErrorExceptionStatus.VALIDATION_FAILED).subError(field, message);
  }
}
