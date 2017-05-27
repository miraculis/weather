name := "weather"

version := "1.0"

scalaVersion := "2.11.8"

resolvers ++= Seq(
  "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases"
)

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.4.11.2",
    "com.typesafe.akka" %% "akka-stream" % "2.4.11.2",
    "com.typesafe.akka" %% "akka-http-experimental" % "2.4.11.2",
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "2.4.11.2",
    "com.typesafe.akka" %% "akka-slf4j" % "2.4.11.2"
)

// https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"


libraryDependencies ++= Seq(
  "org.scala-saddle" %% "saddle-core" % "1.3.+"
)

libraryDependencies += "junit" % "junit" % "4.10" % "test"
libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.1"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"

