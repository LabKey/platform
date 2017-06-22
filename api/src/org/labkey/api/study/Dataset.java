/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.UnauthorizedException;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A table that contains information about subjects and timepoints. The main data type used in studies.
 * User: kevink
 * Date: May 27, 2009
 */
public interface Dataset<T extends Dataset> extends StudyEntity, StudyCachable<T>
{
    Set<String> getDefaultFieldNames();

    /**
     * Get the Domain for the Dataset. The Domain may be null if the Dataset hasn't yet been provisioned.
     * @return The Domain or null.
     */
    @Nullable
    Domain getDomain();
    boolean isShared();

    String getName();

    String getFileName();

    @Deprecated // no support for subcategories using this method, callers should use getViewCategory instead unless they are using the category label for display purposes.
    String getCategory();

    @Nullable
    ViewCategory getViewCategory();

    String getType();

    String getDescription();

    int getDatasetId();

    @Nullable
    String getTypeURI();

    String getPropertyURI(String column);

    TableInfo getTableInfo(User user) throws UnauthorizedException;

    TableInfo getTableInfo(User user, boolean checkPermission) throws UnauthorizedException;

    TableInfo getTableInfo(User user, boolean checkPermission, boolean multiContainer) throws UnauthorizedException;

    boolean isDemographicData();

    Date getModified();

    /**
     * @return true if this dataset is backed by assay data within LabKey Server. Note that if a dataset happens
     * to contain assay data but isn't linked to an assay provider in the server (ie., when importing a study archive), this method will return false.
     */
    boolean isAssayData();

    ExpProtocol getAssayProtocol();

    Study getStudy();

    Integer getCohortId();

    @Nullable
    Cohort getCohort();

    @Nullable
    String getKeyPropertyName();

    void setKeyPropertyName(String name);

    void save(User user) throws SQLException;

    /**
     * @return whether the user has permission to read rows from this dataset
     */
    public boolean canRead(UserPrincipal user);

    /**
     * @return whether the user has permission to write to the dataset
     */
    public boolean canWrite(UserPrincipal user);

    /**
     * @return whether the user has permission to delete the entire dataset. Use canWrite() to check if user can delete
     * rows from the dataset.
     */
    public boolean canDeleteDefinition(UserPrincipal user);

    /**
     * Does the user have admin permissions for this dataset
     * @param user
     * @return
     */
    public boolean canUpdateDefinition(User user);

    public Set<Class<? extends Permission>> getPermissions(UserPrincipal user);

    KeyType getKeyType();

    /**
     * Returns a string describing the primary keys of this Dataset for display purposes.
     * For example, "Mouse/Visit/ExtraKey"
     * @return Primary key description
     */
    String getKeyTypeDescription();

    /**
     * Compares the extra key for this dataset with the passed in dataset.
     * @param pkDataset dataset to compare
     * @return true if the extra key for this Dataset matches the extra key for the passed in dataset
     */
    public boolean hasMatchingExtraKey(Dataset pkDataset);

    public void delete(User user);

    public void deleteAllRows(User user);

    /**
     * Update a single dataset row
     * @return the new lsid for the updated row
     * @param u user performing the update
     * @param lsid the lsid of the dataset row
     * @param data the data to be updated
     * @param errors any errors during update will be added to this list
     */
    public String updateDatasetRow(User u, String lsid, Map<String,Object> data, List<String> errors);

    /**
     * Fetches a single row from a dataset given an LSID
     * @param u user performing the query
     * @param lsid The row LSID
     * @return A map of the dataset row columns, null if no record found
     */
    public Map<String, Object> getDatasetRow(User u, String lsid);

    /**
     * Fetches a set of rows from a dataset given a collection of LSIDs
     * @param u user performing the query
     * @param lsids The row LSIDs
     * @return An array of maps of the dataset row columns
     */
    @NotNull
    public List<Map<String, Object>> getDatasetRows(User u, Collection<String> lsids);

    /**
     * Deletes the specified rows from the dataset.
     * @param u user performing the delete
     * @param lsids keys of the dataset rows
     */
    public void deleteDatasetRows(User u, Collection<String> lsids);

    // constants for dataset types
    public static final String TYPE_STANDARD = "Standard";
    public static final String TYPE_PLACEHOLDER = "Placeholder";

    enum KeyType
    {
        SUBJECT(1),
        SUBJECT_VISIT(2),
        SUBJECT_VISIT_OTHER(3);
        
        private int _cardinality;
        KeyType(int cardinality)
        {
            _cardinality = cardinality;
        }
        public int getCardinality()
        {
            return _cardinality;
        }
    }

    enum KeyManagementType
    {
        // Don't rename enums without updating the values in the database too
        None(""), RowId("rowid", "true"), GUID("entityid", "guid");

        private String _serializationName;
        private String[] _serializationAliases;

        private KeyManagementType(String serializationName, String... serializationAliases)
        {
            _serializationName = serializationName;
            _serializationAliases = serializationAliases;
        }

        public String getSerializationName()
        {
            return _serializationName;
        }

        public boolean matches(String name)
        {
            if (_serializationName.equalsIgnoreCase(name))
            {
                return true;
            }
            for (String alias : _serializationAliases)
            {
                if (alias.equalsIgnoreCase(name))
                {
                    return true;
                }
            }
            return false;
        }

        public static KeyManagementType findMatch(String name)
        {
            if (name == null)
            {
                return KeyManagementType.None;
            }
            for (KeyManagementType type : KeyManagementType.values())
            {
                if (type.matches(name))
                {
                    return type;
                }
            }
            throw new IllegalArgumentException("No match for '" + name + "'");
        }
    }
}
