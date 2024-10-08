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
package org.labkey.experiment;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CounterDefinition;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RemapCache;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.data.validator.ColumnValidator;
import org.labkey.api.data.validator.RequiredValidator;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.ErrorIterator;
import org.labkey.api.dataiterator.ExistingRecordDataIterator;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.dataiterator.Pump;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.StandardDataIteratorBuilder;
import org.labkey.api.dataiterator.TableInsertDataIteratorBuilder;
import org.labkey.api.dataiterator.ValidatorIterator;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.exp.api.ExpLineage;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpLineageService;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.api.SimpleRunRecord;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.AbstractExpSchema;
import org.labkey.api.exp.query.DataClassUserSchema;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.SampleStatusService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FileColumnValueMapper;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.experiment.api.AliasInsertHelper;
import org.labkey.experiment.api.ExpDataClassDataTableImpl;
import org.labkey.experiment.api.ExpMaterialTableImpl;
import org.labkey.experiment.api.ExpSampleTypeImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.SampleTypeServiceImpl;
import org.labkey.experiment.api.SampleTypeUpdateServiceDI;
import org.labkey.experiment.controllers.exp.RunInputOutputBean;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.labkey.api.data.CompareType.IN;
import static org.labkey.api.dataiterator.ExistingRecordDataIterator.EXISTING_RECORD_COLUMN_NAME;
import static org.labkey.api.dataiterator.SampleUpdateAddColumnsDataIterator.CURRENT_SAMPLE_STATUS_COLUMN_NAME;
import static org.labkey.api.exp.api.ExpData.DATA_INPUTS_PREFIX_LC;
import static org.labkey.api.exp.api.ExpData.DATA_INPUT_PARENT;
import static org.labkey.api.exp.api.ExpMaterial.ALIQUOTED_FROM_INPUT;
import static org.labkey.api.exp.api.ExpMaterial.MATERIAL_INPUTS_PREFIX_LC;
import static org.labkey.api.exp.api.ExpMaterial.MATERIAL_INPUT_PARENT;
import static org.labkey.api.exp.api.ExpRunItem.INPUTS_PREFIX_LC;
import static org.labkey.api.exp.api.ExperimentService.ALIASCOLUMNALIAS;
import static org.labkey.api.exp.api.ExperimentService.QueryOptions.SkipBulkRemapCache;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.AliquotCount;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.AliquotVolume;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.AliquotedFromLSID;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.AvailableAliquotCount;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.AvailableAliquotVolume;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.Folder;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.MaterialSourceId;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.Name;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.RootMaterialRowId;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.RowId;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.SampleState;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.StoredAmount;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.Units;
import static org.labkey.api.query.AbstractQueryImportAction.configureLoader;
import static org.labkey.experiment.api.SampleTypeServiceImpl.SampleChangeType.insert;
import static org.labkey.experiment.api.SampleTypeUpdateServiceDI.PARENT_RECOMPUTE_NAME_SET;
import static org.labkey.experiment.api.SampleTypeUpdateServiceDI.ROOT_RECOMPUTE_ROWID_COL;
import static org.labkey.experiment.api.SampleTypeUpdateServiceDI.PARENT_RECOMPUTE_NAME_COL;
import static org.labkey.experiment.api.SampleTypeUpdateServiceDI.ROOT_RECOMPUTE_ROWID_SET;


public class ExpDataIterators
{
    private static final Logger LOG = LogHelper.getLogger(ExpDataIterators.class, "Experiment data-related data iterators");

    public static class CounterDataIteratorBuilder implements DataIteratorBuilder
    {
        private final DataIteratorBuilder _in;
        private final Container _container;
        private final AbstractTableInfo _expTable;
        private final String _sequencePrefix;
        private final int _id;

        public static DataIteratorBuilder create(@NotNull DataIteratorBuilder in, Container container,
                                                 AbstractTableInfo expTable, String sequencePrefix, int sequenceId)
        {
            if (expTable.getCounterDefinitions().isEmpty())
                return in;

            return new CounterDataIteratorBuilder(in, container, expTable, sequencePrefix, sequenceId);
        }

        public CounterDataIteratorBuilder(@NotNull DataIteratorBuilder in, Container container,
                                          AbstractTableInfo expTable, String sequencePrefix, int sequenceId)
        {
            _in = in;
            _container = container;
            _expTable = expTable;
            _sequencePrefix = sequencePrefix;
            _id = sequenceId;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _in.getDataIterator(context);

            SimpleTranslator counterTranslator = new SimpleTranslator(pre, context);
            counterTranslator.setDebugName("Counter Def");
            Set<String> skipColumns = new CaseInsensitiveHashSet();
            Map<String, Integer> columnNameMap = DataIteratorUtil.createColumnNameMap(pre);

            for (CounterDefinition counterDefinition : _expTable.getCounterDefinitions())
            {
                Set<String> attachedColumnNames = counterDefinition.getAttachedColumnNames();
                skipColumns.addAll(attachedColumnNames);

                // validate we have all the paired columns
                List<Integer> pairedIndexes = new ArrayList<>();
                for (String pairedColumnName : counterDefinition.getPairedColumnNames())
                {
                    Integer i = columnNameMap.get(pairedColumnName);
                    if (i == null)
                    {
                        // immediately return error iterator tied to the input DataIterator instead of counterTranslator
                        ValidationException setupError = new ValidationException();
                        setupError.addGlobalError("Paired column '" + pairedColumnName + "' is required for counter '" + counterDefinition.getCounterName() + "'");
                        return ErrorIterator.wrap(pre, context, true, setupError);
                    }
                    else
                    {
                        pairedIndexes.add(i);
                    }
                }

                // add a sequence column for each of the attached columns
                for (String columnName : attachedColumnNames)
                {
                    Integer i = columnNameMap.get(columnName);
                    ColumnInfo column;
                    if (null != i)
                    {
                        column = pre.getColumnInfo(i);
                        skipColumns.add(columnName);
                    }
                    else
                    {
                        column = _expTable.getColumn(columnName);
                    }

                    counterTranslator.addPairedSequenceColumn(column, i, _container, counterDefinition, pairedIndexes, _sequencePrefix, _id, 100);
                }
            }

            counterTranslator.selectAll(skipColumns);

            return LoggingDataIterator.wrap(counterTranslator);
        }
    }

    public static class ExpMaterialValidatorIterator extends ValidatorIterator
    {
        private final Integer _aliquotedFromColIdx;

        public ExpMaterialValidatorIterator(DataIterator data, DataIteratorContext context, Container c, User user)
        {
            super(data, context, c, user);
            boolean isUpdateOnly = context.getInsertOption().updateOnly;

            Map<String, Integer> columnNameMap = DataIteratorUtil.createColumnNameMap(data);
            if (!isUpdateOnly && columnNameMap.containsKey(ExpMaterial.ALIQUOTED_FROM_INPUT))
                _aliquotedFromColIdx = columnNameMap.get(ExpMaterial.ALIQUOTED_FROM_INPUT);
            else if (isUpdateOnly && columnNameMap.containsKey(AliquotedFromLSID.name()))
                _aliquotedFromColIdx = columnNameMap.get(AliquotedFromLSID.name());
            else
                _aliquotedFromColIdx = -1;
        }

        @Override
        protected String validate(ColumnValidator v, int rowNum, Object value, DataIterator data)
        {
            if (!(v instanceof RequiredValidator) || _aliquotedFromColIdx < 0)
                return super.validate(v, rowNum, value, data);

            String aliquotedFromValue = null;
            Object aliquotedFromObj = data.get(_aliquotedFromColIdx);
            if (aliquotedFromObj != null)
            {
                if (aliquotedFromObj instanceof String)
                {
                    aliquotedFromValue = (String) aliquotedFromObj;
                }
                else if (aliquotedFromObj instanceof Number)
                {
                    aliquotedFromValue = aliquotedFromObj.toString();
                }
            }

            // skip required field check for aliquots since aliquots properties are inherited
            if (!StringUtils.isEmpty(aliquotedFromValue))
                return null;

            return v.validate(rowNum, value);
        }
    }

    public static class ExpMaterialDataIteratorBuilder extends StandardDataIteratorBuilder
    {
        public ExpMaterialDataIteratorBuilder(TableInfo target, @NotNull DataIteratorBuilder in, @Nullable Container c, @NotNull User user)
        {
            super(target, in, c, user);
        }

        @Override
        protected ValidatorIterator getValidatorIterator(DataIterator validateInput, DataIteratorContext context, Map<String, TranslateHelper> translateHelperMap, Container c, User user)
        {
            ExpMaterialValidatorIterator validate = new ExpMaterialValidatorIterator(LoggingDataIterator.wrap(validateInput), context, c, user);
            validate.setDebugName("ExpMaterialDataIteratorBuilder validate");
            return validate;
        }
    }

    public static class AliquotRollupDataIteratorBuilder implements DataIteratorBuilder
    {
        private final DataIteratorBuilder _in;
        private final Container _container;

        public AliquotRollupDataIteratorBuilder(@NotNull DataIteratorBuilder in, Container container)
        {
            _in = in;
            _container = container;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _in.getDataIterator(context);
            return LoggingDataIterator.wrap(new AliquotRollupDataIterator(pre, context, _container));
        }
    }

    public static class AliquotRollupDataIterator extends WrapperDataIterator
    {
        private final Integer _storedAmountCol;
        private final Integer _unitsCol;
        private final Integer _sampleStateCol;
        private final Integer _aliquotedFromCol;
        private final Integer _rootMaterialRowIdCol;
        private final Integer _rootIdToRecomputeCol;
        private final Integer _parentNameToRecomputeCol;
        private final boolean _isInsert;
        private final boolean _isUpdate;
        private final List<Integer> availableSampleStatuses = new ArrayList<>();

        protected AliquotRollupDataIterator(DataIterator di, DataIteratorContext context, Container container)
        {
            super(di);
            _isInsert = !context.getInsertOption().allowUpdate;
            _isUpdate = context.getInsertOption().updateOnly;
            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _storedAmountCol = map.get(StoredAmount.name());
            _unitsCol = map.get(Units.name());
            _sampleStateCol = map.get(SampleState.name());
            _aliquotedFromCol = map.get(ExpMaterial.ALIQUOTED_FROM_INPUT);
            _rootMaterialRowIdCol = map.get(RootMaterialRowId.name());
            _rootIdToRecomputeCol = map.get(ROOT_RECOMPUTE_ROWID_COL);
            _parentNameToRecomputeCol = map.get(PARENT_RECOMPUTE_NAME_COL);

            if (SampleStatusService.get().supportsSampleStatus())
            {
                for (DataState state: SampleStatusService.get().getAllProjectStates(container))
                {
                    if (ExpSchema.SampleStateType.Available.name().equals(state.getStateType()))
                        availableSampleStatuses.add(state.getRowId());
                }
            }
        }

        private Pair<Boolean, Integer> determineRecalcFromExistingRecord(int i, Map<String, Object> existingMap)
        {
            if (_storedAmountCol == null && _unitsCol == null && _sampleStateCol == null)
                return null; // update/merge existing will only trigger recompute if stored amount or unit or status is updated)

            if (i == _parentNameToRecomputeCol) // only return lsid if existing map not null
                return null;

            Integer rootAliquot = (Integer) existingMap.get(RootMaterialRowId.name());
            Double existingAmount = (Double) existingMap.get(StoredAmount.name());
            String existingUnits = (String) existingMap.get(Units.name());
            Integer existingState = (Integer) existingMap.get(SampleState.name());

            if (!availableSampleStatuses.isEmpty())
            {
                Integer newState = _sampleStateCol == null ? null : (Integer) get(_sampleStateCol);
                if (SampleTypeUpdateServiceDI.isAliquotStatusChangeNeedRecalc(availableSampleStatuses, existingState, newState))
                    return new Pair<>(true, rootAliquot);
            }

            Double newAmount = _storedAmountCol == null ? null : (Double) get(_storedAmountCol);
            String newUnits = _unitsCol == null ? null : (String) get(_unitsCol);

            boolean amountChanged = !(Objects.equals(existingAmount, newAmount) && Objects.equals(existingUnits, newUnits));
            if (!amountChanged && (_storedAmountCol == null || _unitsCol == null))
            {
                if (_storedAmountCol == null && !Objects.equals(existingUnits, newUnits))
                    amountChanged = true;
                if (_unitsCol == null && !Objects.equals(existingAmount, newAmount))
                    amountChanged = true;
            }

            return amountChanged ? new Pair<>(true, rootAliquot) : null;
        }

        @Override
        public Object get(int i)
        {
            if (i == _rootIdToRecomputeCol || i == _parentNameToRecomputeCol)
            {
                if (_isInsert)
                {
                    if (i == _parentNameToRecomputeCol && _aliquotedFromCol != null)
                        return get(_aliquotedFromCol); // recompute parent when new aliquot is created
                    return null;
                }

                if (_isUpdate)
                {
                    if (_storedAmountCol == null && _unitsCol == null && _sampleStateCol == null)
                        return null; // update will only trigger recompute if stored amount or unit or status is updated)
                }

                Map<String, Object> existingMap = getExistingRecord();
                if (existingMap != null && !existingMap.isEmpty())
                {
                    Pair<Boolean, Integer> needRecac = determineRecalcFromExistingRecord(i, existingMap);
                    if (needRecac == null)
                        return null;
                    if (needRecac.first && needRecac.second != null)
                        return needRecac.second;
                }

                // without existing record, or if existing record is missing root information, we have to be conservative and assume this is a new aliquot, or a amount/status update
                // merge: either a new record, or detailed audit disabled
                if (!_isUpdate)
                {
                    if (i == _parentNameToRecomputeCol && _aliquotedFromCol != null)
                        return get(_aliquotedFromCol); // recompute parent when new aliquot is created
                    return null;
                }
                // update only, return rootMaterialRowId that's queried from SampleUpdateAliquotedFromDataIterator
                if (_rootMaterialRowIdCol != null && get(_rootMaterialRowIdCol) != null && i == _rootIdToRecomputeCol)
                    return get(_rootMaterialRowIdCol);

                return null;
            }

            return super.get(i);
        }
    }

    /**
     * Data iterator to handle aliases
     */
    public static class AliasDataIteratorBuilder implements DataIteratorBuilder
    {
        private final DataIteratorBuilder _in;
        private final Container _container;
        private final User _user;
        private final TableInfo _expAliasTable;

