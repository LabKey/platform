/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

import com.drew.lang.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.MultiValuedDisplayColumn;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.MultiValuedLookupColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserIdRenderer;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.model.IssueManager.CustomColumn;
import org.labkey.issue.model.IssueManager.CustomColumnConfiguration;
import org.labkey.issue.model.IssueManager.EntryTypeNames;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.io.Writer;

public class IssuesTable extends FilteredTable<IssuesQuerySchema>
{
    private static final List<String> DEFAULT_LIST_COLUMNS = Arrays.asList("IssueId", "Type", "Area", "Title",
            "AssignedTo", "Priority", "Status", "Milestone");

    public IssuesTable(IssuesQuerySchema schema)
    {
        super(IssuesSchema.getInstance().getTableInfoIssues(), schema);

        addAllColumns();

        setDefaultVisibleColumns(getDefaultColumns());
        setTitleColumn("Title");
    }

    private String getCustomCaption(String realName, CustomColumnConfiguration ccc)
    {
        String caption = ccc.getCaption(realName.toLowerCase());
        return caption == null ? realName : caption;
    }

    private void addAllColumns()
    {
        ActionURL base = IssuesController.issueURL(_userSchema.getContainer(), IssuesController.DetailsAction.class);
        DetailsURL detailsURL = new DetailsURL(base, Collections.singletonMap("issueId", "IssueId"));
        setDetailsURL(detailsURL);

        EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
        CustomColumnConfiguration ccc = IssueManager.getCustomColumnConfiguration(getContainer());

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
        issueIdColumn.setURL(detailsURL);
        addColumn(issueIdColumn);

        ColumnInfo folder = new AliasedColumn(this, "Folder", _rootTable.getColumn("container"));
        folder.setHidden(true);
        ContainerForeignKey.initColumn(folder, _userSchema);
        addColumn(folder);

        addColumn(new AliasedColumn(this, getCustomCaption("Type", ccc), _rootTable.getColumn("Type")));

        addColumn(new AliasedColumn(this, getCustomCaption("Area", ccc), _rootTable.getColumn("Area")));

        addWrapColumn(_rootTable.getColumn("Title"));

        ColumnInfo assignedTo = wrapColumn("AssignedTo", _rootTable.getColumn("AssignedTo"));
        assignedTo.setFk(new UserIdForeignKey(getUserSchema()));
        assignedTo.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new UserIdRenderer.GuestAsBlank(colInfo);
            }
        });
        addColumn(assignedTo);

        addColumn(new AliasedColumn(this, getCustomCaption("Priority", ccc), _rootTable.getColumn("Priority")));

        addWrapColumn(_rootTable.getColumn("Status"));

        addColumn(new AliasedColumn(this, getCustomCaption("Milestone", ccc), _rootTable.getColumn("Milestone")));

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
        addColumn(new AliasedColumn(this, getCustomCaption("Resolution", ccc), _rootTable.getColumn("Resolution")));

        ColumnInfo duplicate = addWrapColumn(_rootTable.getColumn("Duplicate"));
        duplicate.setURL(new DetailsURL(base, Collections.singletonMap("issueId", "Duplicate")));
        duplicate.setDisplayColumnFactory(new URLTitleDisplayColumnFactory("Issue ${Duplicate}: ${Duplicate/Title:htmlEncode}"));
        duplicate.setFk(new QueryForeignKey(getUserSchema(), getContainer(), "Issues", "IssueId", "IssueId"));

        ColumnInfo related = addColumn(new AliasedColumn(this, getCustomCaption("Related", ccc), issueIdColumn));
        related.setKeyField(false);

        DetailsURL relatedURL = new DetailsURL(base, Collections.singletonMap("issueId", FieldKey.fromParts(getCustomCaption("Related", ccc), "IssueId")));
        relatedURL.setContainerContext(new ContainerContext.FieldKeyContext(FieldKey.fromParts(getCustomCaption("Related", ccc), "Folder")));
        related.setURL(relatedURL);
        related.setFk(new MultiValuedForeignKey(
                new QueryForeignKey(getUserSchema(), getContainer(), "RelatedIssues", "IssueId", null),
                "RelatedIssueId",
                "IssueId")
        {
            @Override
            protected MultiValuedLookupColumn createMultiValuedLookupColumn(ColumnInfo relatedIssueId, ColumnInfo parent, ColumnInfo childKey, ColumnInfo junctionKey, ForeignKey fk)
            {
                relatedIssueId.setDisplayColumnFactory(new URLTitleDisplayColumnFactory("Issue ${Related/IssueId}: ${Related/Title:htmlEncode}"));

                return super.createMultiValuedLookupColumn(relatedIssueId, parent, childKey, junctionKey, fk);
            }
        });

        related.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                DataColumn dataColumn = new DataColumn(colInfo);
                dataColumn.setURLTitle(new StringExpressionFactory.FieldKeyStringExpression("Issue ${Related/IssueId}: ${Related/Title:htmlEncode}", false, StringExpressionFactory.AbstractStringExpression.NullValueBehavior.NullResult));

                MultiValuedDisplayColumn displayColumn = new MultiValuedDisplayColumn(dataColumn, true);
                return displayColumn;
            }
        });

        ColumnInfo closedBy = wrapColumn(_rootTable.getColumn("ClosedBy"));
        UserIdForeignKey.initColumn(closedBy);
        addColumn(closedBy);

        addWrapColumn(_rootTable.getColumn("Closed"));

        ColumnInfo notifyList = addWrapColumn(_rootTable.getColumn("NotifyList"));
        notifyList.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new NotifyListDisplayColumn(colInfo, _userSchema.getUser());
            }
        });

        // add any custom columns that weren't added above
        for (CustomColumn cc : ccc.getCustomColumns(_userSchema.getUser()))
        {
            ColumnInfo realColumn = getRealTable().getColumn(cc.getName());
            if (realColumn != null)
            {
                ColumnInfo column = new AliasedColumn(this, cc.getCaption(), realColumn);
                if (getColumn(column.getName()) == null)
                    addColumn(column);
            }
        }
    }

    // Returns the default list of visible columns
    private List<FieldKey> getDefaultColumns()
    {
        Set<FieldKey> visibleColumns = new LinkedHashSet<>();

        CustomColumnConfiguration ccc = IssueManager.getCustomColumnConfiguration(getContainer());

        for (String name : DEFAULT_LIST_COLUMNS)
            visibleColumns.add(FieldKey.fromParts(getCustomCaption(name, ccc)));

        for (CustomColumn column : ccc.getCustomColumns(_userSchema.getUser()))
            visibleColumns.add(FieldKey.fromParts(column.getCaption()));

        return new ArrayList<>(visibleColumns);
    }

    @Override
    public FieldKey getContainerFieldKey()
    {
        return new FieldKey(null, "Folder");
    }
}


