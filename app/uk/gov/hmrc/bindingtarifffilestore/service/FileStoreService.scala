/*
 * Copyright 2019 HM Revenue & Customs
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
      Logger.info(s"Upscan-Initiated file [${fileWithMetadata.metadata.id}] with Upscan reference [${response.reference}]")
      upscanConnector.upload(response.uploadRequest, fileWithMetadata)
    } recover { case t => Logger.error("Upscan error", t) }

    repository.insert(fileWithMetadata.metadata)
  }

  def getById(id: String): Future[Option[FileMetadata]] = {
    repository.get(id) map signingPermanentURL
  }

  def getByIds(ids: Seq[String]): Future[Seq[FileMetadata]] = {
    repository.get(ids) map (signingPermanentURLs(_))
  }

  def notify(attachment: FileMetadata, scanResult: ScanResult): Future[Option[FileMetadata]] = {
    Logger.info(s"Scan completed for file [${attachment.id}] with status [${scanResult.fileStatus}] and Upscan reference [${scanResult.reference}]")
    scanResult.fileStatus match {
      case FAILED => repository.update(attachment.copy(scanStatus = Some(FAILED)))
      case READY =>
        val result = scanResult.asInstanceOf[SuccessfulScanResult]
        val update = attachment.copy(url = Some(result.downloadUrl), scanStatus = Some(READY))
        if (update.published) {
          for {
            updated: Option[FileMetadata] <- repository.update(update)
            published: Option[FileMetadata] <- updated match {
              case Some(metadata) => publish(metadata)
              case _ =>
                Logger.warn(s"Scan completed for file [${attachment.id}] but it couldn't be published as it no longer exits")
                Future.successful(None)
            }
          } yield published
        } else {
          repository.update(update)
        }
    }
  }

  def publish(att: FileMetadata): Future[Option[FileMetadata]] = {
    Logger.info(s"Publishing file [${att.id}]")
    att.scanStatus match {
      case Some(READY) =>
        val metadata = fileStoreConnector.upload(att)
        val update = if (metadata.published) metadata else metadata.copy(published = true)
        repository.update(update)
          .map(signingPermanentURL)
      case _ =>
        repository.update(att.copy(published = true))
    }

  }

  def deleteAll(): Future[Unit] = {
    fileStoreConnector.deleteAll()
    repository.deleteAll()
  }

  def delete(id: String): Future[Unit] = {
    fileStoreConnector.delete(id)
    repository.delete(id)
  }

  private def signingPermanentURL: Option[FileMetadata] => Option[FileMetadata] = _ map signingIfPublished

  private def signingPermanentURLs: Seq[FileMetadata] => Seq[FileMetadata] = _ map signingIfPublished

  private def signingIfPublished: FileMetadata => FileMetadata = {
    case file if file.published => fileStoreConnector.sign(file)
    case other => other
  }



}
