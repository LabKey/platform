/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.study.model;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.log4j.Logger;
import org.labkey.api.dataiterator.ScrollableDataIterator;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.ErrorIterator;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.Pair;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.query.DatasetTableImpl;
import org.labkey.study.writer.DefaultStudyDesignWriter;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

class DatasetDataIteratorBuilder implements DataIteratorBuilder
{
    private DatasetDefinition _datasetDefinition;
    User user;
    boolean needsQC;
    QCState defaultQC;
    List<String> lsids = null;
    DatasetDefinition.CheckForDuplicates checkDuplicates = DatasetDefinition.CheckForDuplicates.never;
    boolean isForUpdate = false;
    boolean useImportAliases = false;
    Logger logger = null;
    StudyImportContext _studyImportContext;


    DataIteratorBuilder builder = null;
    DataIterator input = null;

    ValidationException setupError = null;

    DatasetDataIteratorBuilder(DatasetDefinition datasetDefinition, User user, boolean qc, QCState defaultQC, StudyImportContext studyImportContext)
    {
        _datasetDefinition = datasetDefinition;
        this.user = user;
        this.needsQC = qc;
        this.defaultQC = defaultQC;
        _studyImportContext = studyImportContext;
    }

    /**
     * StudyServiceImpl.updateDatasetRow() is implemented as a delete followed by insert.
     * This is very gross, and it causes a special case here, as we want to re-use any server
     * managed keys, instead of regenerating them.
     *
     * @param update
     */
    void setForUpdate(boolean update)
    {
        this.isForUpdate = update;
    }

    void setUseImportAliases(boolean aliases)
    {
        this.useImportAliases = aliases;
    }

    void setCheckDuplicates(DatasetDefinition.CheckForDuplicates check)
    {
        checkDuplicates = check;
    }

    void setInput(DataIteratorBuilder b)
    {
        builder = b;
    }

    void setInput(DataIterator it)
    {
        input = it;
    }

    void setLogger(Logger logger)
    {
        this.logger = logger;
    }

    void setKeyList(List<String> lsids)
    {
        this.lsids = lsids;
    }

    void setupError(String msg)
    {
        if (null == setupError)
            setupError = new ValidationException();
        setupError.addGlobalError(msg);
    }

