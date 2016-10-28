/*
 * Copyright (c) 2013-2016 LabKey Corporation
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

import org.apache.commons.beanutils.converters.LongConverter;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.study.Study;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.SpecimenManager;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.EditableSpecimenImporter;
import org.labkey.study.model.Vial;
import org.labkey.study.model.SpecimenEvent;
import org.labkey.study.model.StudyManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: davebradlee
 * Date: 6/11/13
 * Time: 2:33 PM
 */
public class SpecimenUpdateService extends AbstractQueryUpdateService
{
    Logger _logger = null;

    public SpecimenUpdateService(TableInfo queryTable)
    {
        super(queryTable);
    }


    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws SQLException
    {
        if (null != configParameters)
        {
            Object o = configParameters.get(ConfigParameters.Logger);
            if (o instanceof org.apache.log4j.Logger)
                _logger = (Logger)o;
        }
        DataIteratorContext context = getDataIteratorContext(errors, InsertOption.IMPORT, configParameters);
        return _importRowsUsingInsertRows(user,container,rows.getDataIterator(context),errors,configParameters,extraScriptContext);
    }


    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, DeletePermission.class))
            throw new UnauthorizedException("You do not have permission to delete data from this table.");

        BatchValidationException errors = new BatchValidationException();
        errors.setExtraContext(extraScriptContext);
        getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.DELETE, true, errors, extraScriptContext);

        Set<Long> rowIds = new HashSet<>(keys.size());
        for (Map<String, Object> key : keys)
        {
            Object rowId = key.get("rowid");
            if (null != rowId)
                rowIds.add(Long.class == rowId.getClass() ? (Long) rowId : (Integer) rowId);
            else
                throw new IllegalArgumentException("RowId not found for a row.");
        }
        List<Vial> vials = SpecimenManager.getInstance().getVials(container, user, rowIds);
        if (vials.size() != keys.size())
            throw new IllegalStateException("Specimens should be same size as rows.");

        List<Map<String, Object>> rows = new ArrayList<>(1);
        try
        {
            for (Vial vial : vials)
                checkDeletability(container, vial);

            for (Vial vial : vials)
                SpecimenManager.getInstance().deleteSpecimen(vial, false);
            SpecimenManager.getInstance().clearCaches(container);

            // Force recalculation of requestability and specimen table
            EditableSpecimenImporter importer = new EditableSpecimenImporter(container, user, false);
            importSpecimens(importer, rows, true);
        }
        catch (ValidationException e)
        {
            errors.addRowError(e);
            throw errors;
        }
        catch (SpecimenManager.SpecimenRequestException e)
        {
            errors.addRowError(new ValidationException(e.getMessage()));
            throw errors;
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage());
        }

        getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.DELETE, false, errors, extraScriptContext);

        if (!isBulkLoad())
            QueryService.get().addAuditEvent(user, container, getQueryTable(), QueryService.AuditAction.DELETE);

        return new ArrayList<>();
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, SQLException
    {
        long rowId = keyFromMap(oldRow);
        Vial vial = SpecimenManager.getInstance().getVial(container, user, rowId);

        if (null == vial)
            throw new IllegalArgumentException("No specimen found for rowId: " + rowId);

        checkDeletability(container, vial);
        SpecimenManager.getInstance().deleteSpecimen(vial, true);
        List<Map<String, Object>> rows = new ArrayList<>(1);

        try
        {
            // Force recalculation of requestability and specimen table
            EditableSpecimenImporter importer = new EditableSpecimenImporter(container, user, false);
            importSpecimens(importer, rows, true);
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
        Map<String, Object> row = new TableSelector(getQueryTable()).getObject(keyFromMap(keys), Map.class);
        Map<String, Object> result = new HashMap<>();
        for (ColumnInfo column : getQueryTable().getColumns())
            if (row.containsKey(column.getName()))
                result.put(column.getName(), row.get(column.getName()));
            else
                result.put(column.getName(), row.get(column.getName().toLowerCase()));

        return result;
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
            throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        Study study = StudyManager.getInstance().getStudy(container);
        if (null == study)
            throw new IllegalStateException("No study found.");

        boolean hasTableScript = getQueryTable().hasTriggers(container);

        errors.setExtraContext(extraScriptContext);
        try
        {
            if (hasTableScript)
                getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.INSERT, true, errors, extraScriptContext);
        }
        catch (BatchValidationException e)
        {
            return null;
        }

        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        String subjectColumnName = study.getSubjectColumnName();

        try
        {
            for (Map<String, Object> row : rows)
            {
                if (null == row.get(subjectColumnName) || null == row.get("SequenceNum"))
                {
                    throw new ValidationException(subjectColumnName + " and Sequence Num are required.");
                }

                if (!subjectColumnName.equalsIgnoreCase("participantid"))
                {
                    row.put("participantid", row.get(subjectColumnName));
                    row.remove(subjectColumnName);
                }

                row.put("externalid", 1);
            }

            EditableSpecimenImporter importer = new EditableSpecimenImporter(container, user, true);
            importSpecimens(importer, rows, true);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage());
        }
        catch (IllegalStateException e)
        {
            errors.addRowError(new ValidationException(e.getMessage()));
        }
        catch (ValidationException e)
        {
            errors.addRowError(e);
        }

        if (!errors.hasErrors())
        {
/*            try
            {
                result = getRows(user, container, keys);
            }
            catch (InvalidKeyException e)
            {
                throw UnexpectedException.wrap(e);
            }
*/
        }

        try
        {
            if (hasTableScript)
                getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.INSERT, false, errors, extraScriptContext);
        }
        catch (BatchValidationException e)
        {

        }

        return result;
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
            throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        Study study = StudyManager.getInstance().getStudy(container);
        if (null == study)
            throw new IllegalStateException("No study found.");
        String subjectColumnName = study.getSubjectColumnName();

        if (null == row.get(subjectColumnName) || null == row.get("SequenceNum"))
        {
            throw new ValidationException(subjectColumnName + " and Sequence Num are required.");
        }

        if (!subjectColumnName.equalsIgnoreCase("participantid"))
        {
            row.put("participantid", row.get(subjectColumnName));
            row.remove(subjectColumnName);
        }

        row.put("externalid", 1);
        List<Map<String, Object>> rows = new ArrayList<>(1);
        rows.add(row);

        try
        {
            EditableSpecimenImporter importer = new EditableSpecimenImporter(container, user, true);
            importSpecimens(importer, rows, true);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage());
        }
        catch (IllegalStateException e)
        {
            throw new ValidationException(e.getMessage());
        }

