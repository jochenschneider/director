package com.advancedtelematic.director.data

import cats.syntax.either._
import com.advancedtelematic.director.data.DataType._
import com.advancedtelematic.libats.data.RefinedUtils._
import com.advancedtelematic.libats.messaging_datatype.DataType.{EcuSerial, ValidEcuSerial}
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs._
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.TufCodecs.{uriDecoder, uriEncoder, _}
import com.advancedtelematic.libtuf.data.TufDataType.SignedPayload
import io.circe.{Decoder, Encoder, JsonObject, KeyDecoder, KeyEncoder}
import io.circe.syntax._
import com.advancedtelematic.libats.codecs.AkkaCirce._

object Codecs {
  import AdminRequest._
  import DeviceRequest._
  import io.circe.generic.semiauto._

  implicit val keyDecoderHardwareIdentifier: KeyDecoder[HardwareIdentifier] = KeyDecoder.instance { value =>
    value.refineTry[ValidHardwareIdentifier].toOption
  }
  implicit val keyEncoderHardwareIdentifier: KeyEncoder[HardwareIdentifier] = KeyEncoder[String].contramap(_.value)

  implicit val decoderFileInfo: Decoder[FileInfo] = deriveDecoder
  implicit val encoderFileInfo: Encoder[FileInfo] = deriveEncoder

  implicit val decoderImage: Decoder[Image] = deriveDecoder
  implicit val encoderImage: Encoder[Image] = deriveEncoder

  implicit val decoderCustomImage: Decoder[CustomImage] = deriveDecoder
  implicit val encoderCustomImage: Encoder[CustomImage] = deriveEncoder

  /*** Device Request ***/

  implicit val decoderEcuManifest: Decoder[EcuManifest] = deriveDecoder
  implicit val encoderEcuManifest: Encoder[EcuManifest] = deriveEncoder[EcuManifest].mapJsonObject{ obj =>
    JsonObject.fromMap(obj.toMap.filter{
                         case ("custom", value) => !value.isNull
                         case _ => true
                       })
  }

  val legacyDeviceManifestDecoder: Decoder[DeviceManifest] = Decoder.instance { cursor =>
    for {
      ecu <- cursor.downField("primary_ecu_serial").as[EcuSerial]
      manifests <- cursor.downField("ecu_version_manifest").as[Seq[SignedPayload[EcuManifest]]]
    } yield DeviceManifest(ecu, manifests.map(sman => sman.signed.ecu_serial -> sman.asJson).toMap)
  }

  implicit val decoderDeviceManifest: Decoder[DeviceManifest] = deriveDecoder[DeviceManifest] or legacyDeviceManifestDecoder
  implicit val encoderDeviceManifest: Encoder[DeviceManifest] = deriveEncoder

  implicit val decoderDeviceRegistration: Decoder[DeviceRegistration] = deriveDecoder
  implicit val encoderDeviceRegistration: Encoder[DeviceRegistration] = deriveEncoder

  implicit val decoderOperationResult: Decoder[OperationResult] = deriveDecoder
  implicit val encoderOperationResult: Encoder[OperationResult] = deriveEncoder

  implicit val decoderCustomManifest: Decoder[CustomManifest] = deriveDecoder
  implicit val encoderCustomManifest: Encoder[CustomManifest] = deriveEncoder

  /*** Admin Request ***/
  implicit val decoderRegisterEcu: Decoder[RegisterEcu] = deriveDecoder
  implicit val encoderRegisterEcu: Encoder[RegisterEcu] = deriveEncoder[RegisterEcu].mapJsonObject{ obj =>
    JsonObject.fromMap(obj.toMap.filter{
                         case ("hardware_identifier", value) => !value.isNull
                         case _ => true
                       })
  }

  implicit val decoderRegisterDevice: Decoder[RegisterDevice] = deriveDecoder
  implicit val encoderRegisterDevice: Encoder[RegisterDevice] = deriveEncoder

  implicit val decoderSetTarget: Decoder[SetTarget] = deriveDecoder
  implicit val encoderSetTarget: Encoder[SetTarget] = deriveEncoder

  implicit val decoderTargetUpdate: Decoder[TargetUpdate] = deriveDecoder
  implicit val encoderTargetUpdate: Encoder[TargetUpdate] = deriveEncoder

  implicit val decoderTargetUpdateRequest: Decoder[TargetUpdateRequest] = deriveDecoder
  implicit val encoderTargetUpdateRequest: Encoder[TargetUpdateRequest] = deriveEncoder

  implicit val multiTargetUpdateCreatedEncoder: Encoder[MultiTargetUpdateRequest] = deriveEncoder
  implicit val multiTargetUpdateCreatedDecoder: Decoder[MultiTargetUpdateRequest] = deriveDecoder

  implicit val findAffectedRequestEncoder: Encoder[FindAffectedRequest] = deriveEncoder
  implicit val findAffectedRequestDecoder: Decoder[FindAffectedRequest] = deriveDecoder

  implicit val ecuInfoImageEncoder: Encoder[EcuInfoImage] = deriveEncoder
  implicit val ecuInfoImageDecoder: Decoder[EcuInfoImage] = deriveDecoder

  implicit val ecuInfoResponseEncoder: Encoder[EcuInfoResponse] = deriveEncoder
  implicit val ecuInfoResponseDecoder: Decoder[EcuInfoResponse] = deriveDecoder

  implicit val queueResponseEncoder: Encoder[QueueResponse] = deriveEncoder
  implicit val queueResponseDecoder: Decoder[QueueResponse] = deriveDecoder
}

object AkkaHttpUnmarshallingSupport {
  import akka.http.scaladsl.unmarshalling.{FromStringUnmarshaller, Unmarshaller}
  import akka.http.scaladsl.util.FastFuture

  implicit val ecuSerial: FromStringUnmarshaller[EcuSerial] =
    Unmarshaller{ec => x => FastFuture(x.refineTry[ValidEcuSerial])}
}
