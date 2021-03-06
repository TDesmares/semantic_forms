package deductions.runtime.jena

import org.w3.banana.jena.JenaModule
import deductions.runtime.sparql_cache.RDFStoreHelpers
import org.w3.banana.jena.Jena
import com.hp.hpl.jena.query.Dataset

trait JenaHelpers extends JenaModule with RDFStoreHelpers[Jena, Dataset]
    with JenaRDFLoader {
}