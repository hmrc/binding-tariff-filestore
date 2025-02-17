/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.bindingtarifffilestore.model

import play.api.libs.json._
import uk.gov.hmrc.bindingtarifffilestore.util.UnitSpec

import java.time.Instant

class FileMetadataSpec extends UnitSpec {

  private val model: FileMetadata = FileMetadata(
    id = "id",
    fileName = Some("fileName"),
    mimeType = Some("type"),
    url = Some("url"),
    publishable = true,
    published = true,
    scanStatus = Some(ScanStatus.READY),
    lastUpdated = Instant.EPOCH
  )

  private val jsonMongo: JsObject = Json.obj(
    "id"          -> JsString("id"),
    "fileName"    -> JsString("fileName"),
    "mimeType"    -> JsString("type"),
    "url"         -> JsString("url"),
    "scanStatus"  -> JsString("READY"),
    "publishable" -> JsBoolean(true),
    "published"   -> JsBoolean(true),
    "lastUpdated" -> Json.obj("$date" -> JsNumber(0))
  )

  private val jsonMongoWithoutDefaults: JsObject = Json.obj(
    "id"          -> JsString("id"),
    "fileName"    -> JsString("fileName"),
    "mimeType"    -> JsString("type"),
    "url"         -> JsString("url"),
    "scanStatus"  -> JsString("READY"),
    "lastUpdated" -> Json.obj("$date" -> JsNumber(0))
  )

  private val jsonREST: JsObject = Json.obj(
    "url"         -> JsString("url"),
    "lastUpdated" -> JsString("1970-01-01T00:00:00Z"),
    "published"   -> JsBoolean(true),
    "scanStatus"  -> JsString("READY"),
    "fileName"    -> JsString("fileName"),
    "mimeType"    -> JsString("type"),
    "id"          -> JsString("id"),
    "publishable" -> JsBoolean(true)
  )

  "FileMetadata" should {
    "convert to Mongo JSON" in {
      val value = Json.toJson(model)(FileMetadataMongo.format)
      value.toString() shouldBe jsonMongo.toString()
    }

    "convert from Mongo JSON" in {
      val value = Json.fromJson[FileMetadata](jsonMongo)(FileMetadataMongo.format).get
      value shouldBe model
    }

    "convert from Mongo JSON with defaults" in {
      val value = Json.fromJson[FileMetadata](jsonMongoWithoutDefaults)(FileMetadataMongo.format).get
      value shouldBe model.copy(publishable = false, published = false)
    }

    "convert to REST JSON" in {
      val value = Json.toJson(model)(FileMetadataREST.format)
      value.toString() shouldBe jsonREST.toString()
    }

    "convert to REST JSON ignoring URL if Un-scanned" in {
      val value = Json.toJson(model.copy(scanStatus = None))(FileMetadataREST.format)
      value.toString() shouldBe Json
        .obj(
          "lastUpdated" -> JsString("1970-01-01T00:00:00Z"),
          "published"   -> JsBoolean(true),
          "fileName"    -> JsString("fileName"),
          "mimeType"    -> JsString("type"),
          "id"          -> JsString("id"),
          "publishable" -> JsBoolean(true)
        )
        .toString()
    }

    "convert to REST JSON ignoring URL if Failed" in {
      val value = Json.toJson(model.copy(scanStatus = Some(ScanStatus.FAILED)))(FileMetadataREST.format)
      value.toString() shouldBe Json
        .obj(
          "lastUpdated" -> JsString("1970-01-01T00:00:00Z"),
          "published"   -> JsBoolean(true),
          "scanStatus"  -> JsString("FAILED"),
          "fileName"    -> JsString("fileName"),
          "mimeType"    -> JsString("type"),
          "id"          -> JsString("id"),
          "publishable" -> JsBoolean(true)
        )
        .toString()
    }

    "convert from REST JSON" in {
      val value = Json.fromJson[FileMetadata](jsonREST)(FileMetadataREST.format).get
      value shouldBe model
    }

    "calculate liveness of signed URL" in {
      def metadata(url: String): FileMetadata = FileMetadata("id", Some("file"), Some("type"), Some(url))

      metadata(
        "https://s3.amazonaws.com/bucket/abc?X-Amz-Date=30000101T000000Zkey=value&X-Amz-Expires=86400"
      ).isLive                                                                                            shouldBe true
      metadata(
        "https://s3.amazonaws.com/bucket/abc?X-Amz-Expires=86400&X-Amz-Date=30000101T000000Zkey=value"
      ).isLive                                                                                            shouldBe true
      metadata("https://s3.amazonaws.com/bucket/file?X-Amz-Date=20190101T000000Z&X-Amz-Expires=0").isLive shouldBe false
      metadata("url").isLive                                                                              shouldBe true
      FileMetadata("id", Some("file"), Some("type")).isLive                                               shouldBe true
    }

    def test(json: JsObject): Unit =
      s"produce a JsError when converting from $json" in {
        val result: JsResult[Instant] = FileMetadataMongo.instantFormat.reads(json)

        result shouldBe JsError("Unexpected Instant Format")
      }

    Seq(
      Json.obj("$date" -> JsBoolean(true)),
      Json.obj("$date" -> Json.obj()),
      Json.obj()
    ).foreach(test)
  }
}
