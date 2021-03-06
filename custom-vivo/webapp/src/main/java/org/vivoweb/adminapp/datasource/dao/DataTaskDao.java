package org.vivoweb.adminapp.datasource.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.vivoweb.adminapp.datasource.DataSourceUpdateFrequency;
import org.vivoweb.adminapp.datasource.DataTask;
import org.vivoweb.adminapp.datasource.DataTaskStatus;
import org.vivoweb.adminapp.datasource.SparqlEndpointParams;
import org.vivoweb.adminapp.datasource.ingest.DataIngest;
import org.vivoweb.adminapp.datasource.merge.DataMerge;
import org.vivoweb.adminapp.datasource.publish.DataPublish;
import org.vivoweb.adminapp.datasource.util.RDFUtils;

public class DataTaskDao {
    
    public static final String ADMIN_APP_TBOX = "http://vivoweb.org/ontology/adminapp/";
    private static final String DATATASK = ADMIN_APP_TBOX + "DataTask";
    private static final String DATAINGEST = ADMIN_APP_TBOX + "DataIngest";
    private static final String DATAMERGE = ADMIN_APP_TBOX + "DataMerge";
    private static final String DATAPUBLISH = ADMIN_APP_TBOX + "DataPublish";
    private static final String USESSPARQLENDPOINT = ADMIN_APP_TBOX + "usesSparqlEndpoint";
    private static final String PUBLISHTOENDPOINT = ADMIN_APP_TBOX + "publishToEndpoint";
    private static final String PRIORITY = ADMIN_APP_TBOX + "priority";
    private static final String SERVICEURI = ADMIN_APP_TBOX + "serviceURI";
    private static final String HTTPMETHOD = ADMIN_APP_TBOX + "httpMethod";
    private static final String HTTPPARAM = ADMIN_APP_TBOX + "httpParam";
    private static final String HTTPHEADER = ADMIN_APP_TBOX + "httpHeader";
    private static final String RESPONSEFORMAT = ADMIN_APP_TBOX + "responseFormat";
    private static final String RESULTNUM = ADMIN_APP_TBOX + "resultNum";
    private static final String PROGRESS = ADMIN_APP_TBOX + "progress";
    private static final String ISOK = ADMIN_APP_TBOX + "isOK";
    private static final String MESSAGE = ADMIN_APP_TBOX + "message";
    private static final String ENDPOINTURI = ADMIN_APP_TBOX + "endpointURI";
    private static final String ENDPOINTUPDATEURI = ADMIN_APP_TBOX + "endpointUpdateURI";
    private static final String ENDPOINTUSERNAME = ADMIN_APP_TBOX + "username";
    private static final String ENDPOINTPASSWORD = ADMIN_APP_TBOX + "password";
    private static final String LASTUPDATE = ADMIN_APP_TBOX + "lastUpdate";
    public static final String NEXTUPDATE = ADMIN_APP_TBOX + "nextUpdate";
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String UPDATEFREQUENCY = ADMIN_APP_TBOX + "updateFrequency";
    public static final String SCHEDULEAFTER = ADMIN_APP_TBOX + "scheduleAfter";
    public static final String MOSTSPECIFICTYPE = "http://vitro.mannlib.cornell.edu/ns/vitro/0.7#mostSpecificType";
    
    private static final Log log = LogFactory.getLog(DataTaskDao.class);
    
    private final RDFUtils rdfUtils = new RDFUtils();
    private final ModelConstructor readModelConstructor;
    private final Model aboxModel;
    private final String ingestTasksQuery = getDataSourcesQuery(DATAINGEST);
    private final String mergeTasksQuery = getDataSourcesQuery(DATAMERGE);
    private final String publishTasksQuery = getDataSourcesQuery(DATAPUBLISH);
    
    
    
    public DataTaskDao(ModelConstructor modelConstructor, Model aboxModel) {
        this.readModelConstructor = modelConstructor;
        this.aboxModel = aboxModel;
    }
    
    
    public String getAllDataSourcesQuery() {
        return getDataSourcesQuery(DATATASK);
    }
    
    public String getDataSourcesQuery(String taskClass) {
        return "CONSTRUCT { \n" +
                "    ?taskURI ?p ?o . \n" +
                "    ?endpoint ?endpointP ?endpointO . \n" +
                "} WHERE { \n" +
                "    ?taskURI a <" + taskClass + "> . \n" +
                "    ?taskURI ?p ?o . \n" +
                "    OPTIONAL { \n" +
                "        ?taskURI ?endpointOP ?endpoint . \n" +
                "        ?endpoint ?endpointP ?endpointO \n" +
                "    } \n" +
                "} \n";        
    }
    
