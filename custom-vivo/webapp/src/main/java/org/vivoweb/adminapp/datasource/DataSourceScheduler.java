package org.vivoweb.adminapp.datasource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.vivoweb.adminapp.datasource.dao.DataSourceDao;

import edu.cornell.mannlib.vitro.webapp.config.ConfigurationProperties;
import edu.cornell.mannlib.vitro.webapp.modelaccess.ModelAccess;
import edu.cornell.mannlib.vitro.webapp.rdfservice.ChangeListener;
import edu.cornell.mannlib.vitro.webapp.rdfservice.ModelChange;
import edu.cornell.mannlib.vitro.webapp.rdfservice.ModelChange.Operation;
import edu.cornell.mannlib.vitro.webapp.rdfservice.RDFService;
import edu.cornell.mannlib.vitro.webapp.rdfservice.RDFServiceException;
import edu.cornell.mannlib.vitro.webapp.rdfservice.impl.RDFServiceUtils;
import edu.cornell.mannlib.vitro.webapp.utils.threads.VitroBackgroundThread;
import edu.cornell.mannlib.vitro.webapp.utils.threads.VitroBackgroundThread.WorkLevel;

public class DataSourceScheduler implements ServletContextListener, ChangeListener {

    private ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private HashMap<String, ScheduledFuture<?>> scheduledTasks = new HashMap<String, ScheduledFuture<?>>();
    private Model aboxModel;
    private DataSourceDao dataSourceDao;
    private RDFService rdfService;
    private static final String DATASOURCE_CONFIG_PROPERTY_PREFIX = "datasource.";
    private Map<String, String> datasourceConfigurationProperties = new HashMap<String, String>();
    private static final String DEFAULT_NAMESPACE_PROPERTY = "Vitro.defaultNamespace"; 
    private final Runnings runnings = new Runnings();
    
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
    
    private static final Log log = LogFactory.getLog(DataSourceScheduler.class);
    
    public static DataSourceScheduler getInstance(ServletContext ctx) {
        Object o = ctx.getAttribute(DataSourceScheduler.class.getName());
        if (o instanceof DataSourceScheduler) {
            return (DataSourceScheduler) o;
        } else {
            throw new RuntimeException("No DataSourceScheduler was set up "
                    + "in the supplied context");
        }
    }
    
    
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        try {
            log.info("Attempting to cancel all scheduled tasks...");
            try {
                for(ScheduledFuture<?> f : scheduledTasks.values()) {
                    try {
                        f.cancel(true);        
                    } catch (Exception e) {
                        log.debug(e, e);
                    }
                }
            } catch (Exception e) {
                log.debug(e, e);
            }
            log.info("Attempting to shut down scheduler...");
            scheduler.shutdown();
            log.info("Task scheduler shut down successfully.");
            scheduler.destroy();
            log.info("Task scheduler destroyed successfully.");
            sce.getServletContext().setAttribute(this.getClass().getName(), null);         
        } catch (Exception e) {
            log.debug(e, e);
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        populateDataSourceRelatedConfigurationProperties(
                ConfigurationProperties.getBean(sce).getPropertyMap());
        try {
            this.aboxModel = ModelAccess.on(sce.getServletContext()).getOntModelSelector().getABoxModel();
            this.rdfService = ModelAccess.on(sce.getServletContext()).getRDFService();
            this.dataSourceDao = new DataSourceDao(new RDFServiceModelConstructor(this.rdfService), this.aboxModel);
            
        } catch (Exception e) {
            throw new RuntimeException(this.getClass().getSimpleName() 
                    + " must be run after the context's RDFService is set up.");
        }
        scheduler.setPoolSize(20);
        scheduler.initialize();
        sce.getServletContext().setAttribute(this.getClass().getName(), this);
        try {
            RDFServiceUtils.getRDFServiceFactory(
                    sce.getServletContext()).registerListener(this);
        } catch (RDFServiceException e) {
            throw new RuntimeException(e);
        }
        if(!sce.getServletContext().getContextPath().isEmpty()) {
            String scheduleTasksInAllContexts = ConfigurationProperties.getBean(
                    sce).getProperty("org.vivoweb.adminapp.scheduleTasksInAllContexts"); 
            if( ("FALSE".equals(scheduleTasksInAllContexts))) {
                // guard against scheduling the same tasks twice if VIVO happens
                // to be deployed a second time in a context other than root
                log.warn("Not scheduling tasks because this is not the root context");
                return;
            }            
        }
        log.info("Task scheduler set up");
        scheduleDataSources();
    }
    
    private void populateDataSourceRelatedConfigurationProperties(
            Map<String, String> configurationProperties) {
        for(String key : configurationProperties.keySet()) {
            if(!key.startsWith(DATASOURCE_CONFIG_PROPERTY_PREFIX)) {
                continue;
            }
            datasourceConfigurationProperties.put(
                    key, configurationProperties.get(key));
        }
        datasourceConfigurationProperties.put(DEFAULT_NAMESPACE_PROPERTY, 
                configurationProperties.get(DEFAULT_NAMESPACE_PROPERTY));    
    }
    
    
    private void scheduleDataSources() {
        for(DataTask task : this.dataSourceDao.listIngestTasks()) {
            schedule(task);
        }
    }
    
