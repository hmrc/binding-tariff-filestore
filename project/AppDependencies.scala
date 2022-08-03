import sbt._
import play.core.PlayVersion.current

object AppDependencies {

  private lazy val apacheHttpVersion = "4.5.13"
  private lazy val mongoHmrcVersion = "0.68.0"

  val compile: Seq[ModuleID] = Seq(
    "com.amazonaws"             % "aws-java-sdk-s3"            % "1.11.882",
    "uk.gov.hmrc"               %% "bootstrap-backend-play-28" % "6.3.0",
    "uk.gov.hmrc.mongo"         %% "hmrc-mongo-play-28"        % mongoHmrcVersion,
    "uk.gov.hmrc"               %% "play-json-union-formatter" % "1.15.0-play-28",
    "org.apache.httpcomponents" % "httpclient"                 % apacheHttpVersion,
    "org.apache.httpcomponents" % "httpmime"                   % apacheHttpVersion
  )

  val jettyVersion = "9.4.32.v20200930"

  val test: Seq[ModuleID] = Seq(
    "com.github.tomakehurst" % "wiremock"                  % "2.27.2",
    "com.typesafe.play"      %% "play-test"                % current ,
    "org.mockito"            % "mockito-core"              % "4.6.1",
    "org.jsoup"              % "jsoup"                     % "1.15.1",
    "org.pegdown"            % "pegdown"                   % "1.6.0" ,
    "org.scalatest"          %% "scalatest"                % "3.2.9" ,
    "org.scalatestplus.play" %% "scalatestplus-play"       % "5.1.0" ,
    "org.scalatestplus"      %% "mockito-3-4"              % "3.2.9.0",
    "com.vladsch.flexmark"   % "flexmark-all"              % "0.35.10",
    "org.scalacheck"         %% "scalacheck"               % "1.16.0",
    "uk.gov.hmrc"            %% "service-integration-test" % "1.3.0-play-28",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28"  % mongoHmrcVersion,
    "org.scalaj"             %% "scalaj-http"              % "2.4.2",
    //Need to peg this version for wiremock - try removing this on next lib upgrade
    "org.eclipse.jetty" % "jetty-server"  % jettyVersion,
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion
  ).map(_ % "test, it")

}
