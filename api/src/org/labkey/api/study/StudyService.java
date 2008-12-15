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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.query.SchemaUpdateService;
import org.labkey.api.query.SchemaUpdateServiceRegistry;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: markigra
 * Date: Oct 31, 2007
 * Time: 11:42:47 AM
 */
public class StudyService
{
    private static Service _serviceImpl;

    public interface Service extends SchemaUpdateService
    {
        /**
         * Returns the dataset id of the requested dataset definition label,
         * or -1 if no such dataset by that name exists
         */
        public int getDatasetId(Container c, String datasetLabel);

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
         * Update a single dataset row
         * @return the new lsid for the updated row
         * @param u the user performing the update
         * @param c container the dataset is in
         * @param datasetId the dataset defition id
         * @param lsid the lsid of the dataset row
         * @param data the data to be updated
         * @param errors any errors during update will be added to this list
         * @param auditComment a comment that will appear in the audit entry for this update
         * @param assignDefaultQCState Whether the default direct data entry QC state should be assigned to rows that
         * do not otherwise specify a QC state.
         */
        public String updateDatasetRow(User u, Container c, int datasetId, String lsid, Map<String,Object> data,
                                       List<String> errors, String auditComment, boolean assignDefaultQCState)
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
         * @return A map of the dataset row columns, null if no record found
         * @throws SQLException Thrown if there's a database error
         */
        public Map<String, Object> getDatasetRow(User u, Container c, int datasetId, String lsid) throws SQLException;

        /**
         * Fetches a set of rows from a dataset given a collection of LSIDs
         * @param u The user
         * @param c The container
         * @param datasetId The dataset Id
         * @param lsids The row LSIDs
         * @return An array of maps of the dataset row columns, null if no records found
         * @throws SQLException Thrown if there's a database error
         */
        public Map<String, Object>[] getDatasetRows(User u, Container c, int datasetId, Collection<String> lsids) throws SQLException;

        public void beginTransaction() throws SQLException;
        public void commitTransaction() throws SQLException;
        public void rollbackTransaction();
        public boolean isTransactionActive();

        /**
         * Applies the administrator-configured default QC filter for a dataset data view.
         * This ensures that users do not see data that should be hidden in the specified view.
         * @param view The data view that should be filtered.
         */
        public void applyDefaultQCStateFilter(DataView view);

        public ActionURL getDatasetURL(Container container, int datasetId);

        @Nullable
        public String getStudyName(Container container);

        /**
         * Returns the set of containers which have ever had data copied from the provided protocol
         */
        public Set<Container> getStudyContainersForAssayProtocol(int protocolId);
    }

    public static void register(Service serviceImpl)
    {
        if (_serviceImpl != null)
            throw new IllegalStateException("Service has already been set.");
        _serviceImpl = serviceImpl;
        SchemaUpdateServiceRegistry.get().register(serviceImpl);
    }

    public static Service get()
    {
        if (_serviceImpl == null)
            throw new IllegalStateException("Service has not been set.");
        return _serviceImpl;
    }

}
