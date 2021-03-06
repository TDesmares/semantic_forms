/**
 *
 */

package deductions.runtime.abstract_syntax

import scala.util.Try
import org.w3.banana.RDF
import org.w3.banana.RDFOps
import org.w3.banana.RDFStore
import org.w3.banana.URIOps
import UnfilledFormFactory.defaultInstanceURIPrefix
import org.w3.banana.SparqlGraphModule
import org.w3.banana.SparqlEngine
import org.w3.banana.SparqlOps

/**
 * @author j.m. Vanel
 *
 */
object UnfilledFormFactory {
  /** TODO put in config. */
  var defaultInstanceURIPrefix = "http://assemblee-virtuelle.org/resource/"

  /** make a unique Id with given prefix, currentTimeMillis() and nanoTime() */
  def makeId(instanceURIPrefix: String): String = {
    instanceURIPrefix + System.currentTimeMillis() + "-" + System.nanoTime() // currentId = currentId + 1
  }
}

/** Factory for an Unfilled Form */
class UnfilledFormFactory[Rdf <: RDF, DATASET](graph: Rdf#Graph,
  preferedLanguage: String = "en",
  instanceURIPrefix: String = defaultInstanceURIPrefix)(implicit ops: RDFOps[Rdf],
    uriOps: URIOps[Rdf],
    rdfStore: RDFStore[Rdf, Try, DATASET],
    sparqlGraph: SparqlEngine[Rdf, Try, Rdf#Graph],
    sparqlOps: SparqlOps[Rdf])
    extends FormSyntaxFactory[Rdf](graph: Rdf#Graph, preferedLanguage) {

  import formConfiguration._

  /**
   * create Form from a class URI,
   *  looking up for Form Configuration within RDF graph in this class
   */
  def createFormFromClass(classs: Rdf#URI): FormModule[Rdf#Node, Rdf#URI]#FormSyntax = {
    val (propsListInFormConfig, formConfig) = lookPropertieslistFormInConfiguration(classs)
    if (propsListInFormConfig.isEmpty) {
      val props = fieldsFromClass(classs, graph)
      createFormDetailed(ops.makeUri(makeId), props toSeq, classs)
    } else
      createFormDetailed(ops.makeUri(makeId), propsListInFormConfig.toSeq, classs, formConfig = formConfig)
  }

  def makeId: String = {
    UnfilledFormFactory.makeId(instanceURIPrefix)
  }
}