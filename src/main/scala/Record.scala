import akka.actor.{Actor, ActorRef}

case class AgentStateRecord
(
    confidence: Double,
    speaking: Boolean,
    opinionClimate: Double,
    round: Int,
    numberOfUpdates: Int,
    inFavor: Int,
    against: Int,
    percentageInFavor: Double,
    percentageAgainst: Double
)

case class SendRecords(records: Vector[AgentStateRecord])

class Record(agent: ActorRef) extends Actor {
    var records: Vector[AgentStateRecord]= Vector.empty

    def receive: Receive = {
        case SaveState(size, confidence, speaking, opinionClimate, round, numberOfUpdates, inFavor, against) =>
            val record = AgentStateRecord(
                confidence,
                speaking,
                opinionClimate,
                round,
                numberOfUpdates,
                inFavor,
                against,
                inFavor.toDouble / size.toDouble,
                against.toDouble / size.toDouble
            )
            records = records :+ record

        case GetRecords =>
            agent ! SendRecords(records)
    }
}
