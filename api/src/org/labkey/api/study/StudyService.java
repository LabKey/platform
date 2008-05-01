package org.labkey.api.study;

import org.labkey.api.data.Container;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: markigra
 * Date: Oct 31, 2007
 * Time: 11:42:47 AM
 */
public class StudyService
{
    private static Service _serviceImpl;

    public interface Service
    {
        /**
         * Returns the dataset id of the requested dataset definition name,
         * or -1 if no such dataset by that name exists
         */
        public int getDatasetId(Container c, String datasetName);

        /**
         * Delete a single dataset row
         */
        public void deleteDatasetRow(Container c, int datasetId, String lsid) throws SQLException;

        /**
         * Update a single dataset row
         * @return the new lsid for the updated row
         * @param c container the dataset is in
         * @param datasetId the dataset defition id
         * @param lsid the lsid of the dataset row
         * @param data the data to be updated
         * @param errors any errors during update will be added to this list
         */
        public String updateDatasetRow(Container c, int datasetId, String lsid, Map<String,Object> data, List<String> errors)
                throws SQLException;

        /**
         * Insert a single dataset row
         * @return the lsid for the new dataset row
         * @param c container the dataset is in
         * @param datasetId the dataset defition id
         * @param data the data to be updated
         * @param errors any errors during update will be added to this list
         */
        public String insertDatasetRow(Container c, int datasetId, Map<String,Object> data, List<String> errors)
                throws SQLException;
    }

    public static void register(Service serviceImpl)
    {
        if (_serviceImpl != null)
            throw new IllegalStateException("Service has already been set.");
        _serviceImpl = serviceImpl;
    }

    public static Service get()
    {
        if (_serviceImpl == null)
            throw new IllegalStateException("Service has not been set.");
        return _serviceImpl;
    }

}
