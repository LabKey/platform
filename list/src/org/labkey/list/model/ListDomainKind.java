/*
 * Copyright (c) 2013-2018 LabKey Corporation
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

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
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
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.writer.ContainerUser;
import org.labkey.data.xml.domainTemplate.DomainTemplateType;
import org.labkey.data.xml.domainTemplate.ListOptionsType;
import org.labkey.data.xml.domainTemplate.ListTemplateType;
import org.labkey.list.view.ListItemAttachmentParent;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.exp.property.DomainTemplate.findProperty;

/**
 * User: Nick
 * Date: 5/8/13
 * Time: 4:12 PM
 */
public abstract class ListDomainKind extends AbstractDomainKind<ListDomainKindProperties>
{
    /*
     * the columns common to all lists
     */
    private final static Set<PropertyStorageSpec> BASE_PROPERTIES;
    private ListDefinitionImpl _list;
    private final static int MAX_NAME_LENGTH = 64;

    static
    {
        BASE_PROPERTIES = PageFlowUtil.set(new PropertyStorageSpec("entityId", JdbcType.GUID).setNullable(false),
                new PropertyStorageSpec("created", JdbcType.TIMESTAMP),
                new PropertyStorageSpec("createdBy", JdbcType.INTEGER),
                new PropertyStorageSpec("modified", JdbcType.TIMESTAMP),
                new PropertyStorageSpec("modifiedBy", JdbcType.INTEGER),
                new PropertyStorageSpec("lastIndexed", JdbcType.TIMESTAMP),
                new PropertyStorageSpec("container", JdbcType.GUID).setNullable(false));
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
    public boolean allowAttachmentProperties()
    {
        return true;
    }

    @Override
    public boolean showDefaultValueSettings()
    {
        return true;
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

    abstract KeyType getDefaultKeyType();

    abstract Collection<KeyType> getSupportedKeyTypes();

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
    public Class<? extends ListDomainKindProperties> getTypeClass()
    {
        return ListDomainKindProperties.class;
    }

    @Override
    public Domain createDomain(GWTDomain domain, ListDomainKindProperties listProperties, Container container, User user, @Nullable TemplateInfo templateInfo)
    {
        String name = domain.getName();
        String keyName = listProperties.getKeyName();

        if (StringUtils.isEmpty(name))
            throw new ApiUsageException("List name must not be null");
        if (name.length() > MAX_NAME_LENGTH)
            throw new ApiUsageException("List name cannot be longer than " + MAX_NAME_LENGTH + " characters");
        if (ListService.get().getList(container, name) != null)
            throw new ApiUsageException("The name '" + name + "' is already in use.");
        if (StringUtils.isEmpty(keyName))
            throw new ApiUsageException("List keyName must not be null");

        KeyType keyType = getDefaultKeyType();

        if (null != listProperties.getKeyType())
        {
            String rawKeyType = listProperties.getKeyType();
            if (EnumUtils.isValidEnum(KeyType.class, rawKeyType))
                keyType = KeyType.valueOf(rawKeyType);
            else
                throw new ApiUsageException("List keyType provided does not exist.");
        }

        if (!getSupportedKeyTypes().contains(keyType))
            throw new ApiUsageException("List keyType provided is not supported for list domain kind (" + getKindName() + ").");

        ListDefinition list = ListService.get().createList(container, name, keyType, templateInfo);
        list.setKeyName(keyName);

        String description = listProperties.getDescription() != null ? listProperties.getDescription() : domain.getDescription();
        list.setDescription(description);

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

                DomainUtil.addProperty(d, pd, defaultValues, propertyUris, null);
            }

            Set<PropertyStorageSpec.Index> propertyIndices = new HashSet<>();
            for (GWTIndex index : indices)
            {
                PropertyStorageSpec.Index propIndex = new PropertyStorageSpec.Index(index.isUnique(), index.getColumnNames());
                propertyIndices.add(propIndex);
            }
            d.setPropertyIndices(propertyIndices);

            list.save(user);
            updateListProperties(container, user, list.getListId(), listProperties);

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
    public ValidationException updateDomain(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update,
                                            ListDomainKindProperties listProperties, Container container, User user, boolean includeWarnings)
    {
        ValidationException exception;

        try (DbScope.Transaction transaction = ListManager.get().getListMetadataSchema().getScope().ensureTransaction())
        {
            exception = new ValidationException();

            Domain domain = PropertyService.get().getDomain(container, original.getDomainURI());
            if (null == domain)
                return exception.addGlobalError("Expected domain for list: " + original.getName());

            ListDefinition listDefinition = ListService.get().getList(domain);
            TableInfo table = listDefinition.getTable(user);

            if (null == table)
                return exception.addGlobalError("Expected table for list: " + listDefinition.getName());

            // Handle cases when existing key field is null or is not provided in the updated domainDesign
            GWTPropertyDescriptor key = findField(listDefinition.getKeyName(), original.getFields());
            if (null != key)
            {
                int id = key.getPropertyId();
                GWTPropertyDescriptor newKey = findField(id, update.getFields());
                if (null == newKey)
                {
                    return exception.addGlobalError("Key field not provided, expecting key field '" + key.getName() + "'");
                }
                else if (!key.getName().equalsIgnoreCase(newKey.getName()))
                {
                    return exception.addGlobalError("Cannot change key field name");
                }
            }
            else
            {
                return exception.addGlobalError("Key field not found for list '" + listDefinition.getName() + "'");
            }

            //handle name change
            if (!original.getName().equals(update.getName()))
            {
                if (update.getName().length() > MAX_NAME_LENGTH)
                {
                    return exception.addGlobalError("List name cannot be longer than " + MAX_NAME_LENGTH + " characters.");
                }
                else if (ListService.get().getList(container, update.getName()) != null)
                {
                    return exception.addGlobalError("The name '" + update.getName() + "' is already in use.");
                }
            }

            //return if there are errors before moving forward with the save
            if (exception.hasErrors())
            {
                return exception;
            }

            //update list properties
            if (null != listProperties)
            {
                if (listProperties.getDomainId() != original.getDomainId() || listProperties.getDomainId() != update.getDomainId())
                    return exception.addGlobalError("domainId for the list does not match old or the new domain");
                if (!original.getDomainURI().equals(update.getDomainURI()))
                    return exception.addGlobalError("domainURI mismatch between old and new domain");

                updateListProperties(container, user, listDefinition.getListId(), listProperties);
            }

            //update domain design properties
            try
            {
                //handle attachment cols
                Map<String, ColumnInfo> modifiedAttachmentColumns = new CaseInsensitiveHashMap<>();

                for (DomainProperty oldProp : domain.getProperties())
                {
                    if (PropertyType.ATTACHMENT.equals(oldProp.getPropertyDescriptor().getPropertyType()))
                    {
                        GWTPropertyDescriptor newGWTProp = findField(oldProp.getPropertyId(), update.getFields());
                        if (null == newGWTProp || !PropertyType.ATTACHMENT.equals(PropertyType.getFromURI(newGWTProp.getConceptURI(), newGWTProp.getRangeURI(), null)))
                        {
                            ColumnInfo column = table.getColumn(oldProp.getPropertyDescriptor().getName());
                            if (null != column)
                                modifiedAttachmentColumns.put(oldProp.getPropertyDescriptor().getName(), column);
                        }
                    }
                }

                Collection<Map<String, Object>> attachmentMapCollection = null;
                if (!modifiedAttachmentColumns.isEmpty())
                {
                    List<ColumnInfo> columns = new ArrayList<>(modifiedAttachmentColumns.values());
                    columns.add(table.getColumn("entityId"));
                    attachmentMapCollection = new TableSelector(table, columns, null, null).getMapCollection();
                }

                // Remove attachments from any attachment columns that are removed or no longer attachment columns
                if (null != attachmentMapCollection)
                {
                    for (Map<String, Object> map : attachmentMapCollection)
                    {
                        String entityId = (String) map.get("entityId");
                        ListItemAttachmentParent parent = new ListItemAttachmentParent(entityId, container);
                        for (Map.Entry<String, Object> entry : map.entrySet())
                            if (null != entry.getValue() && modifiedAttachmentColumns.containsKey(entry.getKey()))
                                AttachmentService.get().deleteAttachment(parent, entry.getValue().toString(), user);
                    }
                }

                //update domain properties
                exception.addErrors(DomainUtil.updateDomainDescriptor(original, update, container, user));
            }
            catch (RuntimeSQLException x)
            {
                // issue 19202 - check for null value exceptions in case provided file data not contain the column
                // and return a better error message
                String message = x.getMessage();
                if (x.isNullValueException())
                {
                    message = "The provided data does not contain the specified '" + listDefinition.getKeyName() + "' field or contains null key values.";
                }
                return exception.addGlobalError(message);
            }
            catch (DataIntegrityViolationException x)
            {
                return exception.addGlobalError("A data error occurred: " + x.getMessage());
            }

            if (!exception.hasErrors())
            {
                transaction.commit();
            }
            return exception;
        }
    }

    private void updateListProperties(Container container, User user, int listId, ListDomainKindProperties listProperties)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("ListId"), listId);
        ListDomainKindProperties existingListProps = new TableSelector(ListManager.get().getListMetadataTable(), filter, null).getObject(ListDomainKindProperties.class);

        //merge existing and new properties
        ListDomainKindProperties updatedListProps = updateListProperties(existingListProps, listProperties);

        ListManager.get().update(user, container, updatedListProps);
    }

