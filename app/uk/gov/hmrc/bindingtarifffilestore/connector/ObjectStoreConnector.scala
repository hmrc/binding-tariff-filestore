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

package uk.gov.hmrc.bindingtarifffilestore.connector

import com.google.inject.Inject
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.util.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{ObjectSummary, ObjectSummaryWithMd5, Path}
import uk.gov.hmrc.objectstore.client.play.Implicits.stringWrite
import uk.gov.hmrc.objectstore.client.play.Implicits.futureMonad

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class ObjectStoreConnector @Inject() (
  client: PlayObjectStoreClient,
  config: AppConfig
)(implicit
  val ec: ExecutionContext
) extends Logging {

  private val directory: Path.Directory =
    Path.Directory(config.s3bucket)

  def getAll(path: Path.Directory)(implicit hc: HeaderCarrier): Future[List[ObjectSummary]] =
    client
      .listObjects(path)
      .map(_.objectSummaries.map(o => ObjectSummary(o.location, o.contentLength, o.lastModified)))

  def upload(fileMetaData: FileMetadata)(implicit hc: HeaderCarrier): FileMetadata =
    Try(
      client.putObject(
        path = directory.file(fileMetaData.fileName.getOrElse("Name")),
        content = fileMetaData.mimeType.get
      )
    ) match {
      case Success(_)            =>
        log.info(s"File uploaded to Object Store: ${fileMetaData.fileName.getOrElse(fileMetaData.id)}")
        fileMetaData
      case Failure(e: Throwable) =>
        log.error("Failed to upload to the object store.", e)
        throw e
    }

  def delete(id: String)(implicit hc: HeaderCarrier): Future[Unit] =
    client.deleteObject(
      path = directory.file(id)
    )

  def deleteAll()(implicit hc: HeaderCarrier): Future[Unit] =
    getAll(directory).map(files =>
      if (files.nonEmpty) {
        log.info(s"Removing [${files.length}] files from object store")
        Future.traverse(files) { filename =>
          client.deleteObject(
            path = directory.file(filename.location.fileName)
          )
        }

      } else {
        log.info(s"No files to remove from object store")
      }
    )

  def sign(fileMetaData: FileMetadata)(implicit hc: HeaderCarrier): Future[FileMetadata] =
    if (fileMetaData.url.isDefined) {
      client
        .presignedDownloadUrl(
          path = directory.file(fileMetaData.fileName.getOrElse(fileMetaData.id))
        )
        .transformWith {
          case scala.util.Failure(exception)            =>
            log.error(
              s"Failure to get pre-signed URL to ${directory.file(fileMetaData.fileName.getOrElse(fileMetaData.id))} because of $exception"
            )
            exception.printStackTrace()
            Future.successful(fileMetaData)
          case scala.util.Success(presignedDownloadUrl) =>
            log.info(s"Files signed in object store")
            Future.successful(fileMetaData)
        }
    } else {
      Future.successful(fileMetaData)
    }
}
