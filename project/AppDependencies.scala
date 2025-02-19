import sbt.*

object AppDependencies {

  private lazy val bootstrapPlayVersion = "9.3.0"
  private lazy val hmrcMongoVersion     = "2.5.0"

  val compile: Seq[ModuleID] = Seq(
    "com.amazonaws"                 % "aws-java-sdk-s3"           % "1.12.781",
    //  "uk.gov.hmrc"                  %% "play-json-union-formatter" % "1.22.0",
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30" % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.18.2",
    "org.apache.httpcomponents"     % "httpmime"                  % "4.5.14"
  )

  val test: Seq[ModuleID]    = Seq(
    "org.scalacheck"    %% "scalacheck"              % "1.18.1",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapPlayVersion
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test

}
