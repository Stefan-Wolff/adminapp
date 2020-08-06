package org.vivoweb.adminapp.datasource.ingest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jena.rdf.model.Model;
import org.vivoweb.adminapp.datasource.DataTask;
import org.vivoweb.adminapp.datasource.dao.DataTaskDao;
import org.vivoweb.adminapp.datasource.util.HttpUtils;
import org.vivoweb.adminapp.datasource.util.sparql.SparqlEndpoint;

/**
 * Implementation of a data ingest task returning RDF format.
 * 
 * @author Stefan.Wolff@slub-dresden.de
 */
public class DataIngest extends DataTask {

    private static final Log log = LogFactory.getLog(DataIngest.class);
    public static final String INGEST_GRAPH_PREFIX = "http://vitro.mannlib.cornell.edu/a/graph/ingest/";
    
    private final String serviceURI;
    private final String resultsGraphURI;
    private final String httpMethod;
    private final String responseFormat;
    private Map<String, String> httpParams = new HashMap<String, String>();
    private Map<String, String> httpHeaders = new HashMap<String, String>();

    
    public DataIngest(String taskUri, String serviceUri, String httpMethod, String responseFormat) {
        super(taskUri);
        
        String[] uriParts = taskUri.split("/");
        resultsGraphURI = INGEST_GRAPH_PREFIX + uriParts[uriParts.length-1];  
        
        this.serviceURI = serviceUri;
        this.httpMethod = httpMethod;
        this.responseFormat = responseFormat;
    }
    
    
    @Override
    public long run(DataTaskDao dataDao) throws IOException {
        if (null == serviceURI) {
            throw new IllegalArgumentException("Service URI not configured!");
        }
        if (null == httpMethod) {
            throw new IllegalArgumentException("HTTP method not configured!");
        }
        if (null == responseFormat) {
            throw new IllegalArgumentException("Response format not configured!");
        }
        
        HttpUtils httpUtil = new HttpUtils();
        
        log.info("start ingest from " + getServiceURI());
        
        dataDao.saveProgress(getURI(), 1);
        Model ingestData = httpUtil.getRDFResponse(getServiceURI(), httpMethod, httpParams, httpHeaders, responseFormat);
        dataDao.saveProgress(getURI(), 50);
        
        SparqlEndpoint endpoint = new SparqlEndpoint(this.getEndpointParams());
        
        log.info("ingest into graph: " + getResultsGraphURI());
        endpoint.clear(getResultsGraphURI());
        dataDao.saveProgress(getURI(), 51);
        endpoint.writeModel(ingestData, getResultsGraphURI(), dataDao, getURI());
        
        return ingestData.size();
    }

    
    
    public String getServiceURI() {
        return serviceURI;
    }


    public String getResultsGraphURI() {
        return resultsGraphURI;
    }


    public void addHttpParam(String key, String value) {
        httpParams.put(key, value);
    }


    public void addHttpHeader(String key, String value) {
        httpHeaders.put(key, value);
    }


    @Override
    public boolean indexingEnabled() {
        return true;
    }
    
}
