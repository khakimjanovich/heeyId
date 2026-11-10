package uz.heeyid.myidadapter.client;

import static uz.heeyid.myidadapter.utils.ConstantUtils.TOKEN_CIPHER;
import static uz.heeyid.myidadapter.utils.ConstantUtils.TOKEN_IV_BYTES;
import static uz.heeyid.myidadapter.utils.ConstantUtils.TOKEN_RENEW_BEFORE;
import static uz.heeyid.myidadapter.utils.ConstantUtils.TOKEN_TAG_BITS;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.function.Supplier;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import uz.heeyid.myidadapter.client.MyidClient.Grant;
import uz.heeyid.myidadapter.property.MyidProperties;

/**
 * The adapter's MyID access tokens in Valkey (SAD §8), shared by every adapter instance. SAD TB-5:
 * a token is in Valkey only encrypted (AES-256-GCM with the adapter's token key, the Valkey key as
 * associated data), and in clear only in the adapter's memory. A token is kept until 5 minutes
 * before MyID's expiry.
 */
@Slf4j
@Component
public class MyidTokens {

  /** A token as MyID gave it. */
  public record Token(String value, Duration expiresIn) {}

  private final StringRedisTemplate valkey;
  private final SecretKey key;
  private final SecureRandom random = new SecureRandom();

  public MyidTokens(StringRedisTemplate valkey, MyidProperties properties) {
    this.valkey = valkey;
    this.key = new SecretKeySpec(Base64.getDecoder().decode(properties.tokenKey()), "AES");
  }

  /** The token for the grant: from Valkey, or a new one from {@code issue}. */
  public String get(Grant grant, Supplier<Token> issue) {
    var name = key(grant);
    var cached = valkey.opsForValue().get(name);
    if (cached != null) {
      var token = open(name, cached);
      if (token != null) {
        return token;
      }
    }
    var token = issue.get();
    var keep = token.expiresIn().minus(TOKEN_RENEW_BEFORE);
    if (keep.isPositive()) {
      valkey.opsForValue().set(name, seal(name, token.value()), keep);
    }
    return token.value();
  }

  /** Forgets a token MyID refused. */
  public void forget(Grant grant) {
    valkey.delete(key(grant));
  }

  static String key(Grant grant) {
    return "myid-adapter:token:" + grant.name().toLowerCase(Locale.ROOT);
  }

  private String seal(String name, String token) {
    try {
      var iv = new byte[TOKEN_IV_BYTES];
      random.nextBytes(iv);
      var cipher = Cipher.getInstance(TOKEN_CIPHER);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TOKEN_TAG_BITS, iv));
      cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
      var sealed = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder()
          .encodeToString(
              ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("cannot encrypt a MyID token", e);
    }
  }

  /** The token, or null when the token key changed since it was written (a new one is taken). */
  private @Nullable String open(String name, String stored) {
    try {
      var bytes = Base64.getDecoder().decode(stored);
      var cipher = Cipher.getInstance(TOKEN_CIPHER);
      cipher.init(
          Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TOKEN_TAG_BITS, bytes, 0, TOKEN_IV_BYTES));
      cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
      return new String(
          cipher.doFinal(bytes, TOKEN_IV_BYTES, bytes.length - TOKEN_IV_BYTES),
          StandardCharsets.UTF_8);
    } catch (AEADBadTagException | IllegalArgumentException e) {
      log.warn("MyID token in Valkey not readable with the current token key | key: {}", name);
      return null;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("cannot decrypt a MyID token", e);
    }
  }
}
