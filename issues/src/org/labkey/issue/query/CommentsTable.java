/*
 * Copyright (c) 2013-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;

import java.util.Collections;

/**
 * User: adam
 * Date: 9/21/13
 * Time: 4:38 PM
 */
public class CommentsTable extends FilteredTable<IssuesQuerySchema>
{
    public CommentsTable(IssuesQuerySchema schema)
    {
        super(IssuesSchema.getInstance().getTableInfoComments(), schema);

        ColumnInfo commentIdColumn = wrapColumn(_rootTable.getColumn("CommentId"));
        commentIdColumn.setSortDirection(Sort.SortDirection.DESC);      // This is a nice idea, but only sorts if the column is shown
        commentIdColumn.setHidden(true);
        addColumn(commentIdColumn);

        IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer(), IssueListDef.DEFAULT_ISSUE_LIST_NAME);
        ColumnInfo issueIdColumn = wrapColumn(_rootTable.getColumn("IssueId"));
        issueIdColumn.setLabel(names.singularName);
        ActionURL base = IssuesController.issueURL(_userSchema.getContainer(), IssuesController.DetailsAction.class);
        issueIdColumn.setURL(new DetailsURL(base, Collections.singletonMap("issueId", "IssueId")));
        issueIdColumn.setFk(new LookupForeignKey("IssueId", "IssueId")
        {
            @Override
            public @Nullable TableInfo getLookupTableInfo()
            {
                return IssuesSchema.getInstance().getTableInfoIssues();
            }
        });
        issueIdColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new IssueIdDisplayColumn(colInfo,getContainer(), getUserSchema().getUser());
            }
        });
        addColumn(issueIdColumn);

        ColumnInfo createdBy = wrapColumn(_rootTable.getColumn("CreatedBy"));
        UserIdForeignKey.initColumn(createdBy);
        addColumn(createdBy);

        addWrapColumn(_rootTable.getColumn("Created"));
        ColumnInfo comment = addWrapColumn(_rootTable.getColumn("Comment"));

        // Special display column to render the HTML comment with proper formatting
        comment.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                DataColumn dc = new DataColumn(colInfo) {
                    @Override @NotNull
                    public String getFormattedValue(RenderContext ctx)
                    {
                        String html = super.getFormattedValue(ctx);

                        // Comment HTML is stored in the database, so use an HTML5 scoped style tag to remove the borders
                        // Unconventional, but this seems to be supported on most browsers
                        String inline = "<style type=\"text/css\" scoped>table.issues-Changes td {border:none;}</style>";

                        return "<span>" + inline + "</span>" + html;
                    }
                };
                dc.setRequiresHtmlFiltering(false);
                dc.setPreserveNewlines(false);

                return dc;
            }
        });

        // Don't need a "Details" column
        setDetailsURL(AbstractTableInfo.LINK_DISABLER);
    }

    @Override
    public FieldKey getContainerFieldKey()
    {
        return FieldKey.fromParts("IssueId", "Container");
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        FieldKey containerFieldKey = FieldKey.fromParts("Container");
        clearConditions(containerFieldKey);
        SQLFragment sql = new SQLFragment("IssueId IN (SELECT i.IssueId FROM ");
        sql.append(IssuesSchema.getInstance().getTableInfoIssues(), "i");
        sql.append(" WHERE ");
        sql.append(filter.getSQLFragment(getSchema(), new SQLFragment("i.Container"), getContainer()));
        sql.append(")");
        addCondition(sql, containerFieldKey);
    }
}

/**
 * Display column to render the title of the issue
 */
class IssueIdDisplayColumn extends DataColumn
{
    private Container _container;
    private User _user;

    public IssueIdDisplayColumn(ColumnInfo col, Container container, User user)
    {
        super(col);
        _container = container;
        _user = user;
    }

    @NotNull
    @Override
    public String getFormattedValue(RenderContext ctx)
    {
        String title = getIssueTitle(ctx);
        return title != null ? title : super.getFormattedValue(ctx);
    }

    @Nullable
    private String getIssueTitle(RenderContext ctx)
    {
        Object o = getValue(ctx);
        if (o instanceof Integer)
        {
            Issue issue = IssueManager.getIssue(_container, _user, (Integer)o);
            if (issue != null)
            {
                return issue.getTitle();
            }
        }
        return null;
    }
}

