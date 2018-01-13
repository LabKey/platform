/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.api.issues;

import com.google.common.collect.Sets;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by davebradlee on 8/3/16.
 */
public abstract class AbstractIssuesListDefDomainKind extends AbstractDomainKind
{
    protected static String XAR_SUBSTITUTION_SCHEMA_NAME = "SchemaName";
    protected static String XAR_SUBSTITUTION_TABLE_NAME = "TableName";

    //    private static String DOMAIN_NAMESPACE_PREFIX_TEMPLATE = "%s-${SchemaName}";
    protected static String DOMAIN_NAMESPACE_PREFIX_TEMPLATE = "%s";
    protected static String DOMAIN_LSID_TEMPLATE = "${FolderLSIDBase}:${TableName}";

    protected static final Set<PropertyStorageSpec> BASE_PROPERTIES;
    protected static final Set<PropertyStorageSpec> BASE_REQUIRED_PROPERTIES;
    protected static final Set<PropertyStorageSpec.Index> INDEXES;

    static
    {
        // required property descriptors, initialized at domain creation time
        BASE_REQUIRED_PROPERTIES = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(
            new PropertyStorageSpec("AssignedTo", JdbcType.INTEGER).setNullable(false),
            new PropertyStorageSpec("Resolution", JdbcType.VARCHAR, 200).setDefaultValue("Fixed")
        )));

        BASE_PROPERTIES = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(
            new PropertyStorageSpec("EntityId", JdbcType.VARCHAR).setEntityId(true).setNullable(false),
            new PropertyStorageSpec("Container", JdbcType.VARCHAR).setEntityId(true).setNullable(false),
            new PropertyStorageSpec("Status", JdbcType.VARCHAR, 60).setNullable(false),
            new PropertyStorageSpec("Created", JdbcType.TIMESTAMP),
            new PropertyStorageSpec("CreatedBy", JdbcType.INTEGER).setNullable(false),
            new PropertyStorageSpec("Modified", JdbcType.TIMESTAMP),
            new PropertyStorageSpec("ModifiedBy", JdbcType.INTEGER).setNullable(false),
            new PropertyStorageSpec("Resolved", JdbcType.TIMESTAMP),
            new PropertyStorageSpec("ResolvedBy", JdbcType.INTEGER),
            new PropertyStorageSpec("Closed", JdbcType.TIMESTAMP),
            new PropertyStorageSpec("ClosedBy", JdbcType.INTEGER)
        )));

        INDEXES = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(
            new PropertyStorageSpec.Index(false, "AssignedTo"),
            new PropertyStorageSpec.Index(false, "Status")
        )));

    }

    public abstract Set<PropertyStorageSpec> getRequiredProperties();
    public abstract void createLookupDomains(Container domainContainer, User user, String domainName) throws BatchValidationException;
    public abstract List<FieldKey> getDefaultColumnNames();

    @Override
    public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
    {
        return BASE_PROPERTIES;
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        return INDEXES;
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        return null;
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        return null;
    }

    @Nullable
    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(containerUser.getContainer(), domain.getTypeURI(), false, false, true);
    }

    @Nullable
    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return getKindName().equals(lsid.getNamespacePrefix()) ? Priority.MEDIUM : null;
    }

    @Override
    public DbSchemaType getSchemaType()
    {
        return DbSchemaType.Provisioned;
    }

    @Override
    public String getStorageSchemaName()
    {
        return IssuesSchema.ISSUE_DEF_SCHEMA_NAME;
    }

    @Override
    public DbScope getScope()
    {
        DbSchema schema = DbSchema.get(getStorageSchemaName(), DbSchemaType.Provisioned);
        return schema.getScope();
    }

    @Override
    public String generateDomainURI(String schemaName, String tableName, Container c, User u)
    {
        return getDomainURI(schemaName, tableName, getKindName(), c, u);
    }

    public static String getDomainURI(String schemaName, String tableName, String namespacePrefix, Container c, User u)
    {
        try
        {
            XarContext xc = new XarContext("Domains", c, u);
            xc.addSubstitution(XAR_SUBSTITUTION_SCHEMA_NAME, schemaName);
            xc.addSubstitution(XAR_SUBSTITUTION_TABLE_NAME, tableName);

            String template = String.format(DOMAIN_NAMESPACE_PREFIX_TEMPLATE, namespacePrefix);
            return LsidUtils.resolveLsidFromTemplate(DOMAIN_LSID_TEMPLATE, xc, template);
        }
        catch (XarFormatException xfe)
        {
            return null;
        }
    }

    @Override
    public void deleteDomain(User user, Domain domain)
    {
        try
        {
            domain.delete(user);
        }
        catch (DomainNotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }

    protected void deleteLookup(Domain domain, User user, String lookupName) throws DomainNotFoundException
    {
        Container c = domain.getContainer();

        ListDefinition def = ListService.get().getList(c, getLookupTableName(domain.getName(), lookupName));
        if (def != null)
            def.delete(user);
    }

    public static String getLookupListName(String domainName, String lookupTemplateName)
    {
        return domainName + "-" + lookupTemplateName + "-lookup";
    }

    public String getLookupTableName(String domainName, String tableName)
    {
        return getLookupListName(domainName, tableName);
    }

    public void setDefaultValues(Container domainContainer, Domain domain) throws ExperimentException
    {
        Map<DomainProperty, Object> defaultValues = new HashMap<>();

        DomainProperty prop = domain.getPropertyByName("Priority");
        DefaultValueService.get().setDefaultValues(domainContainer, defaultValues);
    }

    public void addAdditionalQueryColumns(TableInfo table)
    {
    }

    public Map<String, List<Pair<String, ActionURL>>> getAdditionalDetailInfo(TableInfo tableInfo, int issueId)
    {
        return null;
    }

    public abstract String getDefaultSingularName();

    public abstract String getDefaultPluralName();

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return container.hasPermission(user, AdminPermission.class);
    }

    @Override
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user, @Nullable TemplateInfo templateInfo)
    {
        String name = domain.getName();
        String providerName = arguments.containsKey("providerName") ? (String)arguments.get("providerName") : null;
        String singularNoun = arguments.containsKey("singularNoun") ? (String)arguments.get("singularNoun") : getDefaultSingularName();
        String pluralNoun = arguments.containsKey("pluralNoun") ? (String)arguments.get("pluralNoun") : getDefaultPluralName();

        if (name == null)
            throw new IllegalArgumentException("Issue name must not be null");

        if (providerName == null)
            providerName = getKindName();

        int issueDefId;
        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            issueDefId = IssuesListDefService.get().createIssueListDef(container, user, providerName, name, singularNoun, pluralNoun);

            List<GWTPropertyDescriptor> properties = (List<GWTPropertyDescriptor>)domain.getFields();
            List<GWTIndex> indices = (List<GWTIndex>)domain.getIndices();

            Domain newDomain = IssuesListDefService.get().getDomainFromIssueDefId(issueDefId, container, user);
            if (newDomain != null)
            {
                Set<String> reservedNames = getReservedPropertyNames(newDomain);
                Set<String> lowerReservedNames = reservedNames.stream().map(String::toLowerCase).collect(Collectors.toSet());
                Set<String> existingProperties = newDomain.getProperties().stream().map(o -> o.getName().toLowerCase()).collect(Collectors.toSet());
                Map<DomainProperty, Object> defaultValues = new HashMap<>();
                Set<String> propertyUris = new HashSet<>();

                for (GWTPropertyDescriptor pd : properties)
                {
                    if (lowerReservedNames.contains(pd.getName().toLowerCase()) || existingProperties.contains(pd.getName().toLowerCase()))
                    {
                        throw new IllegalArgumentException("Property: " + pd.getName() + " is reserved or exists in the current domain.");
                    }
                    DomainUtil.addProperty(newDomain, pd, defaultValues, propertyUris, null);
                }

                Set<PropertyStorageSpec.Index> propertyIndices = new HashSet<>();
                for (GWTIndex index : indices)
                {
                    PropertyStorageSpec.Index propIndex = new PropertyStorageSpec.Index(index.isUnique(), index.getColumnNames());
                    propertyIndices.add(propIndex);
                }
                newDomain.setPropertyIndices(propertyIndices);

                // set default values on the base properties
                DomainKind domainKind = newDomain.getDomainKind();
                if (domainKind instanceof AbstractIssuesListDefDomainKind)
                {
                    setDefaultValues(newDomain, ((AbstractIssuesListDefDomainKind)domainKind).getRequiredProperties());
                }
                newDomain.save(user);
            }
            transaction.commit();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        return  IssuesListDefService.get().getDomainFromIssueDefId(issueDefId, container, user);
    }

    public static void setDefaultValues(Domain domain, Collection<PropertyStorageSpec> requiredProps)
    {
        for (PropertyStorageSpec spec : requiredProps)
        {
            // kind of a hack, if there is a default value for now assume it is of type fixed_editable
            if (spec.getDefaultValue() != null)
            {
                DomainProperty prop = domain.getPropertyByName(spec.getName());
                if (prop != null)
                {
                    prop.setDefaultValueTypeEnum(DefaultValueType.FIXED_EDITABLE);
                    prop.setDefaultValue(String.valueOf(spec.getDefaultValue()));
                }
            }
        }
    }
}
