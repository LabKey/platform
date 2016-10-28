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
package org.labkey.study.query.studydesign;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.StudySchema;
import org.labkey.study.query.StudyQuerySchema;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by klum on 12/10/13.
 */
public abstract class AbstractStudyDesignDomainKind extends AbstractDomainKind
{
    private static final String XAR_SUBSTITUTION_SCHEMA_NAME = "SchemaName";
    private static final String XAR_SUBSTITUTION_TABLE_NAME = "TableName";

    private static final String DOMAIN_NAMESPACE_PREFIX_TEMPLATE = "%s-${SchemaName}";
    private static final String DOMAIN_LSID_TEMPLATE = "${FolderLSIDBase}:${TableName}";

    // Prevent race conditions, #21128. TODO: Ideally, this would be one lock per DomainKind, but we new these up all over the place, #21199.
    private static final Object ENSURE_DOMAIN_LOCK = new Object();

    private static final Set<PropertyStorageSpec> BASE_FIELDS;

    static
    {
        Set<PropertyStorageSpec> baseFields = new LinkedHashSet<>();
        baseFields.add(createFieldSpec("Container", JdbcType.VARCHAR).setEntityId(true).setNullable(false));
        baseFields.add(createFieldSpec("Created", JdbcType.TIMESTAMP));
        baseFields.add(createFieldSpec("CreatedBy", JdbcType.INTEGER));
        baseFields.add(createFieldSpec("Modified", JdbcType.TIMESTAMP));
        baseFields.add(createFieldSpec("ModifiedBy", JdbcType.INTEGER));

        BASE_FIELDS = Collections.unmodifiableSet(baseFields);
    }

    private final Set<PropertyStorageSpec> _standardFields = new LinkedHashSet<>(BASE_FIELDS);
    private final String _tableName;

    public AbstractStudyDesignDomainKind(String tableName, Set<PropertyStorageSpec> standardFields)
    {
        _tableName = tableName;
        _standardFields.addAll(standardFields);
    }

    public Domain ensureDomain(Container container, User user, String tableName)
    {
        String domainURI = generateDomainURI(StudyQuerySchema.SCHEMA_NAME, tableName, container, null);

        synchronized (ENSURE_DOMAIN_LOCK)
        {
            Domain domain = PropertyService.get().getDomain(container, domainURI);

            if (domain == null)
            {
                try
                {
                    domain = PropertyService.get().createDomain(container, domainURI, tableName);
                    domain.save(user);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }

            return domain;
        }
    }

    protected abstract String getNamespacePrefix();

    protected String getTableName()
    {
        return _tableName;
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
    {
        return _standardFields;
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

    protected static Container getDomainContainer(Container c)
    {
        // for now create the domains per project, override to root the domains at
        // a different level.
        return c.getProject();
    }

    @Override
    public String generateDomainURI(String schemaName, String tableName, Container c, User u)
    {
        return getDomainURI(schemaName, tableName, getNamespacePrefix(), getDomainContainer(c), u);
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
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        return QueryService.get().urlFor(containerUser.getUser(), containerUser.getContainer(), QueryAction.executeQuery, StudyQuerySchema.SCHEMA_NAME, getTableName());
    }

    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(containerUser.getContainer(), domain.getTypeURI(), false, false, false);
    }

    @Override
    public DbScope getScope()
    {
        return StudySchema.getInstance().getSchema().getScope();
    }

    @Override
    public String getStorageSchemaName()
    {
        return StudySchema.getInstance().getStudyDesignSchemaName();
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        return Collections.emptySet();
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

    @Override
    public Set<String> getNonProvisionedTableNames()
    {
        return Collections.emptySet();
    }

    @Override
    public DbSchemaType getSchemaType()
    {
        return DbSchemaType.Provisioned;
    }
}
