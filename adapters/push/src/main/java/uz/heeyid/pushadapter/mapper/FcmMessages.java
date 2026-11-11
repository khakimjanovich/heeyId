package uz.heeyid.pushadapter.mapper;

import static uz.heeyid.pushadapter.utils.ConstantUtils.APNS_COLLAPSE_ID;
import static uz.heeyid.pushadapter.utils.ConstantUtils.APNS_EXPIRATION;
import static uz.heeyid.pushadapter.utils.ConstantUtils.APNS_PRIORITY;
import static uz.heeyid.pushadapter.utils.ConstantUtils.APNS_PRIORITY_NOW;
import static uz.heeyid.pushadapter.utils.ConstantUtils.DATA_REFERENCE;
import static uz.heeyid.pushadapter.utils.ConstantUtils.DATA_TYPE;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.Message;
import java.time.Instant;
import lombok.experimental.UtilityClass;
import uz.khakimjanovich.contracts.pushadapter.api.model.SendMessageRequest;

/**
 * A request as one FCM message (contract push-adapter-api): data {type, reference} and nothing
 * else; no notification block, so FCM shows nothing by itself on Android. iOS shows the app's own
 * static text of the type, which the app's extension replaces with the text it gets by the
 * reference.
 */
@UtilityClass
public class FcmMessages {

  // setToken: FCM deprecates registration tokens for Firebase Installation IDs (setFid, SDK 9.10).
  // Its token field takes both while the migration runs, so apps on either work. ponytail: move to
  // setFid once every bank app registers an FID; FCM gives no removal date yet.
  @SuppressWarnings("deprecation")
  public static Message of(SendMessageRequest request, Instant now) {
    var type = request.getType().getValue();
    var reference = request.getReference();
    var ttlSeconds = request.getTtlSeconds();
    return Message.builder()
        .setToken(request.getToken())
        .putData(DATA_TYPE, type)
        .putData(DATA_REFERENCE, reference)
        .setAndroidConfig(
            AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setTtl(ttlSeconds * 1000L)
                .setCollapseKey(reference)
                .build())
        .setApnsConfig(
            ApnsConfig.builder()
                .putHeader(APNS_PRIORITY, APNS_PRIORITY_NOW)
                .putHeader(
                    APNS_EXPIRATION, String.valueOf(now.plusSeconds(ttlSeconds).getEpochSecond()))
                .putHeader(APNS_COLLAPSE_ID, reference)
                .setAps(
                    Aps.builder()
                        .setAlert(ApsAlert.builder().setLocalizationKey(type).build())
                        .setMutableContent(true)
                        .build())
                .build())
        .build();
  }
}
