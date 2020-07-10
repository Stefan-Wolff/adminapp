package org.vivoweb.adminapp.datasource.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.vivoweb.adminapp.datasource.DataSourceUpdateFrequency;
import org.vivoweb.adminapp.datasource.DataTask;
import org.vivoweb.adminapp.datasource.DataTaskStatus;
import org.vivoweb.adminapp.datasource.SparqlEndpointParams;
import org.vivoweb.adminapp.datasource.ingest.DataIngest;
import org.vivoweb.adminapp.datasource.merge.DataMerge;
import org.vivoweb.adminapp.datasource.publish.DataPublish;

public class DataTaskDao {
    
    public static final String ADMIN_APP_TBOX = "http://vivoweb.org/ontology/adminapp/";
    private static final String DATATASK = ADMIN_APP_TBOX + "DataTask";
    private static final String DATAINGEST = ADMIN_APP_TBOX + "DataIngest";
    private static final String DATAMERGE = ADMIN_APP_TBOX + "DataMerge";
    private static final String DATAPUBLISH = ADMIN_APP_TBOX + "DataPublish";
    private static final String USESSPARQLENDPOINT = ADMIN_APP_TBOX + "usesSparqlEndpoint";
    private static final String PRIORITY = ADMIN_APP_TBOX + "priority";
    private static final String SERVICEURI = ADMIN_APP_TBOX + "serviceURI";
    private static final String HTTPMETHOD = ADMIN_APP_TBOX + "httpMethod";
    private static final String HTTPPARAM = ADMIN_APP_TBOX + "httpParam";
    private static final String HTTPHEADER = ADMIN_APP_TBOX + "httpHeader";
    private static final String RESPONSEFORMAT = ADMIN_APP_TBOX + "responseFormat";
    private static final String RESULTNUM = ADMIN_APP_TBOX + "resultNum";
    private static final String ISOK = ADMIN_APP_TBOX + "isOK";
    private static final String MESSAGE = ADMIN_APP_TBOX + "message";
    private static final String ENDPOINTURI = ADMIN_APP_TBOX + "endpointURI";
    private static final String ENDPOINTUPDATEURI = ADMIN_APP_TBOX + "endpointUpdateURI";
    private static final String ENDPOINTUSERNAME = ADMIN_APP_TBOX + "username";
    private static final String ENDPOINTPASSWORD = ADMIN_APP_TBOX + "password";
    private static final String GRAPHURI = ADMIN_APP_TBOX + "graphURI";
    public static final String LASTUPDATE = ADMIN_APP_TBOX + "lastUpdate";
    public static final String NEXTUPDATE = ADMIN_APP_TBOX + "nextUpdate";
    public static final String UPDATEFREQUENCY = ADMIN_APP_TBOX + "updateFrequency";
    public static final String SCHEDULEAFTER = ADMIN_APP_TBOX + "scheduleAfter";
    
    private static final String MOSTSPECIFICTYPE = "http://vitro.mannlib.cornell.edu/ns/vitro/0.7#mostSpecificType";
    
