package mesosphere.marathon
package api

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.CustomHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import mesosphere.marathon.core.deployment.DeploymentPlan

trait BaseHandler {

  implicit def rejectionHandler =
    RejectionHandler.newBuilder()
      .handle(LeaderDirectives.handleNonLeader)
      .handle(ValidationDirectives.handleNonValid)
      .handle {
        case ValidationRejection(msg, _) =>
          complete((InternalServerError, "That wasn't valid! " + msg))
      }
      .handleAll[MethodRejection] { methodRejections =>
        val names = methodRejections.map(_.supported.name)
        complete((MethodNotAllowed, s"Can't do that! Supported: ${names mkString " or "}!"))
      }
      .handleNotFound { complete((NotFound, "Not here!")) }
      .result()

  case class Deployment(plan: DeploymentPlan) extends CustomHeader {
    override def name(): String = "Marathon-Deployment-Id"
    override def value(): String = plan.id
    override def renderInResponses(): Boolean = true
    override def renderInRequests(): Boolean = false
  }
}
