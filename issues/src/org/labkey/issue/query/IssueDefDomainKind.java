package org.labkey.issue.query;

import com.google.common.collect.Sets;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by klum on 4/6/2016.
 */
public class IssueDefDomainKind extends AbstractDomainKind
{
    public static final String NAME = "IssueDefinition";
    private static String XAR_SUBSTITUTION_SCHEMA_NAME = "SchemaName";
    private static String XAR_SUBSTITUTION_TABLE_NAME = "TableName";

//    private static String DOMAIN_NAMESPACE_PREFIX_TEMPLATE = "%s-${SchemaName}";
    private static String DOMAIN_NAMESPACE_PREFIX_TEMPLATE = "%s";
    private static String DOMAIN_LSID_TEMPLATE = "${FolderLSIDBase}:${TableName}";

    private static final Set<PropertyStorageSpec> BASE_PROPERTIES;
    private static final Set<PropertyStorageSpec> REQUIRED_PROPERTIES;
    //private static final Set<PropertyStorageSpec.Index> INDEXES;
    private static final Set<String> RESERVED_NAMES;
    private static final Set<String> MANDATORY_PROPERTIES;

    //private static final Set<PropertyStorageSpec.ForeignKey> FOREIGN_KEYS;

    static
    {
        BASE_PROPERTIES = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(
                new PropertyStorageSpec("EntityId", JdbcType.VARCHAR).setEntityId(true).setNullable(false),
                new PropertyStorageSpec("Container", JdbcType.VARCHAR).setNullable(false)
        )));

        RESERVED_NAMES = BASE_PROPERTIES.stream().map(PropertyStorageSpec::getName).collect(Collectors.toSet());
        RESERVED_NAMES.addAll(Arrays.asList("RowId", "Name"));

        // required property descriptors, initialized at domain creation time
        REQUIRED_PROPERTIES = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(
                new PropertyStorageSpec("Title", JdbcType.VARCHAR, 255).setNullable(false),
                new PropertyStorageSpec("Type", JdbcType.VARCHAR, 200),
                new PropertyStorageSpec("Area", JdbcType.VARCHAR, 200),
                new PropertyStorageSpec("NotifyList", JdbcType.VARCHAR),
                new PropertyStorageSpec("Priority", JdbcType.INTEGER).setNullable(false),
                new PropertyStorageSpec("Milestone", JdbcType.VARCHAR, 200),
                new PropertyStorageSpec("Resolution", JdbcType.VARCHAR, 200)
        )));

        MANDATORY_PROPERTIES = REQUIRED_PROPERTIES.stream().map(PropertyStorageSpec::getName).collect(Collectors.toSet());
    }

    @Override
    public String getKindName()
    {
        return NAME;
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
        return PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(containerUser.getContainer(), domain.getTypeURI(), true, false, false);
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties()
    {
        return BASE_PROPERTIES;
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return RESERVED_NAMES;
    }

    public Set<PropertyStorageSpec> getRequiredProperties()
    {
        return REQUIRED_PROPERTIES;
    }

    @Override
    public Set<String> getMandatoryPropertyNames(Domain domain)
    {
        return MANDATORY_PROPERTIES;
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
}

