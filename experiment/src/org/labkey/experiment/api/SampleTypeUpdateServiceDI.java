/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.experiment.api;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.Filter;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.data.measurement.Measurement;
import org.labkey.api.dataiterator.AttachmentDataIterator;
import org.labkey.api.dataiterator.CachingDataIterator;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.DropColumnsDataIterator;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.dataiterator.Pump;
import org.labkey.api.dataiterator.SampleUpdateAliquotedFromDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.NameExpressionOptionService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.inventory.InventoryService;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.SampleStatusService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.MoveEntitiesPermission;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.experiment.ExpDataIterators;
import org.labkey.experiment.SampleTypeAuditProvider;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.labkey.api.data.TableSelector.ALL_COLUMNS;
import static org.labkey.api.dataiterator.DetailedAuditLogDataIterator.AuditConfigs;
import static org.labkey.api.exp.api.ExpRunItem.PARENT_IMPORT_ALIAS_MAP_PROP;
import static org.labkey.api.exp.api.SampleTypeService.ConfigParameters.SkipAliquotRollup;
import static org.labkey.api.exp.api.SampleTypeService.ConfigParameters.SkipMaxSampleCounterFunction;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.AliquotedFromLSID;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.LSID;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.Name;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.RootMaterialRowId;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.RowId;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.SampleState;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.StoredAmount;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.Units;
import static org.labkey.experiment.ExpDataIterators.incrementCounts;

/**
 *
 * This replaces the old row at a time UploadSamplesHelper.uploadMaterials() implementations.
 *
 * originally copied from ExpDataClassDataTableImpl.DataClassDataUpdateService,
 *
 * TODO find remaining shared code and refactor
 *
 */
public class SampleTypeUpdateServiceDI extends DefaultQueryUpdateService
{
    public static final Logger LOG = LogHelper.getLogger(SampleTypeUpdateServiceDI.class, "Sample type update service info");

    public static final String ROOT_RECOMPUTE_ROWID_COL = "RootIdToRecompute";
    public static final String PARENT_RECOMPUTE_NAME_COL = "ParentNameToRecompute";
    public static final String ROOT_RECOMPUTE_ROWID_SET = "RootIdToRecomputeSet";
    public static final String PARENT_RECOMPUTE_NAME_SET = "ParentNameToRecomputeSet";

    public static final Map<String, String> SAMPLE_ALT_IMPORT_NAME_COLS;

    static
    {
        SAMPLE_ALT_IMPORT_NAME_COLS = new CaseInsensitiveHashMap<>();
        SAMPLE_ALT_IMPORT_NAME_COLS.put("SampleId", "Name");
        SAMPLE_ALT_IMPORT_NAME_COLS.put("Sample Id", "Name");
    }

    public enum Options
    {
        SkipDerivation,
        SkipAliquot
    }

    // SampleType may be null for read or delete. We don't allow insert or update without a sample type.
    final @Nullable ExpSampleTypeImpl _sampleType;
    final UserSchema _schema;
    final @Nullable TableInfo _samplesTable;
    // super.getRootTable() is UserSchema table
    // getDbTable() is exp.materials
    // getSamplesTable() is the materialized table with row properties

    public SampleTypeUpdateServiceDI(ExpMaterialTableImpl table, @Nullable ExpSampleTypeImpl sampleType)
    {
        super(table, table.getRealTable(), sampleType == null ? emptyMap() : createMVMapping(sampleType.getDomain()));
        _sampleType = sampleType;
        _schema = table.getUserSchema();
        _samplesTable = sampleType == null ? null : sampleType.getTinfo();
        // we do this in ExpMaterialTableImpl.persistRows() via ExpDataIterators.PersistDataIteratorBuilder
        _enableExistingRecordsDataIterator = false;
    }

    UserSchema getSchema()
    {
        return _schema;
    }

    Container getContainer()
    {
        return _schema.getContainer();
    }

    User getUser()
    {
        return _schema.getUser();
    }

    @Override
    public void configureDataIteratorContext(DataIteratorContext context)
    {
        if (context.getInsertOption().allowUpdate)
            context.putConfigParameter(QueryUpdateService.ConfigParameters.CheckForCrossProjectData, true);
    }

    @Override
    protected DataIteratorBuilder preTriggerDataIterator(DataIteratorBuilder in, DataIteratorContext context)
    {
        assert _sampleType != null : "SampleType required for insert/update, but not required for read/delete";
        return new PrepareDataIteratorBuilder(_sampleType, (ExpMaterialTableImpl) getQueryTable(), in, getContainer(), getUser());
    }

    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
    {
        assert _sampleType != null : "SampleType required for insert/update, but not required for read/delete";
        ArrayList<Map<String, Object>> outputRows = new ArrayList<>();
        Map<Enum, Object> finalConfigParameters = configParameters == null ? new HashMap<>() : configParameters;
        finalConfigParameters.put(ExperimentService.QueryOptions.GetSampleRecomputeCol, true);
        int ret = _importRowsUsingDIB(user, container, rows, outputRows, getDataIteratorContext(errors, InsertOption.INSERT, finalConfigParameters), extraScriptContext);
        if (ret > 0 && !errors.hasErrors())
        {
            onSamplesChanged(outputRows, configParameters, container);
            audit(QueryService.AuditAction.INSERT);
        }
        return ret;
    }

    private Pair<Set<Integer>, Set<String>> getSampleParentsForRecalc(List<Map<String, Object>> outputRows)
    {
        if (outputRows == null || outputRows.isEmpty())
            return null;

        Set<Integer> rootRowIds = new HashSet<>();
        Set<String> parentNames = new HashSet<>();
        if (outputRows.size() == 1 && outputRows.get(0).containsKey(ROOT_RECOMPUTE_ROWID_SET))
        {
            rootRowIds.addAll((Collection<? extends Integer>) outputRows.get(0).get(ROOT_RECOMPUTE_ROWID_SET));
            if (outputRows.get(0).containsKey(PARENT_RECOMPUTE_NAME_SET))
                parentNames.addAll((Collection<? extends String>) outputRows.get(0).get(PARENT_RECOMPUTE_NAME_SET));
        }
        else
        {
            for (Map<String, Object> result : outputRows)
            {
                if (!result.containsKey(ROOT_RECOMPUTE_ROWID_COL))
                    break;
                Object rootIdObj = result.get(ROOT_RECOMPUTE_ROWID_COL);
                Object nameObj = result.get(PARENT_RECOMPUTE_NAME_COL);
                if (rootIdObj != null)
                    rootRowIds.add((Integer) rootIdObj);
                if (nameObj != null)
                    parentNames.add((String) nameObj);
            }
        }

        return new Pair<>(rootRowIds, parentNames);
    }

    @Override
    protected int _pump(DataIteratorBuilder etl, final @Nullable ArrayList<Map<String, Object>> rows, DataIteratorContext context)
    {
        DataIterator it = etl.getDataIterator(context);

        try
        {
            if (null != rows)
            {

                MapDataIterator maps = DataIteratorUtil.wrapMap(it, false);
                Map<String, Integer> columnMap = DataIteratorUtil.createColumnNameMap(it);
                Integer parenRowIdToRecomputeCol = columnMap.get(ROOT_RECOMPUTE_ROWID_COL);
                Integer parentNameToRecomputeCol = columnMap.get(PARENT_RECOMPUTE_NAME_COL);

                boolean hasRollUpColumns = parenRowIdToRecomputeCol != null && parentNameToRecomputeCol != null;
                Set<Integer> rowIdsToRecompute = new HashSet<>();
                Set<String> nameToRecompute = new HashSet<>();

                if (hasRollUpColumns)
                {
                    Map<String, Object> recomputeRes = new CaseInsensitiveHashMap<>();
                    if (context.getConfigParameterBoolean(ExperimentService.QueryOptions.GetSampleRecomputeCol))
                    {
                        recomputeRes.put(ROOT_RECOMPUTE_ROWID_SET, rowIdsToRecompute);
                        recomputeRes.put(PARENT_RECOMPUTE_NAME_SET, nameToRecompute);
                        rows.add(recomputeRes);
                    }
                }

                if (hasRollUpColumns || !context.getConfigParameterBoolean(ExperimentService.QueryOptions.GetSampleRecomputeCol))
                {
                    it = new WrapperDataIterator(maps)
                    {
                        @Override
                        public boolean next() throws BatchValidationException
                        {
                            boolean ret = super.next();
                            if (ret)
                            {
                                if (hasRollUpColumns && context.getConfigParameterBoolean(ExperimentService.QueryOptions.GetSampleRecomputeCol))
                                {
                                    Object rowIdObj = (_delegate).get(parenRowIdToRecomputeCol);
                                    if (rowIdObj != null)
                                        rowIdsToRecompute.add((Integer) rowIdObj);
                                    Object nameObj = (_delegate).get(parentNameToRecomputeCol);
                                    if (nameObj != null)
                                    {
                                        if (nameObj instanceof String)
                                        {
                                            nameToRecompute.add((String) nameObj);
                                        }
                                        else if (nameObj instanceof Number)
                                        {
                                            nameToRecompute.add(nameObj.toString());
                                        }
                                    }
                                }
                                else
                                    rows.add(((MapDataIterator) _delegate).getMap());
                            }
                            return ret;
                        }
                    };
                }
            }

            Pump pump = new Pump(it, context);
            pump.run();

            return pump.getRowCount();
        }
        finally
        {
            DataIteratorUtil.closeQuietly(it);
        }
    }

