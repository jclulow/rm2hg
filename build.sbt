seq(com.github.retronym.SbtOneJar.oneJarSettings: _*)

organization := "org.sysmgr"

name := "rm2hg"

version := "1.0-SNAPSHOT"

scalaVersion := "2.9.0"

libraryDependencies ++= Seq(
  "org.squeryl" %% "squeryl" % "0.9.4",
  "org.scalatra" %% "scalatra" % "2.0.0",
  "postgresql" % "postgresql" % "9.0-801.jdbc4",
  "org.codehaus.jackson" % "jackson-core-asl" % "1.8.5",
  "org.codehaus.jackson" % "jackson-mapper-asl" % "1.8.5",
  "jaxen" % "jaxen" % "1.1.1"
)

mainClass := Some("org.sysmgr.rm2hg.Main")

