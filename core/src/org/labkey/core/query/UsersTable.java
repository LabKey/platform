/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.core.query;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.CacheClearingQueryUpdateService;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SimpleQueryUpdateService;
import org.labkey.api.query.SimpleTableDomainKind;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.UserManagementPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.core.security.SecurityController;
import org.labkey.core.user.UserController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* User: klum
* Date: 9/19/12
*/
public class UsersTable extends SimpleUserSchema.SimpleTable<UserSchema>
{
    List<FieldKey> _defaultColumns;
    Set<String> _illegalColumns;
    boolean _mustCheckPermissions = true;

    private final static Logger LOG = Logger.getLogger(UsersTable.class);

    public UsersTable(UserSchema schema, TableInfo table)
    {
        super(schema, table);

        setDescription("Contains all users who are members of the current project." +
                " The data in this table are available only to users who are signed-in (not guests). Guests will see no rows." +
                " All signed-in users will see the columns UserId, EntityId, DisplayName, Email, FirstName, LastName, Description, Created, Modified." +
                " Users with administrator permissions will also see the columns Phone, Mobile, Pager, IM, Active and LastLogin.");
        setImportURL(LINK_DISABLER);
        if (schema.getUser().hasRootPermission(UserManagementPermission.class))
        {
            setDeleteURL(new DetailsURL(new ActionURL(UserController.DeleteUsersAction.class, schema.getContainer())));
            setInsertURL(new DetailsURL(new ActionURL(SecurityController.AddUsersAction.class, schema.getContainer())));
        }
        else
        {
            setDeleteURL(LINK_DISABLER);
            setInsertURL(LINK_DISABLER);
        }
    }

    @Override
    public void addColumns()
    {
        _defaultColumns = new ArrayList<>();

        wrapAllColumns();

        if (SecurityManager.canSeeEmailAddresses(getContainer(), getUser()) || getUser().isSearchUser())
        {
            addWrapColumn(getRealTable().getColumn("Email"));
        }
        else
        {
            ColumnInfo emailCol = addColumn(new NullColumnInfo(this, "Email", JdbcType.VARCHAR));
            emailCol.setReadOnly(true);
        }

        // add the standard default columns
        _defaultColumns.add(FieldKey.fromParts("UserId"));
        _defaultColumns.add(FieldKey.fromParts("DisplayName"));
        _defaultColumns.add(FieldKey.fromParts("Email"));
        _defaultColumns.add(FieldKey.fromParts("Active"));
        _defaultColumns.add(FieldKey.fromParts("LastLogin"));
        _defaultColumns.add(FieldKey.fromParts("Created"));

        if (null != PropertyService.get())
        {
            Collection<FieldKey> domainDefaultCols = addDomainColumns();
            _defaultColumns.addAll(domainDefaultCols);
        }
        setDefaultVisibleColumns(_defaultColumns);

        // expiration date will only show for admins under Site Users, if enabled
        hideExpirationDateColumn();

        // The details action requires admin permission so don't offer the link if they can't see it
        if (getUser().hasRootPermission(UserManagementPermission.class) || getContainer().hasPermission(getUser(), AdminPermission.class))
        {
            ColumnInfo userIdCol = getColumn(FieldKey.fromParts("UserId"));
            if (userIdCol != null)
            {
                DetailsURL detailsURL = QueryService.get().urlDefault(getContainer(), QueryAction.detailsQueryRow,
                        getSchema().getName(), CoreQuerySchema.USERS_TABLE_NAME, Collections.singletonMap("userId", userIdCol));

                ActionURL url = detailsURL.getActionURL();
                url.setAction(UserController.DetailsAction.class);
                setDetailsURL(new DetailsURL(url));
                userIdCol.setUserEditable(false);
                userIdCol.setShownInInsertView(false);
                userIdCol.setShownInUpdateView(false);
            }
        }
    }

    private void hideExpirationDateColumn()
    {
        ColumnInfo expirationDateCol = getColumn(FieldKey.fromParts("ExpirationDate"));
        if (expirationDateCol != null)
        {
            expirationDateCol.setHidden(true);
            expirationDateCol.setShownInDetailsView(false);
            expirationDateCol.setUserEditable(false);
            expirationDateCol.setShownInInsertView(false);
            expirationDateCol.setShownInUpdateView(false);
        }
    }

    private User getUser()
    {
        return _userSchema.getUser();
    }

    @Override
    protected boolean acceptColumn(ColumnInfo col)
    {
        if (_illegalColumns == null)
        {
            _illegalColumns = new HashSet<>();

            // handle the email column through a different code path
            _illegalColumns.add("Email");

            if (!getUser().hasRootPermission(UserManagementPermission.class) && !getContainer().hasPermission(getUser(), AdminPermission.class))
            {
                //_illegalColumns.add("UserId");

                _illegalColumns.add("Active");
                _illegalColumns.add("LastLogin");
            }
        }

        return !_illegalColumns.contains(col.getName());
    }

