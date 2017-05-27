package net.az.weather.connector.impl.darksky

import spray.json.DefaultJsonProtocol

/**
 */
trait Google extends DefaultJsonProtocol {
  case class Geocode(results: List[Result], status:String)
  case class Result(address_components: List[Address], formatted_address:String, geometry: Geometry, place_id:String, types:List[String])
  case class Address(long_name:String, short_name:String, types:List[String])
  case class Geometry(bounds:Bounds, location:Location, location_type:String, viewport:Bounds)
  case class Location(lat:Double, lng:Double)
  case class Bounds(northeast:Location, southwest:Location)

  implicit val lf = jsonFormat2(Location)
  implicit val bf = jsonFormat2(Bounds)
  implicit val gf = jsonFormat4(Geometry)
  implicit val af = jsonFormat3(Address)
  implicit val rf = jsonFormat5(Result)
  implicit val gof = jsonFormat2(Geocode)
}
