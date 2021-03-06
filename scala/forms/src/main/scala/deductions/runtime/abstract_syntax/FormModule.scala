/* copyright the Déductions Project
under GNU Lesser General Public License
http://www.gnu.org/licenses/lgpl.html
$Id$
 */
package deductions.runtime.abstract_syntax

import org.w3.banana._
import org.w3.banana.diesel._
import org.w3.banana.syntax._
import scala.collection.mutable

object FormModule {
  val formDefaults = FormDefaults()
}

/** Default values for the whole Form or for an `Entry` */
case class FormDefaults(
    var defaultCardinality: Cardinality = zeroOrMore,
    /** displaying rdf:type fields is configurable for editing, and displayed unconditionally for non editing */
    val displayRdfType: Boolean = true) {
  def multivalue: Boolean = defaultCardinality == zeroOrMore ||
    defaultCardinality == oneOrMore
}

trait FormModule[NODE, URI <: NODE] {

  /**
   * abstract_syntax for a semantic form , called FA (Abstract Form) :
   *  - generated from a list of URI's for properties, and a triple store
   *  - used in conjunction with HTML5 forms and Banana-RDF
   *  - could be used with N3Form(Swing) in EulerGUI,
   */
  case class FormSyntax(
      val subject: NODE,
      var fields: Seq[Entry],
      classs: URI = nullURI,
      formGroup: URI = nullURI,
      //      var defaultCardinality: Cardinality = zeroOrOne,
      val defaults: FormDefaults = FormModule.formDefaults) {
    override def toString(): String = {
      s"""FormSyntax:
        subject: $subject
        classs: $classs
        ${fields.mkString("\n")}
      """
    }
  }

  type DatatypeProperty = URI
  type ObjectProperty = URI
  case class Triple(val s: NODE, val p: URI, val o: NODE)

  val nullURI: URI
  def makeURI(n: NODE): URI = nullURI

  /**
   * openChoice allows user in form to choose a value not in suggested values
   *  TODO somehow factor value: Any ?
   */
  sealed abstract case class Entry(
      val label: String, val comment: String,
      val property: URI = nullURI,
      val mandatory: Boolean = false,
      val type_ : NODE = nullURI,
      val value: NODE = nullURI, // Any = "",
      var widgetType: WidgetType = Text,
      var openChoice: Boolean = true,
      var possibleValues: Seq[(NODE, NODE)] = Seq(),
      val defaults: FormDefaults = FormModule.formDefaults) {
    private val triples: mutable.Buffer[Triple] = mutable.ListBuffer[Triple]()
    def setPossibleValues(newPossibleValues: Seq[(NODE, NODE)]): Entry
    override def toString(): String = {
      s"""${getClass.getSimpleName} "$label", "$comment" $widgetType openChoice $openChoice"""
    }
    def addTriple(s: NODE, p: URI, o: NODE) = {
      val t = Triple(s, p, o)
      triples :+ t
    }

    def asResource(): Entry = {
      this
    }
  }

  /** @param possibleValues a couple of an RDF node id and the label to display, see trait RangeInference */
  class ResourceEntry(label: String, comment: String,
    property: ObjectProperty = nullURI, validator: ResourceValidator,
    value: URI = nullURI, val alreadyInDatabase: Boolean = true,
    possibleValues: Seq[(NODE, NODE)] = Seq(),
    val valueLabel: String = "",
    type_ : URI = nullURI)
      extends Entry(label, comment, property, type_ = type_, value = value, possibleValues = possibleValues) {
    override def toString(): String = {
      super.toString + s""" : <$value>, valueLabel "$valueLabel" possibleValues count:${possibleValues.size} """
    }
    def setPossibleValues(newPossibleValues: Seq[(NODE, NODE)]) = {
      val ret = new ResourceEntry(label, comment,
        property, validator,
        value, alreadyInDatabase,
        newPossibleValues, valueLabel, type_)
      ret.openChoice = this.openChoice
      ret.widgetType = this.widgetType
      ret
    }

    def this(e: Entry, validator: ResourceValidator,
      alreadyInDatabase: Boolean,
      valueLabel: String) = this(
      e.label: String, e.comment: String,
      e.property, validator,
      makeURI(e.value),
      alreadyInDatabase,
      e.possibleValues,
      valueLabel,
      makeURI(e.type_))
  }

  class BlankNodeEntry(label: String, comment: String,
      property: ObjectProperty = nullURI, validator: ResourceValidator,
      value: NODE, type_ : NODE = nullURI,
      possibleValues: Seq[(NODE, NODE)] = Seq()) extends Entry(label, comment, property, type_ = type_, value = value, possibleValues = possibleValues) {
    override def toString(): String = {
      super.toString + s", $value , possibleValues count:${possibleValues.size}"
    }
    def getId: String = value.toString
    def setPossibleValues(newPossibleValues: Seq[(NODE, NODE)]) = {
      val ret = new BlankNodeEntry(label, comment,
        property, validator, value, type_, newPossibleValues)
      ret.openChoice = this.openChoice
      ret.widgetType = this.widgetType
      ret
    }
  }
  class LiteralEntry(l: String, c: String,
    property: DatatypeProperty = nullURI, validator: DatatypeValidator,
    value: NODE = nullURI, // String = "",
    val lang: String = "",
    type_ : NODE = nullURI,
    possibleValues: Seq[(NODE, NODE)] = Seq()) extends Entry(l, c, property, type_ = type_,
    value = value, possibleValues = possibleValues) {
    override def toString(): String = {
      super.toString + s""" := "$value" """
    }
    def setPossibleValues(newPossibleValues: Seq[(NODE, NODE)]) = {
      val ret = new LiteralEntry(label, comment,
        property, validator,
        value, lang, type_,
        newPossibleValues)
      ret.openChoice = this.openChoice
      ret.widgetType = this.widgetType
      ret
    }

    override def asResource(): Entry = {
      new ResourceEntry(this,
        validator = null,
        alreadyInDatabase = true,
        valueLabel = this.value.toString()
      )
    }

  }

  case class ResourceValidator(typ: Set[NODE]) // URI])
  case class DatatypeValidator(typ: Set[NODE]) // URI])

}

sealed class WidgetType
object Text extends WidgetType { override def toString() = "Text WidgetType" }
object Textarea extends WidgetType
object Checkbox extends WidgetType

abstract class Choice extends WidgetType
/** Can be Radio Button or checkboxes for multiple choices, depending on Entry.openChoice */
object Buttons extends Choice
object Slider extends Choice
object PulldownMenu extends Choice

object Collection extends WidgetType
object DBPediaLookup extends WidgetType { override def toString() = "DBPediaLookup WidgetType" }
object UpLoad extends WidgetType

sealed class Cardinality
object zeroOrMore extends Cardinality { override def toString() = "0 Or More" }
object oneOrMore extends Cardinality { override def toString() = "1 Or More" }
object zeroOrOne extends Cardinality { override def toString() = "0 Or 1" }
object exactlyOne extends Cardinality { override def toString() = "exactly 1" }

