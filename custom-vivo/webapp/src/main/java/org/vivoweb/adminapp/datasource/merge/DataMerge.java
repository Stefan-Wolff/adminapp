package org.vivoweb.adminapp.datasource.merge;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDFS;
import org.vivoweb.adminapp.datasource.DataTask;
import org.vivoweb.adminapp.datasource.dao.DataTaskDao;
import org.vivoweb.adminapp.datasource.util.RDFUtils;
import org.vivoweb.adminapp.datasource.util.sparql.LevenshteinFunction;
import org.vivoweb.adminapp.datasource.util.sparql.SparqlEndpoint;

/**
 * Implementation of the data merge task.
 * 
 * @author swolff
 *
 */
public class DataMerge extends DataTask {

    private static final Log log = LogFactory.getLog(DataMerge.class);

    private static final String MERGERULE = DataTaskDao.ADMIN_APP_TBOX + "MergeRule";
    private static final String DISABLED = DataTaskDao.ADMIN_APP_TBOX + "disabled";
    private static final String MERGERULEATOM = DataTaskDao.ADMIN_APP_TBOX + "MergeRuleAtom";
    private static final String LINKEDMERGERULE = DataTaskDao.ADMIN_APP_TBOX + "LinkedMergeRule";
    private static final String HASMERGERULE = DataTaskDao.ADMIN_APP_TBOX + "hasMergeRule";
    private static final String MERGERULECLASS = DataTaskDao.ADMIN_APP_TBOX + "mergeRuleClass";
    private static final String PRIORITY = DataTaskDao.ADMIN_APP_TBOX + "priority";
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

    private static final String BASIC_SAMEAS_GRAPH = "http://vitro.mannlib.cornell.edu/a/graph/basicSameAs";
    private static final String NAMEVARIANT_GRAPH = "http://vitro.mannlib.cornell.edu/a/graph/nameVariant";

    private static String LEVENSHTEIN_URI = "http://vivo.adminapp.local/function/" + LevenshteinFunction.class.getSimpleName();

    private final RDFUtils rdfUtils = new RDFUtils();
    
    
    
    static {
        FunctionRegistry.get().put(LEVENSHTEIN_URI, LevenshteinFunction.class);
    }
    

    
    public DataMerge(String taskUri) {
        super(taskUri);
    }

    @Override
    public boolean indexingEnabled() {
        return false;
    }
    
