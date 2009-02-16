/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.security.User;

import java.util.Collection;

/**
 * User: jeckels
 * Date: Feb 4, 2009
 */
public class DelegatingContainerFilter implements ContainerFilter
{
    private ContainerFilterable _source;

    public DelegatingContainerFilter(ContainerFilterable source)
    {
        _source = source;
    }

    public Collection<String> getIds(Container currentContainer, User user)
    {
        return _source.getContainerFilter().getIds(currentContainer, user);
    }

    public boolean isPublicFilter()
    {
        return _source.getContainerFilter().isPublicFilter();
    }

    public String name()
    {
        return _source.getContainerFilter().name();
    }
}
