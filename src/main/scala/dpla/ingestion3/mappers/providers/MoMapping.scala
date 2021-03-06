package dpla.ingestion3.mappers.providers

import dpla.ingestion3.enrichments.normalizations.filters.ExtentIdentificationList
import dpla.ingestion3.mappers.utils._
import dpla.ingestion3.messages.IngestMessageTemplates
import dpla.ingestion3.model.DplaMapData.{AtLeastOne, ExactlyOne, ZeroToMany}
import dpla.ingestion3.model._
import dpla.ingestion3.utils.Utils
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._

class MoMapping extends JsonMapping with JsonExtractor with IdMinter[JValue] with IngestMessageTemplates {

  val formatBlockList: Set[String] = ExtentIdentificationList.termList

  // ID minting functions
  // TODO: Should this be true or false?
  override def useProviderName: Boolean = true

  // TODO: Should this be the same as provider short name?
  override def getProviderName: String = "mo"

  override def getProviderId(implicit data: Document[JValue]): String =
    extractString(unwrap(data) \ "@id")
      .getOrElse(throw new RuntimeException(s"No ID for record: ${compact(data)}"))

  // OreAggregation

  override def dataProvider(data: Document[JValue]): ZeroToMany[EdmAgent] =
    extractStrings(unwrap(data) \ "dataProvider").map(nameOnlyAgent)

  override def dplaUri(data: Document[JValue]): ExactlyOne[URI] = mintDplaItemUri(data)

  override def hasView(data: Document[JValue]): ZeroToMany[EdmWebResource] =
    extractStrings(unwrap(data) \ "hasView" \ "@id").map(stringOnlyWebResource)

  override def isShownAt(data: Document[JValue]): ZeroToMany[EdmWebResource] =
    extractStrings(unwrap(data) \ "isShownAt").map(stringOnlyWebResource)

  override def preview(data: Document[JValue]): ZeroToMany[EdmWebResource] =
    extractStrings(unwrap(data) \ "object").map(stringOnlyWebResource)

  override def originalRecord(data: Document[JValue]): ExactlyOne[String] =
    Utils.formatJson(data)

  override def provider(data: Document[JValue]): ExactlyOne[EdmAgent] = agent

  override def sidecar(data: Document[JValue]): JValue =
    ("prehashId", buildProviderBaseId()(data)) ~ ("dplaId", mintDplaId(data))

  // SourceResource

  override def creator(data: Document[JValue]): ZeroToMany[EdmAgent] =
    extractStrings(unwrap(data)  \ "sourceResource" \ "creator").map(nameOnlyAgent)

  override def description(data: Document[JValue]): ZeroToMany[String] =
    extractStrings(unwrap(data) \ "sourceResource" \ "description")

  override def format(data: Document[JValue]): ZeroToMany[String] =
    extractStrings(unwrap(data) \ "sourceResource" \ "format")

  // TODO: Confirm with team that this is an appropriate mapping
  override def genre(data: Document[JValue]): ZeroToMany[SkosConcept] =
    extractStrings(unwrap(data) \ "sourceResource" \ "specType").map(nameOnlyConcept)

  override def identifier(data: Document[JValue]): ZeroToMany[String] =
    extractStrings(unwrap(data) \ "sourceResource" \ "identifier")

  // TODO: Confirm with team that this is an appropriate mapping
  // Initial analysis suggests that all languages have iso639_3 values
  override def language(data: Document[JValue]): ZeroToMany[SkosConcept] =
    extractStrings(unwrap(data) \ "sourceResource" \ "language" \ "iso639_3")
      .map(nameOnlyConcept)

  override def rights(data: Document[JValue]): AtLeastOne[String] =
    extractStrings(unwrap(data) \ "sourceResource" \ "rights")

  override def subject(data: Document[JValue]): ZeroToMany[SkosConcept] =
    extractStrings(unwrap(data)  \ "sourceResource" \ "subject" \ "name").map(nameOnlyConcept)

  override def date(data: Document[JValue]): ZeroToMany[EdmTimeSpan] =
     extractDate(unwrap(data) \ "sourceResource" \ "temporal")

  override def title(data: Document[JValue]): AtLeastOne[String] =
    extractStrings(unwrap(data) \ "sourceResource" \ "title")

  def agent = EdmAgent(
    name = Some("Missouri Hub"),
    uri = Some(URI("http://dp.la/api/contributor/missouri-hub"))
  )

  def extractDate(date: JValue): ZeroToMany[EdmTimeSpan] = {
    iterify(date).children.map(d =>
      EdmTimeSpan(
        begin = extractString(d \ "start"),
        end = extractString(d \ "end"),
        originalSourceDate = extractString(d \ "displayDate")
      ))
  }
}