    @NotNull
    @Override
    protected Collection<FieldKey> addDomainColumns()
    {
        Collection<FieldKey> defaultCols = new ArrayList<>();
        Domain domain = getDomain();
        if (domain != null)
        {
            UsersDomainKind domainKind = (UsersDomainKind)PropertyService.get().getDomainKindByName(UsersDomainKind.NAME);
            if (null != domainKind)
            {
                Set<String> reserved = domainKind.getWrappedColumns();
                User user = getUserSchema().getUser();

                for (DomainProperty dp : domain.getProperties())
                {
                    PropertyDescriptor pd = dp.getPropertyDescriptor();
                    ColumnInfo propColumn = new PropertyColumn(pd, getObjectUriColumn(), getContainer(), user, false);

                    if (reserved.contains(propColumn.getName()))
                    {
                        // merge property descriptor settings into the built in columns
                        ColumnInfo col = getColumn(propColumn.getName());
                        if (col != null)
                        {
                            if (col.getScale() != pd.getScale())
                                LOG.error("Scale doesn't match for column " + col.getName() + ": " + col.getScale() + " vs " + pd.getScale());
                            pd.copyTo(col);
                            if (!col.isHidden())
                                defaultCols.add(FieldKey.fromParts(col.getName()));
                        }
                    }
                    else if (getColumn(propColumn.getName()) == null)
                    {
                        if (!pd.isHidden())
                            defaultCols.add(FieldKey.fromParts(propColumn.getName()));
                        addColumn(propColumn);
                    }
                }
            }
        }

        return defaultCols;
    }

    @Override
    public Domain getDomain()
    {
        if (getObjectUriColumn() == null)
            return null;

        if (_domain == null)
        {
            String domainURI = getDomainURI();
            _domain = PropertyService.get().getDomain(UsersDomainKind.getDomainContainer(), domainURI);
        }

        return _domain;
    }

    @Override
    public SimpleTableDomainKind getDomainKind()
    {
        if (getObjectUriColumn() == null)
            return null;

        return (UsersDomainKind)PropertyService.get().getDomainKindByName(UsersDomainKind.NAME);
    }

    @Override
    public String getDomainURI()
    {
        if (getObjectUriColumn() == null)
            return null;

        return UsersDomainKind.getDomainURI(getUserSchema().getName(), getName(), UsersDomainKind.getDomainContainer(), getUserSchema().getUser());
    }

    public QueryUpdateService getUpdateService()
    {
        // UNDONE: add an 'isUserEditable' bit to the schema and table?
        TableInfo table = getRealTable();
        if (table != null)
        {
            DefaultQueryUpdateService.DomainUpdateHelper helper = new SimpleQueryUpdateService.SimpleDomainUpdateHelper(this)
            {
                @Override
                public Container getDomainContainer(Container c)
                {
                    return UsersDomainKind.getDomainContainer();
                }

                @Override
                public Container getDomainObjContainer(Container c)
                {
                    return UsersDomainKind.getDomainContainer();
                }
            };
            QueryUpdateService updateService = new SimpleQueryUpdateService(this, table, helper);
            return new CacheClearingQueryUpdateService(updateService)
            {
                @Override
                protected void clearCache()
                {
                    UserManager.clearUserList();
                }
            };
        }
        return null;
    }

    public boolean getMustCheckPermissions()
    {
        return _mustCheckPermissions;
    }

    public void setMustCheckPermissions(boolean mustCheckPermissions)
    {
        _mustCheckPermissions = mustCheckPermissions;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return !getMustCheckPermissions() || super.hasPermission(user, perm);
    }

    public static SimpleFilter authorizeAndGetProjectMemberFilter(@NotNull Container c, @NotNull User u, String userIdColumnName) throws UnauthorizedException
    {
        SimpleFilter filter = new SimpleFilter();

        if (c.isRoot())
        {
            if (!u.hasRootPermission(UserManagementPermission.class))
                throw new UnauthorizedException();
        }
        else
        {
            SQLFragment sql = SecurityManager.getProjectUsersSQL(c.getProject());

            final FieldKey userIdColumnFieldKey = new FieldKey(null, userIdColumnName);
            filter.addClause(new SimpleFilter.SQLClause(sql.getSQL(), sql.getParamsArray(), userIdColumnFieldKey)
            {
                @Override
                public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
                {
                    ColumnInfo col = columnMap.get(userIdColumnFieldKey);

                    // NOTE: Ideally we would use col.getValueSql() here instead
                    SQLFragment sql = new SQLFragment();

                    if (col != null)
                        sql.append(col.getAlias());
                    else
                        sql.append(userIdColumnFieldKey);
                    sql.append(" IN (SELECT members.UserId ");
                    sql.append(super.toSQLFragment(columnMap, dialect));
                    sql.append(")");
                    return sql;
                }
            });
        }
        return filter;
    }


    @Override
    public void fireRowTrigger(Container c, TriggerType type, boolean before, int rowNumber, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, Map<String, Object> extraContext) throws ValidationException
    {
        super.fireRowTrigger(c, type, before, rowNumber, newRow, oldRow, extraContext);
        Integer userId = null!=newRow ? (Integer)newRow.get("UserId") : null!=oldRow ? (Integer)oldRow.get("UserId") : null;
        if (null != userId && !before)
            UserManager.fireUserPropertiesChanged(userId);
    }
}