    @Override
    public long run(DataTaskDao dataTaskDao) throws IOException {
        log.info("Starting merge " + getURI());

        SparqlEndpoint endpoint = new SparqlEndpoint(this.getEndpointParams());
        Model rulesModel = retrieveMergeRulesFromEndpoint(endpoint);
        Model differentFromModel = getDifferentFromModel(endpoint);

        log.info("adding basic sameAs assertions");
        //log.info("skip: adding basic sameAs assertions");
        addBasicSameAsAssertions(endpoint);

        log.info("Clearing previous merge state");
        List<MergeRule> mergeRules = new ArrayList<MergeRule>();
        for (Resource mergeRule : getMergeRuleURIs(getURI(), endpoint)) {
            endpoint.clear(mergeRule.getURI());
            try {
                mergeRules.add(getMergeRule(mergeRule.getURI(), rulesModel));
            } catch(Exception e) {
                log.warn("Loading of merge rule failed!", e);
            }
        }

        log.info("running merge rules");
        Map<String, Long> statistics = new HashMap<String, Long>();

        // TODO: more then one iteration necessary?
        int ruleNum = mergeRules.size();
        int i = 0;
        for (MergeRule rule : mergeRules) {
            String mergeRuleURI = rule.getURI();
            log.info("Processing rule " + mergeRuleURI);
            
            log.info("clean up old prepared data structure");
            //log.info("skip: clean up old prepared data structure");
            endpoint.clear(NAMEVARIANT_GRAPH);
            
            log.info("Prepare data structure ..");
            prepareDataStructure(rule, endpoint);
            log.info(".. data structure prepared");
            
            Model ruleResult = getSameAs(rule, endpoint);
            if (isSuspicious(ruleResult)) {
                log.warn(mergeRuleURI + " produced a suspiciously large number (" + ruleResult.size() + ") of triples.");
            }

            filterObviousResults(ruleResult);
            filterKnownDifferentFrom(ruleResult, differentFromModel);
            
            Long stat = statistics.get(mergeRuleURI);
            if (stat == null) {
                statistics.put(mergeRuleURI, ruleResult.size());
            } else {
                statistics.put(mergeRuleURI, stat + ruleResult.size());
            }

            log.info("Rule results size: " + ruleResult.size());
            endpoint.writeModel(ruleResult, mergeRuleURI);
            
            dataTaskDao.saveProgress(getURI(), 100 * i++ / ruleNum);
        }

        log.info("======== Final Results ========");
        int result = 0;
        for (String ruleURI : statistics.keySet()) {
            result += statistics.get(ruleURI);
            dataTaskDao.saveResultNum(ruleURI, statistics.get(ruleURI));
            log.info("Rule " + ruleURI + " added " + statistics.get(ruleURI));
        }
        
        return result;
    }

    
    private void prepareDataStructure(MergeRule rule, SparqlEndpoint endpoint) throws IOException {
        NameVariantBuilder nameVariant = new NameVariantBuilder();
        
        for (MergeRuleAtom atom : rule.getAtoms()) {
            if (atom instanceof TextMergeAtom) {
                TextMergeAtom textMergeAtom = (TextMergeAtom) atom;
                if (!textMergeAtom.nameVariants()) {
                    continue;
                }
                
                StringBuilder query = new StringBuilder();
                query.append("SELECT ?x ?y WHERE { \n")
                     .append("  ?x a <").append(rule.getMergeClassURI()).append("> . \n")
                     .append("  ?x <").append(textMergeAtom.getMergeDataPropertyURI()).append("> ?y . \n")
                     .append("}");
                
                String queryString = query.toString();
                log.info("Preparation query: " + queryString);
                
                Model target = ModelFactory.createDefaultModel();
                Property predicate = target.createProperty(textMergeAtom.getMergeDataPropertyURI());
                SolutionIterator iterator = new SolutionIterator(endpoint, queryString);
                
                long stmtCount = 0;
                QuerySolution sol;
                while (null != (sol = iterator.next())) {
                    Resource subject = sol.get("x").asResource();
                    String objValue = sol.get("y").toString();
                    String variant = nameVariant.build(objValue);

                    target.add(subject, predicate, variant);
                    
                    if (0 == (SparqlEndpoint.CHUNK_SIZE % ++stmtCount)) {
                        endpoint.writeModel(target, NAMEVARIANT_GRAPH);
                        target.removeAll();
                    }
                }
                
                if (0 != target.size()) {
                    endpoint.writeModel(target, NAMEVARIANT_GRAPH);
                }
            }
        }
        
        
        for (MergeRule linkedRule : rule.getLinkedRules().values()) {
            prepareDataStructure(linkedRule, endpoint);
        }
    }
    
    private List<String> createMatchValues(String value, int degree) {
        List<String> result = new LinkedList<>();
        result.add(value);
        int valueLength = value.length();
        int stepSize = valueLength < 100 ? 100 / valueLength : 1;
        
        int current = 100 - stepSize;
        int lastCharNum = 0;
        while (degree < current) {
            int charNum = current * valueLength / 100;
            
            if (charNum != lastCharNum) {
                for (int i=0; i+charNum <= valueLength; i++) {
                    result.add(value.substring(i, charNum + i));
                }
                lastCharNum = charNum;
            }
            
            current -= stepSize;
        }
        
        return result;
    }
    
    
    /**
     * Materialize inferences of type sameAs(x,x) for query support
     * 
     * @throws IOException
     */
    private void addBasicSameAsAssertions(SparqlEndpoint endpoint) throws IOException {
        endpoint.clear(BASIC_SAMEAS_GRAPH);
        String queryStr = "CONSTRUCT { ?x <" + OWL.sameAs.getURI() + "> ?x } WHERE { \n" +
                          "    ?x a ?thing \n"
                          + "} \n";
        Model m = endpoint.construct(queryStr);
        log.info("Writing " + m.size() + " triples to " + BASIC_SAMEAS_GRAPH);
        endpoint.writeModel(m, BASIC_SAMEAS_GRAPH);
    }

