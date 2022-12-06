/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.study.publish;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Nov 6, 2006
 * Time: 11:00:12 AM
 */
public interface StudyPublishService
{
    String PARTICIPANTID_PROPERTY_NAME = "ParticipantID";
    String SEQUENCENUM_PROPERTY_NAME = "SequenceNum";
    String DATE_PROPERTY_NAME = "Date";
    String SOURCE_LSID_PROPERTY_NAME = "SourceLSID";
    String LSID_PROPERTY_NAME = "LSID";
    String ROWID_PROPERTY_NAME = "RowId";
    String TARGET_STUDY_PROPERTY_NAME = "TargetStudy";

    String AUTO_LINK_TARGET_PROPERTY_URI = "terms.labkey.org#AutoCopyTargetContainer";
    String AUTO_LINK_CATEGORY_PROPERTY_URI = "terms.labkey.org#AutoLinkCategory";

    String STUDY_PUBLISH_PROTOCOL_NAME = "Study Publish Protocol";
    String STUDY_PUBLISH_PROTOCOL_LSID = "urn:lsid:labkey.org:Protocol:StudyPublishProtocol";

    // auto link to study target which defaults to the study in the folder the import occurs, using the shared folder
    // which should be safe from collisions since we don't allow assay creation there
    Container AUTO_LINK_TARGET_IMPORT_FOLDER = ContainerManager.getSharedContainer();

    // special fields that are used during the link to study process
    enum LinkToStudyKeys
    {
        SourceId,               // the source ID for the published rows, eg. runId or sampleId
        ObjectId,               // the identifier for the result row
        ParticipantId,
        VisitId,
        Date,
        SpecimenId,             // legacy specimen match columns
        SpecimenMatch,
        SpecimenPtid,
        SpecimenVisit,
        SpecimenDate,
        TargetStudy,            // for assays the run level target study value
        SampleId
    }

    static void setInstance(StudyPublishService serviceImpl)
    {
        ServiceRegistry.get().registerService(StudyPublishService.class, serviceImpl);
    }

    static StudyPublishService get()
    {
        return ServiceRegistry.get().getService(StudyPublishService.class);
    }

    void checkForAlreadyLinkedRows(User user, Pair<Dataset.PublishSource, Integer> publishSource,
                                   List<String> errors, Map<Container, Set<Integer>> rowIdsByTargetContainer);

    ActionURL publishData(User user, Container sourceContainer, Container targetContainer, String sourceName,
                          Pair<Dataset.PublishSource, Integer> publishSource,
                          List<Map<String, Object>> dataMaps, Map<String, PropertyType> propertyTypes, List<String> errors);

    ActionURL publishData(User user, Container sourceContainer, @Nullable Container targetContainer, @Nullable String datasetCategory,
                          String sourceName, Pair<Dataset.PublishSource, Integer> publishSource,
                          List<Map<String, Object>> dataMaps, String keyPropertyName, List<String> errors);

    /**
     * Set of studies the user has permission to.
     */
    Set<Study> getValidPublishTargets(@NotNull User user, @NotNull Class<? extends Permission> permission);

    ActionURL getPublishHistory(Container container, Dataset.PublishSource source, Integer publishSourceId);

    ActionURL getPublishHistory(Container container, Dataset.PublishSource source, Integer publishSourceId, ContainerFilter containerFilter);

    TimepointType getTimepointType(Container container);

    /**
     * Automatically link assay data to a study if the design is set up to do so
     * @return any errors that prevented the link
     */
    @Nullable
    ActionURL autoLinkAssayResults(ExpProtocol protocol, ExpRun run, User user, Container container, List<String> errors);

    /**
     * Automatically link sample type data to a study if the design is set up to do so
     */
    void autoLinkSamples(ExpSampleType sampleType, List<Map<FieldKey, Object>> results, Container container, User user);

    /**
     * Automatically link derived samples, this will potentially pull in fields from the parent sample that
     * are configured in the default view.
     */
    void autoLinkDerivedSamples(ExpSampleType sampleType, List<Integer> keys, Container container, User user) throws ExperimentException;

    /** Checks if the assay and specimen participant/visit/dates don't match based on the specimen id and target study */
    boolean hasMismatchedInfo(List<Integer> dataRowPKs, AssayProtocolSchema schema);

    ExpProtocol ensureStudyPublishProtocol(User user) throws ExperimentException;

    /**
     * Returns the set of datasets which have ever had data linked from the provided protocol
     */
    Set<? extends Dataset> getDatasetsForPublishSource(Integer sourceId, Dataset.PublishSource publishSource);

    /**
     * Returns the set of datasets which currently contain rows from the provided runs. The user may not have
     * permission to read or modify all of the datasets that are returned.
     */
    Set<? extends Dataset> getDatasetsForAssayRuns(Collection<ExpRun> runs, User user);

    String checkForLockedLinks(Dataset def, @Nullable List<Integer> rowIds);

    void addRecallAuditEvent(Container sourceContainer, User user, Dataset def, int rowCount, @Nullable Collection<Pair<String,Integer>> datasetRowLsidAndSourceRowIds);

    /**
     * Adds columns to an assay data table, providing a link to any datasets that have
     * had data linked into them.
     * @return The names of the added columns that should be visible
     */
    Set<String> addLinkedToStudyColumns(AbstractTableInfo table, Dataset.PublishSource publishSource, boolean setVisibleColumns, int rowId, String rowIdName, User user);

    /**
     * For a given sample, helps identify the special columns : subject/visit/date that are needed to publish sample rows to a
     * study target.
     *
     * @param qs an optional query settings if custom view fields should be additionally inspected for publish relevant columns.
     */
    Map<LinkToStudyKeys, FieldKey> getSamplePublishFieldKeys(User user, Container container, ExpSampleType sampleType, @Nullable QuerySettings qs);
}
