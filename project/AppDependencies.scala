import sbt._
import play.core.PlayVersion.current

object AppDependencies {

  private lazy val apacheHttpVersion = "4.5.14"
  private lazy val mongoHmrcVersion  = "0.74.0"

  val compile: Seq[ModuleID] = Seq(
    "com.amazonaws"                 % "aws-java-sdk-s3"           % "1.12.408",
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-28" % "7.13.0",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-28"        % mongoHmrcVersion,
    "uk.gov.hmrc"                  %% "play-json-union-formatter" % "1.18.0-play-28",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.14.2",
    "org.apache.httpcomponents"     % "httpclient"                % apacheHttpVersion,
    "org.apache.httpcomponents"     % "httpmime"                  % apacheHttpVersion
  )

  val test: Seq[ModuleID]    = Seq(
    "com.github.tomakehurst"  % "wiremock-jre8"           % "2.35.0",
    "com.typesafe.play"      %% "play-test"               % current,
    "org.mockito"             % "mockito-core"            % "4.8.0",
    "org.jsoup"               % "jsoup"                   % "1.15.3",
    "org.scalatest"          %% "scalatest"               % "3.2.15",
    "org.scalatestplus.play" %% "scalatestplus-play"      % "5.1.0",
    "org.scalatestplus"      %% "mockito-3-4"             % "3.2.10.0",
    "com.vladsch.flexmark"    % "flexmark-all"            % "0.62.2",
    "org.scalacheck"         %% "scalacheck"              % "1.17.0",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % mongoHmrcVersion,
    "org.scalaj"             %% "scalaj-http"             % "2.4.2"
  ).map(_ % "test, it")

  def apply(): Seq[ModuleID] = compile ++ test
}
