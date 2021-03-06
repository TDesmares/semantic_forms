package deductions.runtime.abstract_syntax

import java.io.FileInputStream
import java.io.FileOutputStream
import org.hamcrest.BaseMatcher
import org.junit.Assert
import org.scalatest.FunSuite
import org.w3.banana.FOAFPrefix
import org.w3.banana.RDFModule
import org.w3.banana.RDFOpsModule
import org.w3.banana.TurtleReaderModule
import org.w3.banana.diesel._
import org.w3.banana.jena.JenaModule
import org.w3.banana.TurtleWriterModule
import org.w3.banana.SparqlOpsModule
import org.w3.banana.SparqlGraphModule

class FormSyntaxFactoryTestJena extends FunSuite
    with JenaModule
    with FormSyntaxFactoryTest {

  test("form contains label and data") {
    val form = createFormWithGivenProps
    println("form:\n" + form)
    Assert.assertThat("form contains label and data", form.toString,
      new BaseMatcher[String]() {
        def matches(a: Any): Boolean = {
          val s = a.toString
          s.contains("Alexandre") &&
            s.contains("name") &&
            s.contains("Henry Story")
        }
        def describeTo(x$1: org.hamcrest.Description): Unit = {}
      })
  }

  test("form With inferred fields") {
    val form = createFormWithInferredProps()
    println("form:\n" + form)
    Assert.assertThat("form contains label and data", form.toString,
      new BaseMatcher[String]() {
        def matches(a: Any): Boolean = {
          val s = a.toString
          s.contains("Alexandre") &&
            s.contains("name") &&
            s.contains("Henry Story")
        }
        def describeTo(x$1: org.hamcrest.Description): Unit = {}
      })
  }

  test("form from Class") {
    val form = createFormFromClass
    println("form:\n" + form)
  }

}

trait FormSyntaxFactoryTest // [Rdf <: RDF]
    extends RDFOpsModule
    with TurtleReaderModule
    with TurtleWriterModule
    with SparqlOpsModule
    with SparqlGraphModule {

  import ops._
  lazy val foaf = FOAFPrefix[Rdf]

  def makeFOAFsample: Rdf#Graph = {
    (URI("betehess")
      -- foaf.name ->- "Alexandre".lang("fr")
      -- foaf.title ->- "Mr"
      -- foaf.knows ->- (
        URI("http://bblfish.net/#hjs")
        -- foaf.name ->- "Henry Story"
        -- foaf.currentProject ->- URI("http://webid.info/"))).graph
  }

  def createFormWithGivenProps() = {
    val graph1 = makeFOAFsample
    val resource = new FileInputStream("src/test/resources/foaf.n3")
    val graph2 = turtleReader.read(resource, foaf.prefixIri).get
    val graph = union(Seq(graph1, graph2))

    val fact = new FormSyntaxFactory[Rdf](graph)
    println((graph.triples).mkString("\n"))
    val form = fact.createFormDetailed(
      URI("betehess"),
      Seq(foaf.title,
        foaf.name, foaf.knows),
      URI(""))
    form
  }

  def createFormWithInferredProps() = {
    val graph1 = makeFOAFsample
    val resource = new FileInputStream("src/test/resources/foaf.n3")
    val graph2 = turtleReader.read(resource, foaf.prefixIri).get
    val graph = union(Seq(graph1, graph2))

    val fact = new FormSyntaxFactory[Rdf](graph)
    fact.createForm(URI("betehess"), editable = true)
  }

  def createFormFromClass() = {
    val resource = new FileInputStream("src/test/resources/foaf.n3")
    val graph2 = turtleReader.read(resource, foaf.prefixIri).get
    val formspec = new FileInputStream("form_specs/foaf.form.ttl")
    val graph1 = turtleReader.read(formspec, "").get
    val graph = union(Seq(graph1, graph2))
    //  val fact = new UnfilledFormFactory[Rdf](graph)
    val fact = new FormSyntaxFactory[Rdf](graph)
    val os = new FileOutputStream("/tmp/graph.nt")
    turtleWriter.write(graph, os, "")
    fact.createFormDetailed(URI("betehess"), Seq(foaf.topic_interest), foaf.Person)
  }

}