    @Override
    public DataIteratorBuilder createImportDIB(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
    {
        assert context.isCrossTypeImport() || _sampleType != null : "SampleType required for insert/update, but not required for read/delete";
        if (context.isCrossTypeImport() || context.isCrossFolderImport())
            return new ExpDataIterators.MultiDataTypeCrossProjectDataIteratorBuilder(user, container, data, context.isCrossTypeImport(), context.isCrossFolderImport(), _sampleType, true);

        DataIteratorBuilder dib = new ExpDataIterators.ExpMaterialDataIteratorBuilder(getQueryTable(), data, container, user);

        dib = ((UpdateableTableInfo) getQueryTable()).persistRows(dib, context);
        dib = AttachmentDataIterator.getAttachmentDataIteratorBuilder(getQueryTable(), dib, user, context.getInsertOption().batch ? getAttachmentDirectory() : null, container, getAttachmentParentFactory());
        dib = DetailedAuditLogDataIterator.getDataIteratorBuilder(getQueryTable(), dib, context.getInsertOption(), user, container);

        UserSchema userSchema = getQueryTable().getUserSchema();
        if (userSchema != null)
        {
            ExpSampleType sampleType = ((ExpMaterialTableImpl) getQueryTable()).getSampleType();
            if (InventoryService.get() != null && !_sampleType.isMedia())
                dib = LoggingDataIterator.wrap(InventoryService.get().getPersistStorageItemDataIteratorBuilder(dib, userSchema.getContainer(), userSchema.getUser(), sampleType));

            if (sampleType.getAutoLinkTargetContainer() != null && StudyPublishService.get() != null && !context.getInsertOption().updateOnly/* TODO support link to study on update? */)
                dib = LoggingDataIterator.wrap(new ExpDataIterators.AutoLinkToStudyDataIteratorBuilder(dib, getSchema(), userSchema.getContainer(), userSchema.getUser(), sampleType));
        }
        return dib;
    }

    @Override
    public int loadRows(User user, Container container, DataIteratorBuilder rows, DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext)
    {
        assert context.isCrossTypeImport() || _sampleType != null : "SampleType required for insert/update, but not required for read/delete";

        // Issue 44256: We want to support "Name", "SampleId" and "Sample Id" for easier import
        // Issue 46639: "SampleId" column header not recognized when loading samples from pipeline trigger
        try
        {
            if (rows instanceof DataLoader dataLoader) // junit test uses ListofMapsDataIterator
            {
                if (!context.isCrossTypeImport() && dataLoader.getColumnInfoMap().isEmpty())
                    dataLoader.setKnownColumns(getQueryTable().getColumns());
                ColumnDescriptor[] columnDescriptors = dataLoader.getColumns(SAMPLE_ALT_IMPORT_NAME_COLS);
                for (ColumnDescriptor columnDescriptor : columnDescriptors)
                {
                    if (SAMPLE_ALT_IMPORT_NAME_COLS.containsKey(columnDescriptor.getColumnName()))
                    {
                        columnDescriptor.name = SAMPLE_ALT_IMPORT_NAME_COLS.get(columnDescriptor.getColumnName());
                    }
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        context.putConfigParameter(ExperimentService.QueryOptions.GetSampleRecomputeCol, true);
        ArrayList<Map<String, Object>> outputRows = new ArrayList<>();
        int ret = super.loadRows(user, container, rows, outputRows, context, extraScriptContext);
        if (ret > 0 && !context.getErrors().hasErrors() && _sampleType != null)
        {
            boolean isMediaUpdate = _sampleType.isMedia() && context.getInsertOption().updateOnly;
            onSamplesChanged(!isMediaUpdate ? outputRows : null, context.getConfigParameters(), container);
            audit(context.getInsertOption().auditAction);
        }
        return ret;
    }

    @Override
    public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
    {
        assert _sampleType != null : "SampleType required for insert/update, but not required for read/delete";
        int ret = _importRowsUsingDIB(user, container, rows, null, getDataIteratorContext(errors, InsertOption.MERGE, configParameters), extraScriptContext);
        if (ret > 0 && !errors.hasErrors())
        {
            onSamplesChanged(null, configParameters, container); // mergeRows not really used, skip wiring recalc
            audit(QueryService.AuditAction.MERGE);
        }
        return ret;
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws SQLException
    {
        assert _sampleType != null : "SampleType required for insert/update, but not required for read/delete";
        // insertRows with lineage is pretty good at deadlocking against itself, so use retry loop

        DbScope scope = getSchema().getDbSchema().getScope();
        List<Map<String, Object>> results = scope.executeWithRetry(transaction ->
                super._insertRowsUsingDIB(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext));

        if (results != null && !results.isEmpty() && !errors.hasErrors())
        {
            onSamplesChanged(results, configParameters, container);
            audit(QueryService.AuditAction.INSERT);
        }
        return results;
    }

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        assert _sampleType != null : "SampleType required for insert/update, but not required for read/delete";

        boolean useDib = false;
        if (rows != null && !rows.isEmpty() && oldKeys == null)
            useDib = rows.get(0).containsKey("lsid");

        useDib = useDib && hasUniformKeys(rows);

        List<Map<String, Object>> results;
        if (useDib)
        {
            Map<Enum, Object> finalConfigParameters = configParameters == null ? new HashMap<>() : configParameters;
            finalConfigParameters.put(ExperimentService.QueryOptions.UseLsidForUpdate, true);

            DbScope scope = getSchema().getDbSchema().getScope();
            results = scope.executeWithRetry(transaction ->
                    super._updateRowsUsingDIB(user, container, rows, getDataIteratorContext(errors, InsertOption.UPDATE, finalConfigParameters), extraScriptContext));
        }
        else
        {
            results = super.updateRows(user, container, rows, oldKeys, errors, configParameters, extraScriptContext);

            /* setup mini dataiterator pipeline to process lineage */
            DataIterator di = _toDataIterator("updateRows.lineage", results);
            ExpDataIterators.derive(user, container, di, true, _sampleType, true);
        }

        if (results != null && !results.isEmpty() && !errors.hasErrors())
        {
            onSamplesChanged(!_sampleType.isMedia() ? results : null, configParameters, container);
            audit(QueryService.AuditAction.UPDATE);
        }

        return results;
    }

    @Override
    public Map<String, Object> moveRows(User user, Container container, Container targetContainer, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
            throws BatchValidationException, QueryUpdateServiceException
    {
        Map<String, Integer> allContainerResponse = new HashMap<>();

        AuditBehaviorType auditType = configParameters != null ? (AuditBehaviorType) configParameters.get(AuditConfigs.AuditBehavior) : null;
        String auditUserComment = configParameters != null ? (String) configParameters.get(AuditConfigs.AuditUserComment) : null;

        Map<Container, List<ExpMaterial>> containerMaterials = getMaterialsForMoveRows(container, targetContainer, rows, errors);
        if (!errors.hasErrors() && containerMaterials != null)
        {
            for (Container c : containerMaterials.keySet())
            {
                if (!c.hasPermission(user, MoveEntitiesPermission.class))
                    throw new UnauthorizedException("You do not have permission to move samples out of '" + c.getName() + "'.");
                List<? extends ExpMaterial> materials = containerMaterials.get(c);
                try
                {
                    Map<String, Integer> response = SampleTypeService.get().moveSamples(materials, c, targetContainer, user, auditUserComment, auditType);
                    incrementCounts(allContainerResponse, response);
                }
                catch (ExperimentException e)
                {
                    throw new QueryUpdateServiceException(e);
                }
            }

            SimpleMetricsService.get().increment(ExperimentService.MODULE_NAME, "moveEntities", "samples");
        }
        return new HashMap<>(allContainerResponse);
    }

    private Map<Container, List<ExpMaterial>> getMaterialsForMoveRows(Container container, Container targetContainer, List<Map<String, Object>> rows, BatchValidationException errors)
    {
        Set<Integer> sampleIds = rows.stream().map(row -> (Integer) row.get(RowId.toString())).collect(Collectors.toSet());
        if (sampleIds.isEmpty())
        {
            errors.addRowError(new ValidationException("Sample IDs must be specified for the move operation."));
            return null;
        }

        List<? extends ExpMaterial> materials = ExperimentServiceImpl.get().getExpMaterials(sampleIds);
        if (materials.size() != sampleIds.size())
        {
            errors.addRowError(new ValidationException("Unable to find all samples for the move operation."));
            return null;
        }

        // Filter out materials already in the target container
        materials = materials
                .stream().filter(material -> material.getContainer().getEntityId() != targetContainer.getEntityId()).toList();

        Map<Container, List<ExpMaterial>> containerMaterials = new HashMap<>();
        materials.forEach(material -> {
            if (!containerMaterials.containsKey(material.getContainer()))
                containerMaterials.put(material.getContainer(), new ArrayList<>());
            containerMaterials.get(material.getContainer()).add(material);
        });

        // verify allowed moves based on sample statuses
        List<ExpMaterial> invalidStatusSamples = new ArrayList<>();
        for (ExpMaterial material : materials)
        {
            DataState sampleStatus = material.getSampleState();
            if (sampleStatus == null) continue;

            // prevent move for locked samples
            if (!material.isOperationPermitted(SampleTypeService.SampleOperations.Move))
            {
                invalidStatusSamples.add(material);
            }
            // prevent moving samples if data QC state doesn't exist in target container scope (i.e. home project),
            // only applies when moving from child to parent or child to sibling
            else if (!container.isProject() && sampleStatus.getContainer().equals(container))
            {
                invalidStatusSamples.add(material);
            }
        }
        if (!invalidStatusSamples.isEmpty())
        {
            errors.addRowError(new ValidationException(SampleTypeService.get().getOperationNotPermittedMessage(invalidStatusSamples, SampleTypeService.SampleOperations.Move)));
            return null;
        }

        return containerMaterials;
    }

    @Override
    protected boolean hasImportRowsPermission(User user, Container container, DataIteratorContext context)
    {
        return context.isCrossTypeImport() || super.hasImportRowsPermission(user, container, context);
    }

    @Override
    protected Map<String, Object> _select(Container container, Object[] keys) throws ConversionException
    {
        throw new IllegalStateException("Overridden .getRow()/.getRows() calls .getMaterialMap()");
    }

    public Set<String> getAliquotSpecificFields()
    {
        Domain domain = getDomain();
        Set<String> fields = domain.getProperties().stream()
                .filter(dp -> ExpSchema.DerivationDataScopeType.ChildOnly.name().equalsIgnoreCase(dp.getDerivationDataScope()))
                .map(ImportAliasable::getName)
                .collect(Collectors.toSet());

        return new CaseInsensitiveHashSet(fields);
    }

    public Set<String> getSampleMetaFields()
    {
        Domain domain = getDomain();
        Set<String> fields = domain.getProperties().stream()
                .filter(dp -> !LSID.name().equalsIgnoreCase(dp.getName())
                                && !ExpMaterialTable.Column.Name.name().equalsIgnoreCase(dp.getName())
                                && (StringUtils.isEmpty(dp.getDerivationDataScope())
                                    || ExpSchema.DerivationDataScopeType.ParentOnly.name().equalsIgnoreCase(dp.getDerivationDataScope())))
                .map(ImportAliasable::getName)
                .collect(Collectors.toSet());

        return new CaseInsensitiveHashSet(fields);
    }

    public static boolean isAliquotStatusChangeNeedRecalc(Collection<Integer> availableStatuses, Integer oldStatus, Integer newStatus)
    {
        if (availableStatuses == null || availableStatuses.isEmpty())
            return false;

        if (Objects.equals(oldStatus, newStatus))
            return false;

        if (availableStatuses.contains(oldStatus) && !availableStatuses.contains(newStatus))
            return true;

        if (availableStatuses.contains(newStatus) && !availableStatuses.contains(oldStatus))
            return true;

        return false;
    }

    @Override
    protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
    {
        assert _sampleType != null : "SampleType required for insert/update, but not required for read/delete";
        // LSID was stripped by super.updateRows() and is needed to insert into the dataclass provisioned table
        String lsid = (String) oldRow.get("lsid");
        if (lsid == null)
            throw new ValidationException("lsid required to update row");

        String newName = (String) row.get(Name.name());
        if (row.containsKey(Name.name()) && StringUtils.isEmpty(newName))
            throw new ValidationException("Sample name cannot be blank");

        String oldName = (String) oldRow.get(Name.name());
        boolean hasNameChange = !StringUtils.isEmpty(newName) && !newName.equals(oldName);
        if (hasNameChange && !NameExpressionOptionService.get().getAllowUserSpecificNamesValue(c))
            throw new ValidationException("User-specified sample name not allowed");

        String oldAliquotedFromLSID = (String) oldRow.get(AliquotedFromLSID.name());
        boolean isAliquot = !StringUtils.isEmpty(oldAliquotedFromLSID);

        Integer aliquotRollupRoot = null;

        if (!_sampleType.isMedia() && isAliquot)
        {
            Integer aliquotRoot = (Integer) oldRow.get(RootMaterialRowId.name());

            if (row.containsKey(StoredAmount.name()) || row.containsKey(Units.name()))
            {
                Measurement oldAmount = new Measurement(oldRow.get(StoredAmount.name()), (String) oldRow.get(Units.name()), _sampleType.getMetricUnit());
                Measurement newAmount = new Measurement(row.get(StoredAmount.name()), (String) row.get(Units.name()), _sampleType.getMetricUnit());

                if (!oldAmount.equals(newAmount))
                {
                    if (aliquotRoot != null)
                        aliquotRollupRoot = aliquotRoot;
                }
            }

            if (aliquotRollupRoot == null && row.containsKey(SampleState.name()))
            {
                List<Integer> availableSampleStatuses = new ArrayList<>();
                if (SampleStatusService.get().supportsSampleStatus())
                {
                    for (DataState state: SampleStatusService.get().getAllProjectStates(c))
                    {
                        if (ExpSchema.SampleStateType.Available.name().equals(state.getStateType()))
                            availableSampleStatuses.add(state.getRowId());
                    }
                }

                if (!availableSampleStatuses.isEmpty())
                {
                    Integer oldState = (Integer) oldRow.get(SampleState.name());
                    Integer newState = (Integer) row.get(SampleState.name());
                    if (isAliquotStatusChangeNeedRecalc(availableSampleStatuses, oldState, newState))
                        aliquotRollupRoot = aliquotRoot;
                }
            }
        }

        Set<String> aliquotFields = getAliquotSpecificFields();
        Set<String> sampleMetaFields = getSampleMetaFields();

        // Replace attachment columns with filename and keep AttachmentFiles
        Map<String, Object> rowCopy = new CaseInsensitiveHashMap<>();

        // remove aliquotedFrom from row, or error out
        rowCopy.putAll(row);
        String newAliquotedFromLSID = (String) rowCopy.get(AliquotedFromLSID.name());
        if (!StringUtils.isEmpty(newAliquotedFromLSID) && !newAliquotedFromLSID.equals(oldAliquotedFromLSID))
            throw new ValidationException("Updating aliquotedFrom is not supported");
        rowCopy.remove(AliquotedFromLSID.name());
        rowCopy.remove(RootMaterialRowId.name());
        rowCopy.remove(ExpMaterial.ALIQUOTED_FROM_INPUT);

        // We need to allow updating from one locked status to another locked status, but without other changes
        // and updating from either locked or unlocked to something else while also updating other metadata
        DataState oldStatus = SampleStatusService.get().getStateForRowId(getContainer(), (Integer) oldRow.get(SampleState.name()));
        boolean oldAllowsOp = SampleStatusService.get().isOperationPermitted(oldStatus, SampleTypeService.SampleOperations.EditMetadata);
        DataState newStatus = SampleStatusService.get().getStateForRowId(getContainer(), (Integer) rowCopy.get(SampleState.name()));
        boolean newAllowsOp = SampleStatusService.get().isOperationPermitted(newStatus, SampleTypeService.SampleOperations.EditMetadata);

        Map<String, Object> ret = new CaseInsensitiveHashMap<>(super._update(user, c, rowCopy, oldRow, keys));

        if (aliquotRollupRoot != null)
            ret.put(ROOT_RECOMPUTE_ROWID_COL, aliquotRollupRoot);

        Map<String, Object> validRowCopy = new CaseInsensitiveHashMap<>();
        boolean hasNonStatusChange = false;
        for (String updateField : rowCopy.keySet())
        {
            Object updateValue = rowCopy.get(updateField);
            boolean isAliquotField = aliquotFields.contains(updateField);
            boolean isSampleMetaField = sampleMetaFields.contains(updateField);

            if (isAliquot && isSampleMetaField)
            {
                Object oldMetaValue = oldRow.get(updateField);
                if (!Objects.equals(oldMetaValue, updateValue))
                    LOG.warn("Sample metadata update has been skipped for an aliquot");
            }
            else if (!isAliquot && isAliquotField)
            {
                LOG.warn("Aliquot-specific field update has been skipped for a sample.");
            }
            else
            {
                hasNonStatusChange = hasNonStatusChange || !SampleTypeServiceImpl.statusUpdateColumns.contains(updateField.toLowerCase());
                validRowCopy.put(updateField, updateValue);
            }
        }
        // had a locked status before and either not updating the status or updating to a new locked status
        if (hasNonStatusChange && !oldAllowsOp && (newStatus == null || !newAllowsOp))
        {
            throw new ValidationException(String.format("Updating sample data when status is %s is not allowed.", oldStatus.getLabel()));
        }

        keys = new Object[]{lsid};
        TableInfo t = _sampleType.getTinfo();
        // Sample type uses FILE_LINK not FILE_ATTACHMENT, use convertTypes() to handle posted files
        convertTypes(user, c, validRowCopy, t, "sampletype");
        if (t.getColumnNameSet().stream().anyMatch(validRowCopy::containsKey))
        {
            ret.putAll(Table.update(user, t, validRowCopy, t.getColumn("lsid"), keys, null, Level.DEBUG));
        }

        ExpMaterialImpl sample = null;
        if (hasNameChange)
        {
            sample = ExperimentServiceImpl.get().getExpMaterial(lsid);
            if (sample != null)
                ExperimentService.get().addObjectLegacyName(sample.getObjectId(), ExperimentServiceImpl.getNamespacePrefix(ExpMaterial.class), oldName, user);
        }

        // update comment
        if (row.containsKey("flag") || row.containsKey("comment"))
        {
            Object o = row.containsKey("flag") ? row.get("flag") : row.get("comment");
            String flag = Objects.toString(o, null);

            if (sample == null)
                sample = ExperimentServiceImpl.get().getExpMaterial(lsid);
            if (sample != null)
                sample.setComment(user, flag);
        }

        // update aliases
        if (row.containsKey("Alias"))
            AliasInsertHelper.handleInsertUpdate(getContainer(), user, lsid, ExperimentService.get().getTinfoMaterialAliasMap(), row.get("Alias"));

        // search index
        SearchService ss = SearchService.get();
        if (ss != null)
        {
            if (sample == null)
                sample = ExperimentServiceImpl.get().getExpMaterial(lsid);
            if (sample != null)
                sample.index(null);
        }

        ret.put("lsid", lsid);
        ret.put(AliquotedFromLSID.name(), oldRow.get(AliquotedFromLSID.name()));
        return ret;
    }

    @Override
    protected int truncateRows(User user, Container container)
    {
        if (_sampleType == null)
            return 0;

        int ret = SampleTypeServiceImpl.get().truncateSampleType(_sampleType, user, container);
        if (ret > 0)
        {
            // NOTE: Not necessary to call onSamplesChanged -- already called by truncateSampleSet
            audit(QueryService.AuditAction.TRUNCATE);
        }
        return ret;
    }

    @Override
    protected Domain getDomain()
    {
        return _sampleType == null ? null : _sampleType.getDomain();
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap)
    {
        List<Integer> id = new LinkedList<>();
        Integer rowId = getMaterialRowId(oldRowMap);
        id.add(rowId);
        ExperimentServiceImpl.get().deleteMaterialByRowIds(user, container, id, true, _sampleType, false, false);
        return oldRowMap;
    }

    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
            throws QueryUpdateServiceException, SQLException, InvalidKeyException, BatchValidationException
    {
        List<Map<String, Object>> result = new ArrayList<>(keys.size());

        // Check for trigger scripts
        if (getQueryTable().hasTriggers(container))
        {
            result = super.deleteRows(user, container, keys, configParameters, extraScriptContext);
        }
        else
        {
            List<Integer> ids = new LinkedList<>();

            for (Map<String, Object> k : keys)
            {
                Integer rowId = getMaterialRowId(k);
                // Issue 40621
                // adding input fields is expensive, skip input fields for delete since deleted samples are not surfaced on Timeline UI
                Map<String, Object> map = getMaterialMap(rowId, getMaterialLsid(k), user, container, false);
                if (map == null)
                    throw new QueryUpdateServiceException("No Sample Type Material found for RowID or LSID");

                if (rowId == null)
                    rowId = getMaterialRowId(map);
                if (rowId == null)
                    throw new QueryUpdateServiceException("RowID is required to delete a Sample Type Material");

                Integer sampleStateId = (Integer) map.get(SampleState.name());
                if (!SampleStatusService.get().isOperationPermitted(getContainer(), sampleStateId, SampleTypeService.SampleOperations.Delete))
                {
                    DataState dataState = SampleStatusService.get().getStateForRowId(container, sampleStateId);
                    throw new QueryUpdateServiceException(String.format("Sample with RowID %d cannot be deleted due to its current status (%s)", rowId, dataState));
                }

                ids.add(rowId);
                result.add(map);
            }
            // TODO check if this handle attachments???
            ExperimentServiceImpl.get().deleteMaterialByRowIds(user, container, ids, true, _sampleType, false, false);
        }

        if (!result.isEmpty())
        {
            // NOTE: Not necessary to call onSamplesChanged -- already called by deleteMaterialByRowIds
            audit(QueryService.AuditAction.DELETE);
            addAuditEvent(user, container,  QueryService.AuditAction.DELETE, configParameters, result, null);
        }
        return result;
    }

    private @Nullable String getMaterialStringValue(Map<String, Object> row, String columnName)
    {
        Object o = row.get(columnName);
        if (o instanceof String s)
            return s;

        return null;
    }

    private @Nullable String getMaterialLsid(Map<String, Object> row)
    {
        return getMaterialStringValue(row, LSID.name());
    }

    private @Nullable String getMaterialName(Map<String, Object> row)
    {
        return getMaterialStringValue(row, Name.name());
    }

    IntegerConverter _converter = new IntegerConverter();

    private @Nullable Integer getMaterialIntegerValue(Map<String, Object> row, String columnName)
    {
        if (row != null)
        {
            Object o = row.get(columnName);
            if (o != null)
                return _converter.convert(Integer.class, o);
        }

        return null;
    }

    private @Nullable Integer getMaterialSourceId(Map<String, Object> row)
    {
        return getMaterialIntegerValue(row, ExpMaterialTable.Column.MaterialSourceId.name());
    }

    private @Nullable Integer getMaterialRowId(Map<String, Object> row)
    {
        return getMaterialIntegerValue(row, ExpMaterialTable.Column.RowId.name());
    }

    private Map<String, Object> getMaterialMap(Integer rowId, String lsid, User user, Container container, boolean addInputs)
            throws QueryUpdateServiceException
    {
        Filter filter;
        if (rowId != null)
            filter = new SimpleFilter(FieldKey.fromParts(ExpMaterialTable.Column.RowId), rowId);
        else if (lsid != null)
            filter = new SimpleFilter(FieldKey.fromParts(LSID), lsid);
        else
            throw new QueryUpdateServiceException("Either RowId or LSID is required to get Sample Type Material.");

        Map<String, Object> sampleRow = new TableSelector(getQueryTable(), filter, null).getMap();
        if (null == sampleRow || !addInputs)
            return sampleRow;

        ExperimentService experimentService = ExperimentService.get();
        ExpMaterial seed = rowId != null ? experimentService.getExpMaterial(rowId) : experimentService.getExpMaterial(lsid);
        if (null == seed)
            return sampleRow;
        Set<ExpMaterial> parentSamples = experimentService.getParentMaterials(container, user, seed);
        if (!parentSamples.isEmpty())
            addParentFields(sampleRow, parentSamples, ExpMaterial.MATERIAL_INPUT_PARENT + "/", user);
        Set<ExpData> parentDatas = experimentService.getParentDatas(container, user, seed);
        if (!parentDatas.isEmpty())
            addParentFields(sampleRow, parentDatas, ExpData.DATA_INPUT_PARENT + "/", user);

        return sampleRow;
    }

    private <T extends ExpRunItem> void addParentFields(Map<String, Object> sampleRow, Set<T> parents, String parentPrefix, User user)
    {
        Map<String, List<String>> parentByType = new HashMap<>();
        for (ExpRunItem parent : parents)
        {
            String type = "";
            if (parent instanceof ExpData dataParent)
            {
                ExpDataClass dataClass = dataParent.getDataClass(user);
                if (dataClass == null)
                    continue;
                type = dataClass.getName();
            }
            else if (parent instanceof ExpMaterial materialParent)
            {
                ExpSampleType sampleType = materialParent.getSampleType();
                if (sampleType == null)
                    continue;
                type = sampleType.getName();
            }

            parentByType.computeIfAbsent(type, k -> new ArrayList<>());
            String parentName = parent.getName();
            if (parentName.contains(","))
                parentName = "\"" + parentName + "\"";
            parentByType.get(type).add(parentName);
        }

        for (String type : parentByType.keySet())
        {
            String key = parentPrefix + type;
            String value = String.join(",", parentByType.get(type));
            sampleRow.put(key, value);
        }
    }

    @Override
    public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys)
            throws QueryUpdateServiceException
    {
        return getRows(user, container, keys, false /*skip addInputs for insertRows*/);
    }

    @Override
    public boolean hasExistingRowsInOtherContainers(Container container, Map<Integer, Map<String, Object>> keys)
    {
        Integer sampleTypeId = null;
        Set<String> sampleNames = new HashSet<>();
        for (Map.Entry<Integer, Map<String, Object>> keyMap : keys.entrySet())
        {
            String name = getMaterialName(keyMap.getValue());

            if (name != null)
                sampleNames.add(name);

            if (sampleTypeId == null)
                sampleTypeId = getMaterialSourceId(keyMap.getValue());
        }

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("MaterialSourceId"), sampleTypeId);
        filter.addCondition(FieldKey.fromParts("Name"), sampleNames, CompareType.IN);
        filter.addCondition(FieldKey.fromParts("Container"), container, CompareType.NEQ);

        return new TableSelector(ExperimentService.get().getTinfoMaterial(), filter, null).exists();
    }

    @Override
    public Map<Integer, Map<String, Object>> getExistingRows(User user, Container container, Map<Integer, Map<String, Object>> keys, boolean verifyNoCrossFolderData, boolean verifyExisting, @Nullable Set<String> columns)
            throws InvalidKeyException, QueryUpdateServiceException
    {
        return getMaterialMapsWithInput(keys, user, container, verifyNoCrossFolderData, verifyExisting, columns);
    }

    private record ExistingRowSelect(TableInfo tableInfo, Set<String> columns, boolean includeParent, boolean addContainerFilter) {}

    private @NotNull ExistingRowSelect getExistingRowSelect(@Nullable Set<String> dataColumns)
    {
        if (!(getQueryTable() instanceof UpdateableTableInfo updatable) || dataColumns == null)
            return new ExistingRowSelect(getQueryTable(), ALL_COLUMNS, true, false);

        CaseInsensitiveHashMap<String> remap = updatable.remapSchemaColumns();
        if (null == remap)
            remap = CaseInsensitiveHashMap.of();

        Set<String> includedColumns = new CaseInsensitiveHashSet("name", "lsid", "rowid");
        for (ColumnInfo column : getQueryTable().getColumns())
        {
            if (dataColumns.contains(column.getColumnName()))
                includedColumns.add(column.getColumnName());
            else if (dataColumns.contains(remap.get(column.getColumnName())))
                includedColumns.add(remap.get(column.getColumnName()));
        }

        boolean isAllFromMaterialTable = new CaseInsensitiveHashSet(Stream.of(ExpMaterialTable.Column.values())
                .map(Enum::name)
                .collect(Collectors.toSet()))
                .containsAll(includedColumns);
        TableInfo selectTable = isAllFromMaterialTable ? ExperimentService.get().getTinfoMaterial() : getQueryTable();

        boolean hasParentInput = false;
        if (_sampleType != null)
        {
            try
            {
                Map<String, String> importAliases = _sampleType.getImportAliasMap();
                for (String col : dataColumns)
                {
                    if (!hasParentInput && ExperimentService.isInputOutputColumn(col) || equalsIgnoreCase("parent",col) || importAliases.containsKey(col))
                    {
                        hasParentInput = true;
                        break;
                    }

                }
            }
            catch (IOException ignored)
            {
            }
        }

        return new ExistingRowSelect(selectTable, includedColumns, hasParentInput, isAllFromMaterialTable /* Unlike samples table, Materials table doesn't have container filter applied*/);
    }

    private Map<Integer, Map<String, Object>> getMaterialMapsWithInput(Map<Integer, Map<String, Object>> keys, User user, Container container, boolean checkCrossFolderData, boolean verifyExisting, @Nullable Set<String> columns)
            throws QueryUpdateServiceException, InvalidKeyException
    {
        ExistingRowSelect existingRowSelect = getExistingRowSelect(columns);
        TableInfo queryTableInfo = existingRowSelect.tableInfo;
        Set<String> selectColumns = existingRowSelect.columns;
        boolean filterToCurrentContainer = existingRowSelect.addContainerFilter;

        Map<Integer, Map<String, Object>> sampleRows = new LinkedHashMap<>();
        Map<Integer, String> rowNumLsid = new HashMap<>();

        Map<Integer, Integer> rowIdRowNumMap = new LinkedHashMap<>();
        Map<String, Integer> lsidRowNumMap = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<>());
        Map<String, Integer> nameRowNumMap = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<>());
        Integer sampleTypeId = null;
        for (Map.Entry<Integer, Map<String, Object>> keyMap : keys.entrySet())
        {
            Integer rowNum = keyMap.getKey();

            Integer rowId = getMaterialRowId(keyMap.getValue());
            String lsid = getMaterialLsid(keyMap.getValue());
            String name = getMaterialName(keyMap.getValue());
            Integer materialSourceId = getMaterialSourceId(keyMap.getValue());

            if (rowId != null)
                rowIdRowNumMap.put(rowId, rowNum);
            else if (lsid != null)
            {
                lsidRowNumMap.put(lsid, rowNum);
                rowNumLsid.put(rowNum, lsid);
            }
            else if (name != null && materialSourceId != null)
            {
                sampleTypeId = materialSourceId;
                nameRowNumMap.put(name, rowNum);
            }
            else
                throw new QueryUpdateServiceException("Either RowId or LSID is required to get Sample Type Material.");
        }

        if (!rowIdRowNumMap.isEmpty())
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(ExpMaterialTable.Column.RowId), rowIdRowNumMap.keySet(), CompareType.IN);
            if (filterToCurrentContainer)
                filter.addCondition(FieldKey.fromParts("Container"), container);
            Map<String, Object>[] rows = new TableSelector(queryTableInfo, selectColumns, filter, null).getMapArray();
            for (Map<String, Object> row : rows)
            {
                Integer rowId = (Integer) row.get("rowid");
                Integer rowNum = rowIdRowNumMap.get(rowId);
                String sampleLsid = (String) row.get("lsid");

                rowNumLsid.put(rowNum, sampleLsid);
                sampleRows.put(rowNum, row);
            }
        }

