/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.audit.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.writer.ContainerUser;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/8/13
 */
public abstract class AbstractAuditDomainKind extends DomainKind
{
    private static String XAR_SUBSTITUTION_SCHEMA_NAME = "SchemaName";
    private static String XAR_SUBSTITUTION_TABLE_NAME = "TableName";

    private static String DOMAIN_NAMESPACE_PREFIX_TEMPLATE = "%s-${SchemaName}";
    private static String DOMAIN_LSID_TEMPLATE = "${FolderLSIDBase}:${TableName}";

    private static final Set<PropertyStorageSpec> _baseFields;
    private static final Set<String> _reservedNames = new HashSet<>();

    public static final String OLD_RECORD_PROP_NAME = "oldRecordMap";
    public static final String NEW_RECORD_PROP_NAME = "newRecordMap";

    static
    {
        Set<PropertyStorageSpec> baseFields = new LinkedHashSet<>();
        baseFields.add(createFieldSpec("RowId", JdbcType.INTEGER, true, true));       // pk
        baseFields.add(createFieldSpec("Container", JdbcType.VARCHAR).setEntityId(true));
        baseFields.add(createFieldSpec("Comment", JdbcType.VARCHAR));
        baseFields.add(createFieldSpec("EventType", JdbcType.VARCHAR));
        baseFields.add(createFieldSpec("Created", JdbcType.TIMESTAMP));
        baseFields.add(createFieldSpec("CreatedBy", JdbcType.INTEGER));
        baseFields.add(createFieldSpec("ImpersonatedBy", JdbcType.INTEGER));
        baseFields.add(createFieldSpec("ProjectId", JdbcType.VARCHAR).setEntityId(true));
        _baseFields = Collections.unmodifiableSet(baseFields);
    }

    private final String _eventType;

    public AbstractAuditDomainKind(String eventType)
    {
        _eventType = eventType;
    }

    protected abstract String getNamespacePrefix();

    /**
     * @return The PropertyDescriptors that should exist on the AuditTypeProvider's Domain (these properties don't exist in the database yet.)
     */
    public abstract Set<PropertyDescriptor> getProperties();

    protected String getEventType()
    {
        return _eventType;
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
    {
        return _baseFields;
    }

    @Override
    public Set<String> getMandatoryPropertyNames(Domain domain)
    {
        if (_reservedNames.isEmpty())
        {
            for (PropertyStorageSpec spec : getBaseProperties(domain))
                _reservedNames.add(spec.getName());
            for (PropertyDescriptor pd : getProperties())
                _reservedNames.add(pd.getName());
        }
        return _reservedNames;
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        return new SQLFragment("NULL");
    }

    protected static Container getDomainContainer()
    {
        return ContainerManager.getSharedContainer();
    }

    @Override
    public String generateDomainURI(String schemaName, String tableName, Container c, User u)
    {
        return getDomainURI(schemaName, tableName, getNamespacePrefix(), getDomainContainer(), u);
    }

    public String getDomainURI()
    {
        return getDomainURI(AbstractAuditTypeProvider.SCHEMA_NAME, getEventType(), getNamespacePrefix(), getDomainContainer(), null);
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

    private String generatePropertyURI(String propertyName)
    {
        return getDomainURI() + "#" + propertyName;
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        return QueryService.get().urlFor(containerUser.getUser(), containerUser.getContainer(), QueryAction.executeQuery, AbstractAuditTypeProvider.QUERY_SCHEMA_NAME, getEventType());
    }

    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return null;
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        return null;
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return false;
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        return false;
    }

    @Override
    public boolean canDeleteDefinition(User user, Domain domain)
    {
        return false;
    }

    @Override
    public void appendNavTrail(NavTree root, Container c, User user)
    {
    }

    @Override
    public void deletePropertyDescriptor(Domain domain, User user, PropertyDescriptor pd)
    {
    }

    @Override
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user, @Nullable TemplateInfo templateInfo)
    {
        return null;
    }

    @Override
    public List<String> updateDomain(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update, Container container, User user)
    {
        return null;
    }

    @Override
    public void deleteDomain(User user, Domain domain)
    {
    }

    @Override
    public DbScope getScope()
    {
        DbSchema schema = DbSchema.get(getStorageSchemaName(), DbSchemaType.Provisioned);
        return schema.getScope();
    }

    @Override
    public String getStorageSchemaName()
    {
        return AbstractAuditTypeProvider.SCHEMA_NAME;
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        return Collections.emptySet();
    }

    @Override
    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys(Container container)
    {
        return Collections.emptySet();
    }

    @Override
    public boolean hasNullValues(Domain domain, DomainProperty prop)
    {
        return false;
    }

    @Override
    public DbSchemaType getSchemaType()
    {
        return DbSchemaType.Provisioned;
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        Set<String> names = new HashSet<>();

        for (PropertyStorageSpec spec : getBaseProperties(domain))
            names.add(spec.getName());

        return names;
    }

    protected static PropertyStorageSpec createFieldSpec(String name, JdbcType jdbcType)
    {
        return createFieldSpec(name, jdbcType, false, false);
    }

    protected static PropertyStorageSpec createFieldSpec(String name, JdbcType jdbcType, boolean isPrimaryKey, boolean isAutoIncrement)
    {
        PropertyStorageSpec spec = new PropertyStorageSpec(name, jdbcType);
        spec.setAutoIncrement(isAutoIncrement);
        spec.setPrimaryKey(isPrimaryKey);

        return spec;
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);

        return lsid.getNamespacePrefix() != null && lsid.getNamespacePrefix().startsWith(getNamespacePrefix()) ? Handler.Priority.MEDIUM : null;
    }

    protected PropertyDescriptor createPropertyDescriptor(@NotNull String name, @NotNull PropertyType type)
    {
        return createPropertyDescriptor(name, type, null, null, false, null);
    }

    protected PropertyDescriptor createPropertyDescriptor(@NotNull String name, @NotNull PropertyType type, @Nullable Integer scale)
    {
        return createPropertyDescriptor(name, type, null, null, false, scale);
    }

    protected PropertyDescriptor createPropertyDescriptor(
            @NotNull String name, @NotNull PropertyType type,
            @Nullable String caption, @Nullable String description,
            boolean required)
    {
        return createPropertyDescriptor(name, type, caption, description, required, null);
    }

    protected PropertyDescriptor createPropertyDescriptor(
            @NotNull String name, @NotNull PropertyType type,
            @Nullable String caption, @Nullable String description,
            boolean required, @Nullable Integer scale)
    {
        Container domainContainer = getDomainContainer();

        String propertyURI = generatePropertyURI(name);

        PropertyDescriptor pd = new PropertyDescriptor(propertyURI, type, name, null, domainContainer);
        if (caption != null)
            pd.setLabel(caption);
        if (description != null)
            pd.setDescription(description);
        if (scale != null)
            pd.setScale(scale);
        pd.setRequired(required);

        return pd;
    }

    @Override
    public PropertyStorageSpec getPropertySpec(PropertyDescriptor pd, Domain domain)
    {
        return new PropertyStorageSpec(pd);
    }

    @Override
    public Set<String> getNonProvisionedTableNames()
    {
        // omit the legacy auditlog table, this can be removed once the
        // table is dropped after migration
        Set<String> tables = new CaseInsensitiveHashSet();
        tables.add("auditlog");

        return tables;
    }

    @Nullable
    @Override
    public String getMetaDataSchemaName()
    {
        return null;
    }

    @Nullable
    @Override
    public String getMetaDataTableName()
    {
        return null;
    }
}