    @Override
    public DataIterator getDataIterator(DataIteratorContext context)
    {
        Map<Enum, Object> contextConfig = context.getConfigParameters();
        if (null == contextConfig)
            contextConfig = Collections.emptyMap();

        // might want to make allow importManagedKey an explicit option, for now allow for GUID
        boolean allowImportManagedKey = isForUpdate || _datasetDefinition.getKeyManagementType() == Dataset.KeyManagementType.GUID;
        boolean isManagedKey = _datasetDefinition.getKeyType() == Dataset.KeyType.SUBJECT_VISIT_OTHER && _datasetDefinition.getKeyManagementType() != Dataset.KeyManagementType.None;

        TimepointType timetype = _datasetDefinition.getStudy().getTimepointType();
        TableInfo table = _datasetDefinition.getTableInfo(user, false);

        ColumnInfo subjectCol = table.getColumn(_datasetDefinition.getStudy().getSubjectColumnName());
        String keyColumnName = _datasetDefinition.getKeyPropertyName();
        ColumnInfo keyColumn = null == keyColumnName ? null : table.getColumn(keyColumnName);
        ColumnInfo lsidColumn = table.getColumn("lsid");
        ColumnInfo seqnumColumn = table.getColumn("sequenceNum");
        ColumnInfo containerColumn = table.getColumn("container");
        ColumnInfo visitdateColumn = table.getColumn("date");

        if (null == input && null != builder)
            input = builder.getDataIterator(context);

        input = LoggingDataIterator.wrap(input);

        DatasetColumnsIterator it = new DatasetColumnsIterator(_datasetDefinition, input, context, user);

        ValidationException matchError = new ValidationException();
        ArrayList<ColumnInfo> inputMatches = DataIteratorUtil.matchColumns(input, table, useImportAliases, matchError);
        if (matchError.hasErrors())
            setupError(matchError.getMessage());

        // select all columns except those we explicitly calculate (e.g. lsid)
        for (int in = 1; in <= input.getColumnCount(); in++)
        {
            ColumnInfo inputColumn = input.getColumnInfo(in);
            ColumnInfo match = inputMatches.get(in);

            if (null != match)
            {
                inputColumn.setPropertyURI(match.getPropertyURI());

                if (match == lsidColumn || match == seqnumColumn)
                    continue;

                // We usually ignore incoming containerColumn.  However, if we're in a dataspace study
                // we can internally target multiple containers

                if (match == containerColumn)
                {
                    boolean targetMultiple = Boolean.TRUE == contextConfig.get(QueryUpdateService.ConfigParameters.TargetMultipleContainers);
                    JdbcType jdbcType = inputColumn.getJdbcType();
                    if (_datasetDefinition.getStudy().isDataspaceStudy() && targetMultiple && (JdbcType.GUID == jdbcType || JdbcType.VARCHAR == jdbcType))
                        it.addColumn(in);
                    continue;
                }

                if (match == keyColumn && isManagedKey && !allowImportManagedKey)
                {
                    // TODO silently ignore or add error?
                    continue;
                }

                int out;
                if (DefaultStudyDesignWriter.isColumnNumericForeignKeyToDataspaceTable(match.getFk(), true))
                {
                    // Use rowId mapping tables or extra column if necessary to map FKs
                    FieldKey extraColumnFieldKey = DefaultStudyDesignWriter.getExtraForeignKeyColumnFieldKey(match, match.getFk());
                    Map<Object, Object> dataspaceTableIdMap = Collections.emptyMap();
                    if (null != _studyImportContext && null != match.getFk())
                    {
                        String lookupTableName = match.getFk().getLookupTableName();
                        dataspaceTableIdMap = _studyImportContext.getTableIdMap(lookupTableName);
                    }
                    out = it.addSharedTableLookupColumn(in, extraColumnFieldKey, match.getFk(),
                            dataspaceTableIdMap);
                }
                else if (match == keyColumn && _datasetDefinition.getKeyManagementType() == Dataset.KeyManagementType.None)
                {
                    // usually we let DataIterator handle convert, but we need to convert for consistent _key/lsid generation
                    out = it.addConvertColumn(match.getName(), in, match.getJdbcType(), null != match.getMvColumnName());
                }
                else if (match == keyColumn && _datasetDefinition.getKeyManagementType() == Dataset.KeyManagementType.GUID)
                {
                    // make sure guid is not null (12884)
                    out = it.addCoaleseColumn(match.getName(), in, new SimpleTranslator.GuidColumn());
                }
                else
                {
                    // to simplify a little, use matched name/propertyuri here (even though StandardDataIteratorBuilder would rematch using the same logic)
                    out = it.addColumn(match.getName(), in);
                }
                it.getColumnInfo(out).setPropertyURI(match.getPropertyURI());
            }
            else
            {
                it.addColumn(in);
            }
        }

        Map<String, Integer> inputMap = DataIteratorUtil.createColumnAndPropertyMap(input);
        Map<String, Integer> outputMap = DataIteratorUtil.createColumnAndPropertyMap(it);

        // find important columns in the input
        // NOTE: Standard DataIterator usually is responsible for matching input columns by label/uri/importAlias
        // NOTE: However, this Builder is _before_ StandardDataIteratorBuilder, so for these columns we have to duplicate that effort
        Integer indexPTIDInput = findColumnInMap(inputMap, subjectCol);
        Integer indexPTID = findColumnInMap(outputMap, subjectCol);
        Integer indexKeyProperty = findColumnInMap(outputMap, keyColumn);
        Integer indexVisitDate = findColumnInMap(outputMap, visitdateColumn);
        Integer indexContainer = outputMap.get(containerColumn);
        Integer indexReplace = outputMap.get("replace");

        // do a conversion for PTID aliasing
        Integer translatedIndexPTID = indexPTID;
        try
        {
            translatedIndexPTID = it.translatePtid(indexPTIDInput, user);
        }
        catch (ValidationException e)
        {
            context.getErrors().addRowError(e);
        }

        // For now, just specify null for sequence num index... we'll add it below
        it.setSpecialOutputColumns(translatedIndexPTID, null, indexVisitDate, indexKeyProperty, indexContainer);
        it.setTimepointType(timetype);

        /* NOTE: these columns must be added in dependency order
         *
         * sequencenum -> date
         * participantsequence -> ptid, sequencenum
         * lsid -> ptid, sequencenum, key
         */

        //
        // date
        //

        if (!timetype.isVisitBased() && null == indexVisitDate && (_datasetDefinition.isDemographicData() || _datasetDefinition.isParticipantAliasDataset()))
        {
            final Date start = _datasetDefinition.getStudy().getStartDate();
            indexVisitDate = it.addColumn(new ColumnInfo("Date", JdbcType.TIMESTAMP), new Callable()
            {
                @Override
                public Object call() throws Exception
                {
                    return start;
                }
            });
            it.indexVisitDateOutput = indexVisitDate;
        }

        //
        // SequenceNum
        //

        Integer indexVisitDateColumnInput = findColumnInMap(inputMap, visitdateColumn);
        Integer indexSequenceNumColumnInput = findColumnInMap(inputMap, seqnumColumn);
        it.indexSequenceNumOutput = it.translateSequenceNum(indexSequenceNumColumnInput, indexVisitDateColumnInput);

        if (null == indexKeyProperty)
        {
            //
            // ROWID
            //

            if (_datasetDefinition.getKeyManagementType() == Dataset.KeyManagementType.RowId)
            {
                ColumnInfo key = new ColumnInfo(keyColumn);
                Supplier call = new SimpleTranslator.AutoIncrementColumn()
                {
                    @Override
                    protected int getFirstValue()
                    {
                        return _datasetDefinition.getMaxKeyValue() + 1;
                    }
                };
                indexKeyProperty = it.addColumn(key, call);
            }

            //
            // GUID
            //

            else if (_datasetDefinition.getKeyManagementType() == Dataset.KeyManagementType.GUID)
            {
                if (keyColumn == null)
                {
                    throw new IllegalStateException("Could not find key column '" + keyColumnName + "' in dataset " + _datasetDefinition.getName());
                }
                ColumnInfo key = new ColumnInfo(keyColumn);
                indexKeyProperty = it.addColumn(key, new SimpleTranslator.GuidColumn());
            }

            //
            // Time portion of Date field
            //
            else if (_datasetDefinition.getUseTimeKeyField())
            {
                indexKeyProperty = indexVisitDate;
            }
        }


        //
        // _key
        //

        if (null != indexKeyProperty)
        {
            it.indexKeyPropertyOutput = it.addAliasColumn("_key", indexKeyProperty, JdbcType.VARCHAR);
        }

        //
        // ParticipantSequenceNum
        //

        it.addParticipantSequenceNum();


        //
        // LSID
        //

        // NOTE have to add LSID after columns it depends on
        int indexLSID = it.addLSID();


        //
        // QCSTATE
        //

        if (needsQC)
        {
            Integer indexInputQCState = findColumnInMap(inputMap, table.getColumn(DatasetTableImpl.QCSTATE_ID_COLNAME));
            Integer indexInputQCText = inputMap.get(DatasetTableImpl.QCSTATE_LABEL_COLNAME);
            if (null == indexInputQCState)
            {
                int indexText = null == indexInputQCText ? -1 : indexInputQCText;
                it.addQCStateColumn(indexText,  DatasetDefinition.getQCStateURI(), defaultQC);
            }
        }


        //
        // check errors, misc
        //

        it.setKeyList(lsids);

        it.setDebugName(_datasetDefinition.getName());

        // don't bother going on if we don't have these required columns
        if (null == indexPTIDInput) // input
            setupError("Missing required field " + _datasetDefinition.getStudy().getSubjectColumnName());

        if (!timetype.isVisitBased() && null == indexVisitDate)
            setupError("Missing required field Date");

        if (timetype.isVisitBased() && null == it.indexSequenceNumOutput)
            setupError("Missing required field SequenceNum");

        it.setInput(ErrorIterator.wrap(input, context, false, setupError));
        DataIterator ret = LoggingDataIterator.wrap(it);

        //
        // Check Duplicates
        //

        boolean hasError = null != setupError && setupError.hasErrors();
        if (checkDuplicates != DatasetDefinition.CheckForDuplicates.never && !hasError)
        {
            Integer indexVisit = timetype.isVisitBased() ? it.indexSequenceNumOutput : indexVisitDate;
            // no point if required columns are missing
            if (null != translatedIndexPTID && null != indexVisit)
            {
                ScrollableDataIterator scrollable = DataIteratorUtil.wrapScrollable(ret);
                _datasetDefinition.checkForDuplicates(scrollable, indexLSID,
                        translatedIndexPTID, null == indexVisit ? -1 : indexVisit, null == indexKeyProperty ? -1 : indexKeyProperty, null == indexReplace ? -1 : indexReplace,
                        context, logger,
                        checkDuplicates);
                scrollable.beforeFirst();
                ret = scrollable;
            }
        }

        return ret;
    }


