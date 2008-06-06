/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.query.ExprColumn;

import java.util.EnumSet;
import java.sql.Types;

/**
 * Exposes a Java enum as a virtual query table. Useful for creating lookups when it's really a hard-coded list
 * of possible values that the code needs to match up against exactly. 
 * User: jeckels
 * Date: Jun 2, 2008
*/
public class EnumTableInfo<EnumType extends Enum<EnumType>> extends VirtualTable
{
    private final Class<EnumType> _enum;
    private final EnumValueGetter<EnumType> _getter;

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
     */
    public EnumTableInfo(Class<EnumType> e, DbSchema schema)
    {
        this(e, schema, new EnumValueGetter<EnumType>()
        {
            public String getValue(EnumType e)
            {
                return e.toString();
            }
        });
    }

    /**
     * Exposes an enum as a one-column virtual table, using the getter to determine its value
     * @param e class of the enum
     * @param schema parent DBSchema
     * @param getter callback to determine the String value of each item in the enum
     */
    public EnumTableInfo(Class<EnumType> e, DbSchema schema, EnumValueGetter<EnumType> getter)
    {
        super(schema);
        _enum = e;
        _getter = getter;

        ExprColumn column = new ExprColumn(this, "Value", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".Value"), Types.VARCHAR);
        column.setKeyField(true);
        setTitleColumn(column.getName());
        addColumn(column);
    }

    public SQLFragment getFromSQL(String alias)
    {
        SQLFragment sql = new SQLFragment("(");
        String separator = "";
        EnumSet<EnumType> enumSet = EnumSet.allOf(_enum);
        for (EnumType e : enumSet)
        {
            sql.append(separator);
            separator = " UNION ";
            sql.append("SELECT ? AS VALUE");
            sql.add(_getter.getValue(e));
        }

        sql.append(") AS ");
        sql.append(alias);
        return sql;
    }
}