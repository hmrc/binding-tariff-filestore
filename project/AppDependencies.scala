import sbt._

object AppDependencies {

  val compile = Seq(
    "com.amazonaws"           %  "aws-java-sdk-s3"            % "1.11.475",
    "uk.gov.hmrc"             %% "bootstrap-play-25"          % "4.4.0",
    "uk.gov.hmrc"             %% "play-json-union-formatter"  % "1.4.0",
    "uk.gov.hmrc"             %% "simple-reactivemongo"       % "7.7.0-play-25"
  )

  lazy val scope: String = "test,it"

  val test = Seq(
    "com.github.tomakehurst"        %  "wiremock"               % "2.20.0"          % scope,
    "org.mockito"                   %  "mockito-core"           % "2.23.4"          % scope,
    "org.pegdown"                   %  "pegdown"                % "1.6.0"           % scope,
    "org.scalaj"                    %% "scalaj-http"            % "2.4.1"           % scope,
    "org.scalatestplus.play"        %% "scalatestplus-play"     % "2.0.1"           % scope,
    "uk.gov.hmrc"                   %% "hmrctest"               % "3.3.0"           % scope,
    "uk.gov.hmrc"                   %% "http-verbs-test"        % "1.2.0"           % scope,
    "uk.gov.hmrc"                   %% "reactivemongo-test"     % "4.4.0-play-25"   % scope
  )

}
