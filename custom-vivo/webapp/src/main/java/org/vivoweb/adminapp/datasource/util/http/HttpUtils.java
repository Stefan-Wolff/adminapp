package org.vivoweb.adminapp.datasource.util.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

public class HttpUtils {
    
    public static final String DEFAULT_USER_AGENT = "adminapp VIVO(http://vivoweb.org/adminapp)";
    private String userAgent = DEFAULT_USER_AGENT;
    private long msBetweenRequests = 125; // ms
    private HttpClient httpClient;
    private static final Log log = LogFactory.getLog(HttpUtils.class);
    long lastRequestMillis = 0; 
    
    /**
     * Construct an instance of HttpUtils with optional configuration values.
     * @param userAgent User-Agent header value for each request.  If null,
     *                  a default adminapp VIVO string will be used.
     * @param msBetweenRequests number of milliseconds to wait between 
     *                          subsequent requests (in the absence of 503
     *                          rate-limiting errors).  If null, a default
     *                          value of 125 ms will be used.
     */            
    public HttpUtils(String userAgent, Long msBetweenRequests) {
        if(userAgent != null) {
            this.userAgent = userAgent;
        }
        if (msBetweenRequests != null) {
            this.msBetweenRequests = msBetweenRequests;
        }
        buildHttpClient();
    }
    
    /**
     * Construct an instance of HttpUtils with default User-Agent string and
     * wait time between requests.
     */
    public HttpUtils() {
        buildHttpClient();
    }
    
    private void buildHttpClient() {
        this.httpClient = HttpClients.custom()
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setUserAgent(userAgent)
                .build();
    }
    
    public String getHttpGetResponse(String url) throws IOException {
        HttpGet get = new HttpGet(url);
        get.setHeader("Accept-charset", "utf-8");
        HttpResponse response;
        try {
            response = execute(get, httpClient);
        } catch (Exception e) {
            try {
                Thread.sleep(2000);
                response = execute(get, httpClient);
            } catch (InterruptedException e1) {
                throw new RuntimeException(e1);
            } catch (Exception e2) {
                try {
                    Thread.sleep(4000);
                    response = execute(get, httpClient);
                } catch (InterruptedException e3) {
                    throw new RuntimeException(e3);
                }
            }
        }
        if(response.getStatusLine().getStatusCode() >= 400) {
            throw new RuntimeException(response.getStatusLine().getStatusCode() 
                    + ": " + response.getStatusLine().getReasonPhrase() + " (" + url + ")");    
        }
        try {            
            return EntityUtils.toString(response.getEntity(), "UTF-8");
        } finally {
            EntityUtils.consume(response.getEntity());
        }   
    }
    
    private HttpResponse execute(HttpUriRequest request, HttpClient httpClient) throws ClientProtocolException, IOException {                
        try {
            long msToSleep = this.msBetweenRequests - 
                    (System.currentTimeMillis() - this.lastRequestMillis);
            if(msToSleep > 0) {
                Thread.sleep(msToSleep);
            }
            this.lastRequestMillis = System.currentTimeMillis();
            HttpResponse response = httpClient.execute(request);
            if(response.getStatusLine().getStatusCode() == 503) {
                throw new RuntimeException("503 Unavailable");
            } else {
                return response;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    
    public String getHttpPostResponse(String url, String payload, 
            String contentType) {
        HttpPost post = new HttpPost(url);
        post.addHeader("content-type", contentType);
        try {
            ContentType ctype = ContentType.create(contentType, "UTF-8");
            post.setEntity(new StringEntity(payload, ctype));
        } catch (Exception e) {
            log.warn("Unable to use content type " + contentType +
                    ".  Using default UTF-8 StringEntity");
            post.setEntity(new StringEntity(payload, "UTF-8"));
        }
        try {
            HttpResponse response = execute(post, httpClient);
            try {
                return EntityUtils.toString(response.getEntity());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                EntityUtils.consume(response.getEntity());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } 

    }
    
    public Model getRDFResponse(String url, String method, Map<String, String> params, Map<String, String> headers, String responseType) throws IOException {
        URIBuilder builder;
        HttpRequestBase request;
        
        try {
            if ("post".equals(method.strip().toLowerCase())) {
                request = new HttpPost(url);
                List<NameValuePair> paramList = new ArrayList<NameValuePair>();
                for (Map.Entry<String, String> param : params.entrySet()) {
                    paramList.add(new BasicNameValuePair(param.getKey(), param.getValue()));
                }
                
                ((HttpPost)request).setEntity(new UrlEncodedFormEntity(paramList));
            } else {
                
                builder = new URIBuilder(url);
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
        
        HttpEntity entity = null;
        try {
            HttpResponse response = execute(request, httpClient);
            entity = response.getEntity();
            Model m = ModelFactory.createDefaultModel();
            
            return m.read(entity.getContent(), null, responseType);
        }
        finally {
            EntityUtils.consume(entity);
        }
    }
    
    
    public Model getRDFLinkedDataResponse(String url) throws IOException {
        HttpGet get = new HttpGet(url);
        get.setHeader("Accept", "application/rdf+xml");
        HttpResponse response = execute(get, httpClient);
        try {
            byte[] entity = EntityUtils.toByteArray(response.getEntity());
            Model model = ModelFactory.createDefaultModel();
            model.read(new ByteArrayInputStream(entity), null, "RDF/XML");
            return model;
        } finally {
            EntityUtils.consume(response.getEntity());
        }
    }
   
    
}
