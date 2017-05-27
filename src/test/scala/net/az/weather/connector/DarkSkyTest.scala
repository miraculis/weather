package net.az.weather.connector

import net.az.weather.connector.impl.darksky.Period
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import scala.concurrent.duration._
/**
 */

@RunWith(classOf[JUnitRunner])
class DarkSkySuite extends FunSuite {
  test("small Period spits into one Perios")(
   {
     println(System.currentTimeMillis()+" "+(System.currentTimeMillis().millis - 3.days).toMillis)
    assert(Period(System.currentTimeMillis() - 1.day.toMillis, System.currentTimeMillis() - 1.day.toMillis + 100).split().size == 1)})
}
