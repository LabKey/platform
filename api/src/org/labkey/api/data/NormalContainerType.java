package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.ImportContext;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.labkey.api.data.ContainerType.DataType.assayProtocols;
import static org.labkey.api.data.ContainerType.DataType.protocol;
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
    public boolean canHaveChildren()
    {
        return true;
    }

    @Override
    public boolean includeForImportExport(ImportContext context)
    {
       return context.isIncludeSubfolders();
    }

    @Override
    public boolean shouldRemoveFromPortal()
    {
        return true;
    }

    @Override
    public boolean includePropertiesAsChild(boolean includeTabs)
    {
        return true;
    }

    @Override
    public boolean isInFolderNav()
    {
        return true;
    }

    @Override
    public boolean isConvertibleToTab()
    {
        return true;
    }

    @Override
    public boolean canDeleteFromContainer(Container currentContainer, Container container)
    {
        return currentContainer == container;
    }

    @Override
    public boolean canUpdateFromContainer(Container currentContainer, Container container)
    {
        return currentContainer == container;
    }

    @Override
    public boolean canAdminFolder()
    {
        return true;
    }

    @Override
    public boolean requiresAdminToDelete()
    {
        return true;
    }

    @Override
    public boolean requiresAdminToCreate()
    {
        return true;
    }

    @Override
    public boolean isDuplicatedInContainerFilter()
    {
        return false;
    }

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
    public Container getContainerFor(DataType dataType, Container currentContainer)
    {
        return currentContainer;
    }

    @Override
    @NotNull
    public Set<Container> getContainersFor(DataType dataType, Container currentContainer)
    {
        Set<Container> containers = new LinkedHashSet<>();

        if (dataType == assayProtocols)
        {
            containers.add(currentContainer);
            Container project = currentContainer.getProject();
            if (project != null)
                containers.add(project);
            containers.add(ContainerManager.getSharedContainer());
        }
        else if (dataType == protocol)
        {
            containers.add(currentContainer);
            containers.add(currentContainer.getProject());
            containers.add(ContainerManager.getSharedContainer());
        }
        else if (dataType == sharedDataTable)
        {
            containers.add(ContainerManager.getSharedContainer());
        }
        return containers;
    }
}
