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
package org.labkey.study.query;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.security.StudySecurityEscalator;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.visitmanager.PurgeParticipantsJob.ParticipantPurger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.labkey.api.gwt.client.AuditBehaviorType.DETAILED;

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
public class DatasetUpdateService extends AbstractQueryUpdateService
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
    private Set<Role> _contextualRoles = Set.of();

    /** Mapping for MV column names */
    private Map<String, String> _columnMapping = Collections.emptyMap();

    public DatasetUpdateService(DatasetTableImpl table)
    {
        super(table);
        _dataset = table.getDatasetDefinition();
        Domain domain = _dataset.getDomain();
        if (null != domain)
            _columnMapping = createMVMapping(domain);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, Class<? extends Permission> acl)
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
        List.of("Container","Datasets","DatasetId","Dataset","Folder","ParticipantSequenceNum").forEach(nameset::remove);
        List<ColumnInfo> columns = getQueryTable().getColumns(nameset.toArray(new String[0]));

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
                        map.put(columns.get(i).getName(), rs.getObject(i + 1));
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
        int count = _importRowsUsingDIB(user, container, rows, null, getDataIteratorContext(errors, InsertOption.MERGE, configParameters), extraScriptContext);
        if (count > 0)
        {
            StudyManager.datasetModified(_dataset, true);
            resyncStudy(user, container, null, null, true);
        }
        return count;
    }

    @Override
    public int loadRows(User user, Container container, DataIteratorBuilder rows, DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext)
    {
        int count = _importRowsUsingDIB(user, container, rows, null, context, extraScriptContext);
        if (count > 0 && Boolean.TRUE != context.getConfigParameterBoolean(Config.SkipResyncStudy))
        {
            StudyManager.datasetModified(_dataset, true);
            resyncStudy(user, container, null, null, true);
        }
        return count;
    }

    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, Map<Enum,Object> configParameters, Map<String, Object> extraScriptContext)
    {
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

        DataIteratorContext context = getDataIteratorContext(errors, InsertOption.INSERT, configParameters);
        if (!isBulkLoad())
            context.putConfigParameter(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior, DETAILED);

        List<Map<String, Object>> result = super._insertRowsUsingDIB(user, container, rows, context, extraScriptContext);

        if (null != result && result.size() > 0)
        {
            for (Map<String, Object> row : result)
            {
                try
                {
                    String participantID = getParticipant(row, user, container);
                    _potentiallyNewParticipants.add(participantID);
                }
                catch (ValidationException e)
                {
                    throw new QueryUpdateServiceException(e);
                }
            }

            _participantVisitResyncRequired = true; // 13717 : Study failing to resync() on dataset insert
            StudyManager.datasetModified(_dataset, true);
            resyncStudy(user, container);
        }
        return result;
    }

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
                            result.addCoaleseColumn(col.getName(), c, new SimpleTranslator.GuidColumn());
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

        return _dataset.getInsertDataIterator(user, data, context);
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


    @NotNull String getParticipant(Map<String, Object> row, User user, Container container) throws ValidationException, QueryUpdateServiceException
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
            throw new ValidationException("All dataset rows must include a value for " + columnName);
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

            if (_container != null ? !_container.equals(that._container) : that._container != null) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            return _container != null ? _container.hashCode() : 0;
        }
    }


    @Override
    public List<Map<String, Object>> updateRows(User user, final Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
            List<Map<String, Object>> result = super.updateRows(user, container, rows, oldKeys, configParameters, extraScriptContext);
            if (null != extraScriptContext && Boolean.TRUE.equals(extraScriptContext.get("synchronousParticipantPurge")))
            {
                PurgeParticipantCommitTask addObj = new PurgeParticipantCommitTask(container, _potentiallyDeletedParticipants);
                PurgeParticipantCommitTask setObj = getQueryTable().getSchema().getScope().addCommitTask(addObj, DbScope.CommitTaskOption.POSTCOMMIT);
                setObj._potentiallyDeletedParticipants.addAll(addObj._potentiallyDeletedParticipants);
            }

            resyncStudy(user, container);
            return result;
        }

    private void resyncStudy(User user, Container container)
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
      * If null, all of the changedDatasets and specimens will be checked to see if they contain new participants
      * @param potentiallyDeletedParticipants optionally, the specific participants that may have been removed from the
      * study. If null, all participants will be checked to see if they are still in the study.
      * @param participantVisitResyncRequired If true, will force an update of the ParticipantVisit mapping for this study
    */
    private void resyncStudy(User user, Container container, @Nullable Set<String> potentiallyAddedParticipants, @Nullable Set<String> potentiallyDeletedParticipants, boolean participantVisitResyncRequired)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user, Collections.singletonList(_dataset), potentiallyAddedParticipants, potentiallyDeletedParticipants, participantVisitResyncRequired, null);
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException
    {
        // Update will delete old and insert, so covering aliases, like insert, is needed
        aliasColumns(_columnMapping, row);

        String lsid = keyFromMap(oldRow);
        // Make sure we've found the original participant before doing the update
        String oldParticipant = getParticipant(oldRow, user, container);
        String newLsid = _dataset.updateDatasetRow(user, lsid, row);
        //update the lsid and return
        row.put("lsid", newLsid);

        row = getRow(user, container, row);

        String newParticipant = getParticipant(row, user, container);
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
            Object newTimepoint = row.get(columnName);
            if (!Objects.equals(oldTimepoint, newTimepoint))
            {
                _participantVisitResyncRequired = true;
            }
        }

        return row;
    }

    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super.deleteRows(user, container, keys, configParameters, extraScriptContext);
        resyncStudy(user, container);
        return result;
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
            throws InvalidKeyException, QueryUpdateServiceException, ValidationException
    {
        // Make sure we've found the original participant before doing the delete
        String participant = getParticipant(oldRow, user, container);
        _dataset.deleteDatasetRows(user, Collections.singleton(keyFromMap(oldRow)));
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
}
