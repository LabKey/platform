package org.labkey.specimen.importer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.util.TimeOnlyDate;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ImportableColumn
{
    protected final String _dbType;

    private final String _tsvColumnName;
    private final Collection<String> _tsvColumnAliases;
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
        this(tsvColumnName, Collections.emptyList(), dbColumnName, databaseType, unique, maskOnExport);
    }

    public ImportableColumn(String tsvColumnName, Collection<String> tsvColumnAliases, String dbColumnName, String databaseType, boolean unique, boolean maskOnExport)
    {
        _tsvColumnName = tsvColumnName;
        _tsvColumnAliases = List.copyOf(tsvColumnAliases);
        _dbColumnName = dbColumnName;
        _unique = unique;
        _maskOnExport = maskOnExport;

        switch (databaseType)
        {
            case ImportTypes.DURATION_TYPE:
                _dbType = SpecimenSchema.get().getSqlDialect().getDefaultDateTimeDataType();
                _javaClass = TimeOnlyDate.class;
                break;
            case ImportTypes.DATETIME_TYPE:
                _dbType = SpecimenSchema.get().getSqlDialect().getDefaultDateTimeDataType();
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
        if (dbType.contains(ImportTypes.DATETIME_TYPE))
            throw new IllegalStateException("Java types for DateTime/Timestamp columns should be previously initialized.");

        if (dbType.contains("VARCHAR"))
            return String.class;
        else if (dbType.contains("FLOAT") || dbType.contains("DOUBLE") || dbType.contains(ImportTypes.NUMERIC_TYPE))
            return Double.class;
        else if (dbType.contains("BIGINT"))
            return Long.class;
        else if (dbType.contains("INT"))
            return Integer.class;
        else if (dbType.contains(ImportTypes.BOOLEAN_TYPE))
            return Boolean.class;
        else if (dbType.contains(ImportTypes.BINARY_TYPE))
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

    // Preferred TSV column name. For example, the name that gets exported.
    public String getPrimaryTsvColumnName()
    {
        return _tsvColumnName;
    }

    public Collection<String> getImportAliases()
    {
        return _tsvColumnAliases;
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
