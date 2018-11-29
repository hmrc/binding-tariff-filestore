package uk.gov.hmrc.bindingtarifffilestore.model

import java.time.Instant

import play.api.libs.json.{JsNumber, JsObject, JsString, Json}
import uk.gov.hmrc.play.test.UnitSpec

class FileMetadataSpec extends UnitSpec {

  "File Meta Data" should {
    val model = FileMetadata(
      id = "id",
      fileName = "filename",
      mimeType = "type",
      url = Some("url"),
      scanStatus = Some(ScanStatus.READY),
      lastUpdated = Instant.EPOCH
    )
    val json: JsObject = Json.obj(
      "id" -> JsString("id"),
      "fileName" -> JsString("fileName"),
      "mimeType" -> JsString("type"),
      "url" -> JsString("url"),
      "scanStatus" -> JsString("READY"),
      "lastUpdated" -> Json.obj("$date" -> JsNumber(0))
    )

    "Convert to JSON" in {
      val value = Json.toJson(model)(FileMetadata.format)
      value shouldBe json
    }

    "Convert from JSON" in {
      val value = Json.fromJson[FileMetadata](json)(FileMetadata.format).get
      value shouldBe model
    }
  }
}
