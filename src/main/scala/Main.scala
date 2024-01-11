import akka.actor.{ActorSystem, Props}

def caseClassToString(cc: Product): Unit = {
    val className = cc.getClass.getSimpleName
    val fields = cc.getClass.getDeclaredFields

    val values = fields.map { field =>
        field.setAccessible(true)
        val name = field.getName
        val value = field.get(cc)
        s"$name = $value"
    }

    println(s"$className(\n  ${values.mkString(",\n  ")}\n)")
}

// Distributions
sealed trait Distribution

case object Uniform extends Distribution

case class Normal(mean: Double, std: Double) extends Distribution

case class Exponential(lambda: Double) extends Distribution


// Global control
case class CreateNetwork
(
    name: String,
    numberOfAgents: Int,
    minNumberOfNeighbors: Int,
    stopThreshold: Double,
    degreeDistributionParameter: Double,
    distribution: Distribution
)


object Main extends App {
    val numOfNetworks = 1
    val system = ActorSystem("Async")
    val monitor = system.actorOf(Props(new Monitor(Debug, numOfNetworks)), "Monitor")
    val maxDensity = 10
    //val listenerActor = system.actorOf(Props(new DeadLetterListener), "Listener")
//    for (i <- 1 to maxDensity) {
//        for (j <- 1 to numOfNetworks) {
//            monitor ! CreateNetwork(s"Network$j", 1000, i, 0.001, 2.5, Uniform)
//        }
//    }


    for (i <- 1 to numOfNetworks) {
        monitor ! CreateNetwork(s"Network$i", 10, 4, 0.001, 2.5, Uniform)
    }
    //system.terminate()
}