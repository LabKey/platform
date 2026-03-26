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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.IntArrayList;
import org.labkey.api.collections.IntHashMap;
import org.labkey.api.collections.LongArrayList;
import org.labkey.api.collections.LongHashMap;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CounterDefinition;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ExpDataFileConverter;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiChoice;
import org.labkey.api.data.NameGenerator;
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
import org.labkey.api.exp.OntologyManager;
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
import org.labkey.api.exp.api.NameExpressionOptionService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.api.SimpleRunRecord;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.AbstractExpSchema;
import org.labkey.api.exp.query.DataClassUserSchema;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.ExpTable;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.SampleStatusService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FileColumnValueMapper;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
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
import org.labkey.vfs.FileLike;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.labkey.api.data.CompareType.IN;
import static org.labkey.api.dataiterator.ExistingRecordDataIterator.EXISTING_RECORD_COLUMN_NAME;
import static org.labkey.api.dataiterator.SampleUpdateAddColumnsDataIterator.CURRENT_SAMPLE_STATUS_COLUMN_NAME;
import static org.labkey.api.exp.api.ExpData.DATA_INPUTS_PREFIX_LC;
import static org.labkey.api.exp.api.ExpData.DATA_INPUT_PARENT;
import static org.labkey.api.exp.api.ExpMaterial.ALIQUOTED_FROM_INPUT;
import static org.labkey.api.exp.api.ExpMaterial.ALIQUOTED_FROM_INPUT_LABEL;
import static org.labkey.api.exp.api.ExpMaterial.MATERIAL_INPUTS_PREFIX_LC;
import static org.labkey.api.exp.api.ExpMaterial.MATERIAL_INPUT_PARENT;
import static org.labkey.api.exp.api.ExpRunItem.INPUTS_PREFIX_LC;
import static org.labkey.api.exp.api.ExperimentService.ALIASCOLUMNALIAS;
import static org.labkey.api.exp.api.ExperimentService.QueryOptions.SkipBulkRemapCache;
import static org.labkey.api.util.IntegerUtils.asLong;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.*;
import static org.labkey.api.query.AbstractQueryImportAction.configureLoader;
import static org.labkey.experiment.api.SampleTypeServiceImpl.SampleChangeType.insert;
import static org.labkey.experiment.api.SampleTypeUpdateServiceDI.PARENT_RECOMPUTE_NAME_COL;
import static org.labkey.experiment.api.SampleTypeUpdateServiceDI.PARENT_RECOMPUTE_NAME_SET;
import static org.labkey.experiment.api.SampleTypeUpdateServiceDI.ROOT_RECOMPUTE_ROWID_COL;
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
                                                 AbstractTableInfo expTable, String sequencePrefix, long sequenceId)
        {
            // TODO BIGIT
            if (sequenceId > Integer.MAX_VALUE)
                throw new IllegalStateException("Sequence id is too large");
            if (expTable.getCounterDefinitions().isEmpty())
                return in;

            return new CounterDataIteratorBuilder(in, container, expTable, sequencePrefix,(int) sequenceId);
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
            if (pre == null)
                return null; // can happen if context has errors

            SimpleTranslator counterTranslator = new SimpleTranslator(pre, context);
            counterTranslator.setDebugName("Counter Def");
            Set<String> skipColumns = new CaseInsensitiveHashSet();
            Map<String, Integer> columnNameMap = DataIteratorUtil.createColumnNameMap(pre);

            for (CounterDefinition counterDefinition : _expTable.getCounterDefinitions())
            {
                Set<String> attachedColumnNames = counterDefinition.getAttachedColumnNames();
                skipColumns.addAll(attachedColumnNames);

                // validate we have all the paired columns
                List<Integer> pairedIndexes = new IntArrayList();
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
            if (!isUpdateOnly && columnNameMap.containsKey(ALIQUOTED_FROM_INPUT))
                _aliquotedFromColIdx = columnNameMap.get(ALIQUOTED_FROM_INPUT);
            else if (isUpdateOnly && columnNameMap.containsKey(AliquotedFromLSID.name()))
                _aliquotedFromColIdx = columnNameMap.get(AliquotedFromLSID.name());
            else
                _aliquotedFromColIdx = -1;
        }

        @Override
        protected String validate(ColumnValidator v, int rowNum, Object value, DataIterator data, Object providedValue)
        {
            if (!(v instanceof RequiredValidator) || _aliquotedFromColIdx < 0)
                return super.validate(v, rowNum, value, data, providedValue);

            String aliquotedFromValue = null;
            Object aliquotedFromObj = data.get(_aliquotedFromColIdx);
            if (aliquotedFromObj != null)
            {
                if (aliquotedFromObj instanceof String s)
                {
                    aliquotedFromValue = s;
                }
                else if (aliquotedFromObj instanceof Number)
                {
                    aliquotedFromValue = aliquotedFromObj.toString();
                }
                if (aliquotedFromValue != null)
                    aliquotedFromValue = aliquotedFromValue.trim();
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
            if (pre == null)
                return null; // can happen if context has errors

            return LoggingDataIterator.wrap(new AliquotRollupDataIterator(pre, context, _container));
        }
    }

    public static class AliquotRollupDataIterator extends WrapperDataIterator
    {
        private final DataIteratorContext _context;
        private final Integer _storedAmountCol;
        private final Integer _unitsCol;
        private final Integer _sampleStateCol;
        private final Integer _aliquotedFromCol;
        private final Integer _rootMaterialRowIdCol;
        private final Integer _rootIdToRecomputeCol;
        private final Integer _parentNameToRecomputeCol;
        private final boolean _isInsert;
        private final boolean _isUpdate;
        private final List<Long> availableSampleStatuses = new LongArrayList();
        private final TSVWriter _tsvWriter;

        protected AliquotRollupDataIterator(DataIterator di, DataIteratorContext context, Container container)
        {
            super(di);
            _context = context;
            _isInsert = !context.getInsertOption().allowUpdate;
            _isUpdate = context.getInsertOption().updateOnly;
            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _storedAmountCol = map.get(StoredAmount.name());
            _unitsCol = map.get(Units.name());
            _sampleStateCol = map.get(SampleState.name());
            _aliquotedFromCol = map.get(ALIQUOTED_FROM_INPUT);
            _rootMaterialRowIdCol = map.get(RootMaterialRowId.name());
            _rootIdToRecomputeCol = map.get(ROOT_RECOMPUTE_ROWID_COL);
            _parentNameToRecomputeCol = map.get(PARENT_RECOMPUTE_NAME_COL);
            _tsvWriter = new TSVWriter() // Used to quote values with newline/tabs/quotes
            {
                @Override
                protected int write()
                {
                    throw new UnsupportedOperationException();
                }
            };

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
            Long existingState = asLong(existingMap.get(SampleState.name()));

            if (!availableSampleStatuses.isEmpty())
            {
                Long newState = _sampleStateCol == null ? null : asLong(get(_sampleStateCol));
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
                        return getAliquotParent(get(_aliquotedFromCol), _context, _tsvWriter); // recompute parent when new aliquot is created
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
                    Pair<Boolean, Integer> needRecalc = determineRecalcFromExistingRecord(i, existingMap);
                    if (needRecalc == null)
                        return null;
                    if (needRecalc.first && needRecalc.second != null)
                        return needRecalc.second;
                }

                // without existing record, or if existing record is missing root information, we have to be conservative and assume this is a new aliquot, or an amount/status update
                // merge: either a new record, or detailed audit disabled
                if (!_isUpdate)
                {
                    if (i == _parentNameToRecomputeCol && _aliquotedFromCol != null)
                        return getAliquotParent(get(_aliquotedFromCol), _context, _tsvWriter); // recompute parent when new aliquot is created
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
        private final boolean _isSample;
        private final ExpObject _dataType;

        public AliasDataIteratorBuilder(@NotNull DataIteratorBuilder in, Container container, User user, TableInfo expAliasTable, ExpObject dataType, boolean isSample)
        {
            _in = in;
            _container = container;
            _user = user;
            _expAliasTable = expAliasTable;
            _isSample = isSample;
            _dataType = dataType;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator di = _in.getDataIterator(context);
            if (di == null)
                return null; // can happen if context has errors

            return LoggingDataIterator.wrap(new AliasDataIterator(di, context, _container, _user, _expAliasTable, _dataType, _isSample));
        }
    }

    private static class AliasDataIterator extends ExpDataTypeDataIterator
    {
        // For some reason I don't quite understand we don't want to pass through a column called "alias" so we rename it to ALIASCOLUMNALIAS
        final Supplier<Object> _aliasCol;
        final Supplier<Object> _lsidCol;
        final Supplier<Object> _nameCol;
        Map<String, Object> _lsidAliasMap = new HashMap<>();
        private final TableInfo _expAliasTable;
        private final boolean _isUpdateOnly;

        protected AliasDataIterator(DataIterator di, DataIteratorContext context, Container container, User user, TableInfo expAliasTable, ExpObject dataType, boolean isSample)
        {
            super(di, context, container, user, dataType, isSample);

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _aliasCol = map.get(ALIASCOLUMNALIAS) == null ? null : di.getSupplier(map.get(ALIASCOLUMNALIAS));
            _lsidCol = map.get(LSID.name()) == null ? null : di.getSupplier(map.get(LSID.name()));
            _nameCol = map.get(Name.name()) == null ? null : di.getSupplier(map.get(Name.name()));
            _expAliasTable = expAliasTable;
            _isUpdateOnly = _context.getInsertOption().updateOnly;

            if (_isUpdateOnly && !di.supportsGetExistingRecord())
                throw new IllegalArgumentException("DataIterator must support getExistingRecord() to update aliases");
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            // skip processing if aliases are not being modified
            if (_aliasCol == null)
                return hasNext;

            // skip processing if there are errors upstream
            if (getErrors().hasErrors())
                return hasNext;

            if (hasNext)
            {
                // Collect alias values and map them by LSID
                String lsid = null;

                if (_nameCol != null && (_context.getInsertOption().mergeRows || _isUpdateOnly))
                {
                    Object nameValue = _nameCol.get();
                    if (nameValue instanceof String name)
                    {
                        ExpObject obj = getExpObjectByName(name);
                        if (obj != null)
                            lsid = obj.getLSID();
                    }
                }

                if (lsid == null && _lsidCol != null)
                {
                    Object lsidValue = _lsidCol.get();
                    if (lsidValue instanceof String lsidString)
                        lsid = lsidString;
                }

                if (lsid == null && _isUpdateOnly)
                {
                    Map<String, Object> oldRow = getExistingRecord();
                    if (oldRow != null)
                        lsid = (String) oldRow.get(LSID.name());
                }

                if (!StringUtils.isEmpty(lsid))
                    _lsidAliasMap.put(lsid, _aliasCol.get());

                return true;
            }

            if (_lsidAliasMap.isEmpty())
                return false;

            // after the last row, insert all aliases
            try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
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
            if (pre == null)
                return null; // can happen if context has errors

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
        final List<Long> _derivativeKeys = new LongArrayList();
        final UserSchema _schema;
        final boolean _hasParentInput;
        final Integer _rowIdCol;
        final List<Integer> _parentCols = new IntArrayList();

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
                if (ExperimentService.isInputOutputColumn(name) || Strings.CI.equals("parent", name) || Strings.CI.equals(ALIQUOTED_FROM_INPUT, name))
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
                _derivativeKeys.add(asLong(get(_rowIdCol)));
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
            if (pre == null)
                return null; // can happen if context has errors

            return LoggingDataIterator.wrap(new FlagDataIterator(pre, context, _user, _isSample, _expObject, _container));
        }
    }

    private static class FlagDataIterator extends ExpDataTypeDataIterator
    {
        final DataIteratorContext _context;
        final Integer _lsidCol;
        final Integer _nameCol;
        final Integer _flagCol;
        final boolean _isUpdateOnly;

        protected FlagDataIterator(DataIterator di, DataIteratorContext context, User user, boolean isSample, ExpObject dataType, Container container)
        {
            super(di, context, container, user, dataType, isSample);
            _context = context;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _nameCol = map.get("name");
            _flagCol = map.containsKey("flag") ? map.get("flag") : map.get("comment");
            _isUpdateOnly = _context.getInsertOption().updateOnly;

            if (_isUpdateOnly && !di.supportsGetExistingRecord())
                throw new IllegalArgumentException("DataIterator must support getExistingRecord() to update flag/comment");
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

            if (_flagCol == null)
                return true;

            ExpObject expObject = null;
            if (_nameCol != null && (_context.getInsertOption().mergeRows || _isUpdateOnly))
            {
                Object nameValue = get(_nameCol);
                if (nameValue instanceof String name)
                    expObject = getExpObjectByName(name);
            }

            if (expObject == null && _lsidCol != null)
            {
                Object lsidValue = get(_lsidCol);
                if (lsidValue instanceof String lsid)
                    expObject = getExpObjectByLsid(lsid);
            }

            if (expObject == null && _isUpdateOnly)
            {
                Map<String, Object> oldRow = getExistingRecord();
                if (oldRow != null)
                {
                    String lsid = (String) oldRow.get(LSID.name());
                    if (lsid != null)
                        expObject = getExpObjectByLsid(lsid);
                }
            }

            if (expObject != null)
            {
                Object flagValue = get(_flagCol);
                String flag = Objects.toString(flagValue, null);

                try
                {
                    expObject.setComment(_user, flag, false);
                }
                catch (ValidationException e)
                {
                    throw new BatchValidationException(e);
                }
            }

            return true;
        }
    }

    /**
     * Issue 52504 (sort of): Chooses a container filter that is appropriate for import, merge or update actions in the face of product folders.
     * Note that this is slightly different from our treatment of lookups:
     *   - when in a project, we allow import or update to all subfolders,
     *   - when in a folder, we only allow references to data up the folder tree
     * @param qDef The QueryDefinition in use for the import action
     * @param container The container that is the target of the import or update
     * @param user The user doing the action
     */
    public static void setContainerFilterForImport(QueryDefinition qDef, Container container, User user)
    {
        if (container.isProductFoldersEnabled())
            qDef.setContainerFilter(new ContainerFilter.ProductFolderImport(container, user));
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
            DataIterator di = _pre.getDataIterator(context);
            if (di == null)
                return null; // can happen if context has errors

            if (context.getConfigParameters().containsKey(SampleTypeUpdateServiceDI.Options.SkipDerivation))
                return di;

            if (context.getInsertOption() != QueryUpdateService.InsertOption.UPDATE)
                di = new DerivationDataIterator(di, context, _container, _user, _currentDataType, _isSample, _skipAliquot);
            else if (_isSample)
                di = new SampleUpdateDerivationDataIterator(di, context, _container, _user, _currentDataType, _checkRequiredParents);
            else
                di = new DataUpdateDerivationDataIterator(di, context, _container, _user, _currentDataType, _checkRequiredParents);

            return LoggingDataIterator.wrap(di);
        }
    }

    static boolean hasAliquots(long sampleTypeRowId, List<String> names)
    {
        SimpleFilter f = new SimpleFilter(Name.fieldKey(), names, IN);
        f.addCondition(MaterialSourceId.fieldKey(), sampleTypeRowId);
        f.addCondition(AliquotedFromLSID.fieldKey(), null, CompareType.NONBLANK);

        return new TableSelector(ExperimentService.get().getTinfoMaterial(), Set.of(RowId.name()), f, null).exists();
    }

    public static String getAliquotParent(Object parentObj, DataIteratorContext context, TSVWriter tsvWriter)
    {
        Collection<String> parentNames = getParentNames(parentObj, tsvWriter, ALIQUOTED_FROM_INPUT, null);
        if (parentNames != null)
        {
            List<String> parents = parentNames.stream()
                    .map(String::trim)
                    .filter(s -> !StringUtils.isEmpty(s))
                    .toList();
            if (!parents.isEmpty())
            {
                if (parents.size() > 1)
                    context.getErrors().addRowError(new ValidationException(String.format("Multiple %s values are provided.", ALIQUOTED_FROM_INPUT)));
                return parents.get(0);
            }
        }

        return null;
    }

    static Collection<String> getParentNames(Object parentObj, TSVWriter tsvWriter, String fieldName, @Nullable BatchValidationException errors)
    {
        if (parentObj instanceof String parentStr && parentStr.trim().isEmpty())
            return Arrays.asList(parentStr.trim()); // This is needed to remove existing lineage

        Stream<String> values = NameGenerator.parentNames(parentObj, fieldName, tsvWriter, errors);
        return values == null ? null : values.collect(Collectors.toList());
    }

    static class DerivationDataIteratorBase extends ExpDataTypeDataIterator
    {
        final Integer _lsidCol;
        final Integer _nameCol;
        final Map<Integer, String> _parentCols;
        final Map<Integer, String> _requiredParentCols;
        final Map<String, String> _aliquotParents;
        /** Cache sample type lookups because even though we do caching in SampleTypeService, it's still a lot of overhead to check permissions for the user */
        final Map<String, ExpSampleType> _sampleTypes = new HashMap<>();
        final Map<String, ExpDataClass> _dataClasses = new HashMap<>();
        final TSVWriter _tsvWriter;

        protected DerivationDataIteratorBase(DataIterator di, DataIteratorContext context, Container container, User user, ExpObject currentDataType, boolean isSample, boolean checkRequiredParent)
        {
            super(di, context, container, user, currentDataType, isSample);
            Set<String> requiredParents = new CaseInsensitiveHashSet();

            try
            {
                if (checkRequiredParent)
                {
                    if (isSample())
                        requiredParents.addAll(getSampleType().getRequiredImportAliases().values());
                    else
                        requiredParents.addAll(getDataClass().getRequiredImportAliases().values());
                }
            }
            catch (IOException ignore)
            {
            }

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _nameCol = map.get("name");
            _parentCols = new HashMap<>();
            _requiredParentCols = new HashMap<>();
            _aliquotParents = new LinkedHashMap<>();

            for (Map.Entry<String, Integer> entry : map.entrySet())
            {
                String name = entry.getKey();
                if (ExperimentService.isInputOutputColumn(name) || isSample() && Strings.CI.equals("parent", name))
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

        protected Set<Pair<String, String>> _getParentParts()
        {
            Set<Pair<String, String>> allParts = new HashSet<>();
            for (Integer parentCol : _parentCols.keySet())
            {
                Object o = get(parentCol);
                if (o != null)
                {
                    Collection<String> parentNames = getParentNames(o, _tsvWriter, _parentCols.get(parentCol), getErrors());

                    if (parentNames != null)
                    {
                        String parentColName = _parentCols.get(parentCol);
                        Set<Pair<String, String>> parts = parentNames.stream()
                                .map(String::trim)
                                .map(s -> Pair.of(parentColName, s))
                                .collect(Collectors.toSet());

                        allParts.addAll(parts);
                    }
                }
                else // we have parent columns but the parent value is empty, indicating that the parents should be cleared
                {
                    allParts.add(new Pair<>(_parentCols.get(parentCol), null));
                }
            }

            return allParts;
        }

        protected void _processRun(
            ExpRunItem runItem,
            List<UploadSampleRunRecord> runRecords,
            Set<Pair<String, String>> parentNames,
            RemapCache cache,
            Map<Long, ExpMaterial> materialCache,
            Map<Long, ExpData> dataCache,
            @Nullable String aliquotedFrom,
            String dataType /*sample type or source type name*/,
            boolean updateOnly
        ) throws ValidationException, ExperimentException
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

            if (isSample() && aliquotedFrom == null && !((ExpMaterial) runItem).isOperationPermitted(SampleTypeService.SampleOperations.EditLineage))
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

                if (isSample())
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
    
    private static class DerivationDataIterator extends DerivationDataIteratorBase
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
            _lsidNames = new HashMap<>();
            _parentNames = new LinkedHashMap<>();
            _aliquotParents = new LinkedHashMap<>();
            _candidateAliquotNames = new ArrayList<>();

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _aliquotParentCol = isSample() ? map.getOrDefault(ALIQUOTED_FROM_INPUT, -1) : -1;
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

                    String aliquotParentName = getAliquotParent(o, _context, _tsvWriter);
                    if (aliquotParentName != null)
                        _aliquotParents.put(lsid, aliquotParentName.trim());

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
                    RemapCache cache = new RemapCache(!_context.getConfigParameterBoolean(SkipBulkRemapCache));
                    Map<Long, ExpMaterial> materialCache = new LongHashMap<>();
                    Map<Long, ExpData> dataCache = new LongHashMap<>();

                    if (isSample() && _context.getInsertOption().mergeRows)
                    {
                        if (!_candidateAliquotNames.isEmpty())
                        {
                            if (hasAliquots(getSampleType().getRowId(), _candidateAliquotNames))
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
                        ExpRunItem runItem;
                        String aliquotedFrom = _aliquotParents.get(lsid);
                        String dataType = null;
                        if (isSample())
                        {
                            ExpMaterial m = null;
                            if (_context.getInsertOption().mergeRows) // column lsid generated from dbseq might not be valid for existing materials, lookup by name instead
                            {
                                String sampleName = _lsidNames.get(lsid);
                                if (!StringUtils.isEmpty(sampleName))
                                    m = getSampleType().getSample(_container, sampleName);
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
                                    d = getDataClass().getData(_container, dataName);
                            }

                            if (d == null)
                                d = ExperimentService.get().getExpData(lsid);

                            if (d != null)
                                dataCache.put(d.getRowId(), d);

                            runItem = d;
                        }

                        if (runItem == null) // nothing to do if the item does not exist
                            continue;

                        Set<Pair<String, String>> parentNames = _parentNames.getOrDefault(lsid, Collections.emptySet());
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
    
    private static class SampleUpdateDerivationDataIterator extends DerivationDataIteratorBase
    {
        final Integer _aliquotParentCol; // Map from Data name to Set of (parentColName, parentName)
        final Map<Object, String> _aliquotParents; // Map of Data name and its aliquotedFromLSID
        final Map<Object, Set<Pair<String, String>>> _parentNames;
        final Integer _rowIdCol;
        final boolean _useRowId;

        protected SampleUpdateDerivationDataIterator(DataIterator di, DataIteratorContext context, Container container, User user, ExpObject currentDataType, boolean checkRequiredParent)
        {
            super(di, context, container, user, currentDataType, true, checkRequiredParent);

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _parentNames = new LinkedHashMap<>();
            _aliquotParents = new LinkedHashMap<>();
            _aliquotParentCol = map.getOrDefault(AliquotedFromLSID.name(), -1);
            _rowIdCol = map.getOrDefault(RowId.name(), -1);
            _useRowId = map.containsKey(RowId.name());
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
                Object key = null;
                if (_useRowId && _rowIdCol != null)
                {
                    key = get(_rowIdCol);
                    if (key instanceof String k)
                        key = Long.parseLong(k);
                    else
                        key = asLong(key);
                }
                else if (_nameCol != null)
                    key = get(_nameCol);

                String aliquotParentName = null;

                if (_aliquotParentCol > -1 && !_context.getConfigParameterBoolean(SampleTypeService.ConfigParameters.DeferAliquotRuns))
                {
                    Object o = get(_aliquotParentCol);

                    if (o != null)
                    {
                        if (o instanceof String s)
                        {
                            aliquotParentName = StringUtilsLabKey.unquoteString(s);
                        }
                        else if (o instanceof Number)
                        {
                            aliquotParentName = o.toString();
                        }
                        else
                        {
                            getErrors().addRowError(new ValidationException("Expected string value for aliquot parent name: " + o, AliquotedFromLSID.name()));
                        }

                        if (aliquotParentName != null)
                            _aliquotParents.put(key, aliquotParentName.trim());
                    }
                }

                // for non-aliquot, check required parent lineage
                if (aliquotParentName == null && !_requiredParentCols.isEmpty())
                {
                    for (Integer parentCol : _requiredParentCols.keySet())
                    {
                        Object parentVal = get(parentCol);
                        if (parentVal == null || (parentVal instanceof String s && s.isEmpty()))
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
                    Map<Long, ExpMaterial> materialCache = new LongHashMap<>();
                    Map<Long, ExpData> dataCache = new LongHashMap<>();

                    List<UploadSampleRunRecord> runRecords = new ArrayList<>();
                    Set<Object> keys = new LinkedHashSet<>();
                    keys.addAll(_parentNames.keySet());
                    keys.addAll(_aliquotParents.keySet());

                    for (Object key : keys)
                    {
                        ExpMaterial expMaterial = _useRowId ? ExperimentService.get().getExpMaterial((Long) key) : getSampleType().getSample(_container, (String) key);
                        if (expMaterial == null)
                            continue;

                        materialCache.put(expMaterial.getRowId(), expMaterial);
                        String dataType = getSampleType().getName();
                        String aliquotedFromLSID = _aliquotParents.get(key);
                        Set<Pair<String, String>> parentNames = _parentNames.getOrDefault(key, Collections.emptySet());

                        _processRun(expMaterial, runRecords, parentNames, cache, materialCache, dataCache, aliquotedFromLSID, dataType, true);
                    }

                    if (!runRecords.isEmpty())
                        ExperimentService.get().deriveSamplesBulk(runRecords, new ViewBackgroundInfo(_container, _user, null), null);
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

    private static class DataUpdateDerivationDataIterator extends DerivationDataIteratorBase
    {
        // Map from Data name to Set of (parentColName, parentName)
        final Map<String, Set<Pair<String, String>>> _parentNames;
        final boolean _useLsid;

        protected DataUpdateDerivationDataIterator(DataIterator di, DataIteratorContext context, Container container, User user, ExpObject currentDataType, boolean checkRequiredParent)
        {
            super(di, context, container, user, currentDataType, false, checkRequiredParent);

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _parentNames = new LinkedHashMap<>();
            _useLsid = map.containsKey("lsid") && context.getConfigParameterBoolean(ExperimentService.QueryOptions.UseLsidForUpdate);
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

                for (Integer parentCol : _requiredParentCols.keySet())
                {
                    Object parentVal = get(parentCol);
                    if (parentVal == null || (parentVal instanceof String s && s.isEmpty()))
                        getErrors().addRowError(new ValidationException("Missing value for required property: " + _requiredParentCols.get(parentCol)));
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
                    Map<Long, ExpMaterial> materialCache = new LongHashMap<>();
                    Map<Long, ExpData> dataCache = new LongHashMap<>();

                    List<UploadSampleRunRecord> runRecords = new ArrayList<>();
                    for (String key : _parentNames.keySet())
                    {
                        ExpData expData = _useLsid ? ExperimentService.get().getExpData(key) : getDataClass().getData(_container, key);
                        if (expData == null)
                            continue;

                        dataCache.put(expData.getRowId(), expData);
                        String dataType = getDataClass().getName();
                        Set<Pair<String, String>> parentNames = _parentNames.getOrDefault(key, Collections.emptySet());

                        _processRun(expData, runRecords, parentNames, cache, materialCache, dataCache, null, dataType, true);
                    }

                    if (!runRecords.isEmpty())
                        ExperimentService.get().deriveSamplesBulk(runRecords, new ViewBackgroundInfo(_container, _user, null), null);
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
            return Pair.of(previousMaterialParents, previousMaterialChildren);

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

    static Set<ExpData> getNearestChildDatas(Container c, User user, ExpRunItem start)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setParents(false);

        ExpLineage lineage = ExperimentService.get().getLineage(c, user, start, options);
        return lineage.findNearestChildDatas(start);
    }

    static Set<ExpMaterial> getNearestChildMaterials(Container c, User user, ExpRunItem start)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setParents(false);

        ExpLineage lineage = ExperimentService.get().getLineage(c, user, start, options);
        return lineage.findNearestChildMaterials(start);
    }


    /**
     * support for mapping DataClass or SampleSet objects as a parent input using the column name format:
     * DataInputs/<data class name> or MaterialInputs/<sample type name>. Either / or . works as a delimiter
     *
     * @param runItem the item whose parents are being modified.  If provided, existing parents of the item
     *                will be incorporated into the resolved inputs and outputs
     * @param entityNamePairs set of (parent column name, parent value) pairs.  Parent values that are empty
     *                    indicate the parent should be removed.
     */
    @NotNull
    private static Pair<RunInputOutputBean, RunInputOutputBean> resolveInputsAndOutputs(
        User user, Container c,
        @Nullable ExpRunItem runItem,
        Set<Pair<String, String>> entityNamePairs,
        RemapCache cache,
        Map<Long, ExpMaterial> materialMap,
        Map<Long, ExpData> dataMap,
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
            ExpSampleType sampleType = sampleTypes.computeIfAbsent(dataType, (name) -> SampleTypeService.get().getSampleType(c, name, true));
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

        Set<ExpData> existingChildData = null;
        Set<ExpMaterial> existingChildMaterials = null;
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

                        ExpSampleType sampleType = sampleTypes.computeIfAbsent(namePart, (name) -> SampleTypeService.get().getSampleType(c, name, true));
                        if (sampleType == null)
                            throw new ValidationException(String.format("Invalid import alias: parent SampleType [%1$s] does not exist or may have been deleted", namePart));

                        ExpMaterial sample = ExperimentService.get().findExpMaterial(c, user, entityName, sampleType, cache, materialMap);

                        if (isUpdatingExisting && sample != null)
                        {
                            if (existingChildMaterials == null)
                                existingChildMaterials = getNearestChildMaterials(c, user, runItem); // lazy initialization

                            for (ExpMaterial child : existingChildMaterials)
                            {
                                if (child.getRowId() == sample.getRowId())
                                    throw new ValidationException(String.format("'%s' is %s from sample '%s'. Circular relationships are not allowed.", entityName, child.getRootMaterialRowId() != child.getRowId() ? "aliquoted" : "derived", runItem.getName()));
                            }
                        }

                        if (sample != null)
                            parentMaterials.put(sample, sampleRole(sample));
                        else
                            throw new ValidationException("Sample '" + entityName + "' not found in Sample Type '" + namePart + "'.");

                    }
                }
                else if (ExpMaterial.MATERIAL_OUTPUT_CHILD.equalsIgnoreCase(aliasPrefix))
                {
                    ExpSampleType sampleType = sampleTypes.computeIfAbsent(namePart, (name) -> SampleTypeService.get().getSampleType(c, name, true));
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

                        ExpDataClass dataClass = dataClasses.computeIfAbsent(namePart, (name) -> ExperimentService.get().getDataClass(c, name, true));
                        if (dataClass == null)
                            throw new ValidationException(String.format("Invalid import alias: parent DataClass [%1$s] does not exist or may have been deleted", namePart));

                        ExpData data = ExperimentService.get().findExpData(c, user, dataClass, namePart, entityName, cache, dataMap);

                        if (isUpdatingExisting && data != null)
                        {
                            if (existingChildData == null)
                                existingChildData = getNearestChildDatas(c, user, runItem); // lazy initialization

                            for (ExpData child : existingChildData)
                            {
                                if (child.getRowId() == data.getRowId())
                                    throw new ValidationException(String.format("'%s' is child of the current source '%s'. Circular relationships are not allowed.", entityName, runItem.getName()));
                            }
                        }

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
                    ExpDataClass dataClass = dataClasses.computeIfAbsent(namePart, (name) -> ExperimentService.get().getDataClass(c, name, true));
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
            if (pre == null)
                return null; // can happen if context has errors

            return LoggingDataIterator.wrap(new SearchIndexIterator(pre, context, _indexFunction));
        }
    }

    public record SearchIndexDataKeys(@NotNull List<Long> orderedRowIds, @NotNull List<String> lsids) { }

    private static class SearchIndexIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final Integer _lsidCol;
        final Integer _rowIdCol;
        final ArrayList<String> _lsids;
        final ArrayList<Long> _rowIds;
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
            _rowIds = new LongArrayList(100);

            _isInsert = !context.getInsertOption().allowUpdate; // only useRowIdCol for INSERT. For UPDATE, rowId usually is not available. For MERGE, rowId is a new DBSequence value for existing data

            if (!_isInsert && !di.supportsGetExistingRecord())
                throw new IllegalArgumentException("DataIterator must support getExistingRecord() for search index update.");
        }

        static Long asLong(Object o)
        {
            return null==o ? null : ((Number)o).longValue();
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            if (hasNext)
            {
                Long rowId = null;
                String lsid = null;
                if (_isInsert)
                {
                    rowId = _rowIdCol == null ? null : asLong(get(_rowIdCol));
                    if (rowId == null)
                        lsid = _lsidCol == null ? null : (String) get(_lsidCol);
                }
                else
                {
                    Map<String, Object> map = getExistingRecord();
                    if (map != null)
                    {
                        if (map.containsKey("rowId")) // favor rowId over lsid to reduce deadlock during indexing
                            rowId = MapUtils.getLong(map, "rowId");
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
                final ArrayList<String> lsids = new ArrayList<>(_lsids);
                final ArrayList<Long> rowIds = new LongArrayList(_rowIds);
                Collections.sort(rowIds);
                final Runnable indexTask = _indexFunction.apply(new SearchIndexDataKeys(rowIds, lsids));

                if (null != DbScope.getLabKeyScope())
                    DbScope.getLabKeyScope().addCommitTask(indexTask, DbScope.CommitTaskOption.POSTCOMMIT);
                else
                    indexTask.run();
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
                                Path path = AssayFileWriter.getUploadDirectoryPath(c, fileLinkDirName).toNioPathForWrite();
                                Object file = fileColumnValueMapping.saveFileColumnValue(user, c, path, col.getName(), value);
                                assert file instanceof FileLike;
                                value = ((FileLike)file).toNioPathForRead().toString();
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

                        return ExpDataFileConverter.convert(value, false);
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

    // Common fields in both exp.data and exp.material that cannot be updated
    private static final Set<String> COMMON_NOT_FOR_UPDATE = CaseInsensitiveHashSet.of(
            Created.name(),
            CreatedBy.name(),
            LSID.name(),
            RowId.name(),
            "genId"
    );

    public static final Set<String> DATA_NOT_FOR_UPDATE;
    public static final Set<String> MATERIAL_NOT_FOR_UPDATE;

    static {
        DATA_NOT_FOR_UPDATE = COMMON_NOT_FOR_UPDATE;

        Set<String> materialNotForUpdate = Sets.newCaseInsensitiveHashSet(COMMON_NOT_FOR_UPDATE);
        materialNotForUpdate.addAll(CaseInsensitiveHashSet.of(
                AliquotCount.name(),
                AliquotedFromLSID.name(),
                AliquotVolume.name(),
                AvailableAliquotCount.name(),
                AvailableAliquotVolume.name(),
                RootMaterialRowId.name()
        ));
        MATERIAL_NOT_FOR_UPDATE = Collections.unmodifiableSet(materialNotForUpdate);
    }

    public static class PersistDataIteratorBuilder implements DataIteratorBuilder
    {
        private final DataIteratorBuilder _in;
        private final ExpTable<?> _expTable;
        private final TableInfo _propertiesTable;
        private final ExpObject _dataTypeObject;
        private final Container _container;
        private final User _user;
        private final Set<String> _excludedColumns = CaseInsensitiveHashSet.of("generated", RunId.name(), SourceApplicationId.name()); // generated has database DEFAULT 0

        private String _fileLinkDirectory = null;
        Function<SearchIndexDataKeys, Runnable> _indexFunction;
        final Map<String, String> _importAliases;

        // expTable is the shared experiment table e.g. exp.Data or exp.Materials
        public PersistDataIteratorBuilder(@NotNull DataIteratorBuilder in, ExpTable<?> expTable, TableInfo propsTable, ExpObject typeObject, Container container, User user, Map<String, String> importAliases)
        {
            _in = in;
            _expTable = expTable;
            _propertiesTable = propsTable;
            _dataTypeObject = typeObject;
            _container = container;
            _user = user;
            _importAliases = importAliases != null ? new CaseInsensitiveHashMap<>(importAliases) : Collections.emptyMap();
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
            boolean isMergeOrUpdate = context.getInsertOption().allowUpdate;
            boolean isUpdateOnly = context.getInsertOption().updateOnly;

            SimpleTranslator step1 = new SimpleTranslator(input, context);
            step1.selectAll(Sets.newCaseInsensitiveHashSet(Alias.name()), _importAliases);
            if (colNameMap.containsKey(Alias.name()))
                step1.addColumn(ExperimentService.ALIASCOLUMNALIAS, colNameMap.get(Alias.name())); // see AliasDataIteratorBuilder

            CaseInsensitiveHashSet dontUpdate = new CaseInsensitiveHashSet(isSample ? MATERIAL_NOT_FOR_UPDATE : DATA_NOT_FOR_UPDATE);
            if (isMergeOrUpdate)
            {
                // Common fields in both exp.data and exp.material that cannot be updated
                dontUpdate.addAll(CpasType.name(), ObjectId.name());

                if (isSample)
                    dontUpdate.add(MaterialSourceId.name());
            }

            CaseInsensitiveHashSet keyColumns = new CaseInsensitiveHashSet();
            CaseInsensitiveHashSet propertyKeyColumns = new CaseInsensitiveHashSet();
            boolean canUpdateNames = NameExpressionOptionService.get().getAllowUserSpecificNamesValue(_container);

            var keys = _expTable.getExistingRecordKeyColumnNames(context, colNameMap);
            if (keys != null)
                keyColumns.addAll(keys);

            for (String key : keyColumns)
            {
                if (_propertiesTable.getColumn(key) != null)
                    propertyKeyColumns.add(key);
            }

            if (isSample)
            {
                if (isUpdateOnly && !canUpdateNames)
                    dontUpdate.add(Name.name());

                dontUpdate.addAll(((ExpMaterialTableImpl) _expTable).getUniqueIdFields());
            }
            else
            {
                if (isMergeOrUpdate)
                {
                    boolean isUpdateUsingLsid = isUpdateOnly &&
                            colNameMap.containsKey(ExpDataTable.Column.LSID.name()) &&
                            context.getConfigParameterBoolean(ExperimentService.QueryOptions.UseLsidForUpdate);

                    if (isUpdateUsingLsid && !canUpdateNames)
                        dontUpdate.add(ExpDataTable.Column.Name.name());
                }
            }

            // Since we support detailed audit logging, add the ExistingRecordDataIterator here just before TableInsertDataIterator.
            // This is a NOOP unless we are merging/updating and detailed logging is enabled
            DataIteratorBuilder dib = ExistingRecordDataIterator.createBuilder(step1, _expTable, keyColumns, _expTable.getExistingRecordSharedKeyColumnNames(), true);

            if (isSample)
            {
                // Add RootMaterialRowId if it does not exist
                dib = getRootMaterialRowIdBuilder(dib);

                if (isMergeOrUpdate)
                {
                    dib = new SampleStatusCheckIteratorBuilder(dib, _container);

                    if (isUpdateOnly)
                    {
                        dib = new SampleUpdateOnlyValidatorsIteratorBuilder(dib, _container, _user);
                        dib = new SampleNameChangeDataIteratorBuilder(dib, _user, canUpdateNames);
                    }
                }
            }

            Set<DomainProperty> vocabProps = PropertyService.get().findVocabularyProperties(_container, colNameMap.keySet());

            // Ensure the property cache is cleared after vocabulary changes
            if (isMergeOrUpdate && !vocabProps.isEmpty())
            {
                var tx = _expTable.getSchema().getScope().getCurrentTransaction();
                if (tx != null)
                    tx.addCommitTask(OntologyManager::clearPropertyCache, DbScope.CommitTaskOption.POSTCOMMIT);
            }

            // Insert into exp.data then the provisioned table
            // Use embargo data iterator to ensure rows are committed before being sent along Issue 26082 (row at a time, reselect rowId)
            dib = LoggingDataIterator.wrap(new TableInsertDataIteratorBuilder(dib, _expTable, _container)
                    .setKeyColumns(keyColumns)
                    .setDontUpdate(dontUpdate)
                    .setVocabularyProperties(vocabProps)
                    .setAddlSkipColumns(_excludedColumns)
                    .setCommitRowsBeforeContinuing(true)
                    .setFailOnEmptyUpdate(false));

            // pass in remap columns to help reconcile columns that may be aliased in the virtual table
            dib = LoggingDataIterator.wrap(new TableInsertDataIteratorBuilder(dib, _propertiesTable, _container)
                    .setKeyColumns(propertyKeyColumns)
                    .setDontUpdate(dontUpdate)
                    .setRemapSchemaColumns(((UpdateableTableInfo) _expTable).remapSchemaColumns())
                    .setFailOnEmptyUpdate(false));

            if (colNameMap.containsKey(Flag.name()) || colNameMap.containsKey("comment"))
                dib = new FlagDataIteratorBuilder(dib, _user, isSample, _dataTypeObject, _container);

            // Wire up derived parent/child data and materials
            dib = new DerivationDataIteratorBuilder(dib, _container, _user, isSample, _dataTypeObject, false, false /* Validation already done in StandardDataIterator */);

            if (isSample && !context.getConfigParameterBoolean(SampleTypeService.ConfigParameters.DeferAliquotRuns) && colNameMap.containsKey(ROOT_RECOMPUTE_ROWID_COL))
                dib = new AliquotRollupDataIteratorBuilder(dib, _container);

            // Hack: add the alias and lsid values back into the input, so we can process them in the chained data iterator
            if (null != _indexFunction)
                dib = new SearchIndexIteratorBuilder(dib, _indexFunction); // may need to add this after the aliases are set

            return dib.getDataIterator(context);
        }

        private DataIteratorBuilder getRootMaterialRowIdBuilder(DataIteratorBuilder dib)
        {
            return ctx -> {
                DataIterator in = dib.getDataIterator(ctx);
                var map = DataIteratorUtil.createColumnNameMap(in);
                if (map.containsKey(RootMaterialRowId.toString()) || !map.containsKey(RowId.toString()))
                    return in;
                var ret = new SimpleTranslator(in, ctx);
                ret.selectAll();
                ret.addAliasColumn(RootMaterialRowId.toString(), map.get(RowId.toString()));
                return ret;
            };
        }
    }

    private static class SampleUpdateOnlyValidatorsIteratorBuilder implements DataIteratorBuilder
    {
        private final Container _container;
        private final DataIteratorBuilder _in;
        private final User _user;

        public SampleUpdateOnlyValidatorsIteratorBuilder(@NotNull DataIteratorBuilder in, Container container, User user)
        {
            _container = container;
            _in = in;
            _user = user;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator di = _in.getDataIterator(context);
            if (di == null)
                return null; // can happen if context has errors

            ValidatorIterator validate = new ValidatorIterator(di, context, _container, _user);
            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(validate);

            Integer index = map.get(Name.name());
            if (index != null)
            {
                ColumnInfo column = di.getColumnInfo(index);
                validate.addValidator(index, new RequiredValidator(column.getColumnName(), column.getJdbcType(), false, false, "Sample name cannot be blank"));
            }

            // Add other column validators here...

            if (validate.hasValidators())
                di = validate;

            return LoggingDataIterator.wrap(di);
        }
    }

    private static class SampleNameChangeDataIteratorBuilder implements DataIteratorBuilder
    {
        private final DataIteratorBuilder _in;
        private final boolean _canUpdateNames;
        private final User _user;

        public SampleNameChangeDataIteratorBuilder(@NotNull DataIteratorBuilder in, User user, boolean canUpdateNames)
        {
            _in = in;
            _canUpdateNames = canUpdateNames;
            _user = user;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator di = _in.getDataIterator(context);
            if (di == null)
                return null; // can happen if context has errors

            return LoggingDataIterator.wrap(new SampleNameChangeDataIterator(di, context, _user, _canUpdateNames));
        }
    }

    private static class SampleNameChangeDataIterator extends WrapperDataIterator
    {
        private final DataIteratorContext _context;
        private final Integer _nameCol;
        private final boolean _canUpdateNames;
        private final User _user;

        protected SampleNameChangeDataIterator(
            DataIterator di,
            DataIteratorContext context,
            User user,
            boolean canUpdateNames
        )
        {
            super(di);
            _context = context;
            _nameCol = DataIteratorUtil.createColumnNameMap(di).get(Name.name());
            _canUpdateNames = canUpdateNames;
            _user = user;

            if (!di.supportsGetExistingRecord())
                throw new IllegalArgumentException("DataIterator must support getExistingRecord()");
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();
            if (!hasNext)
                return false;

            if (_nameCol == null || _context.getErrors().hasErrors())
                return true;

            var existingRecord = getExistingRecord();
            if (existingRecord == null)
                return true;

            Object newNameObj = get(_nameCol);
            String newName = newNameObj == null ? null : String.valueOf(newNameObj);
            String oldName = (String) existingRecord.get(Name.name());
            boolean hasNameChange = !StringUtils.isEmpty(newName) && !newName.equals(oldName);
            if (!hasNameChange)
                return true;

            if (_canUpdateNames)
            {
                Long rowId = asLong(existingRecord.get(RowId.name()));
                ExpMaterial sample = ExperimentService.get().getExpMaterial(rowId);
                if (sample != null)
                    ExperimentService.get().addObjectLegacyName(sample.getObjectId(), ExperimentServiceImpl.getNamespacePrefix(ExpMaterial.class), oldName, _user);
            }
            else
                _context.getErrors().addRowError(new ValidationException("User-specified sample name not allowed"));

            return true;
        }
    }

    public static class MultiDataTypeCrossProjectDataIterator extends WrapperDataIterator
    {
        private static final String INVALID_FOLDER_MESSAGE = "Import or update of data in folder %s from folder %s is not allowed. Verify the folder exists, you have proper permissions, and data from that folder is visible here.";
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
                List<Object> dataIds,
                String headerRow,
                Map<Integer, File> folderFiles
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
        private final Map<String, Set<String>> _orderDependencies = new HashMap<>();
        private final int _dataIdIndex;
        private final FieldKey _dataKey;
        private final boolean _dataKeyIsNumeric;
        private final Map<String, Set<String>> _idsPerType = new HashMap<>();
        private final Map<String, Set<String>> _parentIdsPerType = new HashMap<>();
        private final Map<String, Container> _containerMap = new CaseInsensitiveHashMap<>();
        private final boolean _isCrossFolderUpdate;
        private final TSVWriter _tsvWriter;

        private MultiDataTypeCrossProjectDataIterator(DataIterator di, DataIteratorContext context, Container container, User user, boolean isCrossType, boolean isCrossFolder, ExpObject dataType, boolean isSamples)
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

            // Determine the dataId column
            {
                int index;
                FieldKey dataKey;
                boolean isNumeric;

                if (_isSamples)
                {
                    var foundId = RowId.namesAndLabels().stream()
                            .filter(map::containsKey)
                            .findFirst();

                    if (foundId.isPresent())
                    {
                        index = map.get(foundId.get());
                        dataKey = RowId.fieldKey();
                        isNumeric = true;
                    }
                    else
                    {
                        index = map.getOrDefault(Name.name(), -1);
                        dataKey = Name.fieldKey();
                        isNumeric = false;
                    }
                }
                else
                {
                    index = map.getOrDefault(ExpDataTable.Column.Name.name(), -1);
                    dataKey = ExpDataTable.Column.Name.fieldKey();
                    isNumeric = false;
                }

                _dataIdIndex = index;
                _dataKey = dataKey;
                _dataKeyIsNumeric = isNumeric;
            }

            _tsvWriter = new TSVWriter() // Used to quote values with newline/tabs/quotes
            {
                @Override
                protected int write()
                {
                    throw new UnsupportedOperationException();
                }
            };
            _tsvWriter.setAdditionalQuotedChars(TSVWriter.BACKSLASH_CHAR_STRING);

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
                    ContainerFilter cf;
                    if (container.isProductFoldersEnabled())
                    {
                        // Note that this is slightly different from our treatment of lookups:
                        //    - when in a project, we allow import or update to all subfolders,
                        //    - when in a folder, we only allow references to data up the folder tree
                        if (container.isProject())
                            cf = new ContainerFilter.AllInProjectPlusShared(container, user);
                        else
                            cf = new ContainerFilter.CurrentPlusProjectAndShared(container, user);
                    }
                    else
                        cf = ContainerFilter.current(container, user);

                    Collection<GUID> validContainerIds;
                    if (cf instanceof ContainerFilter.ContainerFilterWithPermission cfp)
                        validContainerIds = cfp.generateIds(container, context.getInsertOption().allowUpdate ? UpdatePermission.class : InsertPermission.class, null);
                    else
                        validContainerIds = cf.getIds();

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

        private int _importSplitFile(TypeData typeData, File splitFile, Container dataContainer, TableInfo dataTable)
        {
            var updateService = dataTable.getUpdateService();
            if (updateService == null)
            {
                _context.getErrors().addRowError(new ValidationException("No update service available for type '" + typeData.dataType.getName() + "'."));
                return 0;
            }

            try (DataLoader loader = DataLoader.get().createLoader(splitFile, "text/plain", true, null, null))
            {
                Set<String> aliasNames;
                if (_isSamples)
                    aliasNames = new CaseInsensitiveHashSet(((ExpSampleType) typeData.dataType).getImportAliases().keySet());
                else
                    aliasNames = new CaseInsensitiveHashSet(((ExpDataClass) typeData.dataType).getImportAliases().keySet());
                // We do not need to configure the loader for renamed columns as that has been taken care of when writing the file.
                configureLoader(loader, dataTable, null, true, aliasNames, null /* Not needed since partition is not a cross type import*/);
                if (loader instanceof TabLoader tabLoader)
                    tabLoader.setIncludeComments(true); // don't skip lines that starts with "#" (if the original file is Excel)
                QueryService.get().setEnvironment(QueryService.Environment.CONTAINER, dataContainer);
                return updateService.loadRows(_user, dataContainer, loader, _context, null);
            }
            catch (SQLException | IOException e)
            {
                String msg = "Problem importing data for type '" + typeData.dataType.getName() + "'. ";
                LOG.error(msg, e);
                _context.getErrors().addRowError(new ValidationException(msg));
            }

            return 0;
        }

        private int _importPartition(TypeData typeData)
        {
            if (_context.getErrors().hasErrors())
                return 0;

            int totalRowCount = 0;
            if (_isCrossFolderUpdate && !typeData.folderFiles.keySet().isEmpty())
            {
                boolean hasCrossFolderData = typeData.folderFiles.keySet().stream().anyMatch(id -> id != _container.getRowId());

                if (hasCrossFolderData)
                {
                    for (Map.Entry<Integer, File> containerSplitFile : typeData.folderFiles.entrySet())
                    {
                        Container splitContainer = ContainerManager.getForRowId(containerSplitFile.getKey());
                        AbstractExpSchema schema = _isSamples ? new SamplesSchema(_user, splitContainer) : new DataClassUserSchema(splitContainer, _user);
                        QueryDefinition qDef = schema.getQueryDefForTable(typeData.dataType.getName());
                        setContainerFilterForImport(qDef, splitContainer, _user);
                        TableInfo dataTable = qDef.getTable(schema, new ArrayList<>(), true);

                        if (dataTable == null)
                        {
                            _context.getErrors().addRowError(new ValidationException("Table for " + (_isSamples ? "sample type" : "dataclass") + " '" + typeData.dataType.getName() + "' not found."));
                            return totalRowCount;
                        }
                        totalRowCount += _importSplitFile(typeData, containerSplitFile.getValue(), splitContainer, dataTable);
                    }
                    return totalRowCount;
                }
            }

            return _importSplitFile(typeData, typeData.dataFile, typeData.container, typeData.tableInfo);
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
                    _context.putConfigParameter(QueryUpdateService.ConfigParameters.ProcessingPartition, true);

                    boolean hasCrossFolderImport = false;

                    // process the individual files
                    for (String key : importOrderKeys)
                    {
                        Map<String, TypeData> typeFolderData = _typeFolderDataMap.get(key);
                        hasCrossFolderImport = hasCrossFolderImport || typeFolderData.keySet().size() > 1;
                        for (TypeData typeData : typeFolderData.values())
                        {
                            writeRowsToFile(typeData); // write the last rows that have been collected since the last write, if any
                            if (!_context.getErrors().hasErrors()) // Issue 48402: Stop early since the transaction may have been aborted
                                _importPartition(typeData);
                        }
                    }

                    if (_isCrossFolder && !_context.getInsertOption().updateOnly && hasCrossFolderImport) // all updates are cross-folder due to lack of Container column
                        SimpleMetricsService.get().increment(ExperimentService.MODULE_NAME, _isSamples ? "sampleImport" : "dataClassImport", "multiFolderImport");

                    _context.putConfigParameter(QueryUpdateService.ConfigParameters.ProcessingPartition, false);
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
                    // Issue 52626 and Issue 52609 - don't check folders during update
                    if (_isCrossFolder && _folderColIndex != null && !_context.getInsertOption().updateOnly)
                    {
                        String rowFolderId = StringUtils.trim((String) get(_folderColIndex));
                        if (!StringUtils.isEmpty(rowFolderId))
                        {
                            targetContainer = _containerMap.get(rowFolderId);
                            if (targetContainer == null)
                            {
                                _context.getErrors().addRowError(new ValidationException(String.format(INVALID_FOLDER_MESSAGE, rowFolderId, _container.getName())));
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
                            ExpSampleTypeImpl sampleType = _typeColIndex != null ? (ExpSampleTypeImpl) SampleTypeService.get().getSampleType(targetContainer, typeName, true) : (ExpSampleTypeImpl) _dataType;
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
                    if (!typeData.folderFiles.isEmpty())
                    {
                        typeData.folderFiles.values().forEach(file -> {
                            file.delete();
                        });
                    }
                });
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

        private File writeSplitFile(String dataType, String prefix, String suffix, String headerRow)
        {
            File dataFile;
            try
            {
                dataFile = FileUtil.createTempFile(prefix, suffix);
            }
            catch (IOException e)
            {
                _context.getErrors().addRowError(new ValidationException("Unable to write data for '" + dataType + "'."));
                return null;
            }

            try (FileWriter writer = new FileWriter(dataFile, true))
            {
                writer.write(headerRow);
                writer.write(System.lineSeparator());
            }
            catch (IOException e)
            {
                _context.getErrors().addRowError(new ValidationException("Unable to write data for '" + dataType + "'."));
            }

            return dataFile;
        }

        private TypeData createDataClassHeaderRow(ExpDataClass dataClass, Container container) throws IOException
        {
            List<QueryException> qpe = new ArrayList<>();
            DataClassUserSchema schema = new DataClassUserSchema(container, _user);
            QueryDefinition qDef = schema.getQueryDefForTable(dataClass.getName());
            setContainerFilterForImport(qDef, container, _user);
            TableInfo dataTable = qDef.getTable(schema, qpe, true);
            if (dataTable == null)
            {
                _context.getErrors().addRowError(new ValidationException("Table for dataclass '" + dataClass.getName() + "' not found."));
                return null;
            }

            List<Integer> fieldIndexes = new IntArrayList();
            List<String> header = new ArrayList<>();

            for (int i = 1; i <= getColumnCount(); i++)
            {
                ColumnInfo colInfo = getColumnInfo(i);
                String name = colInfo.getName();

                fieldIndexes.add(i);
                header.add(_tsvWriter.quoteValue(name));
            }

            String headerRow = StringUtils.join(header, "\t");
            File dataFile = writeSplitFile(dataClass.getName(), "~importSplit-", container.getRowId() + FileUtil.makeLegalName(dataClass.getName()) + ".tsv", headerRow);
            return new TypeData(container, _dataType, dataTable, dataFile, fieldIndexes, Collections.emptyMap(), new ArrayList<>(), new ArrayList<>(), headerRow, new LinkedHashMap<>());
        }

        private TypeData createSampleHeaderRow(ExpSampleTypeImpl sampleType, Container container) throws IOException
        {
            List<QueryException> qpe = new ArrayList<>();
            SamplesSchema schema = new SamplesSchema(_user, container);
            QueryDefinition qDef = schema.getQueryDefForTable(sampleType.getName());
            setContainerFilterForImport(qDef, container, _user);
            TableInfo samplesTable = qDef.getTable(schema, qpe, true);
            if (samplesTable == null)
            {
                _context.getErrors().addRowError(new ValidationException("Table for sample type '" + sampleType.getName() + "' not found."));
                return null;
            }
            Set<String> validFields = new CaseInsensitiveHashSet();
            samplesTable.getColumns().forEach(column -> {
                if (!IGNORED_FIELD_NAMES.contains(column.getName()))
                {
                    validFields.addAll(ImportAliasable.Helper.createImportSet(column));
                }
            });
            Map<String, String> aliasMap = sampleType.getImportAliases();
            validFields.addAll(aliasMap.keySet());
            validFields.add(ALIQUOTED_FROM_INPUT);
            validFields.add(ALIQUOTED_FROM_INPUT_LABEL);
            validFields.add("StorageUnit");
            validFields.add("Storage Unit");
            validFields.add("StorageUnitLabel");
            validFields.add("Storage Unit Label");
            // For consistency with other storage fields that are imported without spaces in the names
            validFields.add("EnteredStorage");
            List<Integer> fieldIndexes = new IntArrayList();
            Map<Integer, String> dependencyIndexes = new IntHashMap<>();
            List<String> header = new ArrayList<>();
            // index is 1-based; column 0 is the rowNumber
            for (int i = 1; i <= getColumnCount(); i++)
            {
                ColumnInfo colInfo = getColumnInfo(i);
                String name = colInfo.getName();
                String lcName = name.toLowerCase();
                if (_typeColIndex != null && _typeColIndex == i) // Issue 52355: assure we have some data in the row by including the type
                {
                    fieldIndexes.add(i);
                    header.add(_typeColName);
                }
                else if (validFields.contains(name))
                {
                    fieldIndexes.add(i);
                    header.add(_tsvWriter.quoteValue(name));
                }
                if (lcName.startsWith(MATERIAL_INPUTS_PREFIX_LC))
                {
                    fieldIndexes.add(i);
                    header.add(_tsvWriter.quoteValue(name));
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
                    header.add(_tsvWriter.quoteValue(name));
                }
                else if (lcName.startsWith(INPUTS_PREFIX_LC))
                {
                    fieldIndexes.add(i);
                    header.add(_tsvWriter.quoteValue(name));
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

            String headerRow = StringUtils.join(header, "\t");
            File dataFile = writeSplitFile(sampleType.getName(), "~importSplit-", container.getRowId() + FileUtil.makeLegalName(sampleType.getName()) + ".tsv", headerRow);
            return new TypeData(container, sampleType, samplesTable, dataFile, fieldIndexes, dependencyIndexes, new ArrayList<>(), new ArrayList<>(), headerRow, new LinkedHashMap<>());
        }

        private Object getSerializingObject(Object data)
        {
            if (data instanceof Time t)
                return DateUtil.formatIsoLongTime(t);
            if (data instanceof Date d)
                return DateUtil.formatIsoDateLongTime(d, true);
            if (data instanceof String s)
                return _tsvWriter.quoteValue(s.trim());
            if (data instanceof MultiChoice.Array array)
            {
                // GitHub Issue 950: cross folder export/import roundtripping problems for MVTC with commas and quotes
                return _tsvWriter.quoteValue(array.toString().trim());
            }

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
                        String dataString = data.toString();
                        _idsPerType.computeIfAbsent(typeData.dataType.getName(), k -> new HashSet<>()).add(dataString);
                        if (_isCrossFolderUpdate)
                        {
                            if (_dataKeyIsNumeric)
                            {
                                try
                                {
                                    typeData.dataIds.add(JdbcType.BIGINT.convert(data));
                                }
                                catch (ConversionException e)
                                {
                                    _context.getErrors().addRowError(new ValidationException(e.getMessage() + " on row " + get(0), _dataKey.getName()));
                                    return;
                                }
                            }
                            else
                                typeData.dataIds.add(dataString);
                        }
                    }

                    // if the data represents a derivation dependency between types, and we're creating ids within the file,
                    // capture a dependency map, so we can try to figure out a good ordering to use for importing the data.
                    if (typeData.dependencyIndexes.containsKey(index) && _dataIdIndex >= 0)
                    {
                        String parentTypeName = typeData.dependencyIndexes.get(index);
                        _parentIdsPerType.computeIfAbsent(parentTypeName, k -> new HashSet<>()).add(data.toString());
                        // Issue 52368: "Unable to determine ordering for sample type imports." error when importing to multiple sample types and including simple lineage
                        // skip dependencies for self type
                        if (!typeData.dataType.getName().equals(parentTypeName))
                            _orderDependencies.computeIfAbsent(typeData.dataType.getName(), i -> new HashSet<>()).add(parentTypeName);
                    }
                }
                else if (index == _dataIdIndex && _isCrossFolderUpdate)
                {
                    // Issue 52922: Samples with blank sample id in the file are getting ignored
                    throw new IllegalArgumentException(_dataKey.getName() + " value not provided on row " + get(0));
                }
            });
            typeData.dataRows.add(StringUtils.join(dataRow, "\t"));
        }

        private void writeRowsToFile(TypeData typeData)
        {
            if (typeData.dataRows.isEmpty())
                return;

            // for cross-folder import, write to further partitions
            if (_isCrossFolderUpdate)
            {
                ExpObject dataType = typeData.dataType;
                Map<String, List<Integer>> containerRows = new HashMap<>();

                TableInfo tableInfo;
                SimpleFilter filter;

                if (_isSamples)
                {
                    filter = new SimpleFilter(MaterialSourceId.fieldKey(), dataType.getRowId());
                    filter.addCondition(_dataKey, typeData.dataIds, CompareType.IN);
                    tableInfo = ExperimentService.get().getTinfoMaterial();
                }
                else
                {
                    filter = new SimpleFilter(FieldKey.fromParts("ClassId"), dataType.getRowId());
                    filter.addCondition(_dataKey, typeData.dataIds, CompareType.IN);
                    tableInfo = ExperimentService.get().getTinfoData();
                }

                Map<String, Object>[] rows = new TableSelector(tableInfo, Set.of(_dataKey.getName(), "container"), filter, null).getMapArray();

                Set<Object> notFoundIds = new HashSet<>(typeData.dataIds);
                for (Map<String, Object> row : rows)
                {
                    Object raw = row.get(_dataKey.getName());
                    Object identifier = _dataKeyIsNumeric ? asLong(raw) : raw;
                    notFoundIds.remove(identifier);
                    String dataContainer = (String) row.get("container");
                    // could be updating the same data multiple times in a single import, the import will later be rejected
                    List<Integer> dataRowIds =
                            IntStream.range(0, typeData.dataIds.size()).boxed()
                                    .filter(i -> typeData.dataIds.get(i).equals(identifier))
                                    .toList();
                    containerRows.computeIfAbsent(dataContainer, k -> new ArrayList<>()).addAll(dataRowIds);
                }
                if (!notFoundIds.isEmpty())
                {
                    _context.getErrors().addRowError(new ValidationException((_isSamples ? "Samples" : "Data") + " not found for " + StringUtils.join(notFoundIds, ", ")));
                    return;
                }

                for (String containerId : containerRows.keySet())
                {
                    Container container = _containerMap.get(containerId);
                    if (container == null)
                    {
                        Container folder = ContainerManager.getForId(containerId);
                        _context.getErrors().addRowError(new ValidationException(String.format(INVALID_FOLDER_MESSAGE, (folder != null ? folder.getName() : containerId), _container.getName())));
                        return;
                    }

                    int containerRowId = container.getRowId();
                    File splitFile = typeData.folderFiles.get(containerRowId);

                    if (splitFile == null)
                    {
                        splitFile = writeSplitFile(typeData.dataType.getName(), "~containerSplit~", containerRowId + "-" + typeData.dataFile.getName(), typeData.headerRow);
                        if (splitFile == null)
                            return;
                        typeData.folderFiles.put(containerRowId, splitFile);
                    }

                    List<String> dataRows = new ArrayList<>();
                    List<Integer> dataRowIndexes = containerRows.get(containerId);
                    Collections.sort(dataRowIndexes);
                    for (Integer dataRowIndex : dataRowIndexes)
                        dataRows.add(typeData.dataRows.get(dataRowIndex));

                    try (FileWriter writer = new FileWriter(splitFile, true))
                    {
                        writer.write(StringUtils.join(dataRows, System.lineSeparator()));
                        writer.write(System.lineSeparator()); // Issue 48442: add a new line to the end so the next written rows start on a new line
                    }
                    catch (IOException e)
                    {
                        _context.getErrors().addRowError(new ValidationException("Unable to write data for '" + typeData.dataType.getName() + "'."));
                        return;
                    }
                }
            }

            try (FileWriter writer = new FileWriter(typeData.dataFile, true))
            {
                writer.write(StringUtils.join(typeData.dataRows, System.lineSeparator()));
                writer.write(System.lineSeparator()); // Issue 48442: add a new line to the end so the next written rows start on a new line
                typeData.dataRows.clear();
                typeData.dataIds.clear();
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
            DataIterator di = _in.getDataIterator(context);
            if (di == null)
                return null; // can happen if context has errors

            return LoggingDataIterator.wrap(new MultiDataTypeCrossProjectDataIterator(di, context, _container, _user, _isCrossType, _isCrossFolder, _dataType, _isSamples));
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
            if (pre == null)
                return null; // can happen if context has errors

            return LoggingDataIterator.wrap(new SampleStatusCheckDataIterator(pre, context, _container));
        }
    }

    private static class SampleStatusCheckDataIterator extends WrapperDataIterator
    {
        private final Set<String> SAMPLE_IMPORT_BASE_FIELDS = new CaseInsensitiveHashSet(
                "LSID",
                "CreatedBy",
                "Modified",
                "ModifiedBy",
                "Created",
                ROWNUMBER_COLUMNNAME,
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
        private final Map<Long, DataState> _allStates;
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

            Long oldState;
            if (_oldSampleStateCol != null)
                oldState = asLong(get(_oldSampleStateCol));
            else
                oldState = asLong(getExistingRecord().get(SampleState.name()));

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

            Long newState = asLong(get(_sampleStateCol));
            DataState newStatus = _allStates.get(newState);
            boolean newAllowsOp = SampleStatusService.get().isOperationPermitted(newStatus, SampleTypeService.SampleOperations.EditMetadata);

            if (!newAllowsOp && _hasNonStatusChangeCol)
                _context.getErrors().addRowError(new ValidationException(String.format("Updating sample data when status is %s is not allowed.", oldStatus.getLabel())));

            return true;
        }
    }
}
