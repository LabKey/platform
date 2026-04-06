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
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.LongHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConversionExceptionWithMessage;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.Filter;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.NameGeneratorState;
import org.labkey.api.data.RemapCache;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpdateableTableInfo;
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
import org.labkey.api.dataiterator.SampleUpdateAddColumnsDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
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
import org.labkey.api.ontology.KindOfQuantity;
import org.labkey.api.ontology.Quantity;
import org.labkey.api.ontology.Unit;
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
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.MoveEntitiesPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.workflow.WorkflowService;
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

import static java.util.Collections.emptyMap;
import static org.labkey.api.audit.AuditHandler.DELTA_PROVIDED_DATA_PREFIX;
import static org.labkey.api.audit.AuditHandler.PROVIDED_DATA_PREFIX;
import static org.labkey.api.data.TableSelector.ALL_COLUMNS;
import static org.labkey.api.dataiterator.DataIteratorUtil.DUPLICATE_COLUMN_IN_DATA_ERROR;
import static org.labkey.api.dataiterator.DetailedAuditLogDataIterator.AuditConfigs;
import static org.labkey.api.dataiterator.SampleUpdateAddColumnsDataIterator.CURRENT_SAMPLE_STATUS_COLUMN_NAME;
import static org.labkey.api.exp.api.ExpRunItem.PARENT_IMPORT_ALIAS_MAP_PROP;
import static org.labkey.api.exp.api.ExperimentService.QueryOptions.SkipBulkRemapCache;
import static org.labkey.api.exp.api.SampleTypeDomainKind.ALIQUOT_ROLLUP_FIELD_LABELS;
import static org.labkey.api.exp.api.SampleTypeService.ConfigParameters.SkipAliquotRollup;
import static org.labkey.api.exp.api.SampleTypeService.ConfigParameters.SkipMaxSampleCounterFunction;
import static org.labkey.api.exp.api.SampleTypeService.MISSING_AMOUNT_ERROR_MESSAGE;
import static org.labkey.api.exp.api.SampleTypeService.MISSING_UNITS_ERROR_MESSAGE;
import static org.labkey.api.exp.api.SampleTypeService.UNPROVIDED_VALUE_ERROR_MESSAGE_PATTERN;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.*;
import static org.labkey.api.util.IntegerUtils.asLong;
import static org.labkey.experiment.ExpDataIterators.incrementCounts;
import static org.labkey.experiment.api.SampleTypeServiceImpl.SampleChangeType.insert;
import static org.labkey.experiment.api.SampleTypeServiceImpl.SampleChangeType.rollup;
import static org.labkey.experiment.api.SampleTypeServiceImpl.SampleChangeType.update;

/**
 * QueryUpdateService implementation for samples in sample types.
 */
public class SampleTypeUpdateServiceDI extends DefaultQueryUpdateService
{
    public static final Logger LOG = LogHelper.getLogger(SampleTypeUpdateServiceDI.class, "Sample type update service info");

    public static final String ROOT_RECOMPUTE_ROWID_COL = "RootIdToRecompute";
    public static final String PARENT_RECOMPUTE_NAME_COL = "ParentNameToRecompute";
    public static final String ROOT_RECOMPUTE_ROWID_SET = "RootIdToRecomputeSet";
    public static final String PARENT_RECOMPUTE_NAME_SET = "ParentNameToRecomputeSet";

    public static final Map<String, String> SAMPLE_ALT_IMPORT_NAME_COLS;

    private static final Map<ExpMaterialTable.Column, JdbcType> ALIQUOT_ROLLUP_FIELDS = Map.of(
            AliquotCount, JdbcType.INTEGER,
            AvailableAliquotCount, JdbcType.INTEGER,
            AliquotVolume, JdbcType.DOUBLE,
            AvailableAliquotVolume, JdbcType.DOUBLE
    );

