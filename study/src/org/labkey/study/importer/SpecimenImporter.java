/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.data.Selector.ForEachBatchBlock;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.ListofMapsDataIterator;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.dataiterator.Pump;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.StandardDataIteratorBuilder;
import org.labkey.api.dataiterator.TableInsertDataIterator;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListImportProgress;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.iterator.MarkableIterator;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.Location;
import org.labkey.api.study.SpecimenImportStrategy;
import org.labkey.api.study.SpecimenImportStrategyFactory;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MultiPhaseCPUTimer;
import org.labkey.api.util.MultiPhaseCPUTimer.InvocationTimer.Order;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.TimeOnlyDate;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.SpecimenManager;
import org.labkey.study.SpecimenServiceImpl;
import org.labkey.study.StudySchema;
import org.labkey.study.model.ParticipantIdImportHelper;
import org.labkey.study.model.SequenceNumImportHelper;
import org.labkey.study.model.SpecimenComment;
import org.labkey.study.model.SpecimenEvent;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Vial;
import org.labkey.study.query.LocationTable;
import org.labkey.study.query.SpecimenTablesProvider;
import org.labkey.study.visitmanager.VisitManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;

/**
 * User: brittp
 * Date: Mar 13, 2006
 * Time: 2:18:48
 */
@SuppressWarnings({"AssertWithSideEffects", "ConstantConditions"})
public class SpecimenImporter
{
    private enum ImportPhases {UpdateCommentSpecimenHashes, MarkOrphanedRequestVials, SetLockedInRequest, VialUpdatePreLoopPrep,
        GetVialBatch, GetDateOrderedEvents, GetSpecimenComments, GetProcessingLocationId, GetFirstProcessedBy, GetCurrentLocationId,
        CalculateLocation, GetLastEvent, DetermineUpdateVial, SetUpdateParameters, HandleComments, UpdateVials, UpdateComments,
        UpdateSpecimenProcessingInfo, UpdateRequestability, UpdateVialCounts, ResyncStudy, SetLastSpecimenLoad, DropTempTable,
        UpdateAllStatistics, CommitTransaction, ClearCaches, PopulateMaterials, PopulateSpecimens, PopulateVials, PopulateSpecimenEvents,
        PopulateTempTable, PopulateLabs, SpecimenTypes, DeleteOldData, PrepareQcComments, NotifyChanged}

    private static MultiPhaseCPUTimer<ImportPhases> TIMER = new MultiPhaseCPUTimer<>(ImportPhases.class, ImportPhases.values());

    public static class ImportableColumn
    {
        protected final String _dbType;

        private final String _tsvColumnName;
        private final String _dbColumnName;
        private final boolean _maskOnExport;
        private final boolean _unique;
        private final int _size;
        private final Class _javaClass;
        private final JdbcType _jdbcType;
        private Object _defaultValue = null;

        public ImportableColumn(String tsvColumnName, String dbColumnName, String databaseType)
        {
            this(tsvColumnName, dbColumnName, databaseType, false);
        }

        public ImportableColumn(String tsvColumnName, String dbColumnName, String databaseType, Object defaultValue)
        {
            this(tsvColumnName, dbColumnName, databaseType, false);
            _defaultValue = defaultValue;
        }

        public ImportableColumn(String tsvColumnName, String dbColumnName, String databaseType, boolean unique)
        {
            this(tsvColumnName, dbColumnName, databaseType, unique, false);
        }

        public ImportableColumn(String tsvColumnName, String dbColumnName, String databaseType, boolean unique, boolean maskOnExport)
        {
            _tsvColumnName = tsvColumnName;
            _dbColumnName = dbColumnName;
            _unique = unique;
            _maskOnExport = maskOnExport;

            switch (databaseType)
            {
                case DURATION_TYPE:
                    _dbType = StudySchema.getInstance().getSqlDialect().getDefaultDateTimeDataType();
                    _javaClass = TimeOnlyDate.class;
                    break;
                case DATETIME_TYPE:
                    _dbType = StudySchema.getInstance().getSqlDialect().getDefaultDateTimeDataType();
                    _javaClass = Date.class;
                    break;
                default:
                    _dbType = databaseType.toUpperCase();
                    _javaClass = determineJavaType(_dbType);
                    break;
            }

            _jdbcType = JdbcType.valueOf(getJavaClass());

            if (_dbType.startsWith("VARCHAR("))
            {
                assert _dbType.charAt(_dbType.length() - 1) == ')' : "Unexpected VARCHAR type format: " + _dbType;
                String sizeStr = _dbType.substring(8, _dbType.length() - 1);
                _size = Integer.parseInt(sizeStr);
            }
            else
            {
                _size = -1;
            }
        }

        // Can't use standard JdbcType.valueOf() method since this uses contains()
        private static Class determineJavaType(String dbType)
        {
            if (dbType.contains(DATETIME_TYPE))
                throw new IllegalStateException("Java types for DateTime/Timestamp columns should be previously initialized.");

            if (dbType.contains("VARCHAR"))
                return String.class;
            else if (dbType.contains("FLOAT") || dbType.contains("DOUBLE") || dbType.contains(NUMERIC_TYPE))
                return Double.class;
            else if (dbType.contains("BIGINT"))
                return Long.class;
            else if (dbType.contains("INT"))
                return Integer.class;
            else if (dbType.contains(BOOLEAN_TYPE))
                return Boolean.class;
            else if (dbType.contains(BINARY_TYPE))
                return byte[].class;
            else if (dbType.contains("DATE"))
                return Date.class;
            else if (dbType.contains("TIME"))
                return Date.class;
            else
                throw new UnsupportedOperationException("Unrecognized sql type: " + dbType);
        }

        public ColumnDescriptor getColumnDescriptor()
        {
            return new ColumnDescriptor(_tsvColumnName, getJavaClass());
        }

        public String getDbColumnName()
        {
            return _dbColumnName;
        }

        private String _legalDbColumnName;

        public String getLegalDbColumnName(SqlDialect dialect)
        {
            if (null == _legalDbColumnName)
                _legalDbColumnName = PropertyDescriptor.getLegalSelectNameFromStorageName(dialect, getDbColumnName());
            return _legalDbColumnName;
        }

        public String getTsvColumnName()
        {
            return _tsvColumnName;
        }

        public boolean isUnique()
        {
            return _unique;
        }

        public Class getJavaClass()
        {
            return _javaClass;
        }

        public JdbcType getJdbcType()
        {
            return _jdbcType;
        }

        public int getMaxSize()
        {
            return _size;
        }

        public boolean isMaskOnExport()
        {
            return _maskOnExport;
        }

        public @Nullable Object getDefaultValue()
        {
            return _defaultValue;
        }
    }

    public enum TargetTable
    {
        SPECIMEN_EVENTS
        {
            public List<String> getTableNames()
            {
                List<String> names = new ArrayList<>(1);
                names.add("SpecimenEvent");
                return names;
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
            }
        },
        SPECIMENS
        {
            public List<String> getTableNames()
            {
                List<String> names = new ArrayList<>(1);
                names.add("Specimen");
                return names;
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
            }
        },
        VIALS
        {
            public List<String> getTableNames()
            {
                List<String> names = new ArrayList<>(1);
                names.add("Vial");
                return names;
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
            public List<String> getTableNames()
            {
                List<String> names = new ArrayList<>(1);
                names.add("Specimen");
                names.add("SpecimenEvent");
                return names;
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
            public List<String> getTableNames()
            {
                List<String> names = new ArrayList<>(1);
                names.add("Vial");
                names.add("SpecimenEvent");
                return names;
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
            }
        };

        public abstract boolean isEvents();
        public abstract boolean isVials();
        public abstract boolean isSpecimens();
        public abstract List<String> getTableNames();
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

//        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType,
//                              TargetTable eventColumn, String fkTable, String fkColumn)
//        {
//            this(tsvColumnName, dbColumnName, databaseType, eventColumn, fkTable, fkColumn, "INNER");
//        }
//
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
            return getDbType() != null && (getDbType().equals("DATETIME") || getDbType().equals("TIMESTAMP")) && !getJavaClass().equals(TimeOnlyDate.class);
        }
    }

    private static class SpecimenLoadInfo
    {
        TempTableInfo _tempTableInfo;
        private final String _tempTableName;
        private final List<SpecimenColumn> _availableColumns;
        private final int _rowCount;
        private final Container _container;
        private final User _user;
        private final DbSchema _schema;

        public SpecimenLoadInfo(User user, Container container, DbSchema schema, List<SpecimenColumn> availableColumns, int rowCount, TempTableInfo tempTableInfo)
        {
            _user = user;
            _schema = schema;
            _container = container;
            _availableColumns = availableColumns;
            _rowCount = rowCount;
            _tempTableName = tempTableInfo.getSelectName();
            _tempTableInfo = tempTableInfo;
        }

        // Number of rows inserted into the temp table
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

    /**
     * Rollups from one table to another. Patterns specify what the To table must be to match,
     * where '%' is the full name of the From field name.
     */
    public interface Rollup
    {
        List<String> getPatterns();
        boolean isTypeConstraintMet(JdbcType from, JdbcType to);
        boolean match(PropertyDescriptor from, PropertyDescriptor to, boolean allowTypeMismatch);
    }

    public enum EventVialRollup implements Rollup
    {
        EventVialLatest
        {
            public List<String> getPatterns()
            {
                return Arrays.asList("%", "Latest%");
            }
            public Object getRollupResult(List<SpecimenEvent> events, String eventColName, JdbcType fromType, JdbcType toType)
            {
                // Input is SpecimenEvent list
                if (null == events || events.isEmpty())
                    return null;
                if (!(events.get(0) instanceof SpecimenEvent))
                    throw new IllegalStateException("Expected SpecimenEvents.");
                return SpecimenManager.getInstance().getLastEvent(events).get(eventColName);
            }
            public boolean isTypeConstraintMet(JdbcType from, JdbcType to)
            {
                return from.equals(to) || canPromoteNumeric(from, to);
            }
        },
        EventVialFirst
        {
            public List<String> getPatterns()
            {
                return Arrays.asList("First%");
            }
            public Object getRollupResult(List<SpecimenEvent> events, String eventColName, JdbcType fromType, JdbcType toType)
            {
                // Input is SpecimenEvent list
                if (null == events || events.isEmpty())
                    return null;
                if (!(events.get(0) instanceof SpecimenEvent))
                    throw new IllegalStateException("Expected SpecimenEvents.");
                return SpecimenManager.getInstance().getFirstEvent(events).get(eventColName);
            }
            public boolean isTypeConstraintMet(JdbcType from, JdbcType to)
            {
                return from.equals(to) || canPromoteNumeric(from, to);
            }
        },
        EventVialLatestNonBlank
        {
            public List<String> getPatterns()
            {
                return Arrays.asList("LatestNonBlank%");
            }
            public Object getRollupResult(List<SpecimenEvent> events, String eventColName, JdbcType fromType, JdbcType toType)
            {
                // Input is SpecimenEvent list
                if (null == events || events.isEmpty())
                    return null;
                if (!(events.get(0) instanceof SpecimenEvent))
                    throw new IllegalStateException("Expected SpecimenEvents.");
                ListIterator<SpecimenEvent> iterator = events.listIterator(events.size());
                Object result = null;
                while (iterator.hasPrevious())
                {
                    SpecimenEvent event = iterator.previous();
                    if (null == event)
                        continue;
                    result = event.get(eventColName);
                    if (null == result)
                        continue;
                    if (result instanceof String && StringUtils.isBlank((String)result))
                        continue;
                    break;
                }
                return result;
            }
            public boolean isTypeConstraintMet(JdbcType from, JdbcType to)
            {
                return from.equals(to) || canPromoteNumeric(from, to);
            }
        },
        EventVialCombineAll
        {
            public List<String> getPatterns()
            {
                return Arrays.asList("Combine%");
            }
            public Object getRollupResult(List<SpecimenEvent> events, String eventColName, JdbcType fromType, JdbcType toType)
            {
                // Input is SpecimenEvent list
                if (null == events || events.isEmpty())
                    return null;
                if (!(events.get(0) instanceof SpecimenEvent))
                    throw new IllegalStateException("Expected SpecimenEvents.");
                Object result = null;

                if (toType.isText())
                {
                    for (SpecimenEvent event : events)
                    {
                        if (null != event)
                        {
                            Object value = event.get(eventColName);
                            if (null != value)
                            {
                                if (value instanceof String)
                                {
                                    if (!StringUtils.isBlank((String)value))
                                    {
                                        if (null == result)
                                            result = value;
                                        else
                                            result = result + ", " + value;
                                    }
                                }
                                else
                                {
                                    throw new IllegalStateException("Expected String type.");
                                }
                            }
                        }
                    }
                }
                else if (toType.isNumeric())
                {
                    for (SpecimenEvent event : events)
                    {
                        if (null != event)
                        {
                            Object value = event.get(eventColName);
                            if (null != value)
                            {
                                if (null == result)
                                    result = value;
                                else
                                    result = JdbcType.add (result, value, toType);
                            }
                        }
                    }
                }
                else
                    throw new IllegalStateException("CombineAll rollup is supported on String or Numeric types only.");

                return result;
            }

            public boolean isTypeConstraintMet(JdbcType from, JdbcType to)
            {
                return (from.equals(to) || canPromoteNumeric(from, to)) &&
                        (from.isNumeric() || from.isText());
            }
        };


        // Gets the field value from a particular object in the list (used for event -> vial rollups)
        public abstract Object getRollupResult(List<SpecimenEvent> objs, String colName, JdbcType fromType, JdbcType toType);

        public boolean match(PropertyDescriptor from, PropertyDescriptor to, boolean allowTypeMismatch)
        {
            for (String pattern : getPatterns())
            {
                if (pattern.replace("%", from.getName()).equalsIgnoreCase(to.getName()) && (allowTypeMismatch || isTypeConstraintMet(from.getJdbcType(), to.getJdbcType())))
                    return true;
            }
            return false;
        }
    }

    public enum VialSpecimenRollup implements Rollup
    {
        VialSpecimenCount
        {
            public List<String> getPatterns()
            {
                return Arrays.asList("Count%", "%Count");
            }
            public SQLFragment getRollupSql(String fromColName, String toColName)
            {
                SQLFragment sql = new SQLFragment("SUM(CASE ");
                sql.append(fromColName).append(" WHEN ? THEN 1 ELSE 0 END) AS ").append(toColName);
                sql.add(Boolean.TRUE);
                return sql;
            }
            public boolean isTypeConstraintMet(JdbcType from, JdbcType to)
            {
                return JdbcType.BOOLEAN.equals(from) && to.isInteger();
            }
        },
        VialSpecimenTotal
        {
            public List<String> getPatterns()
            {
                return Arrays.asList("Total%", "%Total", "SumOf%");
            }
            public SQLFragment getRollupSql(String fromColName, String toColName)
            {
                SQLFragment sql = new SQLFragment("SUM(");
                sql.append(fromColName).append(") AS ").append(toColName);
                return sql;
            }
            public boolean isTypeConstraintMet(JdbcType from, JdbcType to)
            {
                return from.isNumeric() && to.isNumeric();
            }
        },
        VialSpecimenMaximum
        {
            public List<String> getPatterns()
            {
                return Arrays.asList("Max%", "%Max");
            }
            public SQLFragment getRollupSql(String fromColName, String toColName)
            {
                SQLFragment sql = new SQLFragment("MAX(");
                sql.append(fromColName).append(") AS ").append(toColName);
                return sql;
            }
            public boolean isTypeConstraintMet(JdbcType from, JdbcType to)
            {
                return !JdbcType.BOOLEAN.equals(from) &&
                        (from.equals(to) || canPromoteNumeric(from, to));
            }
        },
        VialSpecimenMinimum
        {
            public List<String> getPatterns()
            {
                return Arrays.asList("Min%", "%Min");
            }
            public SQLFragment getRollupSql(String fromColName, String toColName)
            {
                SQLFragment sql = new SQLFragment("MIN(");
                sql.append(fromColName).append(") AS ").append(toColName);
                return sql;
            }
            public boolean isTypeConstraintMet(JdbcType from, JdbcType to)
            {
                return !JdbcType.BOOLEAN.equals(from) &&
                        (from.equals(to) || canPromoteNumeric(from, to));
            }
        };

        // Gets SQL to calulate rollup (used for vial -> specimen rollups)
        public abstract SQLFragment getRollupSql(String fromColName, String toColName);

        public boolean match(PropertyDescriptor from, PropertyDescriptor to, boolean allowTypeMismtach)
        {
            for (String pattern : getPatterns())
            {
                if (pattern.replace("%", from.getName()).equalsIgnoreCase(to.getName()) && (allowTypeMismtach || isTypeConstraintMet(from.getJdbcType(), to.getJdbcType())))
                    return true;
            }
            return false;
        }
    }

    private static boolean canPromoteNumeric(JdbcType from, JdbcType to)
    {
        return (from.isNumeric() && to.isNumeric() && JdbcType.promote(from, to) == to);
    }

    public static class RollupInstance<K extends Rollup> extends Pair<String, K>
    {
        private final JdbcType _fromType;
        private final JdbcType _toType;

        public RollupInstance(String first, K second, JdbcType fromType, JdbcType toType)
        {
            super(first.toLowerCase(), second);
            _fromType = fromType;
            _toType = toType;
        }

        public JdbcType getFromType()
        {
            return _fromType;
        }

        public JdbcType getToType()
        {
            return _toType;
        }

        public boolean isTypeConstraintMet()
        {
            return second.isTypeConstraintMet(_fromType, _toType);
        }
    }

    public static class RollupMap<K extends Rollup> extends CaseInsensitiveHashMap<List<RollupInstance<K>>>
    {
    }

    private static final List<EventVialRollup> _eventVialRollups = Arrays.asList(EventVialRollup.values());
    private static final List<VialSpecimenRollup> _vialSpecimenRollups = Arrays.asList(VialSpecimenRollup.values());

    public static List<VialSpecimenRollup> getVialSpecimenRollups()
    {
        return _vialSpecimenRollups;
    }

    public static List<EventVialRollup> getEventVialRollups()
    {
        return _eventVialRollups;
    }

    private static final CaseInsensitiveHashSet _eventFieldNamesDisallowedForRollups = new CaseInsensitiveHashSet(
        "RowId", "VialId", "LabId", "PTID", "PrimaryTypeId", "AdditiveTypeId", "DerivativeTypeId", "DerivativeTypeId2", "OriginatingLocationId"
    );

