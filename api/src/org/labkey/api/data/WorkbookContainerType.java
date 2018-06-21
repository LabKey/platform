package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.ImportContext;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.labkey.api.data.ContainerType.DataType.protocol;

public class WorkbookContainerType implements ContainerType
{
    public static final String NAME = "workbook";

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
        return false;
    }

    @Override
    public boolean shouldRemoveFromPortal()
    {
        return false;
    }

    @Override
    public boolean includePropertiesAsChild(boolean includeTabs)
    {
        return false;
    }

    @Override
    public boolean isInFolderNav()
    {
        return false;
    }

    @Override
    public boolean isConvertibleToTab()
    {
        return false;
    }

    @Override
    public boolean canDeleteFromContainer(@NotNull Container currentContainer, @NotNull Container container)
    {
        return currentContainer.equals(container) || currentContainer.getParent().equals(container);
    }

    @Override
    public boolean canUpdateFromContainer(@NotNull Container currentContainer, @NotNull Container container)
    {
        return currentContainer.equals(container) || currentContainer.getParent().equals(container);
    }

    @Override
    public boolean canAdminFolder()
    {
        return false;
    }

    @Override
    public boolean requiresAdminToDelete()
    {
        return false;
    }

    @Override
    public boolean requiresAdminToCreate()
    {
        return false;
    }

    @Override
    public boolean isDuplicatedInContainerFilter()
    {
        return true;
    }

    @Override
    public String getContainerNoun(Container currentContainer)
    {
        return "workbook";
    }

    @Override
    public String getTitleFor(TitleContext context, Container currentContainer)
    {
        switch (context)
        {
            case appBar:
                return currentContainer.getTitle();
            case parentInNav:
                return currentContainer.getParent() != null ? currentContainer.getParent().getTitle() : currentContainer.getTitle();
            case childInNav:
                return currentContainer.getName();
            case importTarget:
                return currentContainer.getTitle();
            default:
                return currentContainer.getTitle();
        }
    }

    @Override
    public Container getContainerFor(DataType dataType, Container currentContainer)
    {
        switch (dataType)
        {
            //The intent is that outside of these blacklisted actions, return the current container (parent otherwise)
            case customQueryViews:
            case domainDefinitions:
            case dataspace:
            case fileAdmin:
            case navVisibility:
            case permissions:
            case properties:
            case protocol:
            case folderManagement:
            case fileRoot:
            case tabParent:
                return currentContainer.getParent();
            default:
                return currentContainer;
        }
    }

    @Override
    @NotNull
    public Set<Container> getContainersFor(DataType dataType, Container currentContainer)
    {
        Set<Container> containers = new LinkedHashSet<>();

        if (dataType == protocol)
        {
            containers.add(currentContainer);
            Container project = currentContainer.getProject();
            if (project != null)
            {
                containers.add(project);
            }

            containers.add(currentContainer.getParent());
            containers.add(ContainerManager.getSharedContainer());
        }

        return containers;
    }
}
