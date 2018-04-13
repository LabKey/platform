package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.ImportContext;

import java.util.HashSet;
import java.util.Set;

import static org.labkey.api.data.ContainerType.DataType.assayProtocols;
import static org.labkey.api.data.ContainerType.DataType.sharedDataTable;

public class NormalContainerType implements ContainerType
{
    public static final String NAME = "normal";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public Boolean canHaveChildren()
    {
        return true;
    }

    @Override
    public Boolean includeForImportExport(ImportContext context)
    {
       return context.isIncludeSubfolders();
    }

    @Override
    public Boolean shouldRemoveFromPortal()
    {
        return true;
    }

    @Override
    public Boolean includePropertiesAsChild(boolean includeTabs)
    {
        return true;
    }

    @Override
    public Boolean isInFolderNav()
    {
        return true;
    }

    @Override
    public Boolean isConvertibleToTab()
    {
        return true;
    }

    @Override
    public Boolean canDeleteFromContainer(Container currentContainer, Container container)
    {
        return false;
    }

    @Override
    public Boolean canUpdateFromContainer(Container currentContainer, Container container)
    {
        return false;
    }

    @Override
    public Boolean canAdminFolder()
    {
        return true;
    }

    @Override
    public Boolean requiresAdminToDelete()
    {
        return true;
    }

    @Override
    public Boolean requiresAdminToCreate()
    {
        return true;
    }

    @Override
    public Boolean isDuplicatedInContainerFilter()
    {
        return false;
    }

    @Override
    public Boolean parentDataIsRelevant(DataType dataType)
    {
        return false;
    }

    // The methods below require the current container
    @Override
    public String getTitleFor(TitleContext tContext, Container currentContainer)
    {
        switch (tContext)
        {
            case appBar:
                return currentContainer.getName();
            case parentInNav:
                return currentContainer.getTitle();
            case childInNav:
                return currentContainer.getTitle();
            case importTarget:
                return currentContainer.getPath();
            default:
                return currentContainer.getTitle();
        }
    }

    @Override
    public String getContainerNoun(Container currentContainer)
    {
        return currentContainer.isProject() ? "project" : "folder";
    }

    @Override
    public Boolean isContainerFor(DataType dataType)
    {
        return true;
    }

    @Override
    public Container getContainerFor(DataType dataType, Container currentContainer)
    {
        return currentContainer;
    }

    @Override
    @NotNull
    public Set<Container> getContainersFor(DataType dataType, Container currentContainer)
    {
        Set<Container> containers = new HashSet<>();

        if (dataType == assayProtocols)
        {
            containers.add(currentContainer);
            Container project = currentContainer.getProject();
            if (project != null)
                containers.add(project);
        }
        else if (dataType == sharedDataTable)
        {
            containers.add(ContainerManager.getSharedContainer());
        }
        return containers;
    }
}
