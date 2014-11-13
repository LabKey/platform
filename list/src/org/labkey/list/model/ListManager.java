/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.DomainURIFactory;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleUpgrader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpressionFactory.AbstractStringExpression.NullValueBehavior;
import org.labkey.api.util.StringExpressionFactory.FieldKeyStringExpression;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.webdav.SimpleDocumentResource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ListManager implements SearchService.DocumentProvider
{
    private static final Logger LOG = Logger.getLogger(ListManager.class);
    private static final String LIST_SEQUENCE_NAME = "org.labkey.list.Lists";
    private static final ListManager INSTANCE = new ListManager();

    public static final String LIST_AUDIT_EVENT = "ListAuditEvent";

    public static ListManager get()
    {
        return INSTANCE;
    }

    public DbSchema getListMetadataSchema()
    {
        return ExperimentService.get().getSchema();
    }

    public TableInfo getListMetadataTable()
    {
        return getListMetadataSchema().getTable("list");
    }

    public Collection<ListDef> getLists(Container container)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), container.getEntityId());
        Collection<ListDef> ownLists = new TableSelector(getListMetadataTable(), filter, null).getCollection(ListDef.class);

        return getAllScopedLists(ownLists, container);
    }

    private Collection<ListDef> getAllScopedLists(Collection<ListDef> ownLists, Container container)
    {
        // Workbooks can see parent lists. In the event of a name collision, the child workbook list wins.
        // In future, may add additional ways to cross-folder scope lists
        if (container.getType() == Container.TYPE.workbook)
        {
            SimpleFilter parentFilter = new SimpleFilter(FieldKey.fromParts("Container"), container.getParent().getEntityId());
            Collection<ListDef> parentLists = new TableSelector(getListMetadataTable(), parentFilter, null).getCollection(ListDef.class);
            if (ownLists.size() > 0 && parentLists.size() > 0)
            {
                Map<String, ListDef> listDefMap = new CaseInsensitiveHashMap<>();
                for (ListDef def : parentLists)
                {
                    listDefMap.put(def.getName(), def);
                }
                for (ListDef def : ownLists)
                {
                    listDefMap.put(def.getName(), def);
                }
                return listDefMap.values();
            }
            else if (parentLists.size() > 0)
                return parentLists;
        }
        return ownLists;
    }

    /**
     * Utility method now that ListTable is ContainerFilter aware; TableInfo.getSelectName() returns now returns null
     * @param ti
     * @return
     */
    String getListTableName(TableInfo ti)
    {
        if (ti instanceof ListTable)
            return ((ListTable)ti).getRealTable().getSelectName();
        else return ti.getSelectName();  // if db is being upgraded from <= 13.1, lists are still SchemaTableInfo instances
    }

    public ListDef getList(Container container, int listId)
    {
        SimpleFilter filter = new PkFilter(getListMetadataTable(), new Object[]{container, listId});
        ListDef list = new TableSelector(getListMetadataTable(), filter, null).getObject(ListDef.class);

        // Workbooks can see their parent's lists, so check that container if we didn't find the list the first time
        if (list == null && container.getType() == Container.TYPE.workbook)
        {
            filter = new PkFilter(getListMetadataTable(), new Object[]{container.getParent(), listId});
            list = new TableSelector(getListMetadataTable(), filter, null).getObject(ListDef.class);
        }
        return list;
    }

    // Note: callers must invoke indexer (can't invoke here since we may be in a transaction)
    public ListDef insert(User user, final ListDef def, Collection<Integer> preferredListIds) throws SQLException
    {
        Container c = def.lookupContainer();
        if (null == c)
            throw Table.OptimisticConflictException.create(Table.ERROR_DELETED);

        TableInfo tinfo = getListMetadataTable();
        DbSequence sequence = DbSequenceManager.get(c, LIST_SEQUENCE_NAME);
        ListDef ret = def.clone();

        for (Integer preferredListId : preferredListIds)
        {
            SimpleFilter filter = new SimpleFilter(tinfo.getColumn("Container").getFieldKey(), c).addCondition(tinfo.getColumn("ListId"), preferredListId);

            // Need to check proactively... unfortunately, calling insert and handling the constraint violation will cancel the current transaction
            if (!new TableSelector(getListMetadataTable().getColumn("ListId"), filter, null).exists())
            {
                def.setListId(preferredListId);
                ret = Table.insert(user, tinfo, def);
                sequence.ensureMinimum(preferredListId);  // Ensure sequence is at or above the preferred ID we just used
                return def;
            }
        }

        // If none of the preferred IDs is available then use the next sequence value
        ret.setListId(sequence.next());

        return Table.insert(user, tinfo, ret);
    }


    // Note: callers must invoke indexer (can't invoke here since we may already be in a transaction)
    ListDef update(User user, final ListDef def)
    {
        Container c = def.lookupContainer();
        if (null == c)
            throw Table.OptimisticConflictException.create(Table.ERROR_DELETED);

        DbScope scope = getListMetadataSchema().getScope();
        ListDef ret;

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            ListDef old = getList(c, def.getListId());
            ret = Table.update(user, getListMetadataTable(), def, new Object[]{c, def.getListId()});
            if (!old.getName().equals(ret.getName()))
            {
                QueryChangeListener.QueryPropertyChange change = new QueryChangeListener.QueryPropertyChange<>(
                        QueryService.get().getUserSchema(user, c, ListQuerySchema.NAME).getQueryDefForTable(ret.getName()),
                        QueryChangeListener.QueryProperty.Name,
                        old.getName(),
                        ret.getName()
                );

                QueryService.get().fireQueryChanged(user, c, null, new SchemaKey(null, ListQuerySchema.NAME),
                        QueryChangeListener.QueryProperty.Name, Collections.singleton(change));
            }

            transaction.commit();
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
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(list.getKeyName()), item.getKey());
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

        if (getListMetadataSchema().getScope().isTransactionActive())
        {
            getListMetadataSchema().getScope().addCommitTask(new Runnable(){
                @Override
                public void run()
                {
                    task.addRunnable(r, p);
                }
            }, DbScope.CommitTaskOption.POSTCOMMIT);
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

        private Container getContainer()
        {
            return _list.getContainer();
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
            return "Indexing runnable for list " + getContainer().getPath() + ": " + getListId();
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ListIndexRunnable that = (ListIndexRunnable) o;

            if (this.getListId() != that.getListId()) return false;
            if (!this.getContainer().equals(that.getContainer())) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = getContainer().hashCode();
            result = 31 * result + getListId();
            return result;
        }
    }

    // Delete a single list item from the index after item delete
    public void deleteItemIndex(final ListDefinition list, @NotNull final String entityId)
    {
        if (!list.getEntireListIndex() && !list.getEachItemIndex())
            return;

        final SearchService.IndexTask task = ServiceRegistry.get(SearchService.class).defaultTask();

        Runnable r = new Runnable()
        {
            public void run()
            {
                ServiceRegistry.get(SearchService.class).deleteResource(getDocumentId(list, entityId));
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

        if (null == listTable)
            return 0;

        FieldKeyStringExpression titleTemplate = createEachItemTitleTemplate(list, listTable);
        FieldKeyStringExpression bodyTemplate = createBodyTemplate(list, "\"each item as a separate document\" custom indexing template", list.getEachItemBodySetting(), list.getEachItemBodyTemplate(), listTable);

        FieldKey keyKey = new FieldKey(null, list.getKeyName());
        FieldKey entityIdKey = new FieldKey(null, "EntityId");
        int count = 0;

        try (Results results = new TableSelector(listTable, filter, null).setForDisplay(true).getResults())
        {
            while (results.next())
            {
                Map<FieldKey, Object> map = results.getFieldKeyRowMap();
                final Object pk = map.get(keyKey);
                String entityId = (String)map.get(entityIdKey);

                String documentId = getDocumentId(list, entityId);
                Map<String, Object> props = new HashMap<>();
                props.put(SearchService.PROPERTY.categories.toString(), listCategory.toString());
                props.put(SearchService.PROPERTY.title.toString(), titleTemplate.eval(map));

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
                        ListManager.get().setItemLastIndexed(list, pk, ms);
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
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return count;
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
        Map<String, Object> props = new HashMap<>();

        // Use standard title if that setting is chosen or template is null/whitespace
        String title = list.getEntireListTitleSetting() == ListDefinition.TitleSetting.Standard || StringUtils.isBlank(list.getEntireListTitleTemplate()) ? "List " + list.getName() : list.getEntireListTitleTemplate();

        props.put(SearchService.PROPERTY.categories.toString(), listCategory.toString());
        props.put(SearchService.PROPERTY.title.toString(), title);

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

            if (ti != null)
            {
                FieldKeyStringExpression template = createBodyTemplate(list, "\"entire list as a single document\" custom indexing template", list.getEntireListBodySetting(), list.getEntireListBodyTemplate(), ti);
                StringBuilder data = new StringBuilder();

                // All columns, all rows, no filters, no sorts
                try (Results results = new TableSelector(ti).setForDisplay(true).getResults())
                {
                    while (results.next())
                    {
                        Map<FieldKey, Object> map = results.getFieldKeyRowMap();
                        data.append(template.eval(map)).append("\n");
                    }
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }

                body.append(sep);
                body.append(data);
            }
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
        FieldKeyStringExpression template;
        StringBuilder error = new StringBuilder();

        if (list.getEachItemTitleSetting() != ListDefinition.TitleSetting.Standard && !StringUtils.isBlank(list.getEachItemTitleTemplate()))
        {
            template = createValidStringExpression(list.getEachItemTitleTemplate(), error);

            if (null != template)
                return template;
            else
                LOG.warn(getTemplateErrorMessage(list, "\"each item as a separate document\" title template", error));
        }

        // If you're devious enough to put ${ in your list name then we'll just strip it out, #21794
        String name = list.getName().replaceAll("\\$\\{", "_{");
        template = createValidStringExpression("List " + name + " - ${" + PageFlowUtil.encode(listTable.getTitleColumn()) + "}", error);

        if (null == template)
            throw new IllegalStateException(getTemplateErrorMessage(list, "auto-generated title template", error));

        return template;
    }


    private FieldKeyStringExpression createBodyTemplate(ListDefinition list, String templateType, ListDefinition.BodySetting setting, @Nullable String customTemplate, TableInfo listTable)
    {
        FieldKeyStringExpression template;
        StringBuilder error = new StringBuilder();

        if (setting == ListDefinition.BodySetting.Custom && !StringUtils.isBlank(customTemplate))
        {
            template = createValidStringExpression(customTemplate, error);

            if (null != template)
                return template;
            else
                LOG.warn(getTemplateErrorMessage(list, templateType, error));
        }

        StringBuilder sb = new StringBuilder();
        String sep = "";

        for (ColumnInfo column : listTable.getColumns())
        {
            if (setting.accept(column))
            {
                sb.append(sep);
                sb.append("${");
                sb.append(column.getFieldKey().encode());  // Must encode, #21794
                sb.append("}");
                sep = " ";
            }
        }

        template = createValidStringExpression(sb.toString(), error);

        if (null == template)
            throw new IllegalStateException(getTemplateErrorMessage(list, "auto-generated indexing template", error));

        return template;
    }


    // Perform some simple validation of custom indexing template, #21726.
    private @Nullable FieldKeyStringExpression createValidStringExpression(String template, StringBuilder error)
    {
        // Don't URL encode and use lenient substitution (replace nulls with blank)
        FieldKeyStringExpression se = FieldKeyStringExpression.create(template, false, NullValueBehavior.ReplaceNullWithBlank);

        try
        {
            // TODO: Is there a more official way to validate a StringExpression?
            se.eval(Collections.emptyMap());
        }
        catch (IllegalArgumentException e)
        {
            error.append(e.getMessage());
            se = null;
        }

        return se;
    }


    private String getTemplateErrorMessage(ListDefinition list, String templateType, CharSequence message)
    {
        return "Invalid " + templateType + " for list \"" + list.getName() + "\" in " + list.getContainer().getPath() + ": " + message;
    }


    private boolean hasDefinitionChangedSinceLastIndex(ListDefinition list)
    {
        return list.getLastIndexed() == null || list.getModified().compareTo(list.getLastIndexed()) > 0;
    }


    // Checks for existence of list items that have been modified since the entire list was last indexed
    private boolean hasModifiedItems(ListDefinition list)
    {
        TableInfo table = list.getTable(User.getSearchUser());

        if (null != table && null != getListTableName(table))
        {
            // Using EXISTS query should be reasonably efficient.  This form (using case) seems to work on PostgreSQL and SQL Server
            SQLFragment sql = new SQLFragment("SELECT CASE WHEN EXISTS (SELECT 1 FROM ");
            sql.append(getListTableName(table));
            sql.append(" WHERE Modified > (SELECT LastIndexed FROM ").append(getListMetadataTable().getSelectName());
            sql.append(" WHERE ListId = ? AND Container = ?");
            sql.add(list.getListId());
            sql.add(list.getContainer().getEntityId());
            sql.append(")) THEN 1 ELSE 0 END");

            return new SqlSelector(getListMetadataSchema(), sql).getObject(Boolean.class);
        }

        return false;
    }

    public void setLastIndexed(ListDefinition list, long ms)
    {
        new SqlExecutor(getListMetadataSchema()).execute("UPDATE " + getListMetadataTable().getSelectName() +
                " SET LastIndexed = ? WHERE ListId = ?", new Timestamp(ms), list.getListId());
    }


    public void setItemLastIndexed(ListDefinition list, Object pk, long ms)
    {
        TableInfo ti = list.getTable(User.getSearchUser());

        // The "search user" might not have access
        if (null != ti)
        {
            ColumnInfo keyColumn = ti.getColumn(list.getKeyName());
            if (null != keyColumn)
            {
                String keySelectName = keyColumn.getSelectName();
                new SqlExecutor(ti.getSchema()).execute("UPDATE " + getListTableName(ti) + " SET LastIndexed = ? WHERE " +
                        keySelectName + " = ?", new Timestamp(ms), pk);
            }
        }
    }


    public void indexDeleted() throws SQLException
    {
        SqlExecutor executor = new SqlExecutor(getListMetadataSchema());

        for (TableInfo ti : new TableInfo[]{
                getListMetadataTable()
        })
        {
            executor.execute("UPDATE " + ti.getSelectName() + " SET LastIndexed = NULL");
        }
    }

    public void addAuditEvent(ListDefinitionImpl list, User user, String comment)
    {
        if (null != user)
        {
            AuditLogEvent event = new AuditLogEvent();

            event.setCreatedBy(user);
            event.setComment(comment);

            Container c = list.getContainer();
            event.setContainerId(c.getId());
            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());
            event.setKey1(list.getDomain().getTypeURI());

            event.setEventType(LIST_AUDIT_EVENT);
            event.setIntKey1(list.getListId());
            event.setKey3(list.getName());

            AuditLogService.get().addEvent(event);
        }
    }

    /**
     * Modeled after ListItemImpl.addAuditEvent
     */
    public void addAuditEvent(ListDefinitionImpl list, User user, Container c, String comment, String entityId, @Nullable String oldRecord, @Nullable String newRecord)
    {
        AuditLogEvent event = new AuditLogEvent();

        event.setCreatedBy(user);
        event.setComment(comment);

        event.setContainerId(c.getId());
        Container project = c.getProject();
        if (null != project)
            event.setProjectId(project.getId());

        event.setKey1(list.getDomain().getTypeURI());
        event.setEventType(ListManager.LIST_AUDIT_EVENT);
        event.setIntKey1(list.getListId());
        event.setKey2(entityId);
        event.setKey3(list.getName());

        final Map<String, Object> dataMap = new HashMap<>();
        if (oldRecord != null) dataMap.put(ListAuditViewFactory.OLD_RECORD_PROP_NAME, oldRecord);
        if (newRecord != null) dataMap.put(ListAuditViewFactory.NEW_RECORD_PROP_NAME, newRecord);

        if (!dataMap.isEmpty())
            AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(LIST_AUDIT_EVENT));
        else
            AuditLogService.get().addEvent(event);
    }

    public String formatAuditItem(ListDefinitionImpl list, User user, Map<String, Object> props)
    {
        String itemRecord = "";
        TableInfo ti = list.getTable(user);

        if (null != ti)
        {
            Map<String, String> recordChangedMap = new CaseInsensitiveHashMap<>();
            Set<String> reserved = list.getDomain().getDomainKind().getReservedPropertyNames(list.getDomain());

            // Match props to columns
            for (Map.Entry<String, Object> entry : props.entrySet())
            {
                String baseKey = entry.getKey();

                boolean isReserved = false;
                for (String res : reserved)
                {
                    if (res.equalsIgnoreCase(baseKey))
                    {
                        isReserved = true;
                        break;
                    }
                }

                if (isReserved)
                    continue;

                ColumnInfo col = ti.getColumn(FieldKey.fromParts(baseKey));
                String value = ObjectUtils.toString(entry.getValue());
                String key = null;

                if (null != col)
                {
                    // Found the column
                    key = col.getName(); // best good
                }
                else
                {
                    // See if there is a match in the domain properties
                    for (DomainProperty dp : list.getDomain().getProperties())
                    {
                        if (dp.getName().equalsIgnoreCase(baseKey))
                        {
                            key = dp.getName(); // middle good
                        }
                    }

                    // Try by name
                    DomainProperty dp = list.getDomain().getPropertyByName(baseKey);
                    if (null != dp)
                        key = dp.getName();
                }

                if (null != key && null != value)
                    recordChangedMap.put(key, value);
            }

            if (!recordChangedMap.isEmpty())
                itemRecord = ListAuditViewFactory.encodeForDataMap(recordChangedMap, true);
        }

        return itemRecord;
    }

    public void upgradeListDefinitions(User user)
    {
        Container root = ContainerManager.getRoot();

        // Recurse through the children
        for (Container child : ContainerManager.getAllChildren(root))
        {
            migrateToHardTable(user, child);
        }
    }

    public void ensureListDomains()
    {
        Container root = ContainerManager.getRoot();

        // Recurse through the children
        for (Container child : ContainerManager.getAllChildren(root))
        {
            ensureListDomain(child);
        }

        OntologyManager.clearCaches();
    }

    private void ensureListDomain(Container container)
    {
        Map<String, ListDefinition> definitionMap = ListService.get().getLists(container);

        if (definitionMap.size() > 0)
            ModuleUpgrader.getLogger().info("Ensuring domain properties for lists in [" + container.getPath() + "]");

        for (ListDefinition listDef : definitionMap.values())
        {
            ModuleUpgrader.getLogger().info("Ensuring properties for list [" + listDef.getName() + "]");

            Domain listDomain = listDef.getDomain();

            // Don't want the domain to attempt to add columns based on property descriptor updates
            listDomain.setEnforceStorageProperties(false);

            // Ensure the primary key
            DomainProperty pk = listDomain.getPropertyByName(listDef.getKeyName());
            String pkProperyURI = ListDomainKind.createPropertyURI(listDef.getName(), listDef.getKeyName(), container, listDef.getKeyType()).toString();

            if (null == pk)
            {
                DomainProperty p = listDomain.addProperty();

                p.setName(listDef.getKeyName());
                p.setType(PropertyService.get().getType(listDomain.getContainer(), listDef.getKeyType() == ListDefinition.KeyType.Varchar ? PropertyType.STRING.getXmlName() : PropertyType.INTEGER.getXmlName()));
                p.setPropertyURI(pkProperyURI);
                p.setRequired(true);

                listDomain.setPropertyIndex(p, 0);

                PropertyDescriptor pd = Table.insert(null, OntologyManager.getTinfoPropertyDescriptor(), p.getPropertyDescriptor());
                listDef.clearDomain();
                listDomain = listDef.getDomain();

                DomainDescriptor dd = OntologyManager.getDomainDescriptor(listDomain.getTypeId());
                OntologyManager.ensurePropertyDomain(pd, dd, 0);
            }
            else if (!pk.getPropertyURI().equals(pkProperyURI))
            {
                // ensure the PropertyURI is correctly formatted
                pk.setPropertyURI(pkProperyURI);

                Table.update(null, OntologyManager.getTinfoPropertyDescriptor(), pk.getPropertyDescriptor(), pk.getPropertyId());
                listDef.clearDomain();
                listDomain = listDef.getDomain();
            }

            for (DomainProperty dp : listDomain.getProperties())
            {
                if (dp.getPropertyURI().contains(":List.Folder-"))
                {
                    String mergeURI = dp.getPropertyURI();
                    mergeURI = mergeURI.replace(":List.Folder-", ":" + listDomain.getDomainKind().getKindName() + ".Folder-");
                    dp.setPropertyURI(mergeURI);

                    Table.update(null, OntologyManager.getTinfoPropertyDescriptor(), dp.getPropertyDescriptor(), dp.getPropertyId());
                    listDef.clearDomain();
                    listDomain = listDef.getDomain();
                }
            }

            listDef.clearDomain();
        }
    }


    public boolean importListSchema(ListDefinition unsavedList, String typeColumn, List<Map<String, Object>> importMaps, User user, List<String> errors) throws Exception
    {
        if (!errors.isEmpty())
            return false;

        final Container container = unsavedList.getContainer();
        final String typeURI = unsavedList.getDomain().getTypeURI();

        DomainURIFactory factory = new DomainURIFactory() {
            public Pair<String,Container> getDomainURI(String name)
            {
                return new Pair<>(typeURI,container);
            }
        };

        OntologyManager.ListImportPropertyDescriptors pds = OntologyManager.createPropertyDescriptors(factory, typeColumn, importMaps, errors, container, true);

        if (!errors.isEmpty())
            return false;

        for (OntologyManager.ImportPropertyDescriptor ipd : pds.properties)
        {
            if (null == ipd.domainName || null == ipd.domainURI)
                errors.add("List not specified for property: " + ipd.pd.getName());
        }

        if (!errors.isEmpty())
            return false;

        for (OntologyManager.ImportPropertyDescriptor ipd : pds.properties)
        {
            unsavedList.getDomain().addPropertyOfPropertyDescriptor(ipd.pd);
        }

        for (Map.Entry<String, List<ConditionalFormat>> entry : pds.formats.entrySet())
        {
            PropertyService.get().saveConditionalFormats(user, OntologyManager.getPropertyDescriptor(entry.getKey(), container), entry.getValue());
        }

        unsavedList.save(user);

        return true;
    }

    private void migrateToHardTable(User user, Container container)
    {
        Map<String, ListDefinition> definitionMap = ListService.get().getLists(container);
        ListQuerySchema schema = new ListQuerySchema(user, container);
        boolean isAutoIncrement;

        if (definitionMap.size() > 0)
            ModuleUpgrader.getLogger().info("Migrating list data for [" + container.getPath() + "]");

        for (ListDefinition listDef : definitionMap.values())
        {
            ModuleUpgrader.getLogger().info("Starting migration of list [" + listDef.getName() + "] in [" + container.getPath() + "]");

            if (isMigrated(listDef, container))
            {
                ModuleUpgrader.getLogger().info("List [" + listDef.getName() + "] has previously been migrated.");
                continue;
            }

            isAutoIncrement = listDef.getKeyType() == ListDefinition.KeyType.AutoIncrementInteger;

            // Get the source table info -- original Ontology based list
            @SuppressWarnings({"deprecation"})
            OntologyListTable fromTable = new OntologyListTable(schema, listDef);

            // Wrap the list definition to ensure the correct domain kind
            ListDefinitionImpl hardListDef = new ListDefinitionImpl(container, listDef.getName(), listDef.getKeyType());

            Domain d = migrateDomainURI(listDef, hardListDef);
            // migrateDomainURI() updated the domain in the database, so clear all the caches
            OntologyManager.clearCaches();

            // Using the newly created domain, get a fresh instance of the domain which will contain the migrated properties
            Domain newDomain = PropertyService.get().getDomain(d.getTypeId());

            DomainProperty PKProp = newDomain.getPropertyByName(listDef.getKeyName());

            // add the PK if it does not already exist
            if (null == PKProp)
            {
                DomainProperty p = newDomain.addProperty();

                p.setName(listDef.getKeyName());
                p.setType(PropertyService.get().getType(d.getContainer(), listDef.getKeyType() == ListDefinition.KeyType.Varchar ? PropertyType.STRING.getXmlName() : PropertyType.INTEGER.getXmlName()));
                p.setPropertyURI(ListDomainKind.createPropertyURI(listDef.getName(), listDef.getKeyName(), container, listDef.getKeyType()).toString());
                p.setRequired(true);

                newDomain.setPropertyIndex(p, 0);

                // Add the Primary Key Property Descriptor to the old domain
                PropertyDescriptor pd = Table.insert(null, OntologyManager.getTinfoPropertyDescriptor(), p.getPropertyDescriptor());
                listDef.clearDomain();
                newDomain = listDef.getDomain();

                DomainDescriptor dd = OntologyManager.getDomainDescriptor(newDomain.getTypeId());
                OntologyManager.ensurePropertyDomain(pd, dd, 0);

                OntologyManager.clearCaches();
                listDef.clearDomain();
                newDomain = listDef.getDomain();
            }

            // check for duplicates properties in the domain
            Set<String> names = new CaseInsensitiveHashSet();
            boolean clearDomain = false;

            for (DomainProperty dp : newDomain.getProperties())
            {
                if (null != dp)
                {
                    // Have a duplicate
                    if (names.contains(dp.getName()))
                    {
                        ModuleUpgrader.getLogger().warn("Duplicate column found in List: " + listDef.getName() + ". Column: " + dp.getName());
                        PropertyDescriptor pd = dp.getPropertyDescriptor();
                        pd.setName(pd.getName() + pd.getPropertyId());

                        Table.update(null, OntologyManager.getTinfoPropertyDescriptor(), pd, pd.getPropertyId());
                        clearDomain = true;
                    }
                    else
                        names.add(dp.getName());
                }
            }

            // only need to clear the domain if domain properties have been changed
            if (clearDomain)
            {
                OntologyManager.clearCaches();

                listDef.clearDomain();
                newDomain = listDef.getDomain();
            }

            // create the hard table
            TableInfo toTable = StorageProvisioner.createTableInfo(newDomain);

            // Smoke test row count
            long fromRowCount = new TableSelector(fromTable).getRowCount();

            if (isAutoIncrement)
                migrateBeginAutoIncrement(toTable, toTable.getSqlDialect());

            migrateRows(fromTable, toTable, hardListDef, container);

            if (isAutoIncrement)
                migrateEndAutoIncrement(toTable, toTable.getSqlDialect(), listDef);

            // Smoke test row count
            assert fromRowCount == new TableSelector(toTable).getRowCount();

            // Update Audit Records for the given list
            migrateListAuditRecords(listDef, hardListDef, toTable.getSchema()); // Only needed for scope -- need true TableInfo

            try
            {
                // Delete the list
                OntologyListTable.deleteOntologyList(listDef, user);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
            catch (DomainNotFoundException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    private boolean isMigrated(ListDefinition listDef, Container c)
    {
        Domain d = listDef.getDomain();

        if (null != d)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("storageschemaname"), "list");
            filter.addCondition(FieldKey.fromParts("container"), c.getEntityId());
            filter.addCondition(FieldKey.fromParts("domainid"), d.getTypeId());

            return new TableSelector(OntologyManager.getTinfoDomainDescriptor(), filter, null).exists();
        }
        return false;
    }

    private Domain migrateDomainURI(ListDefinition fromListDef, ListDefinitionImpl toListDef)
    {
        // Add a domain property for the key column as it was wrapped before
        Domain d = fromListDef.getDomain();

        // Update the Domain URI
        TableInfo ddTable = OntologyManager.getTinfoDomainDescriptor();
        ColumnInfo idCol = ddTable.getColumn(FieldKey.fromParts("domainid"));
        ColumnInfo uriCol = ddTable.getColumn(FieldKey.fromParts("domainuri"));

        SQLFragment update = new SQLFragment("UPDATE ").append(ddTable.getSelectName());
        update.append(" SET ").append(uriCol.getSelectName()).append(" = ?");
        update.add(toListDef.getDomain().getTypeURI());
        update.append(" WHERE ").append(idCol.getSelectName()).append(" = ?");
        update.add(d.getTypeId());

        new SqlExecutor(ddTable.getSchema()).execute(update);

        return d;
    }

    private void migrateBeginAutoIncrement(TableInfo table, SqlDialect dialect)
    {
        if (dialect.isSqlServer())
        {
            SQLFragment check = new SQLFragment("SET IDENTITY_INSERT ").append(getListTableName(table)).append(" ON\n");
            ModuleUpgrader.getLogger().info(check.toString());
            new SqlExecutor(table.getSchema()).execute(check);
        }
    }

    private void migrateEndAutoIncrement(TableInfo table, SqlDialect dialect, ListDefinition listDef)
    {
        // If auto-increment based need to reset the sequence counter on the DB
        if (dialect.isPostgreSQL())
        {
            String src = table.getColumn(listDef.getKeyName()).getJdbcDefaultValue();
            if (null != src)
            {
                String sequence = "";

                int start = src.indexOf('\'');
                int end = src.lastIndexOf('\'');

                if (end > start)
                {
                    sequence = src.substring(start + 1, end);
                    if (!sequence.toLowerCase().startsWith("list."))
                        sequence = "list." + sequence;
                }

                SQLFragment keyupdate = new SQLFragment("SELECT setval('").append(sequence).append("'");
                keyupdate.append(", coalesce((SELECT MAX(").append(dialect.quoteIdentifier(listDef.getKeyName().toLowerCase())).append(")+1 FROM ").append(getListTableName(table));
                keyupdate.append("), 1), false);");
                ModuleUpgrader.getLogger().info("Post Key Update");
                ModuleUpgrader.getLogger().info(keyupdate.toString());
                new SqlExecutor(table.getSchema()).execute(keyupdate);
            }
            else
            {
                ModuleUpgrader.getLogger().error("List Column " + listDef.getName() + "." + listDef.getKeyName() + " does not have a correlated sequence.");
            }
        }
        else if (dialect.isSqlServer())
        {
            SQLFragment check = new SQLFragment("SET IDENTITY_INSERT ").append(getListTableName(table)).append(" OFF");
            ModuleUpgrader.getLogger().info(check.toString());
            new SqlExecutor(table.getSchema()).execute(check);
        }
    }

    private void migrateRows(TableInfo fromTable, TableInfo toTable, ListDefinitionImpl toListDef, Container container)
    {
        // Build up a list of all the columns we need from the source table
        List<FieldKey> selectFKs = new ArrayList<>();

        for (ColumnInfo col : fromTable.getColumns())
        {
            // Include all the base columns
            selectFKs.add(col.getFieldKey());
        }
        for (DomainProperty property : fromTable.getDomain().getProperties())
        {
            // Plus the custom properties
            selectFKs.add(FieldKey.fromParts("Properties", property.getName()));
        }

        Map<FieldKey, ColumnInfo> fromColumns = QueryService.get().getColumns(fromTable, selectFKs);

        Map<String, ColumnInfo> colMap = new CaseInsensitiveHashMap<>();
        for (ColumnInfo c : fromColumns.values())
        {
            if (null != c.getPropertyURI())
                colMap.put(c.getPropertyURI(), c);
            colMap.put(c.getName(), c);
        }

        SQLFragment fromSQL = QueryService.get().getSelectSQL(fromTable, fromColumns.values(), null, null, Table.ALL_ROWS, Table.NO_OFFSET, false);

        SQLFragment insertInto = new SQLFragment("INSERT INTO ").append(getListTableName(toTable));
        insertInto.append(" (");
        SQLFragment insertSelect = new SQLFragment("SELECT ");
        String sep = "";

        for (ColumnInfo to : toTable.getColumns())
        {
            ColumnInfo from = colMap.get(to.getPropertyURI());
            if (null == from)
                from = colMap.get(to.getName());
            if (null == from)
            {
                String name = to.getName().toLowerCase();
                if (name.endsWith("_" + MvColumn.MV_INDICATOR_SUFFIX.toLowerCase()))
                {
                    from = colMap.get(name.substring(0,name.length()-(MvColumn.MV_INDICATOR_SUFFIX.length()+1)) + MvColumn.MV_INDICATOR_SUFFIX);
                    if (null == from)
                        continue;
                }

                // Cycle across the fromTable columns and look to make the column name legal and then try to match
                for (ColumnInfo f : fromTable.getColumns())
                {
                    if (f.getName().toLowerCase().startsWith(name))
                    {
                        from = colMap.get(f.getName());
                        if (null != from)
                        {
                            ModuleUpgrader.getLogger().warn("Column name for column: " + f.getName() + " in List: " + toListDef.getName() + " may not be unique.");
                            break;
                        }
                    }
                }

                if (null == from)
                {
                    ModuleUpgrader.getLogger().warn("Could not copy column: " + container.getId() + "-" + container.getPath() + " List: " + toListDef.getName() + "." + to.getName());
                    continue;
                }
            }

            String legalName = to.getSelectName();
            insertInto.append(sep).append(legalName);
            insertSelect.append(sep).append(from.getAlias());
            sep = ", ";
        }
        insertInto.append(")\n");
        insertInto.append(insertSelect);
        insertInto.append("\n FROM (").append(fromSQL).append(") x");

        ModuleUpgrader.getLogger().info(insertInto.toString());
        new SqlExecutor(toTable.getSchema()).execute(insertInto);
    }

    private void migrateListAuditRecords(ListDefinition listDef, ListDefinitionImpl hardListDef, DbSchema schema)
    {
        SQLFragment audit = new SQLFragment("UPDATE ").append("audit.AuditLog");
        audit.append(" SET ").append("Key1").append(" = ?");
        audit.add(hardListDef.getDomain().getTypeURI());
        audit.append(" WHERE ").append("Key1").append(" = ?");
        audit.add(listDef.getDomain().getTypeURI());
        new SqlExecutor(schema).execute(audit);
    }

    /** Used for 13.30 -> 14.1 upgrade */
    public void addContainerColumns(User u)
    {
         /*
            Rename any existing Container domain properties to Container_old
            On the off chance Container_old also already exists, rename Container to Container_old_+ a GUID
            Then add a real container column to all List hard tables
         */
        ListDef[] listDefs = new TableSelector(getListMetadataTable()).getArray(ListDef.class);
        try
        {
            StorageProvisioner.setAllowRenameOfColumnsDuringUpgrade(true);
            for (ListDef def : listDefs)
            {
                ListDefinition list = ListDefinitionImpl.of(def);
                Domain domain = list.getDomain();
                DomainProperty existingContainerProp = domain.getPropertyByName("Container");
                if (existingContainerProp != null)
                {
                    String newName = domain.getPropertyByName("Container_old") == null ? "Container_old" : "Container_old_" + GUID.makeGUID();
                    existingContainerProp.setName(newName);
                    String uri = ListDomainKind.createPropertyURI(list.getName(), newName, domain.getContainer(), list.getKeyType()).toString();
                    existingContainerProp.setPropertyURI(uri);
                    try
                    {
                        domain.save(u);
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        finally
        {
            StorageProvisioner.setAllowRenameOfColumnsDuringUpgrade(false);
        }

        DbScope scope = ListSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction(Connection.TRANSACTION_SERIALIZABLE);
             Connection conn = transaction.getConnection())
        {
            for (ListDef def : listDefs)
            {
                ListDefinition list = ListDefinitionImpl.of(def);
                Domain domain = list.getDomain();
                DomainKind kind = domain.getDomainKind();
                if (kind instanceof ListDomainType)
                {
                    String fullListPath = list.getContainer().getPath() + " " + list.getName();
                    LOG.warn("Found list that was not migrated to a hard table: " + domain.getTypeURI() + " " + fullListPath);
                    LOG.warn("This list has not been functional since the 13.2 upgrade. Please contact LabKey for assistance in deleting it.");
                    continue;
                }

                try
                {
                    if (list.getTable(u).getColumn(FieldKey.fromParts("container")) != null)
                        continue;
                }
                catch (Exception e)
                {
                    LOG.error("Exception retrieving metadata for list: " + list.getName() + ", " + domain.getStorageTableName(), e);
                    continue;
                }

                PropertyStorageSpec newContainerSpec =  new PropertyStorageSpec("container", JdbcType.VARCHAR).setEntityId(true).setNullable(false);
                newContainerSpec.setDefaultValue(list.getContainer().getEntityId());

                TableChange change = new TableChange(kind.getStorageSchemaName(), domain.getStorageTableName(), TableChange.ChangeType.AddColumns);
                change.addColumn(newContainerSpec);
                for (String sql : scope.getSqlDialect().getChangeStatements(change))
                {
                    conn.prepareStatement(sql).execute();
                }
                kind.invalidate(domain);
            }
            transaction.commit();
        }
        catch (SQLException e)
        {
            // We're calling Statement.execute() directly, so we need to log the SQL if an exception occurs
            String sql = ExceptionUtil.getExceptionDecoration(e, ExceptionUtil.ExceptionInfo.DialectSQL);

            if (null != sql)
                LOG.error(sql);

            throw new RuntimeSQLException(e);
        }
    }

    public static class TestCase extends Assert
    {
        private ListDefinitionImpl list;
        private static final String LIST_NAME = "Unit Test list";
        private static final String WORKBOOK1_NAME = "Unit Test Workbook 1";
        private static final String WORKBOOK2_NAME = "Unit Test Workbook 2";
        private static final String FIELD_NAME = "field";
        private static final String PARENT_LIST_ITEM = "parentItem";
        private static final String WORKBOOK1_LIST_ITEM = "workbook1Item";
        private static final String WORKBOOK2_LIST_ITEM = "workbook2Item";
        private static final Integer PARENT_LI_KEY = 1;
        private static final Integer WB1_LI_KEY = 2;
        private static final Integer WB2_LI_KEY = 3;
        private Container c;
        private User u;
        private DomainProperty dp;

        @Before
        public void setUp() throws Exception
        {
            JunitUtil.deleteTestContainer();
            cleanup();
            c = JunitUtil.getTestContainer();
            TestContext context = TestContext.get();
            u = context.getUser();
            list = (ListDefinitionImpl)ListService.get().createList(c, LIST_NAME, ListDefinition.KeyType.AutoIncrementInteger);
            list.setKeyName("Unit test list Key");

            dp = list.getDomain().addProperty();
            dp.setName(FIELD_NAME);
            dp.setType(PropertyService.get().getType(c, PropertyType.STRING.getXmlName()));
            dp.setPropertyURI(ListDomainKind.createPropertyURI(list.getName(), FIELD_NAME, c, list.getKeyType()).toString());
            list.save(u);

            addListItem(c, list, PARENT_LIST_ITEM);
        }

        private void addListItem(Container scopedContainer, ListDefinition scopedList, String value) throws Exception
        {
            List<ListItem> lis = new ArrayList<>();
            ListItem li = scopedList.createListItem();
            li.setProperty(dp, value);
            lis.add(li);
            list.insertListItems(u, scopedContainer, lis);
        }

        @After
        public void tearDown() throws Exception
        {
            cleanup();
        }

        private void cleanup() throws Exception
        {
            //TestContext context = TestContext.get();
            ExperimentService.get().deleteAllExpObjInContainer(c, u);

        }

        @Test
        public void testListServiceInOwnFolder() throws Exception
        {
            Map<String, ListDefinition> lists = ListService.get().getLists(c);
            assertTrue("Test List not found in own container", lists.containsKey(LIST_NAME));
            ListItem li = lists.get(LIST_NAME).getListItem(1, u, c);
            assertTrue("Item not found in own container", li.getProperty(dp).equals(PARENT_LIST_ITEM));
        }

        @Test
        public void testListServiceInWorkbook() throws Exception
        {
            Container workbook1 = setupWorkbook(WORKBOOK1_NAME);
            Container workbook2 = setupWorkbook(WORKBOOK2_NAME);
            Map<String, ListDefinition> lists = ListService.get().getLists(workbook1);
            assertTrue("Test List not found in workbook", lists.containsKey(LIST_NAME));

            checkListItemScoping(workbook1, workbook2);

        }

        private Container setupWorkbook(String name)
        {
            return ContainerManager.createContainer(c, name, name, null, Container.TYPE.workbook, u);
        }

        private void checkListItemScoping(Container wb1, Container wb2) throws Exception
        {
            ListDefinition wbList1 = ListService.get().getLists(wb1).get(LIST_NAME);
            ListDefinition wbList2 = ListService.get().getLists(wb2).get(LIST_NAME);

            assertTrue("Lists available to each workbook are not the same", wbList1.toString().equals(wbList2.toString()));
            addListItem(wb1, wbList1, WORKBOOK1_LIST_ITEM);
            addListItem(wb2, wbList2, WORKBOOK2_LIST_ITEM);

            assertNull("Parent item visible in workbook", wbList1.getListItem(PARENT_LI_KEY, u, wb1));
            assertNull("Sibling workbook item visible in another workbook", wbList1.getListItem(WB2_LI_KEY, u, wb1));
            assertTrue("Parent container can not see child workbook item",wbList1.getListItem(WB1_LI_KEY, u, c).getProperty(dp).equals(WORKBOOK1_LIST_ITEM));
            assertTrue("Workbook can not see its own list item", wbList1.getListItem(WB1_LI_KEY, u, wb1).getProperty(dp).equals(WORKBOOK1_LIST_ITEM));
        }
        /*
        @Test
        public void testUpgrade() throws Exception
        {
            ListDefinition listWithContainerCol = ListService.get().createList(c, "Junit List with container col", ListDefinition.KeyType.AutoIncrementInteger);
            listWithContainerCol.setKeyName("some test key");

            Domain domain = listWithContainerCol.getDomain();
            DomainProperty p = domain.addProperty();

            p.setName("Container");
            p.setType(PropertyService.get().getType(domain.getContainer(), PropertyType.STRING.getXmlName()));
            p.setPropertyURI(ListDomainKind.createPropertyURI(listWithContainerCol.getName(), "Container", domain.getContainer(), listWithContainerCol.getKeyType()).toString());
            p.setScale(4000);

            listWithContainerCol.save(u);

            ListManager.get().addContainerColumns(u);
            return;
        }
        */
    }

}
