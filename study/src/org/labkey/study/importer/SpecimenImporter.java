package org.labkey.study.importer;

import org.labkey.study.SampleManager;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Study;
import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.security.User;
import org.labkey.common.tools.TabLoader;
import org.labkey.common.util.CPUTimer;
import org.labkey.api.util.GUID;
import org.labkey.api.util.DateUtil;
import org.labkey.api.study.SpecimenService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * User: brittp
 * Date: Mar 13, 2006
 * Time: 2:18:48 PM
 */
public class SpecimenImporter
{
    CPUTimer cpuPopulateMaterials = new CPUTimer("populateMaterials");
    CPUTimer cpuUpdateSpecimens = new CPUTimer("updateSpecimens");
    CPUTimer cpuInsertSpecimens = new CPUTimer("insertSpecimens");
    CPUTimer cpuUpdateSpecimenEvents = new CPUTimer("updateSpecimenEvents");
    CPUTimer cpuInsertSpecimenEvents = new CPUTimer("insertSpecimenEvents");
    CPUTimer cpuMergeTable = new CPUTimer("mergeTable");
    CPUTimer cpuCreateTempTable = new CPUTimer("createTempTable");
    CPUTimer cpuPopulateTempTable = new CPUTimer("populateTempTable");


    private static class ImportableColumn
    {
        private String _tsvColumnName;
        protected String _dbType;
        private String _dbColumnName;
        private Class _javaType = null;
        private boolean _unique;

        public ImportableColumn(String tsvColumnName, String dbColumnName, String databaseType)
        {
            this(tsvColumnName, dbColumnName, databaseType, false);
        }

        public ImportableColumn(String tsvColumnName, String dbColumnName, String databaseType, boolean unique)
        {
            _tsvColumnName = tsvColumnName;
            _dbColumnName = dbColumnName;
            _dbType = databaseType.toUpperCase();
            _unique = unique;
        }


        public TabLoader.ColumnDescriptor getColumnDescriptor()
        {
            return new TabLoader.ColumnDescriptor(_tsvColumnName, getJavaType());
        }

        public String getDbColumnName()
        {
            return _dbColumnName;
        }

        public String getTsvColumnName()
        {
            return _tsvColumnName;
        }

        public boolean isUnique()
        {
            return _unique;
        }

        public Class getJavaType()
        {
            if (_javaType == null)
            {
                if (_dbType.indexOf("VARCHAR") >= 0)
                    _javaType = String.class;
                else if (_dbType.indexOf(DATETIME_TYPE) >= 0)
                    _javaType = java.util.Date.class;
                else if (_dbType.indexOf("FLOAT") >= 0)
                    _javaType = Float.class;
                else if (_dbType.indexOf("INT") >= 0)
                    _javaType = Integer.class;
                else if (_dbType.indexOf(BOOLEAN_TYPE) >= 0)
                    _javaType = Boolean.class;
                else
                    throw new UnsupportedOperationException("Unrecognized sql type: " + _dbType);
            }
            return _javaType;
        }

        public int getSQLType()
        {
            if (_dbType.indexOf("VARCHAR") >= 0)
                return Types.VARCHAR;
            else if (_dbType.indexOf(DATETIME_TYPE) >= 0)
                return Types.DATE;
            else if (_dbType.indexOf("FLOAT") >= 0)
                return Types.FLOAT;
            else if (_dbType.indexOf("INT") >= 0)
                return Types.INTEGER;
            else if (_dbType.indexOf(BOOLEAN_TYPE) >= 0)
                return Types.BIT;
            else
                throw new UnsupportedOperationException("SQL type has not been defined for class " + _dbType);
        }
    }

    private enum TargetTable
    {
        SPECIMEN_EVENTS
        {
            public String getName()
            {
                return "SpecimenEvent";
            }
        },
        SPECIMENS
        {
            public String getName()
            {
                return "Specimen";
            }
        },
        IGNORED
        {
            public String getName()
            {
                throw new UnsupportedOperationException("Tablename can't be retrieved for ignored columns.");
            }
        };

        public abstract String getName();
    }

