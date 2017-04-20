package com.advancedtelematic.director.db

import com.advancedtelematic.director.data.DataType.{DeviceId, DeviceUpdateTarget, EcuSerial, FileCacheRequest, Image, UpdateId}
import com.advancedtelematic.director.data.FileCacheRequestStatus
import com.advancedtelematic.director.data.DeviceRequest.EcuManifest
import com.advancedtelematic.libats.data.Namespace
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import slick.driver.MySQLDriver.api._

object DeviceUpdateResult {
  sealed abstract class DeviceUpdateResult

  final case class NoChange() extends DeviceUpdateResult
  final case class UpdatedSuccessfully(timestamp: Int, updateId: Option[UpdateId]) extends DeviceUpdateResult
  final case class UpdatedToWrongTarget(timestamp: Int, targets: Map[EcuSerial, Image], manifest: Map[EcuSerial, Image]) extends DeviceUpdateResult

}

object DeviceUpdate extends AdminRepositorySupport
    with DeviceRepositorySupport
    with FileCacheRequestRepositorySupport {
  import DeviceUpdateResult._

  private lazy val _log = LoggerFactory.getLogger(this.getClass)

  final case class DeviceVersion(current: Int, latestUpdate: Option[Int])

  private def isEqualToUpdate(namespace: Namespace, device: DeviceId, next_version: Int, translatedManifest: Map[EcuSerial, Image])
                             (ifNot : Map[EcuSerial, Image] => DBIO[DeviceUpdateResult])
                             (implicit db: Database, ec: ExecutionContext): DBIO[DeviceUpdateResult] =
    adminRepository.fetchTargetVersionAction(namespace, device, next_version).flatMap { targets =>
      val translatedTargets = targets.mapValues(_.image)
      if (translatedTargets == translatedManifest) {
        for {
          _ <- deviceRepository.updateDeviceVersionAction(device, next_version)
          updateId <- adminRepository.fetchUpdateIdAction(namespace, device, next_version)
        } yield UpdatedSuccessfully(next_version, updateId)
      } else {
        ifNot(translatedTargets)
      }
    }

  def checkAgainstTarget(namespace: Namespace, device: DeviceId, ecuImages: Seq[EcuManifest])
                        (implicit db: Database, ec: ExecutionContext): Future[DeviceUpdateResult] = {
    val translatedManifest = ecuImages.map(ecu => (ecu.ecu_serial, ecu.installed_image)).toMap

    val dbAct = deviceRepository.getCurrentVersionAction(device).flatMap {
      case None => isEqualToUpdate(namespace, device, 1, translatedManifest) { _ =>
        deviceRepository.updateDeviceVersionAction(device, 0).map(_ => NoChange())
      }
      case Some(current_version) =>
        val next_version = current_version + 1
        isEqualToUpdate(namespace, device, next_version, translatedManifest) { translatedTargets =>
          adminRepository.findImagesAction(namespace, device).flatMap { currentStored =>
            if (currentStored.toMap == translatedManifest) {
              DBIO.successful(NoChange())
            } else {
              DBIO.successful(UpdatedToWrongTarget(current_version, translatedTargets, translatedManifest))
            }
          }
        }
    }.flatMap(x => deviceRepository.persistAllAction(namespace, ecuImages).map(_ => x))

    db.run(dbAct.transactionally)
  }

  private [db] def clearTargetsFromAction(namespace: Namespace, device: DeviceId, version: Int)
                                           (implicit db: Database, ec: ExecutionContext): DBIO[Int] = {
    val dbAct = for {
      latestVersion <- adminRepository.getLatestVersion(namespace, device)
      nextTimestampVersion = latestVersion + 1
      fcr = FileCacheRequest(namespace, version, device, FileCacheRequestStatus.PENDING, nextTimestampVersion)
      _ <- deviceRepository.updateDeviceVersionAction(device, nextTimestampVersion)
      _ <- Schema.deviceTargets += DeviceUpdateTarget(device, None, nextTimestampVersion)
      _ <- fileCacheRequestRepository.persistAction(fcr)
    } yield (latestVersion)

    dbAct.transactionally
  }

  def clearTargetsFrom(namespace: Namespace, device: DeviceId, version: Int)
                      (implicit db: Database, ec: ExecutionContext): Future[Int] = db.run{
    clearTargetsFromAction(namespace, device, version)
  }
}
