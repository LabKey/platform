/*
 * Copyright (c) 2008-2011 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.sql.SQLException;
import java.util.*;

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
    private final DataSetDefinition _dataset;
    private Set<String> _potentiallyNewParticipants = new HashSet<String>();
    private Set<String> _potentiallyDeletedParticipants = new HashSet<String>();

    public DatasetUpdateService(DataSetTable table)
    {
        super(table);
        _dataset = table.getDatasetDefinition();
    }

    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        String lsid = keyFromMap(keys);
        return StudyService.get().getDatasetRow(user, container, _dataset.getDataSetId(), lsid);
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, Map<String, Object> extraScriptContext)
            throws DuplicateKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super.insertRows(user, container, rows, extraScriptContext);
        resyncStudy(user, container);
        return result;
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
            throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<String> errors = new ArrayList<String>();
        String newLsid = StudyService.get().insertDatasetRow(user, container, _dataset.getDataSetId(), row, errors);
        _potentiallyNewParticipants.add(getParticipant(row));
        if(errors.size() > 0)
        {
            ValidationException e = new ValidationException();
            for(String err : errors)
                e.addError(new SimpleValidationError(err));
            throw e;
        }

        //update the lsid and return
        row.put("lsid", newLsid);
        return row;
    }

    private @NotNull String getParticipant(Map<String, Object> row) throws ValidationException
    {
        String columnName = _dataset.getStudy().getSubjectColumnName();
        Object participant = row.get(columnName);
        if (participant == null)
        {
            participant = row.get("ParticipantId");
        }
        if (participant == null)
        {
            throw new ValidationException("All dataset rows must include a value for " + columnName);
        }
        return participant.toString();
    }

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super.updateRows(user, container, rows, oldKeys, extraScriptContext);
        resyncStudy(user, container);
        return result;
    }

    private void resyncStudy(User user, Container container)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);

        StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user, Collections.singletonList(_dataset), _potentiallyNewParticipants, _potentiallyDeletedParticipants);
        _potentiallyNewParticipants.clear();
        _potentiallyDeletedParticipants.clear();
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<String> errors = new ArrayList<String>();
        String lsid = keyFromMap(oldRow);
        String newLsid = StudyService.get().updateDatasetRow(user, container, _dataset.getDataSetId(), lsid, row, errors);
        if(errors.size() > 0)
        {
            ValidationException e = new ValidationException();
            for(String err : errors)
                e.addError(new SimpleValidationError(err));
            throw e;
        }

        String oldParticipant = getParticipant(oldRow);
        String newParticipant = getParticipant(row);
        if (!oldParticipant.equals(newParticipant))
        {
            // Participant has changed - might be a reference to a new participant, or removal of the last reference to
            // the old participant
            _potentiallyNewParticipants.add(newParticipant);
            _potentiallyDeletedParticipants.add(oldParticipant);
        }

        //update the lsid and return
        row.put("lsid", newLsid);
        return row;
    }

    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super.deleteRows(user, container, keys, extraScriptContext);
        resyncStudy(user, container);
        return result;
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException, ValidationException
    {
        StudyService.get().deleteDatasetRow(user, container, _dataset.getDataSetId(), keyFromMap(oldRow));
        _potentiallyDeletedParticipants.add(getParticipant(oldRow));
        return oldRow;
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
        if (!isDemographic && _dataset.getKeyManagementType() == DataSetDefinition.KeyManagementType.None) {
            throw new InvalidKeyException("No lsid, and no KeyManagement");
        }

        String keyPropertyName = isDemographic ? _dataset.getStudy().getSubjectColumnName() : _dataset.getKeyPropertyName();
        Object id = map.get(keyPropertyName);

        if (null == id) {
           id = map.get("Key");
        }

        // if there was no other type of key, this query is invalid
        if (null == id) {
            throw new InvalidKeyException(String.format("key needs one of 'lsid', '%s' or 'Key', none of which were found in %s", keyPropertyName, map));
        }
        // now look up lsid
        // if one is found, return that
        // if 0, it's legal to return null
        // if > 1, there is an integrity problem that should raise alarm
        String[] lsids;
        try
        {
            lsids = Table.executeArray(getQueryTable(), getQueryTable().getColumn("LSID"), new SimpleFilter(keyPropertyName, id), null, String.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        if (lsids.length == 0) {
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