// TODO: Remove this class after adding a 'linkTitle' or 'urlTitle' property to ColumnRenderProperties
class URLTitleDisplayColumnFactory implements DisplayColumnFactory
{
    StringExpressionFactory.FieldKeyStringExpression _urlTitleExpr;

    public URLTitleDisplayColumnFactory(String urlTitleExpr)
    {
        this(new StringExpressionFactory.FieldKeyStringExpression(urlTitleExpr, false, StringExpressionFactory.AbstractStringExpression.NullValueBehavior.NullResult));
    }

    public URLTitleDisplayColumnFactory(StringExpressionFactory.FieldKeyStringExpression urlTitleExpr)
    {
        _urlTitleExpr = urlTitleExpr;
    }

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        DisplayColumn displayColumn = new DataColumn(colInfo);
        displayColumn.setURLTitle(_urlTitleExpr);
        return displayColumn;
    }
}

class NotifyListDisplayColumn extends DataColumn
{
    private User _user;
    private static final String DELIM = ", ";

    public NotifyListDisplayColumn(ColumnInfo col, User curUser)
    {
        super(col);
        _user = curUser;
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object o = getValue(ctx);
        if (o != null)
        {
            List<String> usernames = new ArrayList<>();

            for (String notifyUser : o.toString().split(";"))
            {
                notifyUser = parseUserDisplayName(notifyUser);
                if (notifyUser != null)
                    usernames.add(notifyUser);
            }

            out.write(StringUtils.join(usernames, DELIM));
        }
    }

    @Nullable
    public String parseUserDisplayName(String part)
    {
        part = StringUtils.trimToNull(part);
        if (part != null)
        {
            // Issue 20914
            // NOTE: this doesn't address the bad data in the backend just displaying it
            // TODO: consider update script for fixing this issue...
            try
            {
                return UserManager.getUser(Integer.parseInt(part)).getDisplayName(_user);
            }
            catch (NumberFormatException e)
            {
                return part;
            }
        }
        return null;
    }
}