package sla

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.FlatSpec
import sla.web.SlaResource

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps


class SlaPerformanceSpec extends FlatSpec with ScalatestRouteTest {

  private val SlaDummyService: SlaService = (_: String) => Future {
    Sla("", 0)
  }

  private val MaxRpsThrottling: ThrottlingService = new TokenBucketThrottlingService(
    Integer.MAX_VALUE / 100,
    new CacheableSlaService(SlaDummyService)
  )

  private val IdleThrottling: ThrottlingService = new ThrottlingService {
    override val graceRps: Int = 0
    override val slaService: SlaService = SlaDummyService

    override def isRequestAllowed(token: Option[String]): Boolean = true
  }

  "Sla rest service" should "should not have performance degrade caused by throttling service" in {
    val withThrottling = Future {
      measureOkResponseCount(MaxRpsThrottling)
    }

    val withoutThrottling = Future {
      measureOkResponseCount(IdleThrottling)
    }

    val countWithThrottling = Await.result(withThrottling, 3.1 seconds)
    val countWithoutThrottling = Await.result(withoutThrottling, 3.1 seconds)

    println(s"countWithThrottling $countWithThrottling")
    println(s"countWithoutThrottling $countWithoutThrottling")

    println(countWithoutThrottling / countWithThrottling.toDouble)
  }

  /**
    * Measure 'OK' response count of sla rest service of requests without token, using 3 threads
    */
  private def measureOkResponseCount(throttling: ThrottlingService): Int = {
    val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(3))

    val okResponseCount: AtomicInteger = new AtomicInteger(0)
    val start = System.currentTimeMillis

    val route: Route = new SlaResource() {
      override val throttlingService: ThrottlingService = throttling
    }.route

    val tasks: immutable.Seq[Future[Unit]] = for (_ <- 0 until 3) yield Future {

      while ((System.currentTimeMillis - start) <= 3000) {
        Get("/sla") ~> route ~> check {
          if (status.isSuccess()) {
            okResponseCount.incrementAndGet()
          }
        }
      }
    }(ec)

    val aggregate = Future.sequence(tasks)
    Await.ready(aggregate, 3.1 seconds)
    okResponseCount.get()
  }

}
