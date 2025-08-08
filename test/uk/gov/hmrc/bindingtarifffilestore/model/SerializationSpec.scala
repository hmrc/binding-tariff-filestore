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

import play.api.libs.json.Json
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.{FailedScanResult, FailureDetails, FailureReason, SuccessfulScanResult, UploadDetails, UploadSettings, UpscanTemplate}
import uk.gov.hmrc.bindingtarifffilestore.util.UnitSpec
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata.format
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.v2.{FileStoreInitiateResponse, UpscanFormTemplate, UpscanInitiateRequest}

import java.time.Instant

class SerializationSpec extends UnitSpec {

  "All the models" should {

    val upscanFormTemplate: UpscanFormTemplate =
      UpscanFormTemplate(href = "ref1", fields = Map("Darwin" -> "Desbocatti"))

    val uploadDetails =
      UploadDetails(fileName = "file1", fileMimeType = "mimeType", uploadTimestamp = Instant.EPOCH, checksum = "234")

    val failureDetails = FailureDetails(failureReason = FailureReason.UNKNOWN, message = "try again")

    "Serialize and Deserialize JSON for UploadSettings" in {

      val settings = UploadSettings(
        callbackUrl = "url",
        minimumFileSize = 234,
        maximumFileSize = 365
      )

      val json         = Json.toJson(settings)
      val deserialized = json.as[UploadSettings]

      deserialized.shouldBe(settings)
    }

    "Serialize and Deserialize JSON for FileMetadata" in {

      val file = FileMetadata(
        id = "id1",
        fileName = Option("darwin"),
        mimeType = None,
        url = Option("urlDarwin"),
        scanStatus = Option(ScanStatus.READY),
        publishable = true,
        published = true,
        lastUpdated = Instant.EPOCH
      )

      val json         = Json.toJson(file)
      val deserialized = json.as[FileMetadata]

      deserialized.shouldBe(file)
    }

    "Serialize and Deserialize JSON for UploadTemplate" in {

      val template = UploadTemplate(id = "template1", href = "href", fields = Map("Darwin" -> "Desbocatti"))

      val json         = Json.toJson(template)
      val deserialized = json.as[UploadTemplate]

      deserialized.shouldBe(template)
    }

    "Serialize and Deserialize JSON for FileStoreInitiateResponse" in {

      val file = FileStoreInitiateResponse(id = "file1", upscanReference = "ref1", uploadRequest = upscanFormTemplate)

      val json         = Json.toJson(file)
      val deserialized = json.as[FileStoreInitiateResponse]

      deserialized.shouldBe(file)
    }

    "Serialize and Deserialize JSON for UpscanInitiateRequest v2" in {

      val request = UpscanInitiateRequest(
        callbackUrl = "url",
        successRedirect = Option("url2"),
        errorRedirect = Option("url3"),
        minimumFileSize = Option(3L),
        maximumFileSize = Option(5L),
        expectedContentType = Option("application/json")
      )

      val json         = Json.toJson(request)
      val deserialized = json.as[UpscanInitiateRequest]

      deserialized.shouldBe(request)
    }

    "Serialize and Deserialize JSON for UpscanTemplate" in {

      val template = UpscanTemplate(href = "href", fields = Map("Darwin" -> "Desbocatti"))

      val json         = Json.toJson(template)
      val deserialized = json.as[UpscanTemplate]

      deserialized.shouldBe(template)
    }

    "Serialize and Deserialize JSON for UpscanInitiateResponse v1" in {
      import uk.gov.hmrc.bindingtarifffilestore.model.upscan.UpscanInitiateResponse

      val response = UpscanInitiateResponse(
        reference = "ref1",
        uploadRequest = UpscanTemplate(href = "href1", fields = Map("Darwin" -> "Desbocatti"))
      )

      val json         = Json.toJson(response)
      val deserialized = json.as[UpscanInitiateResponse]

      deserialized.shouldBe(response)
    }

    "Serialize and Deserialize JSON for UpscanInitiateResponse v2" in {
      import uk.gov.hmrc.bindingtarifffilestore.model.upscan.v2.UpscanInitiateResponse

      val response = UpscanInitiateResponse(reference = "ref1", uploadRequest = upscanFormTemplate)

      val json         = Json.toJson(response)
      val deserialized = json.as[UpscanInitiateResponse]

      deserialized.shouldBe(response)
    }

    "Serialize and Deserialize JSON for SuccessfulScanResult" in {

      val result = SuccessfulScanResult(
        fileStatus = ScanStatus.READY,
        reference = "ref",
        downloadUrl = "url",
        uploadDetails = uploadDetails
      )

      val json         = Json.toJson(result)
      val deserialized = json.as[SuccessfulScanResult]

      deserialized.shouldBe(result)
    }

    "Serialize and Deserialize JSON for FailedScanResult" in {

      val result = FailedScanResult(fileStatus = ScanStatus.FAILED, reference = "ref2", failureDetails = failureDetails)

      val json         = Json.toJson(result)
      val deserialized = json.as[FailedScanResult]

      deserialized.shouldBe(result)
    }

    "Serialize and Deserialize JSON for UploadDetails" in {
      val json         = Json.toJson(uploadDetails)
      val deserialized = json.as[UploadDetails]

      deserialized.shouldBe(uploadDetails)
    }

    "Serialize and Deserialize JSON for FailureDetails" in {
      val json         = Json.toJson(failureDetails)
      val deserialized = json.as[FailureDetails]

      deserialized.shouldBe(failureDetails)
    }

    "Serialize and Deserialize JSON for UpscanFormTemplate" in {
      val json         = Json.toJson(upscanFormTemplate)
      val deserialized = json.as[UpscanFormTemplate]

      deserialized.shouldBe(upscanFormTemplate)
    }
  }
}
