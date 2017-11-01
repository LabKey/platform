/*
 * Copyright (c) 2012-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.SimpleFolderType;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.data.xml.folderType.FolderTabDocument;
import org.labkey.data.xml.folderType.PermissionClassType;
import org.labkey.data.xml.folderType.SelectorDocument;
import org.labkey.data.xml.folderType.SelectorsDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * User: bbimber
 * Date: 8/17/12
 * Time: 1:21 PM
 */
public class SimpleFolderTab extends FolderTab.PortalPage
{
    private static final Logger LOGGER = Logger.getLogger(SimpleFolderTab.class);
    private List<Portal.WebPart> _requiredWebParts = new ArrayList<>();
    private List<Portal.WebPart> _preferredWebParts = new ArrayList<>();
    private List<TabSelector> _selectors = new ArrayList<>();
    private String _url = null;

    private TAB_TYPE _tabType = TAB_TYPE.Portal;        // default to Portal type
    private String _folderTypeName;
    private FolderType _folderType = null;

    @Override
    public TAB_TYPE getTabType()
    {
        return _tabType;
    }

    public SimpleFolderTab(String name, String caption)
    {
        super(name, caption);
    }

    @SuppressWarnings("unchecked")
    public SimpleFolderTab(FolderTabDocument.FolderTab tab, int defaultIndex)
    {
        super(tab.getName(), tab.getCaption());

        _defaultIndex = defaultIndex;

        //initialize from XML:
        String tabTypeString = StringUtils.trimToNull(tab.getTabType());
        if (tabTypeString != null)
        {
            if (tabTypeString.equalsIgnoreCase("container"))
            {
                _tabType = TAB_TYPE.Container;
                if (tab.getFolderType() != null)
                {
                    _folderTypeName = tab.getFolderType();
                }
            }
//            else if (tabTypeString.equalsIgnoreCase("link"))      // FUTURE
//                _tabType = TAB_TYPE.Link;
            else if (!tabTypeString.equalsIgnoreCase("portal"))
                LOGGER.error("Container tab type " + tabTypeString + " is not a recognized tab type.");
        }

        if (tab.getPreferredWebParts() != null)
        {
            _preferredWebParts = SimpleFolderType.createWebParts(tab.getPreferredWebParts().getWebPartArray());

        }

        if (tab.getRequiredWebParts() != null)
        {
            _requiredWebParts = SimpleFolderType.createWebParts(tab.getRequiredWebParts().getWebPartArray());
        }

        if (tab.getSelectorsArray() != null)
        {
            for (SelectorsDocument.Selectors s : tab.getSelectorsArray())
            {
                for (SelectorDocument.Selector selector : s.getSelectorArray())
                {
                    String view = selector.isSetView() ? selector.getView() : null;
                    String controller = selector.isSetController() ? selector.getController() : null;
                    String regex = selector.isSetRegex() ? selector.getRegex() : null;
                    _selectors.add(new TabSelector(controller, view, regex));
                }
            }
        }

        if (tab.isSetPermissions())
        {
            List<Class<? extends Permission>> permissions = new ArrayList<>();
            for (PermissionClassType permEntry : tab.getPermissions().getPermissionArray())
            {
                String permClassName = permEntry.getName();
                if (null != permClassName)
                {
                    try
                    {
                        Class permClass = Class.forName(permClassName);
                        if (Permission.class.isAssignableFrom(permClass))
                        {
                            permissions.add(permClass);
                        }
                        else
                        {
                            LOGGER.error("Improper permission class: " + permClassName + ", for folder type: " + getFolderType() + ", tab: " + getName());
                        }
                    }
                    catch (ClassNotFoundException e)
                    {
                        // ignore
                        LOGGER.error("Unable to find class for permission name: " + permClassName + ", for folder type: " + getFolderType() + ", tab: " + getName());
                    }
                }
            }

            if (permissions.size() > 0)
                _permissions = permissions;
        }

        if (tab.isSetUrl())
        {
            _url = tab.getUrl();
        }
    }

    @Override
    public boolean isSelectedPage(ViewContext viewContext)
    {
        ActionURL currentURL = viewContext.getActionURL();

        String pageName = currentURL.getParameter("pageId");
        if (pageName != null && getName().equalsIgnoreCase(pageName))
            return true;

        for (TabSelector ts : _selectors)
        {
            if (ts.matchesUrl(currentURL))
                return true;
        }
        return false;
    }

    @Override
    public List<Portal.WebPart> createWebParts()
    {
        List<Portal.WebPart> parts = new ArrayList<>();

        for (Portal.WebPart wp : _requiredWebParts)
        {
            parts.add(wp);
        }

        for (Portal.WebPart wp : _preferredWebParts)
        {
            parts.add(wp);
        }

        return parts;
    }

    private class TabSelector
    {
        String _viewName;
        String _controller;
        String _regex;

        public TabSelector(String controller, String viewName, String regex)
        {
            _controller = controller;
            _viewName = viewName;
            _regex = regex;
        }

        public boolean matchesUrl(ActionURL url)
        {
            if (_viewName != null && !url.getAction().equalsIgnoreCase(_viewName))
                return false;
            if (_controller != null && !url.getController().equalsIgnoreCase(_controller))
                return false;
            if (_regex != null && !url.toString().matches(_regex))
                return false;

            return true;
        }
    }

    @Override
    public Container getContainerTab(Container parent, User user)
    {
        if (TAB_TYPE.Container == getTabType())
        {
            Container container = ContainerManager.getChild(parent, getName());
            if (null == container)
            {
                if (!ContainerManager.hasContainerTabBeenDeleted(parent, getName(), parent.getFolderType().getName()))
                {
                    container = ContainerManager.createContainer(parent, getName(), null, null, Container.TYPE.tab, user);
                    FolderType folderType = getFolderType();
                    if (null != folderType)
                        container.setFolderType(folderType, user);
                }
            }
            else
            {
                // Make sure to clear; if different folder types have the same container tab name, flag could become stale
                ContainerManager.clearContainerTabDeleted(parent, getName(), parent.getFolderType().getName());
            }
            return container;
        }
        return null;
    }

    @Override
    public String getFolderTypeName()
    {
        return _folderTypeName;
    }

    @Nullable
    @Override
    public FolderType getFolderType()
    {
        if (null == _folderType && null != getFolderTypeName())
        {
            return FolderTypeManager.get().getFolderType(getFolderTypeName());
        }
        return _folderType;
    }

    @Override
    public ActionURL getURL(Container container, User user)
    {
        if (null != _url)
        {
            DetailsURL detailsURL = DetailsURL.fromString(_url);
            detailsURL.setContainerContext(container);
            return detailsURL.getActionURL();
        }
        return super.getURL(container, user);
    }
}
