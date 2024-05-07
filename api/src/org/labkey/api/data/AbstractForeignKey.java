/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.data.xml.queryCustomView.FilterType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Convenience base class for {@link ForeignKey} implementors.
 */
public abstract class AbstractForeignKey implements ForeignKey, Cloneable
{
    protected SchemaKey _lookupSchemaKey;
    protected String _tableName;
    protected String _columnName;
    protected String _displayColumnName;
    private boolean _showAsPublicDependency = false;

    // foreignKey lazily creates the target TableInfo, in order to do that it needs to know the container filter to use
    // Some tables may choose to ignore/modify this container filter, but this indicates what the parent table/query/schema
    // is using, and the lookup TableInfo may want to use it as well.
    protected @Nullable ContainerFilter _containerFilter;

    // We will also want a DefaultSchema to use to construct the target schema/table (and check permissions)
    final protected QuerySchema _sourceSchema;

    private Map<FilterOperation, List<FilterType>> _filters = new HashMap<>();

    // Set of additional FieldKeys that query should select to make the join successful.
    private Set<FieldKey> _suggestedFields;

    // Map original FieldKey to query's remapped FieldKey.
    private Map<FieldKey, FieldKey> _remappedFields;

    protected AbstractForeignKey(QuerySchema sourceSchema, @Nullable ContainerFilter cf)
    {
        _sourceSchema = sourceSchema;
        _containerFilter = cf;
    }
    
    protected AbstractForeignKey(QuerySchema sourceSchema, @Nullable ContainerFilter cf, String tableName, String columnName)
    {
        this(sourceSchema, cf, (SchemaKey)null, tableName, columnName, null);
    }

    @Deprecated
    protected AbstractForeignKey(QuerySchema sourceSchema, @Nullable ContainerFilter cf, @Nullable String lookupSchemaName, String tableName, @Nullable String columnName)
    {
        this(sourceSchema, cf, null==lookupSchemaName?null:SchemaKey.fromString(lookupSchemaName), tableName, columnName, null);
    }

    @Deprecated
    protected AbstractForeignKey(QuerySchema sourceSchema, @Nullable ContainerFilter cf, @Nullable String lookupSchemaName, String tableName, @Nullable String columnName, @Nullable String displayColumnName)
    {
        this(sourceSchema, cf, null==lookupSchemaName?null:SchemaKey.fromString(lookupSchemaName), tableName, columnName, displayColumnName);
    }

    protected AbstractForeignKey(QuerySchema sourceSchema, @Nullable ContainerFilter cf, @Nullable SchemaKey lookupSchemaKey, String tableName, @Nullable String columnName, @Nullable String displayColumnName)
    {
        _sourceSchema = sourceSchema;
        _containerFilter = cf;
        _lookupSchemaKey = lookupSchemaKey;
        _tableName = tableName;
        _columnName = columnName;
        _displayColumnName = displayColumnName;
    }

    @Nullable
    protected User getLookupUser()
    {
        if (null != getSourceSchema())
            return getSourceSchema().getUser();

        return null;
    }

    protected QuerySchema getSourceSchema()
    {
        return _sourceSchema;
    }

    protected @Nullable QuerySchema getLookupSchema()
    {
        return null;
    }

    protected ContainerFilter getLookupContainerFilter()
    {
        // if there is an explicit lookup container then use that
        Container c = getLookupContainer();
        if (null != c)
        {
            assert null != getLookupUser();
            return new ContainerFilter.SimpleContainerFilterWithUser(getLookupUser(), c);
        }
        ContainerFilter cf = null;
        QuerySchema targetSchema = getLookupSchema();
        if (targetSchema == null)
        {
            targetSchema = getSourceSchema();
        }

        if (null != _containerFilter)
            cf = _containerFilter;
        else if (targetSchema instanceof UserSchema userSchema)
            cf = userSchema.getDefaultLookupContainerFilter();

        if (null != targetSchema && targetSchema.getContainer().isWorkbook())
        {
            if (null == cf || cf.getType() == ContainerFilter.Type.Current)
                cf = ContainerFilter.Type.CurrentOrParentAndWorkbooks.create(targetSchema);
        }
        return cf;
    }

    @Override
    public SchemaKey getLookupSchemaKey()
    {
        if (_lookupSchemaKey == null)
        {
            initTableAndColumnNames();
        }
        return _lookupSchemaKey;
    }

