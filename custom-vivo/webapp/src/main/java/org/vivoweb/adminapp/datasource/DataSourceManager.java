package org.vivoweb.adminapp.datasource;

import java.util.List;


public interface DataSourceManager {

    public abstract List<DataSourceDescription> listDataSources();
 
    public abstract DataSourceDescription getDataSource(String URI);
    
    public abstract DataSourceDescription getDataSourceByGraphURI(String graphURI);
    
    public abstract List<DataSourceDescription> listMergeDataSources();
    
    public abstract List<DataSourceDescription> listPublishDataSources();
    
}
