package org.vivoweb.adminapp.datasource.ingest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.vivoweb.adminapp.datasource.DataTask;
import org.vivoweb.adminapp.datasource.dao.DataTaskDao;
import org.vivoweb.adminapp.datasource.util.http.HttpUtils;
import org.vivoweb.adminapp.datasource.util.sparql.SparqlEndpoint;

/**
 * Implementation of a data ingest task returning RDF format.
 * 
 * @author Stefan.Wolff@slub-dresden.de
 */
public class DataIngest extends DataTask {

    private final String serviceURI;
    private final String httpMethod;
    private final String responseFormat;
    private Map<String, String> httpParams = new HashMap<String, String>();
    private Map<String, String> httpHeaders = new HashMap<String, String>();

    
    public DataIngest(String taskUri, String serviceUri, String httpMethod, String responseFormat) {
        super(taskUri);
        
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
        Model ingestData = httpUtil.getRDFResponse(serviceURI, httpMethod, httpParams, httpHeaders, responseFormat);
        
        SparqlEndpoint endpoint = new SparqlEndpoint(this.getEndpointParams());
        endpoint.clear(getResultsGraphURI());
        endpoint.writeModel(ingestData, getResultsGraphURI());
        
        return ingestData.size();
    }

    
    public void addHttpParam(String key, String value) {
        httpParams.put(key, value);
    }


    public void addHttpHeader(String key, String value) {
        httpHeaders.put(key, value);
    }

    
}