        Set<String> allKeys = new HashSet<>();
        boolean useLsid = false;

        if (!lsidRowNumMap.isEmpty())
        {
            useLsid = true;
            allKeys.addAll(lsidRowNumMap.keySet());

            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(LSID), lsidRowNumMap.keySet(), CompareType.IN);
            if (filterToCurrentContainer)
                filter.addCondition(FieldKey.fromParts("Container"), container);
            Map<String, Object>[] rows = new TableSelector(queryTableInfo, selectColumns, filter, null).getMapArray();
            for (Map<String, Object> row : rows)
            {
                String sampleLsid = (String) row.get("lsid");
                Integer rowNum = lsidRowNumMap.get(sampleLsid);
                sampleRows.put(rowNum, row);

                allKeys.remove(sampleLsid);
            }
        }

        if (!nameRowNumMap.isEmpty())
        {
            allKeys.addAll(nameRowNumMap.keySet());
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("MaterialSourceId"), sampleTypeId);
            filter.addCondition(FieldKey.fromParts("Name"), nameRowNumMap.keySet(), CompareType.IN);
            if (filterToCurrentContainer)
                filter.addCondition(FieldKey.fromParts("Container"), container);

            Map<String, Object>[] rows = new TableSelector(queryTableInfo, selectColumns, filter, null).getMapArray();
            for (Map<String, Object> row : rows)
            {
                String name = (String) row.get("name");
                Integer rowNum = nameRowNumMap.get(name);
                String sampleLsid = (String) row.get("lsid");
                sampleRows.put(rowNum, row);
                rowNumLsid.put(rowNum, sampleLsid);

                allKeys.remove(name);
            }
        }

        if (checkCrossFolderData && !allKeys.isEmpty())
        {
            ContainerFilter allCf = ContainerFilter.current(container); // use a relaxed CF to find existing data from cross containers
            if (container.isProductProjectsEnabled())
                allCf = new ContainerFilter.AllInProjectPlusShared(container, user);

            SimpleFilter existingDataFilter = new SimpleFilter(FieldKey.fromParts("MaterialSourceId"), sampleTypeId);
            existingDataFilter.addCondition(FieldKey.fromParts("Container"), allCf.getIds(), CompareType.IN);

            existingDataFilter.addCondition(FieldKey.fromParts(useLsid ? "LSID" : "Name"), allKeys, CompareType.IN);
            Map<String, Object>[] cfRows = new TableSelector(ExperimentService.get().getTinfoMaterial(), existingDataFilter, null).getMapArray();
            for (Map<String, Object> row : cfRows)
            {
                String dataContainer = (String) row.get("container");
                if (!dataContainer.equals(container.getId()))
                    throw new InvalidKeyException("Sample does not belong to " + container.getName() + " container: " + (String) row.get("name") + ".");
            }

        }

        if (verifyExisting && !allKeys.isEmpty())
            throw new InvalidKeyException("Sample does not exist: " + allKeys.iterator().next() + ".");

        // if contains domain fields, check for aliquot specific fields
        if (!queryTableInfo.getName().equalsIgnoreCase("material"))
        {
            Set<String> parentOnlyFields = getSampleMetaFields();
            for (Map.Entry<Integer, Map<String, Object>> rowNumSampleRow : sampleRows.entrySet())
            {
                Map<String, Object> sampleRow = rowNumSampleRow.getValue();

                if (!StringUtils.isEmpty((String) sampleRow.get(AliquotedFromLSID.name())))
                {
                    for (String parentOnlyField : parentOnlyFields)
                        sampleRow.put(parentOnlyField, null); // ignore inherited fields for aliquots
                }
            }
        }

        if (!existingRowSelect.includeParent)
            return sampleRows;

        List<ExpMaterialImpl> materials = ExperimentServiceImpl.get().getExpMaterialsByLsid(rowNumLsid.values());

        Map<String, Pair<Set<ExpMaterial>, Set<ExpData>>> parents = ExperimentServiceImpl.get().getParentMaterialAndDataMap(container, user, new HashSet<>(materials));

        for (Map.Entry<Integer, Map<String, Object>> rowNumSampleRow : sampleRows.entrySet())
        {
            Integer rowNum = rowNumSampleRow.getKey();
            String lsidKey = rowNumLsid.get(rowNum);
            Map<String, Object> sampleRow = rowNumSampleRow.getValue();

            if (!parents.containsKey(lsidKey))
                continue;

            Pair<Set<ExpMaterial>, Set<ExpData>> sampleParents = parents.get(lsidKey);

            if (!sampleParents.first.isEmpty())
                addParentFields(sampleRow, sampleParents.first, ExpMaterial.MATERIAL_INPUT_PARENT + "/", user);
            if (!sampleParents.second.isEmpty())
                addParentFields(sampleRow, sampleParents.second, ExpData.DATA_INPUT_PARENT + "/", user);
        }

        return sampleRows;
    }

    public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys, boolean addInputs)
            throws QueryUpdateServiceException
    {
        List<Map<String, Object>> result = new ArrayList<>(keys.size());
        for (Map<String, Object> k : keys)
        {
            Map<String, Object> materialMap = getMaterialMap(getMaterialRowId(k), getMaterialLsid(k), user, container, addInputs);
            if (materialMap != null)
                result.add(materialMap);
        }
        return result;
    }

    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws QueryUpdateServiceException
    {
        return getMaterialMap(getMaterialRowId(keys), getMaterialLsid(keys), user, container, true);
    }

    private void onSamplesChanged(List<Map<String, Object>> results, Map<Enum, Object> params, Container container)
    {
        var tx = getSchema().getDbSchema().getScope().getCurrentTransaction();
        Pair<Set<Integer>, Set<String>> parentKeys = getSampleParentsForRecalc(results);
        boolean useBackgroundRecalc = false;
        if (parentKeys != null)
        {
            int parentSize = parentKeys.first.size() + parentKeys.second.size();
            useBackgroundRecalc = parentSize > 20;
        }

        boolean skipRecalc = false;
        if (params != null && params.containsKey(SkipAliquotRollup)) // used by ExperimentStressTest only to avoid deadlock in test
            skipRecalc = Boolean.TRUE == params.get(SkipAliquotRollup);

        if (!useBackgroundRecalc && parentKeys != null && !skipRecalc)
            handleRecalc(parentKeys.first, parentKeys.second, false, container);

        if (tx != null)
        {
            if (!tx.isAborted())
            {
                boolean finalUseBackgroundRecalc = useBackgroundRecalc;
                boolean finalSkipRecalc = skipRecalc;
                tx.addCommitTask(() -> {
                    fireSamplesChanged();
                    if (finalUseBackgroundRecalc && !finalSkipRecalc)
                        handleRecalc(parentKeys.first, parentKeys.second, true, container);
                }, DbScope.CommitTaskOption.POSTCOMMIT);
            }
            else
                LOG.info("Skipping onSamplesChanged callback; transaction aborted");
        }
        else
        {
            fireSamplesChanged();
        }
    }

    private void handleRecalc(Set<Integer> rootRowIds, Set<String> parentNames, boolean useBackgroundThread, Container container)
    {
        // The caller of this handleRecalc() should generally be calling refreshSampleTypeMaterializedView().
        // However, we also need to call it after this async process runs
        // Also, it's harmless to call it twice, so we can call it in the synchronous case as well.

        Runnable runRecalc = () -> {
            try
            {
                if (_sampleType != null)
                {
                    SampleTypeService.get().recomputeSampleTypeRollup(_sampleType, rootRowIds, parentNames, container);
                    SampleTypeServiceImpl.get().refreshSampleTypeMaterializedView(_sampleType, false);
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        };

        if (useBackgroundThread)
        {
            JobRunner.getDefault().execute(runRecalc);
        }
        else
        {
            runRecalc.run();
        }
    }

    private void fireSamplesChanged()
    {
        if (_sampleType != null)
            _sampleType.onSamplesChanged(getUser(), null);
    }

    void audit(QueryService.AuditAction auditAction)
    {
        assert _sampleType != null || auditAction == QueryService.AuditAction.DELETE : "SampleType required for insert/update, but not required for read/delete";
        SampleTypeAuditProvider.SampleTypeAuditEvent event = new SampleTypeAuditProvider.SampleTypeAuditEvent(
                getContainer().getId(), "Samples " + auditAction.getVerbPastTense() + " in: " + (_sampleType == null ? "<Materials>" : _sampleType.getName()));
        var tx = getSchema().getDbSchema().getScope().getCurrentTransaction();
        if (tx != null)
            event.setTransactionId(tx.getAuditId());
        if (_sampleType != null)
        {
            event.setSourceLsid(_sampleType.getLSID());
            event.setSampleSetName(_sampleType.getName());
        }
        event.setInsertUpdateChoice(auditAction.toString().toLowerCase());
        AuditLogService.get().addEvent(getUser(), event);
    }

    // TODO: validate/compare functionality of CoerceDataIterator and loadRows()
    private static class PrepareDataIteratorBuilder implements DataIteratorBuilder
    {
        private static final int BATCH_SIZE = 100;

        final ExpSampleTypeImpl sampleType;
        final DataIteratorBuilder builder;
        final ExpMaterialTableImpl materialTable;
        final Container container;
        final User user;

        public PrepareDataIteratorBuilder(@NotNull ExpSampleTypeImpl sampleType, ExpMaterialTableImpl materialTable, DataIteratorBuilder in, Container container, User user)
        {
            this.sampleType = sampleType;
            this.builder = in;
            this.materialTable = materialTable;
            this.container = container;
            this.user = user;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator source = LoggingDataIterator.wrap(builder.getDataIterator(context));

            // drop columns
            ColumnInfo containerColumn = this.materialTable.getColumn(this.materialTable.getContainerFieldKey());
            String containerFieldLabel = containerColumn.getLabel();
            var drop = new CaseInsensitiveHashSet();
            for (int i = 1; i <= source.getColumnCount(); i++)
            {
                String name = source.getColumnInfo(i).getName();
                boolean isContainerField = name.equalsIgnoreCase(containerFieldLabel);
                if (!isContainerField)
                    isContainerField = name.equalsIgnoreCase("Container") || name.equalsIgnoreCase("Folder");
                if (isReservedHeader(name) || isContainerField)
                {
                    // Allow some fields on exp.materials to be loaded by the TabLoader.
                    // Skip over other reserved names 'RowId', 'Run', etc.
                    if (isCommentHeader(name))
                        continue;
                    if (isNameHeader(name))
                        continue;
                    if (isDescriptionHeader(name))
                        continue;
                    if (ExperimentService.isInputOutputColumn(name))
                        continue;
                    if (isAliasHeader(name))
                        continue;
                    if (isSampleStateHeader(name))
                        continue;
                    if (isMaterialExpDateHeader(name))
                        continue;
                    if (isStoredAmountHeader(name))
                        continue;
                    if (isUnitsHeader(name))
                        continue;
                    if (isContainerField && context.isCrossFolderImport() && !context.getInsertOption().updateOnly)
                        continue;
                    drop.add(name);
                }
            }

            if (context.getConfigParameterBoolean(ExperimentService.QueryOptions.UseLsidForUpdate))
                drop.remove("lsid");
            if (!drop.isEmpty())
                source = new DropColumnsDataIterator(source, drop);

            Map<String, Integer> columnNameMap = DataIteratorUtil.createColumnNameMap(source);
            if (context.getInsertOption() == InsertOption.UPDATE)
            {
                SimpleTranslator addAliquotedFrom = new SimpleTranslator(source, context);

                if (!columnNameMap.containsKey(AliquotedFromLSID.name()))
                    addAliquotedFrom.addNullColumn(AliquotedFromLSID.name(), JdbcType.VARCHAR);
                if (!columnNameMap.containsKey(RootMaterialRowId.name()))
                    addAliquotedFrom.addNullColumn(RootMaterialRowId.name(), JdbcType.INTEGER);
                addAliquotedFrom.addColumn(new BaseColumnInfo("cpasType", JdbcType.VARCHAR), new SimpleTranslator.ConstantColumn(sampleType.getLSID()));
                addAliquotedFrom.addColumn(new BaseColumnInfo("materialSourceId", JdbcType.INTEGER), new SimpleTranslator.ConstantColumn(sampleType.getRowId()));
                addAliquotedFrom.addNullColumn(ROOT_RECOMPUTE_ROWID_COL, JdbcType.INTEGER);
                addAliquotedFrom.addNullColumn(PARENT_RECOMPUTE_NAME_COL, JdbcType.VARCHAR);
                addAliquotedFrom.selectAll();

                var addAliquotedFromDI = new SampleUpdateAliquotedFromDataIterator(new CachingDataIterator(addAliquotedFrom), materialTable, sampleType.getRowId(), columnNameMap.containsKey("lsid"));

                SimpleTranslator c = new _SamplesCoerceDataIterator(addAliquotedFromDI, context, sampleType, materialTable);
                return LoggingDataIterator.wrap(c);
            }

            // CoerceDataIterator to handle the lookup/alternatekeys functionality of loadRows(),
            // TODO: check if this covers all the functionality, in particular how is alternateKeyCandidates used?
            DataIterator c = LoggingDataIterator.wrap(new _SamplesCoerceDataIterator(source, context, sampleType, materialTable));

            // auto gen a sequence number for genId - reserve BATCH_SIZE numbers at a time so we don't select the next sequence value for every row
            SimpleTranslator addGenId = new SimpleTranslator(c, context);
            addGenId.setDebugName("add genId");
            Set<String> idColNames = Sets.newCaseInsensitiveHashSet("genId");
            materialTable.getColumns().stream().filter(ColumnInfo::isUniqueIdField).forEach(columnInfo -> idColNames.add(columnInfo.getName()));
            addGenId.selectAll(idColNames);

            ColumnInfo genIdCol = new BaseColumnInfo(FieldKey.fromParts("genId"), JdbcType.INTEGER);
            final int batchSize = context.getInsertOption().batch ? BATCH_SIZE : 1;
            addGenId.addSequenceColumn(genIdCol, sampleType.getContainer(), ExpSampleTypeImpl.SEQUENCE_PREFIX, sampleType.getRowId(), batchSize, sampleType.getMinGenId());
            addGenId.addUniqueIdDbSequenceColumns(ContainerManager.getRoot(), materialTable);
            // only add when AliquotedFrom column is not null
            if (columnNameMap.containsKey(ExpMaterial.ALIQUOTED_FROM_INPUT))
            {
                addGenId.addNullColumn(ROOT_RECOMPUTE_ROWID_COL, JdbcType.INTEGER);
                addGenId.addNullColumn(PARENT_RECOMPUTE_NAME_COL, JdbcType.VARCHAR);
            }
            DataIterator dataIterator = LoggingDataIterator.wrap(addGenId);

            // Table Counters
            DataIteratorBuilder dib = ExpDataIterators.CounterDataIteratorBuilder.create(dataIterator, sampleType.getContainer(), materialTable, ExpSampleType.SEQUENCE_PREFIX, sampleType.getRowId());
            dataIterator = dib.getDataIterator(context);

            // sampleset.createSampleNames() + generate lsid
            // TODO: does not handle insertIgnore
            DataIterator names = new _GenerateNamesDataIterator(sampleType, container, user, DataIteratorUtil.wrapMap(dataIterator, false), context, batchSize)
                    .setAllowUserSpecifiedNames(NameExpressionOptionService.get().getAllowUserSpecificNamesValue(container))
                    .addExtraPropsFn(() -> {
                        if (container != null)
                            return Map.of(NameExpressionOptionService.FOLDER_PREFIX_TOKEN, StringUtils.trimToEmpty(NameExpressionOptionService.get().getExpressionPrefix(container)));
                        else
                            return Collections.emptyMap();
                    });

            return LoggingDataIterator.wrap(names);
        }

        private static boolean isReservedHeader(String name)
        {
            if (isNameHeader(name) || isDescriptionHeader(name) || isCommentHeader(name) || "CpasType".equalsIgnoreCase(name) || isAliasHeader(name))
                return true;
            if (ExperimentService.isInputOutputColumn(name))
                return true;
            for (ExpMaterialTable.Column column : ExpMaterialTable.Column.values())
            {
                if (isExpMaterialColumn(column, name))
                    return true;
            }
            return false;
        }

        private static boolean isExpMaterialColumn(ExpMaterialTable.Column column, String name)
        {
            return column.name().equalsIgnoreCase(name);
        }

        private static boolean isNameHeader(String name)
        {
            return isExpMaterialColumn(ExpMaterialTable.Column.Name, name);
        }

        private static boolean isDescriptionHeader(String name)
        {
            return isExpMaterialColumn(ExpMaterialTable.Column.Description, name);
        }

        private static boolean isSampleStateHeader(String name)
        {
            return isExpMaterialColumn(SampleState, name);
        }

        private static boolean isCommentHeader(String name)
        {
            return isExpMaterialColumn(ExpMaterialTable.Column.Flag, name) || "Comment".equalsIgnoreCase(name);
        }

        private static boolean isAliasHeader(String name)
        {
            return isExpMaterialColumn(ExpMaterialTable.Column.Alias, name);
        }

        private static boolean isMaterialExpDateHeader(String name)
        {
            return isExpMaterialColumn(ExpMaterialTable.Column.MaterialExpDate, name);
        }

        private static boolean isStoredAmountHeader(String name)
        {
            return isExpMaterialColumn(ExpMaterialTable.Column.StoredAmount, name) || "Amount".equalsIgnoreCase(name);
        }

        public static boolean isUnitsHeader(String name)
        {
            return isExpMaterialColumn(ExpMaterialTable.Column.Units, name);
        }
    }

    static class _GenerateNamesDataIterator extends SimpleTranslator
    {
        final ExpSampleTypeImpl sampleType;
        final NameGenerator nameGen;
        final NameGenerator aliquotNameGen;
        final NameGenerator.State nameState;
        final Lsid.LsidBuilder lsidBuilder;
        final DbSequence _lsidDbSeq;
        final Container _container;
        final int _batchSize;
        boolean first = true;
        Map<String, String> importAliasMap = null;
        boolean _allowUserSpecifiedNames = true;        // whether manual names specification is allowed or only name expression generation
        Set<String> _existingNames = null;
        List<Supplier<Map<String, Object>>> _extraPropsFns = new ArrayList<>();

        String generatedName = null;

        _GenerateNamesDataIterator(ExpSampleTypeImpl sampleType, Container dataContainer, User user, MapDataIterator source, DataIteratorContext context, int batchSize)
        {
            super(source, context);
            this.sampleType = sampleType;
            try
            {
                this.importAliasMap = sampleType.getImportAliasMap();
                _extraPropsFns.add(() -> {
                    if (this.importAliasMap != null)
                        return Map.of(PARENT_IMPORT_ALIAS_MAP_PROP, this.importAliasMap);
                    else
                        return Collections.emptyMap();
                });
            }
            catch (IOException e)
            {
                // do nothing
            }
            boolean skipDuplicateCheck = context.getConfigParameterBoolean(SkipMaxSampleCounterFunction);
            nameGen = sampleType.getNameGenerator(dataContainer, user, skipDuplicateCheck);
            aliquotNameGen = sampleType.getAliquotNameGenerator(dataContainer, user, skipDuplicateCheck);

            // check for project scoped counter in both name and aliquot name to decide if they need to be included
            NameGenerator.SampleNameExpressionSummary sampleNameExpressionSummary = getSampleNameExpressionSummary(nameGen, aliquotNameGen);
            if (sampleNameExpressionSummary != null)
            {
                NameGenerator.ExpressionSummary expressionSummary = nameGen.getExpressionSummary();
                nameGen.setExpressionSummary(new NameGenerator.ExpressionSummary(sampleNameExpressionSummary, expressionSummary.hasDateBasedSampleCounter(), expressionSummary.hasLineageInputs(), expressionSummary.hasLineageLookup()));
            }
            nameState = nameGen != null ? nameGen.createState(true) : null;
            lsidBuilder = sampleType.generateSampleLSID();
            _container = sampleType.getContainer();
            _batchSize = batchSize;

            if (context.getConfigParameterBoolean(ExperimentService.QueryOptions.UseLsidForUpdate))
                selectAll(CaseInsensitiveHashSet.of(Name.name(), RootMaterialRowId.name()));
            else
                selectAll(CaseInsensitiveHashSet.of(Name.name(), LSID.name(), RootMaterialRowId.name()));

            _lsidDbSeq = sampleType.getSampleLsidDbSeq(_batchSize, _container);

            addColumn(new BaseColumnInfo("name", JdbcType.VARCHAR), (Supplier)() -> generatedName);
            if (!context.getConfigParameterBoolean(ExperimentService.QueryOptions.UseLsidForUpdate))
                addColumn(new BaseColumnInfo("lsid", JdbcType.VARCHAR), (Supplier)() -> lsidBuilder.setObjectId(String.valueOf(_lsidDbSeq.next())).toString());
            // Ensure we have a cpasType column and it is of the right value
            addColumn(new BaseColumnInfo("cpasType", JdbcType.VARCHAR), new SimpleTranslator.ConstantColumn(sampleType.getLSID()));
            addColumn(new BaseColumnInfo("materialSourceId", JdbcType.INTEGER), new SimpleTranslator.ConstantColumn(sampleType.getRowId()));
        }

        _GenerateNamesDataIterator setAllowUserSpecifiedNames(boolean allowUserSpecifiedNames)
        {
            _allowUserSpecifiedNames = allowUserSpecifiedNames;
            return this;
        }

        _GenerateNamesDataIterator addExtraPropsFn(Supplier<Map<String, Object>> extraProps)
        {
            _extraPropsFns.add(extraProps);
            return this;
        }

        void onFirst()
        {
            first = false;
        }

        private NameGenerator.SampleNameExpressionSummary getSampleNameExpressionSummary(@NotNull NameGenerator nameGen, @NotNull NameGenerator aliquotNameGen)
        {
            if (sampleType == null)
                return null;

            boolean hasProjectSampleCounter = false;
            long minProjectSampleCounter = 0;
            boolean hasProjectSampleRootCounter = false;
            long minProjectSampleRootCounter = 0;

            NameGenerator.SampleNameExpressionSummary nameSummary = nameGen.getExpressionSummary().sampleSummary();
            NameGenerator.SampleNameExpressionSummary aliquotSummary = aliquotNameGen.getExpressionSummary().sampleSummary();
            if (nameSummary.hasProjectSampleCounter() || aliquotSummary.hasProjectSampleCounter())
            {
                hasProjectSampleCounter = true;
                minProjectSampleCounter = sampleType.getMinSampleCounter();
            }

            if (nameSummary.hasProjectSampleRootCounter() || aliquotSummary.hasProjectSampleRootCounter())
            {
                hasProjectSampleRootCounter = true;
                minProjectSampleRootCounter = sampleType.getMinRootSampleCounter();
            }

            if (hasProjectSampleCounter || hasProjectSampleRootCounter)
                return new NameGenerator.SampleNameExpressionSummary(hasProjectSampleCounter, hasProjectSampleRootCounter, minProjectSampleCounter, minProjectSampleRootCounter);

            return null;
        }

        @Override
        protected void processNextInput()
        {
            Map<String,Object> map = new HashMap<>(((MapDataIterator)getInput()).getMap());

            String aliquotedFrom = null;
            Object aliquotedFromObj = map.get(ExpMaterial.ALIQUOTED_FROM_INPUT);
            if (aliquotedFromObj != null)
            {
                if (aliquotedFromObj instanceof String)
                {
                    // Issue 45563: We need the AliquotedFrom name to be quoted so we can properly find the parent,
                    // but we don't want to include the quotes in the name we generate using AliquotedFrom
                    aliquotedFrom = StringUtilsLabKey.unquoteString((String) aliquotedFromObj);
                    map.put(ExpMaterial.ALIQUOTED_FROM_INPUT, aliquotedFrom);
                }
                else if (aliquotedFromObj instanceof Number)
                {
                    aliquotedFrom = aliquotedFromObj.toString();
                }
            }

            boolean isAliquot = !StringUtils.isEmpty(aliquotedFrom);

            try
            {
                Object currNameObj = map.get("Name");
                if (currNameObj != null && !_allowUserSpecifiedNames)
                {
                    if (StringUtils.isNotBlank(currNameObj.toString()))
                    {
                        if (_context.getInsertOption().equals(QueryUpdateService.InsertOption.MERGE))
                        {
                            // don't flag rows that already exist if the option is set to update existing
                            if (!rowExists(currNameObj.toString()))
                                addRowError("Manual entry of names has been disabled for this folder. Only naming-pattern-generated names (or existing names) are allowed.");
                        }
                        else
                            addRowError("Manual entry of names has been disabled for this folder. Only naming-pattern-generated names are allowed.");

                    }
                }

                if (nameGen != null)
                {
                    generatedName = nameGen.generateName(nameState, map, null, null, _extraPropsFns, isAliquot ? aliquotNameGen.getParsedNameExpression() : null);
                }
                else
                    addRowError("Error creating naming pattern generator.");
            }

            catch (NameGenerator.DuplicateNameException dup)
            {
                addRowError("Duplicate name '" + dup.getName() + "' on row " + dup.getRowNumber());
            }
            catch (NameGenerator.NameGenerationException e)
            {
                // Failed to generate a name due to some part of the expression not in the row
                if (isAliquot)
                {
                    addRowError("Failed to generate name for aliquot on row " + e.getRowNumber() + " using aliquot naming pattern " + sampleType.getAliquotNameExpression() + ". Check the syntax of the aliquot naming pattern and the data values for the aliquot.");
                }
                else
                {
                    if (sampleType.hasNameExpression())
                        addRowError("Failed to generate name for sample on row " + e.getRowNumber() + " using naming pattern " + sampleType.getNameExpression() + ". Check the syntax of the naming pattern and the data values for the sample.");
                    else if (sampleType.hasNameAsIdCol())
                        addRowError("SampleID or Name is required for sample on row " + e.getRowNumber());
                    else
                        addRowError("All id columns are required for sample on row " + e.getRowNumber());
                }
            }
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            // consider add onFirst() as callback from SimpleTranslator
            if (first)
                onFirst();

            // calls processNextInput()
            boolean next = super.next();
            if (!next)
            {
                if (null != nameState)
                    nameState.cleanUp();
            }
            return next;
        }

        @Override
        public void close() throws IOException
        {
            super.close();
            if (null != nameState)
                nameState.close();
        }

        private boolean rowExists(String name)
        {
            if (_existingNames == null)
            {
                _existingNames = new HashSet<>();
                SamplesSchema schema = new SamplesSchema(User.getSearchUser(), _container);
                TableSelector ts = new TableSelector(schema.getTable(sampleType, null), Collections.singleton("Name")).setMaxRows(1_000_000);
                ts.fillSet(_existingNames);
            }
            return _existingNames.contains(name);
        }
    }

    static class _SamplesCoerceDataIterator extends SimpleTranslator
    {
        private static final String INVALID_ALIQUOT_PROPERTY = "An aliquot-specific property [%1$s] value has been ignored for a non-aliquot sample.";
        private static final String INVALID_NONALIQUOT_PROPERTY = "A sample property [%1$s] value has been ignored for an aliquot.";

        private final ExpSampleTypeImpl _sampleType;
        private final Measurement.Unit _metricUnit;

        public _SamplesCoerceDataIterator(DataIterator source, DataIteratorContext context, ExpSampleTypeImpl sampleType, ExpMaterialTableImpl materialTable)
        {
            super(source, context);
            _sampleType = sampleType;
            _metricUnit = _sampleType.getMetricUnit() != null ? Measurement.Unit.valueOf(_sampleType.getMetricUnit()) : null;
            setDebugName("Coerce before trigger script - samples");
            init(materialTable, context.getInsertOption().useImportAliases);
        }

        void init(TableInfo target, boolean useImportAliases)
        {
            Map<String,ColumnInfo> targetMap = DataIteratorUtil.createTableMap(target, useImportAliases);
            DataIterator di = getInput();
            int count = di.getColumnCount();

            Map<String, Boolean> scopedFields = new CaseInsensitiveHashMap<>(); // fields that are either aliquot-specific, or parent meta
            for (DomainProperty dp : _sampleType.getDomain().getProperties())
            {
                if (!ExpSchema.DerivationDataScopeType.All.name().equalsIgnoreCase(dp.getDerivationDataScope()))
                    scopedFields.put(dp.getName(), ExpSchema.DerivationDataScopeType.ChildOnly.name().equalsIgnoreCase(dp.getDerivationDataScope()));
            }

            int derivationDataColInd = -1;
            int unitDataColInd = -1;
            int amountDataColInd = -1;
            for (int i = 1; i <= count && (derivationDataColInd < 0 || unitDataColInd < 0 || amountDataColInd < 0); i++)
            {
                ColumnInfo from = di.getColumnInfo(i);
                if (from != null)
                {
                    if (getAliquotedFromColName().equalsIgnoreCase(from.getName()))
                        derivationDataColInd = i;
                    else if ("Units".equalsIgnoreCase(from.getName()))
                        unitDataColInd = i;
                    else if ("StoredAmount".equalsIgnoreCase(from.getName()) || "Amount".equalsIgnoreCase(from.getName()))
                        amountDataColInd = i;
                }
            }

            for (int i = 1; i <= count; i++)
            {
                ColumnInfo from = di.getColumnInfo(i);
                ColumnInfo to = targetMap.get(from.getName());

                if (null != to)
                {
                    String name = to.getName();
                    boolean isScopedField = scopedFields.containsKey(name);

                    String ignoredAliquotPropValue = String.format(INVALID_ALIQUOT_PROPERTY, name);
                    String ignoredMetaPropValue = String.format(INVALID_NONALIQUOT_PROPERTY, name);
                    if (to.getPropertyType() == PropertyType.ATTACHMENT || to.getPropertyType() == PropertyType.FILE_LINK)
                    {
                        if (isScopedField)
                        {
                            ColumnInfo clone = new BaseColumnInfo(to);
                            addColumn(clone, new DerivationScopedColumn(i, derivationDataColInd, scopedFields.get(name), ignoredAliquotPropValue, ignoredMetaPropValue));
                        }
                        else
                            addColumn(to, i);
                    }
                    else if (to.getFk() instanceof MultiValuedForeignKey)
                    {
                        // pass-through multi-value columns -- converting will stringify a collection
                        if (isScopedField)
                        {
                            var col = new BaseColumnInfo(getInput().getColumnInfo(i));
                            col.setName(name);
                            addColumn(col, new DerivationScopedColumn(i, derivationDataColInd, scopedFields.get(name), ignoredAliquotPropValue, ignoredMetaPropValue));
                        }
                        else
                            addColumn(to.getName(), i);
                    }
                    else if (name.equalsIgnoreCase("Units"))
                    {
                        addColumn(to, new SampleUnitsConvertColumn(name, i, to.getJdbcType()));
                    }
                    else if (name.equalsIgnoreCase("StoredAmount"))
                    {
                        addColumn(to, new SampleAmountConvertColumn(name, i, to.getJdbcType()));
                    }
                    else
                    {
                        if (isScopedField)
                            _addConvertColumn(name, i, to.getJdbcType(), to.getFk(), derivationDataColInd, scopedFields.get(name));
                        else
                            addConvertColumn(to.getName(), i, to.getJdbcType(), to.getFk(), RemapMissingBehavior.OriginalValue);
                    }
                }
                else
                {
                    if (derivationDataColInd == i && _context.getInsertOption().mergeRows && !_context.getConfigParameterBoolean(SampleTypeService.ConfigParameters.DeferAliquotRuns))
                    {
                        addColumn(AliquotedFromLSID.name(), i); // temporarily populate sample name as lsid for merge, used to differentiate insert vs update for merge
                    }

                    addColumn(i);
                }
            }
        }

        private String getAliquotedFromColName()
        {
            // for update, AliquotedFromLSID is reselected from existing row. For other actions, "AliquotedFrom" needs to be provided
            return _context.getInsertOption().updateOnly ? AliquotedFromLSID.name() : ExpMaterial.ALIQUOTED_FROM_INPUT;
        }

        private void _addConvertColumn(String name, int fromIndex, JdbcType toType, ForeignKey toFk, int derivationDataColInd, boolean isAliquotField)
        {
            var col = new BaseColumnInfo(getInput().getColumnInfo(fromIndex));
            col.setName(name);
            col.setJdbcType(toType);
            if (toFk != null)
                col.setFk(toFk);

            _addConvertColumn(col, fromIndex, derivationDataColInd, isAliquotField);
        }

        private void _addConvertColumn(ColumnInfo col, int fromIndex, int derivationDataColInd, boolean isAliquotField)
        {
            SimpleConvertColumn c = createConvertColumn(col, fromIndex, RemapMissingBehavior.OriginalValue);
            c = new DerivationScopedConvertColumn(fromIndex, c, derivationDataColInd, isAliquotField, String.format(INVALID_ALIQUOT_PROPERTY, col.getName()), String.format(INVALID_NONALIQUOT_PROPERTY, col.getName()));

            addColumn(col, c);
        }

        protected class SampleUnitsConvertColumn extends SimpleTranslator.SimpleConvertColumn
        {
            public SampleUnitsConvertColumn(String fieldName, int indexFrom, @Nullable JdbcType to)
            {
                super(fieldName, indexFrom, to, true);
            }

            @Override
            protected Object convert(Object o)
            {
                Measurement.validateUnits((String) o, _metricUnit);
                return Measurement.Unit.getUnit((String) o);
            }
        }

        protected class SampleAmountConvertColumn extends SimpleTranslator.SimpleConvertColumn
        {
            public SampleAmountConvertColumn(String fieldName, int indexFrom, @Nullable JdbcType to)
            {
                super(fieldName, indexFrom, to, true);
            }

            @Override
            protected Object convert(Object amountObj)
            {
                return Measurement.convertToAmount(amountObj);
            }
        }
    }
}
