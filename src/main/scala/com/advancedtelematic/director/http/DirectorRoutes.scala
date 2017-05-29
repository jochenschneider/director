package com.advancedtelematic.director.http

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, _}
import akka.stream.Materializer
import com.advancedtelematic.director.VersionInfo
import com.advancedtelematic.director.client.CoreClient
import com.advancedtelematic.director.manifest.Verifier.Verifier
import com.advancedtelematic.libats.http.ErrorHandler
import com.advancedtelematic.libats.http.DefaultRejectionHandler.rejectionHandler
import com.advancedtelematic.libtuf.data.ClientDataType.ClientKey
import com.advancedtelematic.libtuf.keyserver.KeyserverClient
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext


class DirectorRoutes(verifier: ClientKey => Verifier,
                     coreClient: CoreClient,
                     tufClient: KeyserverClient)
                    (implicit val db: Database,
                     ec: ExecutionContext,
                     sys: ActorSystem,
                     mat: Materializer) extends VersionInfo {
  import Directives._

  val extractNamespace = NamespaceDirectives.defaultNamespaceExtractor

  val routes: Route =
    handleRejections(rejectionHandler) {
      ErrorHandler.handleErrors {
        pathPrefix("api" / "v1") {
          new AdminResource(extractNamespace).route ~
          new DeviceResource(extractNamespace, verifier, coreClient, tufClient).route ~
          new MultiTargetUpdatesResource(extractNamespace).route
        }
      }
    }
}
