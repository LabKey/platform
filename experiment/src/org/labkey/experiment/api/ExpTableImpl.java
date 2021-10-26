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
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.flag.FlagColumnRenderer;
import org.labkey.api.exp.flag.FlagForeignKey;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.ExpTable;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.PropertiesDisplayColumn;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.URIUtil;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isBlank;

abstract public class ExpTableImpl<C extends Enum>
        extends FilteredTable<UserSchema>
        implements ExpTable<C>
{
    private final Set<Class<? extends Permission>> _allowablePermissions = new HashSet<>();
    private Domain _domain;
    private ExpSchema _expSchema = null;

    // The populated flag indicates all standard columns have been added to the table, but metadata override have not yet been added
    protected boolean _populated;

    protected ExpTableImpl(String name, TableInfo rootTable, UserSchema schema, ContainerFilter cf)
    {
        super(rootTable, schema, cf);
        setName(name);
        _allowablePermissions.add(DeletePermission.class);
        _allowablePermissions.add(ReadPermission.class);
    }

    @Override
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
        markPopulated();
    }

    @Override
    public final void markPopulated()
    {
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
                        PropertyColumn pc = new PropertyColumn(pd, lsidCol, getContainer(), getUserSchema().getUser(), false);
                        // use the property URI as the column's FieldKey name
                        String label = pc.getLabel();
                        pc.setFieldKey(FieldKey.fromParts(name));
                        pc.setLabel(label);
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
        Set<String> set = new LinkedHashSet(result.getImportAliasSet());
        set.add("container");
        result.setImportAliasesSet(set);
        if (null != url)
            result.setURL(new DetailsURL(url));
        return result;
    }

    @Override
    final public MutableColumnInfo addColumn(C column)
    {
        return addColumn(column.toString(), column);
    }

    @Override
    final public MutableColumnInfo addColumn(String alias, C column)
    {
        var ret = createColumn(alias, column);
        assert ret.getParentTable() == this;
        addColumn(ret);
        return ret;
    }

    @Override
    public ColumnInfo getColumn(C column)
    {
        for (ColumnInfo info : getColumns())
        {
            if (info.getName().equals(column.toString()))
            {
                return info;
            }
        }
        return null;
    }

    protected MutableColumnInfo doAdd(MutableColumnInfo column)
    {
        addColumn(column);
        return column;
    }

    @Override
    public MutableColumnInfo createPropertyColumn(String name)
    {
        return wrapColumn(name, getLSIDColumn());
    }

    // Expensive render-time fetching of all ontology properties attached to the object row
    protected MutableColumnInfo createPropertiesColumn(String name)
    {
        var col = new AliasedColumn(this, name, getLSIDColumn());
        col.setDescription("Includes all properties set for this row");
        col.setDisplayColumnFactory(colInfo -> new PropertiesDisplayColumn(getUserSchema(), colInfo));
        col.setConceptURI(PropertiesDisplayColumn.CONCEPT_URI);
        col.setHidden(true);
        col.setUserEditable(false);
        col.setReadOnly(true);
        col.setCalculated(true);
        return col;
    }

    public MutableColumnInfo createUserColumn(String name, ColumnInfo userIdColumn)
    {
        var ret = wrapColumn(name, userIdColumn);
        if (isBlank(ret.getConceptURI()))
            ret.setConceptURI(BuiltInColumnTypes.USERID_CONCEPT_URI);
        return ret;
    }

    protected ColumnInfo getLSIDColumn()
    {
        return _rootTable.getColumn("LSID");
    }

    // if you change this, see similar AssayResultTable.createFlagColumn or TSVAssayProvider.createFlagColumn()
    protected MutableColumnInfo createFlagColumn(String alias)
    {
        var ret = wrapColumn(alias, getLSIDColumn());
        ret.setFk(new FlagForeignKey(_userSchema));
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

    @Override
    public void addRowIdCondition(SQLFragment condition)
    {
        SQLFragment sqlCondition = new SQLFragment("RowId ");
        sqlCondition.append(condition);
        addCondition(sqlCondition);
    }

    @Override
    public void addLSIDCondition(SQLFragment condition)
    {
        SQLFragment sqlCondition = new SQLFragment("LSID ");
        sqlCondition.append(condition);
        addCondition(sqlCondition);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (perm == ReadPermission.class || getUpdateService() != null)
            return isAllowedPermission(perm) && _userSchema.getContainer().hasPermission(user, perm);
        return false;
    }

    /**
     * Add columns directly to the table itself, and optionally also as a single column that is a FK to the full set of properties
     * @param domain the domain from which to add all of the properties
     * @param legacyName if non-null, the name of a hidden node to be added as a FK for backwards compatibility
     */
    @Override
    public MutableColumnInfo addColumns(Domain domain, @Nullable String legacyName)
    {
        MutableColumnInfo colProperty = null;
        if (legacyName != null && !domain.getProperties().isEmpty())
        {
            // Hide because the preferred way to get to these values is to add them directly to the table, instead of having
            // them under the legacyName node
            colProperty = addDomainColumns(domain, legacyName);
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

    /**
     * Create a hidden column as a fake lookup to include all columns in the domain.
     * @param domain The domain to add columns from
     * @param lookupColName The column name
     * @return
     */
    protected MutableColumnInfo addDomainColumns(Domain domain, @NotNull String lookupColName)
    {
        var colProperty = wrapColumn(lookupColName, getLSIDColumn());
        colProperty.setFk(new PropertyForeignKey(_userSchema, getContainerFilter(), domain));
        colProperty.setHidden(true);
        colProperty.setUserEditable(false);
        colProperty.setIsUnselectable(true);
        // As this column wraps the LSID (which is required for insert), we need
        // to mark this as a calculated column so it won't be required during insert
        colProperty.setCalculated(true);
        addColumn(colProperty);

        return colProperty;
    }

    @Override
    public void addVocabularyDomains()
    {
        List<? extends Domain> domains = PropertyService.get().getDomains(getContainer(), getUserSchema().getUser(), new VocabularyDomainKind(), true);
        for (Domain domain : domains)
        {
            String columnName = domain.getName().replaceAll(" ", "") + domain.getTypeId();
            var col = this.addDomainColumns(domain, columnName);
            col.setLabel(domain.getName());
            col.setDescription("Properties from " + domain.getLabel(getContainer()));
        }
    }


    @Override
    public Domain getDomain()
    {
        return _domain;
    }

    @Override
    public void setDomain(Domain domain)
    {
        checkLocked();
        assert _domain == null;
        _domain = domain;
    }

    public ExpSchema getExpSchema()
    {
        if (_expSchema == null)
        {
            if (_userSchema instanceof ExpSchema)
                _expSchema = (ExpSchema)_userSchema;
            else
                _expSchema = (ExpSchema)_userSchema.getDefaultSchema().getSchema(ExpSchema.SCHEMA_NAME);
        }
        return _expSchema;
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
