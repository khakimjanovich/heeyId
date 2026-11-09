package uz.heeyid.myidadapter.client;

import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import uz.heeyid.myidadapter.client.MyidClient.Grant;

/**
 * The MyID access tokens of each tenant in Valkey (SAD §8, Valkey "the cached MyID credential"),
 * shared by every adapter instance. A token is kept until 5 minutes before MyID's expiry; the key
 * starts with the tenant (Valkey's key prefix for each tenant).
 */
@Component
public class MyidTokens {

  /** A token is renewed this long before MyID's expiry. */
  static final Duration RENEW_BEFORE = Duration.ofMinutes(5);

  /** A token as MyID gave it. */
  public record Token(String value, Duration expiresIn) {}

  private final StringRedisTemplate valkey;

  public MyidTokens(StringRedisTemplate valkey) {
    this.valkey = valkey;
  }

  /** The tenant's token for the grant: from Valkey, or a new one from {@code issue}. */
  public String get(String tenant, Grant grant, Supplier<Token> issue) {
    var key = key(tenant, grant);
    var cached = valkey.opsForValue().get(key);
    if (cached != null) {
      return cached;
    }
    var token = issue.get();
    var keep = token.expiresIn().minus(RENEW_BEFORE);
    if (keep.isPositive()) {
      valkey.opsForValue().set(key, token.value(), keep);
    }
    return token.value();
  }

  /** Forgets a token MyID refused. */
  public void forget(String tenant, Grant grant) {
    valkey.delete(key(tenant, grant));
  }

  static String key(String tenant, Grant grant) {
    return tenant + ":myid-adapter:token:" + grant.name().toLowerCase(java.util.Locale.ROOT);
  }
}
