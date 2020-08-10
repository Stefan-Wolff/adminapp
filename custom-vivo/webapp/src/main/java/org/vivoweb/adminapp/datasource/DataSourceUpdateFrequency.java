package org.vivoweb.adminapp.datasource;

public enum DataSourceUpdateFrequency {

    DAILY("http://vivoweb.org/ontology/adminapp/updateFrequencyDaily", "daily"), 
    WEEKLY("http://vivoweb.org/ontology/adminapp/updateFrequencyWeekly", "weekly"), 
    MONTHLY("http://vivoweb.org/ontology/adminapp/updateFrequencyMonthly", "monthly");
    
    private String uri;
    private String label;
    
    private DataSourceUpdateFrequency(String uri, String label) {
        this.uri = uri;
        this.label = label;
    }
    
    public String getURI() {
        return this.uri;
    }
    
    public String getLabel() {
        return this.label;
    }
    
    public static DataSourceUpdateFrequency valueByURI(String URI) {
        if(URI == null) {
            return null;
        }
        for (DataSourceUpdateFrequency value : DataSourceUpdateFrequency.values()) {
            if(URI.equals(value.getURI())) {
                return value;
            }
        }
        return null;
    }
    
}