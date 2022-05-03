/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.collections4.Factory;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SpringActionController;
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
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.module.ResourceRootProvider;
import org.labkey.api.module.SimpleWebPartFactory;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.Button;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.ModuleChangeListener;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.element.CsrfInput;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
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
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.labkey.api.util.DOM.Attribute.action;
import static org.labkey.api.util.DOM.Attribute.method;
import static org.labkey.api.util.DOM.Attribute.name;
import static org.labkey.api.util.DOM.Attribute.type;
import static org.labkey.api.util.DOM.Attribute.value;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.HR;
import static org.labkey.api.util.DOM.INPUT;
import static org.labkey.api.util.DOM.LK;
import static org.labkey.api.util.DOM.OPTION;
import static org.labkey.api.util.DOM.Renderable;
import static org.labkey.api.util.DOM.SELECT;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.cl;
import static org.labkey.api.util.DOM.createHtml;

/**
 * Manages the configuration of portal pages, which can be configured by admins to show
 * a desired set of {@link WebPartView} sections.
 */
public class Portal implements ModuleChangeListener
{
    private static final Logger LOG = LogManager.getLogger(Portal.class);
    private static final WebPartBeanLoader FACTORY = new WebPartBeanLoader();

    public static final String FOLDER_PORTAL_PAGE = "folder";
    public static final String STUDY_PARTICIPANT_PORTAL_PAGE = "participant";

    public static final String DEFAULT_PORTAL_PAGE_ID = "portal.default";
    public static final int MOVE_UP = 0;
    public static final int MOVE_DOWN = 1;
    public static final ModuleResourceCache<Collection<SimpleWebPartFactory>> WEB_PART_FACTORY_CACHE =
        ModuleResourceCaches.create("File-based webpart definitions", new SimpleWebPartFactoryCacheHandler(), ResourceRootProvider.getStandard(ModuleHtmlView.VIEWS_PATH));

    private static Map<String, WebPartFactory> _viewMap = null;
    private static final List<WebPartFactory> _homeWebParts = new ArrayList<>();

    private static final Map<String, List<NavTreeCustomizer>> _navTreeCustomizerMap = new CaseInsensitiveHashMap<>();


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

    @NotNull static ProjectUrls urlProvider()
    {
        return Objects.requireNonNull(PageFlowUtil.urlProvider(ProjectUrls.class));
    }

    public static final String WEBPART_PROP_LegacyPageAdded = "legacyPageAdded";

    /** Bean object for persisting web part configurations in the core.portalwebparts table
     * NOTE: implements Factory<> so this can be used as a builder for immutable object
     */
    public static class WebPart implements Serializable, Factory<WebPart>
    {
        Container container;
        String pageId;
        int portalPageId;
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
            this(copyFrom, false);
        }

        protected WebPart(WebPart copyFrom, boolean readonly)
        {
            pageId = copyFrom.pageId;
            portalPageId = copyFrom.portalPageId;
            container = copyFrom.container;
            index = copyFrom.index;
            rowId = copyFrom.rowId;
            name = copyFrom.name;
            location = copyFrom.location;
            permanent = copyFrom.permanent;
            permission = copyFrom.permission;
            permissionContainer = copyFrom.permissionContainer;
            propertyMap.putAll(copyFrom.propertyMap);
            if (readonly)
                propertyMap = Collections.unmodifiableMap(propertyMap);
            if (null != copyFrom.extendedProperties)
            {
                extendedProperties = new HashMap<>(copyFrom.extendedProperties);
                if (readonly)
                    extendedProperties = Collections.unmodifiableMap(extendedProperties);
            }
        }

        public String getPageId()
        {
            return pageId;
        }

        public int getPortalPageId()
        {
            return portalPageId;
        }

