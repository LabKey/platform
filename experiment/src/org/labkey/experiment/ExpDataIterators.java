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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.labkey.api.assay.AbstractAssayProvider;
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
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.experiment.api.AliasInsertHelper;
import org.labkey.experiment.api.ExpDataClassDataTableImpl;
import org.labkey.experiment.api.ExpMaterialTableImpl;
import org.labkey.experiment.api.SampleTypeUpdateServiceDI;
import org.labkey.experiment.controllers.exp.RunInputOutputBean;
import org.labkey.experiment.samples.UploadSamplesHelper;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        private final TableInfo _expTable;
        private final boolean _isSample;

        public AutoLinkToStudyDataIteratorBuilder(@NotNull DataIteratorBuilder in, boolean isSample, Container container, User user, TableInfo expTable)
        {
            _in = in;
            _container = container;
            _user = user;
            _expTable = expTable;
            _isSample = isSample;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _in.getDataIterator(context);
            return LoggingDataIterator.wrap(new AutoLinkToStudyDataIterator(pre, context, _isSample, _container, _user, _expTable));
        }
    }

    private static class AutoLinkToStudyDataIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final boolean _isSample;
        final Container _container;
        final User _user;
        final TableInfo _expTable;

        Study _study;
        final Supplier<Object> _participantIDCol;
        final Supplier<Object> _dateCol;
        final Supplier<Object> _visitIdCol;
        final Supplier<Object> _lsidCol;
        final Supplier<Object> _rowIdCol;
        final List<Map<String, Object>> _rows = new ArrayList<>();

        final String PARTICIPANT = StudyPublishService.PARTICIPANTID_PROPERTY_NAME;
        final String DATE = StudyPublishService.DATE_PROPERTY_NAME;
        final String VISIT = StudyPublishService.SEQUENCENUM_PROPERTY_NAME;
        final String LSID = StudyPublishService.SOURCE_LSID_PROPERTY_NAME;
        final String ROWID = ExpMaterialTable.Column.RowId.toString();

        protected AutoLinkToStudyDataIterator(DataIterator di, DataIteratorContext context, boolean isSample, Container container, User user,  TableInfo expTable)
        {
            super(di);
            _context = context;

            _isSample = isSample;
            _container = container;
            _user = user;
            _expTable = expTable;

            if (_expTable instanceof  ExpMaterialTableImpl)
            {
                @Nullable Container targetContainer = ((ExpMaterialTableImpl) _expTable).getSampleType().getAutoLinkTargetContainer();
                StudyService studyService = StudyService.get();
                if (_study == null && targetContainer != null && studyService != null) {
                    _study = studyService.getStudy(targetContainer);
                }
                // Issue43234: Support '(Data import folder)' auto-link option
                if (_study == null && targetContainer == StudyPublishService.AUTO_LINK_TARGET_IMPORT_FOLDER && studyService != null)
                    _study = studyService.getStudy(container);
            }
            final String visitName = AbstractAssayProvider.VISITID_PROPERTY_NAME;
            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _participantIDCol = map.get(PARTICIPANT) != null ? di.getSupplier(map.get(PARTICIPANT)) : null;
            _dateCol = map.get(DATE) != null ? di.getSupplier(map.get(DATE)) : null;
            _visitIdCol = map.get(visitName) != null ? di.getSupplier(map.get(visitName)) : null;
            _lsidCol = map.get("LSID") != null ? di.getSupplier(map.get("LSID")) : null;
            _rowIdCol = map.get(ROWID) != null ? di.getSupplier(map.get(ROWID)) : null;
        }

        private BatchValidationException getErrors()
        {
            return _context.getErrors();
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            // if there is no _study set to auto-link, then skip processing
            if (getErrors().hasErrors() || !(_expTable instanceof ExpMaterialTableImpl) || _study == null)
                return hasNext;

            ExpSampleType sampleType = ((ExpMaterialTableImpl) _expTable).getSampleType();

            if (!hasNext)
            {
                if (_rows.size() > 0 && _isSample)
                    StudyPublishService.get().autoLinkSampleType(sampleType, _rows, _container, _user);
                return false;
            }

            boolean isVisitBased = _study.getTimepointType().isVisitBased();
            boolean haveVisitCol = isVisitBased ? _visitIdCol != null : _dateCol != null;
            if (_participantIDCol != null && _rowIdCol != null && _lsidCol != null && haveVisitCol)
            {
                String participantId = _participantIDCol.get() != null ? _participantIDCol.get().toString() : null;
                Object date = _dateCol != null ? _dateCol.get() : null;
                Object visit = _visitIdCol != null ? _visitIdCol.get(): null;
                Object lsid = _lsidCol.get();
                int rowId = ((Number) _rowIdCol.get()).intValue();

                // Only link rows that have a participant and a visit/date. Return if this is not the case
                if (participantId == null || (isVisitBased && visit == null) || (!isVisitBased && date == null))
                    return true;

                Float visitId = null;
                Date dateId = null;

                // 13647: Conversion exception in auto link to study
                if (isVisitBased)
                {
                    visitId = Float.parseFloat(visit.toString());
                }
                else
                {
                    dateId = (Date) ConvertUtils.convert(date.toString(), Date.class);
                }

                Map<String,Object> row = new HashMap<>();
                row.put(PARTICIPANT, participantId);
                row.put(LSID, lsid);
                row.put(ROWID, rowId);
                if (visitId != null)
                    row.put(VISIT, visitId);
                if (dateId != null)
                    row.put(DATE, dateId);

                _rows.add(row);
            }
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

                if (lsidValue instanceof String)
                {
                    String lsid = (String)lsidValue;
                    String flag = Objects.toString(flagValue, null);

                    try
                    {
                        if (_isSample)
                        {
                            ExpMaterial sample = ExperimentService.get().getExpMaterial(lsid);
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
        ExpDataIterators.DerivationDataIteratorBuilder ddib = new ExpDataIterators.DerivationDataIteratorBuilder(DataIteratorBuilder.wrap(di), container, user, isSample, skipAliquot);
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
            _skipAliquot = skipAliquot;

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
                if (UploadSamplesHelper.isInputOutputHeader(name) || _isSample && equalsIgnoreCase("parent",name))
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
                if (_aliquotParentCol > -1)
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
                            parentNames = Arrays.asList(((String) o).split(","));
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

                    List<UploadSamplesHelper.UploadSampleRunRecord> runRecords = new ArrayList<>();
                    Set<String> lsids = new LinkedHashSet<>();
                    lsids.addAll(_parentNames.keySet());
                    lsids.addAll(_aliquotParents.keySet());
                    for (String lsid : lsids)
                    {
                        Set<Pair<String, String>> parentNames = _parentNames.containsKey(lsid) ? _parentNames.get(lsid) : Collections.emptySet();

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
                            pair = UploadSamplesHelper.resolveInputsAndOutputs(
                                    _user, _container, runItem, parentNames, cache, materialCache, dataCache, _sampleTypes, _dataClasses, aliquotedFrom, dataType);
                        }
                        else
                        {
                            pair = UploadSamplesHelper.resolveInputsAndOutputs(
                                    _user, _container, null, parentNames, cache, materialCache, dataCache, _sampleTypes, _dataClasses, aliquotedFrom, dataType);
                        }

                        if (pair.first == null && pair.second == null) // no parents or children columns provided in input data and no existing parents to be updated
                            continue;

                        if (_isSample && !((ExpMaterial) runItem).isOperationPermitted(ExperimentService.SampleOperations.EditLineage))
                            throw new ValidationException(String.format("Sample %s with status %s cannot be have its lineage updated.", runItem.getName(), ((ExpMaterial) runItem).getStatusLabel()));

                        // the parent columns provided in the input are all empty and there are no existing parents not mentioned in the input that need to be retained.
                        if (_isSample && _context.getInsertOption().mergeRows && pair.first.doClear())
                        {
                            Pair<Set<ExpMaterial>, Set<ExpMaterial>> previousSampleRelatives = UploadSamplesHelper.clearSampleSourceRun(_user, (ExpMaterial) runItem);
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
                                    previousSampleRelatives = UploadSamplesHelper.clearSampleSourceRun(_user, sample);
                                }
                                currentMaterialMap = new HashMap<>();
                                currentMaterial = sample;
                                currentMaterialMap.put(sample, UploadSamplesHelper.sampleRole(sample));
                            }
                            else
                            {
                                ExpData data = (ExpData) runItem;
                                currentDataMap = Collections.singletonMap(data, UploadSamplesHelper.dataRole(data, _user));
                            }

                            if (pair.first != null)
                            {
                                // Add parent derivation run
                                Map<ExpMaterial, String> parentMaterialMap = pair.first.getMaterials();

                                String lockCheckMessage = checkForLockedSampleRelativeChange(previousSampleRelatives.first, parentMaterialMap.keySet(), runItem.getName(), "parents");
                                if (!lockCheckMessage.isEmpty())
                                    throw new ValidationException(lockCheckMessage);

                                Map<ExpData, String> parentDataMap = pair.first.getDatas();

                                UploadSamplesHelper.record(_isSample, runRecords,
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

                                UploadSamplesHelper.record(false, runRecords,
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
        Set<ExpMaterial> lockedRelatives = previousSampleRelatives.stream()
                .filter(sample -> !sample.isOperationPermitted(ExperimentService.SampleOperations.EditLineage))
                .collect(Collectors.toSet());

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
        Set<ExpMaterial> addedLocked = currentSampleRelatives
                .stream()
                .filter(material -> !lockedRelativeLsids.contains(material.getLSID()) &&
                        !material.isOperationPermitted(ExperimentService.SampleOperations.EditLineage))
                .collect(Collectors.toSet());

        if (!addedLocked.isEmpty())
        {
            String message = String.format("One or more of the new %s for sample %s has a status that prevents the updating of lineage", relationPlural, sampleName);
            if (addedLocked.size() <= 10)
                message += ": " + addedLocked.stream().map(ExpMaterial::getNameAndStatus).collect(Collectors.joining(", "));
            message += ".";
            messages.add(message);
        }
        return StringUtils.join(messages, " ");
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
        private final Integer _ownerObjectId;

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
            _ownerObjectId = ownerObjectId;
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
                    .setAddlSkipColumns(Set.of("generated","runId","sourceapplicationid"))     // generated has database DEFAULT 0
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