    //updates list properties except listId, domainId, keyName, keyType, and lastIndexed
    private ListDomainKindProperties updateListProperties(ListDomainKindProperties existingListProps, ListDomainKindProperties newListProps)
    {
        ListDomainKindProperties updatedListProps = new ListDomainKindProperties(existingListProps);

        if (null != newListProps.getName())
            updatedListProps.setName(newListProps.getName());

        updatedListProps.setTitleColumn(newListProps.getTitleColumn());
        updatedListProps.setDescription(newListProps.getDescription());
        updatedListProps.setAllowDelete(newListProps.isAllowDelete());
        updatedListProps.setAllowUpload(newListProps.isAllowUpload());
        updatedListProps.setAllowExport(newListProps.isAllowExport());
        updatedListProps.setDiscussionSetting(newListProps.getDiscussionSetting());
        updatedListProps.setEntireListTitleTemplate(newListProps.getEntireListTitleTemplate());
        updatedListProps.setEntireListIndexSetting(newListProps.getEntireListIndexSetting());
        updatedListProps.setEntireListBodySetting(newListProps.getEntireListBodySetting());
        updatedListProps.setEachItemTitleTemplate(newListProps.getEachItemTitleTemplate());
        updatedListProps.setEachItemBodySetting(newListProps.getEachItemBodySetting());
        updatedListProps.setEntireListIndex(newListProps.isEntireListIndex());
        updatedListProps.setEntireListBodyTemplate(newListProps.getEntireListBodyTemplate());
        updatedListProps.setEachItemIndex(newListProps.isEachItemIndex());
        updatedListProps.setEachItemBodyTemplate(newListProps.getEachItemBodyTemplate());
        updatedListProps.setFileAttachmentIndex(newListProps.isFileAttachmentIndex());

        return updatedListProps;
    }

