/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.*;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.view.ActionURL;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.IssueManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IssuesTable extends FilteredTable<IssuesQuerySchema>
{
    private static final List<String> DEFAULT_LIST_COLUMNS = Arrays.asList("IssueId", "Type", "Area", "Title",
            "AssignedTo", "Priority", "Status", "Milestone");

    public IssuesTable(IssuesQuerySchema schema)
    {
        super(IssuesSchema.getInstance().getTableInfoIssues(), schema);

        addAllColumns();

        setDefaultVisibleColumns(getDefaultColumns());
        ActionURL base = IssuesController.issueURL(_userSchema.getContainer(), IssuesController.DetailsAction.class);
        setDetailsURL(new DetailsURL(base, Collections.singletonMap("issueId", "IssueId")));
        setTitleColumn("Title");
    }

    private String getCustomCaption(String realName, Map<String, String> customCaptions)
    {
        String result = customCaptions.get(realName);
        return result == null ? realName : result;
    }

    private void addAllColumns()
    {
        IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());

        Map<String, String> customColumnCaptions = IssueManager.getCustomColumnConfiguration(_userSchema.getContainer()).getColumnCaptions();

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
        issueIdColumn.setLabel(names.singularName.getSource() + " ID");
        addColumn(issueIdColumn);

        ColumnInfo folder = new AliasedColumn(this, "Folder", _rootTable.getColumn("container"));
        folder.setHidden(true);
        addColumn(folder);

        addColumn(new AliasedColumn(this, getCustomCaption("Type", customColumnCaptions), _rootTable.getColumn("Type")));
        addColumn(new AliasedColumn(this, getCustomCaption("Area", customColumnCaptions), _rootTable.getColumn("Area")));
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
        addColumn(new AliasedColumn(this, getCustomCaption("Priority", customColumnCaptions), _rootTable.getColumn("Priority")));
        addWrapColumn(_rootTable.getColumn("Status"));
        addColumn(new AliasedColumn(this, getCustomCaption("Milestone", customColumnCaptions), _rootTable.getColumn("Milestone")));

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
        addColumn(new AliasedColumn(this, getCustomCaption("Resolution", customColumnCaptions), _rootTable.getColumn("Resolution")));
        addWrapColumn(_rootTable.getColumn("Duplicate"));
        ColumnInfo closedBy = wrapColumn(_rootTable.getColumn("ClosedBy"));
        UserIdForeignKey.initColumn(closedBy);
        addColumn(closedBy);
        addWrapColumn(_rootTable.getColumn("Closed"));
        addWrapColumn(_rootTable.getColumn("NotifyList"));
        // add any custom columns
        for (Map.Entry<String, String> cce : customColumnCaptions.entrySet())
        {
            ColumnInfo realColumn = getRealTable().getColumn(cce.getKey());
            if (realColumn != null)
            {
                ColumnInfo column = new AliasedColumn(this, cce.getValue(), realColumn);
                if (getColumn(column.getName()) == null)
                    addColumn(column);
            }
        }
    }

    // Returns the default list of visible columns
    private List<FieldKey> getDefaultColumns()
    {
        Set<FieldKey> visibleColumns = new LinkedHashSet<FieldKey>();

        Map<String, String> columnCaptions = IssueManager.getCustomColumnConfiguration(_userSchema.getContainer()).getColumnCaptions();

        for (String name : DEFAULT_LIST_COLUMNS)
            visibleColumns.add(FieldKey.fromParts(getCustomCaption(name, columnCaptions)));

        for (String columnName : columnCaptions.values())
            visibleColumns.add(FieldKey.fromParts(columnName));

        return new ArrayList<FieldKey>(visibleColumns);
    }

    @Override
    public ContainerContext getContainerContext()
    {
        return new ContainerContext.FieldKeyContext(getContainerFieldKey());
    }

    @Override
    public FieldKey getContainerFieldKey()
    {
        return new FieldKey(null, "Folder");
    }
}
