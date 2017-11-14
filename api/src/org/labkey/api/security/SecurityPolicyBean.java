/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.api.security;

import org.labkey.api.data.Container;

import java.util.Date;


/**
 * Simple bean object representing a policy over a {@link SecurableResource}. Consider using {@link SecurityPolicy} instead
 * which provides significantly more functionality.
 * User: Dave
 * Date: May 6, 2009
 */
public class SecurityPolicyBean
{
    private String _resourceId;
    private String _resourceClass;
    private Container _container;
    private Date _modified;

    public SecurityPolicyBean()
    {
    }

    public SecurityPolicyBean(String resourceId, String resourceClass, Container container)
    {
        _resourceId = resourceId;
        _resourceClass = resourceClass;
        _container = container;
    }

    public SecurityPolicyBean(String resourceId, String resourceClass, Container container, Date modified)
    {
        this(resourceId, resourceClass, container);
        _modified = modified;
    }

    public String getResourceId()
    {
        return _resourceId;
    }

    public void setResourceId(String resourceId)
    {
        _resourceId = resourceId;
    }

    public String getResourceClass()
    {
        return _resourceClass;
    }

    public void setResourceClass(String resourceClass)
    {
        _resourceClass = resourceClass;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }
}