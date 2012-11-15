/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.Role;
import org.labkey.api.services.ServiceRegistry;
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

    /** LSID namespace prefix for study specimens in the experiment material table */
    public static final String SPECIMEN_NAMESPACE_PREFIX = "StudySpecimen";

    public static final String SPECIMEN_SEARCH_WEBPART = "Specimen Search (Experimental)";
    public static final String SPECIMEN_BROWSE_WEBPART = "Specimen Browse (Experimental)";

    public static String SPECIMEN_TOOLS_WEBPART_NAME = "Specimen Tools";
    public static String DATA_TOOLS_WEBPART_NAME = "Study Data Tools";


    public interface Service
    {
        /**
         * Get the {@link Study} for the {@link Container} if it exists.
         * @param container The container
         * @return The container's study or null
         */
        @Nullable
        public Study getStudy(Container container);

        public Study createStudy(Container container, User user, String name, TimepointType timepointType) throws Exception;

        /**
         * Finds a study by either Container id or study label.
         * @param studyReference Container instance, GUID instance, or String representing container id, container path, or study label.
         * @param user Null or a User that must have ReadPermission to the study container.
         *
         * @return A singleton set if a container id or GUID is provided or a set of studies matching the study label.
         * The set will be empty if no Study is found or the user doesn't have permission to the Container.
         */
        @NotNull
        public Set<Study> findStudy(@NotNull Object studyReference, @Nullable User user);

        @Nullable
        public String getStudyName(Container container);

        /**
         * Returns the {@link DataSet} of the given id for the {@link Container} or null.
         * @param container The container
         * @param datasetId The dataset id
         * @return The container's dataset or null
         */
        public DataSet getDataSet(Container container, int datasetId);

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
         * @param datasetId the dataset definition id
         * @param lsid the lsid of the dataset row
         * @param data the data to be updated
         * @param errors any errors during update will be added to this list
         */
        public String updateDatasetRow(User u, Container c, int datasetId, String lsid, Map<String,Object> data, List<String> errors)
                throws SQLException;

        /*
         * USE DatasetUpdateService
         *
         * Insert a single dataset row
         * @return the lsid for the new dataset row
         * @param u the user performing the insert
         * @param c container the dataset is in
         * @param datasetId the dataset definition id
         * @param data the data to be updated
         * @param errors any errors during update will be added to this list
        public String insertDatasetRow(User u, Container c, int datasetId, Map<String,Object> data, List<String> errors)
                throws SQLException;
         */


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
         * @return An array of maps of the dataset row columns
         * @throws SQLException Thrown if there's a database error
         */
        @NotNull public Map<String, Object>[] getDatasetRows(User u, Container c, int datasetId, Collection<String> lsids) throws SQLException;

        /**
         * Applies the administrator-configured default QC filter for a dataset data view.
         * This ensures that users do not see data that should be hidden in the specified view.
         * @param view The data view that should be filtered.
         */
        public void applyDefaultQCStateFilter(DataView view);

        public ActionURL getDatasetURL(Container container, int datasetId);

        /**
         * Returns the set of containers which have ever had data copied from the provided protocol
         */
        public List<DataSet> getDatasetsForAssayProtocol(ExpProtocol protocol);

        public List<SecurableResource> getSecurableResources(Container container, User user);

        public Set<Role> getStudyRoles();

        String getSubjectNounSingular(Container container);

        String getSubjectNounPlural(Container container);

        String getSubjectColumnName(Container container);

        String getSubjectTableName(Container container);

        String getSubjectVisitTableName(Container container);

        String getSubjectVisitColumnName(Container container);

        String getSubjectCategoryTableName(Container container);

        String getSubjectGroupTableName(Container container);

        String getSubjectGroupMapTableName(Container container);

        boolean isValidSubjectColumnName(Container container, String subjectColumnName);

        boolean isValidSubjectNounSingular(Container container, String subjectNounSingular);

        DataSet.KeyType getDatasetKeyType(Container container, String datasetName);

        Map<String, String> getAlternateIdMap(Container container);
    
        Study[] getAllStudies(Container root, User user);
    }

    public static void register(Service serviceImpl)
    {
        if (_serviceImpl != null)
            throw new IllegalStateException("Service has already been set.");
        _serviceImpl = serviceImpl;
        ServiceRegistry.get().registerService(StudyService.Service.class, serviceImpl);
    }

    public static Service get()
    {
        if (_serviceImpl == null)
            throw new IllegalStateException("Service has not been set.");
        return _serviceImpl;
    }
}
