/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

import java.util.Collection;

/**
 * User: jeckels
 * Date: Feb 4, 2009
 */
public class DelegatingContainerFilter extends ContainerFilter
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
        _source = source;
        _promoteWorkbooksToParentContainer = promoteWorkbooksToParentContainer;
    }

    @Nullable
    public Collection<String> getIds(Container currentContainer)
    {
        if (_promoteWorkbooksToParentContainer && currentContainer.isWorkbook())
        {
            currentContainer = currentContainer.getParent();
        }
        return _source.getContainerFilter().getIds(currentContainer);
    }

    @Nullable
    public Type getType()
    {
        return _source.getContainerFilter().getType();
    }
}
