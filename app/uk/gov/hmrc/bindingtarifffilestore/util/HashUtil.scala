/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtarifffilestore.util

import java.security.MessageDigest

import com.google.common.io.BaseEncoding

object HashUtil {
  private val sha256        = MessageDigest.getInstance("SHA-256")
  private val base64Encoder = BaseEncoding.base64Url()

  def hash(value: String): String = base64Encoder.encode(sha256.digest(value.getBytes("UTF-8")))
}
