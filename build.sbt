import play.sbt.PlayImport.PlayKeys._
import sbt.Tests.{Group, SubProcess}
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.SbtArtifactory
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

val appName = "binding-tariff-filestore"

lazy val plugins: Seq[Plugins] = Seq(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val microservice = (project in file("."))
  .enablePlugins(plugins: _*)
  .settings(playSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(majorVersion := 0)
  .settings(
    name := appName,
    scalaVersion := "2.12.10",
    targetJvm := "jvm-1.8",
    playDefaultPort := 9583,
    scalacOptions ++= Seq("-Ywarn-unused-import", "-deprecation", "-feature"),
    libraryDependencies ++= (AppDependencies.compile ++ AppDependencies.test).map(_ withSources()),
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    parallelExecution in Test := false,
    fork in Test := true,
    retrieveManaged := true
  )
  .settings(inConfig(TemplateTest)(Defaults.testSettings): _*)
  .settings(
    unmanagedSourceDirectories in Test := Seq(
      (baseDirectory in Test).value / "test/unit",
      (baseDirectory in Test).value / "test/util"
    ),
    resourceDirectory in Test := baseDirectory.value / "test" / "resources",
    addTestReportOption(Test, "test-reports")
  )
  .configs(IntegrationTest)
  .settings(inConfig(TemplateItTest)(Defaults.itSettings): _*)
  .settings(
    Keys.fork in IntegrationTest := true,
    unmanagedSourceDirectories in IntegrationTest := Seq(
      (baseDirectory in IntegrationTest).value / "test/it",
      (baseDirectory in IntegrationTest).value / "test/util"
    ),
    resourceDirectory in IntegrationTest := baseDirectory.value / "test" / "resources",
    addTestReportOption(IntegrationTest, "int-test-reports"),
//    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false)
  .settings(
    resolvers += Resolver.bintrayRepo("hmrc", "releases"),
    resolvers += Resolver.jcenterRepo
  )
//  .settings(ivyScala := ivyScala.value map {
//    _.copy(overrideScalaVersion = true)
//  })

lazy val allPhases = "tt->test;test->test;test->compile;compile->compile"
lazy val allItPhases = "tit->it;it->it;it->compile;compile->compile"

lazy val TemplateTest = config("tt") extend Test
lazy val TemplateItTest = config("tit") extend IntegrationTest

//def oneForkedJvmPerTest(tests: Seq[TestDefinition]): Seq[Group] = {
//  tests map {
//    test => Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq("-Dtest.name=" + test.name))))
//  }
//}

// Coverage configuration
coverageMinimum := 93
coverageFailOnMinimum := true
coverageExcludedPackages := "<empty>;com.kenshoo.play.metrics.*;prod.*;testOnlyDoNotUseInAppConf.*;app.*;uk.gov.hmrc.BuildInfo"
