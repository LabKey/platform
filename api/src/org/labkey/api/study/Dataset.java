/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
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
public interface Dataset extends StudyEntity, StudyCachable<Dataset>
{
    // standard string to use in URLs etc.
    String DATASETKEY = "datasetId";

    enum DataSharing
    {
        NONE,
        ALL,
        PTID
    }

    /**
     * Provides information about the published source for a dataset
     */
    enum PublishSource {
        Assay
                {
                    @Override
                    public @Nullable ExpProtocol resolvePublishSource(Integer publishSourceId)
                    {
                        if (publishSourceId != null)
                            return ExperimentService.get().getExpProtocol(publishSourceId);
                        return null;
                    }

                    @Override
                    public String getLabel(Integer publishSourceId)
                    {
                        if (publishSourceId != null)
                        {
                            ExpProtocol protocol = ExperimentService.get().getExpProtocol(publishSourceId);
                            if (protocol != null)
                                return protocol.getName();
                        }
                        return "";
                    }

                    @Override
                    public ActionURL getSourceActionURL(ExpObject source, Container container)
                    {
                        return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(container, (ExpProtocol) source);
                    }

                    @Override
                    public @Nullable ActionButton getSourceButton(Integer publishSourceId, ContainerFilter cf, Container container)
                    {
                        if (publishSourceId != null)
                        {
                            ExpProtocol protocol = resolvePublishSource(publishSourceId);
                            if (protocol != null)
                            {
                                ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(
                                        protocol.getContainer(),
                                        protocol,
                                        cf);
                                return new ActionButton("View Source Assay", url);
                            }
                        }
                        return null;
                    }

                    @Override
                    public boolean hasUsefulDetailsPage(Integer publishSourceId)
                    {
                        if (publishSourceId != null)
                        {
                            ExpProtocol protocol = resolvePublishSource(publishSourceId);
                            if (protocol != null)
                            {
                                AssayProvider provider = AssayService.get().getProvider(protocol);
                                if (provider != null)
                                    return provider.hasUsefulDetailsPage();
                            }
                        }
                        return false;
                    }

                    @Override
                    public @Nullable Container resolveSourceLsidContainer(String sourceLsid, Integer sourceRowId)
                    {
                        // for assays the source lsid is the run
                        ExpRun expRun = ExperimentService.get().getExpRun(sourceLsid);
                        if (expRun != null && expRun.getContainer() != null)
                            return expRun.getContainer();

                        return null;
                    }

                    @Override
                    public String getSourceType()
                    {
                        return "assay";
                    }
                },
        SampleType
                {
                    @Override
                    public @Nullable ExpSampleType resolvePublishSource(Integer publishSourceId)
                    {
                        return SampleTypeService.get().getSampleType(publishSourceId);
                    }

                    @Override
                    public String getLabel(Integer publishSourceId)
                    {
                        ExpSampleType sampleType =  SampleTypeService.get().getSampleType(publishSourceId);
                        if (sampleType != null)
                            return sampleType.getName();
                        return "";
                    }

                    @Override
                    public @Nullable ActionButton getSourceButton(Integer publishSourceId, ContainerFilter cf, Container container)
                    {
                        if (publishSourceId != null)
                        {
                            ExpSampleType sampleType = resolvePublishSource(publishSourceId);
                            if (sampleType != null)
                                return new ActionButton("View Source Sample Type", getSourceActionURL(sampleType, container));
                        }
                        return null;
                    }

                    @Override
                    public boolean hasUsefulDetailsPage(Integer publishSourceId)
                    {
                        return false;
                    }

                    @Override
                    public @Nullable Container resolveSourceLsidContainer(String sourceLsid, Integer sourceRowId)
                    {
                        if (sourceRowId != null)
                        {
                            ExpMaterial expMaterial = ExperimentService.get().getExpMaterial(sourceRowId);
                            if (expMaterial != null)
                                return expMaterial.getContainer();
                        }

                        // for sample types the source lsid is the sample type, fall back on this if the source
                        // rowId (ExpMaterial) is not specified. Generally speaking ExpMaterial is more accurate
                        // since a sample type may be scoped to a different container than the data is inserted into.
                        ExpSampleType sampleType = SampleTypeService.get().getSampleType(sourceLsid);
                        if (sampleType != null)
                            return sampleType.getContainer();

                        return null;
                    }

                    @Override
                    public String getSourceType()
                    {
                        return "sample type";
                    }

                    @Override
                    public ActionURL getSourceActionURL(ExpObject sourceObject, Container container)
                    {
                        return PageFlowUtil.urlProvider(ExperimentUrls.class, true).getShowSampleTypeURL((ExpSampleType) sourceObject, container);
                    }
                };

