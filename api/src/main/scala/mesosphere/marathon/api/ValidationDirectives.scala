package mesosphere.marathon
package api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Directive1, Rejection, Route }
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import com.wix.accord.{ Failure, Success, Validator }
import mesosphere.marathon.api.ValidationDirectives.ValidationFailed
import mesosphere.marathon.api.v2.Validation._

trait ValidationDirectives {

  protected def validEntity[T](um: FromRequestUnmarshaller[T], normalization: Normalization[T], validator: Validator[T]): Directive1[T] = entity(um).flatMap { ent =>
    validator(normalization.normalized(ent)) match {
      case Success => provide(ent)
      case failure: Failure => reject(ValidationFailed(failure))
    }
  }

  protected def validEntityRaml[A, B](um: FromRequestUnmarshaller[A], normalization: Normalization[A], reader: raml.Reads[A, B], validator: Validator[B]): Directive1[B] = entity(um).flatMap { ent =>
    val normalized = reader.read(normalization.normalized(ent))
    validator(normalized) match {
      case Success => provide(normalized)
      case failure: Failure => reject(ValidationFailed(failure))
    }
  }
}

object ValidationDirectives extends PlayJson {
  case class ValidationFailed(failure: Failure) extends Rejection
  def handleNonValid: PartialFunction[Rejection, Route] = {
    case ValidationFailed(failure) => complete(StatusCodes.UnprocessableEntity -> failure)
  }
}
