import sbt._
import play.core.PlayVersion.current

object AppDependencies {

  private lazy val apacheHttpVersion = "4.5.13"

  val compile = Seq(
    "com.amazonaws"               %  "aws-java-sdk-s3"            % "1.11.882",
    "uk.gov.hmrc"                 %% "bootstrap-backend-play-27"  % "2.25.0",
    "uk.gov.hmrc"                 %% "simple-reactivemongo"       % "7.30.0-play-27",
    "uk.gov.hmrc"                 %% "play-json-union-formatter"  % "1.12.0-play-27",
    "org.apache.httpcomponents"   %  "httpclient"                 % apacheHttpVersion,
    "org.apache.httpcomponents"   %  "httpmime"                   % apacheHttpVersion
  )

  val scope = "test, it"

  val jettyVersion = "9.4.32.v20200930"

  val test = Seq(
    "com.github.tomakehurst"  %  "wiremock"                 % "2.27.2"         % scope,
    "com.typesafe.play"       %% "play-test"                % current          % scope,
    "org.mockito"             %  "mockito-core"             % "2.28.2"         % scope,
    "org.jsoup"               %  "jsoup"                    % "1.13.1"         % scope,
    "org.pegdown"             %  "pegdown"                  % "1.6.0"          % scope,
    "org.scalatest"           %% "scalatest"                % "3.0.9"          % scope,
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "4.0.3"          % scope,
    "org.scalacheck"          %% "scalacheck"               % "1.14.3"         % scope,
    "uk.gov.hmrc"             %% "http-verbs-test"          % "1.8.0-play-27"  % scope,
    "uk.gov.hmrc"             %% "service-integration-test" % "0.12.0-play-27" % scope,
    "uk.gov.hmrc"             %% "reactivemongo-test"       % "4.21.0-play-27" % scope,
    "org.scalaj"              %% "scalaj-http"              % "2.4.2"          % scope,

    //Need to peg this version for wiremock - try removing this on next lib upgrade
    "org.eclipse.jetty"       % "jetty-server"              % jettyVersion    % scope,
    "org.eclipse.jetty"       % "jetty-servlet"             % jettyVersion    % scope
  )

}
