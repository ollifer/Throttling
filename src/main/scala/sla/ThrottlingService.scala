package sla

import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success}

trait ThrottlingService {
  val graceRps: Int
  val slaService: SlaService

  def isRequestAllowed(token: Option[String]): Boolean
}

class TokenBucketThrottlingService(val graceRps: Int,
                                   val slaService: SlaService) extends ThrottlingService {

  private val authorizedUsersLimiters: TrieMap[String, TokenBucket] = TrieMap()
  private val unauthorizedLimiter = TokenBucket(graceRps)

  override def isRequestAllowed(tokenOpt: Option[String]): Boolean =
    tokenOpt match {

      case Some(token) =>
        val slaFuture = slaService.getSlaByToken(token)

        slaFuture.value match {
          case Some(Success(sla)) =>
            val userLimiter = authorizedUsersLimiters.getOrElseUpdate(sla.user, TokenBucket(sla.rps))
            userLimiter.tryConsume()

          //in case sla future has not completed yet or completed with error,
          // treat it as unauthorized user
          case Some(Failure(_)) | None =>
            unauthorizedLimiter.tryConsume()
        }
      case None =>
        unauthorizedLimiter.tryConsume()
    }
}