    /**
     * Check if a result model is likely to contain an unwanted Cartesian product
     * 
     * @param m
     * @return true if the number of triples in the model is greater than one half
     *         the square of the number of distinct URIs
     */
    private boolean isSuspicious(Model m) {
        if (m.size() < 128) {
            return false;
        }
        String distinctURIs = "SELECT (COUNT(DISTINCT ?x) AS ?count) WHERE { \n"
                + "    { ?x ?p ?o } UNION { ?s ?p ?x } \n" + "} \n";
        QueryExecution qe = QueryExecutionFactory.create(distinctURIs, m);
        try {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                QuerySolution qsoln = rs.next();
                RDFNode node = qsoln.get("count");
                if (node.isLiteral()) {
                    int distinctURICount = Integer.parseInt(node.asLiteral().getLexicalForm(), 10);
                    boolean suspicious = m.size() >= ((distinctURICount * distinctURICount) / 2);
                    log.info("Distinct URIs: " + distinctURICount + "; result size: " + m.size());
                    log.info(suspicious ? "suspicious!" : "not suspicious");
                    return suspicious;
                }
            }
            return false;
        } finally {
            qe.close();
        }
    }

    /**
     * Remove each statement of the type owl:sameAs(x,y) from model m where
     * differentFromModel contains a statement owl:differentFrom(x,y).
     * 
     * @param m                  the model containing sameAs statements to be
     *                           filtered
     * @param differentFromModel the model containing differentFrom statements
     */
    private void filterKnownDifferentFrom(Model m, Model differentFromModel) {
        if (differentFromModel == null || differentFromModel.isEmpty()) {
            return;
        }
        Model delete = ModelFactory.createDefaultModel();
        StmtIterator sit = m.listStatements(null, OWL.sameAs, (Resource) null);
        while (sit.hasNext()) {
            Statement stmt = sit.next();
            if (differentFromModel.contains(stmt.getSubject(), OWL.differentFrom, stmt.getObject())) {
                delete.add(stmt);
            }
        }
        m.remove(delete);
    }

    private void filterObviousResults(Model m) {
        Model delete = ModelFactory.createDefaultModel();

        for (Statement stmt : m.listStatements().toList()) {
            if (stmt.getObject().isURIResource()
                    && stmt.getObject().asResource().getURI().equals(stmt.getSubject().getURI())) {
                delete.add(stmt);
            }
        }

        m.remove(delete);
    }

    private Model getSameAs(MergeRule rule, SparqlEndpoint endpoint)  throws IOException {
        SameAsQueryBuilder queryBuilder = new SameAsQueryBuilder();
        
        putSameAsQuery(rule, queryBuilder, null);
        
        String query = queryBuilder.build();
        log.info("Generated sameAs query: \n" + query);
        
        return endpoint.construct(query);
    }
    
    private void putSameAsQuery(MergeRule rule, SameAsQueryBuilder queryBuilder, String linkedObjURI)  throws IOException {
        queryBuilder.addRule(rule, linkedObjURI);

        for (Entry<String, MergeRule> linkedRule : rule.getLinkedRules().entrySet()) {
            log.info("Processing linked rule: " + linkedRule.getValue().getURI());
            
            putSameAsQuery(linkedRule.getValue(), queryBuilder, linkedRule.getKey());
        }

    }
    
    /**
     * Caution: If changing this function (or deeper), then {@link #getLinkedRuleOuterFilter(MergeRule, String, int)} may also have to be changed!
     */
    private String getAllAtomQueries(MergeRule rule) throws IOException {
        StringBuilder atomQueries = new StringBuilder();
        
        atomQueries.append("    ?x a <").append(rule.getMergeClassURI()).append("> . \n");
        atomQueries.append("    ?y a <").append(rule.getMergeClassURI()).append("> . \n");
                
        for (MergeRuleAtom atom : rule.getAtoms()) {
            log.info("Processing merge rule pattern " + atom.getURI());
          
            if (atom instanceof ObjectPropertyMergeAtom) {
                putObjectPropertySameAs((ObjectPropertyMergeAtom) atom, atomQueries);

            } else if (atom instanceof AuthorGroupMergeAtom) {
                putAuthorGroupSameAs((AuthorGroupMergeAtom) atom, rule.getMergeClassURI(), atomQueries);

            } else if (atom instanceof TextMergeAtom) {
                putDataPropertySameAs((TextMergeAtom) atom, atomQueries);
                
            } else {
                throw new IllegalStateException("Merge rule patern type not implemented: " + atom.getClass());
            }
            
        }
        
        return atomQueries.toString();
    }


    private void putObjectPropertySameAs(ObjectPropertyMergeAtom atom, StringBuilder result) throws IOException {
        String varName = buildVarNameFromURI(atom.getMergeObjectPropertyURI());
        
        result.append("    ?x <").append(atom.getMergeObjectPropertyURI()).append("> ?").append(varName).append(" . \n")
              .append("    ?y <").append(atom.getMergeObjectPropertyURI()).append("> ?").append(varName).append(" . \n");
    }

    
    private void putDataPropertySameAs(TextMergeAtom atom, StringBuilder result) {
        String varName1 = buildVarNameFromURI(atom.getMergeDataPropertyURI());
        String varName2 = varName1 + "2";
        
        result.append("    ?x <").append(atom.getMergeDataPropertyURI()).append("> ?").append(varName1).append(" . \n")
              .append("    ?y <").append(atom.getMergeDataPropertyURI()).append("> ?").append(varName2).append(" . \n")
              .append("    FILTER((lcase(str(?").append(varName1).append(")) = lcase(str(?").append(varName2).append(")))");
        
        if (100 > atom.getMatchDegree()) {
            result.append(" || ").append("(<").append(LEVENSHTEIN_URI).append(">(?").append(varName1).append(", ?").append(varName2).append(", ").append(atom.getMatchDegree()).append("))");
        }

        result.append(") . \n");
    }
    
    private void putAuthorGroupSameAs(AuthorGroupMergeAtom atom, String mergeClassRuleURI, StringBuilder result) {
        for (int i=0; i<atom.getNumPublications(); i++) {
            result.append("    ?doc_").append(i).append(" a <http://purl.org/ontology/bibo/Document> . \n");
            
            for (int j=0; j<atom.getNumPersons(); j++) {
                String authorshipVar = "as_" + i + "_" + j;
                String personVar;
                String personNameVar = "name_" + i + "_" + j; 
                
                if (0 == i && 0 == j) {
                    personVar = "x";
                } else if (1 == i && 0 == j) {
                    personVar = "y";
                } else {
                    personVar = "p_" + i + "_" + j;
                }
                
                result.append("    ?doc_").append(i).append(" <http://vivoweb.org/ontology/core#relatedBy> ?").append(authorshipVar).append(" . \n");
                result.append("    ?").append(authorshipVar).append(" a <http://vivoweb.org/ontology/core#Authorship> . \n");
                result.append("    ?").append(authorshipVar).append(" <http://vivoweb.org/ontology/core#relates> ?").append(personVar).append(" . \n");
                result.append("    ?").append(personVar).append(" a <").append(mergeClassRuleURI).append("> . \n");
                result.append("    ?").append(personVar).append(" <http://www.w3.org/2000/01/rdf-schema#label> ?").append(personNameVar).append(" . \n");
            
                if (i+1 < atom.getNumPublications()) {
                    String name2 = "name_" + (i+1) + "_" + j;
                    
                    result.append("    FILTER((lcase(str(?").append(personNameVar).append(")) = lcase(str(?").append(name2).append(")))");
                    
                    if (100 > atom.getMatchDegree()) {
                        result.append(" || ").append("(<").append(LEVENSHTEIN_URI).append(">(?").append(personNameVar).append(", ?").append(name2).append(", ").append(atom.getMatchDegree()).append("))");
                    }
                    
                    result.append(") . \n");
                }
                
                for (int k=j+1; k<atom.getNumPersons(); k++) {
                    result.append("    FILTER (?").append(personNameVar).append(" != ?name_").append(i).append("_").append(k).append(") . \n");
                }
                
            }
            
            for (int j=0; j<atom.getNumPublications(); j++) {
                if (i != j) {
                    result.append("    FILTER NOT EXISTS { ?doc_").append(i).append(" <http://www.w3.org/2002/07/owl#sameAs> ?doc_").append(j).append(" } \n");
                }
            }

        }
        
    }
    
    private String buildVarNameFromURI(String uri) {
        String[] parts = uri.split("//");
        
        if (1 > parts.length) {
            throw new IllegalArgumentException("Invalid URI format: " + uri);
        }
        
        return parts[1].replaceAll("[/_#-\\.]", "_");
    }

    
    /**
     * Builds the filter to ensure, ALL entries are found in potential duplicate.
     * 
     * Example: Its not enough, to find a same author in two publications entries.
     *          It is also necessary to ensure, that there are no authors in one of the potential publication duplicate,
     *          which are not mentioned in the other potential publication duplicate.
     */
    private String getLinkedRuleOuterFilter(MergeRule rule, String linkedObjURI) {
        StringBuilder result = new StringBuilder();
        StringBuilder innerFilter = new StringBuilder();
        
        result.append("    FILTER NOT EXISTS { \n")
              .append("        ?x <").append(linkedObjURI).append("> ?otherX . \n")
              .append("        ?otherX a <").append(rule.getMergeClassURI()).append("> . \n");
        
        for (MergeRuleAtom atom : rule.getAtoms()) {
            
            if (atom instanceof ObjectPropertyMergeAtom) {
                ObjectPropertyMergeAtom opAtom = (ObjectPropertyMergeAtom) atom;
                String varName = "other" + buildVarNameFromURI(opAtom.getMergeObjectPropertyURI());
                String varNameY = varName + "Y";
                
                result.append("    ?otherX <").append(opAtom.getMergeObjectPropertyURI()).append("> ?").append(varName).append(" . \n");
                innerFilter.append("    ?otherY <").append(opAtom.getMergeObjectPropertyURI()).append("> ?").append(varNameY).append(" . \n")
                           .append("    ?").append(varName).append(" <").append(OWL.sameAs.getURI()).append("> ?").append(varNameY).append(" . \n");
                
            } else if (atom instanceof TextMergeAtom) {
                TextMergeAtom agAtom = (TextMergeAtom) atom;
                String varName = "other" + buildVarNameFromURI(agAtom.getMergeDataPropertyURI());
                    
                result.append("        ?otherX <").append(agAtom.getMergeDataPropertyURI()).append("> ?").append(varName).append(" . \n");
                innerFilter.append("        ?otherY <").append(agAtom.getMergeDataPropertyURI()).append("> ?").append(varName).append(" . \n");
                
            } else if (atom instanceof AuthorGroupMergeAtom) {
                   // no outer filter needed for this pattern
                
            } else {
                throw new IllegalStateException("Merge rule patern type not implemented: " + atom.getClass());
            }
  
        }
        
        result.append("        FILTER NOT EXISTS { \n")
              .append("            ?y <").append(linkedObjURI).append("> ?otherY . \n")
              .append("            ?otherY a <").append(rule.getMergeClassURI()).append("> . \n");
        
        result.append(innerFilter);
        
        
        return result.toString();
    }

    protected boolean isSubclass(String classURI, String superclassURI, SparqlEndpoint sparqlEndpoint)
            throws IOException {
        if (classURI == null || superclassURI == null) {
            return false;
        }
        if (superclassURI.equals(classURI)) {
            return true;
        }
        Model ask = sparqlEndpoint
                .construct("CONSTRUCT { <" + classURI + "> <" + RDFS.subClassOf + "> <" + superclassURI + "> } WHERE "
                        + "{ <" + classURI + "> <" + RDFS.subClassOf + "> <" + superclassURI + "> }");
        return (ask.size() > 0);
    }


    protected Model retrieveMergeRulesFromEndpoint(SparqlEndpoint endpoint) throws IOException {
        String queryStr =   "DESCRIBE ?x WHERE { \n" +
                            "    { ?x a <" + MERGERULE + "> } \n" +
                            "UNION \n" +
                            "    { ?x a <" + MERGERULEATOM + "> } \n" +
                            "UNION \n" +
                            "    { ?x a <" + LINKEDMERGERULE + "> } \n" +
                            "FILTER NOT EXISTS { ?x <" + DISABLED + "> true } \n" + "} \n";
        
        return endpoint.construct(queryStr);
    }

    /**
     * @param endpoint the SPARQL endpoint to query
     * @return model containing symmetric closure of differentFrom statements found
     *         in the supplied endpoint
     * @return null if endpoint is null
     * @throws IOException
     */
    protected Model getDifferentFromModel(SparqlEndpoint endpoint) throws IOException {
        if (endpoint == null) {
            return null;
        }
        String queryStr = "CONSTRUCT { ?x <" + OWL.differentFrom.getURI() + "> ?y . \n" + "    ?y <"
                + OWL.differentFrom.getURI() + "> ?x . \n" + "} WHERE { \n" + "    ?x <" + OWL.differentFrom.getURI()
                + "> ?y \n" + "} \n";
        return endpoint.construct(queryStr);
    }

    protected MergeRule getMergeRule(String ruleURI, Model model) {
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

    
    // INNER CLASSES
    
    private class SameAsQueryBuilder {
        
        private final Map<MergeRule, String> rules = new LinkedHashMap<MergeRule, String>();

        private Pattern pattern = Pattern.compile("\\?\\w+");

        
        public void addRule(MergeRule rule, String linkedObjURI) {
            rules.put(rule, linkedObjURI);
        }
        
        public String build() throws IOException {
            int queryNum = rules.size();
            StringBuilder result = new StringBuilder();
            
            result.append("CONSTRUCT { \n");
            
            for (int i=0; i<queryNum; i++) {
                result.append("    ?x_").append(i).append(" <").append(OWL.sameAs.getURI()).append("> ?y").append("_").append(i).append(" . \n");
                result.append("    ?y_").append(i).append(" <").append(OWL.sameAs.getURI()).append("> ?x").append("_").append(i).append(" . \n");
            }
            
            result.append("} WHERE { \n");
            
            StringBuilder outerFilter = new StringBuilder();
            
            int i = 0;
            for (Entry<MergeRule, String> entry : rules.entrySet()) {
                MergeRule rule = entry.getKey();
                String linkedObjURI = entry.getValue();
                String atomQueries = getAllAtomQueries(rule);
                
                result.append(renameVars(atomQueries, i, Collections.emptyList()));

                if (null != linkedObjURI) {
                    String filter = getLinkedRuleOuterFilter(rule, linkedObjURI);
                    filter = renameVars(filter, i, Arrays.asList(new String[] {"?x", "?y"}));
                    
                    if (1 == i) {
                        filter = filter.replace("?x ", "?x_0 ");
                        filter = filter.replace("?y ", "?y_0 ");
                    } else {
                        filter = filter.replace("?x ", "?otherX_" + (i-1) + " ");
                        filter = filter.replace("?y ", "?otherY_" + (i-1) + " ");
                    }
                    
                    outerFilter.append(filter);

                    result.append("    ?x_").append(i-1).append(" <").append(linkedObjURI).append("> ?x").append("_").append(i).append(" . \n");
                    result.append("    ?y_").append(i-1).append(" <").append(linkedObjURI).append("> ?y").append("_").append(i).append(" . \n");
                }
                
                result.append("    FILTER NOT EXISTS { ?x_").append(i).append(" <").append(OWL.sameAs.getURI()).append("> ?y_").append(i).append(" } \n");
                result.append("    FILTER NOT EXISTS { ?y_").append(i).append(" <").append(OWL.sameAs.getURI()).append("> ?x_").append(i).append(" } \n");
                result.append("    FILTER NOT EXISTS { ?x_").append(i).append(" <").append(OWL.differentFrom.getURI()).append("> ?y_").append(i).append(" } \n");
                result.append("    FILTER NOT EXISTS { ?y_").append(i).append(" <").append(OWL.differentFrom.getURI()).append("> ?x_").append(i).append(" } \n");
                
                i++;
            }
            
            result.append(outerFilter);
            for (int j=1; j<i; j++) {
                result.append("   } } \n");             // opened in getLinkedRuleOuterFilter() by: FILTER NOT EXISTS {
            }

            
            result.append("} \n");                      // close: WHERE {
            
            
            return result.toString();
        }
        
        private String renameVars(String query, int varNum, List<String> excludedVars) {
            String result = query;
            Matcher m = pattern.matcher(query);
            Set<String> processedVars = new HashSet<String>();
            
            // renaming of variables because of combined query based on multiple rules
            while (m.find()) {
                String varName = m.group(0);
                
                if (!excludedVars.contains(varName) && processedVars.add(varName)) {
                    result = StringUtils.replacePattern(result, "\\" + varName + "(?=[ ,])", varName + "_" + varNum);
                }
            }
            
            return result;
        }


    }

    
    private class SolutionIterator  {

        private static final int BATCH_SIZE = 5000;

        private final SparqlEndpoint endpoint;
        private final String query;

        private final Queue<QuerySolution> data = new LinkedList<>();
        int offset = 0;

        
        public SolutionIterator(SparqlEndpoint endpoint, String query) {
            this.query = query;
            this.endpoint = endpoint;
            
        }
        
        private boolean loadNext() throws IOException {            
            List<QuerySolution> results = endpoint.listResults(query + " LIMIT " + BATCH_SIZE + " OFFSET " + offset);
            for (QuerySolution solution : results) {
                data.add(solution);
            }
            
            offset += BATCH_SIZE;

            return !results.isEmpty();
        }  
        
        public QuerySolution next() throws IOException {
            QuerySolution result = data.poll();
            
            if (null == result) {
                boolean next = loadNext();
                
                if (next) {
                    result = data.poll();
                }
            }
            
            return result;
        }

        
    }
}
