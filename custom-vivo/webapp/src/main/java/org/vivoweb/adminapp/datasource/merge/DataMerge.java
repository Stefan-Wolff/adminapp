package org.vivoweb.adminapp.datasource.merge;

import java.io.IOException;

import org.vivoweb.adminapp.datasource.DataTask;
import org.vivoweb.adminapp.datasource.dao.DataSourceDao;

/**
 * Implementation of the data merge task.
 * @author Stefan.Wolff@slub-dresden.de
 *
 */
public class DataMerge extends DataTask {

    public DataMerge(String taskUri) {
        super(taskUri);
        // TODO Auto-generated constructor stub
    }

    @Override
    public long run(DataSourceDao dataSourceDao) throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }

}
