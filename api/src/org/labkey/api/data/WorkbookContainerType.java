package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.ImportContext;

import java.util.HashSet;
import java.util.Set;

import static org.labkey.api.data.ContainerType.DataType.assayProtocols;
import static org.labkey.api.data.ContainerType.DataType.inventory;
import static org.labkey.api.data.ContainerType.DataType.list;
import static org.labkey.api.data.ContainerType.DataType.sharedDataTable;

public class WorkbookContainerType implements ContainerType
{
    public static final String NAME = "workbook";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public Boolean canHaveChildren()
    {
        return false;
    }

    @Override
    public Boolean includeForImportExport(ImportContext context)
    {
        return false;
    }

    @Override
    public Boolean shouldRemoveFromPortal()
    {
        return false;
    }

    @Override
    public Boolean includePropertiesAsChild(boolean includeTabs)
    {
        return false;
    }

    @Override
    public Boolean isInFolderNav()
    {
        return false;
    }

    @Override
    public Boolean isConvertibleToTab()
    {
        return false;
    }

    @Override
    public Boolean canDeleteFromContainer(Container currentContainer, Container container)
    {
        return currentContainer.getParent().equals(container);
    }

    @Override
    public Boolean canUpdateFromContainer(Container currentContainer, Container container)
    {
        return currentContainer.getParent().equals(container);
    }

    @Override
    public Boolean canAdminFolder()
    {
        return false;
    }

    @Override
    public Boolean requiresAdminToDelete()
    {
        return false;
    }

    @Override
    public Boolean requiresAdminToCreate()
    {
        return false;
    }

    @Override
    public Boolean isDuplicatedInContainerFilter()
    {
        return true;
    }

    @Override
    public Boolean parentDataIsRelevant(DataType dataType)
    {
        return dataType == inventory || dataType == list;
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
    public Boolean isContainerFor(DataType dataType)
    {
        switch (dataType)
        {
            case assays:
            case assayProtocols:
            case folderManagement:
            case inventory:
            case sharedDataTable:
                return true;
            default:
                return false;
        }
    }

    @Override
    public Container getContainerFor(DataType dataType, Container currentContainer)
    {
        switch (dataType)
        {
            case assays: ;
                // for workbooks, use the parent folder as the current folder (unless it happens to be the project)
                Container container = currentContainer.getParent();
                if (container != null && container.isProject())
                    container = null;
                return container;
            case assayProtocols:
            case dataspace:
            case folderManagement:
            case inventory:
                return currentContainer;
            default:
                return currentContainer.getParent();
        }
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
            {
                containers.add(project);
            }

            containers.add(currentContainer.getParent());
        }
        else if (dataType == sharedDataTable)
        {
            containers.add(ContainerManager.getSharedContainer());
            containers.add(currentContainer.getParent());
        }
        return containers;
    }
}
