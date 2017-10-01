/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.module.Module;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.VirtualFile;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
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
                return new AllIssuesTable(this).init();
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

    /**
     * Add the built in custom views for : all, open, resolved and mine.
     */
    @Override
    public List<CustomView> getModuleCustomViews(Container container, QueryDefinition qd)
    {
        List<CustomView> customViews = new ArrayList<>();
        String queryName = qd.getName();

        // built in custom views only apply to issue lists
        if (!tableNames.contains(queryName) && !TableType.IssueListDef.name().equals(queryName))
        {
            IssueListDef issueListDef = IssueManager.getIssueListDef(container, queryName);
            if (issueListDef != null)
            {
                customViews.add(new IssuesBuiltInCustomView(qd, "all", Collections.emptyList(), null));

                Domain domain = issueListDef.getDomain(getUser());
                Sort sort = new Sort("AssignedTo/DisplayName");
                if (domain != null && domain.getPropertyByName("Milestone") != null)
                    sort.insertSortColumn(FieldKey.fromParts("Milestone"), Sort.SortDirection.ASC, true);

                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("Status"), "open");

                customViews.add(new IssuesBuiltInCustomView(qd, "open", filter.getClauses(), sort));

                filter = new SimpleFilter(FieldKey.fromString("Status"), "resolved");
                customViews.add(new IssuesBuiltInCustomView(qd, "resolved", filter.getClauses(), sort));

                if (!getUser().isGuest())
                {
                    filter = new SimpleFilter(FieldKey.fromString("AssignedTo/DisplayName"), "~me~");
                    filter.addCondition(FieldKey.fromString("Status"), "closed", CompareType.NEQ_OR_NULL);
                    customViews.add(new IssuesBuiltInCustomView(qd, "mine", filter.getClauses(), sort));
                }
            }
        }
        return customViews;
    }

    @Override
    public @Nullable String getDomainURI(String queryName)
    {
        Container container = getContainer();

        IssueListDef issueListDef = IssueManager.getIssueListDef(container, queryName);
        if (issueListDef == null)
            return null;

        Domain domain = issueListDef.getDomain(null);
        if (domain == null)
            return null;

        return domain.getTypeURI();
    }

    private static class IssuesBuiltInCustomView implements CustomView
    {
        private QueryDefinition _queryDef;
        private String _name;
        private List<FieldKey> _columns;
        private List<SimpleFilter.FilterClause> _filters = new ArrayList<>();
        private String _filterText;
        private Sort _sort;

        public IssuesBuiltInCustomView(QueryDefinition def, String name, List<SimpleFilter.FilterClause> filters, @Nullable Sort sort)
        {
            _queryDef = def;
            _name = name;
            _filters.addAll(filters);
            _sort = sort;
        }

        @Override
        public QueryDefinition getQueryDefinition()
        {
            return _queryDef;
        }

        @Override
        public void setName(String name)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setQueryName(String queryName)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setCanInherit(boolean f)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean canEdit(Container c, Errors errors)
        {
            return false;
        }

        @Override
        public void setIsHidden(boolean f)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setColumns(List<FieldKey> columns)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setColumnProperties(List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> list)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void applyFilterAndSortToURL(ActionURL url, String dataRegionName)
        {
            if (!hasFilterOrSort())
                return;

            for (SimpleFilter.FilterClause clause : _filters)
            {
                Map.Entry<String, String> param = clause.toURLParam(dataRegionName + ".");
                url.addParameter(param.getKey(), param.getValue());
            }

            if (_sort != null)
                url.addParameter(dataRegionName + ".sort", _sort.getURLParamValue());
        }

        @Override
        public void setFilterAndSortFromURL(ActionURL url, String dataRegionName)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFilterAndSort(String filter)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(User user, HttpServletRequest request) throws QueryException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(User user, HttpServletRequest request) throws QueryException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean serialize(VirtualFile dir) throws IOException
        {
            return true;
        }

        @Override
        public String getName()
        {
            return _name;
        }

        @Override
        public String getLabel()
        {
            return _name;
        }

        @Override
        public User getOwner()
        {
            return null;
        }

        @Override
        public boolean isShared()
        {
            return false;
        }

        @Override
        public User getCreatedBy()
        {
            return null;
        }

        @NotNull
        @Override
        public Date getCreated()
        {
            return new Date();
        }

        @Override
        public User getModifiedBy()
        {
            return null;
        }

        @NotNull
        @Override
        public Date getModified()
        {
            return new Date();
        }

        @NotNull
        @Override
        public String getSchemaName()
        {
            return _queryDef.getSchema().getSchemaName();
        }

        @NotNull
        @Override
        public SchemaKey getSchemaPath()
        {
            return _queryDef.getSchema().getSchemaPath();
        }

        @NotNull
        @Override
        public String getQueryName()
        {
            return _queryDef.getName();
        }

        @Override
        public Container getContainer()
        {
            return null;
        }

        @Override
        public String getEntityId()
        {
            return null;
        }

        @Override
        public boolean canInherit()
        {
            return false;
        }

        @Override
        public boolean isHidden()
        {
            return false;
        }

        @Override
        public boolean isEditable()
        {
            return false;
        }

        @Override
        public boolean isDeletable()
        {
            return false;
        }

        @Override
        public boolean isRevertable()
        {
            return false;
        }

        @Override
        public boolean isOverridable()
        {
            return true;
        }

        @Override
        public boolean isSession()
        {
            return false;
        }

        @Override
        public String getCustomIconUrl()
        {
            return "/issues/built-in.png";
        }

        @Override
        public String getCustomIconCls()
        {
            return null;
        }

        @NotNull
        @Override
        public List<FieldKey> getColumns()
        {
            if (_columns == null)
            {
                _columns = new ArrayList<>();
                TableInfo table = _queryDef.getTable(new ArrayList<QueryException>(), true);
                if (table != null)
                {
                    _columns = table.getDefaultVisibleColumns();
                }
            }
            return _columns;
        }

        @NotNull
        @Override
        public List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> getColumnProperties()
        {
            List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> result = new ArrayList<>();
            for (FieldKey fieldKey : getColumns())
            {
                result.add(new Pair<>(fieldKey, Collections.emptyMap()));
            }
            return result;
        }

        @Override
        public String getFilterAndSort()
        {
            if (_filterText == null)
            {
                try
                {
                    URLHelper dest = new URLHelper("");
                    for (SimpleFilter.FilterClause clause : _filters)
                    {
                        Map.Entry<String, String> param = clause.toURLParam(FILTER_PARAM_PREFIX + ".");
                        dest.addParameter(param.getKey(), param.getValue());
                    }

                    if (_sort != null)
                        dest.addParameter(FILTER_PARAM_PREFIX + ".sort", _sort.getURLParamValue());

                    _filterText = dest.toString();
                }
                catch (URISyntaxException use)
                {
                    throw UnexpectedException.wrap(use);
                }
            }
            return _filterText;
        }

        @Override
        public String getContainerFilterName()
        {
            return ContainerFilter.Type.Current.name();
        }

        @Override
        public boolean hasFilterOrSort()
        {
            return !_filters.isEmpty();
        }

        @Override
        public Collection<String> getDependents(User user)
        {
            return Collections.emptyList();
        }
    }
}
