/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.api.exp.property;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.NavTree;

import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: kevink
 * Date: Jun 4, 2010
 * Time: 3:29:46 PM
 */
public abstract class AbstractDomainKind extends DomainKind
{
    public boolean canCreateDefinition(User user, Container container)
    {
        return false;
    }

    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, AdminPermission.class);
    }

    // Override to customize the nav trail on shared pages like edit domain
    public void appendNavTrail(NavTree root, Container c)
    {
    }

    // Do any special handling before a PropertyDescriptor is deleted -- do nothing by default
    public void deletePropertyDescriptor(Domain domain, User user, PropertyDescriptor pd)
    {
    }

    public Domain createDomain(GWTDomain domain, JSONObject arguments, Container container, User user)
    {
        return null;
    }

    public List<String> updateDomain(GWTDomain original, GWTDomain update, Container container, User user)
    {
        try
        {
            return DomainUtil.updateDomainDescriptor(original, update, container, user);
        }
        catch (ChangePropertyDescriptorException e)
        {
            return Collections.singletonList(e.getMessage());
        }
    }
}