        @SuppressWarnings("unused")
        public void setPortalPageId(int portalPageId)
        {
            this.portalPageId = portalPageId;
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
            ActionURL ret = urlProvider().getCustomizeWebPartURL(container);
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

        public void hasFrame(boolean hasFrame)
        {
            this.propertyMap.put("framed", Boolean.toString(hasFrame));
        }

        public boolean hasFrame()
        {
            return !this.propertyMap.containsKey("framed") ||
                    Boolean.parseBoolean(this.propertyMap.get("framed"));
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
            if (portalPageId != webPart.portalPageId) return false;
            if (propertyMap != null ? !propertyMap.equals(webPart.propertyMap) : webPart.propertyMap != null)
                return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = container != null ? container.hashCode() : 0;
            result = 31 * result + (pageId != null ? pageId.hashCode() : 0);
            result = 31 * result + portalPageId;
            result = 31 * result + index;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            result = 31 * result + (location != null ? location.hashCode() : 0);
            result = 31 * result + (permanent ? 1 : 0);
            result = 31 * result + (propertyMap != null ? propertyMap.hashCode() : 0);
            result = 31 * result + (extendedProperties != null ? extendedProperties.hashCode() : 0);
            return result;
        }

        // return an immutable webpart

        @Override
        public WebPart create()
        {
            return new WebPart(this, true)
            {
                @Override
                public WebPart create()
                {
                    return this;
                }

                @Override
                public void setPortalPageId(int portalPageId)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setPageId(String pageId)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setContainer(Container container)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setIndex(int index)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setName(String name)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setLocation(String location)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setProperty(String k, String v)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setProperties(String query)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setExtendedProperties(Map<String, Object> extendedProperties)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setPermanent(boolean permanent)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setRowId(int rowId)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setPermission(String permission)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setPermissionContainer(Container permissionContainer)
                {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }


    public static class WebPartBeanLoader extends BeanObjectFactory<WebPart>
    {
        public WebPartBeanLoader()
        {
            super(WebPart.class);
        }

        @Override
        protected void fixupBean(WebPart part)
        {
            if (null == part.location || part.location.length() == 0)
                part.location = HttpView.BODY;
        }
    }

    @NotNull
    public static List<WebPart> getParts(Container c)
    {
        return getParts(c, DEFAULT_PORTAL_PAGE_ID);
    }

    @NotNull
    public static List<WebPart> getEditableParts(Container c)
    {
        return getParts(c, DEFAULT_PORTAL_PAGE_ID).stream().map(WebPart::new).collect(Collectors.toList());
    }


    @NotNull
    public static Map<String,PortalPage> getPages(Container c)
    {
        return getPages(c, false);
    }


    @NotNull
    public static Map<String,PortalPage> getPages(Container c, boolean showHidden)
    {
        Map<String, PortalPage> pages = WebPartCache.getPages(c, showHidden);
        return Collections.unmodifiableMap(pages);
    }

    @NotNull
    public static List<PortalPage> getSortedPages(Container c, boolean showHidden)
    {
        // Note: This does not ignore stealth pages. Use getTabPages for that.
        Map<String, PortalPage> pages = WebPartCache.getPages(c, showHidden);
        ArrayList<Portal.PortalPage> sortedPages = new ArrayList<>(pages.values());
        sortedPages.sort(Comparator.comparingInt(PortalPage::getIndex));

        return Collections.unmodifiableList(sortedPages);
    }

    @NotNull
    public static List<PortalPage> getTabPages(Container c)
    {
        return getTabPages(c, false);
    }

    @NotNull
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
            if (folderTab.getName() != null && folderTab.getName().equalsIgnoreCase(page.getPageId()))
                return true;
        return false;
    }

    @NotNull
    public static List<WebPart> getParts(Container c, String pageId)
    {
        Collection<WebPart> parts = WebPartCache.getWebParts(c, pageId);
        return List.copyOf(parts);
    }

    @NotNull
    public static List<WebPart> getEditableParts(Container c, String pageId)
    {
        return WebPartCache.getWebParts(c, pageId).stream().map(WebPart::new).collect(Collectors.toList());
    }

    @NotNull
    public static List<WebPart> getParts(Container c, ViewContext context)
    {
        return getParts(c, DEFAULT_PORTAL_PAGE_ID, context);
    }


    @NotNull
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
                boolean isAdmin = context.getUser().hasRootAdminPermission();
                boolean hasPermission = false;

                if (permissionContainer != null && permission != null)
                {
                    hasPermission = SecurityManager.hasAllPermissions(part.getName(), permissionContainer, context.getUser(), Set.of(permission.getClass()), Set.of());
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


    /** returns an editable webPart (could rename to getEditablePart() */
    public static WebPart getPart(Container c, int webPartRowId)
    {
        // TODO: Should use WebPartCache... but we need pageId to do that. Fortunately, this is used infrequently now (see #13267).
        WebPart webPart = new TableSelector(getTableInfoPortalWebParts(), SimpleFilter.createContainerFilter(c), null).getObject(webPartRowId, WebPart.class);
        if (null != webPart)
        {
            PortalPage page = new TableSelector(getTableInfoPortalPages(), new SimpleFilter(FieldKey.fromParts("RowId"), webPart.getPortalPageId()), null).getObject(PortalPage.class);
            if (null != page)
            {
                webPart.setPageId(page.getPageId());
                return webPart;       // Only return webPart if we also can obtain its portal page
            }
        }
        return null;
    }

    /** returns an editable webPart (could rename to getEditablePart() */
    @Nullable
    public static WebPart getPart(Container c, String pageId, int index)
    {
        WebPart cached = WebPartCache.getWebPart(c, pageId, index);
        return null==cached ? null : new Portal.WebPart(cached);
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

        if (null != page)
            pageId = page.getPageId();
        
        List<WebPart> parts = getEditableParts(c, pageId);

        WebPart newPart = new Portal.WebPart();
        newPart.setContainer(c);
        newPart.setPageId(pageId);
        newPart.setName(desc.getName());
        newPart.setIndex(partIndex >= 0 ? partIndex : parts.size());

        if (location == null)
            newPart.setLocation(desc.getDefaultLocation());
        else
            newPart.setLocation(location);

        if (properties != null)
        {
            for (Map.Entry<String,String> prop : properties.entrySet())
            {
                String propName = prop.getKey();
                String propValue = prop.getValue();
                newPart.setProperty(propName, propValue);
            }
        }

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

        Portal.saveParts(c, pageId, partsNew);

        return newPart;
    }

    public static void saveParts(Container c, Collection<WebPart> newParts)
    {
        saveParts(c, DEFAULT_PORTAL_PAGE_ID, newParts.toArray(new WebPart[0]));
    }

    public static void saveParts(Container c, String pageId, Collection<WebPart> newParts)
    {
        saveParts(c, pageId, newParts.toArray(new WebPart[0]));
    }


    private static void ensurePage(Container c, String pageId)
    {
        boolean inTransaction = getSchema().getScope().isTransactionActive();

        TableInfo portalPageTable = getTableInfoPortalPages();
        Map<String, PortalPage> pages = Portal.getPages(c, true);
        int index = pages.size() + 1;           // new index must be at least this big
        for (PortalPage p : pages.values())
        {
            if (StringUtils.equalsIgnoreCase(pageId, p.getPageId()))
            {
                _setHidden(p, false);
                return;
            }
            index = Math.max(p.getIndex() + 1, index);
        }

        try
        {
            insertPortalPage(portalPageTable, c, pageId, index, null);
        }
        catch (RuntimeSQLException | DataIntegrityViolationException x)
        {
            // ignore if not in transaction, which is usually the case
            if (inTransaction)
                throw x;
            LOG.warn("Ensure page failed, likely because page already present.");
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
            tabs.sort(Comparator.comparingInt(FolderTab::getDefaultIndex));
            mustUpdateIndexes = true;
        }

        ArrayList<PortalPage> allPages = new ArrayList<>();
        try
        {
            // First add pages for all tabs to allPages
            int tabIndex = 1;
            int newIndex = maxOriginalIndex;
            for (FolderTab tab : tabs)
            {
                PortalPage find = Portal.getPortalPage(c, tab.getName());   // Portal uses CaseInsensitiveHashMap, which is important
                if (null != find)
                {
                    // create a copy for modifying
                    find = find.copy();
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
                    p.setIndex((startingWithNoPages && tab.getDefaultIndex() > 0) ? tab.getDefaultIndex() : newIndex);          // Only use default if we're adding all of them
                    p.setContainer(c.getEntityId());
                    allPages.add(p);
                    mustUpdateIndexes = true;           // Adding a page so must update all indexes to accommodate
                }
            }

            // Sort what we found by their existing indexes so they remain in order
            allPages.sort(Comparator.comparingInt(PortalPage::getIndex));

            // Next add all other pages to allPages (includes custom pages)
            // creating copy of PortalPage object
            allPages.addAll(pageMap.values().stream().map(PortalPage::copy).sorted(Comparator.comparing(PortalPage::getIndex)).collect(Collectors.toList()));

            // Now set indexes of all pages by walking in reverse order, assigning indexes down
            Collections.reverse(allPages);

            Map<String, PortalPage> currentPages = new HashMap<>();
            TableInfo portalTable = getTableInfoPortalPages();
            new TableSelector(portalTable, SimpleFilter.createContainerFilter(c), null)
                    .getArrayList(PortalPage.class)
                    .forEach(page -> currentPages.put(page.getPageId(), page));

            // Transaction does not have to call Portal.getPortalPage
            try (DbScope.Transaction transaction = getSchema().getScope().ensureTransaction())
            {
                // We have to insure that the already set page with the highest index will get reset before that index is used
                int validPageIndex = maxOriginalIndex + allPages.size();
                for (PortalPage p : allPages)
                {
                    if (mustUpdateIndexes)
                        p.setIndex(validPageIndex);
                    if (currentPages.containsKey(p.getPageId()))
                        _insertOrUpdate(portalTable, p, true);
                    else
                    {
                        assert mustUpdateIndexes;
                        insertPortalPage(portalTable, c, p);
                    }
                    validPageIndex -= 1;
                }

                transaction.commit();
            }
        }
        catch (DataIntegrityViolationException x)
        {
            throw getPortalPageException(x);
        }
        finally
        {
            WebPartCache.remove(c);
        }
    }

    private static void insertPortalPage(TableInfo portalTable, Container c, String pageId, int index, @Nullable String caption)
    {
        PortalPage p = new PortalPage();
        p.setPageId(pageId);
        p.setIndex(index);
        if (null != caption)
            p.setCaption(caption);
        insertPortalPage(portalTable, c, p);
    }

    private static void insertPortalPage(TableInfo portalTable, Container c, PortalPage p)
    {
        p.setEntityId(new GUID());
        p.setContainer(new GUID(c.getId()));
        p.setType("portal");
        _insertOrUpdate(portalTable, p, false);
    }

    private static void _insertOrUpdate(TableInfo portalTable, PortalPage p, boolean update)
    {
        int count = 0;
        String legalIndexName = portalTable.getSqlDialect().makeLegalIdentifier("Index");
        if (!update)
        {
            // Try insert; SQL checks if pageId or index is already there and doesn't insert in those cases.
            List<String> insertColumns = new ArrayList<>();
            SQLFragment insertSQL = new SQLFragment("INSERT INTO ");

            insertColumns.add("EntityId");
            insertSQL.add(p.getEntityId());
            insertColumns.add("Container");
            insertSQL.add(p.getContainer());
            insertColumns.add("PageId");
            insertSQL.add(p.getPageId());
            insertColumns.add(legalIndexName);
            insertSQL.add(p.getIndex());
            insertColumns.add("Caption");
            insertSQL.add(p.getCaption());
            insertColumns.add("Hidden");
            insertSQL.add(p.isHidden());
            insertColumns.add("Type");
            insertSQL.add(p.getType());
            insertColumns.add("Action");
            insertSQL.add(p.getAction());
            insertColumns.add("TargetFolder");
            insertSQL.add(p.getTargetFolder());
            insertColumns.add("Permanent");
            insertSQL.add(p.isPermanent());
            insertColumns.add("Properties");
            insertSQL.add(p.getProperties());

            insertSQL.append(portalTable.getSelectName())
                    .append("\n(")
                    .append(StringUtils.join(insertColumns, ", "))
                    .append(")\n")
                    .append(" (SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?\n WHERE ? NOT IN (")
                    .add(p.getPageId())
                    .append("SELECT PageId FROM ")
                    .append(portalTable.getSelectName())
                    .append(" WHERE Container = ? AND PageId = ?) AND\n")
                    .add(p.getContainer()).add(p.getPageId())
                    .append(" ? NOT IN (SELECT ").append(legalIndexName).append(" FROM ")
                    .add(p.getIndex())
                    .append(portalTable.getSelectName())
                    .append(" WHERE Container = ?)")
                    .add(p.getContainer())
                    .append(getSqlDialect().isPostgreSQL() ? " FOR UPDATE)" : ")");
            count = new SqlExecutor(portalTable.getSchema()).execute(insertSQL);
        }

        if (0 == count)
        {
            // Either we're only updating or insert found that pageId or index is already there.
            // If index is already there for a different page, update will update other fields, but not index.
            SQLFragment updateSQL = new SQLFragment("UPDATE ");
            updateSQL.append(portalTable.getSelectName());

            SQLFragment indexSQL = new SQLFragment("CASE WHEN ? NOT IN\n(SELECT ");
            indexSQL.append(legalIndexName).append(" FROM ")
                    .add(p.getIndex())
                    .append(portalTable.getSelectName())
                    .append(" WHERE Container = ? AND NOT (PageId = ?))\nTHEN ? ELSE ").append(legalIndexName).append(" END\n")
                    .add(p.getContainer()).add(p.getPageId()).add(p.getIndex());

            if (portalTable.getSqlDialect().isPostgreSQL())
            {
                List<String> updateColumns = new ArrayList<>();
                updateColumns.add(legalIndexName);
                updateColumns.add("Caption");
                updateColumns.add("Hidden");
                updateColumns.add("Type");
                updateColumns.add("Action");
                updateColumns.add("TargetFolder");
                updateColumns.add("Permanent");
                updateColumns.add("Properties");

                updateSQL.append("\nSET (")
                        .append(StringUtils.join(updateColumns, ", "))
                        .append(") =\n")
                        .append("(")
                        .append(indexSQL)

                        .append(", ?, ?, ?, ?, ?, ?, ?)\n");

            }
            else
            {       // SQL Server
                updateSQL.append("\nSET ")
                        .append(legalIndexName).append(" = ").append(indexSQL).append(", ")
                        .append("Caption").append(" = ?, ")
                        .append("Hidden").append(" = ?, ")
                        .append("Type").append(" = ?, ")
                        .append("Action").append(" = ?, ")
                        .append("TargetFolder").append(" = ?, ")
                        .append("Permanent").append(" = ?, ")        
                        .append("Properties").append(" = ? \n");
            }

            updateSQL.add(p.getCaption()).add(p.isHidden()).add(p.getType()).add(p.getAction())
                    .add(p.getTargetFolder()).add(p.isPermanent()).add(p.getProperties())
                    .append("WHERE Container = ? AND PageId = ?")
                    .add(p.getContainer()).add(p.getPageId());

            count = new SqlExecutor(portalTable.getSchema()).execute(updateSQL);
            if (0 == count)
                LOG.warn((update ? "Update" : "Insert") + " failed for page '" + p.pageId + "' in container '" + ContainerManager.getForId(p.getContainer()).getPath() + "'");
        }
    }

    public static void swapPageIndexes(Container c, PortalPage page1, PortalPage page2)
    {
        // In rare cases the 2 indexes could be the same; if so change all the indexes;
        if (page2.getIndex() == page1.getIndex())
        {
            String pageId1 = page1.getPageId();
            String pageId2 = page2.getPageId();
            List<PortalPage> pagesList = Portal.getTabPages(c, true);

            try (DbScope.Transaction transaction1 = getSchema().getScope().ensureTransaction())
            {
                int index = 0;
                for (PortalPage page : pagesList)
                {
                    page = page.copy();    // writable copy
                    page.setIndex(index++);
                    Portal.updatePortalPage(c, page);
                }
                transaction1.commit();
            }

            page1 = WebPartCache.getPortalPage(c, pageId1);
            page2 = WebPartCache.getPortalPage(c, pageId2);
        }

        if (null != page1 && null != page2)
        {
            int newIndex = page2.getIndex();
            int oldIndex = page1.getIndex();

            try (DbScope.Transaction transaction = getSchema().getScope().ensureTransaction())
            {
                page1 = page1.copy();
                page2 = page2.copy();
                page2.setIndex(-1);
                Portal.updatePortalPage(c, page2);
                page1.setIndex(newIndex);
                Portal.updatePortalPage(c, page1);
                page2.setIndex(oldIndex);
                Portal.updatePortalPage(c, page2);
                transaction.commit();
            }
        }
    }

    public static void saveParts(Container c, String pageId, WebPart... newParts)
    {
        // In some rare cases we can have a difference in casing for pageId, so we want to get the page from the cache
        // first so we get the pageId with proper casing.
        PortalPage page = WebPartCache.getPortalPage(c, pageId);

        if (null != page)
            pageId = page.getPageId();

        // make sure indexes are unique
        Arrays.sort(newParts, Comparator.comparingInt(w -> w.index));

        try
        {
            if (null == page)
            {
                ensurePage(c, pageId);
                page = getPortalPageDirect(c, pageId);      // WebPartCache won't load it yet
                if (null == page)
                    throw new IllegalStateException("Ensure page failed.");
            }

            for (int i = 0; i < newParts.length; i++)
            {
                WebPart part = newParts[i];
                part.index = i + 1;
                part.pageId = pageId;
                part.portalPageId = page.getRowId();
                part.container = c;
            }

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
                catch (OptimisticConflictException ex)
                {
                    // ignore
                }
            }
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
        public boolean rightEmpty;
        public String scope;
    }

    private static void addCustomizeDropdowns(ViewContext context, HttpView<?> template, String id, Collection<String> occupiedLocations, String scope)
    {
        Set<String> regionNames = new HashSet<>();

        for (WebPartFactory webPartFactory : getViewMap().values())
        {
            regionNames.addAll(webPartFactory.getAllowableLocations());
        }

        boolean rightEmpty = !occupiedLocations.contains(WebPartFactory.LOCATION_RIGHT);

        for (String regionName : regionNames)
        {
            if (!regionName.equals(WebPartFactory.LOCATION_RIGHT) || !rightEmpty)
            {
                //TODO: Make addPartView a real class & move to ProjectController
                AddWebParts addPart = new AddWebParts();
                addPart.pageId = id;
                addPart.location = regionName;
                addPart.rightEmpty = rightEmpty;
                addPart.scope = scope;

                HtmlView addPartView = new HtmlView(addWebPartWidgets(context,addPart));
                addPartView.setFrame(WebPartView.FrameType.NONE);
                addViewToRegion(template, regionName, addPartView);
            }
        }
    }

    public static HtmlString addWebPartWidgets(ViewContext viewContext, AddWebParts bean)
    {
        if (WebPartFactory.LOCATION_MENUBAR.equals(bean.location))
        {
            return addWebPartWidget(bean, viewContext, "", "pull-left");
        }
        else if (WebPartFactory.LOCATION_BODY.equals(bean.location))
        {
            if (bean.rightEmpty)
            {
                HtmlString leftWidget = addWebPartWidget(bean, viewContext, "", "pull-left");
                AddWebParts newBean = new AddWebParts();
                newBean.pageId = bean.pageId;
                newBean.location = WebPartFactory.LOCATION_RIGHT;
                newBean.rightEmpty = true;
                newBean.scope = bean.scope;
                // Add right webpart dropdown should be hidden on extra small screens
                HtmlString rightWidget = addWebPartWidget(newBean, viewContext, "hidden-xs", "pull-right");
                return HtmlString.unsafe(leftWidget.toString() + rightWidget.toString());
            }

            return addWebPartWidget(bean, viewContext, "visible-md-inline visible-lg-inline", "pull-left");
        }
        else if (WebPartFactory.LOCATION_RIGHT.equals(bean.location) && !bean.rightEmpty)
        {
            AddWebParts newBean = new AddWebParts();
            newBean.pageId = bean.pageId;
            newBean.location = WebPartFactory.LOCATION_BODY;
            newBean.rightEmpty = false;
            newBean.scope = bean.scope;
            HtmlString leftBottomWidget = addWebPartWidget(newBean, viewContext, "visible-xs-inline visible-sm-inline", "pull-left");
            // Add right webpart dropdown should be hidden on extra small screens
            HtmlString rightBottomWidget = addWebPartWidget(bean, viewContext, "visible-sm-inline", "pull-right");
            HtmlString rightMainWidget = addWebPartWidget(bean, viewContext, "visible-md-inline visible-lg-inline", "pull-left");

            return HtmlString.unsafe(leftBottomWidget.toString() + rightBottomWidget.toString() + rightMainWidget.toString());
        }
        else
        {
            // incorrect usage
            return HtmlString.EMPTY_STRING;
        }
    }

    private static HtmlString addWebPartWidget(AddWebParts bean, ViewContext viewContext, String visibilityClass, String pullClass)
    {
        Container c = viewContext.getContainer();
        ActionURL currentURL = viewContext.getActionURL();
        Set<String> partsSeen = new HashSet<>();

        List<Renderable> OPTIONS = new ArrayList<>();

        if (null != bean.scope && !"folder".equals(bean.scope))
        {
            Portal.getPartsToAdd(c, bean.scope, bean.location).forEach((displayName, name) -> {
                if (partsSeen.add(name))
                    OPTIONS.add(OPTION(at(value, name), displayName));
            });

            if (OPTIONS.size() > 0)
                OPTIONS.add(OPTION(at(value, ""), HR()));
        }

        Portal.getPartsToAdd(c, FOLDER_PORTAL_PAGE, bean.location).forEach((displayName, name) -> {
            if (partsSeen.add(name))
                OPTIONS.add(OPTION(at(value, name), displayName));
        });

        return createHtml(
                DIV(
                        LK.FORM(
                                at(method, "POST", action, urlProvider().getAddWebPartURL(c))
                                        .cl("form-inline").cl(pullClass).cl(visibilityClass),

                                new CsrfInput(viewContext),

                                INPUT(
                                        at(type, "hidden", name, "pageId", value, bean.pageId)
                                ),
                                INPUT(
                                        at(type, "hidden", name, "location", value, bean.location)
                                ),
                                ReturnUrlForm.generateHiddenFormField(currentURL),
                                DIV(
                                        cl("input-group"),
                                        SELECT(
                                                at(name, "name").cl("form-control"),
                                                OPTION(
                                                        at(value, ""),
                                                        "<Select Web Part>"),
                                                OPTIONS.stream()

                                        ),
                                        SPAN(
                                                cl("input-group-button"),
                                                new Button.ButtonBuilder("Add").submit(true).build()
                                        )
                                )
                        )
                )
        );
    }

    public static void addViewToRegion(HttpView<?> template, String regionName, HttpView<?> view)
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


    public static void populatePortalView(ViewContext context, String id, HttpView<?> template, boolean printView)
    {
        boolean canCustomize = context.getContainer().hasPermission("populatePortalView",context.getUser(), AdminPermission.class);
        populatePortalView(context, id, template, printView, canCustomize, false, true, FOLDER_PORTAL_PAGE);
    }

    public static void populatePortalView(ViewContext context, String id, HttpView<?> template, boolean printView,
                                          boolean canCustomize, boolean alwaysShowCustomize, boolean allowHideFrame)
    {
        populatePortalView(context, id, template, printView, canCustomize, false, true, FOLDER_PORTAL_PAGE);
    }


    public static int populatePortalView(ViewContext context, String id, HttpView<?> template, boolean printView,
                          boolean canCustomize, boolean alwaysShowCustomize, boolean allowHideFrame, String scope)
    {
        int count = 0;
        boolean showCustomize = alwaysShowCustomize || PageFlowUtil.isPageAdminMode(context);
        id = StringUtils.defaultString(id, DEFAULT_PORTAL_PAGE_ID);
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

        MultiValuedMap<String, WebPart> locationMap = getPartsByLocation(parts);
        Collection<String> locations = locationMap.keySet();

        for (String location : locations)
        {
            Collection<WebPart> partsForLocation = locationMap.get(location);
            int index = 0;

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
                if (canCustomize && !printView)
                {
                    if (desc.isEditable() && view.getCustomize() == null)
                        view.setCustomize(new NavTree("", getCustomizeURL(context, part)));

                    if (showCustomize)
                    {
                        if (index > 0)
                            navTree.addChild("Move Up", getMoveURL(context, part, MOVE_UP), null, "fa fa-caret-square-o-up labkey-fa-portal-nav");
                        else
                            navTree.addChild("Move Up", getMoveURL(context, part, MOVE_UP), null, "fa fa-caret-square-o-up labkey-btn-default-toolbar-small-disabled labkey-fa-portal-nav");

                        if (index < partsForLocation.size() - 1)
                            navTree.addChild("Move Down", getMoveURL(context, part, MOVE_DOWN), null, "fa fa-caret-square-o-down labkey-fa-portal-nav");
                        else
                            navTree.addChild("Move Down", getMoveURL(context, part, MOVE_DOWN), null, "fa fa-caret-square-o-down labkey-btn-default-toolbar-small-disabled labkey-fa-portal-nav");

                        if (!part.isPermanent())
                            navTree.addChild("Remove From Page", getDeleteURL(context, part), null, "fa fa-times");

                        // Only display Show/Hide frame options if the view is a PORTAL view when not being customized
                        if (allowHideFrame && WebPartView.FrameType.PORTAL.equals(view.getFrame()))
                        {
                            if (part.hasFrame())
                                navTree.addChild("Hide Frame", getToggleFrameURL(context, part), null, "fa fa-eye-slash");
                            else
                                navTree.addChild("Show Frame", getToggleFrameURL(context, part), null, "fa fa-eye");
                        }
                    }
                }

                // 36064: when customizing always use PORTAL frame, otherwise there's no menu to use to customize/remove the webpart
                if (showCustomize)
                {
                    view.setFrame(WebPartView.FrameType.PORTAL);
                    view.setTitle(StringUtils.defaultIfBlank(view.getTitle(), part.getName()));
                }
                else if (parts.size() == 1)
                {
                    if (printView)
                        view.setFrame(WebPartView.FrameType.NONE);
                    if (location.equals(HttpView.BODY))
                        view.setIsOnlyWebPartOnPage(true);
                }

                addViewToRegion(template, location, view);
                index++;
                count++;
            }
        }

        if (showCustomize && canCustomize && !printView)
            addCustomizeDropdowns(context, template, id, locations, scope);
        return count;
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

    @Nullable
    private static PortalPage getPortalPageDirect(Container container, String pageId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container).addCondition(FieldKey.fromString("pageId"), pageId);
        ArrayList<PortalPage> pages = new TableSelector(getTableInfoPortalPages(), filter, null).getArrayList(PortalPage.class);
        if (!pages.isEmpty())
            return pages.get(0);        // In rare cases there could be more than one.
        return null;
    }

    public static ActionURL getCustomizeURL(ViewContext context, Portal.WebPart webPart)
    {
        return urlProvider().getCustomizeWebPartURL(context.getContainer(), webPart, context.getActionURL());
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
            return urlProvider().getMoveWebPartURL(context.getContainer(), webPart, direction, context.getActionURL()).getLocalURIString();
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
            return urlProvider().getDeleteWebPartURL(context.getContainer(), webPart, context.getActionURL()).getLocalURIString();
    }

    public static String getToggleFrameURL(ViewContext context, Portal.WebPart webPart)
    {
        return "javascript:LABKEY.Portal.toggleWebPartFrame({" +
                "webPartId:" + webPart.getRowId() + "," +
                "updateDOM:false," +
                "success:function(){window.location.reload();}" +
                "})";
    }


    public static MultiValuedMap<String, WebPart> getPartsByLocation(Collection<WebPart> parts)
    {
        MultiValuedMap<String, WebPart> multiMap = new ArrayListValuedHashMap<>();

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

    @Override
    public void onModuleChanged(Module m)
    {
        // force releasing of WebPartFactory objects
        clearMaps();
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
        WebPartFactory existingFactory = getViewMap().get(name);
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

    static Map<String, String> getPartsToAdd(Container c, String scope, String location)
    {
        //TODO: Cache these?
        Map<String, String> webPartNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Module module : ModuleLoader.getInstance().getModules())
        {
            for (WebPartFactory factory : module.getWebPartFactories())
            {
                if (factory.isAvailable(c, scope, location))
                    webPartNames.put(factory.getDisplayName(c, location), factory.getName());
            }
        }

        return webPartNames;
    }

    public static WebPartView getWebPartViewSafe(@NotNull WebPartFactory factory, @NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        WebPartView view;

        try
        {
            view = factory.getWebPartView(portalCtx, webPart);
            if (view != null)
            {
                view.setWebPart(webPart);
                view.setLocation(webPart.getLocation());
                Map<String, String> props = webPart.getPropertyMap();
                if (null != props && props.containsKey("webpart.title"))
                    view.setTitle(props.get("webpart.title"));

                //find any matching navTreeCustomizers and inject their NavTree elements
                List<NavTreeCustomizer> customizers = _navTreeCustomizerMap.get(webPart.name);
                if (customizers != null)
                {
                    NavTree menu = view.getNavMenu();
                    if (menu == null)
                    {
                        menu = new NavTree();
                    }
                    for (NavTreeCustomizer customizer : customizers)
                    {
                        menu.addChildren(customizer.getNavTrees(portalCtx));
                    }
                    if (menu.getChildCount() > 0)
                    {
                        view.setNavMenu(menu);
                    }
                }
            }

            return view;
        }
        catch (BadRequestException x)
        {
            BindException errors = new BindException(new Object(), "form");
            errors.reject(SpringActionController.ERROR_MSG, x.getMessage());
            return new SimpleErrorView(errors,false);
        }
        catch(Throwable t)
        {
            WebPartView errorView;
            int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            String message = "An unexpected error occurred";
            errorView = ExceptionUtil.getErrorWebPartView(status, message, t, portalCtx.getRequest());
            errorView.setTitle(webPart.getName());
            errorView.setWebPart(webPart);
            return errorView;
        }

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
            filter.addCondition(tableInfo.getColumn("portalpageid"), page.getRowId());
            Table.delete(tableInfo, filter);
            Table.delete(getTableInfoPortalPages(), page.getRowId());
        }
        finally
        {
            WebPartCache.remove(ContainerManager.getForId(page.getContainer()));
        }
    }

    public static void deletePage(Container c, String pageId)
    {
        PortalPage page = WebPartCache.getPortalPage(c,pageId);
        if (null != page)
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
            Table.update(null, getTableInfoPortalPages(), page, page.getRowId());
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
        if (null != page)
        {
            try
            {
                page = page.copy();
                Table.update(null, getTableInfoPortalPages(), page, page.getRowId());
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
                s = new OptimisticConflictException(
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
        if (null == page)
            return;
        page = page.copy();
        page.setProperties(properties);
        _setProperties(page);
    }

    public static void addProperty(Container container, String pageId, String property)
    {
        Portal.PortalPage page = WebPartCache.getPortalPage(container, pageId);
        if (null == page)
            return;
        page = page.copy();
        page.setProperty(property, "true");
        _setProperties(page);
    }

    private static void _setProperties(PortalPage page)
    {
        try
        {
            Table.update(null, getTableInfoPortalPages(), page, page.getRowId());
        }
        finally
        {
            WebPartCache.remove(ContainerManager.getForId(page.getContainer()));
        }
    }

    public static void registerHomeProjectInitWebpart(WebPartFactory webPartFactory)
    {
        _homeWebParts.add(webPartFactory);
    }

    public static List<WebPartFactory> getHomeProjectInitWebparts()
    {
        return _homeWebParts;
    }

    public static class PortalPage implements Cloneable, Factory<PortalPage>
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
        private int rowId;

        private Map<String, String> propertyMap = new HashMap<>();
        private Map<Integer, WebPart> webparts = new LinkedHashMap<>();

        public PortalPage()
        {
        }

        /** copy constructor */
        public PortalPage(PortalPage copyFrom)
        {
            this(copyFrom, false);
        }

        protected PortalPage(PortalPage copyFrom, boolean readonly)
        {
            this.entityId = copyFrom.entityId;
            this.containerId = copyFrom.containerId;
            this.pageId = copyFrom.pageId;
            this.index = copyFrom.index;
            this.caption = copyFrom.caption;
            this.hidden = copyFrom.hidden;
            this.type = copyFrom.type;
            this.action = copyFrom.action;
            this.targetFolder = copyFrom.targetFolder;
            this.permanent = copyFrom.permanent;
            this.rowId = copyFrom.rowId;
            this.propertyMap.putAll(copyFrom.propertyMap);
            if (readonly)
                this.propertyMap = Collections.unmodifiableMap(propertyMap);
            // deep copy, note that .create() creates readonly copy
            for (WebPart wp : copyFrom.webparts.values())
                webparts.put(wp.index, (readonly ? wp.create() : new WebPart(wp)));
            if (readonly)
                this.webparts = Collections.unmodifiableMap(webparts);
        }

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

        @SuppressWarnings("unused")
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
        public Map<Integer, WebPart> getWebParts()
        {
            return webparts;
        }

        public PortalPage copy()
        {
            return new PortalPage(this);
        }

        public boolean isCustomTab()
        {
            String customTab = getProperty(Portal.PROP_CUSTOMTAB);
            if (null != customTab && customTab.equalsIgnoreCase("true"))
                return true;
            return false;
        }

        public int getRowId()
        {
            return rowId;
        }

        public void setRowId(int rowId)
        {
            this.rowId = rowId;
        }

        /** create a read only copy of this PortalPage suitable for caching */
        @Override
        public PortalPage create()
        {
            return new PortalPage(this, true)
            {
                @Override
                public PortalPage create()
                {
                    return this;
                }

                @Override
                public void setEntityId(GUID entityId)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setContainer(GUID containerId)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setPageId(String pageId)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setIndex(int index)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setCaption(String name)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setHidden(boolean hidden)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setType(String type)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setAction(String action)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setTargetFolder(GUID targetFolder)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setPermanent(boolean permanent)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setProperty(String k, String v)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setProperties(String query)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setRowId(int rowId)
                {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testModuleResourceCache()
        {
            // Load all the webpart definitions to ensure no exceptions
            int viewCount = WEB_PART_FACTORY_CACHE.streamAllResourceMaps()
                .mapToInt(Collection::size)
                .sum();

            LOG.info(viewCount + " webparts defined in all modules");

            // Make sure the cache retrieves the expected number of webpart definitions from the simpletest module, if present

            Module simpleTest = ModuleLoader.getInstance().getModule("simpletest");

            if (null != simpleTest)
                assertEquals("Webpart definitions from the simpletest module", 1, WEB_PART_FACTORY_CACHE.getResourceMap(simpleTest).size());
        }
    }

    /** Injects custom menu items for a portal by its web part name */
    public static void registerNavTreeCustomizer(String webPartName, NavTreeCustomizer navTreeCustomizer)
    {
        List<NavTreeCustomizer> customizers = _navTreeCustomizerMap.computeIfAbsent(webPartName, (name) -> new ArrayList<>());
        customizers.add(navTreeCustomizer);
    }
}
