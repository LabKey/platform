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

import java.sql.Types;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: newton
 * Date: Aug 19, 2010
 * Time: 5:14:04 PM
 */
public class TableChange
{
    final ChangeType type;
    final String schemaName;
    final String tableName;
    Collection<PropertyStorageSpec> columns = new HashSet<PropertyStorageSpec>();
    Collection<PropertyStorageSpec.Index> indices = new HashSet<PropertyStorageSpec.Index>();
    Map<String, String> columnRenames = new HashMap<String, String>();

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

    public void dropColumnExactName(String name)
    {
        if (type != ChangeType.DropColumns)
            throw new IllegalStateException();
        PropertyStorageSpec p = new PropertyStorageSpec(name, Types.VARCHAR);
        p.setExactName(true);
        columns.add(p);
    }

    /**
     * @return  map where key = old column name value = new column name
     */
    public Map<String, String> getColumnRenames()
    {
        return columnRenames;
    }

    public Collection<PropertyStorageSpec.Index> getIndexedColumns()
    {
        return indices;
    }

    public void setIndexedColumns(Collection<PropertyStorageSpec.Index> indices)
    {
        this.indices = indices;
    }

    public enum ChangeType
    {
        CreateTable,
        DropTable,
        AddColumns,
        DropColumns,
        RenameColumns
    }


}
