/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

package org.labkey.list.model;

import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.*;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.list.view.ListController;
import org.labkey.list.view.ListItemAttachmentParent;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;

public class ListDomainType extends AbstractDomainKind
{
    public String getTypeLabel(Domain domain)
    {
        return "List '" + domain.getName() + "'";
    }

    @Override
    public String getKindName()
    {
        return ListDefinitionImpl.NAMESPACE_PREFIX;
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return ListDefinitionImpl.NAMESPACE_PREFIX.equals(lsid.getNamespacePrefix()) ? Priority.MEDIUM : null;
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, DesignListPermission.class);
    }

    @Override
    public void appendNavTrail(NavTree root, Container c, User user)
    {
        ListController.appendRootNavTrail(root, c, user);
    }

    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        ListDefinitionImpl listDef = (ListDefinitionImpl) ListService.get().getList(domain);
        if (listDef == null)
            return new SQLFragment("NULL");
        SQLFragment ret = new SQLFragment("SELECT IndexTable.ObjectId FROM ");
        ret.append(listDef.getIndexTable().getFromSQL("IndexTable"));
        ret.append("\nWHERE IndexTable.listId = ").append(String.valueOf(listDef.getListId()));
        return ret;
    }

    public static Lsid generateDomainURI(String name, Container c, String entityId)
    {
        String typeURI = "urn:lsid:" + PageFlowUtil.encode(AppProps.getInstance().getDefaultLsidAuthority()) + ":" + ListDefinitionImpl.NAMESPACE_PREFIX + ".Folder-" + c.getRowId() + ":" + PageFlowUtil.encode(name);
        //bug 13131.  uniqueify the lsid for situations where a preexisting list was renamed
        int i = 1;
        String uniqueURI = typeURI;
        while (OntologyManager.getDomainDescriptor(uniqueURI, c) != null)
        {
            uniqueURI = typeURI + '-' + (i++);
        }
        return new Lsid(uniqueURI);
    }

    public static Lsid generateDomainURI(String name, Container container)
    {
        return generateDomainURI(name, container, "");
    }

    public String generateDomainURI(String schemaName, String name, Container container, User user)
    {
        return generateDomainURI(name, container).toString();
    }

    public ActionURL urlShowData(Domain domain)
    {
        ListDefinition listDef = ListService.get().getList(domain);
        if (listDef == null)
            return null;
        return listDef.urlShowData();
    }

    public ActionURL urlEditDefinition(Domain domain)
    {
        ListDefinition listDef = ListService.get().getList(domain);
        if (listDef == null)
            return null;
        return listDef.urlShowDefinition();
    }

    @Override
    public void deletePropertyDescriptor(Domain domain, User user, PropertyDescriptor pd)
    {
        if (pd.getPropertyType() != PropertyType.ATTACHMENT)
            return;

        ListDefinition list = ListService.get().getList(domain);
        Container c = list.getContainer();
        TableInfo tinfo = list.getTable(user);
        DomainProperty prop = domain.getPropertyByName(pd.getName());

        try
        {
            Object[] keys = Table.executeArray(tinfo, tinfo.getColumn(list.getKeyName()), null, null, Object.class);

            for (Object key : keys)
            {
                ListItem item = list.getListItem(key);
                Object file = item.getProperty(prop);

                if (null != file)
                {
                    AttachmentParent parent = new ListItemAttachmentParent(item, c);
                    // Not auditing individual file deletions.  Not sure this is correct.
                    AttachmentService.get().deleteAttachment(parent, file.toString(), null);
                }
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return Collections.emptySet();
    }
}
