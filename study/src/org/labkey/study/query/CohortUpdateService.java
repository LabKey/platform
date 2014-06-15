/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.util.UnexpectedException;
import org.labkey.study.StudySchema;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: jgarms
 * Date: Aug 8, 2008
 * Time: 11:24:44 AM
 */
public class CohortUpdateService extends AbstractQueryUpdateService
{
    public CohortUpdateService(TableInfo queryTable)
    {
        super(queryTable);
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, SQLException
    {
        int rowId = keyFromMap(oldRow);
        CohortImpl cohort = StudyManager.getInstance().getCohortForRowId(container, user, rowId);

        if (cohort == null)
            throw new IllegalArgumentException("No cohort found for rowId: " + rowId);
        else if (cohort.isInUse())
            throw new ValidationException("Unable to delete in-use cohort: " + cohort.getLabel());

        StudyManager.getInstance().deleteCohort(cohort);

        return oldRow;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        StudyQuerySchema querySchema = StudyQuerySchema.createSchema(study, user, true);
        TableInfo queryTableInfo = querySchema.getTable("Cohort");
        Map<String, Object> result = new TableSelector(queryTableInfo).getObject(keyFromMap(keys), Map.class);
        return result;
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
            throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        Study study = StudyManager.getInstance().getStudy(container);
        CohortImpl cohort = new CohortImpl();
        cohort.setLabel(row.get("label").toString());
        if (row.get("enrolled") != null)
            cohort.setEnrolled((Boolean)row.get("enrolled"));
        if (row.get("subjectCount") != null)
            cohort.setSubjectCount((Integer)row.get("subjectCount"));
        if (row.get("description") != null)
            cohort.setDescription((String)row.get("description"));

        // Check if there's a conflict based on the label
        CohortImpl existingCohort = StudyManager.getInstance().getCohortByLabel(container, user, cohort.getLabel());
        if (existingCohort != null)
            throw new ValidationException("A cohort with the label '" + cohort.getLabel() + "' already exists");

        StudyManager.getInstance().createCohort(study, user, cohort);

        cohort.savePropertyBag(row);

        Map<String,Object> keys = new HashMap<>();
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

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        int rowId = oldRow != null ? keyFromMap(oldRow) : keyFromMap(row);
        CohortImpl cohort = StudyManager.getInstance().getCohortForRowId(container, user, rowId);
        if (cohort == null)
            throw new IllegalArgumentException("No cohort found for rowId: " + rowId);

        // Check if there's a conflict based on the label
        String newLabel = (String)row.get("label");
        CohortImpl existingCohort = StudyManager.getInstance().getCohortByLabel(container, user, newLabel);
        if (existingCohort != null && existingCohort.getRowId() != rowId)
            throw new ValidationException("A cohort with the label '" + newLabel + "' already exists");

        // Start a transaction, so that we can rollback if our insert fails
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {

            // hard table columns handled separately
            boolean newEnrolled = (Boolean)row.get("enrolled");
            Integer newSubjectCount = (Integer)row.get("subjectCount");
            String newDescription = (String)row.get("description");

            if (!cohort.getLabel().equals(newLabel) || (cohort.isEnrolled() != newEnrolled)
                || !PropertyUtil.nullSafeEquals(cohort.getSubjectCount(), newSubjectCount)
                || !StringUtils.equals(cohort.getDescription(), newDescription))
            {
                cohort = cohort.createMutable();

                if (cohort.isEnrolled() != newEnrolled)
                {
                    cohort.setEnrolled(newEnrolled);
                }
                if (!cohort.getLabel().equals(newLabel))
                {
                    cohort.setLabel(newLabel);
                }
                if (!PropertyUtil.nullSafeEquals(cohort.getSubjectCount(), newSubjectCount))
                {
                    cohort.setSubjectCount(newSubjectCount);
                }
                if (!StringUtils.equals(cohort.getDescription(), newDescription))
                {
                    cohort.setDescription(newDescription);
                }

                StudyManager.getInstance().updateCohort(user, cohort);
            }

            cohort.savePropertyBag(row);

            // Successfully updated
            transaction.commit();
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
