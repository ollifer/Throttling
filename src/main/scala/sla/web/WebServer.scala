package sla.web

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import sla._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn

class AkkaWebServer(val throttlingService: ThrottlingService) extends SlaResource {

  implicit val system: ActorSystem = ActorSystem("sla-service")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  var bindingFuture: Future[ServerBinding] = _

  def start(host: String, port: Int): Unit = {
    bindingFuture = Http().bindAndHandle(route, host, port)
    println(s"Server online at http://$host:$port/\nPress RETURN to stop...")
  }

  def stop(): Unit = {
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}

object Main extends App {

  import scala.concurrent.ExecutionContext.Implicits.global

  val config = ConfigFactory.load()
  val graceRps = config.getInt("sla.graceRps")
  val host = config.getString("http.host")
  val port = config.getInt("http.port")

  val slaDummyService: SlaService = (token: String) => Future {
    Thread.sleep(250)
    Sla("john", 12)
  }

  val throttlingService = new TokenBucketThrottlingService(graceRps, new CacheableSlaService(slaDummyService))

  val server = new AkkaWebServer(throttlingService)
  server.start(host, port)
  StdIn.readLine()

  server.stop()
}