        public AliasDataIteratorBuilder(@NotNull DataIteratorBuilder in, Container container, User user, TableInfo expTable)
        {
            _in = in;
            _container = container;
            _user = user;
            _expAliasTable = expTable;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _in.getDataIterator(context);
            return LoggingDataIterator.wrap(new AliasDataIterator(pre, context, _container, _user, _expAliasTable));
        }
    }

    private static class AliasDataIterator extends WrapperDataIterator
    {
        // For some reason I don't quite understand we don't want to pass through a column called "alias" so we rename it to ALIASCOLUMNALIAS
        final DataIteratorContext _context;
        final Supplier<Object> _lsidCol;
        final Supplier<Object> _aliasCol;
        final Container _container;
        final User _user;
        Map<String, Object> _lsidAliasMap = new HashMap<>();
        private final TableInfo _expAliasTable;

        protected AliasDataIterator(DataIterator di, DataIteratorContext context, Container container, User user, TableInfo expTable)
        {
            super(di);
            _context = context;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid")==null ? null : di.getSupplier(map.get("lsid"));
            _aliasCol = map.get(ALIASCOLUMNALIAS)==null ? null : di.getSupplier(map.get(ALIASCOLUMNALIAS));

            _container = container;
            _user = user;
            _expAliasTable = expTable;
        }

        private BatchValidationException getErrors()
        {
            return _context.getErrors();
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            // skip processing if there are errors upstream
            if (getErrors().hasErrors())
                return hasNext;

            // after the last row, insert all aliases
            if (!hasNext)
            {
                final ExperimentService svc = ExperimentService.get();

                try (DbScope.Transaction transaction = svc.getTinfoDataClass().getSchema().getScope().ensureTransaction())
                {
                    for (Map.Entry<String, Object> entry : _lsidAliasMap.entrySet())
                    {
                        String lsid = entry.getKey();
                        Object aliases = entry.getValue();
                        AliasInsertHelper.handleInsertUpdate(_container, _user, lsid, _expAliasTable, aliases);
                    }
                    transaction.commit();
                }

                return false;
            }

            // For each iteration, collect the lsid and alias col values.
            if (_lsidCol != null && _aliasCol != null)
            {
                Object lsidValue = _lsidCol.get();
                Object aliasValue = _aliasCol.get();

                if (aliasValue != null && lsidValue instanceof String)
                {
                    _lsidAliasMap.put((String) lsidValue, aliasValue);
                }
            }
            return true;
        }
    }

    public static class AutoLinkToStudyDataIteratorBuilder implements DataIteratorBuilder
    {
        private final DataIteratorBuilder _in;
        private final Container _container;
        private final User _user;
        private final ExpSampleType _sampleType;
        private final UserSchema _schema;

        public AutoLinkToStudyDataIteratorBuilder(@NotNull DataIteratorBuilder in, UserSchema schema, Container container, User user, ExpSampleType sampleType)
        {
            _in = in;
            _schema = schema;
            _container = container;
            _user = user;
            _sampleType = sampleType;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _in.getDataIterator(context);
            return LoggingDataIterator.wrap(new AutoLinkToStudyDataIterator(DataIteratorUtil.wrapMap(pre, false), _schema, _container, _user, _sampleType));
        }
    }

    private static class AutoLinkToStudyDataIterator extends WrapperDataIterator
    {
        final Container _container;
        final User _user;
        final ExpSampleType _sampleType;
        final MapDataIterator _data;
        final List<Map<FieldKey, Object>> _rows = new ArrayList<>();
        final List<Integer> _derivativeKeys = new ArrayList<>();
        final UserSchema _schema;
        final boolean _hasParentInput;
        final Integer _rowIdCol;
        final List<Integer> _parentCols = new ArrayList<>();

        protected AutoLinkToStudyDataIterator(DataIterator di, UserSchema schema, Container container, User user,  ExpSampleType sampleType)
        {
            super(di);

            _schema = schema;
            _container = container;
            _user = user;
            _sampleType = sampleType;
            _data = (MapDataIterator)di;

            Map<String, Integer> nameMap = DataIteratorUtil.createColumnNameMap(di);
            _rowIdCol = nameMap.get("rowid");

            for (String name : nameMap.keySet())
            {
                if (ExperimentService.isInputOutputColumn(name) || equalsIgnoreCase("parent", name) || equalsIgnoreCase("AliquotedFrom", name))
                {
                    _parentCols.add(nameMap.get(name));
                }
            }
            _hasParentInput = !_parentCols.isEmpty();
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = _data.next();

            if (!hasNext)
            {
                StudyPublishService sps = StudyPublishService.get();
                if (sps != null && (!_derivativeKeys.isEmpty() || !_rows.isEmpty()))
                {
                    // Make sure the sampletype invalidate (POSTCOMMIT task) is queued before the autoLink task.
                    SampleTypeServiceImpl.get().refreshSampleTypeMaterializedView(_sampleType, insert);
                    _schema.getDbSchema().getScope().getCurrentTransaction().addCommitTask(() -> {
                        try
                        {
                            if (!_derivativeKeys.isEmpty())
                                sps.autoLinkDerivedSamples(_sampleType, _derivativeKeys, _container, _user);

                            if (!_rows.isEmpty())
                                sps.autoLinkSamples(_sampleType, _rows, _container, _user);
                        }
                        catch (ExperimentException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }, DbScope.CommitTaskOption.POSTCOMMIT);

                    return false;
                }
            }
            boolean isDerivative = false;
            if (_hasParentInput)
            {
                for (Integer parentCol : _parentCols)
                {
                    if (get(parentCol) != null)
                    {
                        isDerivative = true;
                        break;
                    }
                }
            }

            if (!isDerivative)
            {
                Map<FieldKey, Object> row = new HashMap<>();
                for (Map.Entry<String, Object> entry : _data.getMap().entrySet())
                    row.put(FieldKey.fromParts(entry.getKey()), entry.getValue());
                _rows.add(row);
            }
            else
            {
                _derivativeKeys.add((Integer)get(_rowIdCol));
            }

            return true;
        }
    }

    public static class FlagDataIteratorBuilder implements DataIteratorBuilder
    {
        private final DataIteratorBuilder _in;
        private final User _user;
        private final boolean _isSample;
        private final ExpObject _expObject;
        private final Container _container;

        public FlagDataIteratorBuilder(@NotNull DataIteratorBuilder in, User user, boolean isSample, ExpObject expObject, Container container)
        {
            _in = in;
            _user = user;
            _isSample = isSample;
            _expObject = expObject;
            _container = container;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _in.getDataIterator(context);
            return LoggingDataIterator.wrap(new FlagDataIterator(pre, context, _user, _isSample, _expObject, _container));
        }
    }

    private static class FlagDataIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final User _user;
        final Integer _lsidCol;
        final Integer _nameCol;
        final Integer _flagCol;
        final boolean _isSample;    // as opposed to DataClass
        private final ExpSampleType _sampleType;
        private final ExpDataClass _dataClass;
        final Container _container;

        protected FlagDataIterator(DataIterator di, DataIteratorContext context, User user, boolean isSample, ExpObject expObject, Container container)
        {
            super(di);
            _context = context;
            _user = user;
            _isSample = isSample;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _nameCol = map.get("name");
            _flagCol = map.containsKey("flag") ? map.get("flag") : map.get("comment");
            if (isSample)
            {
                _sampleType = (ExpSampleType) expObject;
                _dataClass = null;
            }
            else
            {
                _sampleType = null;
                _dataClass = (ExpDataClass) expObject;
            }
            _container = container;
        }

        private BatchValidationException getErrors()
        {
            return _context.getErrors();
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();
            if (!hasNext)
                return false;

            // skip processing if there are errors upstream
            if (getErrors().hasErrors())
                return true;

            if (_lsidCol != null && _flagCol != null)
            {
                Object lsidValue = get(_lsidCol);

                Object flagValue = get(_flagCol);

                if (lsidValue instanceof String lsid)
                {
                    String flag = Objects.toString(flagValue, null);

                    try
                    {
                        if (_isSample)
                        {
                            ExpMaterial sample = null;
                            if (_context.getInsertOption() == QueryUpdateService.InsertOption.MERGE && _nameCol != null)
                            {
                                Object nameValue = get(_nameCol);
                                if (nameValue instanceof String)
                                    sample = _sampleType.getSample(_container, (String) nameValue);
                            }

                            if (sample == null)
                                sample = ExperimentService.get().getExpMaterial(lsid);
                            if (sample != null)
                                sample.setComment(_user, flag);
                        }
                        else
                        {
                            ExpData data = null;
                            if (_context.getInsertOption() == QueryUpdateService.InsertOption.MERGE && _nameCol != null)
                            {
                                Object nameValue = get(_nameCol);
                                if (nameValue instanceof String)
                                    data = _dataClass.getData(_container, (String) nameValue);
                            }
                            if (data == null)
                                data = ExperimentService.get().getExpData(lsid);
                            if (data != null)
                                data.setComment(_user, flag);
                        }
                    }
                    catch (ValidationException e)
                    {
                        throw new BatchValidationException(e);
                    }
                }

            }
            return true;
        }
    }

    /* setup mini dataiterator pipeline to process lineage */
    public static void derive(User user, Container container, DataIterator di, boolean isSample, ExpObject dataType, boolean skipAliquot) throws BatchValidationException
    {
        ExpDataIterators.DerivationDataIteratorBuilder ddib = new ExpDataIterators.DerivationDataIteratorBuilder(di, container, user, isSample, dataType, skipAliquot, true);
        DataIteratorContext context = new DataIteratorContext();
        context.setInsertOption(QueryUpdateService.InsertOption.UPDATE);
        Map<Enum, Object> configParameters = new HashMap<>();
        configParameters.put(ExperimentService.QueryOptions.UseLsidForUpdate, true);
        context.setConfigParameters(configParameters);
        DataIterator derive = ddib.getDataIterator(context);
        new Pump(derive, context).run();
        if (context.getErrors().hasErrors())
            throw context.getErrors();
    }

    public static class DerivationDataIteratorBuilder implements DataIteratorBuilder
    {
        final DataIteratorBuilder _pre;
        final Container _container;
        final User _user;
        final boolean _isSample;
        final boolean _skipAliquot;
        final ExpObject _currentDataType;
        final boolean _checkRequiredParents; // Required values check are normally handled by StandardDataIteratorBuilder, but some code path (ExpDataIterators.derive) didn't go through that so explicit check is needed

        public DerivationDataIteratorBuilder(DataIteratorBuilder pre, Container container, User user, boolean isSample, ExpObject currentDataType, boolean skipAliquot, boolean checkRequiredParents)
        {
            _pre = pre;
            _container = container;
            _user = user;
            _isSample = isSample;
            _skipAliquot = skipAliquot;
            _currentDataType = currentDataType;
            _checkRequiredParents = checkRequiredParents;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _pre.getDataIterator(context);
            if (context.getConfigParameters().containsKey(SampleTypeUpdateServiceDI.Options.SkipDerivation))
            {
                return pre;
            }

            if (context.getInsertOption() == QueryUpdateService.InsertOption.UPDATE)
                return LoggingDataIterator.wrap(new ImportWithUpdateDerivationDataIterator(pre, context, _container, _user, _currentDataType, _isSample, _checkRequiredParents));
            return LoggingDataIterator.wrap(new DerivationDataIterator(pre, context, _container, _user, _currentDataType, _isSample, _skipAliquot));
        }
    }

    static boolean hasAliquots(int sampleTypeRowId, List<String> names)
    {
        SimpleFilter f = new SimpleFilter(FieldKey.fromParts("Name"), names, IN);
        f.addCondition(FieldKey.fromParts("MaterialSourceId"), sampleTypeRowId);
        f.addCondition(FieldKey.fromParts(AliquotedFromLSID.name()), null, CompareType.NONBLANK);
        return new TableSelector(ExperimentService.get().getTinfoMaterial(), f, null).exists();
    }

    static class DerivationDataIteratorBase extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final Integer _lsidCol;
        final Integer _nameCol;
        final Map<Integer, String> _parentCols;
        final Map<Integer, String> _requiredParentCols;
        final Map<String, String> _aliquotParents;
        /** Cache sample type lookups because even though we do caching in SampleTypeService, it's still a lot of overhead to check permissions for the user */
        final Map<String, ExpSampleType> _sampleTypes = new HashMap<>();
        final Map<String, ExpDataClass> _dataClasses = new HashMap<>();

        final ExpSampleType _currentSampleType;
        final ExpDataClass _currentDataClass;

        final Container _container;
        final User _user;
        final boolean _isSample;
        final TSVWriter _tsvWriter;

        protected DerivationDataIteratorBase(DataIterator di, DataIteratorContext context, Container container, User user, ExpObject currentDataType, boolean isSample, boolean checkRequiredParent)
        {
            super(di);
            _context = context;
            _isSample = isSample;
            Set<String> requiredParents = new CaseInsensitiveHashSet();
            if (isSample)
            {
                _currentSampleType = (ExpSampleType) currentDataType;
                try
                {
                    if (checkRequiredParent)
                        requiredParents.addAll(_currentSampleType.getRequiredImportAliases().values());
                }
                catch (IOException ignore)
                {
                }
                _currentDataClass = null;
            }
            else
            {
                _currentSampleType = null;
                _currentDataClass = (ExpDataClass) currentDataType;
                try
                {
                    if (checkRequiredParent)
                        requiredParents.addAll(_currentDataClass.getRequiredImportAliases().values());
                }
                catch (IOException ignore)
                {
                }
            }

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _nameCol = map.get("name");
            _parentCols = new HashMap<>();
            _requiredParentCols = new HashMap<>();
            _aliquotParents = new LinkedHashMap<>();
            _container = container;
            _user = user;

            for (Map.Entry<String, Integer> entry : map.entrySet())
            {
                String name = entry.getKey();
                if (ExperimentService.isInputOutputColumn(name) || _isSample && equalsIgnoreCase("parent",name))
                {
                    _parentCols.put(entry.getValue(), entry.getKey());
                    if (requiredParents.contains(name))
                        _requiredParentCols.put(entry.getValue(), entry.getKey());
                }
            }

            _tsvWriter = new TSVWriter() // Used to quote values with newline/tabs/quotes
            {
                @Override
                protected int write()
                {
                    throw new UnsupportedOperationException();
                }
            };

        }

