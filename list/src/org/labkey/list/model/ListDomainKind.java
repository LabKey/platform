/*
 * Copyright (c) 2013-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListDefinition.KeyType;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.list.ListUrls;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.writer.ContainerUser;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: Nick
 * Date: 5/8/13
 * Time: 4:12 PM
 */
public abstract class ListDomainKind extends AbstractDomainKind
{
    /*
     * the columns common to all lists
     */
    private final static Set<PropertyStorageSpec> BASE_PROPERTIES;
    private ListDefinitionImpl _list;

    static
    {
        BASE_PROPERTIES = PageFlowUtil.set(new PropertyStorageSpec("entityId", JdbcType.VARCHAR).setEntityId(true).setNullable(false),
                new PropertyStorageSpec("created", JdbcType.TIMESTAMP),
                new PropertyStorageSpec("createdBy", JdbcType.INTEGER),
                new PropertyStorageSpec("modified", JdbcType.TIMESTAMP),
                new PropertyStorageSpec("modifiedBy", JdbcType.INTEGER),
                new PropertyStorageSpec("lastIndexed", JdbcType.TIMESTAMP),
                new PropertyStorageSpec("container", JdbcType.VARCHAR).setEntityId(true).setNullable(false));
    }

    public void setListDefinition(ListDefinitionImpl list)
    {
        _list = list;
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return "List '" + domain.getName() + "'";
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        throw new UnsupportedOperationException("sqlObjectIdsInDomain NYI for ListDomainKind");
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        ListDefinition listDef = ListService.get().getList(domain);
        if (null == listDef)
            return null;
        return listDef.urlShowData();
    }

