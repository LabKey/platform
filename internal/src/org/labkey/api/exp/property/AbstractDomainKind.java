/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.ArrayList;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: kevink
 * Date: Jun 4, 2010
 * Time: 3:29:46 PM
 */
public abstract class AbstractDomainKind extends DomainKind
{
    @Override
    public String generateDomainURI(String schemaName, String queryName, Container container, User user)
    {
        return null;
    }


    public boolean canCreateDefinition(User user, Container container)
    {
        return false;
    }

    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, AdminPermission.class);
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        String domainURI = generateDomainURI(schemaName, queryName, container, user);
        if (domainURI == null)
            return null;

        ActionURL ret = PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(container, domainURI);
        ret.addParameter("createOrEdit", true);
        return ret;
    }

    // Override to customize the nav trail on shared pages like edit domain
    public void appendNavTrail(NavTree root, Container c, User user)
    {
    }

    // Do any special handling before a PropertyDescriptor is deleted -- do nothing by default
    public void deletePropertyDescriptor(Domain domain, User user, PropertyDescriptor pd)
    {
    }

    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user)
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
            // Collections.singletonList() is not GWT serializeable
            //return Collections.singletonList(e.getMessage());
            return new ArrayList<String>(Arrays.asList(e.getMessage()));
        }
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties()
    {
        return Collections.emptySet();
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices()
    {
        return Collections.emptySet();
    }

    @Override
    public DbScope getScope()
    {
        return null;
    }

    @Override
    public String getStorageSchemaName()
    {
        return null;
    }

    @Override
    public boolean hasRows(Domain domain)
    {
        try
        {
            // CONSIDER changing to EXISTS test or LIMIT 1 for performance
            SQLFragment sqlCount;
            if (getStorageSchemaName() == null)
            {
                SQLFragment sqlObjectIds = sqlObjectIdsInDomain(domain);
                sqlCount = new SQLFragment("SELECT COUNT(exp.object.objectId) AS value FROM exp.object WHERE exp.object.objectid IN (");
                sqlCount.append(sqlObjectIds);
                sqlCount.append(")");
            }
            else if (domain.getStorageTableName() != null)
            {
                String table = domain.getStorageTableName();
                sqlCount = new SQLFragment("SELECT COUNT(*) AS value FROM " + getStorageSchemaName() + "." + table);
            }
            else
            {
                return false;
            }

            Map[] maps = Table.executeQuery(ExperimentService.get().getSchema(), sqlCount, Map.class);
            return ((Number) maps[0].get("value")).intValue() != 0;
        }
        catch (SQLException x)
        {
            throw new RuntimeException(x);
        }
    }
}
