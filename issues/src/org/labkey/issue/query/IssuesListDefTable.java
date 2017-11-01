/*
 * Copyright (c) 2016-2017 LabKey Corporation
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

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.issues.IssuesListDefProvider;
import org.labkey.api.issues.IssuesListDefService;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.issue.IssuesController;
import org.labkey.issue.actions.DeleteIssueListAction;
import org.labkey.issue.actions.InsertIssueDefAction;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 4/10/16.
 */
public class IssuesListDefTable extends FilteredTable<IssuesQuerySchema>
{
    private static final Logger LOG = Logger.getLogger(IssuesListDefTable.class);

    public IssuesListDefTable(IssuesQuerySchema schema)
    {
        super(IssuesSchema.getInstance().getTableInfoIssueListDef(), schema);

        ActionURL url = new ActionURL(InsertIssueDefAction.class, getContainer()).
                addParameter(QueryParam.schemaName, IssuesSchema.getInstance().getSchemaName()).
                addParameter(QueryParam.queryName, IssuesQuerySchema.TableType.IssueListDef.name());
        setInsertURL(new DetailsURL(url));
        addAllColumns();
    }

    @Nullable
    public static String nameFromLabel(String label)
    {
        if (label != null)
        {
            return ColumnInfo.legalNameFromName(label).toLowerCase();
        }
        return null;
    }

