package org.vivoweb.adminapp.datasource;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.vivoweb.adminapp.datasource.dao.ModelConstructor;

import edu.cornell.mannlib.vitro.webapp.rdfservice.RDFService;
import edu.cornell.mannlib.vitro.webapp.rdfservice.RDFServiceException;

public class RDFServiceModelConstructor implements ModelConstructor {

    private RDFService rdfService;
    
    public RDFServiceModelConstructor(RDFService rdfService) {
        this.rdfService = rdfService;
    }
    
    public Model construct(String query) {
        try {
            Model model = ModelFactory.createDefaultModel();
            rdfService.sparqlConstructQuery(query, model);
            return model;
        } catch (RDFServiceException e) {
            throw new RuntimeException(e);
        }
    }
    
}
