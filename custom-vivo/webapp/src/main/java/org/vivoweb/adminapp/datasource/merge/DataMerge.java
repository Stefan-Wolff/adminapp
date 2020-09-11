package org.vivoweb.adminapp.datasource.merge;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.vivoweb.adminapp.datasource.DataTask;
import org.vivoweb.adminapp.datasource.dao.DataTaskDao;
import org.vivoweb.adminapp.datasource.dao.MergeRuleDao;
import org.vivoweb.adminapp.datasource.util.sparql.SparqlEndpoint;

/**
 * Implementation of the data merge task.
 * 
 * @author swolff
 *
 */
public class DataMerge extends DataTask {

    private static final Log log = LogFactory.getLog(DataMerge.class);

    private final Levenshtein levenshtein = new Levenshtein();
    
    
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
        MergeRuleDao mergeRuleDao = new MergeRuleDao(endpoint, getURI());
        NameVariantBuilder nameVariant = new NameVariantBuilder();

        log.info("reading in all data");
        Model inModel = readAll(endpoint);

        log.info("Loading merge rules");
        List<MergeRule> mergeRules = mergeRuleDao.loadMergeRules();

        int ruleNum = mergeRules.size();
        int i = 0;
        long result = 0;
        log.info("running " + ruleNum + " merge rules");
        for (MergeRule rule : mergeRules) {
            log.info("Processing rule " + rule.getURI());
            
            Model ruleResult = getSameAs(rule, inModel, nameVariant);
            long resultNum = ruleResult.size();

            log.info("Rule results size: " + resultNum + " triples");
            updateSameAs(endpoint, ruleResult, rule.getURI());
            
            result += resultNum;
            dataTaskDao.saveResultNum(rule.getURI(), resultNum);
            dataTaskDao.saveProgress(getURI(), (100 * ++i / ruleNum));
        }
        
        cleanOtherRuleSameAs(mergeRules, endpoint);
        
