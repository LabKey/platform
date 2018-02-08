/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
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
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.search.SearchService;
import org.labkey.api.search.SearchService.IndexTask;
import org.labkey.api.security.User;
import org.labkey.api.security.WikiTermsOfUseProvider;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewContext.StackResetter;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.wiki.FormattedHtml;
import org.labkey.api.wiki.MacroProvider;
import org.labkey.api.wiki.WikiChangeListener;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.api.wiki.WikiPartFactory;
import org.labkey.wiki.model.RadeoxMacroProxy;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiVersion;
import org.labkey.wiki.model.WikiVersionsGrid;
import org.labkey.wiki.model.WikiView;
import org.labkey.wiki.renderer.HtmlRenderer;
import org.labkey.wiki.renderer.MarkdownRenderer;
import org.labkey.wiki.renderer.PlainTextRenderer;
import org.labkey.wiki.renderer.RadeoxRenderer;
import org.radeox.macro.MacroRepository;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * User: mbellew
 * Date: Mar 10, 2005
 * Time: 1:27:36 PM
 */
public class WikiManager implements WikiService
{
    private static final Logger LOG = Logger.getLogger(WikiManager.class);
    private static final WikiManager _instance = new WikiManager();

    public static WikiManager get()
    {
        return _instance;
    }

    public static final SearchService.SearchCategory searchCategory = new SearchService.SearchCategory("wiki", "Wiki Pages");

    /* service/schema dependencies */
    private CommSchema comm = CommSchema.getInstance();
    private CoreSchema core = CoreSchema.getInstance();

    private static final List<WikiChangeListener> listeners = new CopyOnWriteArrayList<>();
    private static List<WikiPartFactory> _wikiPartFactories;

    private WikiManager()
    {
        LOG.debug("WikiManager instantiated");
    }

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

    DiscussionService getDiscussionService()
    {
        return DiscussionService.get();
    }

    ContainerService getContainerService()
    {
        return ServiceRegistry.get(ContainerService.class);
    }

    // Used to verify that entityId is a wiki and belongs in the specified container
    public Wiki getWikiByEntityId(Container c, String entityId)
    {
        if (null == c || c.getId().length() == 0 || null == entityId || entityId.length() == 0)
            return null;

        return new TableSelector(comm.getTableInfoPages(),
                SimpleFilter.createContainerFilter(c).addCondition(FieldKey.fromParts("EntityId"), entityId),
                null).getObject(Wiki.class);
    }


    public void insertWiki(User user, Container c, Wiki wikiInsert, WikiVersion wikiversion, List<AttachmentFile> files)
            throws SQLException, IOException
    {
        DbScope scope = comm.getSchema().getScope();

        //transact insert of wiki page, new version, and any attachments
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            wikiInsert.beforeInsert(user, c.getId());
            wikiInsert.setPageVersionId(null);
            LOG.debug("Table.insert() for wiki " + wikiInsert.getName());
            Table.insert(user, comm.getTableInfoPages(), wikiInsert);
            String entityId = wikiInsert.getEntityId();

            //insert initial version for this page
            wikiversion.setPageEntityId(entityId);
            wikiversion.setCreated(wikiInsert.getCreated());
            wikiversion.setCreatedBy(wikiInsert.getCreatedBy());
            wikiversion.setVersion(1);
            LOG.debug("Table.insert() for wiki version " + wikiInsert.getName());
            Table.insert(user, comm.getTableInfoPageVersions(), wikiversion);

            //get rowid for newly inserted version
            wikiversion = WikiSelectManager.getVersion(wikiInsert, 1);

            //store initial version reference in Pages table
            wikiInsert.setPageVersionId(wikiversion.getRowId());
            Table.update(user, comm.getTableInfoPages(), wikiInsert, wikiInsert.getEntityId());

            getAttachmentService().addAttachments(wikiInsert.getAttachmentParent(), files, user);

            transaction.commit();
        }
        finally
        {
            WikiCache.uncache(c, wikiInsert, true);

            LOG.debug("indexWiki() for " + wikiInsert.getName());
            indexWiki(wikiInsert);
        }

