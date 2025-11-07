package org.labkey.issue;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.DatabaseMigrationConfiguration;
import org.labkey.api.data.DatabaseMigrationService.DefaultMigrationSchemaHandler;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.InClause;
import org.labkey.api.data.SimpleFilter.NotClause;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.issue.model.IssueCommentType;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IssueMigrationSchemaHandler extends DefaultMigrationSchemaHandler
{
    private static final Logger LOG = LogHelper.getLogger(IssueMigrationSchemaHandler.class, "Issue migration status");

    private final Set<Integer> ISSUE_IDS = new HashSet<>();

    public IssueMigrationSchemaHandler()
    {
        super(DbSchema.get(IssuesSchema.ISSUE_DEF_SCHEMA_NAME, DbSchemaType.Provisioned));
    }

    @Override
    public void afterTable(TableInfo sourceTable, TableInfo targetTable, SimpleFilter notCopiedFilter)
    {
        // Collect the issue IDs that were copied into the target table. We're assuming this set is much smaller than
        // the set of issues IDs that *weren't* copied.
        int startSize = ISSUE_IDS.size();

        // Join the provisioned table to the issues table to get the IssueIds associated with the rows that were copied
        SQLClause joinOnEntityId = new SQLClause(
            new SQLFragment("EntityId IN (SELECT EntityId FROM ")
                .append(targetTable)
                .append(")")
        );

        new TableSelector(IssuesSchema.getInstance().getTableInfoIssues(), new CsvSet("IssueId, EntityId"), new SimpleFilter(joinOnEntityId), null).stream(Integer.class)
            .forEach(ISSUE_IDS::add);
        LOG.info("   {} added to the IssueId set", StringUtilsLabKey.pluralize(ISSUE_IDS.size() - startSize, "IssueId was", "IssueIds were"));
    }

    @Override
    public void afterSchema(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema)
    {
        LOG.info("   Deleting related issues, comments, and issues rows associated with {}", StringUtilsLabKey.pluralize(ISSUE_IDS.size(), "issue"));

        if (!ISSUE_IDS.isEmpty())
        {
            // Delete all issues, comments, and related issues that were NOT copied
            SimpleFilter deleteRelatedFilter = new SimpleFilter(
                new NotClause(
                    new InClause(FieldKey.fromParts("RelatedIssueId"), ISSUE_IDS)
                )
            );
            Table.delete(IssuesSchema.getInstance().getTableInfoRelatedIssues(), deleteRelatedFilter);
            SimpleFilter deleteFilter = new SimpleFilter(
                new NotClause(
                    new InClause(FieldKey.fromParts("IssueId"), ISSUE_IDS)
                )
            );
            Table.delete(IssuesSchema.getInstance().getTableInfoRelatedIssues(), deleteFilter);
            Table.delete(IssuesSchema.getInstance().getTableInfoComments(), deleteFilter);
            Table.delete(IssuesSchema.getInstance().getTableInfoIssues(), deleteFilter);
        }
    }

    @Override
    public @NotNull Collection<AttachmentType> getAttachmentTypes()
    {
        return List.of(IssueCommentType.get());
    }
}
