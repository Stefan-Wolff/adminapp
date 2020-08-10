package org.vivoweb.adminapp.datasource.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

public class HttpUtils {
    
    protected static final String POST = "post";
    
    public static final String DEFAULT_USER_AGENT = "adminapp VIVO";
    
    private final HttpClient httpClient = HttpClients.custom()
                                               .setRedirectStrategy(new LaxRedirectStrategy())
                                               .setUserAgent(DEFAULT_USER_AGENT)
                                               .build();
    

    public InputStream getHTTPResponse(String url, String method, Map<String, String> params, Map<String, String> headers) throws IOException {
        HttpRequestBase request = buildRequest(url, method, params, headers);

        HttpResponse response = httpClient.execute(request);
        HttpEntity entity = response.getEntity();
        
        if (response.getStatusLine().getStatusCode() == 403) {
            throw new IOException("Access forbidden: " + url);
        } else if (response.getStatusLine().getStatusCode() > 200) {
            throw new IOException("HTTP request to " + url + " failed: " + EntityUtils.toString(entity));
        }

        return entity.getContent();
    }

    
    public Model getRDFResponse(String url, String method, Map<String, String> params, Map<String, String> headers, String responseType) throws IOException {       

        InputStream content = getHTTPResponse(url, method, params, headers);
        Model result = ModelFactory.createDefaultModel();
        result.read(content, null, responseType);        
        content.close();
        
        return result;
    }
    
    
    private HttpRequestBase buildRequest(String url, String method, Map<String, String> params, Map<String, String> headers) {
        HttpRequestBase request;
        
        try {
            if (POST.equals(method.strip().toLowerCase())) {
                request = new HttpPost(url);
                List<NameValuePair> paramList = new ArrayList<NameValuePair>();
                for (Map.Entry<String, String> param : params.entrySet()) {
                    paramList.add(new BasicNameValuePair(param.getKey(), param.getValue()));
                }
                
                ((HttpPost)request).setEntity(new UrlEncodedFormEntity(paramList));
            } else {
                
                URIBuilder builder = new URIBuilder(url);
                for (Map.Entry<String, String> param : params.entrySet()) {
                    builder.addParameter(param.getKey(), param.getValue());
                }
                
                request = new HttpGet(builder.build());
            }
            
            
        } catch(URISyntaxException|UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Invalid service URI: " + url);
        }

        for (Map.Entry<String, String> header : headers.entrySet()) {
            request.addHeader(header.getKey(), header.getValue());
        }
        
        return request;
    }
    
    

    
}
