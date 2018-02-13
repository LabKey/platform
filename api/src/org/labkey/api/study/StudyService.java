/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.Role;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: markigra
 * Date: Oct 31, 2007
 * Time: 11:42:47 AM
 */
public interface StudyService
{
    /** LSID namespace prefix for study specimens in the experiment material table */
    String SPECIMEN_NAMESPACE_PREFIX = "StudySpecimen";

    String SPECIMEN_SEARCH_WEBPART = "Specimen Search (Experimental)";
    String SPECIMEN_BROWSE_WEBPART = "Specimen Browse (Experimental)";

    String SPECIMEN_TOOLS_WEBPART_NAME = "Specimen Tools";
    String DATA_TOOLS_WEBPART_NAME = "Study Data Tools";

    String DATASPACE_FOLDERTYPE_NAME = "Dataspace";

    @Nullable
    static StudyService get()
    {
        return ServiceRegistry.get(StudyService.class);
    }

    /**
     * Get the {@link Study} for the {@link Container} if it exists.
     * @param container The container
     * @return The container's study or null
     */
    @Nullable
    Study getStudy(Container container);

    Study createStudy(Container container, User user, String name, TimepointType timepointType, boolean editableDatasets);

    /**
     * Create new Dataset in the Study container.
     */
    Dataset createDataset(Container container, User user, String name, @Nullable Integer datasetId, boolean isDemographic);

    /**
     * Finds a study by either Container id or study label.
     * @param studyReference Container instance, GUID instance, or String representing container id, container path, or study label.
     * @param user Null or a User that must have ReadPermission to the study container.
     *
     * @return A singleton set if a container id or GUID is provided or a set of studies matching the study label.
     * The set will be empty if no Study is found or the user doesn't have permission to the Container.
     */
    @NotNull
    Set<Study> findStudy(@NotNull Object studyReference, @Nullable User user);

    /**
     * Returns the {@link Dataset} of the given id for the {@link Container} or null.
     * @param container The container
     * @param datasetId The dataset id
     * @return The container's dataset or null
     */
    Dataset getDataset(Container container, int datasetId);

    /**
     * Returns the dataset id of the requested dataset definition label,
     * or -1 if no such dataset by that label exists
     */
    int getDatasetIdByLabel(Container c, String datasetLabel);

    /**
     * Returns the dataset id of the requested dataset definition name,
     * or -1 if no such dataset by that label exists
     */
    int getDatasetIdByName(Container c, String datasetName);

    /**
     * Returns the dataset id of the requested dataset definition label or name,
     * or -1 if no such dataset by that label or name exists (i.e. also check name in the case of a dataset label change)
     */
    Dataset resolveDataset(Container c, String queryName);

    /**
     * Applies the administrator-configured default QC filter for a dataset data view.
     * This ensures that users do not see data that should be hidden in the specified view.
     * @param view The data view that should be filtered.
     */
    void applyDefaultQCStateFilter(DataView view);

    ActionURL getDatasetURL(Container container, int datasetId);

    /**
     * Returns the set of datasets which have ever had data copied from the provided protocol
     */
    Set<? extends Dataset> getDatasetsForAssayProtocol(ExpProtocol protocol);

    // Not used... delete? Was used by migrateToNabSpecimen()
    Map<? extends Dataset, String> getDatasetsAndSelectNameForAssayProtocol(ExpProtocol protocol);

    /**
     * Returns the set of datasets which currently contain rows from the provided runs. The user may not have
     * permission to read or modify all of the datasets that are returned.
     */
    Set<? extends Dataset> getDatasetsForAssayRuns(Collection<ExpRun> runs, User user);

    DbSchema getDatasetSchema();

    void updateDatasetCategory(User user, @NotNull Dataset dataset, @NotNull ViewCategory category);

    void addAssayRecallAuditEvent(Dataset def, int rowCount, Container sourceContainer, User user);

    void addStudyAuditEvent(Container container, User user, String comment);

    List<SecurableResource> getSecurableResources(Container container, User user);

    Set<Role> getStudyRoles();

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

    Dataset.KeyType getDatasetKeyType(Container container, String datasetName);

    Map<String, String> getAlternateIdMap(Container container);

    /** @return all studies under the specified parent container that the user has at least read access to */
    Set<? extends Study> getAllStudies(Container root, User user);

    Set<? extends Study> getAllStudies(Container root);

    boolean runStudyImportJob(Container c, User user, ActionURL url, File studyXml, String originalFilename, BindException errors, PipeRoot pipelineRoot, ImportOptions options);

    DataIteratorBuilder wrapSampleMindedTransform(User user, DataIteratorBuilder in, DataIteratorContext context, Study study, TableInfo target);

    ColumnInfo createAlternateIdColumn(TableInfo ti, ColumnInfo column, Container c);

    TableInfo getSpecimenTableUnion(QuerySchema qsDefault, Set<Container> containers, @NotNull Map<Container, SQLFragment> filterFragments, boolean dontAliasColumns, boolean useParticipantIdName);

    TableInfo getSpecimenTableUnion(QuerySchema from, Set<Container> containers);

    TableInfo getVialTableUnion(QuerySchema from, Set<Container> containers);

    TableInfo getSpecimenDetailTableUnion(QuerySchema qsDefault, Set<Container> containers, @NotNull Map<Container, SQLFragment> filterFragments, boolean dontAliasColumns, boolean useParticipantIdName);

    TableInfo getSpecimenWrapTableUnion(QuerySchema qsDefault, Set<Container> containers, @NotNull Map<Container, SQLFragment> filterFragments, boolean dontAliasColumns, boolean useParticipantIdName);

    TableInfo getSpecimenSummaryTableUnion(QuerySchema qsDefault, Set<Container> containers, @NotNull Map<Container, SQLFragment> filterFragments, boolean dontAliasColumns, boolean useParticipantIdName);

    TableInfo getTypeTableUnion(Class<? extends TableInfo> tableClass, QuerySchema qsDefault, Set<Container> containers, boolean dontAliasColumns);

    /**
     * Register an implementation of a reload source. A StudyReloadSource is a potential source for
     * study artifacts to be created and reloaded automatically through the normal study reload framework.
     * The source of the study artifacts could be an external repository or server.
     */
    void registerStudyReloadSource(StudyReloadSource reloadSource);

    Collection<StudyReloadSource> getStudyReloadSources(Container container);

    @Nullable
    StudyReloadSource getStudyReloadSource(String name);

    PipelineJob createReloadSourceJob(Container container, User user, StudyReloadSource transform, @Nullable ActionURL url) throws SQLException, IOException, ValidationException;

    // consider will set hide==true for empty datasets, and clear (inherit) for non-empty datasets
    void hideEmptyDatasets(Container c, User user);

    List<StudyManagementOption> getManagementOptions();

    void registerManagementOption(StudyManagementOption option);
}
