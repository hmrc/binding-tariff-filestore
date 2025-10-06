import sbt.*

object AppDependencies {

  private lazy val bootstrapPlayVersion = "10.1.0"
  private lazy val hmrcMongoVersion     = "2.7.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30"   % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"          % hmrcMongoVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"        % "2.20.0",
    "org.apache.httpcomponents"     % "httpmime"                    % "4.5.14",
    "uk.gov.hmrc.objectstore"      %% "object-store-client-play-30" % "2.4.0"
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalacheck"    %% "scalacheck"              % "1.19.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapPlayVersion
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test

}
