package sla.web

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.{OK, TooManyRequests}
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.server.Directives.{complete, get, path}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.HeaderDirectives
import sla.ThrottlingService

trait SlaResource extends HeaderDirectives {
  val throttlingService: ThrottlingService

  val route: Route = {
    path("sla") {
      get {
        optionalHeaderValueByName(Authorization.name) {
          token => {
            val requestAllowed = throttlingService.isRequestAllowed(token)
            if (requestAllowed) {
              complete(HttpResponse(OK))
            } else {
              complete(HttpResponse(TooManyRequests))
            }
          }
        }
      }
    }
  }
}
