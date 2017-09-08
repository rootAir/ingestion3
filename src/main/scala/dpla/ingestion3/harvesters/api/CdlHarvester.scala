
package dpla.ingestion3.harvesters.api

import java.net.URI

import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet}
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils

import scala.util.{Failure, Success, Try}

/**
  * Class for harvesting records from the California Digital Library's Solr API
  *
  * Calisphere API documentation
  * https://help.oac.cdlib.org/support/solutions/articles/9000101639-calisphere-api
  *
  * @param queryParams - Query parameters (q, rows, cursorMark etc.)
  */
class CdlHarvester(val queryParams: Map[String, String] = Map()) {

  /**
    * Get a single-page, un-parsed response from the OAI feed, or an error if
    * one occurs.
    *
    * @param cursorMark Uses cursor and not start/offset to paginate. Used to work around Solr
    *                   deep-paging performance issues.
    * @return ApiSource or ApiError
    */
  def harvest(cursorMark: String): ApiResponse = {
    getUri(queryParams.updated("cursorMark", cursorMark)) match {
      // Error building URL
      case Failure(e) =>
        val source = ApiSource(queryParams)
        ApiError(e.toString, source)
      case Success(url) => {
        getResponse(url) match {
          case Failure(e) =>
            ApiError(e.toString, ApiSource(queryParams, Some(url.toString)))
          case Success(response) => response.isEmpty match {
            case true => ApiError("Response body is empty", ApiSource(queryParams, Some(url.toString)))
            case false => ApiSource(queryParams, Some(url.toString), Some(response))
          }
        }
      }
    }
  }

  /**
    * Builds query URI from parameters
    *
    * @param queryParams
    * @return URI
    */
  def getUri(queryParams: Map[String, String]): Try[URI] = Try {
    new URIBuilder()
      .setScheme("https")
      .setHost("solr.calisphere.org")
      .setPath("/solr/query")
      .setParameter("q", queryParams.getOrElse("query", "*:*"))
      .setParameter("cursorMark", queryParams.getOrElse("cursorMark", "*"))
      .setParameter("rows", queryParams.getOrElse("rows", "10"))
      .setParameter("sort", "id desc")
      .build()
    }

  /**
    * Makes request and returns stringified JSON response
    *
    * @param uri
    * @return
    */
  def getResponse(uri: URI): Try[String] = Try {
    val httpclient = HttpClients.createDefault()
    val get = new HttpGet(uri)
    get.addHeader("X-Authentication-Token", queryParams.getOrElse("api_key", ""))
    var response: CloseableHttpResponse = null
    try {
      response = httpclient.execute(get)
      val entity = response.getEntity
      EntityUtils.toString(entity)
    } finally {
      IOUtils.closeQuietly(response)
    }
  }
}