    private void addAllColumns()
    {
        setDescription("Contains one row for each issue list");
        setName("Issue List Definitions");

        addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("RowId"))).setHidden(true);

        // don't show the name, it's derived from label
        ColumnInfo nameCol = addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("Name")));
        nameCol.setHidden(true);
        nameCol.setShownInInsertView(false);

        setDeleteURL(new DetailsURL(new ActionURL(DeleteIssueListAction.class, _userSchema.getContainer())));

        ColumnInfo labelCol = addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("Label")));
        DetailsURL url = new DetailsURL(new ActionURL(IssuesController.ListAction.class, getContainer()),
                Collections.singletonMap("issueDefName", "name"));
        labelCol.setURL(url);

        ColumnInfo containerCol = addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("Container")));
        ContainerForeignKey.initColumn(containerCol, getUserSchema());

        List<Pair<String, String>> inputValues = new ArrayList<>();
        for (IssuesListDefProvider provider : IssuesListDefService.get().getEnabledIssuesListDefProviders(getContainer()))
        {
            inputValues.add(new Pair<>(provider.getName(), provider.getLabel()));
        }

        ColumnInfo kindCol = addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("Kind")));
        kindCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new MultiValueInputColumn(colInfo, inputValues);
            }
        });

        ColumnInfo domainContainer = new AliasedColumn(this, "DomainContainer", _rootTable.getColumn("RowId"));
        domainContainer.setRequired(false);
        domainContainer.setKeyField(false);
        domainContainer.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new DataColumn(colInfo)
                {
                    private Container getContainer(RenderContext ctx)
                    {
                        String c = (String)ctx.get("Container");
                        Integer rowId = (Integer)ctx.get("RowId");

                        if (c != null && rowId != null)
                        {
                            Container container = ContainerManager.getForId(c);
                            if (container != null)
                            {
                                IssueListDef issueListDef = IssueManager.getIssueListDef(container, rowId);
                                if (issueListDef != null)
                                {
                                    return issueListDef.getDomainContainer(getUserSchema().getUser());
                                }
                            }
                        }
                        return null;
                    }

                    @Override
                    public void addQueryFieldKeys(Set<FieldKey> keys)
                    {
                        super.addQueryFieldKeys(keys);

                        keys.add(new FieldKey(getDisplayColumn().getFieldKey().getParent(), "Container"));
                        keys.add(new FieldKey(getDisplayColumn().getFieldKey().getParent(), "RowId"));
                    }

                    @Override
                    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                    {
                        Container c = getContainer(ctx);
                        if (c != null)
                        {
                            if (c.hasPermission(getUserSchema().getUser(), ReadPermission.class))
                            {
                                out.write("<a href=\"");
                                out.write(c.getStartURL(getUserSchema().getUser()).getLocalURIString());
                                out.write("\">");
                                out.write(PageFlowUtil.filter(c.getName()));
                                out.write("</a>");
                            }
                            else
                                out.write(PageFlowUtil.filter(c.getName()));
                        }
                        else
                            super.renderGridCellContents(ctx, out);
                    }
                };
            }
        });
        addColumn(domainContainer);

        addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("Created")));
        UserIdForeignKey.initColumn(addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("CreatedBy"))));
        addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("Modified")));
        UserIdForeignKey.initColumn(addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("ModifiedBy"))));
    }

    @Nullable
    @Override
    public QueryUpdateService getUpdateService()
    {
        return new UpdateService(this);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (getUpdateService() != null)
        {
            if (perm.equals(InsertPermission.class) || perm.equals(DeletePermission.class))
                return _userSchema.getContainer().hasPermission(user, AdminPermission.class);
            else if (perm.equals(ReadPermission.class))
                return _userSchema.getContainer().hasPermission(user, ReadPermission.class);
        }
        return false;
    }

    @Override
    public ActionURL getImportDataURL(Container container)
    {
        return LINK_DISABLER_ACTION_URL;
    }

    private class UpdateService extends DefaultQueryUpdateService
    {
        public UpdateService(IssuesListDefTable table)
        {
            super(table, table.getRealTable());
        }

        @Override
        protected Map<String, Object> getRow(User user, Container c, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            Integer rowId = (Integer)keys.get("rowId");
            String name = keys.get("label") != null ? nameFromLabel((String)keys.get("label")) : (String)keys.get("name");

            IssueListDef def = null;
            if (rowId != null)
                def = IssueManager.getIssueListDef(c, rowId);
            else if (name != null)
                def = IssueManager.getIssueListDef(c, name);
            else
                throw new InvalidKeyException("rowId or name required");

            if (def == null)
                return null;

            return ObjectFactory.Registry.getFactory(IssueListDef.class).toMap(def, new CaseInsensitiveHashMap<>());
        }

        @Override
        protected Map<String, Object> _insert(User user, Container c, Map<String, Object> row) throws SQLException, ValidationException
        {
            String label = (String)row.get("label");
            if (StringUtils.isBlank(label))
                throw new ValidationException("Label required", "label");

            String kind = (String)row.get("kind");
            if (StringUtils.isBlank(label))
                throw new ValidationException("Kind required", "kind");

            if (IssuesQuerySchema.getReservedTableNames().contains(label))
                throw new ValidationException("The table name : " + label + " is reserved.");

            try
            {
                IssueListDef def = new IssueListDef();
                def.setName(nameFromLabel(label));
                def.setLabel(label);
                def.setKind(kind);
                BeanUtils.populate(def, row);

                def = def.save(user);

                return ObjectFactory.Registry.getFactory(IssueListDef.class).toMap(def, new CaseInsensitiveHashMap<>());
            }
            catch (Exception e)
            {
                throw new ValidationException(e.getMessage());
            }
        }

        @Override
        protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
        {
            throw new UnsupportedOperationException("Update not supported.");
        }

        @Override
        protected void _delete(Container c, Map<String, Object> row) throws InvalidKeyException
        {
            Integer rowId = (Integer)row.get("rowId");
            if (rowId == null)
                throw new InvalidKeyException("Issue Definition rowId required");

            try
            {
                IssueManager.deleteIssueListDef(rowId, c, getUserSchema().getUser());
            }
            catch (Exception e)
            {
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    public class MultiValueInputColumn extends DataColumn
    {
        private final List<Pair<String, String>> _values;

        public MultiValueInputColumn(ColumnInfo col, @NotNull List<Pair<String, String>> values)
        {
            super(col);
            _values = values;
        }

        @Override
        public void renderInputHtml(RenderContext ctx, Writer out, Object val) throws IOException
        {
            String formFieldName = ctx.getForm().getFormFieldName(getColumnInfo());

            out.write("<select name='");
            out.write(formFieldName);
            out.write("'>\n");

            if (_values.size() > 0)
            {
                for (Pair<String, String> value : _values)
                {
                    out.write("<option value='");
                    out.write(PageFlowUtil.filter(value.first));
                    out.write("'>");
                    out.write(PageFlowUtil.filter(value.second));
                    out.write("</option>\n");
                }
            }
            else
            {
                out.write("<option value=''/>");
            }
            out.write("</select>\n");
        }

        @Override
        protected Object getInputValue(RenderContext ctx)
        {
            // HACK:
            return _values;
        }
    }
}