    public List<DataTask> listIngestTasks() {
        return listTasks(ingestTasksQuery);
    }
    
    public List<DataTask> listMergeTasks() {
        return listTasks(mergeTasksQuery);
    }
     
    public List<DataTask> listPublishTasks() {
        return listTasks(publishTasksQuery);
    }
    
    public List<DataTask> listTasks(String query) {
        List<DataTask> result = new ArrayList<DataTask>();
        Model model = construct(query);
        
        ResIterator resIt = model.listResourcesWithProperty(RDF.type, model.getResource(DATATASK));
        for (Resource res : resIt.toList()) {
            if(res.isURIResource()) {
                result.add(createTaskByClassURI(res.getURI(), model));
            }
        }
        
        Collections.sort(result, new DataTaskPriorityComparator());
        
        return result;
    }
    
    
    public void saveTaskStatus(DataTaskStatus status, String taskURI) {
        Resource taskResource = aboxModel.getResource(taskURI);
        
        aboxModel.removeAll(taskResource, aboxModel.getProperty(RESULTNUM), (RDFNode) null);
        aboxModel.removeAll(taskResource, aboxModel.getProperty(ISOK), (RDFNode) null);
        aboxModel.removeAll(taskResource, aboxModel.getProperty(MESSAGE), (RDFNode) null);
        aboxModel.removeAll(taskResource, aboxModel.getProperty(LASTUPDATE), (RDFNode) null);
        
        aboxModel.addLiteral(taskResource, aboxModel.getProperty(RESULTNUM), status.getTotalRecords());
        aboxModel.addLiteral(taskResource, aboxModel.getProperty(ISOK), status.isStatusOk());
        
        if (null != status.getMessage()) {
            aboxModel.add(taskResource, aboxModel.getProperty(MESSAGE), status.getMessage());
        }
        
        String timestamp = new LocalDateTime().toString(DateTimeFormat.forPattern(DATE_TIME_PATTERN));
        aboxModel.add(taskResource, aboxModel.getProperty(LASTUPDATE), timestamp, XSDDatatype.XSDdateTime);
    }
    
    public void deleteNextUpdateDateTime(String taskURI) {
        aboxModel.removeAll(aboxModel.getResource(taskURI), aboxModel.getProperty(NEXTUPDATE), null);
    }
    
    public void setNextUpdate(String taskURI, LocalDateTime nextUpdate) {
        deleteNextUpdateDateTime(taskURI);
        aboxModel.add(aboxModel.getResource(taskURI), aboxModel.getProperty(NEXTUPDATE), 
                      nextUpdate.toString(DateTimeFormat.forPattern(DATE_TIME_PATTERN)), XSDDatatype.XSDdateTime);
    }
    
    public void saveResultNum(String URI, long num) {
        aboxModel.removeAll(aboxModel.getResource(URI), aboxModel.getProperty(RESULTNUM), null);
        aboxModel.addLiteral(aboxModel.getResource(URI), aboxModel.getProperty(RESULTNUM), num);
    }
    
    public void saveProgress(String URI, int percent) {
        aboxModel.removeAll(aboxModel.getResource(URI), aboxModel.getProperty(PROGRESS), null);
        aboxModel.addLiteral(aboxModel.getResource(URI), aboxModel.getProperty(PROGRESS), percent);
    }
    
    private Model construct(String queryStr) {
        return readModelConstructor.construct(queryStr);
    }

    public DataTask getTask(String URI) {
        String dataSourceQuery = getAllDataSourcesQuery().replaceAll("\\?taskURI", "<" + URI + ">");
        return createTaskByClassURI(URI, construct(dataSourceQuery));
    }
    
    private DataTask createTaskByClassURI(String URI, Model model) {
        String classURI = rdfUtils.getURIValue(URI, MOSTSPECIFICTYPE, model);
        DataTask result;
        
        switch(classURI) {
            case DATAINGEST:
                result = createIngestTask(URI, model);
                break;
                
            case DATAMERGE:
                result = createMergeTask(URI, model);
                break;
                
            case DATAPUBLISH:
                result = createPublishTask(URI, model);
                break;
                
            default: throw new IllegalStateException("Unknown task class: " + classURI);
        }
        
        return result;
    }
    
