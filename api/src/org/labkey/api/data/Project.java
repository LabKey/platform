/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import java.io.Serializable;

/**
 * Thin wrapper over a {@link Container} that is a project.
 * User: adam
 * Date: Apr 2, 2008
 */
public class Project implements Serializable
{
    private @NotNull Container _c;

    public Project(@NotNull Container c)
    {
        if (!c.isProject())
            throw new IllegalStateException(c.getPath() + " is not a project");

        _c = c;
    }

    public Container getContainer()
    {
        return _c;
    }

    public String getName()
    {
        return getContainer().getName();
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Project project = (Project) o;

        return _c.equals(project._c);
    }

    public int hashCode()
    {
        return _c.hashCode();
    }
}
