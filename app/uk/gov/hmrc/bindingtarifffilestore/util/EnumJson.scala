package uk.gov.hmrc.bindingtarifffilestore.util

import play.api.libs.json.{Format, Reads, Writes}

object EnumJson {

  implicit def format[E <: Enumeration](enum: E): Format[E#Value] = {
    Format(Reads.enumNameReads(enum), Writes.enumNameWrites)
  }

}
