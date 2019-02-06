import sbt._

object AppDependencies {

  val compile = Seq(
    "com.amazonaws"               %  "aws-java-sdk-s3"            % "1.11.490",
    "uk.gov.hmrc"                 %% "bootstrap-play-25"          % "4.8.0",
    "uk.gov.hmrc"                 %% "play-json-union-formatter"  % "1.5.0",
    "uk.gov.hmrc"                 %% "simple-reactivemongo"       % "7.10.0-play-25",
    "org.apache.httpcomponents"   %  "httpclient"                 % "4.5.7",
    "org.apache.httpcomponents"   %  "httpmime"                   % "4.5.7"
  )

  lazy val scope: String = "test,it"

  val test = Seq(
    "com.github.tomakehurst"        %  "wiremock"               % "2.21.0"          % scope,
    "org.mockito"                   %  "mockito-core"           % "2.23.4"          % scope,
    "org.pegdown"                   %  "pegdown"                % "1.6.0"           % scope,
    "org.scalaj"                    %% "scalaj-http"            % "2.4.1"           % scope,
    "org.scalatestplus.play"        %% "scalatestplus-play"     % "2.0.1"           % scope,
    "uk.gov.hmrc"                   %% "hmrctest"               % "3.4.0-play-25"   % scope,
    "uk.gov.hmrc"                   %% "http-verbs-test"        % "1.2.0"           % scope,
    "uk.gov.hmrc"                   %% "reactivemongo-test"     % "4.6.0-play-25"   % scope
  )

}