    protected static class SpecimenColumn extends ImportableColumn
    {
        private TargetTable _targetTable;
        private String _fkTable;
        private String _fkColumn;

        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType, TargetTable eventColumn, boolean unique)
        {
            super(tsvColumnName, dbColumnName, databaseType, unique);
            _targetTable = eventColumn;
        }

        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType, TargetTable eventColumn)
        {
            this(tsvColumnName, dbColumnName, databaseType, eventColumn, false);
        }

        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType,
                              TargetTable eventColumn, String fkTable, String fkColumn)
        {
            this(tsvColumnName, dbColumnName, databaseType, eventColumn, false);
            _fkColumn = fkColumn;
            _fkTable = fkTable;
        }

        public TargetTable getTargetTable()
        {
            return _targetTable;
        }

        public String getFkColumn()
        {
            return _fkColumn;
        }

        public String getFkTable()
        {
            return _fkTable;
        }

        public String getDbType()
        {
            return _dbType;
        }
    }

    private static class SpecimenLoadInfo
    {
        private String _tempTableName;
        private List<SpecimenColumn> _availableColumns;

        public SpecimenLoadInfo(List<SpecimenColumn> availableColumns, String tempTableName)
        {
            _availableColumns = availableColumns;
            _tempTableName = tempTableName;
        }

        public List<SpecimenColumn> getAvailableColumns()
        {
            return _availableColumns;
        }

        public String getTempTableName()
        {
            return _tempTableName;
        }
    }

    private static final String DATETIME_TYPE = StudySchema.getInstance().getSqlDialect().getDefaultDateTimeDatatype();
    private static final String BOOLEAN_TYPE = StudySchema.getInstance().getSqlDialect().getBooleanDatatype();
    private static final String GLOBAL_UNIQUE_ID_TSV_COL = "global_unique_specimen_id";
    private static final String LAB_ID_TSV_COL = "lab_id";
    private static final String SPEC_NUMBER_TSV_COL = "specimen_number";
    private static final String EVENT_ID_COL = "record_id";

    protected final SpecimenColumn[] SPECIMEN_COLUMNS = {
            new SpecimenColumn(EVENT_ID_COL, "ScharpId", "INT NOT NULL", TargetTable.SPECIMEN_EVENTS, true),
            new SpecimenColumn("record_source", "RecordSource", "VARCHAR(10)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn(GLOBAL_UNIQUE_ID_TSV_COL, "GlobalUniqueId", "VARCHAR(20)", TargetTable.SPECIMENS),
            new SpecimenColumn(LAB_ID_TSV_COL, "LabId", "INT", TargetTable.IGNORED, "Site", "ScharpId"),
            new SpecimenColumn("originating_location", "OriginatingLocationId", "INT", TargetTable.SPECIMENS, "Site", "ScharpId"),
            new SpecimenColumn("unique_specimen_id", "UniqueSpecimenId", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("ptid", "Ptid", "VARCHAR(32)", TargetTable.SPECIMENS),
            new SpecimenColumn("parent_specimen_id", "ParentSpecimenId", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("draw_timestamp", "DrawTimestamp", DATETIME_TYPE, TargetTable.SPECIMENS),
            new SpecimenColumn("sal_receipt_date", "SalReceiptDate", DATETIME_TYPE, TargetTable.SPECIMENS),
            new SpecimenColumn(SPEC_NUMBER_TSV_COL, "SpecimenNumber", "VARCHAR(50)", TargetTable.SPECIMENS),
            new SpecimenColumn("class_id", "ClassId", "VARCHAR(4)", TargetTable.SPECIMENS),
            new SpecimenColumn("visit_value", "VisitValue", "FLOAT", TargetTable.SPECIMENS),
            new SpecimenColumn("protocol_number", "ProtocolNumber", "VARCHAR(10)", TargetTable.SPECIMENS),
            new SpecimenColumn("visit_description", "VisitDescription", "VARCHAR(3)", TargetTable.SPECIMENS),
            new SpecimenColumn("other_specimen_id", "OtherSpecimenId", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("volume", "Volume", "FLOAT", TargetTable.SPECIMENS),
            new SpecimenColumn("volume_units", "VolumeUnits", "VARCHAR(3)", TargetTable.SPECIMENS),
            new SpecimenColumn("stored", "Stored", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("storage_flag", "storageFlag", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("storage_date", "StorageDate", DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("ship_flag", "ShipFlag", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("ship_batch_number", "ShipBatchNumber", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("ship_date", "ShipDate", DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("imported_batch_number", "ImportedBatchNumber", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("lab_receipt_date", "LabReceiptDate", DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("expected_time_value", "ExpectedTimeValue", "FLOAT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("expected_time_unit", "ExpectedTimeUnit", "VARCHAR(15)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("group_protocol", "GroupProtocol", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("sub_additive_derivative", "SubAdditiveDerivative", "VARCHAR(20)", TargetTable.SPECIMENS),
            new SpecimenColumn("comments", "Comments", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("primary_specimen_type_id", "PrimaryTypeId", "INT", TargetTable.SPECIMENS),
            new SpecimenColumn("derivative_type_id", "DerivativeTypeId", "INT", TargetTable.SPECIMENS),
            new SpecimenColumn("additive_type_id", "AdditiveTypeId", "INT", TargetTable.SPECIMENS),
            new SpecimenColumn("specimen_condition", "SpecimenCondition", "VARCHAR(3)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("sample_number", "SampleNumber", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("x_sample_origin", "XSampleOrigin", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("external_location", "ExternalLocation", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("update_timestamp", "UpdateTimestamp", DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("requestable", "Requestable", BOOLEAN_TYPE, TargetTable.SPECIMENS),
            new SpecimenColumn("freezer", "freezer", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("fr_level1", "fr_level1", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("fr_level2", "fr_level2", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("fr_container", "fr_container", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("fr_position", "fr_position", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS)
        };

    private final ImportableColumn[] ADDITIVE_COLUMNS = new ImportableColumn[]
        {
            new ImportableColumn("additive_id", "ScharpId", "INT NOT NULL", true),
            new ImportableColumn("ldms_additive_code", "LdmsAdditiveCode", "VARCHAR(3)"),
            new ImportableColumn("labware_additive_code", "LabwareAdditiveCode", "VARCHAR(20)"),
            new ImportableColumn("additive", "Additive", "VARCHAR(100)")
        };

    private final ImportableColumn[] DERIVATIVE_COLUMNS = new ImportableColumn[]
        {
            new ImportableColumn("derivative_id", "ScharpId", "INT NOT NULL", true),
            new ImportableColumn("ldms_derivative_code", "LdmsDerivativeCode", "VARCHAR(3)"),
            new ImportableColumn("labware_derivative_code", "LabwareDerivativeCode", "VARCHAR(20)"),
            new ImportableColumn("derivative", "Derivative", "VARCHAR(100)")
        };

    private final ImportableColumn[] SITE_COLUMNS = new ImportableColumn[]
        {
            new ImportableColumn("lab_id", "ScharpId", "INT NOT NULL", true),
            new ImportableColumn("ldms_lab_code", "LdmsLabCode", "INT"),
            new ImportableColumn("labware_lab_code", "LabwareLabCode", "VARCHAR(20)"),
            new ImportableColumn("lab_name", "Label", "VARCHAR(200)"),
            new ImportableColumn("lab_upload_code", "LabUploadCode", "VARCHAR(2)"),
            new ImportableColumn("is_sal", "IsSal", BOOLEAN_TYPE),
            new ImportableColumn("is_repository", "IsRepository", BOOLEAN_TYPE),
            new ImportableColumn("is_clinic", "IsClinic", BOOLEAN_TYPE),
            new ImportableColumn("is_endpoint", "IsEndpoint", BOOLEAN_TYPE)
        };

    private final ImportableColumn[] PRIMARYTYPE_COLUMNS = new ImportableColumn[]
        {
            new ImportableColumn("primary_type_id", "ScharpId", "INT NOT NULL", true),
            new ImportableColumn("primary_type_ldms_code", "PrimaryTypeLdmsCode", "VARCHAR(5)"),
            new ImportableColumn("primary_type_labware_code", "PrimaryTypeLabwareCode", "VARCHAR(5)"),
            new ImportableColumn("primary_type", "PrimaryType", "VARCHAR(100)")
        };

    private Map<String, ImportableColumn> _tsvNameToColumnInfo = null;
    private String _specimenCols;
    private String _specimenEventCols;
    private Logger _logger;

    private static final int SQL_BATCH_SIZE = 100;

    public void process(User user, Container container, List<File> files, Logger logger) throws SQLException, IOException
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        Map<String,File> fileMap = createFilemap(files);
        _logger = logger;

        try
        {

            DbScope scope = schema.getScope();
            if (!DEBUG)
                scope.beginTransaction();

            SpecimenLoadInfo loadInfo = populateTempSpecimensTable(schema, container, getContents(fileMap.get("specimens")));

            mergeTable(schema, container, "Site", SITE_COLUMNS,
                    getContents(fileMap.get("labs")), true);

            replaceTable(schema, container, "SpecimenAdditive", ADDITIVE_COLUMNS,
                    getContents(fileMap.get("additives")), false);
            replaceTable(schema, container, "SpecimenDerivative", DERIVATIVE_COLUMNS,
                    getContents(fileMap.get("derivatives")), false);
            replaceTable(schema, container, "SpecimenPrimaryType", PRIMARYTYPE_COLUMNS,
                    getContents(fileMap.get("primary_types")), false);
            populateSpecimenTables(schema, container, loadInfo);

            Study study = StudyManager.getInstance().getStudy(container);
            StudyManager.getInstance().getVisitManager(study).updateParticipantVisits();

            if (!DEBUG)
                scope.commitTransaction();
        }
        finally
        {
            if (schema.getScope().isTransactionActive())
                schema.getScope().rollbackTransaction();
            StudyManager.getInstance().clearCaches(container, false);
            SampleManager.getInstance().clearCaches(container);
        }
        dumpTimers();
    }

    protected void process(User user, Container container, Map<String,Map[]> tsvMap, Logger logger) throws SQLException, IOException
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        _logger = logger;

        try
        {

            DbScope scope = schema.getScope();
            if (!DEBUG)
                scope.beginTransaction();

            SpecimenLoadInfo loadInfo = populateTempSpecimensTable(schema, container, tsvMap.get("specimens"));

            mergeTable(schema, container, "Site", SITE_COLUMNS,
                    tsvMap.get("labs"), true);

            replaceTable(schema, container, "SpecimenAdditive", ADDITIVE_COLUMNS,
                    tsvMap.get("additives"), false);
            replaceTable(schema, container, "SpecimenDerivative", DERIVATIVE_COLUMNS,
                    tsvMap.get("derivatives"), false);
            replaceTable(schema, container, "SpecimenPrimaryType", PRIMARYTYPE_COLUMNS,
                    tsvMap.get("primary_types"), false);
            populateSpecimenTables(schema, container, loadInfo);

            Study study = StudyManager.getInstance().getStudy(container);
            StudyManager.getInstance().getVisitManager(study).updateParticipantVisits();

            if (!DEBUG)
                scope.commitTransaction();
        }
        finally
        {
            if (schema.getScope().isTransactionActive())
                schema.getScope().rollbackTransaction();
            StudyManager.getInstance().clearCaches(container, false);
            SampleManager.getInstance().clearCaches(container);
        }
        dumpTimers();

    }

    private void dumpTimers()
    {
        Logger logDebug = Logger.getLogger(SpecimenImporter.class);
        logDebug.debug("  cumulative\t     average\t       calls\ttimer");
        logDebug.debug(cpuPopulateMaterials);
        logDebug.debug(cpuInsertSpecimens);
        logDebug.debug(cpuUpdateSpecimens);
        logDebug.debug(cpuInsertSpecimenEvents);
        logDebug.debug(cpuUpdateSpecimenEvents);
        logDebug.debug(cpuMergeTable);
        logDebug.debug(cpuCreateTempTable);
        logDebug.debug(cpuPopulateTempTable);
    }

    private SpecimenLoadInfo populateTempSpecimensTable(DbSchema schema, Container container, String tsv) throws SQLException, IOException
    {
        String tempTable = createTempTable(schema);
        info("Created temporary table " + tempTable);
        List<SpecimenColumn> availableColumns = populateTempTable(schema, container, tempTable, tsv);
        return new SpecimenLoadInfo(availableColumns, tempTable);
    }

    private SpecimenLoadInfo populateTempSpecimensTable(DbSchema schema, Container container, Map[] rows) throws SQLException, IOException
    {
        String tempTable = createTempTable(schema);
        List<SpecimenColumn> availableColumns = populateTempTable(schema, container, tempTable, rows);
        return new SpecimenLoadInfo(availableColumns, tempTable);
    }


    private void populateSpecimenTables(DbSchema schema, Container container, SpecimenLoadInfo info) throws SQLException, IOException
    {
        SimpleFilter containerFilter = new SimpleFilter("Container", container.getId());
        info("Deleting old data from Specimen Event table...");
        Table.delete(StudySchema.getInstance().getTableInfoSpecimenEvent(), containerFilter);
        info("Complete.");
        info("Deleting old data from Specimen table...");
        Table.delete(StudySchema.getInstance().getTableInfoSpecimen(), containerFilter);
        info("Complete.");

        populateMaterials(schema, container, info);
        populateSpecimens(schema, container, info);
        populateSpecimenEvents(schema, container, info);
    }

    private String getContents(File file) throws IOException
    {
        if (file == null)
            return null;
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(file));
            char[] chars = new char[1024];
            int len;
            while ((len = reader.read(chars, 0, 1024)) > 0)
                builder.append(chars, 0, len);
            return builder.toString();
        }
        finally
        {
            if (reader != null) try { reader.close(); } catch (IOException e) {}
        }
    }


    private Map<String, File> createFilemap(List<File> files) throws IOException
    {
        Map<String, File> fileMap = new HashMap<String, File>(files.size());

        for (File file : files)
        {
            BufferedReader reader = null;
            try
            {
                reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();
                line = line.trim();
                if (line.charAt(0) != '#')
                    throw new IllegalStateException("Import files are expected to start with a comment indicating table name");
                fileMap.put(line.substring(1).trim().toLowerCase(), file);
            }
            finally
            {
                if (reader != null) try { reader.close(); } catch (IOException e) {}
            }
        }
        return fileMap;
    }

    private void info(String message)
    {
        if (_logger != null)
            _logger.info(message);
    }

    private String getSpecimenCols(List<SpecimenColumn> availableColumns)
    {
        if (_specimenCols == null)
        {
            String suffix = ",\n    ";
            StringBuilder cols = new StringBuilder();
            for (SpecimenColumn col : availableColumns)
            {
                if (col.getTargetTable() == TargetTable.SPECIMENS)
                    cols.append(col.getDbColumnName()).append(suffix);
            }
            _specimenCols = cols.toString().substring(0, cols.length() - suffix.length());
        }
        return _specimenCols;
    }

    private String getSpecimenEventCols(List<SpecimenColumn> availableColumns)
    {
        if (_specimenEventCols == null)
        {
            String suffix = ",\n    ";
            StringBuilder cols = new StringBuilder();
            for (SpecimenColumn col : availableColumns)
            {
                if (col.getTargetTable() == TargetTable.SPECIMEN_EVENTS)
                    cols.append(col.getDbColumnName()).append(suffix);
            }
            _specimenEventCols = cols.toString().substring(0, cols.length() - suffix.length());
        }
        return _specimenEventCols;
    }


    private void populateMaterials(DbSchema schema, Container container, SpecimenLoadInfo info) throws SQLException
    {
        assert cpuPopulateMaterials.start();

        String columnName = null;
        for (SpecimenColumn specimenColumn : info.getAvailableColumns())
        {
            if (GLOBAL_UNIQUE_ID_TSV_COL.equals(specimenColumn.getTsvColumnName()))
            {
                columnName = specimenColumn.getDbColumnName();
                break;
            }
        }
        if (columnName == null)
        {
            for (SpecimenColumn specimenColumn : info.getAvailableColumns())
            {
                if (SPEC_NUMBER_TSV_COL.equals(specimenColumn.getTsvColumnName()))
                {
                    columnName = specimenColumn.getDbColumnName();
                    break;
                }
            }
        }
        if (columnName == null)
        {
            throw new IllegalStateException("Could not find the unique name column during import");
        }

        String insertSQL = "INSERT INTO exp.Material (LSID, Name, Container, CpasType, Created)  \n" +
                "SELECT " + info.getTempTableName() + ".LSID, " + info.getTempTableName() + "." + columnName +
                ", ?, ?, ? FROM " + info.getTempTableName() + "\nLEFT OUTER JOIN exp.Material ON\n" +
                info.getTempTableName() + ".LSID = exp.Material.LSID WHERE exp.Material.RowId IS NULL\n" +
                "GROUP BY " + info.getTempTableName() + ".LSID, " + info.getTempTableName() + "." + columnName;

        String deleteSQL = "DELETE FROM exp.Material WHERE RowId IN (SELECT exp.Material.RowId FROM exp.Material \n" +
                "LEFT OUTER JOIN " + info.getTempTableName() + " ON\n" +
                "\texp.Material.LSID = " + info.getTempTableName() + ".LSID\n" +
                "WHERE " + info.getTempTableName() + ".LSID IS NULL\n" +
                "AND (exp.Material.CpasType = ? OR exp.Material.CpasType = 'StudySpecimen') \n" +
                "AND exp.Material.Container = ? AND " +
                "exp.Material.RowId NOT IN (SELECT MaterialId FROM exp.MaterialInput))";

        String prefix = new Lsid("StudySpecimen", "Folder-" + container.getRowId(), "").toString();

        String cpasType;

        String name = "Study Specimens";
        ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(container, name);
        if (sampleSet == null)
        {
            ExpSampleSet source = ExperimentService.get().createSampleSet();
            source.setContainer(container);
            source.setMaterialLSIDPrefix(prefix);
            source.setName(name);
            source.setLSID(ExperimentService.get().getSampleSetLsid(name, container).toString());
            source.setDescription("Study specimens for " + container.getPath());
            source.insert(null);
            cpasType = source.getLSID();
        }
        else
        {
            cpasType = sampleSet.getLSID();
        }

        Timestamp createdTimestamp = new Timestamp(System.currentTimeMillis());

        try
        {
            info("exp.Material: Deleting entries for removed specimens...");
            SQLFragment deleteFragment = new SQLFragment(deleteSQL, cpasType, container.getId());
            if (DEBUG)
                logSQLFragment(deleteFragment);
            int affected = Table.execute(schema, deleteFragment);
            if (affected >= 0)
                info("exp.Material: " + affected + " rows removed.");
            info("exp.Material: Inserting new entries from temp table...");
            SQLFragment insertFragment = new SQLFragment(insertSQL, container.getId(), cpasType, createdTimestamp);
            if (DEBUG)
                logSQLFragment(insertFragment);
            affected = Table.execute(schema, insertFragment);
            if (affected >= 0)
                info("exp.Material: " + affected + " rows inserted.");
            info("exp.Material: Update complete.");
        }
        finally
        {
            assert cpuPopulateMaterials.stop();
        }
    }

    private boolean validateImportData(DbSchema schema, String tempTable) throws SQLException
    {
        try
        {
            info("Specimens: Starting data integrity check.");

            // check for vials at two locations at once, but ignore the return value intentionally;
            // it's possible that a shipping lab will delay their data export until after a receiving
            // lab has completed theirs, opening a window when duplicate locations can occur during
            // normal operations.
            if (!validateCurrentLocations(schema, tempTable))
                info("Ignoring duplicate location errors: import will continue");

            // don't bother to check for denormalized data problems if the request consistency check fails;
            // since the requestable check is just a more specific version of the general check.  (e.g., the
            // denormalization will always fail if the requestable check has failed.)
            return  (validateRequestableConsistency(schema, tempTable) &&
                     validateDenormalizedData(schema, tempTable));
        }
        finally
        {
            info("Specimens: Data integrity check complete.");
        }
    }

    private boolean validateCurrentLocations(DbSchema schema, String tempTable) throws SQLException
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT GlobalUniqueId, COUNT(GlobalUniqueId) as DupCount FROM\n");
        sql.append(tempTable).append(" WHERE ShipDate IS NULL AND\n");
        sql.append("(ShipBatchNumber IS NULL OR ShipBatchNumber = 0) AND\n");
        sql.append("(ShipFlag IS NULL OR ShipFlag = 0)\n");
        sql.append("GROUP BY GlobalUniqueId HAVING COUNT(GlobalUniqueId) > 1");
        info("Checking for specimens that appear to be in two locations at once...");
        return duplicateCheck(schema, sql);
    }

    private boolean validateRequestableConsistency(DbSchema schema, String tempTable) throws SQLException
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT GlobalUniqueId, COUNT(GlobalUniqueId) as DupCount FROM\n");
        sql.append("(SELECT DISTINCT GlobalUniqueId, Requestable from ").append(tempTable).append(") as DistinctValues\n");
        sql.append("GROUP BY GlobalUniqueId HAVING COUNT(GlobalUniqueId) > 1");
        info("Checking for specimens where events have inconsistent 'requestable' flags...");
        return duplicateCheck(schema, sql);
    }


    private boolean validateDenormalizedData(DbSchema schema, String tempTable) throws SQLException
    {
        StringBuilder columnList = new StringBuilder();
        boolean first = true;
        for (SpecimenColumn col : SPECIMEN_COLUMNS)
        {
            if (col.getTargetTable() == TargetTable.SPECIMENS)
            {
                if (!first)
                    columnList.append(",\n    ");
                else
                    first = false;
                columnList.append(tempTable).append(".").append(col.getDbColumnName());
            }
        }
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT GlobalUniqueId, Count(GlobalUniqueId) As DupCount FROM (\n");
        sql.append("SELECT DISTINCT\n").append(columnList).append("\nFROM ").append(tempTable).append("\nGROUP BY\n").append(columnList);
        sql.append(") AS DupCheckView\nGROUP BY GlobalUniqueId HAVING Count(GlobalUniqueId) > 1");
        info("Checking for specimens where fixed properties (primary type, participant id, etc.) have changed between events...");
        return duplicateCheck(schema, sql);
    }

    private boolean duplicateCheck(DbSchema schema, SQLFragment sql) throws SQLException
    {
        if (DEBUG)
            info(sql.toString());
        ResultSet rs = null;
        try
        {
            rs = Table.executeQuery(schema, sql);
            boolean dupsFound = false;
            while (rs.next())
            {
                info("    Validation failed for Global Unique ID " + rs.getString("GlobalUniqueId"));
                dupsFound = true;
            }
            return !dupsFound;
        }
        finally
        {
            if (rs != null) try { rs.close(); } catch (SQLException e) {}
        }
    }


    private void populateSpecimens(DbSchema schema, Container container, SpecimenLoadInfo info) throws SQLException
    {
        if (!validateImportData(schema, info.getTempTableName()))
            throw new RuntimeException("Data validation failed: upload aborted");

        StringBuilder columnList = new StringBuilder();
        columnList.append("exp.Material.RowId,\n    ").append(info.getTempTableName()).append(".Container");
        for (SpecimenColumn col : info.getAvailableColumns())
        {
            if (col.getTargetTable() == TargetTable.SPECIMENS)
            {
                if (col.getFkTable() == null)
                    columnList.append(",\n    ").append(info.getTempTableName()).append(".").append(col.getDbColumnName());
                else
                    columnList.append(",\n    ").append("study.").append(col.getFkTable()).append(".RowId");
            }
        }

        SQLFragment insertSql = new SQLFragment();
        insertSql.append("INSERT INTO study.Specimen \n(RowId, Container, ");
        insertSql.append(getSpecimenCols(info.getAvailableColumns())).append(")\n");
        insertSql.append("SELECT DISTINCT ").append(columnList.toString());
        insertSql.append("\nFROM ").append(info.getTempTableName()).append("\n    JOIN exp.Material ON (");
        insertSql.append(info.getTempTableName()).append(".LSID = exp.Material.LSID");
        insertSql.append(" AND exp.Material.Container = ?)");
        insertSql.add(container.getId());

        for (SpecimenColumn col : info.getAvailableColumns())
        {
            if (col.getTargetTable() == TargetTable.SPECIMENS && col.getFkTable() != null)
            {
                insertSql.append("\n    JOIN study.").append(col.getFkTable()).append(" ON ");
                insertSql.append("(").append(info.getTempTableName()).append(".").append(col.getDbColumnName()).append(" = ").append(" study.").append(col.getFkTable()).append(".").append(col.getFkColumn());
                insertSql.append(" AND study.").append(col.getFkTable()).append(".Container").append(" = ?)");
                insertSql.add(container.getId());
            }
        }

        if (DEBUG)
            logSQLFragment(insertSql);
        assert cpuInsertSpecimens.start();
        info("Specimens: Inserting new rows...");
        Table.execute(schema, insertSql);
        info("Specimens: Insert complete.");
        assert cpuInsertSpecimens.stop();
    }

    private void logSQLFragment(SQLFragment sql)
    {
        info(sql.toString());
        info("Params: ");
        for (Object param : sql.getParams())
            info(param.toString());
    }
    private void populateSpecimenEvents(DbSchema schema, Container container, SpecimenLoadInfo info) throws SQLException
    {
        String insertSql = "INSERT INTO study.SpecimenEvent\n" +
                "(Container, SpecimenId, LabId, " + getSpecimenEventCols(info.getAvailableColumns()) + ")\n" +
                "SELECT TempTable.Container, TempTable.SpecimenId, TempTable.CpasLabId As LabId, \n" +
                getSpecimenEventCols(info.getAvailableColumns()) + " FROM (\n" +
                "-- join the temp table to study.Site, to translate CHAVI site ids into LabKey Server site ids:\n" +
                "SELECT " + info.getTempTableName() + ".*, study.Site.RowId AS CpasLabId, study.Specimen.RowId As SpecimenId \n" +
                "FROM " + info.getTempTableName() + ", study.Site, study.Specimen\n" +
                "WHERE study.Site.ScharpId = " + info.getTempTableName() + ".LabId AND study.Site.Container = ? AND\n" +
                info.getTempTableName() + ".GlobalUniqueId = study.Specimen.GlobalUniqueId AND study.Specimen.Container = ?\n" +
                ") TempTable;";

        Object[] params = new Object[]{container.getId(), container.getId()};
        if (DEBUG)
        {
            info(insertSql);
            info("Params: ");
            for (Object param : params)
                info(param.toString());
        }
        assert cpuInsertSpecimenEvents.start();
        info("Specimen Events: Inserting new rows.");
        Table.execute(schema, insertSql, params);
        info("Specimen Events: Insert complete.");
        assert cpuInsertSpecimenEvents.stop();
    }

    private void mergeTable(DbSchema schema, Container container, String tableName, ImportableColumn[] potentialColumns, String tsv, boolean addEntityId) throws IOException, SQLException
    {
        if (tsv == null || tsv.length() == 0)
        {
            info(tableName + ": no data to merge.");
            return;
        }

        Map[] maps = loadTsv(potentialColumns, tsv, tableName);
        mergeTable(schema, container, tableName, potentialColumns, maps, addEntityId);
    }

    private void mergeTable(DbSchema schema, Container container, String tableName, ImportableColumn[] potentialColumns, Map[] maps, boolean addEntityId) throws IOException, SQLException
    {
        assert cpuMergeTable.start();
        info(tableName + ": Starting merge of data...");
        List<ImportableColumn> availableColumns = new ArrayList<ImportableColumn>();
        if (maps.length > 0)
        {
            for (ImportableColumn column : potentialColumns)
            {
                if (maps[0].containsKey(column.getTsvColumnName()))
                    availableColumns.add(column);
            }
        }

        List<ImportableColumn> uniqueCols = new ArrayList<ImportableColumn>();

        for (ImportableColumn col : availableColumns)
        {
            if (col.isUnique())
                uniqueCols.add(col);
        }

        StringBuilder selectSql = new StringBuilder();
        selectSql.append("SELECT * FROM study.").append(tableName).append(" WHERE Container = ? ");
        for (ImportableColumn col : uniqueCols)
            selectSql.append(" AND ").append(col.getDbColumnName()).append(" = ? ");

        StringBuilder insertSql = new StringBuilder();
        insertSql.append("INSERT INTO study.").append(tableName).append(" (Container");
        if (addEntityId)
            insertSql.append(", EntityId");
        for (ImportableColumn col : availableColumns)
            insertSql.append(", ").append(col.getDbColumnName());
        insertSql.append(") VALUES (?");
        if (addEntityId)
            insertSql.append(", ?");
        for (ImportableColumn col : availableColumns)
            insertSql.append(", ?");
        insertSql.append(")");

        StringBuilder updateSql = new StringBuilder();
        updateSql.append("UPDATE study.").append(tableName).append(" SET ");
        String separator = "";
        for (ImportableColumn col : availableColumns)
        {
            if (!col.isUnique())
            {
                updateSql.append(separator).append(col.getDbColumnName()).append(" = ?");
                separator = ", ";
            }
        }
        updateSql.append(" WHERE Container = ?");
        separator = " AND ";
        for (ImportableColumn col : availableColumns)
        {
            if (col.isUnique())
                updateSql.append(separator).append(col.getDbColumnName()).append(" = ?");
        }

        int rowsAdded = 0;
        int rowsUpdated = 0;
        for (Map row : maps)
        {
            boolean rowExists = false;
            if (!uniqueCols.isEmpty())
            {
                ResultSet rs = null;
                try
                {
                    Object[] params = new Object[uniqueCols.size() + 1];
                    int colIndex = 0;
                    params[colIndex++] = container.getId();
                    for (ImportableColumn col : uniqueCols)
                        params[colIndex++] = getValue(col, row);

                    rs = Table.executeQuery(schema, selectSql.toString(), params);
                    if (rs.next())
                        rowExists = true;
                }
                finally
                {
                    if (rs != null) try { rs.close(); } catch (SQLException e) {}
                }
            }

            if (!rowExists)
            {
                Object[] params = new Object[availableColumns.size() + 1 + (addEntityId ? 1 : 0)];
                int colIndex = 0;
                params[colIndex++] = container.getId();
                if (addEntityId)
                    params[colIndex++] = GUID.makeGUID();
                for (ImportableColumn col : availableColumns)
                    params[colIndex++] = getValue(col, row);
                Table.execute(schema, insertSql.toString(), params);
                rowsAdded++;
            }
            else
            {
                List<Object> params = new ArrayList<Object>();
                for (ImportableColumn col : availableColumns)
                {
                    if (!col.isUnique())
                        params.add(getValue(col, row));
                }
                params.add(container.getId());

                for (ImportableColumn col : uniqueCols)
                    params.add(getValue(col, row));
                Table.execute(schema, updateSql.toString(), params.toArray());
                rowsUpdated++;
            }
        }
        info(tableName + ": inserted " + rowsAdded + " new rows, updated " + rowsUpdated + " rows.  (" + maps.length + " rows found in input file.)");
        assert cpuMergeTable.stop();
    }


    private void replaceTable(DbSchema schema, Container container, String tableName, ImportableColumn[] potentialColumns, String tsv, boolean addEntityId) throws IOException, SQLException
    {
        if (tsv == null || tsv.length() == 0)
        {
            info(tableName + ": no data to merge.");
            return;
        }

        Map[] maps = loadTsv(potentialColumns, tsv, tableName);
        replaceTable(schema, container, tableName, potentialColumns, maps, addEntityId);
    }

    private void replaceTable(DbSchema schema, Container container, String tableName, ImportableColumn[] potentialColumns, Map[] maps, boolean addEntityId) throws IOException, SQLException
    {
        assert cpuMergeTable.start();
        info(tableName + ": Starting replacement of all data...");

        Table.execute(schema, "DELETE FROM study." + tableName + " WHERE Container = ?", new Object[] { container.getId() });
        List<ImportableColumn> availableColumns = new ArrayList<ImportableColumn>();
        if (maps.length > 0)
        {
            for (ImportableColumn column : potentialColumns)
            {
                if (maps[0].containsKey(column.getTsvColumnName()))
                    availableColumns.add(column);
            }
        }

        StringBuilder insertSql = new StringBuilder();
        insertSql.append("INSERT INTO study.").append(tableName).append(" (Container");
        if (addEntityId)
            insertSql.append(", EntityId");
        for (ImportableColumn col : availableColumns)
            insertSql.append(", ").append(col.getDbColumnName());
        insertSql.append(") VALUES (?");
        if (addEntityId)
            insertSql.append(", ?");
        for (ImportableColumn col : availableColumns)
            insertSql.append(", ?");
        insertSql.append(")");

        List<Object[]> rows = new ArrayList<Object[]>();
        for (Map row : maps)
        {
            Object[] params = new Object[availableColumns.size() + 1 + (addEntityId ? 1 : 0)];
            int colIndex = 0;
            params[colIndex++] = container.getId();
            if (addEntityId)
                params[colIndex++] = GUID.makeGUID();
            for (ImportableColumn col : availableColumns)
                params[colIndex++] = getValue(col, row);
            rows.add(params);
        }
        Table.batchExecute(schema, insertSql.toString(), rows);
        info(tableName + ": Replaced all data with " + rows.size() + " new rows.  (" + maps.length + " rows found in input file.)");
        assert cpuMergeTable.stop();
    }

    private Map[] loadTsv(ImportableColumn[] columns, String tsv, String tableName) throws IOException
    {
        info(tableName + ": Parsing data file for table...");
        Map<String, TabLoader.ColumnDescriptor> expectedColumns = new HashMap<String, TabLoader.ColumnDescriptor>(columns.length);
        for (ImportableColumn col : columns)
            expectedColumns.put(col.getTsvColumnName().toLowerCase(), col.getColumnDescriptor());

        TabLoader loader = new TabLoader(tsv, true);
        for (TabLoader.ColumnDescriptor column : loader.getColumns())
        {
            TabLoader.ColumnDescriptor expectedColumnDescriptor = expectedColumns.get(column.name.toLowerCase());
            if (expectedColumnDescriptor != null)
                column.clazz = expectedColumnDescriptor.clazz;
            else
                column.load = false;
        }
        info(tableName + ": Parsing complete.");
        return (Map[]) loader.load();
    }

    private List<SpecimenColumn> populateTempTable(DbSchema schema, Container container, String tempTable, String tsv) throws SQLException, IOException
    {
        assert cpuPopulateTempTable.start();
        Map[] maps = loadTsv(SPECIMEN_COLUMNS, tsv, "Specimen");
        return populateTempTable(schema, container, tempTable, maps);
    }

    private List<SpecimenColumn> populateTempTable(DbSchema schema, Container container, String tempTable, Map[] maps) throws SQLException, IOException
    {
        assert cpuPopulateTempTable.start();
        try
        {
            info("Populating temp table...");
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("INSERT INTO ").append(tempTable).append("\n(\n    Container,\n    LSID");
            for (ImportableColumn col : SPECIMEN_COLUMNS)
                sqlBuilder.append(",\n     ").append(col.getDbColumnName());
            sqlBuilder.append("\n) values (?, ?");
            for (ImportableColumn col : SPECIMEN_COLUMNS)
                sqlBuilder.append(", ?");
            sqlBuilder.append(")");

            List<Object[]> rows = new ArrayList<Object[]>(SQL_BATCH_SIZE);
            String sql = sqlBuilder.toString();
            if (DEBUG)
            {
                info(sql);
            }
            for (Map properties : maps)
            {
                String id = (String) properties.get(GLOBAL_UNIQUE_ID_TSV_COL);
                if (id == null)
                    id = (String) properties.get(SPEC_NUMBER_TSV_COL);
                Lsid lsid = SpecimenService.get().getSpecimenMaterialLsid(container, id);

                Object[] params = new Object[SPECIMEN_COLUMNS.length + 2];
                int idx = 0;
                params[idx++] = container.getId();
                params[idx++] = lsid.toString();
                for (ImportableColumn col : SPECIMEN_COLUMNS)
                    params[idx++] = getValue(col, properties);
                rows.add(params);
                if (rows.size() == SQL_BATCH_SIZE)
                {
                    Table.batchExecute(schema, sql, rows);
                    rows = new ArrayList<Object[]>(SQL_BATCH_SIZE);
                }
            }
            if (!rows.isEmpty())
                Table.batchExecute(schema, sql, rows);
        }
        finally
        {
            assert cpuPopulateTempTable.stop();
        }

        List<SpecimenColumn> loadedColumns = new ArrayList<SpecimenColumn>();
        if (maps.length > 0)
        {
            Map row = maps[0];
            for (SpecimenColumn column : SPECIMEN_COLUMNS)
            {
                if (row.containsKey(column.getTsvColumnName()))
                    loadedColumns.add(column);
            }
        }
        info("Temp table populated.");
        return loadedColumns;
    }

    private Object getValue(ImportableColumn col, Map tsvRow)
    {
        Object value = tsvRow.get(col.getTsvColumnName());
        if (value == null)
            return Parameter.nullParameter(col.getSQLType());
        return value;
    }

    private static final boolean DEBUG = false;

    private String createTempTable(DbSchema schema) throws SQLException
    {
        assert cpuCreateTempTable.start();
        try
        {
            info("Creating temp table to hold archive data...");
            SqlDialect dialect = StudySchema.getInstance().getSqlDialect();
            String tableName;
            StringBuilder sql = new StringBuilder();
            int randomizer = (new Random().nextInt(1000000000));
            if (DEBUG)
            {
                tableName = dialect.getGlobalTempTablePrefix() + "SpecimenUpload" + randomizer;
                sql.append("CREATE TABLE ").append(tableName);
            }
            else
            {
                tableName = dialect.getTempTablePrefix() + "SpecimenUpload" + randomizer;
                sql.append("CREATE ").append(dialect.getTempTableKeyword()).append(" TABLE ").append(tableName);
            }
            sql.append("\n(\n    Container VARCHAR(300) NOT NULL,\n    LSID VARCHAR(300) NOT NULL");
            for (SpecimenColumn col : SPECIMEN_COLUMNS)
                sql.append(",\n    ").append(col.getDbColumnName()).append(" ").append(col.getDbType());
            sql.append("\n);");
            if (DEBUG)
                info(sql.toString());
            Table.execute(schema, sql.toString(), null);

            String lsidIndexSql = "CREATE INDEX IX_SpecimenUpload" + randomizer + "_LSID ON " + tableName + "(LSID)";
            if (DEBUG)
                info(lsidIndexSql);
            Table.execute(schema, lsidIndexSql, null);
            return tableName;
        }
        finally
        {
            assert cpuCreateTempTable.stop();
        }
    }
}
