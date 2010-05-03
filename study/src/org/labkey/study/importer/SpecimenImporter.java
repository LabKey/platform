/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.util.*;
import org.labkey.study.SampleManager;
import org.labkey.study.StudySchema;
import org.labkey.study.model.*;
import org.labkey.study.visitmanager.VisitManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.*;

/**
 * User: brittp
 * Date: Mar 13, 2006
 * Time: 2:18:48 PM
 */
public class SpecimenImporter
{
    private final CPUTimer cpuPopulateMaterials = new CPUTimer("populateMaterials");
    private final CPUTimer cpuUpdateSpecimens = new CPUTimer("updateSpecimens");
    private final CPUTimer cpuInsertSpecimens = new CPUTimer("insertSpecimens");
    private final CPUTimer cpuUpdateSpecimenEvents = new CPUTimer("updateSpecimenEvents");
    private final CPUTimer cpuInsertSpecimenEvents = new CPUTimer("insertSpecimenEvents");
    private final CPUTimer cpuMergeTable = new CPUTimer("mergeTable");
    private final CPUTimer cpuCreateTempTable = new CPUTimer("createTempTable");
    private final CPUTimer cpuPopulateTempTable = new CPUTimer("populateTempTable");
    private final CPUTimer cpuCurrentLocations = new CPUTimer("updateCurrentLocations");


    public static class ImportableColumn
    {
        private final String _tsvColumnName;
        protected final String _dbType;
        private final String _dbColumnName;
        private Class _javaType = null;
        private final boolean _unique;

        public ImportableColumn(String tsvColumnName, String dbColumnName, String databaseType)
        {
            this(tsvColumnName, dbColumnName, databaseType, false);
        }

        public ImportableColumn(String tsvColumnName, String dbColumnName, String databaseType, boolean unique)
        {
            _tsvColumnName = tsvColumnName;
            _dbColumnName = dbColumnName;
            _unique = unique;
            if (DURATION_TYPE.equals(databaseType))
            {
                _dbType = StudySchema.getInstance().getSqlDialect().getDefaultDateTimeDatatype();
                _javaType = TimeOnlyDate.class;
            }
            else if (DATETIME_TYPE.equals(databaseType))
            {
                _dbType = StudySchema.getInstance().getSqlDialect().getDefaultDateTimeDatatype();
                _javaType = java.util.Date.class;
            }
            else
                _dbType = databaseType.toUpperCase();
        }


        public ColumnDescriptor getColumnDescriptor()
        {
            return new ColumnDescriptor(_tsvColumnName, getJavaType());
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
                    throw new IllegalStateException("Java types for DateTime/Timestamp columns should be previously initalized.");
                else if (_dbType.indexOf("FLOAT") >= 0 || _dbType.indexOf(NUMERIC_TYPE) >= 0)
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
            if (getJavaType() == String.class)
                return Types.VARCHAR;
            else if (getJavaType() == java.util.Date.class)
                return Types.TIMESTAMP;
            else if (getJavaType() == TimeOnlyDate.class)
                return Types.TIMESTAMP;
            else if (getJavaType() == Float.class)
                return Types.FLOAT;
            else if (getJavaType() == Integer.class)
                return Types.INTEGER;
            else if (getJavaType() == Boolean.class)
                return Types.BIT;
            else
                throw new UnsupportedOperationException("SQL type has not been defined for DB type " + _dbType + ", java type " + getJavaType());
        }
    }

    public enum TargetTable
    {
        SPECIMEN_EVENTS
        {
            public String getName()
            {
                return "SpecimenEvent";
            }

            public boolean isEvents()
            {
                return true;
            }

            public boolean isSpecimens()
            {
                return false;
            }

            public boolean isVials()
            {
                return false;
            }},
        SPECIMENS
        {
            public String getName()
            {
                return "Specimen";
            }

            public boolean isEvents()
            {
                return false;
            }

            public boolean isSpecimens()
            {
                return true;
            }

            public boolean isVials()
            {
                return false;
            }},
        VIALS
        {
            public String getName()
            {
                return "Vial";
            }

            public boolean isEvents()
            {
                return false;
            }

            public boolean isSpecimens()
            {
                return false;
            }

            public boolean isVials()
            {
                return true;
            }
        },
        SPECIMENS_AND_SPECIMEN_EVENTS
        {
            public String getName()
            {
                return "Specimen";
            }

            public boolean isEvents()
            {
                return true;
            }

            public boolean isSpecimens()
            {
                return true;
            }

            public boolean isVials()
            {
                return false;
            }
        },
        VIALS_AND_SPECIMEN_EVENTS
        {
            public String getName()
            {
                return "Vials";
            }

            public boolean isEvents()
            {
                return true;
            }

            public boolean isSpecimens()
            {
                return false;
            }

            public boolean isVials()
            {
                return true;
            }};

        public abstract boolean isEvents();
        public abstract boolean isVials();
        public abstract boolean isSpecimens();
        public abstract String getName();
    }

