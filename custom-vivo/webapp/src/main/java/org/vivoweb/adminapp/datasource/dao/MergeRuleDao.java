package org.vivoweb.adminapp.datasource.dao;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.vivoweb.adminapp.datasource.merge.AuthorGroupMergeAtom;
import org.vivoweb.adminapp.datasource.merge.MergeRule;
import org.vivoweb.adminapp.datasource.merge.MergeRuleAtom;
import org.vivoweb.adminapp.datasource.merge.ObjectPropertyMergeAtom;
import org.vivoweb.adminapp.datasource.merge.TextMergeAtom;
import org.vivoweb.adminapp.datasource.util.RDFUtils;
import org.vivoweb.adminapp.datasource.util.sparql.SparqlEndpoint;

public class MergeRuleDao {

    private static final String MERGERULEATOM = DataTaskDao.ADMIN_APP_TBOX + "MergeRuleAtom";
    private static final String HASMERGERULE = DataTaskDao.ADMIN_APP_TBOX + "hasMergeRule";
    private static final String LINKEDMERGERULE = DataTaskDao.ADMIN_APP_TBOX + "LinkedMergeRule";
    private static final String MERGERULE = DataTaskDao.ADMIN_APP_TBOX + "MergeRule";
    private static final String DISABLED = DataTaskDao.ADMIN_APP_TBOX + "disabled";
    private static final String PRIORITY = DataTaskDao.ADMIN_APP_TBOX + "priority";
    private static final String MERGERULECLASS = DataTaskDao.ADMIN_APP_TBOX + "mergeRuleClass";
    private static final String HASATOM = DataTaskDao.ADMIN_APP_TBOX + "hasAtom";
    private static final String HASLINKEDMERGERULE = DataTaskDao.ADMIN_APP_TBOX + "hasLinkedMergeRule";
    private static final String LINKEDBYOBJECTPROPERTY = DataTaskDao.ADMIN_APP_TBOX + "linkedByObjectProperty";
    private static final String MATCHDEGREE = DataTaskDao.ADMIN_APP_TBOX + "matchDegree";
    private static final String NUMBERPUBLICATIONS = DataTaskDao.ADMIN_APP_TBOX + "numberPublications";
    private static final String NUMBERPERSONS = DataTaskDao.ADMIN_APP_TBOX + "numberPersons";
    private static final String MERGEATOMDATAPROPERTY = DataTaskDao.ADMIN_APP_TBOX + "mergeAtomDataProperty";
    private static final String MERGEATOMOBJECTPROPERTY = DataTaskDao.ADMIN_APP_TBOX + "mergeAtomObjectProperty";
    private static final String NAMEVARIANTS = DataTaskDao.ADMIN_APP_TBOX + "nameVariants";
    private static final String TEXTMERGEATOM = DataTaskDao.ADMIN_APP_TBOX + "TextMergeAtom";
    private static final String AUTHORGROUPMERGEATOM = DataTaskDao.ADMIN_APP_TBOX + "AuthorGroupMergeAtom";
    private static final String OBJECTPROPERTYMERGEATOM = DataTaskDao.ADMIN_APP_TBOX + "ObjectPropertyMergeAtom";

    private final RDFUtils rdfUtils = new RDFUtils();
    private final SparqlEndpoint endpoint;
    private final String mergeConfigURI;
    
    
    public MergeRuleDao(SparqlEndpoint endpoint, String mergeConfigURI) {
        this.endpoint = endpoint;
        this.mergeConfigURI = mergeConfigURI;
    }
    
    
    public List<MergeRule> loadMergeRules() throws IOException {
        List<MergeRule> result = new LinkedList<>();
        
        Model ruleData = loadMergeRuleData(endpoint);
        List<Resource> ruleRes = getMergeRuleURIs(mergeConfigURI, endpoint);
        
        for (Resource res : ruleRes) {
            result.add(getMergeRule(res.getURI(), ruleData));
        }
        
        return result;
    }
 
    private List<Resource> getMergeRuleURIs(String configURI, SparqlEndpoint endpoint) throws IOException {
        String queryStr =   "SELECT ?x WHERE { \n" +
                            "    <" + configURI + "> <" + HASMERGERULE + "> ?x . \n" +
                            "    ?x <" + PRIORITY + "> ?prio . \n" +
                            "    FILTER NOT EXISTS { ?x <" + DISABLED + "> true } \n" + 
                            "} ORDER BY ?prio \n";
        
        List<Resource> result = new LinkedList<>();
        for (QuerySolution sol :  endpoint.listResults(queryStr)) {
            result.add(sol.get("x").asResource());
        }
        
        return result;
    }
    
