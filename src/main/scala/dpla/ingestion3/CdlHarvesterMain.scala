package dpla.ingestion3

import java.io.File

import dpla.ingestion3.harvesters.api._
import dpla.ingestion3.harvesters.file.NaraFileHarvestMain.getAvroWriter
import dpla.ingestion3.utils.{FlatFileIO, Utils}
import org.apache.avro.Schema
import org.apache.avro.file.DataFileWriter
import org.apache.avro.generic.GenericRecord
import org.apache.log4j.LogManager
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._
import org.rogach.scallop.{ScallopConf, ScallopOption}


object CdlHarvesterMain extends ApiHarvester {

  private val logger = LogManager.getLogger(getClass)

  /**
    * Driver for harvesting from California Digital Library's Solr endpoint
    * (https://solr.calisphere.org/solr/query).
    *
    * Expects command line args of
    * --apiKey - The CDL api key
    * --rows - Number of records to return per page. Defaults to 10
    * --outputFile - Location to save the avro. Defaults to ./out
    * --query - Solr query to select the records. Defaults to *:*
    *
    * @param args
    */
  def main(args: Array[String]): Unit = {
    // Parse and set the command line options
    val conf = new CdlHarvestConf(args)
    val outFile = new File(conf.outputFile.getOrElse("out"))
    val queryParams = Map(
      "query" -> conf.query.getOrElse("*:*"),
      "rows" -> conf.rows.getOrElse("100"),
      "api_key" -> conf.apiKey.getOrElse("MISSING API KEY")
    )

    // Must do this before setting the avroWriter
    outFile.getParentFile.mkdir()
    Utils.deleteRecursively(outFile)

    val schemaStr = new FlatFileIO().readFileAsString("/avro/OriginalRecord.avsc")
    val schema = new Schema.Parser().parse(schemaStr)
    val avroWriter = getAvroWriter(outFile, schema)

    startHarvest(outFile, queryParams, avroWriter, schema)
  }

  /**
    *
    * @param queryParams Map of query parameters
    * @param avroWriter Writer
    * @param schema Schema to apply to data
    */
  override def doHarvest(queryParams: Map[String, String],
                         avroWriter: DataFileWriter[GenericRecord],
                         schema: Schema): Unit = {
    implicit val formats = DefaultFormats

    val cdl = new CdlHarvester(queryParams)

    // Mutable vars for controlling harvest loop
    var continueHarvest = true
    var cursorMark = "*"

    // Runtime tracking
    val startTime = System.currentTimeMillis()

    while(continueHarvest) cdl.harvest(cursorMark) match {
      case error: ApiError with ApiResponse =>
        logger.error("Error returned by request %s\n%s\n%s".format(
          error.errorSource.url.getOrElse("Undefined url"),
          error.errorSource.queryParams,
          error.message
        ))
        continueHarvest = false
      case src: ApiSource with ApiResponse =>
        src.text match {
          case Some(docs) =>
            val json = parse(docs)
            val cdlRecords = (json \\ "docs").children.map(doc => {
              ApiRecord((doc \\ "identifier").toString, compact(render(doc)))
            })

            saveOut(avroWriter, cdlRecords, schema, "cdl", "application_json")

            // Loop control
            cursorMark = (json \\ "cursorMark").extract[String]
            val nextCursorMark = (json \\ "nextCursorMark").extract[String]

            cursorMark.matches(nextCursorMark) match {
              case true => continueHarvest = false
              case false => cursorMark = nextCursorMark
            }
          case None =>
            logger.error(s"The body of the response is empty. Stopping run.\nCdlSource >> ${src.toString}")
            continueHarvest = false
        }
      case _ =>
        logger.error("Harvest returned None")
        continueHarvest = false
    }
  }
}

/**
  * CDL harvester command line parameters
  *
  * @param arguments
  */
class CdlHarvestConf(arguments: Seq[String]) extends ScallopConf(arguments) {
  val outputFile: ScallopOption[String] = opt[String](
    "outputFile",
    required = true,
    noshort = true,
    validate = _.endsWith(".avro"),
    descr = "Output file must end with .avro"
  )

  val apiKey: ScallopOption[String] = opt[String](
    "apiKey",
    required = true,
    noshort = true,
    validate = _.nonEmpty
  )

  val query: ScallopOption[String] = opt[String](
    "query",
    required = false,
    noshort = true,
    validate = _.nonEmpty
  )

  val rows: ScallopOption[String] = opt[String](
    "rows",
    required = false,
    noshort = true,
    validate = _.nonEmpty
  )

  verify()
}