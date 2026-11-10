package uz.heeyid.myidadapter.utils;

import static uz.heeyid.myidadapter.utils.ConstantUtils.PNG_SIGNATURE_HEX;

import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import lombok.experimental.UtilityClass;

/** A photo as MyID takes it: png or jpeg. */
@UtilityClass
public class MyidPhotoUtils {

  /** A data URI of a photo (MyID's compare API takes them so): png or jpeg by its first bytes. */
  public static String dataUri(byte[] photo) {
    return "data:image/"
        + (isPng(photo) ? "png" : "jpeg")
        + ";base64,"
        + Base64.getEncoder().encodeToString(photo);
  }

  /** A PNG by its 8-byte signature; MyID takes png or jpeg, so anything else is sent as jpeg. */
  public static boolean isPng(byte[] photo) {
    var png = HexFormat.of().parseHex(PNG_SIGNATURE_HEX);
    return photo.length >= png.length && Arrays.equals(photo, 0, png.length, png, 0, png.length);
  }
}
