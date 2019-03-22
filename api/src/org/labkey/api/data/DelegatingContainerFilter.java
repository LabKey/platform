/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.GUID;

import java.util.Collection;
import java.util.Set;

/**
 * Useful when you need to match a ContainerFilter (say, across a lookup), but the desired ContainerFilter
 * may not yet be set on the base object.
 * User: jeckels
 * Date: Feb 4, 2009
 */
public class DelegatingContainerFilter extends ContainerFilter.ContainerFilterWithUser
{
    private final TableInfo _source;
    private final boolean _promoteWorkbooksToParentContainer;

    public DelegatingContainerFilter(TableInfo source)
    {
        this(source, false);
    }

    /** @param promoteWorkbooksToParentContainer if true, when evaluating this ContainerFilter, in workbooks use the parent
     *                                           container instead of the workbook container as the base. See issue 16596
     */
    public DelegatingContainerFilter(TableInfo source, boolean promoteWorkbooksToParentContainer)
    {
        super(null);
        _source = source;
        _promoteWorkbooksToParentContainer = promoteWorkbooksToParentContainer;
    }

    /**
     * If we are delegating to a ContainerFilterWithUser subclass, then be sure to apply the appropriate
     * permission.  See issue 19515
     */
    @Nullable
    public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> permission, Set<Role> roles)
    {
        currentContainer = getContainer(currentContainer);
        ContainerFilter cf = _source.getContainerFilter();
        if (cf instanceof ContainerFilterWithUser)
            return ((ContainerFilterWithUser)cf).getIds(currentContainer, permission, roles);

        return cf.getIds(currentContainer);
    }


    @Nullable
    public Collection<GUID> getIds(Container currentContainer)
    {
        currentContainer = getContainer(currentContainer);
        return _source.getContainerFilter().getIds(currentContainer);
    }

    @Nullable
    public Type getType()
    {
        return _source.getContainerFilter().getType();
    }

    private Container getContainer(Container currentContainer)
    {
        if (_promoteWorkbooksToParentContainer && currentContainer.isWorkbook())
            return currentContainer.getParent();

        return currentContainer;
    }

    @Override
    public String toString()
    {
        return "DelegatingContainerFilter: source=" + _source.getName();
    }
}
