package uz.heeyid.myidadapter.utils;

import java.util.Collection;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
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

  public static GeneralException invalid(String field, String message) {
    return new GeneralException(ErrorExceptionStatus.VALIDATION_FAILED).subError(field, message);
  }
}
