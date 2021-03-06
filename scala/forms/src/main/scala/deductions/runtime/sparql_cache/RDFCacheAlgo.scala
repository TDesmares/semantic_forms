package deductions.runtime.sparql_cache

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.apache.http.impl.cookie.DateUtils
import org.w3.banana.RDFModule
import org.w3.banana.RDFOpsModule
import org.w3.banana.RDFXMLReaderModule
import org.w3.banana.TurtleReaderModule
import org.w3.banana.XSDPrefix
import deductions.runtime.jena.JenaHelpers
import deductions.runtime.jena.RDFStoreLocalJena1Provider
import deductions.runtime.dataset.RDFStoreLocalProvider
import org.w3.banana.OWLPrefix
import org.apache.log4j.Logger
import org.w3.banana.RDF
import org.w3.banana.jena.JenaModule

/** */
trait RDFCacheDependencies
  extends RDFModule
  with RDFOpsModule
  with TurtleReaderModule
  with RDFXMLReaderModule

/** depends on generic Rdf but, through RDFStoreLocalJena1Provider and JenaHelpers, on Jena :( TODO remove Jena */
trait RDFCacheAlgo[Rdf <: RDF, DATASET] extends RDFStoreLocalProvider[Rdf, DATASET]
    with RDFCacheDependencies
    with RDFStoreHelpers[Rdf, DATASET] {

  import ops._
  import rdfStore.transactorSyntax._
  import rdfStore.graphStoreSyntax._
  import rdfStore.sparqlEngineSyntax._
  import scala.concurrent.ExecutionContext.Implicits.global

  val timestampGraphURI = "http://deductions-software.com/timestampGraph"
  lazy val xsd = XSDPrefix[Rdf]
  lazy val owl = OWLPrefix[Rdf]

  /** with transaction */
  def isGraphInUse(uri: String): Boolean = {
    isGraphInUse(makeUri(uri))
  }

  /** with transaction */
  def isGraphInUse(uri: Rdf#URI) = {
    dataset.r({
      for (graph <- dataset.getGraph(uri)) yield {
        val uriGraphIsEmpty = graph.size == 0
        println("uriGraphIsEmpty " + uriGraphIsEmpty)
        !uriGraphIsEmpty
      }
    }).flatMap { identity }.getOrElse(false)
  }

  /**
   * retrieve URI from a graph named by itself;
   * or download and store URI only if corresponding graph is empty,
   * with transaction
   * TODO save timestamp in another Dataset
   */
  def retrieveURI(uri: Rdf#URI, dataset: DATASET): Try[Rdf#Graph] = {
    dataset.rw({
      for (graph <- dataset.getGraph(uri)) yield {
        val uriGraphIsEmpty = graph.size == 0
        println("uriGraphIsEmpty " + uriGraphIsEmpty)
        if (uriGraphIsEmpty) {
          val g = storeURINoTransaction(uri, uri, dataset)
          println("Graph at URI was downloaded, new addition: " + uri + " , size " + g.size)
          addTimestampToDatasetNoTransaction(uri, dataset)
          g
        } else {
          updateLocalVersion(uri, dataset)
          graph
        }
      }
    }).flatMap { identity }
  }

  /**
   * according to timestamp download if outdated;
   * with transaction, in a Future
   */
  def updateLocalVersion(uri: Rdf#URI, dataset: DATASET) = {
    val future = Future {
      dataset.rw({
        val localTimestamp = getTimestampFromDataset(uri, dataset)
        localTimestamp match {
          case Success(long) =>
            val l = lastModified(uri.toString(), 500)
            println(s"$uri $localTimestamp: $localTimestamp; lastModified: $l.")
            if (l._1) {
              for (lts <- localTimestamp) {
                if (l._2 > lts) {
                  storeURINoTransaction(uri, uri, dataset)
                  println(s"$uri was outdated; downloaded.")
                }
              }
            }
          case Failure(fail) =>
            storeURINoTransaction(uri, uri, dataset)
            println(s"$uri had no localTimestamp ($fail); downloaded.")
        }
      })
    }
    future
  }

  /**
   * download and store URI, with transaction, in a graph named by its URI minus the # part,
   *  and store the timestamp from HTTP HEAD request;
   * transactional,
   * load also the direct owl:imports , but not recursively ( as EulerGUI IDE does )
   *
   * TODO rename storeURIInNamedGraph
   */
  def storeURI(uri: Rdf#URI, dataset: DATASET): Rdf#Graph = {
    val model = storeURI(uri, uri, dataset)
    println("RDFCacheAlgo.storeURI " + uri + " size: " + model.size)
    val r = dataset.rw({
      val it = find(model, ANY, owl.imports, ANY)
      for (importedOntology <- it) {
        try {
          Logger.getRootLogger().info(s"Before Loading imported Ontology $importedOntology")
          foldNode(importedOntology.subject)(ontoMain => Some(ontoMain), x => None, x => None) match {
            case Some(ontoMain) =>
              foldNode(importedOntology.objectt)(onto => storeURINoTransaction(onto, onto, dataset),
                identity, identity)
            case None =>
          }
        } catch {
          case e: Throwable => println(e)
        }
      }
    })
    addTimestampToDataset(uri, dataset)
    model
  }

  /**
   * add timestamp to dataset (actually a dedicated Graph timestampGraphURI ),
   *  with transaction
   */
  private def addTimestampToDataset(uri: Rdf#URI, dataset: DATASET) = {
    dataset.rw({
      addTimestampToDatasetNoTransaction(uri, dataset)
    })
  }

  def addTimestampToDatasetNoTransaction(uri: Rdf#URI, dataset: DATASET) = {
    val time = lastModified(fromUri(uri), 1000)
    dataset.appendToGraph(makeUri(timestampGraphURI),
      makeGraph(Seq(makeTriple(
        uri,
        makeUri(timestampGraphURI),
        makeLiteral(time._2.toString, xsd.integer)))))
  }

  /**
   * get timestamp from dataset (actually a dedicated Graph timestampGraphURI ),
   *  No Transaction
   */
  private def getTimestampFromDataset(uri: Rdf#URI, dataset: DATASET): Try[Long] = {
    val queryString =
      s"""
         |SELECT DISTINCT ?ts WHERE {
         |  graph <$timestampGraphURI> {
         |    <$uri> <$timestampGraphURI> ?ts .
         |  }
         |}""".stripMargin
    val result = for {
      query <- sparqlOps.parseSelect(queryString)
      solutions <- dataset.executeSelect(query, Map())
    } yield {
      solutions.toIterable.map {
        row => row("ts") getOrElse sys.error("getTimestampFromDataset: " + row)
      }
    }
    //    val r = 
    result.map { x => java.lang.Long.valueOf(x.next.toString()) }
  }

  /**
   * @return pair:
   * _1 : true <=> no error
   * _2 : timestamp from HTTP HEAD request
   *  NOTE elsewhere akka HTTP client is used
   */
  private def lastModified(url0: String, timeout: Int): (Boolean, Long) = {
    val url = url0.replaceFirst("https", "http"); // Otherwise an exception may be thrown on invalid SSL certificates.
    try {
      val connection0 = new URL(url).openConnection()
      val connection = connection0.asInstanceOf[HttpURLConnection]
      connection.setConnectTimeout(timeout);
      connection.setReadTimeout(timeout);
      connection.setRequestMethod("HEAD");
      val responseCode = connection.getResponseCode();
      def tryHeaderField(headerName: String): (Boolean, Boolean, Long) = {
        val dateString = connection.getHeaderField(headerName)
        if (dateString != null) {
          val date: java.util.Date = DateUtils.parseDate(dateString) // from apache http-components
          println("RDFCacheAlgo.lastModified: responseCode: " + responseCode +
            ", date " + date +
            "; url " + url)
          (true, 200 <= responseCode && responseCode <= 399, date.getTime())
        } else (false, false, Long.MaxValue)
      }
      // TODO should be a better way in Scala:
      val lm = tryHeaderField("Last-Modified")
      val r = if (lm._1) {
        (lm._2, lm._3)
      } else {
        val lm2 = tryHeaderField("Date")
        if (lm2._1) {
          (lm2._2, lm2._3)
        } else (false, Long.MaxValue)
      }
      return r
    } catch {
      case exception: IOException => (false, Long.MinValue)
      case e: Throwable => throw e
    }
  }
}

//object RDFCache extends RDFModule
//  with RDFOpsModule {
//   /** unused currently ... */
//  def getGraphURI(classs: Rdf#URI) : String = {
//    Ops.getFragment(classs) match {
//      case Some(frag) =>
////        classs.getURI().substring(frag.length() + 1)
//        classs.toString().substring(frag.length() + 1)
//      case None => ""
//    }
//  }
//}
