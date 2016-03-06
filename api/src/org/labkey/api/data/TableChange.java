/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.data.PropertyStorageSpec.Index;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;

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
    private static final Logger LOG = Logger.getLogger(TableChange.class);

    private final ChangeType _type;
    private final String _schemaName;
    private final String _tableName;
    private final Collection<PropertyStorageSpec> _columns = new LinkedHashSet<>();
    private final Map<String, String> _columnRenames = new LinkedHashMap<>();
    private final Map<Index, Index> _indexRenames = new LinkedHashMap<>();
    private final Domain _domain;

    private Collection<Index> _indices = new LinkedHashSet<>();
    private Collection<PropertyStorageSpec.ForeignKey> _foreignKeys = Collections.emptySet();

    /** In most cases, domain knows the storage table name **/
    public TableChange(Domain domain, ChangeType changeType)
    {
        this(domain, changeType, domain.getStorageTableName());
    }

    /** Only for cases where domain doesn't have a storage table name, e.g., create table **/
    public TableChange(Domain domain, ChangeType changeType, String tableName)
    {
        _domain = domain;
        _schemaName = domain.getDomainKind().getStorageSchemaName();
        _tableName = tableName;
        _type = changeType;
    }

    public void execute()
    {
        DomainKind kind = _domain.getDomainKind();
        DbScope scope = kind.getScope();
        SqlExecutor executor = new SqlExecutor(scope);

        try
        {
            for (String sql : scope.getSqlDialect().getChangeStatements(this))
            {
                LOG.debug("Will issue: " + sql);
                executor.execute(sql);
            }
        }
        finally
        {
            kind.invalidate(_domain);
        }
    }

    public ChangeType getType()
    {
        return _type;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public String getTableName()
    {
        return _tableName;
    }

    public Collection<PropertyStorageSpec> getColumns()
    {
        return _columns;
    }

    public void addColumn(PropertyStorageSpec prop)
    {
        _columns.add(prop);
    }

    public void addColumn(PropertyDescriptor prop)
    {
        addColumn(new PropertyStorageSpec(prop));
    }

    public void addColumnRename(String oldName, String newName)
    {
        _columnRenames.put(oldName, newName);
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
        _indexRenames.put(oldIndex, newIndex);
    }

    public void dropColumnExactName(String name)
    {
        if (_type != ChangeType.DropColumns)
            throw new IllegalStateException();
        PropertyStorageSpec p = new PropertyStorageSpec(name, JdbcType.VARCHAR);
        p.setExactName(true);
        _columns.add(p);
    }

    /**
     * @return  map where key = old column name, value = new column name
     */
    public Map<String, String> getColumnRenames()
    {
        return _columnRenames;
    }

    /**
     * @return  map where key = old index, value = new index
     */
    public Map<Index, Index> getIndexRenames()
    {
        return _indexRenames;
    }

    public Collection<Index> getIndexedColumns()
    {
        return _indices;
    }

    public void setIndexedColumns(Collection<Index> indices)
    {
        _indices = indices;
    }

    public Collection<PropertyStorageSpec.ForeignKey> getForeignKeys()
    {
        return _foreignKeys;
    }

    public void setForeignKeys(Collection<PropertyStorageSpec.ForeignKey> foreignKeys)
    {
        _foreignKeys = foreignKeys;
    }

    public enum ChangeType
    {
        CreateTable,
        DropTable,
        AddColumns,
        DropColumns,
        RenameColumns,
        DropIndices,
        AddIndices,
        ResizeColumns
    }
}