    private GWTPropertyDescriptor findField(String name, List<? extends GWTPropertyDescriptor> fields)
    {
        for (GWTPropertyDescriptor f : fields)
        {
            if (name.equalsIgnoreCase(f.getName()))
                return f;
        }
        return null;
    }

    private GWTPropertyDescriptor findField(int id, List<? extends GWTPropertyDescriptor> fields)
    {
        if (id > 0)
        {
            for (GWTPropertyDescriptor f : fields)
            {
                if (id == f.getPropertyId())
                    return f;
            }
        }
        return null;
    }

    @Override
    public @Nullable ListDomainKindProperties getDomainKindProperties(@NotNull GWTDomain domain, Container container, User user)
    {
        ListDefinition list = ListService.get().getList(PropertyService.get().getDomain(domain.getDomainId()));
        return ListManager.get().getListDomainKindProperties(container, list.getListId());
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
            ListManager.get().indexList(list, true);
    }

    @Override
    public boolean matchesTemplateXML(String templateName, DomainTemplateType template, List<GWTPropertyDescriptor> properties)
    {
        if(!(template instanceof ListTemplateType))
            return false;

        ListOptionsType options = ((ListTemplateType) template).getOptions();
        if (options == null)
            throw new IllegalArgumentException("List template requires specifying a keyCol");

        String keyName = options.getKeyCol();
        if (keyName == null)
            throw new IllegalArgumentException("List template requires specifying a keyCol");

        Pair<GWTPropertyDescriptor, Integer> pair = findProperty(templateName, properties, keyName);
        GWTPropertyDescriptor prop = pair.first;

        PropertyType type = PropertyType.getFromURI(prop.getConceptURI(), prop.getRangeURI());

        return type.equals(getDefaultKeyType().getPropertyType());
    }

}
