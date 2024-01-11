import akka.actor.{Actor, ActorRef, ActorSystem, DeadLetter, Props}
import akka.util.Timeout
import akka.pattern.ask

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Random, Success}
import scala.concurrent.duration._
import scala.math.{log, E}

// Agent

// Messages
case class SendSpeakingStatus(opinion: Int) // -2 = silent b0, -1 = silent b1, 1 = speaking b1, 2 = speaking b0, 0 = no change
case class SendInitialSpeakingStatus(opinion: Int)

case class SendAgentCharacteristics(agentData: AgentCharacteristicsItem)

case class Converged(value: Boolean)

case class SaveState(size: Int, confidence: Double, speaking: Boolean, opinionClimate: Double, round: Int,
                     numberOfUpdates: Int, inFavor: Int, against: Int)

case object GetRecords

// Actor
class Agent(stopThreshold: Double, distribution: Distribution, startingBelief: Int, network: ActorRef,
            coordinator: ActorRef, recordsSaver: ActorRef) extends Actor {
    var belief: Int = -1
    var beliefExpressionThreshold: Double = -1
    var perceivedOpinionClimate: Double = 0.0
    var confidenceUnbounded: Double = -1
    var confidence: Double = -1
    var neighbors: Vector[ActorRef] = Vector.empty
    var neighborsAgree: Int = 0
    var neighborsDisagree: Int = 0
    var numberOfUpdates: Int = 0
    var round: Int = 0
    val recorder: ActorRef = context.actorOf(Props(new Record(self)), s"Recorder_${self.path.name}")
    implicit val timeout: Timeout = Timeout(60.seconds)

    def calculateOpinionClimate(): Double = {
        val totalNeighbors = neighborsAgree + neighborsDisagree

        if (totalNeighbors == 0)
            return 0.0

        (neighborsAgree - neighborsDisagree).toDouble / totalNeighbors
    }

    def notifyOpinionChange(): Unit = {
        numberOfUpdates += 1
        var speakingStatus = if (belief == 1) 1 else 2
        speakingStatus = if (confidence < beliefExpressionThreshold) speakingStatus * -1 else speakingStatus
        neighbors.foreach { neighbor =>
            neighbor ! SendSpeakingStatus(speakingStatus)
        }
    }

    def receive: Receive = {
        case AddToNeighborhood(neighbor) =>
            neighbors = neighbors :+ neighbor
            if (confidence >= beliefExpressionThreshold)
                val speakingStatus = if (belief == 1) 1 else 2
                neighbor ! SendInitialSpeakingStatus(speakingStatus)

        case SendInitialSpeakingStatus(speakingStatus) =>
            speakingStatus match {
                case 1 => neighborsAgree += 1
                case 2 => neighborsDisagree += 1
            }
            perceivedOpinionClimate = calculateOpinionClimate()

        case StartAgent => self ! SendSpeakingStatus(0)

        case SendSpeakingStatus(speakingStatus) =>
            round += 1
            var debugging = true
            speakingStatus match {
                case -2 if neighborsDisagree != 0 => neighborsDisagree -= 1
                case -1 if neighborsAgree != 0 => neighborsAgree -= 1
                case 1 => neighborsAgree += 1
                case 2 => neighborsDisagree += 1
                case _ => debugging = false
            }

            // Calculate new confidence
            val spoke = confidence >= beliefExpressionThreshold
            val oldConfidence = confidence
            perceivedOpinionClimate = calculateOpinionClimate()
            confidenceUnbounded = math.max(confidenceUnbounded + perceivedOpinionClimate, 0)
            confidence = (2 / (1 + Math.exp(-confidenceUnbounded))) - 1
            //println(s"${self.path.name} confidence change of: ${confidence - oldConfidence}")
            val aboveThreshold = math.abs(confidence - oldConfidence) >= stopThreshold
            val speaks = confidence >= beliefExpressionThreshold
            if (debugging) println(s"${self.path.name} for ${neighborsAgree} against ${neighborsDisagree} confidence ${confidence} Opinion climate: ${perceivedOpinionClimate}")


            //println(self.path.name + ":" + speakingStatus + ", " + neighborsAgree + ", " + neighborsDisagree + ", " + neighbors.size + ", " + aboveThreshold)

            if (speaks != spoke) notifyOpinionChange()
            if (aboveThreshold) self ! SendSpeakingStatus(0)
            network ! Converged(aboveThreshold)
            //println(self.path.name + ": " + aboveThreshold)



        case GetAgentCharacteristics =>
            perceivedOpinionClimate = calculateOpinionClimate()
            val agentData = AgentCharacteristicsItem(
                neighbors.size,
                belief,
                beliefExpressionThreshold,
                confidence,
                confidence >= beliefExpressionThreshold,
                perceivedOpinionClimate,
                round
            )
            sender() ! SendAgentCharacteristics(agentData)


    }

    override def preStart(): Unit = {
        distribution match {
            case Uniform =>
                belief = startingBelief

                def reverseConfidence(c: Double): Double = {
                    if (c == 1.0) {
                        37.42994775023705
                    } else {
                        -math.log(-((c - 1) / (c + 1)))
                    }
                }

                beliefExpressionThreshold = Random.nextDouble()
//                confidence = Random.nextDouble()
//                confidenceUnbounded = reverseConfidence(confidence)
                confidenceUnbounded = Random.nextDouble()
                confidence = (2 / (1 + Math.exp(-confidenceUnbounded))) - 1

            case Normal(mean, std) =>
            // ToDo Implement initialization for the Normal distribution

            case Exponential(lambda) =>
            // ToDo Implement initialization for the Exponential distribution
        }
    }
}
