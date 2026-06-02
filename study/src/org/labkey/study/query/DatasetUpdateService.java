/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
package org.labkey.study.query;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.security.StudySecurityEscalator;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.study.model.DatasetDataIteratorBuilder;
import org.labkey.study.dataset.DatasetAuditProvider;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.DatasetDomainKind;
import org.labkey.study.model.DatasetLsidImportHelper;
import org.labkey.study.model.ParticipantIdImportHelper;
import org.labkey.study.model.ParticipantSeqNumImportHelper;
import org.labkey.study.model.QCStateImportHelper;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.SequenceNumImportHelper;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.visitmanager.PurgeParticipantsJob.ParticipantPurger;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.labkey.api.util.IntegerUtils.asInteger;
import static org.labkey.api.gwt.client.AuditBehaviorType.DETAILED;
import static org.labkey.api.gwt.client.AuditBehaviorType.NONE;

/*
* User: Dave
* Date: Jun 13, 2008
* Time: 4:15:51 PM
*/

/**
 * QueryUpdateService implementation for Study datasets.
 * <p>
 * Since datasets are of an unpredictable shape, this class just implements
 * the QueryUpdateService directly, working with <code>Map&lt;String,Object&gt;</code>
 * collections for the row data.
 */
public class DatasetUpdateService extends DefaultQueryUpdateService
{
    // These are that can be passed into DatasetUpdateService via DataIteratorContext.configParameters.
    // These used to be passed to DatasetDataIterator via
    // DatasetDefinition.importDatasetData()->DatasetDefinition.insertData().
    // Moving these options into DataInteratorContext allows for even more consistency and code sharing
    // also see QueryUpdateService.ConfigParameters.Logger
    public enum Config
    {
        CheckForDuplicates,     // expected: enum CheckForDuplicates
        DefaultQCState,         // expected: class QCState
        SkipResyncStudy,        // expected: Boolean

        // NOTE: There really has to be better way to handle the functionality of StudyImportContext.getTableIdMap()
        // NOTE: Could this be handled by a method on StudySchema or something???
        // see StudyImportContext.getTableIdMapMap()
        StudyImportMaps,        // expected: Map<String,Map<Object,Object>>

        KeyList,                // expected: List<String>
        AllowImportManagedKey   // expected: Boolean
    }

    private static final Logger LOG = LogManager.getLogger(DatasetUpdateService.class);

    private final DatasetDefinition _dataset;
    private final Set<String> _potentiallyNewParticipants = new HashSet<>();
    private final Set<String> _potentiallyDeletedParticipants = new HashSet<>();
    private boolean _participantVisitResyncRequired = false;

    private final boolean _skipAuditLogging = false;

    public DatasetUpdateService(DatasetTableImpl table)
    {
        super(table, table.getDatasetDefinition().getStorageTableInfo(false), createMVMapping(table.getDatasetDefinition().getDomain()));
        _dataset = table.getDatasetDefinition();
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> acl)
    {
        if (StudySecurityEscalator.isEscalated()) {
            return true;
        }
        else {
            return super.hasPermission(user, acl);
        }
    }


    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
            throws InvalidKeyException
    {
        String lsid = keyFromMap(keys);
        SimpleFilter filter = new SimpleFilter()
                .addCondition(new FieldKey(null,"container"), container.getId())
                .addCondition(new FieldKey(null,"lsid"),lsid);

        // NOTE getQueryTable().getColumns() returns a bunch of columns that getDatasetRow() did not such as:
        //      Container, Dataset, DatasetId, Datasets, Folder, Modified, ModifiedBy, MouseVisit, ParticipantSequenceNum, VisitDay, VisitRowId
        // Mostly this is harmless, but there is some noise.
        HashSet<String> nameset = new HashSet<>(getQueryTable().getColumnNameSet());
        List.of("Container","Datasets","DatasetId","Dataset","Folder").forEach(nameset::remove);
        List<ColumnInfo> columns = new ArrayList<>(getQueryTable().getColumns(nameset.toArray(new String[0])));

        // filter out calculated columns which can be expensive to reselect
        columns.removeIf(ColumnInfo::isCalculated);

        // This is a general version of DatasetDefinition.canonicalizeDatasetRow()
        // The caller needs to make sure names are unique.  Not suitable for use w/ lookups etc where there can be name collisions.
        // CONSIDER: might be nice to make this a TableSelector method.
        var map = new CaseInsensitiveHashMap<>();
        try (var str = new TableSelector(getQueryTable(), columns, filter, null).uncachedResultSetStream())
        {
            str.forEach(rs -> {
                try
                {
                    for (int i = 0; i < columns.size(); i++)
                    {
                        Object o = rs.getObject(i + 1);
                        o = ResultSetRowMapFactory.translateResultSetObject(o, false);
                        map.put(columns.get(i).getName(), o);
                    }
                }
                catch (SQLException x)
                {
                    throw new RuntimeSQLException(x);
                }
            });
        }
        return map.isEmpty() ? null : map;
    }


