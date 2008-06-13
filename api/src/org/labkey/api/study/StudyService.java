/*
 * Copyright (c) 2007-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.study;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

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
        public void deleteDatasetRow(User u, Container c, int datasetId, String lsid) throws SQLException;

        /**
         * Update a single dataset row
         * @return the new lsid for the updated row
         * @param u the user performing the update
         * @param c container the dataset is in
         * @param datasetId the dataset defition id
         * @param lsid the lsid of the dataset row
         * @param data the data to be updated
         * @param errors any errors during update will be added to this list
         */
        public String updateDatasetRow(User u, Container c, int datasetId, String lsid, Map<String,Object> data, List<String> errors)
                throws SQLException;

        /**
         * Insert a single dataset row
         * @return the lsid for the new dataset row
         * @param u the user performing the insert
         * @param c container the dataset is in
         * @param datasetId the dataset defition id
         * @param data the data to be updated
         * @param errors any errors during update will be added to this list
         */
        public String insertDatasetRow(User u, Container c, int datasetId, Map<String,Object> data, List<String> errors)
                throws SQLException;


        /**
         * Fetches a single row from a dataset given an LSID
         * @param u The user
         * @param c The container
         * @param datasetId The dataset Id
         * @param lsid The row LSID
         * @return A map of the dataset row columns
         * @throws SQLException Thrown if there's a database error
         */
        public Map<String, Object> getDatasetRow(User u, Container c, int datasetId, String lsid) throws SQLException;

        public void beginTransaction() throws SQLException;
        public void commitTransaction() throws SQLException;
        public void rollbackTransaction();
        public boolean isTransactionActive();

        /**
         * Returns true if datasets in the container's study are editable, false if not.
         * @param container The container.
         * @return True if editable, false if not.
         */
        public boolean areDatasetsEditable(Container container);
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
