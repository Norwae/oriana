name := "slikka"

version := "1.0"

scalaVersion := "2.11.7"


libraryDependencies ++= {
  val akkaVersion = "2.4.1"
  val slickVersion = "3.1.1"
  Seq(
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,

    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,

    "com.typesafe.slick" %% "slick" % slickVersion,
    "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,

    "org.scalatest" %% "scalatest" % "2.2.6" % "test"
  )
}
