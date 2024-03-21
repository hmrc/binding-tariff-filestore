import sbt.*

object AppDependencies {

  private lazy val apacheHttpVersion = "4.5.14"

  private lazy val bootstrapPlayVersion = "8.5.0"
  private lazy val hmrcMongoVersion     = "1.8.0"

  val compile: Seq[ModuleID] = Seq(
    "com.amazonaws"                 % "aws-java-sdk-s3"           % "1.12.683",
    "uk.gov.hmrc"                  %% "play-json-union-formatter" % "1.21.0",
    "org.apache.httpcomponents"     % "httpclient"                % apacheHttpVersion,
    "org.apache.httpcomponents"     % "httpmime"                  % apacheHttpVersion,
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30" % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.17.0"
  )

  val test: Seq[ModuleID]    = Seq(
    "org.scalaj"        %% "scalaj-http"             % "2.4.2",
    "org.scalacheck"    %% "scalacheck"              % "1.17.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapPlayVersion
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test
}
