/*
 * Copyright (c) 2013 LabKey Corporation
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
