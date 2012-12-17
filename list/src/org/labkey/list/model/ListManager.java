/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.list.model;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PkFilter;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpressionFactory.AbstractStringExpression.NullValueBehavior;
import org.labkey.api.util.StringExpressionFactory.FieldKeyStringExpression;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.webdav.SimpleDocumentResource;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ListManager implements SearchService.DocumentProvider
{
    private static final Logger LOG = Logger.getLogger(ListManager.class);
    private static final ListManager INSTANCE = new ListManager();
    public static final String LIST_AUDIT_EVENT = "ListAuditEvent";

    public static ListManager get()
    {
        return INSTANCE;
    }

    public DbSchema getSchema()
    {
        return ExperimentService.get().getSchema();
    }

    public TableInfo getTinfoList()
    {
        return getSchema().getTable("list");
    }

    public ListDef[] getLists(Container container)
    {
        ListDef.Key key = new ListDef.Key(container);
        return key.select();
    }


    public ListDef getList(Container container, int id)
    {
        SimpleFilter filter = new PkFilter(getTinfoList(), id);
        filter.addCondition("Container", container);

        return new TableSelector(getTinfoList(), filter, null).getObject(ListDef.class);
    }

    
    // Note: callers must invoke indexer (can't invoke here since we may be in a transaction)
    public ListDef insert(User user, ListDef def) throws SQLException
    {
        return Table.insert(user, getTinfoList(), def);
    }


    // Note: callers must invoke indexer (can't invoke here since we may already be in a transaction)
    ListDef update(User user, ListDef def) throws SQLException
    {
        Container c = ContainerManager.getForId(def.getContainerId());
        if (null == c)
            throw Table.OptimisticConflictException.create(Table.ERROR_DELETED);

        DbScope scope = getSchema().getScope();
        ListDef ret;

        try
        {
            scope.ensureTransaction();
            ListDef old = getList(c, def.getRowId());
            ret = Table.update(user, getTinfoList(), def, def.getRowId());
            if (!old.getName().equals(ret.getName()))
                QueryService.get().updateCustomViewsAfterRename(c, ListSchema.NAME, old.getName(), def.getName());

            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();
        }

        return ret;
    }


    public static final SearchService.SearchCategory listCategory = new SearchService.SearchCategory("list", "List");

    // Index all lists in this container
    public void enumerateDocuments(@Nullable SearchService.IndexTask t, final @NotNull Container c, @Nullable Date since)   // TODO: Use since?
    {
        final SearchService.IndexTask task = null == t ? ServiceRegistry.get(SearchService.class).defaultTask() : t;

        Runnable r = new Runnable()
        {
            public void run()
            {
                Map<String, ListDefinition> lists = ListService.get().getLists(c);

                for (ListDefinition list : lists.values())
                {
                    indexList(task, list);
                }
            }
        };

        task.addRunnable(r, SearchService.PRIORITY.group);
    }

    public void indexList(final ListDefinition def)
    {
        indexList(((ListDefinitionImpl)def)._def);
    }

    // Index a single list
    public void indexList(final ListDef def)
    {
        final SearchService.IndexTask task = ServiceRegistry.get(SearchService.class).defaultTask();

        Runnable r = new Runnable()
        {
            public void run()
            {
                ListDefinition list = ListDefinitionImpl.of(def);
                indexList(task, list);
            }
        };

        task.addRunnable(r, SearchService.PRIORITY.item);
    }


    private void indexList(@NotNull SearchService.IndexTask task, ListDefinition list)
    {
        Domain domain = list.getDomain();

        // Delete from index if list has just been deleted
        if (null == domain)
        {
            // TODO: Shouldn't be necessary... triggers should delete on delete/change
            deleteIndexedList(list);
            return;
        }

        indexEntireList(task, list);
        indexModifiedItems(task, list);
    }


    // Index (or delete) a single list item after item save or delete
    public void indexItem(final ListDefinition list, final ListItem item)
    {
        final SearchService.IndexTask task = ServiceRegistry.get(SearchService.class).defaultTask();

        if (list.getEachItemIndex())
        {
            Runnable r = new Runnable()
            {
                public void run()
                {
                    SimpleFilter filter = new SimpleFilter(list.getKeyName(), item.getKey());
                    int count = indexItems(task, list, filter);
                    if (0 == count)
                        LOG.info("I should be deleting!");
                }
            };
            _addIndexTask(r, SearchService.PRIORITY.item);
        }

        if (list.getEntireListIndex() && list.getEntireListIndexSetting().indexItemData())
        {
            Runnable r = new ListIndexRunnable(task, list);
            _addIndexTask(r, SearchService.PRIORITY.item);
        }
    }


    private void _addIndexTask(final Runnable r, final SearchService.PRIORITY p)
    {
        final SearchService.IndexTask task = ServiceRegistry.get(SearchService.class).defaultTask();

        if (getSchema().getScope().isTransactionActive())
        {
            getSchema().getScope().addCommitTask(new Runnable(){
                @Override
                public void run()
                {
                    task.addRunnable(r, p);
                }
            });
        }
        else
        {
            task.addRunnable(r, p);
        }
    }


    // This Runnable implementation defines equals() and hashCode() so the indexer will coalesce multiple re-indexing
    // tasks of the same list within the same transaction (i.e., don't re-index the entire list on every insert during
    // bulk upload).
    private class ListIndexRunnable implements Runnable
    {
        private final @NotNull SearchService.IndexTask _task;
        private final @NotNull ListDefinition _list;

        private ListIndexRunnable(@NotNull SearchService.IndexTask task, final @NotNull ListDefinition list)
        {
            _task = task;
            _list = list;
        }

        private int getListId()
        {
            return _list.getListId();
        }

        @Override
        public void run()
        {
            LOG.debug("Indexing entire list: " + _list.getName() + ", " + _list.getListId());
            indexEntireList(_task, _list);
        }

        @Override
        public String toString()
        {
            return "Indexing runnable for list " + getListId();
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ListIndexRunnable that = (ListIndexRunnable) o;

            return this.getListId() == that.getListId();
        }

        @Override
        public int hashCode()
        {
            return getListId();
        }
    }


    // Delete a single list item from the index after item delete
    public void deleteItem(final ListDefinition list, final ListItem item)
    {
        if (!list.getEntireListIndex() && !list.getEachItemIndex())
            return;

        final SearchService.IndexTask task = ServiceRegistry.get(SearchService.class).defaultTask();

        Runnable r = new Runnable()
        {
            public void run()
            {
                ServiceRegistry.get(SearchService.class).deleteResource(getDocumentId(list, item.getEntityId()));
            }
        };

        task.addRunnable(r, SearchService.PRIORITY.delete);
    }


    private String getDocumentId(ListDefinition list)
    {
        return "list:" + ((ListDefinitionImpl)list).getEntityId();
    }


    // Use each item's EntityId since PKs are mutable. ObjectIds maybe be the better choice (they're shorter) but
    // that would require adding this column to the query definition. Consider: a private TableInfo just for indexing.
    private String getDocumentId(ListDefinition list, @Nullable String entityId)
    {
        return getDocumentId(list) + ":" + (null != entityId ? entityId : "");
    }


    // Index all modified items in this list
    private void indexModifiedItems(@NotNull final SearchService.IndexTask task, final ListDefinition list)
    {
        if (!list.getEachItemIndex())
        {
            deleteIndexedItems(list);
            return;
        }

        // Index all items that have never been indexed OR where either the list definition or list item itself has changed since last indexed
        String test = "LastIndexed IS NULL OR LastIndexed < ? OR (Modified IS NOT NULL AND LastIndexed < Modified)";
        SimpleFilter filter = new SimpleFilter(new SimpleFilter.SQLClause(test, new Object[]{list.getModified()}));

        indexItems(task, list, filter);
    }


    // Reindex items specified by filter
    private int indexItems(@NotNull final SearchService.IndexTask task, final ListDefinition list, SimpleFilter filter)
    {
        TableInfo listTable = list.getTable(User.getSearchUser());
        FieldKeyStringExpression titleTemplate = createEachItemTitleTemplate(list, listTable);
        FieldKeyStringExpression bodyTemplate = createBodyTemplate(list, list.getEachItemBodySetting(), list.getEachItemBodyTemplate(), listTable);

        try
        {
            Results results = null;

            try
            {
                results = Table.selectForDisplay(listTable, Table.ALL_COLUMNS, null, filter, null, Table.ALL_ROWS, Table.NO_OFFSET);
                results.getFieldMap().keySet();
                FieldKey keyKey = new FieldKey(null, list.getKeyName());
                FieldKey entityIdKey = new FieldKey(null, "EntityId");
                int count = 0;

                while (results.next())
                {
                    Map<FieldKey, Object> map = results.getFieldKeyRowMap();
                    final Object pk = map.get(keyKey);
                    String entityId = (String)map.get(entityIdKey);

                    String documentId = getDocumentId(list, entityId);
                    Map<String, Object> props = new HashMap<String, Object>();
                    props.put(SearchService.PROPERTY.categories.toString(), listCategory.toString());
                    props.put(SearchService.PROPERTY.displayTitle.toString(), titleTemplate.eval(map));

                    String body = bodyTemplate.eval(map);

                    ActionURL itemURL = list.urlDetails(pk);
                    itemURL.setExtraPath(list.getContainer().getId()); // Use ID to guard against folder moves/renames

                    SimpleDocumentResource r = new SimpleDocumentResource(
                            new Path(documentId),
                            documentId,
                            list.getContainer().getId(),
                            "text/plain",
                            null == body ? new byte[0] : body.getBytes(),
                            itemURL,
                            props) {
                        @Override
                        public void setLastIndexed(long ms, long modified)
                        {
                            ListManager.get().setLastIndexed(list, pk, ms);
                        }
                    };

                    // Add navtrail that includes link to full list grid
                    ActionURL gridURL = list.urlShowData();
                    gridURL.setExtraPath(list.getContainer().getId()); // Use ID to guard against folder moves/renames
                    NavTree t = new NavTree("list", gridURL);
                    String nav = NavTree.toJS(Collections.singleton(t), null, false).toString();
                    r.getMutableProperties().put(SearchService.PROPERTY.navtrail.toString(), nav);

                    task.addResource(r, SearchService.PRIORITY.item);
                    count++;
                }

                return count;
            }
            finally
            {
                if (null != results)
                    results.close();
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    private void indexEntireList(@NotNull SearchService.IndexTask task, final ListDefinition list)
    {
        if (!list.getEntireListIndex())
        {
            // TODO: Shouldn't be necessary
            deleteIndexedEntireListDoc(list);
            return;
        }

        ListDefinition.IndexSetting setting = list.getEntireListIndexSetting();
        String documentId = getDocumentId(list);

        // First check if meta data needs to be indexed: if the setting is enabled and the definition has changed
        boolean needToIndex = (setting.indexMetaData() && hasDefinitionChangedSinceLastIndex(list));

        // If that didn't hold true then check for entire list data indexing: if the definition has changed or any item has been modified
        if (!needToIndex && setting.indexItemData())
            needToIndex = hasDefinitionChangedSinceLastIndex(list) || hasModifiedItems(list);

        if (!needToIndex)
            return;

        StringBuilder body = new StringBuilder();
        Map<String, Object> props = new HashMap<String, Object>();

        // Use standard title if that setting is chosen or template is null/whitespace
        String title = list.getEntireListTitleSetting() == ListDefinition.TitleSetting.Standard || StringUtils.isBlank(list.getEntireListTitleTemplate()) ? "List " + list.getName() : list.getEntireListTitleTemplate();

        props.put(SearchService.PROPERTY.categories.toString(), listCategory.toString());
        props.put(SearchService.PROPERTY.displayTitle.toString(), title);

        if (!StringUtils.isEmpty(list.getDescription()))
            body.append(list.getDescription()).append("\n");

        String sep = "";

        if (setting.indexMetaData())
        {
            String comma = "";
            for (DomainProperty property : list.getDomain().getProperties())
            {
                String n = StringUtils.trimToEmpty(property.getName());
                String l = StringUtils.trimToEmpty(property.getLabel());
                if (n.equals(l))
                    l = "";
                body.append(comma).append(sep).append(StringUtilsLabKey.joinNonBlank(" ", n, l));
                comma = ",";
                sep = "\n";
            }
        }

        if (setting.indexItemData())
        {
            TableInfo ti = list.getTable(User.getSearchUser());
            FieldKeyStringExpression template = createBodyTemplate(list, list.getEntireListBodySetting(), list.getEntireListBodyTemplate(), ti);
            StringBuilder data = new StringBuilder();

            try
            {
                Results results = null;

                try
                {
                    results = Table.selectForDisplay(ti, Table.ALL_COLUMNS, null, null, null, Table.ALL_ROWS, Table.NO_OFFSET);

                    while (results.next())
                    {
                        Map<FieldKey, Object> map = results.getFieldKeyRowMap();
                        data.append(template.eval(map)).append("\n");
                    }
                }
                finally
                {
                    if (null != results)
                        results.close();
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }

            body.append(sep);
            body.append(data);
        }

        ActionURL url = list.urlShowData();
        url.setExtraPath(list.getContainer().getId()); // Use ID to guard against folder moves/renames

        SimpleDocumentResource r = new SimpleDocumentResource(
                new Path(documentId),
                documentId,
                list.getContainer().getId(),
                "text/plain",
                body.toString().getBytes(),
                url,
                props) {
            @Override
            public void setLastIndexed(long ms, long modified)
            {
                ListManager.get().setLastIndexed(list, ms);
            }
        };

        task.addResource(r, SearchService.PRIORITY.item);
    }


    public void deleteIndexedList(ListDefinition list)
    {
        deleteIndexedEntireListDoc(list);
        deleteIndexedItems(list);
    }


    // Un-index the entire list doc alone, but leave the list items alone
    private void deleteIndexedEntireListDoc(ListDefinition list)
    {
        ServiceRegistry.get(SearchService.class).deleteResource(getDocumentId(list));
    }


    // Un-index all list items, but leave the entire list doc alone
    private void deleteIndexedItems(ListDefinition list)
    {
        ServiceRegistry.get(SearchService.class).deleteResourcesForPrefix(getDocumentId(list, null));
    }


    private FieldKeyStringExpression createEachItemTitleTemplate(ListDefinition list, TableInfo listTable)
    {
        String template;

        if (list.getEachItemTitleSetting() == ListDefinition.TitleSetting.Standard || StringUtils.isBlank(list.getEachItemTitleTemplate()))
            template = "List " + list.getName() + " - ${" + listTable.getTitleColumn() + "}";
        else
            template = list.getEachItemTitleTemplate();

        // Don't URL encode and use lenient substitution (replace nulls with blank)
        return FieldKeyStringExpression.create(template, false, NullValueBehavior.ReplaceNullWithBlank);
    }


    private FieldKeyStringExpression createBodyTemplate(ListDefinition list, ListDefinition.BodySetting setting, @Nullable String customTemplate, TableInfo listTable)
    {
        String template;

        if (setting == ListDefinition.BodySetting.Custom && !StringUtils.isBlank(customTemplate))
        {
            template = customTemplate;
        }
        else
        {
            StringBuilder sb = new StringBuilder();
            String sep = "";

            for (ColumnInfo column : listTable.getColumns())
            {
                if (setting.accept(column))
                {
                    sb.append(sep);
                    sb.append("${");
                    sb.append(column.getFieldKey());
                    sb.append("}");
                    sep = " ";
                }
            }

            template = sb.toString();
        }

        // Don't URL encode and use lenient substitution (replace nulls with blank)
        return FieldKeyStringExpression.create(template, false, NullValueBehavior.ReplaceNullWithBlank);
    }


    private boolean hasDefinitionChangedSinceLastIndex(ListDefinition list)
    {
        return list.getLastIndexed() == null || list.getModified().compareTo(list.getLastIndexed()) > 0;
    }


    // Checks for existence of list items that have been modified since the entire list was last indexed
    private boolean hasModifiedItems(ListDefinition list)
    {
        // Using EXISTS query should be reasonably efficient.  This form (using case) seems to work on PostgreSQL and SQL Server
        SQLFragment sql = new SQLFragment("SELECT CASE WHEN EXISTS (SELECT 1 FROM " +
                ((ListDefinitionImpl) list).getIndexTable().getSelectName() +
                " WHERE ListId = ? AND Modified > ?) THEN 1 ELSE 0 END", list.getListId(), list.getLastIndexed());

        return new SqlSelector(getSchema(), sql).getObject(Boolean.class);
    }


    public void setLastIndexed(ListDefinition list, long ms)
    {
        new SqlExecutor(getSchema()).execute(new SQLFragment("UPDATE " + getTinfoList().getSelectName() +
                " SET LastIndexed = ? WHERE RowId = ?", new Timestamp(ms), list.getListId()));
    }


    public void setLastIndexed(ListDefinition list, Object pk, long ms)
    {
        TableInfo ti = ((ListDefinitionImpl) list).getIndexTable();
        String keySelectName = ti.getColumn("Key").getSelectName();   // Reserved word on sql server
        new SqlExecutor(getSchema()).execute(new SQLFragment("UPDATE " + ti.getSelectName() + " SET LastIndexed = ? WHERE ListId = ? AND " +
                keySelectName + " = ?", new Timestamp(ms), list.getListId(), pk));
    }


    public void indexDeleted() throws SQLException
    {
        for (TableInfo ti : new TableInfo[]{
                getTinfoList(),
                ListTable.getIndexTable(ListDefinition.KeyType.Integer),
                ListTable.getIndexTable(ListDefinition.KeyType.Varchar)
            })
        {
            new SqlExecutor(getSchema()).execute(new SQLFragment("UPDATE " + ti.getSelectName() + " SET LastIndexed = NULL"));
        }
    }
}
