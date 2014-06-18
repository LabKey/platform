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

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.data.PropertyStorageSpec.Index;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * User: newton
 * Date: Aug 19, 2010
 * Time: 5:14:04 PM
 */
public class TableChange
{
    final ChangeType type;
    final String schemaName;
    final String tableName;
    Collection<PropertyStorageSpec> columns = new LinkedHashSet<>();
    Collection<Index> indices = new LinkedHashSet<>();
    Collection<PropertyStorageSpec.ForeignKey> foreignKeys = Collections.emptySet();
    Map<String, String> columnRenames = new LinkedHashMap<>();
    Map<Index, Index> indexRenames = new LinkedHashMap<>();

    public TableChange(String schemaName, String tableName, ChangeType changeType)
    {
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.type = changeType;
    }

    public ChangeType getType()
    {
        return type;
    }

    public String getSchemaName()
    {
        return schemaName;
    }

    public String getTableName()
    {
        return tableName;
    }

    public Collection<PropertyStorageSpec> getColumns()
    {
        return columns;
    }

    public void addColumn(PropertyStorageSpec prop)
    {
        columns.add(prop);
    }

    public void addColumn(PropertyDescriptor prop)
    {
        addColumn(new PropertyStorageSpec(prop));
    }

    public void addColumnRename(String oldName, String newName)
    {
        columnRenames.put(oldName, newName);
    }

    /**
     * Index will be renamed using the columns listed in the Index.
     * The columns used by the index won't be changed.  We need to
     * pass the list of columns since the index name is created by the dialect.
     *
     * @param oldIndex Old index to be renamed.
     * @param newIndex New index to be renamed.
     */
    public void addIndexRename(Index oldIndex, Index newIndex)
    {
        indexRenames.put(oldIndex, newIndex);
    }

    public void dropColumnExactName(String name)
    {
        if (type != ChangeType.DropColumns)
            throw new IllegalStateException();
        PropertyStorageSpec p = new PropertyStorageSpec(name, JdbcType.VARCHAR);
        p.setExactName(true);
        columns.add(p);
    }

    /**
     * @return  map where key = old column name, value = new column name
     */
    public Map<String, String> getColumnRenames()
    {
        return columnRenames;
    }

    /**
     * @return  map where key = old index, value = new index
     */
    public Map<Index, Index> getIndexRenames()
    {
        return indexRenames;
    }

    public Collection<Index> getIndexedColumns()
    {
        return indices;
    }

    public void setIndexedColumns(Collection<Index> indices)
    {
        this.indices = indices;
    }

    public Collection<PropertyStorageSpec.ForeignKey> getForeignKeys()
    {
        return foreignKeys;
    }

    public void setForeignKeys(Collection<PropertyStorageSpec.ForeignKey> foreignKeys)
    {
        this.foreignKeys = foreignKeys;
    }

    public enum ChangeType
    {
        CreateTable,
        DropTable,
        AddColumns,
        DropColumns,
        RenameColumns,
        DropIndices,
        AddIndices
    }


}
