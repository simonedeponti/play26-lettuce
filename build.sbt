name := "play26-lettuce"

version := "0.1"

scalaVersion := "2.11.8"

libraryDependencies += "org.scala-lang.modules" %% "scala-java8-compat" % "0.7.0"
libraryDependencies += "com.github.romix.akka" %% "akka-kryo-serialization" % "0.5.0"
libraryDependencies += "io.lettuce" % "lettuce-core" % "5.0.0.RELEASE"
libraryDependencies += "com.typesafe.play" %% "play" % "2.6.6" % "provided"
libraryDependencies += "com.typesafe.play" %% "play-cache" % "2.6.6" % "provided"
libraryDependencies += "com.typesafe.play" %% "play-test" % "2.6.6" % "provided"
libraryDependencies += "org.specs2" %% "specs2-core" % "3.9.4" % "test"
