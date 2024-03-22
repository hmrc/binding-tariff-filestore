/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtarifffilestore.model.upscan

import play.api.libs.json.{JsObject, JsString, Json}
import uk.gov.hmrc.bindingtarifffilestore.util._

import java.time.Instant

class ScanResultSpec extends UnitSpec {

  "Successful Scan Result" should {
    val model = SuccessfulScanResult("ref", "url", UploadDetails("file", "type", Instant.EPOCH, "checksum"))
    val json  = JsObject(
      Map(
        "reference"     -> JsString("ref"),
        "downloadUrl"   -> JsString("url"),
        "uploadDetails" -> JsObject(
          Map(
            "fileName"        -> JsString("file"),
            "fileMimeType"    -> JsString("type"),
            "uploadTimestamp" -> JsString("1970-01-01T00:00:00Z"),
            "checksum"        -> JsString("checksum")
          )
        ),
        "fileStatus"    -> JsString("READY")
      )
    )

    "Convert Result to JSON" in {
      Json.toJson[ScanResult](model)(ScanResult.format) shouldBe json
    }

    "Convert JSON to Result" in {
      Json.fromJson[ScanResult](json)(ScanResult.format).get shouldBe model
    }
  }

  "Failed Scan Result" should {
    val model = FailedScanResult("ref", FailureDetails(FailureReason.QUARANTINE, "message"))
    val json  = JsObject(
      Map(
        "reference"      -> JsString("ref"),
        "failureDetails" -> JsObject(
          Map(
            "failureReason" -> JsString("QUARANTINE"),
            "message"       -> JsString("message")
          )
        ),
        "fileStatus"     -> JsString("FAILED")
      )
    )

    "Convert Result to JSON" in {
      Json.toJson[ScanResult](model)(ScanResult.format) shouldBe json
    }

    "Convert JSON to Result" in {
      Json.fromJson[ScanResult](json)(ScanResult.format).get shouldBe model
    }
  }

}
