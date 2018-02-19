/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

package org.labkey.study;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyManagementOption;
import org.labkey.api.study.StudyReloadSource;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.UnionTable;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.util.GUID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.study.assay.AssayPublishManager;
import org.labkey.study.assay.query.AssayAuditProvider;
import org.labkey.study.audit.StudyAuditProvider;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.dataset.DatasetAuditProvider;
import org.labkey.study.importer.StudyImportJob;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.SpecimenDomainKind;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.UploadLog;
import org.labkey.study.model.VialDomainKind;
import org.labkey.study.pipeline.SampleMindedTransformTask;
import org.labkey.study.pipeline.StudyReloadSourceJob;
import org.labkey.study.query.AdditiveTypeTable;
import org.labkey.study.query.BaseStudyTable;
import org.labkey.study.query.DatasetTableImpl;
import org.labkey.study.query.DerivativeTypeTable;
import org.labkey.study.query.LocationTable;
import org.labkey.study.query.PrimaryTypeTable;
import org.labkey.study.query.SimpleSpecimenTable;
import org.labkey.study.query.SpecimenDetailTable;
import org.labkey.study.query.SpecimenSummaryTable;
import org.labkey.study.query.SpecimenWrapTable;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.query.VialTable;
import org.labkey.study.security.roles.SpecimenCoordinatorRole;
import org.labkey.study.security.roles.SpecimenRequesterRole;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: jgarms
 */
public class StudyServiceImpl implements StudyService
{
    public static final StudyServiceImpl INSTANCE = new StudyServiceImpl();
    private final Map<String, StudyReloadSource> _reloadSourceMap = new ConcurrentHashMap<>();
    private static List<StudyManagementOption> _managementOptions = new ArrayList<>();

    private StudyServiceImpl() {}

    public static StudyServiceImpl get()
    {
        return (StudyServiceImpl)ServiceRegistry.get(StudyService.class);
    }

    public StudyImpl getStudy(Container container)
    {
        return StudyManager.getInstance().getStudy(container);
    }

    @Override
    public StudyImpl createStudy(Container container, User user, String name, TimepointType timepointType, boolean editableDatasets)
    {
        // Needed for study creation from VISC module. We might want to remove this when we don't need the old study design tool.

        // We no longer check for admin permissions due to Issue 14493, permissions are checked earlier during folder creation,
        // and permissions are not properly set on a new folder until after the study is created, so folder Admins will not be
        // recognized as folder admins at this stage.

            StudyImpl study = new StudyImpl(container, name);

            study.setTimepointType(timepointType);
            study.setSubjectColumnName("ParticipantId");
            study.setSubjectNounSingular("Participant");
            study.setSubjectNounPlural("Participants");
            study.setStartDate(new Date());

            if (editableDatasets)
                study.setSecurityType(SecurityType.BASIC_WRITE);

            return StudyManager.getInstance().createStudy(user, study);
    }

    @Override
    public DatasetDefinition createDataset(Container container, User user, String name, @Nullable Integer datasetId, boolean isDemographic)
    {
        StudyImpl study = getStudy(container);
        if (study == null)
            throw new IllegalStateException("Study required");

        return AssayPublishManager.getInstance().createDataset(user, study, name, datasetId, isDemographic);
    }


    public DatasetDefinition getDataset(Container c, int datasetId)
    {
        Study study = StudyManager.getInstance().getStudy(c);
        if (study != null)
            return StudyManager.getInstance().getDatasetDefinition(study, datasetId);
        return null;
    }

    @Override
    public int getDatasetIdByLabel(Container c, String datasetLabel)
    {
        Study study = StudyManager.getInstance().getStudy(c);
        if (study == null)
            return -1;
        Dataset def = StudyManager.getInstance().getDatasetDefinitionByLabel(study, datasetLabel);

        return def == null ? -1 : def.getDatasetId();
    }

    @Override
    public int getDatasetIdByName(Container c, String datasetName)
    {
        Study study = StudyManager.getInstance().getStudy(c);
        if (study == null)
            return -1;
        Dataset def = StudyManager.getInstance().getDatasetDefinitionByName(study, datasetName);

        return def == null ? -1 : def.getDatasetId();
    }


    @Override
    public Dataset resolveDataset(Container c, String queryName)
    {
        Study study = StudyManager.getInstance().getStudy(c);
        if (study == null)
            return null;
        return StudyManager.getInstance().getDatasetDefinitionByQueryName(study, queryName);
    }


