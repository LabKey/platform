/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.PropertyStorageSpec.Index;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aggregated changes to be performed via SQL DDL statements against a target table by
 * {@link org.labkey.api.exp.api.StorageProvisioner}
 * User: newton
 * Date: Aug 19, 2010
 */
public class TableChange
{
    private static final Logger LOG = Logger.getLogger(TableChange.class);

    private final ChangeType _type;
    private final String _schemaName;
    private final String _tableName;
    private final Collection<PropertyStorageSpec> _columns = new LinkedHashSet<>();
    private final Map<String, String> _columnRenames = new LinkedHashMap<>();
    private final Map<String, Integer> _columnResizes = new LinkedHashMap<>();
    private final Map<Index, Index> _indexRenames = new LinkedHashMap<>();
    private final Domain _domain;

    private Collection<Index> _indices = new LinkedHashSet<>();
    private Collection<PropertyStorageSpec.ForeignKey> _foreignKeys = Collections.emptySet();
    private Collection<Constraint> _constraints;
    private Set<String> _indicesToBeDroppedByName;
    private IndexSizeMode _sizeMode = IndexSizeMode.Auto;

    /** In most cases, domain knows the storage table name **/
    public TableChange(Domain domain, ChangeType changeType)
    {
        this(domain, changeType, domain.getStorageTableName());
    }

    /** Call directly only in cases where domain doesn't have a storage table name, e.g., create table **/
    public TableChange(Domain domain, ChangeType changeType, String tableName)
    {
        _domain = domain;
        _schemaName = domain.getDomainKind().getStorageSchemaName();
        _tableName = tableName;
        _type = changeType;
    }

    public void execute()
    {
        if (isValidChange())
        {
            DomainKind kind = _domain.getDomainKind();
            DbScope scope = kind.getScope();
            SqlExecutor executor = new SqlExecutor(scope);

            boolean success = false;
            try
            {
                for (String sql : scope.getSqlDialect().getChangeStatements(this))
                {
                    LOG.debug("Will issue: " + sql);
                    executor.execute(sql);
                }
                success = true;
            }
            finally
            {
                scope.addCommitTask(() -> kind.invalidate(_domain), success ? DbScope.CommitTaskOption.IMMEDIATE : DbScope.CommitTaskOption.POSTROLLBACK);
            }
        }
    }

    private boolean isValidChange()
    {
        boolean valid = null != getType();
        valid |= null != getSchemaName();
        valid |= null != getTableName();

        if (valid)
        {
            switch (_type)
            {
                case AddColumns:
                case DropColumns:
                case ResizeColumns:
                    valid = !getColumns().isEmpty();
                    break;
                case RenameColumns:
                    valid = !getColumnRenames().isEmpty();
                    break;
                case DropIndices:
                case AddIndices:
                    valid = !getIndexedColumns().isEmpty();
                    break;
                case DropIndicesByName:
                    valid = !getIndicesToBeDroppedByName().isEmpty();
                    break;
                case AddConstraints:
                case DropConstraints:
                    valid = !getConstraints().isEmpty();
            }
        }
        return valid;
    }

    // Issue 26311: when resizing, we need to change the indices as well and SQL Server needs to be given explicit instructions for this.
    // TODO consider indices created from domain as well(domain does not persist property indices after creating currently, so they are not available during resizing)
    public void updateResizeIndices()
    {
        DomainKind kind = _domain.getDomainKind();

        if (_type == ChangeType.ResizeColumns)
        {
            Set<Index> changedIndexColumns = new HashSet<>();
            Map<String, List<Index>> columnIndexMap = new HashMap<>();
            for (Index index : kind.getPropertyIndices(_domain))
            {
                for (String columnName : index.columnNames)
                {
                    if (!columnIndexMap.containsKey(columnName))
                        columnIndexMap.put(columnName, new ArrayList<>());

                    columnIndexMap.get(columnName).add(index);
                }
            }

            // Get any custom indices added to the domain -- these aren't saved anywhere except in the database
            DbSchema schema = DbSchema.get(_schemaName);
                       TableInfo storageTableInfo = schema.getTable(_domain.getStorageTableName());
                if (storageTableInfo != null)
                {
                    for (Pair<TableInfo.IndexType, List<ColumnInfo>> index : storageTableInfo.getAllIndices().values())
                    {
                        List<String> columnNames = index.getValue().stream().map(ColumnInfo::getName).collect(Collectors.toList());

                        // CONSIDER: Move this re-classification of the non-unique index as a unique index into SchemaColumnMetaData.loadUniqueIndices()
                        // SQLServer creates a non-unique index for single large text columns with a "_hashed_" prefix.
                        // The uniqueness is enforced by a database trigger.
                        boolean unique = index.first == TableInfo.IndexType.Unique ||
                                (schema.getSqlDialect().isSqlServer() && columnNames.size() == 1 && columnNames.get(0).startsWith("_hashed_"));

                        // remove the _hashed_ column prefix for SQLServer
                        if (schema.getSqlDialect().isSqlServer() && unique)
                            columnNames = columnNames.stream().map(s -> s.startsWith("_hashed_") ? s.substring("_hashed_".length()) : s).collect(Collectors.toList());

                        Index idx = new Index(unique, columnNames);

                        for (String columnName : columnNames)
                        {
                            if (!columnIndexMap.containsKey(columnName))
                                columnIndexMap.put(columnName, new ArrayList<>());

                            columnIndexMap.get(columnName).add(idx);
                        }
                    }
                }


            for (String name : getColumnResizes().keySet())
            {
                if (columnIndexMap.containsKey(name))
                {
                    changedIndexColumns.addAll(columnIndexMap.get(name));
                }
            }

            getIndexedColumns().addAll(changedIndexColumns);
        }
    }

