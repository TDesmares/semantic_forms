/* copyright the Déductions Project
under GNU Lesser General Public License
http://www.gnu.org/licenses/lgpl.html
$Id$
 */
package deductions.runtime.abstract_syntax

import scala.collection.mutable
import org.apache.log4j.Logger
import org.w3.banana.OWLPrefix
import org.w3.banana.PointedGraph
import org.w3.banana.RDF
import org.w3.banana.RDFDSL
import org.w3.banana.RDFOps
import org.w3.banana.RDFPrefix
import org.w3.banana.RDFSPrefix
import org.w3.banana.URIOps
import org.w3.banana.XSDPrefix
import org.w3.banana.diesel._
import org.w3.banana.Prefix
import org.w3.banana.SparqlOps
import org.w3.banana.syntax._
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import org.w3.banana.LocalNameException
import org.w3.banana.RDFStore
import deductions.runtime.jena.RDFStoreObject
import deductions.runtime.utils.RDFHelpers
import org.w3.banana.SparqlEngine

object FormSyntaxFactory {
  /** vocabulary for form specifications */
  val formVocabPrefix = "http://deductions-software.com/ontologies/forms.owl.ttl#"
}

/**
 * Factory for an abstract Form Syntax;
 * the main class here;
 * NON transactional
 */
