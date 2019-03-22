/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.PageFlowUtil;
import org.springframework.web.servlet.mvc.Controller;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A FolderTab is a UI element at the top of the page that allows users to navigate to other key pages.
 * They are backed by one of the {@link TAB_TYPE} values.
 * User: brittp
 * Date: Sep 15, 2011
 */
public abstract class FolderTab
{
    private final String _name;
    private String _caption;
    private boolean _isDefaultTab = false;
    protected Set<String> _legacyNames = new HashSet<>();
    protected int _defaultIndex = -1;

    public enum TAB_TYPE
    {
        /** A portal page that is configured with web parts */
        Portal,
        /** Backed by a child container */
        Container,
        /** A simple link to some arbitrary URL */
        Link
    }

    public TAB_TYPE getTabType()
    {
        return TAB_TYPE.Portal;         // Default is Portal; derived classes can override
    }

    public String getFolderTypeName()
    {
        return "";                      // Default is no folder type name; container tabs will have a folder type name
    }

    @Nullable
    public FolderType getFolderType()
    {
        return null;
    }

    /** Controllers and their child actions (both are Spring Controller classes) claimed by this tab */
    private Set<Class<? extends Controller>> _controllersAndActions = new HashSet<>();

    protected List<Class<? extends Permission>> _permissions;

    protected FolderTab(@NotNull String name)
    {
        this(name, name);
    }

    /**
     * @param name the stable, consistent name for this tab, regardless of any content in this folder
     * @param caption the title to be shown on the tab itself in the UI, which may vary based on configuration
     */
    protected FolderTab(@NotNull String name, @Nullable String caption)
    {
        _name = name;
        _caption = caption == null ? name : caption;
    }

    /** A tab backed by a portal page */
    public static abstract class PortalPage extends FolderTab
    {
        protected PortalPage(@NotNull String pageId, @Nullable String caption)
        {
            super(pageId, caption);
        }

        public ActionURL getURL(Container container, User user)
        {
            return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container, getName());
        }

        public boolean isSelectedPage(ViewContext viewContext)
        {
            if (super.isSelectedPage(viewContext))
            {
                return true;
            }

            ActionURL currentURL = viewContext.getActionURL();

            String pageName = currentURL.getParameter("pageId");
            if (pageName != null && getName().equalsIgnoreCase(pageName))
                return true;

              // Should not be necessary with the above direct pageId check, but leaving here in case we find a case (dave 11/7/12)
//            ActionURL tabURL = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(ContainerManager.getHomeContainer(), getName());
//            if (currentURL.getController().equalsIgnoreCase(tabURL.getController()) &&
//                currentURL.getAction().equalsIgnoreCase(tabURL.getAction()))
//            {
//                String pageName = currentURL.getParameter("pageId");
//                return getName().equalsIgnoreCase(pageName);
//            }
            return false;
        }

        public abstract List<Portal.WebPart> createWebParts();

        @Override
        public void initializeContent(Container container)
        {
            // Initialize the portal pages for each of the tabs
            List<Portal.WebPart> webParts = Portal.getParts(container, getDbName());

            if (webParts.size() == 0)
            {
                List<Portal.WebPart> parts = createWebParts();

                if (parts.size() > 0)
                {
                    Portal.saveParts(container, getDbName(), parts);
                }
            }
        }
    }

    /** Do any configuration needed on the container, such as setting up the default pages on a portal */
    public void initializeContent(Container container)
    {

    }

    protected void addController(Class<? extends Controller> controller)
    {
        _controllersAndActions.add(controller);
    }

    public boolean isSelectedPage(ViewContext viewContext)
    {
        if (viewContext.getActionURL().equals(getURL(viewContext.getContainer(), viewContext.getUser())))
        {
            return true;
        }

        if (_controllersAndActions.isEmpty())
        {
            return false;
        }

        // Map the current URL to the controller
        Module module = ModuleLoader.getInstance().getModuleForController(viewContext.getActionURL().getController());
        Controller controller = module.getController(viewContext.getRequest(), viewContext.getActionURL().getController());
        if (controller != null && _controllersAndActions.contains(controller.getClass()))
        {
            return true;
        }
        
        if (controller instanceof SpringActionController)
        {
            // Map the current URL to an action in the already resolved controller
            SpringActionController springController = (SpringActionController)controller;
            Controller action = springController.getActionResolver().resolveActionName(springController, viewContext.getActionURL().getAction());
            if (action != null && _controllersAndActions.contains(action.getClass()))
            {
                return true;
            }
        }
        return false;
    }

    public abstract ActionURL getURL(Container container, User user);

    public boolean isVisible(Container c, User u)
    {
        return canRead(u, c);
    }

    public String getDbName()
    {
        return _name;
    }

    public String getName()
    {
        return _name;
    }

    public String getCaption(@Nullable ViewContext viewContext)
    {
        return _caption;
    }

    protected void setCaption(String caption)
    {
        _caption = caption;
    }

    public Set<String> getLegacyNames()
    {
        return _legacyNames;
    }

    public boolean canRead(User u, Container c)
    {
        if (_permissions != null)
        {
            for (Class<? extends Permission> p : _permissions)
            {
                if (!c.hasPermission(u, p))
                    return false;
            }
        }

        return true;
    }

    public void setIsDefaultTab(boolean isDefault)
    {
        _isDefaultTab = isDefault;
    }

    public boolean isDefaultTab()
    {
        return _isDefaultTab;
    }

    public int getDefaultIndex()
    {
        return _defaultIndex;
    }

    public boolean isContainerTab()
    {
        return FolderTab.TAB_TYPE.Container == getTabType();
    }

    @Nullable
    public Container getContainerTab(Container parent, User user)
    {
        return null;
    }
}
