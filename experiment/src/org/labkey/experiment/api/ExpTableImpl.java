/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.flag.FlagColumnRenderer;
import org.labkey.api.exp.flag.FlagForeignKey;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.ExpTable;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URIUtil;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract public class ExpTableImpl<C extends Enum> extends FilteredTable<UserSchema> implements ExpTable<C>
{
    private final ExpObjectImpl _objectType;
    private Set<Class<? extends Permission>> _allowablePermissions = new HashSet<>();
    private Domain _domain;

    // The populated flag indicates all standard columns have been added to the table, but metadata override have not yet been added
    protected boolean _populated;

    protected ExpTableImpl(String name, TableInfo rootTable, UserSchema schema, @Nullable ExpObjectImpl objectType, ContainerFilter cf)
    {
        super(rootTable, schema, cf);
        _objectType = objectType;
        setName(name);
        _allowablePermissions.add(DeletePermission.class);
        _allowablePermissions.add(ReadPermission.class);
    }

    public void addAllowablePermission(Class<? extends Permission> permission)
    {
        _allowablePermissions.add(permission);
    }

    protected final boolean isAllowedPermission(Class<? extends Permission> perm)
    {
        return _allowablePermissions.contains(perm);
    }

    @Override
    public final void populate()
    {
        populateColumns();
        _populated = true;
    }

    protected abstract void populateColumns();

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo result = super.resolveColumn(name);
        if (result == null && "Container".equalsIgnoreCase(name))
        {
            return getColumn("Folder");
        }
        for (ColumnInfo columnInfo : getColumns())
        {
            if (name.equalsIgnoreCase(columnInfo.getLabel()))
            {
                return columnInfo;
            }
        }

        if (_populated)
        {
            ColumnInfo lsidCol = getColumn("LSID", false);
            if (lsidCol != null)
            {
                if ("Properties".equalsIgnoreCase(name))
                {
                    return createPropertiesColumn(name);
                }

                // Attempt to resolve the column name as a property URI if it looks like a URI
                if (URIUtil.hasURICharacters(name))
                {
                    // mark vocab propURI col as Voc column
                    PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(name /* uri */, getContainer());
                    if (pd != null)
                    {
                        List<Domain> domainsForPD = OntologyManager.getDomainsForPropertyDescriptor(getContainer(), pd);
                        PropertyColumn pc = new PropertyColumn(pd, lsidCol, getContainer(), getUserSchema().getUser(), false);
                        pc.setVocabulary(domainsForPD.stream().anyMatch(d-> d.getDomainKind() instanceof VocabularyDomainKind));
                        // use the property URI as the column's FieldKey name
                        pc.setFieldKey(FieldKey.fromParts(name));
                        pc.setLabel(BaseColumnInfo.labelFromName(pd.getName()));
                        return pc;
                    }
                }
            }
        }


        return result;
    }

    @Override
    public FieldKey getContainerFieldKey()
    {
        return new FieldKey(null, ExpMaterialTable.Column.Folder.toString());
    }

    protected ColumnInfo addContainerColumn(C containerCol, ActionURL url)
    {
        var result = addColumn(containerCol);
        result.getImportAliasSet().add("container");
        ContainerForeignKey.initColumn(result, _userSchema, url);
        return result;
    }

    final public BaseColumnInfo addColumn(C column)
    {
        return addColumn(column.toString(), column);
    }

    final public BaseColumnInfo addColumn(String alias, C column)
    {
        var ret = createColumn(alias, column);
        addColumn(ret);
        return ret;
    }

    public ColumnInfo getColumn(C column)
    {
        for (ColumnInfo info : getColumns())
        {
            if (info instanceof ExprColumn && info.getAlias().equals(column.toString()))
            {
                return info;
            }
        }
        return null;
    }

    protected BaseColumnInfo doAdd(BaseColumnInfo column)
    {
        addColumn(column);
        return column;
    }

    public BaseColumnInfo createPropertyColumn(String name)
    {
        return wrapColumn(name, getLSIDColumn());
    }

    // Expensive render-time fetching of all ontology properties attached to the object row
    // TODO: Can we pre-fetch all properties referenced by the rows in the outer select and only include those properties?
    // TODO: How to handle lookup values?
    protected ColumnInfo createPropertiesColumn(String name)
    {
        var col = new AliasedColumn(this, name, getLSIDColumn());
        col.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo)
        {
            @Override
            public Object getValue(RenderContext ctx)
            {
                String lsid = (String)super.getValue(ctx);
                if (lsid == null)
                    return null;

                Map<String, Object> props = OntologyManager.getProperties(ctx.getContainer(), lsid);
                if (!props.isEmpty())
                    return props;

                return null;
            }

            @Override
            public Object getExcelCompatibleValue(RenderContext ctx)
            {
                return toJSONObjectString(ctx);
            }

            @Override
            public String getTsvFormattedValue(RenderContext ctx)
            {
                return toJSONObjectString(ctx);
            }

            // return json string
            private String toJSONObjectString(RenderContext ctx)
            {
                Object props = getValue(ctx);
                if (props == null)
                    return null;

                return new JSONObject(props).toString(2);
            }

            // return html formatted value
            @Override
            public @NotNull String getFormattedValue(RenderContext ctx)
            {
                Object props = getValue(ctx);
                if (props == null)
                    return "&nbsp;";

                String html = PageFlowUtil.filter(new JSONObject(props).toString(2));
                html = html.replaceAll("\\n", "<br>\n");
                return html;
            }

        });
        return col;
    }

    public BaseColumnInfo createUserColumn(String name, ColumnInfo userIdColumn)
    {
        var ret = wrapColumn(name, userIdColumn);
        UserIdQueryForeignKey.initColumn(getUserSchema(), ret, true);
        ret.setShownInInsertView(false);
        ret.setShownInUpdateView(false);
        ret.setUserEditable(false);
        return ret;
    }

    public String urlFlag(boolean flagged)
    {
        assert _objectType != null : "No ExpObject configured for ExpTable type: " + getClass();
        return _objectType.urlFlag(flagged);
    }

    protected ColumnInfo getLSIDColumn()
    {
        return _rootTable.getColumn("LSID");
    }

    // if you change this, see similar AssayResultTable.createFlagColumn or TSVAssayProvider.createFlagColumn()
    protected BaseColumnInfo createFlagColumn(String alias)
    {
        var ret = wrapColumn(alias, getLSIDColumn());
        ret.setFk(new FlagForeignKey(_userSchema, urlFlag(true), urlFlag(false)));
        ret.setDisplayColumnFactory(FlagColumnRenderer::new);
        ret.setDescription("Contains a reference to a user-editable comment about this row");
        ret.setNullable(true);
        ret.setInputType("text");
        ret.setMeasure(false);
        ret.setDimension(false);
        ret.setConceptURI(org.labkey.api.gwt.client.ui.PropertyType.expFlag.getURI());
        ret.setPropertyURI(org.labkey.api.gwt.client.ui.PropertyType.expFlag.getURI());
        ret.setImportAliasesSet(Sets.newCaseInsensitiveHashSet("comment"));
        return ret;
    }

    public void addRowIdCondition(SQLFragment condition)
    {
        SQLFragment sqlCondition = new SQLFragment("RowId ");
        sqlCondition.append(condition);
        addCondition(sqlCondition);
    }

    public void addLSIDCondition(SQLFragment condition)
    {
        SQLFragment sqlCondition = new SQLFragment("LSID ");
        sqlCondition.append(condition);
        addCondition(sqlCondition);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (getUpdateService() != null)
            return isAllowedPermission(perm) && _userSchema.getContainer().hasPermission(user, perm);
        return false;
    }

    /**
     * Add columns directly to the table itself, and optionally also as a single column that is a FK to the full set of properties
     * @param domain the domain from which to add all of the properties
     * @param legacyName if non-null, the name of a hidden node to be added as a FK for backwards compatibility
     */
    public BaseColumnInfo addColumns(Domain domain, @Nullable String legacyName)
    {
        BaseColumnInfo colProperty = null;
        if (legacyName != null && !domain.getProperties().isEmpty())
        {
            colProperty = wrapColumn(legacyName, getLSIDColumn());
            colProperty.setFk(new PropertyForeignKey(_userSchema, getContainerFilter(), domain));
            // Hide because the preferred way to get to these values is to add them directly to the table, instead of having
            // them under the legacyName node
            colProperty.setHidden(true);
            colProperty.setUserEditable(false);
            colProperty.setIsUnselectable(true);
            addColumn(colProperty);
        }

        List<FieldKey> visibleColumns = new ArrayList<>(getDefaultVisibleColumns());
        for (DomainProperty dp : domain.getProperties())
        {
            PropertyDescriptor pd = dp.getPropertyDescriptor();
            PropertyColumn propColumn = new PropertyColumn(pd, getColumn("LSID"), getContainer(), _userSchema.getUser(), false);
            if (getColumn(propColumn.getName()) == null)
            {
                addColumn(propColumn);
                if (!propColumn.isHidden())
                {
                    visibleColumns.add(FieldKey.fromParts(pd.getName()));
                }
            }
        }
        setDefaultVisibleColumns(visibleColumns);
        return colProperty;
    }

    @Override
    public Domain getDomain()
    {
        return _domain;
    }

    public void setDomain(Domain domain)
    {
        checkLocked();
        assert _domain == null;
        _domain = domain;
    }

    public ExpSchema getExpSchema()
    {
        if (_userSchema instanceof ExpSchema)
        {
            return (ExpSchema)_userSchema;
        }
        return new ExpSchema(_userSchema.getUser(), _userSchema.getContainer());
    }

    @Override
    public String getPublicSchemaName()
    {
        return _publicSchemaName == null ? _userSchema.getSchemaName() : _publicSchemaName;
    }

    @Override
    public void setFilterPatterns(String columnName, String... patterns)
    {
        checkLocked();
        if (patterns != null)
        {
            SQLFragment condition = new SQLFragment();
            condition.append("(");
            String separator = "";
            for (String pattern : patterns)
            {
                condition.append(separator);
                condition.append(_rootTable.getColumn(columnName).getAlias());
                // Only use LIKE if the pattern contains a wildcard, since the database can be more efficient
                // for = instead of LIKE. In some cases we're passed the LSID for a specific protocol,
                // and in other cases we're passed a pattern that matches against all protocols of a given type
                if (pattern.contains("%"))
                {
                    condition.append(" LIKE ?");
                }
                else
                {
                    condition.append(" = ?");
                }
                condition.add(pattern);
                separator = " OR ";
            }
            condition.append(")");
            addCondition(condition);
        }
    }

}
