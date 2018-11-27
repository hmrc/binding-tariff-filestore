package uk.gov.hmrc.bindingtarifffilestore.model.upscan

import java.time.Instant

import play.api.libs.json.{JsObject, JsString, Json}
import uk.gov.hmrc.play.test.UnitSpec

class ScanResultSpec extends UnitSpec {

  "Successful Scan Result" should {
    val model = SuccessfulScanResult("ref", "url", UploadDetails(Instant.EPOCH, "checksum"))
    val json = JsObject(Map(
      "reference" -> JsString("ref"),
      "downloadUrl" -> JsString("url"),
      "fileStatus" -> JsString("READY"),
      "uploadDetails" -> JsObject(Map(
        "uploadTimestamp" -> JsString("1970-01-01T00:00:00Z"),
        "checksum" -> JsString("checksum")
      ))
    ))

    "Convert Result to JSON" in {
      Json.toJson(model)(SuccessfulScanResult.format) shouldBe json
    }

    "Convert JSON to Result" in {
      Json.fromJson[SuccessfulScanResult](json)(SuccessfulScanResult.format) shouldBe model
    }
  }

  "Failed Scan Result" should {
    val model = FailedScanResult("ref", FailureDetails(FailureReason.QUARANTINED, "message"))
    val json = JsObject(Map(
      "reference" -> JsString("ref"),
      "fileStatus" -> JsString("FAILED"),
      "failureDetails" -> JsObject(Map(
        "failureReason" -> JsString("QUARANTINED"),
        "message" -> JsString("message")
      ))
    ))

    "Convert Result to JSON" in {
      Json.toJson(model)(FailedScanResult.format) shouldBe json
    }

    "Convert JSON to Result" in {
      Json.fromJson[FailedScanResult](json)(FailedScanResult.format) shouldBe model
    }
  }

}
