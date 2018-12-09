package sla

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import scala.annotation.tailrec

class TokenBucket(val rps: Int, val refillTokensPerPeriod: Int, val refillPeriodMs: Long) {

  private val availableTokens = new AtomicInteger(0)
  private val lastRefillTimestamp = new AtomicLong(System.currentTimeMillis)

  def tryConsume(): Boolean = {
    val tokensToAdd = refillNewTokens()
    tryConsumeRec(tokensToAdd)
  }

  @tailrec
  private def tryConsumeRec(tokensToAdd: Int): Boolean = {
    val existingTokens = availableTokens.get
    val newTokens = math.min(rps, existingTokens + tokensToAdd)
    if (newTokens < 1) {
      false
    } else {
      if (availableTokens.compareAndSet(existingTokens, newTokens - 1)) {
        true
      } else {
        tryConsumeRec(tokensToAdd)
      }
    }
  }

  @tailrec
  private def refillNewTokens(): Int = {
    val now = System.currentTimeMillis()
    val lastRefillTimestampMs = lastRefillTimestamp.get

    if (now < lastRefillTimestampMs + refillPeriodMs) {
      0
    } else {
      val periodsElapsed = (now - lastRefillTimestampMs) / refillPeriodMs
      val tokensToAdd = (periodsElapsed * refillTokensPerPeriod).toInt

      if (lastRefillTimestamp.compareAndSet(lastRefillTimestampMs, now)) {
        tokensToAdd
      } else {
        refillNewTokens()
      }
    }
  }

}

/**
  * SLA should be counted by intervals of 1/10 second (i.e. if RPS
  * limit is reached, after 1/10 second ThrottlingService should allow
  * 10% more requests)
  *
  * According to this rule we rely on the refillTokensPerPeriod will be greater than 0,
  * so rps should be not less that 10.
  *
  * As alternative we could make TokenBucket to deal with non integer token size
  *
  */
object TokenBucket {

  private val RefillPeriodMs = 100
  private val OneSecondMs = 1000

  def apply(rps: Int): TokenBucket = {
    if (rps < 10) {
      throw new IllegalArgumentException("rps can't be less than 10")
    }
    val refillTokensPerPeriod = (rps * RefillPeriodMs / OneSecondMs.toDouble).toInt
    new TokenBucket(rps, refillTokensPerPeriod, RefillPeriodMs)
  }
}