    public static class SpecimenColumn extends ImportableColumn
    {
        private final TargetTable _targetTable;
        private String _fkTable;
        private String _joinType;
        private String _fkColumn;
        private String _aggregateEventFunction;

        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType, TargetTable eventColumn, boolean unique)
        {
            super(tsvColumnName, dbColumnName, databaseType, unique);
            _targetTable = eventColumn;
        }

        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType, TargetTable eventColumn)
        {
            this(tsvColumnName, dbColumnName, databaseType, eventColumn, false);
        }

        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType, TargetTable eventColumn, String aggregateEventFunction)
        {
            this(tsvColumnName, dbColumnName, databaseType, eventColumn, false);
            _aggregateEventFunction = aggregateEventFunction;
        }

        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType,
                              TargetTable eventColumn, String fkTable, String fkColumn)
        {
            this(tsvColumnName, dbColumnName, databaseType, eventColumn, fkTable, fkColumn, "INNER");
        }

        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType,
                              TargetTable eventColumn, String fkTable, String fkColumn, String joinType)
        {
            this(tsvColumnName, dbColumnName, databaseType, eventColumn, false);
            _fkColumn = fkColumn;
            _fkTable = fkTable;
            _joinType = joinType;
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

        public String getJoinType()
        {
            return _joinType;
        }

        public String getDbType()
        {
            return _dbType;
        }

        public String getAggregateEventFunction()
        {
            return _aggregateEventFunction;
        }

        public String getFkTableAlias()
        {
            return getDbColumnName() + "Lookup";
        }
    }

    private static class SpecimenLoadInfo
    {
        private final String _tempTableName;
        private final List<SpecimenColumn> _availableColumns;
        private final Container _container;
        private final User _user;
        private final DbSchema _schema;

        public SpecimenLoadInfo(User user, Container container, DbSchema schema, List<SpecimenColumn> availableColumns, String tempTableName)
        {
            _user = user;
            _schema = schema;
            _container = container;
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

        public Container getContainer()
        {
            return _container;
        }

        public User getUser()
        {
            return _user;
        }

        public DbSchema getSchema()
        {
            return _schema;
        }
    }

    private static final String DATETIME_TYPE = "SpecimenImporter/DateTime";
    private static final String DURATION_TYPE = "SpecimenImporter/TimeOnlyDate";
    private static final String NUMERIC_TYPE = "NUMERIC(15,4)";
    private static final String BOOLEAN_TYPE = StudySchema.getInstance().getSqlDialect().getBooleanDatatype();
    private static final String GLOBAL_UNIQUE_ID_TSV_COL = "global_unique_specimen_id";
    private static final String LAB_ID_TSV_COL = "lab_id";
    private static final String SPEC_NUMBER_TSV_COL = "specimen_number";
    private static final String EVENT_ID_COL = "record_id";

    public static final SpecimenColumn[] SPECIMEN_COLUMNS = {
            new SpecimenColumn(EVENT_ID_COL, "ExternalId", "INT NOT NULL", TargetTable.SPECIMEN_EVENTS, true),
            new SpecimenColumn("record_source", "RecordSource", "VARCHAR(10)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn(GLOBAL_UNIQUE_ID_TSV_COL, "GlobalUniqueId", "VARCHAR(20)", TargetTable.VIALS),
            new SpecimenColumn(LAB_ID_TSV_COL, "LabId", "INT", TargetTable.SPECIMEN_EVENTS, "Site", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("originating_location", "OriginatingLocationId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "Site", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("unique_specimen_id", "UniqueSpecimenId", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("ptid", "Ptid", "VARCHAR(32)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("parent_specimen_id", "ParentSpecimenId", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("draw_timestamp", "DrawTimestamp", DATETIME_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("sal_receipt_date", "SalReceiptDate", DATETIME_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn(SPEC_NUMBER_TSV_COL, "SpecimenNumber", "VARCHAR(50)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("class_id", "ClassId", "VARCHAR(4)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("visit_value", "VisitValue", NUMERIC_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("protocol_number", "ProtocolNumber", "VARCHAR(10)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("visit_description", "VisitDescription", "VARCHAR(3)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("other_specimen_id", "OtherSpecimenId", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("volume", "Volume", "FLOAT", TargetTable.VIALS_AND_SPECIMEN_EVENTS, "MAX"),
            new SpecimenColumn("volume_units", "VolumeUnits", "VARCHAR(3)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
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
            new SpecimenColumn("sub_additive_derivative", "SubAdditiveDerivative", "VARCHAR(20)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("comments", "Comments", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("primary_specimen_type_id", "PrimaryTypeId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "SpecimenPrimaryType", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("derivative_type_id", "DerivativeTypeId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "SpecimenDerivative", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("derivative_type_id_2", "DerivativeTypeId2", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "SpecimenDerivative", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("additive_type_id", "AdditiveTypeId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "SpecimenAdditive", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("specimen_condition", "SpecimenCondition", "VARCHAR(3)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("sample_number", "SampleNumber", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("x_sample_origin", "XSampleOrigin", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("external_location", "ExternalLocation", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("update_timestamp", "UpdateTimestamp", DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("requestable", "Requestable", BOOLEAN_TYPE, TargetTable.VIALS),
            new SpecimenColumn("freezer", "freezer", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("fr_level1", "fr_level1", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("fr_level2", "fr_level2", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("fr_container", "fr_container", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("fr_position", "fr_position", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("shipped_from_lab", "ShippedFromLab", "VARCHAR(32)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("shipped_to_lab", "ShippedtoLab", "VARCHAR(32)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("frozen_time", "FrozenTime", DURATION_TYPE, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("primary_volume", "PrimaryVolume", "FLOAT", TargetTable.VIALS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("primary_volume_units", "PrimaryVolumeUnits", "VARCHAR(20)", TargetTable.VIALS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("processed_by_initials", "ProcessedByInitials", "VARCHAR(32)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("processing_date", "ProcessingDate", DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("processing_time", "ProcessingTime", DURATION_TYPE, TargetTable.SPECIMEN_EVENTS)
        };

    public static final ImportableColumn[] ADDITIVE_COLUMNS = new ImportableColumn[]
        {
            new ImportableColumn("additive_id", "ExternalId", "INT NOT NULL", true),
            new ImportableColumn("ldms_additive_code", "LdmsAdditiveCode", "VARCHAR(3)"),
            new ImportableColumn("labware_additive_code", "LabwareAdditiveCode", "VARCHAR(20)"),
            new ImportableColumn("additive", "Additive", "VARCHAR(100)")
        };

    public static final ImportableColumn[] DERIVATIVE_COLUMNS = new ImportableColumn[]
        {
            new ImportableColumn("derivative_id", "ExternalId", "INT NOT NULL", true),
            new ImportableColumn("ldms_derivative_code", "LdmsDerivativeCode", "VARCHAR(3)"),
            new ImportableColumn("labware_derivative_code", "LabwareDerivativeCode", "VARCHAR(20)"),
            new ImportableColumn("derivative", "Derivative", "VARCHAR(100)")
        };

    public static final ImportableColumn[] SITE_COLUMNS = new ImportableColumn[]
        {
            new ImportableColumn("lab_id", "ExternalId", "INT NOT NULL", true),
            new ImportableColumn("ldms_lab_code", "LdmsLabCode", "INT"),
            new ImportableColumn("labware_lab_code", "LabwareLabCode", "VARCHAR(20)"),
            new ImportableColumn("lab_name", "Label", "VARCHAR(200)"),
            new ImportableColumn("lab_upload_code", "LabUploadCode", "VARCHAR(2)"),
            new ImportableColumn("is_sal", "Sal", BOOLEAN_TYPE),
            new ImportableColumn("is_repository", "Repository", BOOLEAN_TYPE),
            new ImportableColumn("is_clinic", "Clinic", BOOLEAN_TYPE),
            new ImportableColumn("is_endpoint", "Endpoint", BOOLEAN_TYPE)
        };

    public static final ImportableColumn[] PRIMARYTYPE_COLUMNS = new ImportableColumn[]
        {
            new ImportableColumn("primary_type_id", "ExternalId", "INT NOT NULL", true),
            new ImportableColumn("primary_type_ldms_code", "PrimaryTypeLdmsCode", "VARCHAR(5)"),
            new ImportableColumn("primary_type_labware_code", "PrimaryTypeLabwareCode", "VARCHAR(5)"),
            new ImportableColumn("primary_type", "PrimaryType", "VARCHAR(100)")
        };

    private String _specimenCols;
    private String _vialCols;
    private String _vialEventCols;
    private Logger _logger;

    private static final int SQL_BATCH_SIZE = 100;

    public void process(User user, Container container, List<File> files, Logger logger) throws SQLException, IOException
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        Map<String, File> fileMap = createFilemap(files);
        _logger = logger;

        try
        {
            DbScope scope = schema.getScope();
            if (!DEBUG)
                scope.beginTransaction();

            mergeTable(schema, container, "Site", SITE_COLUMNS, fileMap.get("labs"), true);

            replaceTable(schema, container, "SpecimenAdditive", ADDITIVE_COLUMNS, fileMap.get("additives"), false);
            replaceTable(schema, container, "SpecimenDerivative", DERIVATIVE_COLUMNS, fileMap.get("derivatives"), false);
            replaceTable(schema, container, "SpecimenPrimaryType", PRIMARYTYPE_COLUMNS, fileMap.get("primary_types"), false);

            // Specimen temp table must be populated AFTER the types tables have been reloaded, since the SpecimenHash
            // calculated in the temp table relies on the new RowIds for the types:
            SpecimenLoadInfo loadInfo = populateTempSpecimensTable(user, schema, container, fileMap.get("specimens"));

            // Columns will be empty in specimens.tsv has no data
            if (!loadInfo.getAvailableColumns().isEmpty())
                populateSpecimenTables(loadInfo);
            else
                info("Specimens: 0 rows found in input file.");

            cpuCurrentLocations.start();
            updateCalculatedSpecimenData(container, user, _logger);
            cpuCurrentLocations.stop();
            info("Time to determine locations: " + cpuCurrentLocations.toString());

            resyncStudy(user, container);

            // Drop the temp table within the transaction; otherwise, we may get a different connection object,
            // where the table is no longer available.  Note that this means that the temp table will stick around
            // if an exception is throw during loading, but this is probably okay- the DB will clean it up eventually.
            Table.execute(schema, "DROP TABLE " + loadInfo.getTempTableName(), null);

            if (!DEBUG)
                scope.commitTransaction();

            updateStatistics(ExperimentService.get().getTinfoMaterial());
            updateStatistics(StudySchema.getInstance().getTableInfoSpecimen());
            updateStatistics(StudySchema.getInstance().getTableInfoVial());
            updateStatistics(StudySchema.getInstance().getTableInfoSpecimenEvent());
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

    private void resyncStudy(User user, Container container) throws SQLException
    {
        TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
        TableInfo tableSpecimen = StudySchema.getInstance().getTableInfoSpecimen();

        Table.execute(tableParticipant.getSchema(),
                "INSERT INTO " + tableParticipant + " (container, participantid)\n" +
                "SELECT DISTINCT ?, ptid AS participantid\n" +
                "FROM " + tableSpecimen + "\n"+
                "WHERE container = ? AND ptid IS NOT NULL AND " +
                "ptid NOT IN (select participantid from " + tableParticipant + " where container = ?)",
                new Object[] {container, container, container});

        StudyImpl study = StudyManager.getInstance().getStudy(container);
        StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user, Collections.<DataSetDefinition>emptyList());
    }

    private boolean updateStatistics(TableInfo tinfo) throws SQLException
    {
        info("Updating statistics for " + tinfo + "...");
        boolean updated = tinfo.getSqlDialect().updateStatistics(tinfo);
        if (updated)
            info("Statistics update " + tinfo + " complete.");
        else
            info("Statistics update not supported for this database type.");
        return updated;
    }

    protected void process(User user, Container container, Map<String, CloseableIterator<Map<String, Object>>> iterMap, Logger logger) throws SQLException, IOException
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        _logger = logger;

        try
        {
            DbScope scope = schema.getScope();
            if (!DEBUG)
                scope.beginTransaction();

            mergeTable(schema, container, "Site", SITE_COLUMNS, iterMap.get("labs"), true);
            replaceTable(schema, container, "SpecimenAdditive", ADDITIVE_COLUMNS, iterMap.get("additives"), false);
            replaceTable(schema, container, "SpecimenDerivative", DERIVATIVE_COLUMNS, iterMap.get("derivatives"), false);
            replaceTable(schema, container, "SpecimenPrimaryType", PRIMARYTYPE_COLUMNS, iterMap.get("primary_types"), false);
            
            // Specimen temp table must be populated AFTER the types tables have been reloaded, since the SpecimenHash
            // calculated in the temp table relies on the new RowIds for the types:
            SpecimenLoadInfo loadInfo = populateTempSpecimensTable(user, schema, container, iterMap.get("specimens"));

            populateSpecimenTables(loadInfo);

            cpuCurrentLocations.start();
            updateCalculatedSpecimenData(container, user, _logger);
            cpuCurrentLocations.stop();
            info("Time to determine locations: " + cpuCurrentLocations.toString());

            resyncStudy(user, container);

            // Drop the temp table within the transaction; otherwise, we may get a different connection object,
            // where the table is no longer available.  Note that this means that the temp table will stick around
            // if an exception is throw during loading, but this is probably okay- the DB will clean it up eventually.
            Table.execute(schema, "DROP TABLE " + loadInfo.getTempTableName(), null);

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

    private SpecimenLoadInfo populateTempSpecimensTable(User user, DbSchema schema, Container container, File tsvFile) throws SQLException, IOException
    {
        String tempTable = createTempTable(schema);
        info("Created temporary table " + tempTable);
        List<SpecimenColumn> availableColumns = populateTempTable(schema, container, tempTable, tsvFile);
        return new SpecimenLoadInfo(user, container, schema, availableColumns, tempTable);
    }

    private SpecimenLoadInfo populateTempSpecimensTable(User user, DbSchema schema, Container container, CloseableIterator<Map<String, Object>> iter) throws SQLException, IOException
    {
        String tempTable = createTempTable(schema);
        List<SpecimenColumn> availableColumns = populateTempTable(schema, container, tempTable, iter);
        return new SpecimenLoadInfo(user, container, schema, availableColumns, tempTable);
    }


    private void populateSpecimenTables(SpecimenLoadInfo info) throws SQLException, IOException
    {
        SimpleFilter containerFilter = new SimpleFilter("Container", info.getContainer().getId());
        info("Deleting old data from Specimen Event table...");
        Table.delete(StudySchema.getInstance().getTableInfoSpecimenEvent(), containerFilter);
        info("Complete.");
        info("Deleting old data from Vial table...");
        Table.delete(StudySchema.getInstance().getTableInfoVial(), containerFilter);
        info("Complete.");
        info("Deleting old data from Specimen table...");
        Table.delete(StudySchema.getInstance().getTableInfoSpecimen(), containerFilter);
        info("Complete.");

        populateMaterials(info);
        populateSpecimens(info);
        populateVials(info);
        populateVialEvents(info);
    }

    private static final int CURRENT_SITE_UPDATE_SIZE = 1000;

    private static boolean safeIntegerEqual(Integer a, Integer b)
    {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;
        return a.intValue() == b.intValue();
    }

    public static void updateAllCalculatedSpecimenData(User user) throws SQLException
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        DbScope scope = schema.getScope();
        boolean transactionOwner = !scope.isTransactionActive();
        Study[] studies = StudyManager.getInstance().getAllStudies();
        Logger logger = Logger.getLogger(SpecimenImporter.class);
        for (Study study : studies)
        {
            try
            {
                if (transactionOwner)
                    scope.beginTransaction();
                updateCalculatedSpecimenData(study.getContainer(), user, logger);
                if (transactionOwner)
                    scope.commitTransaction();
            }
            finally
            {
                if (transactionOwner)
                    scope.closeConnection();
            }
        }
    }

    private static <T> boolean safeObjectEquals(T a, T b)
    {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;
        return a.equals(b);
    }


    private static Set<String> getConflictingEventColumns(SpecimenEvent[] events)
    {
        if (events.length <= 1)
            return Collections.emptySet();
        Set<String> conflicts = new HashSet<String>();

        try
        {
            for (SpecimenColumn col :  SPECIMEN_COLUMNS)
            {
                if (col.getAggregateEventFunction() == null && col.getTargetTable() == TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS)
                {
                    // lower the case of the first character:
                    String propName = col.getDbColumnName().substring(0, 1).toLowerCase() + col.getDbColumnName().substring(1);
                    for (int i = 0; i < events.length - 1; i++)
                    {
                        SpecimenEvent event = events[i];
                        SpecimenEvent nextEvent = events[i + 1];
                        Object currentValue = PropertyUtils.getProperty(event, propName);
                        Object nextValue = PropertyUtils.getProperty(nextEvent, propName);
                        if (!safeObjectEquals(currentValue, nextValue))
                        {
                            conflicts.add(col.getDbColumnName());
                        }
                    }
                }
            }
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
        catch (InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
        return conflicts;
    }

    private static void updateCommentSpecimenHashes(Container container, Logger logger) throws SQLException
    {
        SQLFragment sql = new SQLFragment();
        TableInfo commentTable = StudySchema.getInstance().getTableInfoSpecimenComment();
        TableInfo specimenTable = StudySchema.getInstance().getTableInfoSpecimenDetail();
        sql.append("UPDATE ").append(commentTable).append(" SET SpecimenHash = (\n");
        sql.append("SELECT SpecimenHash FROM ").append(specimenTable).append(" WHERE ").append(specimenTable);
        sql.append(".GlobalUniqueId = ").append(commentTable).append(".GlobalUniqueId AND ");
        sql.append(specimenTable).append(".Container = ?)\nWHERE ").append(commentTable).append(".Container = ?");
        sql.add(container.getId());
        sql.add(container.getId());
        logger.info("Updating hash codes for existing comments...");
        Table.execute(StudySchema.getInstance().getSchema(), sql);
        logger.info("Complete.");
    }

    private static void prepareQCComments(Container container, User user, Logger logger) throws SQLException
    {
        StringBuilder columnList = new StringBuilder();
        columnList.append("VialId");
        for (SpecimenColumn col : SPECIMEN_COLUMNS)
        {
            if (col.getTargetTable() == TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS && col.getAggregateEventFunction() == null)
            {
                columnList.append(",\n    ");
                columnList.append(col.getDbColumnName());
            }
        }

        // find the global unique ID for those vials with conflicts:
        SQLFragment conflictedGUIDs = new SQLFragment("SELECT GlobalUniqueId FROM ");
        conflictedGUIDs.append(StudySchema.getInstance().getTableInfoVial());
        conflictedGUIDs.append(" WHERE RowId IN (\n");
        conflictedGUIDs.append("SELECT VialId FROM\n");
        conflictedGUIDs.append("(SELECT DISTINCT\n").append(columnList).append("\nFROM ").append(StudySchema.getInstance().getTableInfoSpecimenEvent());
        conflictedGUIDs.append("\nWHERE Container = ?\nGROUP BY\n").append(columnList).append(") ");
        conflictedGUIDs.append("AS DupCheckView\nGROUP BY VialId HAVING Count(VialId) > 1");
        conflictedGUIDs.add(container.getId());
        conflictedGUIDs.append("\n)");

        // Delete comments that were holding QC state (and nothing more) for vials that do not currently have any conflicts
        SQLFragment deleteClearedVials = new SQLFragment("DELETE FROM study.SpecimenComment WHERE Container = ? ");
        deleteClearedVials.add(container.getId());
        deleteClearedVials.append("AND Comment IS NULL AND QualityControlFlag = ? ");
        deleteClearedVials.add(Boolean.TRUE);
        deleteClearedVials.append("AND QualityControlFlagForced = ? ");
        deleteClearedVials.add(Boolean.FALSE);
        deleteClearedVials.append("AND GlobalUniqueId NOT IN (").append(conflictedGUIDs).append(");");
        logger.info("Clearing QC flags for vials that no longer have history conflicts...");
        Table.execute(StudySchema.getInstance().getSchema(), deleteClearedVials);
        logger.info("Complete.");


        // Insert placeholder comments for newly discovered QC problems; SpecimenHash will be updated within updateCalculatedSpecimenData, so this
        // doesn't have to be set here.
        SQLFragment insertPlaceholderQCComments = new SQLFragment("INSERT INTO study.SpecimenComment ");
        insertPlaceholderQCComments.append("(GlobalUniqueId, Container, QualityControlFlag, QualityControlFlagForced, Created, CreatedBy, Modified, ModifiedBy) ");
        insertPlaceholderQCComments.append("SELECT GlobalUniqueId, ?, ?, ?, ?, ?, ?, ? ");
        insertPlaceholderQCComments.add(container.getId());
        insertPlaceholderQCComments.add(Boolean.TRUE);
        insertPlaceholderQCComments.add(Boolean.FALSE);
        insertPlaceholderQCComments.add(new Date());
        insertPlaceholderQCComments.add(user.getUserId());
        insertPlaceholderQCComments.add(new Date());
        insertPlaceholderQCComments.add(user.getUserId());
        insertPlaceholderQCComments.append(" FROM (\n").append(conflictedGUIDs).append(") ConflictedVials\n");
        insertPlaceholderQCComments.append("WHERE GlobalUniqueId NOT IN ");
        insertPlaceholderQCComments.append("(SELECT GlobalUniqueId FROM study.SpecimenComment WHERE Container = ?);");
        insertPlaceholderQCComments.add(container.getId());
        logger.info("Setting QC flags for vials that have new history conflicts...");
        Table.execute(StudySchema.getInstance().getSchema(), insertPlaceholderQCComments);
        logger.info("Complete.");
    }

    private static void markOrphanedRequestVials(Container container, User user, Logger logger) throws SQLException
    {
        // Mark those global unique IDs that are in requests but are no longer found in the vial table:
        SQLFragment orphanMarkerSql = new SQLFragment("UPDATE study.SampleRequestSpecimen SET Orphaned = ? WHERE RowId IN (\n" +
                "\tSELECT study.SampleRequestSpecimen.RowId FROM study.SampleRequestSpecimen\n" +
                "\tLEFT OUTER JOIN study.Vial ON\n" +
                "\t\tstudy.Vial.GlobalUniqueId = study.SampleRequestSpecimen.SpecimenGlobalUniqueId AND\n" +
                "\t\tstudy.Vial.Container = study.SampleRequestSpecimen.Container\n" +
                "\tWHERE study.Vial.GlobalUniqueId IS NULL AND\n" +
                "\t\tstudy.SampleRequestSpecimen.Container = ?);", Boolean.TRUE, container.getId());
        logger.info("Marking requested vials that have been orphaned...");
        Table.execute(StudySchema.getInstance().getSchema(), orphanMarkerSql);
        logger.info("Complete.");

        // un-mark those global unique IDs that were previously marked as orphaned but are now found in the vial table:
        SQLFragment deorphanMarkerSql = new SQLFragment("UPDATE study.SampleRequestSpecimen SET Orphaned = ? WHERE RowId IN (\n" +
                "\tSELECT study.SampleRequestSpecimen.RowId FROM study.SampleRequestSpecimen\n" +
                "\tLEFT OUTER JOIN study.Vial ON\n" +
                "\t\tstudy.Vial.GlobalUniqueId = study.SampleRequestSpecimen.SpecimenGlobalUniqueId AND\n" +
                "\t\tstudy.Vial.Container = study.SampleRequestSpecimen.Container\n" +
                "\tWHERE study.Vial.GlobalUniqueId IS NOT NULL AND\n" +
                "\t\tstudy.SampleRequestSpecimen.Orphaned = ? AND\n" +
                "\t\tstudy.SampleRequestSpecimen.Container = ?);", Boolean.FALSE, Boolean.TRUE, container.getId());
        logger.info("Marking requested vials that have been de-orphaned...");
        Table.execute(StudySchema.getInstance().getSchema(), deorphanMarkerSql);
        logger.info("Complete.");

    }

    private static void setLockedInRequestStatus(Container container, User user, Logger logger) throws SQLException
    {
        SQLFragment lockedInRequestSql = new SQLFragment("UPDATE " + StudySchema.getInstance().getTableInfoVial() +
                " SET LockedInRequest = ? WHERE RowId IN (SELECT study.Vial.RowId FROM study.Vial, study.LockedSpecimens " +
                "WHERE study.Vial.Container = ? AND study.LockedSpecimens.Container = ? AND " +
                "study.Vial.GlobalUniqueId = study.LockedSpecimens.GlobalUniqueId)");

        lockedInRequestSql.add(Boolean.TRUE);
        lockedInRequestSql.add(container.getId());
        lockedInRequestSql.add(container.getId());

        logger.info("Setting Specimen Locked in Request status...");
        Table.execute(StudySchema.getInstance().getSchema(), lockedInRequestSql);
        logger.info("Complete.");
    }

    private static void updateSpecimenProcessingInfo(Container container, Logger logger) throws SQLException
    {
        SQLFragment sql = new SQLFragment("UPDATE study.Specimen SET ProcessingLocation = (\n" +
                "\tSELECT MAX(ProcessingLocation) AS ProcessingLocation FROM \n" +
                "\t\t(SELECT DISTINCT SpecimenId, ProcessingLocation FROM study.Vial WHERE SpecimenId = study.Specimen.RowId AND Container = ?) Locations\n" +
                "\tGROUP BY SpecimenId\n" +
                "\tHAVING COUNT(ProcessingLocation) = 1\n" +
                ") WHERE Container = ?");
        sql.add(container.getId());
        sql.add(container.getId());
        logger.info("Updating processing locations on the specimen table...");
        Table.execute(StudySchema.getInstance().getSchema(), sql);
        logger.info("Complete.");

        sql = new SQLFragment("UPDATE study.Specimen SET FirstProcessedByInitials = (\n" +
                "\tSELECT MAX(FirstProcessedByInitials) AS FirstProcessedByInitials FROM \n" +
                "\t\t(SELECT DISTINCT SpecimenId, FirstProcessedByInitials FROM study.Vial WHERE SpecimenId = study.Specimen.RowId AND Container = ?) Locations\n" +
                "\tGROUP BY SpecimenId\n" +
                "\tHAVING COUNT(FirstProcessedByInitials) = 1\n" +
                ") WHERE Container = ?");
        sql.add(container.getId());
        sql.add(container.getId());
        logger.info("Updating first processed by initials on the specimen table...");
        Table.execute(StudySchema.getInstance().getSchema(), sql);
        logger.info("Complete.");

    }

    private static void updateCalculatedSpecimenData(Container container, User user, Logger logger) throws SQLException
    {
        // delete unnecessary comments and create placeholders for newly discovered errors:
        prepareQCComments(container, user, logger);

        updateCommentSpecimenHashes(container, logger);

        markOrphanedRequestVials(container, user, logger);
        setLockedInRequestStatus(container, user, logger);

        // clear caches before determining current sites:
        SimpleFilter containerFilter = new SimpleFilter("Container", container.getId());
        SampleManager.getInstance().clearCaches(container);
        Specimen[] specimens;
        int offset = 0;
        Map<Integer, SiteImpl> siteMap = new HashMap<Integer, SiteImpl>();
        String vialPropertiesSql = "UPDATE " + StudySchema.getInstance().getTableInfoVial() +
                " SET CurrentLocation = CAST(? AS INTEGER), ProcessingLocation = CAST(? AS INTEGER), " +
                "FirstProcessedByInitials = ?, AtRepository = ? WHERE RowId = ?";
        String commentSql = "UPDATE " + StudySchema.getInstance().getTableInfoSpecimenComment() +
                " SET QualityControlComments = ? WHERE GlobalUniqueId = ?";
        do
        {
            if (logger != null)
                logger.info("Determining current locations for vials " + (offset + 1) + " through " + (offset + CURRENT_SITE_UPDATE_SIZE) + ".");
            specimens = Table.select(StudySchema.getInstance().getTableInfoVial(), Table.ALL_COLUMNS,
                    containerFilter, null, Specimen.class, CURRENT_SITE_UPDATE_SIZE, offset);
            List<List<?>> vialPropertiesParams = new ArrayList<List<?>>();
            List<List<?>> commentParams = new ArrayList<List<?>>();

            Map<Specimen, List<SpecimenEvent>> specimenToOrderedEvents = SampleManager.getInstance().getDateOrderedEventLists(specimens);
            Map<Specimen, SpecimenComment> specimenComments = SampleManager.getInstance().getSpecimenComments(specimens);

            for (Map.Entry<Specimen, List<SpecimenEvent>> entry : specimenToOrderedEvents.entrySet())
            {
                Specimen specimen = entry.getKey();
                List<SpecimenEvent> dateOrderedEvents = entry.getValue();
                Integer processingLocation = SampleManager.getInstance().getProcessingSiteId(dateOrderedEvents);
                String firstProcessedByInitials = SampleManager.getInstance().getFirstProcessedByInitials(dateOrderedEvents);
                Integer currentLocation = SampleManager.getInstance().getCurrentSiteId(dateOrderedEvents);
                boolean atRepository = false;
                if (currentLocation != null)
                {
                    SiteImpl site;
                    if (!siteMap.containsKey(currentLocation))
                    {
                        site = StudyManager.getInstance().getSite(specimen.getContainer(), currentLocation.intValue());
                        if (site != null)
                            siteMap.put(currentLocation, site);
                    }
                    else
                        site = siteMap.get(currentLocation);

                    if (site != null)
                        atRepository = site.isRepository() != null && site.isRepository().booleanValue();
                }

                if (!safeIntegerEqual(currentLocation, specimen.getCurrentLocation()) ||
                    !safeIntegerEqual(processingLocation, specimen.getProcessingLocation()) ||
                    !safeObjectEquals(firstProcessedByInitials, specimen.getFirstProcessedByInitials()) ||
                    atRepository != specimen.isAtRepository())
                {
                    List<Object> params = new ArrayList<Object>();
                    params.add(currentLocation);
                    params.add(processingLocation);
                    params.add(firstProcessedByInitials);
                    params.add(atRepository);
                    params.add(specimen.getRowId());
                    vialPropertiesParams.add(params);
                }

                SpecimenComment comment = specimenComments.get(specimen);
                if (comment != null)
                {
                    // if we have a comment, it may be because we're in a bad QC state.  If so, we should update
                    // the reason for the QC problem.
                    String message = null;
                    if (comment.isQualityControlFlag() || comment.isQualityControlFlagForced())
                    {
                        SpecimenEvent[] events = SampleManager.getInstance().getSpecimenEvents(specimen);
                        Set<String> conflicts = getConflictingEventColumns(events);
                        if (!conflicts.isEmpty())
                        {
                            String sep = "";
                            message = "Conflicts found: ";
                            for (String conflict : conflicts)
                            {
                                message += sep + conflict;
                                sep = ", ";
                            }
                        }
                        commentParams.add(Arrays.asList(message, specimen.getGlobalUniqueId()));
                    }
                }
            }
            if (!vialPropertiesParams.isEmpty())
                Table.batchExecute(StudySchema.getInstance().getSchema(), vialPropertiesSql, vialPropertiesParams);
            if (!commentParams.isEmpty())
                Table.batchExecute(StudySchema.getInstance().getSchema(), commentSql, commentParams);
            offset += CURRENT_SITE_UPDATE_SIZE;
        }
        while (specimens.length > 0);

        // finally, after all other data has been updated, we can update our cached specimen counts and processing locations:
        updateSpecimenProcessingInfo(container, logger);

        RequestabilityManager.getInstance().updateRequestability(container, user, false, logger);
        if (logger != null)
            logger.info("Updating cached vial counts...");
        SampleManager.getInstance().updateSpecimenCounts(container, user);
        if (logger != null)
            logger.info("Vial count update complete.");
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
                if (col.getTargetTable().isSpecimens())
                    cols.append(col.getDbColumnName()).append(suffix);
            }
            _specimenCols = cols.toString().substring(0, cols.length() - suffix.length());
        }
        return _specimenCols;
    }

    private String getVialCols(List<SpecimenColumn> availableColumns)
    {
        if (_vialCols == null)
        {
            String suffix = ",\n    ";
            StringBuilder cols = new StringBuilder();
            for (SpecimenColumn col : availableColumns)
            {
                if (col.getTargetTable().isVials())
                    cols.append(col.getDbColumnName()).append(suffix);
            }
            _vialCols = cols.toString().substring(0, cols.length() - suffix.length());
        }
        return _vialCols;
    }

    private String getSpecimenEventCols(List<SpecimenColumn> availableColumns)
    {
        if (_vialEventCols == null)
        {
            String suffix = ",\n    ";
            StringBuilder cols = new StringBuilder();
            for (SpecimenColumn col : availableColumns)
            {
                if (col.getTargetTable().isEvents())
                    cols.append(col.getDbColumnName()).append(suffix);
            }
            _vialEventCols = cols.toString().substring(0, cols.length() - suffix.length());
        }
        return _vialEventCols;
    }


    private void populateMaterials(SpecimenLoadInfo info) throws SQLException
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
            throw new IllegalStateException("Could not find a unique specimen identifier column.  Either \"" + GLOBAL_UNIQUE_ID_TSV_COL
            + "\" or \"" + SPEC_NUMBER_TSV_COL + "\" must be present in the set of specimen columns.");
        }

        String insertSQL = "INSERT INTO exp.Material (LSID, Name, Container, CpasType, Created)  \n" +
                "SELECT " + info.getTempTableName() + ".LSID, " + info.getTempTableName() + "." + columnName +
                ", ?, ?, ? FROM " + info.getTempTableName() + "\nLEFT OUTER JOIN exp.Material ON\n" +
                info.getTempTableName() + ".LSID = exp.Material.LSID WHERE exp.Material.RowId IS NULL\n" +
                "GROUP BY " + info.getTempTableName() + ".LSID, " + info.getTempTableName() + "." + columnName;

        String deleteSQL = "DELETE FROM exp.Material WHERE RowId IN (SELECT exp.Material.RowId FROM exp.Material \n" +
                "LEFT OUTER JOIN " + info.getTempTableName() + " ON\n" +
                "\texp.Material.LSID = " + info.getTempTableName() + ".LSID\n" +
                "LEFT OUTER JOIN exp.MaterialInput ON\n" +
                "\texp.Material.RowId = exp.MaterialInput.MaterialId\n" +
                "WHERE " + info.getTempTableName() + ".LSID IS NULL\n" +
                "AND exp.MaterialInput.MaterialId IS NULL\n" +
                "AND (exp.Material.CpasType = ? OR exp.Material.CpasType = 'StudySpecimen') \n" +
                "AND exp.Material.Container = ?)";

        String prefix = new Lsid("StudySpecimen", "Folder-" + info.getContainer().getRowId(), "").toString();

        String cpasType;

        String name = "Study Specimens";
        ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(info.getContainer(), name);
        if (sampleSet == null)
        {
            ExpSampleSet source = ExperimentService.get().createSampleSet();
            source.setContainer(info.getContainer());
            source.setMaterialLSIDPrefix(prefix);
            source.setName(name);
            source.setLSID(ExperimentService.get().getSampleSetLsid(name, info.getContainer()).toString());
            source.setDescription("Study specimens for " + info.getContainer().getPath());
            source.save(null);
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
            SQLFragment deleteFragment = new SQLFragment(deleteSQL, cpasType, info.getContainer().getId());
            if (DEBUG)
                logSQLFragment(deleteFragment);
            int affected = Table.execute(info.getSchema(), deleteFragment);
            if (affected >= 0)
                info("exp.Material: " + affected + " rows removed.");
            info("exp.Material: Inserting new entries from temp table...");
            SQLFragment insertFragment = new SQLFragment(insertSQL, info.getContainer().getId(), cpasType, createdTimestamp);
            if (DEBUG)
                logSQLFragment(insertFragment);
            affected = Table.execute(info.getSchema(), insertFragment);
            if (affected >= 0)
                info("exp.Material: " + affected + " rows inserted.");
            info("exp.Material: Update complete.");
        }
        finally
        {
            assert cpuPopulateMaterials.stop();
        }
    }

    private String getSpecimenEventTempTableColumns(SpecimenLoadInfo info)
    {
        StringBuilder columnList = new StringBuilder();
        String prefix = "";
        for (SpecimenColumn col : info.getAvailableColumns())
        {
            if (col.getTargetTable().isEvents())
            {
                columnList.append(prefix);
                prefix = ", ";
                columnList.append("\n    ").append(info.getTempTableName()).append(".").append(col.getDbColumnName());
            }
        }
        return columnList.toString();
    }

    private void appendConflictResolvingSQL(SqlDialect dialect, SQLFragment sql, SpecimenColumn col, String tempTableName)
    {
        String selectCol = tempTableName + "." + col.getDbColumnName();

        if (col.getAggregateEventFunction() != null)
            sql.append(col.getAggregateEventFunction()).append("(").append(selectCol).append(")");
        else
        {
            String singletonAggregate;
            if (col.getJavaType().equals(Boolean.class))
            {
                // gross nested calls to cast the boolean to an int, get its min, then cast back to a boolean.
                // this is needed because most aggregates don't work on boolean values.
                singletonAggregate = "CAST(MIN(CAST(" + selectCol + " AS INTEGER)) AS " + dialect.getBooleanDatatype()  + ")";
            }
            else
            {
                singletonAggregate = "MIN(" + selectCol + ")";
            }
            sql.append("CASE WHEN");
            sql.append(" COUNT(DISTINCT(").append(selectCol).append(")) = 1 THEN ");
            sql.append(singletonAggregate);
            sql.append(" ELSE NULL END");
        }
        sql.append(" AS ").append(col.getDbColumnName());
    }


    private void populateSpecimens(SpecimenLoadInfo info) throws SQLException
    {
        SQLFragment insertSql = new SQLFragment();
        insertSql.append("INSERT INTO study.Specimen \n(").append("ParticipantSequenceKey, Container, SpecimenHash, ");
        insertSql.append(getSpecimenCols(info.getAvailableColumns())).append(")\n");
        insertSql.append("SELECT ");
        insertSql.append(VisitManager.getParticipantSequenceKeyExpr(info._schema, "PTID", "VisitValue"));
        insertSql.append(", Container, SpecimenHash, ");
        insertSql.append(getSpecimenCols(info.getAvailableColumns())).append(" FROM (\n");
        insertSql.append(getVialListFromTempTableSql(info)).append(") VialList\n");
        insertSql.append("GROUP BY ").append("Container, SpecimenHash, ");
        insertSql.append(getSpecimenCols(info.getAvailableColumns()));

        if (DEBUG)
            logSQLFragment(insertSql);
        assert cpuInsertSpecimens.start();
        info("Specimens: Inserting new rows...");
        Table.execute(info.getSchema(), insertSql);
        info("Specimens: Insert complete.");
        assert cpuInsertSpecimens.stop();
    }

    private SQLFragment getVialListFromTempTableSql(SpecimenLoadInfo info)
    {
        String prefix = ",\n    ";
        SQLFragment vialListSql = new SQLFragment();
        vialListSql.append("SELECT ").append(info.getTempTableName()).append(".LSID AS LSID");
        vialListSql.append(prefix).append("SpecimenHash");
        vialListSql.append(prefix).append("? AS Container");
        vialListSql.add(info.getContainer().getId());
        for (SpecimenColumn col : info.getAvailableColumns())
        {
            if (col.getTargetTable().isVials() || col.getTargetTable().isSpecimens())
            {
                vialListSql.append(prefix);
                appendConflictResolvingSQL(info.getSchema().getSqlDialect(), vialListSql, col, info.getTempTableName());
            }
        }
        vialListSql.append("\nFROM ").append(info.getTempTableName());
        vialListSql.append("\nGROUP BY\n");
        vialListSql.append(info.getTempTableName()).append(".LSID,\n    ");
        vialListSql.append(info.getTempTableName()).append(".Container,\n    ");
        vialListSql.append(info.getTempTableName()).append(".SpecimenHash,\n    ");
        vialListSql.append(info.getTempTableName()).append(".GlobalUniqueId");
        return vialListSql;
    }

    private static String getSpecimenHashSqlExpression(SpecimenLoadInfo info, String tableName)
    {
        StringBuilder sql = new StringBuilder();
        sql.append("\n    'Fld-").append(info.getContainer().getRowId()).append("'");
        String concatOp = info.getSchema().getSqlDialect().getConcatenationOperator();
        for (SpecimenColumn col : info.getAvailableColumns())
        {
            if (col.getTargetTable().isSpecimens())
            {
                String columnName = (tableName != null ? tableName  + "." : "" ) + col.getDbColumnName();
                sql.append("\n    ").append(concatOp).append("'~'").append(concatOp).append(" ");
                sql.append("CASE WHEN ").append(columnName).append(" IS NOT NULL THEN CAST(");
                sql.append(columnName).append(" AS VARCHAR").append(") ELSE '' END");
            }
        }
        return sql.toString();
    }
    
    private void populateVials(SpecimenLoadInfo info) throws SQLException
    {
        String prefix = ",\n    ";
        SQLFragment insertSelectSql = new SQLFragment();
        insertSelectSql.append("SELECT exp.Material.RowId");
        insertSelectSql.append(prefix).append("study.Specimen.RowId");
        insertSelectSql.append(prefix).append("study.Specimen.SpecimenHash");
        insertSelectSql.append(prefix).append("VialList.Container");
        insertSelectSql.append(prefix).append("?");
        // Set a default value of true for the 'Available' column:
        insertSelectSql.add(Boolean.TRUE);

        for (SpecimenColumn col : info.getAvailableColumns())
        {
            if (col.getTargetTable().isVials())
                insertSelectSql.append(prefix).append("VialList.").append(col.getDbColumnName());
        }

        SQLFragment insertSql = new SQLFragment();
        insertSql.append("INSERT INTO study.Vial \n(RowId, SpecimenId, SpecimenHash, Container, Available, ");
        insertSql.append(getVialCols(info.getAvailableColumns())).append(")\n");
        insertSql.append(insertSelectSql).append(" FROM (").append(getVialListFromTempTableSql(info)).append(") VialList");

        // join to material:
        insertSql.append("\n    JOIN exp.Material ON (");
        insertSql.append("VialList.LSID = exp.Material.LSID");
        insertSql.append(" AND exp.Material.Container = ?)");
        insertSql.add(info.getContainer().getId());

        // join to specimen:
        insertSql.append("\n    JOIN study.Specimen ON study.Specimen.Container = ? ");
        insertSql.add(info.getContainer().getId());
        insertSql.append("AND study.Specimen.SpecimenHash = VialList.SpecimenHash");

        if (DEBUG)
            logSQLFragment(insertSql);
        assert cpuInsertSpecimens.start();
        info("Vials: Inserting new rows...");
        Table.execute(info.getSchema(), insertSql);
        info("Vials: Insert complete.");
        assert cpuInsertSpecimens.stop();
    }

    private void logSQLFragment(SQLFragment sql)
    {
        info(sql.getSQL());
        info("Params: ");
        for (Object param : sql.getParams())
            info(param.toString());
    }

    private void populateVialEvents(SpecimenLoadInfo info) throws SQLException
    {
        SQLFragment insertSql = new SQLFragment();
        insertSql.append("INSERT INTO study.SpecimenEvent\n" +
                "(Container, VialId, " + getSpecimenEventCols(info.getAvailableColumns()) + ")\n" +
                "SELECT ? AS Container, study.Vial.RowId AS VialId, \n" +
                getSpecimenEventTempTableColumns(info) + " FROM " +
                info.getTempTableName() + "\nJOIN study.Vial ON " +
                info.getTempTableName() + ".GlobalUniqueId = study.Vial.GlobalUniqueId AND study.Vial.Container = ?");
        insertSql.add(info.getContainer().getId());
        insertSql.add(info.getContainer().getId());

        if (DEBUG)
        {
            info(insertSql.getSQL());
            info("Params: ");
            for (Object param : insertSql.getParams())
                info(param.toString());
        }
        assert cpuInsertSpecimenEvents.start();
        info("Specimen Events: Inserting new rows.");
        Table.execute(info.getSchema(), insertSql);
        info("Specimen Events: Insert complete.");
        assert cpuInsertSpecimenEvents.stop();
    }

    private void mergeTable(DbSchema schema, Container container, String tableName, ImportableColumn[] potentialColumns, File tsvFile, boolean addEntityId) throws IOException, SQLException
    {
        if (tsvFile == null || !NetworkDrive.exists(tsvFile))
        {
            info(tableName + ": no data to merge.");
            return;
        }

        CloseableIterator<Map<String, Object>> iter = null;

        try
        {
            iter = loadTsv(potentialColumns, tsvFile, tableName);
            mergeTable(schema, container, tableName, potentialColumns, iter, addEntityId);
        }
        finally
        {
            if (null != iter)
                iter.close();
        }
    }

    private void mergeTable(DbSchema schema, Container container, String tableName, ImportableColumn[] potentialColumns, Iterator<Map<String, Object>> iter, boolean addEntityId) throws IOException, SQLException
    {
        assert cpuMergeTable.start();
        info(tableName + ": Starting merge of data...");

        List<ImportableColumn> availableColumns = new ArrayList<ImportableColumn>();
        List<ImportableColumn> uniqueCols = new ArrayList<ImportableColumn>();

        StringBuilder selectSql = new StringBuilder();
        StringBuilder insertSql = new StringBuilder();
        StringBuilder updateSql = new StringBuilder();

        int rowCount = 0;
        int rowsAdded = 0;
        int rowsUpdated = 0;

        while (iter.hasNext())
        {
            Map row = iter.next();
            rowCount++;

            if (1 == rowCount)
            {
                for (ImportableColumn column : potentialColumns)
                {
                    if (row.containsKey(column.getTsvColumnName()))
                        availableColumns.add(column);
                }

                for (ImportableColumn col : availableColumns)
                {
                    if (col.isUnique())
                        uniqueCols.add(col);
                }

                selectSql.append("SELECT * FROM study.").append(tableName).append(" WHERE Container = ? ");
                for (ImportableColumn col : uniqueCols)
                    selectSql.append(" AND ").append(col.getDbColumnName()).append(" = ? ");

                insertSql.append("INSERT INTO study.").append(tableName).append(" (Container");
                if (addEntityId)
                    insertSql.append(", EntityId");
                for (ImportableColumn col : availableColumns)
                    insertSql.append(", ").append(col.getDbColumnName());
                insertSql.append(") VALUES (?");
                if (addEntityId)
                    insertSql.append(", ?");
                insertSql.append(StringUtils.repeat(", ?", availableColumns.size()));
                insertSql.append(")");

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
            }

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
                        params[colIndex++] = getValueParameter(col, row);

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
                    params[colIndex++] = getValueParameter(col, row);
                Table.execute(schema, insertSql.toString(), params);
                rowsAdded++;
            }
            else
            {
                List<Object> params = new ArrayList<Object>();
                for (ImportableColumn col : availableColumns)
                {
                    if (!col.isUnique())
                        params.add(getValueParameter(col, row));
                }
                params.add(container.getId());

                for (ImportableColumn col : uniqueCols)
                    params.add(getValueParameter(col, row));
                Table.execute(schema, updateSql.toString(), params.toArray());
                rowsUpdated++;
            }
        }

        info(tableName + ": inserted " + rowsAdded + " new rows, updated " + rowsUpdated + " rows.  (" + rowCount + " rows found in input file.)");
        assert cpuMergeTable.stop();
    }


    private void replaceTable(DbSchema schema, Container container, String tableName, ImportableColumn[] potentialColumns, File tsvFile, boolean addEntityId) throws IOException, SQLException
    {
        if (tsvFile == null || !NetworkDrive.exists(tsvFile))
        {
            info(tableName + ": no data to merge.");
            return;
        }

        CloseableIterator<Map<String, Object>> iter = null;

        try
        {
            iter = loadTsv(potentialColumns, tsvFile, tableName);
            replaceTable(schema, container, tableName, potentialColumns, iter, addEntityId);
        }
        finally
        {
            if (null != iter)
                iter.close();
        }
    }

    private void replaceTable(DbSchema schema, Container container, String tableName, ImportableColumn[] potentialColumns, Iterator<Map<String, Object>> iter, boolean addEntityId) throws IOException, SQLException
    {
        assert cpuMergeTable.start();
        info(tableName + ": Starting replacement of all data...");

        Table.execute(schema, "DELETE FROM study." + tableName + " WHERE Container = ?", new Object[] { container.getId() });
        List<ImportableColumn> availableColumns = new ArrayList<ImportableColumn>();
        StringBuilder insertSql = new StringBuilder();

        List<List<Object>> rows = new ArrayList<List<Object>>();
        int rowCount = 0;

        while (iter.hasNext())
        {
            Map<String, Object> row = iter.next();
            rowCount++;

            if (1 == rowCount)
            {
                for (ImportableColumn column : potentialColumns)
                {
                    if (row.containsKey(column.getTsvColumnName()))
                        availableColumns.add(column);
                }

                insertSql.append("INSERT INTO study.").append(tableName).append(" (Container");
                if (addEntityId)
                    insertSql.append(", EntityId");
                for (ImportableColumn col : availableColumns)
                    insertSql.append(", ").append(col.getDbColumnName());
                insertSql.append(") VALUES (?");
                if (addEntityId)
                    insertSql.append(", ?");
                insertSql.append(StringUtils.repeat(", ?", availableColumns.size()));
                insertSql.append(")");
            }

            List<Object> params = new ArrayList<Object>(availableColumns.size() + 1 + (addEntityId ? 1 : 0));
            params.add(container.getId());
            if (addEntityId)
                params.add(GUID.makeGUID());
            for (ImportableColumn col : availableColumns)
                params.add(getValueParameter(col, row));
            rows.add(params);
        }

        // No point in trying to insert zero rows.  Also, insertSql won't be set if no rows exist.
        if (!rows.isEmpty())
            Table.batchExecute(schema, insertSql.toString(), rows);

        info(tableName + ": Replaced all data with " + rows.size() + " new rows.  (" + rowCount + " rows found in input file.)");
        assert cpuMergeTable.stop();
    }

    private CloseableIterator<Map<String, Object>> loadTsv(ImportableColumn[] columns, File tsvFile, String tableName) throws IOException
    {
        info(tableName + ": Parsing data file for table...");
        Map<String, ColumnDescriptor> expectedColumns = new HashMap<String, ColumnDescriptor>(columns.length);
        for (ImportableColumn col : columns)
            expectedColumns.put(col.getTsvColumnName().toLowerCase(), col.getColumnDescriptor());

        TabLoader loader = new TabLoader(tsvFile, true);
        for (ColumnDescriptor column : loader.getColumns())
        {
            ColumnDescriptor expectedColumnDescriptor = expectedColumns.get(column.name.toLowerCase());
            if (expectedColumnDescriptor != null)
                column.clazz = expectedColumnDescriptor.clazz;
            else
                column.load = false;
        }
        info(tableName + ": Parsing complete.");
        return loader.iterator();
    }

    private List<SpecimenColumn> populateTempTable(DbSchema schema, Container container, String tempTable, File tsvFile) throws SQLException, IOException
    {
        assert cpuPopulateTempTable.start();
        CloseableIterator<Map<String, Object>> iter = loadTsv(SPECIMEN_COLUMNS, tsvFile, "Specimen");
        return populateTempTable(schema, container, tempTable, iter);
    }

    private List<SpecimenColumn> populateTempTable(DbSchema schema, Container container, String tempTable, CloseableIterator<Map<String, Object>> iter) throws SQLException, IOException
    {
        assert cpuPopulateTempTable.start();
        int rowCount = 0;
        Map<String, Object> firstRow = null;

        try
        {
            info("Populating temp table...");
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("INSERT INTO ").append(tempTable).append("\n(\n    Container,\n    LSID");
            for (ImportableColumn col : SPECIMEN_COLUMNS)
                sqlBuilder.append(",\n     ").append(col.getDbColumnName());
            sqlBuilder.append("\n) values (?, ?");
            sqlBuilder.append(StringUtils.repeat(", ?", SPECIMEN_COLUMNS.length));
            sqlBuilder.append(")");

            List<List<Object>> rows = new ArrayList<List<Object>>(SQL_BATCH_SIZE);
            String sql = sqlBuilder.toString();

            if (DEBUG)
            {
                info(sql);
            }

            while (iter.hasNext())
            {
                Map<String, Object> properties = iter.next();
                rowCount++;

                if (1 == rowCount)
                    firstRow = properties;

                String id = (String) properties.get(GLOBAL_UNIQUE_ID_TSV_COL);

                if (id == null)
                    id = (String) properties.get(SPEC_NUMBER_TSV_COL);

                Lsid lsid = SpecimenService.get().getSpecimenMaterialLsid(container, id);
                List<Object> params = new ArrayList<Object>(SPECIMEN_COLUMNS.length + 2);
                params.add(container.getId());
                params.add(lsid.toString());

                for (ImportableColumn col : SPECIMEN_COLUMNS)
                    params.add(getValueParameter(col, properties));

                rows.add(params);

                if (rows.size() == SQL_BATCH_SIZE)
                {
                    Table.batchExecute(schema, sql, rows);
                    rows = new ArrayList<List<Object>>(SQL_BATCH_SIZE);
                    // output a message every 100 batches (every 10,000 events, by default)
                    if (rowCount % (SQL_BATCH_SIZE*100) == 0)
                        info(rowCount + " temp rows loaded...");
                }
            }

            if (!rows.isEmpty())
                Table.batchExecute(schema, sql, rows);

            info("Complete. " + rowCount + " rows loaded.");
        }
        finally
        {
            assert cpuPopulateTempTable.stop();
            iter.close();
        }

        List<SpecimenColumn> loadedColumns = new ArrayList<SpecimenColumn>();

        if (rowCount > 0)
        {
            for (SpecimenColumn column : SPECIMEN_COLUMNS)
            {
                if (firstRow.containsKey(column.getTsvColumnName()))
                    loadedColumns.add(column);
            }
        }

        if (loadedColumns.isEmpty())
        {
            info("Found no specimen columns to import. Temp table will not be loaded.");
            return loadedColumns;
        }

        String sep = "";
        SQLFragment innerTableSelectSql = new SQLFragment("SELECT " + tempTable + ".RowId AS RowId");
        SQLFragment innerTableJoinSql = new SQLFragment();
        SQLFragment remapExternalIdsSql = new SQLFragment("UPDATE ").append(tempTable).append(" SET ");
        for (SpecimenColumn col : loadedColumns)
        {
            if (col.getFkTable() != null)
            {
                remapExternalIdsSql.append(sep).append(col.getDbColumnName()).append(" = InnerTable.").append(col.getDbColumnName());

                innerTableSelectSql.append(",\n\t").append(col.getFkTableAlias()).append(".RowId AS ").append(col.getDbColumnName());

                innerTableJoinSql.append("\nLEFT OUTER JOIN study.").append(col.getFkTable()).append(" AS ").append(col.getFkTableAlias()).append(" ON ");
                innerTableJoinSql.append("(" + tempTable + ".");
                innerTableJoinSql.append(col.getDbColumnName()).append(" = ").append(col.getFkTableAlias()).append(".").append(col.getFkColumn());
                innerTableJoinSql.append(" AND ").append(col.getFkTableAlias()).append(".Container").append(" = ?)");
                innerTableJoinSql.add(container.getId());

                sep = ",\n\t";
            }
        }
        remapExternalIdsSql.append(" FROM (").append(innerTableSelectSql).append(" FROM ").append(tempTable);
        remapExternalIdsSql.append(innerTableJoinSql).append(") InnerTable\nWHERE InnerTable.RowId = ").append(tempTable).append(".RowId;");

        info("Remapping lookup indexes in temp table...");
        if (DEBUG)
            info(remapExternalIdsSql.toString());
        Table.execute(schema, remapExternalIdsSql);
        info("Update complete.");


        SQLFragment conflictResolvingSubselect = new SQLFragment("SELECT GlobalUniqueId");
        for (SpecimenColumn col : loadedColumns)
        {
            if (col.getTargetTable().isSpecimens())
            {
                conflictResolvingSubselect.append(",\n\t");
                String selectCol = tempTable + "." + col.getDbColumnName();

                if (col.getAggregateEventFunction() != null)
                    conflictResolvingSubselect.append(col.getAggregateEventFunction()).append("(").append(selectCol).append(")");
                else
                {
                    String singletonAggregate;
                    if (col.getJavaType().equals(Boolean.class))
                    {
                        // gross nested calls to cast the boolean to an int, get its min, then cast back to a boolean.
                        // this is needed because most aggregates don't work on boolean values.
                        singletonAggregate = "CAST(MIN(CAST(" + selectCol + " AS INTEGER)) AS " + schema.getSqlDialect().getBooleanDatatype()  + ")";
                    }
                    else
                    {
                        singletonAggregate = "MIN(" + selectCol + ")";
                    }
                    conflictResolvingSubselect.append("CASE WHEN");
                    conflictResolvingSubselect.append(" COUNT(DISTINCT(").append(selectCol).append(")) = 1 THEN ");
                    conflictResolvingSubselect.append(singletonAggregate);
                    conflictResolvingSubselect.append(" ELSE NULL END");
                }
                conflictResolvingSubselect.append(" AS ").append(col.getDbColumnName());
            }
        }
        conflictResolvingSubselect.append("\nFROM ").append(tempTable).append("\nGROUP BY GlobalUniqueId");


        SQLFragment updateHashSql = new SQLFragment();
        updateHashSql.append("UPDATE ").append(tempTable).append(" SET SpecimenHash = 'Fld-").append(container.getRowId()).append("'");
        String concatOp = schema.getSqlDialect().getConcatenationOperator();
        for (SpecimenColumn col : loadedColumns)
        {
            if (col.getTargetTable().isSpecimens())
            {
                String columnName = "InnerTable." + col.getDbColumnName();
                updateHashSql.append("\n    ").append(concatOp).append("'~'").append(concatOp).append(" ");
                updateHashSql.append("CASE WHEN ").append(columnName).append(" IS NOT NULL THEN CAST(");
                updateHashSql.append(columnName).append(" AS ").append(schema.getSqlDialect().sqlTypeNameFromSqlType(Types.VARCHAR)).append(") ELSE '' END");
            }
        }
        updateHashSql.append("\nFROM (").append(conflictResolvingSubselect).append(") InnerTable WHERE ");
        updateHashSql.append(tempTable).append(".GlobalUniqueId = InnerTable.GlobalUniqueId");


        info("Updating specimen hash values in temp table...");
        if (DEBUG)
            info(updateHashSql.toString());
        Table.execute(schema, updateHashSql);
        info("Update complete.");

        info("Temp table populated.");
        return loadedColumns;
    }

    private Parameter getValueParameter(ImportableColumn col, Map tsvRow)
    {
        Object value = tsvRow.get(col.getTsvColumnName());
        if (value == null)
            return Parameter.nullParameter(col.getSQLType());
        return new Parameter(value, col.getSQLType());
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
            String strType = schema.getSqlDialect().sqlTypeNameFromSqlType(Types.VARCHAR);
            sql.append("\n(\n    RowId ").append(schema.getSqlDialect().getUniqueIdentType()).append(", ");
            sql.append("Container ").append(strType).append("(300) NOT NULL, ");
            sql.append("LSID ").append(strType).append("(300) NOT NULL, ");
            sql.append("SpecimenHash ").append(strType).append("(300)");
            for (SpecimenColumn col : SPECIMEN_COLUMNS)
                sql.append(",\n    ").append(col.getDbColumnName()).append(" ").append(col.getDbType());
            sql.append("\n);");
            if (DEBUG)
                info(sql.toString());
            Table.execute(schema, sql.toString(), null);

            String rowIdIndexSql = "CREATE INDEX IX_SpecimenUpload" + randomizer + "_RowId ON " + tableName + "(RowId)";
            String globalUniqueIdIndexSql = "CREATE INDEX IX_SpecimenUpload" + randomizer + "_GlobalUniqueId ON " + tableName + "(GlobalUniqueId)";
            String lsidIndexSql = "CREATE INDEX IX_SpecimenUpload" + randomizer + "_LSID ON " + tableName + "(LSID)";
            String hashIndexSql = "CREATE INDEX IX_SpecimenUpload" + randomizer + "_SpecimenHash ON " + tableName + "(SpecimenHash)";
            if (DEBUG)
            {
                info(globalUniqueIdIndexSql);
                info(rowIdIndexSql);
                info(lsidIndexSql);
                info(hashIndexSql);
            }
            Table.execute(schema, globalUniqueIdIndexSql, null);
            Table.execute(schema, rowIdIndexSql, null);
            Table.execute(schema, lsidIndexSql, null);
            Table.execute(schema, hashIndexSql, null);
            return tableName;
        }
        finally
        {
            assert cpuCreateTempTable.stop();
        }
    }
}
