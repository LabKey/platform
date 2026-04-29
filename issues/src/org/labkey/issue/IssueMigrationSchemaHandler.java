package org.labkey.issue;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.InClause;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.migration.DatabaseMigrationConfiguration;
import org.labkey.api.migration.DefaultMigrationSchemaHandler;
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

    private final Set<Integer> COPIED_ISSUE_IDS = new HashSet<>();

    private boolean _filtered;

    public IssueMigrationSchemaHandler()
    {
        super(DbSchema.get(IssuesSchema.ISSUE_DEF_SCHEMA_NAME, DbSchemaType.Provisioned));
    }

    @Override
    public void beforeSchema()
    {
        _filtered = false;
    }

    @Override
    public void afterTable(TableInfo sourceTable, TableInfo targetTable, SimpleFilter notCopiedFilter)
    {
        // afterTable() is called only when rows are filtered (i.e., a DomainDataFilter is configured). Remember this so
        // afterSchema() can clean up associated tables (or not).
        _filtered = true;

        // Collect the issue IDs that were copied into the target table. We're assuming this set is much smaller than
        // the set of issues IDs that *weren't* copied.
        int startSize = COPIED_ISSUE_IDS.size();

        // Join the provisioned table to the issues table to get the IssueIds associated with the rows that were copied
        SQLClause joinOnEntityId = new SQLClause(
            new SQLFragment("EntityId IN (SELECT EntityId FROM ")
                .append(targetTable)
                .append(")")
        );

        new TableSelector(IssuesSchema.getInstance().getTableInfoIssues(), new CsvSet("IssueId, EntityId"), new SimpleFilter(joinOnEntityId), null).stream(Integer.class)
            .forEach(COPIED_ISSUE_IDS::add);
        LOG.info("   {} added to the IssueId set", StringUtilsLabKey.pluralize(COPIED_ISSUE_IDS.size() - startSize, "IssueId was", "IssueIds were"));
    }

    @Override
    public void afterSchema(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema)
    {
        if (_filtered)
        {
            LOG.info("{} were copied. Now deleting related issues, comments, and issues rows associated with all issues that were not copied.", StringUtilsLabKey.pluralize(COPIED_ISSUE_IDS.size(), "issue"));

            // Delete all issues, comments, and related issues that were NOT copied
            SimpleFilter deleteRelatedFilter = new SimpleFilter(
                new InClause(FieldKey.fromParts("RelatedIssueId"), COPIED_ISSUE_IDS, false, true) // Negated
            );
            int deletedRowCount = Table.delete(IssuesSchema.getInstance().getTableInfoRelatedIssues(), deleteRelatedFilter);
            LOG.info("   Deleted {} from RelatedIssues (RelatedIssueId)", StringUtilsLabKey.pluralize(deletedRowCount, "row"));
            SimpleFilter deleteFilter = new SimpleFilter(
                new InClause(FieldKey.fromParts("IssueId"), COPIED_ISSUE_IDS, false, true) // Negated
            );
            deletedRowCount = Table.delete(IssuesSchema.getInstance().getTableInfoRelatedIssues(), deleteFilter);
            LOG.info("   Deleted {} from RelatedIssues (IssueId)", StringUtilsLabKey.pluralize(deletedRowCount, "row"));
            deletedRowCount = Table.delete(IssuesSchema.getInstance().getTableInfoComments(), deleteFilter);
            LOG.info("   Deleted {} from Comments", StringUtilsLabKey.pluralize(deletedRowCount, "row"));
            deletedRowCount = Table.delete(IssuesSchema.getInstance().getTableInfoIssues(), deleteFilter);
            LOG.info("   Deleted {} from Issues", StringUtilsLabKey.pluralize(deletedRowCount, "row"));
        }
    }

    @Override
    public @NotNull Collection<AttachmentParentType> getAttachmentTypes()
    {
        return List.of(IssueCommentType.get());
    }
}