    private static final CaseInsensitiveHashSet _vialFieldNamesDisallowedForRollups = new CaseInsensitiveHashSet(
        "RowId", "SpecimenId"
    );

    public static CaseInsensitiveHashSet getEventFieldNamesDisallowedForRollups()
    {
        return _eventFieldNamesDisallowedForRollups;
    }

    public static CaseInsensitiveHashSet getVialFieldNamesDisallowedForRollups()
    {
        return _vialFieldNamesDisallowedForRollups;
    }

    protected static final String GLOBAL_UNIQUE_ID_TSV_COL = "global_unique_specimen_id";

    private static final String DATETIME_TYPE = "SpecimenImporter/DateTime";
    private static final String DURATION_TYPE = "SpecimenImporter/TimeOnlyDate";
    private static final String NUMERIC_TYPE = "NUMERIC(15,4)";
    private static final String BOOLEAN_TYPE = StudySchema.getInstance().getSqlDialect().getBooleanDataType();
    private static final String BINARY_TYPE = StudySchema.getInstance().getSqlDialect().getBinaryDataType();
    private static final String LAB_ID_TSV_COL = "lab_id";
    private static final String SPEC_NUMBER_TSV_COL = "specimen_number";
    private static final String EVENT_ID_COL = "record_id";
    private static final String VISIT_COL = "visit_value";

    private static final String GENERAL_JOB_STATUS_MSG = "PROCESSING SPECIMENS";

    // SpecimenEvent columns that form a psuedo-unqiue constraint
    private static final SpecimenColumn GLOBAL_UNIQUE_ID, LAB_ID, SHIP_DATE, STORAGE_DATE, LAB_RECEIPT_DATE, DRAW_TIMESTAMP;
    private static final SpecimenColumn VISIT_VALUE;