    static <V> V findColumnInMap(Map<String,V> map, ColumnInfo c)
    {
        if (null == c)
            return null;
        if (map.containsKey(c.getName()))
            return map.get(c.getName());
        if (null != c.getPropertyURI() && map.containsKey(c.getPropertyURI()))
            return map.get(c.getPropertyURI());
        if (null != c.getLabelValue() && map.containsKey(c.getLabelValue()))
            return map.get(c.getLabelValue());
        for (String alias : c.getImportAliasSet())
            if (map.containsKey(alias))
                return map.get(alias);
        return null;
    }


    /**
     * Created by matthew on 6/23/2016.
     */
    static class DatasetColumnsIterator extends SimpleTranslator
    {
        private DatasetDefinition _datasetDefinition;
        DecimalFormat _sequenceFormat = new DecimalFormat("0.0000");
        Converter convertDate = ConvertUtils.lookup(Date.class);
        List<String> lsids;
        User user;
        private int _maxPTIDLength;
    //        boolean requiresKeyLock = false;

        // these columns are used to compute derived columns, should occur early in the output list
        Integer indexPtidOutput, indexSequenceNumOutput, indexVisitDateOutput, indexKeyPropertyOutput;
        Integer indexContainerOutput;
        // for returning lsid list
        Integer indexLSIDOutput;

