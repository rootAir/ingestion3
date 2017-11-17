package dpla.ingestion3.harvesters.oai.refactor

import java.net.URL

import dpla.ingestion3.utils.HttpUtils
import org.apache.http.client.utils.URIBuilder

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object OaiMultiPageResponseBuilder {

  /**
    * Main entry point.
    * Get all pages of results from an OAI feed.
    * OaiPages may contain an OAI error (HTTP code 200) or invalid XML.
    *
    * Makes an initial call to the feed to get the first page of results.
    * For this and all subsequent pages, calls the next page if a resumption
    * token is present.
    *
    * @param baseParams Map[String, String]
    *                   Params that are required for every OAI request, including
    *                   those with resumption tokens.
    *                   Per the OAI spec, the two required params are "verb" and
    *                   "endpoint".
    *
    * @param opts Map[String, String]
    *             Optional params that can be included in an initial OAI request,
    *             but not in any subsequent requests.
    *             Currently, the only supported option for a records request is
    *             metadataPrefix.
    *
    * @return Un-parsed response page from OAI requests, including OaiPages
    *         and OaiErrors.
    */
  def getResponse(baseParams: Map[String, String], opts: Map[String, String] = Map()):
    List[Either[OaiError, OaiPage]] = {

    @tailrec
    def loop(data: List[Either[OaiError, OaiPage]]): List[Either[OaiError, OaiPage]] = {

      data.headOption match {
        // Stops the harvest if an OaiError was trapped and returns everything
        // harvested up that this point plus the error.
        case Some(Left(_)) => data
        // If it was a valid page response then extract data and call the next page.
        case Some(Right(previous)) =>
          val text = previous.page
          val token = OaiXmlParser.getResumptionToken(text)

          token match {
            // If the page does not contain a token, return everything harvested
            // up to this point.
            case None => data
            // Otherwise, get the next page.
            case Some(token) =>
              // Resumption tokens are exclusive, meaning a request with a token
              // cannot have any additional optional args.
              val nextParams = baseParams + ("resumptionToken" -> token)
              val nextResponse = getSinglePage(nextParams)
              loop(nextResponse :: data)
          }
        // This is only reached if something really strange happened
        // If there is an error or unexpected response type, return all data
        // collected up to this point (including the error or unexpected response).
        case _ => data
      }
    }

    // The initial request must include all optional args.
    val firstParams = baseParams ++ opts
    val firstResponse = getSinglePage(firstParams)
    loop(List(firstResponse))
  }

  /**
    * Get a single-page, unparsed response from the OAI feed, or an error if
    * one occurs.
    *
    * The page may contain an OAI error (HTTP code 200) or invalid XML.
    *
    * @param queryParams parameters for a single OAI request.
    * @return OaiPage or OaiError
    */
  def getSinglePage(queryParams: Map[String, String]): Either[OaiError, OaiPage] = {
    getUrl(queryParams) match {
      // Error building URL
      case Left(error) => Left(error)
      case Right(url) => {
        HttpUtils.makeGetRequest(url) match {
          // HTTP error
          case Failure(e) => Left(OaiError(e.toString, Some(url.toString)))
          case Success(page) => Right(OaiPage(page))
        }
      }
    }
  }

  /**
    * Tries to build a URL from the parameters
    *
    * @param queryParams HTTP parameters
    * @return Either[OaiError, URL]
    */
  def getUrl(queryParams: Map[String, String]): Either[OaiError, URL] =
    Try { buildUrl(queryParams) } match {
      case Success(url) => Right(url)
      case Failure(e) =>
        val queryString = queryParams.map(_.productIterator.mkString(":")).mkString("|")
        val errorString = e.toString
        val msg = s"Failed to make URL with params $queryString.  $errorString"
        Left(OaiError(msg))
  }

  /**
    * Builds an OAI request
    *
    * @param params Map of parameters needed to construct the URL
    *               OAI request verbs
    *               See https://www.openarchives.org/OAI/openarchivesprotocol.html#ProtocolMessages
    * @return URL
    */
  def buildUrl(params: Map[String, String]): URL = {
    val url = new URL(params.getOrElse("endpoint",
      throw new RuntimeException("Endpoint not found")))

    val verb = params.getOrElse("verb",
      throw new RuntimeException("Verb not found"))

    // Optional properties.
    val metadataPrefix: Option[String] = params.get("metadataPrefix")
    val resumptionToken: Option[String] = params.get("resumptionToken")
    val set: Option[String] = params.get("set")

    // Build the URL
    val urlParams = new URIBuilder()
      .setScheme(url.getProtocol)
      .setHost(url.getHost)
      .setPort(url.getPort)
      .setPath(url.getPath)
      .setParameter("verb", verb)

    // Set optional properties.
    resumptionToken.foreach(t => urlParams.setParameter("resumptionToken", t))
    set.foreach(s => urlParams.setParameter("set", s))
    metadataPrefix.foreach(prefix => urlParams.setParameter("metadataPrefix", prefix))

    urlParams.build.toURL
  }
}