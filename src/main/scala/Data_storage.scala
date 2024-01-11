import NetworkJsonProtocol.{jsonFormat2, jsonFormat3, jsonFormat5, jsonFormat6, jsonFormat9}
import akka.actor.Actor

import java.nio.file.{Files, Paths}
import spray.json.*

// Data storage classes

case class NetworkData
(
    InitialReport: InitialReportData,
    FinalReport: FinalReportData
)

case class InitialReportData
(
    AgentCharacteristics: Vector[AgentCharacteristicsItem],
    density: Int,
    degreeDistributionParameter: Double,
    stopThreshHold: Double,
    distribution: Distribution
)

case class FinalReportData
(
    AgentCharacteristics: Vector[AgentCharacteristicsItem]
)

case class AgentCharacteristicsItem
(
    size: Int,
    belief: Int,
    willingness: Double,
    confidence: Double,
    speaking: Boolean,
    climate: Double,
    round: Int
)

// Json
object NetworkJsonProtocol extends DefaultJsonProtocol {

    // Custom format for Distribution
    implicit object DistributionFormat extends RootJsonFormat[Distribution] {
        override def write(obj: Distribution): JsValue = obj match {
            case Uniform => JsObject("type" -> JsString("Uniform"))
            case Normal(mean, std) => JsObject(
                "type" -> JsString("Normal"),
                "mean" -> JsNumber(mean),
                "std" -> JsNumber(std)
            )
            case Exponential(lambda) => JsObject(
                "type" -> JsString("Exponential"),
                "lambda" -> JsNumber(lambda)
            )
        }

        override def read(json: JsValue): Distribution = json.asJsObject.getFields("type") match {
            case Seq(JsString("Uniform")) => Uniform
            case Seq(JsString("Normal")) =>
                val fields = json.asJsObject.fields
                Normal(fields("mean").convertTo[Double], fields("std").convertTo[Double])
            case Seq(JsString("Exponential")) =>
                val fields = json.asJsObject.fields
                Exponential(fields("lambda").convertTo[Double])
            case _ => deserializationError("Unknown distribution type")
        }
    }

    // Other formats using the automatic generation methods provided by DefaultJsonProtocol
    implicit val agentCharacteristicsItemFormat: RootJsonFormat[AgentCharacteristicsItem] = jsonFormat7(AgentCharacteristicsItem.apply)
    implicit val initialReportDataFormat: RootJsonFormat[InitialReportData] = jsonFormat5(InitialReportData.apply)
    implicit val finalReportDataFormat: RootJsonFormat[FinalReportData] = jsonFormat1(FinalReportData.apply)
    implicit val networkDataFormat: RootJsonFormat[NetworkData] = jsonFormat2(NetworkData.apply)
}


def saveDataToJson(data: Map[String, NetworkData], filePath: String): Unit = {
    import NetworkJsonProtocol._

    // Convert the data to JSON
    val jsonData = data.toJson.prettyPrint

    // Write JSON data to file
    Files.write(Paths.get(filePath), jsonData.getBytes)
}

// Saver
class DataSaver(initialCount: Int) extends Actor {
    var counter: Int = initialCount
    def receive: Receive = {
        case SendNetworksData(data) =>
            counter -= 1
            if (counter == 0) {
                val filePath = s"src/data/${initialCount}_runs_density_1_to_10.json"
                saveDataToJson(data, filePath)
                println(s"Data saved to $filePath")
            }
    }
}
