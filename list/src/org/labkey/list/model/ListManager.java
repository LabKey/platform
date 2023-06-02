/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.data.Selector.ForEachBlock;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.exp.DomainURIFactory;
import org.labkey.api.exp.ImportTypesHelper;
import org.labkey.api.exp.OntologyManager.ImportPropertyDescriptor;
import org.labkey.api.exp.OntologyManager.ImportPropertyDescriptorsList;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.search.SearchService;
import org.labkey.api.search.SearchService.IndexTask;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory.AbstractStringExpression.NullValueBehavior;
import org.labkey.api.util.StringExpressionFactory.FieldKeyStringExpression;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.list.controllers.ListController;
import org.labkey.list.model.ListImporter.ValidatorImporter;
import org.labkey.list.view.ListItemAttachmentParent;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ListManager implements SearchService.DocumentProvider
{
    private static final Logger LOG = LogHelper.getLogger(ListManager.class, "List indexing events");
    private static final String LIST_SEQUENCE_NAME = "org.labkey.list.Lists";
    private static final ListManager INSTANCE = new ListManager();

    public static final String LIST_AUDIT_EVENT = "ListAuditEvent";
    public static final String LISTID_FIELD_NAME = "listId";


    private final Cache<String, List<ListDef>> _listDefCache = new BlockingCache<>(new DatabaseCache<>(CoreSchema.getInstance().getScope(), CacheManager.UNLIMITED, CacheManager.DAY, "List definitions"), new ListDefCacheLoader()) ;

    private class ListDefCacheLoader implements CacheLoader<String,List<ListDef>>
    {
        @Override
        public List<ListDef> load(@NotNull String entityId, @Nullable Object argument)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), entityId);
            ArrayList<ListDef> ownLists = new TableSelector(getListMetadataTable(), filter, null).getArrayList(ListDef.class);
            return Collections.unmodifiableList(ownLists);
        }
    }

    public static ListManager get()
    {
        return INSTANCE;
    }

    DbSchema getListMetadataSchema()
    {
        return ExperimentService.get().getSchema();
    }

    TableInfo getListMetadataTable()
    {
        return getListMetadataSchema().getTable("list");
    }

    public Collection<ListDef> getPicklists(Container container)
    {
        return getLists(container, true).stream().filter(ListDef::isPicklist).collect(Collectors.toList());
    }

    public Collection<ListDef> getPicklists(Container container, boolean includeProjectAndShared)
    {
        return getLists(container, includeProjectAndShared).stream().filter(ListDef::isPicklist).collect(Collectors.toList());
    }

    public Collection<ListDef> getLists(Container container)
    {
        return getLists(container, false);
    }

    public Collection<ListDef> getLists(Container container, boolean includeProjectAndShared)
    {
        return getLists(container, null, false, true, includeProjectAndShared);
    }

    public Collection<ListDef> getLists(
        @NotNull Container container,
        @Nullable User user,
        boolean checkVisibility,
        boolean includePicklists,
        boolean includeProjectAndShared
    )
    {
        Collection<ListDef> scopedLists = getAllScopedLists(container, includeProjectAndShared);
        if (!includePicklists)
            scopedLists = scopedLists.stream().filter(listDef -> !listDef.isPicklist()).collect(Collectors.toList());
        if (checkVisibility)
            return scopedLists.stream().filter(listDef -> listDef.isVisible(user)).collect(Collectors.toList());
        else
            return scopedLists;
    }

    /**
     * Returns all list definitions defined within the scope of the container. This can optionally include list
     * definitions from the container's project as well as the Shared folder. In the event of a name collision the
     * closest container's list definition will be returned (i.e. container > project > Shared).
     */
    private Collection<ListDef> getAllScopedLists(@NotNull Container container, boolean includeProjectAndShared)
    {
        List<ListDef> ownLists = _listDefCache.get(container.getId());
        Map<String, ListDef> listDefMap = new CaseInsensitiveHashMap<>();

        if (includeProjectAndShared)
        {
            for (ListDef sharedList : _listDefCache.get(ContainerManager.getSharedContainer().getId()))
                listDefMap.put(sharedList.getName(), sharedList);

            Container project = container.getProject();
            if (project != null)
            {
                for (ListDef projectList : _listDefCache.get(project.getId()))
                    listDefMap.put(projectList.getName(), projectList);
            }
        }

        // Workbooks can see parent lists.
        if (container.isWorkbook())
        {
            Container parent = container.getParent();
            if (parent != null)
            {
                for (ListDef parentList : _listDefCache.get(parent.getId()))
                    listDefMap.put(parentList.getName(), parentList);
            }
        }

        for (ListDef ownList : ownLists)
            listDefMap.put(ownList.getName(), ownList);

        return listDefMap.values();
    }

    /**
     * Utility method now that ListTable is ContainerFilter aware; TableInfo.getSelectName() returns now returns null
     */
    String getListTableName(TableInfo ti)
    {
        if (ti instanceof ListTable lti)
            return lti.getRealTable().getSelectName();
        return ti.getSelectName();  // if db is being upgraded from <= 13.1, lists are still SchemaTableInfo instances
    }

    @Nullable
    public ListDef getList(Container container, int listId)
    {
        SimpleFilter filter = new PkFilter(getListMetadataTable(), new Object[]{container, listId});
        ListDef list = new TableSelector(getListMetadataTable(), filter, null).getObject(ListDef.class);

        // Workbooks can see their parent's lists, so check that container if we didn't find the list the first time
        if (list == null && container.isWorkbook())
        {
            filter = new PkFilter(getListMetadataTable(), new Object[]{container.getParent(), listId});
            list = new TableSelector(getListMetadataTable(), filter, null).getObject(ListDef.class);
        }
        return list;
    }

    public ListDomainKindProperties getListDomainKindProperties(Container container, @Nullable Integer listId)
    {
        if (null == listId)
        {
            return new ListDomainKindProperties();
        }
        else
        {
            SimpleFilter filter = new PkFilter(getListMetadataTable(), new Object[]{container, listId});
            ListDomainKindProperties list = new TableSelector(getListMetadataTable(), filter, null).getObject(ListDomainKindProperties.class);

            // Workbooks can see their parent's lists, so check that container if we didn't find the list the first time
            if (list == null && container.isWorkbook())
            {
                filter = new PkFilter(getListMetadataTable(), new Object[]{container.getParent(), listId});
                list = new TableSelector(getListMetadataTable(), filter, null).getObject(ListDomainKindProperties.class);
            }
            return list;
        }
    }

    // Note: callers must invoke indexer (can't invoke here since we may be in a transaction)
    public ListDef insert(User user, final ListDef def, Collection<Integer> preferredListIds)
    {
        Container c = def.lookupContainer();
        if (null == c)
            throw OptimisticConflictException.create(Table.ERROR_DELETED);

        TableInfo tinfo = getListMetadataTable();
        DbSequence sequence = DbSequenceManager.get(c, LIST_SEQUENCE_NAME);
        ListDef.ListDefBuilder builder = new ListDef.ListDefBuilder(def);

        builder.setListId(-1);

        for (Integer preferredListId : preferredListIds)
        {
            SimpleFilter filter = new SimpleFilter(tinfo.getColumn("Container").getFieldKey(), c).addCondition(tinfo.getColumn("ListId"), preferredListId);

            // Need to check proactively... unfortunately, calling insert and handling the constraint violation will cancel the current transaction
            if (!new TableSelector(getListMetadataTable().getColumn("ListId"), filter, null).exists())
            {
                builder.setListId(preferredListId);
                sequence.ensureMinimum(preferredListId);  // Ensure sequence is at or above the preferred ID we just used
                break;
            }
        }

        // If none of the preferred IDs is available then use the next sequence value
        if (builder.getListId() == -1)
            builder.setListId((int)sequence.next());

        ListDef ret = Table.insert(user, tinfo, builder.build());
        _listDefCache.remove(c.getId());
        return ret;
    }


    // Note: callers must invoke indexer (can't invoke here since we may already be in a transaction)
    ListDef update(User user, final ListDef def)
    {
        Container c = def.lookupContainer();
        if (null == c)
            throw OptimisticConflictException.create(Table.ERROR_DELETED);

        DbScope scope = getListMetadataSchema().getScope();
        ListDef ret;

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            ListDef old = getList(c, def.getListId());
            ret = Table.update(user, getListMetadataTable(), def, new Object[]{c, def.getListId()});
            handleIndexSettingChanges(scope, def, old.getEachItemIndex(), ret.getEachItemIndex(), old.getEntireListIndex(), ret.getEachItemIndex(), old.getFileAttachmentIndex(), ret.getFileAttachmentIndex());

            String oldName = old.getName();
            String updatedName = ret.getName();
            queryChangeUpdate(user, c, oldName, updatedName);
            transaction.commit();
        }

        return ret;
    }

    //Note: this is sort of a dupe of above update() which returns ListDef
    ListDomainKindProperties update(User user, Container c, final ListDomainKindProperties listProps)
    {
        if (null == c)
            throw OptimisticConflictException.create(Table.ERROR_DELETED);

        DbScope scope = getListMetadataSchema().getScope();
        ListDomainKindProperties updated;

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            ListDomainKindProperties old = getListDomainKindProperties(c, listProps.getListId());
            updated = Table.update(user, getListMetadataTable(), listProps, new Object[]{c, listProps.getListId()});
            ListDef listDef = getList(c, listProps.getListId());
            handleIndexSettingChanges(scope, listDef, old.isEachItemIndex(), listProps.isEachItemIndex(), old.isEntireListIndex(), listProps.isEntireListIndex(), old.isFileAttachmentIndex(), listProps.isFileAttachmentIndex());

            String oldName = old.getName();
            String updatedName = updated.getName();
            queryChangeUpdate(user, c, oldName, updatedName);

            transaction.commit();
        }

        return updated;
    }

    // Queue up one-time operations related to turning indexing on or off
    private void handleIndexSettingChanges(DbScope scope, ListDef listDef, boolean oldEachItemIndex, boolean newEachItemIndex, boolean oldEntireListIndex, boolean newEntireListIndex, boolean oldFileAttachmentIndex, boolean newFileAttachmentIndex)
    {
        scope.addCommitTask(() -> {
            ListDefinition list = ListDefinitionImpl.of(listDef);

            // Turning on each-item indexing or attachment indexing -> clear last indexed column
            if ((!oldEachItemIndex && newEachItemIndex) || (!oldFileAttachmentIndex && newFileAttachmentIndex))
                clearLastIndexed(scope, ListSchema.getInstance().getSchemaName(), listDef);

            // Turning off each-item indexing -> clear item docs from the index
            if (oldEachItemIndex && !newEachItemIndex)
                deleteIndexedItems(list);

            // Turning off attachment indexing -> clear attachment docs from the index
            if (oldFileAttachmentIndex && !newFileAttachmentIndex)
                deleteIndexedAttachments(list);

            // Turning on entire-list indexing -> clear that list's last indexed column
            if (!oldEntireListIndex && newEntireListIndex)
            {
                SQLFragment sql = new SQLFragment("UPDATE ")
                    .append(getListMetadataTable().getSelectName())
                    .append(" SET LastIndexed = NULL WHERE ListId = ? AND LastIndexed IS NOT NULL")
                    .add(list.getListId());

                new SqlExecutor(scope).execute(sql);
            }

            // Turning off entire-list indexing -> clear entire-list doc from the index
            if (oldEntireListIndex && !newEntireListIndex)
                deleteIndexedEntireListDoc(list);

        }, DbScope.CommitTaskOption.POSTCOMMIT);
    }

    private void queryChangeUpdate(User user, Container c, String oldName, String updatedName)
    {
        _listDefCache.remove(c.getId());
        QueryChangeListener.QueryPropertyChange.handleQueryNameChange(oldName, updatedName, new SchemaKey(null, ListQuerySchema.NAME), user, c);
    }

    // CONSIDER: move "list delete" from  ListDefinitionImpl.delete() implementation to ListManager for consistency
    void deleteListDef(Container c, int listid)
    {
        DbScope scope = getListMetadataSchema().getScope();
        assert scope.isTransactionActive();
        try
        {
            Table.delete(ListManager.get().getListMetadataTable(), new Object[]{c, listid});
        }
        catch (OptimisticConflictException x)
        {
            // ok
        }
        _listDefCache.remove(c.getId());
    }


    public static final SearchService.SearchCategory listCategory = new SearchService.SearchCategory("list", "List");

    // Index all lists in this container
    @Override
    public void enumerateDocuments(@Nullable IndexTask t, final @NotNull Container c, @Nullable Date since)
    {
        final IndexTask task;
        if (null == t)
        {
            final SearchService ss = SearchService.get();
            if (ss == null)
                return;
            task = ss.defaultTask();
        }
        else
        {
            task = t;
        }

        Runnable r = () -> {
            Map<String, ListDefinition> lists = ListService.get().getLists(c, null, false);

            try
            {
                QueryService.get().setEnvironment(QueryService.Environment.USER, User.getSearchUser());
                QueryService.get().setEnvironment(QueryService.Environment.CONTAINER, c);
                for (ListDefinition list : lists.values())
                {
                    try
                    {
                        boolean reindex = since == null || list.getLastIndexed() == null || list.getLastIndexed().compareTo(since) > 0;
                        indexList(task, list, false, reindex);
                    }
                    catch (Exception ex)
                    {
                        LOG.error("Error indexing list '" + list.getName() + "' in container '" + c.getPath() + "'.", ex);
                    }
                }
            }
            finally
            {
                QueryService.get().clearEnvironment();
            }
        };

        task.addRunnable(r, SearchService.PRIORITY.group);
    }

    public void indexList(final ListDefinition def, boolean designChange)
    {
        indexList(((ListDefinitionImpl) def)._def, designChange);
    }

    // Index a single list
    public void indexList(final ListDef def, boolean designChange)
    {
        SearchService ss = SearchService.get();

        if (null != ss)
        {
            final IndexTask task = ss.defaultTask();

            Runnable r = () ->
            {
                Container c = def.lookupContainer();
                if (c == null || ContainerManager.isDeleting(c))
                {
                    LOG.info("List container has been deleted or is being deleted; not indexing list '" + def.getName() + "");
                }
                else
                {
                    //Refresh list definition -- Issue #42207 - MSSQL server returns entityId as uppercase string
                    ListDefinition list = ListService.get().getList(c, def.getListId());
                    indexList(task, list, designChange, false);
                }
            };

            task.addRunnable(r, SearchService.PRIORITY.item);
        }
    }


    private void indexList(@NotNull IndexTask task, ListDefinition list, boolean designChange, final boolean reindex)
    {
        Domain domain = list.getDomain();

        if (null == domain)
        {
            // List was probably just deleted
            return;
        }

        indexEntireList(task, list, reindex);
        indexModifiedItems(task, list, reindex);

        //If attachmentIndexing (checked within method) is enabled index attachment file(s)
        indexAttachments(task, list, designChange, reindex);
    }

    // Delete a single list item from the index after item delete
    public void deleteItemIndex(final ListDefinition list, @NotNull final String entityId)
    {
        if (!list.getEntireListIndex() && !list.getEachItemIndex())
            return;

        SearchService ss = SearchService.get();

        if (null != ss)
        {
            final IndexTask task = ss.defaultTask();

            Runnable r = () -> ss.deleteResource(getDocumentId(list, entityId));

            task.addRunnable(r, SearchService.PRIORITY.delete);
        }
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
    private void indexModifiedItems(@NotNull final IndexTask task, final ListDefinition list, final boolean reindex)
    {
        if (!list.getEachItemIndex())
        {
            return;
        }

        String lastIndexClause = reindex ? "(1=1) OR " : ""; //Prepend TRUE if we want to force a reindexing

        // Index all items that have never been indexed OR where either the list definition or list item itself has changed since last indexed
        lastIndexClause += "LastIndexed IS NULL OR LastIndexed < ? OR (Modified IS NOT NULL AND LastIndexed < Modified)";
        SimpleFilter filter = new SimpleFilter(new SimpleFilter.SQLClause(lastIndexClause, new Object[]{list.getModified()}));

        indexItems(task, list, filter);
    }


    // Reindex items specified by filter
    private int indexItems(@NotNull final IndexTask task, final ListDefinition list, SimpleFilter filter)
    {
        TableInfo listTable = list.getTable(User.getSearchUser());

        if (null == listTable)
            return 0;

        FieldKeyStringExpression titleTemplate = createEachItemTitleTemplate(list, listTable);
        FieldKeyStringExpression bodyTemplate = createBodyTemplate(list, "\"each item as a separate document\" custom indexing template", list.getEachItemBodySetting(), list.getEachItemBodyTemplate(), listTable);

        FieldKey keyKey = new FieldKey(null, list.getKeyName());
        FieldKey entityIdKey = new FieldKey(null, "EntityId");

        FieldKey createdKey = new FieldKey(null, "created");
        FieldKey createdByKey = new FieldKey(null, "createdBy");
        FieldKey modifiedKey = new FieldKey(null, "modified");
        FieldKey modifiedByKey = new FieldKey(null, "modifiedBy");

        MutableInt count = new MutableInt(0);

        // TODO: Attempting to respect tableUrl for details link... but this doesn't actually work. See #28747.
        StringExpression se = listTable.getDetailsURL(null, list.getContainer());

        new TableSelector(listTable, filter, null).setJdbcCaching(false).setForDisplay(true).forEachResults(results -> {
            Map<FieldKey, Object> map = results.getFieldKeyRowMap();
            final Object pk = map.get(keyKey);
            String entityId = (String)map.get(entityIdKey);

            String documentId = getDocumentId(list, entityId);
            Map<String, Object> props = new HashMap<>();
            props.put(SearchService.PROPERTY.categories.toString(), listCategory.toString());
            String displayTitle = titleTemplate.eval(map);
            props.put(SearchService.PROPERTY.title.toString(), displayTitle);

            Date created = null;
            if (map.get(createdKey) instanceof Date)
                created = (Date)map.get(createdKey);

            Date modified = null;
            if (map.get(modifiedKey) instanceof Date)
                modified = (Date)map.get(modifiedKey);

            String body = bodyTemplate.eval(map);

            ActionURL itemURL;

            try
            {
                itemURL = new ActionURL(se.eval(map));
            }
            catch (Exception e)
            {
                itemURL = list.urlDetails(pk);
            }

            itemURL.setExtraPath(list.getContainer().getId()); // Use ID to guard against folder moves/renames

            SimpleDocumentResource r = new SimpleDocumentResource(
                    new Path(documentId),
                    documentId,
                    list.getContainer().getId(),
                    "text/plain",
                    body,
                    itemURL,
                    UserManager.getUser((Integer)map.get(createdByKey)), created,
                    UserManager.getUser((Integer)map.get(modifiedByKey)), modified,
                    props) {
                @Override
                public void setLastIndexed(long ms, long modified)
                {
                    ListManager.get().setItemLastIndexed(list, pk, listTable, ms);
                }
            };

            // Add navtrail that includes link to full list grid
            ActionURL gridURL = list.urlShowData();
            gridURL.setExtraPath(list.getContainer().getId()); // Use ID to guard against folder moves/renames
            NavTree t = new NavTree("list", gridURL);
            String nav = NavTree.toJS(Collections.singleton(t), null, false).toString();
            r.getMutableProperties().put(SearchService.PROPERTY.navtrail.toString(), nav);

            task.addResource(r, SearchService.PRIORITY.item);

            count.increment();
        });

        return count.getValue();
    }

    /**
     * Add searchable resources to Indexing task for file attachments
     * @param task indexing task
     * @param list containing file attachments
     * @param designChange flag indicating change in design
     */
    private int indexAttachments(@NotNull final IndexTask task, ListDefinition list, boolean designChange, boolean reindex)
    {
        TableInfo listTable = list.getTable(User.getSearchUser());
        if (null == listTable)
            return 0;

        //If the index call was not due to a list design change and there are no attachment columns than don't try to index
        if (!designChange && listTable.getColumns().stream().noneMatch(ci -> ci.getPropertyType() == PropertyType.ATTACHMENT))
            return 0;

        //If FileAttachmentIndexing is disabled, remove any existing resources from the Index
        if (!list.getFileAttachmentIndex())
        {
            return 0;
        }

        //Instantiate a counter
        MutableInt count = new MutableInt(0);

        //Get common objects & properties
        FieldKey entityIdKey = new FieldKey(null, "EntityId");
        AttachmentService as = AttachmentService.get();
        FieldKeyStringExpression titleTemplate = createEachItemTitleTemplate(list, listTable);

        // Index all items that have never been indexed
        //   OR where either the list definition
        //   OR list item itself has changed since last indexed
        String lastIndexedClause = reindex ? "(1=1) OR " : "";
        lastIndexedClause += "LastIndexed IS NULL OR LastIndexed < ? OR (Modified IS NOT NULL AND LastIndexed < Modified)";
        SimpleFilter filter = new SimpleFilter(new SimpleFilter.SQLClause(lastIndexedClause, new Object[]{list.getModified()}));

        new TableSelector(listTable, filter, null).setJdbcCaching(false).setForDisplay(true).forEachResults(results ->
        {
            Map<FieldKey, Object> map = results.getFieldKeyRowMap();
            String title = titleTemplate.eval(map);
            String rowEntityId = (String)map.get(entityIdKey);
            AttachmentParent listItemParent = new ListItemAttachmentParent(rowEntityId, list.getContainer());

            for (Attachment attachment : as.getAttachments(listItemParent))
            {
                //Get documentName and downloadUrl
                String documentName = attachment.getName();
                ActionURL downloadUrl = ListController.getDownloadURL(list, rowEntityId, documentName);

                //Generate searchable resource
                String displayTitle = title + " attachment file \"" + documentName + "\"";
                WebdavResource attachmentRes = as.getDocumentResource(
                        new Path(rowEntityId, documentName),
                        downloadUrl,
                        displayTitle,
                        listItemParent,
                        documentName,
                        SearchService.fileCategory
                );

                //Add breadcrumb link to list
                ActionURL gridURL = list.urlShowData();
                gridURL.setExtraPath(list.getContainer().getId()); // Use ID to guard against folder moves/renames
                NavTree t = new NavTree("list", gridURL);
                String nav = NavTree.toJS(Collections.singleton(t), null, false, true).toString();
                attachmentRes.getMutableProperties().put(SearchService.PROPERTY.navtrail.toString(), nav);
                task.addResource(attachmentRes, SearchService.PRIORITY.item);

                count.increment();
            }
        });

        return count.getValue();
    }

    private void indexEntireList(@NotNull IndexTask task, final ListDefinition list, boolean reindex)
    {
        if (!list.getEntireListIndex())
        {
            return;
        }

        ListDefinition.IndexSetting setting = list.getEntireListIndexSetting();
        String documentId = getDocumentId(list);

        // First check if metadata needs to be indexed: if the setting is enabled and the definition has changed
        boolean needToIndex = (setting.indexMetaData() && hasDefinitionChangedSinceLastIndex(list));

        // If that didn't hold true then check for entire list data indexing: if the definition has changed or any item has been modified
        if (!needToIndex && setting.indexItemData())
            needToIndex = hasDefinitionChangedSinceLastIndex(list) || hasModifiedItems(list);

        needToIndex |= reindex;

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
            int fileSizeLimit = (int)(SearchService.get().getFileSizeLimit()*.99);

            if (ti != null)
            {
                body.append(sep);
                FieldKeyStringExpression template = createBodyTemplate(list, "\"entire list as a single document\" custom indexing template", list.getEntireListBodySetting(), list.getEntireListBodyTemplate(), ti);

                // All columns, all rows, no filters, no sorts
                new TableSelector(ti).setJdbcCaching(false).setForDisplay(true).forEachResults(new ForEachBlock<>()
                {
                    @Override
                    public void exec(Results results) throws StopIteratingException
                    {
                        body.append(template.eval(results.getFieldKeyRowMap())).append("\n");
                        // Issue 25366: Short circuit for very large list
                        if (body.length() > fileSizeLimit)
                        {
                            body.setLength(fileSizeLimit); // indexer also checks size... make sure we're under the limit
                            stopIterating();
                        }
                    }
                });
            }
        }

        ActionURL url = list.urlShowData();
        url.setExtraPath(list.getContainer().getId()); // Use ID to guard against folder moves/renames

        SimpleDocumentResource r = new SimpleDocumentResource(
                new Path(documentId),
                documentId,
                list.getContainer().getId(),
                "text/plain",
                body.toString(),
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


    void deleteIndexedList(ListDefinition list)
    {
        deleteIndexedEntireListDoc(list);
        deleteIndexedItems(list);
        deleteIndexedAttachments(list);
    }

    private void deleteIndexedAttachments(@NotNull ListDefinition list)
    {
        AttachmentService as = AttachmentService.get();

        // make sure container still exists (race condition on container delete)
        Container listContainer = list.getContainer();
        if (null == listContainer)
            return;
        TableInfo listTable = new ListQuerySchema(User.getSearchUser(), listContainer).getTable(list.getName());
        if (null == listTable)
            return;

        List<String> parentIds = new ArrayList<>();
        new TableSelector(listTable, Set.of("EntityId")).setJdbcCaching(false).forEachMap(row -> {
            parentIds.add((String)row.get("EntityId"));

            // Delete in batches to minimize db queries
            if (parentIds.size() > 10000)
            {
                as.deleteAttachmentIndexes(parentIds);
                parentIds.clear();
            }
        });

        // Finish last batch
        as.deleteAttachmentIndexes(parentIds);
    }

    // Un-index the entire list doc alone, but leave the list items alone
    private void deleteIndexedEntireListDoc(ListDefinition list)
    {
        SearchService ss = SearchService.get();

        if (null != ss)
            ss.deleteResource(getDocumentId(list));
    }


    // Un-index all list items, but leave the entire list doc alone
    private void deleteIndexedItems(ListDefinition list)
    {
        SearchService ss = SearchService.get();

        if (null != ss)
            ss.deleteResourcesForPrefix(getDocumentId(list, null));
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

        // Issue 21794: If you're devious enough to put ${ in your list name then we'll just strip it out
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
                sb.append(column.getFieldKey().encode());  // Issue 21794: Must encode
                sb.append("}");
                sep = " ";
            }
        }

        template = createValidStringExpression(sb.toString(), error);

        if (null == template)
            throw new IllegalStateException(getTemplateErrorMessage(list, "auto-generated indexing template", error));

        return template;
    }


    // Issue 21726: Perform some simple validation of custom indexing template
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
            // Using EXISTS query should be reasonably efficient.
            SQLFragment sql = new SQLFragment("SELECT 1 FROM ");
            sql.append(getListTableName(table));
            sql.append(" WHERE Modified > (SELECT LastIndexed FROM ").append(getListMetadataTable().getSelectName());
            sql.append(" WHERE ListId = ? AND Container = ?)");
            sql.add(list.getListId());
            sql.add(list.getContainer().getEntityId());

            return new SqlSelector(getListMetadataSchema(), sql).exists();
        }

        return false;
    }

    private void setLastIndexed(ListDefinition list, long ms)
    {
        // list table does not have an index on listid, so we should include container in the WHERE
        new SqlExecutor(getListMetadataSchema()).execute("UPDATE " + getListMetadataTable().getSelectName() +
                " SET LastIndexed = ? WHERE Container = ? AND ListId = ?", new Timestamp(ms), list.getContainer(), list.getListId());
    }


    private void setItemLastIndexed(ListDefinition list, Object pk, TableInfo ti, long ms)
    {
        // The "search user" might not have access
        if (null != ti)
        {
            // 'unwrap' ListTable to get schema table for update
            TableInfo sti = ((ListTable)ti).getSchemaTableInfo();
            ColumnInfo keyColumn = sti.getColumn(list.getKeyName());
            if (null != keyColumn)
            {
                String keySelectName = keyColumn.getSelectName();
                new SqlExecutor(sti.getSchema()).execute("UPDATE " + getListTableName(sti) + " SET LastIndexed = ? WHERE " +
                        keySelectName + " = ?", new Timestamp(ms), pk);
            }
        }
    }


    @Override
    public void indexDeleted()
    {
        TableInfo listTable = getListMetadataTable();
        DbScope scope = listTable.getSchema().getScope();

        // Clear LastIndexed column of the exp.List table, which addresses the "index the entire list as a single document" case
        clearLastIndexed(scope, listTable.getSelectName());

        String listSchemaName = ListSchema.getInstance().getSchemaName();

        // Now clear LastIndexed column of every underlying list table, which addresses the "index each list item as a separate document" case. See #28748.
        new TableSelector(getListMetadataTable()).forEach(ListDef.class, listDef -> {
            clearLastIndexed(scope, listSchemaName, listDef);
        });
    }

    private void clearLastIndexed(DbScope scope, String listSchemaName, ListDef listDef)
    {
        // Clear LastIndexed column only for lists that are set to index each item, Issue 47998
        if (listDef.getEachItemIndex())
        {
            ListDefinition list = new ListDefinitionImpl(listDef);
            Domain domain = list.getDomain();
            if (null != domain && null != domain.getStorageTableName())
            {
                LOG.info("List " + listDef.getContainerPath() + " - " + listDef.getName() + ": Set to index each item, so clearing last indexed");
                clearLastIndexed(scope, listSchemaName + "." + domain.getStorageTableName());
            }
        }
    }

    private void clearLastIndexed(DbScope scope, String selectName)
    {
        try
        {
            // Yes, that WHERE clause is intentional and makes a big performance improvement in some cases
            new SqlExecutor(scope).execute("UPDATE " + selectName + " SET LastIndexed = NULL WHERE LastIndexed IS NOT NULL");
        }
        catch (Exception e)
        {
            // Log the exception, but allow other tables to be cleared
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }

    void addAuditEvent(ListDefinitionImpl list, User user, String comment)
    {
        if (null != user)
        {
            ListAuditProvider.ListAuditEvent event = new ListAuditProvider.ListAuditEvent(list.getContainer().getId(), comment);

            Container c = list.getContainer();
            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());

            event.setListDomainUri(list.getDomain().getTypeURI());
            event.setListId(list.getListId());
            event.setListName(list.getName());

            AuditLogService.get().addEvent(user, event);
        }
    }

    /**
     * Modeled after ListItemImpl.addAuditEvent
     */
    void addAuditEvent(ListDefinitionImpl list, User user, Container c, String comment, String entityId, @Nullable String oldRecord, @Nullable String newRecord)
    {
        ListAuditProvider.ListAuditEvent event = new ListAuditProvider.ListAuditEvent(c.getId(), comment);

        Container project = c.getProject();
        if (null != project)
            event.setProjectId(project.getId());

        event.setListDomainUri(list.getDomain().getTypeURI());
        event.setListId(list.getListId());
        event.setListItemEntityId(entityId);
        event.setListName(list.getName());

        if (oldRecord != null) event.setOldRecordMap(oldRecord);
        if (newRecord != null) event.setNewRecordMap(newRecord);

        AuditLogService.get().addEvent(user, event);
    }

    String formatAuditItem(ListDefinitionImpl list, User user, Map<String, Object> props)
    {
        String itemRecord = "";
        TableInfo ti = list.getTable(user);

        if (null != ti)
        {
            Map<String, String> recordChangedMap = new CaseInsensitiveHashMap<>();
            Set<String> reserved = list.getDomain().getDomainKind().getReservedPropertyNames(list.getDomain(), user);

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
                itemRecord = ListAuditProvider.encodeForDataMap(list.getContainer(), recordChangedMap);
        }

        return itemRecord;
    }

    boolean importListSchema(
        ListDefinition unsavedList,
        ImportTypesHelper importHelper,
        User user,
        Collection<ValidatorImporter> validatorImporters,
        List<String> errors
    ) throws Exception
    {
        if (!errors.isEmpty())
            return false;

        final Container container = unsavedList.getContainer();
        final Domain domain = unsavedList.getDomain();
        final String typeURI = domain.getTypeURI();

        DomainURIFactory factory = name -> new Pair<>(typeURI, container);

        ImportPropertyDescriptorsList pds = importHelper.getImportPropertyDescriptors(factory, errors, container);

        if (!errors.isEmpty())
            return false;

        for (ImportPropertyDescriptor ipd : pds.properties)
        {
            if (null == ipd.domainName || null == ipd.domainURI)
                errors.add("List not specified for property: " + ipd.pd.getName());
        }

        if (!errors.isEmpty())
            return false;

        for (ImportPropertyDescriptor ipd : pds.properties)
        {
            DomainProperty domainProperty = domain.addPropertyOfPropertyDescriptor(ipd.pd);
            domainProperty.setConditionalFormats(ipd.formats);
            domainProperty.setDefaultValue(ipd.defaultValue);
        }

        unsavedList.save(user);

        // Save validators later, after all the lists are imported, #40343
        validatorImporters.add(new ValidatorImporter(domain.getTypeId(), pds.properties, user));

        return true;
    }

    public static class TestCase extends Assert
    {
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

        private ListDefinitionImpl list;
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

        private void addListItem(Container scopedContainer, ListDefinition scopedList, String value)
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
        public void testListServiceInOwnFolder()
        {
            Map<String, ListDefinition> lists = ListService.get().getLists(c);
            assertTrue("Test List not found in own container", lists.containsKey(LIST_NAME));
            ListItem li = lists.get(LIST_NAME).getListItem(1, u, c);
            assertEquals("Item not found in own container", li.getProperty(dp), PARENT_LIST_ITEM);
        }

        @Test
        public void testListServiceInWorkbook()
        {
            Container workbook1 = setupWorkbook(WORKBOOK1_NAME);
            Container workbook2 = setupWorkbook(WORKBOOK2_NAME);
            Map<String, ListDefinition> lists = ListService.get().getLists(workbook1);
            assertTrue("Test List not found in workbook", lists.containsKey(LIST_NAME));

            checkListItemScoping(workbook1, workbook2);
        }

        private Container setupWorkbook(String title)
        {
            return ContainerManager.createContainer(c, null, title, null, WorkbookContainerType.NAME, u);
        }

        private void checkListItemScoping(Container wb1, Container wb2)
        {
            ListDefinition wbList1 = ListService.get().getLists(wb1).get(LIST_NAME);
            ListDefinition wbList2 = ListService.get().getLists(wb2).get(LIST_NAME);

            assertEquals("Lists available to each workbook are not the same", wbList1.toString(), wbList2.toString());
            addListItem(wb1, wbList1, WORKBOOK1_LIST_ITEM);
            addListItem(wb2, wbList2, WORKBOOK2_LIST_ITEM);

            assertNull("Parent item visible in workbook", wbList1.getListItem(PARENT_LI_KEY, u, wb1));
            assertNull("Sibling workbook item visible in another workbook", wbList1.getListItem(WB2_LI_KEY, u, wb1));
            assertEquals("Parent container can not see child workbook item", wbList1.getListItem(WB1_LI_KEY, u, c).getProperty(dp), WORKBOOK1_LIST_ITEM);
            assertEquals("Workbook can not see its own list item", wbList1.getListItem(WB1_LI_KEY, u, wb1).getProperty(dp), WORKBOOK1_LIST_ITEM);
        }
    }
}
