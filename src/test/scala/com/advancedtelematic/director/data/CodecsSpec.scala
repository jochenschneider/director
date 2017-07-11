package com.advancedtelematic.director.data

import com.advancedtelematic.director.data.AdminRequest.RegisterEcu
import com.advancedtelematic.director.data.Codecs._
import com.advancedtelematic.director.data.DataType.{FileInfo, Image, ValidHardwareIdentifier}
import com.advancedtelematic.director.data.DeviceRequest.{CustomManifest, DeviceRegistration, EcuManifest, OperationResult}
import com.advancedtelematic.director.util.DirectorSpec
import com.advancedtelematic.libats.data.Namespace
import com.advancedtelematic.libats.data.RefinedUtils._
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, HashMethod, UpdateId, ValidChecksum, ValidEcuSerial}
import com.advancedtelematic.libtuf.data.ClientDataType.ClientKey
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{ClientSignature, KeyType, SignatureMethod, SignedPayload, ValidKeyId, ValidSignature}
import io.circe.{Decoder, Encoder}
import io.circe.parser._
import io.circe.syntax._
import java.time.Instant
import java.util.UUID

import eu.timepit.refined.api.Refined

import scala.reflect.ClassTag

class CodecsSpec extends DirectorSpec {
  def example[T : Decoder : Encoder](sample: String, parsed: T, msg: String = "")(implicit ct: ClassTag[T]): Unit = {
    val name = if (msg == "") {
      ct.runtimeClass.getSimpleName
    } else {
      ct.runtimeClass.getSimpleName + s" ($msg)"
    }

    test(s"$name decodes correctly") {
      decode[T](sample) shouldBe Right(parsed)
    }

    test(s"$name encodes correctly}") {
      parse(sample) shouldBe Right(parsed.asJson)
    }
  }

  {
    val filepath = "/file.img"
    val length = 21
    val sha256 = "303e3a1e1ad2c60dd0d6f4ee377a0a3f4113981191676197e5e8e642faebe4fa"
    val sample = s"""{"filepath":"$filepath", "fileinfo": {"hashes": {"sha256": "$sha256"}, "length": $length} }"""
    val parsed = Image(Refined.unsafeApply(filepath), FileInfo(Map(HashMethod.SHA256 -> sha256.refineTry[ValidChecksum].get), length))

    example(sample, parsed)
  }

  {
    val ecu_manifest_sample: String = """{"signatures": [{"method": "rsassa-pss", "sig": "df043006d4322a386cf85a6761a96bb8c92b2a41f4a4201badb8aae6f6dc17ef930addfa96a3d17f20533a01c158a7a33e406dd8291382a1bbab772bd2fa9804df043006d4322a386cf85a6761a96bb8c92b2a41f4a4201badb8aae6f6dc17ef930addfa96a3d17f20533a01c158a7a33e406dd8291382a1bbab772bd2fa9804", "keyid": "49309f114b857e4b29bfbff1c1c75df59f154fbc45539b2eb30c8a867843b2cb"}], "signed": {"timeserver_time": "2016-10-14T16:06:03Z", "installed_image": {"filepath": "/file2.txt", "fileinfo": {"hashes": {"sha256": "3910b632b105b1e03baa9780fc719db106f2040ebfe473c66710c7addbb2605a"}, "length": 21}}, "previous_timeserver_time": "2016-10-14T16:06:03Z", "ecu_serial": "ecu11111", "attacks_detected": ""}}"""

    val ecu_manifest_sample_parsed: SignedPayload[EcuManifest]
      = SignedPayload(
        signatures = Vector(ClientSignature(
                              method = SignatureMethod.RSASSA_PSS,
                              sig = "df043006d4322a386cf85a6761a96bb8c92b2a41f4a4201badb8aae6f6dc17ef930addfa96a3d17f20533a01c158a7a33e406dd8291382a1bbab772bd2fa9804df043006d4322a386cf85a6761a96bb8c92b2a41f4a4201badb8aae6f6dc17ef930addfa96a3d17f20533a01c158a7a33e406dd8291382a1bbab772bd2fa9804".refineTry[ValidSignature].get,
                              keyid = "49309f114b857e4b29bfbff1c1c75df59f154fbc45539b2eb30c8a867843b2cb".refineTry[ValidKeyId].get)),
        signed = EcuManifest(timeserver_time = Instant.ofEpochSecond(1476461163),
                             installed_image = Image(
                               filepath = Refined.unsafeApply("/file2.txt"),
                               fileinfo = FileInfo(
                                 hashes = Map(HashMethod.SHA256 -> "3910b632b105b1e03baa9780fc719db106f2040ebfe473c66710c7addbb2605a".refineTry[ValidChecksum].get),
                                 length = 21)),
                             previous_timeserver_time = Instant.ofEpochSecond(1476461163),
                             ecu_serial = "ecu11111".refineTry[ValidEcuSerial].get,
                             attacks_detected = ""))

    example(ecu_manifest_sample, ecu_manifest_sample_parsed)
  }

