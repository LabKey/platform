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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.springframework.validation.BindException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class IssuesQuerySchema extends UserSchema 
{
    public static final String SCHEMA_NAME = "issues";
    public static final String ALL_ISSUE_TABLE = "all_issues";      // legacy all issues
    public static final String SCHEMA_DESCR = "Contains one data table containing all the issues.";

    public enum TableType
    {
        RelatedIssues
        {
            @Override
            public TableInfo createTable(IssuesQuerySchema schema)
            {
                SimpleUserSchema.SimpleTable<IssuesQuerySchema> table =
                        new SimpleUserSchema.SimpleTable<>(
                                schema, IssuesSchema.getInstance().getTableInfoRelatedIssues()).init();

                return table;
            }
        },
        Comments
        {
            @Override
            public TableInfo createTable(IssuesQuerySchema schema)
            {
                return new CommentsTable(schema);
            }
        },
        IssueListDef
        {
            @Override
            public TableInfo createTable(IssuesQuerySchema schema)
            {
                return new IssuesListDefTable(schema);
            }
        };

        public abstract TableInfo createTable(IssuesQuerySchema schema);
    }
    static private Set<String> tableNames;
    static private Set<String> visibleTableNames;
    static
    {
        tableNames = Collections.unmodifiableSet(
                Sets.newCaseInsensitiveHashSet(TableType.Comments.toString(), TableType.RelatedIssues.toString(), ALL_ISSUE_TABLE));

        visibleTableNames = Collections.unmodifiableSet(
                Sets.newCaseInsensitiveHashSet(TableType.Comments.toString()));
    }

    static public Set<String> getReservedTableNames()
    {
        return tableNames;
    }

    static public void register(final Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module) {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                // CONSIDER: use default implementation and only publish schema if module is active
                return true;
            }

            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new IssuesQuerySchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public IssuesQuerySchema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHEMA_DESCR, user, container, IssuesSchema.getInstance().getSchema());
    }

    public Set<String> getTableNames()
    {
        Set<String> names = new HashSet<>();

        names.addAll(tableNames);
        names.add(TableType.IssueListDef.name());
        names.addAll(IssueManager.getIssueListDefs(getContainer()).stream().map(IssueListDef::getName).collect(Collectors.toList()));
        return names;
    }

    @Override
    public Set<String> getVisibleTableNames()
    {
        Set<String> names = new HashSet<>();

        names.addAll(visibleTableNames);
        names.add(TableType.IssueListDef.name());
        names.addAll(IssueManager.getIssueListDefs(getContainer()).stream().map(IssueListDef::getName).collect(Collectors.toList()));
        return names;
    }

    public TableInfo createTable(String name)
    {
        if (name != null)
        {
            TableInfo issueTable = getIssueTable(name);
            if (issueTable != null)
            {
                return issueTable;
            }

            TableType tableType = null;
            for (TableType t : TableType.values())
            {
                // Make the enum name lookup case insensitive
                if (t.name().equalsIgnoreCase(name.toLowerCase()))
                {
                    tableType = t;
                    break;
                }
            }
            if (tableType != null)
            {
                return tableType.createTable(this);
            }

            if (name.equalsIgnoreCase(ALL_ISSUE_TABLE))
            {
                return new AllIssuesTable(this);
            }
        }
        return null;
    }

    @Override
    public QueryView createView(ViewContext context, QuerySettings settings, BindException errors)
    {
        String queryName = settings.getQueryName();
        if (queryName != null)
        {
            // check for an issue definition
            IssueListDef def =  getIssueDefs().get(queryName);
            if (def != null)
            {
                return new IssuesQueryView(def, context, this, settings, errors);
            }
            else if (queryName.equals(TableType.IssueListDef.name()))
            {
                return new QueryView(this, settings, errors){

                    @Override
                    public ActionButton createDeleteButton()
                    {
                        ActionButton button = super.createDeleteButton();

                        button.setRequiresSelection(true);
                        button.setActionType(ActionButton.Action.GET);

                        return button;
                    }
                };
            }
        }

        return super.createView(context, settings, errors);
    }

    @Nullable
    protected TableInfo getIssueTable(String name)
    {
        IssueListDef issueDef = getIssueDefs().get(name);
        if (issueDef == null)
            return null;

        return new IssuesTable(this, issueDef);
    }

    private Map<String, IssueListDef> getIssueDefs()
    {
        Map<String, IssueListDef> map = new CaseInsensitiveTreeMap<>();
        for (IssueListDef def : IssueManager.getIssueListDefs(getContainer()))
        {
            map.put(def.getName(), def);
        }
        return map;
    }
}
