package uk.gov.hmrc.bindingtarifffilestore.model

object FailureReason extends Enumeration {
  type FailureReason = Value

  val QUARANTINED = Value("QUARANTINE")
  val REJECTED = Value("REJECTED")
  val UNKNOWN = Value("UNKNOWN")

  implicit val format = EnumJson.format(FailureReason)
}
