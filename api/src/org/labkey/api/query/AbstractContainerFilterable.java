/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.DbSchema;

/**
 * Utility base class that adds support for {@link ContainerFilter}s via the {@link ContainerFilterable} interface.
 * Created by: jeckels
 * Date: 1/31/16
 */
public abstract class AbstractContainerFilterable extends AbstractTableInfo implements ContainerFilterable
{
    @Nullable // if null, means default
    protected ContainerFilter _containerFilter;

    public AbstractContainerFilterable(DbSchema schema, String name)
    {
        super(schema, name);
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return true;
    }

    public void setContainerFilter(@NotNull ContainerFilter filter)
    {
        checkLocked();
        //noinspection ConstantConditions
        if (filter == null) // this really can happen, if other callers ignore warnings
            throw new IllegalArgumentException("filter cannot be null");
        if (!supportsContainerFilter())
            throw new IllegalArgumentException("container filter is not supported by " + this.getClass().getSimpleName());
        _setContainerFilter(filter);
    }

    /** Do the implementation-specific work of setting (and filtering) with the requested ContainerFilter */
    protected abstract void _setContainerFilter(ContainerFilter filter);

    protected String getContainerFilterColumn()
    {
        return "Container";
    }

    @NotNull
    public ContainerFilter getContainerFilter()
    {
        if (_containerFilter == null)
            return getDefaultContainerFilter();
        return _containerFilter;
    }

    protected ContainerFilter getDefaultContainerFilter()
    {
        return ContainerFilter.CURRENT;
    }

    /**
     * Returns true if the container filter has never been set on this table
     */
    public boolean hasDefaultContainerFilter()
    {
        return _containerFilter == null;
    }
}
