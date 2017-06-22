/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
import org.labkey.api.util.Pair;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes a Java enum as a virtual query table. Useful for creating lookups when it's really a hard-coded list
 * of possible values that the code needs to match up against exactly. 
 * User: jeckels
 * Date: Jun 2, 2008
*/
public class EnumTableInfo<EnumType extends Enum<EnumType>> extends VirtualTable<UserSchema>
{
    private final Class<EnumType> _enum;
    private final EnumValueGetter<EnumType> _valueGetter;
    private final EnumRowIdGetter<EnumType> _rowIdGetter;
    @Nullable private String _schemaName;
    @Nullable private String _queryName;

    /**
     * Turns an enum value into a string to expose in the virtual table
     */
    public interface EnumValueGetter<EnumType>
    {
        String getValue(EnumType e);
    }

    public interface EnumRowIdGetter<EnumType>
    {
        int getRowId(EnumType e);
    }

    /**
     * Exposes an enum as a three column virtual table, using its toString() as the value, ordinal() as rowId, and ordinal() as ordinal
     * @param e class of the enum
     * @param schema parent DBSchema*
     * @param description a description of this table and its uses for display in the schema browser
     * @param rowIdPK use the rowId as the key field, otherwise use value field
     */
    public EnumTableInfo(Class<EnumType> e, UserSchema schema, String description, boolean rowIdPK)
    {
        this(e, schema, EnumType::toString, EnumType::ordinal, rowIdPK, description);
    }

    /**
     * Exposes an enum as a three column virtual table, using valueGetter to determine its value, ordinal() as rowId, and ordinal() as ordinal
     * @param e class of the enum
     * @param schema parent DBSchema
     * @param valueGetter callback to determine the String value of each item in the enum
     * @param rowIdPK use the rowId as the key field, otherwise use value field
     * @param description a description of this table and its uses for display in the schema browser
     */
    public EnumTableInfo(Class<EnumType> e, UserSchema schema, EnumValueGetter<EnumType> valueGetter, boolean rowIdPK, String description)
    {
        this(e, schema, valueGetter, EnumType::ordinal, rowIdPK, description);
    }

    /**
     * Exposes an enum as a three column virtual table, using its toString() as the value, rowIdGetter to determine rowId, and ordinal() as ordinal
     * @param e class of the enum
     * @param schema parent DBSchema
     * @param rowIdGetter callback to determine the int rowId of each item in the enum
     * @param rowIdPK use the rowId as the key field, otherwise use value field
     * @param description a description of this table and its uses for display in the schema browser
     */
    public EnumTableInfo(Class<EnumType> e, UserSchema schema, EnumRowIdGetter<EnumType> rowIdGetter, boolean rowIdPK, String description)
    {
        this(e, schema, EnumType::toString, rowIdGetter, rowIdPK, description);
    }

    /**
     * Exposes an enum as a three column virtual table, using valueGetter to determine its value, rowIdGetter to determine rowId, and ordinal() as ordinal
     * @param e class of the enum
     * @param schema parent DBSchema
     * @param valueGetter callback to determine the String value of each item in the enum
     * @param rowIdGetter callback to determine the int rowId of each item in the enum
     * @param rowIdPK use the rowId as the key field, otherwise use value field
     * @param description a description of this table and its uses for display in the schema browser
     */
    public EnumTableInfo(Class<EnumType> e, UserSchema schema, EnumValueGetter<EnumType> valueGetter, EnumRowIdGetter<EnumType> rowIdGetter, boolean rowIdPK, String description)
    {
        super(schema.getDbSchema(), e.getSimpleName(), schema);
        setDescription(description);
        _enum = e;
        _valueGetter = valueGetter;
        _rowIdGetter = rowIdGetter;

        ExprColumn column = new ExprColumn(this, "Value", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".Value"), JdbcType.VARCHAR);
        column.setKeyField(!rowIdPK);
        setTitleColumn(column.getName());
        addColumn(column);

        ExprColumn rowIdColumn = new ExprColumn(this, "RowId", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".RowId"), JdbcType.INTEGER);
        rowIdColumn.setKeyField(rowIdPK);
        rowIdColumn.setHidden(true);
        addColumn(rowIdColumn);

        ExprColumn ordinalColumn = new ExprColumn(this, "Ordinal", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".Ordinal"), JdbcType.INTEGER);
        ordinalColumn.setHidden(true);
        addColumn(ordinalColumn);
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
            sql.append("SELECT ? AS VALUE, ? AS RowId, ? As Ordinal");
            sql.add(_valueGetter.getValue(e));
            sql.add(_rowIdGetter.getRowId(e));
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

    @Override
    public @NotNull Map<String, Pair<IndexType, List<ColumnInfo>>> getAllIndices()
    {
        Map<String, Pair<IndexType, List<ColumnInfo>>> indices = new HashMap<>();
        indices.put("pk_rowId", Pair.of(IndexType.Primary, Arrays.asList(getColumn("RowId"))));
        indices.putAll(getUniqueIndices());
        return indices;
    }

    @NotNull
    @Override
    public Map<String, Pair<IndexType, List<ColumnInfo>>> getUniqueIndices()
    {
        Map<String, Pair<IndexType, List<ColumnInfo>>> indices = new HashMap<>();
        indices.put("uq_value", Pair.of(IndexType.Unique, Arrays.asList(getColumn("Value"))));
        indices.put("uq_oridinal", Pair.of(IndexType.Unique, Arrays.asList(getColumn("Ordinal"))));
        return indices;
    }
}
