package uz.heeyid.smsadapter;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * A token issuer for one test run: WireMock serves its OpenID metadata and keys, and tokens are
 * signed here, as a Keycloak realm's would be (azp, aud, realm_access.roles).
 */
final class TestIssuer {

  private final WireMockServer server = new WireMockServer(options().dynamicPort());
  private final RSAKey key;

  TestIssuer() {
    try {
      key = new RSAKeyGenerator(2048).keyID("it").generate();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
    server.start();
    server.stubFor(
        get("/.well-known/openid-configuration")
            .willReturn(
                okJson("{\"issuer\":\"" + uri() + "\",\"jwks_uri\":\"" + uri() + "/certs\"}")));
    server.stubFor(get("/certs").willReturn(okJson(new JWKSet(key.toPublicJWK()).toString())));
  }

  String uri() {
    return server.baseUrl();
  }

  /** A client's token for an audience, with realm roles. */
  String token(String client, String audience, String... roles) {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(uri())
            .subject("service-account-" + client)
            .audience(audience)
            .claim("azp", client)
            .claim("realm_access", Map.of("roles", List.of(roles)))
            .issueTime(new Date())
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .build();
    try {
      var jwt =
          new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("it").build(), claims);
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }
}
