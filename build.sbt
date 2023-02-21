import play.sbt.PlayImport.PlayKeys._
import uk.gov.hmrc.DefaultBuildSettings._

val appName = "binding-tariff-filestore"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  // To resolve a bug with version 2.x.x of the scoverage plugin - https://github.com/sbt/sbt/issues/6997
  .settings(libraryDependencySchemes ++= Seq("org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always))
  .settings(defaultSettings(): _*)
  .settings(majorVersion := 0)
  .settings(
    scalaVersion := "2.13.10",
    targetJvm := "jvm-1.8",
    playDefaultPort := 9583,
    scalacOptions ++= Seq(
      "-feature",
      "-Wconf:src=routes/.*:s"
    ),
    libraryDependencies ++= AppDependencies(),
    Test / fork := true,
    retrieveManaged := true
  )
  .settings(inConfig(Test)(Defaults.testSettings): _*)
  .settings(
    Test / unmanagedSourceDirectories := Seq(
      (Test / baseDirectory).value / "test/unit",
      (Test / baseDirectory).value / "test/util"
    ),
    Test / resourceDirectory := baseDirectory.value / "test" / "resources",
    addTestReportOption(Test, "test-reports")
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    integrationTestSettings(),
    IntegrationTest / Keys.fork := true,
    IntegrationTest / unmanagedSourceDirectories := Seq(
      (IntegrationTest / baseDirectory).value / "test/it",
      (IntegrationTest / baseDirectory).value / "test/util"
    ),
    IntegrationTest / resourceDirectory := baseDirectory.value / "test" / "resources",
    addTestReportOption(IntegrationTest, "int-test-reports")
  )
  .settings(
    // Coverage configuration
    coverageMinimumStmtTotal := 100,
    coverageFailOnMinimum := true,
    coverageExcludedPackages := "<empty>;com.kenshoo.play.metrics.*;prod.*;testOnlyDoNotUseInAppConf.*;app.*;uk.gov.hmrc.BuildInfo"
  )

addCommandAlias("scalafmtAll", "all scalafmtSbt scalafmt Test/scalafmt")
addCommandAlias("scalastyleAll", "all scalastyle Test/scalastyle")
