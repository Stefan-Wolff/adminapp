package org.vivoweb.adminapp.datasource;

import java.io.IOException;

import org.vivoweb.adminapp.datasource.dao.DataTaskDao;

/**
 * Abstract class of a data processing task, esp. meant for ingest, merging und publishing tasks.
 * 
 * @author swolff
 */
public abstract class DataTask {
    
    private final String uri;
    private String name;
    private int priority;
    private DataTaskStatus status = new DataTaskStatus();
    private String lastUpdate;
    private String nextUpdate;
    private DataSourceUpdateFrequency updateFrequency;
    private String scheduleAfterURI;
    private SparqlEndpointParams endPointParams;
    

    public DataTask(String taskUri) {
        this.uri = taskUri;
    }


    public abstract long run(DataTaskDao dataSourceDao) throws IOException;
    
    public abstract boolean indexingEnabled();
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public DataTaskStatus getStatus() {
        return status;
    }

    public String getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(String lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public String getNextUpdate() {
        return nextUpdate;
    }

    public void setNextUpdate(String nextUpdate) {
        this.nextUpdate = nextUpdate;
    }

    public DataSourceUpdateFrequency getUpdateFrequency() {
        return updateFrequency;
    }

    public void setUpdateFrequency(DataSourceUpdateFrequency updateFrequency) {
        this.updateFrequency = updateFrequency;
    }

    public String getScheduleAfterURI() {
        return scheduleAfterURI;
    }

    public void setScheduleAfterURI(String scheduleAfterURI) {
        this.scheduleAfterURI = scheduleAfterURI;
    }

    public String getURI() {
        return uri;
    }

    public void setEndpointParams(SparqlEndpointParams params) {
        this.endPointParams = params;
    }
    
    public SparqlEndpointParams getEndpointParams() {
        return endPointParams;
    }
    
}
