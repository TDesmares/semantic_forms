package deductions.runtime.html

import org.w3.banana.jena.JenaModule
import org.w3.banana.RDFOpsModule
import org.w3.banana.XSDPrefix

object HTML5Types extends JenaModule with HTML5TypesTrait

/**
 * HTML5 input type correspondence to XML Schema (XSD) datatypes
 *  TODO : intervals
 */
trait HTML5TypesTrait extends RDFOpsModule {
  import ops._
  lazy val xsd = XSDPrefix[Rdf]

  def xsd2html5TnputType(xsdDatatype: String): String = xsd2html5.getOrElse(URI(xsdDatatype), "text")

  /** see http://www.w3.org/TR/html-markup/input.html */
  val xsd2html5 = Map[Rdf#URI, String](
    xsd.integer -> "number",
    xsd.int -> "number",
    xsd.float -> "number",
    xsd.decimal -> "number",
    xsd.long -> "number",
    xsd.short -> "number",
    xsd.nonNegativeInteger -> "number",
    xsd.nonPositiveInteger -> "number",
    xsd.negativeInteger -> "number",
    xsd.positiveInteger -> "number",

    xsd.dateTime -> "datetime-local",
    xsd.dateTimeStamp -> "datetime-local",
    xsd.anyURI -> "url",
    xsd.boolean -> "radio"
  /* ⓘ input type=text
ⓘ input type=password
ⓘ input type=checkbox
ⓘ input type=radio
ⓘ input type=button
ⓘ input type=submit
ⓘ input type=reset
ⓘ input type=file
ⓘ input type=hidden
ⓘ input type=image

ⓘ input type=datetime NEW
ⓘ input type=datetime-local NEW
ⓘ input type=date NEW
ⓘ input type=month NEW
ⓘ input type=time NEW
ⓘ input type=week NEW
ⓘ input type=number NEW
ⓘ input type=range NEW
ⓘ input type=email NEW
ⓘ input type=url NEW
ⓘ input type=search NEW
ⓘ input type=tel NEW
ⓘ input type=color NEW
     */
  )
}