    @Override
    public Container getLookupContainer()
    {
        return null;
    }

    @Override
    public String getLookupTableName()
    {
        if (_tableName == null)
        {
            initTableAndColumnNames();
        }
        return _tableName;
    }

    @Override
    public String getLookupColumnName()
    {
        if (_columnName == null)
        {
            initTableAndColumnNames();
        }
        return _columnName;
    }

    @Override
    public String getLookupDisplayName()
    {
        return _displayColumnName;
    }

    @Override
    public Map<FilterOperation, List<org.labkey.data.xml.queryCustomView.FilterType>> getFilters()
    {
        return _filters;
    }

    @Override
    public void setFilters(Map<FilterOperation, List<org.labkey.data.xml.queryCustomView.FilterType>> filters)
    {
        _filters = filters;
    }

    private boolean _initNames = false;

    protected void initTableAndColumnNames()
    {
        if (!_initNames)
        {
            _initNames = true;
            TableInfo table = getLookupTableInfo();
            if (table != null)
            {
                if (_lookupSchemaKey == null)
                {
                    String publicSchemaName = table.getPublicSchemaName();
                    if (null != publicSchemaName)
                        _lookupSchemaKey = SchemaKey.fromString(publicSchemaName);
                }

                if (_tableName == null)
                {
                    _tableName = table.getPublicName();
                    if (_tableName == null)
                        _tableName = table.getName();
                }

                if (_columnName == null)
                {
                    List<ColumnInfo> pkColumns = table.getPkColumns();
                    if (pkColumns != null && pkColumns.size() > 0)
                    {
                        int first = 0;
                        if (pkColumns.size() > 1 && isContainerColumn(pkColumns.get(0)))
                            first = 1;
                        _columnName = pkColumns.get(first).getName();
                    }
                }
            }
        }
    }

    boolean isContainerColumn(ColumnInfo c)
    {
        if (JdbcType.GUID != c.getJdbcType())
            return false;
        return "folder".equalsIgnoreCase(c.getName()) || "container".equalsIgnoreCase(c.getName());
    }

    @Override
    public @NotNull NamedObjectList getSelectList(RenderContext ctx)
    {
        TableInfo lookupTable = getLookupTableInfo();
        if (lookupTable == null)
            return new NamedObjectList();

        Map<FilterOperation, List<FilterType>> filterMap = getFilters();
        List<FilterType> filters = (DataRegion.MODE_INSERT == ctx.getMode() && filterMap.containsKey(FilterOperation.insert)) ? filterMap.get(FilterOperation.insert) :
                                   (DataRegion.MODE_UPDATE == ctx.getMode() && filterMap.containsKey(FilterOperation.update)) ? filterMap.get(FilterOperation.update) :
                                    Collections.emptyList();

        return lookupTable.getSelectList(getLookupColumnName(), filters, null, _displayColumnName);
    }


