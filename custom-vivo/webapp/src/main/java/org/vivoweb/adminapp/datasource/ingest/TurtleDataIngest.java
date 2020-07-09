package org.vivoweb.adminapp.datasource.ingest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.vivoweb.adminapp.datasource.DataTask;
import org.vivoweb.adminapp.datasource.dao.DataSourceDao;
import org.vivoweb.adminapp.datasource.util.http.HttpUtils;
import org.vivoweb.adminapp.datasource.util.sparql.SparqlEndpoint;

/**
 * Implementation of a data ingest task returning turtle format.
 * 
 * @author Stefan.Wolff@slub-dresden.de
 */
public class TurtleDataIngest extends DataTask {

    private String serviceURI;
    private String httpMethod;
    private Map<String, String> httpParams = new HashMap<String, String>();
    private Map<String, String> httpHeaders = new HashMap<String, String>();

    
    public TurtleDataIngest(String taskUri, String serviceUri, String httpMethod) {
        super(taskUri);
        
        this.serviceURI = serviceUri;
        this.httpMethod = httpMethod;
    }
    
    
    @Override
    public long run(DataSourceDao dataDao) throws IOException {
        HttpUtils httpUtil = new HttpUtils();

        Model ingestData = httpUtil.getRDFResponse(serviceURI, httpMethod, httpParams, httpHeaders, "TURTLE");
        
        SparqlEndpoint endpoint = new SparqlEndpoint(this.getEndpointParams());
        endpoint.clear(getResultsGraphURI());
        endpoint.writeModel(ingestData, getResultsGraphURI());
        
        return ingestData.size();
    }


    public String getServiceURI() {
        return serviceURI;
    }


    public String getHttpMethod() {
        return httpMethod;
    }


    public Map<String, String> getHttpParams() {
        return httpParams;
    }


    public Map<String, String> getHttpHeaders() {
        return httpHeaders;
    }

    
}
