import sbt._
import play.core.PlayVersion.current

object AppDependencies {

  private lazy val apacheHttpVersion = "4.5.8"

  val compile = Seq(
    "com.amazonaws"               %  "aws-java-sdk-s3"            % "1.11.532",
    "uk.gov.hmrc"                 %% "bootstrap-play-26"          % "1.7.0",
    "uk.gov.hmrc"                 %% "simple-reactivemongo"       % "7.30.0-play-26",
    "uk.gov.hmrc"                 %% "play-json-union-formatter"  % "1.10.0-play-26",
    "org.apache.httpcomponents"   %  "httpclient"                 % apacheHttpVersion,
    "org.apache.httpcomponents"   %  "httpmime"                   % apacheHttpVersion
  )

  val scope = "test, it"

  val jettyVersion = "9.4.27.v20200227"

  val test = Seq(
    "com.github.tomakehurst"  %  "wiremock"                 % "2.26.3"        % scope,
    "com.typesafe.play"       %% "play-test"                % current         % scope,
    "org.mockito"             %  "mockito-core"             % "2.26.0"        % scope,
    "org.jsoup"               %  "jsoup"                    % "1.11.3"        % scope,
    "org.pegdown"             %  "pegdown"                  % "1.6.0"         % scope,
    "org.scalatest"           %% "scalatest"                % "3.0.4"         % scope,
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "3.1.3"         % scope,
    "org.scalacheck"          %% "scalacheck"               % "1.14.3"        % scope,
    "uk.gov.hmrc"             %% "hmrctest"                 % "3.9.0-play-26" % scope,
    "uk.gov.hmrc"             %% "http-verbs-test"          % "1.8.0-play-26" % scope,
    "uk.gov.hmrc"             %% "service-integration-test" % "0.9.0-play-26" % scope,
    "uk.gov.hmrc"             %% "reactivemongo-test"       % "4.19.0-play-26" % scope,
    "org.scalaj"              %% "scalaj-http"            % "2.4.2"          % scope,

    //Need to peg this version for wiremock - try removing this on next lib upgrade
    "org.eclipse.jetty"       % "jetty-server"              % jettyVersion    % scope,
    "org.eclipse.jetty"       % "jetty-servlet"             % jettyVersion    % scope
  )

}
