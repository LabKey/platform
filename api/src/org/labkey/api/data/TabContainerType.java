package org.labkey.api.data;

import org.labkey.api.admin.ImportContext;

public class TabContainerType extends NormalContainerType implements ContainerType
{
    public static final String NAME = "tab";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public boolean canHaveChildren()
    {
        return false;
    }

    @Override
    public boolean includeForImportExport(ImportContext context)
    {
        return true;
    }

    @Override
    public boolean shouldRemoveFromPortal()
    {
        return true;
    }

    @Override
    public boolean includePropertiesAsChild(boolean includeTabs)
    {
        return includeTabs;
    }

    @Override
    public boolean isInFolderNav()
    {
        return false;
    }

    @Override
    public Container getContainerFor(DataType dataType, Container currentContainer)
    {
        switch (dataType)
        {
            case fileRoot:
            case folderManagement:
            case tabParent:
                return currentContainer.getParent();
            default:
                return currentContainer;
        }
    }

}
