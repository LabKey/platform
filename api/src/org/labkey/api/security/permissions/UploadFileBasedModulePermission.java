package org.labkey.api.security.permissions;

public class UploadFileBasedModulePermission extends AbstractSitePermission
{
    public UploadFileBasedModulePermission()
    {
        super("Upload Module", "Allow updating file-based modules through the web-server user interface.");
    }
}