/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.CloseableIterator;
import org.labkey.api.util.GUID;
import org.labkey.api.util.NetworkDrive;
import org.labkey.study.SampleManager;
import org.labkey.study.StudySchema;
import org.labkey.study.model.*;

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
    CPUTimer cpuPopulateMaterials = new CPUTimer("populateMaterials");
    CPUTimer cpuUpdateSpecimens = new CPUTimer("updateSpecimens");
    CPUTimer cpuInsertSpecimens = new CPUTimer("insertSpecimens");
    CPUTimer cpuUpdateSpecimenEvents = new CPUTimer("updateSpecimenEvents");
    CPUTimer cpuInsertSpecimenEvents = new CPUTimer("insertSpecimenEvents");
    CPUTimer cpuMergeTable = new CPUTimer("mergeTable");
    CPUTimer cpuCreateTempTable = new CPUTimer("createTempTable");
    CPUTimer cpuPopulateTempTable = new CPUTimer("populateTempTable");
    CPUTimer cpuCurrentLocations = new CPUTimer("updateCurrentLocations");


    public static class ImportableColumn
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
        },
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
        };

        public abstract boolean isEvents();
        public abstract boolean isSpecimens();
        public abstract String getName();
    }

    public static class SpecimenColumn extends ImportableColumn
    {
        private TargetTable _targetTable;
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
        private String _tempTableName;
        private List<SpecimenColumn> _availableColumns;
        private Container _container;
        private User _user;
        private DbSchema _schema;

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

    private static final String DATETIME_TYPE = StudySchema.getInstance().getSqlDialect().getDefaultDateTimeDatatype();
    private static final String BOOLEAN_TYPE = StudySchema.getInstance().getSqlDialect().getBooleanDatatype();
    private static final String GLOBAL_UNIQUE_ID_TSV_COL = "global_unique_specimen_id";
    private static final String LAB_ID_TSV_COL = "lab_id";
    private static final String SPEC_NUMBER_TSV_COL = "specimen_number";
    private static final String EVENT_ID_COL = "record_id";

    public static final SpecimenColumn[] SPECIMEN_COLUMNS = {
            new SpecimenColumn(EVENT_ID_COL, "ExternalId", "INT NOT NULL", TargetTable.SPECIMEN_EVENTS, true),
            new SpecimenColumn("record_source", "RecordSource", "VARCHAR(10)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn(GLOBAL_UNIQUE_ID_TSV_COL, "GlobalUniqueId", "VARCHAR(20)", TargetTable.SPECIMENS),
            new SpecimenColumn(LAB_ID_TSV_COL, "LabId", "INT", TargetTable.SPECIMEN_EVENTS, "Site", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("originating_location", "OriginatingLocationId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "Site", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("unique_specimen_id", "UniqueSpecimenId", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("ptid", "Ptid", "VARCHAR(32)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("parent_specimen_id", "ParentSpecimenId", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("draw_timestamp", "DrawTimestamp", DATETIME_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("sal_receipt_date", "SalReceiptDate", DATETIME_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn(SPEC_NUMBER_TSV_COL, "SpecimenNumber", "VARCHAR(50)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("class_id", "ClassId", "VARCHAR(4)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("visit_value", "VisitValue", "FLOAT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("protocol_number", "ProtocolNumber", "VARCHAR(10)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("visit_description", "VisitDescription", "VARCHAR(3)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("other_specimen_id", "OtherSpecimenId", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("volume", "Volume", "FLOAT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "MAX"),
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
            new SpecimenColumn("requestable", "Requestable", BOOLEAN_TYPE, TargetTable.SPECIMENS),
            new SpecimenColumn("freezer", "freezer", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("fr_level1", "fr_level1", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("fr_level2", "fr_level2", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("fr_container", "fr_container", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("fr_position", "fr_position", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("shipped_from_lab", "ShippedFromLab", "INT", TargetTable.SPECIMEN_EVENTS, "Site", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("shipped_to_lab", "ShippedtoLab", "INT", TargetTable.SPECIMEN_EVENTS, "Site", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("frozen_time", "FrozenTime", DATETIME_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("primary_volume", "PrimaryVolume", "FLOAT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("primary_volume_units", "PrimaryVolumeUnits", "VARCHAR(20)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("processing_time", "ProcessingTime", DATETIME_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS)
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
    private String _specimenEventCols;
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

            SpecimenLoadInfo loadInfo = populateTempSpecimensTable(user, schema, container, fileMap.get("specimens"));

            mergeTable(schema, container, "Site", SITE_COLUMNS, fileMap.get("labs"), true);

            replaceTable(schema, container, "SpecimenAdditive", ADDITIVE_COLUMNS, fileMap.get("additives"), false);
            replaceTable(schema, container, "SpecimenDerivative", DERIVATIVE_COLUMNS, fileMap.get("derivatives"), false);
            replaceTable(schema, container, "SpecimenPrimaryType", PRIMARYTYPE_COLUMNS, fileMap.get("primary_types"), false);

            // Columns will be empty in specimens.tsv has no data
            if (!loadInfo.getAvailableColumns().isEmpty())
                populateSpecimenTables(loadInfo);
            else
                info("Specimens: 0 rows found in input file.");

            cpuCurrentLocations.start();
            updateCalculatedSpecimenData(container, user, _logger);
            cpuCurrentLocations.stop();
            info("Time to determine locations: " + cpuCurrentLocations.toString());

            StudyImpl study = StudyManager.getInstance().getStudy(container);
            StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user);

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

    protected void process(User user, Container container, Map<String, CloseableIterator<Map<String, Object>>> iterMap, Logger logger) throws SQLException, IOException
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        _logger = logger;

        try
        {
            DbScope scope = schema.getScope();
            if (!DEBUG)
                scope.beginTransaction();

            SpecimenLoadInfo loadInfo = populateTempSpecimensTable(user, schema, container, iterMap.get("specimens"));

            mergeTable(schema, container, "Site", SITE_COLUMNS, iterMap.get("labs"), true);
            replaceTable(schema, container, "SpecimenAdditive", ADDITIVE_COLUMNS, iterMap.get("additives"), false);
            replaceTable(schema, container, "SpecimenDerivative", DERIVATIVE_COLUMNS, iterMap.get("derivatives"), false);
            replaceTable(schema, container, "SpecimenPrimaryType", PRIMARYTYPE_COLUMNS, iterMap.get("primary_types"), false);
            populateSpecimenTables(loadInfo);

            cpuCurrentLocations.start();
            updateCalculatedSpecimenData(container, user, _logger);
            cpuCurrentLocations.stop();
            info("Time to determine locations: " + cpuCurrentLocations.toString());

            StudyImpl study = StudyManager.getInstance().getStudy(container);
            StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user);

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
        info("Deleting old data from Specimen table...");
        Table.delete(StudySchema.getInstance().getTableInfoSpecimen(), containerFilter);
        info("Complete.");

        populateMaterials(info);
        populateSpecimens(info);
        populateSpecimenEvents(info);
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

    private static void prepareQCComments(Container container, User user, Logger logger) throws SQLException
    {
        StringBuilder columnList = new StringBuilder();
        columnList.append("SpecimenId");
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
        conflictedGUIDs.append(StudySchema.getInstance().getTableInfoSpecimen());
        conflictedGUIDs.append(" WHERE RowId IN (\n");
        conflictedGUIDs.append("SELECT SpecimenId FROM\n");
        conflictedGUIDs.append("(SELECT DISTINCT\n").append(columnList).append("\nFROM ").append(StudySchema.getInstance().getTableInfoSpecimenEvent());
        conflictedGUIDs.append("\nWHERE Container = ?\nGROUP BY\n").append(columnList).append(") ");
        conflictedGUIDs.append("AS DupCheckView\nGROUP BY SpecimenId HAVING Count(SpecimenId) > 1");
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
        SQLFragment orphanMarkerSql = new SQLFragment("UPDATE study.SampleRequestSpecimen SET Orphaned = ? WHERE RowId IN (\n" +
                "\tSELECT study.SampleRequestSpecimen.RowId FROM study.SampleRequestSpecimen\n" +
                "\tLEFT OUTER JOIN study.Specimen ON\n" +
                "\t\tstudy.Specimen.GlobalUniqueId = study.SampleRequestSpecimen.SpecimenGlobalUniqueId AND\n" +
                "\t\tstudy.Specimen.Container = study.SampleRequestSpecimen.Container\n" +
                "\tWHERE study.Specimen.GlobalUniqueId IS NULL AND\n" +
                "\t\tstudy.SampleRequestSpecimen.Container = ?);", Boolean.TRUE, container.getId());
        logger.info("Marking requested vials that have been orphaned...");
        Table.execute(StudySchema.getInstance().getSchema(), orphanMarkerSql);
        logger.info("Complete.");
    }

    private static void setLockedInRequestStatus(Container container, User user, Logger logger) throws SQLException
    {
        SQLFragment lockedInRequestSql = new SQLFragment("UPDATE " + StudySchema.getInstance().getTableInfoSpecimen() +
                " SET LockedInRequest = ? WHERE GlobalUniqueId IN (SELECT study.LockedSpecimens.GlobalUniqueId FROM study.LockedSpecimens" +
                " WHERE study.LockedSpecimens.Container = ?)");

        lockedInRequestSql.add(Boolean.TRUE);
        lockedInRequestSql.add(container.getId());

        logger.info("Setting Specimen Locked in Request status...");
        Table.execute(StudySchema.getInstance().getSchema(), lockedInRequestSql);
        logger.info("Complete.");
    }

    private static void setAvailableStatus(Container container, User user, Logger logger) throws SQLException
    {
        SQLFragment availableSql = SampleManager.getInstance().getSpecimenAvailableQuery();
        logger.info("Setting Specimen Available status...");
        Table.execute(StudySchema.getInstance().getSchema(), availableSql);
        logger.info("Complete.");
    }

    private static void updateCalculatedSpecimenData(Container container, User user, Logger logger) throws SQLException
    {
        // delete unnecessary comments and create placeholders for newly discovered errors:
        prepareQCComments(container, user, logger);

        markOrphanedRequestVials(container, user, logger);
        setLockedInRequestStatus(container, user, logger);

        // clear caches before determining current sites:
        SimpleFilter containerFilter = new SimpleFilter("Container", container.getId());
        SampleManager.getInstance().clearCaches(container);
        Specimen[] specimens;
        int offset = 0;
        Map<Integer, SiteImpl> siteMap = new HashMap<Integer, SiteImpl>();
        String currentLocationSql = "UPDATE " + StudySchema.getInstance().getTableInfoSpecimen() +
                " SET CurrentLocation = CAST(? AS INTEGER), SpecimenHash = ? WHERE RowId = ?";
        String commentSql = "UPDATE " + StudySchema.getInstance().getTableInfoSpecimenComment() +
                " SET SpecimenHash = ?, QualityControlComments = ? WHERE GlobalUniqueId = ?";
        String atRepositorySql = "UPDATE " + StudySchema.getInstance().getTableInfoSpecimen() +
                " SET AtRepository = ? WHERE RowId = ?";
        do
        {
            if (logger != null)
                logger.info("Calculating hashes and current locations for vials " + (offset + 1) + " through " + (offset + CURRENT_SITE_UPDATE_SIZE) + ".");
            specimens = Table.select(StudySchema.getInstance().getTableInfoSpecimen(), Table.ALL_COLUMNS,
                    containerFilter, null, Specimen.class, CURRENT_SITE_UPDATE_SIZE, offset);
            List<List<?>> currentLocationParams = new ArrayList<List<?>>();
            List<List<?>> commentParams = new ArrayList<List<?>>();
            List<List<?>> atRepositoryParams = new ArrayList<List<?>>();

            for (Specimen specimen : specimens)
            {
                Integer currentLocation = SampleManager.getInstance().getCurrentSiteId(specimen);
                String specimenHash = getSpecimenHash(specimen);
                if (!safeIntegerEqual(currentLocation, specimen.getCurrentLocation()) ||
                    !specimenHash.equals(specimen.getSpecimenHash()))
                {
                    currentLocationParams.add(Arrays.asList(currentLocation, specimenHash, specimen.getRowId()));
                }

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

                    if (site != null && site.isRepository())
                        atRepositoryParams.add(Arrays.asList(Boolean.TRUE, specimen.getRowId()));
                }
                SpecimenComment comment = SampleManager.getInstance().getSpecimenCommentForVial(specimen);
                if (comment != null)
                {
                    // if we have a comment, it may be because we're in a bad QC state.  If so, we should update
                    // the reason for the QC problem.  Regardless, we need to make sure our hash is up to date.
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
                    }
                    commentParams.add(Arrays.asList(specimenHash, message, specimen.getGlobalUniqueId()));
                }
            }
            if (!currentLocationParams.isEmpty())
                Table.batchExecute(StudySchema.getInstance().getSchema(), currentLocationSql, currentLocationParams);
            if (!commentParams.isEmpty())
                Table.batchExecute(StudySchema.getInstance().getSchema(), commentSql, commentParams);
            if (!atRepositoryParams.isEmpty())
                Table.batchExecute(StudySchema.getInstance().getSchema(), atRepositorySql, atRepositoryParams);
            offset += CURRENT_SITE_UPDATE_SIZE;
        }
        while (specimens.length > 0);

        setAvailableStatus(container, user, logger);
    }
    
    private static String getSpecimenHash(Specimen specimen)
    {
        String separator = "~";
        StringBuilder builder = new StringBuilder();
        builder.append(specimen.getPtid()).append(separator);
        builder.append(specimen.getDrawTimestamp()).append(separator);
        builder.append(specimen.getVisitValue()).append(separator);
        builder.append(specimen.getVisitDescription()).append(separator);
        builder.append(specimen.getVolumeUnits()).append(separator);
        builder.append(specimen.getSubAdditiveDerivative()).append(separator);
        builder.append(specimen.getPrimaryTypeId()).append(separator);
        builder.append(specimen.getDerivativeTypeId()).append(separator);
        builder.append(specimen.getAdditiveTypeId()).append(separator);
        builder.append(specimen.getSalReceiptDate()).append(separator);
        builder.append(specimen.getProtocolNumber()).append(separator);
        builder.append(specimen.getOriginatingLocationId());
        return builder.toString();
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

    private String getSpecimenEventCols(List<SpecimenColumn> availableColumns)
    {
        if (_specimenEventCols == null)
        {
            String suffix = ",\n    ";
            StringBuilder cols = new StringBuilder();
            for (SpecimenColumn col : availableColumns)
            {
                if (col.getTargetTable().isEvents())
                    cols.append(col.getDbColumnName()).append(suffix);
            }
            _specimenEventCols = cols.toString().substring(0, cols.length() - suffix.length());
        }
        return _specimenEventCols;
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
                if (col.getFkTable() != null)
                {
                    columnList.append(prefix);
                    prefix = ", ";
                    columnList.append("\n    ").append(col.getFkTableAlias()).append(".RowId");
                    columnList.append(" AS ").append(col.getDbColumnName());
                }
                else
                {
                    columnList.append(prefix);
                    prefix = ", ";
                    columnList.append("\n    ").append(info.getTempTableName()).append(".").append(col.getDbColumnName());
                }
            }
        }
        return columnList.toString();
    }

    private void appendConflictResolvingSQL(SqlDialect dialect, SQLFragment sql, SpecimenColumn col, String tempTableName)
    {
        String selectCol;
        if (col.getFkTable() != null)
            selectCol = col.getFkTableAlias() + ".RowId";
        else
            selectCol = tempTableName + "." + col.getDbColumnName();

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
        insertSql.append("INSERT INTO study.Specimen \n(RowId, Container, ");
        insertSql.append(getSpecimenCols(info.getAvailableColumns())).append(")\n");
        insertSql.append("SELECT ");

        StringBuilder groupingClause = new StringBuilder();
        groupingClause.append("exp.Material.RowId,\n    ");
        groupingClause.append(info.getTempTableName()).append(".Container,\n    ");
        groupingClause.append(info.getTempTableName()).append(".GlobalUniqueId");
        insertSql.append(groupingClause);
        String prefix = ",\n    ";

        for (SpecimenColumn col : info.getAvailableColumns())
        {
            if (col.getTargetTable().isSpecimens() && !GLOBAL_UNIQUE_ID_TSV_COL.equals(col.getTsvColumnName()))
            {
                insertSql.append(prefix);
                appendConflictResolvingSQL(info.getSchema().getSqlDialect(), insertSql, col, info.getTempTableName());
            }
        }

        insertSql.append("\nFROM ").append(info.getTempTableName()).append("\n    JOIN exp.Material ON (");
        insertSql.append(info.getTempTableName()).append(".LSID = exp.Material.LSID");
        insertSql.append(" AND exp.Material.Container = ?)");
        insertSql.add(info.getContainer().getId());

        for (SpecimenColumn col : info.getAvailableColumns())
        {
            if (col.getTargetTable().isSpecimens() && col.getFkTable() != null)
            {
                insertSql.append("\n    ");
                if (col.getJoinType() != null)
                    insertSql.append(col.getJoinType()).append(" ");
                insertSql.append("JOIN study.").append(col.getFkTable()).append(" AS ").append(col.getFkTableAlias()).append(" ON ");
                insertSql.append("(").append(info.getTempTableName()).append(".");
                insertSql.append(col.getDbColumnName()).append(" = ").append(col.getFkTableAlias()).append(".").append(col.getFkColumn());
                insertSql.append(" AND ").append(col.getFkTableAlias()).append(".Container").append(" = ?)");
                insertSql.add(info.getContainer().getId());
            }
        }

        insertSql.append("\nGROUP BY ").append(groupingClause);

        if (DEBUG)
            logSQLFragment(insertSql);
        assert cpuInsertSpecimens.start();
        info("Specimens: Inserting new rows...");
        Table.execute(info.getSchema(), insertSql);
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

    private void populateSpecimenEvents(SpecimenLoadInfo info) throws SQLException
    {
        SQLFragment insertSql = new SQLFragment();
        insertSql.append("INSERT INTO study.SpecimenEvent\n" +
                "(Container, SpecimenId, " + getSpecimenEventCols(info.getAvailableColumns()) + ")\n" +
                "SELECT ? AS Container, study.Specimen.RowId AS SpecimenId, \n" +
                getSpecimenEventTempTableColumns(info) + " FROM " +
                info.getTempTableName() + "\nJOIN study.Specimen ON " +
                info.getTempTableName() + ".GlobalUniqueId = study.Specimen.GlobalUniqueId AND study.Specimen.Container = ?");
        insertSql.add(info.getContainer().getId());
        insertSql.add(info.getContainer().getId());

        for (SpecimenColumn col : info.getAvailableColumns())
        {
            if (col.getTargetTable().isEvents() && col.getFkTable() != null)
            {
                insertSql.append("\n    ");
                if (col.getJoinType() != null)
                    insertSql.append(col.getJoinType()).append(" ");
                insertSql.append("JOIN study.").append(col.getFkTable()).append(" AS ").append(col.getFkTableAlias()).append(" ON ");
                insertSql.append("(").append(info.getTempTableName()).append(".");
                insertSql.append(col.getDbColumnName()).append(" = ").append(col.getFkTableAlias()).append(".").append(col.getFkColumn());
                insertSql.append(" AND ").append(col.getFkTableAlias()).append(".Container").append(" = ?)");
                insertSql.add(info.getContainer().getId());
            }
        }
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

        Iterator<Map<String, Object>> iter = loadTsv(potentialColumns, tsvFile, tableName);
        mergeTable(schema, container, tableName, potentialColumns, iter, addEntityId);
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

        CloseableIterator<Map<String, Object>> iter = loadTsv(potentialColumns, tsvFile, tableName);
        replaceTable(schema, container, tableName, potentialColumns, iter, addEntityId);
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
                params.add(getValue(col, row));
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
                    params.add(getValue(col, properties));

                rows.add(params);

                if (rows.size() == SQL_BATCH_SIZE)
                {
                    Table.batchExecute(schema, sql, rows);
                    rows = new ArrayList<List<Object>>(SQL_BATCH_SIZE);
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

        if (rowCount > 0)
        {
            for (SpecimenColumn column : SPECIMEN_COLUMNS)
            {
                if (firstRow.containsKey(column.getTsvColumnName()))
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