/*
        try
        {
            return getRow(user, container, keys.get(0));
        }
        catch (InvalidKeyException e)
        {
            throw UnexpectedException.wrap(e);
        }
*/
        return new HashMap<>();
    }

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        if (oldKeys != null && rows.size() != oldKeys.size())
            throw new IllegalArgumentException("rows and oldKeys are required to be the same length, but were " + rows.size() + " and " + oldKeys + " in length, respectively");

        BatchValidationException errors = new BatchValidationException();
        errors.setExtraContext(extraScriptContext);
        getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.UPDATE, true, errors, extraScriptContext);

        Set<Long> rowIds = new HashSet<>(rows.size());
        Map<Long, Map<String, Object>> uniqueRows = new HashMap<>(rows.size());
        for (int i = 0; i < rows.size(); i++)
        {
            Map<String, Object> row = rows.get(i);
            assert null != oldKeys;
            Map<String, Object> oldRow = oldKeys.get(i);
            long rowId = oldRow != null ? keyFromMap(oldRow) : keyFromMap(row);
            rowIds.add(rowId);
            uniqueRows.put(rowId, row);
        }
        Map<Long, Vial> vials = new HashMap<>();
        for (Vial vial : SpecimenManager.getInstance().getVials(container, user, rowIds))
        {
            vials.put(vial.getRowId(), vial);
        }
        if (vials.size() != uniqueRows.size())
            throw new IllegalStateException("Specimens should be same size as rows.");

        try
        {
            for (Vial vial : vials.values())
                checkEditability(container, vial);
        }
        catch (ValidationException e)
        {
            errors.addRowError(e);
            throw errors;
        }
        catch (SpecimenManager.SpecimenRequestException e)
        {
            errors.addRowError(new ValidationException(e.getMessage()));
            throw errors;
        }

        EditableSpecimenImporter importer = new EditableSpecimenImporter(container, user, false);
        List<Map<String, Object>> newKeys = new ArrayList<>(uniqueRows.size());
        List<Map<String, Object>> newRows = new ArrayList<>(uniqueRows.size());
        long externalId = SpecimenManager.getInstance().getMaxExternalId(container) + 1;
        for (Map.Entry<Long, Map<String, Object>> row : uniqueRows.entrySet())
        {
            Vial vial = vials.get(row.getKey());
            Map<String, Object> rowMap = prepareRowMap(container, row.getValue(), vial, importer, externalId++);
            newRows.add(rowMap);

            Map<String,Object> keys = new HashMap<>();
            keys.put("rowId", row.getKey());
            newKeys.add(keys);
        }

        try
        {
            importSpecimens(importer, newRows, true);
        }
        catch (ValidationException e)
        {
            e.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), newRows.get(0), 0);
            errors.addRowError(e);
            throw errors;
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage());
        }

        getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.UPDATE, false, errors, extraScriptContext);

        if (!isBulkLoad())
            QueryService.get().addAuditEvent(user, container, getQueryTable(), QueryService.AuditAction.UPDATE, rows);

        return getRows(user, container, newKeys);
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        long rowId = oldRow != null ? keyFromMap(oldRow) : keyFromMap(row);
        Vial vial = SpecimenManager.getInstance().getVial(container, user, rowId);
        if (vial == null)
            throw new IllegalArgumentException("No specimen found for rowId: " + rowId);

        checkEditability(container, vial);

        EditableSpecimenImporter importer = new EditableSpecimenImporter(container, user, false);
        long externalId = SpecimenManager.getInstance().getMaxExternalId(container) + 1;
        Map<String, Object> rowMap = prepareRowMap(container, row, vial, importer, externalId);

        // TODO: this is a hack to best deal with Requestable being Null until a better fix can be accomplished
        if (null == oldRow || null == oldRow.get("requestable"))
        {
            Object obj = rowMap.get("requestable");
            if (null != obj && !(Boolean)obj)
                rowMap.put("requestable", null);
        }

        List<Map<String, Object>> rows = new ArrayList<>(1);
        rows.add(rowMap);

        try
        {
            importSpecimens(importer, rows, true);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage());
        }

        // The rowId will not have changed
        Map<String,Object> keys = new HashMap<>();
        keys.put("rowId", vial.getRowId());
        try
        {
            return getRow(user, container, keys);
        }
        catch (InvalidKeyException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    private long keyFromMap(Map<String,Object> map) throws InvalidKeyException
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
        Long rowLong = (Long)new LongConverter(null).convert(Long.class, rowId);
        if (null == rowLong)
            throw new InvalidKeyException("Unable to convert rowId of '" + rowId + "' to a long");
        return rowLong;
    }

    private Map<String, Object> prepareRowMap(Container container, Map<String, Object> row, Vial vial,
                                              EditableSpecimenImporter importer, long externalId)
    {
        // Get last event so that our input considers all fields that were set;
        // Then overwrite whatever is coming from the form
        Map<String, Object> rowMap = getLastEventMap(container, vial);
        for (Map.Entry<String, Object> entry : row.entrySet())
        {
            String columnName = importer.getMappedColumnName(entry.getKey());
            rowMap.put(columnName, entry.getValue());
        }

        rowMap.put("globaluniqueid", vial.getGlobalUniqueId());
        rowMap.put("ExternalId", externalId);
        return rowMap;
    }

    private void importSpecimens(EditableSpecimenImporter importer, List<Map<String, Object>> rows,
                                 boolean merge) throws SQLException, IOException, ValidationException
    {
        rows = importer.mapColumnNamesToTsvColumnNames(rows);
        importer.process(rows, merge, _logger);
    }

    private void checkEditability(Container container, Vial vial) throws ValidationException
    {
        SQLFragment sql = new SQLFragment("SELECT COUNT(FinalState) FROM " + StudySchema.getInstance().getTableInfoSampleRequestStatus() +
                " WHERE FinalState = " + StudySchema.getInstance().getSqlDialect().getBooleanFALSE());
        sql.append(" AND RowId IN (")
                .append("SELECT StatusId FROM " + StudySchema.getInstance().getTableInfoSampleRequest() + " WHERE RowId IN (")
                .append("SELECT SampleRequestId FROM " + StudySchema.getInstance().getTableInfoSampleRequestSpecimen() + " WHERE Container = ? ");
        sql.add(container.getId());
        sql.append(" AND SpecimenGlobalUniqueId = ?").add(vial.getGlobalUniqueId());
        sql.append(")) GROUP BY FinalState");

        ArrayList<Integer> counts = new SqlSelector(getQueryTable().getSchema(), sql).getArrayList(Integer.class);
        if (counts.size() > 1)
            throw new IllegalStateException("Expected one and only one count of rows.");
        else if (counts.size() > 0 && counts.get(0) != 0)
            throw new ValidationException("Specimen may not be edited when it's in a non-final request.");
    }

    private void checkDeletability(Container container, Vial vial) throws ValidationException
    {
        SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM " + StudySchema.getInstance().getTableInfoSampleRequestSpecimen() + " WHERE Container = ? ");
        sql.add(container.getId());
        sql.append(" AND SpecimenGlobalUniqueId = ?").add(vial.getGlobalUniqueId());

        ArrayList<Integer> counts = new SqlSelector(getQueryTable().getSchema(), sql).getArrayList(Integer.class);
        if (counts.size() > 1)
            throw new ValidationException("Expected one and only one count of rows.");
        else if (counts.size() > 0 && counts.get(0) != 0)
            throw new ValidationException("Specimen may not be deleted because it has been used in a request.");
    }

    private static Map<String, Object> getLastEventMap(Container container, Vial vial)
    {
        List<Vial> vials = Collections.singletonList(vial);
        SpecimenEvent specimenEvent = SpecimenManager.getInstance().getLastEvent(SpecimenManager.getInstance().getSpecimenEvents(vials, false));
        if (null == specimenEvent)
            throw new IllegalStateException("Expected at least one event for specimen.");
        TableInfo tableInfoSpecimenEvent = StudySchema.getInstance().getTableInfoSpecimenEvent(container);

        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromString("RowId"), specimenEvent.getRowId());
        return new TableSelector(tableInfoSpecimenEvent, filter, null).getMap();
    }
}