        return result;
    }
    
    private void cleanOtherRuleSameAs(List<MergeRule> mergeRules, SparqlEndpoint endpoint) throws IOException {
        String query = "SELECT DISTINCT ?g WHERE { GRAPH ?g { \n" + 
                       "?s <" + OWL.sameAs.getURI() + "> ?o . } \n" + 
                       "FILTER (regex(str(?g), \"^http://vivo.adminapp.local/\")) . \n";
        
        for (MergeRule rule : mergeRules) {
            query += "FILTER (?g != <" + rule.getURI() + ">) .";
        }
        
        query += " }";
        
        List<QuerySolution> qsols = endpoint.listResults(query);
        for (QuerySolution sol : qsols) {
            String ruleURI = sol.get("g").toString();
            Model toDelete = loadSameAsByGraph(endpoint, ruleURI);
            endpoint.deleteModel(toDelete, ruleURI);
                
            log.info("sameAs triples of non-active merge rule (" + ruleURI + ") removed: " + toDelete.size());
        }
        
    }
    
    
    private void updateSameAs(SparqlEndpoint endpoint, Model updateModel, String graphURI) {
        Model oldSameAs = loadSameAsByGraph(endpoint, graphURI);
        
        Model toDelete = ModelFactory.createDefaultModel();
        StmtIterator stmtIt = oldSameAs.listStatements();
        while (stmtIt.hasNext()) {
            Statement stmt = stmtIt.next();
            
            if (!updateModel.contains(stmt) && !updateModel.contains(stmt.getSubject(), OWL.sameAs, stmt.getObject())) {
                toDelete.add(stmt);
            } else {
                updateModel.remove(stmt);
            }
        }
        
        log.info("delete old sameAs: " + toDelete.size() + " triples");
        endpoint.deleteModel(toDelete, graphURI);
        
        log.info("add new sameAs: " + updateModel.size() + " triples");
        endpoint.writeModel(updateModel, graphURI);
    }
    
    
    private Model loadSameAsByGraph(SparqlEndpoint endpoint, String graphURI) {
        return endpoint.construct("CONSTRUCT { ?s <" + OWL.sameAs.getURI() + "> ?o } WHERE { GRAPH <" + graphURI + "> { \n" + 
                "?s <" + OWL.sameAs.getURI() + "> ?o } }");
    }
    
    
    private Model readAll(SparqlEndpoint endpoint) {
        return endpoint.construct("CONSTRUCT { ?s ?p ?o } WHERE { GRAPH ?g { \n" + 
                                  "?s ?p ?o . \n"+
                                  " FILTER NOT EXISTS { ?s <" + OWL.sameAs.getURI() + "> ?o } } }");
    }


    private Model getSameAs(MergeRule rule, Model inModel, NameVariantBuilder nameVariant)  throws IOException {
        Model result = ModelFactory.createDefaultModel();
        
        Resource mergeClass = inModel.createResource(rule.getMergeClassURI());
        
        List<Resource> subjects = inModel.listResourcesWithProperty(RDF.type, mergeClass).toList();
        Collection<List<Resource>> duplicates = getRuleSameAs(rule, inModel, nameVariant, subjects);
        
        for (List<Resource> duplList : duplicates) {

            for (Resource dupl : duplList) {
                for (Resource dupl2 : duplList) {
                    if (dupl != dupl2) {
                        result.add(dupl, OWL.sameAs, dupl2);
                    }
                }
            }
            
        }
        
        return result;
    }
    
    private Collection<List<Resource>> getRuleSameAs(MergeRule rule, Model inModel, NameVariantBuilder nameVariant, List<Resource> candidates) throws IOException {
        Collection<List<Resource>> duplicates = new LinkedList<>();
        duplicates.add(candidates);
        
        for (MergeRuleAtom atom : rule.getAtoms()) {
            log.info("Processing merge rule pattern " + atom.getURI());

            if (atom instanceof ObjectPropertyMergeAtom) {
                duplicates = getObjectPropertySameAs((ObjectPropertyMergeAtom)atom, duplicates, inModel);

            } else if (atom instanceof AuthorGroupMergeAtom) {
                duplicates = getAuthorGroupSameAs((AuthorGroupMergeAtom)atom, duplicates, inModel, nameVariant);

            } else if (atom instanceof TextMergeAtom) {
                duplicates = getDataPropertySameAs((TextMergeAtom)atom, duplicates, inModel, nameVariant);
                
            } else {
                throw new IllegalStateException("Merge rule pattern not implemented: " + atom.getClass());
            }
            
            if (duplicates.isEmpty()) {
                return duplicates;
            }
        }


        for (Entry<String, MergeRule> linkedRule : rule.getLinkedRules().entrySet()) {
            log.info("Processing linked rule: " + linkedRule.getValue().getURI());
            
            Property linkedProp = inModel.createProperty(linkedRule.getKey());
            Resource ruleClass = inModel.createProperty(rule.getMergeClassURI());
            Resource linkedClass = inModel.createResource(linkedRule.getValue().getMergeClassURI());
        
            List<Resource> linkedCandidates = new ArrayList<>();

            StmtIterator stmtIt = inModel.listStatements(null, linkedProp, (RDFNode) null);
            
            while (stmtIt.hasNext()) {
                Statement stmt = stmtIt.next();
                Resource subject = stmt.getSubject().asResource();
                Resource object = stmt.getObject().asResource();
                
                if (subject.hasProperty(RDF.type, ruleClass) && object.hasProperty(RDF.type, linkedClass)) {
                    linkedCandidates.add(object);
                }
            }
            
            Collection<List<Resource>> linkedDupls = getRuleSameAs(linkedRule.getValue(), inModel, nameVariant, linkedCandidates);
            List<List<Resource>> duplIntersection = new LinkedList<>();
            
            // intersection of current duplicates and linked duplicates
            for (List<Resource> duplList : duplicates) {
                for (List<Resource> linkedDuplList : linkedDupls) {
                    
                    List<List<Resource>> dupls = linkedIntersection(duplList, linkedProp, linkedDuplList);
                    if (null != dupls) {
                        duplIntersection.addAll(dupls);
                    }
                    
                }
            }
            
            duplicates = duplIntersection;
        }
        
        return duplicates;
    }
    

    private List<List<Resource>> linkedIntersection(List<Resource> list1, Property link, List<Resource> list2) {
        List<List<Resource>> result = null;
        
        List<Resource> linkedList1 = null;
        List<Resource> linkedList2 = null;
        
        for (Resource res : list1) {
            for (Resource res2 : list2) {
                if (res.hasProperty(link, res2)) {
                    if (null == linkedList1) linkedList1 = new LinkedList<>();
                    if (null == linkedList2) linkedList2 = new LinkedList<>();
                    
                    linkedList1.add(res);
                    linkedList2.add(res2);
                }
            }
        }
        
        if (null != linkedList1 && 1 < linkedList1.size()) {
            if (null == result) result = new LinkedList<>();
            result.add(linkedList1);
            result.add(linkedList2);
        }
        
        return result;
    }
    
    private Collection<List<Resource>> getObjectPropertySameAs(ObjectPropertyMergeAtom atom, Collection<List<Resource>> candidates, Model inModel) {
        Map<Resource, List<Resource>> duplicates = new HashMap<>();
        Property mergeObjectProp = inModel.createProperty(atom.getMergeObjectPropertyURI());
        
        
        for (List<Resource> candList : candidates) {
            Map<RDFNode, Resource> targets = new HashMap<>();
            
            for (Resource res : candList) {
                
                StmtIterator stmtIt = res.listProperties(mergeObjectProp);
                
                while (stmtIt.hasNext()) {
                    RDFNode object = stmtIt.next().getObject();
                    Resource dupl = targets.get(object);
                    if (null != dupl) {
                        List<Resource> duplList = duplicates.get(dupl);
                        if (null == duplList) {
                            duplicates.put(dupl, duplList = new LinkedList<>());
                            duplList.add(dupl);
                        }
                        duplList.add(res);
                        
                        
                    } else {
                        targets.put(object, res);
                    }
                }
            }
        }
        
        return duplicates.values();
    }
    
    private Collection<List<Resource>> getDataPropertySameAs(TextMergeAtom atom, Collection<List<Resource>> candidates, Model inModel, NameVariantBuilder nameVariant) throws IOException {
        Property mergeDataProp = inModel.createProperty(atom.getMergeDataPropertyURI());
        
        // load all property values
        Map<Resource, List<String>> propValues = new HashMap<>();
        for (List<Resource> candList : candidates) {
            for (Resource res : candList) {    
                StmtIterator stmtIt = res.listProperties(mergeDataProp);
                
                List<String> values = null;
                while (stmtIt.hasNext()) {
                    String value = stmtIt.next().getObject().toString().toLowerCase();                    
                    if (atom.nameVariants()) {
                        value = nameVariant.build(value);
                    }
                    
                    if (null == values) {
                        values = Collections.singletonList(value);
                        
                    } else {
                        if (1 == values.size()) {
                            values = new LinkedList<>(values);
                        }
                        
                        values.add(value);
                    }
                }
            
                if (null != values) {
                    propValues.put(res, values);
                }
            }
        }
        
        if (propValues.isEmpty()) {
            return Collections.emptyList();
        }
        
        
        // multi threading
        Set<Entry<Resource, List<String>>> valueSet = propValues.entrySet();
        
        int threadNum = Math.max(Runtime.getRuntime().availableProcessors() / 2, 1);
        int stepSize = (int)(valueSet.size() / threadNum);
        LinkedList<DataPropertyComparing> threads = new LinkedList<>();
        
        for (int i=0; i<threadNum; i++) {
            int start = i * stepSize;
            int end = i < threadNum-1 ? (i+1) * stepSize : valueSet.size();
            
            DataPropertyComparing runner = new DataPropertyComparing(valueSet, start, end, atom.getMatchDegree(), i);
            threads.add(runner);
            new Thread(runner).start();
        }
        
        List<List<Resource>> duplicates = new LinkedList<>();
        for (DataPropertyComparing runner : threads) {
            runner.waitForDone();
            duplicates.addAll(runner.getDuplicates());
        }

        return duplicates;
    }
    
    
    
    private Collection<List<Resource>> getAuthorGroupSameAs(AuthorGroupMergeAtom atom, Collection<List<Resource>> candidates, Model inModel, NameVariantBuilder nameVariant) {
        List<List<Resource>> duplicates = new LinkedList<>();
        Property mergeDataProp = inModel.createProperty(atom.getMergeDataPropertyURI());
        
        Property relates = inModel.createProperty("http://vivoweb.org/ontology/core#relates");
        Property relatedBy = inModel.createProperty("http://vivoweb.org/ontology/core#relatedBy");
        Resource authorship = inModel.createResource("http://vivoweb.org/ontology/core#Authorship");
        Resource document = inModel.createResource("http://purl.org/ontology/bibo/Document");
        
        Map<Resource, Map<Resource, String>> publAuthors = new HashMap<>();                        // authors related to the publication
        
        for (List<Resource> candList : candidates) {
            
            for (Resource authorRes : candList) {
                
                // load name of this author
                StmtIterator stmtIt2 = authorRes.listProperties(mergeDataProp);
                String authorName = null;
                while (stmtIt2.hasNext()) {
                    authorName = stmtIt2.next().getObject().toString().toLowerCase();
                    if (atom.nameVariants()) {
                        authorName = nameVariant.build(authorName);
                    }
                }
                
                if (null == authorName) {
                    continue;
                }
                
                // load documents of this author
                StmtIterator authorshipIt = authorRes.listProperties(relatedBy);
                
                while (authorshipIt.hasNext()) {
                    Statement authorshipStmt = authorshipIt.next();
                    RDFNode authorshipObj = authorshipStmt.getObject();
                    if (!authorshipObj.isURIResource()) {
                        continue;
                    }
                    Resource authorshipRes =  authorshipObj.asResource();
                    
                    if (inModel.contains(authorshipRes, RDF.type, authorship)) {
                        StmtIterator docIterator = authorshipRes.listProperties(relates);
                        
                        while (docIterator.hasNext()) {
                            Statement documentStmt = docIterator.next();
                            RDFNode documentObj = documentStmt.getObject();
                            if (!documentObj.isURIResource()) {
                                continue;
                            }
                            Resource documentRes = documentObj.asResource();
                            
                            if (inModel.contains(documentRes, RDF.type, document)) {
                                Map<Resource, String> authorList = publAuthors.get(documentRes);
                                if (null == authorList) publAuthors.put(documentRes, authorList = new HashMap<>());
                                authorList.put(authorRes, authorName);
                                break;
                            }
                        }
                    }
                }
            
            }
        }
        
        List<Set<Entry<Resource, String>>> authors = new ArrayList<>();                     // a list entry == all authors of a specific publication
        for (Map<Resource, String> authorList : publAuthors.values()) {
            if (atom.getNumPersons() <= authorList.size()) {
                authors.add(authorList.entrySet());
            }
        }
        
        for (int i=0; i<authors.size(); i++) {
            Set<Entry<Resource, String>> authorList = authors.get(i);
            List<List<Entry<Resource, String>>> authorGroup = new LinkedList<>();
            
            for (Entry<Resource, String> entry : authorList) {
                authorGroup.add(Collections.singletonList(entry));
            }
            
            getAuthorGroupIntersection(atom, authors, authorGroup, i, 2, duplicates);
        }
        
        return duplicates;
    }

    
    private void getAuthorGroupIntersection(AuthorGroupMergeAtom atom, List<Set<Entry<Resource, String>>> duplList, List<List<Entry<Resource, String>>> authorGroup, int groupIndex, int level, List<List<Resource>> duplicates) {
        for (int i=groupIndex+1; i<duplList.size(); i++) {
            List<List<Entry<Resource, String>>> interGroup = createIntersection(atom, authorGroup, duplList.get(i));
            
            if (null != interGroup && atom.getNumPersons() <= interGroup.size()) {
                
                if (atom.getNumPublications() > level) {
                    getAuthorGroupIntersection(atom, duplList, interGroup, i, level+1, duplicates);
                    
                } else {
                    for (List<Entry<Resource, String>> authorList : interGroup) {
                        List<Resource> curAuthors = new LinkedList<>();
                        for (Entry<Resource, String> entry : authorList) {
                            curAuthors.add(entry.getKey());
                        }
                        duplicates.add(curAuthors);
                    }
                }
            }
            
        }
        
    }
    
    
    private List<List<Entry<Resource, String>>> createIntersection(AuthorGroupMergeAtom atom, List<List<Entry<Resource, String>>> group1, Set<Entry<Resource, String>> group2) {
        List<List<Entry<Resource, String>>> result = null;
        

        for (Entry<Resource, String> entry2 : group2) {

            for (List<Entry<Resource, String>> duplList : group1) {
                
                for (Entry<Resource, String> entry : duplList) {
                
                    boolean match = false;
                    if (100 > atom.getMatchDegree()) {
                        if (levenshtein.match(entry.getValue(), entry2.getValue(), atom.getMatchDegree())) {
                            match = true;
                        }
                        
                    } else if (entry.getValue().equals(entry2.getValue())) {
                        match = true;
                    }
                  
                    if (match) {
                        if (null == result) result = new LinkedList<>();
                        
                        List<Entry<Resource, String>> extendedDuplList = new LinkedList<>(duplList);
                        extendedDuplList.add(entry2);
                        result.add(extendedDuplList);
                        
                        break;
                    }
                }
            
            }
            
        }
        
        return result;
    }


}