    /* TODO for performance, NOTE need to return rows in order of input list
    @Override
    public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys) throws InvalidKeyException
    {
        if (!hasPermission(user, ReadPermission.class))
            throw new UnauthorizedException("You do not have permission to read data from this table.");
        ArrayList<String> lsids = new ArrayList<>(keys.size());
        for (var m : keys)
            lsids.add(keyFromMap(m));
        var result = (List)(new TableSelector(getQueryTable(),
                    TableSelector.ALL_COLUMNS,
                    new SimpleFilter(new FieldKey(null,"lsid"), lsids, CompareType.IN),
                    null))
                .getArrayList(Map.class);
        return (List<Map<String, Object>>)result;
    }
    */


    @Override
    public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
    {
        recordDataIteratorUsed(configParameters);
        int count = _importRowsUsingDIB(user, container, rows, null, getDataIteratorContext(errors, InsertOption.MERGE, configParameters), extraScriptContext);
        if (count > 0)
        {
            try
            {
                StudyManager.datasetModified(_dataset, true);
                resyncStudy(user, container, null, null, true);
            }
            catch (ValidationException e)
            {
                errors.addRowError(e);
            }
        }
        return count;
    }

    @Override
    public int loadRows(User user, Container container, DataIteratorBuilder rows, DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext)
    {
        int count = _importRowsUsingDIB(user, container, rows, null, context, extraScriptContext);
        if (count > 0 && !Boolean.TRUE.equals(context.getConfigParameterBoolean(Config.SkipResyncStudy)))
        {
            try
            {
                StudyManager.datasetModified(_dataset, true);
                resyncStudy(user, container, null, null, true);
            }
            catch (ValidationException e)
            {
                context.getErrors().addRowError(e);
            }
        }
        return count;
    }

    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, Map<Enum,Object> configParameters, Map<String, Object> extraScriptContext)
    {
        recordDataIteratorUsed(configParameters);
        DataIteratorContext context = getDataIteratorContext(errors, InsertOption.IMPORT, configParameters);

        return loadRows(user, container, rows, context, extraScriptContext);
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
            throws QueryUpdateServiceException
    {
        for (Map<String, Object> row : rows)
        {
            aliasColumns(_columnMapping, row);
        }

        recordDataIteratorUsed(configParameters);

        DataIteratorContext context = getDataIteratorContext(errors, InsertOption.INSERT, configParameters);
        if (_skipAuditLogging)
            context.putConfigParameter(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior, NONE);
        else if (!isBulkLoad())
        {
            // default to DETAILED unless there is a metadata XML override
            context.putConfigParameter(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior,
                    getQueryTable().getXmlAuditBehaviorType() != null ? getQueryTable().getXmlAuditBehaviorType() : DETAILED);
        }

        List<Map<String, Object>> result = super._insertRowsUsingDIB(user, container, rows, context, extraScriptContext);

        if (null != result && result.size() > 0)
        {
            for (Map<String, Object> row : result)
            {
                String participantID = getParticipant(row, user, container);
                _potentiallyNewParticipants.add(participantID);
            }

            _participantVisitResyncRequired = true; // 13717 : Study failing to resync() on dataset insert
            if (configParameters == null || !Boolean.TRUE.equals(configParameters.get(DatasetUpdateService.Config.SkipResyncStudy)))
            {
                try
                {
                    StudyManager.datasetModified(_dataset, true);
                    resyncStudy(user, container);
                }
                catch (ValidationException e)
                {
                    errors.addRowError(e);
                }
            }
        }
        return result;
    }

    @Override
    protected DataIteratorBuilder preTriggerDataIterator(DataIteratorBuilder in, DataIteratorContext context)
    {
        // If we're using a managed GUID as a key, wire it up here so that it's available to trigger scripts
        if (_dataset.getKeyType() == Dataset.KeyType.SUBJECT_VISIT_OTHER &&
                _dataset.getKeyManagementType() == Dataset.KeyManagementType.GUID &&
                _dataset.getKeyPropertyName() != null)
        {
            return new DataIteratorBuilder()
            {
                @Override
                public DataIterator getDataIterator(DataIteratorContext context)
                {
                    DataIterator input = in.getDataIterator(context);
                    if (null == input)
                        return null;           // Can happen if context has errors

                    final SimpleTranslator result = new SimpleTranslator(input, context);

                    boolean foundKeyCol = false;
                    for (int c = 1; c <= input.getColumnCount(); c++)
                    {
                        ColumnInfo col = input.getColumnInfo(c);

                        // Incoming data has a matching field
                        if (col.getName().equalsIgnoreCase(_dataset.getKeyPropertyName()))
                        {
                            // make sure guid is not null (12884)
                            result.addCoalesceColumn(col.getName(), c, new SimpleTranslator.GuidColumn());
                            foundKeyCol = true;
                        }
                        else
                        {
                            // Pass it through as-is
                            result.addColumn(c);
                        }
                    }

                    if (!foundKeyCol)
                    {
                        // Inject a column with a new GUID
                        ColumnInfo key = getQueryTable().getColumn(_dataset.getKeyPropertyName());
                        result.addColumn(new BaseColumnInfo(key), new SimpleTranslator.GuidColumn());
                    }

                    return result;
                }
            };
        }
        return in;
    }
    
    @Override
    public DataIteratorBuilder createImportDIB(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
    {
        if (null == context.getConfigParameter(Config.DefaultQCState))
        {
            context.putConfigParameter(Config.DefaultQCState, StudyManager.getInstance().getDefaultQCState(_dataset.getStudy()));
        }

        if (null == context.getConfigParameter(Config.CheckForDuplicates))
        {
            DatasetDefinition.CheckForDuplicates dupePolicy;
            if (isBulkLoad())
                dupePolicy = DatasetDefinition.CheckForDuplicates.never;
            else if (context.getInsertOption().mergeRows)
                dupePolicy = DatasetDefinition.CheckForDuplicates.sourceOnly;
            else
                dupePolicy = DatasetDefinition.CheckForDuplicates.sourceAndDestination;
            context.putConfigParameter(Config.CheckForDuplicates, dupePolicy);
        }

        // NOTE: This was done to help coalesce some old code paths.  However, this is a little weird, because
        // the DI is over the DatasetSchemaTableInfo not the DatasetTableImpl you'd expect.  This all still works
        // because of property URI matching in StatementDataIterator.
        return _dataset.getInsertDataIterator(user, container, data, context);
    }


    @Override
    protected int _pump(DataIteratorBuilder etl, final ArrayList<Map<String, Object>> rows, DataIteratorContext context)
    {
        try
        {
            boolean hasRowId = _dataset.getKeyManagementType() == Dataset.KeyManagementType.RowId;

            if (null != rows)
            {
                // TODO: consider creating DataIterator metadata to mark "internal" cols (not to be returned via API)
                DataIterator it = etl.getDataIterator(context);
                DataIteratorBuilder cleanMap = new MapDataIterator.MapDataIteratorImpl(it, true, CaseInsensitiveHashSet.of(
                        it.getColumnInfo(0).getName()
                ));
                etl = cleanMap;
            }

            if (!hasRowId)
            {
                return super._pump(etl, rows, context);
            }

            synchronized (_dataset.getManagedKeyLock())
            {
                return super._pump(etl, rows, context);
            }
        }
        catch (RuntimeSQLException e)
        {
            String translated = _dataset.translateSQLException(e);
            if (translated != null)
            {
                context.getErrors().addRowError(new ValidationException(translated));
                return 0;
            }
            throw e;
        }
    }


    @NotNull String getParticipant(Map<String, Object> row, User user, Container container) throws QueryUpdateServiceException
    {
        String columnName = _dataset.getStudy().getSubjectColumnName();
        Object participant = row.get(columnName);
        if (participant == null)
        {
            participant = row.get("ParticipantId");
        }
        if (participant == null)
        {
            try
            {
                // This may be an update or delete where the user specified the LSID as the key, but didn't bother
                // sending the participant, so look it up
                Map<String, Object> originalRow = getRow(user, container, row);
                participant = originalRow == null ? null : originalRow.get(columnName);
                if (participant == null)
                {
                    participant = originalRow.get("ParticipantId");
                }
            }
            catch (InvalidKeyException e)
            {
                throw new QueryUpdateServiceException(e);
            }
        }
        if (participant == null)
        {
            throw new QueryUpdateServiceException("All dataset rows must include a value for " + columnName);
        }
        return participant.toString();
    }

    static class PurgeParticipantCommitTask implements Runnable
    {
        private final Container _container;
        private final Set<String> _potentiallyDeletedParticipants;

        PurgeParticipantCommitTask(Container container, Set<String> potentiallyDeletedParticipants)
        {
            _container = container;
            _potentiallyDeletedParticipants = new HashSet<>(potentiallyDeletedParticipants);
        }

        @Override
        public void run()
        {
            new ParticipantPurger(_container, _potentiallyDeletedParticipants, LOG::info, LOG::error).purgeParticipants();
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PurgeParticipantCommitTask that = (PurgeParticipantCommitTask) o;

            if (!Objects.equals(_container, that._container)) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            return _container != null ? _container.hashCode() : 0;
        }
    }


    @Override
    public List<Map<String, Object>> updateRows(User user, final Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super.updateRows(user, container, rows, oldKeys, errors, configParameters, extraScriptContext);
        if (null != extraScriptContext && Boolean.TRUE.equals(extraScriptContext.get("synchronousParticipantPurge")))
        {
            PurgeParticipantCommitTask addObj = new PurgeParticipantCommitTask(container, _potentiallyDeletedParticipants);
            PurgeParticipantCommitTask setObj = getQueryTable().getSchema().getScope().addCommitTask(addObj, DbScope.CommitTaskOption.POSTCOMMIT);
            setObj._potentiallyDeletedParticipants.addAll(addObj._potentiallyDeletedParticipants);
        }

        try
        {
            StudyManager.datasetModified(_dataset, true);
            resyncStudy(user, container);
        }
        catch (ValidationException e)
        {
            errors.addRowError(e);
        }
        return result;
    }

    private void resyncStudy(User user, Container container) throws ValidationException
    {
        resyncStudy(user, container, _potentiallyNewParticipants, _potentiallyDeletedParticipants, _participantVisitResyncRequired);

        _participantVisitResyncRequired = false;
        _potentiallyNewParticipants.clear();
        _potentiallyDeletedParticipants.clear();
    }

    /**
      * Resyncs the study : updates the participant, visit, and (optionally) participant visit tables. Also updates automatic cohort assignments.
      *
      * @param potentiallyAddedParticipants optionally, the specific participants that may have been added to the study.
      * If null, all the changedDatasets and specimens will be checked to see if they contain new participants
      * @param potentiallyDeletedParticipants optionally, the specific participants that may have been removed from the
      * study. If null, all participants will be checked to see if they are still in the study.
      * @param participantVisitResyncRequired If true, will force an update of the ParticipantVisit mapping for this study
    */
    private void resyncStudy(User user, Container container, @Nullable Set<String> potentiallyAddedParticipants,
                             @Nullable Set<String> potentiallyDeletedParticipants,
                             boolean participantVisitResyncRequired) throws ValidationException
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        Study sharedStudy = StudyManager.getInstance().getSharedStudy(study);

        ValidationException errors = StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user, Collections.singletonList(_dataset),
                potentiallyAddedParticipants, potentiallyDeletedParticipants, participantVisitResyncRequired,
                sharedStudy != null ? sharedStudy.isFailForUndefinedTimepoints() : study.isFailForUndefinedTimepoints(), null);

        if (errors.hasErrors())
            throw errors;
    }

    @Override
    protected void convertTypes(User user, Container c, Map<String, Object> row, TableInfo t, @Nullable Path fileLinkDirPath) throws ValidationException
    {
        // Issue 53320 : ensure a valid file link path
        if (fileLinkDirPath == null)
            fileLinkDirPath = AssayFileWriter.getUploadDirectoryPath(c, "datasetdata").toNioPathForWrite();

        super.convertTypes(user, c, row, t, fileLinkDirPath);
    }

    @Override
    protected Map<String, Object> _update(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws ValidationException
    {
        try (DbScope.Transaction transaction = StudyService.get().getDatasetSchema().getScope().ensureTransaction())
        {
            String lsid = keyFromMap(oldRow);
            checkDuplicateUpdate(lsid);
            // Make sure we've found the original participant before doing the update
            String oldParticipant = getParticipant(oldRow, user, container);
            String newLsid;

            Long rowId = (Long)oldRow.get(DatasetDomainKind.DSROWID);
            Map<String, Object> oldData = _dataset.getDatasetRow(user, lsid);

            if (oldData == null)
            {
                // No old record found, so we can't update
                ValidationException error = new ValidationException();
                error.addError(new SimpleValidationError("Record not found with lsid: " + lsid));
                throw error;
            }

            // values that are always recalculated
            getComputedValues(user, row, oldRow);

            newLsid = (String)row.get(DatasetDomainKind.LSID);
            Table.update(user, getDbTable(), row, rowId);

            if (!isBulkLoad())
            {
                DatasetTableImpl target = (DatasetTableImpl)_dataset.getTableInfo(user);
                new DatasetDefinition.DatasetAuditHandler(_dataset).addAuditEvent(user, container, target, AuditBehaviorType.DETAILED, null, QueryService.AuditAction.UPDATE,
                        List.of(row), List.of(oldData));
            }

            // Successfully updated
            transaction.commit();

            // return updated row
            var returnRow = getRow(user, container, Map.of(DatasetDomainKind.LSID, newLsid));

            String newParticipant = getParticipant(returnRow, user, container);
            if (!oldParticipant.equals(newParticipant))
            {
                // Participant has changed - might be a reference to a new participant, or removal of the last reference to
                // the old participant
                _potentiallyNewParticipants.add(newParticipant);
                _potentiallyDeletedParticipants.add(oldParticipant);

                // Need to resync the ParticipantVisit table too
                _participantVisitResyncRequired = true;
            }
            // Check if the timepoint may have changed, but only if we don't already know we need to resync
            else if (!_participantVisitResyncRequired)
            {
                String columnName = StudyManager.getInstance().getStudy(container).getTimepointType().isVisitBased() ?
                        "SequenceNum" : "Date";
                Object oldTimepoint = oldRow.get(columnName);
                Object newTimepoint = returnRow.get(columnName);
                if (!Objects.equals(oldTimepoint, newTimepoint))
                {
                    _participantVisitResyncRequired = true;
                }
            }

            return returnRow;
        }
        catch (QueryUpdateServiceException | InvalidKeyException e)
        {
            throw new ValidationException(e.getMessage());
        }
    }

    private void getComputedValues(User user, Map<String, Object> row, Map<String, Object> oldRow) throws ValidationException
    {
        String subjectColumnName = _dataset.getStudy().getSubjectColumnName();
        TableInfo table = _dataset.getTableInfo(user);
        ColumnInfo subjectColumn = table.getColumn(subjectColumnName);
        ColumnInfo sequenceNumColumn = table.getColumn(DatasetDomainKind.SEQUENCENUM);
        ColumnInfo dateColumn = table.getColumn(DatasetDomainKind.DATE);
        String managedKey = null;
        if (_dataset.getKeyType() == Dataset.KeyType.SUBJECT_VISIT_OTHER)
            managedKey = _dataset.getKeyPropertyName();
        ColumnInfo managedKeyColumn = managedKey != null ? table.getColumn(managedKey) : null;

        Object inputSubjectId = DatasetDataIteratorBuilder.findColumnInMap(row, subjectColumn);
        if (inputSubjectId == null)
            inputSubjectId = DatasetDataIteratorBuilder.findColumnInMap(oldRow, subjectColumn);
        Object inputSeqNum = DatasetDataIteratorBuilder.findColumnInMap(row, sequenceNumColumn);
        if (inputSeqNum == null)
            inputSeqNum = DatasetDataIteratorBuilder.findColumnInMap(oldRow, sequenceNumColumn);
        Date inputDate = (Date)DatasetDataIteratorBuilder.findColumnInMap(row, dateColumn);
        if (inputDate == null)
            inputDate = (Date)DatasetDataIteratorBuilder.findColumnInMap(oldRow, dateColumn);
        Object inputManagedKey = DatasetDataIteratorBuilder.findColumnInMap(row, managedKeyColumn);
        if (inputManagedKey == null)
            inputManagedKey = DatasetDataIteratorBuilder.findColumnInMap(oldRow, managedKeyColumn);
        Integer inputQCState = asInteger(DatasetDataIteratorBuilder.findColumnInMap(row, table.getColumn(DatasetTableImpl.QCSTATE_ID_COLNAME)));

        SequenceNumImportHelper snih = new SequenceNumImportHelper(_dataset.getStudy(), _dataset);
        Double sequenceNum = snih.translateSequenceNum(inputSeqNum, inputDate);

        ParticipantIdImportHelper helper = new ParticipantIdImportHelper(_dataset.getStudy(), user, _dataset);
        String subjectId = helper.translateParticipantId(inputSubjectId);

        // generate participant sequence number
        String participantSeqNum = ParticipantSeqNumImportHelper.translateParticipantSeqNum(subjectId, sequenceNum);

        // re-generate a new lsid
        DatasetLsidImportHelper dlih = new DatasetLsidImportHelper(_dataset);
        String lsid = dlih.translateLsid(subjectId, sequenceNum, inputDate, inputManagedKey, null);

        // handle default QC states
        if (inputQCState == null)
        {
            String inputQCText = (String)DatasetDataIteratorBuilder.findColumnInMap(row, table.getColumn(DatasetTableImpl.QCSTATE_LABEL_COLNAME));
            QCStateImportHelper qcih = new QCStateImportHelper(user, _dataset, true, StudyManager.getInstance().getDefaultQCState(_dataset.getStudy()));
            Long qcState = qcih.translateQCState(inputQCText);
            if (qcState != null)
                row.put(DatasetTableImpl.QCSTATE_ID_COLNAME, qcState);
        }
        row.put(DatasetDomainKind.LSID, lsid);
        row.put(DatasetDomainKind.PARTICIPANTSEQUENCENUM, participantSeqNum);
        row.put(DatasetDomainKind.PARTICIPANTID, subjectId);
    }

    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super.deleteRows(user, container, keys, configParameters, extraScriptContext);
        try
        {
            resyncStudy(user, container);
        }
        catch (ValidationException e)
        {
            throw new BatchValidationException(e);
        }
        return result;
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
            throws InvalidKeyException, QueryUpdateServiceException
    {
        // Make sure we've found the original participant before doing the delete
        String participant = getParticipant(oldRow, user, container);
        _dataset.deleteDatasetRows(user, Collections.singleton(keyFromMap(oldRow)), isBulkLoad());
        _potentiallyDeletedParticipants.add(participant);
        _participantVisitResyncRequired = true;
        return oldRow;
    }

    @Override
    protected int truncateRows(User user, Container container)
    {
       return _dataset.deleteRows((Date) null);
    }

    @Override
    public int truncateRows(User user, Container container, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws BatchValidationException, QueryUpdateServiceException, SQLException
    {
        Map<Enum, Object> updatedParams = configParameters;
        if (updatedParams == null)
            updatedParams = new HashMap<>();
        updatedParams.put(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior, AuditBehaviorType.SUMMARY);
        return super.truncateRows(user, container, updatedParams, extraScriptContext);
    }

    public String keyFromMap(Map<String, Object> map) throws InvalidKeyException
    {
        Object lsid = map.get("lsid");
        if (lsid != null)
            return lsid.toString();
        lsid = map.get("LSID");
        if (lsid != null)
            return lsid.toString();
        
        boolean isDemographic = _dataset.isDemographicData();

        // if there was no explicit lsid and KeyManagementType == None, there is no non-lsid key that is unique by itself.
        // Unless of course it is a demographic table.
        if (!isDemographic && _dataset.getKeyManagementType() == DatasetDefinition.KeyManagementType.None)
        {
            throw new InvalidKeyException("No lsid, and no KeyManagement");
        }

        String keyPropertyName = isDemographic ? _dataset.getStudy().getSubjectColumnName() : _dataset.getKeyPropertyName();
        Object id = map.get(keyPropertyName);

        if (null == id)
        {
           id = map.get("Key");
        }

        // if there was no other type of key, this query is invalid
        if (null == id)
        {
            throw new InvalidKeyException(String.format("key needs one of 'lsid', '%s' or 'Key', none of which were found in %s", keyPropertyName, map));
        }

        // now look up lsid
        // if one is found, return that
        // if 0, it's legal to return null
        // if > 1, there is an integrity problem that should raise alarm
        String[] lsids = new TableSelector(getQueryTable().getColumn("LSID"), new SimpleFilter(keyPropertyName, id), null).getArray(String.class);

        if (lsids.length == 0)
        {
            return null;
        }
        else if (lsids.length > 1)
        {
            throw new IllegalStateException("More than one row matched for key '" + id + "' in column " +
                    _dataset.getKeyPropertyName() + " in dataset " +
                    _dataset.getName() + " in folder " +
                    _dataset.getContainer().getPath());
        }
        else return lsids[0];
    }


    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        private static final String SUBJECT_COLUMN_NAME = "SubjectID";
        private static final String DATASET_NAME = "DS1";
        TestContext _context = null;
        User _user = null;
        Container _container = null;
        StudyImpl _junitStudy = null;
        StudyManager _manager = StudyManager.getInstance();
        String longName = "this is a very long name (with punctuation) that raises many questions \"?\" about your database design choices";

        private void createDataset() throws Exception
        {
            if (DefaultSchema.get(_user, _container).getSchema(StudyQuerySchema.SCHEMA_NAME).getTable(DATASET_NAME) != null)
            {
                return;
            }

            var dsd = new DatasetDefinition(_junitStudy, 1001, DATASET_NAME, DATASET_NAME, null, null, null);
            _manager.createDatasetDefinition(_user, dsd);
            dsd = _manager.getDatasetDefinition(_junitStudy, 1001);
            dsd.refreshDomain();
            {
                var domain = dsd.getDomain(true);
                DomainProperty p;

                p = domain.addProperty();
                p.setName("Field1");
                p.setPropertyURI(domain.getTypeURI() + "." + Lsid.encodePart(p.getName()));
                p.setRangeURI(PropertyType.getFromJdbcType(JdbcType.VARCHAR).getTypeUri());

                p = domain.addProperty();
                p.setName("SELECT");
                p.setPropertyURI(domain.getTypeURI() + "." + Lsid.encodePart(p.getName()));
                p.setRangeURI(PropertyType.getFromJdbcType(JdbcType.VARCHAR).getTypeUri());

                p = domain.addProperty();
                p.setName(longName);
                p.setPropertyURI(domain.getTypeURI() + "." + Lsid.encodePart(p.getName()));
                p.setRangeURI(PropertyType.getFromJdbcType(JdbcType.VARCHAR).getTypeUri());

                p = domain.addProperty();
                p.setName("Value1");
                p.setPropertyURI(domain.getTypeURI() + "." + Lsid.encodePart(p.getName()));
                p.setRangeURI(PropertyType.getFromJdbcType(JdbcType.DOUBLE).getTypeUri());
                p.setMvEnabled(true);

                p = domain.addProperty();
                p.setName("Value2");
                p.setPropertyURI(domain.getTypeURI() + "." + Lsid.encodePart(p.getName()));
                p.setRangeURI(PropertyType.getFromJdbcType(JdbcType.DOUBLE).getTypeUri());
                p.setMvEnabled(true);

                p = domain.addProperty();
                p.setName("Value3");
                p.setPropertyURI(domain.getTypeURI() + "." + Lsid.encodePart(p.getName()));
                p.setRangeURI(PropertyType.getFromJdbcType(JdbcType.DOUBLE).getTypeUri());
                p.setMvEnabled(true);

                domain.save(_user);
            }
        }

        private long getDatasetAuditRowCount()
        {
            return new TableSelector(QueryService.get().getUserSchema(_user, _container, AbstractAuditTypeProvider.QUERY_SCHEMA_NAME).getTable(DatasetAuditProvider.DATASET_AUDIT_EVENT)).getRowCount();
        }

        private String getLatestAuditMessage()
        {
            return new TableSelector(QueryService.get().getUserSchema(_user, _container, AbstractAuditTypeProvider.QUERY_SCHEMA_NAME).getTable(DatasetAuditProvider.DATASET_AUDIT_EVENT), PageFlowUtil.set("Comment"), null, new Sort("-rowId")).setMaxRows(1).getObject(String.class);
        }

        @Test
        public void testAuditing() throws Exception
        {
            createDataset();
            TableInfo t = DefaultSchema.get(_user, _container).getSchema(StudyQuerySchema.SCHEMA_NAME).getTable(DATASET_NAME);
            t.getUpdateService().truncateRows(_user, _container, null, null);

            final QueryUpdateService qus = t.getUpdateService();
            BatchValidationException errors = new BatchValidationException();

            long actualAuditRows = getDatasetAuditRowCount();
            long expectedAuditRows;

            List<Map<String, Object>> insertedRows = qus.insertRows(_user, _container,
                    List.of(Map.of(
                            SUBJECT_COLUMN_NAME, "S1",
                            "SequenceNum", "1.2345",
                            longName, "NA"),
                            Map.of(
                            SUBJECT_COLUMN_NAME, "S2",
                            "SequenceNum", "1.2345",
                            longName, "WithoutBulkLoad")),
                    errors, null, null);

            if (errors.hasErrors())
            {
                fail(errors.getMessage());
            }

            expectedAuditRows = actualAuditRows + 2;
            actualAuditRows = getDatasetAuditRowCount();
            Assert.assertEquals("Incorrect number of audit records", expectedAuditRows, actualAuditRows);
            Assert.assertEquals("Incorrect comment", "A new dataset record was inserted", getLatestAuditMessage());

            qus.insertRows(_user, _container,
                    List.of(Map.of(
                                SUBJECT_COLUMN_NAME, "S3",
                                "SequenceNum", "1.2345",
                                longName, "WithoutBulkLoad")),
                    errors, null, null);

            if (errors.hasErrors())
            {
                fail(errors.getMessage());
            }

            expectedAuditRows = actualAuditRows + 1;
            actualAuditRows = getDatasetAuditRowCount();
            Assert.assertEquals("Incorrect number of audit records", expectedAuditRows, actualAuditRows);

            // Now update:
            insertedRows.get(0).put(longName, "NewValue");
            insertedRows.get(1).put(longName, "NewValue");
            List<Map<String, Object>> oldKeys = Arrays.asList(
                    Map.of("lsid", insertedRows.get(0).get("lsid")),
                    Map.of("lsid", insertedRows.get(1).get("lsid"))
            );
            qus.updateRows(_user, _container, insertedRows, oldKeys, errors, null, null);
            if (errors.hasErrors())
            {
                fail(errors.getMessage());
            }

            expectedAuditRows = actualAuditRows + 2;
            actualAuditRows = getDatasetAuditRowCount();
            Assert.assertEquals("Incorrect number of audit records", expectedAuditRows, actualAuditRows);
            Assert.assertEquals("Incorrect comment", "A dataset record was modified", getLatestAuditMessage());

            qus.deleteRows(_user, _container, oldKeys, null, null);
            expectedAuditRows = actualAuditRows + 2;
            actualAuditRows = getDatasetAuditRowCount();
            Assert.assertEquals("Incorrect number of audit records", expectedAuditRows, actualAuditRows);
            Assert.assertEquals("Incorrect comment", "A dataset record was deleted", getLatestAuditMessage());

            // Repeat using bulkLoad=true:
            qus.setBulkLoad(true);

            insertedRows = qus.insertRows(_user, _container,
                    List.of(Map.of(
                                    SUBJECT_COLUMN_NAME, "S4",
                                    "SequenceNum", "1.2345",
                                    longName, "WithBulkLoad"),
                            Map.of(
                                    SUBJECT_COLUMN_NAME, "S5",
                                    "SequenceNum", "1.2345",
                                    longName, "WithBulkLoad")),
                    errors, null, null);

            if (errors.hasErrors())
            {
                fail(errors.getMessage());
            }

            expectedAuditRows = actualAuditRows;
            actualAuditRows = getDatasetAuditRowCount();
            Assert.assertEquals("Incorrect number of audit records", expectedAuditRows, actualAuditRows);

            // Now update:
            insertedRows.get(0).put(longName, "NewValue");
            insertedRows.get(1).put(longName, "NewValue");
            oldKeys = Arrays.asList(
                    Map.of("lsid", insertedRows.get(0).get("lsid")),
                    Map.of("lsid", insertedRows.get(1).get("lsid"))
            );
            qus.updateRows(_user, _container, insertedRows, oldKeys, errors, null, null);
            if (errors.hasErrors())
            {
                fail(errors.getMessage());
            }

            expectedAuditRows = actualAuditRows;
            actualAuditRows = getDatasetAuditRowCount();
            Assert.assertEquals("Incorrect number of audit records", expectedAuditRows, actualAuditRows);

            qus.deleteRows(_user, _container, oldKeys, null, null);
            expectedAuditRows = actualAuditRows;
            actualAuditRows = getDatasetAuditRowCount();
            Assert.assertEquals("Incorrect number of audit records", expectedAuditRows, actualAuditRows);
        }

        @Test
        public void updateRowTest() throws Exception
        {
            createDataset();

            TableInfo t = DefaultSchema.get(_user, _container).getSchema(StudyQuerySchema.SCHEMA_NAME).getTable(DATASET_NAME);
            assertNotNull(t);
            assertTrue("Field1".equalsIgnoreCase(t.getColumn("Field1").getAlias().getId()));
            assertFalse("SELECT".equalsIgnoreCase(t.getColumn("SELECT").getAlias().getId()));
            assertFalse(longName.equalsIgnoreCase(t.getColumn(longName).getAlias().getId()));
            var up = t.getUpdateService();
            assertNotNull(up);
            var errors = new BatchValidationException();

            var result = up.insertRows(_user, _container,
                    List.of(Map.of(
                            SUBJECT_COLUMN_NAME, " S1 \t",
                            "SequenceNum", "1.2345",
                            "Field1", "f",
                            "SELECT", "s",
                            longName, "l",
                            "value1", "1.0",
                            "value2", "NA",
                            "VALUE3", "NA")),
                    errors, null, null);
            if (errors.hasErrors())
                fail(errors.getMessage());
            assertFalse(errors.hasErrors());
            assertNotNull(result);
            assertEquals(1, result.size());
            var map = result.getFirst();
            assertEquals("S1", map.get(SUBJECT_COLUMN_NAME));
            assertEquals("f", map.get("Field1"));
            assertEquals("s", map.get("SELECT"));
            assertEquals("l", map.get(longName));
            assertEquals( 1.0d, map.get("value1"));            // 1.0
            var v2 = map.get("value2");                                 // NA
            assertTrue(v2 instanceof MvFieldWrapper);
            assertEquals("NA", ((MvFieldWrapper)v2).getMvIndicator());
            var v3 = map.get("value3");                                 // NA
            assertTrue(v3 instanceof MvFieldWrapper);
            assertEquals("NA", ((MvFieldWrapper)v3).getMvIndicator());
            assertNotNull(map.get("lsid"));
            assertTrue(((String)map.get("lsid")).endsWith(":1001.S1.1.2345"));
            String lsid = (String)map.get("lsid");

            // update subjectid column
            result = up.updateRows(_user, _container,
                    List.of(Map.of(SUBJECT_COLUMN_NAME, "\tS2 ")),
                    List.of(Map.of("lsid", lsid)),
                    errors, null, null);
            if (errors.hasErrors())
                fail(errors.getMessage());
            assertNotNull(result);
            assertEquals(1, result.size());
            map = result.getFirst();
            assertEquals("S2", map.get(SUBJECT_COLUMN_NAME));
            // All other columns are preserved
            assertEquals("f", map.get("Field1"));
            assertEquals("s", map.get("SELECT"));
            assertEquals("l", map.get(longName));
            assertEquals( 1.0d, map.get("value1"));                // 1.0
            // DIFFERENCE - updateRows() does not return MvFieldWrapper
            assertNull(map.get("value2"));                                  // NA
            assertEquals("NA", map.get("value2mvindicator"));
            assertNull(map.get("VALUE3"));                                  // NA
            assertEquals("NA", map.get("value3MvIndicator"));
            // LSID is updated
            assertNotNull(map.get("lsid"));
            assertTrue(((String)map.get("lsid")).endsWith(":1001.S2.1.2345"));
            lsid = (String)map.get("lsid");

            // update other columns
            result = up.updateRows(_user, _container,
                    List.of(Map.of(
                            "Field1", "fUpdated",
                            "SELECT", "sUpdated",
                            longName, "lUpdated",
                            "value1", "NA",                         // 1.0 -> NA
                            "value2", "2.0",                            // NA -> 2.0
                            "value3", "QA")                             // NA -> QA
                    ),
                    List.of(Map.of("lsid", lsid)),
                    errors, null, null);
            if (errors.hasErrors())
                fail(errors.getMessage());
            assertNotNull(result);
            assertEquals(1, result.size());
            map = result.getFirst();
            assertEquals("S2", map.get(SUBJECT_COLUMN_NAME));
            assertEquals("fUpdated", map.get("Field1"));
            assertEquals("sUpdated", map.get("SELECT"));
            assertEquals("lUpdated", map.get(longName));
            assertNull(map.get("value1"));        // NA
            assertEquals("NA", map.get("Value1MVIndicator"));
            assertEquals(2.0d, map.get("value2"));                 // 2.0
            assertNull(map.get("Value2MVIndicator"));
            assertNull(map.get("value3"));                                  // QA
            assertEquals("QA", map.get("value3mVindicator"));
            assertNotNull(map.get("lsid"));
            // unchanged
            assertTrue(((String)map.get("lsid")).endsWith(":1001.S2.1.2345"));
            map.get("lsid");
        }

        @Before
        public void createStudy()
        {
            _context = TestContext.get();
            Container junit = JunitUtil.getTestContainer();
            String name = GUID.makeHash();
            Container c = ContainerManager.createContainer(junit, name, _context.getUser());
            MvUtil.assignMvIndicators(c, new String[] {"NA","QA"}, new String[] {"NA","QA"});
            StudyImpl s = new StudyImpl(c, "Junit Study");
            s.setTimepointType(TimepointType.VISIT);
            s.setStartDate(new Date(DateUtil.parseDateTime(c, "2014-01-01")));
            s.setSubjectColumnName(SUBJECT_COLUMN_NAME);
            s.setSubjectNounPlural("Subjects");
            s.setSubjectNounSingular("Subject");
            s.setSecurityType(SecurityType.BASIC_WRITE);
            _junitStudy = StudyManager.getInstance().createStudy(_context.getUser(), s);
            _user = _context.getUser();
            _container = _junitStudy.getContainer();
        }

        @After
        public void tearDown()
        {
            if (null != _junitStudy)
            {
                assertTrue(ContainerManager.delete(_junitStudy.getContainer(), _context.getUser()));
            }
        }
    }
}
