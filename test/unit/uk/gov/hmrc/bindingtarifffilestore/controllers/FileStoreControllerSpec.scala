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

import akka.stream.Materializer
import org.mockito.Mockito.when
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
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

}