  {
    import com.advancedtelematic.libtuf.crypt.RsaKeyPair
    val ecu_serial = "ecu1111"
    val pubKey =
      """-----BEGIN PUBLIC KEY-----
        |MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1cUg8reXsHhoU4QefD+s
        |1cUCmwlenPuIdg9gS4dWMXtIn0X/22zT7rMSQbE5mJxI7lVT8FZivqqwwNdC2Ami
        |PhICu8GKuXMeK8yvvQaI5y5fcwwWFbt+7UI5d8r7g6p1toqDcSHv/Xe+F7Tcw/UA
        |RaqjkaETMYSHo/ksJGHNIsjnG495ShBVt/nm12CUwtB7VQKrKYs2/JgJPOo8rzTj
        |U23kk0SlNEHP8tRfUdY7hmtETFvvkM0T2mLRFVBg3487/iKFG503GgtKGI7Njsxz
        |df1h5aFqNMXUbr4y+GNZXGBjXzY+udx57O12ujvp9gYd0Uacn1aT2u2dSt13I8V4
        |ZQIDAQAB
        |-----END PUBLIC KEY-----""".stripMargin + "\n"

    val hardwareId = "hw-type"
    val clientKey = s"""{"keytype": "RSA", "keyval": {"public": "${pubKey.replace("\n","\\n")}"}}"""
    val ecus = s"""{"ecu_serial": "$ecu_serial", "hardware_identifier": "$hardwareId", "clientKey": $clientKey}"""

    val sample = s"""{"primary_ecu_serial": "$ecu_serial", "ecus": [$ecus]}"""

    val p_ecu_serial = ecu_serial.refineTry[ValidEcuSerial].get
    val p_pubKey = RsaKeyPair.parsePublic(pubKey).get
    val p_clientKey = ClientKey(KeyType.RSA, p_pubKey)
    val parsed = DeviceRegistration(p_ecu_serial, Seq(RegisterEcu(p_ecu_serial, hardwareId.refineTry[ValidHardwareIdentifier].get, p_clientKey)))

    example(sample, parsed)
  }

  {
    import com.advancedtelematic.libtuf.crypt.RsaKeyPair
    val ecu_serial = "ecu1111"
    val pubKey =
      """-----BEGIN PUBLIC KEY-----
        |MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1cUg8reXsHhoU4QefD+s
        |1cUCmwlenPuIdg9gS4dWMXtIn0X/22zT7rMSQbE5mJxI7lVT8FZivqqwwNdC2Ami
        |PhICu8GKuXMeK8yvvQaI5y5fcwwWFbt+7UI5d8r7g6p1toqDcSHv/Xe+F7Tcw/UA
        |RaqjkaETMYSHo/ksJGHNIsjnG495ShBVt/nm12CUwtB7VQKrKYs2/JgJPOo8rzTj
        |U23kk0SlNEHP8tRfUdY7hmtETFvvkM0T2mLRFVBg3487/iKFG503GgtKGI7Njsxz
        |df1h5aFqNMXUbr4y+GNZXGBjXzY+udx57O12ujvp9gYd0Uacn1aT2u2dSt13I8V4
        |ZQIDAQAB
        |-----END PUBLIC KEY-----""".stripMargin + "\n"

    val clientKey = s"""{"keytype": "RSA", "keyval": {"public": "${pubKey.replace("\n","\\n")}"}}"""
    val sample = s"""{"ecu_serial": "$ecu_serial", "clientKey": $clientKey}"""

    val p_ecu_serial = ecu_serial.refineTry[ValidEcuSerial].get
    val p_pubKey = RsaKeyPair.parsePublic(pubKey).get
    val p_clientKey = ClientKey(KeyType.RSA, p_pubKey)
    val parsed = RegisterEcu(p_ecu_serial, None, p_clientKey)

    example(sample, parsed, "without hardware_identifier")
  }