class FormSyntaxFactory[Rdf <: RDF](val graph: Rdf#Graph, preferedLanguage: String = "en",
  val defaults: FormDefaults = FormModule.formDefaults)(implicit val ops: RDFOps[Rdf],
    val uriOps: URIOps[Rdf] //  
    , val sparqlGraph: SparqlEngine[Rdf, Try, Rdf#Graph],
    val sparqlOps: SparqlOps[Rdf])

    extends FormModule[Rdf#Node, Rdf#URI]
    with FieldsInference[Rdf]
    with RangeInference[Rdf] {

  //  /** TODO displaying rdf:type fields should be configurable for editing, and displayed unconditionally for non editing */
  //  val displayRdfType = false

  import ops._

  lazy val nullURI = ops.URI("") // http://null.com#")
  val literalInitialValue = "" // ..empty.."
  override def makeURI(n: Rdf#Node): Rdf#URI = URI(ops.foldNode(n)(
    fromUri(_), fromBNode(_), fromLiteral(_)._1))

  val rdfs = RDFSPrefix[Rdf]
  val owl = OWLPrefix[Rdf]
  val rdf = RDFPrefix[Rdf]

  import FormSyntaxFactory._
  val formPrefix: Prefix[Rdf] = Prefix("form", formVocabPrefix)
  val rdfh = { val gr = graph; new RDFHelpers[Rdf] { val graph = gr } }
  import rdfh._
  println("FormSyntaxFactory: preferedLanguage: " + preferedLanguage)

  /** create Form from an instance (subject) URI */
  def createForm(subject: Rdf#Node,
    editable: Boolean = false,
    formGroup: Rdf#URI = nullURI): FormModule[Rdf#Node, Rdf#URI]#FormSyntax = {

    val classs = classFromSubject(subject) // TODO several classes

    val propsFromConfig = formConfiguration.lookPropertieslistFormInConfiguration(classs)._1
    val propsFromSubject = fieldsFromSubject(subject, graph)
    val propsFromClass =
      if (editable) {
        fieldsFromClass(classs, graph)
      } else Seq()

    createFormDetailed(subject, (propsFromConfig ++ propsFromSubject ++ propsFromClass).distinct, classs, formGroup)
  }

  /**
   * create Form With Detailed arguments: Given Properties;
   * For each given property (props)
   *  look at its rdfs:range ?D
   *  see if ?D is a datatype or an OWL or RDFS class
   *  ( used for creating an empty Form from a class URI )
   */
  def createFormDetailed(subject: Rdf#Node,
    props: Iterable[Rdf#URI], classs: Rdf#URI,
    formGroup: Rdf#URI = nullURI,
    formConfig: Rdf#Node = URI("")): FormModule[Rdf#Node, Rdf#URI]#FormSyntax = {

    Logger.getRootLogger().info(s"createForm subject $subject, props $props")
    val valuesFromFormGroup = possibleValuesFromFormGroup(formGroup: Rdf#URI, graph)
    val entries = for (prop <- props) yield {
      Logger.getRootLogger().info(s"createForm subject $subject, prop $prop")
      val ranges = objectsQuery(prop, rdfs.range)
      val rangesSize = ranges.size
      System.err.println(
        if (rangesSize > 1) {
          "WARNING: ranges " + ranges + " for property " + prop + " are multiple."
        } else if (rangesSize == 0) {
          "WARNING: There is no range for property " + prop
        } else "")
      makeEntries(subject, prop, ranges, valuesFromFormGroup)
    }
    val fields = entries.flatMap { identity }
    val fields2 = // if (displayRdfType)
      addTypeTriple(subject, classs, fields)
    val formSyntax = FormSyntax(subject, fields2, classs)
    updateFormFromConfig(formSyntax, formConfig)
  }

  val formConfiguration = new FormConfigurationFactory[Rdf](graph)

  /**
   * update given Form,
   * looking up for Form Configuration within RDF graph in this class
   * eg in :
   *  <pre>
   *  &lt;topic_interest> :fieldAppliesToForm &lt;personForm> ;
   *   :fieldAppliesToProperty foaf:topic_interest ;
   *   :widgetClass form:DBPediaLookup .
   *  <pre>
   */
  private def updateFormFromConfig(formSyntax: FormSyntax, formConfig: Rdf#Node): FormSyntax = {
    for (field <- formSyntax.fields) {
      val fieldSpecs = formConfiguration.lookFieldSpecInConfiguration(field.property)
      if (!fieldSpecs.isEmpty)
        println(s"""updateFormFromConfig field $field -- fieldSpecs size ${fieldSpecs.size}
        $fieldSpecs""")
      fieldSpecs.map {
        fieldSpec =>
          val triples = find(graph, fieldSpec.subject, ANY, ANY).toSeq
          for (t <- triples) {
            //            println(s"updateFormFromConfig fieldSpec $fieldSpec -- triple $t")
            field.addTriple(t.subject, t.predicate, t.objectt)
          }
          // TODO each feature should be in a different file
          for (t <- triples) {
            if (t.predicate == formPrefix("widgetClass")
              && t.objectt == formPrefix("DBPediaLookup")) {
              def replace[T](s: Seq[T], occurence: T, replacement: T): Seq[T] = {
                s.map { i => if (i == occurence) replacement else i }
              }
              val rep = field.asResource
              rep.widgetType = DBPediaLookup
              formSyntax.fields = replace(formSyntax.fields, field, rep)
            }
          }
      }
    }
    val triples = find(graph, formConfig, ANY, ANY).toSeq
    // TODO try the Object - semantic mapping of Banana-RDF
    val uriToCardinalities = Map[Rdf#Node, Cardinality] {
      formPrefix("zeroOrMore") -> zeroOrMore;
      formPrefix("oneOrMore") -> oneOrMore;
      formPrefix("zeroOrOne") -> zeroOrOne;
      formPrefix("exactlyOne") -> exactlyOne
    }
    for (t <- triples) {
      println("updateFormForClass formConfig " + t)
      if (t.predicate == formPrefix("defaultCardinality")) {
        formSyntax.defaults.defaultCardinality = uriToCardinalities.getOrElse(t.objectt, zeroOrOne)
      }
    }
    formSyntax
  }

  /**
   * add Triple: <subject> rdf:type <classs>
   *  @return augmented fields argument
   */
  def addTypeTriple(subject: Rdf#Node, classs: Rdf#URI,
    fields: Iterable[Entry]): Seq[Entry] = {
    val alreadyInDatabase = !find(graph, subject, rdf.typ, ANY).isEmpty
    if (defaults.displayRdfType || !alreadyInDatabase) {
      val classFormEntry = new ResourceEntry(
        // TODO not I18N:
        "type", "class",
        rdf.typ, ResourceValidator(Set(owl.Class)), classs,
        alreadyInDatabase = alreadyInDatabase)
      (fields ++ Seq(classFormEntry)).toSeq
    } else fields.toSeq
  }

  /** find fields from given Instance subject */
  private def fieldsFromSubject(subject: Rdf#Node, graph: Rdf#Graph): Seq[Rdf#URI] = {
    ops.getPredicates(graph, subject).toSet.toSeq
  }

  /**
   * try to get rdfs:label, comment, rdf:type,
   * or else display terminal Part of URI as label;
   *  taking in account multi-valued properties
   */
  private def makeEntries(subject: Rdf#Node, prop: Rdf#URI, ranges: Set[Rdf#Node],
    valuesFromFormGroup: Seq[(Rdf#Node, Rdf#Node)]): Seq[Entry] = {
    Logger.getRootLogger().info(s"makeEntry subject $subject, prop $prop")
    val label = getPreferedLanguageFromSubjectAndPredicate(prop, rdfs.label, terminalPart(prop))
    val comment = getPreferedLanguageFromSubjectAndPredicate(prop, rdfs.comment, "")
    val propClasses = objectsQuery(prop, RDFPrefix[Rdf].typ)
    val objects = objectsQuery(subject, prop)
    val result = scala.collection.mutable.ArrayBuffer[Entry]()
    val rangeClasses = objectsQueries(ranges, RDFPrefix[Rdf].typ)

    for (obj <- objects) if (prop != rdf.typ) addOneEntry(obj, valuesFromFormGroup)
    if (objects isEmpty) addOneEntry(nullURI, valuesFromFormGroup)

    def makeBN(label: String, comment: String,
      property: ObjectProperty, validator: ResourceValidator,
      value: Rdf#BNode,
      typ: Rdf#Node = nullURI) = {
      new BlankNodeEntry(label, comment, prop, ResourceValidator(ranges), value,
        type_ = typ) {
        override def getId: String = ops.fromBNode(value.asInstanceOf[Rdf#BNode])
      }
    }

    def firstType = firstNodeOrElseNullURI(ranges)

    def addOneEntry(object_ : Rdf#Node,
      valuesFromFormGroup: Seq[(Rdf#Node, Rdf#Node)] //        formGroup: Rdf#URI
      ) = {
      def literalEntry = {
        // TODO match graph pattern for interval datatype ; see issue #17
        // case t if t == ("http://www.bizinnov.com/ontologies/quest.owl.ttl#interval-1-5") =>
        new LiteralEntry(label, comment, prop, DatatypeValidator(ranges),
          getLiteralNodeOrElse(object_, literalInitialValue),
          type_ = firstType)
      }
      def resourceEntry = {
        ops.foldNode(object_)(
          object_ =>
            if (isBN(firstType)) {
              println(s""" resourceEntry makeBN "$label" """)
              makeBN(label, comment, prop, ResourceValidator(ranges), toBN(object_),
                typ = firstType)
            } else
              new ResourceEntry(label, comment, prop, ResourceValidator(ranges), object_,
                alreadyInDatabase = true, valueLabel = instanceLabel(object_),
                type_ = rdfh.nodeSeqToURISeq(ranges).headOption.getOrElse(nullURI)),
          object_ => makeBN(label, comment, prop, ResourceValidator(ranges), object_,
            typ = firstType),
          object_ => literalEntry
        )
      }
      val xsdPrefix = XSDPrefix[Rdf].prefixIri
      val rdf = RDFPrefix[Rdf]
      val rdfs = RDFSPrefix[Rdf]

      val entry = rangeClasses match {
        case _ if rangeClasses.exists { c => c.toString startsWith (xsdPrefix) } => literalEntry
        case _ if rangeClasses.contains(rdfs.Literal) => literalEntry
        case _ if propClasses.contains(owl.DatatypeProperty) => literalEntry
        case _ if ranges.contains(rdfs.Literal) => literalEntry
        case _ if propClasses.contains(owl.ObjectProperty) => resourceEntry
        case _ if rangeClasses.contains(owl.Class) => resourceEntry
        case _ if rangeClasses.contains(rdf.Property) => resourceEntry
        case _ if ranges.contains(owl.Thing) => resourceEntry
        case _ if isURI(object_) => resourceEntry
        case _ if isBN(object_) => makeBN(label, comment, prop, ResourceValidator(ranges), toBN(object_), firstType)
        case _ if object_.toString.startsWith("_:") => resourceEntry
        case _ => literalEntry
      }
      result += addPossibleValues(entry, ranges, valuesFromFormGroup)
    }
    result
  }

  def isURIorBN(node: Rdf#Node) = foldNode(node)(identity, identity, x => None) != None
  def isURI(node: Rdf#Node) = foldNode(node)(identity, x => None, x => None) != None
  def isBN(node: Rdf#Node) = foldNode(node)(x => None, identity, x => None) != None
  def toBN(node: Rdf#Node): Rdf#BNode = foldNode(node)(x => BNode(""), identity, x => BNode(""))
  def firstNodeOrElseNullURI(set: Set[Rdf#Node]): Rdf#Node = set.headOption.getOrElse(nullURI)

  /**
   * compute terminal Part of URI, eg
   *  Person from http://xmlns.com/foaf/0.1/Person
   *  Project from http://usefulinc.com/ns/doap#Project
   *  NOTE: code related for getting the ontology prefix
   */
  def terminalPart(uri: Rdf#URI): String = {
    uriOps.getFragment(uri) match {
      case None => uriOps.lastSegment(uri)
      case Some(frag) => frag
    }
  }

  /**
   * get value in preferred Language From Values Or Else given default;
   *  use application language
   */
  private[abstract_syntax] def getPreferedLanguageFromSubjectAndPredicate(subject: Rdf#Node, predicate: Rdf#URI, default: String): String = {
    //    println("getPreferedLanguageFromSubjectAndPredicate: " + subject + " " + predicate) // debug
    objectsQuery(subject, predicate) match {
      case ll if ll == Set.empty => default
      case ll => getPreferedLanguageFromValues(ll)
    }
  }

  /** get preferred Language value From RDF Values that are language marked or not */
  private def getPreferedLanguageFromValues(values: Iterable[Rdf#Node]): String = {
    def computeValues(): (String, String, String) = {
      var preferedLanguageValue = ""
      var enValue = ""
      var noLanguageValue = ""
      for (value <- values) {
        foldNode(value)(
          x => (), x => (),
          value => {
            val tt = fromLiteral(value)
            val (raw, uri, langOption) = fromLiteral(value)
            // println("getPreferedLanguageFromValues: " + (raw, uri, langOption) )
            langOption match {
              case Some(language) =>
                if (language == preferedLanguage) preferedLanguageValue = raw
                else if (language == "en")
                  enValue = raw
              case None => noLanguageValue = raw
              case _ =>
            }
          }
        )
      }
      //      println(s"preferedLanguageValue: $preferedLanguageValue , enValue $enValue, noLanguageValue $noLanguageValue")
      (preferedLanguageValue, enValue, noLanguageValue)
    }
    val (preferedLanguageValue, enValue, noLanguageValue) = computeValues
    (preferedLanguageValue, enValue, noLanguageValue) match {
      case _ if (preferedLanguageValue != "") => preferedLanguageValue
      case _ if (enValue != "") => enValue
      case _ if (noLanguageValue != "") => noLanguageValue
      case _ =>
        val mess = s"preferedLanguageValue $preferedLanguageValue, enValue $enValue, noLanguageValue $noLanguageValue"
        println(s"getPreferedLanguageFromValues: case not expected in values ${values.mkString(", ")}: $mess"); "en"
    }
  }

  /**
   * get first ?OBJ such that:
   *   subject predicate ?OBJ	,
   *   or returns default
   */
  private def getHeadOrElse(subject: Rdf#Node, predicate: Rdf#URI,
    default: Rdf#URI = nullURI): Rdf#URI = {
    objectsQuery(subject, predicate) match {
      case ll if ll.isEmpty => default
      case ll if (isURI(ll.head)) => ll.head.asInstanceOf[Rdf#URI]
      case _ => default
    }
  }

  private def getHeadValueOrElse(subjects: Set[Rdf#Node], predicate: Rdf#URI): Rdf#Node = {
    val values = objectsQueries(subjects, predicate)
    values.headOption match {
      case Some(x) => x
      case _ => nullURI
    }
  }

  def classFromSubject(subject: Rdf#Node) = {
    getHeadOrElse(subject, rdf.typ)
  }
}
