/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.api.exp.PropertyDescriptor;

/**
 * Created by IntelliJ IDEA.
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
    int sqlTypeInt;
    boolean unique = false;
    boolean indexed = false;
    boolean nullable = true;
    boolean autoIncrement = false;
    boolean hasQcNote;

    Integer size;

    public PropertyStorageSpec(PropertyDescriptor propertyDescriptor)
    {
        setName(propertyDescriptor.getName());
        setSqlTypeInt(propertyDescriptor.getSqlTypeInt());
        setUnique(propertyDescriptor.isLookup());
        setNullable(propertyDescriptor.isNullable());
        setAutoIncrement(propertyDescriptor.isAutoIncrement());
        hasQcNote(propertyDescriptor.isMvEnabled());
        // todo size
    }

    /**
     * bare mininum storage specification
     *
     * @param name
     * @param sqlTypeInt
     */
    public PropertyStorageSpec(String name, int sqlTypeInt)
    {
        this.name = name;
        this.sqlTypeInt = sqlTypeInt;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    /**
     *
     * @return a java.sql.Types.x
     */
    public int getSqlTypeInt()
    {
        return sqlTypeInt;
    }

    /**
     *
     * @param sqlTypeInt - a java.sql.Types.x
     */
    public void setSqlTypeInt(int sqlTypeInt)
    {
        this.sqlTypeInt = sqlTypeInt;
    }

    public boolean isUnique()
    {
        return unique;
    }

    /**
     * enforce uniqueness. defaults false
     * @param unique
     */
    public void setUnique(boolean unique)
    {
        this.unique = unique;
    }

    public boolean isIndexed()
    {
        return indexed;
    }

    /**
     *  defaults false
     *
     * @param indexed
     */
    public void setIndexed(boolean indexed)
    {
        this.indexed = indexed;
    }

    public boolean isNullable()
    {
        return nullable;
    }

    /**
     * defaults true if not set
     * @param nullable
     */
    public void setNullable(boolean nullable)
    {
        this.nullable = nullable;
    }


    public boolean isAutoIncrement()
    {
        return autoIncrement;
    }

    /**
     * defaults false if not set
     * @param autoIncrement
     */
    public void setAutoIncrement(boolean autoIncrement)
    {
        this.autoIncrement = autoIncrement;
    }

    /**
     *
     * @return max value size if specified, null if DBMS default
     */
    public Integer getSize()
    {
        return size;
    }

    /**
     *
     * @param size
     */
    public void setSize(int size)
    {
        this.size = size;
    }

    public boolean hasQcNote()
    {
        return hasQcNote;
    }

    public void hasQcNote(boolean hasQcNote)
    {
        this.hasQcNote = hasQcNote;
    }

    public String getQcNoteFieldName()
    {
        return this.getName() + "qcnote";
    }
}