    private void schedule(DataTask task) {
        // If 'schedule immediately after' has been set, remove any specific 
        // next update date and exit.
        if(task.getScheduleAfterURI() != null) {
            deleteNextUpdateDateTime(task.getURI());
        } else if (task.getNextUpdate() == null) {
            return;
        } else if (task.getUpdateFrequency() != null){
            computeNextUpdateAndScheduleTask(task);
        }
    }
    
    private void deleteNextUpdateDateTime(String dataSourceURI) {
        muteChangeListener(dataSourceURI);
        aboxModel.removeAll(
                aboxModel.getResource(dataSourceURI),
                aboxModel.getProperty(DataSourceDao.NEXTUPDATE), 
                null);
        unmuteChangeListener(dataSourceURI);
        return;
    }
    
    private void setNextUpdate(String dataSourceURI, LocalDateTime nextUpdate) {
        deleteNextUpdateDateTime(dataSourceURI);
        muteChangeListener(dataSourceURI);
        aboxModel.add(
                aboxModel.getResource(dataSourceURI),
                aboxModel.getProperty(DataSourceDao.NEXTUPDATE), 
                nextUpdate.toString(DateTimeFormat.forPattern(
                        DATE_TIME_PATTERN)), XSDDatatype.XSDdateTime);
        unmuteChangeListener(dataSourceURI);
    }
    
    private void computeNextUpdateAndScheduleTask(DataTask task) {
        try {
            LocalDateTime nextUpdate = DateTimeFormat.forPattern(
                    DATE_TIME_PATTERN).parseDateTime(
                            task.getNextUpdate()).toLocalDateTime();
            // Give ourselves a buffer of five minutes to avoid the chance 
            // of scheduling something that won't get run because the time
            // has already passed.
            LocalDateTime now = new LocalDateTime().plusMinutes(5);
            int giveUp = 100;
            while(now.isAfter(nextUpdate) && giveUp > 0) {
                giveUp--;
                nextUpdate = advanceByFrequency(nextUpdate, task.getUpdateFrequency());
            }
            setNextUpdate(task.getURI(), nextUpdate);
            scheduleTask(task.getURI(), nextUpdate);
        } catch (Exception e) {
            log.error(e, e);
            deleteNextUpdateDateTime(task.getURI());
        }
    }
    
    private void scheduleTask(String dataSourceURI, LocalDateTime dateTime) {
        Runnable task = new DataSourceRunner(dataSourceURI, new DataSourceTimestamper(aboxModel));
        this.scheduledTasks.put(dataSourceURI, scheduler.schedule(task, dateTime.toDateTime().toDate()));
        
        log.info("Scheduled " + dataSourceURI + " for " + dateTime.toString());
    }
    
    private LocalDateTime advanceByFrequency(LocalDateTime nextUpdate, 
            DataSourceUpdateFrequency updateFrequency) {
        if(DataSourceUpdateFrequency.DAILY == updateFrequency) {
            return nextUpdate.plusDays(1);
        } else if(DataSourceUpdateFrequency.WEEKLY == updateFrequency) {
            return nextUpdate.plusWeeks(1);
        } else if(DataSourceUpdateFrequency.MONTHLY == updateFrequency) {
            // For now, schedule next run on same day of the week instead of
            // truly monthly.
            return nextUpdate.plusWeeks(4);
        } else {
            return nextUpdate;
        }
    }
    
    public void startNow(String dataSourceURI) {
        DataSourceRunner runner = new DataSourceRunner(dataSourceURI, new DataSourceTimestamper(aboxModel));
        VitroBackgroundThread starter = new VitroBackgroundThread(runner, dataSourceURI + "-starter");
        starter.setWorkLevel(WorkLevel.WORKING);
        starter.start();
    }
    
    public void stopNow(String dataSourceURI) {
        runnings.stop(dataSourceURI);
    }

    private DataTask loadTask(String dataSourceURI) {
        DataTask result = this.dataSourceDao.getDataSource(dataSourceURI);
        if(result == null) {
            throw new RuntimeException("Task " + dataSourceURI + " not found");
        }
        return result;
    }
    
    private class DataSourceTimestamper {
        
        private Model model;

        public DataSourceTimestamper(Model model) {
            this.model = model;
        }
        
        private void timestampLastUpdate(String dataSourceURI) {
            LocalDateTime now = new LocalDateTime();
            String timestampStr = now.toString(DateTimeFormat.forPattern(DATE_TIME_PATTERN));
            Resource dataSource = model.getResource(dataSourceURI);
            Property lastUpdate = model.getProperty(DataSourceDao.LASTUPDATE);
            model.removeAll(dataSource, lastUpdate, null);
            model.add(dataSource, lastUpdate, timestampStr, XSDDatatype.XSDdateTime);
        }
        
    }
    
