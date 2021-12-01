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

import org.labkey.api.admin.ImportContext;

public class TabContainerType extends NormalContainerType
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
            case tabParent:
                return currentContainer.getParent();
            default:
                return currentContainer;
        }
    }

}
