package net.az.weather

/**
 *
 */
case class WeatherHistory(city:String, hourly:List[Weather])
case class Weather(time:Long, t:Double, h:Double, w:Double, wd: Double)
case class StatItem(mu:Double, sigma:Double, min:Double, max:Double)
case class Stat(t: StatItem, h: StatItem, w: StatItem, wd: StatItem)
case class CityReport(city:String, hourly:List[Weather], stat:Stat)
case class WeatherReport(data:List[CityReport])