    private static final Log log = LogFactory.getLog(DataTaskDao.class);
    
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
        return "CONSTRUCT { " +
                "    ?taskURI ?p ?o . " +
                "    ?endpoint ?endpointP ?endpointO . " +
                "} WHERE { " +
                "    ?taskURI a <" + taskClass + "> . " +
                "    ?taskURI ?p ?o . " +
                "    OPTIONAL { " +
                "        ?taskURI <" + USESSPARQLENDPOINT +"> ?endpoint . " +
                "        ?endpoint ?endpointP ?endpointO " +
                "    }" +
                "}";        
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
                result.add(createTaskByModel(res.getURI(), model));
            }
        }
        
        Collections.sort(result, new DataTaskPriorityComparator());
        
        return result;
    }
    
    
    public void saveTaskStatus(DataTaskStatus status, String taskURI) {
        aboxModel.removeAll(aboxModel.getResource(taskURI), aboxModel.getProperty(RESULTNUM), null);
        aboxModel.removeAll(aboxModel.getResource(taskURI), aboxModel.getProperty(ISOK), null);
        aboxModel.removeAll(aboxModel.getResource(taskURI), aboxModel.getProperty(MESSAGE), null);
        
        aboxModel.addLiteral(aboxModel.getResource(taskURI), aboxModel.getProperty(RESULTNUM), status.getTotalRecords());
        aboxModel.addLiteral(aboxModel.getResource(taskURI), aboxModel.getProperty(ISOK), status.isStatusOk());
        
        if (null != status.getMessage()) {
            aboxModel.add(aboxModel.getResource(taskURI), aboxModel.getProperty(MESSAGE), status.getMessage());
        }
    }
    
    public void saveResultNum(String URI, long num) {
        aboxModel.removeAll(aboxModel.getResource(URI), aboxModel.getProperty(RESULTNUM), null);
        aboxModel.addLiteral(aboxModel.getResource(URI), aboxModel.getProperty(RESULTNUM), num);
    }
    
    private Model construct(String queryStr) {
        return readModelConstructor.construct(queryStr);
    }

    public DataTask getDataSource(String URI) {
        String dataSourceQuery = getAllDataSourcesQuery().replaceAll("\\?taskURI", "<" + URI + ">");
        return createTaskByModel(URI, construct(dataSourceQuery));
    }
    


    private DataTask createTaskByModel(String URI, Model model) {
        String classURI = getURIValue(URI, MOSTSPECIFICTYPE, model);
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
                                                getStringValue(URI, SERVICEURI, model), 
                                                getStringValue(URI, HTTPMETHOD, model),
                                                getStringValue(URI, RESPONSEFORMAT, model));
        
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
        DataPublish result = new DataPublish(URI);
        
        initTask(URI, model, result);
        
        return result;
    }
    
    private void initTask(String URI, Model model, DataTask task) {
        task.setName(getStringValue(URI, RDFS.label.getURI(), model));
        task.setLastUpdate(getStringValue(URI, LASTUPDATE, model));
        task.setNextUpdate(getStringValue(URI, NEXTUPDATE, model));
        task.setPriority(getIntValue(URI, PRIORITY, model));
        task.setResultsGraphURI(getStringValue(URI, GRAPHURI, model)); 
        
        task.setUpdateFrequency(DataSourceUpdateFrequency.valueByURI(getURIValue(URI, UPDATEFREQUENCY, model)));
        task.setScheduleAfterURI(getURIValue(URI, SCHEDULEAFTER, model));
        
        task.getStatus().setTotalRecords(getLongValue(URI, RESULTNUM, model, 0));
        task.getStatus().setStatusOk(getBooleanValue(URI, ISOK, model, true));
        task.getStatus().setMessage(getStringValue(URI, MESSAGE, model));

        StmtIterator endpit = model.listStatements(model.getResource(URI), model.getProperty(USESSPARQLENDPOINT), (RDFNode) null);
        for (Statement current : endpit.toList()) {
            if(current.getObject().isURIResource()) {
                String endpoint = current.getObject().asResource().getURI();
                SparqlEndpointParams endpointParams = new SparqlEndpointParams();
                endpointParams.setEndpointURI(getStringValue(endpoint, ENDPOINTURI, model));
                endpointParams.setEndpointUpdateURI(getStringValue(endpoint, ENDPOINTUPDATEURI, model));
                endpointParams.setUsername(getStringValue(endpoint, ENDPOINTUSERNAME, model));
                endpointParams.setPassword(getStringValue(endpoint, ENDPOINTPASSWORD, model));
                task.setEndpointParams(endpointParams); 
                break;
            }
        }
    }
    
    private String getStringValue(String subjectURI, String propertyURI, Model model) {
        StmtIterator sit = model.listStatements(model.getResource(subjectURI), 
                model.getProperty(propertyURI), (RDFNode) null);
        try {
            while(sit.hasNext()) {
                Statement stmt = sit.next();
                if(stmt.getObject().isLiteral()) {
                    return stmt.getObject().asLiteral().getLexicalForm();
                }
            }
            return null;
        } finally {
            sit.close();
        }
    }
    
    private String getURIValue(String subjectURI, String propertyURI, Model model) {
        StmtIterator sit = model.listStatements(model.getResource(subjectURI), 
                model.getProperty(propertyURI), (Resource) null);
        try {
            for (Statement stmt : sit.toList()) {
                if(stmt.getObject().isURIResource()) {
                    return stmt.getObject().asResource().getURI();
                }
            }
            return null;
        } finally {
            sit.close();
        }
    }
    
    
    private int getIntValue(String subjectURI, String propertyURI, 
            Model model) {
        StmtIterator sit = model.listStatements(model.getResource(subjectURI), 
                model.getProperty(propertyURI), (RDFNode) null);
        try {
            while(sit.hasNext()) {
                Statement stmt = sit.next();
                if(stmt.getObject().isLiteral()) {
                    Literal lit = stmt.getObject().asLiteral();
                    Object obj = lit.getValue();
                    if(obj instanceof Integer) {
                        Integer intg = (Integer) obj;
                        return intg;
                    }
                }
            }
            return Integer.MAX_VALUE;
        } finally {
            sit.close();
        }
    }
    
    private long getLongValue(String subjectURI, String propertyURI, Model model, long notFound) {
        StmtIterator sit = model.listStatements(model.getResource(subjectURI), 
                model.getProperty(propertyURI), (RDFNode) null);
        try {
            for (Statement stmt : sit.toList()) {
                if(stmt.getObject().isLiteral()) {
                    Literal lit = stmt.getObject().asLiteral();
                    Object obj = lit.getValue();
                    if(obj instanceof Long) {
                        return (Long) obj;
                    }
                    if (obj instanceof Integer) {
                        return (Integer) obj;
                    }
                }
            }
            return notFound;
        } finally {
            sit.close();
        }
    }
    
    private boolean getBooleanValue(String subjectURI, String propertyURI, Model model, boolean notFound) {
        StmtIterator sit = model.listStatements(model.getResource(subjectURI), 
                model.getProperty(propertyURI), (RDFNode) null);
        try {
            for (Statement stmt : sit.toList()) {
                if(stmt.getObject().isLiteral()) {
                    Literal lit = stmt.getObject().asLiteral();
                    Object obj = lit.getValue();
                    if(obj instanceof Boolean) {
                        return (Boolean) obj;
                    }
                }
            }
            return notFound;
        } finally {
            sit.close();
        }
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

