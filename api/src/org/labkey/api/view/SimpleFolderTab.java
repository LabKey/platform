package org.labkey.api.view;

import org.apache.log4j.Logger;
import org.labkey.api.module.SimpleFolderType;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.data.xml.PermissionType;
import org.labkey.data.xml.folderType.FolderTabDocument;
import org.labkey.data.xml.folderType.SelectorDocument;
import org.labkey.data.xml.folderType.SelectorsDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 8/17/12
 * Time: 1:21 PM
 */
public class SimpleFolderTab extends FolderTab.PortalPage
{
    private List<Portal.WebPart> _requiredWebParts = new ArrayList<Portal.WebPart>();
    private List<Portal.WebPart> _preferredWebParts = new ArrayList<Portal.WebPart>();
    private List<TabSelector> _selectors = new ArrayList<TabSelector>();

    public SimpleFolderTab(String name, String caption)
    {
        super(name, caption);
    }

    public SimpleFolderTab(FolderTabDocument.FolderTab tab, Logger log)
    {
        super(tab.getName(), tab.getCaption());

        if (tab.getPreferredWebParts() != null)
        {
            _preferredWebParts = SimpleFolderType.createWebParts(tab.getPreferredWebParts().getWebPartArray(), log);

        }

        if (tab.getRequiredWebParts() != null)
        {
            _requiredWebParts = SimpleFolderType.createWebParts(tab.getRequiredWebParts().getWebPartArray(), log);
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
            List<Class<? extends Permission>> permissions = new ArrayList<Class<? extends Permission>>();
            for (PermissionType.Enum permEntry : tab.getPermissions().getPermissionArray())
            {
                org.labkey.api.security.SecurityManager.PermissionTypes perm = SecurityManager.PermissionTypes.valueOf(permEntry.toString());
                Class<? extends Permission> permClass = perm.getPermission();
                if (permClass != null)
                    permissions.add(permClass);
            }

            if (permissions.size() > 0)
                _permissions = permissions;
        }
    }

    @Override
    public boolean isSelectedPage(ViewContext viewContext)
    {
        //TODO: URL param??
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
        List<Portal.WebPart> parts = new ArrayList<Portal.WebPart>();

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
}
