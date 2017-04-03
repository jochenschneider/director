package com.advancedtelematic.director.data

import com.advancedtelematic.libats.data.RefinedUtils._
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.TufCodecs._
import io.circe.{Decoder, Encoder, JsonObject, KeyDecoder, KeyEncoder}
import com.advancedtelematic.libats.codecs.AkkaCirce._

object Codecs {
  import AdminRequest._
  import DataType._
  import DeviceRequest._
  import io.circe.generic.semiauto._

  implicit val keyDecoderEcuSerial: KeyDecoder[EcuSerial] = KeyDecoder.instance { value =>
    value.refineTry[ValidEcuSerial].toOption
  }
  implicit val keyEncoderEcuSerial: KeyEncoder[EcuSerial] = KeyEncoder[String].contramap(_.get)

  implicit val decoderFileInfo: Decoder[FileInfo] = deriveDecoder
  implicit val encoderFileInfo: Encoder[FileInfo] = deriveEncoder

  implicit val decoderImage: Decoder[Image] = deriveDecoder
  implicit val encoderImage: Encoder[Image] = deriveEncoder

  implicit val decoderCustomImage: Decoder[CustomImage] = deriveDecoder[CustomImage]
  implicit val encoderCustomImage: Encoder[CustomImage] = deriveEncoder

  /*** Device Request ***/

  implicit val decoderEcuManifest: Decoder[EcuManifest] = deriveDecoder
  implicit val encoderEcuManifest: Encoder[EcuManifest] = deriveEncoder[EcuManifest].mapJsonObject{ obj =>
    JsonObject.fromMap(obj.toMap.filter{
                         case ("custom", value) => !value.isNull
                         case _ => true
                       })
  }

  implicit val decoderDeviceManifest: Decoder[DeviceManifest] = deriveDecoder
  implicit val encoderDeviceManifest: Encoder[DeviceManifest] = deriveEncoder

  implicit val decoderDeviceRegistration: Decoder[DeviceRegistration] = deriveDecoder
  implicit val encoderDeviceRegistration: Encoder[DeviceRegistration] = deriveEncoder

  implicit val decoderOperationResult: Decoder[OperationResult] = deriveDecoder
  implicit val encoderOperationResult: Encoder[OperationResult] = deriveEncoder

  implicit val decoderCustomManifest: Decoder[CustomManifest] = deriveDecoder
  implicit val encoderCustomManifest: Encoder[CustomManifest] = deriveEncoder

  /*** Admin Request ***/
  implicit val decoderRegisterEcu: Decoder[RegisterEcu] = deriveDecoder
  implicit val encoderRegisterEcu: Encoder[RegisterEcu] = deriveEncoder

  implicit val decoderRegisterDevice: Decoder[RegisterDevice] = deriveDecoder
  implicit val encoderRegisterDevice: Encoder[RegisterDevice] = deriveEncoder

  implicit val decoderSetTarget: Decoder[SetTarget] = deriveDecoder
  implicit val encoderSetTarget: Encoder[SetTarget] = deriveEncoder

  implicit val multiTargetUpdateCreatedEncoder: Encoder[MultiTargetUpdateRequest] = deriveEncoder
  implicit val multiTargetUpdateCreatedDecoder: Decoder[MultiTargetUpdateRequest] = deriveDecoder

  implicit val findAffectedRequestEncoder: Encoder[FindAffetectedRequest] = deriveEncoder
  implicit val findAffectedRequestDecoder: Decoder[FindAffetectedRequest] = deriveDecoder
}
