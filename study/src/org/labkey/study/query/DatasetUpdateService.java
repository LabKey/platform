/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
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
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super.insertRows(user, container, rows);
        resyncStudy(user, container, true);
        return result;
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
            throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<String> errors = new ArrayList<String>();
        String newLsid = StudyService.get().insertDatasetRow(user, container, _dataset.getDataSetId(), row, errors);
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

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super.updateRows(user, container, rows, oldKeys);

        Set<Object> changedLSIDs = new HashSet<Object>();
        // Keep track of whether LSIDs changed based on this update
        for (Map<String, Object> oldRowKeys : oldKeys)
        {
            changedLSIDs.add(oldRowKeys.get("lsid"));
        }
        for (Map<String, Object> updatedRows : result)
        {
            changedLSIDs.remove(updatedRows.get("lsid"));
        }

        resyncStudy(user, container, changedLSIDs.isEmpty());
        return result;
    }

    private void resyncStudy(User user, Container container, boolean lsidChanged)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        boolean recomputeCohorts = (!study.isManualCohortAssignment() &&
                PageFlowUtil.nullSafeEquals(_dataset.getDataSetId(), study.getParticipantCohortDataSetId()));

        // If this results in a change to cohort assignments, the participant ID, or the visit,
        // we need to recompute the participant-visit map:
        if (recomputeCohorts || lsidChanged)
        {
            StudyManager.getInstance().recomputeStudyDataVisitDate(study, Collections.singletonList(_dataset));
            StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user, Collections.singletonList(_dataset));
        }
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<String> errors = new ArrayList<String>();
        String lsid = null != oldRow ? keyFromMap(oldRow) : keyFromMap(row);
        String newLsid = StudyService.get().updateDatasetRow(user, container, _dataset.getDataSetId(), lsid, row, errors);
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

    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super.deleteRows(user, container, keys);
        resyncStudy(user, container, true);
        return result;
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        StudyService.get().deleteDatasetRow(user, container, _dataset.getDataSetId(), keyFromMap(oldRow));
        return oldRow;
    }

    /*
    if it's a dataset of kind demographic
    ask the study (StudyImpl, or Study?) what is the column name for the participant/subject?
    then use that to resolve the lsid in the same manner as a managed key
     */

    public String keyFromMap(Map<String, Object> map) throws InvalidKeyException
    {
        Object lsid = map.get("lsid");
        if (lsid != null) {
            return lsid.toString();
        }
        // if there was no explicit lsid and KeyManagementType == None, there is no non-lsid key that is unique by itself
        if (_dataset.getKeyManagementType() == DataSetDefinition.KeyManagementType.None) {
            throw new InvalidKeyException("No lsid, and no KeyManagement");
        }

        // No lsid, look for the other types of key
        Object id = map.get(_dataset.getKeyPropertyName());
        if (null == id) {
           id = map.get("Key");
        }

        // if there was no other type of key, this query is invalid
        if (id == null) {
            throw new InvalidKeyException(String.format("key needs one of 'lsid', '%s' or 'Key'", _dataset.getKeyPropertyName()));
        }
        // now look up lsid
        // if one is found, return that
        // if 0, it's legal to return null
        // if > 1, there is an integrity problem that should raise alarm
        String[] lsids;
        try
        {
            lsids = Table.executeArray(getQueryTable(), getQueryTable().getColumn("LSID"), new SimpleFilter(_dataset.getKeyPropertyName(), id), null, String.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
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
