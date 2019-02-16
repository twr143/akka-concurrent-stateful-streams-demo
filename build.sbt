name := "akka-concurrent-stateful-streams-demo"

version := "1.0"

scalaVersion := "2.12.2"

lazy val akkaVersion = "2.5.13"

resolvers += Resolver.jcenterRepo
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" % "akka-http_2.12" % "10.1.5",
  "com.typesafe.akka" %% "akka-stream" % akkaVersion
)

libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"



libraryDependencies ++= Seq(
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "0.34.1" % Compile,
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "0.34.1" % Provided // required only in compile-time
)