        private BatchValidationException getErrors()
        {
            return _context.getErrors();
        }
        
        protected Set<Pair<String, String>> _getParentParts()
        {
            Set<Pair<String, String>> allParts = new HashSet<>();
            for (Integer parentCol : _parentCols.keySet())
            {
                Object o = get(parentCol);
                if (o != null)
                {
                    Collection<String> parentNames;
                    if (o instanceof String)
                    {
                        if (((String) o).trim().isEmpty())
                        {
                            parentNames = Arrays.asList(((String) o).trim());
                        }
                        else
                        {
                            // Issue 44841: The names of the parents may include commas, so we parse the set of parent names
                            // using TabLoader instead of just splitting on the comma.
                            String quotedStr = ((String) o).contains(",") ? (String) o : _tsvWriter.quoteValue((String) o); // if value contains comma, no need to quote again
                            try (TabLoader tabLoader = new TabLoader(quotedStr))
                            {
                                tabLoader.setDelimiterCharacter(',');
                                tabLoader.setUnescapeBackslashes(false);
                                // Issue 50924: LKSM: Importing samples using naming expression referencing parent inputs with # result in error
                                tabLoader.setIncludeComments(true);
                                try
                                {
                                    String[][] values = tabLoader.getFirstNLines(1);
                                    if (values.length > 0)
                                        parentNames = Arrays.asList(values[0]);
                                    else
                                        parentNames = Collections.emptyList();
                                }
                                catch (IOException e)
                                {
                                    parentNames = Collections.emptyList();
                                    getErrors().addRowError(new ValidationException("Unable to parse parent names from " + o, _parentCols.get(parentCol)));
                                }
                            }
                        }
                    }
                    else if (o instanceof JSONArray ja)
                    {
                        parentNames = ja.toList().stream().map(String::valueOf).collect(Collectors.toSet());
                    }
                    else if (o instanceof Collection)
                    {
                        //noinspection rawtypes
                        Collection<?> c = ((Collection)o);
                        parentNames = c.stream().map(String::valueOf).collect(Collectors.toSet());
                    }
                    else if (o instanceof Number)
                    {
                        parentNames = Arrays.asList(o.toString());
                    }
                    else
                    {
                        getErrors().addRowError(new ValidationException("Expected comma separated list or a JSONArray of parent names: " + o, _parentCols.get(parentCol)));
                        continue;
                    }

                    String parentColName = _parentCols.get(parentCol);
                    Set<Pair<String, String>> parts = parentNames.stream()
                        .map(String::trim)
                        .map(s -> Pair.of(parentColName, s))
                        .collect(Collectors.toSet());

                    allParts.addAll(parts);
                }
                else // we have parent columns but the parent value is empty, indicating that the parents should be cleared
                {
                    allParts.add(new Pair<>(_parentCols.get(parentCol), null));
                }
            }
            return allParts;
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            return super.next();
        }