        TimepointType timetype;

        DatasetColumnsIterator(DatasetDefinition datasetDefinition, DataIterator data, DataIteratorContext context, User user) // , String keyPropertyURI, boolean qc, QCState defaultQC, Map<String, QCState> qcLabels)
        {
            super(data, context);
            _datasetDefinition = datasetDefinition;
            this.user = user;
            _maxPTIDLength = _datasetDefinition.getTableInfo(this.user, false).getColumn("ParticipantID").getScale();
        }

        void setSpecialOutputColumns(Integer indexPTID, Integer indexSequenceNum, Integer indexVisitDate, Integer indexKeyProperty, Integer indexContainer)
        {
            this.indexPtidOutput = indexPTID;
            this.indexSequenceNumOutput = indexSequenceNum;
            this.indexVisitDateOutput = indexVisitDate;
            this.indexKeyPropertyOutput = indexKeyProperty;
            this.indexContainerOutput = indexContainer;
        }

        void setTimepointType(TimepointType timetype)
        {
            this.timetype = timetype;
        }

        void setKeyList(List<String> lsids)
        {
            this.lsids = lsids;
        }


        @Override
        public void beforeFirst()
        {
            super.beforeFirst();
            if (null != lsids)
                lsids.clear();
        }


        @Override
        public boolean next() throws BatchValidationException
        {
    //            assert getKeyManagementType() != KeyManagementType.RowId || Thread.holdsLock(getManagedKeyLock());
    //            assert DbSchema.get("study").getScope().isTransactionActive();

            boolean hasNext = super.next();
            if (hasNext)
            {
                Object ptidObject = get(indexPtidOutput);
                if (ptidObject != null && ptidObject.toString().length() > _maxPTIDLength)
                {
                    throw new BatchValidationException(Collections.singletonList(new ValidationException(_datasetDefinition.getStudy().getSubjectColumnName() + " value '" + ptidObject + "' is too long, maximum length is " + _maxPTIDLength + " characters")), Collections.emptyMap());
                }
                if (null != lsids && null != indexLSIDOutput)
                {
                    try
                    {
                        lsids.add((String) get(indexLSIDOutput));
                    }
                    catch (RuntimeException x)
                    {
                        throw x;
                    }
                    catch (Exception x)
                    {
                        throw new RuntimeException(x);
                    }
                }
            }
            return hasNext;
        }

        Double getOutputDouble(int i)
        {
            Object o = get(i);
            if (null == o)
                return null;
            if (o instanceof Number)
                return ((Number) o).doubleValue();
            if (o instanceof String)
            {
                try
                {
                    return Double.parseDouble((String) o);
                }
                catch (NumberFormatException x)
                {
                    ;
                }
            }
            return null;
        }

