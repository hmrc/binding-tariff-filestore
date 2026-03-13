import sbt.*

object AppDependencies {

  private lazy val bootstrapPlayVersion = "10.7.0"
  private lazy val hmrcMongoVersion     = "2.12.0"
  private lazy val amazonAWSSDKVersion  = "2.42.12"

  val compile: Seq[ModuleID] = Seq(
    "software.amazon.awssdk"        % "s3"                        % amazonAWSSDKVersion,
    "software.amazon.awssdk"        % "apache-client"             % amazonAWSSDKVersion,
    "software.amazon.awssdk"        % "auth"                      % amazonAWSSDKVersion,
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30" % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.19.2",
    "org.apache.httpcomponents"     % "httpmime"                  % "4.5.14"
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalacheck"    %% "scalacheck"              % "1.19.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapPlayVersion
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test

}