        public abstract @Nullable ExpObject resolvePublishSource(Integer publishSourceId);
        public abstract String getLabel(Integer publishSourceId);
        public abstract @Nullable ActionButton getSourceButton(Integer publishSourceId, ContainerFilter cf, Container container);
        public abstract boolean hasUsefulDetailsPage(Integer publishSourceId);
        public abstract @Nullable Container resolveSourceLsidContainer(String sourceLsid, @Nullable Integer sourceRowId);
        public abstract String getSourceType();
        public abstract ActionURL getSourceActionURL(ExpObject source, Container container);

        public String getLinkToStudyAuditMessage(ExpObject source, int recordCount)
        {
            return recordCount + " row(s) were linked to a study from the " + getSourceType() + ": " + source.getName();
        }

        public String getRecallFromStudyAuditMessage(String label, int recordCount)
        {
            return recordCount + " row(s) were recalled from a study to the " + getSourceType() + ": " + label;
        }
    }

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

    boolean isDemographicData();

    Date getModified();

    /**
     * @return true if this dataset is backed by published data (assay, sample type etc). Note that if a dataset happens
     * to contain published data but isn't linked to the publish source in the server (ie., when importing a folder archive),
     * this method will return false.
     */
    boolean isPublishedData();

    @Nullable
    PublishSource getPublishSource();

    @Nullable
    ExpObject resolvePublishSource();

    @Nullable
    Integer getPublishSourceId();

    Study getStudy();

    Integer getCohortId();

    @Nullable
    Cohort getCohort();

    @Nullable
    String getKeyPropertyName();

    String getTag();

    String getVisitDatePropertyName();

    DataSharing getDataSharingEnum();

    boolean getUseTimeKeyField();

    void setUseTimeKeyField(boolean useTimeKeyField);

    void setKeyPropertyName(String name);

    void save(User user) throws SQLException;

    boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm);

    /**
     * @return whether the user has permission to read rows from this dataset
     * @deprecated use DatasetTableImpl.hasPermission()
     */
    @Deprecated
    boolean canRead(UserPrincipal user);


    /**
     * @return whether the user has permission to delete the entire dataset. Use canWrite() to check if user can delete
     * rows from the dataset.
     */
    boolean canDeleteDefinition(UserPrincipal user);

    /**
     * Does the user have admin permissions for this dataset
     */
    boolean canUpdateDefinition(User user);

    Set<Class<? extends Permission>> getPermissions(UserPrincipal user);

    KeyType getKeyType();

    void setKeyManagementType(@NotNull KeyManagementType type);

    @NotNull
    KeyManagementType getKeyManagementType();

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
    boolean hasMatchingExtraKey(Dataset pkDataset);

    void delete(User user);

    void deleteAllRows(User user);

    /**
     * Fetches a single row from a dataset given an LSID
     * @param u user performing the query
     * @param lsid The row LSID
     * @return A map of the dataset row columns, null if no record found
     */
    Map<String, Object> getDatasetRow(User u, String lsid);

    /**
     * Fetches a set of rows from a dataset given a collection of LSIDs
     * @param u user performing the query
     * @param lsids The row LSIDs
     * @return An array of maps of the dataset row columns
     */
    @NotNull List<Map<String, Object>> getDatasetRows(User u, Collection<String> lsids);

    /**
     * Deletes the specified rows from the dataset.
     * @param u user performing the delete
     * @param lsids keys of the dataset rows
     */
    void deleteDatasetRows(User u, Collection<String> lsids);

    // constants for dataset types
    String TYPE_STANDARD = "Standard";
    String TYPE_PLACEHOLDER = "Placeholder";

    enum KeyType
    {
        SUBJECT(1),
        SUBJECT_VISIT(2),
        SUBJECT_VISIT_OTHER(3);
        
        private final int _cardinality;

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

        private final String _serializationName;
        private final String[] _serializationAliases;

        KeyManagementType(String serializationName, String... serializationAliases)
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

        public static KeyManagementType getManagementTypeFromProp(PropertyType propertyType)
        {
            if (propertyType == PropertyType.INTEGER || propertyType == PropertyType.DOUBLE)
            {
                // Number fields must be RowIds
                return RowId;
            }
            else if (propertyType == PropertyType.STRING)
            {
                // Strings can be managed as GUIDs
                return GUID;
            }
            else
            {
                throw new IllegalStateException("Unsupported column type for managed keys: " + propertyType);
            }
        }
    }
}
