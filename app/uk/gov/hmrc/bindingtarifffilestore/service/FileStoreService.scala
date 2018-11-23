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

package uk.gov.hmrc.bindingtarifffilestore.service

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.bindingtarifffilestore.connector.{AmazonS3Connector, UpscanConnector}
import uk.gov.hmrc.bindingtarifffilestore.controllers.routes
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.{ScanResult, SuccessfulScanResult, UploadSettings}
import uk.gov.hmrc.bindingtarifffilestore.model.{FileMetadata, FileWithMetadata, ScanStatus}
import uk.gov.hmrc.bindingtarifffilestore.repository.FileMetadataRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton()
class FileStoreService @Inject()(connector: AmazonS3Connector,
                                 repository: FileMetadataRepository,
                                 upscanConnector: UpscanConnector) {

  //  def getAll: Future[Seq[TemporaryAttachment]] = {
  //    Future.successful(connector.getAll)
  //  }

  def getById(id: String): Future[Option[FileMetadata]] = {
    repository.get(id)
  }

  def upload(attachment: FileWithMetadata)(implicit headerCarrier: HeaderCarrier): Future[FileMetadata] = {
    Future {
      val settings = UploadSettings(routes.FileStoreController.notification(attachment.metadata.id).absoluteURL(true, "localhost:9583"))
      upscanConnector.initiate(settings).flatMap { response =>
        upscanConnector.upload(response.uploadRequest, attachment.file)
      }
    }
    repository.insert(attachment.metadata)
  }

  def notify(attachment: FileMetadata, scanResult: ScanResult): Future[Option[FileMetadata]] = {
    val updated: FileMetadata = scanResult.fileStatus match {
      case ScanStatus.READY =>
        val result = scanResult.asInstanceOf[SuccessfulScanResult]
        attachment.copy(url = Some(result.downloadUrl), scanStatus = Some(ScanStatus.READY))
      case ScanStatus.FAILED =>
        attachment.copy(scanStatus = Some(ScanStatus.FAILED))
    }
    repository.update(updated)
  }

}


