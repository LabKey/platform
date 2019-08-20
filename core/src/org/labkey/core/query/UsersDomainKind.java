/*
 * Copyright (c) 2012-2016 LabKey Corporation
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

import org.labkey.api.collections.CaseInsensitiveHashMap;
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
import org.labkey.api.exp.TemplateInfo;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    private static final List<PropertyDescriptorSpec> _requiredProperties = new ArrayList<>();

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
        _reservedNames.add("ExpirationDate");

        _requiredProperties.add(new PropertyDescriptorSpec("FirstName", PropertyType.STRING, 64, false));
        _requiredProperties.add(new PropertyDescriptorSpec("LastName", PropertyType.STRING, 64, false));
        _requiredProperties.add(new PropertyDescriptorSpec("Phone", PropertyType.STRING, 64, false));
        _requiredProperties.add(new PropertyDescriptorSpec("Mobile", PropertyType.STRING, 64, false));
        _requiredProperties.add(new PropertyDescriptorSpec("Pager", PropertyType.STRING, 64, true));
        _requiredProperties.add(new PropertyDescriptorSpec("IM", PropertyType.STRING, 64, true));
        _requiredProperties.add(new PropertyDescriptorSpec("Description", PropertyType.STRING, 255, true));
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
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user, TemplateInfo templateInfo)
    {
        return super.createDomain(domain, arguments, getDomainContainer(), user, templateInfo);
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
        DbScope scope = CoreSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            User user = context.getUpgradeUser();
            if (user == null)
                user = UserManager.getGuestUser();

            String domainURI = UsersDomainKind.getDomainURI("core", CoreQuerySchema.USERS_TABLE_NAME, UsersDomainKind.getDomainContainer(), user);
            Domain domain = PropertyService.get().getDomain(UsersDomainKind.getDomainContainer(), domainURI);

            if (domain == null)
            {
                domain = PropertyService.get().createDomain(UsersDomainKind.getDomainContainer(), domainURI, CoreQuerySchema.USERS_TABLE_NAME);
                domain.save(user);
            }

            // ensure required fields
            Map<String, DomainProperty> existingProps = new CaseInsensitiveHashMap<>();
            boolean dirty = false;
            for (DomainProperty dp : domain.getProperties())
            {
                existingProps.put(dp.getName(), dp);
            }

            for (PropertyDescriptorSpec pd : _requiredProperties)
            {
                DomainProperty existingProp = existingProps.get(pd.getName());
                if (existingProp == null)
                {
                    dirty = true;
                    pd.createPropertyDescriptor(domain, user);
                }
                else if (!existingProp.getName().equals(pd.getName()))
                {
                    // differs by case, need to remove this old prop in favor of our managed property
                    dirty = true;
                    existingProp.delete();
                    pd.createPropertyDescriptor(domain, user);
                }
            }

            if (dirty)
                domain.save(user);
            transaction.commit();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
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

    private static class PropertyDescriptorSpec
    {
        private String _name;
        private PropertyType _type;
        private int _scale;
        private boolean _hidden;

        public PropertyDescriptorSpec(String name, PropertyType type, int scale, boolean hidden)
        {
            _name = name;
            _type = type;
            _scale = scale;
            _hidden = hidden;
        }

        public String getName()
        {
            return _name;
        }

        public PropertyType getType()
        {
            return _type;
        }

        public int getScale()
        {
            return _scale;
        }

        public boolean isHidden()
        {
            return _hidden;
        }

        public void createPropertyDescriptor(Domain domain, User user)
        {
            String propertyURI = UsersDomainKind.createPropertyURI("core", CoreQuerySchema.USERS_TABLE_NAME, getDomainContainer(), user);

            DomainProperty prop = domain.addProperty();
            prop.setName(_name);
            prop.setType(PropertyService.get().getType(domain.getContainer(), _type.getXmlName()));
            prop.setScale(_scale);
            prop.setPropertyURI(propertyURI);

            if (_hidden)
            {
                prop.setShownInDetailsView(false);
                prop.setShownInInsertView(false);
                prop.setShownInUpdateView(false);
                prop.setHidden(true);
            }
        }
    }
}