    public static final List<SpecimenColumn> BASE_SPECIMEN_COLUMNS = Arrays.asList(
            new SpecimenColumn(EVENT_ID_COL, "ExternalId", "BIGINT NOT NULL", TargetTable.SPECIMEN_EVENTS, true),
            new SpecimenColumn("record_source", "RecordSource", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            GLOBAL_UNIQUE_ID = new SpecimenColumn(GLOBAL_UNIQUE_ID_TSV_COL, "GlobalUniqueId", "VARCHAR(50)", true, TargetTable.VIALS, true),
            LAB_ID = new SpecimenColumn(LAB_ID_TSV_COL, "LabId", "INT", TargetTable.SPECIMEN_EVENTS, "Site", "ExternalId", "LEFT OUTER") {
                public boolean isUnique() { return true; }
            },
            new SpecimenColumn("originating_location", "OriginatingLocationId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "Site", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("unique_specimen_id", "UniqueSpecimenId", "VARCHAR(50)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("ptid", "Ptid", "VARCHAR(32)", true, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("parent_specimen_id", "ParentSpecimenId", "INT", TargetTable.SPECIMEN_EVENTS),
            DRAW_TIMESTAMP = new SpecimenColumn("draw_timestamp", "DrawTimestamp", DATETIME_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("sal_receipt_date", "SalReceiptDate", DATETIME_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn(SPEC_NUMBER_TSV_COL, "SpecimenNumber", "VARCHAR(50)", true, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("class_id", "ClassId", "VARCHAR(20)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            VISIT_VALUE = new SpecimenColumn(VISIT_COL, "VisitValue", NUMERIC_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("protocol_number", "ProtocolNumber", "VARCHAR(20)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("visit_description", "VisitDescription", "VARCHAR(10)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("other_specimen_id", "OtherSpecimenId", "VARCHAR(50)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("volume", "Volume", "FLOAT", TargetTable.VIALS_AND_SPECIMEN_EVENTS, "MAX"),
            new SpecimenColumn("volume_units", "VolumeUnits", "VARCHAR(20)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
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
            new SpecimenColumn("sub_additive_derivative", "SubAdditiveDerivative", "VARCHAR(50)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("comments", "Comments", "VARCHAR(500)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("primary_specimen_type_id", "PrimaryTypeId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "SpecimenPrimaryType", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("derivative_type_id", "DerivativeTypeId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "SpecimenDerivative", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("derivative_type_id_2", "DerivativeTypeId2", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "SpecimenDerivative", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("additive_type_id", "AdditiveTypeId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "SpecimenAdditive", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("specimen_condition", "SpecimenCondition", "VARCHAR(30)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("sample_number", "SampleNumber", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("x_sample_origin", "XSampleOrigin", "VARCHAR(50)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("external_location", "ExternalLocation", "VARCHAR(50)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("update_timestamp", "UpdateTimestamp", DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("requestable", "Requestable", BOOLEAN_TYPE, TargetTable.VIALS),
            new SpecimenColumn("shipped_from_lab", "ShippedFromLab", "VARCHAR(32)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("shipped_to_lab", "ShippedtoLab", "VARCHAR(32)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("frozen_time", "FrozenTime", DURATION_TYPE, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("primary_volume", "PrimaryVolume", "FLOAT", TargetTable.VIALS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("primary_volume_units", "PrimaryVolumeUnits", "VARCHAR(20)", TargetTable.VIALS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("processed_by_initials", "ProcessedByInitials", "VARCHAR(32)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("processing_date", "ProcessingDate", DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("processing_time", "ProcessingTime", DURATION_TYPE, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("quality_comments", "QualityComments", "VARCHAR(500)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("total_cell_count", "TotalCellCount", "FLOAT", TargetTable.VIALS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("tube_type", "TubeType", "VARCHAR(64)", TargetTable.VIALS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("input_hash", "InputHash", BINARY_TYPE, TargetTable.SPECIMEN_EVENTS)   // Not pulled from file... maybe this should be a ComputedColumn
    );

    public static final Collection<ImportableColumn> ADDITIVE_COLUMNS = Arrays.asList(
            new ImportableColumn("additive_id", "ExternalId", "INT NOT NULL", true),
            new ImportableColumn("ldms_additive_code", "LdmsAdditiveCode", "VARCHAR(30)"),
            new ImportableColumn("labware_additive_code", "LabwareAdditiveCode", "VARCHAR(20)"),
            new ImportableColumn("additive", "Additive", "VARCHAR(100)")
    );

    public static final Collection<ImportableColumn> DERIVATIVE_COLUMNS = Arrays.asList(
            new ImportableColumn("derivative_id", "ExternalId", "INT NOT NULL", true),
            new ImportableColumn("ldms_derivative_code", "LdmsDerivativeCode", "VARCHAR(30)"),
            new ImportableColumn("labware_derivative_code", "LabwareDerivativeCode", "VARCHAR(20)"),
            new ImportableColumn("derivative", "Derivative", "VARCHAR(100)")
    );

    public static final Collection<ImportableColumn> SITE_COLUMNS = Arrays.asList(
            new ImportableColumn("lab_id", "ExternalId", "INT NOT NULL", true),
            new ImportableColumn("ldms_lab_code", "LdmsLabCode", "INT"),
            new ImportableColumn("labware_lab_code", "LabwareLabCode", "VARCHAR(20)", false, true),
            new ImportableColumn("lab_name", "Label", "VARCHAR(200)", false, true),
            new ImportableColumn("lab_upload_code", "LabUploadCode", "VARCHAR(10)"),
            new ImportableColumn("is_sal", "Sal", BOOLEAN_TYPE, Boolean.FALSE),
            new ImportableColumn("is_repository", "Repository", BOOLEAN_TYPE, Boolean.FALSE),
            new ImportableColumn("is_clinic", "Clinic", BOOLEAN_TYPE, Boolean.FALSE),
            new ImportableColumn("is_endpoint", "Endpoint", BOOLEAN_TYPE, Boolean.FALSE),
            new ImportableColumn("street_address", "StreetAddress", "VARCHAR(200)", false, true),
            new ImportableColumn("city", "City", "VARCHAR(200)", false, true),
            new ImportableColumn("governing_district", "GoverningDistrict", "VARCHAR(200)", false, true),
            new ImportableColumn("country", "Country", "VARCHAR(200)", false, true),
            new ImportableColumn("postal_area", "PostalArea", "VARCHAR(50)", false, true),
            new ImportableColumn("description", "Description", "VARCHAR(500)", false, true)
    );

    public static final Collection<ImportableColumn> PRIMARYTYPE_COLUMNS = Arrays.asList(
            new ImportableColumn("primary_type_id", "ExternalId", "INT NOT NULL", true),
            new ImportableColumn("primary_type_ldms_code", "PrimaryTypeLdmsCode", "VARCHAR(5)"),
            new ImportableColumn("primary_type_labware_code", "PrimaryTypeLabwareCode", "VARCHAR(5)"),
            new ImportableColumn("primary_type", "PrimaryType", "VARCHAR(100)")
    );

    private static final SpecimenColumn DRAW_DATE = new SpecimenColumn("", "DrawDate", "DATE", TargetTable.SPECIMENS);
    private static final SpecimenColumn DRAW_TIME = new SpecimenColumn("", "DrawTime", "TIME", TargetTable.SPECIMENS);

    private static final Map<JdbcType, String> JDBCtoIMPORTER_TYPE = new HashMap<>();
    static
    {
        JDBCtoIMPORTER_TYPE.put(JdbcType.DATE, DATETIME_TYPE);
        JDBCtoIMPORTER_TYPE.put(JdbcType.TIMESTAMP, DATETIME_TYPE);
        JDBCtoIMPORTER_TYPE.put(JdbcType.TIME, DURATION_TYPE);
        JDBCtoIMPORTER_TYPE.put(JdbcType.DECIMAL, NUMERIC_TYPE);
        JDBCtoIMPORTER_TYPE.put(JdbcType.BOOLEAN, BOOLEAN_TYPE);
        JDBCtoIMPORTER_TYPE.put(JdbcType.BINARY, BINARY_TYPE);
        JDBCtoIMPORTER_TYPE.put(JdbcType.BIGINT, "BIGINT");
        JDBCtoIMPORTER_TYPE.put(JdbcType.INTEGER, "INT");
        JDBCtoIMPORTER_TYPE.put(JdbcType.REAL, "FLOAT");
        JDBCtoIMPORTER_TYPE.put(JdbcType.DOUBLE, null);
        JDBCtoIMPORTER_TYPE.put(JdbcType.VARCHAR, "VARCHAR");
    }

    private List<SpecimenColumn> _specimenCols;
    private List<SpecimenColumn> _vialCols;
    private List<SpecimenColumn> _vialEventCols;
    private String _specimenColsSql;
    private String _vialColsSql;
    private String _vialEventColsSql;
    private Logger _logger;
    private PipelineJob _job;

    protected int _generateGlobalUniqueIds = 0;

    private static final int SQL_BATCH_SIZE = 100;

    public static class SpecimenTableType
    {
        private final String _name;
        private final String _tableName;
        private final Collection<? extends ImportableColumn> _columns;

        public SpecimenTableType(String name, String tableName, Collection<? extends ImportableColumn> columns)
        {
            _name = name;
            _tableName = tableName;
            _columns = columns;
        }

        public String getName()
        {
            return _name;
        }

        public String getTableName()
        {
            return _tableName;
        }

        public Collection<? extends ImportableColumn> getColumns()
        {
            return _columns;
        }
    }

    protected final SpecimenTableType _labsTableType;
    protected final SpecimenTableType _additivesTableType;
    protected final SpecimenTableType _derivativesTableType;
    protected final SpecimenTableType _primaryTypesTableType;
    protected final SpecimenTableType _specimensTableType;

    public @Nullable SpecimenTableType getForName(String name)
    {
        if (_labsTableType.getName().equalsIgnoreCase(name)) return _labsTableType;
        if (_additivesTableType.getName().equalsIgnoreCase(name)) return _additivesTableType;
        if (_derivativesTableType.getName().equalsIgnoreCase(name)) return _derivativesTableType;
        if (_primaryTypesTableType.getName().equalsIgnoreCase(name)) return _primaryTypesTableType;
        if ("specimens".equalsIgnoreCase(name)) return _specimensTableType;
        return null;
    }

    private String getTypeName(PropertyDescriptor property, SqlDialect dialect)
    {
        StudySchema.getInstance().getScope().getSqlDialect();
        String typeName = JDBCtoIMPORTER_TYPE.get(property.getJdbcType());
        if (null == typeName)
            typeName = dialect.sqlTypeNameFromSqlType(property.getJdbcType().sqlType);
        if (null == typeName)
            throw new UnsupportedOperationException("Unsupported JdbcType: " + property.getJdbcType().toString());
        if ("VARCHAR".equals(typeName))
            typeName = String.format("VARCHAR(%d)", property.getScale());
        return typeName;
    }

    // Event -> Vial Rollup map
    private RollupMap<EventVialRollup> _eventToVialRollups = new RollupMap<>();

    private final Container _container;
    private final User _user;

    // Provisioned specimen tables
    private final TableInfo _tableInfoSpecimen;
    private final TableInfo _tableInfoVial;
    private final TableInfo _tableInfoSpecimenEvent;
    private final TableInfo _tableInfoLocation;
    private final TableInfo _tableInfoPrimaryType;
    private final TableInfo _tableInfoDerivative;
    private final TableInfo _tableInfoAdditive;
    private final SqlDialect _dialect;

    private MultiPhaseCPUTimer.InvocationTimer<ImportPhases> _iTimer;

    private TableInfo getTableInfoSpecimen()
    {
        return _tableInfoSpecimen;
    }

    private TableInfo getTableInfoVial()
    {
        return _tableInfoVial;
    }

    private TableInfo getTableInfoSpecimenEvent()
    {
        return _tableInfoSpecimenEvent;
    }

    private TableInfo getTableInfoPrimaryType()
    {
        return _tableInfoPrimaryType;
    }
    private TableInfo getTableInfoLocation()
    {
        return _tableInfoLocation;
    }

    private TableInfo getTableInfoDerivative()
    {
        return _tableInfoDerivative;
    }

    private TableInfo getTableInfoAdditive()
    {
        return _tableInfoAdditive;
    }

    public TableInfo getTableInfoFromFkTableName(String fkTableName)
    {
        if ("Site".equalsIgnoreCase(fkTableName))
            return getTableInfoLocation();
        if ("SpecimenPrimaryType".equalsIgnoreCase(fkTableName))
            return getTableInfoPrimaryType();
        if ("SpecimenDerivative".equalsIgnoreCase(fkTableName))
            return getTableInfoDerivative();
        if ("SpecimenAdditive".equalsIgnoreCase(fkTableName))
            return getTableInfoAdditive();
        throw new IllegalStateException("Unexpected table name.");
    }

    protected Container getContainer()
    {
        return _container;
    }

    protected User getUser()
    {
        return _user;
    }

    protected final Collection<SpecimenColumn> _specimenColumns;

    public Collection<SpecimenColumn> getSpecimenColumns()
    {
        return _specimenColumns;
    }

    /**
     * Constructor
     * @param container import location
     * @param user user whose permissions will be checked during import
     */
    public SpecimenImporter(Container container, User user)
    {
        _container = container;
        _user = user;

        _tableInfoSpecimenEvent = StudySchema.getInstance().getTableInfoSpecimenEvent(_container);
        _tableInfoVial = StudySchema.getInstance().getTableInfoVial(_container);
        _tableInfoSpecimen = StudySchema.getInstance().getTableInfoSpecimen(_container);
        _tableInfoPrimaryType = StudySchema.getInstance().getTableInfoSpecimenPrimaryType(_container);
        _tableInfoLocation = StudySchema.getInstance().getTableInfoSite(_container);
        _tableInfoDerivative = StudySchema.getInstance().getTableInfoSpecimenDerivative(_container);
        _tableInfoAdditive = StudySchema.getInstance().getTableInfoSpecimenAdditive(_container);
        _dialect = _tableInfoSpecimen.getSqlDialect();

        _specimenColumns = determineSpecimenColumns();
        _specimensTableType = new SpecimenTableType("specimens", "study.Specimen", _specimenColumns);
        _labsTableType = new SpecimenTableType("labs",
                StudySchema.getInstance().getTableInfoSite(_container).getSelectName(), SITE_COLUMNS);
        _additivesTableType = new SpecimenTableType("additives",
                StudySchema.getInstance().getTableInfoSpecimenAdditive(_container).getSelectName(), ADDITIVE_COLUMNS);
        _derivativesTableType = new SpecimenTableType("derivatives",
                StudySchema.getInstance().getTableInfoSpecimenDerivative(_container).getSelectName(), DERIVATIVE_COLUMNS);
        _primaryTypesTableType = new SpecimenTableType("primary_types",
                StudySchema.getInstance().getTableInfoSpecimenPrimaryType(_container).getSelectName(), PRIMARYTYPE_COLUMNS);
    }

    private Collection<SpecimenColumn> determineSpecimenColumns()
    {
        Collection<SpecimenColumn> specimenColumns = new ArrayList<>(BASE_SPECIMEN_COLUMNS);

        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(_container, _user, null);

        Domain vialDomain = specimenTablesProvider.getDomain("Vial", true);
        if (null == vialDomain)
            throw new IllegalStateException("Expected Vial domain to already be created.");

        List<PropertyDescriptor> vialProperties = new ArrayList<>();
        for (DomainProperty domainProperty : vialDomain.getNonBaseProperties())
            vialProperties.add(domainProperty.getPropertyDescriptor());

        Domain specimenEventDomain = specimenTablesProvider.getDomain("SpecimenEvent", true);
        if (null == specimenEventDomain)
            throw new IllegalStateException("Expected SpecimenEvent domain to already be created.");

        SqlDialect dialect = getTableInfoSpecimen().getSqlDialect();

        Set<DomainProperty> eventBaseProperties = specimenEventDomain.getBaseProperties();
        for (DomainProperty domainProperty : specimenEventDomain.getProperties())
        {
            PropertyDescriptor property = domainProperty.getPropertyDescriptor();
            if (!eventBaseProperties.contains(domainProperty))
            {
                String name = property.getName();
                String alias = name.toLowerCase();
                Set<String> aliases = property.getImportAliasSet();
                if (null != aliases && !aliases.isEmpty())
                    alias = (String) (aliases.toArray()[0]);

                SpecimenColumn specimenColumn = new SpecimenColumn(alias, property.getStorageColumnName(), getTypeName(property, dialect), TargetTable.SPECIMEN_EVENTS);
                specimenColumns.add(specimenColumn);
            }
            findRollups(_eventToVialRollups, property, vialProperties, _eventVialRollups, false);
        }

        return specimenColumns;
    }

    private void resyncStudy(boolean syncParticipantVisit)
    {
        TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
        TableInfo tableSpecimen = getTableInfoSpecimen();

        executeSQL(tableParticipant.getSchema(), "INSERT INTO " + tableParticipant.getSelectName() + " (Container, ParticipantId)\n" +
                "SELECT DISTINCT ?, ptid AS ParticipantId\n" +
                "FROM " + tableSpecimen.getSelectName() + "\n" +
                "WHERE ptid IS NOT NULL AND " +
                "ptid NOT IN (SELECT ParticipantId FROM " + tableParticipant.getSelectName() + " WHERE Container = ?)", _container, _container);

        if (syncParticipantVisit)
        {
            StudyImpl study = StudyManager.getInstance().getStudy(_container);
            info("Updating study-wide subject/visit information...");
            StudyManager.getInstance().getVisitManager(study).updateParticipantVisitsFromSpecimenImport(_user, _logger);
            info("Subject/visit update complete.");
        }

        info("Updating locations in use...");
        LocationTable.updateLocationTableInUse(getTableInfoLocation(), getContainer());
    }

    private void updateAllStatistics()
    {
        updateStatistics(ExperimentService.get().getTinfoMaterial());
        updateStatistics(getTableInfoSpecimen());
        updateStatistics(getTableInfoVial());
        updateStatistics(getTableInfoSpecimenEvent());
    }

    private boolean updateStatistics(TableInfo tinfo)
    {
        info("Updating statistics for " + tinfo + "...");
        boolean updated = tinfo.getSqlDialect().updateStatistics(tinfo);
        if (updated)
            info("Statistics update " + tinfo + " complete.");
        else
            info("Statistics update not supported for this database type.");
        return updated;
    }

    public void process(VirtualFile specimensDir, boolean merge, StudyImportContext ctx, @Nullable PipelineJob job, boolean syncParticipantVisit)
            throws SQLException, IOException, ValidationException
    {
        Map<SpecimenTableType, SpecimenImportFile> sifMap = populateFileMap(specimensDir, new HashMap<>());

        process(sifMap, merge, ctx.getLogger(), job, syncParticipantVisit, false, ctx.isFailForUndefinedVisits());
    }

    protected void process(Map<SpecimenTableType, SpecimenImportFile> sifMap, boolean merge, Logger logger, @Nullable PipelineJob job,
                           boolean syncParticipantVisit, boolean editingSpecimens)
            throws IOException, ValidationException
    {
        process(sifMap, merge, logger, job, syncParticipantVisit, editingSpecimens, false);
    }

    private void process(Map<SpecimenTableType, SpecimenImportFile> sifMap, boolean merge, Logger logger, @Nullable PipelineJob job,
                           boolean syncParticipantVisit, boolean editingSpecimens, boolean failForUndefinedVisits)
            throws IOException, ValidationException
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        _logger = logger;
        _job = job;

        DbScope scope = schema.getScope();
        boolean commitSuccessfully = false;
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            _iTimer = TIMER.getInvocationTimer();

            setStatus(GENERAL_JOB_STATUS_MSG);
            _iTimer.setPhase(ImportPhases.PopulateLabs);
            if (null != sifMap.get(_labsTableType))
                mergeTable(schema, sifMap.get(_labsTableType), getTableInfoLocation(), true, true);

            _iTimer.setPhase(ImportPhases.SpecimenTypes);
            if (merge)
            {
                if (null != sifMap.get(_additivesTableType))
                    mergeTable(schema, sifMap.get(_additivesTableType), getTableInfoAdditive(), false, true);
                if (null != sifMap.get(_derivativesTableType))
                    mergeTable(schema, sifMap.get(_derivativesTableType), getTableInfoDerivative(), false, true);
                if (null != sifMap.get(_primaryTypesTableType))
                    mergeTable(schema, sifMap.get(_primaryTypesTableType), getTableInfoPrimaryType(), false, true);
            }
            else
            {
                if (null != sifMap.get(_additivesTableType))
                    replaceTable(schema, sifMap.get(_additivesTableType), getTableInfoAdditive(), false, true);
                if (null != sifMap.get(_derivativesTableType))
                    replaceTable(schema, sifMap.get(_derivativesTableType), getTableInfoDerivative(), false, true);
                if (null != sifMap.get(_primaryTypesTableType))
                    replaceTable(schema, sifMap.get(_primaryTypesTableType), getTableInfoPrimaryType(), false, true);
            }

            // Specimen temp table must be populated AFTER the types tables have been reloaded, since the SpecimenHash
            // calculated in the temp table relies on the new RowIds for the types:
            setStatus(GENERAL_JOB_STATUS_MSG + " (temp table)");
            _iTimer.setPhase(ImportPhases.PopulateTempTable);
            SpecimenImportFile specimenFile = sifMap.get(_specimensTableType);
            SpecimenLoadInfo loadInfo = populateTempSpecimensTable(specimenFile, merge);

            StudyImpl study = StudyManager.getInstance().getStudy(_container);
            if (loadInfo.getRowCount() > 0 && failForUndefinedVisits && study.getTimepointType() == TimepointType.VISIT)
                checkForUndefinedVisits(loadInfo, study);

            // NOTE: if no rows were loaded in the temp table, don't remove existing materials/specimens/vials/events.
            if (loadInfo.getRowCount() > 0)
                populateSpecimenTables(loadInfo, merge);
            else
                info("Specimens: 0 rows found in input");

            if (merge)
            {
                // Delete any orphaned specimen rows without vials
                _iTimer.setPhase(ImportPhases.DeleteOldData);
                executeSQL(StudySchema.getInstance().getSchema(), "DELETE FROM " + getTableInfoSpecimen().getSelectName() +
                                  " WHERE RowId NOT IN (SELECT SpecimenId FROM " + getTableInfoVial().getSelectName() + ")");
            }

            // No need to setPhase() here... method sets timer phases immediately
            updateCalculatedSpecimenData(merge, editingSpecimens);

            setStatus(GENERAL_JOB_STATUS_MSG + " (update study)");
            _iTimer.setPhase(ImportPhases.ResyncStudy);
            resyncStudy(syncParticipantVisit);

            ensureNotCanceled();
            _iTimer.setPhase(ImportPhases.SetLastSpecimenLoad);
            // Set LastSpecimenLoad to now... we'll check this before snapshot study specimen refresh
            study = StudyManager.getInstance().getStudy(_container).createMutable();
            study.setLastSpecimenLoad(new Date());
            StudyManager.getInstance().updateStudy(_user, study);

            _iTimer.setPhase(ImportPhases.DropTempTable);

            // Drop the temp table within the transaction; otherwise, we may get a different connection object,
            // where the table is no longer available.  Note that this means that the temp table will stick around
            // if an exception is thrown during loading, but this is probably okay- the DB will clean it up eventually.
            loadInfo._tempTableInfo.delete();

            _iTimer.setPhase(ImportPhases.UpdateAllStatistics);
            updateAllStatistics();

            // notify listeners that specimens have changed in this container
            setStatus(GENERAL_JOB_STATUS_MSG);
            _iTimer.setPhase(ImportPhases.NotifyChanged);
            ((SpecimenServiceImpl) SpecimenService.get()).fireSpecimensChanged(_container, _user, _logger);

            _iTimer.setPhase(ImportPhases.CommitTransaction);
            transaction.commit();
            commitSuccessfully = true;
        }
        finally
        {
            try
            {
                if (commitSuccessfully)
                {
                    _iTimer.setPhase(ImportPhases.ClearCaches);
                    StudyManager.getInstance().clearCaches(_container, false);
                    SpecimenManager.getInstance().clearCaches(_container);

                    info(_iTimer.getTimings("Timings for each phase of this import are listed below:", Order.HighToLow, "|"));
                }
            }
            finally
            {
                TIMER.releaseInvocationTimer(_iTimer);
            }
        }
    }


    private SpecimenLoadInfo populateTempSpecimensTable(SpecimenImportFile file, boolean merge) throws IOException, ValidationException
    {
        TempTablesHolder tempTablesHolder = createTempTable();

        Pair<List<SpecimenColumn>, Integer> pair = populateTempTable(tempTablesHolder, file, merge);
        List<SpecimenColumn> columns = pair.first;
        Integer rowCount = pair.second;

        tempTablesHolder.getCreateIndexes().run();

        if (tempTablesHolder.getSelectInsertTempTableInfo().isTracking())      // If no specimens we will not have created this table
            tempTablesHolder.getSelectInsertTempTableInfo().delete();
        return new SpecimenLoadInfo(_user, _container, DbSchema.getTemp(), columns, rowCount, tempTablesHolder.getTempTableInfo());
    }

    private void checkForUndefinedVisits(SpecimenLoadInfo info, StudyImpl study) throws ValidationException
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT DISTINCT VisitValue FROM ")
            .append(info.getTempTableName()).append(" tt ")
            .append("\nLEFT JOIN study.Visit v")
            .append("\nON tt.VisitValue >= v.SequenceNumMin AND tt.VisitValue <=v.SequenceNumMax AND v.Container = ?")
            .append("\nWHERE tt.VisitValue IS NOT NULL AND v.RowId IS NULL");

        // shared visit container
        Study visitStudy = StudyManager.getInstance().getStudyForVisits(study);
        sql.add(visitStudy.getContainer().getId());

        SqlSelector selector = new SqlSelector(StudySchema.getInstance().getSchema(), sql);
        List<Double> undefinedVisits = selector.getArrayList(Double.class);
        if (!undefinedVisits.isEmpty())
        {
            Collections.sort(undefinedVisits);
            throw new ValidationException("The following undefined visits exist in the specimen data: " + StringUtils.join(undefinedVisits, ", "));
        }
    }

    private void populateSpecimenTables(SpecimenLoadInfo info, boolean merge) throws IOException, ValidationException
    {
        setStatus(GENERAL_JOB_STATUS_MSG + " (populate tables)");
        _iTimer.setPhase(ImportPhases.DeleteOldData);
        if (!merge)
        {
//            SimpleFilter containerFilter = SimpleFilter.createContainerFilter(info.getContainer());
            info("Deleting old data from SpecimenEvent, Vial and Specimen tables...");
            if (getTableInfoSpecimen().getSchema().getSqlDialect().isPostgreSQL())
            {
                SQLFragment sql = new SQLFragment("TRUNCATE ");
                sql.append(getTableInfoSpecimenEvent().getSelectName()).append(", ")
                        .append(getTableInfoVial().getSelectName()).append(", ")
                        .append(getTableInfoSpecimen().getSelectName());
                executeSQL(getTableInfoSpecimen().getSchema(), sql);
            }
            else
            {
                Table.delete(getTableInfoSpecimenEvent());
                ensureNotCanceled();
                Table.delete(getTableInfoVial());
                ensureNotCanceled();
                Table.delete(getTableInfoSpecimen());
            }
            info("Complete.");
        }

        boolean seenVisitValue = false;
        for (SpecimenColumn col : info.getAvailableColumns())
        {
            if (VISIT_VALUE.getDbColumnName().equalsIgnoreCase(col.getDbColumnName()))
                seenVisitValue = true;
        }

        ensureNotCanceled();
        _iTimer.setPhase(ImportPhases.PopulateMaterials);
        populateMaterials(info, merge);
        ensureNotCanceled();
        _iTimer.setPhase(ImportPhases.PopulateSpecimens);
        populateSpecimens(info, merge, seenVisitValue);
        ensureNotCanceled();
        _iTimer.setPhase(ImportPhases.PopulateVials);
        populateVials(info, merge, seenVisitValue);
        ensureNotCanceled();
        _iTimer.setPhase(ImportPhases.PopulateSpecimenEvents);
        populateSpecimenEvents(info, merge);
    }

    private Set<String> getConflictingEventColumns(List<SpecimenEvent> events)
    {
        if (events.size() <= 1)
            return Collections.emptySet();
        Set<String> conflicts = new HashSet<>();

        for (SpecimenColumn col : _specimenColumns)
        {
            if (col.getAggregateEventFunction() == null && col.getTargetTable() == TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS)
            {
                // lower the case of the first character:
                String propName = col.getDbColumnName().substring(0, 1).toLowerCase() + col.getDbColumnName().substring(1);
                for (int i = 0; i < events.size() - 1; i++)
                {
                    SpecimenEvent event = events.get(i);
                    SpecimenEvent nextEvent = events.get(i + 1);
                    Object currentValue = event.get(propName);
                    Object nextValue = nextEvent.get(propName);
                    if (!Objects.equals(currentValue, nextValue))
                    {
                        if (propName.equalsIgnoreCase("drawtimestamp"))
                        {
                            Object currentDateOnlyValue = DateUtil.getDateOnly((Date) currentValue);
                            Object nextDateOnlyValue = DateUtil.getDateOnly((Date) nextValue);
                            if (!Objects.equals(currentDateOnlyValue, nextDateOnlyValue))
                                conflicts.add(DRAW_DATE.getDbColumnName());
                            Object currentTimeOnlyValue = DateUtil.getTimeOnly((Date) currentValue);
                            Object nextTimeOnlyValue = DateUtil.getTimeOnly((Date) nextValue);
                            if (!Objects.equals(currentTimeOnlyValue, nextTimeOnlyValue))
                                conflicts.add(DRAW_TIME.getDbColumnName());
                        }
                        else
                        {
                            conflicts.add(col.getDbColumnName());
                        }
                    }
                }
            }
        }

        return conflicts;
    }

    private void clearConflictingVialColumns(Vial vial, Set<String> conflicts)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("UPDATE ").append(getTableInfoVial().getSelectName()).append(" SET\n  ");

        boolean hasConflict = false;
        String sep = "";
        for (SpecimenColumn col : _specimenColumns)
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

        sql.append("\nWHERE GlobalUniqueId = ?");
        sql.add(vial.getGlobalUniqueId());

        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);
    }


    private void updateCommentSpecimenHashes()
    {
        SQLFragment sql = new SQLFragment();
        TableInfo commentTable = StudySchema.getInstance().getTableInfoSpecimenComment();
        String commentTableSelectName = commentTable.getSelectName();
        TableInfo vialTable = getTableInfoVial();
        String vialTableSelectName = vialTable.getSelectName();

        sql.append("UPDATE ").append(commentTableSelectName).append(" SET SpecimenHash = (\n")
                .append("SELECT SpecimenHash FROM ").append(vialTableSelectName).append(" WHERE ")
                .append(vialTable.getColumn("GlobalUniqueId").getValueSql(vialTableSelectName)).append(" = ")
                .append(commentTable.getColumn("GlobalUniqueId").getValueSql(commentTableSelectName))
                .append(")\nWHERE ").append(commentTable.getColumn("Container").getValueSql(commentTableSelectName)).append(" = ?");
        sql.add(_container.getId());
        info("Updating hash codes for existing comments...");
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);
        info("Complete.");
    }


    private void prepareQCComments()
    {
        StringBuilder columnList = new StringBuilder();
        columnList.append("VialId");
        for (SpecimenColumn col : _specimenColumns)
        {
            if (col.getTargetTable() == TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS && col.getAggregateEventFunction() == null)
            {
                columnList.append(",\n    ");
                columnList.append(col.getDbColumnName());
            }
        }

        // find the global unique ID for those vials with conflicts:
        TableInfo specimenEventTable = getTableInfoSpecimenEvent();
        SQLFragment conflictedGUIDs = new SQLFragment("SELECT GlobalUniqueId FROM ");
        conflictedGUIDs.append(getTableInfoVial(), "vial");
        conflictedGUIDs.append(" WHERE RowId IN (\n");
        conflictedGUIDs.append("SELECT VialId FROM\n");
        conflictedGUIDs.append("(SELECT DISTINCT\n").append(columnList).append("\nFROM ").append(specimenEventTable.getSelectName());
        conflictedGUIDs.append("\nWHERE Obsolete = ").append(specimenEventTable.getSqlDialect().getBooleanFALSE());
        conflictedGUIDs.append("\nGROUP BY\n").append(columnList).append(") ");
        conflictedGUIDs.append("AS DupCheckView\nGROUP BY VialId HAVING Count(VialId) > 1");
        conflictedGUIDs.append("\n)");

        // Delete comments that were holding QC state (and nothing more) for vials that do not currently have any conflicts
        SQLFragment deleteClearedVials = new SQLFragment("DELETE FROM study.SpecimenComment WHERE Container = ? ");
        deleteClearedVials.add(_container.getId());
        deleteClearedVials.append("AND Comment IS NULL AND QualityControlFlag = ? ");
        deleteClearedVials.add(Boolean.TRUE);
        deleteClearedVials.append("AND QualityControlFlagForced = ? ");
        deleteClearedVials.add(Boolean.FALSE);
        deleteClearedVials.append("AND GlobalUniqueId NOT IN (").append(conflictedGUIDs).append(");");
        info("Clearing QC flags for vials that no longer have history conflicts...");
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(deleteClearedVials);
        info("Complete.");


        // Insert placeholder comments for newly discovered QC problems; SpecimenHash will be updated within updateCalculatedSpecimenData, so this
        // doesn't have to be set here.
        SQLFragment insertPlaceholderQCComments = new SQLFragment("INSERT INTO study.SpecimenComment ");
        insertPlaceholderQCComments.append("(GlobalUniqueId, Container, QualityControlFlag, QualityControlFlagForced, Created, CreatedBy, Modified, ModifiedBy) ");
        insertPlaceholderQCComments.append("SELECT GlobalUniqueId, ?, ?, ?, ?, ?, ?, ? ");
        insertPlaceholderQCComments.add(_container.getId());
        insertPlaceholderQCComments.add(Boolean.TRUE);
        insertPlaceholderQCComments.add(Boolean.FALSE);
        insertPlaceholderQCComments.add(new Date());
        insertPlaceholderQCComments.add(_user.getUserId());
        insertPlaceholderQCComments.add(new Date());
        insertPlaceholderQCComments.add(_user.getUserId());
        insertPlaceholderQCComments.append(" FROM (\n").append(conflictedGUIDs).append(") ConflictedVials\n");
        insertPlaceholderQCComments.append("WHERE GlobalUniqueId NOT IN ");
        insertPlaceholderQCComments.append("(SELECT GlobalUniqueId FROM study.SpecimenComment WHERE Container = ?);");
        insertPlaceholderQCComments.add(_container.getId());
        info("Setting QC flags for vials that have new history conflicts...");
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(insertPlaceholderQCComments);
        info("Complete.");
    }

    private void markOrphanedRequestVials()
    {
        // Mark those global unique IDs that are in requests but are no longer found in the vial table:
        TableInfo vialTable = getTableInfoVial();
        String vialTableSelectName = vialTable.getSelectName();
        SQLFragment orphanMarkerSql = new SQLFragment();
        orphanMarkerSql.append("UPDATE study.SampleRequestSpecimen SET Orphaned = ? WHERE RowId IN (\n")
                .append("\tSELECT study.SampleRequestSpecimen.RowId FROM study.SampleRequestSpecimen\n")
                .append("\tLEFT OUTER JOIN ").append(vialTableSelectName).append(" ON\n\t\t")
                .append(vialTable.getColumn("GlobalUniqueId").getValueSql(vialTableSelectName))
                .append(" = study.SampleRequestSpecimen.SpecimenGlobalUniqueId\n")
                .append("\tWHERE ").append(vialTable.getColumn("GlobalUniqueId").getValueSql(vialTableSelectName)).append(" IS NULL AND\n")
                .append("\t\tstudy.SampleRequestSpecimen.Container = ?);");
        orphanMarkerSql.add(Boolean.TRUE);
        orphanMarkerSql.add(_container.getId());
        info("Marking requested vials that have been orphaned...");

        SqlExecutor executor = new SqlExecutor(StudySchema.getInstance().getSchema());
        executor.execute(orphanMarkerSql);
        info("Complete.");

        // un-mark those global unique IDs that were previously marked as orphaned but are now found in the vial table:
        SQLFragment deorphanMarkerSql = new SQLFragment();
        deorphanMarkerSql.append("UPDATE study.SampleRequestSpecimen SET Orphaned = ? WHERE RowId IN (\n")
                .append("\tSELECT study.SampleRequestSpecimen.RowId FROM study.SampleRequestSpecimen\n")
                .append("\tLEFT OUTER JOIN ").append(vialTableSelectName).append(" ON\n\t\t")
                .append(vialTable.getColumn("GlobalUniqueId").getValueSql(vialTableSelectName))
                .append(" = study.SampleRequestSpecimen.SpecimenGlobalUniqueId\n")
                .append("\tWHERE ").append(vialTable.getColumn("GlobalUniqueId").getValueSql(vialTableSelectName)).append(" IS NOT NULL AND\n")
                .append("\t\tstudy.SampleRequestSpecimen.Orphaned = ? AND\n")
                .append("\t\tstudy.SampleRequestSpecimen.Container = ?);");
        deorphanMarkerSql.add(Boolean.FALSE);
        deorphanMarkerSql.add(Boolean.TRUE);
        deorphanMarkerSql.add(_container.getId());
        info("Marking requested vials that have been de-orphaned...");
        executor.execute(deorphanMarkerSql);
        info("Complete.");
    }

    private void setLockedInRequestStatus()
    {
        TableInfo vialTable = getTableInfoVial();
        String vialTableSelectName = vialTable.getSelectName();
        SQLFragment lockedInRequestSql = new SQLFragment("UPDATE ").append(vialTableSelectName).append(
                " SET LockedInRequest = ? WHERE RowId IN (SELECT ").append(vialTable.getColumn("RowId").getValueSql(vialTableSelectName))
                .append(" FROM ").append(vialTableSelectName).append(", study.LockedSpecimens " +
                "WHERE study.LockedSpecimens.Container = ? AND ")
                .append(vialTable.getColumn("GlobalUniqueId").getValueSql(vialTableSelectName)).append(" = study.LockedSpecimens.GlobalUniqueId)");

        lockedInRequestSql.add(Boolean.TRUE);
        lockedInRequestSql.add(_container.getId());

        info("Setting Specimen Locked in Request status...");
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(lockedInRequestSql);
        info("Complete.");
    }

    private void updateSpecimenProcessingInfo()
    {
        TableInfo specimenTable = getTableInfoSpecimen();
        String specimenTableSelectName = specimenTable.getSelectName();
        TableInfo vialTable = getTableInfoVial();
        String vialTableSelectName = vialTable.getSelectName();
        SQLFragment sql = new SQLFragment("UPDATE ").append(specimenTableSelectName).append(" SET ProcessingLocation = (\n" +
                "\tSELECT MAX(ProcessingLocation) AS ProcessingLocation FROM \n" +
                "\t\t(SELECT DISTINCT SpecimenId, ProcessingLocation FROM ").append(vialTableSelectName).append(
                " WHERE SpecimenId = ").append(specimenTable.getColumn("RowId").getValueSql(specimenTableSelectName)).append(") Locations\n" +
                "\tGROUP BY SpecimenId\n" +
                "\tHAVING COUNT(ProcessingLocation) = 1\n" +
                ")");
        info("Updating processing locations on the specimen table...");
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);
        info("Complete.");

        sql = new SQLFragment("UPDATE ").append(specimenTableSelectName).append(" SET FirstProcessedByInitials = (\n" +
                "\tSELECT MAX(FirstProcessedByInitials) AS FirstProcessedByInitials FROM \n" +
                "\t\t(SELECT DISTINCT SpecimenId, FirstProcessedByInitials FROM ").append(vialTableSelectName).append(
                " WHERE SpecimenId = ").append(specimenTable.getColumn("RowId").getValueSql(specimenTableSelectName)).append(") Locations\n" +
                "\tGROUP BY SpecimenId\n" +
                "\tHAVING COUNT(FirstProcessedByInitials) = 1\n" +
                ")");
        info("Updating first processed by initials on the specimen table...");
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);
        info("Complete.");
    }

    private static final int CURRENT_SITE_UPDATE_SIZE = 1000;
    private static final int CURRENT_SITE_UPDATE_LOGGING_SIZE = 10000;   // Can choose to log at a less frequent rate than the update batch size

    // UNDONE: add vials in-clause to only update data for rows that changed
    private void updateCalculatedSpecimenData(final boolean merge, final boolean editingSpecimens)
    {
        setStatus(GENERAL_JOB_STATUS_MSG + " (update)");
        _iTimer.setPhase(ImportPhases.PrepareQcComments);
        // delete unnecessary comments and create placeholders for newly discovered errors:
        prepareQCComments();

        _iTimer.setPhase(ImportPhases.UpdateCommentSpecimenHashes);
        updateCommentSpecimenHashes();

        _iTimer.setPhase(ImportPhases.MarkOrphanedRequestVials);
        markOrphanedRequestVials();
        _iTimer.setPhase(ImportPhases.SetLockedInRequest);
        setLockedInRequestStatus();

        _iTimer.setPhase(ImportPhases.VialUpdatePreLoopPrep);
        // clear caches before determining current sites:
        SpecimenManager.getInstance().clearCaches(_container);
        final Map<Integer, Location> siteMap = new HashMap<>();

        TableInfo vialTable = getTableInfoVial();
        StringBuilder vialPropertiesSB = new StringBuilder("UPDATE ").append(vialTable.getSelectName())
            .append(" SET CurrentLocation = CAST(? AS INTEGER), ProcessingLocation = CAST(? AS INTEGER), FirstProcessedByInitials = ?, AtRepository = ?, LatestComments = ?, LatestQualityComments = ? ");

        for (List<RollupInstance<EventVialRollup>> rollupList : _eventToVialRollups.values())
        {
            for (RollupInstance<EventVialRollup> rollup : rollupList)
            {
                String colName = rollup.first;
                ColumnInfo column = vialTable.getColumn(colName);
                if (null == column)
                    throw new IllegalStateException("Expected Vial table column to exist.");
                vialPropertiesSB.append(", ").append(column.getSelectName()).append(" = ")
                    .append(JdbcType.VARCHAR.equals(column.getJdbcType()) ? "?" : "CAST(? AS " + vialTable.getSqlDialect().sqlCastTypeNameFromJdbcType(column.getJdbcType()) + ")");
            }
        }

        vialPropertiesSB.append(" WHERE RowId = ?");

        final String vialPropertiesSql = vialPropertiesSB.toString();

        _iTimer.setPhase(ImportPhases.HandleComments);

        TableInfo commentTable = StudySchema.getInstance().getTableInfoSpecimenComment();
        final String updateCommentSql = "UPDATE " + commentTable + " SET QualityControlComments = ? WHERE GlobalUniqueId = ?";

        // Populate a GlobalUniqueId -> SpecimenEvent map containing all quality control vial comments in this container
        final Map<String, SpecimenComment> qcCommentMap = new HashMap<>();

        SQLFragment selectCommentsSql = new SQLFragment();
        selectCommentsSql.append("SELECT c.* FROM ");
        selectCommentsSql.append(commentTable, "c");
        selectCommentsSql.append(" INNER JOIN ");
        selectCommentsSql.append(getTableInfoVial(), "v");
        selectCommentsSql.append(" ON c.GlobalUniqueId = v.GlobalUniqueId WHERE Container = ? AND (QualityControlFlag = ? OR QualityControlFlagForced = ?)");
        selectCommentsSql.add(getContainer());
        selectCommentsSql.add(true);
        selectCommentsSql.add(true);

        new SqlSelector(StudySchema.getInstance().getSchema(), selectCommentsSql).forEach(new Selector.ForEachBlock<SpecimenComment>()
        {
            @Override
            public void exec(SpecimenComment comment) throws SQLException
            {
                qcCommentMap.put(comment.getGlobalUniqueId(), comment);
            }
        }, SpecimenComment.class);

//        if (!merge)
//            new SpecimenTablesProvider(getContainer(), getUser(), null).dropTableIndices(SpecimenTablesProvider.VIAL_TABLENAME);

        // TODO: Select only required subset of Event and Vial columns?
        _iTimer.setPhase(ImportPhases.GetDateOrderedEvents);

        TableSelector eventSelector = new TableSelector(getTableInfoSpecimenEvent(), new SimpleFilter(FieldKey.fromString("Obsolete"), false), new Sort("VialId"));

        try (Results eventResults = eventSelector.getResults(false))
        {
            final MutableInt rowCount = new MutableInt();
            final MarkableIterator<Map<String, Object>> eventIterator = new MarkableIterator<>(eventResults.iterator());
            final Comparator<SpecimenEvent> eventComparator = SpecimenManager.getInstance().getSpecimenEventDateComparator();

            _iTimer.setPhase(ImportPhases.GetVialBatch);
            TableSelector vialSelector = new TableSelector(getTableInfoVial(), null, new Sort("RowId"));

            vialSelector.forEachMapBatch(new ForEachBatchBlock<Map<String, Object>>()
            {
                @Override
                public void exec(List<Map<String, Object>> vialBatch) throws SQLException
                {
                    int count = rowCount.intValue();
                    if (count % CURRENT_SITE_UPDATE_LOGGING_SIZE == 0)
                        info("Updating vial rows " + (count + 1) + " through " + (count + CURRENT_SITE_UPDATE_LOGGING_SIZE) + ".");

                    setStatus(GENERAL_JOB_STATUS_MSG + " (update vials)");

                    final List<Vial> vials = new ArrayList<>(CURRENT_SITE_UPDATE_SIZE);

                    for (Map<String, Object> map : vialBatch)
                        vials.add(new Vial(_container, map));

                    List<List<?>> vialPropertiesParams = new ArrayList<>(CURRENT_SITE_UPDATE_SIZE);
                    List<List<?>> commentParams = new ArrayList<>();

                    for (Vial vial : vials)
                    {
                        long vialId = vial.getRowId();

                        _iTimer.setPhase(ImportPhases.GetDateOrderedEvents);
                        List<SpecimenEvent> dateOrderedEvents = new ArrayList<>();
                        while (eventIterator.hasNext())
                        {
                            eventIterator.mark();
                            Map<String, Object> map = eventIterator.next();

                            if (vialId == (Long) map.get("VialId"))
                            {
                                dateOrderedEvents.add(new SpecimenEvent(_container, map));
                            }
                            else
                            {
                                eventIterator.reset();
                                break;
                            }
                        }
                        dateOrderedEvents.sort(eventComparator);

                        _iTimer.setPhase(ImportPhases.GetProcessingLocationId);
                        Integer processingLocation = SpecimenManager.getInstance().getProcessingLocationId(dateOrderedEvents);
                        _iTimer.setPhase(ImportPhases.GetFirstProcessedBy);
                        String firstProcessedByInitials = SpecimenManager.getInstance().getFirstProcessedByInitials(dateOrderedEvents);
                        _iTimer.setPhase(ImportPhases.GetCurrentLocationId);
                        Integer currentLocation = SpecimenManager.getInstance().getCurrentLocationId(dateOrderedEvents);

                        _iTimer.setPhase(ImportPhases.CalculateLocation);
                        boolean atRepository = false;

                        if (currentLocation != null)
                        {
                            Location location;

                            if (!siteMap.containsKey(currentLocation))
                            {
                                location = StudyManager.getInstance().getLocation(_container, currentLocation);
                                if (location != null)
                                    siteMap.put(currentLocation, location);
                            }
                            else
                            {
                                location = siteMap.get(currentLocation);
                            }

                            if (location != null)
                                atRepository = location.isRepository() != null && location.isRepository();
                        }

                        // All of the additional fields (deviationCodes, Concetration, Integrity, Yield, Ratio, QualityComments, Comments) always take the latest value
                        _iTimer.setPhase(ImportPhases.GetLastEvent);
                        SpecimenEvent lastEvent = SpecimenManager.getInstance().getLastEvent(dateOrderedEvents);
                        if (null == lastEvent)
                            throw new IllegalStateException("There should always be at least 1 event.");

                        _iTimer.setPhase(ImportPhases.DetermineUpdateVial);
                        boolean updateVial = false;
                        List<Object> params = new ArrayList<>();

                        if (!Objects.equals(currentLocation, vial.getCurrentLocation()) ||
                                !Objects.equals(processingLocation, vial.getProcessingLocation()) ||
                                !Objects.equals(firstProcessedByInitials, vial.getFirstProcessedByInitials()) ||
                                atRepository != vial.isAtRepository() ||
                                !Objects.equals(vial.getLatestComments(), lastEvent.getComments()) ||
                                !Objects.equals(vial.getLatestQualityComments(), lastEvent.getQualityComments()))
                        {
                            updateVial = true;          // Something is different
                        }

                        if (!updateVial)
                        {
                            for (Map.Entry<String, List<RollupInstance<EventVialRollup>>> rollupEntry : _eventToVialRollups.entrySet())
                            {
                                String eventColName = rollupEntry.getKey();
                                ColumnInfo column = getTableInfoSpecimenEvent().getColumn(eventColName);
                                if (null == column)
                                    throw new IllegalStateException("Expected Specimen Event table column to exist.");
                                String eventColSelectName = column.getSelectName();
                                for (RollupInstance<EventVialRollup> rollupItem : rollupEntry.getValue())
                                {
                                    String vialColName = rollupItem.first;
                                    Object rollupResult = rollupItem.second.getRollupResult(dateOrderedEvents, eventColSelectName,
                                            rollupItem.getFromType(), rollupItem.getToType());
                                    if (!Objects.equals(vial.get(vialColName), rollupResult))
                                    {
                                        updateVial = true;      // Something is different
                                        break;
                                    }
                                }
                                if (updateVial)
                                    break;
                            }
                        }

                        _iTimer.setPhase(ImportPhases.SetUpdateParameters);
                        if (updateVial)
                        {
                            // Something is different; update everything
                            params.add(currentLocation);
                            params.add(processingLocation);
                            params.add(firstProcessedByInitials);
                            params.add(atRepository);
                            params.add(lastEvent.getComments());
                            params.add(lastEvent.getQualityComments());

                            for (Map.Entry<String, List<RollupInstance<EventVialRollup>>> rollupEntry : _eventToVialRollups.entrySet())
                            {
                                String eventColName = rollupEntry.getKey();
                                ColumnInfo column = getTableInfoSpecimenEvent().getColumn(eventColName);
                                if (null == column)
                                    throw new IllegalStateException("Expected Specimen Event table column to exist.");
                                String eventColAlias = column.getAlias();     // Use alias since we're looking up in the rowMap
                                for (RollupInstance<EventVialRollup> rollupItem : rollupEntry.getValue())
                                {
                                    Object rollupResult = rollupItem.second.getRollupResult(dateOrderedEvents, eventColAlias,
                                            rollupItem.getFromType(), rollupItem.getToType());
                                    params.add(rollupResult);
                                }
                            }

                            params.add(vial.getRowId());
                            vialPropertiesParams.add(params);
                        }

                        _iTimer.setPhase(ImportPhases.HandleComments);
                        SpecimenComment comment = qcCommentMap.get(vial.getGlobalUniqueId());

                        if (comment != null)
                        {
                            // if we have a comment, it may be because we're in a bad QC state. If so, we should update
                            // the reason for the QC problem.
                            String message = null;

                            Set<String> conflicts = getConflictingEventColumns(dateOrderedEvents);

                            if (!conflicts.isEmpty())
                            {
                                // Null out conflicting Vial columns
                                if (merge)
                                {
                                    // NOTE: in checkForConflictingSpecimens() we check the imported specimen columns used
                                    // to generate the specimen hash are not in conflict so we shouldn't need to clear any
                                    // columns on the specimen table. Vial columns are not part of the specimen hash and
                                    // can safely be cleared without compromising the specimen hash.
                                    clearConflictingVialColumns(vial, conflicts);
                                }

                                String sep = "";
                                message = "Conflicts found: ";
                                for (String conflict : conflicts)
                                {
                                    message += sep + conflict;
                                    sep = ", ";
                                }
                            }

                            commentParams.add(Arrays.asList(message, vial.getGlobalUniqueId()));
                        }
                    }

                    _iTimer.setPhase(ImportPhases.UpdateVials);
                    if (!vialPropertiesParams.isEmpty())
                        Table.batchExecute(StudySchema.getInstance().getSchema(), vialPropertiesSql, vialPropertiesParams);

                    _iTimer.setPhase(ImportPhases.UpdateComments);
                    if (!commentParams.isEmpty())
                        Table.batchExecute(StudySchema.getInstance().getSchema(), updateCommentSql, commentParams);

                    rowCount.add(CURRENT_SITE_UPDATE_SIZE);
                    _iTimer.setPhase(ImportPhases.GetVialBatch);
                }
            }, CURRENT_SITE_UPDATE_SIZE);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

//        if (!merge)
//            new SpecimenTablesProvider(getContainer(), getUser(), null).addTableIndices(SpecimenTablesProvider.VIAL_TABLENAME);

        // finally, after all other data has been updated, we can update our cached specimen counts and processing locations:
        setStatus(GENERAL_JOB_STATUS_MSG + " (update counts)");
        _iTimer.setPhase(ImportPhases.UpdateSpecimenProcessingInfo);
        updateSpecimenProcessingInfo();

        _iTimer.setPhase(ImportPhases.UpdateRequestability);
        try
        {
            RequestabilityManager.getInstance().updateRequestability(_container, _user, false, editingSpecimens, _logger);
        }
        catch (RequestabilityManager.InvalidRuleException e)
        {
            throw new IllegalStateException("One or more requestability rules is invalid.  Please remove or correct the invalid rule.", e);
        }

        _iTimer.setPhase(ImportPhases.UpdateVialCounts);
        info("Updating cached vial counts...");

        SpecimenManager.getInstance().updateVialCounts(_container, _user);

        info("Vial count update complete.");
    }
    
    private Map<SpecimenTableType, SpecimenImportFile> populateFileMap(VirtualFile dir, Map<SpecimenTableType, SpecimenImportFile> fileNameMap) throws IOException
    {
        for (String dirName : dir.listDirs())
        {
            populateFileMap(dir.getDir(dirName), fileNameMap);
        }

        for (String fileName : dir.list())
        {
            if (!fileName.toLowerCase().endsWith(".tsv"))
                continue;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(dir.getInputStream(fileName))))
            {
                String line = reader.readLine();
                if (null == line)
                    continue;
                line = StringUtils.trimToEmpty(line);
                if (!line.startsWith("#"))
                    throw new IllegalStateException("Import files are expected to start with a comment indicating table name");

                String canonicalName = line.substring(1).trim().toLowerCase();
                SpecimenTableType type = getForName(canonicalName);

                if (null != type)
                    fileNameMap.put(type, getSpecimenImportFile(_container, dir, fileName, type));
            }
        }

        return fileNameMap;
    }


    // TODO: Pass in merge (or import strategy)?
    private SpecimenImportFile getSpecimenImportFile(Container c, VirtualFile dir, String fileName, SpecimenTableType type)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();

        // Enumerate the import filter factories... first one to claim the file gets associated with it
        for (SpecimenImportStrategyFactory factory : SpecimenService.get().getSpecimenImportStrategyFactories())
        {
            SpecimenImportStrategy strategy = factory.get(schema, c, dir, fileName);

            if (null != strategy)
                return new FileSystemSpecimenImportFile(dir, fileName, strategy, type);
        }

        throw new IllegalStateException("No SpecimenImportStrategyFactory claimed this import!");
    }

    private void info(String message)
    {
        if (_logger != null)
            _logger.info(message);
    }

    private void debug(CharSequence message)
    {
        //noinspection PointlessBooleanExpression
        if (DEBUG && _logger != null)
            _logger.debug(message);
    }

    private static String _currentStatus = GENERAL_JOB_STATUS_MSG;
    private void setStatus(@Nullable String status)
    {
        if (null != _job)
        {
            _currentStatus = status;
            _job.setStatus(_currentStatus);
        }
    }

    private void ensureNotCanceled()
    {
        if (null != _job)
            _job.setStatus(_currentStatus);     // Will throw if job has been canceled
    }

    private List<SpecimenColumn> getSpecimenCols(List<SpecimenColumn> availableColumns)
    {
        if (_specimenCols == null)
        {
            List<SpecimenColumn> cols = new ArrayList<>(availableColumns.size());
            for (SpecimenColumn col : availableColumns)
            {
                if (col.getTargetTable().isSpecimens())
                    cols.add(col);
            }
            _specimenCols = cols;
        }
        return _specimenCols;
    }

    private String getSpecimenColsSql(List<SpecimenColumn> availableColumns, boolean seenVisitValue)
    {
        if (_specimenColsSql == null)
        {
            String sep = "";
            StringBuilder cols = new StringBuilder();
            for (SpecimenColumn col : getSpecimenCols(availableColumns))
            {
                cols.append(sep).append(col.getLegalDbColumnName(_dialect));
                sep = ",\n   ";
            }
            if (!seenVisitValue)
                cols.append(sep).append(VISIT_VALUE.getDbColumnName());
            _specimenColsSql = cols.toString();
        }
        return _specimenColsSql;
    }

    private List<SpecimenColumn> getVialCols(List<SpecimenColumn> availableColumns)
    {
        if (_vialCols == null)
        {
            List<SpecimenColumn> cols = new ArrayList<>(availableColumns.size());
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
                cols.append(sep).append(col.getLegalDbColumnName(_dialect));
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
            List<SpecimenColumn> cols = new ArrayList<>(availableColumns.size());
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
                cols.append(sep).append(col.getLegalDbColumnName(_dialect));
                sep = ",\n    ";
            }
            _vialEventColsSql = cols.toString();
        }
        return _vialEventColsSql;
    }

    private void populateMaterials(SpecimenLoadInfo info, boolean merge)
    {
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
        ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(info.getContainer(), SpecimenManager.STUDY_SPECIMENS_SAMPLE_SET_NAME);

        if (sampleSet == null)
        {
            ExpSampleSet source = ExperimentService.get().createSampleSet();
            source.setContainer(info.getContainer());
            source.setMaterialLSIDPrefix(prefix);
            source.setName(SpecimenManager.STUDY_SPECIMENS_SAMPLE_SET_NAME);
            source.setLSID(ExperimentService.get().getSampleSetLsid(SpecimenManager.STUDY_SPECIMENS_SAMPLE_SET_NAME, info.getContainer()).toString());
            source.setDescription("Study specimens for " + info.getContainer().getPath());
            source.save(null);
            cpasType = source.getLSID();
        }
        else
        {
            cpasType = sampleSet.getLSID();
        }

        Timestamp createdTimestamp = new Timestamp(System.currentTimeMillis());

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

    private String getSpecimenEventTempTableColumns(SpecimenLoadInfo info)
    {
        StringBuilder columnList = new StringBuilder();
        String prefix = "";
        for (SpecimenColumn col : getSpecimenEventCols(info.getAvailableColumns()))
        {
            columnList.append(prefix);
            prefix = ", ";
            columnList.append("\n    ").append(info.getTempTableName()).append(".").append(col.getLegalDbColumnName(_dialect));
        }
        return columnList.toString();
    }

    // NOTE: In merge case, we've already checked the specimen hash columns are not in conflict.
    private void appendConflictResolvingSQL(SqlDialect dialect, SQLFragment sql, SpecimenColumn col, String tempTableName,
                                            @Nullable SpecimenColumn castColumn)
    {
        // If castColumn no null, then we still count col, but then cast col's value to castColumn's type and name it castColumn's name
        String selectCol = tempTableName + "." + col.getLegalDbColumnName(_dialect);

        if (col.getAggregateEventFunction() != null)
        {
            sql.append(col.getAggregateEventFunction()).append("(").append(selectCol).append(")");
        }
        else
        {
            sql.append("CASE WHEN");
            if (col.getJavaClass().equals(Boolean.class))
            {
                // gross nested calls to cast the boolean to an int, get its min, then cast back to a boolean.
                // this is needed because most aggregates don't work on boolean values.
                sql.append(" COUNT(DISTINCT(").append(selectCol).append(")) = 1 THEN ");
                sql.append("CAST(MIN(CAST(").append(selectCol).append(" AS INTEGER)) AS ").append(dialect.getBooleanDataType()).append(")");
            }
            else
            {
                if (null != castColumn)
                {
                    sql.append(" COUNT(DISTINCT(").append(tempTableName).append(".").append(castColumn.getLegalDbColumnName(_dialect)).append(")) = 1 THEN ");
                    sql.append("CAST(MIN(").append(selectCol).append(") AS ").append(castColumn.getDbType()).append(")");
                }
                else
                {
                    sql.append(" COUNT(DISTINCT(").append(selectCol).append(")) = 1 THEN ");
                    sql.append("MIN(").append(selectCol).append(")");
                }
            }
            sql.append(" ELSE NULL END");
        }
        sql.append(" AS ");

        if (null != castColumn)
            sql.append(castColumn.getLegalDbColumnName(_dialect));
        else
            sql.append(col.getLegalDbColumnName(_dialect));
    }


    private void populateSpecimens(SpecimenLoadInfo info, boolean merge, boolean seenVisitValue) throws IOException, ValidationException
    {
        String participantSequenceNumExpr = VisitManager.getParticipantSequenceNumExpr(info._schema, "PTID", "VisitValue");

        SQLFragment insertSelectSql = new SQLFragment();
        insertSelectSql.append("SELECT ");
        insertSelectSql.append(participantSequenceNumExpr).append(" AS ParticipantSequenceNum");
        insertSelectSql.append(", SpecimenHash, ");
        insertSelectSql.append(DRAW_DATE.getDbColumnName()).append(", ");
        insertSelectSql.append(DRAW_TIME.getDbColumnName()).append(", ");
        insertSelectSql.append(getSpecimenColsSql(info.getAvailableColumns(), seenVisitValue)).append(" FROM (\n");
        insertSelectSql.append(getVialListFromTempTableSql(info, true, seenVisitValue)).append(") VialList\n");
        insertSelectSql.append("GROUP BY ").append("SpecimenHash, ");
        insertSelectSql.append(getSpecimenColsSql(info.getAvailableColumns(), seenVisitValue));
        insertSelectSql.append(", ").append(DRAW_DATE.getDbColumnName());
        insertSelectSql.append(", ").append(DRAW_TIME.getDbColumnName());

        TableInfo specimenTable = getTableInfoSpecimen();
        String specimenTableSelectName = specimenTable.getSelectName();
        if (merge)
        {
            // Create list of specimen columns, including unique columns not found in SPECIMEN_COLUMNS.
            Set<SpecimenColumn> cols = new LinkedHashSet<>();
            cols.add(new SpecimenColumn("SpecimenHash", "SpecimenHash", "VARCHAR(256)", TargetTable.SPECIMENS, true));
            cols.add(new SpecimenColumn("ParticipantSequenceNum", "ParticipantSequenceNum", "VARCHAR(200)", TargetTable.SPECIMENS, false));
            cols.add(DRAW_DATE);
            cols.add(DRAW_TIME);
            cols.addAll(getSpecimenCols(info.getAvailableColumns()));

            // Insert or update the specimens from in the temp table
            try (TableResultSet rs = new SqlSelector(info.getSchema(), insertSelectSql).getResultSet())
            {
                if (VERBOSE_DEBUG)
                    ResultSetUtil.logData(rs, _logger);
                DataIteratorBuilder dib = new DataIteratorBuilder.Wrapper(new ResultsImpl(rs));
                mergeTable(info.getSchema(), specimenTableSelectName, specimenTable, cols, dib, false, false);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        else
        {
            // Insert all specimens from in the temp table.
            SQLFragment insertSql = new SQLFragment();
            insertSql.append("INSERT INTO ").append(specimenTableSelectName).append("\n(").append("ParticipantSequenceNum, SpecimenHash, ");
            insertSql.append(DRAW_DATE.getDbColumnName()).append(", ");
            insertSql.append(DRAW_TIME.getDbColumnName()).append(", ");
            insertSql.append(getSpecimenColsSql(info.getAvailableColumns(), seenVisitValue)).append(")\n");
            insertSql.append(insertSelectSql);

            if (DEBUG)
                logSQLFragment(insertSql);

            info("Specimens: Inserting new rows...");
            executeSQL(info.getSchema(), insertSql);
            info("Specimens: Insert complete.");
        }
    }

    private SQLFragment getVialListFromTempTableSql(SpecimenLoadInfo info, boolean forSpecimenTable, boolean seenVisitValue)
    {
        String prefix = "";
        SQLFragment vialListSql = new SQLFragment();
        vialListSql.append("SELECT ");
        if (!forSpecimenTable)
        {
            vialListSql.append(info.getTempTableName()).append(".LSID AS LSID");
            prefix = ",\n    ";
        }
        vialListSql.append(prefix).append("SpecimenHash");
        prefix = ",\n    ";
        for (SpecimenColumn col : info.getAvailableColumns())
        {
            if ((col.getTargetTable().isVials() || col.getTargetTable().isSpecimens()) &&
                (!forSpecimenTable || !GLOBAL_UNIQUE_ID.getDbColumnName().equalsIgnoreCase(col.getDbColumnName())))
            {
                vialListSql.append(prefix);
                appendConflictResolvingSQL(info.getSchema().getSqlDialect(), vialListSql, col, info.getTempTableName(), null);
            }
        }
        if (!seenVisitValue)
        {
            vialListSql.append(prefix).append("0.0 AS ").append(VISIT_VALUE.getDbColumnName());
        }

        // DrawDate and DrawTime are a little different;
        // we need to do the conflict count on DrawTimeStamp and then cast to Date or Time
        vialListSql.append(prefix);
        appendConflictResolvingSQL(info.getSchema().getSqlDialect(), vialListSql, DRAW_TIMESTAMP, info.getTempTableName(), DRAW_DATE);
        vialListSql.append(prefix);
        appendConflictResolvingSQL(info.getSchema().getSqlDialect(), vialListSql, DRAW_TIMESTAMP, info.getTempTableName(), DRAW_TIME);

        vialListSql.append("\nFROM ").append(info.getTempTableName());
        vialListSql.append("\nGROUP BY\n");
        if (!forSpecimenTable)
            vialListSql.append(info.getTempTableName()).append(".LSID,\n    ");
        vialListSql.append(info.getTempTableName()).append(".SpecimenHash");
        if (!forSpecimenTable)
            vialListSql.append(",\n    ").append(info.getTempTableName()).append(".GlobalUniqueId");
        return vialListSql;
    }

    private void populateVials(SpecimenLoadInfo info, boolean merge, boolean seenVisitValue) throws ValidationException
    {
        TableInfo specimenTable = getTableInfoSpecimen();
        String specimenTableSelectName = specimenTable.getSelectName();
        TableInfo vialTable = getTableInfoVial();
        String vialTableSelectName = vialTable.getSelectName();

        String prefix = ",\n    ";
        SQLFragment insertSelectSql = new SQLFragment();
        insertSelectSql.append("SELECT exp.Material.RowId");
        insertSelectSql.append(prefix).append(specimenTable.getColumn("RowId").getValueSql(specimenTableSelectName)).append(" AS SpecimenId");
        insertSelectSql.append(prefix).append(specimenTable.getColumn("SpecimenHash").getValueSql(specimenTableSelectName));
        insertSelectSql.append(prefix).append("? AS Available");
        // Set a default value of true for the 'Available' column:
        insertSelectSql.add(Boolean.TRUE);

        for (SpecimenColumn col : getVialCols(info.getAvailableColumns()))
            insertSelectSql.append(prefix).append("VialList.").append(col.getLegalDbColumnName(_dialect));

        insertSelectSql.append(" FROM (").append(getVialListFromTempTableSql(info, false, seenVisitValue)).append(") VialList");

        // join to material:
        insertSelectSql.append("\n    JOIN exp.Material ON (");
        insertSelectSql.append("VialList.LSID = exp.Material.LSID");
        insertSelectSql.append(" AND exp.Material.Container = ?)");
        insertSelectSql.add(info.getContainer().getId());

        // join to specimen:
        insertSelectSql.append("\n    JOIN ").append(specimenTableSelectName).append(" ON ");
        insertSelectSql.append(specimenTable.getColumn("SpecimenHash").getValueSql(specimenTableSelectName)).
                append(" = VialList.SpecimenHash");


        if (merge)
        {
            // Create list of vial columns, including unique columns not found in SPECIMEN_COLUMNS.
            Set<SpecimenColumn> cols = new LinkedHashSet<>();
            // NOTE: study.Vial.RowId is actually an FK to exp.Material.RowId
            cols.add(GLOBAL_UNIQUE_ID);
            cols.add(new SpecimenColumn("RowId", "RowId", "INT NOT NULL", TargetTable.VIALS, false));
            cols.add(new SpecimenColumn("SpecimenId", "SpecimenId", "INT NOT NULL", TargetTable.VIALS, false));
            cols.add(new SpecimenColumn("SpecimenHash", "SpecimenHash", "VARCHAR(256)", TargetTable.VIALS, false));
            cols.add(new SpecimenColumn("Available", "Available", BOOLEAN_TYPE, TargetTable.VIALS, false));
            cols.addAll(getVialCols(info.getAvailableColumns()));

            // Insert or update the vials from in the temp table.
            try (TableResultSet rs = new SqlSelector(info.getSchema(), insertSelectSql).getResultSet())
            {
                if (VERBOSE_DEBUG)
                    ResultSetUtil.logData(rs, _logger);
                DataIteratorBuilder dib = new DataIteratorBuilder.Wrapper(new ResultsImpl(rs));
                mergeTable(info.getSchema(), vialTableSelectName, vialTable, cols, dib, false, false);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        else
        {
            // Insert all vials from in the temp table.
            SQLFragment insertSql = new SQLFragment();
            insertSql.append("INSERT INTO ").append(vialTableSelectName).append("\n(RowId, SpecimenId, SpecimenHash, Available, ");
            insertSql.append(getVialColsSql(info.getAvailableColumns())).append(")\n");
            insertSql.append(insertSelectSql);

            if (DEBUG)
                logSQLFragment(insertSql);

            info("Vials: Inserting new rows...");
            executeSQL(info.getSchema(), insertSql);
            info("Vials: Insert complete.");
        }
    }

    private void logSQLFragment(SQLFragment sql)
    {
        info(sql.getSQL());
        info("Params: ");
        for (Object param : sql.getParams())
            info(param.toString());
    }

    private void populateSpecimenEvents(SpecimenLoadInfo info, boolean merge) throws ValidationException
    {
        TableInfo specimenEventTable = getTableInfoSpecimenEvent();
        String specimenEventTableSelectName = specimenEventTable.getSelectName();
        TableInfo vialTable = getTableInfoVial();
        String vialTableSelectName = vialTable.getSelectName();

        SQLFragment insertSelectSql = new SQLFragment();
        insertSelectSql.append("SELECT ").append(vialTable.getColumn("RowId").getValueSql(vialTableSelectName)).append(" AS VialId, \n");
        insertSelectSql.append(getSpecimenEventTempTableColumns(info));
        insertSelectSql.append(" FROM ");
        insertSelectSql.append(info.getTempTableName()).append("\nJOIN ").append(vialTableSelectName).append(" ON ");
        insertSelectSql.append(info.getTempTableName()).append(".GlobalUniqueId = ")
                .append(vialTable.getColumn("GlobalUniqueId").getValueSql(vialTableSelectName));

        if (merge)
        {
            // Create list of vial columns, including unique columns not found in SPECIMEN_COLUMNS.
            // Events are special in that we want to merge based on a pseudo-unique set of columns:
            //    Container, VialId (vial.GlobalUniqueId), LabId, StorageDate, ShipDate, LabReceiptDate
            // We need to always add these extra columns, even if they aren't in the list of available columns.
            Set<SpecimenColumn> cols = new LinkedHashSet<>();
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
            try (TableResultSet rs = new SqlSelector(info.getSchema(), insertSelectSql).getResultSet())
            {
                if (VERBOSE_DEBUG)
                    ResultSetUtil.logData(rs, _logger);
                DataIteratorBuilder dib = new DataIteratorBuilder.Wrapper(new ResultsImpl(rs));
                mergeTable(info.getSchema(), specimenEventTableSelectName, specimenEventTable, cols, dib, false, false);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        else
        {
            // Insert all events from the temp table
            SQLFragment insertSql = new SQLFragment();
            insertSql.append("INSERT INTO ").append(specimenEventTableSelectName).append("\n");
            insertSql.append("(VialId, ").append(getSpecimenEventColsSql(info.getAvailableColumns())).append(")\n");
            insertSql.append(insertSelectSql);

            if (DEBUG)
                logSQLFragment(insertSql);

            info("Specimen Events: Inserting new rows.");
            executeSQL(info.getSchema(), insertSql);
            info("Specimen Events: Insert complete.");
        }
    }

    private interface ComputedColumn
    {
        String getName();
        Object getValue(Map<String, Object> row) throws ValidationException;
    }

    private class EntityIdComputedColumn implements ComputedColumn
    {
        public String getName() { return "EntityId"; }
        public Object getValue(Map<String, Object> row) { return GUID.makeGUID(); }
    }

    private <T extends ImportableColumn> Pair<List<T>, Integer> mergeTable(
            DbSchema schema, String tableName, @Nullable TableInfo target,
            Collection<T> potentialColumns, DataIteratorBuilder values, boolean addEntityId, boolean hasContainerColumn)
            throws ValidationException
    {
        ComputedColumn entityIdCol = null;
        if (addEntityId)
        {
            entityIdCol = new EntityIdComputedColumn();
        }

        return mergeTable(schema, tableName, target, potentialColumns, values, entityIdCol, hasContainerColumn);
    }

    private void mergeTable(DbSchema schema, SpecimenImportFile file, TableInfo target, boolean addEntityId, boolean hasContainerColumn)
            throws ValidationException, IOException
    {
        SpecimenTableType type = file.getTableType();

        ComputedColumn entityIdCol = null;

        if (addEntityId)
        {
            entityIdCol = new EntityIdComputedColumn();
        }

        try (DataLoader loader = loadTsv(file))
        {
            mergeTable(schema, type.getTableName(), target, type.getColumns(), loader, entityIdCol, hasContainerColumn);
        }
        finally
        {
            file.getStrategy().close();
        }
    }

    private void appendEqualCheck(DbSchema schema, StringBuilder sql, ImportableColumn col)
    {
        String dialectType = schema.getSqlDialect().sqlCastTypeNameFromJdbcType(col.getJdbcType());
        String paramCast = "CAST(? AS " + dialectType + ")";
        // Each unique col has two parameters in the null-equals check.
        sql.append("(").append(col.getLegalDbColumnName(_dialect)).append(" IS NULL AND ").append(paramCast).append(" IS NULL)");
        sql.append(" OR ").append(col.getLegalDbColumnName(_dialect)).append(" = ").append(paramCast);
    }


    /**
     * Insert or update rows on the target table using the unique columns of <code>potentialColumns</code>
     * to identify the existing row.
     *
     * NOTE: The idCol is used only during insert -- the value won't be updated if the row already exists.
     *
     * @param schema The dbschema.
     * @param idCol The computed column.
     * @return A pair of the columns actually found in the data values and a total row count.
     * @throws org.labkey.api.query.ValidationException
     */
    private <T extends ImportableColumn> Pair<List<T>, Integer> mergeTable(
            DbSchema schema, String tableName, @Nullable TableInfo target,
            Collection<T> potentialColumns, DataIteratorBuilder values,
            ComputedColumn idCol, boolean hasContainerColumn)
            throws ValidationException
    {
        // tests  SpecimenTest, LuminexUploadAndCopyTest, VaccineProtocolTest, FlowSpecimenTest, SpecimenImportTest, CreateVialsTest, ViabilityTest
        if (values == null)
        {
            info(tableName + ": No rows to merge");
            return new Pair<>(Collections.emptyList(), 0);
        }

        if (null == target)
        {
            target = schema.getTable(tableName.substring(tableName.indexOf('.') + 1));
            if (null == target)
                throw new IllegalArgumentException("tablename: " + tableName);
        }


        // get the iter 'early' so we can look at the columns

        DataIteratorContext dix = new DataIteratorContext();
        dix.setInsertOption(QueryUpdateService.InsertOption.MERGE);
        DataIterator iter = values.getDataIterator(dix);

        CaseInsensitiveHashSet tsvColumnNames = new CaseInsensitiveHashSet();
        for (int i=1 ; i<= iter.getColumnCount() ; i++)
            tsvColumnNames.add(iter.getColumnInfo(i).getName());

        List<T> availableColumns = new ArrayList<>();
        CaseInsensitiveHashSet skipColumns = new CaseInsensitiveHashSet(target.getColumnNameSet());
        CaseInsensitiveHashSet keyColumns = new CaseInsensitiveHashSet();

        CaseInsensitiveHashSet dontUpdate = new CaseInsensitiveHashSet();
        if (null != idCol)
            dontUpdate.add(idCol.getName());
        dontUpdate.add("entityid");
        skipColumns.remove("entityid");

        // NOTE entityid is handled by DataIterator so ignore EntityIdComputedColumn
        if (idCol instanceof EntityIdComputedColumn)
            idCol = null;

        for (T column : potentialColumns)
        {
            if (tsvColumnNames.contains(column.getTsvColumnName()) || tsvColumnNames.contains(column.getDbColumnName()))
            {
                availableColumns.add(column);
                skipColumns.remove(column.getDbColumnName());
            }
        }

        for (T col : availableColumns)
        {
            if (col.isUnique())
                keyColumns.add(col.getDbColumnName());
        }
        if (hasContainerColumn)
        {
            keyColumns.add("Container");
            skipColumns.remove("Container");
        }
        if (idCol != null)
            skipColumns.remove(idCol.getName());
        if (keyColumns.isEmpty())
            keyColumns = null;

        DataIteratorBuilder specimenIter = new SpecimenImportBuilder(target, new DataIteratorBuilder.Wrapper(iter), potentialColumns, Collections.singletonList(idCol));
        DataIteratorBuilder std = StandardDataIteratorBuilder.forInsert(target, specimenIter, _container, getUser(), dix);
        DataIteratorBuilder tableIter = TableInsertDataIterator.create(std, target, _container, dix, keyColumns, skipColumns, dontUpdate);

        assert !_specimensTableType.getTableName().equalsIgnoreCase(tableName);
        info(tableName + ": Starting merge of data...");

        Pump pump = new Pump(tableIter, dix);
        pump.run();
        if (dix.getErrors().hasErrors())
            throw dix.getErrors().getLastRowError();
        int rowCount = pump.getRowCount();

        info(tableName + "updated or inserted " + rowCount + " rows of data");
        return new Pair<>(availableColumns, rowCount);
    }


    private void replaceTable(DbSchema schema, SpecimenImportFile file, TableInfo target, boolean addEntityId, boolean hasContainerColumn)
        throws IOException, ValidationException
    {
        ComputedColumn entityIdCol = null;
        if (addEntityId)
        {
            entityIdCol = new EntityIdComputedColumn();
        }

        replaceTable(schema, file, file.getTableType().getTableName(), target, false, hasContainerColumn, null, null, entityIdCol);
    }


    /**
     * Deletes the target table and inserts new rows.
     *
     * @param schema The dbschema
     * @param file SpecimenImportFile
     * @param tableName Fully qualified table name, e.g., "study.Vials"
     * @param generateGlobaluniqueIds Generate globalUniqueIds if any needed
     * @param hasContainerColumn
     * @param drawDate DrawDate column or null
     * @param drawTime DrawTime column or null
     * @return A pair of the columns actually found in the data values and a total row count.
     * @throws IOException
     */
    public <T extends ImportableColumn> Pair<List<T>, Integer> replaceTable(
            DbSchema schema, SpecimenImportFile file, String tableName, @Nullable TableInfo target,
            boolean generateGlobaluniqueIds, boolean hasContainerColumn, ComputedColumn drawDate, ComputedColumn drawTime,
            ComputedColumn... computedColumnsAddl)
            throws IOException, ValidationException
    {
        if (file == null)
        {
            info(tableName + ": No rows to replace");
            return new Pair<>(Collections.emptyList(), 0);
        }

        ensureNotCanceled();
        info(tableName + ": Starting replacement of all data...");

        assert !_specimensTableType.getTableName().equalsIgnoreCase(tableName);
        if (hasContainerColumn)
            executeSQL(schema, "DELETE FROM " + tableName + " WHERE Container = ?", _container.getId());
        else
            executeSQL(schema, "DELETE FROM " + tableName);


        ArrayList<ComputedColumn> computedColumns = new ArrayList<>();
        if (null != drawDate)
            computedColumns.add(drawDate);
        if (null != drawTime)
            computedColumns.add(drawTime);
        computedColumns.addAll(Arrays.asList(computedColumnsAddl));

        final List<String> newUniqueIds = (generateGlobaluniqueIds && _generateGlobalUniqueIds > 0) ?
                getValidGlobalUniqueIds(_generateGlobalUniqueIds) : null;

        if (null != newUniqueIds)
        {
            computedColumns.add(new ComputedColumn()
            {
                int idCount = 0;

                @Override
                public String getName()
                {
                    return GLOBAL_UNIQUE_ID_TSV_COL;
                }

                @Override
                public Object getValue(Map<String, Object> row) throws ValidationException
                {
                    Object uniqueid = row.get(GLOBAL_UNIQUE_ID_TSV_COL);
                    if (null == uniqueid)
                        uniqueid = newUniqueIds.get(idCount++);
                    return uniqueid;
                }
            });
        }

        int rowCount;
        ColumnDescriptor[] tsvColumns;

        try
        {
            if (null == target)
            {
                String dbname = tableName;
                if (dbname.startsWith(schema.getName() + "."))
                    dbname = dbname.substring(schema.getName().length() + 1);
                target = schema.getTable(dbname);
            }
            if (null == target)
                throw new IllegalStateException("Could not resolve table: " + tableName);

            DataIteratorContext dix = new DataIteratorContext();
            dix.setInsertOption(QueryUpdateService.InsertOption.IMPORT);
            DataLoader tsv = loadTsv(file);
            tsvColumns = tsv.getColumns();

/*          // DEBUG: Dump data
            StringBuilder infoCol = new StringBuilder("");
            for (ColumnDescriptor cd : tsvColumns)
                infoCol.append(cd.getColumnName() + ", ");
            info(infoCol.toString());
            String[][] lines = tsv.getFirstNLines(15);
            for (String[] line : lines)
            {
                StringBuilder infoRow = new StringBuilder("");
                for (String item : line)
                    infoRow.append(item + ", ");
                info(infoRow.toString());
            }
            info("");
*/
            // CONSIDER turn off data conversion
            //for (ColumnDescriptor cd : tsvColumns) cd.clazz = String.class;
            // CONSIDER sue AsyncDataIterator
            //DataIteratorBuilder asyncIn = new AsyncDataIterator.Builder(tsv);
            DataIteratorBuilder asyncIn = tsv;
            DataIteratorBuilder specimenWrapped = new SpecimenImportBuilder(target, asyncIn, file.getTableType().getColumns(), computedColumns);
            DataIteratorBuilder standardEtl = StandardDataIteratorBuilder.forInsert(target, specimenWrapped, _container, getUser(), dix);
            DataIteratorBuilder persist = ((UpdateableTableInfo)target).persistRows(standardEtl, dix);
            Pump pump = new Pump(persist,dix);
            pump.setProgress(new ListImportProgress()
            {
                long heartBeat = HeartBeat.currentTimeMillis();

                @Override
                public void setTotalRows(int rows)
                {

                }

                @Override
                public void setCurrentRow(int currentRow)
                {
                    if (0 == currentRow%SQL_BATCH_SIZE)
                    {
                        if (0 == currentRow % (SQL_BATCH_SIZE*100))
                            info(currentRow + " rows loaded...");
                        long hb = HeartBeat.currentTimeMillis();
                        if (hb == heartBeat)
                            return;
                        ensureNotCanceled();
                        heartBeat = hb;
                    }
                }
            });
            pump.run();
            if (dix.getErrors().hasErrors())
            {
                throw new ValidationException(dix.getErrors().getLastRowError().getMessage() + " (File: " + file.getTableType().getName() + ")");
            }
            rowCount = pump.getRowCount();

            info(tableName + ": Replaced all data with " + rowCount + " new rows.");

        }
        finally
        {
            file.getStrategy().close();
        }


        List<T> availableColumns = new ArrayList<>();
        Set<String> tsvColumnNames = new CaseInsensitiveHashSet();
        for (ColumnDescriptor c : tsvColumns)
            tsvColumnNames.add(c.getColumnName());

        for (T column : (Collection<T>)file.getTableType().getColumns())
        {
            if (tsvColumnNames.contains(column.getTsvColumnName()) || tsvColumnNames.contains(column.getDbColumnName()))
                availableColumns.add(column);
        }

        return new Pair<>(availableColumns, rowCount);
    }


    private class SpecimenImportBuilder implements DataIteratorBuilder
    {
        final TableInfo target;
        final DataIteratorBuilder dib;
        final Collection<? extends ImportableColumn> importColumns;
        final List<ComputedColumn> computedColumns;

        SpecimenImportBuilder(TableInfo table, DataIteratorBuilder in, Collection<? extends ImportableColumn> importColumns, List<ComputedColumn> computedColumns)
        {
            dib = in;
            this.target = table;
            this.importColumns = importColumns;
            this.computedColumns = computedColumns;
        }

        @Override
        public DataIterator getDataIterator(final DataIteratorContext context)
        {
            MapDataIterator in = DataIteratorUtil.wrapMap(dib.getDataIterator(context), false);
            return new SpecimenImportIterator(this, in, context);
        }
    }

    // TODO We should consider trying to let the Standard DataIterator "import alias" replace some of this ImportableColumn behavior
    // TODO that might let us switch SpecimenImportBuilder to after StandardDataIteratorBuilder instead of before
    // TODO StandardDataIteratorBuilder should be enforcing max length
    private class SpecimenImportIterator extends SimpleTranslator
    {
        Map<String,Object> _rowMap;

        SpecimenImportIterator(SpecimenImportBuilder sib, MapDataIterator in, DataIteratorContext context)
        {
            super(in, context);

            SqlDialect d = DbSchema.getTemp().getSqlDialect();

            CaseInsensitiveHashSet tsvColumnNames = new CaseInsensitiveHashSet();
            for (int i=1 ; i<= in.getColumnCount() ; i++)
                tsvColumnNames.add(in.getColumnInfo(i).getName());

            // deal with computedColumns that might mask importColumns
            CaseInsensitiveHashSet seen = new CaseInsensitiveHashSet();

            for (final ComputedColumn cc : sib.computedColumns)
            {
                if (null != cc && seen.add(cc.getName()))
                {
                    ColumnInfo col = new ColumnInfo(cc.getName(), JdbcType.OTHER);
                    Callable call = new Callable()
                    {
                        @Override
                        public Object call() throws Exception
                        {
                            Object computedValue = cc.getValue(_rowMap);
                            if (computedValue instanceof Parameter.TypedValue)
                                return ((Parameter.TypedValue) computedValue).getJdbcParameterValue();
                            else
                                return computedValue;
                        }
                    };
                    addColumn(col,call);
                }
            }

            for (final ImportableColumn ic : sib.importColumns)
            {
                if (seen.add(ic.getLegalDbColumnName(d)))
                {
                    String boundInputColumnName = null;
                    if (tsvColumnNames.contains(ic.getTsvColumnName()))
                        boundInputColumnName = ic.getTsvColumnName();
                    else if (tsvColumnNames.contains(ic.getDbColumnName()))
                        boundInputColumnName = ic.getDbColumnName();
                    final String name = boundInputColumnName;
                    ColumnInfo col = new ColumnInfo(ic.getLegalDbColumnName(d), ic.getJdbcType());
                    Callable call = new Callable()
                    {
                        @Override
                        public Object call() throws Exception
                        {
                            Object ret = null;
                            if (null != name)
                                ret = _rowMap.get(name);
                            if (null == ret)
                                ret = ic.getDefaultValue();

                            if (ic.getMaxSize() >= 0 && ret instanceof String)
                            {
                                if (((String)ret).length() > ic.getMaxSize())
                                {
                                    throw new SQLException("Value \"" + ret + "\" is too long for column " +
                                            ic.getDbColumnName() + ".  The maximum allowable length is " + ic.getMaxSize() + ".");
                                }
                            }

                            return ret;
                        }
                    };
                    addColumn(col,call);
                }
            }
        }

        @Override
        protected void processNextInput()
        {
            _rowMap = ((MapDataIterator)getInput()).getMap();
        }
    }



    private DataLoader loadTsv(@NotNull SpecimenImportFile importFile) throws IOException
    {
        assert null != importFile;

        SpecimenTableType type = importFile.getTableType();
        String tableName = type.getTableName();

        info(tableName + ": Parsing data file for table...");

        Collection<? extends ImportableColumn> columns = type.getColumns();
        Map<String, ColumnDescriptor> expectedColumns = new HashMap<>(columns.size());

        for (ImportableColumn col : columns)
            expectedColumns.put(col.getTsvColumnName().toLowerCase(), col.getColumnDescriptor());

        DataLoader loader = importFile.getDataLoader();

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

        return loader;
    }


    private Pair<List<SpecimenColumn>, Integer> populateTempTable(TempTablesHolder tempTablesHolder, SpecimenImportFile file, boolean merge)
            throws IOException, ValidationException
    {
        info("Populating specimen temp table...");
        TempTableInfo tempTableInfo = tempTablesHolder.getTempTableInfo();
        int rowCount;
        List<SpecimenColumn> loadedColumns = new ArrayList<>();

        ComputedColumn lsidCol = new ComputedColumn()
        {
            public String getName() { return "LSID"; }
            public Object getValue(Map<String, Object> row) throws ValidationException
            {
                String id = (String) row.get(GLOBAL_UNIQUE_ID_TSV_COL);
                if (id == null)
                    id = (String) row.get(SPEC_NUMBER_TSV_COL);

                if (id == null)
                {
                    throw new ValidationException("GlobalUniqueId is required but was not supplied");
                }

                Lsid lsid = SpecimenService.get().getSpecimenMaterialLsid(_container, id);
                return lsid.toString();
            }
        };

        // remove VISIT_COL since that's a computed column
        // 1) should that be removed from SPECIMEN_COLUMNS?
        // 2) convert this to DataIterator?
        SpecimenColumn _visitCol = null;
        SpecimenColumn _participantIdCol = null;
        for (SpecimenColumn sc : _specimenColumns)
        {
            if (StringUtils.equals("VisitValue", sc.getDbColumnName()))
                _visitCol = sc;
            else if (StringUtils.equals("Ptid", sc.getDbColumnName()))
                _participantIdCol = sc;
        }

        Study study = StudyManager.getInstance().getStudy(_container);
        final SequenceNumImportHelper h = new SequenceNumImportHelper(study, null);
        final ParticipantIdImportHelper piih = new ParticipantIdImportHelper(study, _user, null);
        final SpecimenColumn visitCol = _visitCol;
        final SpecimenColumn dateCol = DRAW_TIMESTAMP;
        final SpecimenColumn participantIdCol = _participantIdCol;
        final Parameter.TypedValue nullDouble = Parameter.nullParameter(JdbcType.DOUBLE);

        ComputedColumn computedParticipantIdCol = new ComputedColumn()
        {
            @Override
            public String getName()
            {
                return participantIdCol.getDbColumnName();
            }

            @Override
            public Object getValue(Map<String, Object> row) throws ValidationException
            {
                Object p = SpecimenImporter.this.getValue(participantIdCol, row);
                return piih.translateParticipantId(p);
            }
        };

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

        ComputedColumn drawDateCol = new ComputedColumn()
        {
            @Override
            public String getName()
            {
                return "DrawDate";
            }

            @Override
            public Object getValue(Map<String, Object> row)
            {
                Object d = SpecimenImporter.this.getValue(dateCol, row);
                return DateUtil.getDateOnly((Date) d);
            }
        };

        ComputedColumn drawTimeCol = new ComputedColumn()
        {
            @Override
            public String getName()
            {
                return "DrawTime";
            }

            @Override
            public Object getValue(Map<String, Object> row)
            {
                Object d = SpecimenImporter.this.getValue(dateCol, row);
                return DateUtil.getTimeOnly((Date)d);
            }
        };

        Pair<List<SpecimenColumn>, Integer> pair = new Pair<>(null, 0);
        boolean success = true;
        final int MAX_TRYS = 3;
        for (int tryCount = 0; tryCount < MAX_TRYS; tryCount += 1)
        {
            try
            {
                pair = replaceTable(tempTableInfo.getSchema(), file, tempTableInfo.getSelectName(), tempTableInfo, true, false, drawDateCol, drawTimeCol,
                        lsidCol, sequencenumCol, computedParticipantIdCol);

                loadedColumns = pair.first;
                rowCount = pair.second;

                if (rowCount == 0)
                {
                    info("Found no specimen columns to import. Temp table will not be loaded.");
                    return pair;
                }

                remapTempTableLookupIndexes(tempTableInfo.getSchema(), tempTableInfo.getSelectName(), loadedColumns);

                updateTempTableVisits(tempTableInfo.getSchema(), tempTableInfo.getSelectName());

                if (merge)
                {
                    checkForConflictingSpecimens(tempTableInfo.getSchema(), tempTableInfo.getSelectName(), loadedColumns);
                }
            }
            catch (OptimisticConflictException e)
            {
                if (tryCount + 1 < MAX_TRYS)
                    success = false;        // Try again
                else
                    throw e;
            }
            if (success)
                break;
        }

        updateTempTableSpecimenHash(tempTablesHolder, loadedColumns);

        info("Specimen temp table populated.");
        return pair;
    }

    protected void remapTempTableLookupIndexes(DbSchema schema, String tempTable, List<SpecimenColumn> loadedColumns)
    {
        String sep = "";
        SQLFragment remapExternalIdsSql = new SQLFragment("UPDATE ").append(tempTable).append(" SET ");
        for (SpecimenColumn col : loadedColumns)
        {
            if (col.getFkTable() != null)
            {
                remapExternalIdsSql.append(sep).append(col.getLegalDbColumnName(_dialect)).append(" = (SELECT RowId FROM ")
                        .append(getTableInfoFromFkTableName(col.getFkTable()).getSelectName()).append(" ").append(col.getFkTableAlias())
                        .append(" WHERE ").append("(").append(tempTable).append(".")
                        .append(col.getLegalDbColumnName(_dialect)).append(" = ").append(col.getFkTableAlias()).append(".").append(col.getFkColumn())
                        .append("))");
                sep = ",\n\t";
            }
        }

        info("Remapping lookup indexes in temp table...");
        if (DEBUG)
            info(remapExternalIdsSql.toDebugString());
        executeSQL(schema, remapExternalIdsSql);
        info("Update complete.");
    }

    private void updateTempTableVisits(DbSchema schema, String tempTable)
    {
        Study study = StudyManager.getInstance().getStudy(_container);
        if (study.getTimepointType() != TimepointType.VISIT)
        {
            info("Updating visit values to match draw timestamps (date-based studies only)...");
            SQLFragment visitValueSql = new SQLFragment();
            visitValueSql.append("UPDATE ").append(tempTable).append(" SET VisitValue = (");
            visitValueSql.append(StudyManager.sequenceNumFromDateSQL("DrawTimestamp"));
            visitValueSql.append(");");
            if (DEBUG)
                info(visitValueSql.toDebugString());
            executeSQL(schema, visitValueSql);
            info("Update complete.");
        }
    }

    protected void checkForConflictingSpecimens(DbSchema schema, String tempTable, List<SpecimenColumn> loadedColumns)
    {
        if (!StudyManager.getInstance().getStudy(_container).getRepositorySettings().isSpecimenDataEditable())
        {
            info("Checking for conflicting specimens before merging...");

            // Columns used in the specimen hash
            StringBuilder hashCols = new StringBuilder();
            for (SpecimenColumn col : loadedColumns)
            {
                if (col.getTargetTable().isSpecimens() && col.getAggregateEventFunction() == null)
                {
                    hashCols.append(",\n\t");
                    hashCols.append(col.getLegalDbColumnName(_dialect));
                }
            }
            hashCols.append("\n");

            SQLFragment existingEvents = new SQLFragment("SELECT GlobalUniqueId");
            existingEvents.append(hashCols);
            existingEvents.append("FROM ").append(getTableInfoVial(), "Vial").append("\n");
            existingEvents.append("JOIN ").append(getTableInfoSpecimen(), "Specimen").append("\n");
            existingEvents.append("ON Vial.SpecimenId = Specimen.RowId\n");
            existingEvents.append("WHERE Vial.GlobalUniqueId IN (SELECT GlobalUniqueId FROM ").append(tempTable).append(")\n");

            SQLFragment tempTableEvents = new SQLFragment("SELECT GlobalUniqueId");
            tempTableEvents.append(hashCols);
            tempTableEvents.append("FROM ").append(tempTable);

            // "UNION ALL" the temp and the existing tables and group by columns used in the specimen hash
            SQLFragment allEventsByHashCols = new SQLFragment("SELECT COUNT(*) AS Group_Count, * FROM (\n");
            allEventsByHashCols.append("(\n").append(existingEvents).append("\n)\n");
            allEventsByHashCols.append("UNION ALL /* SpecimenImporter.checkForConflictingSpecimens() */\n");
            allEventsByHashCols.append("(\n").append(tempTableEvents).append("\n)\n");
            allEventsByHashCols.append(") U\n");
            allEventsByHashCols.append("GROUP BY GlobalUniqueId");
            allEventsByHashCols.append(hashCols);

            Map<String, List<Map<String, Object>>> rowsByGUID = new HashMap<>();
            Set<String> duplicateGUIDs = new TreeSet<>();

            Map<String, Object>[] allEventsByHashColsResults = new SqlSelector(schema, allEventsByHashCols).getMapArray();

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
                        rowsByGUID.put(guid, new ArrayList<>(Arrays.asList(row)));
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
        else
        {
            // Check if any incoming vial is already present in the vial table; this is not allowed
            info("Checking for conflicting specimens in editable repsoitory...");
            SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM ").append(getTableInfoVial().getSelectName());
            sql.append(" WHERE GlobalUniqueId IN " + "(SELECT GlobalUniqueId FROM ");
            sql.append(tempTable).append(")");
            ArrayList<Integer> counts = new SqlSelector(schema, sql).getArrayList(Integer.class);
            if (1 != counts.size())
            {
                throw new IllegalStateException("Expected one and only one count of rows.");
            }
            else if (0 != counts.get(0) && _generateGlobalUniqueIds > 0)
            {
                // We were trying to generate globalUniqueIds
                throw new OptimisticConflictException("Attempt to generate global unique ids failed.", null, 0);
            }
            else if (0 != counts.get(0))
            {
                throw new IllegalStateException("With an editable specimen repository, importing may not reference any existing specimen. " +
                        counts.get(0) + " imported specimen events refer to existing specimens.") ;
            }
            info("No conflicting specimens found");
        }
    }

    private void updateTempTableSpecimenHash(TempTablesHolder tempTablesHolder, List<SpecimenColumn> loadedColumns)
    {
        DbSchema schema = tempTablesHolder.getTempTableInfo().getSchema();
        String tempTableName = tempTablesHolder.getTempTableInfo().getSelectName();
        String selectInsertTempTableName = tempTablesHolder.getSelectInsertTempTableInfo().getSelectName();

        // NOTE: In merge case, we've already checked the specimen hash columns are not in conflict.
        SQLFragment conflictResolvingSubselect = new SQLFragment("SELECT GlobalUniqueId");
        for (SpecimenColumn col : loadedColumns)
        {
            if (col.getTargetTable().isSpecimens())
            {
                conflictResolvingSubselect.append(",\n\t");
                String selectCol = tempTableName + "." + col.getLegalDbColumnName(_dialect);

                if (col.getAggregateEventFunction() != null)
                    conflictResolvingSubselect.append(col.getAggregateEventFunction()).append("(").append(selectCol).append(")");
                else
                {
                    String singletonAggregate;
                    if (col.getJavaClass().equals(Boolean.class))
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
                conflictResolvingSubselect.append(" AS ").append(col.getLegalDbColumnName(_dialect));
            }
        }
        conflictResolvingSubselect.append("\nFROM ").append(tempTableName).append("\nGROUP BY GlobalUniqueId");

        SQLFragment updateHashSql = new SQLFragment("SELECT (");
        makeUpdateSpecimenHashSql(schema, _container, loadedColumns, "InnerTable.", updateHashSql);
        updateHashSql.append(") AS SpecimenHash, ")
                .append("InnerTable.GlobalUniqueId");
        updateHashSql.append("\n\tINTO ").append(selectInsertTempTableName)
                .append("\n\tFROM (").append(conflictResolvingSubselect).append(") InnerTable");

        info("Calculating specimen hash values into second temp table...");
        if (DEBUG)
            info(updateHashSql.toDebugString());
        executeSQL(schema, updateHashSql);
        info("Done calculating specimen hash values.");
        tempTablesHolder.getSelectInsertTempTableInfo().track();   // We've now created the second temp table

        SQLFragment setSpecimenHashSql = new SQLFragment("UPDATE ");
        setSpecimenHashSql.append(tempTableName).append(" SET SpecimenHash = InnerTable.SpecimenHash\nFROM ")
                .append(selectInsertTempTableName).append(" InnerTable\nWHERE ")
                .append(tempTableName).append(".GlobalUniqueId = InnerTable.GlobalUniqueId");

        info("Updating specimen hash values in temp table...");
        if (DEBUG)
            info(setSpecimenHashSql.toDebugString());
        executeSQL(schema, setSpecimenHashSql);
        info("Update complete.");
        info("Temp table populated.");
    }


    public static void makeUpdateSpecimenHashSql(DbSchema schema, Container container, List<SpecimenColumn> loadedColumns, String innerTable, SQLFragment updateHashSql)
    {
        ArrayList<String> hash = new ArrayList<>();
        hash.add("?");
        updateHashSql.add("Fld-" + container.getRowId());
        String strType = schema.getSqlDialect().sqlCastTypeNameFromJdbcType(JdbcType.VARCHAR);

        Map<String, SpecimenColumn> loadedColumnMap = new HashMap<>();
        loadedColumns.forEach(col -> loadedColumnMap.put(col.getTsvColumnName(), col));
        BASE_SPECIMEN_COLUMNS.forEach(col -> {
            if (col.getTargetTable().isSpecimens())
            {
                if (loadedColumnMap.isEmpty() || loadedColumnMap.containsKey(col.getTsvColumnName()))
                {
                    String columnName = innerTable + col.getLegalDbColumnName(schema.getSqlDialect());
                    hash.add("'~'");
                    hash.add(" CASE WHEN " + columnName + " IS NOT NULL THEN CAST(" + columnName + " AS " + strType + ") ELSE '' END");
                }
                else
                {
                    hash.add("'~'");
                }
            }
        });

        updateHashSql.append(schema.getSqlDialect().concatenate(hash.toArray(new String[hash.size()])));
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


    private Parameter.TypedValue getValueParameter(ImportableColumn col, Map tsvRow) throws SQLException
    {
        Object value = getValue(col, tsvRow);

        if (value == null)
        {
            // Currently used by labs.tsv clinic, sal, repository, and enpoint columns
            value = col.getDefaultValue();

            if (value == null)
                return Parameter.nullParameter(col.getJdbcType());
        }

        Parameter.TypedValue typed = new Parameter.TypedValue(value, col.getJdbcType());

        if (col.getMaxSize() >= 0)
        {
            Object valueToBind = Parameter.getValueToBind(typed, col.getJdbcType());
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

    private static class TempTablesHolder
    {
        private final TempTableInfo _tempTableInfo;             // main specimen temp table
        private final TempTableInfo _selectInsertTempTableInfo; // temp table used to Select Insert into while populating main temp table
        private final Runnable _createIndexes;

        public TempTablesHolder(TempTableInfo tempTableInfo, TempTableInfo selectInsertTempTableInfo, Runnable createIndexes)
        {
            _tempTableInfo = tempTableInfo;
            _selectInsertTempTableInfo = selectInsertTempTableInfo;
            _createIndexes = createIndexes;
        }

        public TempTableInfo getTempTableInfo() {
            return _tempTableInfo;
        }

        public TempTableInfo getSelectInsertTempTableInfo() {
            return _selectInsertTempTableInfo;
        }

        public Runnable getCreateIndexes() {
            return _createIndexes;
        }
    }

    private TempTablesHolder createTempTable()
    {

        info("Creating temp table to hold archive data...");
        SqlDialect dialect = DbSchema.getTemp().getSqlDialect();

        StringBuilder sql = new StringBuilder();
        int randomizer = (new Random().nextInt(900000000) + 100000000);  // Ensure 9-digit random number

        ArrayList<ColumnInfo> columns = new ArrayList<>();

        String strType = dialect.sqlTypeNameFromSqlType(Types.VARCHAR);

        sql.append("\n(\n    RowId ").append(dialect.getUniqueIdentType()).append(", ");
        columns.add(new ColumnInfo("RowId", JdbcType.INTEGER, 0, false));
        columns.get(0).setAutoIncrement(true);

        sql.append("LSID ").append(strType).append("(300) NOT NULL, ");
        columns.add(new ColumnInfo("LSID", JdbcType.VARCHAR, 300, false));

        sql.append("SpecimenHash ").append(strType).append("(300), ");
        columns.add(new ColumnInfo("SpecimenHash", JdbcType.VARCHAR, 300, true));

        sql.append(DRAW_DATE.getDbColumnName()).append(" ").append(DRAW_DATE.getDbType()).append(", ");
        columns.add(new ColumnInfo(DRAW_DATE.getDbColumnName(), DRAW_DATE.getJdbcType(), 0, true));

        sql.append(DRAW_TIME.getDbColumnName()).append(" ").append(DRAW_TIME.getDbType());
        columns.add(new ColumnInfo(DRAW_TIME.getDbColumnName(), DRAW_TIME.getJdbcType(), 0, true));

        for (SpecimenColumn col : _specimenColumns)
        {
            String name = col.getLegalDbColumnName(_dialect);
            sql.append(",\n    ").append(name).append(" ").append(col.getDbType());
            columns.add(new ColumnInfo(name, col.getJdbcType(), col.getMaxSize(), true));
        }
        sql.append("\n);");

        TempTableInfo tempTableInfo = new TempTableInfo("SpecimenUpload", columns, Arrays.asList("RowId"));
        final String fullTableName = tempTableInfo.getSelectName();

        sql.insert(0, "CREATE TABLE " + fullTableName + " ");
        executeSQL(DbSchema.getTemp(), sql);
        tempTableInfo.track();

        // globalUniquId
        final String globalUniqueIdIndexSql = "CREATE INDEX IX_SpecimenUpload" + randomizer + "_GlobalUniqueId ON " + fullTableName + "(GlobalUniqueId)";
        if (DEBUG)
            info(globalUniqueIdIndexSql);
        executeSQL(DbSchema.getTemp(), globalUniqueIdIndexSql);

        // We'll Insert Into this one with the calculated specimenHashes and then Update the temp table from there
        ArrayList<ColumnInfo> columns2 = new ArrayList<>();
        columns2.add(new ColumnInfo(GLOBAL_UNIQUE_ID.getDbColumnName(), GLOBAL_UNIQUE_ID.getJdbcType(), GLOBAL_UNIQUE_ID.getMaxSize(), true));
        columns2.add(new ColumnInfo("SpecimenHash", JdbcType.VARCHAR, 300, true));
        TempTableInfo selectInsertTempTableInfo = new TempTableInfo("SpecimenUpload2", columns2, Collections.singletonList("RowId"));

        final String rowIdIndexSql = "CREATE INDEX IX_SpecimenUpload" + randomizer + "_RowId ON " + fullTableName + "(RowId)";
        final String lsidIndexSql = "CREATE INDEX IX_SpecimenUpload" + randomizer + "_LSID ON " + fullTableName + "(LSID)";
        final String hashIndexSql = "CREATE INDEX IX_SpecimenUpload" + randomizer + "_SpecimenHash ON " + fullTableName + "(SpecimenHash)";

        // delay remaining indexes
        Runnable createIndexes = new Runnable()
        {
            @Override
            public void run()
            {
                if (DEBUG)
                {
                    info(rowIdIndexSql);
                    info(lsidIndexSql);
                    info(hashIndexSql);
                }
                executeSQL(DbSchema.getTemp(), rowIdIndexSql);
                executeSQL(DbSchema.getTemp(), lsidIndexSql);
                executeSQL(DbSchema.getTemp(), hashIndexSql);
                info("Created indexes on table " + fullTableName);
            }
        };

        info("Created temporary table " + fullTableName);
        return new TempTablesHolder(tempTableInfo, selectInsertTempTableInfo ,createIndexes);
    }


    private static final String SPECIMEN_SEQUENCE_NAME = "org.labkey.study.samples";

    private List<String> getValidGlobalUniqueIds(int count)
    {
        List<String> uniqueIds = new ArrayList<>();
        DbSequence sequence = DbSequenceManager.get(_container, SPECIMEN_SEQUENCE_NAME);
        sequence.ensureMinimum(70000);

        Sort sort = new Sort();
        sort.appendSortColumn(FieldKey.fromString("GlobalUniqueId"), Sort.SortDirection.DESC, false);
        Set<String> columns = new HashSet<>();
        columns.add("GlobalUniqueId");
        Set<String> currentIds = new HashSet<>(new TableSelector(getTableInfoVial(), columns, null, sort).getArrayList(String.class));

        for (int i = 0; i < count; i += 1)
        {
            while (true)
            {
                String id = ((Integer)sequence.next()).toString();

                if (!currentIds.contains(id))
                {
                    uniqueIds.add(id);
                    break;
                }
            }
        }

        return uniqueIds;
    }


    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        TempTableInfo _simpleTable;

        private static final String TABLE = "SpecimenImporterTest";

        @Before
        public void createTable() throws SQLException
        {
            List<ColumnInfo> columns = new ArrayList<>();
            columns.add(new ColumnInfo("Container",JdbcType.GUID, 0, false));
            columns.add(new ColumnInfo("id", JdbcType.VARCHAR, 0, false));
            columns.add(new ColumnInfo("s", JdbcType.VARCHAR, 30, true));
            columns.get(columns.size()-1).setKeyField(true);
            columns.add(new ColumnInfo("i", JdbcType.INTEGER, 0, true));
            columns.add(new ColumnInfo("entityid", JdbcType.GUID, 0, false));
            _simpleTable = new TempTableInfo(TABLE, columns, Arrays.asList("s"));

            new SqlExecutor(_simpleTable.getSchema()).execute("CREATE TABLE " + _simpleTable.getSelectName() +
                    "(Container VARCHAR(255) NOT NULL, id VARCHAR(10) NOT NULL, s VARCHAR(32), i INTEGER, entityid VARCHAR(36))");
            _simpleTable.track();
        }


        @After
        public void dropTable() throws SQLException
        {
            if (null != _simpleTable)
                _simpleTable.delete();
        }


        private TableResultSet selectValues() throws SQLException
        {
            return new SqlSelector(_simpleTable.getSchema(), "SELECT Container,id,s,i,entityid FROM " + _simpleTable + " ORDER BY id").getResultSet();
        }


        private Map<String, Object> row(String s, Integer i)
        {
            Map<String, Object> map = new CaseInsensitiveHashMap<>();
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

            ListofMapsDataIterator values = new ListofMapsDataIterator(
                    new LinkedHashSet<>(Arrays.asList("s","i")),
                    Arrays.asList(
                        row("Bob", 100),
                        row("Sally", 200),
                        row(null, 300))
            );


            SpecimenImporter importer = new SpecimenImporter(c, null);      // TODO: don't have user here
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
            Pair<List<ImportableColumn>, Integer> pair = importer.mergeTable(_simpleTable.getSchema(), _simpleTable.getSelectName(), _simpleTable, cols, values, idCol, true);
            assertNotNull(pair);
            assertEquals(pair.first.size(), cols.size());
            assertEquals(3, pair.second.intValue());
            assertEquals(3, counter[0].intValue());


            String bobGUID, sallyGUID, nullGUID, jimmyGUID;
            int jimmyID;

            try (TableResultSet rs = selectValues())
            {
                Iterator<Map<String, Object>> iter = rs.iterator();
                Map<String, Object> row0 = iter.next();
                Map<String, Object> row1 = iter.next();
                Map<String, Object> row2 = iter.next();
                assertFalse(iter.hasNext());

                assertEquals("Bob", row0.get("s"));
                assertEquals(100, row0.get("i"));
                assertEquals("1", row0.get("id"));
                bobGUID = (String)row0.get("entityid");
//                assertNotNull(bobGUID);

                assertEquals("Sally", row1.get("s"));
                assertEquals(200, row1.get("i"));
                assertEquals("2", row1.get("id"));
                sallyGUID = (String)row1.get("entityid");
//                assertNotNull(sallyGUID);

                assertEquals(null, row2.get("s"));
                assertEquals(300, row2.get("i"));
                assertEquals("3", row2.get("id"));
                nullGUID = (String)row2.get("entityid");
//                assertNotNull(nullGUID);
            }

            // Add one new row, update one existing row.
            values = new ListofMapsDataIterator(
                    new LinkedHashSet<>(Arrays.asList("s","i")),
                    Arrays.asList(
                            row("Bob", 105),
                            row(null, 305),
                            row("Jimmy", 405)
                    )
            );


            pair = importer.mergeTable(_simpleTable.getSchema(), _simpleTable.getSelectName(), _simpleTable, cols, values, idCol, true);
            assertEquals(pair.first.size(), cols.size());
            assertEquals(3, pair.second.intValue());
//            assertEquals(4, counter[0].intValue());

            try (TableResultSet rs = selectValues())
            {
                Iterator<Map<String, Object>> iter = rs.iterator();
                Map<String, Object> row0 = iter.next();
                Map<String, Object> row1 = iter.next();
                Map<String, Object> row2 = iter.next();
                Map<String, Object> row3 = iter.next();
                assertFalse(iter.hasNext());

                assertEquals("Bob", row0.get("s"));
                assertEquals(105, row0.get("i"));
                assertEquals("1", row0.get("id"));
                assertEquals(bobGUID, row0.get("entityid"));

                assertEquals("Sally", row1.get("s"));
                assertEquals(200, row1.get("i"));
                assertEquals("2", row1.get("id"));
                assertEquals(sallyGUID, row1.get("entityid"));

                assertEquals(null, row2.get("s"));
                assertEquals(305, row2.get("i"));
                assertEquals("3", row2.get("id"));
                assertEquals(nullGUID, row2.get("entityid"));

                assertEquals("Jimmy", row3.get("s"));
                assertEquals(405, row3.get("i"));

                jimmyID = Integer.valueOf((String) row3.get("id"));
                assertTrue(4 <= jimmyID);
                jimmyGUID = (String)row3.get("entityid");

                // HMM, the original mergeTable() fails this check (non DataIteratyor)
                // assertNotNull(jimmyGUID);
            }


            // let's really mix things up and try updating using a column that's not marked as the PK

            Collection<ImportableColumn> colsAlternate = Arrays.asList(
                    new ImportableColumn("s", "s", "VARCHAR(32)", false),
                    new ImportableColumn("i", "i", "INTEGER", true)
            );

            values = new ListofMapsDataIterator(
                    new LinkedHashSet<>(Arrays.asList("s","i")),
                    Arrays.asList(
                            row("John", 405)
                    )
            );

            pair = importer.mergeTable(_simpleTable.getSchema(), _simpleTable.getSelectName(), _simpleTable, colsAlternate, values, idCol, true);
            assertEquals(pair.first.size(), cols.size());
            assertEquals(1, pair.second.intValue());

            try (TableResultSet rs = selectValues())
            {
                Iterator<Map<String, Object>> iter = rs.iterator();
                Map<String, Object> row0 = iter.next();
                Map<String, Object> row1 = iter.next();
                Map<String, Object> row2 = iter.next();
                Map<String, Object> row3 = iter.next();
                assertFalse(iter.hasNext());

                assertEquals("John", row3.get("s"));
                assertEquals(405, row3.get("i"));
                assertEquals(jimmyID, (int)Integer.valueOf((String)row3.get("id")));
                assertEquals(jimmyGUID, row3.get("entityid"));
            }
        }


        @Test
        public void tempTableConsistencyTest() throws Exception
        {
            Container c = JunitUtil.getTestContainer();
            DbSchema schema = StudySchema.getInstance().getSchema();
            User user = TestContext.get().getUser();

            // Provisioned specimen tables need to be created in this order
            TableInfo specimenTableInfo = StudySchema.getInstance().getTableInfoSpecimen(c, user);
            TableInfo vialTableInfo = StudySchema.getInstance().getTableInfoVial(c, user);
            TableInfo specimenEventTableInfo = StudySchema.getInstance().getTableInfoSpecimenEvent(c, user);
            SpecimenImporter importer = new SpecimenImporter(c, user);

            for (SpecimenColumn specimenColumn : importer._specimenColumns)
            {
                TargetTable targetTable = specimenColumn.getTargetTable();
                List<String> tableNames = targetTable.getTableNames();
                for (String tableName : tableNames)
                {
                    TableInfo tableInfo = null;
                    if ("SpecimenEvent".equalsIgnoreCase(tableName))
                        tableInfo = specimenEventTableInfo;
                    else if ("Specimen".equalsIgnoreCase(tableName))
                        tableInfo = specimenTableInfo;
                    else if ("Vial".equalsIgnoreCase(tableName))
                        tableInfo = vialTableInfo;
                    if (null != tableInfo)
                        checkConsistency(tableInfo, tableName, specimenColumn);
                }
            }
            for (ImportableColumn importableColumn : ADDITIVE_COLUMNS)
            {
                checkConsistency(schema, "SpecimenAdditive", importableColumn);
            }
            for (ImportableColumn importableColumn : DERIVATIVE_COLUMNS)
            {
                checkConsistency(schema, "SpecimenDerivative", importableColumn);
            }
            for (ImportableColumn importableColumn : PRIMARYTYPE_COLUMNS)
            {
                checkConsistency(schema, "SpecimenPrimaryType", importableColumn);
            }
            for (ImportableColumn importableColumn : SITE_COLUMNS)
            {
                checkConsistency(schema, "Site", importableColumn);
            }
        }

        private void checkConsistency(DbSchema schema, String tableName, ImportableColumn importableColumn)
        {
            TableInfo tableInfo = schema.getTable(tableName);
            checkConsistency(tableInfo, tableName, importableColumn);
        }

        private void checkConsistency(TableInfo tableInfo, String tableName, ImportableColumn importableColumn)
        {
            String columnName = importableColumn.getDbColumnName();
            ColumnInfo columnInfo = tableInfo.getColumn(columnName);
            JdbcType jdbcType = columnInfo.getJdbcType();

            if (jdbcType == JdbcType.VARCHAR)
            {
                assert importableColumn.getJdbcType() == JdbcType.VARCHAR:
                    "Column '" + columnName + "' in table '" + tableName + "' has inconsistent types in SQL and importer: varchar vs " + importableColumn.getJdbcType().name();
                assert columnInfo.getScale() == importableColumn.getMaxSize() :
                    "Column '" + columnName + "' in table '" + tableName + "' has inconsistent varchar lengths in importer and SQL: " + importableColumn.getMaxSize() + " vs " + columnInfo.getScale();
            }
            assert jdbcType == importableColumn.getJdbcType() ||
                (importableColumn.getJdbcType() == JdbcType.DOUBLE && (jdbcType == JdbcType.REAL || jdbcType == JdbcType.DECIMAL)) :
                "Column '" + columnName + "' in table '" + tableName + "' has inconsistent types in SQL and importer: " + columnInfo.getJdbcType() + " vs " + importableColumn.getJdbcType();
        }
    }


    private int executeSQL(DbSchema schema, CharSequence sql, Object... params)
    {
        return executeSQL(schema, new SQLFragment(sql, params));
    }


    private int executeSQL(DbSchema schema, SQLFragment sql)
    {
        debug(sql);
        return new SqlExecutor(schema).execute(sql);
    }

    public static List<String> getRolledupDuplicateVialColumnNames(Container container, User user)
    {
        // Return names of columns where column is 2nd thru nth column rolled up on same Event column
        List<String> rolledupNames = new ArrayList<>();
        RollupMap<EventVialRollup> eventToVialRollups = getEventToVialRollups(container, user);
        for (List<RollupInstance<EventVialRollup>> rollupList : eventToVialRollups.values())
        {
            boolean duplicate = false;
            for (RollupInstance<EventVialRollup> rollupItem : rollupList)
            {
                if (duplicate)
                    rolledupNames.add(rollupItem.first.toLowerCase());
                duplicate = true;
            }
        }
        return rolledupNames;
    }

    public static RollupMap<EventVialRollup> getEventToVialRollups(Container container, User user)
    {
        List<EventVialRollup> rollups = SpecimenImporter.getEventVialRollups();
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, null);

        Domain fromDomain = specimenTablesProvider.getDomain("SpecimenEvent", true);
        if (null == fromDomain)
            throw new IllegalStateException("Expected SpecimenEvent table to already be created.");

        Domain toDomain = specimenTablesProvider.getDomain("Vial", true);
        if (null == toDomain)
            throw new IllegalStateException("Expected Vial table to already be created.");

        return getRollups(fromDomain, toDomain, rollups);
    }

    public static List<String> getRolledupSpecimenColumnNames(Container container, User user)
    {
        List<String> rolledupNames = new ArrayList<>();
        RollupMap<VialSpecimenRollup> vialToSpecimenRollups = getVialToSpecimenRollups(container, user);
        for (List<RollupInstance<VialSpecimenRollup>> rollupList : vialToSpecimenRollups.values())
        {
            for (RollupInstance<VialSpecimenRollup> rollupItem : rollupList)
            {
                rolledupNames.add(rollupItem.first.toLowerCase());
            }
        }
        return rolledupNames;
    }

    public static RollupMap<VialSpecimenRollup> getVialToSpecimenRollups(Container container, User user)
    {
        List<VialSpecimenRollup> rollups = getVialSpecimenRollups();
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, null);

        Domain fromDomain = specimenTablesProvider.getDomain("Vial", true);
        if (null == fromDomain)
            throw new IllegalStateException("Expected Vial table to already be created.");

        Domain toDomain = specimenTablesProvider.getDomain("Specimen", true);
        if (null == toDomain)
            throw new IllegalStateException("Expected Specimen table to already be created.");

        return getRollups(fromDomain, toDomain, rollups);
    }

    private static <K extends Rollup> RollupMap<K> getRollups(Domain fromDomain, Domain toDomain, List<K> considerRollups)
    {
        RollupMap<K> matchedRollups = new RollupMap<>();
        List<PropertyDescriptor> toProperties = new ArrayList<>();

        for (DomainProperty domainProperty : toDomain.getNonBaseProperties())
            toProperties.add(domainProperty.getPropertyDescriptor());

        for (DomainProperty domainProperty : fromDomain.getProperties())
        {
            PropertyDescriptor property = domainProperty.getPropertyDescriptor();
            findRollups(matchedRollups, property, toProperties, considerRollups, false);
        }
        return matchedRollups;
    }

    public static <K extends Rollup> void findRollups(RollupMap<K> resultRollups, PropertyDescriptor fromProperty,
                                   List<PropertyDescriptor> toProperties, List<K> considerRollups, boolean allowTypeMismatch)
    {
        for (K rollup : considerRollups)
        {
            for (PropertyDescriptor toProperty : toProperties)
            {
                if (rollup.match(fromProperty, toProperty, allowTypeMismatch))
                {
                    List<RollupInstance<K>> matches = resultRollups.get(fromProperty.getName());
                    if (null == matches)
                    {
                        matches = new ArrayList<>();
                        resultRollups.put(fromProperty.getName(), matches);
                    }
                    matches.add(new RollupInstance<>(toProperty.getName(), rollup, fromProperty.getJdbcType(), toProperty.getJdbcType()));
                }
            }
        }
    }

    public static Map<String, Pair<String, RollupInstance<EventVialRollup>>> getVialToEventNameMap(List<PropertyDescriptor> vialProps, List<PropertyDescriptor> eventProps)
    {
        return getRollupNameMap(vialProps, eventProps, SpecimenImporter.getEventVialRollups());
    }

    public static Map<String, Pair<String, RollupInstance<VialSpecimenRollup>>> getSpecimenToVialNameMap(List<PropertyDescriptor> vialProps, List<PropertyDescriptor> eventProps)
    {
        return getRollupNameMap(vialProps, eventProps, SpecimenImporter.getVialSpecimenRollups());
    }

    // Build a map that indicates for a property in Vial or Specimen, which property in Event or Vial, respectively will rollup to it
    private static <K extends Rollup> Map<String, Pair<String, RollupInstance<K>>> getRollupNameMap(List<PropertyDescriptor> toProps,
                                                              List<PropertyDescriptor> fromProps, List<K> considerRollups)
    {
        RollupMap<K> matchedRollups = new RollupMap<>();
        for (PropertyDescriptor property : fromProps)
        {
            SpecimenImporter.findRollups(matchedRollups, property, toProps, considerRollups, true);
        }

        Map<String, Pair<String, RollupInstance<K>>> resultMap = new HashMap<>();
        for (PropertyDescriptor fromProp : fromProps)
        {
            List<RollupInstance<K>> rollupInstances = matchedRollups.get(fromProp.getName());
            if (null != rollupInstances)
            {
                for (RollupInstance<K> rollupInstance : rollupInstances)
                {
                    resultMap.put(rollupInstance.getKey().toLowerCase(), new Pair<>(fromProp.getName(), rollupInstance));
                }
            }
        }
        return resultMap;
    }
}
