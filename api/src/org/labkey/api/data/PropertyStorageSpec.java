/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
package org.labkey.api.data;

import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.PropertyDescriptor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * User: newton
 * Date: Aug 24, 2010
 * Time: 4:08:39 PM
 *
 * The reason that we have this class, instead of doing something like reusing PropertyDescriptor, is that we also need
 * a storage spec like this when there is not a full property descriptor such as for the base properties of DomainKinds.
 * ColumnInfo and SqlColumn are also mismatched for various reasons.
 *
 */
public class PropertyStorageSpec
{
    public static final int DEFAULT_SIZE = 4000;

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getImportAliases()
    {
        return importAliases;
    }

    public void setImportAliases(String importAliases)
    {
        this.importAliases = importAliases;
    }

    public static class ForeignKey
    {
        private String _columnName;
        private String _schemaName;     // Schema that provides table, even if table is provisioned to live elsewhere
        private String _tableName;
        private String _foreignColumnName;
        private String _domainURI;      // URI of the domain the FK references, if it's provisioned
        private boolean _isProvisioned;
        private TableInfo _tableInfoProvisioned = null;

        public ForeignKey(String columnName, String schemaName, String tableName, String foreignColumnName,
                          String domainURI, boolean isProvisioned)
        {
            _columnName = columnName;
            _schemaName = schemaName;
            _tableName = tableName;
            _foreignColumnName = foreignColumnName;
            _domainURI = domainURI;
            _isProvisioned = isProvisioned;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public String getTableName()
        {
            return _tableName;
        }

        public String getColumnName()
        {
            return _columnName;
        }

        public String getForeignColumnName()
        {
            return _foreignColumnName;
        }

        public boolean isProvisioned()
        {
            return _isProvisioned;
        }

        public TableInfo getTableInfoProvisioned()
        {
            return _tableInfoProvisioned;
        }

        public void setTableInfoProvisioned(TableInfo tableInfoProvisioned)
        {
            _tableInfoProvisioned = tableInfoProvisioned;
        }

        public String getDomainURI()
        {
            return _domainURI;
        }
    }

    String name;
    JdbcType jdbcType;
    boolean primaryKey = false;
    boolean nullable = true;
    boolean autoIncrement = false;
    boolean isMvEnabled = false;
    boolean entityId = false;
    private String description;
    private String importAliases;
    Integer size = DEFAULT_SIZE;
    private Object defaultValue = null;

    public PropertyStorageSpec(PropertyDescriptor propertyDescriptor)
    {
        setName(propertyDescriptor.getStorageColumnName());
        if (null == getName())
        {
            // PropertyDescriptors that are shared across domains may not have this set. This is an EHR scenario
            setName(propertyDescriptor.getName());
        }
        setJdbcType(propertyDescriptor.getJdbcType());
        _setSize(propertyDescriptor.getScale(), propertyDescriptor.getJdbcType());
        setNullable(propertyDescriptor.isNullable());
        setAutoIncrement(propertyDescriptor.isAutoIncrement());
        setMvEnabled(propertyDescriptor.isMvEnabled());
        setDescription(propertyDescriptor.getDescription());
        setImportAliases(propertyDescriptor.getImportAliases());
    }

    /**
     * bare mininum storage specification
     */
    public PropertyStorageSpec(String name, JdbcType jdbcType)
    {
        this(name, jdbcType, DEFAULT_SIZE);
    }

    public PropertyStorageSpec(String name, JdbcType jdbcType, int size)
    {
        this(name, jdbcType, size, Special.None);
    }

    public PropertyStorageSpec(String name, JdbcType jdbcType, int size, String description)
    {
        this(name, jdbcType, size, Special.None, true, false, null, description, null);
    }

    public PropertyStorageSpec(String name, JdbcType jdbcType, int size, String description, String alias)
    {
        this(name, jdbcType, size, Special.None, true, false, null, description, alias);
    }

    public PropertyStorageSpec(String name, JdbcType jdbcType, int size, boolean nullable, Object defaultValue)
    {
        this(name, jdbcType, size, Special.None, nullable, false, defaultValue);
    }

    public PropertyStorageSpec(String name, JdbcType jdbcType, int size, Special specialness)
    {
        this(name, jdbcType, size, specialness, true, false, null);
    }

