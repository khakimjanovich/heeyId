package uz.heeyid.myidadapter.utils;

import java.util.Set;
import lombok.experimental.UtilityClass;

/** MyID's outcome codes (docs.myid.uz) by the contract's ReasonKey. */
@UtilityClass
public class MyidReasonCodeConstants {

  /** POOR_IMAGE: 14, 17, 20-33 (33: the device sent no infrared image). */
  public static final Set<Integer> MYID_POOR_IMAGE_CODES =
      Set.of(14, 17, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33);

  /** SERVICE_UNAVAILABLE: MyID or a source it uses could not process the request. */
  public static final Set<Integer> MYID_SERVICE_UNAVAILABLE_CODES =
      Set.of(5, 11, 12, 13, 15, 16, 18, 19, 38, 101, 103);
}
