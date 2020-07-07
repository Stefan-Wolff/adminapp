package org.vivoweb.adminapp.datasource.merge;

import javax.servlet.http.HttpServletRequest;

import org.vivoweb.adminapp.datasource.DataSource;
import org.vivoweb.adminapp.datasource.service.DataSourceService;

public class MergeDataSourceService extends DataSourceService {

    private static volatile DataSource dataSource = null;
    
    @Override
    protected DataSource getDataSource(HttpServletRequest request) {
        return getDataSourceInstance();
    }
    
    public static synchronized DataSource getDataSourceInstance() {
        if(dataSource == null) {
            dataSource = new MergeDataSource();
        }
        return dataSource;
    }
    
}
