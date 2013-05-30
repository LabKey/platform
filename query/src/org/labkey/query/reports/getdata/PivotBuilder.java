package org.labkey.query.reports.getdata;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.FieldKey;

import java.util.Collections;
import java.util.List;

/**
 * Adds a PIVOT clause to a LabKey SQL query. The "columns" are the expressions immediately after the "PIVOT" in the
 * query, and the "by" comes after. For example, "CountOfIssues" is the value of "columns" and "Priority" is
 * the value of "by" in the query below:
 *
 * SELECT Issues.Area, Issues.Priority, count(Issues.IssueId) AS CountOfIssues
 * FROM Issues
 * GROUP BY Issues.Area, Issues.Priority
 * PIVOT CountOfIssues BY Priority
 *
 * User: jeckels
 * Date: 5/29/13
 */
public class PivotBuilder
{
    @NotNull private List<FieldKey> _columns = Collections.emptyList();
    private FieldKey _by;

    public void setColumns(@NotNull List<FieldKey> columns)
    {
        _columns = columns;
    }

    public void setBy(FieldKey by)
    {
        _by = by;
    }

    public void validate()
    {
        if (!_columns.isEmpty() || _by != null)
        {
            if (_columns.isEmpty())
            {
                throw new IllegalStateException("No values specified on which to pivot");
            }
            if (_by == null)
            {
                throw new IllegalStateException("No field specified by which to pivot");
            }
        }
    }

    @NotNull
    public List<FieldKey> getColumns()
    {
        return _columns;
    }

    public FieldKey getBy()
    {
        return _by;
    }
}
