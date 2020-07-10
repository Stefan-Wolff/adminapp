package org.vivoweb.adminapp.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jena.rdf.model.Model;
import org.vivoweb.adminapp.datasource.DataSourceScheduler;
import org.vivoweb.adminapp.datasource.DataTask;
import org.vivoweb.adminapp.datasource.RDFServiceModelConstructor;
import org.vivoweb.adminapp.datasource.dao.DataTaskDao;

import edu.cornell.mannlib.vitro.webapp.auth.permissions.SimplePermission;
import edu.cornell.mannlib.vitro.webapp.auth.requestedAction.AuthorizationRequest;
import edu.cornell.mannlib.vitro.webapp.controller.VitroRequest;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.FreemarkerHttpServlet;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.ResponseValues;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.TemplateResponseValues;
import edu.cornell.mannlib.vitro.webapp.modelaccess.ModelAccess;

/**
 * A controller for retrieving for display lists of available 
 * data, merging and publishing services
 * @author Brian Lowe
 *
 */
public class DataSourceListController extends FreemarkerHttpServlet {

    private static final String LIST_DATA_SOURCES_TEMPLATE = "listDataSources.ftl";
    private static final Log log = LogFactory.getLog(DataSourceListController.class);
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
        log.debug("Data source type: " + type);
        
        List<DataTask> sources;
        if("merge".equals(type)) {
            sources = dsm.listMergeTasks();
        } else if("publish".equals(type)) {
            sources = dsm.listPublishTasks();
        } else {
            sources = dsm.listIngestTasks();
        }
        
        DataSourceScheduler scheduler = DataSourceScheduler.getInstance(getServletContext());
        for (DataTask task : sources) {
            String dataSourceURI = task.getURI();
            task.getStatus().setRunning(scheduler.isRunning(dataSourceURI));
        }
        
        return doListDataSources(sources, vreq);
    }    
    
    private TemplateResponseValues doListDataSources(List<DataTask> tasks, VitroRequest vreq) 
                    throws IOException {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("dataSources", tasks);
        data.put("type", vreq.getParameter("type"));
        return new TemplateResponseValues(LIST_DATA_SOURCES_TEMPLATE, data);
    }
    
    
}