    @Override
    public ForeignKey remapFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> mapping)
    {
        Set<FieldKey> suggested = getSuggestedColumns();
        if (suggested == null || suggested.isEmpty())
            return this;

        // Create a subset of the FieldKey mapping for only the suggested columns
        Map<FieldKey, FieldKey> remappedSuggested = new HashMap<>(suggested.size());
        boolean identityMapping = true;
        for (FieldKey originalField : suggested)
        {
            // Check if the field has already been remapped
            FieldKey field = getRemappedField(originalField);
            FieldKey remappedField = mapping == null ? field : mapping.get(field);
            if (remappedField == null)
                return null;
            remappedSuggested.put(originalField, remappedField);
            identityMapping &= field.equals(remappedField);
        }
        if (identityMapping)
            return this;

        try
        {
            AbstractForeignKey cloned = (AbstractForeignKey)this.clone();
            cloned._remappedFields = remappedSuggested;
            return cloned;
        }
        catch (CloneNotSupportedException e)
        {
            assert false : "Silly programmer, clone not supported for " + this.getClass().getName();
            LogManager.getLogger(AbstractForeignKey.class).error(e);
            return null;
        }
    }


    // Get the possibly remapped FieldKey.
    // Look here for the corrected FieldKey name when trying to resolve columns on the foreign key's parent table.
    protected FieldKey getRemappedField(FieldKey originalFieldKey)
    {
        if (_remappedFields == null || _remappedFields.isEmpty())
            return originalFieldKey;

        FieldKey remappedFieldKey = _remappedFields.get(originalFieldKey);
        if (remappedFieldKey != null)
            return remappedFieldKey;

        return originalFieldKey;
    }

    // Indicate that the FieldKey should be included in the select list of the foreign key parent table.
    public void addSuggested(FieldKey fieldKey)
    {
        if (_suggestedFields == null)
            _suggestedFields = new HashSet<>();
        _suggestedFields.add(fieldKey);
    }

    @Override
    @Nullable
    public Set<FieldKey> getSuggestedColumns()
    {
        if (_suggestedFields == null)
            return null;

        if (null == _remappedFields)
            return Collections.unmodifiableSet(_suggestedFields);

        return _suggestedFields.stream()
            .map(this::getRemappedField)
            .collect(Collectors.toSet());
    }

    /**
     * Check if an alternate key can be used when importing a value for this lookup.
     * The lookup table must meet the following requirements:
     * - Has a single primary key
     * - Has a unique index over a single column that isn't the primary key
     * - The column in the unique index must be a string type
     * - As a fallback, the title column is allowed as an alternate key as well except callers must check that the display value is unique.
     */
    @Override
    public boolean allowImportByAlternateKey()
    {
        TableInfo lookupTable = getLookupTableInfo();
        if (lookupTable == null)
            return false;

        List<ColumnInfo> pkCols = lookupTable.getPkColumns();
        if (pkCols.size() != 1)
            return false;

        // Only use our alternative key approach if we're targeting the PK, since if we're targeting an
        // alternative unique column directly, we don't need to resolve the PK value.
        if (getLookupColumnName() == null)
            return false;
        ColumnInfo lookupColumn = lookupTable.getColumn(getLookupColumnName());
        if (lookupColumn == null)
            return false;
        if (!lookupColumn.isKeyField())
            return false;

        ColumnInfo pkCol = pkCols.get(0);

        Set<ColumnInfo> seen = new HashSet<>();
        List<List<ColumnInfo>> candidates = new ArrayList<>();
        for (Pair<TableInfo.IndexType, List<ColumnInfo>> index : lookupTable.getUniqueIndices().values())
        {
            if (index.getKey() != TableInfo.IndexType.Unique)
                continue;

            if (index.getValue().size() != 1)
                continue;

            ColumnInfo col = index.getValue().get(0);
            if (seen.contains(col))
                continue;
            seen.add(col);

            if (pkCol == col)
                continue;

            if (!col.getJdbcType().isText())
                continue;

            candidates.add(index.getValue());
        }

        ColumnInfo titleCol = lookupTable.getTitleColumn() != null ? lookupTable.getColumn(lookupTable.getTitleColumn()) : null;
        if (titleCol != null && !seen.contains(titleCol) && pkCol != titleCol && titleCol.getJdbcType().isText())
        {
            candidates.add(Collections.singletonList(titleCol));
        }

        return !candidates.isEmpty();
    }

    @Override
    public boolean isShowAsPublicDependency()
    {
        return _showAsPublicDependency;
    }

    public void setShowAsPublicDependency(boolean b)
    {
        _showAsPublicDependency = b;
    }
    
    public static class TestCase
    {
        public static String CONTAINER_SOURCE = "AbstractForeignKeySource";
        public static String CONTAINER_DEST = "AbstractForeignKeyDest";

        @BeforeClass
        public static void doSetup() throws Exception
        {
            doCleanup();

            ContainerManager.createContainer(ContainerManager.getRoot(), CONTAINER_SOURCE, TestContext.get().getUser());
            ContainerManager.createContainer(ContainerManager.getRoot(), CONTAINER_DEST, TestContext.get().getUser());
        }

        @AfterClass
        public static void doCleanup() throws Exception
        {
            Container source = ContainerManager.getForPath(CONTAINER_SOURCE);
            if (source != null)
            {
                ContainerManager.deleteAll(source, TestContext.get().getUser());
            }

            Container dest = ContainerManager.getForPath(CONTAINER_DEST);
            if (dest != null)
            {
                ContainerManager.deleteAll(dest, TestContext.get().getUser());
            }
        }

        /**
         * See: https://github.com/LabKey/platform/pull/4307
         * The purpose of this test is to ensure that when adding a FK across containers, that the target of the FK points to the correct container, rather than the source
         */
        @Test
        public void testCrossContainerFks() throws Exception
        {
            ListDefinition listSource = ListService.get().createList(ContainerManager.getForPath(CONTAINER_SOURCE), CONTAINER_SOURCE, ListDefinition.KeyType.AutoIncrementInteger);
            listSource.setKeyName("RowId");
            Domain d = requireNonNull(listSource.getDomain());
            d.addProperty(new PropertyStorageSpec("Source1", JdbcType.INTEGER));
            d.addProperty(new PropertyStorageSpec("Source2", JdbcType.INTEGER));
            listSource.save(TestContext.get().getUser());

            ListDefinition listDest = ListService.get().createList(ContainerManager.getForPath(CONTAINER_DEST), CONTAINER_DEST, ListDefinition.KeyType.AutoIncrementInteger);
            listDest.setKeyName("RowId");
            d = requireNonNull(listDest.getDomain());
            d.addProperty(new PropertyStorageSpec("Dest1", JdbcType.INTEGER));
            d.addProperty(new PropertyStorageSpec("Dest2", JdbcType.INTEGER));
            listDest.save(TestContext.get().getUser());

            Container workbook = ContainerManager.createContainer(ContainerManager.getForPath(CONTAINER_DEST), null, "The workbook", "The description", WorkbookContainerType.NAME, TestContext.get().getUser());

            TableInfo source = QueryService.get().getUserSchema(TestContext.get().getUser(), ContainerManager.getForPath(CONTAINER_SOURCE), "lists").getTable(CONTAINER_SOURCE, null, true, true);
            int inserted1 = source.getUpdateService().insertRows(TestContext.get().getUser(), ContainerManager.getForPath(CONTAINER_SOURCE), Arrays.asList(
                    new CaseInsensitiveMapWrapper<>(Map.of("Source1", 1, "Source2", 10)),
                    new CaseInsensitiveMapWrapper<>(Map.of("Source1", 2, "Source2", 20)),
                    new CaseInsensitiveMapWrapper<>(Map.of("Source1", 3, "Source2", 30))
            ), new BatchValidationException(), null, null).size();

            Assert.assertEquals("Incorrect row count", 3, inserted1);
            TableInfo dest = QueryService.get().getUserSchema(TestContext.get().getUser(), ContainerManager.getForPath(CONTAINER_DEST), "lists").getTable(CONTAINER_DEST);
            int inserted2 = dest.getUpdateService().insertRows(TestContext.get().getUser(), ContainerManager.getForPath(CONTAINER_DEST), Arrays.asList(
                    new CaseInsensitiveMapWrapper<>(Map.of("Dest1", 10, "Dest2", 100)),
                    new CaseInsensitiveMapWrapper<>(Map.of("Dest1", 20, "Dest2", 200, "Container", workbook.getId())),
                    new CaseInsensitiveMapWrapper<>(Map.of("Dest1", 40, "Dest2", 400))
            ), new BatchValidationException(), null, null).size();
            Assert.assertEquals("Incorrect row count", 3, inserted2);

            if (!(source instanceof AbstractTableInfo ati))
            {
                throw new IllegalStateException("Should be instance of AbstractTableInfo");
            }

            MutableColumnInfo col = ati.getMutableColumn(FieldKey.fromString("Source2"));
            col.setFk(new QueryForeignKey(source.getUserSchema(), null, dest.getUserSchema(), null, CONTAINER_DEST, "Dest1", "Dest2"));
            ati.removeColumn(ati.getColumn(FieldKey.fromString("Source2")));
            ati.addColumn(col);

            Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ati, PageFlowUtil.set(FieldKey.fromString("Source1"), FieldKey.fromString("Source2/Dest2")));
            TableSelector ts = new TableSelector(ati, cols.values(), null, null).setForDisplay(true);
            Assert.assertEquals("Incorrect row count", 3, ts.getRowCount());

            ts.forEachResults(rs -> {
                int sourceVal = rs.getInt(FieldKey.fromString("Source1"));
                Object destVal = rs.getObject(FieldKey.fromString("Source2/Dest2"));

                // sourceVal=3 should not resolve
                Assert.assertEquals("Incorrect lookup value", sourceVal == 3 ? null : sourceVal * 100, destVal);
            });
        }
    }
}