        protected void _processRun(ExpRunItem runItem,
                                   List<UploadSampleRunRecord> runRecords,
                                   Set<Pair<String, String>> parentNames,
                                   RemapCache cache,
                                   Map<Integer, ExpMaterial> materialCache,
                                   Map<Integer, ExpData> dataCache,
                                   @Nullable String aliquotedFrom,
                                   String dataType /*sample type or source type name*/,
                                   boolean updateOnly) throws ValidationException, ExperimentException
        {
            Pair<RunInputOutputBean, RunInputOutputBean> pair;
            if (_context.getInsertOption().allowUpdate)
            {
                pair = resolveInputsAndOutputs(
                        _user, _container, runItem, parentNames, cache, materialCache, dataCache, _sampleTypes, _dataClasses, aliquotedFrom, dataType, updateOnly);
            }
            else
            {
                pair = resolveInputsAndOutputs(
                        _user, _container, null, parentNames, cache, materialCache, dataCache, _sampleTypes, _dataClasses, aliquotedFrom, dataType, updateOnly);
            }

            if (pair.first == null && pair.second == null) // no parents or children columns provided in input data and no existing parents to be updated
                return;

            if (_isSample && aliquotedFrom == null && !((ExpMaterial) runItem).isOperationPermitted(SampleTypeService.SampleOperations.EditLineage))
                throw new ValidationException(String.format("Sample %s with status %s cannot have its lineage updated.", runItem.getName(), ((ExpMaterial) runItem).getStateLabel()));

            // the parent columns provided in the input are all empty and there are no existing parents not mentioned in the input that need to be retained.
            if (_context.getInsertOption().allowUpdate && pair.first.doClear())
            {
                Pair<Set<? extends ExpMaterial>, Set<? extends ExpMaterial>> previousSampleRelatives = clearRunItemSourceRun(_user, runItem, true);
                String lockCheckMessage = checkForLockedSampleRelativeChange(previousSampleRelatives.first, Collections.emptySet(), runItem.getName(), "parents");
                lockCheckMessage += checkForLockedSampleRelativeChange(previousSampleRelatives.second, Collections.emptySet(), runItem.getName(), "children");
                if (!lockCheckMessage.isEmpty())
                    throw new ValidationException(lockCheckMessage);
            }
            else
            {
                ExpMaterial currentMaterial = null;
                Map<ExpMaterial, String> currentMaterialMap = Collections.emptyMap();
                Pair<Set<? extends ExpMaterial>, Set<? extends ExpMaterial>> previousSampleRelatives = Pair.of(Collections.emptySet(), Collections.emptySet());
                Map<ExpData, String> currentDataMap = Collections.emptyMap();

                if (_context.getInsertOption().allowUpdate)
                {
                    // TODO always clear? or only when parentcols is in input? or only when new derivation is specified?
                    // Since this entry was (maybe) already in the database, we may need to delete old derivation info
                    previousSampleRelatives = clearRunItemSourceRun(_user, runItem, false);
                }

                if (_isSample)
                {
                    ExpMaterial sample = (ExpMaterial) runItem;
                    currentMaterialMap = new HashMap<>();
                    currentMaterial = sample;
                    currentMaterialMap.put(sample, sampleRole(sample));
                }
                else
                {
                    ExpData data = (ExpData) runItem;
                    currentDataMap = new HashMap<>();
                    currentDataMap.put(data, dataRole(data, _user));
                }

                if (pair.first != null)
                {
                    // Add parent derivation run
                    Map<ExpMaterial, String> parentMaterialMap = pair.first.getMaterials();

                    String lockCheckMessage = checkForLockedSampleRelativeChange(previousSampleRelatives.first, parentMaterialMap.keySet(), runItem.getName(), "parents");
                    if (!lockCheckMessage.isEmpty())
                        throw new ValidationException(lockCheckMessage);

                    Map<ExpData, String> parentDataMap = pair.first.getDatas();

                    record(true, runRecords,
                            parentMaterialMap, currentMaterialMap,
                            parentDataMap, currentDataMap, pair.first.getAliquotParent(), currentMaterial);
                }

                if (pair.second != null)
                {
                    // Add child derivation run
                    Map<ExpMaterial, String> childMaterialMap = pair.second.getMaterials();
                    Map<ExpData, String> childDataMap = pair.second.getDatas();
                    String lockCheckMessage = checkForLockedSampleRelativeChange(previousSampleRelatives.second, childMaterialMap.keySet(), runItem.getName(), "children");
                    if (!lockCheckMessage.isEmpty())
                        throw new ValidationException(lockCheckMessage);

                    record(false, runRecords,
                            currentMaterialMap, childMaterialMap,
                            currentDataMap, childDataMap, null, null);
                }
            }
        }
    }
    
    static class DerivationDataIterator extends DerivationDataIteratorBase
    {
        final Integer _aliquotParentCol;
        final Map<String, String> _lsidNames;
        // Map of Data lsid and its aliquotedFromLSID
        final Map<String, String> _aliquotParents;
        // Map from Data LSID to Set of (parentColName, parentName)
        final Map<String, Set<Pair<String, String>>> _parentNames;

        final boolean _skipAliquot; // skip aliquot validation, used for update/updates cases

        final List<String> _candidateAliquotNames; // used to check if a name is an aliquot, with absent "AliquotedFrom". used for merge only

        protected DerivationDataIterator(DataIterator di, DataIteratorContext context, Container container, User user, ExpObject currentDataType, boolean isSample, boolean skipAliquot)
        {
            super(di, context, container, user, currentDataType, isSample, false /* for insert/merge, required parents are always checked in StandardDataIteratorBuilder */);
            _skipAliquot = skipAliquot || context.getConfigParameterBoolean(SampleTypeService.ConfigParameters.DeferAliquotRuns);

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidNames = new HashMap<>();
            _parentNames = new LinkedHashMap<>();
            _aliquotParents = new LinkedHashMap<>();
            _candidateAliquotNames = new ArrayList<>();

            Integer aliquotParentCol = -1;
            for (Map.Entry<String, Integer> entry : map.entrySet())
            {
                String name = entry.getKey();
                if (_isSample && ExpMaterial.ALIQUOTED_FROM_INPUT.equalsIgnoreCase(name))
                {
                    aliquotParentCol = entry.getValue();
                }
            }

            _aliquotParentCol = aliquotParentCol;
        }

        private BatchValidationException getErrors()
        {
            return _context.getErrors();
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            // skip processing if there are errors upstream
            if (getErrors().hasErrors())
                return hasNext;

            // For each iteration, collect the parent col values
            if (hasNext)
            {
                String lsid = (String) get(_lsidCol);
                String name = null;
                if (_nameCol != null)
                    name = (String) get(_nameCol);
                _lsidNames.put(lsid, name);
                if (_aliquotParentCol > -1 && !_context.getConfigParameterBoolean(SampleTypeService.ConfigParameters.DeferAliquotRuns))
                {
                    Object o = get(_aliquotParentCol);
                    String aliquotParentName = null;
                    if (o != null)
                    {
                        if (o instanceof String)
                        {
                            aliquotParentName = StringUtilsLabKey.unquoteString((String) o);
                        }
                        else if (o instanceof Number)
                        {
                            aliquotParentName = o.toString();
                        }
                        else
                        {
                            getErrors().addRowError(new ValidationException("Expected string value for aliquot parent name: " + o, ExpMaterial.ALIQUOTED_FROM_INPUT));
                        }

                        if (aliquotParentName != null)
                            _aliquotParents.put(lsid, aliquotParentName);
                    }

                    if (aliquotParentName == null && _context.getInsertOption().mergeRows)
                        _candidateAliquotNames.add(name);
                }
                else if (!_skipAliquot && _context.getInsertOption().mergeRows)
                {
                    _candidateAliquotNames.add(name);
                }

                Set<Pair<String, String>> allParts = _getParentParts();
                if (!allParts.isEmpty())
                    _parentNames.put(lsid, allParts);
            }

            if (getErrors().hasErrors())
                return hasNext;

            if (!hasNext)
            {
                try
                {
                    boolean allowBulkLoadRemapCache = true;
                    if (_context.getConfigParameterBoolean(SkipBulkRemapCache))
                        allowBulkLoadRemapCache = false;

                    RemapCache cache = new RemapCache(allowBulkLoadRemapCache);
                    Map<Integer, ExpMaterial> materialCache = new HashMap<>();
                    Map<Integer, ExpData> dataCache = new HashMap<>();

                    if (_isSample && _context.getInsertOption().mergeRows)
                    {
                        if (!_candidateAliquotNames.isEmpty())
                        {
                            if (hasAliquots(_currentSampleType.getRowId(), _candidateAliquotNames))
                            {
                                // AliquotedFrom is used to determine if aliquot/meta field value should be retained or discarded
                                // In the case of merge, one can argue AliquotedFrom can be queried for existing data, instead of making it a required field.
                                // But that would be too expensive. For performance reasons, merge will error out if any aliquots are present but 'AliquotedFrom' column is missing.
                                if (_aliquotParentCol == -1)
                                    throw new ValidationException("Aliquots are present but 'AliquotedFrom' column is missing.");
                                else
                                    throw new ValidationException("'AliquotedFrom' cannot be blank for existing aliquots.");
                            }
                        }
                    }

                    List<UploadSampleRunRecord> runRecords = new ArrayList<>();
                    Set<String> lsids = new LinkedHashSet<>();
                    lsids.addAll(_parentNames.keySet());
                    lsids.addAll(_aliquotParents.keySet());
                    for (String lsid : lsids)
                    {
                        Set<Pair<String, String>> parentNames = _parentNames.getOrDefault(lsid, Collections.emptySet());

                        ExpRunItem runItem;
                        String aliquotedFrom = _aliquotParents.get(lsid);
                        String dataType = null;
                        if (_isSample)
                        {
                            ExpMaterial m = null;
                            if (_context.getInsertOption().mergeRows) // column lsid generated from dbseq might not be valid for existing materials, lookup by name instead
                            {
                                String sampleName = _lsidNames.get(lsid);
                                if (!StringUtils.isEmpty(sampleName))
                                {
                                    m = _currentSampleType.getSample(_container, sampleName);
                                }
                            }

                            if (m == null)
                                m = ExperimentService.get().getExpMaterial(lsid);

                            if (m != null)
                            {
                                materialCache.put(m.getRowId(), m);
                                dataType = m.getSampleType().getName();
                            }
                            runItem = m;
                        }
                        else
                        {
                            ExpData d = null;
                            if (_context.getInsertOption().mergeRows) // column lsid generated from guid might not be valid for existing data, lookup by name instead
                            {
                                String dataName = _lsidNames.get(lsid);
                                if (!StringUtils.isEmpty(dataName))
                                {
                                    d = _currentDataClass.getData(_container, dataName);
                                }
                            }

                            if (d == null)
                                d = ExperimentService.get().getExpData(lsid);

                            if (d != null)
                            {
                                dataCache.put(d.getRowId(), d);
                            }
                            runItem = d;
                        }
                        if (runItem == null) // nothing to do if the item does not exist
                            continue;

                        _processRun(runItem, runRecords, parentNames, cache, materialCache, dataCache, aliquotedFrom, dataType, false);
                    }

                    if (!runRecords.isEmpty())
                    {
                        ExperimentService.get().deriveSamplesBulk(runRecords, new ViewBackgroundInfo(_container, _user, null), null);
                    }
                }
                catch (ExperimentException e)
                {
                    throw new RuntimeException(e);
                }
                catch (ValidationException e)
                {
                    getErrors().addRowError(e);
                }
            }
            return hasNext;
        }


    }
    
    static class ImportWithUpdateDerivationDataIterator extends DerivationDataIteratorBase
    {
        // Map from Data name to Set of (parentColName, parentName)
        final Map<String, Set<Pair<String, String>>> _parentNames;
        final Integer _aliquotParentCol;
        // Map of Data name and its aliquotedFromLSID
        final Map<String, String> _aliquotParents;

        final boolean _useLsid;

        protected ImportWithUpdateDerivationDataIterator(DataIterator di, DataIteratorContext context, Container container, User user, ExpObject currentDataType, boolean isSample, boolean checkRequiredParent)
        {
            super(di, context, container, user, currentDataType, isSample, checkRequiredParent);

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _parentNames = new LinkedHashMap<>();
            _aliquotParents = new LinkedHashMap<>();

            Integer aliquotParentCol = -1;
            for (Map.Entry<String, Integer> entry : map.entrySet())
            {
                if (_isSample && "AliquotedFromLSID".equalsIgnoreCase(entry.getKey()))
                {
                    aliquotParentCol = entry.getValue();
                }
            }

            _aliquotParentCol = aliquotParentCol;

            _useLsid = map.containsKey("lsid") && context.getConfigParameterBoolean(ExperimentService.QueryOptions.UseLsidForUpdate);
        }

        private BatchValidationException getErrors()
        {
            return _context.getErrors();
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            // skip processing if there are errors upstream
            if (getErrors().hasErrors())
                return hasNext;

            // For each iteration, collect the parent col values
            if (hasNext)
            {
                String key = null;
                if (_useLsid && _lsidCol != null)
                    key = (String) get(_lsidCol);
                else if (_nameCol != null)
                    key = (String) get(_nameCol);

                String aliquotParentName = null;

                if (_aliquotParentCol > -1 && !_context.getConfigParameterBoolean(SampleTypeService.ConfigParameters.DeferAliquotRuns))
                {
                    Object o = get(_aliquotParentCol);

                    if (o != null)
                    {
                        if (o instanceof String)
                        {
                            aliquotParentName = StringUtilsLabKey.unquoteString((String) o);
                        }
                        else if (o instanceof Number)
                        {
                            aliquotParentName = o.toString();
                        }
                        else
                        {
                            getErrors().addRowError(new ValidationException("Expected string value for aliquot parent name: " + o, "AliquotedFromLSID"));
                        }

                        if (aliquotParentName != null)
                            _aliquotParents.put(key, aliquotParentName);
                    }
                }

                // for non-aliquot, check required parent lineage
                if (aliquotParentName == null && !_requiredParentCols.isEmpty())
                {
                    for (Integer parentCol : _requiredParentCols.keySet())
                    {
                        Object parentVal = get(parentCol);
                        if (parentVal == null || (parentVal instanceof String && ((String) parentVal).isEmpty()))
                            getErrors().addRowError(new ValidationException("Missing value for required property: " + _requiredParentCols.get(parentCol)));
                    }
                }

                Set<Pair<String, String>> allParts = _getParentParts();
                if (!allParts.isEmpty())
                    _parentNames.put(key, allParts);
            }

            if (getErrors().hasErrors())
                return hasNext;

            if (!hasNext)
            {
                try
                {
                    RemapCache cache = new RemapCache(true);
                    Map<Integer, ExpMaterial> materialCache = new HashMap<>();
                    Map<Integer, ExpData> dataCache = new HashMap<>();


                    List<UploadSampleRunRecord> runRecords = new ArrayList<>();
                    Set<String> keys = new LinkedHashSet<>();
                    keys.addAll(_parentNames.keySet());
                    keys.addAll(_aliquotParents.keySet());

                    ExperimentService experimentService = ExperimentService.get();
                    for (String key : keys)
                    {
                        Set<Pair<String, String>> parentNames = _parentNames.getOrDefault(key, Collections.emptySet());

                        ExpRunItem runItem;
                        String aliquotedFromLSID = _aliquotParents.get(key);
                        String dataType = null;
                        if (_isSample)
                        {
                            ExpMaterial m = _useLsid ? experimentService.getExpMaterial(key) : _currentSampleType.getSample(_container, key);

                            if (m != null)
                            {
                                materialCache.put(m.getRowId(), m);
                                dataType = _currentSampleType.getName();
                            }
                            runItem = m;
                        }
                        else
                        {
                            ExpData d = _useLsid ? experimentService.getExpData(key) : _currentDataClass.getData(_container, key);

                            if (d != null)
                            {
                                dataCache.put(d.getRowId(), d);
                                dataType = _currentDataClass.getName();
                            }
                            runItem = d;
                        }
                        if (runItem == null) // nothing to do if the item does not exist
                            continue;

                        _processRun(runItem, runRecords, parentNames, cache, materialCache, dataCache, aliquotedFromLSID, dataType, true);
                    }

                    if (!runRecords.isEmpty())
                    {
                        ExperimentService.get().deriveSamplesBulk(runRecords, new ViewBackgroundInfo(_container, _user, null), null);
                    }
                }
                catch (ExperimentException e)
                {
                    throw new RuntimeException(e);
                }
                catch (ValidationException e)
                {
                    getErrors().addRowError(e);
                    throw getErrors();
                }
            }
            return hasNext;
        }

    }

    private static String checkForLockedSampleRelativeChange(Set<? extends ExpMaterial> previousSampleRelatives, Set<? extends ExpMaterial> currentSampleRelatives, String sampleName, String relationPlural)
    {
        List<String> messages = new ArrayList<>();
        // get the relatives whose lineage cannot change
        SampleTypeService sampleService = SampleTypeService.get();
        Collection<? extends ExpMaterial> lockedRelatives = sampleService.getSamplesNotPermitted(previousSampleRelatives, SampleTypeService.SampleOperations.EditLineage);

        Set<String> lockedRelativeLsids = lockedRelatives.stream().map(ExpMaterial::getLSID).collect(Collectors.toSet());
        Set<String> newRelativeLsids = currentSampleRelatives.stream().map(ExpMaterial::getLSID).collect(Collectors.toSet());
        // check if all the locked relatives are still in the current list
        Set<ExpMaterial> removedLocked = lockedRelatives.stream().filter(sample -> !newRelativeLsids.contains(sample.getLSID())).collect(Collectors.toSet());
        if (!removedLocked.isEmpty())
        {
            String message = String.format("One or more existing %s of sample %s has a status that prevents the updating of lineage", relationPlural, sampleName);
            if (removedLocked.size() <= 10)
                message += ": " + removedLocked.stream().map(ExpMaterial::getNameAndStatus).collect(Collectors.joining(", "));
            message += ".";
            messages.add(message);
        }
        //check if any of the newly added relatives are locked
        Set<ExpMaterial> addedLocked = sampleService.getSamplesNotPermitted(currentSampleRelatives, SampleTypeService.SampleOperations.EditLineage)
                .stream().filter(sample -> !lockedRelativeLsids.contains(sample.getLSID()))
                .collect(Collectors.toSet());

        if (!addedLocked.isEmpty())
        {
            String message = String.format("One or more of the new %s for sample %s has a status that prevents the updating of lineage: ", relationPlural, sampleName);
            message += addedLocked.stream().limit(10).map(ExpMaterial::getNameAndStatus).collect(Collectors.joining(", "));
            if (addedLocked.size() > 10)
                message += "...";
            message += ".";
            messages.add(message);
        }
        return StringUtils.join(messages, " ");
    }

    /**
     * Clear the source protocol application for this material.
     * If the run that created this material is not a sample derivation run, throw an error -- we don't
     * want to delete an assay run, for example.
     * If the run has more than the sample as an output, the material is removed as an output of the run
     * otherwise the run will be deleted.
     */
    @NotNull
    private static Pair<Set<? extends ExpMaterial>, Set<? extends ExpMaterial>> clearRunItemSourceRun(User user, ExpRunItem runItem, boolean clearAncestors) throws ValidationException, ExperimentException
    {
        ExpProtocolApplication existingSourceApp = runItem.getSourceApplication();
        Set<? extends ExpMaterial> previousMaterialParents = Collections.emptySet();
        Set<? extends ExpMaterial> previousMaterialChildren = Collections.emptySet();
        if (existingSourceApp == null)
            return Pair.of(previousMaterialParents, previousMaterialChildren);

        ExpRun existingDerivationRun = existingSourceApp.getRun();
        if (existingDerivationRun == null)
            return Pair.of(previousMaterialParents, previousMaterialChildren);

        ExpProtocol protocol = existingDerivationRun.getProtocol();

        if (ExperimentServiceImpl.get().isSampleAliquot(protocol))
            return Pair.of(previousMaterialParents, previousMaterialChildren);;

        if (!ExperimentServiceImpl.get().isSampleDerivation(protocol))
        {
            throw new ValidationException(
                    "Can't remove source run '" + existingDerivationRun.getName() + "'" +
                            " of protocol '" + protocol.getName() + "'" +
                            " for run item '" + runItem.getName() + "' since it is not a sample derivation run");
        }

        previousMaterialParents = existingDerivationRun.getMaterialInputs().keySet();
        previousMaterialChildren = new HashSet<>(existingDerivationRun.getMaterialOutputs());

        List<ExpData> dataOutputs = existingDerivationRun.getDataOutputs();
        List<ExpMaterial> materialOutputs = existingDerivationRun.getMaterialOutputs();
        if ((dataOutputs.isEmpty() && (materialOutputs.isEmpty() || (materialOutputs.size() == 1 && materialOutputs.contains(runItem))))
           || (materialOutputs.isEmpty() && dataOutputs.size() == 1 && dataOutputs.contains(runItem)))
        {
            LOG.debug("Run item '" + runItem.getName() + "' has existing source derivation run '" + existingDerivationRun.getRowId() + "' -- run has no other outputs, deleting run");
            // if run has no other outputs, delete the run completely
            runItem.setSourceApplication(null);
            try
            {
                runItem.save(user);
            }
            catch (BatchValidationException e)
            {
                throw new ExperimentException(e);
            }

            existingDerivationRun.delete(user);
            if (clearAncestors)
                ExperimentServiceImpl.get().clearAncestors(runItem);
        }
        else
        {
            LOG.debug("Run item '" + runItem.getName() + "' has existing source derivation run '" + existingDerivationRun.getRowId() + "' -- run has other " + dataOutputs.size() + " data outputs and " + materialOutputs.size() + " material outputs, removing sample from run");
            // if the existing run has other outputs, remove the run as the source application for this sample
            // and remove it as an output from the run
            runItem.setSourceApplication(null);
            try
            {
                runItem.save(user);
            }
            catch (BatchValidationException e)
            {
                throw new ExperimentException(e);
            }
            ExpProtocolApplication outputApp = existingDerivationRun.getOutputProtocolApplication();

            if (runItem instanceof ExpMaterial material)
            {
                if (outputApp != null)
                    outputApp.removeMaterialInput(user, material);
                existingSourceApp.removeMaterialInput(user, material);
            }
            else if (runItem instanceof ExpData data)
            {
                if (outputApp != null)
                    outputApp.removeDataInput(user, data);
                existingSourceApp.removeDataInput(user, data);
            }
            ExperimentService.get().queueSyncRunEdges(existingDerivationRun);
        }
        return Pair.of(previousMaterialParents, previousMaterialChildren);
    }

    /**
     * Collect the output material or data into a run record.
     * When merge is true, the outputs will be combined with
     * an existing record with the same input parents, if possible.
     */
    private static void record(
            boolean merge,
            @NotNull List<UploadSampleRunRecord> runRecords,
            @NotNull Map<ExpMaterial, String> parentMaterialMap,
            @NotNull Map<ExpMaterial, String> childMaterialMap,
            @NotNull Map<ExpData, String> parentDataMap,
            @NotNull Map<ExpData, String> childDataMap,
            @Nullable ExpMaterial aliquotParent,
            @Nullable ExpMaterial aliquotChild
    )
    {
        if (merge)
        {
            Set<ExpMaterial> parentMaterials = parentMaterialMap.keySet();
            Set<ExpData> parentDatas = parentDataMap.keySet();

            // find existing RunRecord with the same set of parents and add output children to it
            for (UploadSampleRunRecord record : runRecords)
            {
                if (record._aliquotInput != null && record._aliquotInput.equals(aliquotParent))
                {
                    if (aliquotChild != null)
                        record._aliquotOutputs.add(aliquotChild);
                    return;
                }
                else if ((!record.getInputMaterialMap().isEmpty() || !record.getInputDataMap().isEmpty()) && record.getInputMaterialMap().keySet().equals(parentMaterials) && record.getInputDataMap().keySet().equals(parentDatas))
                {
                    if (record._outputMaterial.isEmpty())
                        record._outputMaterial = childMaterialMap;
                    else
                        record._outputMaterial.putAll(childMaterialMap);

                    if (record._outputData.isEmpty())
                        record._outputData = childDataMap;
                    else
                        record._outputData.putAll(childDataMap);
                    return;
                }
            }
        }

        // otherwise, create new run record
        List<ExpMaterial> aliquots = null;
        if (aliquotChild != null)
        {
            aliquots = new LinkedList<>();
            aliquots.add(aliquotChild);
        }

        runRecords.add(new UploadSampleRunRecord(parentMaterialMap, childMaterialMap, parentDataMap, childDataMap, aliquotParent, aliquots));
    }

    public static class UploadSampleRunRecord implements SimpleRunRecord
    {
        private final Map<ExpMaterial, String> _inputMaterial;
        Map<ExpMaterial, String> _outputMaterial;
        Map<ExpData, String> _inputData;
        Map<ExpData, String> _outputData;

        ExpMaterial _aliquotInput;
        List<ExpMaterial> _aliquotOutputs;

        public UploadSampleRunRecord(Map<ExpMaterial, String> inputMaterial, Map<ExpMaterial, String> outputMaterial,
                                     Map<ExpData, String> inputData, Map<ExpData, String> outputData,
                                     ExpMaterial aliquotInput, List<ExpMaterial> aliquotChildren)
        {
            _inputMaterial = inputMaterial;
            _outputMaterial = outputMaterial;
            _inputData = inputData;
            _outputData = outputData;
            _aliquotInput = aliquotInput;
            _aliquotOutputs = aliquotChildren;
        }

        @Override
        public Map<ExpMaterial, String> getInputMaterialMap()
        {
            return _inputMaterial;
        }

        @Override
        public Map<ExpMaterial, String> getOutputMaterialMap()
        {
            return _outputMaterial;
        }

        @Override
        public Map<ExpData, String> getInputDataMap()
        {
            return _inputData;
        }

        @Override
        public Map<ExpData, String> getOutputDataMap()
        {
            return _outputData;
        }

        @Override
        public ExpMaterial getAliquotInput()
        {
            return _aliquotInput;
        }

        @Override
        public List<ExpMaterial> getAliquotOutputs()
        {
            return _aliquotOutputs;
        }
    }

    /**
     * support for mapping DataClass or SampleSet objects as a parent input using the column name format:
     * DataInputs/<data class name> or MaterialInputs/<sample type name>. Either / or . works as a delimiter
     *
     * @param runItem the item whose parents are being modified.  If provided, existing parents of the item
     *                will be incorporated into the resolved inputs and outputs
     * @param entityNamePairs set of (parent column name, parent value) pairs.  Parent values that are empty
     *                    indicate the parent should be removed.
     * @throws ValidationException
     */
    @NotNull
    private static Pair<RunInputOutputBean, RunInputOutputBean> resolveInputsAndOutputs(
        User user, Container c,
        @Nullable ExpRunItem runItem,
        Set<Pair<String, String>> entityNamePairs,
        RemapCache cache,
        Map<Integer, ExpMaterial> materialMap,
        Map<Integer, ExpData> dataMap,
        Map<String, ExpSampleType> sampleTypes,
        Map<String, ExpDataClass> dataClasses,
        @Nullable String aliquotedFrom,
        String dataType /*sample type or source type name*/,
        boolean updateOnly
    ) throws ValidationException
    {
        Map<ExpMaterial, String> parentMaterials = new LinkedHashMap<>();
        Map<ExpData, String> parentData = new LinkedHashMap<>();
        Set<String> parentDataTypesToRemove = new CaseInsensitiveHashSet();
        Set<String> parentSampleTypesToRemove = new CaseInsensitiveHashSet();

        Map<ExpMaterial, String> childMaterials = new HashMap<>();
        Map<ExpData, String> childData = new HashMap<>();
        boolean isUpdatingExisting = runItem != null;

        ExpMaterial aliquotParent = null;
        boolean isAliquot = !StringUtils.isEmpty(aliquotedFrom);
        boolean skipExistingAliquotParents = isAliquot && updateOnly; /* skip updating aliquot parent for UPDATE */

        if (isAliquot && !updateOnly)
        {
            ExpSampleType sampleType = sampleTypes.computeIfAbsent(dataType, (name) -> SampleTypeService.get().getSampleType(c, user, name));
            if (sampleType == null)
                throw new ValidationException("Invalid sample type: " + dataType);

            aliquotParent = ExperimentService.get().findExpMaterial(c, user, aliquotedFrom, sampleType, cache, materialMap);

            if (aliquotParent == null)
            {
                String message = "Aliquot parent '" + aliquotedFrom + "' not found.";
                throw new ValidationException(message);
            }
            else if (!aliquotParent.isOperationPermitted(SampleTypeService.SampleOperations.EditLineage))
            {
                throw new ValidationException(String.format("Creation of aliquots is not allowed for sample '%s' with status '%s'", aliquotParent.getName(), aliquotParent.getStateLabel()));
            }
        }

        for (Pair<String, String> pair : entityNamePairs)
        {
            String entityColName = pair.first;
            String entityName = pair.second;
            boolean isEmptyEntity = StringUtils.isEmpty(entityName);
            Pair<String, String> aliasPair = ExperimentService.parseInputOutputAlias(entityColName);
            String aliasPrefix = aliasPair != null ? aliasPair.first : null;
            String aliasSuffix = aliasPair != null ? aliasPair.second : null;

            if ("parent".equalsIgnoreCase(entityColName))
            {
                if (!isEmptyEntity)
                {
                    if (isAliquot)
                    {
                        String message = "Sample derivation parent input is not allowed for aliquots.";
                        throw new ValidationException(message);
                    }

                    if (skipExistingAliquotParents)
                        continue;

                    ExpMaterial sample = ExperimentService.get().findExpMaterial(c, user, entityName, null, cache, materialMap);
                    if (sample != null)
                        parentMaterials.put(sample, sampleRole(sample));
                    else
                    {
                        String message = "Sample input '" + entityName + "' not found";
                        throw new ValidationException(message);
                    }
                }
            }
            else if (aliasPrefix != null && aliasSuffix != null)
            {
                String namePart = QueryKey.decodePart(aliasSuffix);
                if (MATERIAL_INPUT_PARENT.equalsIgnoreCase(aliasPrefix))
                {
                    if (isEmptyEntity)
                    {
                        if (isUpdatingExisting && !isAliquot)
                            parentSampleTypesToRemove.add(namePart);
                    }
                    else
                    {
                        if (isAliquot)
                        {
                            if (namePart.equals(dataType) && entityName.equals(aliquotedFrom))
                                continue;

                            String message = "Sample derivation parent input is not allowed for aliquots";
                            throw new ValidationException(message);
                        }

                        if (skipExistingAliquotParents)
                            continue;

                        ExpSampleType sampleType = sampleTypes.computeIfAbsent(namePart, (name) -> SampleTypeService.get().getSampleType(c, user, name));
                        if (sampleType == null)
                            throw new ValidationException(String.format("Invalid import alias: parent SampleType [%1$s] does not exist or may have been deleted", namePart));

                        ExpMaterial sample = ExperimentService.get().findExpMaterial(c, user, entityName, sampleType, cache, materialMap);
                        if (sample != null)
                            parentMaterials.put(sample, sampleRole(sample));
                        else
                            throw new ValidationException("Sample '" + entityName + "' not found in Sample Type '" + namePart + "'.");

                    }
                }
                else if (ExpMaterial.MATERIAL_OUTPUT_CHILD.equalsIgnoreCase(aliasPrefix))
                {
                    ExpSampleType sampleType = sampleTypes.computeIfAbsent(namePart, (name) -> SampleTypeService.get().getSampleType(c, user, name));
                    if (sampleType == null)
                        throw new ValidationException(String.format("Invalid import alias: child SampleType [%1$s] does not exist or may have been deleted", namePart));

                    if (!isEmptyEntity)
                    {
                        ExpMaterial sample = ExperimentService.get().findExpMaterial(c, user, entityName, sampleType, cache, materialMap);
                        if (sample != null)
                        {
                            if (StringUtils.isEmpty(sample.getAliquotedFromLSID()))
                                childMaterials.put(sample, sampleRole(sample));
                            else
                            {
                                String message = "Sample derivation output is not allowed for aliquots.";
                                throw new ValidationException(message);
                            }
                        }
                        else
                            throw new ValidationException("Sample output '" + entityName + "' not found in Sample Type '" + namePart + "'.");
                    }
                }
                else if (DATA_INPUT_PARENT.equalsIgnoreCase(aliasPrefix))
                {
                    if (isEmptyEntity)
                    {
                        if (isUpdatingExisting && !isAliquot)
                            parentDataTypesToRemove.add(namePart);
                    }
                    else
                    {
                        if (isAliquot)
                        {
                            String message = entityColName + " is not allowed for aliquots";
                            throw new ValidationException(message);
                        }

                        if (skipExistingAliquotParents)
                            continue;

                        ExpDataClass dataClass = dataClasses.computeIfAbsent(namePart, (name) -> ExperimentService.get().getDataClass(c, user, name));
                        if (dataClass == null)
                            throw new ValidationException(String.format("Invalid import alias: parent DataClass [%1$s] does not exist or may have been deleted", namePart));

                        ExpData data = ExperimentService.get().findExpData(c, user, dataClass, namePart, entityName, cache, dataMap);
                        if (data != null)
                            parentData.put(data, dataRole(data, user));
                        else
                        {

                            if (ExpSchema.DataClassCategoryType.sources.name().equalsIgnoreCase(dataClass.getCategory()))
                                throw new ValidationException("Source '" + entityName + "' not found in Source Type  '" + namePart + "'.");
                            else
                                throw new ValidationException("Data input '" + entityName + "' not found in Data Class '" + namePart + "'.");
                        }
                    }
                }
                else if (ExpData.DATA_OUTPUT_CHILD.equalsIgnoreCase(aliasPrefix))
                {
                    ExpDataClass dataClass = dataClasses.computeIfAbsent(namePart, (name) -> ExperimentService.get().getDataClass(c, user, name));
                    if (dataClass == null)
                        throw new ValidationException(String.format("Invalid import alias: child DataClass [%1$s] does not exist or may have been deleted", namePart));

                    if (!isEmptyEntity)
                    {
                        ExpData data = ExperimentService.get().findExpData(c, user, dataClass, namePart, entityName, cache, dataMap);
                        if (data != null)
                            childData.put(data, dataRole(data, user));
                        else
                            throw new ValidationException("Data output '" + entityName + "' in DataClass '" + namePart + "' not found");
                    }
                }
            }
        }

        if (isUpdatingExisting && !skipExistingAliquotParents)
        {
            ExpLineageOptions options = new ExpLineageOptions();
            options.setChildren(false);
            options.setDepth(2); // use 2 to get the first generation of parents because the first "parent" is the run

            ExpLineage lineage = ExpLineageService.get().getLineage(c, user, runItem, options);
            Pair<Set<ExpData>, Set<ExpMaterial>> currentParents = Pair.of(lineage.getDatas(), lineage.getMaterials());
            if (currentParents.first != null)
            {
                Map<ExpData, String> existingParentData = new HashMap<>();
                currentParents.first.forEach((dataParent) -> {
                    ExpDataClass dataClass = dataParent.getDataClass(user);
                    String role = dataRole(dataParent, user);
                    if (dataClass != null && !parentData.containsValue(role) && !parentDataTypesToRemove.contains(role))
                    {
                        existingParentData.put(dataParent, role);
                    }
                });
                parentData.putAll(existingParentData);
            }
            if (currentParents.second != null)
            {
                boolean isExistingAliquot = false;
                if (runItem instanceof ExpMaterial currentMaterial)
                {
                    isExistingAliquot = !StringUtils.isEmpty(currentMaterial.getAliquotedFromLSID());

                    if (isExistingAliquot && !isAliquot)
                        throw new ValidationException("AliquotedFrom is absent for aliquot " + currentMaterial.getName() + ".");
                    else if (!isExistingAliquot && isAliquot)
                        throw new ValidationException("Unable to change sample to aliquot " + currentMaterial.getName() + ".");
                    else if (isExistingAliquot)
                    {
                        if (!currentMaterial.getAliquotedFromLSID().equals(aliquotParent.getLSID())
                                && !currentMaterial.getAliquotedFromLSID().equals(aliquotParent.getName())) // for insert using merge, parent name is temporarily stored as lsid
                            throw new ValidationException("Aliquot parents cannot be updated for sample " + currentMaterial.getName() + ".");
                        else if (currentMaterial.getAliquotedFromLSID().equals(aliquotParent.getLSID())) // when AliquotedFromLSID is lsid, aliquot is already processed
                            aliquotParent = null; // already exist, not need to recreate
                    }
                }

                Map<ExpMaterial, String> existingParentMaterials = new HashMap<>();
                if (isExistingAliquot && currentParents.second.size() > 1)
                    throw new ValidationException("Invalid parents for aliquot " + runItem.getName() + ".");

                if (!isAliquot)
                {
                    for (ExpMaterial materialParent : currentParents.second)
                    {
                        ExpSampleType sampleType = materialParent.getSampleType();
                        String role = sampleRole(materialParent);
                        if (sampleType != null && !parentMaterials.containsValue(role) && !parentSampleTypesToRemove.contains(role))
                            existingParentMaterials.put(materialParent, role);
                    }
                    parentMaterials.putAll(existingParentMaterials);
                }
            }
        }

        RunInputOutputBean parents = null;

        if (!parentMaterials.isEmpty() || !parentData.isEmpty() || !parentDataTypesToRemove.isEmpty() || !parentSampleTypesToRemove.isEmpty() || aliquotParent != null)
            parents = new RunInputOutputBean(parentMaterials, parentData, aliquotParent, !parentDataTypesToRemove.isEmpty() || !parentSampleTypesToRemove.isEmpty());

        RunInputOutputBean children = null;
        if (!childMaterials.isEmpty() || !childData.isEmpty())
            children = new RunInputOutputBean(childMaterials, childData, null);

        return Pair.of(parents, children);
    }

    private static String sampleRole(ExpMaterial material)
    {
        ExpSampleType st = material.getSampleType();
        return st != null ? st.getName() : "Sample";
    }

    private static String dataRole(ExpData data, User user)
    {
        ExpDataClass dc = data.getDataClass(user);
        return dc != null ? dc.getName() : ExpDataRunInput.DEFAULT_ROLE;
    }

    public static class SearchIndexIteratorBuilder implements DataIteratorBuilder
    {
        final DataIteratorBuilder _pre;
        final Function<SearchIndexDataKeys, Runnable> _indexFunction;

        public SearchIndexIteratorBuilder(DataIteratorBuilder pre, Function<SearchIndexDataKeys, Runnable> indexFunction)
        {
            _pre = pre;
            _indexFunction = indexFunction;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _pre.getDataIterator(context);
            return LoggingDataIterator.wrap(new SearchIndexIterator(pre, context, _indexFunction));
        }
    }

    public record SearchIndexDataKeys(@NotNull List<Integer> orderedRowIds, @NotNull List<String> lsids) { }

    private static class SearchIndexIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final Integer _lsidCol;
        final Integer _rowIdCol;
        final ArrayList<String> _lsids;
        final ArrayList<Integer> _rowIds;
        final Function<SearchIndexDataKeys, Runnable> _indexFunction;
        final boolean _isInsert;

        protected SearchIndexIterator(DataIterator di, DataIteratorContext context, Function<SearchIndexDataKeys, Runnable> indexFunction)
        {
            super(di);
            _context = context;
            _indexFunction = indexFunction;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);

            _lsidCol = map.get("lsid");
            _rowIdCol = map.get("rowId");
            _lsids = new ArrayList<>(100);
            _rowIds = new ArrayList<>(100);

            _isInsert = !context.getInsertOption().allowUpdate; // only useRowIdCol for INSERT. For UPDATE, rowId usually is not available. For MERGE, rowId is a new DBSequence value for existing data
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            if (hasNext)
            {
                Integer rowId = null;
                String lsid = null;
                if (_isInsert)
                {
                    rowId = _rowIdCol == null ? null : (Integer) get(_rowIdCol);
                    if (rowId == null)
                        lsid = _lsidCol == null ? null : (String) get(_lsidCol);
                }
                else
                {
                    Map<String, Object> map = getExistingRecord();
                    if (map != null)
                    {
                        if (map.containsKey("rowId")) // favor rowId over lsid to avoid additional query for indexing
                            rowId = (Integer) map.get("rowId");
                        if (rowId == null && map.containsKey("lsid"))
                            lsid = (String) map.get("lsid");
                    }

                    // for UPDATE/MERGE, don't use _rowIdCol
                    if (rowId == null && lsid == null)
                        lsid = _lsidCol == null ? null : (String) get(_lsidCol);
                }

                if (rowId != null)
                    _rowIds.add(rowId);
                if (lsid != null)
                    _lsids.add(lsid);
            }
            else
            {
                final SearchService ss = SearchService.get();
                if (null != ss)
                {
                    final ArrayList<String> lsids = new ArrayList<>(_lsids);
                    final ArrayList<Integer> rowIds = new ArrayList<>(_rowIds);
                    Collections.sort(rowIds);
                    final Runnable indexTask = _indexFunction.apply(new SearchIndexDataKeys(rowIds, lsids));

                    if (null != DbScope.getLabKeyScope())
                        DbScope.getLabKeyScope().addCommitTask(indexTask, DbScope.CommitTaskOption.POSTCOMMIT);
                    else
                        indexTask.run();
                }
            }
            return hasNext;
        }
    }

    // This should be used AFTER StandardDataIteratorBuilder, say at the beginning of PersistDataIteratorBuilder (below)
    // The incoming dataiterator should bound to target table and have complete ColumnInfo metadata
    // see SimpleQueryUpdateService.convertTypes() for similar handling of FILE_LINK columns
    public static class FileLinkDataIterator extends WrapperDataIterator
    {
        Supplier<Object>[] suppliers;
        String[] savedFileName;
        FileColumnValueMapper fileColumnValueMapping = new FileColumnValueMapper();

        FileLinkDataIterator(final DataIterator in, final DataIteratorContext context, Container c, User user, String fileLinkDirName)
        {
            super(in);
            suppliers = new Supplier[in.getColumnCount() + 1];
            savedFileName = new String[in.getColumnCount() + 1];

            for (int i = 0; i < suppliers.length; i++)
            {
                ColumnInfo col = in.getColumnInfo(i);
                if (PropertyType.FILE_LINK != col.getPropertyType())
                {
                    suppliers[i] = in.getSupplier(i);
                }
                else
                {
                    final int index = i;
                    suppliers[i] = () -> {
                        if (savedFileName[index] != null)
                            return savedFileName[index];
                        Object value = in.get(index);
                        if (value instanceof MultipartFile || value instanceof AttachmentFile)
                        {
                            try
                            {
                                Path path = AssayFileWriter.getUploadDirectoryPath(c, fileLinkDirName);
                                Object file = fileColumnValueMapping.saveFileColumnValue(user, c, path, col.getName(), value);
                                assert file instanceof File;
                                value = ((File)file).getPath();
                                savedFileName[index] = (String)value;
                            }
                            catch (QueryUpdateServiceException ex)
                            {
                                context.getErrors().addRowError(new ValidationException(ex.getMessage()));
                                value = null;
                            }
                            catch (ValidationException vex)
                            {
                                context.getErrors().addRowError(vex);
                                value = null;
                            }
                        }
                        return value;
                    };
                }
            }
        }

        @Override
        public Object get(int i)
        {
            return suppliers[i].get();
        }

        @Override
        public Supplier<Object> getSupplier(int i)
        {
            return suppliers[i];
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            Arrays.fill(savedFileName, null);
            return super.next();
        }
    }

    public static final Set<String> NOT_FOR_UPDATE = Sets.newCaseInsensitiveHashSet(
            ExpDataTable.Column.RowId.toString(),
            ExpDataTable.Column.LSID.toString(),
            ExpDataTable.Column.Created.toString(),
            ExpDataTable.Column.CreatedBy.toString(),
            AliquotedFromLSID.toString(),
            RootMaterialRowId.toString(),
            "genId");

    public static class PersistDataIteratorBuilder implements DataIteratorBuilder
    {
        private final DataIteratorBuilder _in;
        private final TableInfo _expTable;
        private final TableInfo _propertiesTable;
        private final ExpObject _dataTypeObject;
        private final Container _container;
        private final User _user;
        private final Set<String> _excludedColumns = new HashSet<>(List.of("generated","runId","sourceapplicationid")); // generated has database DEFAULT 0

        private String _fileLinkDirectory = null;
        Function<SearchIndexDataKeys, Runnable> _indexFunction;
        final Map<String, String> _importAliases;

        // expTable is the shared experiment table e.g. exp.Data or exp.Materials
        public PersistDataIteratorBuilder(@NotNull DataIteratorBuilder in, TableInfo expTable, TableInfo propsTable, ExpObject typeObject, Container container, User user, Map<String, String> importAliases, @Nullable Integer ownerObjectId)
        {
            _in = in;
            _expTable = expTable;
            _propertiesTable = propsTable;
            _dataTypeObject = typeObject;
            _container = container;
            _user = user;
            _importAliases = importAliases != null ? new CaseInsensitiveHashMap<>(importAliases) : new CaseInsensitiveHashMap<>();
        }

        public PersistDataIteratorBuilder setIndexFunction(Function<SearchIndexDataKeys, Runnable> indexFunction)
        {
            _indexFunction = indexFunction;
            return this;
        }

        public PersistDataIteratorBuilder setFileLinkDirectory(String dir)
        {
            _fileLinkDirectory = dir;
            return this;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator input = _in.getDataIterator(context);
            if (null == input)
                return null;           // Can happen if context has errors

            // useTransactionAuditCache already set for import and merge in AbstractQueryImportAction.createDataIteratorContext
            if (context.getInsertOption() == QueryUpdateService.InsertOption.INSERT)
                context.setUseTransactionAuditCache(true);

            // add FileLink DataIterator if any input columns are of type FILE_LINK
            if (null != _fileLinkDirectory)
            {
                boolean hasFileLink = false;
                // Issue 50299: getColumnCount() subtracts 1 for the _rowNumber column at the 0-index,
                // so we need <= here to make sure to check all columnInfos (see comment at top of DataIterator.java re: 1-based and _rowNumber column)
                for (int i = 0; i <= input.getColumnCount(); i++)
                    hasFileLink |= PropertyType.FILE_LINK == input.getColumnInfo(i).getPropertyType();
                if (hasFileLink)
                    input = LoggingDataIterator.wrap(new FileLinkDataIterator(input, context, _container, _user, _fileLinkDirectory));
            }

            final Map<String, Integer> colNameMap = DataIteratorUtil.createColumnNameMap(input);

            assert _expTable instanceof ExpMaterialTableImpl || _expTable instanceof ExpDataClassDataTableImpl;
            boolean isSample = _expTable instanceof ExpMaterialTableImpl;

            SimpleTranslator step1 = new SimpleTranslator(input, context);
            step1.selectAll(Sets.newCaseInsensitiveHashSet("alias"), _importAliases);
            if (colNameMap.containsKey("alias"))
                step1.addColumn(ExperimentService.ALIASCOLUMNALIAS, colNameMap.get("alias")); // see AliasDataIteratorBuilder

            CaseInsensitiveHashSet dontUpdate = new CaseInsensitiveHashSet();
            dontUpdate.addAll(NOT_FOR_UPDATE);
            dontUpdate.add("rowid"); // rowid is added / not dropped for dataclass for QueryUpdateAuditEvent.rowpk audit purpose
            if (context.getInsertOption().updateOnly)
            {
                dontUpdate.add("objectid");
                dontUpdate.add("cpastype");
                dontUpdate.add("lastindexed");
            }

            boolean isMergeOrUpdate = context.getInsertOption().allowUpdate;
            boolean isUpdateUsingLsid = context.getInsertOption().updateOnly && colNameMap.containsKey("lsid") && context.getConfigParameterBoolean(ExperimentService.QueryOptions.UseLsidForUpdate);

            CaseInsensitiveHashSet keyColumns = new CaseInsensitiveHashSet();
            CaseInsensitiveHashSet propertyKeyColumns = new CaseInsensitiveHashSet();
            if (!isMergeOrUpdate)
                keyColumns.add(ExpDataTable.Column.LSID.toString());

            if (isSample)
            {
                if (isMergeOrUpdate)
                {
                    if (isUpdateUsingLsid)
                    {
                        keyColumns.add(ExpDataTable.Column.LSID.toString());
                    }
                    else
                    {
                        keyColumns.addAll(((ExpMaterialTableImpl) _expTable).getAltMergeKeys(context));
                        propertyKeyColumns.add("name");
                    }
                }

                dontUpdate.addAll(((ExpMaterialTableImpl) _expTable).getUniqueIdFields());
                dontUpdate.add(RootMaterialRowId.toString());
                dontUpdate.add(AliquotedFromLSID.toString());
                dontUpdate.add(ExpMaterialTable.Column.AliquotCount.name());
                dontUpdate.add(ExpMaterialTable.Column.AliquotVolume.name());
                dontUpdate.add(ExpMaterialTable.Column.AvailableAliquotCount.name());
                dontUpdate.add(ExpMaterialTable.Column.AvailableAliquotVolume.name());
            }
            else if (isMergeOrUpdate)
            {
                if (isUpdateUsingLsid)
                {
                    keyColumns.add(ExpDataTable.Column.LSID.toString());
                }
                else
                {
                    keyColumns.addAll(((ExpDataClassDataTableImpl) _expTable).getAltMergeKeys(context));
                }
            }

            // Since we support detailed audit logging add the ExistingRecordDataIterator here just before TableInsertDataIterator
            // this is a NOOP unless we are merging/updating and detailed logging is enabled
            DataIteratorBuilder step2a = ExistingRecordDataIterator.createBuilder(step1, _expTable, keyColumns, true);

            // Add RootMaterialRowId if it does not exist
            DataIteratorBuilder step2b = ctx -> {
                DataIterator in = step2a.getDataIterator(ctx);
                var map = DataIteratorUtil.createColumnNameMap(in);
                if (map.containsKey(RootMaterialRowId.toString()) || !map.containsKey(RowId.toString()))
                    return in;
                var ret = new SimpleTranslator(in, ctx);
                ret.selectAll();
                ret.addAliasColumn(RootMaterialRowId.toString(), map.get(RowId.toString()));
                return ret;
            };

            DataIteratorBuilder step2c = step2b;
            if (isSample && isMergeOrUpdate)
            {
                step2c = LoggingDataIterator.wrap(new ExpDataIterators.SampleStatusCheckIteratorBuilder(step2b, _container));
            }

            // Insert into exp.data then the provisioned table
            // Use embargo data iterator to ensure rows are committed before being sent along Issue 26082 (row at a time, reselect rowid)
            DataIteratorBuilder step3 = LoggingDataIterator.wrap(new TableInsertDataIteratorBuilder(step2c, _expTable, _container)
                    .setKeyColumns(keyColumns)
                    .setDontUpdate(dontUpdate)
                    .setAddlSkipColumns(_excludedColumns)
                    .setCommitRowsBeforeContinuing(true)
                    .setFailOnEmptyUpdate(false));

            // pass in remap columns to help reconcile columns that may be aliased in the virtual table
            DataIteratorBuilder step4 = LoggingDataIterator.wrap(new TableInsertDataIteratorBuilder(step3, _propertiesTable, _container)
                    .setKeyColumns(propertyKeyColumns.isEmpty() ? keyColumns : propertyKeyColumns)
                    .setDontUpdate(dontUpdate)
                    .setVocabularyProperties(PropertyService.get().findVocabularyProperties(_container, colNameMap.keySet()))
                    .setRemapSchemaColumns(((UpdateableTableInfo)_expTable).remapSchemaColumns())
                    .setFailOnEmptyUpdate(false));

            DataIteratorBuilder step5 = step4;
            if (colNameMap.containsKey("flag") || colNameMap.containsKey("comment"))
            {
                step5 = LoggingDataIterator.wrap(new ExpDataIterators.FlagDataIteratorBuilder(step4, _user, isSample, _dataTypeObject, _container));
            }

            // Wire up derived parent/child data and materials
            DataIteratorBuilder step6 = LoggingDataIterator.wrap(new ExpDataIterators.DerivationDataIteratorBuilder(step5, _container, _user, isSample, _dataTypeObject, false, false/*Validation already done in StandardDataIterator*/));

            DataIteratorBuilder step7 = step6;
            boolean hasRollUpColumns = colNameMap.containsKey(ROOT_RECOMPUTE_ROWID_COL);
            if (isSample && !context.getConfigParameterBoolean(SampleTypeService.ConfigParameters.DeferAliquotRuns) && hasRollUpColumns)
                step7 = LoggingDataIterator.wrap(new ExpDataIterators.AliquotRollupDataIteratorBuilder(step6, _container));

            // Hack: add the alias and lsid values back into the input, so we can process them in the chained data iterator
            DataIteratorBuilder step8 = step7;
            if (null != _indexFunction)
                step8 = LoggingDataIterator.wrap(new ExpDataIterators.SearchIndexIteratorBuilder(step7, _indexFunction)); // may need to add this after the aliases are set

            return LoggingDataIterator.wrap(step8.getDataIterator(context));
        }
    }

    public static class MultiDataTypeCrossProjectDataIterator extends WrapperDataIterator
    {
        private static final Set<String> IGNORED_FIELD_NAMES = Set.of("lsid", "genid");
        private static final Set<String> SAMPLE_TYPE_FIELD_NAMES = Set.of("SampleType", "Sample Type");
        private static final Set<String> CONTAINER_FIELD_NAMES = Set.of("Container", "Folder");
        private static final int BATCH_SIZE = 1000;
        record TypeData(
                Container container,
                ExpObject dataType,
                TableInfo tableInfo,
                File dataFile,
                List<Integer> fieldIndexes,
                Map<Integer, String> dependencyIndexes,
                List<String> dataRows,
                List<String> dataIds
        ) { }

        private final DataIteratorContext _context;
        private final boolean _isCrossType;
        private final boolean _isCrossFolder;
        private final boolean _isSamples;
        private final ExpObject _dataType;
        private final Container _container;
        private final User _user;
        private Integer _typeColIndex = null;
        private String _typeColName = null;
        private Integer _folderColIndex = null;
        // want to process the sample types in the order given in the original file, unless we have dependencies
        private final Map<String, Map<String, TypeData>> _typeFolderDataMap = new TreeMap<>();
        private final Map<String, TypeData> _updateOnlyFileDataMap = new LinkedHashMap<>();
        private final Map<String, Set<String>> _orderDependencies = new HashMap<>();
        private final int _dataIdIndex;
        private final Map<String, Set<String>> _idsPerType = new HashMap<>();
        private final Map<String, Set<String>> _parentIdsPerType = new HashMap<>();

        private final Map<String, Container> _containerMap = new HashMap<>();

        private final boolean _isCrossFolderUpdate;

        private final TSVWriter _tsvWriter;


        public MultiDataTypeCrossProjectDataIterator(DataIterator di, DataIteratorContext context, Container container, User user, boolean isCrossType, boolean isCrossFolder, ExpObject dataType, boolean isSamples)
        {
            super(di);
            _context = context;
            _container = container;
            _isSamples = isSamples;
            _dataType = dataType;
            _user = user;
            _isCrossType = isCrossType;
            _isCrossFolder = isCrossFolder;
            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);

            _dataIdIndex = map.getOrDefault("Name", -1);

            _tsvWriter = new TSVWriter() // Used to quote values with newline/tabs/quotes
            {
                @Override
                protected int write()
                {
                    throw new UnsupportedOperationException();
                }
            };

            _isCrossFolderUpdate = isCrossFolder && context.getInsertOption().updateOnly;

            if (_isCrossType && _isSamples) //cross type only supported for samples
            {
                SAMPLE_TYPE_FIELD_NAMES.forEach(name -> {
                    if (map.get(name) != null)
                    {
                        if (_typeColIndex != null)
                            _context.getErrors().addRowError(new ValidationException("Only one of [" + SAMPLE_TYPE_FIELD_NAMES.stream().sorted().collect(Collectors.joining(", ")) + "] allowed for import."));
                        _typeColIndex = map.get(name);
                        _typeColName = di.getColumnInfo(_typeColIndex).getName();
                    }
                });
                if (_typeColIndex == null)
                    _context.getErrors().addRowError(new ValidationException("Could not determine sample type. Please provide a 'Sample Type' column in the data."));
            }

            if (_isCrossFolder)
            {
                CONTAINER_FIELD_NAMES.forEach(name -> {
                    if (map.get(name) != null)
                    {
                        if (_folderColIndex != null)
                            _context.getErrors().addRowError(new ValidationException("Only one of [" + CONTAINER_FIELD_NAMES.stream().sorted().collect(Collectors.joining(", ")) + "] allowed for import."));
                        _folderColIndex = map.get(name);
                    }
                });

                if (_folderColIndex != null || _isCrossFolderUpdate)
                {
                    ContainerFilter cf = ContainerFilter.current(container);
                    if (container.isProductFoldersEnabled())
                        cf = new ContainerFilter.AllInProjectPlusShared(container, user);
                    Collection<GUID> validContainerIds =  cf.getIds();
                    if (cf instanceof ContainerFilter.ContainerFilterWithPermission cfp)
                    {
                        validContainerIds = cfp.generateIds(container, context.getInsertOption().allowUpdate ? UpdatePermission.class : InsertPermission.class, null);
                    }

                    if (validContainerIds != null)
                    {
                        for (GUID containerId : validContainerIds)
                        {
                            Container validContainer = ContainerManager.getForId(containerId);
                            _containerMap.put(validContainer.getId(), validContainer);
                            _containerMap.put(validContainer.getName(), validContainer); // for multi-type import, container column lookup is not yet resolved
                        }
                    }
                }
            }
        }

        private void _importPartition(TypeData typeData)
        {
            if (_context.getErrors().hasErrors())
                return;

            writeRowsToFile(typeData); // write the last rows that have been collected since the last write, if any
            var updateService = typeData.tableInfo.getUpdateService();
            if (updateService == null)
            {
                _context.getErrors().addRowError(new ValidationException("No update service available for sample type '" + typeData.dataType.getName() + "'."));
            }
            else
            {
                try (DataLoader loader = DataLoader.get().createLoader(typeData.dataFile, "text/plain", true, null, null))
                {
                    // We do not need to configure the loader for renamed columns as that has been taken care of when writing the file.
                    configureLoader(loader, typeData.tableInfo, null, true);
                    updateService.loadRows(_user, typeData.container, loader, _context, null);
                }
                catch (SQLException | IOException e)
                {
                    String msg = "Problem importing data for sample type '" + typeData.dataType.getName() + "'. ";
                    LOG.error(msg, e);
                    _context.getErrors().addRowError(new ValidationException(msg));
                }
            }
        }

        /**
         *
         * @param typeData
         * @return true if it has data from multiple containers, or from a container different from current container
         */
        private boolean handleCrossFolderUpdate(TypeData typeData)
        {
            String headerRow = typeData.dataRows.get(0);
            String dataFileName = typeData.dataFile.getName();

            ExpObject dataType = typeData.dataType;

            Map<String, List<Integer>> containerRows = new HashMap<>();
            boolean hasCrossFolderData = false;

            TableInfo tableInfo = null;
            SimpleFilter filter = null;

            if (_isSamples)
            {
                filter = new SimpleFilter(FieldKey.fromParts("MaterialSourceId"), dataType.getRowId());
                filter.addCondition(FieldKey.fromParts("Name"), typeData.dataIds, CompareType.IN);
                tableInfo = ExperimentService.get().getTinfoMaterial();
            }
            else
            {
                filter = new SimpleFilter(FieldKey.fromParts("ClassId"), dataType.getRowId());
                filter.addCondition(FieldKey.fromParts("Name"), typeData.dataIds, CompareType.IN);
                tableInfo = ExperimentService.get().getTinfoData();
            }

            Map<String, Object>[] rows = new TableSelector(tableInfo, Set.of("name", "container"), filter, null).getMapArray();
            for (Map<String, Object> row : rows)
            {
                String name = (String) row.get("name");
                String dataContainer = (String) row.get("container");
                int sampleRowId = typeData.dataIds.indexOf(name) + 1 /* header row */;

                if (!containerRows.containsKey(dataContainer))
                {
                    containerRows.put(dataContainer, new ArrayList<>());
                    hasCrossFolderData = hasCrossFolderData || !dataContainer.equalsIgnoreCase(_container.getId());
                }

                containerRows.get(dataContainer).add(sampleRowId);
            }

            if (!hasCrossFolderData)
                return false;

            Map<String, TypeData> containerTypeDatas = new HashMap<>();
            Map<String, Integer> containerIndexes = new HashMap<>();
            for (String containerId : containerRows.keySet())
            {
                Container container = _containerMap.get(containerId);
                if (container == null)
                {
                    Container folder = ContainerManager.getForId(containerId);
                    _context.getErrors().addRowError(new ValidationException("You don't have the required permission to update " + (_isSamples ? "samples" : "data") + " in the container: " + (folder != null ? folder.getName() : containerId)));
                    return false;
                }

                AbstractExpSchema schema = _isSamples ? new SamplesSchema(_user, container) : new DataClassUserSchema(container, _user);
                QueryDefinition qDef = schema.getQueryDefForTable(dataType.getName());
                TableInfo dataTable = qDef.getTable(schema, new ArrayList<>(), true);

                if (dataTable == null)
                {
                    _context.getErrors().addRowError(new ValidationException("Table for " + (_isSamples ? "sample type" : "dataclass") + " '" + dataType.getName() + "' not found."));
                    return false;
                }

                List<String> dataRows = new ArrayList<>();
                dataRows.add(headerRow);

                File dataFile;
                try
                {
                    dataFile = FileUtil.createTempFile("~containerSplit~", container.getRowId() + dataFileName);
                }
                catch (IOException e)
                {
                    String msg = "Problem importing data for " + (_isSamples ? "sample type" : "dataclass") + " '" + typeData.dataType.getName() + "'. ";
                    LOG.error(msg, e);
                    _context.getErrors().addRowError(new ValidationException(msg));
                    return false;
                }
                List<Integer> dataRowIndexes = containerRows.get(containerId);
                Collections.sort(dataRowIndexes);
                containerIndexes.put(containerId, dataRowIndexes.get(0));
                for (Integer dataRowIndex : dataRowIndexes)
                    dataRows.add(typeData.dataRows.get(dataRowIndex));

                TypeData containerTypeData = new TypeData(container, dataType, dataTable, dataFile, typeData.fieldIndexes, typeData.dependencyIndexes, dataRows, new ArrayList<>());
                containerTypeDatas.put(containerId, containerTypeData);
            }

            List<String> orderedContainer = new ArrayList<>(containerRows.keySet());
            orderedContainer.sort(Comparator.comparing(containerIndexes::get)); // respect original row ordering
            for (String containerId : orderedContainer)
            {
                if (_context.getErrors().hasErrors())
                    return false;

                TypeData containerTypeData = containerTypeDatas.get(containerId);
                _updateOnlyFileDataMap.put(containerId + "-" + dataType.getRowId(), containerTypeData);
                _importPartition(containerTypeData);
            }

            return true;
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            if (_context.getErrors().hasErrors())
                return hasNext;

            if (!hasNext)
            {
                Collection<String> importOrderKeys = getImportOrderTypeKeys();
                if (!_context.getErrors().hasErrors())
                {
                    _context.setCrossTypeImport(false);
                    _context.setCrossFolderImport(false);

                    boolean hasCrossFolderImport = false;

                      // process the individual files
                    for (String key : importOrderKeys)
                    {
                        Map<String, TypeData> typeFolderData = _typeFolderDataMap.get(key);
                        hasCrossFolderImport = hasCrossFolderImport || typeFolderData.keySet().size() > 1;
                        for (TypeData typeData : typeFolderData.values())
                        {
                            if (!_context.getErrors().hasErrors()) // Issue 48402: Stop early since the transaction may have been aborted
                            {
                                if (_isCrossFolderUpdate)
                                {
                                    if (!handleCrossFolderUpdate(typeData))
                                        _importPartition(typeData);
                                }
                                else
                                    _importPartition(typeData);
                            }
                        }
                    }

                    if (_isCrossFolder && !_context.getInsertOption().updateOnly && hasCrossFolderImport) // all updates are cross-folder due to lack of Container column
                        SimpleMetricsService.get().increment(ExperimentService.MODULE_NAME, _isSamples ? "sampleImport" : "dataClassImport", "multiFolderImport");

                    _context.setCrossTypeImport(_isCrossType);
                    _context.setCrossFolderImport(_isCrossFolder);
                }

                return false;
            }
            else
            {
                TypeData typeFolderData = null;
                String typeName;
                if (_typeColIndex != null)
                {
                    Object typeNameObj = get(_typeColIndex);
                    if (typeNameObj == null)
                        typeName = null;
                    else
                        typeName = StringUtils.trim(String.valueOf(typeNameObj));
                }
                else
                    typeName = _dataType.getName();
                Container targetContainer = _container; // default to context container

                if (StringUtils.isEmpty(typeName) && _isCrossType)
                    _context.getErrors().addRowError(new ValidationException("No value provided for '" + _typeColName + "'."));
                else
                {
                    if (_isCrossFolder && _folderColIndex != null)
                    {
                        String rowFolderId = StringUtils.trim((String) get(_folderColIndex));
                        if (!StringUtils.isEmpty(rowFolderId))
                        {
                            targetContainer = _containerMap.get(rowFolderId);
                            if (targetContainer == null)
                            {
                                _context.getErrors().addRowError(new ValidationException("Invalid value provided for 'Container'."));
                                return true;
                            }
                        }
                    }

                    Map<String, TypeData> typeFolderMap = _typeFolderDataMap.computeIfAbsent(typeName, k -> new LinkedHashMap<>());
                    typeFolderData = typeFolderMap.get(targetContainer.getId());
                    if (typeFolderData == null)
                    {
                        if (_isSamples)
                        {
                            ExpSampleTypeImpl sampleType = _typeColIndex != null ? (ExpSampleTypeImpl) SampleTypeService.get().getSampleType(targetContainer, _user, typeName) : (ExpSampleTypeImpl) _dataType;
                            if (sampleType == null)
                                _context.getErrors().addRowError(new ValidationException(_typeColName + " '" + typeName + "' not found.") );
                            else
                            {
                                try
                                {
                                    typeFolderData = createSampleHeaderRow(sampleType, targetContainer);
                                    if (typeFolderData != null)
                                        typeFolderMap.put(targetContainer.getId(), typeFolderData);
                                }
                                catch (IOException e)
                                {
                                    _context.getErrors().addRowError(new ValidationException("Error writing file for '" + sampleType.getName() + "'."));
                                }
                            }
                        }
                        else
                        {
                            try
                            {
                                typeFolderData = createDataClassHeaderRow((ExpDataClass) _dataType, targetContainer);
                                if (typeFolderData != null)
                                    typeFolderMap.put(targetContainer.getId(), typeFolderData);
                            }
                            catch (IOException e)
                            {
                                _context.getErrors().addRowError(new ValidationException("Error writing file for '" + _dataType.getName() + "'."));
                            }
                        }
                    }
                }

                if (typeFolderData != null)
                {
                    addDataRow(typeFolderData);
                }

                return true;
            }
        }

        @Override
        public void close() throws IOException
        {
            _typeFolderDataMap.values().forEach(typeMap -> {
                typeMap.values().forEach(typeData -> {
                    if (typeData.dataFile != null)
                        typeData.dataFile.delete();
                });
            });
            _updateOnlyFileDataMap.values().forEach(typeData -> {
                if (typeData.dataFile != null)
                    typeData.dataFile.delete();
            });
            super.close();
        }

        private Collection<String> getImportOrderTypeKeys()
        {
            if (!_isSamples)
                return _typeFolderDataMap.keySet(); // dataclass cross type not supported

            List<String> keys = new ArrayList<>();
            Set<String> allKeys = _typeFolderDataMap.keySet();
            if (allKeys.size() == 1)
                return allKeys;
            // if the parents referenced in the file are not samples that are potentially being created in this file, there is no dependency
            _idsPerType.forEach((typeName, ids) -> {
                Set<String> parentIds = _parentIdsPerType.get(typeName);
                if (parentIds != null && ids.stream().noneMatch(parentIds::contains))
                {
                    _orderDependencies.values().forEach(set -> set.remove(typeName));
                }
            });
            // if the parent type referenced as parent input, but no data of that type is being created, there is no dependency
            _orderDependencies.values().forEach(set -> {
                Set<String> parentTypeNotBeingCreated = new HashSet<>();
                for (String dep : set)
                {
                    if (!allKeys.contains(dep))
                        parentTypeNotBeingCreated.add(dep);
                }
                set.removeAll(parentTypeNotBeingCreated);
            });

            allKeys.forEach(key -> {
                if (!_orderDependencies.containsKey(key) || _orderDependencies.get(key).isEmpty())
                {
                    keys.add(key);
                    _orderDependencies.values().forEach(set -> set.remove(key));
                }
            });

            boolean hasCycle = false;
            while (keys.size() != allKeys.size() && !hasCycle)
            {
                Set<String> addedTypeNames = new HashSet<>();
                _orderDependencies.forEach((typeName, dependencies) -> {
                    if (dependencies.isEmpty() && !keys.contains(typeName))
                    {
                        keys.add(typeName);
                        addedTypeNames.add(typeName);
                    }
                });
                if (addedTypeNames.isEmpty())
                    hasCycle = true;
                else
                    _orderDependencies.values().forEach(set -> set.removeAll(addedTypeNames));
            }
            if (hasCycle)
            {
                if (_context.getInsertOption().allowUpdate)
                    LOG.warn("Possible derivation circular dependencies when updates are allowed. Using ordering of sample types based data rows.");
                else
                    _context.getErrors().addRowError(new ValidationException("Unable to determine ordering for sample type imports. " +
                            "A cycle of derivation dependencies among the sample types exists. " +
                            "Adjust or remove dependencies for this import."));
                return _typeFolderDataMap.keySet();
            }

            return keys;
        }

        private TypeData createDataClassHeaderRow(ExpDataClass dataClass, Container container) throws IOException
        {
            List<QueryException> qpe = new ArrayList<>();
            DataClassUserSchema schema = new DataClassUserSchema(container, _user);
            QueryDefinition qDef = schema.getQueryDefForTable(dataClass.getName());
            TableInfo dataTable = qDef.getTable(schema, qpe, true);
            if (dataTable == null)
            {
                _context.getErrors().addRowError(new ValidationException("Table for dataclass '" + dataClass.getName() + "' not found."));
                return null;
            }

            List<Integer> fieldIndexes = new ArrayList<>();
            List<String> header = new ArrayList<>();

            for (int i = 1; i <= getColumnCount(); i++)
            {
                ColumnInfo colInfo = getColumnInfo(i);
                String name = colInfo.getName();

                fieldIndexes.add(i);
                header.add(name);
            }

            File dataFile = FileUtil.createTempFile("~importSplit-", container.getRowId() + dataClass.getName() + ".tsv");

            List<String> dataRows = new ArrayList<String>();
            dataRows.add(StringUtils.join(header, "\t"));
            return new TypeData(container, _dataType, dataTable, dataFile, fieldIndexes, Collections.emptyMap(), dataRows, new ArrayList<>());
        }

        private TypeData createSampleHeaderRow(ExpSampleTypeImpl sampleType, Container container) throws IOException
        {
            List<QueryException> qpe = new ArrayList<>();
            SamplesSchema schema = new SamplesSchema(_user, container);
            QueryDefinition qDef = schema.getQueryDefForTable(sampleType.getName());
            TableInfo samplesTable = qDef.getTable(schema, qpe, true);
            if (samplesTable == null)
            {
                _context.getErrors().addRowError(new ValidationException("Table for sample type '" + sampleType.getName() + "' not found."));
                return null;
            }
            File dataFile = FileUtil.createTempFile("~importSplit-", container.getRowId() + sampleType.getName() + ".tsv");
            Set<String> validFields = new CaseInsensitiveHashSet();
            samplesTable.getColumns().forEach(column -> {
                if (!IGNORED_FIELD_NAMES.contains(column.getName()))
                {
                    validFields.add(column.getName());
                    validFields.addAll(column.getImportAliasSet());
                    validFields.add(column.getLabel());
                }
            });
            Map<String, String> aliasMap = sampleType.getImportAliases();
            validFields.addAll(aliasMap.keySet());
            validFields.add(ALIQUOTED_FROM_INPUT);
            validFields.add("StorageUnit");
            validFields.add("Storage Unit");
            // For consistency with other storage fields that are imported without spaces in the names
            validFields.add("EnteredStorage");
            List<Integer> fieldIndexes = new ArrayList<>();
            Map<Integer, String> dependencyIndexes = new HashMap<>();
            List<String> header = new ArrayList<>();
            // index is 1-based; column 0 is the rowNumber
            for (int i = 1; i <= getColumnCount(); i++)
            {
                ColumnInfo colInfo = getColumnInfo(i);
                String name = colInfo.getName();
                String lcName = name.toLowerCase();
                if (validFields.contains(name))
                {
                    fieldIndexes.add(i);
                    header.add(name);
                }
                if (lcName.startsWith(MATERIAL_INPUTS_PREFIX_LC))
                {
                    fieldIndexes.add(i);
                    header.add(name);
                    // no dependencies to register if the names of samples are not being provided in the file.
                    if (_dataIdIndex != -1)
                    {
                        String typeName = name.replaceAll("(?i)" + MATERIAL_INPUTS_PREFIX_LC, "");
                        if (!sampleType.getName().equals(typeName))
                            dependencyIndexes.put(i, typeName);
                    }
                }
                else if (lcName.startsWith(DATA_INPUTS_PREFIX_LC))
                {
                    fieldIndexes.add(i);
                    header.add(name);
                }
                else if (lcName.startsWith(INPUTS_PREFIX_LC))
                {
                    fieldIndexes.add(i);
                    header.add(name);
                    if (_dataIdIndex != -1)
                        dependencyIndexes.put(i, name.replaceAll("(?i)" + INPUTS_PREFIX_LC, ""));
                }
                else if (aliasMap.containsKey(name) && _dataIdIndex != -1)
                {
                    String aliasTarget = aliasMap.get(name);
                    if (aliasTarget.toLowerCase().startsWith(MATERIAL_INPUTS_PREFIX_LC))
                        dependencyIndexes.put(i, aliasTarget
                            .replaceAll("(?i)" + MATERIAL_INPUTS_PREFIX_LC, "")
                        );
                }
            }

            if (header.isEmpty())
            {
                _context.getErrors().addRowError(new ValidationException("No columns found for sample type '" + sampleType.getName() + "'."));
                return null;
            }

            List<String> dataRows = new ArrayList<String>();
            dataRows.add(StringUtils.join(header, "\t"));
            return new TypeData(container, sampleType, samplesTable, dataFile, fieldIndexes, dependencyIndexes, dataRows, new ArrayList<>());
        }

        private Object getSerializingObject(Object data)
        {
            if (data instanceof Date d && !(data instanceof Time))
                return DateUtil.formatIsoDateLongTime(d, true);
            if (data instanceof String s)
                return _tsvWriter.quoteValue(s.trim());
            return data;
        }

        private void addDataRow(TypeData typeData)
        {
            if (typeData.dataRows.size() == BATCH_SIZE)
                writeRowsToFile(typeData);

            List<Object> dataRow = new ArrayList<>();
            typeData.fieldIndexes.forEach(index -> {
                Object data = get(index);
                dataRow.add(getSerializingObject(data));
                if (data != null)
                {
                    if (index == _dataIdIndex)
                    {
                        _idsPerType.computeIfAbsent(typeData.dataType.getName(), k -> new HashSet<>()).add(data.toString());
                        if (_isCrossFolderUpdate)
                            typeData.dataIds.add(data.toString());
                    }

                    // if the data represents a derivation dependency between types, and we're creating ids within the file,
                    // capture a dependency map, so we can try to figure out a good ordering to use for importing the data.
                    if (typeData.dependencyIndexes.containsKey(index) && _dataIdIndex >= 0)
                    {
                        String parentTypeName = typeData.dependencyIndexes.get(index);
                        _orderDependencies.computeIfAbsent(typeData.dataType.getName(), i -> new HashSet<>()).add(parentTypeName);
                        _parentIdsPerType.computeIfAbsent(parentTypeName, k -> new HashSet<>()).add(data.toString());
                    }
                }
            });
            typeData.dataRows.add(StringUtils.join(dataRow, "\t"));
        }

        private void writeRowsToFile(TypeData typeData)
        {
            if (typeData.dataRows.isEmpty())
                return;

            try (FileWriter writer = new FileWriter(typeData.dataFile, true))
            {
                writer.write(StringUtils.join(typeData.dataRows, System.lineSeparator()));
                writer.write(System.lineSeparator()); // Issue 48442: add a new line to the end so the next written rows start on a new line
                typeData.dataRows.clear();
            }
            catch (IOException e)
            {
                _context.getErrors().addRowError(new ValidationException("Unable to write data for '" + typeData.dataType.getName() + "'."));
            }
        }
    }

    public static class MultiDataTypeCrossProjectDataIteratorBuilder implements DataIteratorBuilder
    {
        private final DataIteratorBuilder _in;
        private final Container _container;
        private final User _user;
        private final boolean _isCrossType;
        private final boolean _isCrossFolder;
        private final ExpObject _dataType;
        private final boolean _isSamples;

        public MultiDataTypeCrossProjectDataIteratorBuilder(@NotNull User user, @NotNull Container container, @NotNull DataIteratorBuilder in, boolean isCrossType, boolean isCrossFolder, ExpObject dataType, boolean isSamples)
        {
            _in = in;
            _container = container;
            _user = user;
            _isCrossType = isCrossType;
            _isCrossFolder = isCrossFolder;
            _dataType = dataType;
            _isSamples = isSamples;

        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _in.getDataIterator(context);
            return LoggingDataIterator.wrap(new MultiDataTypeCrossProjectDataIterator(pre, context, _container, _user, _isCrossType, _isCrossFolder, _dataType, _isSamples));
        }
    }

    public static void incrementCounts(Map<String, Integer> currentCounts, Map<String, Integer> increments)
    {
        increments.keySet().forEach(key -> {
            Integer currentCount = currentCounts.getOrDefault(key, 0);
            currentCounts.put(key, currentCount + increments.get(key));
        });
    }

    public static class SampleStatusCheckIteratorBuilder implements DataIteratorBuilder
    {
        private final DataIteratorBuilder _in;
        private final Container _container;

        public SampleStatusCheckIteratorBuilder(@NotNull DataIteratorBuilder in, Container container)
        {
            _in = in;
            _container = container;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _in.getDataIterator(context);
            return LoggingDataIterator.wrap(new SampleStatusCheckDataIterator(pre, context, _container));
        }
    }

    public static class SampleStatusCheckDataIterator extends WrapperDataIterator
    {
        private final Set<String> SAMPLE_IMPORT_BASE_FIELDS = new CaseInsensitiveHashSet(
                "LSID",
                "CreatedBy",
                "Modified",
                "ModifiedBy",
                "Created",
                "_rowNumber",
                RowId.name(),
                "genId",
                AliquotedFromLSID.name(),
                ALIQUOTED_FROM_INPUT,
                ROOT_RECOMPUTE_ROWID_COL,
                PARENT_RECOMPUTE_NAME_COL,
                ROOT_RECOMPUTE_ROWID_SET,
                PARENT_RECOMPUTE_NAME_SET,
                "cpasType",
                Folder.name(),
                Name.name(),
                "EntityId",
                EXISTING_RECORD_COLUMN_NAME,
                CURRENT_SAMPLE_STATUS_COLUMN_NAME,
                MaterialSourceId.name(),
                RootMaterialRowId.name(),
                AliquotCount.name(),
                AliquotVolume.name(),
                AvailableAliquotCount.name(),
                AvailableAliquotVolume.name()
        );

        final DataIteratorContext _context;
        private final Integer _sampleStateCol;
        private final Integer _oldSampleStateCol;
        private Map<Integer, DataState> _allStates;
        private final boolean _noStatusChangeCol;
        private final boolean _hasNonStatusChangeCol;

        protected SampleStatusCheckDataIterator(DataIterator di, DataIteratorContext context, Container container)
        {
            super(di);
            _context = context;
            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _sampleStateCol = map.get(SampleState.name());
            _noStatusChangeCol = _sampleStateCol == null;
            _oldSampleStateCol = map.get(CURRENT_SAMPLE_STATUS_COLUMN_NAME);

            _allStates = SampleStatusService.get()
                    .getAllProjectStates(container)
                    .stream().collect(Collectors.toMap(DataState::getRowId, data -> data));

            boolean hasNonStatusChangeCol = false;
            for (String col : map.keySet())
            {
                if (SampleState.name().equalsIgnoreCase(col))
                    continue;
                if (!SAMPLE_IMPORT_BASE_FIELDS.contains(col))
                {
                    hasNonStatusChangeCol = true;
                    break;
                }
            }
            _hasNonStatusChangeCol = hasNonStatusChangeCol;
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();
            if (!hasNext)
                return false;

            if (_context.getErrors().hasErrors())
                return true;

            if (_oldSampleStateCol == null && getExistingRecord() == null)
                return true;

            Integer oldState;
            if (_oldSampleStateCol != null)
                oldState = (Integer) get(_oldSampleStateCol);
            else
                oldState = (Integer) getExistingRecord().get(SampleState.name());

            if (oldState == null)
                return true;

            DataState oldStatus = _allStates.get(oldState);
            boolean oldAllowsOp = SampleStatusService.get().isOperationPermitted(oldStatus, SampleTypeService.SampleOperations.EditMetadata);
            if (oldAllowsOp)
                return true;

            if (_noStatusChangeCol)
            {
                _context.getErrors().addRowError(new ValidationException(String.format("Updating sample data when status is %s is not allowed.", oldStatus.getLabel())));
                return true;
            }

            Integer newState = (Integer) get(_sampleStateCol);
            DataState newStatus = _allStates.get(newState);
            boolean newAllowsOp = SampleStatusService.get().isOperationPermitted(newStatus, SampleTypeService.SampleOperations.EditMetadata);

            if (!newAllowsOp && _hasNonStatusChangeCol)
                _context.getErrors().addRowError(new ValidationException(String.format("Updating sample data when status is %s is not allowed.", oldStatus.getLabel())));

            return true;
        }
    }

}
