/*
 * Copyright (c) 2005-2010 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.HString;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.wiki.FormattedHtml;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRenderer.WikiLinkable;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.wiki.WikiCache.WikiCacheLoader;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiVersion;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * User: mbellew
 * Date: Mar 10, 2005
 * Time: 1:27:36 PM
 */
public class WikiManager
{
    private static final Logger LOG = Logger.getLogger(WikiManager.class);

    public static final SearchService.SearchCategory searchCategory = new SearchService.SearchCategory("wiki", "Wiki Pages");

    private static CommSchema comm = CommSchema.getInstance();
    private static CoreSchema core = CoreSchema.getInstance();

    private static final int LATEST = -1;

//    private static SessionFactory _sessionFactory = DataSourceSessionFactory.create(_schema,
//            new Class[]{Wiki.class, Attachment.class},
//            CacheMode.NORMAL);


    public static class WikiAndVersion extends Pair<Wiki, WikiVersion>
    {
        public WikiAndVersion(Wiki wiki, WikiVersion version)
        {
            super(wiki, version);
        }

        public Wiki getWiki()
        {
            return getKey();
        }

        public WikiVersion getWikiVersion()
        {
            return getValue();
        }
    }


    private WikiManager()
    {
    }

    //does not include attachments
    public static List<Wiki> getWikisByParentId(String containerId, int parentRowId)
    {
        SimpleFilter filter = new SimpleFilter("container", containerId);
        filter.addCondition("Parent", parentRowId);
        try
        {
            Wiki[] wikis = Table.select(comm.getTableInfoPages(),
                    Table.ALL_COLUMNS,
                    filter,
                    new Sort("DisplayOrder,RowId"), Wiki.class);
            return Arrays.asList(wikis);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    private static void buildDescendentList(List<Wiki> list, Wiki page, boolean deep) throws SQLException
    {
        List<Wiki> children = getWikisByParentId(page.getContainerId(), page.getRowId());
        list.addAll(children);
        if (deep)
        {
            for (Wiki child : children)
                buildDescendentList(list, child, deep);
        }
    }

    public static List<Wiki> getDescendents(Wiki page, boolean deep) throws SQLException
    {
        List<Wiki> descendents = new ArrayList<Wiki>();
        buildDescendentList(descendents, page, deep);
        return descendents;
    }


    // Used to verify that entityId is a wiki and belongs in the specified container
    public static Wiki getWikiByEntityId(Container c, String entityId) throws SQLException
    {
        if(null == c || c.getId().length() == 0 || null == entityId || entityId.length() == 0)
            return null;

        Wiki[] wikis = Table.select(comm.getTableInfoPages(),
                Table.ALL_COLUMNS,
                new SimpleFilter("Container", c.getId()).addCondition("EntityId", entityId),
                null, Wiki.class);
        if (0 == wikis.length)
            return null;
        else
            return wikis[0];
    }

    // CONSIDER: move caching and wiki processing into this layer...
    //get wiki with attachments
    private static Wiki getWikiByName(Container c, HString name)
    {
        try
        {
            if (name == null)
                return null;

            Wiki[] wikis = Table.select(comm.getTableInfoPages(),
                    Table.ALL_COLUMNS,
                    new SimpleFilter("container", c.getId()).addCondition("name", name),
                    null, Wiki.class);

            if (0 == wikis.length)
            {
                //Didn't find it with case-sensitive lookup, try case-sensitive (in case the
                //underlying database is case sensitive)
                //Bug 2225
                wikis = Table.select(comm.getTableInfoPages(),
                        Table.ALL_COLUMNS,
                        new SimpleFilter("container", c.getId()).addWhereClause("LOWER(name) = LOWER(?)", new Object[] { name }),
                        null, Wiki.class);

                if (0 == wikis.length)
                    return null;
            }

            return wikis[0];
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static boolean insertWiki(User user, Container c, Wiki wikiInsert, WikiVersion wikiversion, List<AttachmentFile> files)
            throws SQLException, IOException, AttachmentService.DuplicateFilenameException
    {
        DbScope scope = comm.getSchema().getScope();
        try
        {
            scope.beginTransaction();

            //transact insert of wiki page, new version, and any attachments
            wikiInsert.beforeInsert(user, c.getId());
            wikiInsert.setPageVersionId(null);
            Table.insert(user, comm.getTableInfoPages(), wikiInsert);
            String entityId = wikiInsert.getEntityId();

            //insert initial version for this page
            wikiversion.setPageEntityId(entityId);
            wikiversion.setCreated(wikiInsert.getCreated());
            wikiversion.setCreatedBy(wikiInsert.getCreatedBy());
            wikiversion.setVersion(1);
            Table.insert(user, comm.getTableInfoPageVersions(), wikiversion);

            //get rowid for newly inserted version
            wikiversion = getVersion(wikiInsert, 1);

            //store initial version reference in Pages table
            wikiInsert.setPageVersionId(wikiversion.getRowId());
            Table.update(user, comm.getTableInfoPages(), wikiInsert, wikiInsert.getEntityId());

            AttachmentService.get().addAttachments(user, wikiInsert, files);

            scope.commitTransaction();
        }
        finally
        {
            if (scope != null)
                scope.closeConnection();

            WikiCache.uncache(c, wikiInsert, true);

            indexWiki(wikiInsert);
        }
        return true;
    }


    public static boolean updateWiki(User user, Wiki wikiNew, WikiVersion versionNew)
            throws SQLException
    {
        DbScope scope = comm.getSchema().getScope();
        Container c = wikiNew.lookupContainer();
        boolean uncacheAll = true;

        try
        {
            //transact wiki update and version insert
            scope.beginTransaction();

            //if name, title, parent, & sort order are all still the same,
            //we don't need to uncache all wikis--only the wiki being updated
            //NOTE: getWikiByEntityId does not use the cache, so we'll get a fresh copy from the database
            Wiki wikiOld = getWikiByEntityId(c, wikiNew.getEntityId());
            WikiVersion versionOld = wikiOld.latestVersion();
            String oldTitle = StringUtils.trimToEmpty(versionOld.getTitle().getSource());
            
            uncacheAll = !wikiOld.getName().equals(wikiNew.getName())
                    || wikiOld.getParent() != wikiNew.getParent()
                    || wikiOld.getDisplayOrder() != wikiNew.getDisplayOrder()
                    || (null != versionNew && !oldTitle.equals(versionNew.getTitle().getSource()));

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
                versionNew.setVersion(getNextVersionNumber(wikiNew));
                //insert initial version for this page
                versionNew = Table.insert(user, comm.getTableInfoPageVersions(), versionNew);

                //update version reference in Pages table.
                wikiNew.setPageVersionId(versionNew.getRowId());
                Table.update(user, comm.getTableInfoPages(), wikiNew, wikiNew.getEntityId());
            }
            scope.commitTransaction();
        }
        finally
        {
            if (scope != null)
                scope.closeConnection();

            WikiCache.uncache(c, wikiNew, uncacheAll);

            indexWiki(wikiNew);
        }
        return true;
    }



    public static void deleteWiki(User user, Container c, Wiki wiki) throws SQLException
    {
        //shift children to new parent
        reparent(user, wiki);

        DbScope scope = comm.getSchema().getScope();

        try
        {
            //transact deletion of wiki, version, attachments, and discussions
            scope.beginTransaction();

            wiki.setPageVersionId(null);
            Table.update(user, comm.getTableInfoPages(), wiki, wiki.getEntityId());
            Table.delete(comm.getTableInfoPageVersions(),
                    new SimpleFilter("pageentityId", wiki.getEntityId()));
            Table.delete(comm.getTableInfoPages(),
                    new SimpleFilter("entityId", wiki.getEntityId()));

            AttachmentService.get().deleteAttachments(wiki);

//            DiscussionService.get().unlinkDiscussions(c, wiki.getEntityId(), user);
            DiscussionService.get().deleteDiscussions(c, wiki.getEntityId(), user);

            scope.commitTransaction();
        }
        finally
        {
            if (scope != null)
                scope.closeConnection();

            unindexWiki(wiki.getEntityId());
        }

        WikiCache.uncache(c, wiki, true);
    }


    private static void reparent(User user, Wiki wiki) throws SQLException
    {
        //shift any children upward so they are not orphaned

        //get page's children
        List<Wiki> children = wiki.getChildren();

        if (children.size() > 0)
        {
            Wiki parent = wiki.getParentWiki();
            int parentId = -1;
            float wikiDisplay = wiki.getDisplayOrder();
            Wiki nextWiki = null;

            //if page being deleted is not at root, get id and display order of its parent
            if (null != parent)
                parentId = parent.getRowId();

            //get pages's siblings (children of its parent)
            List<Wiki> siblings = getWikisByParentId(wiki.getContainerId(), parentId);

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
            if(wikiPosition < siblings.size() - 1)
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


    public static long getWikiCount(Container c)
            throws SQLException
    {
        return Table.executeSingleton(comm.getSchema(), "SELECT COUNT(*) FROM " + comm.getTableInfoPages() + " WHERE Container = ?", new Object[]{c.getId()}, Long.class);
    }


    public static void purgeContainer(Container c) throws SQLException
    {
        WikiCache.uncache(c);

        DbScope scope = comm.getSchema().getScope();
        boolean startTransaction = !scope.isTransactionActive();
        try
        {
            if (startTransaction)
            {
                scope.beginTransaction();
            }
            Object[] params = { c.getId() };
            Table.execute(comm.getSchema(), "UPDATE " + comm.getTableInfoPages() + " SET PageVersionId = NULL WHERE Container = ?", params);
            Table.execute(comm.getSchema(), "DELETE FROM " + comm.getTableInfoPageVersions() + " WHERE PageEntityId IN (SELECT EntityId FROM " + comm.getTableInfoPages() + " WHERE Container = ?)", params);

            //delete stored web part information for this container (e.g., page to display in wiki web part)
            Table.execute(Portal.getSchema(),
                    "UPDATE " + Portal.getTableInfoPortalWebParts() + " SET Properties = NULL WHERE (Name LIKE '%Wiki%') AND Properties LIKE '%" + c.getId() + "%'",
                    null);

            ContainerUtil.purgeTable(comm.getTableInfoPages(), c, null);

            if (startTransaction)
            {
                scope.commitTransaction();
            }
        }
        finally
        {
            if (startTransaction)
                scope.closeConnection();
        }
    }


    public static int purge() throws SQLException
    {
        return ContainerUtil.purgeTable(comm.getTableInfoPages(), null);
    }


    private static Map<HString, Wiki> generatePageMap(Container c) throws SQLException
    {
        Map<HString, Wiki> tree = new TreeMap<HString, Wiki>();
        List<Wiki> l = getPageList(c);
        for (Wiki wiki : l)
        {
            tree.put(wiki.getName(), wiki);
        }
        return tree;
    }

    private static void addAllChildren(List<Wiki> pages, Wiki current)
    {
        pages.add(current);
        if (current.getChildren() != null)
        {
            for (Wiki page : current.getChildren())
                addAllChildren(pages, page);
        }
    }

    // includes depth
    public static List<Wiki> getSubTreePageList(Container c, Wiki parentPage) throws SQLException
    {
        List<Wiki> pageList = new ArrayList<Wiki>();
        addAllChildren(pageList, parentPage);

        return pageList;
    }

    public static List<HString> getWikiNameList(Container c) throws SQLException
    {
        List<Wiki> pageList = getPageList(c);
        List<HString> nameList = new ArrayList<HString>();

        for (Wiki page : pageList)
            nameList.add(page.getName());

        return nameList;
    }


    public static Wiki getWikiByRowId(Container c, int rowId)
    {
        List<Wiki> pages = getPageList(c);
        for (Wiki page : pages)
        {
            if (page.getRowId() == rowId)
                return page;
        }
        return null;
    }

    public static Wiki getWiki(Container c, HString name)
    {
        return getWiki(c, name, false);
    }


    //get wiki with specified version
    public static Wiki getWiki(Container c, HString name, boolean forceRefresh)
    {
        WikiAndVersion wikipair = getLatestWikiAndVersion(c, name, forceRefresh);
        if (null == wikipair)
            return null;
        return wikipair.getWiki();
    }


    public static int getNextVersionNumber(Wiki wiki) throws SQLException
    {
        WikiVersion[] versions = getAllVersions(wiki);
        //get last wiki version inserted
        //note: this will break if an existing version between 0 and n is deleted
        WikiVersion wikiversion = versions[versions.length - 1];
        return wikiversion.getVersion() + 1;
    }


    // This method ignores the volatile flag -- don't cache these wikis
    public static WikiVersion getVersion(Wiki wiki, int version) throws SQLException
    {
        if (null == wiki.getEntityId())
            return null;

        //special case for latest version
        if (version == LATEST)
            return getLatestVersion(wiki);

        WikiVersion[] versions = getAllVersions(wiki);

        WikiVersion wikiversion = null;
        for (WikiVersion v : versions)
        {
            if (v.getVersion() == version)
            {
                wikiversion = v;
                break;
            }
        }
        if (null == wikiversion)
            return null;

        return wikiversion;
    }


    public static WikiVersion getLatestVersion(Wiki wiki)
    {
        return getLatestVersion(wiki, false);
    }


    public static WikiVersion getLatestVersion(Wiki wiki, boolean forceRefresh)
    {
        Container c = ContainerManager.getForId(wiki.getContainerId());

        WikiAndVersion wikipair = getLatestWikiAndVersion(c, new HString(wiki.getName(),false), forceRefresh);
        if (null == wikipair)
            return null;
        return wikipair.getWikiVersion();
    }


    // UNDONE: consider exposing this method, or exposing wiki.getLatestVersion()
    private static WikiAndVersion getLatestWikiAndVersion(Container c, final HString name, boolean forceRefresh)
    {
        // TODO: This ignores forceRefresh entirely... is it really necessary?

        return WikiCache.getWikiAndVersion(c, name.getSource(), new WikiCacheLoader<WikiAndVersion>()
        {
            @Override
            public WikiAndVersion load(String key, Container c)
            {
                Wiki wiki = getWikiByName(c, name);

                if (wiki == null)
                {
                    return null;
                }

                WikiAndVersion wikipair;

                try
                {
                    WikiVersion wikiversion = Table.selectObject(comm.getTableInfoPageVersions(),
                                Table.ALL_COLUMNS,
                                new SimpleFilter("RowId", wiki.getPageVersionId()),
                                null,
                                WikiVersion.class);

                    if (wikiversion == null)
                        throw new IllegalStateException("Cannot retrieve a valid version for page " + wiki.getName());

                    // always cache wiki and version -- we defer formatting until WikiVersion.getHtml() is called
                    wikipair = new WikiAndVersion(wiki, wikiversion);
                }
                catch (SQLException x)
                {
                    throw new RuntimeSQLException(x);
                }

                return wikipair;
            }
        });
    }

    public static WikiVersion[] getAllVersions(Wiki wiki)
    {
        //fail if wiki has no entityid
        if(null == wiki.getEntityId())
            throw new IllegalStateException("Cannot retrieve version for non-existent wiki page.");

        try
        {
            WikiVersion[] versions = Table.select(comm.getTableInfoPageVersions(),
                        Table.ALL_COLUMNS,
                        new SimpleFilter("pageentityid", wiki.getEntityId()),
                        new Sort("pageentityid,version"),
                        WikiVersion.class);
            return versions;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public static int getVersionCount(Wiki wiki)
    {
        return getAllVersions(wiki).length;
    }

    public static FormattedHtml formatWiki(Container c, Wiki wiki, WikiVersion wikiversion) throws SQLException
    {
        String hrefPrefix = wiki.getWikiURL(WikiController.PageAction.class, HString.EMPTY).toString();

        String attachPrefix = null;
        if (null != wiki.getEntityId())
            attachPrefix = wiki.getAttachmentLink("");

        Map<HString, WikiLinkable> pages = getVersionMap(c);

        //get formatter specified for this version
        WikiRenderer w = wikiversion.getRenderer(hrefPrefix, attachPrefix, pages, wiki.getAttachments());

        return w.format(wikiversion.getBody());
    }


    private static final WikiCacheLoader<List<Wiki>> PAGE_LIST_CACHE_LOADER = new WikiCacheLoader<List<Wiki>>() {
            @Override
            public List<Wiki> load(String key, Container c)
            {
                List<Wiki> pageList = new ArrayList<Wiki>();
                List<Wiki> rootTopics = getWikisByParentId(c.getId(), -1);

                for (Wiki rootTopic : rootTopics)
                    addAllChildren(pageList, rootTopic);

                return pageList;
            }
        };


    public static List<Wiki> getPageList(Container c)
    {
        return WikiCache.getOrderedPageList(c, PAGE_LIST_CACHE_LOADER);
    }


    private static final WikiCacheLoader<Map<HString, Wiki>> PAGE_MAP_CACHE_LOADER = new WikiCacheLoader<Map<HString, Wiki>>() {
            @Override
            Map<HString, Wiki> load(String key, Container c)
            {
                try
                {
                    return generatePageMap(c);
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }
        };


    public static Map<HString, Wiki> getPageMap(Container c) throws SQLException
    {
        return WikiCache.getPageMap(c, PAGE_MAP_CACHE_LOADER);
    }


    private static final WikiCacheLoader<Map<HString, WikiLinkable>> VERSIONS_MAP_CACHE_LOADER = new WikiCacheLoader<Map<HString, WikiLinkable>>() {
            @Override
            Map<HString, WikiLinkable> load(String key, Container c)
            {
                Map<HString, WikiLinkable> tree = new TreeMap<HString, WikiLinkable>();
                List<Wiki> list = getPageList(c);

                for (Wiki wiki : list)
                    tree.put(wiki.getName(), getLatestVersion(wiki, false));

                return tree;
            }
        };


    private static Map<HString, WikiLinkable> getVersionMap(Container c) throws SQLException
    {
        return WikiCache.getVersionMap(c, VERSIONS_MAP_CACHE_LOADER);
    }


    private static final WikiCacheLoader<NavTree[]> NAV_TREE_CACHE_LOADER = new WikiCacheLoader<NavTree[]>() {
            @Override
            NavTree[] load(String key, Container c)
            {
                return createNavTree(WikiManager.getWikisByParentId(c.getId(), -1), "Wiki-TOC-" + c.getId());
            }
        };


    public static NavTree[] getNavTree(Container c)
    {
        NavTree[] toc = WikiCache.getNavTree(c, NAV_TREE_CACHE_LOADER);

        //need to make a deep copy of the NavTree so that
        //the caller can apply per-session expand state
        //and per-request selection state
        NavTree[] copy = new NavTree[toc.length];

        for (int idx = 0; idx < toc.length; ++idx)
        {
            copy[idx] = new NavTree(toc[idx]);
        }

        return copy;
    }


    private static NavTree[] createNavTree(List<Wiki> pageList, String rootId)
    {
        ArrayList<NavTree> elements = new ArrayList<NavTree>();

        //add all pages to the nav tree
        for (Wiki page : pageList)
        {
            NavTree node = new NavTree(page.latestVersion().getTitle().getSource(), page.getPageLink(), true);
            node.addChildren(createNavTree(page.getChildren(), rootId));
            node.setId(rootId);
            elements.add(node);
        }

        return elements.toArray(new NavTree[elements.size()]);
    }


    public static boolean wikiNameExists(Container c, HString wikiname)
    {
        return getWiki(c, wikiname) != null;
    }


    //copies a single wiki page
    public static Wiki copyPage(User user, Container cSrc, Wiki srcPage, Container cDest, Map<HString, Wiki> destPageMap,
                          Map<Integer, Integer> pageIdMap, boolean fOverwrite)
            throws SQLException, IOException, AttachmentService.DuplicateFilenameException
    {
        //get latest version
        WikiVersion srcLatestVersion = WikiManager.getLatestVersion(srcPage);

        //create new wiki page
        HString srcName = srcPage.getName();
        HString destName = srcName;
        Wiki destPage = WikiManager.getWiki(cDest, destName);

        //check whether name exists in destination wiki
        //if not overwriting, generate new name
        int i = 1;
        if (fOverwrite)
        {
            //can't overwrite if page does not exist
            if (!destPageMap.containsKey(destName))
                fOverwrite = false;
        }
        else
        {
            while (destPageMap.containsKey(destName))
                destName = srcName.concat("" + i++);
        }

        //new wiki page
        Wiki newWikiPage = null;

        if (!fOverwrite)
        {
            newWikiPage = new Wiki(cDest, destName);
            newWikiPage.setDisplayOrder(srcPage.getDisplayOrder());

            //look up parent page via map
            if (pageIdMap != null)
            {
                Integer destParentId = pageIdMap.get(srcPage.getParent());
                if (destParentId != null)
                    newWikiPage.setParent(destParentId);
                else
                    newWikiPage.setParent(-1);
            }
        }

        //new wiki version
        WikiVersion newWikiVersion = new WikiVersion(destName);
        newWikiVersion.setTitle(srcLatestVersion.getTitle());
        newWikiVersion.setBody(srcLatestVersion.getBody());
        newWikiVersion.setRendererTypeEnum(srcLatestVersion.getRendererTypeEnum());

        //get attachments
        Wiki wikiWithAttachments = WikiManager.getWiki(cSrc, srcName);
        Collection<Attachment> attachments = wikiWithAttachments.getAttachments();
        List<AttachmentFile> files = AttachmentService.get().getAttachmentFiles(wikiWithAttachments, attachments);

        if (fOverwrite)
        {
            WikiManager.updateWiki(user, destPage, newWikiVersion);
            AttachmentService.get().deleteAttachments(destPage);
            AttachmentService.get().addAttachments(user, destPage, files);
            // NOTE indexWiki() gets called twice in this case
            touch(destPage);
            indexWiki(destPage);
        }
        else
        {
            //insert new wiki page in destination container
            WikiManager.insertWiki(user, cDest, newWikiPage, newWikiVersion, files);

            //update destination page map
            destPageMap.put(destName, newWikiPage);

            //map source row id to dest row id
            if (pageIdMap != null)
            {
                pageIdMap.put(srcPage.getRowId(), newWikiPage.getRowId());
            }
        }
        return newWikiPage;
    }


    public static String updateAttachments(User user, Wiki wiki, List<String> deleteNames, List<AttachmentFile> files)
            throws IOException
    {
        AttachmentService.Service attsvc = AttachmentService.get();
        boolean changes = false;
        String message = null;

        //delete the attachments requested
        if (null != deleteNames && !deleteNames.isEmpty())
        {
            for (String name : deleteNames)
            {
                attsvc.deleteAttachment(wiki, name);
            }
            changes = true;
        }

        //add any files as attachments
        if (null != files && files.size() > 0)
        {
            try
            {
                attsvc.addAttachments(user, wiki, files);
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


    static void unindexWiki(String entityId)
    {
        SearchService ss = ServiceRegistry.get(SearchService.class);
        String docid = "wiki:" + entityId;
        if (null != ss)
            ss.deleteResource(docid);
        // UNDONE attachment
    }
    

    static void indexWiki(Wiki page)
    {
        SearchService ss = ServiceRegistry.get(SearchService.class);
        Container c = ContainerManager.getForId(page.getContainerId());
        if (null != ss && null != c)
            indexWikiContainerFast(ss.defaultTask(), c, null, page.getName().getSource());
    }


    private static void touch(Wiki wiki)
    {
        try
        {
            // CONSIDER: Table.touch()?
            Table.execute(comm.getSchema(), "UPDATE " + comm.getTableInfoPages() + " SET lastIndexed=null, modified=? WHERE container=? AND name=?",
                    new Object[]{new Date(), wiki.getContainerId(), wiki.getName()});
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static void setLastIndexed(Container c, String name, long ms)
    {
        try
        {
        Table.execute(comm.getSchema(), 
                "UPDATE comm.pages SET lastIndexed=? WHERE container=? AND name=?",
                new Object[] {new Timestamp(ms), c, name}
                );
        }
        catch (SQLException sql)
        {
            throw new RuntimeSQLException(sql);
        }
    }

    
    public static void indexWikis(@NotNull final SearchService.IndexTask task, @NotNull Container c, final Date modifiedSince)
    {
        assert null != c;
        final SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null == ss || null == c)
            return;

        indexWikiContainerFast(task, c, modifiedSince, null);
    }


    public static void indexWikiContainerSlow(@NotNull SearchService.IndexTask task, @NotNull Container c, @Nullable Date modifiedSince)
    {
        ResultSet rs = null;
        ActionURL page = new ActionURL(WikiController.PageAction.class, null);
        try
        {
            SimpleFilter f = new SimpleFilter();
            f.addCondition("container", c);
            SearchService.LastIndexedClause clause = new SearchService.LastIndexedClause(comm.getTableInfoPages(), modifiedSince, null);
            f.addCondition(clause);
            if (null != modifiedSince)
                f.addCondition("modified", modifiedSince, CompareType.GTE);

            rs = Table.select(comm.getTableInfoPages(), PageFlowUtil.set("container","name"), f, null);
            while (rs.next())
            {
                String id = rs.getString(1);
                String name = rs.getString(2);
                ActionURL url = page.clone().setExtraPath(id).replaceParameter("name",name);
                task.addResource(searchCategory, url, SearchService.PRIORITY.item);
            }
        }
        catch (SQLException x)
        {
            Logger.getLogger(WikiManager.class).error(x);
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }


    public static void indexWikiContainerFast(@NotNull SearchService.IndexTask task, @NotNull Container c, @Nullable Date modifiedSince, String name)
    {
        ResultSet rs = null;
        try
        {
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
            if (null != name)
            {
                f.append(" AND P.name = ?");
                f.add(name);
            }
            rs = Table.executeQuery(comm.getSchema(), f, 0, false, false);

            HashMap<String, AttachmentParent> ids = new HashMap<String, AttachmentParent>();
            // AGGH wiki doesn't have a title!
            HashMap<String, String> titles = new HashMap<String,String>();
            
            while (rs.next())
            {
                name = rs.getString("name");
                assert null != name;

                if (SecurityManager.TERMS_OF_USE_WIKI_NAME.equals(name))
                    continue;

                String entityId = rs.getString("entityid");
                assert null != entityId;
                String wikiTitle = rs.getString("title");
                String searchTitle;
                if (null == wikiTitle)
                    searchTitle = wikiTitle = name;
                else
                    searchTitle = wikiTitle + " " + name;   // Always search on wiki title or name
                String body = rs.getString("body");
                if (null == body)
                    body = "";
                WikiRendererType rendererType = WikiRendererType.valueOf(rs.getString("renderertype"));

                Map<String, Object> props = new HashMap<String, Object>();
                props.put(SearchService.PROPERTY.displayTitle.toString(), wikiTitle);
                props.put(SearchService.PROPERTY.searchTitle.toString(), searchTitle);

                WikiWebdavProvider.WikiPageResource r = new RenderedWikiResource(c, name, entityId, body, rendererType, props);
                task.addResource(r, SearchService.PRIORITY.item);
                if (Thread.interrupted())
                    return;
                Wiki parent = new Wiki();
                parent.setContainer(c.getId());
                parent.setEntityId(entityId);
                parent.setName(new HString(name, false));
                ids.put(entityId, parent);
                titles.put(entityId, wikiTitle);
            }

            // now attachments
            ActionURL pageUrl = new ActionURL(WikiController.PageAction.class, c);
            ActionURL downloadUrl = new ActionURL(WikiController.DownloadAction.class, c);
            
            if (!ids.isEmpty())
            {
                List<Pair<String,String>> list = AttachmentService.get().listAttachmentsForIndexing(ids.keySet(), modifiedSince);
                for (Pair<String,String> pair : list)
                {
                    String entityId = pair.first;
                    String documentName = pair.second;
                    Wiki parent = (Wiki)ids.get(entityId);

                    ActionURL wikiUrl = pageUrl.clone().addParameter("name", parent.getName());
                    ActionURL attachmentUrl = downloadUrl.clone()
                            .replaceParameter("entityId",entityId)
                            .replaceParameter("name",documentName);
                    // UNDONE: set title to make LuceneSearchServiceImpl work
                    String displayTitle = "\"" + documentName + "\" attached to page \"" + titles.get(entityId) + "\"";
                    WebdavResource attachmentRes = AttachmentService.get().getDocumentResource(
                            new Path(entityId,documentName),
                            attachmentUrl, displayTitle,
                            parent,
                            documentName, searchCategory);

                    NavTree t = new NavTree("wiki page", wikiUrl);
                    String nav = NavTree.toJS(Collections.singleton(t), null, false).toString();
                    attachmentRes.getMutableProperties().put(SearchService.PROPERTY.navtrail.toString(), nav);
                    task.addResource(attachmentRes, SearchService.PRIORITY.item);
                }
            }
        }
        catch (SQLException x)
        {
            Logger.getLogger(WikiManager.class).error(x);
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testSchema()
        {
            assertNotNull("couldn't find table Pages", comm.getTableInfoPages());
            assertNotNull(comm.getTableInfoPages().getColumn("Container"));
            assertNotNull(comm.getTableInfoPages().getColumn("EntityId"));
            assertNotNull(comm.getTableInfoPages().getColumn("Name"));


            assertNotNull("couldn't find table PageVersions", comm.getTableInfoPageVersions());
            assertNotNull(comm.getTableInfoPageVersions().getColumn("PageEntityId"));
            assertNotNull(comm.getTableInfoPageVersions().getColumn("Title"));
            assertNotNull(comm.getTableInfoPageVersions().getColumn("Body"));
            assertNotNull(comm.getTableInfoPageVersions().getColumn("Version"));
        }


        private void purgePages(Container c, boolean verifyEmpty) throws SQLException
        {
            String deleteDocuments = "DELETE FROM " + core.getTableInfoDocuments() + " WHERE Container = ? AND Parent IN (SELECT EntityId FROM " + comm.getTableInfoPages() + " WHERE Container = ?)";
            int docs = Table.execute(comm.getSchema(), deleteDocuments, new Object[]{c.getId(), c.getId()});

            String updatePages = "UPDATE " + comm.getTableInfoPages() + " SET PageVersionId = null WHERE Container = ?";
            Table.execute(comm.getSchema(), updatePages, new Object[]{c.getId()});

            String deletePageVersions = "DELETE FROM " + comm.getTableInfoPageVersions() + " WHERE PageEntityId IN (SELECT EntityId FROM " + comm.getTableInfoPages() + " WHERE Container = ?)";
            int pageVersions = Table.execute(comm.getSchema(), deletePageVersions, new Object[]{c.getId()});

            String deletePages = "DELETE FROM " + comm.getTableInfoPages() + " WHERE Container = ?";
            int pages = Table.execute(comm.getSchema(), deletePages, new Object[]{c.getId()});

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
            Wiki wikiA = new Wiki(c, new HString("pageA",false));
            WikiVersion wikiversion = new WikiVersion();
            wikiversion.setTitle(new HString("Topic A",false));
            wikiversion.setBody("[pageA]");

            insertWiki(user, c, wikiA, wikiversion, null);

            // verify objects
            wikiA = getWikiByName(c, new HString("pageA",false));
            wikiversion = getVersion(wikiA, LATEST);
            assertTrue(HString.eq("Topic A", wikiversion.getTitle()));

            assertNull(getWikiByName(c, new HString("pageNA",false)));

            //
            // DELETE
            //
            deleteWiki(user, c, wikiA);

            // verify
            assertNull(getWikiByName(c, new HString("pageA",false)));


            purgePages(c, true);
        }
    }
}
