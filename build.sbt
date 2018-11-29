name := "play26-lettuce"

version := "1.0.0"

organization := "com.github.simonedeponti"

licenses += "BSD New" -> url("https://opensource.org/licenses/BSD-3-Clause")

scalaVersion := "2.12.4"
crossScalaVersions := Seq("2.11.11")

libraryDependencies += "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0"
libraryDependencies += "io.lettuce" % "lettuce-core" % "5.0.4.RELEASE"
libraryDependencies += "com.typesafe.play" %% "play" % "2.6.13" % "provided"
libraryDependencies += "com.typesafe.play" %% "play-cache" % "2.6.13" % "provided"
libraryDependencies += "com.typesafe.play" %% "play-test" % "2.6.13" % "provided"
libraryDependencies += "org.specs2" %% "specs2-core" % "3.9.4" % "test"

autoAPIMappings := true
fork := true
javaOptions in test += "-XX:MaxMetaspaceSize=512m"