    @Nullable
    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        ListDefinition listDef = ListService.get().getList(domain);
        if (null == listDef)
            return null;
        return listDef.urlShowDefinition();
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return getKindName().equals(lsid.getNamespacePrefix()) ? Priority.MEDIUM : null;
    }


    @Override
    public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
    {
        Set<PropertyStorageSpec> specs = new HashSet<>(BASE_PROPERTIES);
        specs.addAll(super.getBaseProperties(domain));
        return specs;
    }

    @Override
    public Set<String> getMandatoryPropertyNames(Domain domain)
    {
        Set<String> properties = super.getMandatoryPropertyNames(domain);
        properties.addAll(getAdditionalProtectedPropertyNames(domain));
        return properties;
    }

    /**
     * Returns the List's primary key as a field to get special treatment elsewhere, despite being property driven.
     * @param domain
     * @return
     */
    @Override
    public Set<PropertyStorageSpec> getAdditionalProtectedProperties(Domain domain)
    {
        Set<PropertyStorageSpec> specs = new HashSet<>(super.getAdditionalProtectedProperties(domain));

        ListDefinition listDef = ListService.get().getList(domain);
        if (null != listDef)
        {
            String keyName = listDef.getKeyName();
            JdbcType keyType = listDef.getKeyType() == KeyType.Varchar ? JdbcType.VARCHAR : JdbcType.INTEGER;
            int keySize = keyType == JdbcType.VARCHAR ? 4000 : 0;
            specs.add(new PropertyStorageSpec(keyName, keyType, keySize, PropertyStorageSpec.Special.PrimaryKey));
        }

        return specs;
    }

    @Override
    public PropertyStorageSpec getPropertySpec(PropertyDescriptor pd, Domain domain)
    {
        ListDefinition list = ListService.get().getList(domain);
        if (null == list)
            list = _list;

        if (null != list)
        {
            if (pd.getName().equalsIgnoreCase(list.getKeyName()))
            {
                PropertyStorageSpec key = this.getKeyProperty(list);
                assert key.isPrimaryKey();
                _list = null;
                return key;
            }
        }
        return super.getPropertySpec(pd, domain);
    }

    abstract PropertyStorageSpec getKeyProperty(ListDefinition list);

    abstract KeyType getKeyType();

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        Set<String> properties = new LinkedHashSet<>();
        for (PropertyStorageSpec pss : BASE_PROPERTIES)
        {
            properties.add(pss.getName());
        }

        return Collections.unmodifiableSet(properties);
    }

    @Override
    public String getStorageSchemaName()
    {
        return ListSchema.getInstance().getSchemaName();
    }

    @Override
    public DbScope getScope()
    {
        return ListSchema.getInstance().getSchema().getScope();
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        // return PageFlowUtil.set(new PropertyStorageSpec.Index(false, ListDomainKind.KEY_FIELD));
        return Collections.emptySet(); // TODO: Allow this to return the Key Column
    }

    public static Lsid generateDomainURI(String name, Container c, ListDefinition.KeyType keyType)
    {
        String type = getType(keyType);
        StringBuilder typeURI = getBaseURI(name, type, c);

        // 13131: uniqueify the lsid for situations where a preexisting list was renamed
        int i = 1;
        String sTypeURI = typeURI.toString();
        String uniqueURI = sTypeURI;
        while (OntologyManager.getDomainDescriptor(uniqueURI, c) != null)
        {
            uniqueURI = sTypeURI + '-' + (i++);
        }
        return new Lsid(uniqueURI);
    }

    public static Lsid createPropertyURI(String listName, String columnName, Container c, ListDefinition.KeyType keyType)
    {
        StringBuilder typeURI = getBaseURI(listName, getType(keyType), c);
        typeURI.append(".").append(PageFlowUtil.encode(columnName));
        return new Lsid(typeURI.toString());
    }

    private static String getType(ListDefinition.KeyType keyType)
    {
        String type;
        switch (keyType)
        {
            case Integer:
            case AutoIncrementInteger:
                type = IntegerListDomainKind.NAMESPACE_PREFIX;
                break;
            case Varchar:
                type = VarcharListDomainKind.NAMESPACE_PREFIX;
                break;
            default:
                throw new IllegalStateException();
        }
        return type;
    }

    private static StringBuilder getBaseURI(String listName, String type, Container c)
    {
        return new StringBuilder("urn:lsid:")
                .append(PageFlowUtil.encode(AppProps.getInstance().getDefaultLsidAuthority()))
                .append(":").append(type).append(".Folder-").append(c.getRowId()).append(":")
                .append(PageFlowUtil.encode(listName));
    }

    @Override
    public DbSchemaType getSchemaType()
    {
        return DbSchemaType.Provisioned;
    }

    @Override
    public TableInfo getTableInfo(User user, Container container, String name)
    {
        return new ListQuerySchema(user, container).createTable(name);
    }

    @Override
    public boolean isDeleteAllDataOnFieldImport()
    {
        return true;
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return container.hasPermission("ListDomainKind.canCreateDefinition", user, DesignListPermission.class);
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        Container c = domain.getContainer();
        return c.hasPermission("ListDomainKind.canEditDefinition", user, DesignListPermission.class);
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        return PageFlowUtil.urlProvider(ListUrls.class).getCreateListURL(container);
    }

    @Override
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user,
        @Nullable TemplateInfo templateInfo)
    {
        String name = domain.getName();
        String keyName = arguments.containsKey("keyName") ? (String)arguments.get("keyName") : null;

        if (name == null)
            throw new IllegalArgumentException("List name must not be null");

        if (keyName == null)
            throw new IllegalArgumentException("List keyName must not be null");

        ListDefinition list = ListService.get().createList(container, name, getKeyType(), templateInfo);
        list.setKeyName(keyName);
        list.setDescription(domain.getDescription());

        // TODO: lots of optional stuff we could set: discussionSetting, allowDelete, allowUpload, ...

        List<GWTPropertyDescriptor> properties = (List<GWTPropertyDescriptor>)domain.getFields();
        List<GWTIndex> indices = (List<GWTIndex>)domain.getIndices();

        try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
        {
            Domain d = list.getDomain();

            Set<String> reservedNames = getReservedPropertyNames(d);
            Set<String> lowerReservedNames = reservedNames.stream().map(String::toLowerCase).collect(Collectors.toSet());

            Map<DomainProperty, Object> defaultValues = new HashMap<>();
            Set<String> propertyUris = new HashSet<>();
            for (GWTPropertyDescriptor pd : properties)
            {
                String propertyName = pd.getName().toLowerCase();
                if (lowerReservedNames.contains(propertyName))
                {
                    if (pd.getLabel() == null)
                        pd.setLabel(pd.getName());
                    pd.setName("_" + pd.getName());
                }

                DomainProperty dp = DomainUtil.addProperty(d, pd, defaultValues, propertyUris, null);
            }

            Set<PropertyStorageSpec.Index> propertyIndices = new HashSet<>();
            for (GWTIndex index : indices)
            {
                PropertyStorageSpec.Index propIndex = new PropertyStorageSpec.Index(index.isUnique(), index.getColumnNames());
                propertyIndices.add(propIndex);
            }
            d.setPropertyIndices(propertyIndices);

            list.save(user);
            DefaultValueService.get().setDefaultValues(container, defaultValues);

            tx.commit();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        return list.getDomain();
    }

    @Override
    public void deleteDomain(User user, Domain domain)
    {
        ListDefinition list = ListService.get().getList(domain);
        if (list == null)
            throw new NotFoundException("List not found: " + domain.getTypeURI());

        try
        {
            list.delete(user);
        }
        catch (DomainNotFoundException e)
        {
            throw new NotFoundException(e.getMessage());
        }
    }

    @Override
    public void invalidate(Domain domain)
    {
        super.invalidate(domain);

        ListDefinition list = ListService.get().getList(domain);
        if (list != null)
            ListManager.get().indexList(list);
    }
}
