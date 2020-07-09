package org.vivoweb.adminapp.datasource.publish;

import java.io.IOException;

import org.vivoweb.adminapp.datasource.DataTask;
import org.vivoweb.adminapp.datasource.dao.DataSourceDao;

/**
 * Implementation of data publish task.
 * 
 * @author Stefan.Wolff@slub-dresden.de
 */
public class DataPublish extends DataTask {

    public DataPublish(String taskUri) {
        super(taskUri);
        // TODO Auto-generated constructor stub
    }

    @Override
    public long run(DataSourceDao dataSourceDao) throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }

}
