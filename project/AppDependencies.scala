import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"             %% "play-reactivemongo"       % "6.2.0",
    "uk.gov.hmrc"             %% "bootstrap-play-25"        % "3.15.0",
    "com.amazonaws"           %  "aws-java-sdk-s3"          % "1.11.290"
  )

  lazy val scope: String = "test,it"

  val test = Seq(
    "org.mockito" % "mockito-core" % "2.23.0" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "org.scalaj" %% "scalaj-http" % "2.4.1" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope,
    "uk.gov.hmrc" %% "hmrctest" % "3.2.0" % scope,
    "uk.gov.hmrc" %% "reactivemongo-test" % "4.1.0-play-25" % scope
  )

}