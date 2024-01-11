import akka.actor.Actor

class Coordinator() extends Actor {
    def receive: Receive = {
        case SendSpeakingStatus(speakingStatus) =>
            val agent = sender()
            //println(s"Processing message from ${agent.path.name}...")
            agent ! SendSpeakingStatus(speakingStatus)
    }
}