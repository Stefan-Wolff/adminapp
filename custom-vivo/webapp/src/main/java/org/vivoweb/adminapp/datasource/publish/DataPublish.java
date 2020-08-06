package org.vivoweb.adminapp.datasource.publish;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.vivoweb.adminapp.datasource.DataTask;
import org.vivoweb.adminapp.datasource.SparqlEndpointParams;
import org.vivoweb.adminapp.datasource.dao.DataTaskDao;
import org.vivoweb.adminapp.datasource.ingest.DataIngest;
import org.vivoweb.adminapp.datasource.util.sparql.SparqlEndpoint;

/**
 * A data source that takes a admin app's SPARQL endpoint as an input
 * and publishes data to a public VIVO endpoint, rewriting individuals
 * to use the highest-priority URI and dropping lower-priority duplicate values
 * for functional properties
 * @author Brian Lowe
 *
 */
public class DataPublish extends DataTask {

    private static final Log log = LogFactory.getLog(DataPublish.class);
    
    private static final String DATA_GRAPH_URI = "http://vitro.mannlib.cornell.edu/default/vitro-kb-2";
    
    private static final String RDFS_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    
    private static final String VITRO_SPEC_TYPE = "http://vitro.mannlib.cornell.edu/ns/vitro/0.7#mostSpecificType"; 
    
    // number of individuals to process before writing results
    private static final int BATCH_SIZE = 2500;
    
    private final SparqlEndpointParams targetEndpointParams;
    
    public DataPublish(String taskUri, SparqlEndpointParams endpointParams) {
        super(taskUri);
        this.targetEndpointParams = endpointParams;
    }
    
    @Override
    public boolean indexingEnabled() {
        return false;
    }
   
    @Override
    public long run(DataTaskDao dataDao) throws IOException {
        if (null == this.getEndpointParams().getEndpointURI()) {
            throw new IllegalArgumentException("Missing endpoint URI in configuration: " + this.getURI());
        }
        SparqlEndpoint sourceEndpoint = new SparqlEndpoint(this.getEndpointParams());
        
        if (null == this.targetEndpointParams.getEndpointURI()) {
            throw new IllegalArgumentException("Missing target endpoint URI in configuration: " + this.getURI());
        }
        if (null == this.targetEndpointParams.getEndpointUpdateURI()) {
            throw new IllegalArgumentException("Missing target endpoint update URI in configuration: " + this.getURI());
        }        
        SparqlEndpoint destinationEndpoint = new SparqlEndpoint(this.targetEndpointParams);
       
        log.info("Starting to publish ..");
        
        destinationEndpoint.clear(DATA_GRAPH_URI);

        Model buffer = ModelFactory.createDefaultModel();
        int individualCount = 0;
        IndividualIterator indIt = new IndividualIterator(sourceEndpoint);
        int individualNum = indIt.init();
        Set<Resource> singleSameAs = new HashSet<>();
        
        Resource next;
        while (null != (next = indIt.next())) {
            long start = System.currentTimeMillis();
            mergeData(next, sourceEndpoint, indIt, buffer, singleSameAs, false);
            
            long duration = System.currentTimeMillis() - start;
            if (duration > 1000) {
                log.info(duration + " ms to process individual " + next.getURI());
            }

            individualCount++;
            if(buffer.size() % BATCH_SIZE == 0) {
                flushBufferToDestination(buffer, destinationEndpoint);
                dataDao.saveProgress(getURI(), 100 * individualCount / individualNum);
            }
        }
        
        if (0 != buffer.size()) {
            flushBufferToDestination(buffer, destinationEndpoint);
        }
        
        log.info(".. publishing done.");
        
        return individualCount;
    }

    
    /**
     * Loads all triples of given URIs and changes there URIs all to one result URI.
     */
    private Resource mergeData(Resource individual, SparqlEndpoint endpoint, IndividualIterator indIt, Model result, Set<Resource> singleSameAs, boolean inRecursion) throws IOException {
        log.debug("merge individual " + individual.getURI());
        
        List<Resource> sameAsURIs;                                    // save stack memory
        if (singleSameAs.contains(individual)) {
            if (inRecursion) {
                return individual;
            } else {
                sameAsURIs = new LinkedList<>();
                sameAsURIs.add(individual);
                singleSameAs.remove(individual);
            }
            
        } else {
            sameAsURIs = getSameAsURIList(individual.getURI(), endpoint);
            
            if (inRecursion && 1 == sameAsURIs.size()) {
                singleSameAs.add(individual);
                return individual;
            }
        }

        indIt.removeAll(sameAsURIs);
        
        String resultURI = decideURI(sameAsURIs);
        Resource resultRes = result.createResource(resultURI);
        
        for (Resource uri : sameAsURIs) {
            StringBuilder query = new StringBuilder();
            query.append("SELECT ?p ?o WHERE { \n")
                 .append("  GRAPH ?g { \n")
                 .append("      <").append(uri.getURI()).append("> ?p ?o . \n")
                 .append("      FILTER(?p != <").append(OWL.sameAs.getURI()).append(">) \n")
                 .append("      FILTER(?p != <").append(VITRO_SPEC_TYPE).append(">) \n")
                 .append("  } \n")
                 .append("  FILTER(?g = <").append(DATA_GRAPH_URI).append("> || REGEX(str(?g),\"^").append(DataIngest.INGEST_GRAPH_PREFIX).append("\")) \n")
                 .append("} \n");
           
            for (QuerySolution sol : endpoint.listResults(query.toString())) {
                String predURI = sol.get("p").toString();
                RDFNode object = sol.get("o");

                if (object.isResource() && !predURI.equals(RDFS_TYPE) && indIt.contains(object.asResource())) {
                    log.debug("recursive merge call");
                    object = mergeData(object.asResource(), endpoint, indIt, result, singleSameAs, true);
                }

                result.add(resultRes, result.createProperty(predURI), object);
            }

        }

        return resultRes;
    }
    
