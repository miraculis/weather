package net.az.weather.rest

import java.io.IOException

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{StatusCodes, StatusCode, HttpResponse, HttpRequest}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source, Flow}
import com.typesafe.config.ConfigFactory
import net.az.weather._
import spray.json.DefaultJsonProtocol
import scala.concurrent.Future
import org.saddle.seqToVec

/**
 * .
 */
object Rest extends App with SprayJsonSupport with DefaultJsonProtocol {
  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()

  implicit val wf = jsonFormat5(Weather)
  implicit val sif = jsonFormat4(StatItem)
  implicit val sf = jsonFormat4(Stat)
  implicit val cf = jsonFormat3(CityReport)
  implicit val wrf = jsonFormat1(WeatherReport)
  implicit val whf = jsonFormat2(WeatherHistory)

  val config = ConfigFactory.load()
  val logger = Logging(system, getClass)

  lazy val historyFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(config.getString("services.darksky-api.host"), config.getInt("services.darksky-api.port"))

  def historyApiRequest(request: HttpRequest): Future[HttpResponse] = Source.single(request).via(historyFlow).runWith(Sink.head)

  def report(cities: Iterable[String], st:Long, et:Long): Future[WeatherReport] =
    Future.traverse(cities.toList)((city) =>
      historyApiRequest(RequestBuilding.Get(s"/${config.getString("services.darksky-api.endpoint")}?city=$city&st=$st&et=$et")).flatMap {
        response =>
          response.status match {
            case OK => Unmarshal(response.entity).to[WeatherHistory].map((x) => CityReport(city, x.hourly, stat(x)))
            case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
              val error = s"Darksky service request failed with status code ${response.status} and entity $entity"
              logger.error(error)
              Future.failed(new IOException(error))
            }
          }
      }
    ).map(WeatherReport)

  def stat(h: WeatherHistory): Stat = {
    val tv = h.hourly.map(_.t).toVec
    val hv = h.hourly.map(_.h).toVec
    val wv = h.hourly.map(_.w).toVec
    val wdv = h.hourly.map(_.wd).toVec
    Stat(StatItem(tv.mean, tv.stdev, tv.min match {case Some(x) => x case None => 0}, tv.max match {case Some(x) => x case None => 0}),
      StatItem(hv.mean, hv.stdev, hv.min match {case Some(x) => x case None => 0}, hv.max match {case Some(x) => x case None => 0}),
      StatItem(wv.mean, wv.stdev, wv.min match {case Some(x) => x case None => 0}, wv.max match {case Some(x) => x case None => 0}),
      StatItem(wdv.mean, wdv.stdev, wdv.min match {case Some(x) => x case None => 0}, wdv.max match {case Some(x) => x case None => 0}))
  }

  val routes = logRequestResult("frontend-api-service") {
    pathPrefix("api") {
      get {
        parameters('city.*, 'st.as[Long], 'et.as[Long]) {
          (x, y, z) => complete(report(x, y, z))
        }
      }
    }
  }

  Http().bindAndHandle(routes, config.getString("services.frontend-api.host"), config.getInt("services.frontend-api.port"))
}
