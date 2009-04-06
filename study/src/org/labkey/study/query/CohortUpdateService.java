/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.apache.commons.beanutils.converters.IntegerConverter;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.UnexpectedException;
import org.labkey.study.StudySchema;
import org.labkey.study.model.Cohort;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: jgarms
 * Date: Aug 8, 2008
 * Time: 11:24:44 AM
 */
public class CohortUpdateService implements QueryUpdateService
{
    public Map<String, Object> deleteRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        Map<String,Object> oldRow = getRow(user, container, keys);

        int rowId = keyFromMap(keys);
        Cohort cohort = StudyManager.getInstance().getCohortForRowId(container, user, rowId);

        if (cohort == null)
            throw new IllegalArgumentException("No cohort found for rowId: " + rowId);

        StudyManager.getInstance().deleteCohort(cohort);

        return oldRow;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        Study study = StudyManager.getInstance().getStudy(container);
        StudyQuerySchema querySchema = new StudyQuerySchema(study, user, true);
        TableInfo queryTableInfo = querySchema.getTable("Cohort");
        Map<String,Object> result = Table.selectObject(queryTableInfo, keyFromMap(keys), Map.class);
        return result;
    }

    public Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        Study study = StudyManager.getInstance().getStudy(container);
        Cohort cohort = new Cohort();
        cohort.setLabel(row.get("label").toString());
        StudyManager.getInstance().createCohort(study, user, cohort);

        cohort.savePropertyBag(row);

        Map<String,Object> keys = new HashMap<String,Object>();
        keys.put("rowId", cohort.getRowId());

        try
        {
            return getRow(user, container, keys);
        }
        catch (InvalidKeyException ike)
        {
            throw UnexpectedException.wrap(ike);
        }
    }

    public Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldKeys) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        int rowId = oldKeys != null ? keyFromMap(oldKeys) : keyFromMap(row);
        Cohort cohort = StudyManager.getInstance().getCohortForRowId(container, user, rowId);
        if (cohort == null)
            throw new IllegalArgumentException("No cohort found for rowId: " + rowId);

        // Start a transaction, so that we can rollback if our insert fails
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        boolean transactionOwner = !scope.isTransactionActive();
        if (transactionOwner)
            StudyService.get().beginTransaction();
        try
        {

            // label is in the hard table, so handle it separately
            String newLabel = (String)row.get("label");
            if (!cohort.getLabel().equals(newLabel))
            {
                cohort = cohort.createMutable();
                cohort.setLabel(newLabel);
                StudyManager.getInstance().updateCohort(user, cohort);
            }

            cohort.savePropertyBag(row);

            // Successfully updated
            if(transactionOwner)
                StudyService.get().commitTransaction();
        }
        finally
        {
            if(transactionOwner)
                StudyService.get().rollbackTransaction();
        }


        // The rowId will not have changed
        return getRow(user, container, row);
    }

    public int keyFromMap(Map<String,Object> map) throws InvalidKeyException
    {
        if (map == null)
            throw new InvalidKeyException("No values provided");

        Object rowId = map.get("rowId");
        if (rowId == null)
            throw new InvalidKeyException("No value provided for 'rowId' column");
        Integer rowInteger = (Integer)new IntegerConverter(null).convert(Integer.class, rowId);
        if (rowInteger == null)
            throw new InvalidKeyException("Unable to convert rowId of '" + rowId + "' to an int");
        return rowInteger.intValue();
    }
}
