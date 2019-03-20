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
import uk.gov.hmrc.bindingtarifffilestore.audit.AuditService
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.connector.{AmazonS3Connector, UpscanConnector}
import uk.gov.hmrc.bindingtarifffilestore.controllers.routes
import uk.gov.hmrc.bindingtarifffilestore.model.ScanStatus.{FAILED, READY}
import uk.gov.hmrc.bindingtarifffilestore.model.upscan._
import uk.gov.hmrc.bindingtarifffilestore.model.{FileMetadata, FileWithMetadata, Search, UploadTemplate}
import uk.gov.hmrc.bindingtarifffilestore.repository.FileMetadataRepository
import uk.gov.hmrc.bindingtarifffilestore.util.HashUtil
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton()
class FileStoreService @Inject()(appConfig: AppConfig,
                                 fileStoreConnector: AmazonS3Connector,
                                 repository: FileMetadataRepository,
                                 upscanConnector: UpscanConnector,
                                 auditService: AuditService) {

  private lazy val authToken = HashUtil.hash(appConfig.authorization)

  // Initiates an upload for a POST direct to Upscan
  def initiate(metadata: FileMetadata)(implicit hc: HeaderCarrier): Future[UploadTemplate] = {
    val fileId = metadata.id
    Logger.info(s"Initiating file [$fileId]")
    val settings = UploadSettings(
      callbackUrl = routes.FileStoreController
        .notification(fileId)
        .absoluteURL(appConfig.filestoreSSL, appConfig.filestoreUrl) + s"?X-Api-Token=$authToken",
      minimumFileSize = appConfig.fileStoreSizeConfiguration.minFileSize,
      maximumFileSize = appConfig.fileStoreSizeConfiguration.maxFileSize
    )

    for {
      initiateResponse <- upscanConnector.initiate(settings)
      _ = auditService.auditUpScanInitiated(fileId, metadata.fileName, initiateResponse.reference)
      _ <- repository.insert(metadata)
      template = initiateResponse.uploadRequest
    } yield UploadTemplate(fileId, template.href, template.fields)
  }

  // Initiates an upload and Uploads the file direct
  def upload(fileWithMetadata: FileWithMetadata)(implicit hc: HeaderCarrier): Future[FileMetadata] = {
    val fileId = fileWithMetadata.metadata.id
    Logger.info(s"Uploading file [$fileId]")
    val settings = UploadSettings(
      callbackUrl = routes.FileStoreController
        .notification(fileId)
        .absoluteURL(appConfig.filestoreSSL, appConfig.filestoreUrl) + s"?X-Api-Token=$authToken",
      minimumFileSize = appConfig.fileStoreSizeConfiguration.minFileSize,
      maximumFileSize = appConfig.fileStoreSizeConfiguration.maxFileSize
    )

    for {
      initiateResponse <- upscanConnector.initiate(settings)
      _ = Logger.info(s"Upscan-Initiated file [$fileId] with Upscan reference [${initiateResponse.reference}]")
      _ = auditService.auditUpScanInitiated(fileId, fileWithMetadata.metadata.fileName, initiateResponse.reference)
      // This future (Upload) executes asynchronously intentionally
      _ = upscanConnector.upload(initiateResponse.uploadRequest, fileWithMetadata)
      update <- repository.insert(fileWithMetadata.metadata)
    } yield update
  }

  def getById(id: String): Future[Option[FileMetadata]] = {
    repository.get(id) map signingPermanentURL
  }

  def getByIds(search: Search): Future[Seq[FileMetadata]] = {
    repository.get(search) map (signingPermanentURLs(_))
  }

  // when UpScan comes back to us with the scan result
  def notify(attachment: FileMetadata, scanResult: ScanResult)(implicit hc: HeaderCarrier): Future[Option[FileMetadata]] = {
    Logger.info(s"Scan completed for file [${attachment.id}] with status [${scanResult.fileStatus}] and Upscan reference [${scanResult.reference}]")
    auditService.auditFileScanned(attachment.id, attachment.fileName, scanResult.reference, scanResult.fileStatus.toString)
    scanResult.fileStatus match {
      case FAILED =>
        val details = scanResult.asInstanceOf[FailedScanResult].failureDetails
        Logger.info(s"Scan for file [${attachment.id}] failed because it was [${details.failureReason}] with message [${details.message}]")
        repository.update(attachment.copy(scanStatus = Some(FAILED)))
      case READY =>
        val result = scanResult.asInstanceOf[SuccessfulScanResult]
        val update = attachment.copy(url = Some(result.downloadUrl), scanStatus = Some(READY))
        if (update.publishable) {
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

  // when the file is uploaded to our S3 bucket
  def publish(att: FileMetadata)(implicit hc: HeaderCarrier): Future[Option[FileMetadata]] = {
    Logger.info(s"Publishing file [${att.id}]")

    (att.scanStatus, att.published) match {
        // File is Safe, unpublished and the download URL is still live
      case (Some(READY), false) if att.isLive =>
        val metadata = fileStoreConnector.upload(att)
        auditService.auditFilePublished(att.id, att.fileName)
        repository.update(metadata.copy(publishable = true, published = true))
          .map(signingPermanentURL)

        // File is safe, unpublished but the download URL has expired. Clean Up.
      case (Some(READY), false) =>
        repository.delete(att.id).map(_ => None)

        // File not safe yet & is unpublished
      case (_, false) =>
        repository.update(att.copy(publishable = true))

        // File is already published
      case (_, true) =>
        Future(Some(att))
    }
  }

  def deleteAll(): Future[Unit] = {
    repository.deleteAll() map (_ => fileStoreConnector.deleteAll())
  }

  def delete(id: String): Future[Unit] = {
    repository.delete(id) map (_ => fileStoreConnector.delete(id))
  }

  private def signingPermanentURL: Option[FileMetadata] => Option[FileMetadata] = _ map signingIfPublished

  private def signingPermanentURLs: Seq[FileMetadata] => Seq[FileMetadata] = _ map signingIfPublished

  private def signingIfPublished: FileMetadata => FileMetadata = {
    case file if file.published => fileStoreConnector.sign(file)
    case other => other
  }

}
