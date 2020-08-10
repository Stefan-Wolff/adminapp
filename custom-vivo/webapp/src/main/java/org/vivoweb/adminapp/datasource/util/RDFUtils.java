package org.vivoweb.adminapp.datasource.util;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;

/**
 * Utility collection esp. for reading of RDF.
 * 
 * @author swolff
 *
 */
public class RDFUtils {

    public String getString(String subjectURI, String propertyURI, Model model) {
        StmtIterator sit = model.listStatements(model.getResource(subjectURI), 
                model.getProperty(propertyURI), (RDFNode) null);

        for (Statement stmt : sit.toList()) {
            RDFNode object = stmt.getObject();
            if (object.isLiteral()) {
                return object.asLiteral().getLexicalForm();
            } else if (object.isURIResource()) {
                return object.asResource().getURI();
            }
        }
            
        return null;
    }
    
    public String getURIValue(String subjectURI, String propertyURI, Model model) {
        StmtIterator sit = model.listStatements(model.getResource(subjectURI), 
                model.getProperty(propertyURI), (Resource) null);
        
        for (Statement stmt : sit.toList()) {
            if(stmt.getObject().isURIResource()) {
                return stmt.getObject().asResource().getURI();
            }
        }
        
        return null;
    }
    
    
    public int getIntValue(String subjectURI, String propertyURI, Model model, int notFound) {
        StmtIterator sit = model.listStatements(model.getResource(subjectURI), 
                model.getProperty(propertyURI), (RDFNode) null);
        
        for (Statement stmt : sit.toList()) {
            if(stmt.getObject().isLiteral()) {
                Literal lit = stmt.getObject().asLiteral();
                Object obj = lit.getValue();
                if(obj instanceof Integer) {
                    return (Integer) obj;
                }
            }
        }
            
        return notFound;
    }
    
    public long getLongValue(String subjectURI, String propertyURI, Model model, long notFound) {
        StmtIterator sit = model.listStatements(model.getResource(subjectURI), 
                model.getProperty(propertyURI), (RDFNode) null);

        for (Statement stmt : sit.toList()) {
            if(stmt.getObject().isLiteral()) {
                Literal lit = stmt.getObject().asLiteral();
                Object obj = lit.getValue();
                if(obj instanceof Long) {
                    return (Long) obj;
                }
                if (obj instanceof Integer) {
                    return (Integer) obj;
                }
            }
        }
        
        return notFound;
    }
    
    public boolean getBooleanValue(String subjectURI, String propertyURI, Model model, boolean notFound) {
        StmtIterator sit = model.listStatements(model.getResource(subjectURI), 
                model.getProperty(propertyURI), (RDFNode) null);

        for (Statement stmt : sit.toList()) {
            if(stmt.getObject().isLiteral()) {
                Literal lit = stmt.getObject().asLiteral();
                Object obj = lit.getValue();
                if(obj instanceof Boolean) {
                    return (Boolean) obj;
                }
            }
        }
        
        return notFound;
    }
    

    
}
