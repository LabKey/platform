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
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainTemplate;
import org.labkey.api.exp.property.DomainTemplateGroup;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;
import org.quartz.ListenerManager;

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
    private static final Set<PropertyStorageSpec.ForeignKey> FOREIGN_KEYS;

    private static final String ISSUE_LOOKUP_TEMPLATE_GROUP = "issue-lookup";
    private static final String PRIORITY_LOOKUP = "priority";
    private static final String TYPE_LOOKUP = "type";
    private static final String AREA_LOOKUP = "area";
    private static final String MILESTONE_LOOKUP = "milestone";
    private static final String RESOLUTION_LOOKUP = "resolution";

    static
    {
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
                new PropertyStorageSpec("ClosedBy", JdbcType.INTEGER),
                new PropertyStorageSpec("Duplicate", JdbcType.INTEGER),
                new PropertyStorageSpec("Lastindexed", JdbcType.TIMESTAMP)
        )));

        // required property descriptors, initialized at domain creation time
        REQUIRED_PROPERTIES = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(
                new PropertyStorageSpec("AssignedTo", JdbcType.INTEGER).setNullable(false),
                new PropertyStorageSpec("Title", JdbcType.VARCHAR, 255).setNullable(false),
                new PropertyStorageSpec("Type", JdbcType.VARCHAR, 200),
                new PropertyStorageSpec("Area", JdbcType.VARCHAR, 200),
                new PropertyStorageSpec("NotifyList", JdbcType.VARCHAR),
                new PropertyStorageSpec("Priority", JdbcType.INTEGER).setNullable(false),
                new PropertyStorageSpec("Milestone", JdbcType.VARCHAR, 200),
                new PropertyStorageSpec("Resolution", JdbcType.VARCHAR, 200)
        )));

        FOREIGN_KEYS = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(
                new PropertyStorageSpec.ForeignKey(PRIORITY_LOOKUP, "Lists", PRIORITY_LOOKUP, "value", null, false),
                new PropertyStorageSpec.ForeignKey(TYPE_LOOKUP, "Lists", TYPE_LOOKUP, "value", null, false),
                new PropertyStorageSpec.ForeignKey(AREA_LOOKUP, "Lists", AREA_LOOKUP, "value", null, false),
                new PropertyStorageSpec.ForeignKey(MILESTONE_LOOKUP, "Lists", MILESTONE_LOOKUP, "value", null, false),
                new PropertyStorageSpec.ForeignKey(RESOLUTION_LOOKUP, "Lists", RESOLUTION_LOOKUP, "value", null, false)
        )));

        RESERVED_NAMES = BASE_PROPERTIES.stream().map(PropertyStorageSpec::getName).collect(Collectors.toSet());
        RESERVED_NAMES.addAll(Arrays.asList("RowId", "Name"));
        RESERVED_NAMES.addAll(REQUIRED_PROPERTIES.stream().map(PropertyStorageSpec::getName).collect(Collectors.toSet()));

        // field names that are contained in the issues table that get's joined to the provisioned table
        RESERVED_NAMES.addAll(Arrays.asList("IssueId", "AssignedTo", "Modified", "ModifiedBy",
                "Created", "CreatedBy", "Resolved", "ResolvedBy", "Status", "BuildFound",
                "Tag", "Resolution", "Duplicate", "ClosedBy", "Closed", "LastIndexed", "IssueDefId"));

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
        return PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(containerUser.getContainer(), domain.getTypeURI(), false, false, true);
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

    @Override
    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys(Container container)
    {
        return FOREIGN_KEYS;
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
            // delete any of the built in lookups that are created automatically
            deleteLookup(domain, user, PRIORITY_LOOKUP);
            deleteLookup(domain, user, AREA_LOOKUP);
            deleteLookup(domain, user, TYPE_LOOKUP);
            deleteLookup(domain, user, MILESTONE_LOOKUP);
            deleteLookup(domain, user, RESOLUTION_LOOKUP);
            domain.delete(user);
        }
        catch (DomainNotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void deleteLookup(Domain domain, User user, String lookupName) throws DomainNotFoundException
    {
        Container c = domain.getContainer();

        ListDefinition def = ListService.get().getList(c, getLookupTableName(domain.getName(), lookupName));
        if (def != null)
            def.delete(user);
    }

    public String getLookupTableName(String domainName, String lookupTemplateName)
    {
        return domainName + "-" + lookupTemplateName + "-lookup";
    }

    /**
     * Create the lists for any of the built in lookup fields (priority, type, area and milestone)
     */
    public void createLookupDomains(Container domainContainer, User user, String domainName) throws BatchValidationException
    {
        DomainTemplateGroup templateGroup = DomainTemplateGroup.get(domainContainer, ISSUE_LOOKUP_TEMPLATE_GROUP);

        DomainTemplate priorityTemplate = templateGroup.getTemplate(PRIORITY_LOOKUP);
        priorityTemplate.createAndImport(domainContainer, user, getLookupTableName(domainName, PRIORITY_LOOKUP), true, true);

        DomainTemplate typeTemplate = templateGroup.getTemplate(TYPE_LOOKUP);
        typeTemplate.createAndImport(domainContainer, user, getLookupTableName(domainName, TYPE_LOOKUP), true, true);

        DomainTemplate areaTemplate = templateGroup.getTemplate(AREA_LOOKUP);
        areaTemplate.createAndImport(domainContainer, user, getLookupTableName(domainName, AREA_LOOKUP), true, false);

        DomainTemplate milestoneTemplate = templateGroup.getTemplate(MILESTONE_LOOKUP);
        milestoneTemplate.createAndImport(domainContainer, user, getLookupTableName(domainName, MILESTONE_LOOKUP), true, false);

        DomainTemplate resolutionTemplate = templateGroup.getTemplate(RESOLUTION_LOOKUP);
        resolutionTemplate.createAndImport(domainContainer, user, getLookupTableName(domainName, RESOLUTION_LOOKUP), true, true);
    }
}

