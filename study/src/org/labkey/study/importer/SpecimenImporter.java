/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.*;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.SampleManager;
import org.labkey.study.StudySchema;
import org.labkey.study.model.*;
import org.labkey.study.visitmanager.VisitManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
    public static final String EXPERIMENTAL_PERFORMANCE_IMPROVEMENTS = "ExperimentalSpecimenLoadPerformanceImprovements";

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
        private int _size = -1;

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
                _dbType = StudySchema.getInstance().getSqlDialect().getDefaultDateTimeDataType();
                _javaType = TimeOnlyDate.class;
            }
            else if (DATETIME_TYPE.equals(databaseType))
            {
                _dbType = StudySchema.getInstance().getSqlDialect().getDefaultDateTimeDataType();
                _javaType = java.util.Date.class;
            }
            else
                _dbType = databaseType.toUpperCase();
            if (_dbType.startsWith("VARCHAR("))
            {
                assert _dbType.charAt(_dbType.length() - 1) == ')' : "Unexpected VARCHAR type format: " + _dbType;
                String sizeStr = _dbType.substring(8, _dbType.length() - 1);
                _size = Integer.parseInt(sizeStr);
            }
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
                    _javaType = Double.class;
                else if (_dbType.indexOf("BIGINT") >= 0)
                    _javaType = Long.class;
                else if (_dbType.indexOf("INT") >= 0)
                    _javaType = Integer.class;
                else if (_dbType.indexOf(BOOLEAN_TYPE) >= 0)
                    _javaType = Boolean.class;
                else
                    throw new UnsupportedOperationException("Unrecognized sql type: " + _dbType);
            }
            return _javaType;
        }

        public JdbcType getSQLType()
        {
            if (getJavaType() == String.class)
                return JdbcType.VARCHAR;
            else if (getJavaType() == java.util.Date.class)
                return JdbcType.TIMESTAMP;
            else if (getJavaType() == TimeOnlyDate.class)
                return JdbcType.TIMESTAMP;
            else if (getJavaType() == Double.class)
                return JdbcType.DOUBLE;
            else if (getJavaType() == Integer.class)
                return JdbcType.INTEGER;
            else if (getJavaType() == Boolean.class)
                return JdbcType.BOOLEAN;
            else if (getJavaType() == Long.class)
                return JdbcType.BIGINT;
            else
                throw new UnsupportedOperationException("SQL type has not been defined for DB type " + _dbType + ", java type " + getJavaType());
        }

        public int getMaxSize()
        {
            return _size;
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
        private boolean _isKeyColumn = false;

        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType, TargetTable eventColumn, boolean unique)
        {
            super(tsvColumnName, dbColumnName, databaseType, unique);
            _targetTable = eventColumn;
        }

        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType, boolean isKeyColumn, TargetTable eventColumn, boolean unique)
        {
            this(tsvColumnName, dbColumnName, databaseType, eventColumn, unique);
            _isKeyColumn = isKeyColumn;
        }

        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType, boolean isKeyColumn, TargetTable eventColumn)
        {
            this(tsvColumnName, dbColumnName, databaseType, eventColumn, false);
            _isKeyColumn = isKeyColumn;
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

        public boolean isKeyColumn()
        {
            return _isKeyColumn;
        }

        public String getFkTableAlias()
        {
            return getDbColumnName() + "Lookup";
        }

        public boolean isDateType()
        {
            return getDbType() != null && (getDbType().equals("DATETIME") || getDbType().equals("TIMESTAMP")) && !getJavaType().equals(TimeOnlyDate.class);
        }
    }

    private static class SpecimenLoadInfo
    {
        private final String _tempTableName;
        private final List<SpecimenColumn> _availableColumns;
        private final int _rowCount;
        private final Container _container;
        private final User _user;
        private final DbSchema _schema;

        public SpecimenLoadInfo(User user, Container container, DbSchema schema, List<SpecimenColumn> availableColumns, int rowCount, String tempTableName)
        {
            _user = user;
            _schema = schema;
            _container = container;
            _availableColumns = availableColumns;
            _rowCount = rowCount;
            _tempTableName = tempTableName;
        }

        /** Number of rows inserted into the temp table. */
        public int getRowCount()
        {
            return _rowCount;
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
    private static final String BOOLEAN_TYPE = StudySchema.getInstance().getSqlDialect().getBooleanDataType();
    private static final String GLOBAL_UNIQUE_ID_TSV_COL = "global_unique_specimen_id";
    private static final String LAB_ID_TSV_COL = "lab_id";
    private static final String SPEC_NUMBER_TSV_COL = "specimen_number";
    private static final String EVENT_ID_COL = "record_id";
    private static final String VISIT_COL = "visit_value";

    // SpecimenEvent columns that form a psuedo-unqiue constraint
    private static final SpecimenColumn GLOBAL_UNIQUE_ID, LAB_ID, SHIP_DATE, STORAGE_DATE, LAB_RECEIPT_DATE;

    public static final Collection<SpecimenColumn> SPECIMEN_COLUMNS = Arrays.asList(
            new SpecimenColumn(EVENT_ID_COL, "ExternalId", "BIGINT NOT NULL", TargetTable.SPECIMEN_EVENTS, true),
            new SpecimenColumn("record_source", "RecordSource", "VARCHAR(10)", TargetTable.SPECIMEN_EVENTS),
            GLOBAL_UNIQUE_ID = new SpecimenColumn(GLOBAL_UNIQUE_ID_TSV_COL, "GlobalUniqueId", "VARCHAR(50)", true, TargetTable.VIALS, true),
            LAB_ID = new SpecimenColumn(LAB_ID_TSV_COL, "LabId", "INT", TargetTable.SPECIMEN_EVENTS, "Site", "ExternalId", "LEFT OUTER") {
                public boolean isUnique() { return true; }
            },
            new SpecimenColumn("originating_location", "OriginatingLocationId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "Site", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("unique_specimen_id", "UniqueSpecimenId", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("ptid", "Ptid", "VARCHAR(32)", true, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("parent_specimen_id", "ParentSpecimenId", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("draw_timestamp", "DrawTimestamp", DATETIME_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("sal_receipt_date", "SalReceiptDate", DATETIME_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn(SPEC_NUMBER_TSV_COL, "SpecimenNumber", "VARCHAR(50)", true, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("class_id", "ClassId", "VARCHAR(4)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn(VISIT_COL, "VisitValue", NUMERIC_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("protocol_number", "ProtocolNumber", "VARCHAR(10)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("visit_description", "VisitDescription", "VARCHAR(3)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("other_specimen_id", "OtherSpecimenId", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("volume", "Volume", "FLOAT", TargetTable.VIALS_AND_SPECIMEN_EVENTS, "MAX"),
            new SpecimenColumn("volume_units", "VolumeUnits", "VARCHAR(3)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("stored", "Stored", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("storage_flag", "storageFlag", "INT", TargetTable.SPECIMEN_EVENTS),
            STORAGE_DATE = new SpecimenColumn("storage_date", "StorageDate", DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS, true),
            new SpecimenColumn("ship_flag", "ShipFlag", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("ship_batch_number", "ShipBatchNumber", "INT", TargetTable.SPECIMEN_EVENTS),
            SHIP_DATE = new SpecimenColumn("ship_date", "ShipDate", DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS, true),
            new SpecimenColumn("imported_batch_number", "ImportedBatchNumber", "INT", TargetTable.SPECIMEN_EVENTS),
            LAB_RECEIPT_DATE = new SpecimenColumn("lab_receipt_date", "LabReceiptDate", DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS, true),
            new SpecimenColumn("expected_time_value", "ExpectedTimeValue", "FLOAT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("expected_time_unit", "ExpectedTimeUnit", "VARCHAR(15)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("group_protocol", "GroupProtocol", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("sub_additive_derivative", "SubAdditiveDerivative", "VARCHAR(20)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("comments", "Comments", "VARCHAR(500)", TargetTable.SPECIMEN_EVENTS),
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
            new SpecimenColumn("processing_time", "ProcessingTime", DURATION_TYPE, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("tube_type", "TubeType", "VARCHAR(64)", TargetTable.VIALS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("total_cell_count", "TotalCellCount", "FLOAT", TargetTable.VIALS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("deviation_code1", "DeviationCode1", "VARCHAR(50)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("deviation_code2", "DeviationCode2", "VARCHAR(50)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("deviation_code3", "DeviationCode3", "VARCHAR(50)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("quality_comments", "QualityComments", "VARCHAR(500)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("yield", "Yield", "FLOAT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("concentration", "Concentration", "FLOAT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("ratio", "Ratio", "FLOAT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("integrity", "Integrity", "FLOAT", TargetTable.SPECIMEN_EVENTS)
            );

    public static final Collection<ImportableColumn> ADDITIVE_COLUMNS = Arrays.asList(
            new ImportableColumn("additive_id", "ExternalId", "INT NOT NULL", true),
            new ImportableColumn("ldms_additive_code", "LdmsAdditiveCode", "VARCHAR(3)"),
            new ImportableColumn("labware_additive_code", "LabwareAdditiveCode", "VARCHAR(20)"),
            new ImportableColumn("additive", "Additive", "VARCHAR(100)")
    );

    public static final Collection<ImportableColumn> DERIVATIVE_COLUMNS = Arrays.asList(
            new ImportableColumn("derivative_id", "ExternalId", "INT NOT NULL", true),
            new ImportableColumn("ldms_derivative_code", "LdmsDerivativeCode", "VARCHAR(3)"),
            new ImportableColumn("labware_derivative_code", "LabwareDerivativeCode", "VARCHAR(20)"),
            new ImportableColumn("derivative", "Derivative", "VARCHAR(100)")
    );

    public static final Collection<ImportableColumn> SITE_COLUMNS = Arrays.asList(
            new ImportableColumn("lab_id", "ExternalId", "INT NOT NULL", true),
            new ImportableColumn("ldms_lab_code", "LdmsLabCode", "INT"),
            new ImportableColumn("labware_lab_code", "LabwareLabCode", "VARCHAR(20)"),
            new ImportableColumn("lab_name", "Label", "VARCHAR(200)"),
            new ImportableColumn("lab_upload_code", "LabUploadCode", "VARCHAR(2)"),
            new ImportableColumn("is_sal", "Sal", BOOLEAN_TYPE),
            new ImportableColumn("is_repository", "Repository", BOOLEAN_TYPE),
            new ImportableColumn("is_clinic", "Clinic", BOOLEAN_TYPE),
            new ImportableColumn("is_endpoint", "Endpoint", BOOLEAN_TYPE)
    );

    public static final Collection<ImportableColumn> PRIMARYTYPE_COLUMNS = Arrays.asList(
            new ImportableColumn("primary_type_id", "ExternalId", "INT NOT NULL", true),
            new ImportableColumn("primary_type_ldms_code", "PrimaryTypeLdmsCode", "VARCHAR(5)"),
            new ImportableColumn("primary_type_labware_code", "PrimaryTypeLabwareCode", "VARCHAR(5)"),
            new ImportableColumn("primary_type", "PrimaryType", "VARCHAR(100)")
    );

    private List<SpecimenColumn> _specimenCols;
    private List<SpecimenColumn> _vialCols;
    private List<SpecimenColumn> _vialEventCols;
    private String _specimenColsSql;
    private String _vialColsSql;
    private String _vialEventColsSql;

    private Logger _logger;

    private static final int SQL_BATCH_SIZE = 100;

    public void process(User user, Container container, VirtualFile specimensDir, boolean merge, Logger logger) throws SQLException, IOException, ValidationException
    {
        Map<String, InputStream> isMap = new HashMap<String, InputStream>();
        createFilemap(specimensDir, isMap);

        // Create a map of Tables->TabLoaders
        Map<String, Iterable<Map<String, Object>>> iterMap = new HashMap<String, Iterable<Map<String, Object>>>();
        iterMap.put("labs", loadTsv(container, SITE_COLUMNS, isMap.get("labs"), "study.Site"));
        iterMap.put("additives", loadTsv(container, ADDITIVE_COLUMNS, isMap.get("additives"), "study.SpecimenAdditive"));
        iterMap.put("derivatives", loadTsv(container, DERIVATIVE_COLUMNS, isMap.get("derivatives"), "study.SpecimenDerivative"));
        iterMap.put("primary_types", loadTsv(container, PRIMARYTYPE_COLUMNS, isMap.get("primary_types"), "study.SpecimenPrimaryType"));
        iterMap.put("specimens", loadTsv(container, SPECIMEN_COLUMNS, isMap.get("specimens"), "study.Specimen"));

        try
        {
            process(user, container, iterMap, merge, logger);
        }
        finally
        {
            // close the tab loaders and any input streams that might still be open
            for (Iterable<Map<String, Object>> map : iterMap.values())
            {
                if (map != null && map instanceof TabLoader)
                    ((TabLoader)map).close();
            }
            for (InputStream is : isMap.values())
            {
                if (is != null)
                    is.close();
            }
        }
    }

    private void resyncStudy(User user, Container container) throws SQLException
    {
        TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
        TableInfo tableSpecimen = StudySchema.getInstance().getTableInfoSpecimen();

        executeSQL(tableParticipant.getSchema(), "INSERT INTO " + tableParticipant + " (container, participantid)\n" +
                "SELECT DISTINCT ?, ptid AS participantid\n" +
                "FROM " + tableSpecimen + "\n" +
                "WHERE container = ? AND ptid IS NOT NULL AND " +
                "ptid NOT IN (select participantid from " + tableParticipant + " where container = ?)", container, container, container);

        StudyImpl study = StudyManager.getInstance().getStudy(container);
        info("Updating study-wide subject/visit information...");
        StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user, Collections.<DataSetDefinition>emptyList());
        info("Subject/visit update complete.");
    }

    private void updateAllStatistics() throws SQLException
    {
        updateStatistics(ExperimentService.get().getTinfoMaterial());
        updateStatistics(StudySchema.getInstance().getTableInfoSpecimen());
        updateStatistics(StudySchema.getInstance().getTableInfoVial());
        updateStatistics(StudySchema.getInstance().getTableInfoSpecimenEvent());
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

    protected void process(User user, Container container, Map<String, Iterable<Map<String, Object>>> iterMap, boolean merge, Logger logger) throws SQLException, IOException, ValidationException
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        _logger = logger;

        try
        {
            DbScope scope = schema.getScope();
            if (!DEBUG)
                scope.beginTransaction();

            mergeTable(schema, container, "study.Site", SITE_COLUMNS, iterMap.get("labs"), true);
            if (merge)
            {
                mergeTable(schema, container, "study.SpecimenAdditive", ADDITIVE_COLUMNS, iterMap.get("additives"), false);
                mergeTable(schema, container, "study.SpecimenDerivative", DERIVATIVE_COLUMNS, iterMap.get("derivatives"), false);
                mergeTable(schema, container, "study.SpecimenPrimaryType", PRIMARYTYPE_COLUMNS, iterMap.get("primary_types"), false);
            }
            else
            {
                replaceTable(schema, container, "study.SpecimenAdditive", ADDITIVE_COLUMNS, iterMap.get("additives"), false);
                replaceTable(schema, container, "study.SpecimenDerivative", DERIVATIVE_COLUMNS, iterMap.get("derivatives"), false);
                replaceTable(schema, container, "study.SpecimenPrimaryType", PRIMARYTYPE_COLUMNS, iterMap.get("primary_types"), false);
            }
            
            // Specimen temp table must be populated AFTER the types tables have been reloaded, since the SpecimenHash
            // calculated in the temp table relies on the new RowIds for the types:
            SpecimenLoadInfo loadInfo = populateTempSpecimensTable(user, schema, container, iterMap.get("specimens"), merge);

            // NOTE: if no rows were loaded in the temp table, don't remove existing materials/specimens/vials/events.
            if (loadInfo.getRowCount() > 0)
                populateSpecimenTables(loadInfo, merge);
            else
                info("Specimens: 0 rows found in input");

            cpuCurrentLocations.start();
            updateCalculatedSpecimenData(container, user, merge, _logger);
            cpuCurrentLocations.stop();
            info("Time to determine locations: " + cpuCurrentLocations.toString());

            resyncStudy(user, container);

            // Set LastSpecimenLoad to now... we'll check this before snapshot study specimen refresh
            StudyImpl study = StudyManager.getInstance().getStudy(container).createMutable();
            study.setLastSpecimenLoad(new Date());
            StudyManager.getInstance().updateStudy(user, study);

            // Drop the temp table within the transaction; otherwise, we may get a different connection object,
            // where the table is no longer available.  Note that this means that the temp table will stick around
            // if an exception is thrown during loading, but this is probably okay- the DB will clean it up eventually.
            executeSQL(schema, "DROP TABLE " + loadInfo.getTempTableName());

            if (!DEBUG)
                scope.commitTransaction();

            updateAllStatistics();
        }
        finally
        {
            schema.getScope().closeConnection();
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

    private SpecimenLoadInfo populateTempSpecimensTable(User user, DbSchema schema, Container container, Iterable<Map<String, Object>> iter, boolean merge) throws SQLException, IOException
    {
        String tableName = createTempTable(schema);
        Pair<List<SpecimenColumn>, Integer> pair = populateTempTable(schema, container, tableName, iter, merge);
        return new SpecimenLoadInfo(user, container, schema, pair.first, pair.second, tableName);
    }


    private void populateSpecimenTables(SpecimenLoadInfo info, boolean merge) throws SQLException, IOException, ValidationException
    {
        if (!merge)
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
        }

        populateMaterials(info, merge);
        populateSpecimens(info, merge);
        populateVials(info, merge);
        populateVialEvents(info, merge);

        if (merge)
        {
            // Delete any orphaned specimen rows without vials
            executeSQL(StudySchema.getInstance().getSchema(), "DELETE FROM " + StudySchema.getInstance().getTableInfoSpecimen() +
                    " WHERE Container=? " +
                    " AND RowId NOT IN (SELECT SpecimenId FROM " + StudySchema.getInstance().getTableInfoVial() + ")", info.getContainer());
        }
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

    private static void clearConflictingVialColumns(Specimen specimen, Set<String> conflicts)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("UPDATE ").append(StudySchema.getInstance().getTableInfoVial()).append(" SET\n  ");

        boolean hasConflict = false;
        String sep = "";
        for (SpecimenColumn col : SPECIMEN_COLUMNS)
        {
            if (col.getAggregateEventFunction() == null && col.getTargetTable().isVials() && !col.isUnique())
            {
                if (conflicts.contains(col.getDbColumnName()))
                {
                    hasConflict = true;
                    sql.append(sep);
                    sql.append(col.getDbColumnName()).append(" = NULL");
                    sep = ",\n  ";
                }
            }
        }

        if (!hasConflict)
            return;

        sql.append("\nWHERE Container = ? AND GlobalUniqueId = ?");
        sql.add(specimen.getContainer().getId());
        sql.add(specimen.getGlobalUniqueId());

        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);
    }

    private static void clearConflictingSpecimenColumns(Specimen specimen, Set<String> conflicts)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("UPDATE ").append(StudySchema.getInstance().getTableInfoSpecimen()).append(" SET\n  ");

        boolean hasConflict = false;
        String sep = "";
        for (SpecimenColumn col : SPECIMEN_COLUMNS)
        {
            if (col.getAggregateEventFunction() == null && col.getTargetTable().isSpecimens() && !col.isUnique())
            {
                if (conflicts.contains(col.getDbColumnName()))
                {
                    hasConflict = true;
                    sql.append(sep);
                    sql.append(col.getDbColumnName()).append(" = NULL");
                    sep = ",\n  ";
                }
            }
        }

        if (!hasConflict)
            return;

        sql.append("\nWHERE Container = ? AND RowId = ?");
        sql.add(specimen.getContainer().getId());
        sql.add(specimen.getSpecimenId());

        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);
    }

    private static void updateCommentSpecimenHashes(Container container, Logger logger)
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
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);
        logger.info("Complete.");
    }

    private static void prepareQCComments(Container container, User user, Logger logger)
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
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(deleteClearedVials);
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
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(insertPlaceholderQCComments);
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
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(orphanMarkerSql);
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
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(deorphanMarkerSql);
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
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(lockedInRequestSql);
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
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);
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
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);
        logger.info("Complete.");

    }

    // UNDONE: add vials in-clause to only update data for rows that changed
    private static void updateCalculatedSpecimenData(Container container, User user, boolean merge, Logger logger) throws SQLException
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
        Map<Integer, LocationImpl> siteMap = new HashMap<Integer, LocationImpl>();
        String vialPropertiesSql = "UPDATE " + StudySchema.getInstance().getTableInfoVial() +
                " SET CurrentLocation = CAST(? AS INTEGER), ProcessingLocation = CAST(? AS INTEGER), " +
                "FirstProcessedByInitials = ?, AtRepository = ?, " +
                "LatestComments = ?, LatestQualityComments = ?, LatestDeviationCode1 = ?, LatestDeviationCode2 = ?, LatestDeviationCode3 = ?, " +
                "LatestConcentration = CAST(? AS REAL), LatestIntegrity = CAST(? AS REAL), LatestRatio = CAST(? AS REAL), LatestYield = CAST(? AS REAL), " +
                "Freezer = ?, Fr_container = ?, Fr_position = ?, Fr_level1 = ?, Fr_level2 = ? " +
                " WHERE RowId = ?";
        String commentSql = "UPDATE " + StudySchema.getInstance().getTableInfoSpecimenComment() +
                " SET QualityControlComments = ? WHERE GlobalUniqueId = ?";
        do
        {
            if (logger != null)
                logger.info("Determining current locations for vials " + (offset + 1) + " through " + (offset + CURRENT_SITE_UPDATE_SIZE) + ".");

            specimens = new TableSelector(StudySchema.getInstance().getTableInfoVial(), containerFilter, null).setMaxRows(CURRENT_SITE_UPDATE_SIZE).setOffset(offset).getArray(Specimen.class);

            List<List<?>> vialPropertiesParams = new ArrayList<List<?>>();
            List<List<?>> commentParams = new ArrayList<List<?>>();

            Map<Specimen, List<SpecimenEvent>> specimenToOrderedEvents = SampleManager.getInstance().getDateOrderedEventLists(specimens);
            Map<Specimen, SpecimenComment> specimenComments = SampleManager.getInstance().getSpecimenComments(specimens);

            for (Map.Entry<Specimen, List<SpecimenEvent>> entry : specimenToOrderedEvents.entrySet())
            {
                Specimen specimen = entry.getKey();
                List<SpecimenEvent> dateOrderedEvents = entry.getValue();
                Integer processingLocation = SampleManager.getInstance().getProcessingLocationId(dateOrderedEvents);
                String firstProcessedByInitials = SampleManager.getInstance().getFirstProcessedByInitials(dateOrderedEvents);
                Integer currentLocation = SampleManager.getInstance().getCurrentLocationId(dateOrderedEvents);
                boolean atRepository = false;
                if (currentLocation != null)
                {
                    LocationImpl location;
                    if (!siteMap.containsKey(currentLocation))
                    {
                        location = StudyManager.getInstance().getLocation(specimen.getContainer(), currentLocation.intValue());
                        if (location != null)
                            siteMap.put(currentLocation, location);
                    }
                    else
                        location = siteMap.get(currentLocation);

                    if (location != null)
                        atRepository = location.isRepository() != null && location.isRepository().booleanValue();
                }

                // All of the additional fields (deviationCodes, Concetration, Integrity, Yield, Ratio, QualityComments, Comments) always take the latest value
                SpecimenEvent lastEvent = SampleManager.getInstance().getLastEvent(dateOrderedEvents);

                if (!safeIntegerEqual(currentLocation, specimen.getCurrentLocation()) ||
                    !safeIntegerEqual(processingLocation, specimen.getProcessingLocation()) ||
                    !safeObjectEquals(firstProcessedByInitials, specimen.getFirstProcessedByInitials()) ||
                    atRepository != specimen.isAtRepository() ||
                    !safeObjectEquals(specimen.getLatestComments(), lastEvent.getComments()) ||
                    !safeObjectEquals(specimen.getLatestQualityComments(), lastEvent.getQualityComments()) ||
                    !safeObjectEquals(specimen.getLatestDeviationCode1(), lastEvent.getDeviationCode1()) ||
                    !safeObjectEquals(specimen.getLatestDeviationCode2(), lastEvent.getDeviationCode2()) ||
                    !safeObjectEquals(specimen.getLatestDeviationCode3(), lastEvent.getDeviationCode3()) ||
                    !safeObjectEquals(specimen.getLatestConcentration(), lastEvent.getConcentration()) ||
                    !safeObjectEquals(specimen.getLatestIntegrity(), lastEvent.getIntegrity()) ||
                    !safeObjectEquals(specimen.getLatestRatio(), lastEvent.getRatio()) ||
                    !safeObjectEquals(specimen.getLatestYield(), lastEvent.getYield()) ||
                    !safeObjectEquals(specimen.getFreezer(), lastEvent.getFreezer()) ||
                    !safeObjectEquals(specimen.getFr_container(), lastEvent.getFr_container()) ||
                    !safeObjectEquals(specimen.getFr_position(), lastEvent.getFr_position()) ||
                    !safeObjectEquals(specimen.getFr_level1(), lastEvent.getFr_level1()) ||
                    !safeObjectEquals(specimen.getFr_level2(), lastEvent.getFr_level2()))
                {
                    List<Object> params = new ArrayList<Object>();
                    params.add(currentLocation);
                    params.add(processingLocation);
                    params.add(firstProcessedByInitials);
                    params.add(atRepository);
                    params.add(lastEvent.getComments());
                    params.add(lastEvent.getQualityComments());
                    params.add(lastEvent.getDeviationCode1());
                    params.add(lastEvent.getDeviationCode2());
                    params.add(lastEvent.getDeviationCode3());
                    params.add(lastEvent.getConcentration());
                    params.add(lastEvent.getIntegrity());
                    params.add(lastEvent.getRatio());
                    params.add(lastEvent.getYield());
                    params.add(lastEvent.getFreezer());
                    params.add(lastEvent.getFr_container());
                    params.add(lastEvent.getFr_position());
                    params.add(lastEvent.getFr_level1());
                    params.add(lastEvent.getFr_level2());
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
                            // Null out conflicting Vial columns
                            if (merge)
                            {
                                // NOTE: in checkForConflictingSpecimens() we check the imported specimen columns used
                                // to generate the specimen hash are not in conflict so we shouldn't need to clear any
                                // columns on the specimen table.  Vial columns are not part of the specimen hash and
                                // can safely be cleared without compromising the specimen hash.
                                //clearConflictingSpecimenColumns(specimen, conflicts);
                                clearConflictingVialColumns(specimen, conflicts);
                            }

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

        try
        {
            RequestabilityManager.getInstance().updateRequestability(container, user, false, logger);
        }
        catch (RequestabilityManager.InvalidRuleException e)
        {
            throw new IllegalStateException("One or more requestability rules is invalid.  Please remove or correct the invalid rule.", e);
        }
        if (logger != null)
            logger.info("Updating cached vial counts...");
        SampleManager.getInstance().updateSpecimenCounts(container, user);
        if (logger != null)
            logger.info("Vial count update complete.");
    }
    
    private void createFilemap(VirtualFile vf, Map<String, InputStream> fileNameMap) throws IOException
    {
        for (String dirName  : vf.listDirs())
        {
            createFilemap(vf.getDir(dirName), fileNameMap);
        }

        for (String fileName : vf.list())
        {
            if (!fileName.toLowerCase().endsWith(".tsv"))
                continue;

            BufferedReader reader = null;
            try
            {
                reader = new BufferedReader(new InputStreamReader(vf.getInputStream(fileName)));
                String line = reader.readLine();
                if (null == line)
                    continue;
                line = StringUtils.trimToEmpty(line);
                if (!line.startsWith("#"))
                    throw new IllegalStateException("Import files are expected to start with a comment indicating table name");
                fileNameMap.put(line.substring(1).trim().toLowerCase(), vf.getInputStream(fileName));

                if (AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_PERFORMANCE_IMPROVEMENTS))
                    IncrementalCreator.generateIncrementalArchive(vf, fileName);
            }
            finally
            {
                if (reader != null) try { reader.close(); } catch (IOException e) {}
            }
        }
    }

    private void info(String message)
    {
        if (_logger != null)
            _logger.info(message);
    }

    private List<SpecimenColumn> getSpecimenCols(List<SpecimenColumn> availableColumns)
    {
        if (_specimenCols == null)
        {
            List<SpecimenColumn> cols = new ArrayList<SpecimenColumn>(availableColumns.size());
            for (SpecimenColumn col : availableColumns)
            {
                if (col.getTargetTable().isSpecimens())
                    cols.add(col);
            }
            _specimenCols = cols;
        }
        return _specimenCols;
    }

    private String getSpecimenColsSql(List<SpecimenColumn> availableColumns)
    {
        if (_specimenColsSql == null)
        {
            String sep = "";
            StringBuilder cols = new StringBuilder();
            for (SpecimenColumn col : getSpecimenCols(availableColumns))
            {
                cols.append(sep).append(col.getDbColumnName());
                sep = ",\n   ";
            }
            _specimenColsSql = cols.toString();
        }
        return _specimenColsSql;
    }

    private List<SpecimenColumn> getVialCols(List<SpecimenColumn> availableColumns)
    {
        if (_vialCols == null)
        {
            List<SpecimenColumn> cols = new ArrayList<SpecimenColumn>(availableColumns.size());
            for (SpecimenColumn col : availableColumns)
            {
                if (col.getTargetTable().isVials())
                    cols.add(col);
            }
            _vialCols = cols;
        }
        return _vialCols;
    }

    private String getVialColsSql(List<SpecimenColumn> availableColumns)
    {
        if (_vialColsSql == null)
        {
            String sep = "";
            StringBuilder cols = new StringBuilder();
            for (SpecimenColumn col : getVialCols(availableColumns))
            {
                cols.append(sep).append(col.getDbColumnName());
                sep = ",\n   ";
            }
            _vialColsSql = cols.toString();
        }
        return _vialColsSql;
    }

    private List<SpecimenColumn> getSpecimenEventCols(List<SpecimenColumn> availableColumns)
    {
        if (_vialEventCols == null)
        {
            List<SpecimenColumn> cols = new ArrayList<SpecimenColumn>(availableColumns.size());
            for (SpecimenColumn col : availableColumns)
            {
                if (col.getTargetTable().isEvents())
                    cols.add(col);
            }
            _vialEventCols = cols;
        }
        return _vialEventCols;
    }

    private String getSpecimenEventColsSql(List<SpecimenColumn> availableColumns)
    {
        if (_vialEventColsSql == null)
        {
            String sep = "";
            StringBuilder cols = new StringBuilder();
            for (SpecimenColumn col : getSpecimenEventCols(availableColumns))
            {
                cols.append(sep).append(col.getDbColumnName());
                sep = ",\n    ";
            }
            _vialEventColsSql = cols.toString();
        }
        return _vialEventColsSql;
    }

    private void populateMaterials(SpecimenLoadInfo info, boolean merge) throws SQLException
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

        String prefix = new Lsid(StudyService.SPECIMEN_NAMESPACE_PREFIX, "Folder-" + info.getContainer().getRowId(), "").toString();

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
            int affected;
            if (!merge)
            {
                info("exp.Material: Deleting entries for removed specimens...");
                SQLFragment deleteFragment = new SQLFragment(deleteSQL, cpasType, info.getContainer().getId());
                if (DEBUG)
                    logSQLFragment(deleteFragment);
                affected = executeSQL(info.getSchema(), deleteFragment);
                if (affected >= 0)
                    info("exp.Material: " + affected + " rows removed.");
            }

            // NOTE: No need to update existing Materials when merging -- just insert any new materials not found.
            info("exp.Material: Inserting new entries from temp table...");
            SQLFragment insertFragment = new SQLFragment(insertSQL, info.getContainer().getId(), cpasType, createdTimestamp);
            if (DEBUG)
                logSQLFragment(insertFragment);
            affected = executeSQL(info.getSchema(), insertFragment);
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
        for (SpecimenColumn col : getSpecimenEventCols(info.getAvailableColumns()))
        {
            columnList.append(prefix);
            prefix = ", ";
            columnList.append("\n    ").append(info.getTempTableName()).append(".").append(col.getDbColumnName());
        }
        return columnList.toString();
    }

    // NOTE: In merge case, we've already checked the specimen hash columns are not in conflict.
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
                singletonAggregate = "CAST(MIN(CAST(" + selectCol + " AS INTEGER)) AS " + dialect.getBooleanDataType()  + ")";
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


    private void populateSpecimens(SpecimenLoadInfo info, boolean merge) throws IOException, SQLException, ValidationException
    {
        String participantSequenceNumExpr = VisitManager.getParticipantSequenceNumExpr(info._schema, "PTID", "VisitValue");

        SQLFragment insertSelectSql = new SQLFragment();
        insertSelectSql.append("SELECT ");
        insertSelectSql.append(participantSequenceNumExpr).append(" AS ParticipantSequenceNum");
        insertSelectSql.append(", Container, SpecimenHash, ");
        insertSelectSql.append(getSpecimenColsSql(info.getAvailableColumns())).append(" FROM (\n");
        insertSelectSql.append(getVialListFromTempTableSql(info)).append(") VialList\n");
        insertSelectSql.append("GROUP BY ").append("Container, SpecimenHash, ");
        insertSelectSql.append(getSpecimenColsSql(info.getAvailableColumns()));

        if (merge)
        {
            Table.TableResultSet rs = null;
            try
            {
                // Create list of specimen columns, including unique columns not found in SPECIMEN_COLUMNS.
                Set<SpecimenColumn> cols = new LinkedHashSet<SpecimenColumn>();
                cols.add(new SpecimenColumn("SpecimenHash", "SpecimenHash", "VARCHAR(256)", TargetTable.SPECIMENS, true));
                cols.add(new SpecimenColumn("ParticipantSequenceNum", "ParticipantSequenceNum", "VARCHAR(200)", TargetTable.SPECIMENS, false));
                cols.addAll(getSpecimenCols(info.getAvailableColumns()));

                // Insert or update the specimens from in the temp table.
                rs = Table.executeQuery(info.getSchema(), insertSelectSql);
                if (VERBOSE_DEBUG)
                    ResultSetUtil.logData(rs, _logger);
                mergeTable(info.getSchema(), info.getContainer(), "study.Specimen", cols, rs, false);
            }
            finally
            {
                if (rs != null) { try { rs.close(); } catch (SQLException _) { } }
            }
        }
        else
        {
            // Insert all specimens from in the temp table.
            SQLFragment insertSql = new SQLFragment();
            insertSql.append("INSERT INTO study.Specimen \n(").append("ParticipantSequenceNum, Container, SpecimenHash, ");
            insertSql.append(getSpecimenColsSql(info.getAvailableColumns())).append(")\n");
            insertSql.append(insertSelectSql);

            if (DEBUG)
                logSQLFragment(insertSql);

            assert cpuInsertSpecimens.start();
            info("Specimens: Inserting new rows...");
            executeSQL(info.getSchema(), insertSql);
            info("Specimens: Insert complete.");
            assert cpuInsertSpecimens.stop();
        }
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

    private void populateVials(SpecimenLoadInfo info, boolean merge) throws SQLException, ValidationException
    {
        String prefix = ",\n    ";
        SQLFragment insertSelectSql = new SQLFragment();
        insertSelectSql.append("SELECT exp.Material.RowId");
        insertSelectSql.append(prefix).append("study.Specimen.RowId AS SpecimenId");
        insertSelectSql.append(prefix).append("study.Specimen.SpecimenHash");
        insertSelectSql.append(prefix).append("VialList.Container");
        insertSelectSql.append(prefix).append("? AS Available");
        // Set a default value of true for the 'Available' column:
        insertSelectSql.add(Boolean.TRUE);

        for (SpecimenColumn col : getVialCols(info.getAvailableColumns()))
            insertSelectSql.append(prefix).append("VialList.").append(col.getDbColumnName());

        insertSelectSql.append(" FROM (").append(getVialListFromTempTableSql(info)).append(") VialList");

        // join to material:
        insertSelectSql.append("\n    JOIN exp.Material ON (");
        insertSelectSql.append("VialList.LSID = exp.Material.LSID");
        insertSelectSql.append(" AND exp.Material.Container = ?)");
        insertSelectSql.add(info.getContainer().getId());

        // join to specimen:
        insertSelectSql.append("\n    JOIN study.Specimen ON study.Specimen.Container = ? ");
        insertSelectSql.add(info.getContainer().getId());
        insertSelectSql.append("AND study.Specimen.SpecimenHash = VialList.SpecimenHash");


        if (merge)
        {
            Table.TableResultSet rs = null;
            try
            {
                // Create list of vial columns, including unique columns not found in SPECIMEN_COLUMNS.
                Set<SpecimenColumn> cols = new LinkedHashSet<SpecimenColumn>();
                // NOTE: study.Vial.RowId is actually an FK to exp.Material.RowId
                cols.add(GLOBAL_UNIQUE_ID);
                cols.add(new SpecimenColumn("RowId", "RowId", "INT NOT NULL", TargetTable.VIALS, false));
                cols.add(new SpecimenColumn("SpecimenId", "SpecimenId", "INT NOT NULL", TargetTable.VIALS, false));
                cols.add(new SpecimenColumn("SpecimenHash", "SpecimenHash", "VARCHAR(256)", TargetTable.VIALS, false));
                cols.add(new SpecimenColumn("Available", "Available", BOOLEAN_TYPE, TargetTable.VIALS, false));
                cols.addAll(getVialCols(info.getAvailableColumns()));

                // Insert or update the vials from in the temp table.
                rs = Table.executeQuery(info.getSchema(), insertSelectSql);
                if (VERBOSE_DEBUG)
                    ResultSetUtil.logData(rs, _logger);
                mergeTable(info.getSchema(), info.getContainer(), "study.Vial", cols, rs, false);
            }
            finally
            {
                if (rs != null) { try { rs.close(); } catch (SQLException _) { } }
            }
        }
        else
        {
            // Insert all vials from in the temp table.
            SQLFragment insertSql = new SQLFragment();
            insertSql.append("INSERT INTO study.Vial \n(RowId, SpecimenId, SpecimenHash, Container, Available, ");
            insertSql.append(getVialColsSql(info.getAvailableColumns())).append(")\n");
            insertSql.append(insertSelectSql);

            if (DEBUG)
                logSQLFragment(insertSql);

            assert cpuInsertSpecimens.start();
            info("Vials: Inserting new rows...");
            executeSQL(info.getSchema(), insertSql);
            info("Vials: Insert complete.");
            assert cpuInsertSpecimens.stop();
        }
    }

    private void logSQLFragment(SQLFragment sql)
    {
        info(sql.getSQL());
        info("Params: ");
        for (Object param : sql.getParams())
            info(param.toString());
    }

    private void populateVialEvents(SpecimenLoadInfo info, boolean merge) throws SQLException, ValidationException
    {
        SQLFragment insertSelectSql = new SQLFragment();
        insertSelectSql.append("SELECT study.Vial.Container, study.Vial.RowId AS VialId, \n");
        insertSelectSql.append(getSpecimenEventTempTableColumns(info));
        insertSelectSql.append(" FROM ");
        insertSelectSql.append(info.getTempTableName()).append("\nJOIN study.Vial ON ");
        insertSelectSql.append(info.getTempTableName()).append(".GlobalUniqueId = study.Vial.GlobalUniqueId AND study.Vial.Container = ?");
        insertSelectSql.add(info.getContainer().getId());

        if (merge)
        {
            Table.TableResultSet rs = null;
            try
            {
                // Create list of vial columns, including unique columns not found in SPECIMEN_COLUMNS.
                // Events are special in that we want to merge based on a pseudo-unique set of columns:
                //    Container, VialId (vial.GlobalUniqueId), LabId, StorageDate, ShipDate, LabReceiptDate
                // We need to always add these extra columns, even if they aren't in the list of available columns.
                Set<SpecimenColumn> cols = new LinkedHashSet<SpecimenColumn>();
                cols.add(new SpecimenColumn("VialId", "VialId", "INT NOT NULL", TargetTable.SPECIMEN_EVENTS, true));
                cols.add(LAB_ID);
                cols.add(SHIP_DATE);
                cols.add(STORAGE_DATE);
                cols.add(LAB_RECEIPT_DATE);
                for (SpecimenColumn col : getSpecimenEventCols(info.getAvailableColumns()))
                {
                    cols.add(col);
                }

                // Insert or update the vials from in the temp table.
                rs = Table.executeQuery(info.getSchema(), insertSelectSql);
                if (VERBOSE_DEBUG)
                    ResultSetUtil.logData(rs, _logger);
                mergeTable(info.getSchema(), info.getContainer(), "study.SpecimenEvent", cols, rs, false);
            }
            finally
            {
                if (rs != null) { try { rs.close(); } catch (SQLException _) { } }
            }
        }
        else
        {
            // Insert all events from the temp table
            SQLFragment insertSql = new SQLFragment();
            insertSql.append("INSERT INTO study.SpecimenEvent\n");
            insertSql.append("(Container, VialId, " + getSpecimenEventColsSql(info.getAvailableColumns()) + ")\n");
            insertSql.append(insertSelectSql);

            if (DEBUG)
                logSQLFragment(insertSql);
            assert cpuInsertSpecimenEvents.start();
            info("Specimen Events: Inserting new rows.");
            executeSQL(info.getSchema(), insertSql);
            info("Specimen Events: Insert complete.");
            assert cpuInsertSpecimenEvents.stop();
        }
    }

    private interface ComputedColumn
    {
        String getName();
        Object getValue(Map<String, Object> row);
    }

    private class EntityIdComputedColumn implements ComputedColumn
    {
        public String getName() { return "EntityId"; }
        public Object getValue(Map<String, Object> row) { return GUID.makeGUID(); }
    }

    private <T extends ImportableColumn> Pair<List<T>, Integer> mergeTable(
            DbSchema schema, Container container, String tableName,
            Collection<T> potentialColumns, Iterable<Map<String, Object>> values, boolean addEntityId)
            throws SQLException, ValidationException
    {
        ComputedColumn entityIdCol = null;
        if (addEntityId)
        {
            entityIdCol = new EntityIdComputedColumn();
        }

        return mergeTable(schema, container, tableName, potentialColumns, values, entityIdCol);
    }

    private void appendEqualCheck(DbSchema schema, StringBuilder sql, ImportableColumn col)
    {
        String dialectType = schema.getSqlDialect().sqlTypeNameFromSqlType(col.getSQLType().sqlType);
        String paramCast = "CAST(? AS " + dialectType + ")";
        // Each unique col has two parameters in the null-equals check.
        sql.append("(").append(col.getDbColumnName()).append(" IS NULL AND ").append(paramCast).append(" IS NULL)");
        sql.append(" OR ").append(col.getDbColumnName()).append(" = ").append(paramCast);
    }

    /**
     * Insert or update rows on the target table using the unique columns of <code>potentialColumns</code>
     * to identify the existing row.
     *
     * NOTE: The idCol is used only during insert -- the value won't be updated if the row already exists.
     *
     * @param schema The dbschema.
     * @param container The container.
     * @param tableName Fully qualified table name, e.g., "study.Vials"
     * @param potentialColumns List of columns to be inserted/updated on the table.
     * @param values The data values to be inserted or updated.
     * @param idCol The computed column.
     * @return A pair of the columns actually found in the data values and a total row count.
     * @throws SQLException
     */
    private <T extends ImportableColumn> Pair<List<T>, Integer> mergeTable(
            DbSchema schema, Container container, String tableName,
            Collection<T> potentialColumns, Iterable<Map<String, Object>> values,
            ComputedColumn idCol)
        throws SQLException, ValidationException
    {
        if (values == null)
        {
            info(tableName + ": No rows to merge");
            return new Pair<List<T>, Integer>(Collections.<T>emptyList(), 0);
        }
        Iterator<Map<String, Object>> iter = values.iterator();

        assert cpuMergeTable.start();
        info(tableName + ": Starting merge of data...");

        List<T> availableColumns = new ArrayList<T>();
        List<T> uniqueCols = new ArrayList<T>();

        StringBuilder selectSql = new StringBuilder();
        StringBuilder insertSql = new StringBuilder();
        List<Parameter> parametersInsert = new ArrayList<Parameter>();
        Parameter.ParameterMap parameterMapInsert = null;
        PreparedStatement stmtInsert = null;

        StringBuilder updateSql = new StringBuilder();
        List<Parameter> parametersUpdate = new ArrayList<Parameter>();
        Parameter.ParameterMap parameterMapUpdate = null;
        PreparedStatement stmtUpdate = null;

        int rowCount = 0;
        Connection conn = null;

        try
        {
            conn = schema.getScope().getConnection();
            int rowsAdded = 0;
            int rowsUpdated = 0;

            while (iter.hasNext())
            {
                Map<String, Object> row = iter.next();
                rowCount++;

                if (1 == rowCount)
                {
                    for (T column : potentialColumns)
                    {
                        if (row.containsKey(column.getTsvColumnName()) || row.containsKey(column.getDbColumnName()))
                            availableColumns.add(column);
                    }

                    for (T col : availableColumns)
                    {
                        if (col.isUnique())
                            uniqueCols.add(col);
                    }

                    selectSql.append("SELECT * FROM ").append(tableName).append(" WHERE Container = ? ");
                    for (ImportableColumn col : uniqueCols)
                    {
                        selectSql.append(" AND (");
                        appendEqualCheck(schema, selectSql, col);
                        selectSql.append(")\n");
                    }
                    if (DEBUG)
                    {
                        info(tableName + ": select sql:");
                        info(selectSql.toString());
                    }

                    int p = 1;
                    insertSql.append("INSERT INTO ").append(tableName).append(" (Container");
                    parametersInsert.add(new Parameter("Container", p++, JdbcType.VARCHAR));
                    if (idCol != null)
                    {
                        insertSql.append(", ").append(idCol.getName());
                        parametersInsert.add(new Parameter(idCol.getName(), p++, JdbcType.VARCHAR));
                    }
                    for (ImportableColumn col : availableColumns)
                    {
                        insertSql.append(", ").append(col.getDbColumnName());
                        parametersInsert.add(new Parameter(col.getDbColumnName(), p++, col.getSQLType()));
                    }
                    insertSql.append(") VALUES (?");
                    if (idCol != null)
                        insertSql.append(", ?");
                    insertSql.append(StringUtils.repeat(", ?", availableColumns.size()));
                    insertSql.append(")");
                    stmtInsert = conn.prepareStatement(insertSql.toString());
                    parameterMapInsert = new Parameter.ParameterMap(schema.getScope(), stmtInsert, parametersInsert);
                    if (DEBUG)
                    {
                        info(tableName + ": insert sql:");
                        info(insertSql.toString());
                    }

                    p = 1;
                    updateSql.append("UPDATE ").append(tableName).append(" SET ");
                    String separator = "";
                    for (ImportableColumn col : availableColumns)
                    {
                        if (!col.isUnique())
                        {
                            updateSql.append(separator).append(col.getDbColumnName()).append(" = ?");
                            separator = ", ";
                            parametersUpdate.add(new Parameter(col.getDbColumnName(), p++, col.getSQLType()));
                        }
                    }
                    updateSql.append(" WHERE Container = ?\n");
                    parametersUpdate.add(new Parameter("Container", p++, JdbcType.VARCHAR));
                    for (ImportableColumn col : availableColumns)
                    {
                        if (col.isUnique())
                        {
                            updateSql.append(" AND (");
                            appendEqualCheck(schema, updateSql, col);
                            updateSql.append(")\n");
                            parametersUpdate.add(new Parameter(col.getDbColumnName(), new int[] { p++, p++ }, col.getSQLType()));
                        }
                    }
                    stmtUpdate = conn.prepareStatement(updateSql.toString());
                    parameterMapUpdate = new Parameter.ParameterMap(schema.getScope(), stmtUpdate, parametersUpdate);
                    if (DEBUG)
                    {
                        info(tableName + ": update sql:");
                        info(updateSql.toString());
                    }
                }

                boolean rowExists = false;
                if (!uniqueCols.isEmpty())
                {
                    ResultSet rs = null;
                    try
                    {
                        Object[] params = new Object[(2*uniqueCols.size()) + 1];
                        int colIndex = 0;
                        params[colIndex++] = container.getId();
                        for (ImportableColumn col : uniqueCols)
                        {
                            // Each unique col has two parameters in the null-equals check.
                            Object value = getValueParameter(col, row);
                            params[colIndex++] = value;
                            params[colIndex++] = value;
                        }

                        rs = Table.executeQuery(schema, selectSql.toString(), params);
                        if (rs.next())
                            rowExists = true;
                        if (VERBOSE_DEBUG)
                            info((rowExists ? "Row exists" : "Row does NOT exist") + " matching:\n" + JdbcUtil.format(new SQLFragment(selectSql, params)));
                    }
                    finally
                    {
                        if (rs != null) try { rs.close(); } catch (SQLException e) {}
                    }
                }

                if (!rowExists)
                {
                    parameterMapInsert.clearParameters();
                    parameterMapInsert.put("Container", container.getId());
                    if (idCol != null)
                        parameterMapInsert.put(idCol.getName(), idCol.getValue(row));
                    for (ImportableColumn col : availableColumns)
                        parameterMapInsert.put(col.getDbColumnName(), getValueParameter(col, row));
                    if (VERBOSE_DEBUG)
                        info(stmtInsert.toString());
                    stmtInsert.execute();
                    rowsAdded++;
                }
                else
                {
                    parameterMapUpdate.clearParameters();
                    for (ImportableColumn col : availableColumns)
                    {
                        Object value = getValueParameter(col, row);
                        parameterMapUpdate.put(col.getDbColumnName(), value);
                    }
                    parameterMapUpdate.put("Container", container.getId());
                    if (VERBOSE_DEBUG)
                        info(stmtUpdate.toString());
                    stmtUpdate.execute();
                    rowsUpdated++;
                }
            }

            info(tableName + ": inserted " + rowsAdded + " new rows, updated " + rowsUpdated + " rows.  (" + rowCount + " rows found in input file.)");
        }
        finally
        {
            if (iter instanceof CloseableIterator) try { ((CloseableIterator)iter).close(); } catch (IOException ioe) { }
            if (null != conn)
                schema.getScope().releaseConnection(conn);    
        }
        assert cpuMergeTable.stop();

        return new Pair<List<T>, Integer>(availableColumns, rowCount);
    }


    private <T extends ImportableColumn> Pair<List<T>, Integer> replaceTable(
            DbSchema schema, Container container, String tableName,
            Collection<T> potentialColumns, Iterable<Map<String, Object>> values, boolean addEntityId)
        throws IOException, SQLException
    {
        ComputedColumn entityIdCol = null;
        if (addEntityId)
        {
            entityIdCol = new EntityIdComputedColumn();
        }

        return replaceTable(schema, container, tableName, potentialColumns, values, entityIdCol);
    }

    /**
     * Deletes the target table and inserts new rows.
     *
     * @param schema The dbschema.
     * @param container The container.
     * @param tableName Fully qualified table name, e.g., "study.Vials"
     * @param potentialColumns List of columns to be inserted/updated on the table.
     * @param values The data values to be inserted or updated.
     * @param computedColumns The computed column.
     * @return A pair of the columns actually found in the data values and a total row count.
     * @throws SQLException
     */
    private <T extends ImportableColumn> Pair<List<T>, Integer> replaceTable(
            DbSchema schema, Container container, String tableName,
            Collection<T> potentialColumns, Iterable<Map<String, Object>> values,
            ComputedColumn... computedColumns)
        throws IOException, SQLException
    {
        if (values == null)
        {
            info(tableName + ": No rows to replace");
            return new Pair<List<T>, Integer>(Collections.<T>emptyList(), 0);
        }
        Iterator<Map<String, Object>> iter = values.iterator();

        assert cpuMergeTable.start();
        info(tableName + ": Starting replacement of all data...");

        executeSQL(schema, "DELETE FROM " + tableName + " WHERE Container = ?", container.getId());

        // boundColumns is the same as availableColumns, skipping any columns that are computed
        List<T> availableColumns = new ArrayList<T>();
        List<T> boundColumns = new ArrayList<T>();
        LinkedHashMap<String,ComputedColumn> computedColumnsMap = new LinkedHashMap<String,ComputedColumn>();
        for (ComputedColumn cc: computedColumns)
            if (null != cc)
                computedColumnsMap.put(cc.getName(), cc);

        StringBuilder insertSql = new StringBuilder();

        List<List<Object>> rows = new ArrayList<List<Object>>();
        int rowCount = 0;

        try
        {
            while (iter.hasNext())
            {
                Map<String, Object> row = iter.next();
                rowCount++;

                if (1 == rowCount)
                {
                    for (T column : potentialColumns)
                    {
                        if (row.containsKey(column.getTsvColumnName()) || row.containsKey(column.getDbColumnName()))
                        {
                            availableColumns.add(column);
                            if (!computedColumnsMap.containsKey(column.getDbColumnName()))
                                boundColumns.add(column);
                        }
                    }

                    insertSql.append("INSERT INTO ").append(tableName).append(" (Container");
                    for (ComputedColumn cc : computedColumnsMap.values())
                        insertSql.append(", ").append(cc.getName());
                    for (ImportableColumn col : boundColumns)
                        insertSql.append(", ").append(col.getDbColumnName());
                    insertSql.append(") VALUES (?");
                    insertSql.append(StringUtils.repeat(", ?", computedColumnsMap.size() + boundColumns.size()));
                    insertSql.append(")");

                    if (DEBUG)
                        info(insertSql.toString());
                }

                List<Object> params = new ArrayList<Object>(computedColumnsMap.size() + boundColumns.size() + 1);
                params.add(container.getId());

                for (ComputedColumn cc : computedColumns)
                    if (null != cc) params.add(cc.getValue(row));

                for (ImportableColumn col : boundColumns)
                {
                    Object value = getValueParameter(col, row);
                    params.add(value);
                }

                rows.add(params);

                if (rows.size() == SQL_BATCH_SIZE)
                {
                    Table.batchExecute(schema, insertSql.toString(), rows);
                    rows = new ArrayList<List<Object>>(SQL_BATCH_SIZE);
                    // output a message every 100 batches (every 10,000 events, by default)
                    if (rowCount % (SQL_BATCH_SIZE*100) == 0)
                        info(rowCount + " rows loaded...");
                }
            }

            // No point in trying to insert zero rows.  Also, insertSql won't be set if no rows exist.
            if (!rows.isEmpty())
                Table.batchExecute(schema, insertSql.toString(), rows);

            info(tableName + ": Replaced all data with " + rowCount + " new rows.");
        }
        finally
        {
            if (iter instanceof CloseableIterator) try { ((CloseableIterator)iter).close(); } catch (IOException ioe) { }
        }
        assert cpuMergeTable.stop();

        return new Pair<List<T>, Integer>(availableColumns, rowCount);
    }


    private TabLoader loadTsv(Container container, Collection<? extends ImportableColumn> columns, InputStream tsvIS, String tableName) throws IOException, SQLException
    {
        if (tsvIS == null)
        {
            info(tableName + ": no data to merge.");
            return null;
        }

        info(tableName + ": Parsing data file for table...");
        Map<String, ColumnDescriptor> expectedColumns = new HashMap<String, ColumnDescriptor>(columns.size());

        for (ImportableColumn col : columns)
            expectedColumns.put(col.getTsvColumnName().toLowerCase(), col.getColumnDescriptor());

        Reader reader = new BufferedReader(new InputStreamReader(tsvIS));
        TabLoader loader = new TabLoader(reader, true);

        for (ColumnDescriptor column : loader.getColumns())
        {
            ColumnDescriptor expectedColumnDescriptor = expectedColumns.get(column.name.toLowerCase());

            if (expectedColumnDescriptor != null)
            {
                column.clazz = expectedColumnDescriptor.clazz;
                if (VISIT_COL.equals(column.name))
                    column.clazz = String.class;
            }
            else
            {
                column.load = false;
            }
        }

        info(tableName + ": Parsing complete.");
        return loader;
    }


    private Pair<List<SpecimenColumn>, Integer> populateTempTable(
            DbSchema schema, final Container container, String tempTable,
            Iterable<Map<String, Object>> iter, boolean merge)
        throws SQLException, IOException
    {
        assert cpuPopulateTempTable.start();

        info("Populating specimen temp table...");
        int rowCount;
        List<SpecimenColumn> loadedColumns;

        ComputedColumn lsidCol = new ComputedColumn()
        {
            public String getName() { return "LSID"; }
            public Object getValue(Map<String, Object> row)
            {
                String id = (String) row.get(GLOBAL_UNIQUE_ID_TSV_COL);
                if (id == null)
                    id = (String) row.get(SPEC_NUMBER_TSV_COL);

                Lsid lsid = SpecimenService.get().getSpecimenMaterialLsid(container, id);
                return lsid.toString();
            }
        };

        // remove VISIT_COL since that's a computed column
        // 1) should that be removed from SPECIMEN_COLUMNS?
        // 2) convert this to ETL?
        SpecimenColumn _visitCol = null;
        SpecimenColumn _dateCol = null;
        for (SpecimenColumn sc : SPECIMEN_COLUMNS)
        {
            if (StringUtils.equals("VisitValue",sc.getDbColumnName()))
                _visitCol = sc;
            else if (StringUtils.equals("DrawTimestamp", sc.getDbColumnName()))
                _dateCol = sc;
        }

        Study study = StudyManager.getInstance().getStudy(container);
        final SequenceNumImportHelper h = new SequenceNumImportHelper(study, null);
        final SpecimenColumn visitCol = _visitCol;
        final SpecimenColumn dateCol = _dateCol;
        final Parameter.TypedValue nullDouble = Parameter.nullParameter(JdbcType.DOUBLE);
        ComputedColumn sequencenumCol = new ComputedColumn()
        {
            @Override
            public String getName()
            {
                return visitCol.getDbColumnName();
            }

            @Override
            public Object getValue(Map<String, Object> row)
            {
                Object s = SpecimenImporter.this.getValue(visitCol, row);
                Object d = SpecimenImporter.this.getValue(dateCol, row);
                Double sequencenum = h.translateSequenceNum(s,d);
//                if (sequencenum == null)
//                    throw new org.apache.commons.beanutils.ConversionException("No visit_value provided: visit_value=" + String.valueOf(s) + " draw_timestamp=" + String.valueOf(d));
                if (null == sequencenum)
                    return nullDouble;
                return sequencenum;
            }
        };

        Pair<List<SpecimenColumn>, Integer> pair = replaceTable(schema, container, tempTable, SPECIMEN_COLUMNS, iter, lsidCol, sequencenumCol);

        loadedColumns = pair.first;
        rowCount = pair.second;

        if (rowCount == 0)
        {
            info("Found no specimen columns to import. Temp table will not be loaded.");
            return pair;
        }

        remapTempTableLookupIndexes(schema, container, tempTable, loadedColumns);

        updateTempTableVisits(schema, container, tempTable);

        if (merge)
        {
            checkForConflictingSpecimens(schema, container, tempTable, loadedColumns);
        }

        updateTempTableSpecimenHash(schema, container, tempTable, loadedColumns);

        info("Specimen temp table populated.");
        return pair;
    }

    private void remapTempTableLookupIndexes(DbSchema schema, Container container, String tempTable, List<SpecimenColumn> loadedColumns)
            throws SQLException
    {
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
        executeSQL(schema, remapExternalIdsSql);
        info("Update complete.");
    }

    private void updateTempTableVisits(DbSchema schema, Container container, String tempTable)
            throws SQLException
    {
        Study study = StudyManager.getInstance().getStudy(container);
        if (study.getTimepointType() != TimepointType.VISIT)
        {
            info("Updating visit values to match draw timestamps (date-based studies only)...");
            SQLFragment visitValueSql = new SQLFragment();
            visitValueSql.append("UPDATE " + tempTable + " SET VisitValue = (");
            visitValueSql.append(StudyManager.sequenceNumFromDateSQL("DrawTimestamp"));
            visitValueSql.append(");");
            if (DEBUG)
                info(visitValueSql.toString());
            executeSQL(schema, visitValueSql);
            info("Update complete.");
        }
    }

    private void checkForConflictingSpecimens(DbSchema schema, Container container, String tempTable, List<SpecimenColumn> loadedColumns)
            throws SQLException
    {
        info("Checking for conflicting specimens before merging...");

        // Columns used in the specimen hash
        StringBuilder hashCols = new StringBuilder();
        for (SpecimenColumn col : loadedColumns)
        {
            if (col.getTargetTable().isSpecimens() && col.getAggregateEventFunction() == null)
            {
                hashCols.append(",\n\t");
                hashCols.append(col.getDbColumnName());
            }
        }
        hashCols.append("\n");

        SQLFragment existingEvents = new SQLFragment("SELECT Vial.Container, GlobalUniqueId");
        existingEvents.append(hashCols);
        existingEvents.append("FROM ").append(StudySchema.getInstance().getTableInfoVial(), "Vial").append("\n");
        existingEvents.append("JOIN ").append(StudySchema.getInstance().getTableInfoSpecimen(), "Specimen").append("\n");
        existingEvents.append("ON Vial.SpecimenId = Specimen.RowId\n");
        existingEvents.append("WHERE Vial.Container=?\n").add(container.getId());
        existingEvents.append("AND Vial.GlobalUniqueId IN (SELECT GlobalUniqueId FROM ").append(tempTable).append(")\n");

        SQLFragment tempTableEvents = new SQLFragment("SELECT Container, GlobalUniqueId");
        tempTableEvents.append(hashCols);
        tempTableEvents.append("FROM ").append(tempTable);

        // "UNION ALL" the temp and the existing tables and group by columns used in the specimen hash
        SQLFragment allEventsByHashCols = new SQLFragment("SELECT COUNT(*) AS Group_Count, * FROM (\n");
        allEventsByHashCols.append("(\n").append(existingEvents).append("\n)\n");
        allEventsByHashCols.append("UNION ALL\n");
        allEventsByHashCols.append("(\n").append(tempTableEvents).append("\n)\n");
        allEventsByHashCols.append(") U\n");
        allEventsByHashCols.append("GROUP BY Container, GlobalUniqueId");
        allEventsByHashCols.append(hashCols);

        Map<String, List<Map<String, Object>>> rowsByGUID = new HashMap<String, List<Map<String, Object>>>();
        Set<String> duplicateGUIDs = new TreeSet<String>();
        Map<String, Object>[] allEventsByHashColsResults = Table.executeQuery(schema, allEventsByHashCols, Map.class);
        for (Map<String, Object> row : allEventsByHashColsResults)
        {
            String guid = (String)row.get("GlobalUniqueId");
            if (guid != null)
            {
                if (rowsByGUID.containsKey(guid))
                {
                    // Found a duplicate
                    List<Map<String, Object>> dups = rowsByGUID.get(guid);
                    dups.add(row);
                    duplicateGUIDs.add(guid);
                }
                else
                {
                    rowsByGUID.put(guid, new ArrayList<Map<String, Object>>(Arrays.asList(row)));
                }
            }
        }

        if (duplicateGUIDs.size() == 0)
        {
            info("No conflicting specimens found");
        }
        else
        {
            StringBuilder sb = new StringBuilder();
            for (String guid : duplicateGUIDs)
            {
                List<Map<String, Object>> dups = rowsByGUID.get(guid);
                if (dups != null && dups.size() > 0)
                {
                    if (sb.length() > 0)
                        sb.append("\n");
                    sb.append("Conflicting specimens found for GlobalUniqueId '").append(guid).append("':\n");

                    for (Map<String, Object> row : dups)
                    {
                        // CONSIDER: if we want to be really fancy, we could diff the columns to find the conflicting value.
                        for (SpecimenColumn col : loadedColumns)
                            if (col.getTargetTable().isSpecimens() && col.getAggregateEventFunction() == null)
                                sb.append("  ").append(col.getDbColumnName()).append("=").append(row.get(col.getDbColumnName())).append("\n");
                        sb.append("\n");
                    }
                }
            }

            _logger.error(sb);

            // If conflicts are found, stop the import.
            throw new IllegalStateException(sb.toString());
        }
    }

    private void updateTempTableSpecimenHash(DbSchema schema, Container container, String tempTable, List<SpecimenColumn> loadedColumns)
            throws SQLException
    {
        // NOTE: In merge case, we've already checked the specimen hash columns are not in conflict.
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
                        singletonAggregate = "CAST(MIN(CAST(" + selectCol + " AS INTEGER)) AS " + schema.getSqlDialect().getBooleanDataType()  + ")";
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
        updateHashSql.append("UPDATE ").append(tempTable).append(" SET SpecimenHash = ");
        ArrayList<String> hash = new ArrayList<String>(loadedColumns.size());
        hash.add("?");
        updateHashSql.add("Fld-" + container.getRowId());
        String strType = schema.getSqlDialect().sqlTypeNameFromSqlType(Types.VARCHAR);

        for (SpecimenColumn col : loadedColumns)
        {
            if (col.getTargetTable().isSpecimens())
            {
                String columnName = "InnerTable." + col.getDbColumnName();
                hash.add("'~'");
                hash.add(" CASE WHEN " + columnName + " IS NOT NULL THEN CAST(" + columnName + " AS " + strType + ") ELSE '' END");
            }
        }

        updateHashSql.append(schema.getSqlDialect().concatenate(hash.toArray(new String[hash.size()])));
        updateHashSql.append("\nFROM (").append(conflictResolvingSubselect).append(") InnerTable WHERE ");
        updateHashSql.append(tempTable).append(".GlobalUniqueId = InnerTable.GlobalUniqueId");

        info("Updating specimen hash values in temp table...");
        if (DEBUG)
            info(updateHashSql.toString());
        executeSQL(schema, updateHashSql);
        info("Update complete.");
        info("Temp table populated.");
    }


    private Object getValue(ImportableColumn col, Map tsvRow)
    {
        Object value = null;
        if (tsvRow.containsKey(col.getTsvColumnName()))
            value = tsvRow.get(col.getTsvColumnName());
        else if (tsvRow.containsKey(col.getDbColumnName()))
            value = tsvRow.get(col.getDbColumnName());
        return value;
    }


    private Parameter.TypedValue getValueParameter(ImportableColumn col, Map tsvRow)
            throws SQLException
    {
        Object value = getValue(col, tsvRow);

        if (value == null)
            return Parameter.nullParameter(col.getSQLType());
        Parameter.TypedValue typed = new Parameter.TypedValue(value, col.getSQLType());

        if (col.getMaxSize() >= 0)
        {
            Object valueToBind = Parameter.getValueToBind(typed, col.getSQLType());
            if (valueToBind != null)
            {
                if (valueToBind.toString().length() > col.getMaxSize())
                {
                    throw new SQLException("Value \"" + valueToBind.toString() + "\" is too long for column " +
                            col.getDbColumnName() + ".  The maximum allowable length is " + col.getMaxSize() + ".");
                }
            }
        }

        return typed;
    }

    private static final boolean DEBUG = false;
    private static final boolean VERBOSE_DEBUG = false;

    private String createTempTable(DbSchema schema) throws SQLException
    {
        assert cpuCreateTempTable.start();
        try
        {
            info("Creating temp table to hold archive data...");
            SqlDialect dialect = schema.getSqlDialect();
            String tableName;
            StringBuilder sql = new StringBuilder();
            int randomizer = (new Random().nextInt(900000000) + 100000000);  // Ensure 9-digit random number
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
            String strType = dialect.sqlTypeNameFromSqlType(Types.VARCHAR);
            sql.append("\n(\n    RowId ").append(dialect.getUniqueIdentType()).append(", ");
            sql.append("Container ").append(strType).append("(300) NOT NULL, ");
            sql.append("LSID ").append(strType).append("(300) NOT NULL, ");
            sql.append("SpecimenHash ").append(strType).append("(300)");
            for (SpecimenColumn col : SPECIMEN_COLUMNS)
                sql.append(",\n    ").append(col.getDbColumnName()).append(" ").append(col.getDbType());
            sql.append("\n);");
            executeSQL(schema, sql.toString());

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
            executeSQL(schema, globalUniqueIdIndexSql);
            executeSQL(schema, rowIdIndexSql);
            executeSQL(schema, lsidIndexSql);
            executeSQL(schema, hashIndexSql);
            info("Created temporary table " + tableName);

            return tableName;
        }
        finally
        {
            assert cpuCreateTempTable.stop();
        }
    }

    public static class TestCase extends Assert
    {
        private DbSchema _schema;
        private String _tableName;
        private static final String TABLE = "SpecimenImporterTest";

        @Before
        public void createTable() throws SQLException
        {
            _schema = TestSchema.getInstance().getSchema();

            _tableName = _schema.getName() + "." + TABLE;
            dropTable();

            new SqlExecutor(_schema).execute("CREATE TABLE " + _tableName +
                    "(Container VARCHAR(255) NOT NULL, id VARCHAR(10), s VARCHAR(32), i INTEGER)");
        }

        @After
        public void dropTable() throws SQLException
        {
            _schema.dropTableIfExists(TABLE);
        }

        private Table.TableResultSet selectValues()
                throws SQLException
        {
            return Table.executeQuery(_schema, "SELECT Container,id,s,i FROM " + _tableName + " ORDER BY id", null);
        }

        private Map<String, Object> row(String s, Integer i)
        {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("s", s);
            map.put("i", i);
            return map;
        }

        @Test
        public void mergeTest() throws Exception
        {
            Container c = JunitUtil.getTestContainer();

            Collection<ImportableColumn> cols = Arrays.asList(
                    new ImportableColumn("s", "s", "VARCHAR(32)", true),
                    new ImportableColumn("i", "i", "INTEGER", false)
            );

            Iterable<Map<String, Object>> values = Arrays.asList(
                    row("Bob", 100),
                    row("Sally", 200),
                    row(null, 300)
            );

            SpecimenImporter importer = new SpecimenImporter();
            final Integer[] counter = new Integer[] { 0 };
            ComputedColumn idCol = new ComputedColumn()
            {
                public String getName() { return "id"; }
                public Object getValue(Map<String, Object> row)
                {
                    return String.valueOf(++counter[0]);
                }
            };

            // Insert rows
            Pair<List<ImportableColumn>, Integer> pair = importer.mergeTable(_schema, c, _tableName, cols, values, idCol);
            assertNotNull(pair);
            assertEquals(pair.first.size(), cols.size());
            assertEquals(3, pair.second.intValue());
            assertEquals(3, counter[0]);

            Table.TableResultSet rs = selectValues();
            try
            {
                Iterator<Map<String, Object>> iter = rs.iterator();
                Map<String, Object> row0 = iter.next();
                assertEquals("Bob", row0.get("s"));
                assertEquals(100, row0.get("i"));
                assertEquals("1", row0.get("id"));

                Map<String, Object> row1 = iter.next();
                assertEquals("Sally", row1.get("s"));
                assertEquals(200, row1.get("i"));
                assertEquals("2", row1.get("id"));

                Map<String, Object> row2 = iter.next();
                assertEquals(null, row2.get("s"));
                assertEquals(300, row2.get("i"));
                assertEquals("3", row2.get("id"));
                assertFalse(iter.hasNext());
            }
            finally
            {
                if (rs != null) try { rs.close(); } catch (SQLException e) { }
            }

            // Add one new row, update one existing row.
            values = Arrays.asList(
                    row("Bob", 105),
                    row(null, 305),
                    row("Jimmy", 405)
            );
            pair = importer.mergeTable(_schema, c, _tableName, cols, values, idCol);
            assertEquals(pair.first.size(), cols.size());
            assertEquals(3, pair.second.intValue());
            assertEquals(4, counter[0]);

            rs = selectValues();
            try
            {
                Iterator<Map<String, Object>> iter = rs.iterator();
                Map<String, Object> row0 = iter.next();
                assertEquals("Bob", row0.get("s"));
                assertEquals(105, row0.get("i"));
                assertEquals("1", row0.get("id"));

                Map<String, Object> row1 = iter.next();
                assertEquals("Sally", row1.get("s"));
                assertEquals(200, row1.get("i"));
                assertEquals("2", row1.get("id"));

                Map<String, Object> row2 = iter.next();
                assertEquals(null, row2.get("s"));
                assertEquals(305, row2.get("i"));
                assertEquals("3", row2.get("id"));

                Map<String, Object> row3 = iter.next();
                assertEquals("Jimmy", row3.get("s"));
                assertEquals(405, row3.get("i"));
                assertEquals("4", row3.get("id"));
                assertFalse(iter.hasNext());
            }
            finally
            {
                if (rs != null) try { rs.close(); } catch (SQLException e) { }
            }
        }
    }


    private int executeSQL(DbSchema schema, String sql, Object... params) throws SQLException
    {
        if (DEBUG && _logger != null)
            _logger.debug(sql);
        return Table.execute(schema, sql, params);
    }


    private int executeSQL(DbSchema schema, SQLFragment sql)
    {
        if (DEBUG && _logger != null)
            _logger.debug(sql.toString());
        return new SqlExecutor(schema).execute(sql);
    }
}