        Date getOutputDate(int i)
        {
            Object o = get(i);
            Date date = (Date) convertDate.convert(Date.class, o);
            return date;
        }

        String getInputString(int i)
        {
            Object o = getInput().get(i);
            return null == o ? "" : o.toString();
        }

        String getOutputString(int i)
        {
            Object o = this.get(i);
            return null == o ? "" : o.toString();
        }

        int addQCStateColumn(int index, String uri, QCState defaultQCState)
        {
            ColumnInfo qcCol = new ColumnInfo("QCState", JdbcType.INTEGER);
            qcCol.setPropertyURI(uri);
            Callable qcCall = new QCStateColumn(index, defaultQCState);
            return addColumn(qcCol, qcCall);
        }

    //        int addSequenceNumFromDateColumn()
    //        {
    //            return addColumn(new ColumnInfo("SequenceNum", JdbcType.DOUBLE), new SequenceNumFromDateColumn());
    //        }
    //
    //        int translateColumn(final int index, Map<?, ?> map, boolean strict)
    //        {
    //            ColumnInfo existing = getColumnInfo(index);
    //            Callable origCallable = this._outputColumns.get(index).getValue();
    //            RemapColumn remapColumn = new RemapColumn(origCallable, map, strict);
    //            return replaceOrAddColumn(index, existing, remapColumn);
    //        }

        int translateSequenceNum(Integer indexSequenceNumInput, Integer indexVisitDateInput)
        {
            ColumnInfo col = new ColumnInfo("SequenceNum", JdbcType.DOUBLE);
            SequenceNumImportHelper snih = new SequenceNumImportHelper(_datasetDefinition.getStudy(), _datasetDefinition);
            Callable call = snih.getCallable(getInput(), indexSequenceNumInput, indexVisitDateInput);
            return addColumn(col, call);
        }

        int translatePtid(Integer indexPtidInput, User user) throws ValidationException
        {
            ColumnInfo col = new ColumnInfo("ParticipantId", JdbcType.VARCHAR);
            ParticipantIdImportHelper piih = new ParticipantIdImportHelper(_datasetDefinition.getStudy(), user, _datasetDefinition);
            Callable call = piih.getCallable(getInput(), indexPtidInput);
            return addColumn(col, call);
        }

        int addLSID()
        {
            ColumnInfo col = new ColumnInfo("lsid", JdbcType.VARCHAR);
            indexLSIDOutput = addColumn(col, new LSIDColumn());
            return indexLSIDOutput;
        }

        int addParticipantSequenceNum()
        {
            ColumnInfo col = new ColumnInfo("participantsequencenum", JdbcType.VARCHAR);
            return addColumn(col, new ParticipantSequenceNumColumn());
        }

        int replaceOrAddColumn(Integer index, ColumnInfo col, Callable call)
        {
            if (null == index || index <= 0)
                return addColumn(col, call);
            Pair p = new Pair(col, call);
            _outputColumns.set(index, p);
            return index;
        }

        String getFormattedSequenceNum()
        {
            assert null != indexSequenceNumOutput || hasErrors();
            if (null == indexSequenceNumOutput)
                return null;
            Double d = getOutputDouble(indexSequenceNumOutput);
            if (null == d)
                return null;
            return _sequenceFormat.format(d);
        }

    //        class SequenceNumFromDateColumn implements Callable
    //        {
    //            @Override
    //            public Object call() throws Exception
    //            {
    //                Date date = getOutputDate(indexVisitDateOutput);
    //                if (null != date)
    //                    return StudyManager.sequenceNumFromDate(date);
    //                else
    //                    return VisitImpl.DEMOGRAPHICS_VISIT;
    //            }
    //        }

        // MUST match what is produced by DatasetDefinition.generateLSIDSQL
        class LSIDColumn implements Callable
        {
            Map<String, String> map = new HashMap<>();

