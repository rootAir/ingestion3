package dpla.ingestion3.mappers.providers

import dpla.ingestion3.enrichments.normalizations.StringNormalizationUtils._
import dpla.ingestion3.enrichments.normalizations.filters.{DigitalSurrogateBlockList, ExtentIdentificationList}
import dpla.ingestion3.mappers.utils.{Document, IdMinter, Mapping, XmlExtractor}
import dpla.ingestion3.messages.IngestMessageTemplates
import dpla.ingestion3.model.DplaMapData.{AtLeastOne, ExactlyOne, ZeroToMany}
import dpla.ingestion3.model._
import dpla.ingestion3.utils.Utils
import org.json4s.JValue
import org.json4s.JsonDSL._

import scala.xml._


class MtMapping extends Mapping[NodeSeq] with XmlExtractor with IdMinter[NodeSeq] with IngestMessageTemplates {

  val formatBlockList: Set[String] =
    DigitalSurrogateBlockList.termList ++
      ExtentIdentificationList.termList

  val extentAllowAlist = ExtentIdentificationList.termList

  // ID minting functions
  override def useProviderName(): Boolean = true

  override def getProviderName(): String = "mt"

  override def getProviderId(implicit data: Document[NodeSeq]): String =
    extractString(data \\ "header" \ "identifier").getOrElse(throw new RuntimeException(s"No ID for record $data"))

  // SourceResource mapping
  override def collection(data: Document[NodeSeq]): ZeroToMany[DcmiTypeCollection] =
    // first instance of <mods:relatedItem><mods:titleInfo><mods:title>
    extractStrings(data \\ "relatedItem" \ "titleInfo" \ "title")
      .map(nameOnlyCollection)
      .take(1)

  override def creator(data: Document[NodeSeq]): ZeroToMany[EdmAgent] =
    // <mods:name><mods:namePart> when <mods:role><mods:roleTerm> equals Creator
  (data \\ "name")
    .map(node => node.filter(n => (n \\ "roleTerm").text.equalsIgnoreCase("creator")))
    .flatMap(n => extractStrings(n \ "namePart"))
    .map(nameOnlyAgent)

  override def date(data: Document[NodeSeq]): ZeroToMany[EdmTimeSpan] =
  // <mods:originInfo><mods:dateCreated>
    extractStrings(data \\ "originInfo" \ "dateCreated")
      .map(stringOnlyTimeSpan)

  override def description(data: Document[NodeSeq]): ZeroToMany[String] =
  // <mods:note> @type=content
    (data \\ "metadata" \ "mods" \ "note")
      .flatMap(n => getByAttribute(n.asInstanceOf[Elem], "type", "content"))
      .flatMap(n => extractStrings(n))

  override def extent(data: Document[NodeSeq]): ZeroToMany[String] =
    extractStrings(data \\ "physicalDescription" \ "extent")
      .map(_.applyAllowFilter(extentAllowAlist))
      .filter(_.nonEmpty)

  override def format(data: Document[NodeSeq]): ZeroToMany[String] =
  // <mods:physicalDescription><form>
    extractStrings(data \\ "physicalDescription" \ "form")
      .map(_.applyBlockFilter(formatBlockList))
      .filter(_.nonEmpty)

  override def place(data: Document[NodeSeq]): ZeroToMany[DplaPlace] =
  // <mods:subject><mods:geographic>
    extractStrings(data \\ "subject" \ "geographic")
      .map(nameOnlyPlace)

  override def publisher(data: Document[NodeSeq]): ZeroToMany[EdmAgent] =
  // <mods:originInfo><mods:publisher>
    extractStrings(data \\ "originInfo" \ "publisher")
      .map(nameOnlyAgent)

  override def rights(data: Document[NodeSeq]): AtLeastOne[String] =
    // <mods:accessCondition> @type=local rights statements
    (data \\ "mods" \ "accessCondition")
      .filter({ n => filterAttribute(n, "type", "local rights statements") })
      .flatMap(extractStrings)

  override def subject(data: Document[NodeSeq]): ZeroToMany[SkosConcept] =
  // <mods:subject><mods:topic>
    extractStrings(data \\ "subject" \ "topic")
      .map(nameOnlyConcept)

  override def title(data: Document[NodeSeq]): ZeroToMany[String] =
  // <mods:titleInfo><mods:title>
    extractStrings(data \\ "mods" \ "titleInfo" \\ "title")

  override def `type`(data: Document[NodeSeq]): ZeroToMany[String] =
  // <mods:typeofresource>
    extractStrings(data \\ "typeOfResource")

  // OreAggregation
  override def dplaUri(data: Document[NodeSeq]): URI = mintDplaItemUri(data)

  override def dataProvider(data: Document[NodeSeq]): ZeroToMany[EdmAgent] =
  // <mods:note> @type=ownership
  (data \\ "metadata" \ "mods" \ "note")
    .flatMap(node => getByAttribute(node.asInstanceOf[Elem], "type", "ownership"))
    .flatMap(extractStrings)
    .map(nameOnlyAgent)

  override def edmRights(data: Document[NodeSeq]): ZeroToMany[URI] = {
    // <mods:accessCondition>@type=use and reproduction @xlink:href =[this is the value to be mapped]
    (data \\ "metadata" \ "mods" \ "accessCondition")
      .flatMap(node => getByAttribute(node.asInstanceOf[Elem], "type", "use and reproduction"))
      .flatMap(node => node.attribute(node.getNamespace("xlink"), "href"))
      .flatMap(n => extractString(n.head))
      .map(URI)
  }

  override def isShownAt(data: Document[NodeSeq]): ZeroToMany[EdmWebResource] =
    // <mods:location><mods:url> @access=object in context @usage=primary display
    (data \\ "location" \ "url")
      .flatMap(node => getByAttribute(node.asInstanceOf[Elem], "usage", "primary display"))
      .flatMap(node => getByAttribute(node.asInstanceOf[Elem], "access", "object in context"))
      .flatMap(extractStrings)
      .map(stringOnlyWebResource)

  override def originalRecord(data: Document[NodeSeq]): ExactlyOne[String] = Utils.formatXml(data)

  override def preview(data: Document[NodeSeq]): ZeroToMany[EdmWebResource] =
  // <mods:location><mods:url> @access=preview
  (data \\ "location" \ "url")
    .flatMap(node => getByAttribute(node.asInstanceOf[Elem], "access", "preview"))
    .flatMap(extractStrings)
    .map(stringOnlyWebResource)

  override def provider(data: Document[NodeSeq]): ExactlyOne[EdmAgent] = agent

  override def sidecar(data: Document[NodeSeq]): JValue =
    ("prehashId" -> buildProviderBaseId()(data)) ~ ("dplaId" -> mintDplaId(data))

  // Helper method
  def agent = EdmAgent(
    name = Some("Big Sky Country Digital Network"),
    uri = Some(URI("http://dp.la/api/contributor/mt"))
  )
}