    private String decideURI(List<Resource> sameAsURIs) {
        // no specific rule for now - maybe later
        return sameAsURIs.get(0).getURI();
    }
    
    /**
     * 
     * @return list via reasoned transitive closure of owl:sameAs statements
     * from endpoint
     * @throws IOException 
     */
    private List<Resource> getSameAsURIList(String individualURI, SparqlEndpoint endpoint) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("SELECT DISTINCT ?x WHERE { \n")
             .append("  <").append(individualURI).append("> <").append(OWL.sameAs.getURI()).append(">* ?x \n")
             .append("} \n");

        List<Resource> result = new LinkedList<>();
        for (QuerySolution sol : endpoint.listResults(query.toString())) {
            result.add(sol.getResource("x"));
        }
        
        return result;
    }


    
    private void flushBufferToDestination(Model buffer, SparqlEndpoint destinationEndpoint) {
        log.info("Writing " + buffer.size() + " triples");
        long start = System.currentTimeMillis();
        
        destinationEndpoint.writeModel(buffer, DATA_GRAPH_URI);
        
        log.info(".. written in " + (System.currentTimeMillis() - start)/ 1000 + "s");
        
        buffer.removeAll();
    }
    
    
    private class IndividualIterator  {

        private static final int INDIVIDUAL_BATCH_SIZE = 5000;
        
        private static final String QUERY = "SELECT DISTINCT ?s WHERE { \n" + 
                                            "     GRAPH ?g { ?s a ?o } \n" +
                                            "     FILTER (?g = <" + DATA_GRAPH_URI + "> || REGEX(str(?g),\"^" + DataIngest.INGEST_GRAPH_PREFIX + "\")) \n" + 
                                            "     FILTER NOT EXISTS { \n" + 
                                            "          ?s a ?sub \n" +
                                            "          FILTER (REGEX(str(?sub),\"^http://vivoweb.org/ontology/adminapp/\")) \n" +
                                            "     } \n" +
                                            "} LIMIT " + INDIVIDUAL_BATCH_SIZE + " OFFSET ";
        
        int individualOffset = 0;
        
        private final Queue<Resource> resources = new LinkedList<Resource>();
        
        private SparqlEndpoint endpoint;

        
        public IndividualIterator(SparqlEndpoint endpoint) {
            this.endpoint = endpoint;
        }
        
        public int init() throws IOException {
            while(loadNext()) { 
                // nothing to do here
            }
            return resources.size();
        }
        
        private boolean loadNext() throws IOException {            
            List<QuerySolution> results = endpoint.listResults(QUERY + individualOffset);
            for (QuerySolution solution : results) {
                resources.add(solution.getResource("s"));
            }
            
            individualOffset += INDIVIDUAL_BATCH_SIZE;

            return !results.isEmpty();
        }  
        
        public Resource next() {
            return resources.poll();
        }
        
        public void removeAll(List<Resource> res) {
            resources.removeAll(res);
        }

        public boolean contains(Resource res) {
            return resources.contains(res);
        }
        
    }

}
