package org.labkey.issue.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.FilteredTable;

/**
 * Created by klum on 6/24/2016.
 */
public class AllIssuesTable extends FilteredTable<IssuesQuerySchema>
{
    public AllIssuesTable(@NotNull IssuesQuerySchema schema)
    {
        super(IssuesSchema.getInstance().getTableInfoIssues(), schema);

        wrapAllColumns(true);
    }
}