  {
    val sample: String ="""{"operation_result": {"id": "some-id", "result_code": 0, "result_text": "update successful"}}"""
    val parsed = CustomManifest(
      OperationResult(
        "some-id",
        0,
        "update successful"))
    example(sample, parsed)
  }

  {
    val sample: String = """{"timeserver_time": "2016-10-14T16:06:03Z", "installed_image": {"filepath": "/file2.txt", "fileinfo": {"hashes": {"sha256": "3910b632b105b1e03baa9780fc719db106f2040ebfe473c66710c7addbb2605a"}, "length": 21}}, "previous_timeserver_time": "2016-10-14T16:06:03Z", "ecu_serial": "ecu11111", "attacks_detected": "", "custom": {"operation_result": {"id": "some-id", "result_code": 0, "result_text": "victory"}}}"""

    val parsed: EcuManifest = EcuManifest(
      timeserver_time = Instant.ofEpochSecond(1476461163),
      installed_image = Image(
        filepath = Refined.unsafeApply("/file2.txt"),
        fileinfo = FileInfo(
          hashes = Map(HashMethod.SHA256 -> "3910b632b105b1e03baa9780fc719db106f2040ebfe473c66710c7addbb2605a".refineTry[ValidChecksum].get),
          length = 21)),
      previous_timeserver_time = Instant.ofEpochSecond(1476461163),
      ecu_serial = "ecu11111".refineTry[ValidEcuSerial].get,
      attacks_detected = "",
      custom = Some(CustomManifest(OperationResult(
                                     "some-id",
                                     0,
                                     "victory")).asJson)
    )

    example(sample, parsed, "with custom field")
  }

  {
    import com.advancedtelematic.director.data.Messages.UpdateSpec
    import com.advancedtelematic.director.data.MessageCodecs._
    import com.advancedtelematic.director.data.MessageDataType.{SOTA_Instant, UpdateStatus}
    import java.time.format.DateTimeFormatter

    val sample: String = """{"namespace":"the updateSpec namespace","device":"61d89c4f-b238-4fff-ad7a-b2f0a196230a","packageUuid":"32eb10cc-7431-4945-9b4b-145abb26f69e","status":"Finished","timestamp":"2017-07-03T12:35:32.353Z"}"""

    val parsed: UpdateSpec = UpdateSpec(Namespace("the updateSpec namespace"),
                                        DeviceId(UUID.fromString("61d89c4f-b238-4fff-ad7a-b2f0a196230a")),
                                        UpdateId(UUID.fromString("32eb10cc-7431-4945-9b4b-145abb26f69e")),
                                        UpdateStatus.Finished,
                                        SOTA_Instant(Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2017-07-03T12:35:32.353Z"))))

    example(sample, parsed, "UpdateSpec event")
  }

  {
    import com.advancedtelematic.director.data.Messages.UpdateSpec
    import com.advancedtelematic.director.data.MessageDataType.UpdateStatus

    val sample: String = "\"00000000-0000-0000-0000-000000000000\""
    val parsed: UpdateId = UpdateSpec(Namespace("the updateSpec namespace"), DeviceId.generate, UpdateStatus.Failed).packageUuid

    example(sample, parsed, "UpdateSpec creates zero uuid for packageUuid")
  }
}
