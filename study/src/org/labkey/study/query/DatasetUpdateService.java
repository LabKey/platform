/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.labkey.api.study.StudyService;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.sql.SQLException;

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
public class DatasetUpdateService implements QueryUpdateService
{
    private int _datasetId = -1;

    public DatasetUpdateService(int datasetId)
    {
        _datasetId = datasetId;
    }

    public int getDatasetId()
    {
        return _datasetId;
    }

    public Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        String lsid = keyFromMap(keys);
        return StudyService.get().getDatasetRow(user, container, getDatasetId(), lsid);
    }

    public Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<String> errors = new ArrayList<String>();
        String newLsid = StudyService.get().insertDatasetRow(user, container, getDatasetId(), row, errors);
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

    public Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldKeys) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<String> errors = new ArrayList<String>();
        String lsid = null != oldKeys ? keyFromMap(oldKeys) : keyFromMap(row);
        String newLsid = StudyService.get().updateDatasetRow(user, container, getDatasetId(), lsid, row, errors);
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

    public Map<String, Object> deleteRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        StudyService.get().deleteDatasetRow(user, container, getDatasetId(), keyFromMap(keys));
        return keys;
    }

    public String keyFromMap(Map<String, Object> map) throws InvalidKeyException
    {
        Object key = map.get("lsid");
        if(null == key)
            throw new InvalidKeyException("No value provided for 'lsid' key column!", map);
        return key.toString();
    }
}