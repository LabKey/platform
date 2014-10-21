package org.labkey.api.admin;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * User: vsharma
 * Date: 9/2/2014
 * Time: 1:58 PM
 */
public class FolderExportPermission extends AbstractPermission
{
    public FolderExportPermission()
    {
        super("Export Folder", "May export a folder.");
    }
}