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
import play.api.Logger
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.connector.{AmazonS3Connector, UpscanConnector}
import uk.gov.hmrc.bindingtarifffilestore.controllers.routes
import uk.gov.hmrc.bindingtarifffilestore.model.ScanStatus.{FAILED, READY}
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.{ScanResult, SuccessfulScanResult, UploadSettings}
import uk.gov.hmrc.bindingtarifffilestore.model.{FileMetadata, FileWithMetadata}
import uk.gov.hmrc.bindingtarifffilestore.repository.FileMetadataRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton()
class FileStoreService @Inject()(appConfig: AppConfig,
                                 fileStoreConnector: AmazonS3Connector,
                                 repository: FileMetadataRepository,
                                 upscanConnector: UpscanConnector) {

  def upload(fileWithMetadata: FileWithMetadata)(implicit headerCarrier: HeaderCarrier): Future[FileMetadata] = {
    Logger.info(s"Uploading file [${fileWithMetadata.metadata.id}]")
    val settings = UploadSettings(
      routes.FileStoreController
        .notification(fileWithMetadata.metadata.id)
        .absoluteURL(appConfig.filestoreSSL, appConfig.filestoreUrl)
    )

    upscanConnector.initiate(settings).flatMap { response =>
      Logger.info(s"Upscan-Initiating file [${fileWithMetadata.metadata.id}] with Upscan reference [${response.reference}]")
      upscanConnector.upload(response.uploadRequest, fileWithMetadata)
    } recover {case t => Logger.error("Upscan error", t)}

    repository.insert(fileWithMetadata.metadata)
  }

  def getById(id: String): Future[Option[FileMetadata]] = {
    repository.get(id)
  }

  def notify(attachment: FileMetadata, scanResult: ScanResult): Future[Option[FileMetadata]] = {
    Logger.info(s"Scan completed for file [${attachment.id}] with status [${scanResult.fileStatus}] and Upscan reference [${scanResult.reference}]")
    val updated: FileMetadata = scanResult.fileStatus match {
      case FAILED => attachment.copy(scanStatus = Some(FAILED))
      case READY =>
        val result = scanResult.asInstanceOf[SuccessfulScanResult]
        attachment.copy(url = Some(result.downloadUrl), scanStatus = Some(READY))
    }

    repository.update(updated)
  }

  def publish(att: FileMetadata): Future[FileMetadata] = {
    Logger.info(s"Publishing file [${att.id}]")
    val metadata = fileStoreConnector.upload(att)
    repository.update(metadata).map(_.get)
  }

}
