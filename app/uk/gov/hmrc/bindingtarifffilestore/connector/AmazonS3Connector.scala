/*
 * Copyright 2021 HM Revenue & Customs
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

import java.io.{InputStream, OutputStream}
import java.net.URL
import java.nio.file.Files
import java.util

import better.files._
import com.amazonaws.HttpMethod
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.google.inject.Inject
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.apache.poi.ooxml.POIXMLDocument
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import javax.imageio.ImageIO
import javax.inject.Singleton
import play.api.Logging
import play.api.libs.Files.{TemporaryFile, TemporaryFileCreator}
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata

import scala.collection.{JavaConversions, JavaConverters}
import scala.util.{Failure, Success, Try}

@Singleton
class AmazonS3Connector @Inject()(
  config: AppConfig,
  tempFileCreator: TemporaryFileCreator
) extends Logging {

  private lazy val s3Config = config.s3Configuration

  private lazy val credentials = new BasicAWSCredentials(s3Config.key, s3Config.secret)
  private lazy val provider = new AWSStaticCredentialsProvider(credentials)

  private lazy val s3client: AmazonS3 = {
    logger.info(s"${s3Config.bucket}:${s3Config.region}:${s3Config.key}:${s3Config.secret.substring(0,3)}")
    val builder = AmazonS3ClientBuilder
      .standard()
      .withCredentials(provider)
      .withPathStyleAccessEnabled(true)

    s3Config.endpoint match {
      case Some(endpoint) => builder.withEndpointConfiguration(new EndpointConfiguration(endpoint, s3Config.region))
      case _ => builder.withRegion(s3Config.region)
    }

    builder.build()
  }

  def getAll: Seq[String] = {
    sequenceOf(
      s3client.listObjects(s3Config.bucket).getObjectSummaries
    ).map(_.getKey)
  }

  def downloadFile(fileMetaData: FileMetadata): TemporaryFile = {
    val url: URL = new URL(fileMetaData.url.getOrElse(throw new IllegalArgumentException("Missing URL")))
    val tempFile = tempFileCreator.create(fileMetaData.id)

    for {
      in <- url.openStream().autoClosed
      out <- Files.newOutputStream(tempFile.path).autoClosed
    } in.pipeTo(out)

    tempFile
  }

  def withFiles[A](origFile: TemporaryFile, newFile: TemporaryFile)(f: (InputStream, OutputStream) => A): A = {
    val result = for {
      in <- Files.newInputStream(origFile.path).autoClosed
      out <- Files.newOutputStream(newFile.path).autoClosed
    } yield f(in, out)

    result.get()
  }

  def clearOfficeXmlMetadata(doc: POIXMLDocument): Unit = {
    val coreProps = doc.getProperties().getCoreProperties()
    val extendedProps = doc.getProperties().getExtendedProperties()
    val packageProps = doc.getPackage().getPackageProperties()
    val emptyString = util.Optional.empty[String]
    val emptyDate = util.Optional.empty[util.Date]
    coreProps.setCategory("")
    coreProps.setContentStatus("")
    coreProps.setContentType("")
    coreProps.setCreated(emptyDate)
    coreProps.setCreator("")
    coreProps.setDescription("")
    coreProps.setIdentifier("")
    coreProps.setKeywords("")
    coreProps.setLastModifiedByUser("")
    coreProps.setLastPrinted(emptyDate)
    coreProps.setModified(emptyDate)
    coreProps.setRevision("")
    coreProps.setSubjectProperty("")
    coreProps.setTitle("")
    extendedProps.setCompany("")
    packageProps.setCategoryProperty(emptyString)
    packageProps.setContentStatusProperty(emptyString)
    packageProps.setContentTypeProperty(emptyString)
    packageProps.setCreatedProperty(emptyDate)
    packageProps.setCreatorProperty(emptyString)
    packageProps.setDescriptionProperty(emptyString)
    packageProps.setIdentifierProperty(emptyString)
    packageProps.setKeywordsProperty(emptyString)
    packageProps.setLanguageProperty(emptyString)
    packageProps.setLastModifiedByProperty(emptyString)
    packageProps.setLastPrintedProperty(emptyDate)
    packageProps.setModifiedProperty(emptyDate)
    packageProps.setRevisionProperty(emptyString)
    packageProps.setSubjectProperty(emptyString)
    packageProps.setTitleProperty(emptyString)
    packageProps.setVersionProperty(emptyString)
  }

  def stripMetadata(fileMetaData: FileMetadata, origFile: TemporaryFile): TemporaryFile = {
    val strippedFile = tempFileCreator.create(fileMetaData.id, "_stripped")

    withFiles(origFile, strippedFile) { (inputStream, outputStream) =>
      fileMetaData.mimeType match {
        case Some("image/jpeg") =>
          ImageIO.write(ImageIO.read(inputStream), "jpg", outputStream)
          strippedFile
        case Some("image/png") =>
          ImageIO.write(ImageIO.read(inputStream), "png", outputStream)
          strippedFile
        case Some("application/pdf") =>
          using(PDDocument.load(inputStream, MemoryUsageSetting.setupTempFileOnly())) { doc =>
            val emptyDocInfo = new PDDocumentInformation()
            val emptyMetadata = new PDMetadata(doc)
            doc.setDocumentInformation(emptyDocInfo)
            doc.getDocumentCatalog().setMetadata(emptyMetadata)
            doc.save(outputStream)
            strippedFile
          }
        case Some("application/vnd.openxmlformats-officedocument.wordprocessingml.document") =>
          using(new XWPFDocument(inputStream)) { doc =>
            clearOfficeXmlMetadata(doc)
            doc.write(outputStream)
            strippedFile
          }
        case Some("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") =>
          using(new XSSFWorkbook(inputStream)) { doc =>
            clearOfficeXmlMetadata(doc)
            doc.write(outputStream)
            strippedFile
          }
        case Some("text/plain") =>
          origFile
        case Some(other) =>
          logger.warn(s"Unable to strip file metadata for unknown content type ${other}")
          origFile
        case None =>
          logger.warn(s"Unable to strip file metadata for unknown content type")
          origFile
      }
    }
  }

  def upload(fileMetaData: FileMetadata): FileMetadata = {
    val strippedFile = stripMetadata(fileMetaData, downloadFile(fileMetaData))
    val metadata = new ObjectMetadata
    // This .get is scary but our file must have received a positive scan
    // result and received metadata from Upscan if it is being published
    metadata.setContentType(fileMetaData.mimeType.get)
    metadata.setContentLength(Files.size(strippedFile.path))

    using(Files.newInputStream(strippedFile.path)) { inputStream =>
      val request = new PutObjectRequest(
        s3Config.bucket, fileMetaData.id, inputStream, metadata
      ).withCannedAcl(CannedAccessControlList.Private)

      Try(s3client.putObject(request)) match {
        case Success(_) =>
          fileMetaData.copy(url = Some(s"${s3Config.baseUrl}/${s3Config.bucket}/${fileMetaData.id}"))
        case Failure(e: Throwable) =>
          logger.error("Failed to upload to the S3 bucket.", e)
          throw e
      }
    }
  }

  def delete(id: String): Unit = {
    s3client.deleteObject(s3Config.bucket, id)
  }

  def deleteAll(): Unit = {
    val keys: Seq[KeyVersion] = getAll.map(new KeyVersion(_))
    if(keys.nonEmpty) {
      logger.info(s"Removing [${keys.length}] files from S3")
      val request = new DeleteObjectsRequest(s3Config.bucket)
        .withKeys(JavaConversions.seqAsJavaList(keys))
        .withQuiet(false)
      s3client.deleteObjects(request)
    } else {
      logger.info(s"No files to remove from S3")
    }
  }

  def sign(fileMetaData: FileMetadata): FileMetadata = {
    if (fileMetaData.url.isDefined) {
      val authenticatedURLRequest = new GeneratePresignedUrlRequest(config.s3Configuration.bucket, fileMetaData.id)
        .withMethod(HttpMethod.GET)
      val authenticatedURL: URL = s3client.generatePresignedUrl(authenticatedURLRequest)
      fileMetaData.copy(url = Some(authenticatedURL.toString))
    } else {
      fileMetaData
    }
  }

  private def sequenceOf[T](list: util.List[T]): Seq[T] = {
    JavaConverters.asScalaIteratorConverter(list.iterator).asScala.toSeq
  }
}