    public PropertyStorageSpec(String name, JdbcType jdbcType, int size, Special specialness, boolean nullable,
                               boolean autoIncrement, Object defaultValue)
    {
        this(name, jdbcType, size, specialness, nullable, autoIncrement, defaultValue, null, null);
    }

    public PropertyStorageSpec(String name, JdbcType jdbcType, int size, Special specialness, boolean nullable,
                               boolean autoIncrement, Object defaultValue, String description, String alias)
    {
        this.name = name;
        this.jdbcType = jdbcType;
        this.primaryKey = (specialness == Special.PrimaryKey);
        _setSize(size, jdbcType);
        this.nullable = nullable;
        this.autoIncrement = autoIncrement;
        this.defaultValue = defaultValue;
        this.description = description;
        if (null != alias)
        {
            Set<String> aliases = new HashSet<>();
            aliases.add(alias);
            setImportAliases(ColumnRenderProperties.convertToString(aliases));
        }
    }

    public String getName()
    {
        return name;
    }

    public PropertyStorageSpec setName(String name)
    {
        this.name = name;
        return this;
    }

    public JdbcType getJdbcType()
    {
        return jdbcType;
    }

    public PropertyStorageSpec setJdbcType(JdbcType jdbcType)
    {
        this.jdbcType = jdbcType;
        return this;
    }

    public boolean isPrimaryKey()
    {
        return primaryKey;
    }

    /**
     * cause this field to be provisioned as a primary key in the db
     */
    public PropertyStorageSpec setPrimaryKey(boolean primaryKey)
    {
        this.primaryKey = primaryKey;
        return this;
    }

    public boolean isNullable()
    {
        return nullable;
    }

    /**
     * defaults true if not set
     */
    public PropertyStorageSpec setNullable(boolean nullable)
    {
        this.nullable = nullable;
        return this;
    }


    public boolean isAutoIncrement()
    {
        return autoIncrement;
    }

    /**
     * defaults false if not set
     * Assumes that the JdbcType is JdbcType.INTEGER. Enforced in dialect.
     */
    public PropertyStorageSpec setAutoIncrement(boolean autoIncrement)
    {
        this.autoIncrement = autoIncrement;
        return this;
    }


    public boolean isEntityId()
    {
        return entityId;
    }

    /**
     * defaults false if not set
     * Assumes that the JdbcType is JdbcType.VARCHAR. Enforced in dialect.
     */
    public PropertyStorageSpec setEntityId(boolean entityId)
    {
        this.entityId = entityId;
        return this;
    }


    /**
     *
     * @return max value size if specified, null if DBMS default
     */
    public Integer getSize()
    {
        return size;
    }

    public PropertyStorageSpec setSize(int size)
    {
        _setSize(size, jdbcType);
        return this;
    }


    private void _setSize(int size, JdbcType type)
    {
        if (type == JdbcType.VARCHAR && size == 0)
        {
            // ignore for now, should probably throw IllegalState
            return;
        }
        this.size = size;
    }


    public boolean isMvEnabled()
    {
        return isMvEnabled;
    }

    public PropertyStorageSpec setMvEnabled(boolean isMvEnabled)
    {
        this.isMvEnabled = isMvEnabled;
        return this;
    }

    public String getMvIndicatorColumnName()
    {
        return getMvIndicatorColumnName(getName());
    }

    public static String getMvIndicatorColumnName(String rootName)
    {
        return rootName + "_" + MvColumn.MV_INDICATOR_SUFFIX;
    }

    public Object getDefaultValue()
    {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue)
    {
        this.defaultValue = defaultValue;
    }

    public static class Index
    {
        final public String[] columnNames;
        final public boolean isUnique;

        public Index(boolean unique, String... columnNames)
        {
            this.columnNames = columnNames;
            this.isUnique = unique;
        }

        public Index(boolean unique, Collection<String> columnNames)
        {
            this.columnNames = columnNames.toArray(new String[columnNames.size()]);
            this.isUnique = unique;
        }

    }

    public enum Special
    {
        None,
        PrimaryKey
    }


    /*** SPECIAL CASES for fixing broken tables ***/

    /* FOR DROP COLUMN */
    boolean _exactName=false;
    public void setExactName(boolean b)
    {
        _exactName = b;
    }
    public boolean getExactName()
    {
        return _exactName;
    }
}
