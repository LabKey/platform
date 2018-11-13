/*
 * Copyright (c) 2018 LabKey Corporation
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
    public boolean allowRowMutationFromContainer(Container primaryContainer, Container targetContainer)
    {
        //Issue 15301: allow workbooks records to be deleted/updated from the parent container
        return primaryContainer.equals(targetContainer) || targetContainer.getParent().equals(primaryContainer);
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
            case sharedSchemaOwner:
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
