import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.util.Timeout
import akka.pattern.ask

import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Random, Success}
// Network

// Messages
case class AddAgent(remainingAgentsToAdd: Int)

case class AddToNeighborhood(neighbor: ActorRef)

case class InitialReport(reportData: InitialReportData)

case class FinalReport(reportData: FinalReportData)

case object GetAgentCharacteristics

case object StartAgent

case object ShutdownNetwork

// Actor
class Network(minNumberOfNeighbors: Int, degreeDistributionParameter: Double, stopThreshold: Double,
              distribution: Distribution, monitor: ActorRef) extends Actor {
    // Actor: (number of neighbors, attractiveness score, isStable)
    var agents: Map[ActorRef, (Int, Double, Boolean)] = Map.empty
    var stableAgentsCount: Int = 0
    var totalAttractiveness: Double = 0.0 // Storing the total attractiveness
    var finalReportInitiated = false
    val coordinator: ActorRef = context.actorOf(Props(new Coordinator()), s"Coordinator_${self.path.name}")
    var recordsSaver: ActorRef = ActorRef.noSender


    implicit val timeout: Timeout = Timeout(60.seconds)

    def receive: Receive = empty

    def empty: Receive = {
        case BuildNetwork(numberOfAgents) =>
            context.become(building)
            //println("Building network...")
            recordsSaver = context.actorOf(Props(new RecordsSaver(numberOfAgents)), s"Coordinator_${self.path.name}")
            self ! AddAgent(numberOfAgents)
    }

    def building: Receive = {
        case AddAgent(remainingAgentsToAdd) if remainingAgentsToAdd > 0 =>
            val startingBelief = {
                if (remainingAgentsToAdd % 2 == 0) 0
                else 1
            }
            val newAgent = context.actorOf(Props(new Agent(stopThreshold, distribution, startingBelief, self, 
                coordinator, recordsSaver)), s"Agent$remainingAgentsToAdd")
            agents = agents + (newAgent -> (0, minNumberOfNeighbors * (degreeDistributionParameter - 2), true))

            def pickAgentBasedOnAttractiveness(excludedAgents: Set[ActorRef] = Set.empty): ActorRef = {
                val filteredAgents = agents.filterNot { case (agent, _) => excludedAgents.contains(agent) }

                if (filteredAgents.isEmpty) {
                    if (agents.isEmpty) {
                        throw new RuntimeException("No agents available to pick from!")
                    } else {
                        return agents.keys.toSeq(Random.nextInt(agents.size))
                    }
                }

                val totalFilteredAttractiveness = filteredAgents.values.map(_._2).sum
                val target = Random.nextDouble() * totalFilteredAttractiveness
                var accumulated = 0.0
                for ((agent, (_, attractiveness, _)) <- filteredAgents) {
                    accumulated += attractiveness
                    if (accumulated >= target) return agent
                }
                filteredAgents.keys.head
            }


            var chosenNeighbors = Set.empty[ActorRef]
            for (_ <- 1 to minNumberOfNeighbors) {
                val newNeighbor = pickAgentBasedOnAttractiveness(chosenNeighbors)
                chosenNeighbors += newNeighbor
            }

            chosenNeighbors.foreach { neighbor =>
                newAgent ! AddToNeighborhood(neighbor)
                neighbor ! AddToNeighborhood(newAgent)
                // Update the agents map and total attractiveness
                val (oldQi, oldAttractiveness, _) = agents(neighbor)
                val newQi = oldQi + 1
                val newAttractiveness = minNumberOfNeighbors * (degreeDistributionParameter - 2) + newQi
                agents = agents.updated(neighbor, (newQi, newAttractiveness, true))
                totalAttractiveness += (newAttractiveness - oldAttractiveness)
            }
            agents = agents.updated(newAgent, (
                chosenNeighbors.size,
                (minNumberOfNeighbors * (degreeDistributionParameter - 2)) + chosenNeighbors.size,
                true
            ))
            self ! AddAgent(remainingAgentsToAdd - 1)


        case AddAgent(remainingAgentsToAdd) if remainingAgentsToAdd <= 0 =>
            //println("Finished building network")

            //println("Creating initial report")

            // Request characteristics from all agents and collect their responses
            val futures = agents.keys.map { agent =>
                (agent ? GetAgentCharacteristics).mapTo[SendAgentCharacteristics]
            }

            val collectedFutures = Future.sequence(futures)

            collectedFutures.onComplete {
                case Success(agentCharacteristicsList) =>
                    val agentCharacteristicsVector = agentCharacteristicsList.map(_.agentData).toVector
                    val initialReport = InitialReportData(
                        AgentCharacteristics = agentCharacteristicsVector,
                        density = minNumberOfNeighbors,
                        degreeDistributionParameter = degreeDistributionParameter,
                        stopThreshHold = stopThreshold,
                        distribution = distribution
                    )
                    //println("Finished creating initial report network ready for iteration")

                    context.become(idle)
                    monitor ! InitialReport(initialReport)

                case Failure(e) =>
                // Handle the failure here, e.g., logging the error
            }
    }

    def idle: Receive = {
        case StartNetwork =>
            context.become(running)
            agents.keys.foreach { agent =>
                agent ! StartAgent
            }
    }

    def running: Receive = {

        case Converged(value) =>
            val agent = sender()
            val (i, d, oldValue) = agents(agent)
            //println(stableAgentsCount)
            if (value != oldValue) {
                if (value) stableAgentsCount -= 1
                else stableAgentsCount += 1
                agents = agents.updated(agent, (i, d, value))
            }

            if (stableAgentsCount == agents.size & !finalReportInitiated) {
                // Stop system
                finalReportInitiated = true
                //println("Entered")
                val futures = agents.keys.map { agent =>
                    (agent ? GetAgentCharacteristics).mapTo[SendAgentCharacteristics]
                }

                val collectedFutures = Future.sequence(futures)

                collectedFutures.onComplete {
                    case Success(agentCharacteristicsList) =>
                        val agentCharacteristicsVector = agentCharacteristicsList.map(_.agentData).toVector
                        val finalReport = FinalReportData(
                            AgentCharacteristics = agentCharacteristicsVector
                        )
                        monitor ! FinalReport(finalReport)
                        //println("Finished creating final report shutting down...")
                        context.become(finished)
                        self ! ShutdownNetwork

                    case Failure(e) =>
                    // Handle the failure here, e.g., logging the error
                }
            }


    }

    def finished: Receive = {
        case ShutdownNetwork =>
            agents.keys.map { agent => agent ! PoisonPill }
    }
}
