// The Lagom plugin
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.6.1")
// Test coverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")
// Scala Style
addSbtPlugin("com.beautiful-scala" % "sbt-scalastyle" % "1.4.0")
// .env files
addSbtPlugin("au.com.onegeek" %% "sbt-dotenv" % "2.1.169")

// Akka GRPC
addSbtPlugin("com.lightbend.akka.grpc" %% "sbt-akka-grpc" % "0.8.4")
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4") // ALPN agent, only required on JVM 8
resolvers += Resolver.bintrayRepo("playframework", "maven")
libraryDependencies += "com.lightbend.play" %% "play-grpc-generators" % "0.8.2"
