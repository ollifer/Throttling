package sla

import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.FlatSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class ThrottlingServiceSpec extends FlatSpec {

  private val slaFixtures: Map[String, Sla] = Map(
    "john" -> Sla("john", 20),
    "jack" -> Sla("jack", 30),
    "bob" -> Sla("bob", 40)
  )

  private val slaDummyService: SlaService = (token: String) => Future {
    Thread.sleep(250)
    slaFixtures(token)
  }

  private val throttlingService = new TokenBucketThrottlingService(15, new CacheableSlaService(slaDummyService))

  "ThrottlingService" should "allow no more (20 + 30 + 40) * 3 requests for 3 users during 3 sec" in {
    import scala.concurrent.duration._

    val totalConsumed = new AtomicInteger(0)
    val start = System.currentTimeMillis

    val users = slaFixtures.keys.toArray

    val tasks = for (i <- 0 until 3) yield Future {
      while ((System.currentTimeMillis - start) <= 3000) {
        if (throttlingService.isRequestAllowed(Some(users(i)))) {
          totalConsumed.incrementAndGet()
        }
      }
    }

    val aggregate = Future.sequence(tasks)
    Await.ready(aggregate, 3 seconds)

    val expectedTotalConsumed = (20 + 30 + 40) * 3
    val toleranceFault = expectedTotalConsumed / 10
    assert((expectedTotalConsumed - totalConsumed.get) <= toleranceFault)
  }

}
