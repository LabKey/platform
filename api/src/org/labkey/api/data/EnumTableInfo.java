/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.UserSchema;

import java.util.EnumSet;

/**
 * Exposes a Java enum as a virtual query table. Useful for creating lookups when it's really a hard-coded list
 * of possible values that the code needs to match up against exactly. 
 * User: jeckels
 * Date: Jun 2, 2008
*/
public class EnumTableInfo<EnumType extends Enum<EnumType>> extends VirtualTable<UserSchema>
{
    private final Class<EnumType> _enum;
    private final EnumValueGetter<EnumType> _getter;
    @Nullable private String _schemaName;
    @Nullable private String _queryName;
    private final boolean _ordinalPK;

    /**
     * Turns an enum value into a string to expose in the virtual table
     */
    public interface EnumValueGetter<EnumType>
    {
        public String getValue(EnumType e);
    }

    /**
     * Exposes an enum as a one-column virtual table, using its toString() as the value
     * @param e class of the enum
     * @param schema parent DBSchema
     * @param description a description of this table and its uses for display in the schema browser
     */
    public EnumTableInfo(Class<EnumType> e, UserSchema schema, String description, boolean ordinalPK)
    {
        this(e, schema, new EnumValueGetter<EnumType>()
        {
            public String getValue(EnumType e)
            {
                return e.toString();
            }
        }, ordinalPK, description);
    }

    /**
     * Exposes an enum as a one-column virtual table, using the getter to determine its value
     * @param e class of the enum
     * @param schema parent DBSchema
     * @param getter callback to determine the String value of each item in the enum
     * @param description a description of this table and its uses for display in the schema browser
     */
    public EnumTableInfo(Class<EnumType> e, UserSchema schema, EnumValueGetter<EnumType> getter, boolean ordinalPK, String description)
    {
        super(schema.getDbSchema(), e.getSimpleName(), schema);
        setDescription(description);
        _enum = e;
        _getter = getter;
        _ordinalPK = ordinalPK;

        ExprColumn column = new ExprColumn(this, "Value", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".Value"), JdbcType.VARCHAR);
        column.setKeyField(!ordinalPK);
        setTitleColumn(column.getName());
        addColumn(column);

        ExprColumn rowIdColumn = new ExprColumn(this, "RowId", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".RowId"), JdbcType.INTEGER);
        rowIdColumn.setKeyField(ordinalPK);
        rowIdColumn.setHidden(true);
        addColumn(rowIdColumn);
    }



    @Override @NotNull
    public SQLFragment getFromSQL()
    {
        SQLFragment sql = new SQLFragment();
        String separator = "";
        EnumSet<EnumType> enumSet = EnumSet.allOf(_enum);
        for (EnumType e : enumSet)
        {
            sql.append(separator);
            separator = " UNION ";
            sql.append("SELECT ? AS VALUE, ? AS RowId");
            sql.add(_getter.getValue(e));
            sql.add(e.ordinal());
        }
        return sql;
    }

    public String getPublicSchemaName()
    {
        return _schemaName == null ? super.getPublicSchemaName() : _schemaName;
    }

    public void setPublicSchemaName(@Nullable String schemaName)
    {
        _schemaName = schemaName;
    }

    public void setPublicName(@Nullable String queryName)
    {
        _queryName = queryName;
    }

    @Override
    public String getPublicName()
    {
        return _queryName == null ? super.getPublicName() : _queryName;
    }

    @Override
    public boolean isPublic()
    {
        return true;
    }
}