package org.vivoweb.adminapp.datasource.util.sparql;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.vivoweb.adminapp.datasource.SparqlEndpointParams;
import org.vivoweb.adminapp.datasource.dao.DataTaskDao;
import org.vivoweb.adminapp.datasource.dao.ModelConstructor;
import org.vivoweb.adminapp.datasource.util.HttpUtils;

public class SparqlEndpoint  extends HttpUtils implements ModelConstructor {

    // Writing too many triples at once to VIVO results in 403 errors
    // if maxPostSize is not adjusted on Tomcat.
    public static final int CHUNK_SIZE = 2500;  // triples per 'chunk'
    
    private static final Log log = LogFactory.getLog(SparqlEndpoint.class);
    
    private static final String GET = "get";
    
    private SparqlEndpointParams endpointParams;
    
    public SparqlEndpoint(SparqlEndpointParams params) {
        if (null == params) {
            throw new IllegalArgumentException("No endpoint configured!");
        }
        this.endpointParams = params;
    }
    
    public List<QuerySolution> listResults(String query) throws IOException {
        List<QuerySolution> result = new LinkedList<>();
        
        Map<String, String> params = getHttpParams();
        params.put("query", query);
        
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "application/sparql-results+xml");
        
        InputStream content = getHTTPResponse(endpointParams.getEndpointURI(), GET, params, headers);
        ResultSet rs = ResultSetFactory.fromXML(content);

        while(rs.hasNext()) {
            result.add(rs.next());
        }
        
        content.close();
        
        return result;
    }
    
    
    private Map<String, String> getHttpParams() {
        Map<String, String> params = new HashMap<String, String>();
        
        params.put("email", endpointParams.getUsername());
        params.put("password", endpointParams.getPassword());
        
        return params;
    }
    
    /**
     * Uses the SPARQL UPDATE endpoint to write a model to VIVO.
     * The model is split into individual requests of size CHUNK_SIZE to 
     * avoid error responses from VIVO.
     * @param model
     */
    public void writeModel(Model model, String graphURI) {
        writeModel(model, graphURI, null, null);
    }
    

    public void writeModel(Model model, String graphURI, DataTaskDao dataDao, String taskURI) {
        List<Model> modelChunks = new ArrayList<Model>();
        StmtIterator sit = model.listStatements();
        int i = 0;
        Model currentChunk = null;
        while(sit.hasNext()) {
            if(i % CHUNK_SIZE == 0) {
                if (currentChunk != null) {
                    modelChunks.add(currentChunk);
                }
                currentChunk = ModelFactory.createDefaultModel();
            }
            currentChunk.add(sit.nextStatement());
            i++;
        }
        if (currentChunk != null) {
            modelChunks.add(currentChunk);
        }
        long total = model.size();
        long written = 0;
        log.debug("Writing " + total + " new statements to VIVO");
        for (Model chunk : modelChunks) {
            writeChunk(chunk, graphURI);
            
            
            if (null != dataDao) {
                written += chunk.size();    
                int percent = 51 + (int) (49 * written / total);
                dataDao.saveProgress(taskURI, percent);
            }
        }
    }

    public void update(String updateString) {       
        Map<String, String> params = getHttpParams();
        params.put("update", updateString);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        
        try {
            InputStream content = getHTTPResponse(endpointParams.getEndpointUpdateURI(), HttpUtils.POST, params, headers);
            content.close();
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Uses the SPARQL UPDATE endpoint to write a model to VIVO
     * @param chunk
     */
    private void writeChunk(Model chunk, String graphURI) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        chunk.write(out, "N-TRIPLE");
        StringBuffer reqBuff = new StringBuffer();
        reqBuff.append("INSERT DATA { GRAPH <" + graphURI + "> { \n");
        reqBuff.append(out);
        reqBuff.append(" } } \n");
        String reqStr = reqBuff.toString();     
        long startTime = System.currentTimeMillis();
        update(reqStr);
        log.debug("\t" + (System.currentTimeMillis() - startTime) / 1000 + 
                " seconds to insert " + chunk.size() + " triples");
    }

    public Model construct(String query) {
        Map<String, String> params = getHttpParams();
        params.put("query", query);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "text/turtle");
        
        Model result;
        try {
            result = getRDFResponse(endpointParams.getEndpointURI(), GET, params, headers, "TURTLE");
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
        
        return result;
    } 
       
    /**
     * An alternative to CLEAR, which is very inefficient with VIVO-based 
     * endpoints
     * @param graphURI
     * @throws IOException 
     */
    public void clearGraph(String graphURI) throws IOException {
        // retrieve individual URIs in batches of 1000
        int batchSize = 1000;
        log.info("Clearing graph " + graphURI + " in batches of " + batchSize + 
                " individuals");
        boolean getNextBatch = true;
        do {
            String individualsBatch = "SELECT DISTINCT ?s WHERE { \n" +
                    "    GRAPH <" + graphURI + "> { \n" +
                    "        ?s ?p ?o \n" +
                    "    } \n" +
                    "} LIMIT " + batchSize;
            log.debug(individualsBatch);
            List<QuerySolution> solutions = listResults(individualsBatch);
            StringBuilder deletion = new StringBuilder();
            for (QuerySolution sol : solutions) {
                Resource s = sol.getResource("s");
                if(s.isURIResource()) {
                    deletion.append("DELETE { \n")
                    .append("    GRAPH<").append(graphURI).append(">")
                    .append(" { <").append(s.getURI()).append("> ?p ?o } \n")
                    .append("} WHERE { \n")
                    .append("    GRAPH<").append(graphURI).append(">")
                    .append(" { <").append(s.getURI()).append("> ?p ?o } \n")
                    .append("}; \n");
                }
            }
            String deletionStr = deletion.toString();
            log.debug(deletionStr);
            if(deletionStr.isEmpty()) {
                getNextBatch = false;
            } else {
                try {
                    update(deletionStr);    
                } catch (Exception e) {
                    log.info("Failed to delete batch of triples", e);
                }
            }            
        } while (getNextBatch);
        // TODO check that count is decreasing after each N batches, otherwise 
        // terminate loop
        // Finally, issue the regular CLEAR to flush out anything remaining
        // (e.g. blank nodes)
        //log.info("Clearing graph " + graphURI);
        log.info("Calling final clear");
        update("CLEAR GRAPH <" + graphURI + ">");
    }
    
    /**
     * Removes all triples from given graph.
     * @param graphURI
     * @throws IOException 
     */
    public void clear(String graphURI) throws IOException {
        clearGraph(graphURI);                                   // memory problems with VIVO-based implementation, so try do clearing here
        
//        long startTime = System.currentTimeMillis();
//        log.info("Clearing graph " + graphURI);
//        update("CLEAR GRAPH <" + graphURI + ">");
//        log.info(".. cleared in " + (System.currentTimeMillis() - startTime) / 1000 + "s");
    }
    
    

    
}