import akka.actor.Actor


class RecordsSaver(initialCount: Int) extends Actor {
    def receive: Receive = {
        case SendRecords(records) =>

    }
}