    private Model loadMergeRuleData(SparqlEndpoint endpoint) throws IOException {
        String queryStr =   "DESCRIBE ?x WHERE { \n" +
                            "    { ?x a <" + MERGERULE + "> } \n" +
                            "UNION \n" +
                            "    { ?x a <" + MERGERULEATOM + "> } \n" +
                            "UNION \n" +
                            "    { ?x a <" + LINKEDMERGERULE + "> } \n" +
                            "FILTER NOT EXISTS { ?x <" + DISABLED + "> true } \n" + "} \n";
        
        return endpoint.construct(queryStr);
    }
    
    
    private MergeRule getMergeRule(String ruleURI, Model model) {
        MergeRule mergeRule = new MergeRule(ruleURI);
        Integer priority = rdfUtils.getIntValue(ruleURI, PRIORITY, model, 0);
        mergeRule.setPriority(priority);
        mergeRule.setMergeClassURI(rdfUtils.getString(ruleURI, MERGERULECLASS, model));
        StmtIterator atomIt = model.listStatements(model.getResource(ruleURI), model.getProperty(HASATOM), (RDFNode) null);
        for (Statement atomStmt : atomIt.toList()) {
            if (atomStmt.getObject().isURIResource()) {
                mergeRule.addAtom(createAtom(atomStmt.getObject().asResource().getURI(), model));
            }
        }

        StmtIterator linkedIt = model.listStatements(model.getResource(ruleURI), model.getProperty(HASLINKEDMERGERULE), (RDFNode) null);
        for (Statement linkedStmt : linkedIt.toList()) {
            if (linkedStmt.getObject().isURIResource()) {
                Resource linkedMergeRule = linkedStmt.getObject().asResource();
                
                String linkedObjPropURI = rdfUtils.getString(linkedMergeRule.getURI(), LINKEDBYOBJECTPROPERTY, model);
                String linkedRuleURI = rdfUtils.getString(linkedMergeRule.getURI(), HASMERGERULE, model);

                mergeRule.addLinkedRule(linkedObjPropURI, getMergeRule(linkedRuleURI, model));
            }
        }

        return mergeRule;
    }

    private MergeRuleAtom createAtom(String atomURI, Model model) {
        String classURI = rdfUtils.getURIValue(atomURI, DataTaskDao.MOSTSPECIFICTYPE, model);
        MergeRuleAtom result;
        
        switch(classURI) {
            case TEXTMERGEATOM:
                result = createTextMergeAtom(atomURI, model);
                break;
        
            case AUTHORGROUPMERGEATOM:
                result = createAuthorGroupAtom(atomURI, model);
                break;
                
            case OBJECTPROPERTYMERGEATOM:
                result = createObjectPropertyAtom(atomURI, model);
                break;
        
            default: throw new IllegalStateException("Merge rule pattern type not implemented: " + classURI);
        
        }
        
        return result;
    }
    

    
    private TextMergeAtom createTextMergeAtom(String uri, Model model) {
        return new TextMergeAtom(uri, 
                                     rdfUtils.getString(uri, MERGEATOMDATAPROPERTY, model),
                                     rdfUtils.getIntValue(uri, MATCHDEGREE, model, 100),
                                     rdfUtils.getBooleanValue(uri, NAMEVARIANTS, model, false));
    }
    
    private AuthorGroupMergeAtom createAuthorGroupAtom(String uri, Model model) {
        return new AuthorGroupMergeAtom(uri,
                                            rdfUtils.getString(uri, MERGEATOMDATAPROPERTY, model),
                                            rdfUtils.getIntValue(uri, MATCHDEGREE, model, 100),
                                            rdfUtils.getBooleanValue(uri, NAMEVARIANTS, model, false),
                                            rdfUtils.getIntValue(uri, NUMBERPUBLICATIONS, model, 0),
                                            rdfUtils.getIntValue(uri, NUMBERPERSONS, model, 0));
    }
    
    private ObjectPropertyMergeAtom createObjectPropertyAtom(String uri, Model model) {
        return new ObjectPropertyMergeAtom(uri, rdfUtils.getString(uri, MERGEATOMOBJECTPROPERTY, model));
    }
    
}
