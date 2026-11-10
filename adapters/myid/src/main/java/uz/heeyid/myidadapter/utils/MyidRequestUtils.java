package uz.heeyid.myidadapter.utils;

import java.util.Collection;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import uz.khakimjanovich.contracts.myidadapter.api.model.DataScope;
import uz.khakimjanovich.platform.error.ErrorExceptionStatus;
import uz.khakimjanovich.platform.error.GeneralException;

/** Small helpers the services share. */
@UtilityClass
public class MyidRequestUtils {

  public static String scope(Collection<DataScope> scope) {
    return scope.stream().map(DataScope::getValue).collect(Collectors.joining(","));
  }

  public static @Nullable String text(@Nullable Object value) {
    return value == null ? null : value.toString();
  }

  public static @Nullable String stringOrNull(JsonNode node) {
    return node.isString() ? node.asString() : null;
  }

  public static GeneralException invalid(String field, String message) {
    return new GeneralException(ErrorExceptionStatus.VALIDATION_FAILED).subError(field, message);
  }
}
