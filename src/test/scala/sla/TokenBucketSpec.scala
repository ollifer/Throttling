package sla

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

import org.scalatest.FlatSpec

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

class TokenBucketSpec extends FlatSpec {

  "TokenBucket" should "allow 6 requests per 3 second" in {
    val tokenBucket = new TokenBucket(2, 2, 1000)

    val start = System.currentTimeMillis
    var consumed = 0

    while (System.currentTimeMillis - start <= 3000) {
      if (tokenBucket.tryConsume()) {
        consumed += 1
      }
    }
    assert(consumed == 6)
  }

  "TokenBucket" should "allow 6 requests per 3 second concurrently" in {
    import scala.concurrent.duration._

    val tokenBucket = new TokenBucket(2, 2, 1000)
    implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(8))

    val consumed = new AtomicLong(0)
    val start = System.currentTimeMillis

    val tasks = for (_ <- 1 to 8) yield Future {

      while ((System.currentTimeMillis - start) <= 3000) {
        if (tokenBucket.tryConsume()) {
          consumed.incrementAndGet
        }
      }
    }

    val aggregate = Future.sequence(tasks)
    Await.ready(aggregate, 3 seconds)
    assert(consumed.get == 6)
  }

}
