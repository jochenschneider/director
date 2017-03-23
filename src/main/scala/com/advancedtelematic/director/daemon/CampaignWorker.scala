package com.advancedtelematic.director.daemon

import akka.Done
import akka.http.scaladsl.util.FastFuture
import com.advancedtelematic.director.data.AdminRequest.SetTarget
import com.advancedtelematic.director.data.DataType.{CustomImage, DeviceId, FileInfo, UpdateId}
import com.advancedtelematic.director.db.{AdminRepositorySupport, SetTargets, Errors => DBErrors}
import com.advancedtelematic.libats.codecs.RefinementError
import com.advancedtelematic.libats.data.Namespace
import com.advancedtelematic.libats.data.RefinedUtils.RefineTry
import com.advancedtelematic.libats.messaging_datatype.Messages.CampaignLaunched
import com.advancedtelematic.libtuf.data.TufDataType.ValidChecksum
import com.advancedtelematic.libtuf.data.TufDataType.HashMethod
import org.slf4j.LoggerFactory

import scala.async.Async._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import slick.driver.MySQLDriver.api._

object CampaignWorker extends AdminRepositorySupport {
  private lazy val _log = LoggerFactory.getLogger(this.getClass)

  def action(cl: CampaignLaunched)(implicit db: Database, ec: ExecutionContext): Future[Done] = {

    val action = async {
      _log.info(s"received event CampaignLaunched ${cl.updateId}")
      val deviceIds = cl.devices.map(deviceId => DeviceId(deviceId)).toSeq
      val image = await(Future.fromTry(getImage(cl)))

      val primEcus = await(Future.sequence(deviceIds.map(adminRepository.getPrimaryEcuForDevice)))

      val devTargets = deviceIds.zip(primEcus.map(prim => SetTarget(Map(prim -> image))))
      await(SetTargets.setTargets(Namespace(cl.namespace), devTargets, Some(UpdateId(cl.updateId))))

      Done
    }

    action.recoverWith {
      case DBErrors.DeviceMissingPrimaryEcu =>
        _log.info(s"Ignoring campaign for ${cl.updateId} since the device is not registered.")
        FastFuture.successful(Done)
      case RefinementError(o, msg) =>
        _log.info(s"Ignoring campaign for ${cl.updateId} since the checksum doesn't match", msg)
        FastFuture.successful(Done)
    }
  }

  private def getImage(cl: CampaignLaunched): Try[CustomImage] =
    cl.pkgChecksum.refineTry[ValidChecksum].map { hash =>
      CustomImage(cl.pkg.mkString, FileInfo(Map(HashMethod.SHA256 -> hash), cl.pkgSize.toInt), cl.pkgUri)
    }
}
