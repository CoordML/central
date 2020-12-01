package central

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import akka.util.Timeout
import central.ExpManager._
import spray.json._

class ExpRoutes(expManager: ActorRef[ExpManager.Command])(implicit val system: ActorSystem[_]) {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import JsonFormats._

  private implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("coordml-central.routes.ask-timeout"))

  def expCreate(request: CreateExpRequest): Future[Either[String, ExpCreated]] =
    expManager.ask(ExpCreate(request, _))

  def getExpOverview(expId: String): Future[Option[ExpOverviewResponse]] =
    expManager.ask(GetExpOverview(expId, _))

  def listExpOverview: Future[ExpOverviewListing] =
    expManager ? ListExpOverview

  def expRoutes: Route = cors() {
    pathPrefix("api") {
      pathPrefix("exp") {
        concat(
          pathPrefix("create") {
            entity(as[CreateExpRequest]) { req =>
              onSuccess(expCreate(req)) {
                case Left(errMsg) => complete((StatusCodes.InternalServerError, errMsg))
                case Right(value) => complete(value)
              }
            }
          },

          pathPrefix("getOverview") {
            parameters("exp_id") { expId =>
              rejectEmptyResponse {
                onSuccess(getExpOverview(expId)) { resp => complete(resp) }
              }
            }
          },

          pathPrefix("listOverview") {
            onSuccess(listExpOverview) { resp =>
              complete(resp)
            }
          }
        )
      }
    }
  }
}