    static
    {
        SAMPLE_ALT_IMPORT_NAME_COLS = new CaseInsensitiveHashMap<>();
        SAMPLE_ALT_IMPORT_NAME_COLS.put("SampleId", "Name");
        SAMPLE_ALT_IMPORT_NAME_COLS.put("Sample Id", "Name");
        SAMPLE_ALT_IMPORT_NAME_COLS.put("ExpirationDate", "MaterialExpDate");
        SAMPLE_ALT_IMPORT_NAME_COLS.put("Expiration Date", "MaterialExpDate");
        SAMPLE_ALT_IMPORT_NAME_COLS.put("Entered Storage", "Stored");
        SAMPLE_ALT_IMPORT_NAME_COLS.put("EnteredStorage", "Stored");
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
        return new PreTriggerDataIteratorBuilder(_sampleType, (ExpMaterialTableImpl) getQueryTable(), in, getContainer(), getUser());
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
            onSamplesChanged(outputRows, configParameters, container, insert);
            audit(QueryService.AuditAction.INSERT);
        }
        return ret;
    }

    private Pair<Set<Long>, Set<String>> getSampleParentsForRecalc(List<Map<String, Object>> outputRows)
    {
        if (outputRows == null || outputRows.isEmpty())
            return null;

        Set<Long> rootRowIds = new LongHashSet();
        Set<String> parentNames = new HashSet<>();
        if (outputRows.size() == 1 && outputRows.get(0).containsKey(ROOT_RECOMPUTE_ROWID_SET))
        {
            rootRowIds.addAll((Collection<? extends Long>) outputRows.get(0).get(ROOT_RECOMPUTE_ROWID_SET));
            if (outputRows.get(0).containsKey(PARENT_RECOMPUTE_NAME_SET))
                parentNames.addAll((Collection<? extends String>) outputRows.get(0).get(PARENT_RECOMPUTE_NAME_SET));
        }
        else
        {
            for (Map<String, Object> result : outputRows)
            {
                if (!result.containsKey(ROOT_RECOMPUTE_ROWID_COL))
                    break;
                Long rootIdObj = MapUtils.getLong(result,ROOT_RECOMPUTE_ROWID_COL);
                Object nameObj = result.get(PARENT_RECOMPUTE_NAME_COL);
                if (rootIdObj != null)
                    rootRowIds.add(rootIdObj);
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
        if (it == null || context.getErrors().hasErrors())
            return 0;

        try
        {
            if (null != rows)
            {
                MapDataIterator maps = DataIteratorUtil.wrapMap(it, false);
                Map<String, Integer> columnMap = DataIteratorUtil.createColumnNameMap(it);
                Integer parenRowIdToRecomputeCol = columnMap.get(ROOT_RECOMPUTE_ROWID_COL);
                Integer parentNameToRecomputeCol = columnMap.get(PARENT_RECOMPUTE_NAME_COL);

                boolean hasRollUpColumns = parenRowIdToRecomputeCol != null && parentNameToRecomputeCol != null;
                Set<Long> rowIdsToRecompute = new LongHashSet();
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
                                        rowIdsToRecompute.add(asLong(rowIdObj));
                                    Object nameObj = (_delegate).get(parentNameToRecomputeCol);
                                    if (nameObj != null)
                                    {
                                        if (nameObj instanceof String name)
                                        {
                                            nameToRecompute.add(name);
                                        }
                                        else if (nameObj instanceof Number)
                                        {
                                            nameToRecompute.add(nameObj.toString());
                                        }
                                    }
                                }
                                else
                                    rows.add(((MapDataIterator) _delegate).getMapExcludeExistingRecord());
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
    protected void preImportDIBValidation(@Nullable DataIteratorBuilder in, @Nullable Collection<String> inputColumns)
    {
        ExperimentServiceImpl.get().checkDuplicateParentColumns(in, inputColumns, _sampleType);
    }

    @Nullable
    protected Map<String, Object> extractProvidedAmountsAndUnits(@NotNull Map<String, Object> dataRow)
    {
        Map<String, Object> result = new HashMap<>();
        String unitsStr = "";
        String prefix;
        if (dataRow.containsKey(DELTA_PROVIDED_DATA_PREFIX + StoredAmount.name()))
            prefix = DELTA_PROVIDED_DATA_PREFIX;
        else
        {
            // with no sample type display unit, no conversion will happen
            if (_sampleType == null || _sampleType.getMetricUnit() == null)
                return null;
            prefix = PROVIDED_DATA_PREFIX;
        }
        Object amountVal = dataRow.get(prefix + StoredAmount.name());
        if (amountVal == null)
            return null;

        if (dataRow.get(prefix + Units.name()) != null)
            unitsStr = " " + dataRow.get(prefix + Units.name()).toString();

        result.put(prefix + StoredAmount.label(), amountVal + unitsStr);

        return result;
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
        dib = DetailedAuditLogDataIterator.getDataIteratorBuilder(getQueryTable(), dib, context.getInsertOption(), user, container, this::extractProvidedAmountsAndUnits);

        UserSchema userSchema = getQueryTable().getUserSchema();
        if (userSchema != null)
        {
            ExpSampleType sampleType = ((ExpMaterialTableImpl) getQueryTable()).getSampleType();
            if (InventoryService.get() != null && !_sampleType.isMedia())
                dib = LoggingDataIterator.wrap(InventoryService.get().getPersistStorageItemDataIteratorBuilder(dib, userSchema.getContainer(), userSchema.getUser(), sampleType));

            if (sampleType.getAutoLinkTargetContainer() != null && StudyPublishService.get() != null && !context.getInsertOption().updateOnly/* TODO support link to study on update? */)
                dib = LoggingDataIterator.wrap(new ExpDataIterators.AutoLinkToStudyDataIteratorBuilder(dib, getSchema(), userSchema.getContainer(), userSchema.getUser(), sampleType));
            WorkflowService workService = WorkflowService.get();
            if (workService != null)
            {
                if (context.getConfigParameter(WorkflowService.WorkflowConfigs.ActionId) != null)
                {
                    dib = workService.getSampleCreationDataIteratorBuilder(dib, userSchema.getContainer(), userSchema.getUser());
                    dib = workService.getActionAuditDataIteratorBuilder(dib, userSchema.getContainer(), userSchema.getUser());
                }
            }
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
                configureCrossFolderImport(rows, context);
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
            onSamplesChanged(!isMediaUpdate ? outputRows : null, context.getConfigParameters(), container, context.getInsertOption().allowUpdate ? update : insert);
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
            onSamplesChanged(null, configParameters, container, update); // mergeRows not really used, skip wiring recalc
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
            onSamplesChanged(results, configParameters, container, SampleTypeServiceImpl.SampleChangeType.insert);
            audit(QueryService.AuditAction.INSERT);
        }
        return results;
    }

    /**
     * This method is meant to help us ensure that every stored amount also has a unit. This checks only for the
     * presence or absence of columns in the incoming data. If both columns are present, no exception is thrown.
     *
     * @param columns      The set of columns in the input
     */
    public static void confirmAmountAndUnitsColumns(Collection<String> columns)
    {
        boolean hasUnits = columns.stream().anyMatch(column -> column.equalsIgnoreCase(Units.name()));
        boolean hasAmount = columns.stream().anyMatch(column -> StoredAmount.namesAndLabels().contains(column));

        if (hasUnits == hasAmount)
            return; // both columns are present or neither is
        if (!hasAmount)
            throw new ConversionExceptionWithMessage(MISSING_AMOUNT_ERROR_MESSAGE);

        throw new ConversionExceptionWithMessage(MISSING_UNITS_ERROR_MESSAGE);
    }

    @Override
    public List<Map<String, Object>> updateRows(
        User user,
        Container container,
        List<Map<String, Object>> rows,
        List<Map<String, Object>> oldKeys,
        BatchValidationException errors,
        @Nullable Map<Enum, Object> configParameters,
        Map<String, Object> extraScriptContext
    ) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        assert _sampleType != null : "SampleType required for insert/update, but not required for read/delete";
        if (rows == null || rows.isEmpty())
            return Collections.emptyList();

        List<Map<String, Object>> results;
        Map<Enum, Object> finalConfigParameters = configParameters == null ? new HashMap<>() : configParameters;
        recordDataIteratorUsed(configParameters);

        try
        {
            results = getSchema().getDbSchema().getScope().executeWithRetry(tx ->
                    updateRowsUsingPartitionedDIB(tx, user, container, rows, errors, finalConfigParameters, extraScriptContext));
        }
        catch (DbScope.RetryPassthroughException retryException)
        {
            retryException.rethrow(BatchValidationException.class);
            throw retryException.throwRuntimeException();
        }

        if (results != null && !results.isEmpty() && !errors.hasErrors())
        {
            onSamplesChanged(!_sampleType.isMedia() ? results : null, configParameters, container, update);
            audit(QueryService.AuditAction.UPDATE);
        }

        return results;
    }

    @Override
    protected void validatePartitionedRowKeys(Collection<String> columns)
    {
        confirmAmountAndUnitsColumns(columns);
    }

    @Override
    public Map<String, Object> moveRows(User user, Container container, Container targetContainer, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
            throws BatchValidationException
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
                    throw new BatchValidationException(new ValidationException(e.getMessage()));
                }
            }

            SimpleMetricsService.get().increment(ExperimentService.MODULE_NAME, "moveEntities", "samples");
        }
        return new HashMap<>(allContainerResponse);
    }

    private Map<Container, List<ExpMaterial>> getMaterialsForMoveRows(Container container, Container targetContainer, List<Map<String, Object>> rows, BatchValidationException errors)
    {
        Set<Long> sampleIds = rows.stream().map(row -> MapUtils.getLong(row, RowId.name())).collect(Collectors.toSet());
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

    public Set<String> getSampleMetaFields()
    {
        Domain domain = getDomain();
        Set<String> fields = domain.getProperties().stream()
                .filter(dp -> !LSID.name().equalsIgnoreCase(dp.getName())
                                && !Name.name().equalsIgnoreCase(dp.getName())
                                && (StringUtils.isEmpty(dp.getDerivationDataScope())
                                    || ExpSchema.DerivationDataScopeType.ParentOnly.name().equalsIgnoreCase(dp.getDerivationDataScope())))
                .map(ImportAliasable::getName)
                .collect(Collectors.toSet());

        // Issue 53036: also include column labels and aliases
        Set<String> metaFieldNames = new CaseInsensitiveHashSet(fields);
        for (String fieldName : fields)
        {
            ColumnInfo columnInfo = getQueryTable().getColumn(fieldName);
            if (columnInfo != null)
            {
                metaFieldNames.add(columnInfo.getLabel());
                metaFieldNames.add(columnInfo.getAlias().getId());
            }
        }

        return metaFieldNames;
    }

    public static boolean isAliquotStatusChangeNeedRecalc(Collection<Long> availableStatuses, Long oldStatus, Long newStatus)
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
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, boolean allowOwner, boolean retainCreation)
    {
        throw new UnsupportedOperationException("_update() is no longer supported for samples");

    }

    @Override
    protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys)
    {
        throw new UnsupportedOperationException("_update() is no longer supported for samples");
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
        List<Long> id = new LinkedList<>();
        Long rowId = getMaterialRowId(oldRowMap);
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
            List<Long> ids = new LinkedList<>();

            for (Map<String, Object> k : keys)
            {
                // Issue 40621
                // adding input fields is expensive, skip input fields for delete since deleted samples are not surfaced on Timeline UI
                Map<String, Object> map = getMaterialMap(k);
                if (map == null)
                    throw new QueryUpdateServiceException("No Sample Type Material found for RowID or LSID");

                Long rowId = getMaterialRowId(map);
                if (rowId == null)
                    throw new QueryUpdateServiceException("RowID is required to delete a Sample Type Material");

                Long sampleStateId = MapUtils.getLong(map, SampleState.name());
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
            addAuditEvent(user, container,  QueryService.AuditAction.DELETE, configParameters, result, null, null);
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

    private @Nullable Long getMaterialSourceId(Map<String, Object> row)
    {
        return MapUtils.getLong(row, MaterialSourceId.name());
    }

    private @Nullable Long getMaterialRowId(Map<String, Object> row)
    {
        return MapUtils.getLong(row, RowId.name());
    }

    private @Nullable Filter getMaterialFilter(Map<String, Object> keys, boolean useSampleType)
    {
        Long rowId = getMaterialRowId(keys);
        if (rowId != null)
            return new SimpleFilter(RowId.fieldKey(), rowId);

        String lsid = getMaterialLsid(keys);
        if (lsid != null)
            return new SimpleFilter(LSID.fieldKey(), lsid);

        String name = getMaterialName(keys);
        if (name != null)
        {
            Long materialSourceId = null;
            if (useSampleType)
            {
                if (_sampleType != null)
                    materialSourceId = _sampleType.getRowId();
            }
            else
                materialSourceId = getMaterialSourceId(keys);

            if (materialSourceId != null)
            {
                SimpleFilter filter = new SimpleFilter(Name.fieldKey(), name);
                filter.addCondition(MaterialSourceId.fieldKey(), materialSourceId);
                return filter;
            }
        }

        return null;
    }

    private Map<String, Object> getMaterialMap(Map<String, Object> keys) throws QueryUpdateServiceException
    {
        return getMaterialMap(keys, false);
    }

    private Map<String, Object> getMaterialMap(Map<String, Object> keys, boolean useSampleType) throws QueryUpdateServiceException
    {
        Filter filter = getMaterialFilter(keys, useSampleType);
        if (filter == null)
            throw new QueryUpdateServiceException("Either RowId, LSID, or Name and MaterialSourceId is required to get sample.");

        return new TableSelector(getQueryTable(), filter, null).getMap();
    }

    @Override
    public boolean hasExistingRowsInOtherContainers(Container container, Map<Integer, Map<String, Object>> keys)
    {
        Long sampleTypeId = null;
        Set<String> sampleNames = new HashSet<>();
        for (Map.Entry<Integer, Map<String, Object>> keyMap : keys.entrySet())
        {
            String name = getMaterialName(keyMap.getValue());

            if (name != null)
                sampleNames.add(name);

            if (sampleTypeId == null)
                sampleTypeId = getMaterialSourceId(keyMap.getValue());
        }

        SimpleFilter filter = new SimpleFilter(MaterialSourceId.fieldKey(), sampleTypeId);
        filter.addCondition(Name.fieldKey(), sampleNames, CompareType.IN);
        filter.addCondition(FieldKey.fromParts("Container"), container, CompareType.NEQ);

        return new TableSelector(ExperimentService.get().getTinfoMaterial(), filter, null).exists();
    }

    private record ExistingRowSelect(Set<String> columns, boolean includeParent) {}

    private @NotNull ExistingRowSelect getExistingRowSelect(@Nullable Set<String> dataColumns)
    {
        if (!(getQueryTable() instanceof UpdateableTableInfo updatable) || dataColumns == null)
            return new ExistingRowSelect(ALL_COLUMNS, true);

        CaseInsensitiveHashMap<String> remap = updatable.remapSchemaColumns();
        if (null == remap)
            remap = CaseInsensitiveHashMap.of();

        // AliquotRollupDataIterator needs "samplestate", "storedamount", "rootmaterialrowId", "units" for MERGE option
        // "RawAmount" and "RawUnits" are needed to replace converted amount and unit values with raw values so the
        // audit difference is accurate.
        Set<String> includedColumns = new CaseInsensitiveHashSet(
                LSID.name(),
                Name.name(),
                RawAmount.name(),
                RawUnits.name(),
                RootMaterialRowId.name(),
                RowId.name(),
                SampleState.name(),
                StoredAmount.name(),
                Units.name()
        );
        for (ColumnInfo column : getQueryTable().getColumns())
        {
            if (dataColumns.contains(column.getColumnName()))
                includedColumns.add(column.getColumnName());
            else if (dataColumns.contains(remap.get(column.getColumnName())))
                includedColumns.add(remap.get(column.getColumnName()));
        }

        boolean hasParentInput = false;
        if (_sampleType != null)
        {
            try
            {
                Map<String, String> importAliases = _sampleType.getImportAliases();
                for (String col : dataColumns)
                {
                    if (ExperimentService.isInputOutputColumn(col) || Strings.CI.equals("parent", col) || importAliases.containsKey(col))
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

        return new ExistingRowSelect(includedColumns, hasParentInput);
    }

    @Override
    public Map<Integer, Map<String, Object>> getExistingRows(
        User user,
        Container container,
        Map<Integer, Map<String, Object>> keys,
        boolean verifyNoCrossFolderData,
        boolean verifyExisting,
        @Nullable Set<String> columns
    ) throws InvalidKeyException, QueryUpdateServiceException
    {
        ExistingRowSelect existingRowSelect = getExistingRowSelect(columns);
        Set<String> selectColumns = existingRowSelect.columns;

        Map<Integer, Map<String, Object>> sampleRows = new LinkedHashMap<>();
        Map<Long, Integer> rowIdRowNumMap = new LinkedHashMap<>();
        Map<String, Integer> nameRowNumMap = new LinkedHashMap<>();
        Long sampleTypeId = null;
        for (Map.Entry<Integer, Map<String, Object>> keyMap : keys.entrySet())
        {
            Integer rowNum = keyMap.getKey();
            Long rowId = getMaterialRowId(keyMap.getValue());
            if (rowId != null)
            {
                rowIdRowNumMap.put(rowId, rowNum);
                continue;
            }

            String name = getMaterialName(keyMap.getValue());
            Long materialSourceId = getMaterialSourceId(keyMap.getValue());
            if (name != null && materialSourceId != null)
            {
                sampleTypeId = materialSourceId;
                nameRowNumMap.put(name, rowNum);
                continue;
            }

            throw new QueryUpdateServiceException("Either RowId or Name is required to get Sample Type Material.");
        }

        Set<Long> missingRowIds;
        if (rowIdRowNumMap.isEmpty())
            missingRowIds = Collections.emptySet();
        else
        {
            missingRowIds = new HashSet<>(rowIdRowNumMap.keySet());
            SimpleFilter filter = new SimpleFilter(RowId.fieldKey(), rowIdRowNumMap.keySet(), CompareType.IN);
            filter.addCondition(FieldKey.fromParts("Container"), container);
            Map<String, Object>[] rows = new TableSelector(getQueryTable(), selectColumns, filter, null).getMapArray();
            for (Map<String, Object> row : rows)
            {
                Long rowId = asLong(row.get(RowId.name()));
                Integer rowNum = rowIdRowNumMap.get(rowId);
                missingRowIds.remove(rowId);
                sampleRows.put(rowNum, row);
            }
        }

        Set<String> missingNames;
        if (nameRowNumMap.isEmpty())
            missingNames = Collections.emptySet();
        else
        {
            missingNames = new HashSet<>(nameRowNumMap.keySet());
            SimpleFilter filter = new SimpleFilter(MaterialSourceId.fieldKey(), sampleTypeId);
            filter.addCondition(Name.fieldKey(), nameRowNumMap.keySet(), CompareType.IN);
            filter.addCondition(FieldKey.fromParts("Container"), container);
            Map<String, Object>[] rows = new TableSelector(getQueryTable(), selectColumns, filter, null).getMapArray();
            for (Map<String, Object> row : rows)
            {
                String name = (String) row.get(Name.name());
                Integer rowNum = nameRowNumMap.get(name);
                sampleRows.put(rowNum, row);
                missingNames.remove(name);
            }
        }

        if (verifyNoCrossFolderData && (!missingNames.isEmpty() || !missingRowIds.isEmpty()))
        {
            // Issue 52922: cross-folder merge without Product Folders enabled silently ignores the cross-folder
            // row update. Use a relaxed container filter to find existing data from cross-containers.
            ContainerFilter cf = new ContainerFilter.AllInProjectPlusShared(container, user);
            Set<GUID> containerIds = new HashSet<>(Objects.requireNonNull(cf.getIds()));
            containerIds.remove(container.getEntityId());

            if (!containerIds.isEmpty())
            {
                if (!missingRowIds.isEmpty())
                {
                    SimpleFilter filter = new SimpleFilter(RowId.fieldKey(), missingRowIds, CompareType.IN);
                    filter.addCondition(FieldKey.fromParts("Container"), containerIds, CompareType.IN);
                    var row = new TableSelector(ExperimentService.get().getTinfoMaterial(), CaseInsensitiveHashSet.of(RowId.name(), Name.name()), filter, null).setMaxRows(1).getMap();
                    if (row != null)
                        throw new InvalidKeyException("Sample does not belong to " + container.getName() + " container: " + row.get(Name.name()) + " (" + row.get(RowId.name()) + ").");
                }

                if (!missingNames.isEmpty())
                {
                    SimpleFilter filter = new SimpleFilter(MaterialSourceId.fieldKey(), sampleTypeId);
                    filter.addCondition(FieldKey.fromParts("Container"), containerIds, CompareType.IN);
                    filter.addCondition(Name.fieldKey(), missingNames, CompareType.IN);

                    var row = new TableSelector(ExperimentService.get().getTinfoMaterial(), CaseInsensitiveHashSet.of(Name.name()), filter, null).setMaxRows(1).getMap();
                    if (row != null)
                        throw new InvalidKeyException("Sample does not belong to " + container.getName() + " container: " + row.get(Name.name()) + ".");
                }
            }
        }

        if (verifyExisting)
        {
            if (!missingRowIds.isEmpty())
                throw new InvalidKeyException("Sample does not exist: (RowId) " + missingRowIds.iterator().next() + ".");
            if (!missingNames.isEmpty())
                throw new InvalidKeyException("Sample does not exist: " + missingNames.iterator().next() + ".");
        }

        // if contains domain fields, check for aliquot specific fields
        if (!getQueryTable().getName().equalsIgnoreCase("material"))
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

        Set<String> lsids = new HashSet<>();
        for (Map<String, Object> sampleRow : sampleRows.values())
            lsids.add(getMaterialLsid(sampleRow));
        List<ExpMaterialImpl> seeds = ExperimentServiceImpl.get().getExpMaterialsByLsid(lsids);

        ExperimentServiceImpl.get().addRowsParentsFields(new HashSet<>(seeds), sampleRows, user, container);

        return sampleRows;
    }

    @Override
    public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys)
            throws QueryUpdateServiceException
    {
        if (!hasPermission(user, ReadPermission.class))
            throw new UnauthorizedException("You do not have permission to read data from this table.");

        List<Map<String, Object>> result = new ArrayList<>(keys.size());
        for (Map<String, Object> k : keys)
        {
            Map<String, Object> materialMap = getMaterialMap(k);
            if (materialMap != null)
                result.add(materialMap);
        }
        return result;
    }

    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws QueryUpdateServiceException
    {
        Map<String, Object> sampleRow = getMaterialMap(keys, true);
        if (sampleRow == null)
            return null;

        Long sampleRowId = asLong(sampleRow.get(RowId.name()));
        if (sampleRowId == null)
            throw new QueryUpdateServiceException("Failed to resolve sample rowId.");

        ExpMaterial seed = ExperimentService.get().getExpMaterial(sampleRowId);
        if (null == seed)
            return sampleRow;

        ExperimentServiceImpl.get().addParentsFields(seed, sampleRow, user, container);

        return sampleRow;
    }

    private void onSamplesChanged(List<Map<String, Object>> results, Map<Enum, Object> params, Container container, SampleTypeServiceImpl.SampleChangeType reason)
    {
        var tx = getSchema().getDbSchema().getScope().getCurrentTransaction();
        Pair<Set<Long>, Set<String>> parentKeys = getSampleParentsForRecalc(results);
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
                    fireSamplesChanged(reason);
                    if (finalUseBackgroundRecalc && !finalSkipRecalc)
                        handleRecalc(parentKeys.first, parentKeys.second, true, container);
                }, DbScope.CommitTaskOption.POSTCOMMIT);
            }
            else
                LOG.info("Skipping onSamplesChanged callback; transaction aborted");
        }
        else
        {
            fireSamplesChanged(reason);
        }
    }

    private void handleRecalc(Set<Long> rootRowIds, Set<String> parentNames, boolean useBackgroundThread, Container container)
    {
        Runnable runRecalc = () -> {
            try
            {
                if (_sampleType != null)
                {
                    var count = SampleTypeService.get().recomputeSampleTypeRollup(_sampleType, rootRowIds, parentNames, container);
                    if (count > 0)
                        SampleTypeServiceImpl.get().refreshSampleTypeMaterializedView(_sampleType, rollup);
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

    private void fireSamplesChanged(SampleTypeServiceImpl.SampleChangeType reason)
    {
        if (_sampleType != null)
            _sampleType.onSamplesChanged(getUser(), null, reason);
    }

    void audit(QueryService.AuditAction auditAction)
    {
        assert _sampleType != null || auditAction == QueryService.AuditAction.DELETE : "SampleType required for insert/update, but not required for read/delete";
        SampleTypeAuditProvider.SampleTypeAuditEvent event = new SampleTypeAuditProvider.SampleTypeAuditEvent(
                getContainer(), "Samples " + auditAction.getVerbPastTense() + " in: " + (_sampleType == null ? "<Materials>" : _sampleType.getName()));
        if (_sampleType != null)
        {
            event.setSourceLsid(_sampleType.getLSID());
            event.setSampleSetName(_sampleType.getName());
        }
        event.setInsertUpdateChoice(auditAction.toString().toLowerCase());
        AuditLogService.get().addEvent(getUser(), event);
    }

    // TODO: validate/compare functionality of CoerceDataIterator and loadRows()
    private static class PreTriggerDataIteratorBuilder implements DataIteratorBuilder
    {
        private static final int BATCH_SIZE = 100;

        final ExpSampleTypeImpl sampleType;
        final DataIteratorBuilder builder;
        final ExpMaterialTableImpl materialTable;
        final Container container;
        final User user;

        public PreTriggerDataIteratorBuilder(@NotNull ExpSampleTypeImpl sampleType, ExpMaterialTableImpl materialTable, DataIteratorBuilder in, Container container, User user)
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
            DataIterator di = builder.getDataIterator(context);
            if (di == null)
                return null; // can happen if context has errors

            boolean isMerge = context.getInsertOption() == InsertOption.MERGE;
            boolean isUpdate = context.getInsertOption() == InsertOption.UPDATE;

            // drop columns
            ColumnInfo containerColumn = materialTable.getColumn(materialTable.getContainerFieldKey());
            String containerFieldLabel = containerColumn.getLabel();
            var drop = new CaseInsensitiveHashSet();
            var keysCheck = new CaseInsensitiveHashSet();
            for (int i = 1; i <= di.getColumnCount(); i++)
            {
                String name = di.getColumnInfo(i).getName();
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
                    {
                        keysCheck.add(Name.name());
                        continue;
                    }
                    if (isDescriptionHeader(name))
                        continue;
                    if (ExperimentService.isInputOutputColumn(name))
                        continue;
                    if (isAliasHeader(name))
                        continue;
                    if (isExpMaterialColumn(SampleState, name))
                        continue;
                    if (isExpMaterialColumn(MaterialExpDate, name))
                        continue;
                    if (isExpMaterialColumn(StoredAmount, name))
                        continue;
                    if (isExpMaterialColumn(Units, name))
                        continue;
                    if (isContainerField && context.isCrossFolderImport() && !context.getInsertOption().updateOnly)
                        continue;
                    if (isExpMaterialColumn(RowId, name))
                    {
                        keysCheck.add(RowId.name());
                        if (isUpdate)
                            continue;

                        // While accepting RowId during merge is not our preferred behavior, we want to give users a way
                        // to opt-in to the old behavior where RowId is accepted and ignored.
                        if (isMerge && !OptionalFeatureService.get().isFeatureEnabled(ExperimentService.EXPERIMENTAL_FEATURE_ALLOW_ROW_ID_MERGE))
                        {
                            context.getErrors().addRowError(new ValidationException("RowId is not accepted when merging samples. Specify only the sample name instead.", RowId.name()));
                            return null;
                        }
                    }
                    if (isExpMaterialColumn(LSID, name))
                        keysCheck.add(LSID.name());
                    drop.add(name);
                }
            }

            if ((isMerge || isUpdate) && keysCheck.size() == 1 && keysCheck.contains(LSID.name()))
            {
                String message = String.format("LSID is no longer accepted as a key for sample %s. Specify a RowId or Name instead.", isMerge ? "merge" : "update");
                context.getErrors().addRowError(new ValidationException(message, LSID.name()));
                return null;
            }

            if (!drop.isEmpty())
                di = new DropColumnsDataIterator(di, drop);

            Map<String, Integer> columnNameMap = DataIteratorUtil.createColumnNameMap(di);
            if (isUpdate)
            {
                SimpleTranslator addAliquotedFrom = new SimpleTranslator(di, context);

                if (!columnNameMap.containsKey(AliquotedFromLSID.name()))
                    addAliquotedFrom.addNullColumn(AliquotedFromLSID.name(), JdbcType.VARCHAR);
                if (!columnNameMap.containsKey(RootMaterialRowId.name()))
                    addAliquotedFrom.addNullColumn(RootMaterialRowId.name(), JdbcType.INTEGER);
                addAliquotedFrom.addNullColumn(CURRENT_SAMPLE_STATUS_COLUMN_NAME, JdbcType.INTEGER);
                addAliquotedFrom.addColumn(new BaseColumnInfo(CpasType.fieldKey(), JdbcType.VARCHAR), new SimpleTranslator.ConstantColumn(sampleType.getLSID()));
                addAliquotedFrom.addColumn(new BaseColumnInfo(MaterialSourceId.fieldKey(), JdbcType.INTEGER), new SimpleTranslator.ConstantColumn(sampleType.getRowId()));
                addAliquotedFrom.addNullColumn(ROOT_RECOMPUTE_ROWID_COL, JdbcType.INTEGER);
                addAliquotedFrom.addNullColumn(PARENT_RECOMPUTE_NAME_COL, JdbcType.VARCHAR);
                addAliquotedFrom.selectAll();

                String keyColumnAlias = getKeyColumnAliasForUpdate(materialTable, columnNameMap);
                if (keyColumnAlias == null)
                {
                    context.getErrors().addRowError(new ValidationException(String.format(DUPLICATE_COLUMN_IN_DATA_ERROR, RowId.name())));
                    return null;
                }
                di = new SampleUpdateAddColumnsDataIterator(new CachingDataIterator(addAliquotedFrom), materialTable, sampleType.getRowId(), keyColumnAlias);

                di = new _SamplesCoerceDataIterator(di, context, sampleType, materialTable);
                context.setWithLookupRemapping(false);

                return LoggingDataIterator.wrap(di);
            }

            // CoerceDataIterator to handle the lookup/alternatekeys functionality of loadRows(),
            // TODO: check if this covers all the functionality, in particular how is alternateKeyCandidates used?
            DataIterator c = LoggingDataIterator.wrap(new _SamplesCoerceDataIterator(di, context, sampleType, materialTable));
            context.setWithLookupRemapping(false);
            SimpleTranslator addColumns = new SimpleTranslator(c, context);
            addColumns.setDebugName("add genId and other required columns");
            Set<String> idColNames = Sets.newCaseInsensitiveHashSet("genId");
            materialTable.getColumns().stream().filter(ColumnInfo::isUniqueIdField).forEach(columnInfo -> idColNames.add(columnInfo.getName()));
            addColumns.selectAll(idColNames);

            // auto gen a sequence number for genId - reserve BATCH_SIZE numbers at a time so we don't select the next sequence value for every row
            ColumnInfo genIdCol = new BaseColumnInfo(FieldKey.fromParts("genId"), JdbcType.INTEGER);
            final int batchSize = context.getInsertOption().batch ? BATCH_SIZE : 1;
            addColumns.addSequenceColumn(genIdCol, sampleType.getContainer(), ExpSampleTypeImpl.SEQUENCE_PREFIX, sampleType.getRowId(), batchSize, sampleType.getMinGenId());
            addColumns.addUniqueIdDbSequenceColumns(ContainerManager.getRoot(), materialTable);

            // recompute only add when AliquotedFrom column is not null
            if (columnNameMap.containsKey(ExpMaterial.ALIQUOTED_FROM_INPUT) || columnNameMap.containsKey(ExpMaterial.ALIQUOTED_FROM_INPUT_LABEL))
            {
                addColumns.addNullColumn(ROOT_RECOMPUTE_ROWID_COL, JdbcType.INTEGER);
                addColumns.addNullColumn(PARENT_RECOMPUTE_NAME_COL, JdbcType.VARCHAR);
            }

            di = LoggingDataIterator.wrap(addColumns);

            // Table Counters
            di = ExpDataIterators.CounterDataIteratorBuilder
                    .create(di, sampleType.getContainer(), materialTable, ExpSampleType.SEQUENCE_PREFIX, sampleType.getRowId())
                    .getDataIterator(context);

            // sampleset.createSampleNames() + generate lsid
            // TODO: does not handle insertIgnore
            DataIterator names = new _GenerateNamesDataIterator(sampleType, container, user, DataIteratorUtil.wrapMap(di, false), context, batchSize);
            return LoggingDataIterator.wrap(names);
        }

        private static boolean isReservedHeader(String name)
        {
            if (isNameHeader(name) || isDescriptionHeader(name) || isCommentHeader(name) || "CpasType".equalsIgnoreCase(name) || isAliasHeader(name))
                return true;
            if (ExperimentService.isInputOutputColumn(name))
                return true;
            for (ExpMaterialTable.Column column : values())
            {
                if (isExpMaterialColumn(column, name))
                    return true;
            }
            return isAliquotRollupHeader(name);
        }

        private static boolean isExpMaterialColumn(ExpMaterialTable.Column column, String name)
        {
            return column.name().equalsIgnoreCase(name) || (column.label() != null && column.label().equalsIgnoreCase(name));
        }

        private static boolean isNameHeader(String name)
        {
            return isExpMaterialColumn(Name, name);
        }

        private static boolean isDescriptionHeader(String name)
        {
            return isExpMaterialColumn(Description, name);
        }

        private static boolean isCommentHeader(String name)
        {
            return isExpMaterialColumn(Flag, name) || "Comment".equalsIgnoreCase(name);
        }

        private static boolean isAliasHeader(String name)
        {
            return isExpMaterialColumn(Alias, name);
        }

        private static boolean isAliquotRollupHeader(String name)
        {
            Set<String> rollupFields = new CaseInsensitiveHashSet();
            rollupFields.addAll(ALIQUOT_ROLLUP_FIELDS.keySet().stream().map(ExpMaterialTable.Column::name).toList());
            rollupFields.addAll(ALIQUOT_ROLLUP_FIELD_LABELS);
            return rollupFields.contains(name);
        }
    }

    static class _GenerateNamesDataIterator extends SimpleTranslator
    {
        final boolean _allowUserSpecifiedNames;        // whether manual names specification is allowed or only name expression generation
        final RemapCache _cache;
        final Container _container;
        final List<Supplier<Map<String, Object>>> _extraPropsFns;
        final SampleNameGeneratorState _nameState;
        final Lsid.LsidBuilder lsidBuilder;
        final ExpSampleTypeImpl _sampleType;
        final User _user;

        Set<String> _existingNames = null;
        String generatedName = null;

        _GenerateNamesDataIterator(
            @NotNull ExpSampleTypeImpl sampleType,
            Container container,
            User user,
            MapDataIterator source,
            DataIteratorContext context,
            int batchSize
        )
        {
            super(source, context);
            _allowUserSpecifiedNames = NameExpressionOptionService.get().getAllowUserSpecificNamesValue(container);
            _cache = new RemapCache(!context.getConfigParameterBoolean(SkipBulkRemapCache));
            _container = container;
            _extraPropsFns = new ArrayList<>();
            _sampleType = sampleType;
            _user = user;

            try
            {
                Map<String, String> importAliasMap = sampleType.getImportAliasesIncludingAliquot();
                _extraPropsFns.add(() -> Map.of(PARENT_IMPORT_ALIAS_MAP_PROP, importAliasMap));
            }
            catch (IOException e)
            {
                // do nothing
            }

            _extraPropsFns.add(() -> {
                if (_container == null)
                    return Collections.emptyMap();
                return Map.of(NameExpressionOptionService.FOLDER_PREFIX_TOKEN, StringUtils.trimToEmpty(NameExpressionOptionService.get().getExpressionPrefix(_container)));
            });

            boolean skipDuplicateCheck = context.getConfigParameterBoolean(SkipMaxSampleCounterFunction);
            _nameState = sampleType.getNameGenState(skipDuplicateCheck, true, _container, user);
            lsidBuilder = sampleType.generateSampleLSID();

            selectAll(CaseInsensitiveHashSet.of(Name.name(), LSID.name(), RootMaterialRowId.name()));

            addColumn(new BaseColumnInfo(Name.fieldKey(), JdbcType.VARCHAR), (Supplier<String>)() -> generatedName);

            DbSequence lsidDbSeq = sampleType.getSampleLsidDbSeq(batchSize, sampleType.getContainer());
            addColumn(new BaseColumnInfo(LSID.name(), JdbcType.VARCHAR), (Supplier<String>) () -> lsidBuilder.setObjectId(String.valueOf(lsidDbSeq.next())).toString());

            addColumn(new BaseColumnInfo(CpasType.fieldKey(), JdbcType.VARCHAR), new SimpleTranslator.ConstantColumn(sampleType.getLSID()));
            addColumn(new BaseColumnInfo(MaterialSourceId.fieldKey(), JdbcType.INTEGER), new SimpleTranslator.ConstantColumn(sampleType.getRowId()));
        }

        @Override
        protected void processNextInput()
        {
            Map<String, Object> map = new CaseInsensitiveHashMap<>(((MapDataIterator)getInput()).getMap());

            boolean isAliquot = isAliquotRow(map);

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

                generatedName = _nameState.nextName(map, _extraPropsFns);
            }

            catch (NameGenerator.DuplicateNameException dup)
            {
                addRowError(dup.getMessage());
            }
            catch (NameGenerator.NameGenerationException e)
            {
                // Failed to generate a name due to some part of the expression not in the row
                // Issue 53963: Cross-sample-type import gives incorrect row number in message
                String rowText = _context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.ProcessingPartition) ? "" : " on row " + e.getRowNumber();
                if (isAliquot)
                    addRowError("Failed to generate name for aliquot" + rowText + " using aliquot naming pattern " + _sampleType.getAliquotNameExpression() + ". Check the syntax of the aliquot naming pattern and the data values for the aliquot.");
                else if (_sampleType.hasNameExpression())
                    addRowError("Failed to generate name for sample" + rowText + " using naming pattern " + _sampleType.getNameExpression() + ". Check the syntax of the naming pattern and the data values for the sample.");
                else if (_sampleType.hasNameAsIdCol())
                    addRowError("SampleID or Name is required for sample" + rowText + ".");
                else
                    addRowError("All id columns are required for sample" + rowText + ".");
            }
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            // calls processNextInput()
            boolean next = super.next();
            if (!next)
            {
                if (null != _nameState)
                    _nameState.cleanUp();
            }
            return next;
        }

        @Override
        public void close() throws IOException
        {
            super.close();
            if (null != _nameState)
                _nameState.close();
        }

        private boolean rowExists(String name)
        {
            if (_existingNames == null)
            {
                _existingNames = new HashSet<>();
                SamplesSchema schema = new SamplesSchema(User.getSearchUser(), _sampleType.getContainer());
                TableSelector ts = new TableSelector(schema.getTable(_sampleType, null), Collections.singleton("Name")).setMaxRows(1_000_000);
                ts.fillSet(_existingNames);
            }
            return _existingNames.contains(name);
        }
    }

    static class _SamplesCoerceDataIterator extends SimpleTranslator
    {
        private static final String INVALID_ALIQUOT_PROPERTY = "An aliquot-specific property [%1$s] value has been ignored for a non-aliquot sample.";
        private static final String INVALID_NON_ALIQUOT_PROPERTY = "A sample property [%1$s] value has been ignored for an aliquot.";

        private final ExpSampleTypeImpl _sampleType;
        private final Unit _sampleTypeBaseUnit;

        public _SamplesCoerceDataIterator(DataIterator source, DataIteratorContext context, ExpSampleTypeImpl sampleType, ExpMaterialTableImpl materialTable)
        {
            super(source, context);
            _sampleType = sampleType;
            _sampleTypeBaseUnit = _sampleType.getBaseUnit();
            setDebugName("Coerce before trigger script - samples");
            init(materialTable, context.getInsertOption().useImportAliases, !context.getInsertOption().updateOnly);
        }

        void init(TableInfo target, boolean useImportAliases, boolean initRollupCounts)
        {
            Map<String,ColumnInfo> targetMap = DataIteratorUtil.createTableMap(target, useImportAliases);
            Set<String> amountImportAliasSet = ImportAliasable.Helper.createImportSet(target.getColumn(StoredAmount.name()));
            Set<String> unitsImportAliasSet = ImportAliasable.Helper.createImportSet(target.getColumn(Units.name()));
            DataIterator di = getInput();
            int count = di.getColumnCount();

            Map<String, Boolean> scopedFields = new CaseInsensitiveHashMap<>(); // fields that are either aliquot-specific, or parent meta
            for (DomainProperty dp : _sampleType.getDomain().getProperties())
            {
                if (!ExpSchema.DerivationDataScopeType.All.name().equalsIgnoreCase(dp.getDerivationDataScope()))
                    scopedFields.put(dp.getName(), ExpSchema.DerivationDataScopeType.ChildOnly.name().equalsIgnoreCase(dp.getDerivationDataScope()));
            }

            int aliquotedFromDataColInd = -1;
            int unitDataColInd = -1;
            int amountDataColInd = -1;
            for (int i = 1; i <= count && (aliquotedFromDataColInd < 0 || unitDataColInd < 0 || amountDataColInd < 0); i++)
            {
                ColumnInfo from = di.getColumnInfo(i);
                if (from != null)
                {
                    if (isAliquotedFromColName(from.getName()))
                        aliquotedFromDataColInd = i;
                    else if (unitsImportAliasSet.contains(from.getName()))
                        unitDataColInd = i;
                    else if (amountImportAliasSet.contains(from.getName()))
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
                    String ignoredMetaPropValue = String.format(INVALID_NON_ALIQUOT_PROPERTY, name);
                    if (to.getPropertyType() == PropertyType.ATTACHMENT || to.getPropertyType() == PropertyType.FILE_LINK)
                    {
                        if (isScopedField)
                        {
                            ColumnInfo clone = new BaseColumnInfo(to);
                            addColumn(clone, new DerivationScopedColumn(i, aliquotedFromDataColInd, scopedFields.get(name), ignoredAliquotPropValue, ignoredMetaPropValue));
                        }
                        else
                            addColumn(to, i);
                    }
                    else if (to.isMultiValued() || to.getFk() instanceof MultiValuedForeignKey)
                    {
                        // pass-through multi-value columns -- converting will stringify a collection
                        if (isScopedField)
                        {
                            var col = new BaseColumnInfo(getInput().getColumnInfo(i));
                            col.setName(name);
                            addColumn(col, new DerivationScopedColumn(i, aliquotedFromDataColInd, scopedFields.get(name), ignoredAliquotPropValue, ignoredMetaPropValue));
                        }
                        else
                            addColumn(to.getName(), i);
                    }
                    else if (Units.name().equalsIgnoreCase(name))
                    {
                        addColumn(PROVIDED_DATA_PREFIX + Units.name(), i);
                        addColumn(to, new SampleUnitsConvertColumn(name, i, to.getJdbcType(), amountDataColInd, !_context.getInsertOption().allowUpdate));
                    }
                    else if (StoredAmount.name().equalsIgnoreCase(name))
                    {
                        addColumn(PROVIDED_DATA_PREFIX + StoredAmount.name(), i);
                        addColumn(to, new SampleAmountConvertColumn(name, i, to.getJdbcType(), unitDataColInd));
                    }
                    else
                    {
                        if (isScopedField)
                            _addConvertColumn(name, i, to.getJdbcType(), to.getPropertyType(), to.getFk(), aliquotedFromDataColInd, scopedFields.get(name));
                        else
                            addConvertColumn(to.getName(), i, to.getJdbcType(), to.getPropertyType(), to.getFk(), to.getRemapMissingBehavior(), true);
                    }
                }
                else
                {
                    if (aliquotedFromDataColInd == i && _context.getInsertOption().mergeRows && !_context.getConfigParameterBoolean(SampleTypeService.ConfigParameters.DeferAliquotRuns))
                    {
                        addColumn(AliquotedFromLSID.name(), i); // temporarily populate sample name as lsid for merge, used to differentiate insert vs update for merge
                    }

                    addColumn(i);
                }
            }

            if (initRollupCounts)
            {
                for (Map.Entry<ExpMaterialTable.Column, JdbcType> entry : ALIQUOT_ROLLUP_FIELDS.entrySet())
                {
                    ExpMaterialTable.Column field = entry.getKey();
                    JdbcType jdbcType = entry.getValue();
                    var col = new BaseColumnInfo(field.fieldKey(), jdbcType);

                    addColumn(col, new AliquotRollupConvertColumn(field, jdbcType, aliquotedFromDataColInd));
                }
            }
        }

        private boolean isAliquotedFromColName(String fromCol)
        {
            if (_context.getInsertOption().updateOnly)
                return AliquotedFromLSID.name().equalsIgnoreCase(fromCol);

            return ExperimentService.isAliquotedFromColumn(fromCol);
        }

        private void _addConvertColumn(String name, int fromIndex, JdbcType toType, @Nullable PropertyType pt, ForeignKey toFk, int derivationDataColInd, boolean isAliquotField)
        {
            var col = new BaseColumnInfo(getInput().getColumnInfo(fromIndex));
            col.setName(name);
            col.setJdbcType(toType);
            if (null != pt)
                col.setPropertyType(pt);
            if (toFk != null)
                col.setFk(toFk);

            _addConvertColumn(col, fromIndex, derivationDataColInd, isAliquotField);
        }

        private void _addConvertColumn(ColumnInfo col, int fromIndex, int derivationDataColInd, boolean isAliquotField)
        {
            SimpleConvertColumn c = createConvertColumn(col, fromIndex, RemapMissingBehavior.Error);
            c = new DerivationScopedConvertColumn(fromIndex, c, derivationDataColInd, isAliquotField, String.format(INVALID_ALIQUOT_PROPERTY, col.getName()), String.format(INVALID_NON_ALIQUOT_PROPERTY, col.getName()));

            addColumn(col, c);
        }

        protected class SampleUnitsConvertColumn extends SimpleTranslator.SimpleConvertColumn
        {
            final int _storedAmountColInd;
            final boolean _isInsert;

            public SampleUnitsConvertColumn(String fieldName, int indexFrom, @Nullable JdbcType to, int storedAmountIdx, boolean isInsert)
            {
                super(fieldName, indexFrom, to, null, true);
                _storedAmountColInd = storedAmountIdx;
                _isInsert = isInsert;
            }

            // This should return the base unit if we have one for the sample type since we are storing all data in the base unit
            public static Object getValue(Object o, Object amountObj, boolean haveAmountCol, Unit baseUnit, String sampleTypeName)
            {
                if (o == null || ((o instanceof String) && ((String) o).isEmpty()))
                {
                    return null;
                }

                // when there's a units value but no amount column, this is an error
                if (!haveAmountCol)
                    throw new ConversionExceptionWithMessage(MISSING_AMOUNT_ERROR_MESSAGE);

                // When an amount column is present but no amount value is provided, this is an error
                if (amountObj == null || ((amountObj instanceof String) && ((String) amountObj).isEmpty()))
                    throw new ConversionExceptionWithMessage(String.format(UNPROVIDED_VALUE_ERROR_MESSAGE_PATTERN,  StoredAmount.label(), Units.name(), o));


                Unit validatedUnit = SampleTypeService.get().getValidatedUnit(o, baseUnit, sampleTypeName);
                if (validatedUnit != null && baseUnit != null && KindOfQuantity.Count == validatedUnit.getKindOfQuantity() && validatedUnit.getValue() == baseUnit.getValue())
                {
                    // if both units are 'count' units and have the same value, prefer returning provided unit name
                    return validatedUnit.name();
                }
                // if there's a base unit, return the base unit name otherwise return the name of the given unit
                return validatedUnit == null ? null : baseUnit != null ? baseUnit.name() : validatedUnit.name();
            }

            @Override
            protected Object convert(Object o)
            {
                return getValue(o, _storedAmountColInd == -1 ? null : _data.get(_storedAmountColInd), _storedAmountColInd != -1, _sampleTypeBaseUnit, _sampleType.getName());
            }
        }

        protected class SampleAmountConvertColumn extends SimpleTranslator.SimpleConvertColumn
        {
            final int _unitsColInd;
            public SampleAmountConvertColumn(String fieldName, int indexFrom, @Nullable JdbcType to, int unitsColInd)
            {
                // should convert from the amount in the given unit into the sample type base unit, if we have one
                super(fieldName, indexFrom, to, _sampleTypeBaseUnit, true);
                _unitsColInd = unitsColInd;
            }

            // This should return a Number in the base units of the sample type.
            public static Object getValue(Object amountObj, boolean hasUnitsCol, Object unitsObj, Unit displayUnit, @Nullable String sampleTypeName)
            {
                if (amountObj == null || ((amountObj instanceof String) && ((String) amountObj).trim().isEmpty()))
                    return null;

                // When there is an amount value, if there isn't a units column, this is an error.
                if (!hasUnitsCol)
                    throw new ConversionExceptionWithMessage(MISSING_UNITS_ERROR_MESSAGE);

                // Have a units column, but no units value
                if (unitsObj == null || ((unitsObj instanceof String) && ((String) unitsObj).trim().isEmpty()))
                {
                    // N.B. It could be that we want to support users providing the amount and unit together in the amount column (e.g., 7g)
                    // To support that we could try Quantity.convert here. Leaving this out for now, though.
                    throw new ConversionExceptionWithMessage(String.format(UNPROVIDED_VALUE_ERROR_MESSAGE_PATTERN , Units.name(), StoredAmount.label(), amountObj));
                }

                Unit unit = SampleTypeService.get().getValidatedUnit(unitsObj, displayUnit, sampleTypeName);

                // Should always be non-null at this point.
                if (unit != null && displayUnit != null)
                {
                    Double quantityValue;
                    if (amountObj instanceof Number)
                        quantityValue = Quantity.of((Number) amountObj, unit).doubleValue();
                    else if (amountObj instanceof String amountStr)
                    {
                        if (StringUtils.isEmpty(amountStr.trim()))
                            return null;
                        // If the string value includes the unit (e.g., "7ml"), convert will handle that and should
                        // ignore the unit value. If the string amount does not have the unit (e.g., "7"), we use either the
                        // unit from the unit column or the sample type display unit. doubleValue returns the amount in the base unit.
                        quantityValue = Quantity.convert(amountObj, unit).doubleValue();
                    }
                    else
                        throw new ConversionException("Cannot convert " + amountObj + " to " + unit);

                    // Issue 53979: check for non-finite values
                    if (!Double.isFinite(quantityValue))
                        throw new ConversionException("Could not parse a finite number from '" + amountObj + "'.");

                    return quantityValue;
                }
                return amountObj; // have no display unit, so we'll do no conversions
            }

            @Override
            protected Object convert(Object amountObj)
            {
                return getValue(amountObj, _unitsColInd != -1, _unitsColInd == -1 ? null : _data.get(_unitsColInd), _sampleTypeBaseUnit, _sampleType.getName());
            }
        }

        protected class AliquotRollupConvertColumn extends SimpleConvertColumn
        {
            final int aliquotedFromColInd;

            public AliquotRollupConvertColumn(ExpMaterialTable.Column field, @Nullable JdbcType to, int aliquotedFromColInd)
            {
                super(field.name(),0, to, field.hasUnit() ? _sampleTypeBaseUnit : null, true);
                this.aliquotedFromColInd = aliquotedFromColInd;
            }

            @Override
            protected Object convert(Object o)
            {
                if (aliquotedFromColInd < 0)
                    return 0; // if AliquotedFrom column absent, is root, initialize rollup count/amount to 0

                Object aliquotedFrom = _data.get(aliquotedFromColInd);
                String aliquotParentName = null;
                if (aliquotedFrom instanceof String)
                {
                    aliquotParentName = StringUtilsLabKey.unquoteString((String) aliquotedFrom);
                }
                else if (aliquotedFrom instanceof Number)
                {
                    aliquotParentName = aliquotedFrom.toString();
                }
                if (StringUtils.isEmpty(aliquotParentName)) // if AliquotedFrom is empty, is root
                    return 0;

                return null; // for aliquot, initialize rollup count/amount to null
            }
        }
    }

    private static boolean isAliquotRow(Map<String, Object> map, String aliquotedFromColName)
    {
        String aliquotedFrom = null;
        Object aliquotedFromObj = map.get(aliquotedFromColName);
        if (aliquotedFromObj == null && map.containsKey(ColumnInfo.labelFromName(ExpMaterial.ALIQUOTED_FROM_INPUT)))
            aliquotedFromObj = map.get(ColumnInfo.labelFromName(ExpMaterial.ALIQUOTED_FROM_INPUT));
        if (aliquotedFromObj != null)
        {
            if (aliquotedFromObj instanceof String)
            {
                // Issue 45563: We need the AliquotedFrom name to be quoted so we can properly find the parent,
                // but we don't want to include the quotes in the name we generate using AliquotedFrom
                aliquotedFrom = StringUtilsLabKey.unquoteString((String) aliquotedFromObj).trim();
                if (!StringUtils.isEmpty(aliquotedFrom))
                    map.put(ExpMaterial.ALIQUOTED_FROM_INPUT, aliquotedFrom);
            }
            else if (aliquotedFromObj instanceof Number)
            {
                aliquotedFrom = aliquotedFromObj.toString();
            }
        }

        return !StringUtils.isEmpty(aliquotedFrom);
    }

    private static boolean isAliquotRow(Map<String, Object> map)
    {
        return isAliquotRow(map, ExpMaterial.ALIQUOTED_FROM_INPUT) || isAliquotRow(map, ExpMaterial.ALIQUOTED_FROM_INPUT_LABEL);
    }

    public static class SampleNameGeneratorState extends NameGeneratorState
    {
        private final NameGenerator aliquotNameGenerator;

        public SampleNameGeneratorState(@NotNull NameGenerator nameGenerator, boolean incrementSampleCounts, @Nullable NameGenerator aliquotNameGenerator)
        {
            super(nameGenerator, incrementSampleCounts, nameGenerator.getExpressionSummary() == null ? null : nameGenerator.getExpressionSummary().sampleSummary());
            this.aliquotNameGenerator = aliquotNameGenerator;
        }

        public String nextName(Map<String, Object> map, @Nullable List<Supplier<Map<String, Object>>> _extraPropsFns) throws NameGenerator.NameGenerationException
        {
            return nextName(map, null, null, _extraPropsFns);
        }

        public String nextName(Map<String, Object> map,
                               @Nullable Set<ExpData> parentDatas,
                               @Nullable Set<ExpMaterial> parentSamples,
                               @Nullable List<Supplier<Map<String, Object>>> _extraPropsFns) throws NameGenerator.NameGenerationException
        {
            boolean isAliquot = isAliquotRow(map);

            String generatedName = null;
            if (isAliquot && aliquotNameGenerator != null)
                generatedName = nextName(map, parentDatas, parentSamples, _extraPropsFns, aliquotNameGenerator);
            else if (!isAliquot)
                generatedName = nextName(map, parentDatas, parentSamples, _extraPropsFns, null);

            return generatedName;
        }
    }
}
