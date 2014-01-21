/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.study.importer;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.SpecimenImportStrategy;
import org.labkey.study.SampleManager;
import org.labkey.study.StudySchema;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: davebradlee
 * Date: 6/13/13
 * Time: 12:13 PM
 */
public class EditableSpecimenImporter extends SpecimenImporter
{
    private static final String VISIT = "visit_value";
    private static final String DRAW_TIMESTAMP = "draw_timestamp";
    private static final String GUID_COLNAME = "GlobalUniqueId";
    private boolean _insert = false;

    public EditableSpecimenImporter(Container container, User user, boolean insert)
    {
        super(container, user);
        _insert = insert;
    }

    public void process(List<Map<String, Object>> rows, boolean merge) throws SQLException, IOException, ValidationException
    {
        _process(rows, merge, Logger.getLogger(getClass()));
    }

    private void _process(List<Map<String, Object>> rows, boolean merge, Logger logger) throws SQLException, IOException, ValidationException
    {
        if (merge)
        {
            int noGuidRowCount = markEventsObsolete(rows);
            _generateGlobalUniqueIds = noGuidRowCount;            // Generate this many new ids (needed if not present)
        }

        List<Map<String, Object>> specimenRows = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            Map<String, Object> specimenRow = new HashMap<>();
            for (String colName : row.keySet())
            {
                specimenRow.put(colName, row.get(colName));
            }
            specimenRows.add(specimenRow);
        }

        SpecimenImportStrategy strategy = new StandardSpecimenImportStrategy(getContainer());
        Map<SpecimenTableType, SpecimenImportFile> sifMap = new HashMap<>();
        addSpecimenImportFile(sifMap, _specimensTableType, specimenRows, strategy);

        try
        {
            super.process(sifMap, merge, logger);
        }
        catch (SQLException | ValidationException ex)
        {
            if (logger != null)
                logger.error("Error during import", ex);
            throw ex;
        }
    }


    private void addSpecimenImportFile(Map<SpecimenTableType, SpecimenImportFile> fileNameMap, SpecimenTableType type, List<Map<String, Object>> specimenRows, SpecimenImportStrategy strategy)
    {
        assert null != type : "Unknown type!";
        fileNameMap.put(type, new IteratorSpecimenImportFile(specimenRows, strategy, type));
    }

    public List<Map<String, Object>> mapColumnNamesToTsvColumnNames(List<Map<String, Object>> rows) throws IOException
    {
        List<Map<String, Object>> newRows = new ArrayList<>();

        for (Map<String, Object> row : rows)
        {
            Map<String, Object> newRow = new HashMap<>();
            for (String columnName : row.keySet())
            {
                Object value = row.get(columnName);
                if (null != value)
                {
                    String specialName = getSpecialColumnName(columnName);
                    if (null != specialName)
                        columnName = specialName;
                    SpecimenColumn specCol = findSpecimenColumnFromDbName(columnName);
                    if (null != specCol)
                    {
                        newRow.put(specCol.getTsvColumnName(), value);
                    }
                }
            }
            newRows.add(newRow);
        }
        return newRows;
    }

    private Map<String,SpecimenColumn> _dbNameToSpecimenColumnMap;

    private SpecimenColumn findSpecimenColumnFromDbName(String name)
    {
        if (null == _dbNameToSpecimenColumnMap)
        {
            _dbNameToSpecimenColumnMap = new HashMap<>();
            for (SpecimenColumn col : SPECIMEN_COLUMNS)
                _dbNameToSpecimenColumnMap.put(col.getDbColumnName().toLowerCase(), col);
        }

        return _dbNameToSpecimenColumnMap.get(name.toLowerCase());
    }

    private Map<String, String> _specialColumnNameMap;

    private String getSpecialColumnName(String name)
    {
        if (null == _specialColumnNameMap)
        {
            _specialColumnNameMap = new HashMap<>();
            _specialColumnNameMap.put("participantid", "ptid");
            _specialColumnNameMap.put("primarytype", "primarytypeid");
            _specialColumnNameMap.put("additivetype", "additivetypeid");
            _specialColumnNameMap.put("derivativetype", "derivativetypeid");
            _specialColumnNameMap.put("derivativetype2", "derivativetypeid2");
            _specialColumnNameMap.put("sequencenum", "visitvalue");
            _specialColumnNameMap.put("sitename", "labid");
            _specialColumnNameMap.put("clinic", "originatinglocationid");
            _specialColumnNameMap.put("latestdeviationcode1", "deviationcode1");
            _specialColumnNameMap.put("latestdeviationcode2", "deviationcode2");
            _specialColumnNameMap.put("latestdeviationcode3", "deviationcode3");
            _specialColumnNameMap.put("latestintegrity", "integrity");
            _specialColumnNameMap.put("latestcomments", "comments");
            _specialColumnNameMap.put("latestqualitycomments", "qualitycomments");
            _specialColumnNameMap.put("latestyield", "yield");
            _specialColumnNameMap.put("latestratio", "ratio");
            _specialColumnNameMap.put("latestconcentration", "concentration");
            _specialColumnNameMap.put("firstprocessedbyinitials", "processedbyinitials");
        }

        return _specialColumnNameMap.get(name.toLowerCase());
    }

    @Override
    protected void remapTempTableLookupIndexes(DbSchema schema, String tempTable, List<SpecimenColumn> loadedColumns)
            throws SQLException
    {
        // do nothing
    }

    @Override
    protected void checkForConflictingSpecimens(DbSchema schema, String tempTable, List<SpecimenColumn> loadedColumns)
            throws SQLException
    {
        // Only check if inserting
        if (_insert)
            super.checkForConflictingSpecimens(schema, tempTable, loadedColumns);
    }

    private int markEventsObsolete(List<Map<String, Object>> rows)
    {
        Container container = getContainer();
        boolean seenAtLeastOneGuid = false;
        TableInfo tableInfoSpecimenEvent = StudySchema.getInstance().getTableInfoSpecimenEvent(container);
        TableInfo tableInfoVial = StudySchema.getInstance().getTableInfoVial(container);
        if (null == tableInfoSpecimenEvent || null == tableInfoVial)
            throw new IllegalStateException("Expected Vial and SpecimenEvent table to already exist.");

        SQLFragment sql = new SQLFragment("UPDATE ");
        sql.append(tableInfoSpecimenEvent.getSelectName()).append(" SET Obsolete = ")
            .append(tableInfoSpecimenEvent.getSqlDialect().getBooleanTRUE()).append(" WHERE VialId IN ")
            .append("(SELECT RowId FROM ").append(tableInfoVial.getSelectName())
            .append(" WHERE Obsolete = ").append(tableInfoSpecimenEvent.getSqlDialect().getBooleanFALSE())
            .append(" AND " + GUID_COLNAME + " IN (");

        int noGuidRowCount = 0;
        for (Map<String, Object> row : rows)
        {
            String guid = (String)row.get(GLOBAL_UNIQUE_ID_TSV_COL);
            if (null != guid)
            {
                if (seenAtLeastOneGuid)
                    sql.append(", ");
                sql.append("?").add(guid);
                seenAtLeastOneGuid = true;
            }
            else
            {
                noGuidRowCount += 1;
            }
        }
        sql.append("))");
        if (seenAtLeastOneGuid)
        {
            new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);
            SampleManager.getInstance().clearCaches(container);
        }
        return noGuidRowCount;
    }
}
