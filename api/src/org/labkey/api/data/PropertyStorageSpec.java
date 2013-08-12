/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
    String name;
    JdbcType jdbcType;
    boolean primaryKey = false;
    boolean nullable = true;
    boolean autoIncrement = false;
    boolean isMvEnabled = false;
    boolean entityId = false;
    Integer size = 4000;

    public PropertyStorageSpec(PropertyDescriptor propertyDescriptor)
    {
        setName(propertyDescriptor.getName());
        setJdbcType(propertyDescriptor.getJdbcType());
        setNullable(propertyDescriptor.isNullable());
        setAutoIncrement(propertyDescriptor.isAutoIncrement());
        setMvEnabled(propertyDescriptor.isMvEnabled());
    }

    /**
     * bare mininum storage specification
     */
    public PropertyStorageSpec(String name, JdbcType jdbcType)
    {
        this.name = name;
        this.jdbcType = jdbcType;
    }

    public PropertyStorageSpec(String name, JdbcType jdbcType, int size)
    {
        this.jdbcType = jdbcType;
        this.name = name;
        this.size = size;
    }

    public PropertyStorageSpec(String name, JdbcType jdbcType, int size, Special specialness)
    {
        this.name = name;
        this.jdbcType = jdbcType;
        this.primaryKey = specialness == Special.PrimaryKey;
        this.size = size;
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
        this.size = size;
        return this;
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

    public static class Index
    {
        final public String[] columnNames;
        final public boolean isUnique;

        public Index(boolean unique, String... columnNames)
        {
            this.columnNames = columnNames;
            this.isUnique = unique;
        }

    }

    public enum Special
    {
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
