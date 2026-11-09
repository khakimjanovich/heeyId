package uz.heeyid.myidadapter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * A certificate authority for one test run, made with the JDK's keytool: it issues the server's and
 * the callers' certificates, so no key ever sits in the repository.
 */
// ponytail: copied into each adapter's integration tests (separate Gradle builds); move to the
// platform's security test fixtures when another repository needs it.
final class ThrowawayCa {

  private static final String PASSWORD = "throwaway";

  private final Path dir;
  private final String name;

  /** What one party holds: its certificate and key as PEM files, and both in a PKCS12 store. */
  record Party(Path certificate, Path privateKey, Path keyStore) {}

  ThrowawayCa(Path dir, String name) {
    this.dir = dir;
    this.name = name;
    keytool(
        "-genkeypair",
        "-alias",
        "ca",
        "-keyalg",
        "EC",
        "-groupname",
        "secp256r1",
        "-dname",
        "CN=" + name,
        "-ext",
        "bc:c",
        "-validity",
        "2",
        "-keystore",
        caStore(),
        "-storetype",
        "PKCS12",
        "-storepass",
        PASSWORD);
    keytool(
        "-exportcert",
        "-rfc",
        "-alias",
        "ca",
        "-keystore",
        caStore(),
        "-storepass",
        PASSWORD,
        "-file",
        certificate().toString());
  }

  /** A PKCS12 truststore holding only this CA's certificate, for a server that trusts it. */
  Path trustStore() {
    var store = dir.resolve(name + "-trust.p12");
    if (!Files.exists(store)) {
      keytool(
          "-importcert",
          "-noprompt",
          "-alias",
          "ca",
          "-file",
          certificate().toString(),
          "-keystore",
          store.toString(),
          "-storetype",
          "PKCS12",
          "-storepass",
          PASSWORD);
    }
    return store;
  }

  /** The one password of every store this CA writes. */
  static String password() {
    return PASSWORD;
  }

  /** This CA's certificate, PEM. */
  Path certificate() {
    return dir.resolve(name + "-ca.pem");
  }

  /** A server certificate for localhost. */
  Party server(String party) {
    return issue(party, "san=dns:localhost,ip:127.0.0.1", "eku=serverAuth");
  }

  /** A client certificate, as a calling unit holds one. */
  Party client(String party) {
    return issue(party, "eku=clientAuth", "ku=digitalSignature");
  }

  /** TLS for a caller: its own certificate, and trust in the server's CA. */
  static SSLContext tls(Party caller, Path serverCa) {
    try {
      var keys = KeyStore.getInstance("PKCS12");
      try (InputStream in = Files.newInputStream(caller.keyStore())) {
        keys.load(in, PASSWORD.toCharArray());
      }
      var keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagers.init(keys, PASSWORD.toCharArray());
      var context = SSLContext.getInstance("TLS");
      context.init(keyManagers.getKeyManagers(), trust(serverCa), null);
      return context;
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** TLS for a caller without a certificate of its own. */
  static SSLContext tls(Path serverCa) {
    try {
      var context = SSLContext.getInstance("TLS");
      context.init(null, trust(serverCa), null);
      return context;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static javax.net.ssl.TrustManager[] trust(Path ca) throws GeneralSecurityException {
    try (InputStream in = Files.newInputStream(ca)) {
      var store = KeyStore.getInstance("PKCS12");
      store.load(null, null);
      store.setCertificateEntry(
          "ca", java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(in));
      var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      factory.init(store);
      return factory.getTrustManagers();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private Party issue(String party, String... extensions) {
    var store = store(party).toString();
    var request = dir.resolve(party + ".csr").toString();
    var certificate = dir.resolve(party + ".pem");
    keytool(
        "-genkeypair",
        "-alias",
        party,
        "-keyalg",
        "EC",
        "-groupname",
        "secp256r1",
        "-dname",
        "CN=" + party,
        "-validity",
        "2",
        "-keystore",
        store,
        "-storetype",
        "PKCS12",
        "-storepass",
        PASSWORD);
    keytool(
        "-certreq", "-alias", party, "-keystore", store, "-storepass", PASSWORD, "-file", request);
    var gencert =
        new ArrayList<>(
            List.of(
                "-gencert",
                "-rfc",
                "-alias",
                "ca",
                "-keystore",
                caStore(),
                "-storepass",
                PASSWORD,
                "-infile",
                request,
                "-outfile",
                certificate.toString(),
                "-validity",
                "2"));
    for (var extension : extensions) {
      gencert.add("-ext");
      gencert.add(extension);
    }
    keytool(gencert.toArray(String[]::new));
    keytool(
        "-importcert",
        "-noprompt",
        "-alias",
        "ca",
        "-file",
        certificate().toString(),
        "-keystore",
        store,
        "-storepass",
        PASSWORD);
    keytool(
        "-importcert",
        "-alias",
        party,
        "-file",
        certificate.toString(),
        "-keystore",
        store,
        "-storepass",
        PASSWORD);
    return new Party(certificate, privateKey(party), store(party));
  }

  /** The party's key as PKCS #8 PEM, which keytool can't write. */
  private Path privateKey(String party) {
    try (InputStream in = Files.newInputStream(store(party))) {
      var keys = KeyStore.getInstance("PKCS12");
      keys.load(in, PASSWORD.toCharArray());
      var encoded = keys.getKey(party, PASSWORD.toCharArray()).getEncoded();
      var pem =
          "-----BEGIN PRIVATE KEY-----\n"
              + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                  .encodeToString(encoded)
              + "\n-----END PRIVATE KEY-----\n";
      return Files.writeString(dir.resolve(party + "-key.pem"), pem);
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private String caStore() {
    return dir.resolve(name + "-ca.p12").toString();
  }

  private Path store(String party) {
    return dir.resolve(party + ".p12");
  }

  private static void keytool(String... arguments) {
    var command = new ArrayList<String>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
    command.addAll(List.of(arguments));
    try {
      var process = new ProcessBuilder(command).redirectErrorStream(true).start();
      var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (process.waitFor() != 0) {
        throw new IllegalStateException("keytool " + arguments[0] + " failed: " + output);
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