    /**
     * Requests arrive as maps of name->value. The StudyManager expects arrays of maps
     * of property URI -> value. This is a convenience method to do that conversion.
    private List<Map<String,Object>> convertMapToPropertyMapArray(User user, Map<String,Object> origData, DatasetDefinition def)
        throws SQLException
    {
        Map<String,Object> map = new HashMap<String,Object>();

        TableInfo tInfo = def.getTableInfo(user, false);

        Set<String> mvColumnNames = new HashSet<String>();
        for (ColumnInfo col : tInfo.getColumns())
        {
            String name = col.getName();
            if (mvColumnNames.contains(name))
                continue; // We've already processed this field
            Object value = origData.get(name);

            if (col.isMvEnabled())
            {
                String mvColumnName = col.getMvColumnName();
                mvColumnNames.add(mvColumnName);
                String mvIndicator = (String)origData.get(mvColumnName);
                if (mvIndicator != null)
                {
                    value = new MvFieldWrapper(value, mvIndicator);
                }
            }

            if (value == null) // value isn't in the map. Ignore.
                continue;

            map.put(col.getPropertyURI(), value);
        }

        if (origData.containsKey(DatasetTableImpl.QCSTATE_LABEL_COLNAME))
        {
            // DatasetDefinition.importDatasetData() pulls this one out by name instead of PropertyURI
            map.put(DatasetTableImpl.QCSTATE_LABEL_COLNAME, origData.get(DatasetTableImpl.QCSTATE_LABEL_COLNAME));
        }

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        result.add(map);
        return result;
    }
*/

    public void addAssayRecallAuditEvent(Dataset def, int rowCount, Container sourceContainer, User user)
    {
        String assayName = def.getLabel();
        ExpProtocol protocol = def.getAssayProtocol();
        if (protocol != null)
            assayName = protocol.getName();

        AssayAuditProvider.AssayAuditEvent event = new AssayAuditProvider.AssayAuditEvent(sourceContainer.getId(), rowCount + " row(s) were recalled to the assay: " + assayName);

        if (protocol != null)
            event.setProtocol(protocol.getRowId());
        event.setTargetStudy(def.getStudy().getContainer().getId());
        event.setDatasetId(def.getDatasetId());

        AuditLogService.get().addEvent(user, event);
    }

    @Override
    public void addStudyAuditEvent(Container container, User user, String comment)
    {
        AuditTypeEvent event = new AuditTypeEvent(StudyAuditProvider.STUDY_AUDIT_EVENT, container, comment);
        AuditLogService.get().addEvent(user, event);
    }

    /**
     * if oldRecord is null, it's an insert, if newRecord is null, it's delete,
     * if both are set, it's an edit
     */
    public static void addDatasetAuditEvent(User u, Dataset def, @Nullable Map<String, Object> oldRecord, @Nullable Map<String, Object> newRecord)
    {
        String comment;
        if (oldRecord == null)
            comment = "A new dataset record was inserted";
        else if (newRecord == null)
            comment = "A dataset record was deleted";
        else
            comment = "A dataset record was modified";
        addDatasetAuditEvent(u, def, oldRecord, newRecord, comment);
    }

    /**
     * if oldRecord is null, it's an insert, if newRecord is null, it's delete,
     * if both are set, it's an edit
     */
    public static void addDatasetAuditEvent(User u, Dataset def, Map<String, Object> oldRecord, Map<String, Object> newRecord, String auditComment)
    {
        Container c = def.getContainer();
        DatasetAuditProvider.DatasetAuditEvent event = new DatasetAuditProvider.DatasetAuditEvent(c.getId(), auditComment);

        if (c.getProject() != null)
            event.setProjectId(c.getProject().getId());
        event.setDatasetId(def.getDatasetId());
        event.setHasDetails(true);

        String oldRecordString = null;
        String newRecordString = null;
        Object lsid;
        if (oldRecord == null)
        {
            newRecordString = DatasetAuditProvider.encodeForDataMap(newRecord);
            lsid = newRecord.get("lsid");
        }
        else if (newRecord == null)
        {
            oldRecordString = DatasetAuditProvider.encodeForDataMap(oldRecord);
            lsid = oldRecord.get("lsid");
        }
        else
        {
            oldRecordString = DatasetAuditProvider.encodeForDataMap(oldRecord);
            newRecordString = DatasetAuditProvider.encodeForDataMap(newRecord);
            lsid = newRecord.get("lsid");
        }
        event.setLsid(lsid == null ? null : lsid.toString());

        if (oldRecordString != null) event.setOldRecordMap(oldRecordString);
        if (newRecordString != null) event.setNewRecordMap(newRecordString);

        AuditLogService.get().addEvent(u, event);
    }

    public static void addDatasetAuditEvent(User u, Container c, Dataset def, String comment, UploadLog ul /*optional*/)
    {
        DatasetAuditProvider.DatasetAuditEvent event = new DatasetAuditProvider.DatasetAuditEvent(c.getId(), comment);

        if (c.getProject() != null)
            event.setProjectId(c.getProject().getId());
        event.setDatasetId(def.getDatasetId());
        if (ul != null)
        {
            event.setLsid(ul.getFilePath());
        }
        AuditLogService.get().addEvent(u,event);
    }

    public void applyDefaultQCStateFilter(DataView view)
    {
        if (StudyManager.getInstance().showQCStates(view.getRenderContext().getContainer()))
        {
            QCStateSet stateSet = QCStateSet.getDefaultStates(view.getRenderContext().getContainer());
            if (null != stateSet)
            {
                SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
                if (null == filter)
                {
                    filter = new SimpleFilter();
                    view.getRenderContext().setBaseFilter(filter);
                }
                FieldKey qcStateKey = FieldKey.fromParts(DatasetTableImpl.QCSTATE_ID_COLNAME, "rowid");
                Map<FieldKey, ColumnInfo> qcStateColumnMap = QueryService.get().getColumns(view.getDataRegion().getTable(), Collections.singleton(qcStateKey));
                ColumnInfo qcStateColumn = qcStateColumnMap.get(qcStateKey);
                if (qcStateColumn != null)
                    filter.addClause(new SimpleFilter.SQLClause(stateSet.getStateInClause(qcStateColumn.getAlias()), null, qcStateColumn.getFieldKey()));
            }
        }
    }

