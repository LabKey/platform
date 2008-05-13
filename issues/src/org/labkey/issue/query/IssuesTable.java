/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.issue.query;

import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.*;
import org.labkey.api.view.ActionURL;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.IssueManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class IssuesTable extends FilteredTable
{
    private static Logger _log = Logger.getLogger(IssuesTable.class);

    private static final String DEFAULT_LIST_COLUMNS = "IssueId,Type,Area,Title,AssignedTo,Priority,Status,Milestone";
    private IssuesQuerySchema _schema;

    public IssuesTable(IssuesQuerySchema schema)
    {
        super(IssuesSchema.getInstance().getTableInfoIssues(), schema.getContainer());
        _schema = schema;

        addAllColumns();

        setDefaultVisibleColumns(getDefaultColumns());
        ActionURL base = IssuesController.issueURL(_schema.getContainer(), "details");
        setDetailsURL(new DetailsURL(base, Collections.singletonMap("issueId", "IssueId")));
        setTitleColumn("Title");
    }

    private void addAllColumns()
    {
        ColumnInfo issueIdColumn = wrapColumn(_rootTable.getColumn("IssueId"));
        issueIdColumn.setFk(new RowIdForeignKey(issueIdColumn)
        {
            public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
            {
                if (displayField == null)
                    return null;
                return super.createLookupColumn(parent, displayField);
            }
        });

        issueIdColumn.setKeyField(true);
        addColumn(issueIdColumn);
        addWrapColumn(_rootTable.getColumn("Type"));
        addWrapColumn(_rootTable.getColumn("Area"));
        addWrapColumn(_rootTable.getColumn("Title"));
        ColumnInfo assignedTo = wrapColumn("AssignedTo", _rootTable.getColumn("AssignedTo"));
        assignedTo.setFk(new UserIdForeignKey());
        assignedTo.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new UserIdRenderer.GuestAsBlank(colInfo);
            }
        });
        addColumn(assignedTo);
        addWrapColumn(_rootTable.getColumn("Priority"));
        addWrapColumn(_rootTable.getColumn("Status"));
        addWrapColumn(_rootTable.getColumn("Milestone"));

        addWrapColumn(_rootTable.getColumn("BuildFound"));
        ColumnInfo modifiedBy = wrapColumn(_rootTable.getColumn("ModifiedBy"));
        UserIdForeignKey.initColumn(modifiedBy);
        addColumn(modifiedBy);
        addWrapColumn(_rootTable.getColumn("Modified"));
        ColumnInfo createdBy = wrapColumn(_rootTable.getColumn("CreatedBy"));
        UserIdForeignKey.initColumn(createdBy);
        addColumn(createdBy);
        addWrapColumn(_rootTable.getColumn("Created"));
        ColumnInfo resolvedBy = wrapColumn(_rootTable.getColumn("ResolvedBy"));
        UserIdForeignKey.initColumn(resolvedBy);
        addColumn(resolvedBy);
        addWrapColumn(_rootTable.getColumn("Resolved"));
        addWrapColumn(_rootTable.getColumn("Resolution"));
        addWrapColumn(_rootTable.getColumn("Duplicate"));
        ColumnInfo closedBy = wrapColumn(_rootTable.getColumn("ClosedBy"));
        UserIdForeignKey.initColumn(closedBy);
        addColumn(closedBy);
        addWrapColumn(_rootTable.getColumn("Closed"));
        addWrapColumn(_rootTable.getColumn("NotifyList"));
        // add any custom columns
        Map<String, String> customColumnCaptions = getCustomColumnCaptions(_schema.getContainer());
        for (Map.Entry<String, String> cce : customColumnCaptions.entrySet())
        {
            ColumnInfo realColumn = getRealTable().getColumn(cce.getKey());
            if (realColumn != null)
            {
                ColumnInfo column = new AliasedColumn(this, cce.getValue(), realColumn);
                column.setAlias(cce.getKey());
                if (getColumn(column.getName()) == null)
                    addColumn(column);
            }
        }
    }

    // Returns the default list of visible columns
    private List<FieldKey> getDefaultColumns()
    {
        List<FieldKey> visibleColumns = new ArrayList<FieldKey>();

        for (String name : DEFAULT_LIST_COLUMNS.split(","))
            visibleColumns.add(FieldKey.fromString(name));

        Map<String, String> columnCaptions = getCustomColumnCaptions(_schema.getContainer());

        for (String columnName : columnCaptions.values())
            visibleColumns.add(FieldKey.fromString(columnName));

        return visibleColumns;
    }

    public static Map<String, String> getCustomColumnCaptions(Container container)
    {
        try {
            return IssueManager.getCustomColumnConfiguration(container).getColumnCaptions();
        }
        catch (Exception e)
        {
            _log.error(e);
        }
        return Collections.emptyMap();
    }
}
