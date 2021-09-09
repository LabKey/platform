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
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.qc.DataStateManager;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.specimen.model.SpecimenDomainKind;
import org.labkey.api.specimen.model.VialDomainKind;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Location;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyManagementOption;
import org.labkey.api.study.StudyReloadSource;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.UnionTable;
import org.labkey.api.study.Visit;
import org.labkey.api.study.model.ParticipantInfo;
import org.labkey.api.util.GUID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.study.assay.StudyPublishManager;
import org.labkey.study.audit.StudyAuditProvider;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.dataset.DatasetAuditProvider;
import org.labkey.study.importer.StudyImportJob;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.UploadLog;
import org.labkey.study.model.VisitImpl;
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
import org.labkey.study.query.VisitTable;
import org.springframework.validation.BindException;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: jgarms
 */
public class StudyServiceImpl implements StudyService
{
    public static final StudyServiceImpl INSTANCE = new StudyServiceImpl();
    private static final List<StudyManagementOption> _managementOptions = new ArrayList<>();

    private final Map<String, StudyReloadSource> _reloadSourceMap = new ConcurrentHashMap<>();

    private StudyServiceImpl() {}

    @Override
    public Class<? extends Module> getStudyModuleClass()
    {
        return StudyModule.class;
    }

    @Override
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

