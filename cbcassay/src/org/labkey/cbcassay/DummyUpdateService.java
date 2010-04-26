/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.cbcassay;

import org.labkey.api.data.Container;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: Dec 18, 2009 2:54:53 PM
 */
public class DummyUpdateService implements QueryUpdateService
{

    public DummyUpdateService()
    {
    }

    public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException();
    }

    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException();
    }

    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException();
    }

    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        List<Integer> objectIds = new ArrayList<Integer>(keys.size());
        for (Map<String, Object> key : keys)
        {
            Number objectIdValue = (Number)key.get("ObjectId");
            objectIds.add(objectIdValue.intValue());
        }
        try
        {
            boolean transaction = false;
            try
            {
                if (!ExperimentService.get().isTransactionActive())
                {
                    ExperimentService.get().beginTransaction();
                    transaction = true;
                }
                OntologyManager.deleteOntologyObjects(
                        objectIds.toArray(new Integer[objectIds.size()]),
                        container, true);
                if (transaction)
                {
                    ExperimentService.get().commitTransaction();
                    transaction = false;
                }
            }
            finally
            {
                if (transaction)
                    ExperimentService.get().rollbackTransaction();
            }
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
        return keys;
    }
}
