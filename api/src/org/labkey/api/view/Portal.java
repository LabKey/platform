/*
 * Copyright (c) 2004-2014 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.view;

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.Transient;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.module.SimpleController;
import org.labkey.api.module.SimpleWebPartFactory;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class Portal
{
    private static final WebPartBeanLoader FACTORY = new WebPartBeanLoader();

    public static final String DEFAULT_PORTAL_PAGE_ID = "portal.default";
    public static final int MOVE_UP = 0;
    public static final int MOVE_DOWN = 1;
    public static final ModuleResourceCache<SimpleWebPartFactory> WEB_PART_FACTORY_CACHE =
        ModuleResourceCaches.create(new Path(SimpleController.VIEWS_DIRECTORY), "File-based webpart definitions", new SimpleWebPartFactoryCacheHandler());

    private static Map<String, WebPartFactory> _viewMap = null;


    public static DbSchema getSchema()
    {
        return CoreSchema.getInstance().getSchema();
    }

    public static SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public static TableInfo getTableInfoPortalWebParts()
    {
        return getSchema().getTable("PortalWebParts");
    }

    public static TableInfo getTableInfoPortalPages()
    {
        return getSchema().getTable("PortalPages");
    }

    public static void containerDeleted(Container c)
    {
        WebPartCache.remove(c);

        Table.delete(getTableInfoPortalWebParts(), SimpleFilter.createContainerFilter(c));
        Table.delete(getTableInfoPortalPages(), SimpleFilter.createContainerFilter(c));
    }


    // Clear the properties of all webparts whose name contains nameSearchText and whose properties contain propertiesSearchText
    public static void clearWebPartProperties(String nameSearchText, String propertiesSearchText)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("Name"), nameSearchText, CompareType.CONTAINS);
        filter.addCondition(FieldKey.fromParts("Properties"), propertiesSearchText, CompareType.CONTAINS);

        // Select all containers that are affected
        SQLFragment where = filter.getSQLFragment(Portal.getSqlDialect());
        SQLFragment selectContainers = new SQLFragment("SELECT DISTINCT Container FROM ").append(Portal.getTableInfoPortalWebParts().getSelectName()).append(" ").append(where);
        Collection<String> containersToClear = new SqlSelector(Portal.getSchema(), selectContainers).getCollection(String.class);

        // Clear the properties
        SQLFragment update = new SQLFragment("UPDATE ");
        update.append(Portal.getTableInfoPortalWebParts().getSelectName());
        update.append(" SET Properties = NULL ");
        update.append(where);
        new SqlExecutor(Portal.getSchema()).execute(update);

        // Now clear the webpart cache for all affected containers, #13937
        for (String cid : containersToClear)
        {
            Container c = ContainerManager.getForId(cid);

            if (null != c)
                WebPartCache.remove(c);
        }
    }

    public static final String WEBPART_PROP_LegacyPageAdded = "legacyPageAdded";

    public static class WebPart implements Serializable
    {
        Container container;
        String pageId;
        int rowId;
        int index = 999;
        String name;
        String location = HttpView.BODY;
        boolean permanent;
        Map<String, String> propertyMap = new HashMap<>();
        Map<String, Object> extendedProperties = null;
        String permission;
        Container permissionContainer;

        static
        {
            BeanObjectFactory.Registry.register(WebPart.class, new WebPartBeanLoader());
        }

        public WebPart()
        {
        }

        public WebPart(WebPart copyFrom)
        {
            pageId = copyFrom.pageId;
            container = copyFrom.container;
            index = copyFrom.index;
            rowId = copyFrom.rowId;
            name = copyFrom.name;
            location = copyFrom.location;
            permanent = copyFrom.permanent;
            permission = copyFrom.permission;
            permissionContainer = copyFrom.permissionContainer;
            setProperties(copyFrom.getProperties());
            this.extendedProperties = copyFrom.extendedProperties;
        }

        public String getPageId()
        {
            return pageId;
        }

        public void setPageId(String pageId)
        {
            this.pageId = pageId;
        }

        public Container getContainer()
        {
            return container;
        }

        public void setContainer(Container container)
        {
            this.container = container;
        }
        
        public int getIndex()
        {
            return index;
        }

        public void setIndex(int index)
        {
            this.index = index;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getLocation()
        {
            return location;
        }

        public void setLocation(String location)
        {
            this.location = location;
        }

        public Map<String, String> getPropertyMap()
        {
            return propertyMap;
        }

        public void setProperty(String k, String v)
        {
            propertyMap.put(k, v);
        }

        public String getProperties()
        {
            return PageFlowUtil.toQueryString(propertyMap.entrySet());
        }

        public void setProperties(String query)
        {
            for (Pair<String, String> prop : PageFlowUtil.fromQueryString(query))
                setProperty(prop.first, prop.second);
        }

        public PropertyValues getPropertyValues()
        {
            return new MutablePropertyValues(getPropertyMap());
        }

        public void setExtendedProperties(Map<String,Object> extendedProperties)
        {
            this.extendedProperties = extendedProperties;
        }

        public Map<String,Object> getExtendedProperties()
        {
            return this.extendedProperties;
        }

        public boolean isPermanent()
        {
            return permanent;
        }

        public void setPermanent(boolean permanent)
        {
            this.permanent = permanent;
        }

        public ActionURL getCustomizePostURL(ViewContext viewContext)
        {
            ActionURL current = viewContext.getActionURL();
            ActionURL ret = PageFlowUtil.urlProvider(ProjectUrls.class).getCustomizeWebPartURL(container);
            ret.addParameter("pageId", getPageId());
            ret.addParameter("index", Integer.toString(getIndex()));
            if (null != current.getReturnURL())
                ret.addReturnURL(current.getReturnURL());
            return ret;
        }

        public int getRowId()
        {
            return rowId;
        }

        public void setRowId(int rowId)
        {
            this.rowId = rowId;
        }

        public String getPermission()
        {
            return permission;
        }

        public void setPermission(String permission)
        {
            this.permission = permission;
        }

        public Container getPermissionContainer()
        {
            return permissionContainer;
        }

        public void setPermissionContainer(Container permissionContainer)
        {
            this.permissionContainer = permissionContainer;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            WebPart webPart = (WebPart) o;

            if (index != webPart.index) return false;
            if (permanent != webPart.permanent) return false;
            if (container != null ? !container.equals(webPart.container) : webPart.container != null) return false;
            if (extendedProperties != null ? !extendedProperties.equals(webPart.extendedProperties) : webPart.extendedProperties != null)
                return false;
            if (location != null ? !location.equals(webPart.location) : webPart.location != null) return false;
            if (name != null ? !name.equals(webPart.name) : webPart.name != null) return false;
            if (pageId != null ? !pageId.equals(webPart.pageId) : webPart.pageId != null) return false;
            if (propertyMap != null ? !propertyMap.equals(webPart.propertyMap) : webPart.propertyMap != null)
                return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = container != null ? container.hashCode() : 0;
            result = 31 * result + (pageId != null ? pageId.hashCode() : 0);
            result = 31 * result + index;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            result = 31 * result + (location != null ? location.hashCode() : 0);
            result = 31 * result + (permanent ? 1 : 0);
            result = 31 * result + (propertyMap != null ? propertyMap.hashCode() : 0);
            result = 31 * result + (extendedProperties != null ? extendedProperties.hashCode() : 0);
            return result;
        }
    }


    public static class WebPartBeanLoader extends BeanObjectFactory<WebPart>
    {
        public WebPartBeanLoader()
        {
            super(WebPart.class);
        }

        protected void fixupBean(WebPart part)
        {
            if (null == part.location || part.location.length() == 0)
                part.location = HttpView.BODY;
        }
    }

    public static List<WebPart> getParts(Container c)
    {
        return getParts(c, DEFAULT_PORTAL_PAGE_ID);
    }


    public static Map<String,PortalPage> getPages(Container c)
    {
        return getPages(c, false);
    }


    public static Map<String,PortalPage> getPages(Container c, boolean showHidden)
    {
        Map<String, PortalPage> pages = WebPartCache.getPages(c, showHidden);
        return Collections.unmodifiableMap(pages);
    }

    public static List<PortalPage> getSortedPages(Container c, boolean showHidden)
    {
        // Note: This does not ignore stealth pages. Use getTabPages for that.
        Map<String, PortalPage> pages = WebPartCache.getPages(c, showHidden);
        ArrayList<Portal.PortalPage> sortedPages = new ArrayList<>(pages.values());
        Collections.sort(sortedPages, new Comparator<Portal.PortalPage>()
        {
            @Override
            public int compare(Portal.PortalPage o1, Portal.PortalPage o2)
            {
                return o1.getIndex() - o2.getIndex();
            }
        });

        return Collections.unmodifiableList(sortedPages);
    }

    public static List<PortalPage> getTabPages(Container c)
    {
        return getTabPages(c, false);
    }

    public static List<PortalPage> getTabPages(Container c, boolean showHidden)
    {
        // Returns the list of tab pages in order. Ignores "stealth" pages.
        List<FolderTab> folderTabs = c.getFolderType().getDefaultTabs();
        List<PortalPage> allPortalPages = getSortedPages(c, showHidden);
        ArrayList<PortalPage> tabPages = new ArrayList<>();

        for (PortalPage page : allPortalPages)
            if (page.isCustomTab() || isPageInTabList(folderTabs, page)) // Check for stealth page
                tabPages.add(page);

        return tabPages;
    }

    private static boolean isPageInTabList(List<FolderTab> folderTabs, Portal.PortalPage page)
    {
        for (FolderTab folderTab : folderTabs)
            if (folderTab.getName().equalsIgnoreCase(page.getPageId()))
                return true;
        return false;
    }

    public static List<WebPart> getParts(Container c, String pageId)
    {
        Collection<WebPart> parts = WebPartCache.getWebParts(c, pageId);
        if (parts instanceof List)
            return Collections.unmodifiableList((List<WebPart>)parts);
        return Collections.unmodifiableList(new ArrayList<>(parts));
    }

    public static List<WebPart> getParts(Container c, ViewContext context)
    {
        return getParts(c, DEFAULT_PORTAL_PAGE_ID, context);
    }


    public static List<WebPart> getParts(Container c, String pageId, ViewContext context)
    {
        Collection<WebPart> parts = WebPartCache.getWebParts(c, pageId);
        List<WebPart> visibleParts = new ArrayList<>();

        for (WebPart part : parts)
        {
            if (part.getPermission() != null)
            {
                Container permissionContainer = part.getPermissionContainer() != null ? part.getPermissionContainer() : context.getContainer();
                Permission permission = RoleManager.getPermission(part.getPermission());
                boolean isAdmin = context.getUser().isSiteAdmin();
                boolean hasPermission = false;

                if (permissionContainer != null && permission != null)
                {
                    SecurityPolicy policy = permissionContainer.getPolicy();
                    hasPermission = policy.hasPermission(part.getName(), context.getUser(), permission.getClass());
                }

                // If the permissionContainer is null, or the permission is missing, then we only show the webpart if
                // the user is an admin.
                if (hasPermission || isAdmin)
                {
                    visibleParts.add(part);
                }
            }
            else
            {
                visibleParts.add(part);
            }
        }
        
        return Collections.unmodifiableList(visibleParts);
    }


    // TODO: Should use WebPartCache... but we need pageId to do that. Fortunately, this is used infrequently now (see #13267).
    public static WebPart getPart(Container c, int webPartRowId)
    {
        return new TableSelector(getTableInfoPortalWebParts(), SimpleFilter.createContainerFilter(c), null).getObject(webPartRowId, WebPart.class);
    }


    @Nullable
    public static WebPart getPart(Container c, String pageId, int index)
    {
        return WebPartCache.getWebPart(c, pageId, index);
    }


    public static void updatePart(User u, WebPart part)
    {
        Table.update(u, getTableInfoPortalWebParts(), part, new Object[]{part.getRowId()});
        WebPartCache.remove(part.getContainer());
    }

    // Add a web part to the container at the end of the list
    public static WebPart addPart(Container c, WebPartFactory desc, String location)
    {
        return addPart(c, desc, location, -1);
    }

    // Add a web part to a particular page at the end of the list
    public static WebPart addPart(Container c, String pageId, WebPartFactory desc, String location)
    {
        return addPart(c, pageId, desc, location, -1, null);
    }

    // Add a web part to the container at the end of the list, with properties
    public static WebPart addPart(Container c, WebPartFactory desc, String location, Map<String, String> properties)
    {
        return addPart(c, desc, location, -1, properties);
    }

    // Add a web part to the container at the specified index
    public static WebPart addPart(Container c, WebPartFactory desc, String location, int partIndex)
    {
        return addPart(c, desc, location, partIndex, null);
    }

    public static WebPart addPart(Container c, WebPartFactory desc, @Nullable String location, int partIndex, @Nullable Map<String, String> properties)
    {
        return addPart(c, DEFAULT_PORTAL_PAGE_ID, desc, location, partIndex, properties);
    }

    // Add a web part to the container at the specified index, with properties
    public static WebPart addPart(Container c, String pageId, WebPartFactory desc, @Nullable String location, int partIndex, @Nullable Map<String, String> properties)
    {
        PortalPage page = WebPartCache.getPortalPage(c, pageId);

        if(null != page)
            pageId = page.getPageId();
        
        List<WebPart> parts = getParts(c, pageId);

        WebPart newPart = new Portal.WebPart();
        newPart.setContainer(c);
        newPart.setPageId(pageId);
        newPart.setName(desc.getName());
        newPart.setIndex(partIndex >= 0 ? partIndex : parts.size());

        if (location == null)
        {
            newPart.setLocation(desc.getDefaultLocation());
        }
        else
        {
            newPart.setLocation(location);
        }

        if (properties != null)
        {
            for (Map.Entry prop : properties.entrySet())
            {
                String propName = prop.getKey().toString();
                String propValue = prop.getValue().toString();
                newPart.setProperty(propName, propValue);
            }
        }

        if (parts == null)
        {
            parts = Collections.singletonList(newPart);
        }
        else
        {
            List<Portal.WebPart> partsNew = new LinkedList<>();

            for (final WebPart currentPart : parts)
            {
                if (partsNew.size() == newPart.getIndex())
                    partsNew.add(newPart);
                final int iPart = currentPart.getIndex();
                if (iPart > newPart.getIndex())
                    currentPart.setIndex(iPart + 1);
                partsNew.add(currentPart);
            }

            if (partsNew.size() <= parts.size())
                partsNew.add(newPart);

            parts = partsNew;
        }

        Portal.saveParts(c, pageId, parts);

//        Set<Module> activeModules = new HashSet<Module>(c.getActiveModules());
//        if (!activeModules.contains(desc.getModule()))
//        {
//            activeModules.add(desc.getModule());
//            c.setActiveModules(activeModules);
//        }
//
        return newPart;
    }

    public static void saveParts(Container c, Collection<WebPart> newParts)
    {
        saveParts(c, DEFAULT_PORTAL_PAGE_ID, newParts.toArray(new WebPart[newParts.size()]));
    }

    public static void saveParts(Container c, String pageId, Collection<WebPart> newParts)
    {
        saveParts(c, pageId, newParts.toArray(new WebPart[newParts.size()]));
    }


    private static void ensurePage(Container c, String pageId)
    {
        assert getSchema().getScope().isTransactionActive();
        PortalPage find = Portal.getPortalPage(c, pageId);
        if (null != find)
        {
            _setHidden(find, false);
            return;
        }

        Map<String, PortalPage> pages = Portal.getPages(c, true);
        int index = pages.size() + 1;           // new index must be at least this big
        for (PortalPage p : pages.values())
            index = Math.max(p.getIndex()+1, index);

        try
        {
            insertPortalPage(c, pageId, index, null);
        }
        catch (SQLException | DataIntegrityViolationException x)
        {
            throw getPortalPageException(x);
        }
    }

    public static void resetPages(Container c, List<FolderTab> tabs, boolean resetIndexes)
    {
        // NOTE: this should not be called when we're refreshing a page or tabs or anything;
        //       It should only be called because of a user action (e.g. change folder type) or when importing folders
        boolean mustUpdateIndexes = false;              // Sometimes we just need to unhide some pages that are already there
        Map<String, PortalPage> pageMap = new HashMap<>(Portal.getPages(c, true));
        int maxOriginalIndex = 0;
        for (PortalPage p : pageMap.values())
            maxOriginalIndex = Math.max(maxOriginalIndex, p.getIndex());

        boolean startingWithNoPages = pageMap.isEmpty() || (pageMap.size() == 1 && pageMap.containsKey("portal.default"));
        if (resetIndexes)
        {
            // Indexes only matter relative to each other
            // Sort tabs (often tabDefaultIndex is -1; sort will not change order of equal elements)
            Collections.sort(tabs, new Comparator<FolderTab>()
            {
                public int compare(FolderTab w1, FolderTab w2)
                {
                    return w1.getDefaultIndex() - w2.getDefaultIndex();
                }
            });
            mustUpdateIndexes = true;
        }

        ArrayList<PortalPage> allPages = new ArrayList<>();
        try (DbScope.Transaction transaction = getSchema().getScope().ensureTransaction())
        {
            // First add pages for all tabs to allPages
            int tabIndex = 1;
            int newIndex = maxOriginalIndex;
            for (FolderTab tab : tabs)
            {
                PortalPage find = Portal.getPortalPage(c, tab.getName());   // Portal uses CaseInsensitiveHashMap, which is important
                if (null != find)
                {
                    pageMap.remove(tab.getName());

                    if (resetIndexes)
                    {   // Leave hiddenness as is
                        find.setIndex(tabIndex++);
                        if (null != tab.getCaption(null))
                            find.setCaption(tab.getCaption(null));
                    }
                    else
                    {
                        find.setHidden(false);
                    }

                    allPages.add(find);
                }
                else
                {
                    assert !resetIndexes;       // resetIndexes==true only when called from PageImporter who has already done saveParts on tab pages
                    newIndex += 1;
                    PortalPage p = new PortalPage();
                    p.setPageId(tab.getName());
                    p.setIndex((startingWithNoPages && tab.getDefaultIndex() > 0) ?
                            tab.getDefaultIndex() : newIndex);          // Only use default if we're adding all of them
                    allPages.add(p);
                    mustUpdateIndexes = true;           // Adding a page so must update all indexes to accommodate
                }
            }

            // Sort what we found by their existing indexes so they remain in order
            Collections.sort(allPages, new Comparator<PortalPage>()
            {
                public int compare(PortalPage w1, PortalPage w2)
                {
                    return w1.getIndex() - w2.getIndex();
                }
            });

            // Next add all other pages to allPages (includes custom pages)
            ArrayList<PortalPage> pagesLeft = new ArrayList<>(pageMap.values());
            Collections.sort(pagesLeft, new Comparator<PortalPage>()
            {
                public int compare(PortalPage w1, PortalPage w2)
                {
                    return w1.getIndex() - w2.getIndex();
                }
            });
            allPages.addAll(pagesLeft);

            // Now set indexes of all pages by walking in reverse order, assigning indexes down
            Collections.reverse(allPages);

            // We have to insure that the already set page with the highest index will get reset before that index is used
            int validPageIndex = maxOriginalIndex + allPages.size();
            TableInfo portalTable = getTableInfoPortalPages();
            for (PortalPage p : allPages)
            {
                if (mustUpdateIndexes)
                    p.setIndex(validPageIndex);
                if (null != Portal.getPortalPage(c, p.getPageId()))
                    Table.update(null, portalTable, p, new Object[] {p.getContainer(), p.getPageId()});
                else
                {
                    assert mustUpdateIndexes;
                    insertPortalPage(c, p);
                }
                validPageIndex -= 1;
            }

            transaction.commit();
        }
        catch (SQLException | DataIntegrityViolationException x)
        {
            throw getPortalPageException(x);
        }
        finally
        {
            WebPartCache.remove(c);
        }
    }

    private static PortalPage insertPortalPage(Container c, String pageId, int index, @Nullable String caption) throws SQLException
    {
        PortalPage p = new PortalPage();
        p.setPageId(pageId);
        p.setIndex(index);
        if (null != caption)
            p.setCaption(caption);
        return insertPortalPage(c, p);
    }

    private static PortalPage insertPortalPage(Container c, PortalPage p) throws SQLException
    {
        p.setEntityId(new GUID());
        p.setContainer(new GUID(c.getId()));
        p.setType("portal");
        Table.insert(null, getTableInfoPortalPages(), p);
        return p;
    }

    public static void swapPageIndexes(Container c, PortalPage page1, PortalPage page2)
    {
        int newIndex = page2.getIndex();
        int oldIndex = page1.getIndex();

        try (DbScope.Transaction transaction = getSchema().getScope().ensureTransaction())
        {
            page2.setIndex(-1);
            Portal.updatePortalPage(c, page2);
            page1.setIndex(newIndex);
            Portal.updatePortalPage(c, page1);
            page2.setIndex(oldIndex);
            Portal.updatePortalPage(c, page2);
            transaction.commit();
        }
    }

    public static void saveParts(Container c, String pageId, WebPart... newParts)
    {
        // In some rare cases we can have a difference in casing for pageId, so we want to get the page from the cache
        // first so we get the pageId with proper casing.
        PortalPage page = WebPartCache.getPortalPage(c, pageId);

        if(null != page)
            pageId = page.getPageId();

        // make sure indexes are unique
        Arrays.sort(newParts, new Comparator<WebPart>()
        {
            public int compare(WebPart w1, WebPart w2)
            {
                return w1.index - w2.index;
            }
        });

        for (int i = 0; i < newParts.length; i++)
        {
            WebPart part = newParts[i];
            part.index = i + 1;
            part.pageId = pageId;
            part.container = c;
        }

        try (DbScope.Transaction transaction = getSchema().getScope().ensureTransaction())
        {
            ensurePage(c, pageId);

            List<WebPart> oldParts = getParts(c, pageId);
            Set<Integer> oldPartIds = new HashSet<>();
            for (WebPart oldPart : oldParts)
                oldPartIds.add(oldPart.getRowId());
            Set<Integer> newPartIds = new HashSet<>();
            for (WebPart newPart : newParts)
            {
                if (newPart.getRowId() >= 0)
                    newPartIds.add(newPart.getRowId());
            }


            // delete any removed webparts:
            for (Integer oldId : oldPartIds)
            {
                if (!newPartIds.contains(oldId))
                    Table.delete(getTableInfoPortalWebParts(), oldId);
            }

            for (WebPart part1 : newParts)
            {
                try
                {
                    Map<String, Object> m = FACTORY.toMap(part1, null);
                    if (oldPartIds.contains(part1.getRowId()))
                        Table.update(null, getTableInfoPortalWebParts(), m, part1.getRowId());
                    else
                        Table.insert(null, getTableInfoPortalWebParts(), m);
                }
                catch (Table.OptimisticConflictException ex)
                {
                    // ignore
                }
            }
            transaction.commit();
        }
        catch (RuntimeSQLException x)
        {
            if (!x.isConstraintException())
                throw x;
        }
        finally
        {
            WebPartCache.remove(c);
        }
    }

    public static class AddWebParts
    {
        public String pageId;
        public String location;
        public Map<String, String> webPartNames;
        public Map<String, String> rightWebPartNames;
    }

    private static void addCustomizeDropdowns(Container c, HttpView template, String id, Collection<String> occupiedLocations)
    {
        Set<String> regionNames = new HashSet<>();

        for (WebPartFactory webPartFactory : _viewMap.values())
        {
            regionNames.addAll(webPartFactory.getAllowableLocations());
        }

        boolean rightEmpty = !occupiedLocations.contains(WebPartFactory.LOCATION_RIGHT);
        AddWebParts bodyAddPart = null;
        Map<String, String> rightParts = null;
        
        for (String regionName : regionNames)
        {
            Map<String, String> partsToAdd = Portal.getPartsToAdd(c, regionName);

            if (WebPartFactory.LOCATION_RIGHT.equals(regionName) && rightEmpty)
                rightParts = partsToAdd;
            else
            {
                //TODO: Make addPartView a real class & move to ProjectController
                AddWebParts addPart = new AddWebParts();
                addPart.pageId = id;
                addPart.location = regionName;
                addPart.webPartNames = partsToAdd;
                WebPartView addPartView = new JspView<>("/org/labkey/api/view/addWebPart.jsp", addPart);
                addPartView.setFrame(WebPartView.FrameType.NONE);

                // save these off in case we have to re-shuffle due to an empty right region:
                if (HttpView.BODY.equals(regionName))
                    bodyAddPart = addPart;

                addViewToRegion(template, regionName, addPartView);
            }
        }
        if (rightEmpty && bodyAddPart != null && rightParts != null)
            bodyAddPart.rightWebPartNames = rightParts;
    }

    private static void addViewToRegion(HttpView template, String regionName, HttpView view)
    {
        //place
        ModelAndView region = template.getView(regionName);
        if (null == region)
        {
            region = new VBox();
            template.setView(regionName, region);
        }
        if (!(region instanceof VBox))
        {
            region = new VBox(region);
            template.setView(regionName, region);
        }
        ((VBox) region).addView(view);
    }


    public static void populatePortalView(ViewContext context, String id, HttpView template) throws Exception
    {
        populatePortalView(context, id, template, context.getContainer().hasPermission("populatePortalView",context.getUser(), AdminPermission.class));
    }


    public static void populatePortalView(ViewContext context, String id, HttpView template, boolean canCustomize) throws Exception
    {
        id = StringUtils.defaultString(id, DEFAULT_PORTAL_PAGE_ID);
        String contextPath = context.getContextPath();
        List<WebPart> parts = getParts(context.getContainer(), id, context);

        // Initialize content for non-default portal pages that are folder tabs
        if (parts.isEmpty() && !StringUtils.equalsIgnoreCase(DEFAULT_PORTAL_PAGE_ID,id))
        {
            FolderTab folderTab = getFolderTabFromId(context, id);
            if (null != folderTab)
            {
                folderTab.initializeContent(context.getContainer());
                parts = getParts(context.getContainer(), id, context);
            }
        }

        MultiMap<String, WebPart> locationMap = getPartsByLocation(parts);
        Collection<String> locations = locationMap.keySet();

        for (String location : locations)
        {
            Collection<WebPart> partsForLocation = locationMap.get(location);
            int i = 0;

            for (WebPart part : partsForLocation)
            {
                //instantiate
                WebPartFactory desc = Portal.getPortalPart(part.getName());
                if (null == desc)
                    continue;

                WebPartView<Object> view = getWebPartViewSafe(desc, context, part);
                if (null == view)
                    continue;
                view.prepare(view.getModelBean());
                template.addClientDependencies(view.getClientDependencies());

                NavTree navTree = view.getPortalLinks();
                if (canCustomize)
                {
                    if (desc.isEditable() && view.getCustomize() == null)
                        view.setCustomize(new NavTree("", getCustomizeURL(context, part)));

                    if (i > 0)
                        navTree.addChild("Move Up", getMoveURL(context, part, MOVE_UP), contextPath + "/_images/partup.png");
                    else if (part.getLocation().equals(WebPartFactory.LOCATION_RIGHT))
                        navTree.addChild("Move Up", getMoveURL(context, part, MOVE_UP), contextPath + "/_images/partupg.png");
                    else
                        navTree.addChild("", "", contextPath + "/_images/partupg.png");

                    if (i < partsForLocation.size() - 1)
                        navTree.addChild("Move Down", getMoveURL(context, part, MOVE_DOWN), contextPath + "/_images/partdown.png");
                    else if (part.getLocation().equals(WebPartFactory.LOCATION_RIGHT))
                        navTree.addChild("Move Down", getMoveURL(context, part, MOVE_DOWN), contextPath + "/_images/partdowng.png");
                    else
                        navTree.addChild("", "", contextPath + "/_images/partdowng.png");
                    
                    if (!part.isPermanent())
                    {
                        NavTree delete = new NavTree("Remove From Page", getDeleteURL(context, part));
                        delete.setImage(contextPath + "/_images/partdelete.png", 24, 24);
                        navTree.addChild(delete);
                    }
                }

                addViewToRegion(template, location, view);
                i++;
            }
        }

        if (canCustomize)
            addCustomizeDropdowns(context.getContainer(), template, id, locations);
    }

    @Nullable
    public static FolderTab getFolderTabFromId(ViewContext context, String id)
    {
        if (null != id)
        {
            for (FolderTab folderTab : context.getContainer().getFolderType().getDefaultTabs())
            {
                if (folderTab instanceof FolderTab.PortalPage && id.equalsIgnoreCase(folderTab.getName()))
                {
                    return folderTab;
                }
            }
        }
        return null;
    }

    @Nullable
    public static PortalPage getPortalPage(Container container, String pageId)
    {
        return WebPartCache.getPortalPage(container, pageId);
    }

    public static String getCustomizeURL(ViewContext context, Portal.WebPart webPart)
    {
        return PageFlowUtil.urlProvider(ProjectUrls.class).getCustomizeWebPartURL(context.getContainer(), webPart, context.getActionURL()).getLocalURIString();
    }


    private static final boolean USE_ASYNC_PORTAL_ACTIONS = true;
    public static String getMoveURL(ViewContext context, Portal.WebPart webPart, int direction)
    {
        if (USE_ASYNC_PORTAL_ACTIONS)
        {
            String methodName = direction == MOVE_UP ? "moveWebPartUp" : "moveWebPartDown";
            return "javascript:LABKEY.Portal." + methodName + "({" +
                    "webPartId:" + webPart.getRowId() + "," +
                    "updateDOM:true" +
                    "})";
        }
        else
            return PageFlowUtil.urlProvider(ProjectUrls.class).getMoveWebPartURL(context.getContainer(), webPart, direction, context.getActionURL()).getLocalURIString();
    }


    public static String getDeleteURL(ViewContext context, Portal.WebPart webPart)
    {
        if (USE_ASYNC_PORTAL_ACTIONS)
        {
            return "javascript:LABKEY.Portal.removeWebPart({" +
                    "webPartId:" + webPart.getRowId() + "," +
                    "updateDOM:true" +
                    "})";
        }
        else
            return PageFlowUtil.urlProvider(ProjectUrls.class).getDeleteWebPartURL(context.getContainer(), webPart, context.getActionURL()).getLocalURIString();
    }


    public static MultiMap<String, WebPart> getPartsByLocation(Collection<WebPart> parts)
    {
        MultiMap<String, WebPart> multiMap = new MultiHashMap<>();

        for (WebPart part : parts)
        {
            if (null == part.getName() || 0 == part.getName().length())
                continue;
            String location = part.getLocation();
            multiMap.put(location, part);
        }

        return multiMap;
    }

    public static WebPartFactory getPortalPart(String name)
    {
        return getViewMap().get(name);
    }

    public static WebPartFactory getPortalPartCaseInsensitive(String name)
    {
        CaseInsensitiveHashMap<WebPartFactory> viewMap = new CaseInsensitiveHashMap<>(getViewMap());
        return viewMap.get(name);
    }

    private static synchronized Map<String, WebPartFactory> getViewMap()
    {
        if (null == _viewMap)
            initMaps();

        return _viewMap;
    }

    private synchronized static void initMaps()
    {
        _viewMap = new HashMap<>(20);

        List<Module> modules = ModuleLoader.getInstance().getModules();
        for (Module module : modules)
        {
            for (WebPartFactory factory : module.getWebPartFactories())
            {
                if (validateFactoryName(factory.getName(), module))
                {
                    _viewMap.put(factory.getName(), factory);
                }
                for (String legacyName : factory.getLegacyNames())
                {
                    if (validateFactoryName(legacyName, module))
                    {
                        _viewMap.put(legacyName, factory);
                    }
                }
            }
        }

    }

    private static boolean validateFactoryName(String name, Module module)
    {
        WebPartFactory existingFactory = _viewMap.get(name);
        if (existingFactory != null)
        {
            ModuleLoader.getInstance().addModuleFailure(module.getName(), new IllegalStateException("A webpart named '" + name + "' was already registered by the module '" + existingFactory.getModule().getName() + "'"));
            return false;
        }
        return true;
    }

    synchronized static void clearMaps()
    {
        _viewMap = null;
    }

    static void clearWebPartFactories(Module module)
    {
        if (module instanceof DefaultModule)
        {
            // TODO: Move the webPartFactory handling into Portal
            ((DefaultModule) module).clearWebPartFactories();
        }
    }

    public static Map<String, String> getPartsToAdd(Container c, String location)
    {
        //TODO: Cache these?
        Map<String, String> webPartNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Module module : ModuleLoader.getInstance().getModules())
        {
            for (WebPartFactory factory : module.getWebPartFactories())
            {
                if (factory.isAvailable(c, location))
                    webPartNames.put(factory.getName(), factory.getDisplayName(c, location));
            }
        }

        return webPartNames;
    }

    public static int purge()
    {
        return ContainerUtil.purgeTable(getTableInfoPortalWebParts(), "PageId");
    }

    public static WebPartView getWebPartViewSafe(@NotNull WebPartFactory factory, @NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        WebPartView view;

        try
        {
            view = factory.getWebPartView(portalCtx, webPart);
            if (view != null)
            {
                view.setWebPartRowId(webPart.getRowId());
                view.setLocation(webPart.getLocation());
                Map<String, String> props = webPart.getPropertyMap();
                if (null != props && props.containsKey("webpart.title"))
                    view.setTitle(props.get("webpart.title"));
            }
        }
        catch(Throwable t)
        {
            int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            String message = "An unexpected error occurred";
            view = ExceptionUtil.getErrorWebPartView(status, message, t, portalCtx.getRequest());
            view.setTitle(webPart.getName());
            view.setWebPartRowId(webPart.getRowId());
        }

        return view;
    }


    private static void _setHidden(PortalPage page, boolean hidden)
    {
        if (page.isHidden() == hidden)
            return;


        _hidePage(page, hidden);
    }

    public static void deletePage(PortalPage page)
    {
        // Called above, and also by ContainerManager when deleting container tab
        try
        {
            TableInfo tableInfo = getTableInfoPortalWebParts();
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(tableInfo.getColumn("container"), page.getContainer());
            filter.addCondition(tableInfo.getColumn("pageid"), page.getPageId());
            Table.delete(tableInfo, filter);
            Table.delete(getTableInfoPortalPages(), new Object[] {page.getContainer(), page.getPageId()});
        }
        finally
        {
            WebPartCache.remove(ContainerManager.getForId(page.getContainer()));
        }
    }

    public static void deletePage(Container c, String pageId)
    {
        PortalPage page = WebPartCache.getPortalPage(c,pageId);
        deletePage(page);
    }

    public static void deletePage(Container c, int index)
    {
        Map<String, PortalPage> pages = WebPartCache.getPages(c, true);
        for (PortalPage page : pages.values())
        {
            if (page.getIndex() == index)
            {
                deletePage(page);
                break;
            }
        }
    }


    private static void _hidePage(PortalPage page, boolean hidden)
    {
        try
        {
            page = page.copy();
            page.setHidden(hidden);
            Table.update(null, getTableInfoPortalPages(), page, new Object[] {page.getContainer(), page.getPageId()});
        }
        finally
        {
            WebPartCache.remove(ContainerManager.getForId(page.getContainer()));
        }
    }

    public static void hidePage(Container c, String pageId)
    {
        PortalPage page = WebPartCache.getPortalPage(c,pageId);
        if (null != page)
            _setHidden(page, true);
    }


    public static void showPage(Container c, String pageId)
    {
        PortalPage page = WebPartCache.getPortalPage(c,pageId);
        if (null != page)
            _setHidden(page, false);
    }


    public static void hidePage(Container c, int index)
    {
        Map<String, PortalPage> pages = WebPartCache.getPages(c, true);
        for (PortalPage page : pages.values())
        {
            if (page.getIndex() == index)
            {
                _setHidden(page, true);
                break;
            }
        }
    }

    public static void updatePortalPage(Container c, PortalPage page)
    {
        if(null != page)
        {
            try
            {
                page = page.copy();
                Table.update(null, getTableInfoPortalPages(), page, new Object[] {page.getContainer(), page.getPageId()});
            }
            catch (RuntimeSQLException | DataIntegrityViolationException x)
            {
                throw getPortalPageException(x);
            }
            finally
            {
                WebPartCache.remove(ContainerManager.getForId(page.getContainer()));
            }
        }
    }

    private static RuntimeException getPortalPageException(Exception x)
    {
        RuntimeSQLException s = null;
        if (x instanceof RuntimeSQLException)
            s = (RuntimeSQLException)x;
        else if (x instanceof DataIntegrityViolationException)
        {
            DataIntegrityViolationException d = (DataIntegrityViolationException)x;
            if (d.getCause() instanceof RuntimeSQLException)
                s = (RuntimeSQLException)d.getCause();
        }
        if (null != s)
        {
            if (s.isConstraintException())
            {
                s = new Table.OptimisticConflictException(
                    "A SQL exception occurred which could have been caused by two clients changing portal page ordering simultaneously. Try again.",
                    Table.SQLSTATE_TRANSACTION_STATE, 0);
            }
            return s;
        }
        return new RuntimeException(x);
    }

    public static final String PROP_CUSTOMTAB = "customTab";

    public static void addProperties(Container container, String pageId, String properties)
    {
        Portal.PortalPage page = WebPartCache.getPortalPage(container, pageId);
        page = page.copy();
        page.setProperties(properties);
        _setProperties(page);
    }

    public static void addProperty(Container container, String pageId, String property)
    {
        Portal.PortalPage page = WebPartCache.getPortalPage(container, pageId);
        page = page.copy();
        page.setProperty(property, "true");
        _setProperties(page);
    }

    private static void _setProperties(PortalPage page)
    {
        try
        {
            Table.update(null, getTableInfoPortalPages(), page, new Object[] {page.getContainer(), page.getPageId()});
        }
        finally
        {
            WebPartCache.remove(ContainerManager.getForId(page.getContainer()));
        }
    }

    public static class PortalPage implements Cloneable
    {
        private GUID entityId;
        private GUID containerId;
        private String pageId;
        private int index;
        private String caption;
        private boolean hidden;
        private String type;
        private String action;       // detailsurl (type==action)
        private GUID targetFolder;   // continerId (type==folder)
        private boolean permanent;   // may not rename,hide,delete
        Map<String, String> propertyMap = new HashMap<>();
        private String properties;
        private LinkedHashMap<Integer,WebPart> webparts = new LinkedHashMap<>();

        public GUID getEntityId()
        {
            return entityId;
        }

        public void setEntityId(GUID entityId)
        {
            this.entityId = entityId;
        }

        public GUID getContainer()
        {
            return containerId;
        }

        public void setContainer(GUID containerId)
        {
            this.containerId = containerId;
        }

        public String getPageId()
        {
            return pageId;
        }

        public void setPageId(String pageId)
        {
            this.pageId = pageId;
        }

        public int getIndex()
        {
            return index;
        }

        public void setIndex(int index)
        {
            this.index = index;
        }

        public String getCaption()
        {
            return caption;
        }

        public void setCaption(String name)
        {
            this.caption = name;
        }

        public boolean isHidden()
        {
            return hidden;
        }

        public void setHidden(boolean hidden)
        {
            this.hidden = hidden;
        }

        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = type;
        }

        public String getAction()
        {
            return action;
        }

        public void setAction(String action)
        {
            this.action = action;
        }

        public GUID getTargetFolder()
        {
            return targetFolder;
        }

        public void setTargetFolder(GUID targetFolder)
        {
            this.targetFolder = targetFolder;
        }

        public boolean isPermanent()
        {
            return permanent;
        }

        public void setPermanent(boolean permanent)
        {
            this.permanent = permanent;
        }

        public Map<String, String> getPropertyMap()
        {
            return propertyMap;
        }

        public void setProperty(String k, String v)
        {
            propertyMap.put(k, v);
        }

        public String getProperty(String k)
        {
            return propertyMap.get(k);
        }

        public String getProperties()
        {
            return PageFlowUtil.toQueryString(propertyMap.entrySet());
        }

        public void setProperties(String query)
        {
            for (Pair<String, String> prop : PageFlowUtil.fromQueryString(query))
                setProperty(prop.first, prop.second);
        }

        public void addWebPart(WebPart part)
        {
            webparts.put(part.getIndex(),part);
        }

        @Transient
        public LinkedHashMap<Integer,WebPart> getWebParts()
        {
            return webparts;
        }

        public PortalPage copy()
        {
            try
            {
                return (PortalPage)this.clone();
            }
            catch (CloneNotSupportedException x)
            {
                throw new RuntimeException(x);
            }
        }

        public boolean isCustomTab()
        {
            String customTab = getProperty(Portal.PROP_CUSTOMTAB);
            if (null != customTab && customTab.equalsIgnoreCase("true"))
                return true;
            return false;
        }
    }
}
