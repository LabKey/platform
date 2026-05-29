/*
 * Copyright (c) 2005-2018 Fred Hutchinson Cancer Research Center
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
package org.labkey.wiki;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.AttachmentService.DuplicateFilenameException;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerService;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.mcp.McpService;
import org.labkey.api.mcp.McpService.VectorDocument;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.WikiTermsOfUseProvider;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewContext.StackResetter;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.wiki.FormattedHtml;
import org.labkey.api.wiki.WikiChangeListener;
import org.labkey.api.wiki.WikiPartFactory;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiRenderingService.SubstitutionMode;
import org.labkey.api.wiki.WikiService;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiType;
import org.labkey.wiki.model.WikiVersion;
import org.labkey.wiki.model.WikiVersionsGrid;
import org.labkey.wiki.model.WikiView;
import org.labkey.wiki.query.WikiSchema;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;

public class WikiManager implements WikiService
{
    public static final WikiRendererType DEFAULT_WIKI_RENDERER_TYPE = WikiRendererType.HTML;
    public static final SearchService.SearchCategory searchCategory = new SearchService.SearchCategory("wiki", "Wiki Pages");

    private static final Logger LOG = LogManager.getLogger(WikiManager.class);
    private static final WikiManager _instance = new WikiManager();
    private static final List<WikiChangeListener> listeners = new CopyOnWriteArrayList<>();

    private static List<WikiPartFactory> _wikiPartFactories;

    /* service/schema dependencies */
    private final CommSchema comm = CommSchema.getInstance();
    private final CoreSchema core = CoreSchema.getInstance();

    private WikiManager()
    {
        LOG.debug("WikiManager instantiated");
    }

    public static WikiManager get()
    {
        return _instance;
    }

    @Override
    public void registerWikiPartFactory(WebPartFactory partFactory, WikiPartFactory.Privilege privilege, String activeModuleName)
    {
        if (null == _wikiPartFactories)
            _wikiPartFactories = new ArrayList<>();

        _wikiPartFactories.add(new WikiPartFactory(partFactory, privilege, activeModuleName));
    }

    @NotNull
    public List<WikiPartFactory> getWikiPartFactories()
    {
        if (null == _wikiPartFactories)
            return Collections.emptyList();
        return _wikiPartFactories;
    }

    AttachmentService getAttachmentService()
    {
        return AttachmentService.get();
    }

    @Nullable
    SearchService getSearchService()
    {
        return SearchService.get();
    }

    // Used to verify that entityId is a wiki and belongs in the specified container
    public Wiki getWikiByEntityId(Container c, String entityId)
    {
        if (null == c || c.getId().isEmpty() || null == entityId || entityId.isEmpty())
            return null;

        return new TableSelector(comm.getTableInfoPages(),
                SimpleFilter.createContainerFilter(c).addCondition(FieldKey.fromParts("EntityId"), entityId),
                null).getObject(Wiki.class);
    }

    // aliases: null and empty collection are equivalent (no aliases)
    public void insertWiki(User user, Container c, Wiki wikiInsert, WikiVersion wikiversion, List<AttachmentFile> files, boolean copyHistory, @Nullable Collection<String> aliases) throws IOException
    {
        DbScope scope = comm.getSchema().getScope();

        //transact insert of wiki page, new version, and any attachments
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            wikiInsert.beforeInsert(user, c.getId());
            wikiInsert.setPageVersionId(null);
            LOG.debug("Table.insert() for wiki {}", wikiInsert.getName());
            Table.insert(user, comm.getTableInfoPages(), wikiInsert);
            String entityId = wikiInsert.getEntityId();

            //insert initial version for this page
            wikiversion.setPageEntityId(entityId);
            if (!copyHistory)
            {
                wikiversion.setCreated(wikiInsert.getCreated());
                wikiversion.setCreatedBy(wikiInsert.getCreatedBy());
            }
            wikiversion.setVersion(1);
            LOG.debug("Table.insert() for wiki version {}", wikiInsert.getName());

            //if copying wiki with history, avoid overwriting 'created by' user
            User userToInsert = (copyHistory) ? null : user;
            Table.insert(userToInsert, comm.getTableInfoPageVersions(), wikiversion);

            //get rowid for newly inserted version
            wikiversion = WikiSelectManager.getVersion(wikiInsert, 1);

            //store initial version reference in Pages table
            wikiInsert.setPageVersionId(wikiversion.getRowId());
            Table.update(userToInsert, comm.getTableInfoPages(), wikiInsert, wikiInsert.getEntityId());

            getAttachmentService().addAttachments(wikiInsert.getAttachmentParent(), files, user);

            if (null != aliases)
            {
                WikiManager.get().addAliases(wikiInsert, aliases, null);
            }

            transaction.commit();
        }
        finally
        {
            WikiCache.uncache(c, wikiInsert, true);

            LOG.debug("indexWiki() for {}", wikiInsert.getName());
            indexWiki(wikiInsert);
        }

        if (wikiInsert.getName() != null)
            fireWikiCreated(user, c, wikiInsert.getName());
    }


    public boolean updateWiki(User user, Wiki wikiNew, WikiVersion versionNew, boolean copyHistory)
    {
        return updateWiki(user, wikiNew, versionNew, copyHistory, true);
    }

    /**
     * Update an existing wiki with new content.
     *
     * @param copyHistory true to propagate the user and date created from the previous wiki version, else just use the current user
     *                    and current date.
     * @param createNewVersion by default, we create a new wiki version for each update, if this is set to false we will update
     *                         the latest wiki version.
     */
    private boolean updateWiki(User user, Wiki wikiNew, WikiVersion versionNew, boolean copyHistory, boolean createNewVersion)
    {
        DbScope scope = comm.getSchema().getScope();
        Container c = wikiNew.lookupContainer();
        boolean uncacheAllContent = true;
        Wiki wikiOld = null;

        //transact wiki update and version insert
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            //if name, title, parent, & sort order are all still the same,
            //we don't need to uncache all wikis, only the wiki being updated
            //NOTE: getWikiByEntityId does not use the cache, so we'll get a fresh copy from the database
            wikiOld = getWikiByEntityId(c, wikiNew.getEntityId());
            WikiVersion versionOld = wikiOld.getLatestVersion();
            String oldTitle = StringUtils.trimToEmpty(versionOld.getTitle());
            boolean rename = !wikiOld.getName().equals(wikiNew.getName());

            uncacheAllContent = rename
                    || !Objects.equals(wikiOld.getParent(), wikiNew.getParent())
                    || wikiOld.getDisplayOrder() != wikiNew.getDisplayOrder()
                    || (null != versionNew && !oldTitle.equals(versionNew.getTitle()));

            //update Pages table
            //UNDONE: should take RowId, not EntityId
            Table.update(user, comm.getTableInfoPages(), wikiNew, wikiNew.getEntityId());

            if (versionNew != null)
            {
                String entityId = wikiNew.getEntityId();
                versionNew.setPageEntityId(entityId);
                if (!copyHistory)
                {
                    versionNew.setCreated(new Date(System.currentTimeMillis()));
                    versionNew.setCreatedBy(user.getUserId());
                }
                //if copying wiki with history, avoid overwriting 'created by' user
                User userToInsert = (copyHistory) ? null : user;

                if (createNewVersion)
                {
                    //get version number for new version
                    versionNew.setVersion(WikiSelectManager.getNextVersionNumber(wikiNew));
                    //insert initial version for this page
                    versionNew = Table.insert(userToInsert, comm.getTableInfoPageVersions(), versionNew);

                    //update version reference in Pages table.
                    wikiNew.setPageVersionId(versionNew.getRowId());
                    Table.update(userToInsert, comm.getTableInfoPages(), wikiNew, wikiNew.getEntityId());
                }
                else
                {
                    versionNew.setVersion(versionOld.getVersion());
                    Table.update(userToInsert, comm.getTableInfoPageVersions(), versionNew, versionNew.getRowId());
                }
            }
            transaction.commit();
        }
        finally
        {
            // TODO: unindexWiki()... especially in rename case?

            if (null != wikiNew)
            {
                // Always uncache the new one (even in rename case -- we've probably cached a miss under the new name)
                WikiCache.uncache(c, wikiNew, false);
                indexWiki(wikiNew);
            }

            // Uncache the old wiki #12249
            if (null != wikiOld)
                WikiCache.uncache(c, wikiOld, uncacheAllContent);
        }

        if (wikiNew.getName() != null)
            fireWikiChanged(user, c, wikiNew.getName());

        return true;
    }

    /**
     * Attempts to add the specified aliases to the specified wiki. This is a best effort operation; failure to add an
     * alias (e.g., an alias that already exists in this container) will result in an error (added to the BindException
     * collection if not null, otherwise logged as a warning) but adding will continue and no exception will be thrown.
     *
     * Callers are responsible for uncaching this wiki and the wiki container collections
     */
    public void addAliases(Wiki wiki, @NotNull Collection<String> aliases, @Nullable BindException errors)
    {
        assert null != wiki.getContainerId();
        SqlExecutor executor = new SqlExecutor(CommSchema.getInstance().getSchema());

        aliases.forEach(alias->
        {
            // Table.insert() provides no way to conditionalize the insert, resulting in constraint violation exceptions
            // that kill the current transaction on PostgreSQL. We want "best effort" inserts here, so execute custom
            // INSERT SQL instead.
            SQLFragment sql = new SQLFragment("INSERT INTO ")
                .append(CommSchema.getInstance().getTableInfoPageAliases())
                .append(" (Container, Alias, PageRowId) SELECT ?, ?, ?\n")
                .add(wiki.getContainerId())
                .add(alias)
                .add(wiki.getRowId())
                .append("WHERE NOT EXISTS (SELECT * FROM ")
                .append(CommSchema.getInstance().getTableInfoPageAliases())
                .append(" WHERE Container = ? AND LOWER(Alias) = LOWER(?))")
                .add(wiki.getContainerId())
                .add(alias);
            int rows = executor.execute(sql);

            if (0 == rows)
            {
                if (null != errors)
                    errors.rejectValue("name", ERROR_MSG, "Warning: Alias '" + alias + "' already exists in this folder.");
                else
                    LOG.warn("Attempt to add alias to wiki \"{}\" failed; \"{}\" already exists in this folder.", wiki.getName(), alias);
            }
        });
    }

    // Callers are responsible for uncaching this wiki and the wiki container collections
    // null == wiki ==> delete all aliases in a container
    // null != wiki ==> delete all aliases associated with a wiki
    public void deleteAliases(Container c, @Nullable Wiki wiki)
    {
        assert null != c;
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        if (null != wiki)
            filter.addCondition(FieldKey.fromParts("PageRowId"), wiki.getRowId());
        Table.delete(CommSchema.getInstance().getTableInfoPageAliases(), filter);
    }

    // Callers are responsible for uncaching this wiki and the wiki container collections
    public void replaceAliases(Wiki wiki, Collection<String> newAliases, @Nullable BindException errors)
    {
        deleteAliases(wiki.lookupContainer(), wiki);
        addAliases(wiki, newAliases, errors);
    }

    public void deleteWiki(User user, Container c, Wiki wiki, boolean isDeletingSubtree) throws SQLException
    {
        //shift children to new parent, or delete recursively if deleting the whole subtree
        handleChildren(user, c, wiki, isDeletingSubtree);

        DbScope scope = comm.getSchema().getScope();

        //transact deletion of wiki, version, attachments, and discussions
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            wiki.setPageVersionId(null);
            Table.update(user, comm.getTableInfoPages(), wiki, wiki.getEntityId());
            Table.delete(comm.getTableInfoPageVersions(),
                    new SimpleFilter(FieldKey.fromParts("pageentityId"), wiki.getEntityId()));
            Table.delete(comm.getTableInfoPages(),
                    new SimpleFilter(FieldKey.fromParts("entityId"), wiki.getEntityId()));
            deleteAliases(c, wiki);

            getAttachmentService().deleteAttachments(wiki.getAttachmentParent());

            transaction.commit();
        }
        finally
        {
            unindexWiki(wiki.getEntityId());
        }

        if (wiki.getName() != null)
            fireWikiDeleted(user, c, wiki.getName());
        WikiCache.uncache(c, wiki, true);
    }


    private void handleChildren(User user, Container c, Wiki wiki, boolean isDeletingSubtree) throws SQLException
    {
        //shift any children upward so they are not orphaned

        //get page's children
        List<Wiki> children = wiki.children();

        if (!children.isEmpty())
        {
            if(isDeletingSubtree)
            {
                for(Wiki childWiki : children)
                    deleteWiki(user, c, childWiki, true);
            }
            else
            {
                Wiki parent = wiki.getParentWiki();
                Integer parentId = null;
                float wikiDisplay = wiki.getDisplayOrder();
                Wiki nextWiki = null;

                //if page being deleted is not at root, get id and display order of its parent
                if (null != parent)
                    parentId = parent.getRowId();

                //get page's siblings (children of its parent)
                List<Wiki> siblings = WikiSelectManager.getChildWikis(wiki.lookupContainer(), parentId);

                //find parent wiki page in sibling list, and determine its position (based on display order)
                int wikiPosition = 0;

                for (Wiki w : siblings)
                {
                    //hack: make sure we are working with the right kind of wiki object for comparison
                    if (w.getEntityId().equals(wiki.getEntityId()))
                    {
                        wikiPosition = siblings.indexOf(w);
                        break;
                    }
                }

                //get next sibling to parent
                if (wikiPosition < siblings.size() - 1)
                    nextWiki = siblings.get(wikiPosition + 1);

                //children need to fit between parent wiki and next wiki
                //increment child's order, starting with deleted page's order
                float reorder = wikiDisplay;

                for (Wiki child : children)
                {
                    child.setParent(parentId);
                    child.setDisplayOrder(reorder++);
                    updateWiki(user, child, null, false);
                }

                //if there are subsequent siblings, reorder them as well.
                if (null != nextWiki)
                {
                    //walk through siblings starting with page following parent
                    for (int i = wikiPosition + 1; i < siblings.size(); i++)
                    {
                        Wiki lowerSib = siblings.get(i);
                        lowerSib.setDisplayOrder(reorder++);
                        updateWiki(user, lowerSib, null, false);
                    }
                }
            }
        }
    }

    public void purgeContainer(Container c)
    {
        DbScope scope = comm.getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            new SqlExecutor(comm.getSchema()).execute("UPDATE " + comm.getTableInfoPages() + " SET PageVersionId = NULL WHERE Container = ?", c.getId());
            new SqlExecutor(comm.getSchema()).execute("DELETE FROM " + comm.getTableInfoPageVersions() + " WHERE PageEntityId IN (SELECT EntityId FROM " + comm.getTableInfoPages() + " WHERE Container = ?)", c.getId());
            deleteAliases(c, null);

            // Clear all wiki webpart properties that refer to this container. This includes wiki and wiki TOC
            // webparts in this and potentially other containers. #13937
            Portal.clearWebPartProperties("Wiki", c.getId());

            ContainerUtil.purgeTable(comm.getTableInfoPages(), c, null);

            transaction.commit();
        }

        WikiCache.uncache(c);
    }

    public FormattedHtml formatWiki(Container c, Wiki wiki, WikiVersion wikiversion, SubstitutionMode substitutionMode)
    {
        String hrefPrefix = wiki.getWikiURL(WikiController.PageAction.class, "").toString();
        String attachPrefix = null;

        if (null != wiki.getEntityId())
            attachPrefix = WikiController.getDownloadURL(wiki.lookupContainer(), wiki, "").getLocalURIString();

        // When rendering wikis, we want aliases to resolve to their titles as well, Issue 45497
        Map<String, String> nameTitleMap = WikiSelectManager.getNameAndAliasTitleMap(c);

        //get formatter specified for this version
        WikiRenderer w = wikiversion.getRenderer(substitutionMode, hrefPrefix, attachPrefix, nameTitleMap, wiki.getAttachments(), "Wiki '" + wiki.getName() + "' version " + wikiversion.getVersion() + " in " + wiki.getContainerPath());

        return w.format(wikiversion.getBody());
    }


    public static boolean wikiNameExists(Container c, String wikiname)
    {
        return WikiSelectManager.getWiki(c, wikiname) != null;
    }


    //copies a single wiki page
    public Wiki copyPage(User user, Container cSrc, Wiki srcPage, Container cDest, List<String> destPageNames,
                          Map<Integer, Integer> pageIdMap, boolean isCopyingHistory)
            throws IOException
    {
        //create new wiki page
        String srcName = srcPage.getName();
        String destName = srcName;

        int i = 1;

        while (containsCaseInsensitive(destName, destPageNames))
            destName = srcName.concat("" + i++);

        //new wiki page
        Wiki newWikiPage = new Wiki(cDest, destName);
        newWikiPage.setDisplayOrder(srcPage.getDisplayOrder());
        newWikiPage.setShowAttachments(srcPage.isShowAttachments());
        newWikiPage.setShouldIndex(srcPage.isShouldIndex());

        //look up parent page via map
        if (pageIdMap != null)
        {
            Integer destParentId = pageIdMap.get(srcPage.getParent());

            if (destParentId != null)
                newWikiPage.setParent(destParentId);
            else
                newWikiPage.setParent(null);
        }

        //get wiki & attachments
        Wiki wiki = WikiSelectManager.getWiki(cSrc, srcName);
        Collection<Attachment> attachments = wiki.getAttachments();
        List<AttachmentFile> files = getAttachmentService().getAttachmentFiles(wiki.getAttachmentParent(), attachments);
        Collection<String> aliases = WikiSelectManager.getAliases(cSrc, wiki.getRowId());

        final WikiVersion[] wikiVersions;

        if (!isCopyingHistory)
        {
            wikiVersions = new WikiVersion[] { srcPage.getLatestVersion() };
        }
        else
        {
            wikiVersions = WikiSelectManager.getAllVersions(wiki);
        }

        boolean firstVersion = true;
        for (WikiVersion wikiVersion : wikiVersions)
        {
            //new wiki version
            WikiVersion newWikiVersion = new WikiVersion(destName);
            newWikiVersion.setTitle(wikiVersion.getTitle());
            newWikiVersion.setBody(wikiVersion.getBody());
            newWikiVersion.setCreatedBy(wikiVersion.getCreatedBy());
            newWikiVersion.setCreated((wikiVersion.getCreated()));
            newWikiVersion.setRendererTypeEnum(wikiVersion.getRendererTypeEnum());

            if (firstVersion)
            {
                insertWiki(user, cDest, newWikiPage, newWikiVersion, files, isCopyingHistory, aliases);
                firstVersion = false;
            }
            else
            {
                updateWiki(user, newWikiPage, newWikiVersion, isCopyingHistory);
            }
        }

        //map source row id to dest row id
        if (pageIdMap != null)
        {
            pageIdMap.put(srcPage.getRowId(), newWikiPage.getRowId());
        }

        return newWikiPage;
    }

    private boolean containsCaseInsensitive(String str, List<String> list)
    {
        for (String s : list)
        {
            if (s.equalsIgnoreCase(str))
                return true;
        }
        return false;
    }


    public @Nullable String updateAttachments(User user, Wiki wiki, @Nullable List<String> deleteNames, @Nullable List<AttachmentFile> files)
    {
        AttachmentService attsvc = getAttachmentService();
        boolean changes = false;
        String message = null;
        AttachmentParent parent = wiki.getAttachmentParent();

        //delete the attachments requested
        if (null != deleteNames && !deleteNames.isEmpty())
        {
            for (String name : deleteNames)
            {
                attsvc.deleteAttachment(parent, name, user);
            }
            changes = true;
        }

        //add any files as attachments
        if (null != files && !files.isEmpty())
        {
            try
            {
                attsvc.addAttachments(parent, files, user);
            }
            catch (DuplicateFilenameException e)
            {
                //since this is now being called ajax style with just the files, we don't
                //really need to generate an error in this case. Just add a warning
                message = e.getMessage();
            }
            catch (IOException e)
            {
                message = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            changes = true;
        }

        if (changes)
        {
            touch(wiki);
            indexWiki(wiki);
        }

        return message;
    }


    //
    // Search
    //


    void unindexWiki(String entityId)
    {
        SearchService ss = getSearchService();
        String docid = "wiki:" + entityId;
        if (ss != null)
            ss.deleteResource(docid);
        // UNDONE attachment
    }
    

    void indexWiki(Wiki page)
    {
        if (!page.isShouldIndex())
        {
            unindexWiki(page.getEntityId());
            return;
        }
        Container c = ContainerService.get().getForId(page.getContainerId());
        SearchService ss = getSearchService();
        if (null != ss && c != null)
        {
            indexWikis(ss.defaultTask().getQueue(c, SearchService.PRIORITY.modified), null, page.getName());
        }
    }


    private void touch(Wiki wiki)
    {
        // CONSIDER: Table.touch()?
        new SqlExecutor(comm.getSchema()).execute("UPDATE " + comm.getTableInfoPages() + " SET LastIndexed = NULL, Modified=? WHERE Container = ? AND Name = ?", new Date(), wiki.getContainerId(), wiki.getName());
    }


    public void setLastIndexed(Container c, String name, long ms)
    {
        new SqlExecutor(comm.getSchema()).execute("UPDATE " + comm.getTableInfoPages() + " SET LastIndexed = ? WHERE Container = ? AND Name = ?", new Timestamp(ms), c, name);
    }


    public void indexWikis(SearchService.TaskIndexingQueue queue, @Nullable Date modifiedSince, @Nullable String wikiName)
    {
        // Use a Runnable to postpone construction of the MockViewContext; if we're bootstrapping then base server URL won't be ready.
        queue.addRunnable((q) -> {
            // Push a ViewContext onto the stack before indexing; wikis may need this to render embedded webparts
            try (StackResetter ignored = ViewContext.pushMockViewContext(User.getSearchUser(), q.getContainer(), new ActionURL()))
            {
                indexWikiContainerFast(q, modifiedSince, wikiName);
            }
        });
    }


    private void indexWikiContainerFast(SearchService.TaskIndexingQueue queue, @Nullable Date modifiedSince, @Nullable String wikiName)
    {
        LOG.debug("indexWikiContainerFast({})", wikiName);
        Container c = queue.getContainer();
        SQLFragment f = new SQLFragment();
        f.append("SELECT P.entityid, P.container, P.name, P.owner, P.createdby, P.created, P.modifiedby, P.modified, P.shouldindex,")
            .append("V.title, V.body, V.renderertype\n");
        f.append("FROM comm.pages P INNER JOIN comm.pageversions V ON P.entityid=V.pageentityid and P.pageversionid=V.rowid\n");
        f.append("WHERE P.container = ?");
        f.add(c);
        SQLFragment since = new SearchService.LastIndexedClause(comm.getTableInfoPages(), modifiedSince, "P").toSQLFragment(null, comm.getSqlDialect());

        if (!since.isEmpty())
        {
            f.append(" AND ").append(since);
        }
        if (null != wikiName)
        {
            f.append(" AND P.name = ?");
            f.add(wikiName);
        }

        HashMap<String, Wiki> ids = new HashMap<>();
        // AGGH wiki doesn't have a title!
        HashMap<String, String> titles = new HashMap<>();

        try (ResultSet rs = new SqlSelector(comm.getSchema(), f).getResultSet(false, false))
        {
            while (rs.next())
            {
                wikiName = rs.getString("name");
                assert null != wikiName;

                if (WikiTermsOfUseProvider.TERMS_OF_USE_WIKI_NAME.equals(wikiName))
                    continue;

                if (!rs.getBoolean("shouldindex"))
                {
                    LOG.debug("Wiki [{}] set to not index, skipping.", wikiName);
                    continue;
                }

                String entityId = rs.getString("entityid");
                assert null != entityId;

                LOG.debug("Indexing wiki {}:{}", wikiName, entityId);

                String wikiTitle = rs.getString("title");
                String keywords;
                if (null == wikiTitle)
                    keywords = wikiTitle = wikiName;
                else
                    keywords = wikiTitle + " " + wikiName;   // Always search on wiki title or name
                String body = rs.getString("body");
                if (null == body)
                    body = "";
                WikiRendererType rendererType = WikiRendererType.valueOf(rs.getString("renderertype"));

                Map<String, Object> props = new HashMap<>();
                props.put(SearchService.PROPERTY.title.toString(), wikiTitle);
                props.put(SearchService.PROPERTY.keywordsMed.toString(), keywords);

                try
                {
                    WikiWebdavProvider.WikiPageResource r = new RenderedWikiResource(c, wikiName, entityId, body, rendererType, props);
                    queue.addResource(r);
                }
                catch (Throwable t)
                {
                    // Log rendering exception and details about the culprit, but continue indexing wikis in this container
                    LOG.error("Could not render wiki \"{}\" in folder \"{}\"", wikiName, c.getPath());
                    ExceptionUtil.logExceptionToMothership(null, t);
                    continue;
                }

                if (Thread.interrupted())
                {
                    LOG.debug("Wiki indexing interrupted");
                    return;
                }

                Wiki parent = new Wiki();
                parent.setContainer(c.getId());
                parent.setEntityId(entityId);
                parent.setName(wikiName);
                ids.put(entityId, parent);
                titles.put(entityId, wikiTitle);
            }
        }
        catch (SQLException x)
        {
            LOG.error(x);
            throw new RuntimeSQLException(x);
        }

        // now attachments
        ActionURL pageUrl = new ActionURL(WikiController.PageAction.class, c);
        ActionURL downloadUrl = new ActionURL(WikiController.DownloadAction.class, c);

        if (!ids.isEmpty())
        {
            List<Pair<String,String>> list = getAttachmentService().listAttachmentsForIndexing(ids.keySet(), modifiedSince);

            for (Pair<String,String> pair : list)
            {
                String entityId = pair.first;
                String documentName = pair.second;
                Wiki parent = ids.get(entityId);
                AttachmentParent attachmentParent = parent.getAttachmentParent();

                ActionURL wikiUrl = pageUrl.clone().addParameter("name", parent.getName());
                ActionURL attachmentURL = downloadUrl.clone()
                        .replaceParameter("entityId",entityId)
                        .replaceParameter("name",documentName);
                // UNDONE: set title to make LuceneSearchServiceImpl work
                String displayTitle = "\"" + documentName + "\" attached to page \"" + titles.get(entityId) + "\"";
                WebdavResource attachmentRes = getAttachmentService().getDocumentResource(
                        new Path(entityId,documentName),
                        attachmentURL, displayTitle,
                        attachmentParent,
                        documentName, searchCategory);

                NavTree t = new NavTree("wiki page", wikiUrl);
                String nav = NavTree.toJS(Collections.singleton(t), null, false, true).toString();
                attachmentRes.getMutableProperties().put(SearchService.PROPERTY.navtrail.toString(), nav);
                queue.addResource(attachmentRes);
            }
        }
    }


    //
    // WikiService
    //

    /** Note: Does not handle the client dependencies declared by the wiki or any of its embedded webparts! */
    @Override
    public RenderedWiki getRenderedWiki(Container c, String name)
    {
        if (null == c || null == name)
            return null;

        try
        {
            Wiki wiki = WikiSelectManager.getWiki(c, name);
            if (null == wiki)
                return null;
            WikiVersion version = wiki.getLatestVersion();
            HtmlString html = version.getHtml(c, wiki);
            return new RenderedWiki(name, version.getTitle(), html, wiki.getEntityId());
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }
    }

    @Override
    public WikiMarkdown getWikiMarkdown(Container c, String name)
    {
        if (null == c || null == name)
            return null;

        try
        {
            Wiki wiki = WikiSelectManager.getWiki(c, name);
            if (null == wiki)
                return null;
            WikiVersion version = wiki.getLatestVersion();
            String body = version.getBody();
            String markdown = version.getRendererTypeEnum().bestAttemptConvertToMarkdown(null == body ? "" : body);
            return new WikiMarkdown(name, version.getTitle(), markdown, wiki.getEntityId());
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }
    }

    @Override
    public void insertWiki(User user, Container c, String name, String body, WikiRendererType renderType, String title)
    {
        Wiki wiki = new Wiki(c, name);
        WikiVersion wikiversion = new WikiVersion();
        wikiversion.setTitle(title);

        wikiversion.setBody(body);

        if (renderType == null)
            renderType = DEFAULT_WIKI_RENDERER_TYPE;

        wikiversion.setRendererTypeEnum(renderType);

        try
        {
            insertWiki(user, c, wiki, wikiversion, null, false, null);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public WebPartView<?> getView(Container c, String name, boolean contentOnly)
    {
        try
        {
            if (contentOnly)
            {
                HtmlString html = getHtml(c, name);
                return null == html ? null : new HtmlView(html);
            }
            Wiki wiki = WikiSelectManager.getWiki(c, name);
            if (null == wiki)
                return null;
            WikiVersion version = wiki.getLatestVersion();
            return new WikiView(wiki, version, true);
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }
    }

    @Override
    public WikiVersionsGrid getHistoryView(Container c, String name)
    {
        Wiki wiki = WikiSelectManager.getWiki(c, name);
        if (null == wiki)
            return null;
        WikiVersion version = wiki.getLatestVersion();
        return new WikiVersionsGrid(wiki, version, null);
    }

    @Override
    public List<String> getNames(Container c)
    {
        List<String> l = WikiSelectManager.getPageNames(c);
        return new ArrayList<>(l);
    }

    @Override
    public int populateVectorStore(Container container)
    {
        McpService mcp = McpService.get();
        if (null == mcp.getVectorStore())
            throw new NotFoundException("VectorStore not enabled.");

        ActionURL wikiBase = new ActionURL("wiki", "page", container);
        AtomicInteger count = new AtomicInteger();

        for (String name : getNames(container))
        {
            Wiki wiki = WikiSelectManager.getWiki(container, name);
            if (null == wiki)
                continue;
            WikiVersion version = wiki.getLatestVersion();
            if (null == version)
                continue;

            String body = version.getBody();
            String markdown = version.getRendererTypeEnum().bestAttemptConvertToMarkdown(null == body ? "" : body);
            List<String> path = getAncestorTitles(wiki);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("Content-Type", "text/markdown");
            metadata.put("filename", name + ".md");
            metadata.put("title", version.getTitle());
            metadata.put("source", wikiBase.clone().addParameter("name", name).getURIString());
            if (!path.isEmpty())
                metadata.put("path", path);

            VectorDocument doc = new VectorDocument(container.getId() + "/" + wiki.getEntityId(), markdown, metadata);
            try
            {
                mcp.addDocuments(List.of(doc));
                count.incrementAndGet();
            }
            catch (IllegalArgumentException x)
            {
                LogManager.getLogger(WikiManager.class).info(name, x);
            }
        }

        mcp.saveVectorStore();
        return count.get();
    }

    private List<String> getAncestorTitles(Wiki wiki)
    {
        List<String> titles = new ArrayList<>();
        Wiki current = wiki.getParentWiki();
        while (current != null)
        {
            WikiVersion version = current.getLatestVersion();
            titles.add(0, version != null ? version.getTitle() : current.getName());
            current = current.getParentWiki();
        }
        return titles;
    }

    @Override
    public void addWikiListener(WikiChangeListener listener)
    {
        listeners.add(listener);
    }

    @Override
    public void removeWikiListener(WikiChangeListener listener)
    {
        listeners.remove(listener);
    }

    @Override
    public TableInfo getRendererTypeTable(User user, Container container)
    {
        return QueryService.get().getUserSchema(user, container, WikiSchema.SCHEMA_NAME).getTable(WikiSchema.RENDERER_TYPE_TABLE_NAME, null);
    }

    private void fireWikiCreated(User user, Container c, String name)
    {
        for (WikiChangeListener l : listeners)
            l.wikiCreated(user, c, name);
    }

    private void fireWikiChanged(User user, Container c, String name)
    {
        for (WikiChangeListener l : listeners)
            l.wikiChanged(user, c, name);
    }

    private void fireWikiDeleted(User user, Container c, String name)
    {
        for (WikiChangeListener l : listeners)
            l.wikiDeleted(user, c, name);
    }

    @Override
    public String getContent(Container c, String wikiName)
    {
        Wiki wiki = WikiSelectManager.getWiki(c, wikiName);
        if (null == wiki)
            return null;
        WikiVersion version = wiki.getLatestVersion();
        if (version != null)
            return version.getBody();

        return null;
    }

    @Override
    public List<String> getAllContent(Container c, String wikiName)
    {
        Wiki wiki = WikiSelectManager.getWiki(c, wikiName);

        if (null == wiki)
            return null;

        List<String> allContent = new ArrayList<>();

        for (WikiVersion version : WikiSelectManager.getAllVersions(wiki))
            allContent.add(version.getBody());

        return allContent;
    }

    @Override
    public boolean updateContent(Container c, User user, String wikiName, String content, @Nullable Integer newVersionThreshold)
    {
        if (content != null)
        {
            Wiki wiki = WikiSelectManager.getWiki(c, wikiName);
            if (wiki != null)
            {
                WikiVersion version = wiki.getLatestVersion();
                if (version != null)
                {
                    // only update if something has changed
                    if (!content.equals(version.getBody()))
                    {
                        version.setBody(content);
                        boolean createNewVersion = true;

                        if (newVersionThreshold != null)
                        {
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(new Date());
                            cal.add(Calendar.MILLISECOND, - newVersionThreshold);

                            // only update if the wiki was updated outside of the specified time window
                            createNewVersion = wiki.getModified().before(cal.getTime());
                        }
                        return updateWiki(user, wiki, version, false, createNewVersion);
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void deleteWiki(Container c, User user, String wikiName, boolean deleteSubtree) throws SQLException
    {
        Wiki wiki = WikiSelectManager.getWiki(c, wikiName);
        if (wiki != null)
        {
            deleteWiki(user, c, wiki, true);
        }
    }

    @Override
    public @Nullable AttachmentParent getAttachmentParent(Container c, User user, String wikiName)
    {
        Wiki wiki = WikiSelectManager.getWiki(c, wikiName);
        if (wiki != null)
            return wiki.getAttachmentParent();
        return null;
    }

    @Override
    public @Nullable String updateAttachments(Container c, User user, String wikiName, @Nullable List<AttachmentFile> attachmentFiles, @Nullable List<String> deleteAttachmentNames)
    {
        Wiki wiki = WikiSelectManager.getWiki(c, wikiName);
        if (wiki != null)
            return updateAttachments(user, wiki, deleteAttachmentNames, attachmentFiles);
        return null;
    }

    @Override
    public AttachmentParentType getAttachmentType()
    {
        return WikiType.get();
    }

    public static class TestCase extends Assert
    {
        WikiManager _m = null;

        @Before
        public void setup()
        {
            _m = new WikiManager();
        }
        
        @Test
        public void testSchema()
        {
            assertNotNull("couldn't find table Pages", _m.comm.getTableInfoPages());
            assertNotNull(_m.comm.getTableInfoPages().getColumn("Container"));
            assertNotNull(_m.comm.getTableInfoPages().getColumn("EntityId"));
            assertNotNull(_m.comm.getTableInfoPages().getColumn("Name"));

            assertNotNull("couldn't find table PageVersions", _m.comm.getTableInfoPageVersions());
            assertNotNull(_m.comm.getTableInfoPageVersions().getColumn("PageEntityId"));
            assertNotNull(_m.comm.getTableInfoPageVersions().getColumn("Title"));
            assertNotNull(_m.comm.getTableInfoPageVersions().getColumn("Body"));
            assertNotNull(_m.comm.getTableInfoPageVersions().getColumn("Version"));
        }


        private void purgePages(Container c, boolean verifyEmpty)
        {
            SqlExecutor executor = new SqlExecutor(_m.comm.getSchema());

            // TODO this belongs in attachment service!
            String deleteDocuments = "DELETE FROM " + _m.core.getTableInfoDocuments() + " WHERE Container = ? AND Parent IN (SELECT EntityId FROM " + _m.comm.getTableInfoPages() + " WHERE Container = ?)";
            int docs = executor.execute(deleteDocuments, c, c);

            String updatePages = "UPDATE " + _m.comm.getTableInfoPages() + " SET PageVersionId = null WHERE Container = ?";
            executor.execute(updatePages, c);

            String deletePageVersions = "DELETE FROM " + _m.comm.getTableInfoPageVersions() + " WHERE PageEntityId IN (SELECT EntityId FROM " + _m.comm.getTableInfoPages() + " WHERE Container = ?)";
            int pageVersions = executor.execute(deletePageVersions, c);

            String deletePages = "DELETE FROM " + _m.comm.getTableInfoPages() + " WHERE Container = ?";
            int pages = executor.execute(deletePages, c);

            if (verifyEmpty)
            {
                assertEquals(0, docs);
                assertEquals(0, pageVersions);
                assertEquals(0, pages);
            }
        }


        @Test
        public void testWiki() throws IOException, SQLException
        {
            TestContext context = TestContext.get();

            User user = context.getUser();
            assertNotNull("login before running this test", user);
            assertFalse("login before running this test", user.isGuest());

            Container c = JunitUtil.getTestContainer();

            purgePages(c, false);

            //
            // CREATE
            //
            Wiki wikiA = new Wiki(c, "pageA");
            WikiVersion wikiversion = new WikiVersion();
            wikiversion.setTitle("Topic A");
            wikiversion.setBody("[pageA]");

            _m.insertWiki(user, c, wikiA, wikiversion, null, false, null);

            // verify objects
            wikiA = WikiSelectManager.getWikiFromDatabase(c, "pageA");
            wikiversion = WikiVersionCache.getVersion(c, wikiA.getPageVersionId());
            assertEquals("Topic A", wikiversion.getTitle());

            assertNull(WikiSelectManager.getWikiFromDatabase(c, "pageNA"));

            //
            // DELETE
            //
            _m.deleteWiki(user, c, wikiA, false);

            // verify
            assertNull(WikiSelectManager.getWikiFromDatabase(c, "pageA"));

            purgePages(c, true);
        }
    }
}