        if (wikiInsert.getName() != null)
            fireWikiCreated(user, c, wikiInsert.getName());
    }


    public boolean updateWiki(User user, Wiki wikiNew, WikiVersion versionNew)
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
                    || wikiOld.getParent() != wikiNew.getParent()
                    || wikiOld.getDisplayOrder() != wikiNew.getDisplayOrder()
                    || (null != versionNew && !oldTitle.equals(versionNew.getTitle()));

            //update Pages table
            //UNDONE: should take RowId, not EntityId
            Table.update(user, comm.getTableInfoPages(), wikiNew, wikiNew.getEntityId());

            if (versionNew != null)
            {
                String entityId = wikiNew.getEntityId();
                versionNew.setPageEntityId(entityId);
                versionNew.setCreated(new Date(System.currentTimeMillis()));
                versionNew.setCreatedBy(user.getUserId());
                //get version number for new version
                versionNew.setVersion(WikiSelectManager.getNextVersionNumber(wikiNew));
                //insert initial version for this page
                versionNew = Table.insert(user, comm.getTableInfoPageVersions(), versionNew);

                //update version reference in Pages table.
                wikiNew.setPageVersionId(versionNew.getRowId());
                Table.update(user, comm.getTableInfoPages(), wikiNew, wikiNew.getEntityId());
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

            getAttachmentService().deleteAttachments(wiki.getAttachmentParent());

            if (null != getDiscussionService())
                getDiscussionService().deleteDiscussions(c, user, wiki.getEntityId());

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

        if (children.size() > 0)
        {
            if(isDeletingSubtree)
            {
                for(Wiki childWiki : children)
                    deleteWiki(user, c, childWiki, true);
            }
            else
            {
                Wiki parent = wiki.getParentWiki();
                int parentId = -1;
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
                    updateWiki(user, child, null);
                }

                //if there are subsequent siblings, reorder them as well.
                if (null != nextWiki)
                {
                    //walk through siblings starting with page following parent
                    for (int i = wikiPosition + 1; i < siblings.size(); i++)
                    {
                        Wiki lowerSib = siblings.get(i);
                        lowerSib.setDisplayOrder(reorder++);
                        updateWiki(user, lowerSib, null);
                    }
                }
            }
        }
    }


    public void purgeContainer(Container c)
    {
        WikiCache.uncache(c);

        DbScope scope = comm.getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            new SqlExecutor(comm.getSchema()).execute("UPDATE " + comm.getTableInfoPages() + " SET PageVersionId = NULL WHERE Container = ?", c.getId());
            new SqlExecutor(comm.getSchema()).execute("DELETE FROM " + comm.getTableInfoPageVersions() + " WHERE PageEntityId IN (SELECT EntityId FROM " + comm.getTableInfoPages() + " WHERE Container = ?)", c.getId());

            // Clear all wiki webpart properties that refer to this container. This includes wiki and wiki TOC
            // webparts in this and potentially other containers. #13937
            Portal.clearWebPartProperties("Wiki", c.getId());

            ContainerUtil.purgeTable(comm.getTableInfoPages(), c, null);

            transaction.commit();
        }
    }


    public int purge()
    {
        return ContainerUtil.purgeTable(comm.getTableInfoPages(), null);
    }


    public FormattedHtml formatWiki(Container c, Wiki wiki, WikiVersion wikiversion)
    {
        String hrefPrefix = wiki.getWikiURL(WikiController.PageAction.class, "").toString();
        String attachPrefix = null;

        if (null != wiki.getEntityId())
            attachPrefix = WikiController.getDownloadURL(wiki.lookupContainer(), wiki, "").getLocalURIString();

        Map<String, String> nameTitleMap = WikiSelectManager.getNameTitleMap(c);

        //get formatter specified for this version
        WikiRenderer w = wikiversion.getRenderer(hrefPrefix, attachPrefix, nameTitleMap, wiki.getAttachments());

        return w.format(wikiversion.getBody());
    }


    public static boolean wikiNameExists(Container c, String wikiname)
    {
        return WikiSelectManager.getWiki(c, wikiname) != null;
    }


    //copies a single wiki page
    public Wiki copyPage(User user, Container cSrc, Wiki srcPage, Container cDest, List<String> destPageNames,
                          Map<Integer, Integer> pageIdMap, boolean isCopyingHistory)
            throws SQLException, IOException
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
                newWikiPage.setParent(-1);
        }

        //get wiki & attachments
        Wiki wiki = WikiSelectManager.getWiki(cSrc, srcName);
        Collection<Attachment> attachments = wiki.getAttachments();
        List<AttachmentFile> files = getAttachmentService().getAttachmentFiles(wiki.getAttachmentParent(), attachments);


        final WikiVersion[] wikiVersions;

        if(!isCopyingHistory)
        {
            wikiVersions = new WikiVersion[] { srcPage.getLatestVersion() };
        }
        else
        {
            wikiVersions = WikiSelectManager.getAllVersions(wiki);
        }

        boolean firstVersion = true;
        for(WikiVersion wikiVersion : wikiVersions)
        {
            //new wiki version
            WikiVersion newWikiVersion = new WikiVersion(destName);
            newWikiVersion.setTitle(wikiVersion.getTitle());
            newWikiVersion.setBody(wikiVersion.getBody());
            newWikiVersion.setRendererTypeEnum(wikiVersion.getRendererTypeEnum());

            if(firstVersion)
            {
                insertWiki(user, cDest, newWikiPage, newWikiVersion, files);
                firstVersion = false;
            }
            else
            {
                updateWiki(user, newWikiPage, newWikiVersion);
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


    public String updateAttachments(User user, Wiki wiki, List<String> deleteNames, List<AttachmentFile> files)
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
        if (null != files && files.size() > 0)
        {
            try
            {
                attsvc.addAttachments(parent, files, user);
            }
            catch (AttachmentService.DuplicateFilenameException e)
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
        Container c = getContainerService().getForId(page.getContainerId());
        if (null != c)
            indexWikis(null, c, null, page.getName());
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

    
    public void indexWikis(@Nullable IndexTask task, @NotNull Container c, @Nullable Date modifiedSince, @Nullable String wikiName)
    {
        final SearchService ss = getSearchService();
        if (null == ss)
            return;

        if (null == task)
            task = ss.defaultTask();

        final IndexTask fTask = task;

        // Use a Runnable to postpone construction of the MockViewContext; if we're bootstrapping then base server URL won't be ready.
        fTask.addRunnable(() -> {
            // Push a ViewContext onto the stack before indexing; wikis may need this to render embedded webparts
            try (StackResetter ignored = ViewContext.pushMockViewContext(User.getSearchUser(), c, new ActionURL()))
            {
                indexWikiContainerFast(fTask, c, modifiedSince, wikiName);
            }
        }, SearchService.PRIORITY.item);
    }


    private void indexWikiContainerFast(@NotNull IndexTask task, @NotNull Container c, @Nullable Date modifiedSince, @Nullable String wikiName)
    {
        LOG.debug("indexWikiContainerFast(" + wikiName + ")");

        SQLFragment f = new SQLFragment();
        f.append("SELECT P.entityid, P.container, P.name, owner$.searchterms as owner, createdby$.searchterms as createdby, P.created, modifiedby$.searchterms as modifiedby, P.modified,")
            .append("V.title, V.body, V.renderertype\n");
        f.append("FROM comm.pages P INNER JOIN comm.pageversions V ON P.entityid=V.pageentityid and P.pageversionid=V.rowid\n")
            .append("LEFT OUTER JOIN core.usersearchterms AS owner$ ON P.createdby = owner$.userid\n")
            .append("LEFT OUTER JOIN core.usersearchterms AS createdby$ ON P.createdby = createdby$.userid\n")
            .append("LEFT OUTER JOIN core.usersearchterms AS modifiedby$ ON P.createdby = modifiedby$.userid\n");
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

                String entityId = rs.getString("entityid");
                assert null != entityId;

                LOG.debug("Indexing wiki " + wikiName + ":" + entityId);

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
                    task.addResource(r, SearchService.PRIORITY.item);
                }
                catch (Throwable t)
                {
                    // Log rendering exception and details about the culprit, but continue indexing wikis in this container
                    LOG.error("Could not render wiki \"" + wikiName + "\" in folder \"" + c.getPath() + "\"");
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
                String nav = NavTree.toJS(Collections.singleton(t), null, false).toString();
                attachmentRes.getMutableProperties().put(SearchService.PROPERTY.navtrail.toString(), nav);
                task.addResource(attachmentRes, SearchService.PRIORITY.item);
            }
        }
    }


    //
    // WikiService
    //

    public static WikiRendererType DEFAULT_WIKI_RENDERER_TYPE = WikiRendererType.HTML;
    public static WikiRendererType DEFAULT_MESSAGE_RENDERER_TYPE = WikiRendererType.MARKDOWN;

    private Map<String, MacroProvider> providers = new HashMap<>();

    public String getHtml(Container c, String name)
    {
        if (null == c || null == name)
            return null;

        try
        {
            Wiki wiki = WikiSelectManager.getWiki(c, name);
            if (null == wiki)
                return null;
            WikiVersion version = wiki.getLatestVersion();
            return version.getHtml(c, wiki);
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
            renderType = getDefaultWikiRendererType();

        wikiversion.setRendererTypeEnum(renderType);

        try
        {
            insertWiki(user, c, wiki, wikiversion, null);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void registerMacroProvider(String name, MacroProvider provider)
    {
        providers.put(name, provider);
        MacroRepository repository = MacroRepository.getInstance();
        repository.put(name, new RadeoxMacroProxy(name, provider));

    }

    //Package
    MacroProvider getMacroProvider(String name)
    {
        return providers.get(name);
    }

    public WebPartView getView(Container c, String name, boolean contentOnly)
    {
        try
        {
            if (contentOnly)
            {
                String html = getHtml(c, name);
                return null == html ? null : new HtmlView(html);
            }
            Wiki wiki = WikiSelectManager.getWiki(c, new String(name));
            if (null == wiki)
                return null;
            WikiVersion version = wiki.getLatestVersion();
            WikiView view = new WikiView(wiki, version, true);
            return view;
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }
    }

    public WebPartView getHistoryView(Container c, String name)
    {
        Wiki wiki = WikiSelectManager.getWiki(c, name);
        if (null == wiki)
            return null;
        WikiVersion version = wiki.getLatestVersion();
        WikiVersionsGrid view = new WikiVersionsGrid(wiki, version, null);
        return view;
    }

    @Override
    public WikiRendererType getDefaultWikiRendererType()
    {
        return DEFAULT_WIKI_RENDERER_TYPE;
    }

    @Override
    public WikiRendererType getDefaultMessageRendererType()
    {
        return DEFAULT_MESSAGE_RENDERER_TYPE;
    }

    @Override
    public String getFormattedHtml(WikiRendererType rendererType, String source)
    {
        return getFormattedHtml(rendererType, source, null, null);
    }

    @Override
    public String getFormattedHtml(WikiRendererType rendererType, String source, @Nullable String attachPrefix, @Nullable Collection<? extends Attachment> attachments)
    {
        return WIKI_PREFIX + getRenderer(rendererType, null, attachPrefix, null, attachments).format(source).getHtml() + WIKI_SUFFIX;
    }

    public WikiRenderer getRenderer(WikiRendererType rendererType, String hrefPrefix,
                                    String attachPrefix, Map<String, String> nameTitleMap,
                                    Collection<? extends Attachment> attachments)
    {
        WikiRenderer renderer;

        switch (rendererType)
        {
            case RADEOX:
                renderer = new RadeoxRenderer(hrefPrefix, attachPrefix, nameTitleMap, attachments);
                break;
            case HTML:
                renderer = new HtmlRenderer(hrefPrefix, attachPrefix, nameTitleMap, attachments);
                break;
            case TEXT_WITH_LINKS:
                renderer = new PlainTextRenderer();
                break;
            case MARKDOWN:
                renderer = new MarkdownRenderer(hrefPrefix, attachPrefix, nameTitleMap, attachments);
                break;
            default:
                renderer = new RadeoxRenderer(null, attachPrefix, null, attachments);
        }

        return renderer;
    }


    public List<String> getNames(Container c)
    {
        List<String> l = WikiSelectManager.getPageNames(c);
        return new ArrayList<>(l);
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


        private void purgePages(Container c, boolean verifyEmpty) throws SQLException
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
        public void testWiki()
                throws IOException, SQLException, ServletException, AttachmentService.DuplicateFilenameException
        {
            TestContext context = TestContext.get();

            User user = context.getUser();
            assertTrue("login before running this test", null != user);
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

            _m.insertWiki(user, c, wikiA, wikiversion, null);

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
