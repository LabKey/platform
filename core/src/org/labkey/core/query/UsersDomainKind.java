/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.core.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SimpleModule;
import org.labkey.api.query.SimpleTableDomainKind;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 9/18/12
 */
public class UsersDomainKind extends SimpleTableDomainKind
{
    public static final String NAME = "CoreUsersTable";
    private static final Set<String> _reservedNames = new HashSet<>();

    static {
        _reservedNames.add("Email");
        _reservedNames.add("_ts");
        _reservedNames.add("EntityId");
        _reservedNames.add("CreatedBy");
        _reservedNames.add("Created");
        _reservedNames.add("ModifiedBy");
        _reservedNames.add("Modified");
        _reservedNames.add("Owner");
        _reservedNames.add("UserId");
        _reservedNames.add("DisplayName");
        _reservedNames.add("LastLogin");
        _reservedNames.add("Active");
    }

    @Override
    public String getKindName()
    {
        return NAME;
    }

    public static Container getDomainContainer()
    {
        return ContainerManager.getSharedContainer();
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        return super.urlCreateDefinition(schemaName, queryName, getDomainContainer(), user);
    }

    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(containerUser.getContainer(), domain.getTypeURI(), false, false, false);
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
/*
        ActionURL url = super.urlShowData(domain, containerUser);
        return url.setContainer(containerUser.getContainer());
*/
        return PageFlowUtil.urlProvider(UserUrls.class).getSiteUsersURL();
    }

    @Override
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user)
    {
        return super.createDomain(domain, arguments, getDomainContainer(), user);
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return _reservedNames;
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        SimpleUserSchema.SimpleTable table = getTable(domain);
        if (table != null)
        {
            ColumnInfo objectUriColumn = table.getObjectUriColumn();
            if (objectUriColumn != null)
            {
                TableInfo schemaTable = table.getRealTable();

                SQLFragment sql = new SQLFragment();
                sql.append("SELECT o.ObjectId FROM " + schemaTable + " me, exp.object o WHERE me." + objectUriColumn.getSelectName() + " = o.ObjectURI");
                return sql;
            }
        }
        return new SQLFragment("NULL");
    }

    /**
     * Returns the set of built in columns that are managed by property descriptors
     * @return
     */
    public Set<String> getWrappedColumns()
    {
        Set<String> columns = new HashSet<>();

        columns.add("FirstName");
        columns.add("LastName");
        columns.add("Phone");
        columns.add("Mobile");
        columns.add("Pager");
        columns.add("IM");
        columns.add("Description");

        return columns;
    }

    public static void ensureDomain(ModuleContext context)
    {
        User user = context.getUpgradeUser();
        String domainURI = UsersDomainKind.getDomainURI("core", CoreQuerySchema.USERS_TABLE_NAME, UsersDomainKind.getDomainContainer(), user);
        Domain domain = PropertyService.get().getDomain(UsersDomainKind.getDomainContainer(), domainURI);


        if (domain == null)
        {
            try
            {
                domain = PropertyService.get().createDomain(UsersDomainKind.getDomainContainer(), domainURI, CoreQuerySchema.USERS_TABLE_NAME);

                createPropertyDescriptor(domain, user, "FirstName", PropertyType.STRING, 64, false);
                createPropertyDescriptor(domain, user, "LastName", PropertyType.STRING, 64, false);
                createPropertyDescriptor(domain, user, "Phone", PropertyType.STRING, 64, false);
                createPropertyDescriptor(domain, user, "Mobile", PropertyType.STRING, 64, false);
                createPropertyDescriptor(domain, user, "Pager", PropertyType.STRING, 64, true);
                createPropertyDescriptor(domain, user, "IM", PropertyType.STRING, 64, true);
                createPropertyDescriptor(domain, user, "Description", PropertyType.STRING, 255, true);

                domain.save(context.getUpgradeUser());
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public static void ensureDomainPropertyScales(Domain domain, User user)
    {
        DbScope scope = CoreSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            Map<String, DomainProperty> existingProps = new HashMap<>();
            boolean dirty = false;

            if (user == null)
                user = UserManager.getGuestUser();

            for (DomainProperty dp : domain.getProperties())
            {
                existingProps.put(dp.getName(), dp);
            }


            dirty = (ensurePropertyDescriptorScale(existingProps.get("FirstName"), 64)|| dirty);
            dirty = (ensurePropertyDescriptorScale(existingProps.get("LastName"), 64) || dirty);
            dirty = (ensurePropertyDescriptorScale(existingProps.get("Phone"), 64)    || dirty);
            dirty = (ensurePropertyDescriptorScale(existingProps.get("Mobile"), 64)   || dirty);
            dirty = (ensurePropertyDescriptorScale(existingProps.get("Pager"), 64)    || dirty);
            dirty = (ensurePropertyDescriptorScale(existingProps.get("IM"), 64)       || dirty);
            dirty = (ensurePropertyDescriptorScale(existingProps.get("Description"), 255) || dirty);

            if (dirty)
                domain.save(user);

            transaction.commit();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private static void createPropertyDescriptor(Domain domain, User user, String name, PropertyType type, int scale, boolean hidden)
    {
        String propertyURI = UsersDomainKind.createPropertyURI("core", CoreQuerySchema.USERS_TABLE_NAME, getDomainContainer(), user);

        DomainProperty prop = domain.addProperty();
        prop.setName(name);
        prop.setType(PropertyService.get().getType(domain.getContainer(), type.getXmlName()));
        prop.setScale(scale);
        prop.setPropertyURI(propertyURI);

        if (hidden)
        {
            prop.setShownInDetailsView(false);
            prop.setShownInInsertView(false);
            prop.setShownInUpdateView(false);
            prop.setHidden(true);
        }
    }

    private static boolean ensurePropertyDescriptorScale(DomainProperty prop, int scale)
    {
        if (null != prop && prop.getScale() != scale)
        {
            prop.setScale(scale);
            return true;
        }
        return false;
    }

    /**
     * Called to determine if a property descriptor can be marked as required. In the user table case we always want to allow
     * this so a user can be redirected at login to furnish newly required fields.
     */
    @Override
    public boolean hasNullValues(Domain domain, DomainProperty prop)
    {
        return false;
    }

    @Override
    public Set<String> getMandatoryPropertyNames(Domain domain)
    {
        return getWrappedColumns();
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        String namespacePrefix = lsid.getNamespacePrefix();
        String objectId = lsid.getObjectId();
        if (namespacePrefix == null || objectId == null)
        {
            return null;
        }
        return (namespacePrefix.equalsIgnoreCase(SimpleModule.NAMESPACE_PREFIX + "-core") && objectId.equalsIgnoreCase("users")) ? Handler.Priority.MEDIUM : null;
    }
}
