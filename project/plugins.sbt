// The Lagom plugin
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.6.2")
// Set the version dynamically to the git hash
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.0.0")
// Not needed once upgraded to Play 2.7.1
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.6.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.2")
// Test coverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")
// Scala Style
addSbtPlugin("com.beautiful-scala" % "sbt-scalastyle" % "1.4.0")
// Scala auto fix
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.13")
addSbtPlugin("au.com.onegeek" %% "sbt-dotenv" % "2.1.146")

// sbt docker-compose
addSbtPlugin("com.tapad" % "sbt-docker-compose" % "1.0.35")

// Akka GRPC
addSbtPlugin("com.lightbend.akka.grpc" %% "sbt-akka-grpc" % "0.8.4")
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4") // ALPN agent, only required on JVM 8
resolvers += Resolver.bintrayRepo("playframework", "maven")
libraryDependencies += "com.lightbend.play" %% "play-grpc-generators" % "0.8.2"
