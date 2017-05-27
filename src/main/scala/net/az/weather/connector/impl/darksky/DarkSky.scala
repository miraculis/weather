package net.az.weather.connector.impl.darksky

import spray.json.DefaultJsonProtocol


case class Info(latitude: Double, longitude: Double, timezone: String, offset:Int, hourly: Hourly)

case class Hourly(summary: String, icon: String, data: List[Item])

case class Item(time: Long, temperature: Double, humidity: Double, windSpeed: Double,
                windBearing: Int, pressure: Double)

/**
  */
trait DarkSky extends DefaultJsonProtocol {
  implicit val itf = jsonFormat6(Item)
  implicit val hf = jsonFormat3(Hourly)
  implicit val iif = jsonFormat5(Info)
}
