/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.list.view.ListItemAttachmentParent;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class ListDomainType extends DomainKind
{
    static public final ListDomainType instance = new ListDomainType();

    public String getTypeLabel(Domain domain)
    {
        return "List '" + domain.getName() + "'";
    }

    public boolean isDomainType(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return "List".equals(lsid.getNamespacePrefix());
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, DesignListPermission.class);
    }

    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        ListDefinitionImpl listDef = (ListDefinitionImpl) ListService.get().getList(domain);
        if (listDef == null)
            return null;
        SQLFragment ret = new SQLFragment("SELECT IndexTable.ObjectId FROM (");
        ret.append(listDef.getIndexTable().getFromSQL());
        ret.append(") IndexTable WHERE IndexTable.listId = " + listDef.getListId());
        return ret;
    }

//    public String generateDomainURI(Container container, String name)
//    {
//        String str = "urn:lsid:" + AppProps.getInstance().getDefaultLsidAuthority() + ":List" + ".Folder-" + container.getRowId() + ":" + name;
//        return new Lsid(str).toString();
//    }

    public Map.Entry<TableInfo, ColumnInfo> getTableInfo(User user, Domain domain, Container[] containers)
    {
        return null;
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
                    AttachmentService.get().deleteAttachment(parent, file.toString());
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