    public ActionURL getDatasetURL(Container container, int datasetId)
    {
        return new ActionURL(StudyController.DatasetAction.class, container).addParameter("datasetId", datasetId);
    }

    @NotNull
    public Set<Study> findStudy(@NotNull Object studyReference, @Nullable User user)
    {
        if (studyReference == null)
            return Collections.emptySet();
        
        Container c = null;
        if (studyReference instanceof Container)
            c = (Container)studyReference;

        if (studyReference instanceof GUID)
            c = ContainerManager.getForId((GUID)studyReference);

        if (studyReference instanceof String)
        {
            try
            {
                c = (Container)ConvertUtils.convert((String)studyReference, Container.class);
            }
            catch (ConversionException ce)
            {
                // Ignore. Input may have been a Study label.
            }
        }

        if (c != null)
        {
            Study study = null;
            if (user == null || c.hasPermission(user, ReadPermission.class))
                study = getStudy(c);
            return study != null ? Collections.singleton(study) : Collections.emptySet();
        }

        Set<Study> result = new HashSet<>();
        if (studyReference instanceof String)
        {
            String studyRef = (String)studyReference;
            // look for study by label
            Set<? extends StudyImpl> studies = user == null ?
                    StudyManager.getInstance().getAllStudies() :
                    StudyManager.getInstance().getAllStudies(ContainerManager.getRoot(), user, ReadPermission.class);

            for (Study study : studies)
            {
                if (studyRef.equals(study.getLabel()))
                    result.add(study);
            }
        }

        return result;
    }

