/*
 * Copyright (c) 2013 LabKey Corporation
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
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;
import org.labkey.study.SampleManager;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.EditableSpecimenImporter;
import org.labkey.study.model.Specimen;
import org.labkey.study.model.SpecimenEvent;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: davebradlee
 * Date: 6/11/13
 * Time: 2:33 PM
 */
public class SpecimenUpdateService extends AbstractQueryUpdateService
{
    public SpecimenUpdateService(TableInfo queryTable)
    {
        super(queryTable);
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        int rowId = keyFromMap(oldRow);
        Specimen specimen = SampleManager.getInstance().getSpecimen(container, rowId);

        if (null == specimen)
            throw new IllegalArgumentException("No specimen found for rowId: " + rowId);

        // Check deletability
        SQLFragment sql = getAllRequestCountSql(container, specimen);
        ArrayList<Integer> counts = new SqlSelector(getQueryTable().getSchema(), sql).getArrayList(Integer.class);
        if (counts.size() > 1)
            throw new ValidationException("Expected one and only one count of rows.");
        else if (counts.size() > 0 && counts.get(0) != 0)
            throw new ValidationException("Specimen may not be deleted because it has been used in a request.");

        SampleManager.getInstance().deleteSpecimen(specimen);
        List<Map<String, Object>> rows = new ArrayList<>(1);

        try
        {
            importSpecimens(user, container, rows, true);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage());
        }

        return oldRow;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        Map<String, Object> result = new TableSelector(getQueryTable()).getObject(keyFromMap(keys), Map.class);
        return result;
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
            throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        String globalUniqueId = (String)row.get("GlobalUniqueId");
        if (null == globalUniqueId || null == row.get("ParticipantId") || null == row.get("SequenceNum"))
        {
            throw new ValidationException("Global Unique Id, Participant Id and Sequence Num are required.");
        }

        row.put("externalid", 1);
        List<Map<String, Object>> rows = new ArrayList<>(1);
        rows.add(row);

        try
        {
            importSpecimens(user, container, rows, true);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage());
        }

        Specimen specimen = SampleManager.getInstance().getSpecimen(container, globalUniqueId);
        if (null == specimen)
            throw new RuntimeException("Specimen did not get added.");

        Map<String,Object> keys = new HashMap<>();
        keys.put("rowId", specimen.getRowId());

        try
        {
            return getRow(user, container, keys);
        }
        catch (InvalidKeyException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        int rowId = oldRow != null ? keyFromMap(oldRow) : keyFromMap(row);
        Specimen specimen = SampleManager.getInstance().getSpecimen(container, rowId);
        if (specimen == null)
            throw new IllegalArgumentException("No specimen found for rowId: " + rowId);

        // Check editablity
        SQLFragment sql = getNonFinalRequestCountSql(container, specimen);
        ArrayList<Integer> counts = new SqlSelector(getQueryTable().getSchema(), sql).getArrayList(Integer.class);
        if (counts.size() > 1)
            throw new IllegalStateException("Expected one and only one count of rows.");
        else if (counts.size() > 0 && counts.get(0) != 0)
            throw new ValidationException("Specimen may not be edited when it's in a non-final request.");

        // Get last event so that our input considers all fields that were set;
        // Then overwrite whatever is coming from the form
        Map<String, Object> rowMap = getLastEventMap(container, specimen);
        for (Map.Entry<String, Object> entry : row.entrySet())
        {
            rowMap.put(entry.getKey(), entry.getValue());
        }

        rowMap.put("globaluniqueid", oldRow.get("globaluniqueid"));         // Add this in
        Long externalId = (Long)rowMap.get("ExternalId");
        externalId += 1;
        rowMap.put("ExternalId", externalId);

        // TODO: this is a hack to best deal with Requestable being Null until a better fix can be accomplished
        if (null == oldRow.get("requestable"))
        {
            Object obj = rowMap.get("requestable");
            if (null != obj && !(Boolean)obj)
                rowMap.put("requestable", null);
        }

        List<Map<String, Object>> rows = new ArrayList<>(1);
        rows.add(rowMap);

        try
        {
            importSpecimens(user, container, rows, true);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage());
        }

        // The rowId will not have changed
        Map<String,Object> keys = new HashMap<>();
        keys.put("rowId", specimen.getRowId());
        try
        {
            return getRow(user, container, keys);
        }
        catch (InvalidKeyException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public int keyFromMap(Map<String,Object> map) throws InvalidKeyException
    {
        if (null == map)
            throw new InvalidKeyException("No values provided");

        Object rowId = map.get("rowId");
        if (null == rowId)
            rowId = map.get("RowId");
        if (null == rowId)
            rowId = map.get("rowid");
        if (null == rowId)
            throw new InvalidKeyException("No value provided for 'rowId' column");
        Integer rowInteger = (Integer)new IntegerConverter(null).convert(Integer.class, rowId);
        if (null == rowInteger)
            throw new InvalidKeyException("Unable to convert rowId of '" + rowId + "' to an int");
        return rowInteger;
    }

    public void importSpecimens(User user, Container container, List<Map<String, Object>> rows, boolean merge) throws SQLException, IOException, ValidationException
    {
        EditableSpecimenImporter importer = new EditableSpecimenImporter();
        rows = importer.mapColumnNamesToTsvColumnNames(rows);
        importer.process(user, container, rows, merge);
    }

    public static SQLFragment getNonFinalRequestCountSql(Container container, Specimen specimen)
    {
        SQLFragment sql = new SQLFragment("SELECT COUNT(FinalState) FROM " + StudySchema.getInstance().getTableInfoSampleRequestStatus() +
                " WHERE FinalState = " + StudySchema.getInstance().getSqlDialect().getBooleanFALSE());
        sql.append(" AND RowId IN (")
                .append("SELECT StatusId FROM " + StudySchema.getInstance().getTableInfoSampleRequest() + " WHERE RowId IN (")
                .append("SELECT SampleRequestId FROM " + StudySchema.getInstance().getTableInfoSampleRequestSpecimen() + " WHERE Container = ? ");
        sql.add(container.getId());
        sql.append(" AND SpecimenGlobalUniqueId = ?").add(specimen.getGlobalUniqueId());
        sql.append(")) GROUP BY FinalState");
        return sql;
    }

    public static SQLFragment getAllRequestCountSql(Container container, Specimen specimen)
    {
        SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM " + StudySchema.getInstance().getTableInfoSampleRequestSpecimen() + " WHERE Container = ? ");
        sql.add(container.getId());
        sql.append(" AND SpecimenGlobalUniqueId = ?").add(specimen.getGlobalUniqueId());
        return sql;
    }

    private static Map<String, Object> getLastEventMap(Container container, Specimen specimen)
    {
        List<Specimen> specimens = new ArrayList<>(1);
        specimens.add(specimen);
        SpecimenEvent specimenEvent = SampleManager.getInstance().getLastEvent(SampleManager.getInstance().getSpecimenEvents(specimens, false));
        if (null == specimenEvent)
            throw new IllegalStateException("Expected at least one event for specimen.");

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("Container"), container);
        filter.addCondition(FieldKey.fromString("VialId"), specimen.getRowId());
        filter.addCondition(FieldKey.fromString("ExternalId"), specimenEvent.getExternalId());
        return new TableSelector(StudySchema.getInstance().getTableInfoSpecimenEvent(), filter, null).getMap();
    }
}

