package sla

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Sla(user: String, rps: Int)

trait SlaService {
  def getSlaByToken(token: String): Future[Sla]
}

class CacheableSlaService(val slaService: SlaService) extends SlaService {

  private val slaCache: TrieMap[String, Future[Sla]] = TrieMap()

  override def getSlaByToken(token: String): Future[Sla] = {

    slaCache.get(token) match {
      case Some(sla) => sla
      case None =>
        val fetchedSla = slaService.getSlaByToken(token)
        slaCache.update(token, fetchedSla)

        fetchedSla.failed.foreach(e => {
          println(s"Failed to get sla: ${e.getMessage}")
          slaCache.remove(token)
        })
        fetchedSla
    }
  }
}