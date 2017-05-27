package net.az.weather.connector.impl.darksky

import java.io.IOException

import akka.event.Logging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Sink, Source, Flow}
import com.typesafe.config.ConfigFactory
import net.az.weather.{WeatherHistory, Weather}
import spray.json._

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.http.scaladsl.server.Directives._
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.StatusCodes._

/**
 * .
 */
case class Period(st: Long, et: Long) {
  def split(): List[Period] = split(st, et)

  private def split(st: Long, et: Long): List[Period] = if (et < st) List()
  else
  if (et > System.currentTimeMillis()) split(st, System.currentTimeMillis())
  else if ((System.currentTimeMillis() - et).millis > 30.days) List()
  else (st to et by 1.day.toMillis).map(t => Period(t, t + 1.hour.toMillis)).toList
}

case class Memoize1[-T, +R](f: T => R) extends (T => R) {
  private[this] val vals = TrieMap.empty[T, R]

  def apply(x: T): R = {
    vals.getOrElseUpdate(x, f(x))
  }
}

case class Memoize3[-T1, -T2, -T3, +R](f: (T1,T2,T3) => R) extends ((T1,T2,T3) => R) {
  private[this] val vals = TrieMap.empty[(T1,T2,T3), R]

  def apply(x: T1, y: T2, z: T3): R = {
    vals.getOrElseUpdate((x,y,z), f(x,y,z))
  }
}

object Api extends App with SprayJsonSupport with DefaultJsonProtocol with Google with DarkSky {
  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()

  implicit val wf = jsonFormat5(Weather)
  implicit val whf = jsonFormat2(WeatherHistory)

  val config = ConfigFactory.load()
  val logger = Logging(system.eventStream, getClass)

  case class Retry1[-T, +R](f: T => Future[R]) extends (T=>Future[R]) {
    def apply(x: T): Future[R] = {
      (1 to 10).foldLeft(f(x))((fut, i) => fut.recoverWith { case ex: Exception => {
        logger.error(ex, s"$i attempt failed")
        f(x)
      }
      })
    }
  }

  case class Retry3[-T1, -T2, -T3, +R](f: (T1,T2,T3) => Future[R]) extends ((T1,T2,T3)=>Future[R]) {
    def apply(x: T1, y: T2, z: T3): Future[R] = {
      (1 to 10).foldLeft(f(x,y,z))((fut, i) => fut.recoverWith { case ex: Exception => {
        logger.error(ex, s"$i attempt failed")
        f(x,y,z)
      }
      })
    }
  }


  lazy val geocodeFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(config.getString("services.geocode-api.host"))

  lazy val dsFlow: Flow[HttpRequest, HttpResponse, Any] = {
    Http().outgoingConnectionHttps(config.getString("services.weather-api.host"))
  }

  lazy val geocodeMemo = Memoize1(Retry1(coordinates))
  lazy val historyMemo = Memoize3(Retry3(history))

  def geocodeApiRequest(request: HttpRequest): Future[HttpResponse] = Source.single(request).via(geocodeFlow).runWith(Sink.head)

  def weatherApiRequest(request: HttpRequest): Future[HttpResponse] = Source.single(request).via(dsFlow).runWith(Sink.head)

  def coordinates(city: String): Future[Either[String, Location]] = {
    geocodeApiRequest(RequestBuilding.Get(s"/${config.getString("services.geocode-api.endpoint")}?address=$city")).flatMap {
      response =>
        response.status match {
          case OK => Unmarshal(response.entity).to[Geocode].map((x) => Right(x.results.head.geometry.location))
          case BadRequest => Future.successful(Left(s"$city: incorrect city"))
          case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
            val error = s"Google geocode API request failed with status code ${response.status} and entity $entity"
            logger.error(error)
            Future.failed(new IOException(error))
          }
        }
    }
  }

  def history(time: Long, lat: Double, lon: Double): Future[List[Weather]] = {
    weatherApiRequest(RequestBuilding.Get(s"/${config.getString("services.weather-api.endpoint")}/$lat,$lon,${time.millis.toSeconds}?exclude=currently,daily,flags")).flatMap {
      response =>
        response.status match {
          case OK => Unmarshal(response.entity).to[Info].map((x) => x.hourly.data.map(
            d => Weather(d.time.seconds.toMillis, d.temperature, d.humidity, d.windSpeed, d.windBearing)))
          case _ => Unmarshal(response.entity).to[String].map { entity => {
            val error = s"Darksky weather API request failed with status code ${response.status} and entity $entity"
            logger.error(error)
            List()
          }
          }
        }
    }
  }

  val routes = {
    logRequestResult("dark-sky-microservice") {
      pathPrefix("dark-sky") {
        get {
          parameters('city.as[String], 'st.as[Long], 'et.as[Long]) {
            (x, y, z) => {
              lazy val h = geocodeMemo(x).map[ToResponseMarshallable] {
                case Right(ll) =>
                Future.traverse(Period(y, z).split)((p) => historyMemo(p.st, ll.lat, ll.lng)).map[ToResponseMarshallable](d => WeatherHistory(x, d.flatten))
                case Left(e) => BadRequest -> e
              }
              complete(h)
            }
          }
        }
      }
    }
  }
  Http().bindAndHandle(routes, config.getString("services.darksky-api.host"), config.getInt("services.darksky-api.port"))
}