    public Set<DatasetDefinition> getDatasetsForAssayProtocol(ExpProtocol protocol)
    {
        TableInfo datasetTable = StudySchema.getInstance().getTableInfoDataset();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("protocolid"), protocol.getRowId());
        Set<DatasetDefinition> result = new HashSet<>();
        Collection<Map<String, Object>> rows = new TableSelector(datasetTable, new CsvSet("container,datasetid"), filter, null).getMapCollection();
        for (Map<String, Object> row : rows)
        {
            String containerId = (String)row.get("container");
            int datasetId = ((Number)row.get("datasetid")).intValue();
            Container container = ContainerManager.getForId(containerId);
            result.add(getDataset(container, datasetId));
        }
        return result;
    }

    public Map<DatasetDefinition, String> getDatasetsAndSelectNameForAssayProtocol(ExpProtocol protocol)
    {
        Set<DatasetDefinition> datasets = getDatasetsForAssayProtocol(protocol);
        Map<DatasetDefinition, String> result = new HashMap<>();
        for (DatasetDefinition dataset : datasets)
            result.put(dataset, dataset.getStorageTableInfo().getSelectName());
        return result;
    }

    @Override
    public Set<Dataset> getDatasetsForAssayRuns(Collection<ExpRun> runs, User user)
    {
        // Cache the datasets for a specific protocol (assay design)
        Map<ExpProtocol, Set<DatasetDefinition>> protocolDatasets = new HashMap<>();
        // Remember all of the run RowIds for a given protocol (assay design)
        Map<ExpProtocol, List<Integer>> allProtocolRunIds = new HashMap<>();

        // Go through the runs and figure out what protocols they belong to, and what datasets they could have been copied to
        for (ExpRun run : runs)
        {
            ExpProtocol protocol = run.getProtocol();
            Set<DatasetDefinition> datasets = protocolDatasets.get(protocol);
            if (datasets == null)
            {
                datasets = getDatasetsForAssayProtocol(protocol);
                protocolDatasets.put(protocol, datasets);
            }
            List<Integer> protocolRunIds = allProtocolRunIds.get(protocol);
            if (protocolRunIds == null)
            {
                protocolRunIds = new ArrayList<>();
                allProtocolRunIds.put(protocol, protocolRunIds);
            }
            protocolRunIds.add(run.getRowId());
        }

        // All of the datasets that have rows backed by data in the specified runs
        Set<Dataset> result = new HashSet<>();

        for (Map.Entry<ExpProtocol, Set<DatasetDefinition>> entry : protocolDatasets.entrySet())
        {
            for (DatasetDefinition dataset : entry.getValue())
            {
                // Don't enforce permissions for the current user - we still want to tell them if the data
                // has been copied even if they can't see the dataset.
                UserSchema schema = StudyQuerySchema.createSchema(dataset.getStudy(), user, false);
                TableInfo tableInfo = schema.getTable(dataset.getName());
                AssayProvider provider = AssayService.get().getProvider(entry.getKey());
                if (provider != null)
                {
                    AssayTableMetadata tableMetadata = provider.getTableMetadata(entry.getKey());
                    SimpleFilter filter = new SimpleFilter();
                    filter.addInClause(tableMetadata.getRunRowIdFieldKeyFromResults(), allProtocolRunIds.get(entry.getKey()));
                    if (new TableSelector(tableInfo, filter, null).exists())
                    {
                        result.add(dataset);
                    }
                }
            }
        }

        return result;
    }

    @Override
    public DbSchema getDatasetSchema()
    {
        return StudySchema.getInstance().getDatasetSchema();
    }

    @Override
    public void updateDatasetCategory(User user, @NotNull Dataset dataset, @NotNull ViewCategory category)
    {
        DatasetDefinition dsDef = StudyManager.getInstance().getDatasetDefinitionByEntityId(dataset.getStudy(), dataset.getEntityId());
        if (dsDef != null)
        {
            dsDef = dsDef.createMutable();
            dsDef.setCategoryId(category.getRowId());
            dsDef.save(user);
        }
    }

    public List<SecurableResource> getSecurableResources(Container container, User user)
    {
        Study study = StudyManager.getInstance().getStudy(container);

        if(null == study || !SecurityPolicyManager.getPolicy(container).hasPermission(user, ReadPermission.class))
            return Collections.emptyList();
        else
            return Collections.singletonList(study);
    }

    public Set<Role> getStudyRoles()
    {
        return RoleManager.roleSet(SpecimenCoordinatorRole.class, SpecimenRequesterRole.class);
    }

    public String getSubjectNounSingular(Container container)
    {
        Study study = getStudy(container);
        if (study == null)
            return "Participant";
        return study.getSubjectNounSingular();
    }

    public String getSubjectNounPlural(Container container)
    {
        Study study = getStudy(container);
        if (study == null)
            return "Participants";
        return study.getSubjectNounPlural();
    }

    public String getSubjectColumnName(Container container)
    {
        Study study = getStudy(container);
        if (study == null)
            return "ParticipantId";
        return study.getSubjectColumnName();
    }

    public String getSubjectVisitColumnName(Container container)
    {
        return ColumnInfo.legalNameFromName(getSubjectNounSingular(container) + "Visit");
    }

    public String getSubjectTableName(Container container)
    {
        return getSubjectTableName(getSubjectNounSingular(container));
    }

    public String getSubjectVisitTableName(Container container)
    {
        return getSubjectVisitTableName(getSubjectNounSingular(container));
    }

    private String getSubjectTableName(String subjectNounSingular)
    {
        return ColumnInfo.legalNameFromName(subjectNounSingular);
    }

    private String getSubjectVisitTableName(String subjectNounSingular)
    {
        return getSubjectTableName(subjectNounSingular) + "Visit";
    }

    public String getSubjectCategoryTableName(Container container)
    {
        return getSubjectTableName(container) + "Category";
    }

    public String getSubjectGroupTableName(Container container)
    {
        return getSubjectTableName(container) + "Group";
    }

    public String getSubjectGroupMapTableName(Container container)
    {
        return getSubjectTableName(container) + "GroupMap";
    }

    public boolean isValidSubjectColumnName(Container container, String subjectColumnName)
    {
        if (subjectColumnName == null || subjectColumnName.length() == 0)
            return false;
        // Short-circuit for the common case:
        if ("ParticipantId".equalsIgnoreCase(subjectColumnName))
            return true;
        Set<String> colNames = new CaseInsensitiveHashSet(Arrays.asList(StudyUnionTableInfo.COLUMN_NAMES));
        // We allow any name that isn't found in the default set of columns added to all datasets, except "participantid",
        // which is handled above:
        return !colNames.contains(subjectColumnName);
    }

    public boolean isValidSubjectNounSingular(Container container, String subjectNounSingular)
    {
        if (subjectNounSingular == null || subjectNounSingular.length() == 0)
            return false;

        String subjectTableName = getSubjectTableName(subjectNounSingular);
        String subjectVisitTableName = getSubjectVisitTableName(subjectNounSingular);

        for (String tableName : StudySchema.getInstance().getSchema().getTableNames())
        {
            if (!tableName.equalsIgnoreCase("Participant") && !tableName.equalsIgnoreCase("ParticipantVisit"))
            {
                if (subjectTableName.equalsIgnoreCase(tableName) || subjectVisitTableName.equalsIgnoreCase(tableName))
                    return false;
            }
        }

        return true;
    }

    @Override
    public Dataset.KeyType getDatasetKeyType(Container container, String datasetName)
    {
        Study study = StudyManager.getInstance().getStudy(container);
        if (study != null)
        {
            Dataset dataset = StudyManager.getInstance().getDatasetDefinitionByName(study, datasetName);
            if (dataset != null)
                return dataset.getKeyType();
        }
        if (datasetName.equals(getSubjectGroupMapTableName(container)) || datasetName.equals(getSubjectTableName(container)))
        {
            // Treat these the same as demographics datasets for JOIN purposes - just use ParticipantId
            return Dataset.KeyType.SUBJECT;
        }
        return null;
    }

    public Map<String, String> getAlternateIdMap(Container container)
    {
        Map<String, String> alternateIdMap = new HashMap<>();
        Map<String, StudyManager.ParticipantInfo> pairMap = StudyManager.getInstance().getParticipantInfos(StudyManager.getInstance().getStudy(container), null, false, true);

        for(String ptid : pairMap.keySet())
            alternateIdMap.put(ptid, pairMap.get(ptid).getAlternateId());

        return alternateIdMap;
    }

    @Override
    public Set<? extends Study> getAllStudies(Container root, User user)
    {
        return StudyManager.getInstance().getAllStudies(root, user);
    }

    @Override
    public Set<? extends Study> getAllStudies(Container root)
    {
        return StudyManager.getInstance().getAllStudies(root);
    }

    @Override
    public boolean runStudyImportJob(Container c, User user, ActionURL url, File studyXml, String originalFilename, BindException errors, PipeRoot pipelineRoot, ImportOptions options)
    {
        try
        {
            PipelineService.get().queueJob(new StudyImportJob(c, user, url, studyXml, originalFilename, errors, pipelineRoot, options));
            return true;
        }
        catch (PipelineValidationException e)
        {
            return false;
        }
    }


    public DataIteratorBuilder wrapSampleMindedTransform(User user, DataIteratorBuilder in, DataIteratorContext context, Study study, TableInfo target)
    {
        return SampleMindedTransformTask.wrapSampleMindedTransform(user, in,context,study,target);
    }

    @Override
    public ColumnInfo createAlternateIdColumn(TableInfo ti, ColumnInfo column, Container c)
    {
        // join to the study.participant table to get the participant's alternateId
        SQLFragment sql = new SQLFragment();
        sql.append("(SELECT p.AlternateId FROM ");
        sql.append(StudySchema.getInstance().getTableInfoParticipant(), "p");
        sql.append(" WHERE p.participantid = ");
        sql.append(column.getValueSql(ExprColumn.STR_TABLE_ALIAS));
        sql.append(" AND p.container = ?)");
        sql.add(c);

        return new ExprColumn(ti, column.getName(), sql, column.getJdbcType(), column);
    }


    /**
     * This is in the Service because SpecimenForeignKey is in API.  Otherwise, this would just be something like
     * StudyQuerySchema.createVialUnionTable() and StudyQuerySchema.createSpecimenUnionTable()
     */

    @Override
    public TableInfo getSpecimenTableUnion(QuerySchema qsDefault, Set<Container> containers)
    {
        return getSpecimenTableUnion(qsDefault, containers, new HashMap<Container, SQLFragment>(), false, true);
    }

    @Override
    public TableInfo getSpecimenTableUnion(QuerySchema qsDefault, Set<Container> containers,
                        @NotNull Map<Container, SQLFragment> filterFragments, boolean dontAliasColumns, boolean useParticipantIdName)
    {
        return getOneOfSpecimenTablesUnion(qsDefault, containers, filterFragments, new SpecimenDomainKind(),
                SimpleSpecimenTable.class, dontAliasColumns, useParticipantIdName);
    }

    @Override
    public TableInfo getVialTableUnion(QuerySchema qsDefault, Set<Container> containers)
    {
        return getOneOfSpecimenTablesUnion(qsDefault, containers, new HashMap<Container, SQLFragment>(), new VialDomainKind(),
                VialTable.class, false, true);
    }

    @Override
    public TableInfo getSpecimenDetailTableUnion(QuerySchema qsDefault, Set<Container> containers,
                        @NotNull Map<Container, SQLFragment> filterFragments, boolean dontAliasColumns, boolean useParticipantIdName)
    {
        return getOneOfSpecimenTablesUnion(qsDefault, containers, filterFragments, new SpecimenDomainKind(),
                SpecimenDetailTable.class, dontAliasColumns, useParticipantIdName);
    }

    @Override
    public TableInfo getSpecimenWrapTableUnion(QuerySchema qsDefault, Set<Container> containers,
                        @NotNull Map<Container, SQLFragment> filterFragments, boolean dontAliasColumns, boolean useParticipantIdName)
    {
        return getOneOfSpecimenTablesUnion(qsDefault, containers, filterFragments, new SpecimenDomainKind(),
                SpecimenWrapTable.class, dontAliasColumns, useParticipantIdName);
    }

    @Override
    public TableInfo getSpecimenSummaryTableUnion(QuerySchema qsDefault, Set<Container> containers,
                        @NotNull Map<Container, SQLFragment> filterFragments, boolean dontAliasColumns, boolean useParticipantIdName)
    {
        return getOneOfSpecimenTablesUnion(qsDefault, containers, filterFragments, new SpecimenDomainKind(),
                SpecimenSummaryTable.class, dontAliasColumns, useParticipantIdName);
    }

    private TableInfo getOneOfSpecimenTablesUnion(QuerySchema qsDefault, Set<Container> containers, @NotNull Map<Container, SQLFragment> filterFragments,
                                                  DomainKind kind, Class<? extends BaseStudyTable> tableClass,
                                                  boolean dontAliasColumns, boolean useParticipantIdName)
    {
        if (!(qsDefault instanceof StudyQuerySchema))
            throw new IllegalArgumentException("expected study schema");
        StudyQuerySchema schemaDefault = (StudyQuerySchema)qsDefault;
        User user = schemaDefault.getUser();
        String publicName = null;

        if (null == containers)                     // TODO: I'm reasonably sure this can't happen; for 15.2, verify all paths and @NotNull parameter
            containers = Collections.emptySet();
        Map<Container, BaseStudyTable> tables = new HashMap<>();
        Map<TableInfo, SQLFragment> filterFragmentMap = new HashMap<>();
        try
        {
            for (Container c : containers)
            {
                Study s = StudyManager.getInstance().getStudy(c);
                if (null != s)
                {
                    StudyQuerySchema schema = StudyQuerySchema.createSchema((StudyImpl) s, user, false);
                    BaseStudyTable t = tableClass.getConstructor(StudyQuerySchema.class).newInstance(schema);
                    t.setPublic(false);
                    tables.put(c, t);
                    if (filterFragments.containsKey(c))
                        filterFragmentMap.put(t, filterFragments.get(c));
                    publicName = t.getPublicName();
                }
            }
            if (tables.isEmpty())
            {
                BaseStudyTable t = tableClass.getConstructor(StudyQuerySchema.class).newInstance(schemaDefault);
                t.setPublic(false);
                return t;
            }
        }
        catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e)
        {
            throw new IllegalStateException("Unable to construct class instance.", e);
        }
        return createUnionTable(schemaDefault, tables.values(), tables.keySet(), publicName, kind, filterFragmentMap,
                dontAliasColumns, useParticipantIdName);
    }

    private TableInfo createUnionTable(StudyQuerySchema schemaDefault, Collection<BaseStudyTable> terms, final Set<Container> containers, String tableName, DomainKind kind,
                     @NotNull Map<TableInfo, SQLFragment> filterFragmentMap, boolean dontAliasColumns, boolean useParticipantIdName)
    {
        if (null == terms || terms.isEmpty())
            return null;

        // NOTE: we don't optimize this case, because we always want to return consistent column names
        //if (terms.size() == 1)
        //    return terms.get(0);

        BaseStudyTable table = (BaseStudyTable)terms.toArray()[0];
        SqlDialect dialect = table.getSqlDialect();
        AliasManager aliasManager = new AliasManager(table.getSchema());

        // scan all tables for all columns
        Map<String,ColumnInfo> unionColumns = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<String,ColumnInfo>());
        for (TableInfo t : terms)
        {
            final StudyQuerySchema studyQuerySchema = (StudyQuerySchema)t.getUserSchema();
            final StudyService studyService = StudyService.get();
            assert null != studyQuerySchema && null != studyService;        // All tables must be in StudyQuerySchema
            String subjectColumnName = studyQuerySchema.getSubjectColumnName();
            for (ColumnInfo c : t.getColumns())
            {
                String name = c.getName();
                if (useParticipantIdName && name.equalsIgnoreCase(subjectColumnName))
                    name = "ParticipantId";
                ColumnInfo unionCol = unionColumns.get(name);
                if (null == unionCol)
                {
                    unionCol = makeUnionColumn(c, aliasManager, containers, name);
                    if ("primarytype".equalsIgnoreCase(name))
                    {
                        LookupForeignKey fk = new LookupForeignKey("RowId")
                        {
                            @Override
                            public TableInfo getLookupTableInfo()
                            {
                                return studyService.getTypeTableUnion(PrimaryTypeTable.class, studyQuerySchema, containers, studyQuerySchema.getDontAliasColumns());
                            }
                        };
                        fk.addJoin(FieldKey.fromParts("Container"), "Container", false);
                        unionCol.setFk(fk);
                    }
                    else if ("derivativetype".equalsIgnoreCase(name) || "derivativetype2".equalsIgnoreCase(name))
                    {
                        LookupForeignKey fk = new LookupForeignKey("RowId")
                        {
                            @Override
                            public TableInfo getLookupTableInfo()
                            {
                                return studyService.getTypeTableUnion(DerivativeTypeTable.class, studyQuerySchema, containers, studyQuerySchema.getDontAliasColumns());
                            }
                        };
                        fk.addJoin(FieldKey.fromParts("Container"), "Container", false);
                        unionCol.setFk(fk);
                    }
                    else if ("additivetype".equalsIgnoreCase(name))
                    {
                        LookupForeignKey fk = new LookupForeignKey("RowId")
                        {
                            @Override
                            public TableInfo getLookupTableInfo()
                            {
                                return studyService.getTypeTableUnion(AdditiveTypeTable.class, studyQuerySchema, containers, studyQuerySchema.getDontAliasColumns());
                            }
                        };
                        fk.addJoin(FieldKey.fromParts("Container"), "Container", false);
                        unionCol.setFk(fk);
                    }
                    else if ("processinglocation".equalsIgnoreCase(name))
                    {
                        LookupForeignKey fk = new LookupForeignKey("RowId")
                        {
                            @Override
                            public TableInfo getLookupTableInfo()
                            {
                                return studyService.getTypeTableUnion(LocationTable.class, studyQuerySchema, containers, studyQuerySchema.getDontAliasColumns());
                            }
                        };
                        fk.addJoin(FieldKey.fromParts("Container"), "Container", false);
                        unionCol.setFk(fk);
                    }
                    else if (null != unionCol.getFk() && ("participantid".equalsIgnoreCase(name)) || "visit".equalsIgnoreCase(name) || "collectioncohort".equalsIgnoreCase(name))
                    {
                        final TableInfo lookupTable = unionCol.getFk().getLookupTableInfo();
                        LookupForeignKey fk = new LookupForeignKey()
                        {
                            @Override
                            public TableInfo getLookupTableInfo()
                            {
                                if (lookupTable instanceof FilteredTable && lookupTable.supportsContainerFilter())
                                    ((FilteredTable)lookupTable).setContainerFilter(new ContainerFilter.SimpleContainerFilter(containers));
                                return lookupTable;
                            }
                        };
                        if ("visit".equalsIgnoreCase(name))
                            fk.addJoin(new FieldKey(null, "Container"), "Folder", false);
                        else if ("participantid".equalsIgnoreCase(name))
                            fk.addJoin(new FieldKey(null, "Container"), "Container", false);
                        unionCol.setFk(fk);
                    }

                    unionColumns.put(name,unionCol);
                }
                unionCol.setJdbcType(JdbcType.promote(unionCol.getJdbcType(), c.getJdbcType()));
            }
        }

        SQLFragment sqlf = getUnionSql(terms, filterFragmentMap, dontAliasColumns, dialect, unionColumns);
        return new UnionTable(schemaDefault, tableName, unionColumns.values(), sqlf, table);
    }

    @Override
    public TableInfo getTypeTableUnion(Class<? extends TableInfo> tableClass, QuerySchema qsDefault, Set<Container> containers, boolean dontAliasColumns)
    {
        assert BaseStudyTable.class.isAssignableFrom(tableClass);      // BaseStudyTable could not be in declaration because not visible to interface
        if (!(qsDefault instanceof StudyQuerySchema))
            throw new IllegalArgumentException("expected study schema");
        StudyQuerySchema schemaDefault = (StudyQuerySchema)qsDefault;
        User user = schemaDefault.getUser();
        String publicName = null;

        if (null == containers)                     // TODO: I'm reasonably sure this can't happen; for 15.2, verify all paths and @NotNull parameter
            containers = Collections.emptySet();
        Map<Container, BaseStudyTable> tables = new HashMap<>();
        try
        {
            for (Container c : containers)
            {
                Study s = StudyManager.getInstance().getStudy(c);
                if (null != s)
                {
                    StudyQuerySchema schema = StudyQuerySchema.createSchema((StudyImpl) s, user, false);
                    BaseStudyTable t = (BaseStudyTable) tableClass.getConstructor(StudyQuerySchema.class).newInstance(schema);
                    t.setPublic(false);
                    tables.put(c, t);
                    publicName = t.getPublicName();
                }
            }
            if (tables.isEmpty())
            {
                BaseStudyTable t = (BaseStudyTable)tableClass.getConstructor(StudyQuerySchema.class).newInstance(schemaDefault);
                t.setPublic(false);
                return t;
            }
        }
        catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e)
        {
            throw new IllegalStateException("Unable to construct class instance.");
        }

        return createTypeUnionTable(schemaDefault, tables.values(), tables.keySet(), publicName, Collections.emptyMap(), dontAliasColumns);
    }

    private TableInfo createTypeUnionTable(StudyQuerySchema schemaDefault, Collection<BaseStudyTable> terms, Set<Container> containers, String tableName,
                                   @NotNull Map<TableInfo, SQLFragment> filterFragmentMap, boolean dontAliasColumns)
    {
        if (null == terms || terms.isEmpty())
            return null;

        final StudyService studyService = StudyService.get();
        assert null != studyService;

        // For these tables, all columns must be the same
        BaseStudyTable table = (BaseStudyTable)terms.toArray()[0];
        SqlDialect dialect = table.getSqlDialect();
        AliasManager aliasManager = new AliasManager(table.getSchema());

        Map<String, ColumnInfo> unionColumns = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<String,ColumnInfo>());
        for (ColumnInfo c : table.getColumns())
        {
            unionColumns.put(c.getName(), makeUnionColumn(c, aliasManager, containers, c.getName()));
        }

        SQLFragment sqlf = getUnionSql(terms, filterFragmentMap, dontAliasColumns, dialect, unionColumns);
        return new UnionTable(schemaDefault, tableName, unionColumns.values(), sqlf, table, table.getTitleColumn());
    }

    private ColumnInfo makeUnionColumn(ColumnInfo column, AliasManager aliasManager, Set<Container> containers, String name)
    {
        ColumnInfo unionCol = new AliasedColumn(null, new FieldKey(null,name), column, true)
        {
            @Override
            public SQLFragment getValueSql(String tableAlias)
            {
                return new SQLFragment(tableAlias + "." + getAlias());
            }
        };
        unionCol.copyAttributesFrom(column);
        unionCol.setHidden(column.isHidden());
        unionCol.setAlias(aliasManager.decideAlias(name));

        unionCol.setJdbcType(JdbcType.promote(unionCol.getJdbcType(), column.getJdbcType()));
        if ("container".equalsIgnoreCase(unionCol.getName()) && null != unionCol.getFk())
        {
            TableInfo lookupTable = unionCol.getFk().getLookupTableInfo();
            if (lookupTable instanceof FilteredTable)
                ((FilteredTable)lookupTable).setContainerFilter(new ContainerFilter.SimpleContainerFilter(containers));
        }

        return unionCol;
    }

    private SQLFragment getUnionSql(Collection<BaseStudyTable> terms, Map<TableInfo, SQLFragment> filterFragmentMap, boolean dontAliasColumns, SqlDialect dialect, Map<String, ColumnInfo> unionColumns)
    {
        SQLFragment sqlf = new SQLFragment();
        String union = "";
        int counter = 1;
        for (TableInfo t : terms)
        {
            UserSchema userSchema = t.getUserSchema();
            assert null != userSchema;

            LinkedHashMap<String,SQLFragment> joins = new LinkedHashMap<>();
            String tableAlias = "_" + (counter++);
            sqlf.append(union);
            sqlf.append("SELECT ");
            String comma = "";
            for (ColumnInfo colUnion : unionColumns.values())
            {
                ColumnInfo col = t.getColumn(colUnion.getName());
                sqlf.append(comma);
                if (null == col && colUnion.getName().equalsIgnoreCase("ParticipantId"))
                    col = t.getColumn(((StudyQuerySchema)userSchema).getSubjectColumnName());
                if (null == col)
                {
                    sqlf.append("CAST(NULL AS ").append(dialect.sqlCastTypeNameFromJdbcType(colUnion.getJdbcType())).append(")");
                }
                else if (col.getJdbcType() != colUnion.getJdbcType())
                {
                    sqlf.append("CAST(").append(col.getValueSql(tableAlias));
                    sqlf.append(" AS ").append(dialect.sqlTypeNameFromJdbcType(colUnion.getJdbcType())).append(")");
                    col.declareJoins(tableAlias,joins);
                }
                else
                {
                    sqlf.append(col.getValueSql(tableAlias));
                    col.declareJoins(tableAlias,joins);
                }
                if (!dontAliasColumns || "container".equalsIgnoreCase(colUnion.getAlias()))
                    sqlf.append(" AS ").append(colUnion.getAlias());
                comma = ", ";
            }
            sqlf.append("\nFROM ");
            sqlf.append(t.getFromSQL(tableAlias));
            for (SQLFragment j : joins.values())
                sqlf.append(" ").append(j);
            if (filterFragmentMap.containsKey(t))
                sqlf.append(" WHERE ").append(filterFragmentMap.get(t));

            union = "\nUNION ALL\n";
        }
        return sqlf;
    }

    @Override
    public void registerStudyReloadSource(StudyReloadSource source)
    {
        if (!_reloadSourceMap.containsKey(source.getName()))
            _reloadSourceMap.put(source.getName(), source);
        else
            throw new IllegalStateException("A study reload source implementation with the name: " + source.getName() + " is already registered");
    }

    @Override
    public Collection<StudyReloadSource> getStudyReloadSources(Container container)
    {
        List<StudyReloadSource> sources = new ArrayList<>();

        for (StudyReloadSource source : _reloadSourceMap.values())
        {
            if (source.isEnabled(container))
                sources.add(source);
        }
        return sources;
    }

    @Nullable
    @Override
    public StudyReloadSource getStudyReloadSource(String name)
    {
        return _reloadSourceMap.get(name);
    }

    @Override
    public PipelineJob createReloadSourceJob(Container container, User user, StudyReloadSource reloadSource, @Nullable ActionURL url) throws SQLException, IOException, ValidationException
    {
        PipeRoot root = PipelineService.get().findPipelineRoot(container);
        StudyReloadSourceJob job = new StudyReloadSourceJob(new ViewBackgroundInfo(container, user, url), root, reloadSource.getName());

        return job;
    }

    @Override
    public void hideEmptyDatasets(Container c, User user)
    {
        Study study = getStudy(c);
        if (null == study)
            return;
        List<? extends Dataset> datasets = study.getDatasets();
        if (null == datasets || datasets.isEmpty())
            return;
        for (Dataset dataset : datasets)
        {
            DatasetDefinition d = (DatasetDefinition)dataset;
            TableInfo t = d.getTableInfo(user,true, false);
            if (null == t)
                continue;
            long count = new TableSelector(t).getRowCount();
            if (0 == count)
            {
                if (d.isShowByDefault())
                {
                    d = d.createMutable();
                    d.setShowByDefault(false);
                    StudyManager.getInstance().updateDatasetDefinition(user,d);
                }
            }
            else
            {
                if (!d.isShowByDefault())
                {
                    d = d.createMutable();
                    d.setShowByDefault(true);
                    StudyManager.getInstance().updateDatasetDefinition(user,d);
                }
            }
        }
    }

    @Override
    @NotNull
    public List<StudyManagementOption> getManagementOptions()
    {
        return _managementOptions;
    }

    @Override
    public void registerManagementOption(StudyManagementOption option)
    {
        _managementOptions.add(option);
    }
}
