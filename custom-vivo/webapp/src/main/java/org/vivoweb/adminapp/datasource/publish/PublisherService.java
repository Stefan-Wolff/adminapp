package org.vivoweb.adminapp.datasource.publish;

import org.vivoweb.adminapp.datasource.DataSource;
import org.vivoweb.adminapp.datasource.service.DataSourceServiceMultipleInstance;

public class PublisherService extends DataSourceServiceMultipleInstance {

    private static final long serialVersionUID = 1L;

    @Override
    protected DataSource constructDataSource() {
        return new Publisher();
    }
    
}
