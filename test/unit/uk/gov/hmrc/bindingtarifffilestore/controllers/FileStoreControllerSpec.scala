/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtarifffilestore.controllers

import java.time.Instant

import akka.stream.Materializer
import org.mockito.Mockito.when
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.{SuccessfulScanResult, UploadDetails}
import uk.gov.hmrc.bindingtarifffilestore.service.FileStoreService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future.successful

class FileStoreControllerSpec extends UnitSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar {

  private implicit val mat: Materializer = fakeApplication.materializer

  private val service = mock[FileStoreService]
  private val controller = new FileStoreController(service)

  private val fakeRequest = FakeRequest()

  "Get By ID" should {
    "return 200 when found" in {
      val attachment = FileMetadata(id="id", fileName = "file", mimeType = "type")
      when(service.getById("id")).thenReturn(successful(Some(attachment)))

      val result = await(controller.get("id")(fakeRequest))

      status(result) shouldBe Status.OK
      bodyOf(result) shouldEqual Json.toJson(attachment).toString()
    }

    "return 404 when not found" in {
      when(service.getById("id")).thenReturn(successful(None))

      val result = await(controller.get("id")(fakeRequest))

      status(result) shouldBe Status.NOT_FOUND
    }
  }

  "Notify" should {
    "return 201 when found" in {
      val scanResult = SuccessfulScanResult("ref", "url", UploadDetails(Instant.now(), "checksum"))
      val attachment = FileMetadata(id="id", fileName = "file", mimeType = "type")
      val attachmentUpdated = FileMetadata(id="id", fileName = "file", mimeType = "type", url = Some("url"))
      when(service.getById("id")).thenReturn(successful(Some(attachment)))
      when(service.notify(attachment, scanResult)).thenReturn(successful(Some(attachmentUpdated)))

      val result: Result = await(controller.notification("id")(fakeRequest.withBody(Json.toJson(scanResult))))

      status(result) shouldBe Status.CREATED
      jsonBodyOf(result) shouldBe Json.toJson(attachmentUpdated)
    }

    "return 404 when not found" in {
      val scanResult = SuccessfulScanResult("ref", "url", UploadDetails(Instant.now(), "checksum"))
      when(service.getById("id")).thenReturn(successful(None))

      val result: Result = await(controller.notification("id")(fakeRequest.withBody(Json.toJson(scanResult))))

      status(result) shouldBe Status.NOT_FOUND
    }
  }

}
