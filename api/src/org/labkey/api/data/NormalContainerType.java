/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.labkey.api.data.ContainerType.DataType.protocol;

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
    public boolean includeForImportExport(FolderExportContext context)
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
    public boolean includeInAPIResponse()
    {
        return true;
    }

    @Override
    public boolean isConvertibleToTab()
    {
        return true;
    }

    @Override
    public boolean allowRowMutationFromContainer(Container primaryContainer, Container targetContainer)
    {
        return primaryContainer.equals(targetContainer);
    }

    @Override
    public Class<? extends Permission> getPermissionNeededToDelete()
    {
        return AdminPermission.class;
    }

    @Override
    public Class<? extends Permission> getPermissionNeededToCreate()
    {
        return AdminPermission.class;
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

        if (dataType == protocol)
        {
            containers.add(currentContainer);
            Container project = currentContainer.getProject();
            if (project != null)
                containers.add(project);
            containers.add(ContainerManager.getSharedContainer());
        }

        return containers;
    }
}
