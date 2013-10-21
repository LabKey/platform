/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
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
import org.labkey.api.writer.ContainerUser;
import org.labkey.list.controllers.ListController;
import org.labkey.list.view.ListItemAttachmentParent;

import java.sql.SQLException;
import java.util.Set;

@Deprecated
/* package */ public class ListDomainType extends AbstractDomainKind
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
        return new SQLFragment("NULL");
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

    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        ListDefinition listDef = ListService.get().getList(domain);
        if (listDef == null)
            return null;
        return listDef.urlShowData();
    }

    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        ListDefinition listDef = ListService.get().getList(domain);
        if (listDef == null)
            return null;
        return listDef.urlShowDefinition();
    }

    @Override
    public void deletePropertyDescriptor(Domain domain, final User user, PropertyDescriptor pd)
    {
        if (pd.getPropertyType() != PropertyType.ATTACHMENT)
            return;

        final ListDefinition list = ListService.get().getList(domain);
        TableInfo tinfo = list.getTable(user);

        if (null != tinfo)
        {
            final Container c = list.getContainer();
            final DomainProperty prop = domain.getPropertyByName(pd.getName());

            new TableSelector(tinfo.getColumn(list.getKeyName())).forEach(new Selector.ForEachBlock<Object>() {
                @Override
                public void exec(Object key) throws SQLException
                {
                    ListItem item = list.getListItem(key, user);
                    Object file = item.getProperty(prop);

                    if (null != file)
                    {
                        AttachmentParent parent = new ListItemAttachmentParent(item, c);
                        // Not auditing individual file deletions.  Not sure this is correct.
                        AttachmentService.get().deleteAttachment(parent, file.toString(), null);
                    }
                }
            }, Object.class);
        }
    }

    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return PageFlowUtil.set("ObjectId", "EntityId", "Created", "CreatedBy", "Modified", "ModifiedBy", "LastIndexed");
    }
}
