import sbt.Setting
import scoverage.ScoverageKeys.*

object CodeCoverageSettings {

  private val excludedPackages: Seq[String] = Seq(
    "<empty>",
    "com.kenshoo.play.metrics.*;prod.*;testOnlyDoNotUseInAppConf.*",
    "..*Routes.*",
    ".*filters.*",
    "app.*",
    "uk.gov.hmrc.BuildInfo"
  )

  val settings: Seq[Setting[?]] = Seq(
    coverageExcludedPackages := excludedPackages.mkString(";"),
    coverageMinimumStmtTotal := 99,
    coverageFailOnMinimum := true,
    coverageHighlighting := true
  )
}