    public IndexSizeMode getIndexSizeMode()
    {
        return _sizeMode;
    }

    public void setIndexSizeMode(IndexSizeMode sizeMode)
    {
        _sizeMode = sizeMode;
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

    public Collection<Constraint> getConstraints()
    {
        return _constraints;
    }

    public void setConstraints(Collection<Constraint> constraints)
    {
        _constraints = constraints;
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

    /** Add the property to the set of columns tracked in this change along with it's old scale. */
    public void addColumnResize(PropertyDescriptor prop, Integer oldScale)
    {
        addColumn(prop);
        _columnResizes.put(prop.getName(), oldScale);
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
     * Map of existing columns to previous column size.  The new column scale is
     * found the the column map.
     * @return map where key = existing column name, value = old column scale
     */
    public Map<String, Integer> getColumnResizes()
    {
        return Collections.unmodifiableMap(_columnResizes);
    }

    /**
     * @return  map where key = old column name, value = new column name
     */
    public Map<String, String> getColumnRenames()
    {
        return Collections.unmodifiableMap(_columnRenames);
    }

    /**
     * @return  map where key = old index, value = new index
     */
    public Map<Index, Index> getIndexRenames()
    {
        return Collections.unmodifiableMap(_indexRenames);
    }

    public Collection<Index> getIndexedColumns()
    {
        return _indices;
    }

    public void setIndexedColumns(Collection<Index> indices)
    {
        _indices = indices;
    }

    public Set<String> getIndicesToBeDroppedByName(){
        return _indicesToBeDroppedByName;
    }

    public void setIndicesToBeDroppedByName(Set<String> indicesToBeDroppedByName)
    {
        _indicesToBeDroppedByName = indicesToBeDroppedByName;
    }

    public Collection<PropertyStorageSpec.ForeignKey> getForeignKeys()
    {
        return _foreignKeys;
    }

    public void setForeignKeys(Collection<PropertyStorageSpec.ForeignKey> foreignKeys)
    {
        _foreignKeys = foreignKeys;
    }

    public final List<PropertyStorageSpec> toSpecs(Collection<String> columnNames)
    {
        final Domain domain = _domain;
        final DomainKind kind = _domain.getDomainKind();

        Map<String, PropertyStorageSpec> specs = new CaseInsensitiveHashMap<>();
        kind.getBaseProperties(_domain).forEach(p -> specs.put(p.getName(), p));
        domain.getProperties().forEach(dp -> specs.put(dp.getName(), kind.getPropertySpec(dp.getPropertyDescriptor(), domain)));

        return columnNames
                .stream()
                .map(s -> {
                    PropertyStorageSpec spec = specs.get(s);
                    if (spec == null)
                        throw new IllegalArgumentException("Column '" + s + "' not found for use in index on table '" + _domain.getName() + "'");
                    return spec;
                })
                .collect(Collectors.toList());
    }

    public enum ChangeType
    {
        CreateTable,
        DropTable,
        AddColumns,
        DropColumns,
        RenameColumns,
        ResizeColumns,
        DropIndices,
        DropIndicesByName,
        AddIndices,
        DropConstraints,
        AddConstraints
    }

    public enum IndexSizeMode
    {
        Auto,
        Normal,
        OverSized
    }
}
