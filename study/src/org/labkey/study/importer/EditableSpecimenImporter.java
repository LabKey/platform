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
 * Created with IntelliJ IDEA.
 * User: davebradlee
 * Date: 6/13/13
 * Time: 12:13 PM
 * To change this template use File | Settings | File Templates.
 */
public class EditableSpecimenImporter extends SpecimenImporter
{
    private static final String VISIT = "visit_value";
    private static final String DRAW_TIMESTAMP = "draw_timestamp";
    private static final String GUID_COLNAME = "GlobalUniqueId";

    public EditableSpecimenImporter()
    {

    }

    public void process(User user, Container container, List<Map<String, Object>> rows, boolean merge) throws SQLException, IOException, ValidationException
    {
        _process(user, container, rows, merge, Logger.getLogger(getClass()));
    }

    private void _process(User user, Container container, List<Map<String, Object>> rows, boolean merge, Logger logger) throws SQLException, IOException, ValidationException
    {
        if (merge)
            markEventsObsolete(container, rows);

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

        SpecimenImportStrategy strategy = new StandardSpecimenImportStrategy(container);
        Map<SpecimenTableType, SpecimenImportFile> sifMap = new EnumMap<>(SpecimenTableType.class);
        addSpecimenImportFile(sifMap, SpecimenTableType.Specimens, specimenRows, strategy);

        try
        {
            super.process(user, container, sifMap, merge, logger);
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
    protected void remapTempTableLookupIndexes(DbSchema schema, Container container, String tempTable, List<SpecimenColumn> loadedColumns)
            throws SQLException
    {
        // do nothing
    }

    @Override
    protected void checkForConflictingSpecimens(DbSchema schema, Container container, String tempTable, List<SpecimenColumn> loadedColumns)
            throws SQLException
    {
        // do nothing
    }

    private void markEventsObsolete(Container container, List<Map<String, Object>> rows)
    {
        boolean seenAtLeastOneGuid = false;
        TableInfo specimenEventTable = StudySchema.getInstance().getTableInfoSpecimenEvent();
        SQLFragment sql = new SQLFragment("UPDATE " + specimenEventTable.getSelectName() + " SET Obsolete = " +
                specimenEventTable.getSqlDialect().getBooleanTRUE() + " WHERE Container = ? AND VialId IN ");
        sql.add(container);
        sql.append("(SELECT RowId FROM " + StudySchema.getInstance().getTableInfoVial() + " WHERE Container = ? ").add(container);
        sql.append(" AND Obsolete = " + specimenEventTable.getSqlDialect().getBooleanFALSE() + " AND " + GUID_COLNAME + " IN (");
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
        }
        sql.append("))");
        if (seenAtLeastOneGuid)
        {
            new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);
            SampleManager.getInstance().clearCaches(container);
        }
    }
}