    private DataIngest createIngestTask(String URI, Model model) {
        DataIngest result = new DataIngest(URI, 
                                           rdfUtils.getString(URI, SERVICEURI, model), 
                                           rdfUtils.getString(URI, HTTPMETHOD, model),
                                           rdfUtils.getString(URI, RESPONSEFORMAT, model));
        
        StmtIterator httpParamIt = model.listStatements(model.getResource(URI), model.getProperty(HTTPPARAM), (RDFNode) null);
        for (Statement current : httpParamIt.toList()) {
            String[] paramTokens = current.getString().split("=");
            
            if (1 > paramTokens.length) {
                log.warn("Not wellformed HTTP parameter (asserted: key=value): " + current.getString());
            }
            
            result.addHttpParam(paramTokens[0], paramTokens[1]);
        }
        
        StmtIterator httpHeaderIt = model.listStatements(model.getResource(URI), model.getProperty(HTTPHEADER), (RDFNode) null);
        for (Statement current : httpHeaderIt.toList()) {
            String keyValue = current.getString();
            int sepIndex = keyValue.indexOf("=");
            
            if (-1 == sepIndex) {
                log.warn("Not wellformed HTTP header (asserted: key=value): " + keyValue);
            }
            
            result.addHttpHeader(keyValue.substring(0, sepIndex), keyValue.substring(sepIndex+1));
        }
        
        initTask(URI, model, result);
        
        return result;
    }
    
    private DataMerge createMergeTask(String URI, Model model) {
        DataMerge result = new DataMerge(URI);
        
        initTask(URI, model, result);
        
        return result;
    }
    
    private DataPublish createPublishTask(String URI, Model model) {
        DataPublish result = new DataPublish(URI, getEndpointParams(URI, PUBLISHTOENDPOINT, model));
        
        initTask(URI, model, result);
        
        return result;
    }
    
    private void initTask(String URI, Model model, DataTask task) {
        task.setName(rdfUtils.getString(URI, RDFS.label.getURI(), model));
        task.setLastUpdate(rdfUtils.getString(URI, LASTUPDATE, model));
        task.setNextUpdate(rdfUtils.getString(URI, NEXTUPDATE, model));
        task.setPriority(rdfUtils.getIntValue(URI, PRIORITY, model, Integer.MAX_VALUE));
        task.setEndpointParams(getEndpointParams(URI, USESSPARQLENDPOINT, model));
        
        task.setUpdateFrequency(DataSourceUpdateFrequency.valueByURI(rdfUtils.getURIValue(URI, UPDATEFREQUENCY, model)));
        task.setScheduleAfterURI(rdfUtils.getURIValue(URI, SCHEDULEAFTER, model));
        
        task.getStatus().setTotalRecords(rdfUtils.getLongValue(URI, RESULTNUM, model, 0));
        task.getStatus().setStatusOk(rdfUtils.getBooleanValue(URI, ISOK, model, true));
        task.getStatus().setMessage(rdfUtils.getString(URI, MESSAGE, model));
        task.getStatus().setProgress(rdfUtils.getIntValue(URI, PROGRESS, model, 0));
    }
    
    private SparqlEndpointParams getEndpointParams(String taskURI, String propertyURI, Model model) {
        String uri = rdfUtils.getURIValue(taskURI, propertyURI, model);
               
        SparqlEndpointParams result = new SparqlEndpointParams();
        result.setEndpointURI(rdfUtils.getString(uri, ENDPOINTURI, model));
        result.setEndpointUpdateURI(rdfUtils.getString(uri, ENDPOINTUPDATEURI, model));
        result.setUsername(rdfUtils.getString(uri, ENDPOINTUSERNAME, model));
        result.setPassword(rdfUtils.getString(uri, ENDPOINTPASSWORD, model));
        
        return result;
    }
    

    private class DataTaskPriorityComparator implements Comparator<DataTask> {

        public int compare(DataTask o1, DataTask o2) {
            if(o1 == null && o2 != null) {
                return 1;
            } else if (o2 == null && o1 != null) {
                return -1;
            } else if (o1 == null && o1 == null) {
                return 0;
            } else {
                return o1.getPriority() - o2.getPriority();
            }
        }
        
    }
    
}

