package org.labkey.issue.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;

/**
 * Created by klum on 6/24/2016.
 */
public class AllIssuesTable extends FilteredTable<IssuesQuerySchema>
{
    public AllIssuesTable(@NotNull IssuesQuerySchema schema)
    {
        super(IssuesSchema.getInstance().getTableInfoIssues(), schema);

        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Container")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("IssueId")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("EntityId")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Duplicate")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("LastIndexed")));
    }
}
