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
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.CounterDefinition;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RemapCache;
import org.labkey.api.data.SimpleFilter;
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
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.api.SimpleRunRecord;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.util.Pair;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.experiment.api.AliasInsertHelper;
import org.labkey.experiment.api.ExpDataClassDataTableImpl;
import org.labkey.experiment.api.ExpMaterialTableImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.SampleTypeUpdateServiceDI;
import org.labkey.experiment.controllers.exp.RunInputOutputBean;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.labkey.api.data.CompareType.IN;
import static org.labkey.api.exp.api.ExperimentService.ALIASCOLUMNALIAS;


public class ExpDataIterators
{
    private static final Logger LOG = LogHelper.getLogger(ExpDataIterators.class, "Experiment data related data iterators");

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
        private Integer _aliquotedFromColInd = null;

        public ExpMaterialValidatorIterator(DataIterator data, DataIteratorContext context, Container c, User user)
        {
            super(data, context, c, user);
        }

        @Override
        protected String validate(ColumnValidator v, int rowNum, Object value, DataIterator data)
        {
            if (_aliquotedFromColInd == null)
            {
                Map<String, Integer> columnNameMap = ((SimpleTranslator) data).getColumnNameMap();
                if (columnNameMap != null && columnNameMap.containsKey("AliquotedFrom"))
                    _aliquotedFromColInd = columnNameMap.get("AliquotedFrom");
                else
                    _aliquotedFromColInd = -1;
            }

            if (!(v instanceof RequiredValidator) || _aliquotedFromColInd < 0)
                return super.validate(v, rowNum, value, data);

            String aliquotedFromValue = null;
            Object aliquotedFromObj = data.get(_aliquotedFromColInd);
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

            // after the last row, insert all of the aliases
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
            return LoggingDataIterator.wrap(new AutoLinkToStudyDataIterator(DataIteratorUtil.wrapMap(pre, false), context, _schema, _container, _user, _sampleType));
        }
    }

    private static class AutoLinkToStudyDataIterator extends WrapperDataIterator
    {
        final Container _container;
        final User _user;
        final ExpSampleType _sampleType;
        final MapDataIterator _data;
        final List<Map<FieldKey, Object>> _rows = new ArrayList<>();
        final List<Integer> _keys = new ArrayList<>();
        final UserSchema _schema;
        private boolean _isDerivation = false;
        final Integer _rowIdCol;

        protected AutoLinkToStudyDataIterator(DataIterator di, DataIteratorContext context, UserSchema schema, Container container, User user,  ExpSampleType sampleType)
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
                if (ExperimentService.isInputOutputColumn(name) || equalsIgnoreCase("parent", name))
                {
                    _isDerivation = true;
                    break;
                }
            }
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = _data.next();

            if (!hasNext)
            {
                if (!_rows.isEmpty())
                {
                    if (_isDerivation)
                    {
                        _schema.getDbSchema().getScope().getCurrentTransaction().addCommitTask(() -> {
                            try
                            {
                                // derived samples can't be linked until after the transaction is committed
                                StudyPublishService.get().autoLinkDerivedSamples(_sampleType, _keys, _container, _user);
                            }
                            catch (ExperimentException e)
                            {
                                throw new RuntimeException(e);
                            }
                        }, DbScope.CommitTaskOption.POSTCOMMIT);
                    }
                    else
                        StudyPublishService.get().autoLinkSamples(_sampleType, _rows, _container, _user);
                }
                return false;
            }
            Map<FieldKey, Object> row = new HashMap<>();
            for (Map.Entry<String, Object> entry : _data.getMap().entrySet())
                row.put(FieldKey.fromParts(entry.getKey()), entry.getValue());
            _rows.add(row);

            if (_isDerivation)
                _keys.add((Integer)get(_rowIdCol));

            return true;
        }
    }

    public static class FlagDataIteratorBuilder implements DataIteratorBuilder
    {
        private final DataIteratorBuilder _in;
        private final User _user;
        private final boolean _isSample;

        public FlagDataIteratorBuilder(@NotNull DataIteratorBuilder in, User user, boolean isSample)
        {
            _in = in;
            _user = user;
            _isSample = isSample;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _in.getDataIterator(context);
            return LoggingDataIterator.wrap(new FlagDataIterator(pre, context, _user, _isSample));
        }
    }

    private static class FlagDataIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final User _user;
        final Integer _lsidCol;
        final Integer _flagCol;
        final boolean _isSample;    // as oppsed to DataClass

        protected FlagDataIterator(DataIterator di, DataIteratorContext context, User user, boolean isSample)
        {
            super(di);
            _context = context;
            _user = user;
            _isSample = isSample;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _flagCol = map.containsKey("flag") ? map.get("flag") : map.get("comment");
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
                            ExpMaterial sample = ExperimentService.get().getExpMaterial(lsid);
                            if (sample != null)
                                sample.setComment(_user, flag);
                        }
                        else
                        {
                            ExpData data = ExperimentService.get().getExpData(lsid);
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
    public static void derive(User user, Container container, DataIterator di, boolean isSample, boolean skipAliquot) throws BatchValidationException
    {
        ExpDataIterators.DerivationDataIteratorBuilder ddib = new ExpDataIterators.DerivationDataIteratorBuilder(di, container, user, isSample, skipAliquot);
        DataIteratorContext context = new DataIteratorContext();
        context.setInsertOption(QueryUpdateService.InsertOption.MERGE);
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

        public DerivationDataIteratorBuilder(DataIteratorBuilder pre, Container container, User user, boolean isSample, boolean skipAliquot)
        {
            _pre = pre;
            _container = container;
            _user = user;
            _isSample = isSample;
            _skipAliquot = skipAliquot;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _pre.getDataIterator(context);
            if (null != context.getConfigParameters() && context.getConfigParameters().containsKey(SampleTypeUpdateServiceDI.Options.SkipDerivation))
            {
                return pre;
            }
            return LoggingDataIterator.wrap(new DerivationDataIterator(pre, context, _container, _user, _isSample, _skipAliquot));
        }
    }

    static boolean hasAliquots(List<String> lsids)
    {
        SimpleFilter f = new SimpleFilter(FieldKey.fromParts("LSID"), lsids, IN);
        f.addCondition(FieldKey.fromParts("AliquotedFromLSID"), null, CompareType.NONBLANK);
        return new TableSelector(ExperimentService.get().getTinfoMaterial(), f, null).exists();
    }

    static class DerivationDataIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final Integer _lsidCol;
        final Map<Integer, String> _parentCols;
        final Integer _aliquotParentCol;
        final Map<String, String> _aliquotParents;
        // Map from Data LSID to Set of (parentColName, parentName)
        final Map<String, Set<Pair<String, String>>> _parentNames;
        /** Cache sample type lookups because even though we do caching in SampleTypeService, it's still a lot of overhead to check permissions for the user */
        final Map<String, ExpSampleType> _sampleTypes = new HashMap<>();
        final Map<String, ExpDataClass> _dataClasses = new HashMap<>();

        final Container _container;
        final User _user;
        final boolean _isSample;
        final boolean _skipAliquot; // skip aliquot validation, used for update/updates cases

        final List<String> _candidateAliquotLsids; // used to check if a lsid is an aliquot, with absent "AliquotedFrom". used for merge only

        protected DerivationDataIterator(DataIterator di, DataIteratorContext context, Container container, User user, boolean isSample, boolean skipAliquot)
        {
            super(di);
            _context = context;
            _isSample = isSample;
            _skipAliquot = skipAliquot || context.getConfigParameterBoolean(SampleTypeService.ConfigParameters.DeferAliquotRuns);

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _parentNames = new LinkedHashMap<>();
            _parentCols = new HashMap<>();
            _aliquotParents = new LinkedHashMap<>();
            _candidateAliquotLsids = new ArrayList<>();
            _container = container;
            _user = user;

            Integer aliquotParentCol = -1;
            for (Map.Entry<String, Integer> entry : map.entrySet())
            {
                String name = entry.getKey();
                if (ExperimentService.isInputOutputColumn(name) || _isSample && equalsIgnoreCase("parent",name))
                {
                    _parentCols.put(entry.getValue(), entry.getKey());
                }
                else if (_isSample && "AliquotedFrom".equalsIgnoreCase(name))
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
                if (_aliquotParentCol > -1 && !_context.getConfigParameterBoolean(SampleTypeService.ConfigParameters.DeferAliquotRuns))
                {
                    Object o = get(_aliquotParentCol);
                    String aliquotParentName = null;
                    if (o != null)
                    {
                        if (o instanceof String)
                        {
                            aliquotParentName = (String) o;
                        }
                        else if (o instanceof Number)
                        {
                            aliquotParentName = o.toString();
                        }
                        else
                        {
                            getErrors().addRowError(new ValidationException("Expected string value for aliquot parent name: " + o, "AliquotedFrom"));
                        }

                        if (aliquotParentName != null)
                            _aliquotParents.put(lsid, aliquotParentName);
                    }

                    if (aliquotParentName == null && _context.getInsertOption().mergeRows)
                        _candidateAliquotLsids.add(lsid);
                }
                else if (!_skipAliquot && _context.getInsertOption().mergeRows)
                {
                    _candidateAliquotLsids.add(lsid);
                }

                Set<Pair<String, String>> allParts = new HashSet<>();
                for (Integer parentCol : _parentCols.keySet())
                {
                    Object o = get(parentCol);
                    if (o != null)
                    {
                        Collection<String> parentNames;
                        if (o instanceof String)
                        {
                            try (TabLoader tabLoader = new TabLoader((String) o))
                            {
                                tabLoader.setDelimiterCharacter(',');
                                try
                                {
                                    parentNames = Arrays.asList(tabLoader.getFirstNLines(1)[0]);
                                }
                                catch (IOException e)
                                {
                                    parentNames = Collections.emptyList();
                                    getErrors().addRowError(new ValidationException("Unable to parse parent names from " + o, _parentCols.get(parentCol)));
                                }
                            }
                        }
                        else if (o instanceof JSONArray)
                        {
                            parentNames = Arrays.stream(((JSONArray) o).toArray()).map(String::valueOf).collect(Collectors.toSet());
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

                if (!allParts.isEmpty())
                    _parentNames.put(lsid, allParts);
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

                    if (_isSample && _context.getInsertOption().mergeRows)
                    {
                        if (!_candidateAliquotLsids.isEmpty())
                        {
                            if (hasAliquots(_candidateAliquotLsids))
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
                            ExpMaterial m = ExperimentService.get().getExpMaterial(lsid);
                            if (m != null)
                            {
                                materialCache.put(m.getRowId(), m);
                            }
                            runItem = m;
                            dataType = m.getSampleType().getName();
                        }
                        else
                        {
                            ExpData d = ExperimentService.get().getExpData(lsid);
                            if (d != null)
                            {
                                dataCache.put(d.getRowId(), d);
                            }
                            runItem = d;
                        }
                        if (runItem == null) // nothing to do if the item does not exist
                            continue;

                        Pair<RunInputOutputBean, RunInputOutputBean> pair;
                        if (_isSample && _context.getInsertOption().mergeRows)
                        {
                            pair = resolveInputsAndOutputs(
                                    _user, _container, runItem, parentNames, cache, materialCache, dataCache, _sampleTypes, _dataClasses, aliquotedFrom, dataType);
                        }
                        else
                        {
                            pair = resolveInputsAndOutputs(
                                    _user, _container, null, parentNames, cache, materialCache, dataCache, _sampleTypes, _dataClasses, aliquotedFrom, dataType);
                        }

                        if (pair.first == null && pair.second == null) // no parents or children columns provided in input data and no existing parents to be updated
                            continue;

                        if (_isSample && !((ExpMaterial) runItem).isOperationPermitted(SampleTypeService.SampleOperations.EditLineage))
                            throw new ValidationException(String.format("Sample %s with status %s cannot have its lineage updated.", runItem.getName(), ((ExpMaterial) runItem).getStateLabel()));

                        // the parent columns provided in the input are all empty and there are no existing parents not mentioned in the input that need to be retained.
                        if (_isSample && _context.getInsertOption().mergeRows && pair.first.doClear())
                        {
                            Pair<Set<ExpMaterial>, Set<ExpMaterial>> previousSampleRelatives = clearSampleSourceRun(_user, (ExpMaterial) runItem);
                            String lockCheckMessage = checkForLockedSampleRelativeChange(previousSampleRelatives.first, Collections.emptySet(), runItem.getName(), "parents");
                            lockCheckMessage += checkForLockedSampleRelativeChange(previousSampleRelatives.second, Collections.emptySet(), runItem.getName(), "children");
                            if (!lockCheckMessage.isEmpty())
                                throw new ValidationException(lockCheckMessage);
                        }
                        else
                        {
                            ExpMaterial currentMaterial = null;
                            Map<ExpMaterial, String> currentMaterialMap = Collections.emptyMap();
                            Pair<Set<ExpMaterial>, Set<ExpMaterial>> previousSampleRelatives = Pair.of(Collections.emptySet(), Collections.emptySet());
                            Map<ExpData, String> currentDataMap = Collections.emptyMap();
                            if (_isSample)
                            {
                                ExpMaterial sample = (ExpMaterial) runItem;

                                if (_context.getInsertOption().mergeRows)
                                {
                                    // TODO always clear? or only when parentcols is in input? or only when new derivation is specified?
                                    // Since this entry was (maybe) already in the database, we may need to delete old derivation info
                                    previousSampleRelatives = clearSampleSourceRun(_user, sample);
                                }
                                currentMaterialMap = new HashMap<>();
                                currentMaterial = sample;
                                currentMaterialMap.put(sample, sampleRole(sample));
                            }
                            else
                            {
                                ExpData data = (ExpData) runItem;
                                currentDataMap = Collections.singletonMap(data, dataRole(data, _user));
                            }

                            if (pair.first != null)
                            {
                                // Add parent derivation run
                                Map<ExpMaterial, String> parentMaterialMap = pair.first.getMaterials();

                                String lockCheckMessage = checkForLockedSampleRelativeChange(previousSampleRelatives.first, parentMaterialMap.keySet(), runItem.getName(), "parents");
                                if (!lockCheckMessage.isEmpty())
                                    throw new ValidationException(lockCheckMessage);

                                Map<ExpData, String> parentDataMap = pair.first.getDatas();

                                record(_isSample, runRecords,
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

    private static String checkForLockedSampleRelativeChange(Set<ExpMaterial> previousSampleRelatives, Set<ExpMaterial> currentSampleRelatives, String sampleName, String relationPlural)
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
    private static Pair<Set<ExpMaterial>, Set<ExpMaterial>> clearSampleSourceRun(User user, ExpMaterial material) throws ValidationException
    {
        ExpProtocolApplication existingSourceApp = material.getSourceApplication();
        Set<ExpMaterial> previousSampleParents = Collections.emptySet();
        Set<ExpMaterial> previousSampleChildren = Collections.emptySet();
        if (existingSourceApp == null)
            return Pair.of(previousSampleParents, previousSampleChildren);

        ExpRun existingDerivationRun = existingSourceApp.getRun();
        if (existingDerivationRun == null)
            return Pair.of(previousSampleParents, previousSampleChildren);

        ExpProtocol protocol = existingDerivationRun.getProtocol();

        if (ExperimentServiceImpl.get().isSampleAliquot(protocol))
            return Pair.of(previousSampleParents, previousSampleChildren);;

        if (!ExperimentServiceImpl.get().isSampleDerivation(protocol))
        {
            throw new ValidationException(
                    "Can't remove source run '" + existingDerivationRun.getName() + "'" +
                            " of protocol '" + protocol.getName() + "'" +
                            " for sample '" + material.getName() + "' since it is not a sample derivation run");
        }

        previousSampleParents = existingDerivationRun.getMaterialInputs().keySet();
        previousSampleChildren = new HashSet<>(existingDerivationRun.getMaterialOutputs());

        List<ExpData> dataOutputs = existingDerivationRun.getDataOutputs();
        List<ExpMaterial> materialOutputs = existingDerivationRun.getMaterialOutputs();
        if (dataOutputs.isEmpty() && (materialOutputs.isEmpty() || (materialOutputs.size() == 1 && materialOutputs.contains(material))))
        {
            LOG.debug("Sample '" + material.getName() + "' has existing source derivation run '" + existingDerivationRun.getRowId() + "' -- run has no other outputs, deleting run");
            // if run has no other outputs, delete the run completely
            material.setSourceApplication(null);
            material.save(user);
            existingDerivationRun.delete(user);
        }
        else
        {
            LOG.debug("Sample '" + material.getName() + "' has existing source derivation run '" + existingDerivationRun.getRowId() + "' -- run has other " + dataOutputs.size() + " data outputs and " + materialOutputs.size() + " material outputs, removing sample from run");
            // if the existing run has other outputs, remove the run as the source application for this sample
            // and remove it as an output from the run
            material.setSourceApplication(null);
            material.save(user);
            ExpProtocolApplication outputApp = existingDerivationRun.getOutputProtocolApplication();
            if (outputApp != null)
                outputApp.removeMaterialInput(user, material);
            existingSourceApp.removeMaterialInput(user, material);
            ExperimentService.get().queueSyncRunEdges(existingDerivationRun);
        }
        return Pair.of(previousSampleParents, previousSampleChildren);
    }

    /**
     * Collect the output material or data into a run record.
     * When merge is true, the outputs will be combined with
     * an existing record with the same input parents, if possible.
     */
    private static void record(boolean merge,
                              List<UploadSampleRunRecord> runRecords,
                              Map<ExpMaterial, String> parentMaterialMap,
                              Map<ExpMaterial, String> childMaterialMap,
                              Map<ExpData, String> parentDataMap,
                              Map<ExpData, String> childDataMap,
                              ExpMaterial aliquotParent,
                              ExpMaterial aliquotChild)
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
     * @throws ExperimentException
     */
    @NotNull
    private static Pair<RunInputOutputBean, RunInputOutputBean> resolveInputsAndOutputs(User user, Container c, @Nullable ExpRunItem runItem,
                                                                                       Set<Pair<String, String>> entityNamePairs,
                                                                                       RemapCache cache,
                                                                                       Map<Integer, ExpMaterial> materialMap,
                                                                                       Map<Integer, ExpData> dataMap,
                                                                                       Map<String, ExpSampleType> sampleTypes,
                                                                                       Map<String, ExpDataClass> dataClasses,
                                                                                       @Nullable String aliquotedFrom,
                                                                                       String dataType /*sample type or source type name*/)
            throws ValidationException, ExperimentException
    {
        Map<ExpMaterial, String> parentMaterials = new LinkedHashMap<>();
        Map<ExpData, String> parentData = new LinkedHashMap<>();
        Set<String> parentDataTypesToRemove = new CaseInsensitiveHashSet();
        Set<String> parentSampleTypesToRemove = new CaseInsensitiveHashSet();

        Map<ExpMaterial, String> childMaterials = new HashMap<>();
        Map<ExpData, String> childData = new HashMap<>();
        boolean isMerge = runItem != null;

        ExpMaterial aliquotParent = null;
        boolean isAliquot = !StringUtils.isEmpty(aliquotedFrom);

        if (isAliquot)
        {
            ExpSampleType sampleType = sampleTypes.computeIfAbsent(dataType, (name) -> SampleTypeService.get().getSampleType(c, user, name));
            if (sampleType == null)
                throw new ValidationException("Invalid sample type: " + dataType);

            aliquotParent = ExperimentService.get().findExpMaterial(c, user, sampleType, dataType, aliquotedFrom, cache, materialMap);

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

            String[] parts = entityColName.split("[./]");
            if (parts.length == 1)
            {
                if (parts[0].equalsIgnoreCase("parent"))
                {
                    if (!isEmptyEntity)
                    {
                        if (isAliquot)
                        {
                            String message = "Sample derivation parent input is not allowed for aliquots.";
                            throw new ValidationException(message);
                        }

                        ExpMaterial sample = ExperimentService.get().findExpMaterial(c, user, null, null, entityName, cache, materialMap);
                        if (sample != null)
                            parentMaterials.put(sample, sampleRole(sample));
                        else
                        {
                            String message = "Sample input '" + entityName + "' not found";
                            throw new ValidationException(message);
                        }
                    }
                }
            }
            if (parts.length == 2)
            {
                String namePart = QueryKey.decodePart(parts[1]);
                if (parts[0].equalsIgnoreCase(ExpMaterial.MATERIAL_INPUT_PARENT))
                {
                    ExpSampleType sampleType = sampleTypes.computeIfAbsent(namePart, (name) -> SampleTypeService.get().getSampleType(c, user, name));
                    if (sampleType == null)
                        throw new ValidationException(String.format("Invalid import alias: parent SampleType [%1$s] does not exist or may have been deleted", namePart));

                    if (isEmptyEntity)
                    {
                        if (isMerge && !isAliquot)
                            parentSampleTypesToRemove.add(namePart);
                    }
                    else
                    {
                        if (isAliquot)
                        {
                            String message = "Sample derivation parent input is not allowed for aliquots";
                            throw new ValidationException(message);
                        }

                        ExpMaterial sample = ExperimentService.get().findExpMaterial(c, user, sampleType, namePart, entityName, cache, materialMap);
                        if (sample != null)
                            parentMaterials.put(sample, sampleRole(sample));
                        else
                            throw new ValidationException("Sample '" + entityName + "' not found in Sample Type '" + namePart + "'.");

                    }
                }
                else if (parts[0].equalsIgnoreCase(ExpMaterial.MATERIAL_OUTPUT_CHILD))
                {
                    ExpSampleType sampleType = sampleTypes.computeIfAbsent(namePart, (name) -> SampleTypeService.get().getSampleType(c, user, name));
                    if (sampleType == null)
                        throw new ValidationException(String.format("Invalid import alias: child SampleType [%1$s] does not exist or may have been deleted", namePart));

                    if (!isEmptyEntity)
                    {
                        ExpMaterial sample = ExperimentService.get().findExpMaterial(c, user, sampleType, namePart, entityName, cache, materialMap);
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
                else if (parts[0].equalsIgnoreCase(ExpData.DATA_INPUT_PARENT))
                {
                    ExpDataClass dataClass = dataClasses.computeIfAbsent(namePart, (name) -> ExperimentService.get().getDataClass(c, user, name));
                    if (dataClass == null)
                        throw new ValidationException(String.format("Invalid import alias: parent DataClass [%1$s] does not exist or may have been deleted", namePart));

                    if (isEmptyEntity)
                    {
                        if (isMerge && !isAliquot)
                            parentDataTypesToRemove.add(namePart);
                    }
                    else
                    {
                        if (isAliquot)
                        {
                            String message = entityColName + " is not allowed for aliquots";
                            throw new ValidationException(message);
                        }

                        ExpData data = ExperimentService.get().findExpData(c, user, dataClass, namePart, entityName, cache, dataMap);
                        if (data != null)
                            parentData.put(data, dataRole(data, user));
                        else
                        {

                            if (ExpSchema.DataClassCategoryType.sources.name().equalsIgnoreCase(dataClass.getCategory()))
                                throw new ValidationException("Source '" + entityName + "' not found in Source Type  '" + namePart + "'.");
                            else
                                throw new ValidationException("Data input '" + entityName + "' not found in in Data Class '" + namePart + "'.");
                        }
                    }
                }
                else if (parts[0].equalsIgnoreCase(ExpData.DATA_OUTPUT_CHILD))
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

        if (isMerge)
        {
            ExpLineageOptions options = new ExpLineageOptions();
            options.setChildren(false);
            options.setDepth(2); // use 2 to get the first generation of parents because the first "parent" is the run

            ExpLineage lineage = ExperimentService.get().getLineage(c, user, runItem, options);
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
        final Function<List<String>, Runnable> _indexFunction;

        public SearchIndexIteratorBuilder(DataIteratorBuilder pre, Function<List<String>, Runnable> indexFunction)
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

    private static class SearchIndexIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final Integer _lsidCol;
        final ArrayList<String> _lsids;
        final Function<List<String>, Runnable> _indexFunction;

        protected SearchIndexIterator(DataIterator di, DataIteratorContext context, Function<List<String>, Runnable> indexFunction)
        {
            super(di);
            _context = context;
            _indexFunction = indexFunction;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _lsids = new ArrayList<>(100);
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            if (hasNext)
            {
                String lsid = (String) get(_lsidCol);
                if (null != lsid)
                    _lsids.add(lsid);
            }
            else
            {
                final SearchService ss = SearchService.get();
                if (null != ss)
                {
                    final ArrayList<String> lsids = new ArrayList<>(_lsids);
                    final Runnable indexTask = _indexFunction.apply(lsids);

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
        Supplier<?>[] suppliers;
        String[] savedFileName;

        FileLinkDataIterator(final DataIterator in, final DataIteratorContext context, Container c, String file_link_dir_name)
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
                                Object file = AbstractQueryUpdateService.saveFile(c, col.getName(), value, file_link_dir_name);
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
        public boolean next() throws BatchValidationException
        {
            Arrays.fill(savedFileName, null);
            return super.next();
        }
    }

    public static final Set<String> NOT_FOR_UPDATE = Sets.newCaseInsensitiveHashSet(
            ExpDataTable.Column.LSID.toString(),
            ExpDataTable.Column.Created.toString(),
            ExpDataTable.Column.CreatedBy.toString(),
            ExpMaterialTable.Column.AliquotedFromLSID.toString(),
            "genId");

    public static class PersistDataIteratorBuilder implements DataIteratorBuilder
    {
        private final DataIteratorBuilder _in;
        private final TableInfo _expTable;
        private final TableInfo _propertiesTable;
        private final Container _container;
        private final User _user;
        private final Set<String> _excludedColumns = new HashSet<>(List.of("generated","runId","sourceapplicationid")); // generated has database DEFAULT 0

        private String _fileLinkDirectory = null;
        Function<List<String>, Runnable> _indexFunction;
        Map<String, String> _importAliases;


        // expTable is the shared experiment table e.g. exp.Data or exp.Materials
        public PersistDataIteratorBuilder(@NotNull DataIteratorBuilder in, TableInfo expTable, TableInfo propsTable, Container container, User user, Map<String, String> importAliases, @Nullable Integer ownerObjectId)
        {
            _in = in;
            _expTable = expTable;
            _propertiesTable = propsTable;
            _container = container;
            _user = user;
            _importAliases = importAliases;
        }

        public PersistDataIteratorBuilder setIndexFunction(Function<List<String>, Runnable> indexFunction)
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

            // add FileLink DataIterator if any input columns are of type FILE_LINK
            if (null != _fileLinkDirectory)
            {
                boolean hasFileLink = false;
                for (int i = 0; i < input.getColumnCount(); i++)
                    hasFileLink |= PropertyType.FILE_LINK == input.getColumnInfo(i).getPropertyType();
                if (hasFileLink)
                    input = LoggingDataIterator.wrap(new FileLinkDataIterator(input, context, _container, _fileLinkDirectory));
            }

            final Map<String, Integer> colNameMap = DataIteratorUtil.createColumnNameMap(input);

            Map<String, String> aliases = _importAliases != null ?
                    _importAliases :
                    new CaseInsensitiveHashMap<>();

            assert _expTable instanceof ExpMaterialTableImpl || _expTable instanceof ExpDataClassDataTableImpl;
            boolean isSample = _expTable instanceof ExpMaterialTableImpl;

            SimpleTranslator step0 = new SimpleTranslator(input, context);
            step0.selectAll(Sets.newCaseInsensitiveHashSet("alias"), aliases);
            if (colNameMap.containsKey("alias"))
                step0.addColumn(ExperimentService.ALIASCOLUMNALIAS, colNameMap.get("alias")); // see AliasDataIteratorBuilder

            CaseInsensitiveHashSet dontUpdate = new CaseInsensitiveHashSet();
            dontUpdate.addAll(NOT_FOR_UPDATE);
            CaseInsensitiveHashSet keyColumns = new CaseInsensitiveHashSet();
            if (isSample || !context.getInsertOption().mergeRows)
            {
                keyColumns.add(ExpDataTable.Column.LSID.toString());
                if (isSample)
                {
                    dontUpdate.addAll(((ExpMaterialTableImpl) _expTable).getUniqueIdFields());
                    dontUpdate.add(ExpMaterialTable.Column.RootMaterialLSID.toString());
                    dontUpdate.add(ExpMaterialTable.Column.AliquotedFromLSID.toString());
                }
            }
            else
            {
                keyColumns.add("classid");
                keyColumns.add("name");
            }

            // Since we support detailed audit logging add the ExistingRecordDataIterator here just before TableInsertDataIterator
            // this is a NOOP unless we are merging and detailed logging is enabled
            DataIteratorBuilder step1 = ExistingRecordDataIterator.createBuilder(step0, _expTable, Set.of(ExpDataTable.Column.LSID.toString()), true);

            // Insert into exp.data then the provisioned table
            // Use embargo data iterator to ensure rows are committed before being sent along Issue 26082 (row at a time, reselect rowid)
            DataIteratorBuilder step2 = LoggingDataIterator.wrap(new TableInsertDataIteratorBuilder(step1, _expTable, _container)
                    .setKeyColumns(keyColumns)
                    .setDontUpdate(dontUpdate)
                    .setAddlSkipColumns(_excludedColumns)
                    .setCommitRowsBeforeContinuing(true));

            // pass in remap columns to help reconcile columns that may be aliased in the virtual table
            DataIteratorBuilder step3 = LoggingDataIterator.wrap(new TableInsertDataIteratorBuilder(step2, _propertiesTable, _container)
                    .setKeyColumns(keyColumns)
                    .setDontUpdate(dontUpdate)
                    .setVocabularyProperties(PropertyService.get().findVocabularyProperties(_container, colNameMap.keySet()))
                    .setRemapSchemaColumns(((UpdateableTableInfo)_expTable).remapSchemaColumns()));

            DataIteratorBuilder step4 = step3;
            if (colNameMap.containsKey("flag") || colNameMap.containsKey("comment"))
            {
                step4 = LoggingDataIterator.wrap(new ExpDataIterators.FlagDataIteratorBuilder(step3, _user, isSample));
            }

            // Wire up derived parent/child data and materials
            DataIteratorBuilder step5 = LoggingDataIterator.wrap(new ExpDataIterators.DerivationDataIteratorBuilder(step4, _container, _user, isSample, false));

            // Hack: add the alias and lsid values back into the input so we can process them in the chained data iterator
            DataIteratorBuilder step6 = step5;
            if (null != _indexFunction)
                step6 = LoggingDataIterator.wrap(new ExpDataIterators.SearchIndexIteratorBuilder(step5, _indexFunction)); // may need to add this after the aliases are set

            return LoggingDataIterator.wrap(step6.getDataIterator(context));
        }
    }
}