    private class DataSourceRunner implements Runnable {

        private String dataSourceURI;
        private DataSourceTimestamper timestamper;
        private DataTask task = null;
        
        public DataSourceRunner(String dataSourceURI, DataSourceTimestamper timestamper) {
            this.dataSourceURI = dataSourceURI;
            this.timestamper = timestamper;
        }
        
        @Override
        public void run() {
            if (runnings.isRunning(dataSourceURI)) {
                log.warn("Service is already running: " + dataSourceURI);
                return;
            }
            
            runnings.addRunning(dataSourceURI, this);
            task = loadTask(dataSourceURI);
            timestamper.timestampLastUpdate(dataSourceURI);
            
            task.getStatus().setRunning(true);
            task.getStatus().setStatusOk(true);
            task.getStatus().setMessage(null);
            
            long resultNum = 0;
            boolean taskOK = true;
            
            try {
                resultNum = task.run(DataSourceScheduler.this.dataSourceDao);
                
            } catch (Exception e1) {
                log.error("Running of service failed: " + dataSourceURI, e1);
                task.getStatus().setStatusOk(false);
                task.getStatus().setMessage(e1.getMessage());
                taskOK = false;
            }
            
            task.getStatus().setTotalRecords(resultNum);
            dataSourceDao.saveTaskStatus(task.getStatus(), dataSourceURI);
            
            schedule(task);
            
            if (taskOK) {
                for(DataTask nextTask : dataSourceDao.listIngestTasks()) {
                    if(dataSourceURI.equals(nextTask.getScheduleAfterURI())) {
                        log.info("Starting service " + nextTask.getURI() + " scheduled as run after: " + dataSourceURI);
                        startNow(nextTask.getURI());
                    }
                }
            }
            
            runnings.removeRunning(dataSourceURI);
        }
        
        
        protected void stop() {
            if (null != task) {
                task.getStatus().setRunning(false);
            }
        }
        
    }
    
    public boolean isRunning(String dataSourceURI) {
        return runnings.isRunning(dataSourceURI);
    }

    @Override
    public void notifyEvent(String arg0, Object arg1) {
             
    }

    @Override
    public void notifyModelChange(ModelChange modelChange) {
        Model change = RDFServiceUtils.parseModel(
                modelChange.getSerializedModel(), 
                modelChange.getSerializationFormat());
        if(modelChange.getOperation().equals(Operation.ADD)) {
            doAddedModel(change);
        } else if (Operation.REMOVE.equals(modelChange.getOperation())) {
            doRemovedModel(change);
        } else {
            log.error("Unrecognized model change operation " 
                    + modelChange.getOperation());
        }
    }
    
    Set<String> mutedForChangeListening = new HashSet<String>();
    
    private void muteChangeListener(String dataSourceURI) {
        mutedForChangeListening.add(dataSourceURI);
    }
    
    private void unmuteChangeListener(String dataSourceURI) {
        mutedForChangeListening.remove(dataSourceURI);
    }
    
    protected void doAddedModel(Model additions) {
        doChangedDataSources(additions);
    }
    
    protected void doRemovedModel(Model removals) {
        doChangedDataSources(removals);
    }

    private void doChangedDataSources(Model changes) {
        if(log.isDebugEnabled()) {
            log.debug("Heard " + changes.size() + " changes");
        }
        StmtIterator sit = changes.listStatements();
        while(sit.hasNext()) {
            Statement stmt = sit.next();
            if(stmt.getSubject().isURIResource() 
                    && mutedForChangeListening.contains(
                            stmt.getSubject().asResource().getURI())) {
                continue;
            }
            if(DataSourceDao.NEXTUPDATE.equals(stmt.getPredicate().getURI())
                    || DataSourceDao.UPDATEFREQUENCY.equals(stmt.getPredicate().getURI())
                    || DataSourceDao.SCHEDULEAFTER.equals(stmt.getPredicate().getURI())
                    ) {
                if(stmt.getSubject().isURIResource()) {
                    log.debug("Scheduling based on heard change");
                    schedule(dataSourceDao.getDataSource(
                            stmt.getSubject().asResource().getURI()));   
                }                
            }
        }
    }
    
    
    private class Runnings {
        
        private final Map<String, DataSourceRunner> uris = new HashMap<String, DataSourceRunner>();
        
        public synchronized void addRunning(String uri, DataSourceRunner instance) {
            if (uris.containsKey(uri)) {
                throw new IllegalStateException("Cannot start service while it is already running: " + uri);
            }
            
            uris.put(uri, instance);
        }
        
        public synchronized void removeRunning(String uri) {
            uris.remove(uri);
        }
        
        public synchronized boolean isRunning(String uri) {
            return uris.containsKey(uri);
        }
        
        public synchronized void stop(String uri) {
            DataSourceRunner instance = uris.get(uri);
            
            if (null != instance) {
                instance.stop();
            }
        }
        
    }
}
