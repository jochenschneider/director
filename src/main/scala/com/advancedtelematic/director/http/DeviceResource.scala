package com.advancedtelematic.director.http

import akka.http.scaladsl.server.Directive1
import akka.stream.Materializer
import com.advancedtelematic.director.client.CoreClient
import com.advancedtelematic.director.data.Codecs._
import com.advancedtelematic.director.data.DataType.{DeviceId, Namespace}
import com.advancedtelematic.director.data.DeviceRequest.{DeviceManifest, DeviceRegistration}
import com.advancedtelematic.director.db.{DeviceRepositorySupport, DeviceUpdate,
  FileCacheRepositorySupport, RootFilesRepositorySupport}
import com.advancedtelematic.director.manifest.Verifier.Verifier
import com.advancedtelematic.director.manifest.Verify
import com.advancedtelematic.libtuf.data.ClientDataType.ClientKey
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.SignedPayload
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import org.slf4j.LoggerFactory
import scala.async.Async._
import scala.concurrent.{ExecutionContext, Future}
import slick.driver.MySQLDriver.api._

class DeviceResource(extractNamespace: Directive1[Namespace],
                     verifier: ClientKey => Verifier,
                     coreClient: CoreClient)
                    (implicit db: Database, ec: ExecutionContext, mat: Materializer)
    extends DeviceRepositorySupport
    with FileCacheRepositorySupport
    with RootFilesRepositorySupport {
  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.server.Route

  private lazy val _log = LoggerFactory.getLogger(this.getClass)

  def setDeviceManifest(namespace: Namespace, device: DeviceId, signedDevMan: SignedPayload[DeviceManifest]): Route = {
    val action = async {
      val ecus = await(deviceRepository.findEcus(namespace, device))
      val ecuImages = await(Future.fromTry(Verify.deviceManifest(ecus, verifier, signedDevMan)))

      await(DeviceUpdate.setEcus(coreClient)(namespace, device, ecuImages))
    }
    complete(action)
  }

  def fetchTargets(ns: Namespace, device: DeviceId): Route = {
    val action = deviceRepository.getNextVersion(device).flatMap { version =>
      fileCacheRepository.fetchTarget(device, version)
    }
    complete(action)
  }

  def fetchSnapshot(ns: Namespace, device: DeviceId): Route = {
    val action = deviceRepository.getNextVersion(device).flatMap { version =>
      fileCacheRepository.fetchSnapshot(device, version)
    }
    complete(action)
  }

  def fetchTimestamp(ns: Namespace, device: DeviceId): Route = {
    val action = deviceRepository.getNextVersion(device).flatMap { version =>
      fileCacheRepository.fetchTimestamp(device, version)
    }
    complete(action)
  }

  def fetchRoot(ns: Namespace): Route = {
    complete(rootFilesRepository.find(ns))
  }

  def registerDevice(ns: Namespace, device: DeviceId, regDev: DeviceRegistration): Route = {
    val primEcu = regDev.primary_ecu_serial

    regDev.ecus.find(_.ecu_serial == primEcu) match {
      case None => complete(Errors.PrimaryIsNotListedForDevice)
      case Some(_) => complete(deviceRepository.create(ns, device, primEcu, regDev.ecus))
    }
  }

  val route = extractNamespace { ns =>
    pathPrefix("device" / DeviceId.Path) { device =>
      post {
        (path("ecus") & entity(as[DeviceRegistration])) { regDev =>
          registerDevice(ns, device, regDev)
        }
      } ~
      put {
        (path("manifest") & entity(as[SignedPayload[DeviceManifest]])) { devMan =>
          setDeviceManifest(ns, device, devMan)
        }
      } ~
      get {
        path("root.json") {
          fetchRoot(ns)
        } ~
        path("targets.json") {
          fetchTargets(ns, device)
        } ~
        path("snapshots.json") {
          fetchSnapshot(ns, device)
        } ~
        path("timestamp.json") {
          fetchTimestamp(ns, device)
        }
      }
    }
  }
}