            String getURNPrefix()
            {
                Container c = null;
                String entityId = null;
                if (_datasetDefinition.isShared() && _datasetDefinition.getDataSharingEnum() == DatasetDefinition.DataSharing.PTID)
                {
                    c = _datasetDefinition.getDefinitionContainer();
                    entityId = c.getId();
                }
                else if (null == indexContainerOutput)
                {
                    c = _datasetDefinition.getContainer();
                    entityId = c.getId();
                }
                else
                {
                    entityId = getOutputString(indexContainerOutput);
                }
                String urn = map.get(entityId);
                if (null != urn)
                    return urn;
                if (null == c)
                    c = ContainerManager.getForId(entityId);
                String id = null == c ? entityId : String.valueOf(c.getRowId());
                urn = "urn:lsid:" + AppProps.getInstance().getDefaultLsidAuthority() + ":Study.Data-" + id + ":" + _datasetDefinition.getDatasetId() + ".";
                map.put(entityId, urn);
                return urn;
            }

            @Override
            public Object call() throws Exception
            {
                StringBuilder sb = new StringBuilder(getURNPrefix());
                assert null != indexPtidOutput || hasErrors();

                String ptid = null == indexPtidOutput ? "" : getOutputString(indexPtidOutput);
                sb.append(ptid);

                if (!_datasetDefinition.isDemographicData())
                {
                    String seqnum = getFormattedSequenceNum();
                    sb.append(".").append(seqnum);

                    if (!_datasetDefinition.getStudy().getTimepointType().isVisitBased() && _datasetDefinition.getUseTimeKeyField())
                    {
                        Date date = getOutputDate(indexVisitDateOutput);
                        sb.append(".").append(String.format("%tH%tM%tS", date, date, date));
                    }
                    else if (null != indexKeyPropertyOutput)
                    {
                        Object key = DatasetColumnsIterator.this.get(indexKeyPropertyOutput);
                        if (null != key)
                            sb.append(".").append(key);
                    }
                }
                return sb.toString();
            }
        }

        class ParticipantSequenceNumColumn implements Callable
        {
            @Override
            public Object call() throws Exception
            {
                assert null != indexPtidOutput || hasErrors();
                String ptid = null == indexPtidOutput ? "" : getOutputString(indexPtidOutput);
                String seqnum = getFormattedSequenceNum();
                return ptid + "|" + seqnum;
            }
        }

        class ParticipantSequenceNumKeyColumn implements Callable
        {
            @Override
            public Object call() throws Exception
            {
                assert (null != indexPtidOutput && null != indexKeyPropertyOutput) || hasErrors();
                String ptid = null == indexPtidOutput ? "" : getOutputString(indexPtidOutput);
                String seqnum = getFormattedSequenceNum();
                Object key = null == indexKeyPropertyOutput ? "" : String.valueOf(DatasetColumnsIterator.this.get(indexKeyPropertyOutput));
                return ptid + "|" + seqnum + "|" + key;
            }
        }

        class QCStateColumn implements Callable
        {
            boolean _autoCreate = true;
            int _indexInputQCState = -1;
            QCState _defaultQCState;
            Map<String, QCState> _qcLabels;
            Set<String> notFound = new CaseInsensitiveHashSet();

            QCStateColumn(int index, QCState defaultQCState)
            {
                _indexInputQCState = index;
                _defaultQCState = defaultQCState;

                _qcLabels = new CaseInsensitiveHashMap<>();
                for (QCState state : StudyManager.getInstance().getQCStates(_datasetDefinition.getContainer()))
                    _qcLabels.put(state.getLabel(), state);
            }

            @Override
            public Object call() throws Exception
            {
                Object currentStateObj = _indexInputQCState < 1 ? null : getInput().get(_indexInputQCState);
                String currentStateLabel = null == currentStateObj ? null : currentStateObj.toString();

                if (currentStateLabel != null)
                {
                    QCState state = _qcLabels.get(currentStateLabel);
                    if (null == state)
                    {
                        if (!_autoCreate)
                        {
                            if (notFound.add(currentStateLabel))
                                getRowError().addFieldError(DatasetTableImpl.QCSTATE_LABEL_COLNAME, "QC State not found: " + currentStateLabel);
                            return null;
                        }
                        else
                        {

                            QCState newState = new QCState();
                            // default to public data:
                            newState.setPublicData(true);
                            newState.setLabel(currentStateLabel);
                            newState.setContainer(_datasetDefinition.getContainer());
                            newState = StudyManager.getInstance().insertQCState(user, newState);
                            _qcLabels.put(newState.getLabel(), newState);
                            return newState.getRowId();
                        }
                    }
                    return state.getRowId();
                }
                else if (_defaultQCState != null)
                {
                    return _defaultQCState.getRowId();
                }
                return null;
            }
        }
    }
}