        return StudyPublishManager.getInstance().createDataset(user, new DatasetDefinition.Builder(name)
                .setStudy(study)
                .setDatasetId(datasetId)
                .setDemographicData(isDemographic));
    }

    @Override
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

    @Override
    public void addStudyAuditEvent(Container container, User user, String comment)
    {
        AuditTypeEvent event = new AuditTypeEvent(StudyAuditProvider.STUDY_AUDIT_EVENT, container, comment);
        AuditLogService.get().addEvent(user, event);
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


    @Override
    public void applyDefaultQCStateFilter(DataView view)
    {
        if (DataStateManager.getInstance().showStates(view.getRenderContext().getContainer()))
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

    @Override
    public ActionURL getDatasetURL(Container container, int datasetId)
    {
        return new ActionURL(StudyController.DatasetAction.class, container).addParameter("datasetId", datasetId);
    }

    @Override
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

    @Override
    public DbSchema getDatasetSchema()
    {
        return StudySchema.getInstance().getDatasetSchema();
    }

    @Override
    public DbSchema getStudySchema()
    {
        return StudySchema.getInstance().getSchema();
    }

    @Override
    public UserSchema getStudyQuerySchema(Study study, User user)
    {
        return StudyQuerySchema.createSchema((StudyImpl)study, user, true);
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

    @Override
    public List<SecurableResource> getSecurableResources(Container container, User user)
    {
        Study study = StudyManager.getInstance().getStudy(container);

        if(null == study || !SecurityPolicyManager.getPolicy(container).hasPermission(user, ReadPermission.class))
            return Collections.emptyList();
        else
            return Collections.singletonList(study);
    }

    @Override
    public String getSubjectNounSingular(Container container)
    {
        Study study = getStudy(container);
        if (study == null)
            return "Participant";
        return study.getSubjectNounSingular();
    }

    @Override
    public String getSubjectNounPlural(Container container)
    {
        Study study = getStudy(container);
        if (study == null)
            return "Participants";
        return study.getSubjectNounPlural();
    }

    @Override
    public String getSubjectColumnName(Container container)
    {
        Study study = getStudy(container);
        if (study == null)
            return "ParticipantId";
        return study.getSubjectColumnName();
    }

    @Override
    public String getSubjectVisitColumnName(Container container)
    {
        return ColumnInfo.legalNameFromName(getSubjectNounSingular(container) + "Visit");
    }

    @Override
    public String getSubjectTableName(Container container)
    {
        return getSubjectTableName(getSubjectNounSingular(container));
    }

    @Override
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

    @Override
    public String getSubjectCategoryTableName(Container container)
    {
        return getSubjectTableName(container) + "Category";
    }

    @Override
    public String getSubjectGroupTableName(Container container)
    {
        return getSubjectTableName(container) + "Group";
    }

    @Override
    public String getSubjectGroupMapTableName(Container container)
    {
        return getSubjectTableName(container) + "GroupMap";
    }

    @Override
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

    @Override
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

    @Override
    public Map<String, String> getAlternateIdMap(Container container)
    {
        Map<String, String> alternateIdMap = new HashMap<>();
        Map<String, ParticipantInfo> pairMap = StudyManager.getInstance().getParticipantInfos(StudyManager.getInstance().getStudy(container), null, false, true);

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
    public boolean runStudyImportJob(Container c, User user, @Nullable ActionURL url, Path studyXml, String originalFilename, BindException errors, PipeRoot pipelineRoot, ImportOptions options)
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
        return getSpecimenTableUnion(qsDefault, containers, new HashMap<>(), false, true);
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
        return getOneOfSpecimenTablesUnion(qsDefault, containers, new HashMap<>(), new VialDomainKind(),
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

       for (Container c : containers)
        {
            Study s = StudyManager.getInstance().getStudy(c);
            if (null != s)
            {
                StudyQuerySchema schema = StudyQuerySchema.createSchema((StudyImpl) s, user, false);
                BaseStudyTable t = constructStudyTable(tableClass, schema);
                t.setPublic(false);
                tables.put(c, t);
                if (filterFragments.containsKey(c))
                    filterFragmentMap.put(t, filterFragments.get(c));
                publicName = t.getPublicName();
            }
        }
        if (tables.isEmpty())
        {
            BaseStudyTable t = constructStudyTable(tableClass, schemaDefault);
            t.setPublic(false);
            return t;
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
        Map<String,BaseColumnInfo> unionColumns = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<>());
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
                var unionCol = unionColumns.get(name);
                if (null == unionCol)
                {
                    unionCol = makeUnionColumn(studyQuerySchema, c, aliasManager, containers, name);
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
                        ((BaseColumnInfo)unionCol).setFk(fk);
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
                    else if ("visit".equalsIgnoreCase(name))
                    {
                        var fk = new LookupForeignKey()
                        {
                            @Override
                            public TableInfo getLookupTableInfo()
                            {
                                var ret = new VisitTable(schemaDefault, new ContainerFilter.SimpleContainerFilter(containers));
                                return ret;
                            }
                        };
                        fk.addJoin(new FieldKey(null, "Container"), "Folder", false);
                        unionCol.setFk(fk);
                    }
                    else if (null != unionCol.getFk() && ("participantid".equalsIgnoreCase(name) || "collectioncohort".equalsIgnoreCase(name)))
                    {
                        TableInfo lookupTable = unionCol.getFk().getLookupTableInfo();
                        UserSchema schema = null==lookupTable ? null : lookupTable.getUserSchema();
                        if (schema instanceof StudyQuerySchema)
                        {
                            final String lookupTableName = lookupTable.getName();
                            LookupForeignKey fk = new LookupForeignKey()
                            {
                                @Override
                                public TableInfo getLookupTableInfo()
                                {
                                    return schemaDefault.getTable(lookupTableName, new ContainerFilter.SimpleContainerFilter(containers));
                                }
                            };
                            if (null != lookupTable.getColumn("Folder"))
                                fk.addJoin(new FieldKey(null, "Container"), "Folder", false);
                            else if (null != lookupTable.getColumn("Container"))
                                fk.addJoin(new FieldKey(null, "Container"), "Container", false);
                            unionCol.setFk(fk);
                        }
                        else
                        {
                            unionCol.clearFk();
                        }
                    }

                    unionColumns.put(name,unionCol);
                }
                unionCol.setJdbcType(JdbcType.promote(unionCol.getJdbcType(), c.getJdbcType()));
            }
        }

        SQLFragment sqlf = getUnionSql(terms, filterFragmentMap, dontAliasColumns, dialect, unionColumns);
        return new UnionTable(schemaDefault, tableName, unionColumns.values(), sqlf, table);
    }


    private static BaseStudyTable constructStudyTable(Class<? extends TableInfo> tableClass, StudyQuerySchema schema)
    {
        try
        {
            return (BaseStudyTable) tableClass.getConstructor(StudyQuerySchema.class, ContainerFilter.class).newInstance(schema, null);
        }
        catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e)
        {
            throw new IllegalStateException("Unable to construct class instance.");
        }
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

        for (Container c : containers)
        {
            Study s = StudyManager.getInstance().getStudy(c);
            if (null != s)
            {
                StudyQuerySchema schema = StudyQuerySchema.createSchema((StudyImpl) s, user, false);
                BaseStudyTable t = constructStudyTable(tableClass, schema);
                t.setPublic(false);
                tables.put(c, t);
                publicName = t.getPublicName();
            }
        }
        if (tables.isEmpty())
        {
            BaseStudyTable t = constructStudyTable(tableClass, schemaDefault);
            t.setPublic(false);
            return t;
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

        Map<String, BaseColumnInfo> unionColumns = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<>());
        for (ColumnInfo c : table.getColumns())
        {
            unionColumns.put(c.getName(), makeUnionColumn(schemaDefault, c, aliasManager, containers, c.getName()));
        }

        SQLFragment sqlf = getUnionSql(terms, filterFragmentMap, dontAliasColumns, dialect, unionColumns);
        return new UnionTable(schemaDefault, tableName, unionColumns.values(), sqlf, table, table.getTitleColumn());
    }

    private BaseColumnInfo makeUnionColumn(StudyQuerySchema schema, ColumnInfo column, AliasManager aliasManager, Set<Container> containers, String name)
    {
        var unionCol = new AliasedColumn(null, new FieldKey(null,name), column, true)
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
        if ("container".equalsIgnoreCase(unionCol.getName()) && unionCol.getFk() instanceof ContainerForeignKey)
        {
            unionCol.setFk(new ContainerForeignKey(schema, new ContainerFilter.SimpleContainerFilter(containers)));
        }

        return unionCol;
    }

    private SQLFragment getUnionSql(Collection<BaseStudyTable> terms, Map<TableInfo, SQLFragment> filterFragmentMap, boolean dontAliasColumns, SqlDialect dialect, Map<String, BaseColumnInfo> unionColumns)
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
                    sqlf.append("CAST(NULL AS ").append(dialect.getSqlCastTypeName(colUnion.getJdbcType())).append(")");
                }
                else if (col.getJdbcType() != colUnion.getJdbcType())
                {
                    sqlf.append("CAST(").append(col.getValueSql(tableAlias));
                    sqlf.append(" AS ").append(dialect.getSqlTypeName(colUnion.getJdbcType())).append(")");
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
    public PipelineJob createReloadSourceJob(Container container, User user, StudyReloadSource reloadSource, @Nullable ActionURL url)
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

    @Override
    public boolean isLocationInUse(Location loc)
    {
        return LocationManager.get().isLocationInUse(loc, StudySchema.getInstance().getTableInfoParticipant(), "EnrollmentSiteId", "CurrentSiteId") ||
            LocationManager.get().isLocationInUse(loc, StudySchema.getInstance().getTableInfoAssaySpecimen(), "LocationId");
    }

    @Override
    public void appendLocationInUseClauses(SQLFragment sql, String locationTableAlias, String exists)
    {
        sql
            .append(exists)
            .append(StudySchema.getInstance().getTableInfoParticipant(), "p")
            .append(" WHERE (")
            .append(locationTableAlias)
            .append(".RowId = p.EnrollmentSiteId OR ")
            .append(locationTableAlias)
            .append(".RowId = p.CurrentSiteId) AND ")
            .append(locationTableAlias)
            .append(".Container = p.Container) OR\n")
            .append(exists)
            .append(StudySchema.getInstance().getTableInfoAssaySpecimen(), "a")
            .append(" WHERE ")
            .append(locationTableAlias)
            .append(".RowId = a.LocationId AND ")
            .append(locationTableAlias)
            .append(".Container = a.Container)");
    }

    private static final List<StudyTabProvider> TAB_PROVIDERS = new CopyOnWriteArrayList<>();

    @Override
    public void registerStudyTabProvider(StudyTabProvider provider)
    {
        TAB_PROVIDERS.add(provider);
    }

    public static List<StudyTabProvider> getStudyTabProviders()
    {
        return TAB_PROVIDERS;
    }

    @Override
    public Collection<? extends Study> getAncillaryStudies(Container sourceStudyContainer)
    {
        return StudyManager.getInstance().getAncillaryStudies(sourceStudyContainer);
    }

    @Override
    public Study getStudyForVisits(@NotNull Study study)
    {
        return StudyManager.getInstance().getStudyForVisits(study);
    }

    @Override
    public boolean showCohorts(Container container, @Nullable User user)
    {
        return StudyManager.getInstance().showCohorts(container, user);
    }

    @Override
    public Date getLastSpecimenLoad(@NotNull Study study)
    {
        return ((StudyImpl)study).getLastSpecimenLoad();
    }

    @Override
    public void setLastSpecimenLoad(@NotNull Study study, User user, Date lastSpecimenLoad)
    {
        StudyImpl studyImpl = ((StudyImpl)study).createMutable();
        studyImpl.setLastSpecimenLoad(new Date());
        StudyManager.getInstance().updateStudy(user, studyImpl);
    }

    @Override
    public List<? extends Visit> getVisits(Study study, SimpleFilter filter, Sort sort)
    {
        SimpleFilter queryFilter = SimpleFilter.createContainerFilter(study.getContainer());
        queryFilter.addAllClauses(filter);
        return new TableSelector(SpecimenSchema.get().getTableInfoVisit(), filter, new Sort("DisplayOrder,SequenceNumMin")).getArrayList(VisitImpl.class);
    }

    @Override
    public void saveLocationSettings(Study study, User user, @Nullable Boolean allowReqLocRepository, @Nullable Boolean allowReqLocClinic, @Nullable Boolean allowReqLocSal, @Nullable Boolean allowReqLocEndpoint)
    {
        StudyImpl studyImpl = (StudyImpl)study;
        StudyImpl mutable = studyImpl.createMutable();
        if (null != allowReqLocRepository)
            mutable.setAllowReqLocRepository(allowReqLocRepository);
        if (null != allowReqLocClinic)
            mutable.setAllowReqLocClinic(allowReqLocClinic);
        if (null != allowReqLocSal)
            mutable.setAllowReqLocSal(allowReqLocSal);
        if (null != allowReqLocEndpoint)
            mutable.setAllowReqLocEndpoint(allowReqLocEndpoint);
        StudyManager.getInstance().updateStudy(user, mutable);
    }

    @Override
    public Collection<String> getParticipantIds(Study study, User user)
    {
        return StudyManager.getInstance().getParticipantIds(study, user);
    }

    @Override
    public boolean participantExists(Study study, String participantId)
    {
        return null != StudyManager.getInstance().getParticipant(study, participantId);
    }
}
