package org.labkey.core.project;

import org.labkey.api.view.ViewContext;
import org.labkey.api.view.menu.FolderMenu;

/**
 * User: Nick
 * Date: 4/10/13
 */
public class FolderNavigationForm
{
    ViewContext _portalContext;
    FolderMenu _folderMenu;

    public FolderMenu getFolderMenu()
    {
        return _folderMenu;
    }

    public void setFolderMenu(FolderMenu folderMenu)
    {
        _folderMenu = folderMenu;
    }

    public ViewContext getPortalContext()
    {
        return _portalContext;
    }

    public void setPortalContext(ViewContext portalContext)
    {
        _portalContext = portalContext;
    }
}
