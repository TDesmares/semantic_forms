package deductions.runtime.services

import java.net.URLEncoder
import org.w3.banana.RDFOpsModule
import deductions.runtime.jena.RDFStoreObject
import org.w3.banana.SparqlGraphModule
import org.w3.banana.SparqlOpsModule
import org.w3.banana.PointedGraph
import org.w3.banana.diesel._
import org.scalatest.FunSuite
import org.w3.banana.jena.JenaModule
import deductions.runtime.jena.JenaHelpers
import deductions.runtime.jena.RDFStoreLocalJena1Provider
import org.w3.banana.jena.Jena
import com.hp.hpl.jena.query.Dataset
import deductions.runtime.jena.RDFCache
//import deductions.runtime.jena.RDFStoreLocalProvider

class TestFormSaver
    extends FunSuite
    with RDFCache
    with RDFOpsModule
    with SparqlGraphModule
    with SparqlOpsModule //    with RDFStoreLocalProvider[Rdf, DATASET]
    with JenaModule //    with RDFStoreLocalProvider 
    with JenaHelpers
    with RDFStoreLocalJena1Provider
    with FormSaver[Jena, Dataset] {
  import ops._

  lazy val fs = this // new FormSaver[Jena, Dataset] {}

  val uri = "http://jmvanel.free.fr/jmv.rdf#me"
  retrieveURI(makeUri(uri), dataset)
  val map: Map[String, Seq[String]] = Map(
    "uri" -> Seq(encode(uri)),
    "url" -> Seq(encode(uri)),
    encode("LIT-http://xmlns.com/foaf/0.1/familyName") -> Seq("Van Eel"),
    encode("ORIG-LIT-http://xmlns.com/foaf/0.1/familyName") -> Seq("Vanel")
  )

  test("remove the old value from TDB when saving") {
    fs.saveTriples(map)
    rdfStore.r(dataset, {
      //    store.readTransaction {
      val graph = rdfStore.getGraph(dataset, makeUri("urn:x-arq:UnionGraph"))
      val pg = PointedGraph[Rdf](makeUri(uri), graph.get)
      val objects = pg / makeUri("http://xmlns.com/foaf/0.1/familyName")
      val familyNames = objects.map(_.pointer).toSet
      // assert that foaf:familyName "Vanel" has been removed from Triple store
      assert(familyNames.size == 1)
      assert(familyNames.contains(makeLiteral("Van Eel", xsd.string)))
    })
  }
  ////
  def encode(uri: String) = { URLEncoder.encode(uri, "utf-8") }
}
