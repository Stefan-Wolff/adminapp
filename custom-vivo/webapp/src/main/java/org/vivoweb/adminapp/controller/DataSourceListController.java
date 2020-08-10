package org.vivoweb.adminapp.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.vivoweb.adminapp.datasource.DataSourceScheduler;
import org.vivoweb.adminapp.datasource.DataTask;
import org.vivoweb.adminapp.datasource.RDFServiceModelConstructor;
import org.vivoweb.adminapp.datasource.SparqlEndpointParams;
import org.vivoweb.adminapp.datasource.dao.DataTaskDao;

import edu.cornell.mannlib.vitro.webapp.auth.permissions.SimplePermission;
import edu.cornell.mannlib.vitro.webapp.auth.requestedAction.AuthorizationRequest;
import edu.cornell.mannlib.vitro.webapp.controller.VitroRequest;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.FreemarkerHttpServlet;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.ResponseValues;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.TemplateResponseValues;
import edu.cornell.mannlib.vitro.webapp.modelaccess.ModelAccess;

/**
 * A controller for retrieving for display lists of available configuration of
 * ingest tasks, merging tasks and publishing tasks
 * 
 * @author Brian Lowe, swolff
 *
 */
public class DataSourceListController extends FreemarkerHttpServlet {

    private static final String LIST_DATA_SOURCES_TEMPLATE = "listDataSources.ftl";
    protected static final AuthorizationRequest REQUIRED_ACTIONS = 
            SimplePermission.USE_MISCELLANEOUS_ADMIN_PAGES.ACTION;
    
    @Override
    protected AuthorizationRequest requiredActions(VitroRequest vreq) {
        return REQUIRED_ACTIONS;
    }
    
    @Override
    protected ResponseValues processRequest(VitroRequest vreq) throws IOException {
        Model aboxModel = ModelAccess.on(this.getServletContext()).getOntModelSelector().getABoxModel();
        DataTaskDao dsm = new DataTaskDao(new RDFServiceModelConstructor(vreq.getRDFService()), aboxModel);
        String type = vreq.getParameter("type");
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("type", vreq.getParameter("type"));
        
        List<DataTask> tasks;
        if("merge".equals(type)) {
            tasks = dsm.listMergeTasks();
        } else if("publish".equals(type)) {
            tasks = dsm.listPublishTasks();
        } else {
            tasks = dsm.listIngestTasks();
            List<SparqlEndpointParams> endpointParams = new ArrayList<SparqlEndpointParams>(tasks.size());
            
            for (DataTask task : tasks) {
                endpointParams.add(task.getEndpointParams());
            }
            
            data.put("endpoints", endpointParams);
        }
        
        DataSourceScheduler scheduler = DataSourceScheduler.getInstance(getServletContext());
        for (DataTask task : tasks) {
            String dataSourceURI = task.getURI();
            task.getStatus().setRunning(scheduler.isRunning(dataSourceURI));
        }
        
        data.put("dataSources", tasks);
        
        
        return new TemplateResponseValues(LIST_DATA_SOURCES_TEMPLATE, data);
    }    
    
    
}
