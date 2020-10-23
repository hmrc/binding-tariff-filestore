/*
 * Copyright 2020 HM Revenue & Customs
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

import java.{util => ju}
import javax.inject.{Inject, Singleton}
import play.api.Logging
import uk.gov.hmrc.bindingtarifffilestore.audit.AuditService
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.connector.{AmazonS3Connector, UpscanConnector}
import uk.gov.hmrc.bindingtarifffilestore.controllers.routes
import uk.gov.hmrc.bindingtarifffilestore.model.ScanStatus.{FAILED, READY}
import uk.gov.hmrc.bindingtarifffilestore.model._
import uk.gov.hmrc.bindingtarifffilestore.model.upscan._
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.v2
import uk.gov.hmrc.bindingtarifffilestore.repository.FileMetadataMongoRepository
import uk.gov.hmrc.bindingtarifffilestore.util.HashUtil
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton()
class FileStoreService @Inject()(appConfig: AppConfig,
  fileStoreConnector: AmazonS3Connector,
  repository: FileMetadataMongoRepository,
  upscanConnector: UpscanConnector,
  auditService: AuditService
)(implicit ec: ExecutionContext) extends Logging {

  private lazy val authToken = HashUtil.hash(appConfig.authorization)

  // Initiates an upload for a POST direct to Upscan
  def initiate(metadata: FileMetadata)(implicit hc: HeaderCarrier): Future[UploadTemplate] = {
    val fileId = metadata.id
    log(fileId, "Initiating")

    for {
      _ <- repository.insertFile(metadata)
      initiateResponse <- upscanInitiate(metadata)
      template = initiateResponse.uploadRequest
    } yield UploadTemplate(fileId, template.href, template.fields)
  }

  def initiateV2(request: v2.FileStoreInitiateRequest)(implicit hc: HeaderCarrier): Future[v2.FileStoreInitiateResponse] = {
    val fileId = request.id.getOrElse(ju.UUID.randomUUID().toString())

    log(fileId, "Initiating")

    val fileMetadata = FileMetadata.fromInitiateRequestV2(fileId, request)
    val upscanRequest = v2.UpscanInitiateRequest.fromFileStoreRequest(appConfig, request)

    for {
      update <- repository.insertFile(fileMetadata)
      initiateResponse <- upscanConnector.initiateV2(upscanRequest)
      _ = log(fileId, s"Upscan Initiated with url [${initiateResponse.uploadRequest.href}] and Upscan reference [${initiateResponse.reference}]")
      _ = auditService.auditUpScanInitiated(update.id, update.fileName, initiateResponse.reference)
    } yield v2.FileStoreInitiateResponse.fromUpscanResponse(fileId, initiateResponse)
  }

  // Initiates an upload and Uploads the file direct
  def upload(fileWithMetadata: FileWithMetadata)(implicit hc: HeaderCarrier): Future[FileMetadata] = {
    val fileId = fileWithMetadata.metadata.id
    log(fileId, "Uploading")

    for {
      update <- repository.insertFile(fileWithMetadata.metadata)
      initiateResponse <- upscanInitiate(fileWithMetadata.metadata)
      // This future (Upload) executes asynchronously intentionally
      _ = log(fileId, s"Uploading to Upscan url [${initiateResponse.uploadRequest.href}] with Upscan reference [${initiateResponse.reference}]")
      _ = upscanConnector.upload(initiateResponse.uploadRequest, fileWithMetadata)
        .recover { case e =>
          error(fileId, s"Upload failed to Upscan url [${initiateResponse.uploadRequest.href}] with Upscan reference [${initiateResponse.reference}]", e)
        }
        .onComplete {
          _ => log(fileId, s"Uploaded to Upscan url [${initiateResponse.uploadRequest.href}] with Upscan reference [${initiateResponse.reference}]")
        }
    } yield update
  }

  def find(id: String): Future[Option[FileMetadata]] = {
    repository.get(id) map signingPermanentURL
  }

  def find(search: Search, pagination: Pagination): Future[Paged[FileMetadata]] = {
    repository.get(search, pagination: Pagination) map (signingPermanentURLs(_))
  }

  // when UpScan comes back to us with the scan result
  def notify(attachment: FileMetadata, scanResult: ScanResult)(implicit hc: HeaderCarrier): Future[Option[FileMetadata]] = {
    log(attachment.id, s"Scan completed with status [${scanResult.fileStatus}] and Upscan reference [${scanResult.reference}]")
    auditService.auditFileScanned(attachment.id, attachment.fileName, scanResult.reference, scanResult.fileStatus.toString)

    val updatedAttachment = attachment.withScanResult(scanResult)

    scanResult match {
      case FailedScanResult(_, details) =>
        log(attachment.id, s"Scan failed because it was [${details.failureReason}] with message [${details.message}]")
        repository.update(updatedAttachment)
      case SuccessfulScanResult(_, _, details) =>
        log(attachment.id, s"Scan succeeded with details [${details.fileName}, ${details.fileMimeType}, ${details.checksum}, ${details.uploadTimestamp}]")
        if (updatedAttachment.publishable) {
          for {
            updated: Option[FileMetadata] <- repository.update(updatedAttachment)
            published: Option[FileMetadata] <- updated match {
              case Some(metadata) =>
                publish(metadata)
              case _ =>
                log(attachment.id, s"Scan completed as READY but it couldn't be published as it no longer exists")
                Future.successful(None)
            }
          } yield published
        } else {
          repository.update(updatedAttachment)
        }
    }
  }

  // when the file is uploaded to our S3 bucket
  def publish(att: FileMetadata)(implicit hc: HeaderCarrier): Future[Option[FileMetadata]] = {
    log(att.id, "Publishing")

    (att.scanStatus, att.published) match {
      // File is Safe, unpublished and the download URL is still live
      case (Some(READY), false) if att.isLive =>
        log(att.id, "Publishing file to Permanent Storage")
        val metadata = fileStoreConnector.upload(att)
        auditService.auditFilePublished(att.id, att.fileName.get)
        log(att.id, "Published file to Permanent Storage")
        repository.update(metadata.copy(publishable = true, published = true))
          .map(signingPermanentURL)

      // File is safe, unpublished but the download URL has expired. Clean Up.
      case (Some(READY), false) =>
        log(att.id, s"Removing as it had an expired download URL [${att.url}]")
        repository.delete(att.id).map(_ => None)

      // File not safe yet & is unpublished
      case (_, false) =>
        log(att.id, s"Marking as publishable")
        repository.update(att.copy(publishable = true))

      // File is already published
      case (_, true) =>
        log(att.id, s"Ignoring publish request as it was already published")
        Future(Some(att))
    }
  }

  def deleteAll(): Future[Unit] = {
    repository.deleteAll() map (_ => fileStoreConnector.deleteAll())
  }

  def delete(id: String): Future[Unit] = {
    log(id, "Deleting")
    repository.delete(id) map (_ => fileStoreConnector.delete(id))
  }

  private def upscanInitiate(fileMetadata: FileMetadata)(implicit hc: HeaderCarrier): Future[UpscanInitiateResponse] = {
    val settings = UploadSettings(
      callbackUrl = routes.FileStoreController
        .notification(fileMetadata.id)
        .absoluteURL(appConfig.filestoreSSL, appConfig.filestoreUrl) + s"?X-Api-Token=$authToken",
      minimumFileSize = appConfig.fileStoreSizeConfiguration.minFileSize,
      maximumFileSize = appConfig.fileStoreSizeConfiguration.maxFileSize
    )
    for {
      initiateResponse <- upscanConnector.initiate(settings)
      _ = log(fileMetadata.id, s"Upscan Initiated with url [${initiateResponse.uploadRequest.href}] and Upscan reference [${initiateResponse.reference}]")
      _ = auditService.auditUpScanInitiated(fileMetadata.id, fileMetadata.fileName, initiateResponse.reference)
    } yield initiateResponse
  }

  private def signingPermanentURL: Option[FileMetadata] => Option[FileMetadata] = _ map signingIfPublished

  private def signingPermanentURLs: Paged[FileMetadata] => Paged[FileMetadata] = _ map signingIfPublished

  private def signingIfPublished: FileMetadata => FileMetadata = {
    case file if file.published => fileStoreConnector.sign(file)
    case other => other
  }

  private def error(id: String, message: String, error: Throwable): Unit = {
    logger.error(s"File [$id]: $message", error)
  }

  private def log(id: String, message: String): Unit = {
    logger.info(s"File [$id]: $message")
  }

}
