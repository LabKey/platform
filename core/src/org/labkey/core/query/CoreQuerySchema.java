/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerTable;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.Group;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.SecurityLogger;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.core.workbook.WorkbooksTableInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: matthewb
 * Date: Jul 16, 2008
 * Time: 4:11:34 PM
 */
public class CoreQuerySchema extends UserSchema
{
    private Set<Integer> _projectUserIds;
    final boolean _mustCheckPermissions;

    public static final String NAME = "core";
    public static final String USERS_TABLE_NAME = "Users";
    public static final String GROUPS_TABLE_NAME = "Groups";
    public static final String USERS_AND_GROUPS_TABLE_NAME = "UsersAndGroups";
    public static final String SITE_USERS_TABLE_NAME = "SiteUsers";
    public static final String PRINCIPALS_TABLE_NAME = "Principals";
    public static final String MEMBERS_TABLE_NAME = "Members";
    public static final String MODULES_TABLE_NAME = "Modules";
    public static final String CONTAINERS_TABLE_NAME = "Containers";
    public static final String WORKBOOKS_TABLE_NAME = "Workbooks";
    public static final String FILES_TABLE_NAME = "Files";
    public static final String USERS_MSG_SETTINGS_TABLE_NAME = "UsersMsgPrefs";
    public static final String SCHEMA_DESCR = "Contains data about the system users and groups.";

    public CoreQuerySchema(User user, Container c)
    {
        this(user, c, true);
    }

    public CoreQuerySchema(User user, Container c, boolean mustCheckPermissions)
    {
        super(NAME, SCHEMA_DESCR, user, c, CoreSchema.getInstance().getSchema());
        _mustCheckPermissions = mustCheckPermissions;
    }

    public Set<String> getTableNames()
    {
        return PageFlowUtil.set(
                USERS_TABLE_NAME, SITE_USERS_TABLE_NAME, PRINCIPALS_TABLE_NAME,
                MODULES_TABLE_NAME,
                MEMBERS_TABLE_NAME, GROUPS_TABLE_NAME, USERS_AND_GROUPS_TABLE_NAME,
                CONTAINERS_TABLE_NAME, WORKBOOKS_TABLE_NAME);
    }


    public TableInfo createTable(String name)
    {
        if (USERS_TABLE_NAME.equalsIgnoreCase(name))
            return getUsers();
        if (SITE_USERS_TABLE_NAME.equalsIgnoreCase(name))
            return getSiteUsers();
        if (PRINCIPALS_TABLE_NAME.equalsIgnoreCase(name))
            return getPrincipals();
        if (MODULES_TABLE_NAME.equalsIgnoreCase(name))
            return getModules();
        if (MEMBERS_TABLE_NAME.equalsIgnoreCase(name))
            return getMembers();
        if (GROUPS_TABLE_NAME.equalsIgnoreCase(name))
            return getGroups();
        if (USERS_AND_GROUPS_TABLE_NAME.equalsIgnoreCase(name))
            return getUsersAndGroupsTable();
        if (WORKBOOKS_TABLE_NAME.equalsIgnoreCase(name))
            return getWorkbooks();
        if (CONTAINERS_TABLE_NAME.equalsIgnoreCase(name))
            return getContainers();
        if (USERS_MSG_SETTINGS_TABLE_NAME.equalsIgnoreCase(name))
            return new UsersMsgPrefTable(this, CoreSchema.getInstance().getSchema().getTable(USERS_TABLE_NAME)).init();
        // Files table is not visible
        if (FILES_TABLE_NAME.equalsIgnoreCase(name))
            return getFilesTable();
        return null;
    }

    public TableInfo getWorkbooks()
    {
        return new WorkbooksTableInfo(this);
    }

    public TableInfo getContainers()
    {
        return new ContainerTable(this);
    }

    public TableInfo getGroups()
    {
        TableInfo principalsBase = CoreSchema.getInstance().getTableInfoPrincipals();
        // We apply a special filter for containers below, so don't filter here too
        FilteredTable groups = new FilteredTable<CoreQuerySchema>(principalsBase, this)
        {
            @Override
            public boolean supportsContainerFilter()
            {
                return false;
            }

            @Override
            protected void applyContainerFilter(ContainerFilter filter)
            {
                //ignore
            }
        };
        groups.setName("Groups");

        //expose UserId, Name, Container, and Type
        ColumnInfo col = groups.wrapColumn(principalsBase.getColumn("UserId"));
        col.setKeyField(true);
        col.setReadOnly(true);
        col.setUserEditable(false);
        col.setShownInInsertView(false);
        col.setShownInUpdateView(false);
        groups.addColumn(col);

        col = groups.wrapColumn(principalsBase.getColumn("Name"));
        col.setReadOnly(true);
        groups.addColumn(col);

        col = groups.wrapColumn(principalsBase.getColumn("Type"));
        col.setReadOnly(true);
        groups.addColumn(col);

        col = groups.wrapColumn(principalsBase.getColumn("Container"));
        col.setReadOnly(true);
        groups.addColumn(col);

        List<FieldKey> defCols = new ArrayList<>();
        defCols.add(FieldKey.fromParts("Name"));
        defCols.add(FieldKey.fromParts("Type"));
        defCols.add(FieldKey.fromParts("Container"));
        groups.setDefaultVisibleColumns(defCols);

        //filter for just groups
        groups.addCondition(new SQLFragment("Type IN ('g','r')"));
        
        //filter out inactive
        groups.addCondition(new SQLFragment("Active = ?", true));

        // always include root plus current container's project
        groups.addCondition(getRootPlusProjectCondition());

        //if guest add null filter
        if (getUser().isGuest())
            addNullSetFilter(groups);

        groups.setDescription("Contains all groups defined in the current project." +
        " The data in this table are available only to users who are signed-in (not guests). Guests will see no rows.");
        
        return groups;
    }

    public TableInfo getSiteUsers()
    {
        FilteredTable<UserSchema> users = _getUserTable();

        // only site admins are allowed to see all site users, if the
        // user is a guest, return an empty set, else just filter to
        // the logged in user, so the user can at least see and update their
        // account info.

        User user = getUser();
        if (!user.isSiteAdmin())
        {
            if (!user.isGuest())
            {
                ColumnInfo userid = users.getRealTable().getColumn("userid");
                users.addInClause(userid, Collections.singletonList(user.getUserId()));
            }
            else
                addNullSetFilter(users);
        }
        users.setName(SITE_USERS_TABLE_NAME);
        users.setDescription("Contains all users who have accounts on the server regardless of whether they are members of the current project or not." +
        " The data in this table are available only to site administrators. All other users will see no rows.");

        addGroupsColumn(users);
        
        return users;
    }

    public TableInfo getPrincipals()
    {
        TableInfo principalsBase = CoreSchema.getInstance().getTableInfoPrincipals();
        FilteredTable principals = new FilteredTable<>(principalsBase, this, ContainerFilter.EVERYTHING);

        //we expose userid, name and type via query
        ColumnInfo col = principals.wrapColumn(principalsBase.getColumn("UserId"));
        col.setKeyField(true);
        col.setHidden(true);
        col.setReadOnly(true);
        col.setUserEditable(false);
        col.setShownInInsertView(false);
        col.setShownInUpdateView(false);
        principals.addColumn(col);

        col = principals.wrapColumn(principalsBase.getColumn("Name"));
        col.setReadOnly(true);
        principals.addColumn(col);

        col = principals.wrapColumn(principalsBase.getColumn("Type"));
        col.setReadOnly(true);
        principals.addColumn(col);

        col = principals.wrapColumn(principalsBase.getColumn("Container"));
        col.setReadOnly(true);
        principals.addColumn(col);

        List<FieldKey> defCols = new ArrayList<>();
        defCols.add(FieldKey.fromParts("Name"));
        defCols.add(FieldKey.fromParts("Type"));
        defCols.add(FieldKey.fromParts("Container"));
        principals.setDefaultVisibleColumns(defCols);

        principals.getColumn("Container").setFk(new ContainerForeignKey(this));

        //filter out inactive
        principals.addCondition(new SQLFragment("Active=?", true));

        //filter for container is null or container = current-container
        principals.addCondition(new SQLFragment("Container IS NULL or Container=?", getContainer().getProject()));

        //only non-guest users may see the principals
        if (!getContainer().hasPermission(getUser(), AdminPermission.class))
            addNullSetFilter(principals);

        principals.setDescription("Contains all principals (users and groups) who are members of the current project." +
        " The data in this table are available only to users with administrator permission in the current folder. All other users will see no rows.");

        return principals;
    }

    public TableInfo getMembers()
    {
        TableInfo membersBase = CoreSchema.getInstance().getTableInfoMembers();
        FilteredTable members = new FilteredTable<>(membersBase, this);

        ColumnInfo col = members.wrapColumn(membersBase.getColumn("UserId"));
        col.setKeyField(true);
        final boolean isSiteAdmin = getUser().isSiteAdmin();
        col.setFk(new LookupForeignKey("UserId", "DisplayName")
        {
            public TableInfo getLookupTableInfo()
            {
                return isSiteAdmin ? getSiteUsers() : getUsers();
            }
        });
        members.addColumn(col);

        col = members.wrapColumn(membersBase.getColumn("GroupId"));
        col.setKeyField(true);
        col.setFk(new LookupForeignKey("UserId", "Name")
        {
            public TableInfo getLookupTableInfo()
            {
                return getGroups();
            }
        });
        members.addColumn(col);

        //if user is guest, add a null-set filter
        if (!getContainer().hasPermission(getUser(), AdminPermission.class))
        {
            addNullSetFilter(members);
        }
        else
        {
            CoreSchema coreSchema = CoreSchema.getInstance();

            SQLFragment groupIdSql = new SQLFragment("GroupId IN (SELECT UserId FROM " + coreSchema.getTableInfoPrincipals() + " WHERE ");
            groupIdSql.append(getRootPlusProjectCondition());
            groupIdSql.append(")");

            members.addCondition(groupIdSql);
        }

        List<FieldKey> defCols = new ArrayList<>();
        defCols.add(FieldKey.fromParts("UserId"));
        defCols.add(FieldKey.fromParts("GroupId"));
        members.setDefaultVisibleColumns(defCols);

        members.setDescription("Contains rows indicating which users are in which groups in the current project." +
            " By default, the data in this table are available only to administrators. The \"See Email Addresses\" role can be explicitly granted to other users and groups.");

        return members;
    }


    private SQLFragment getRootPlusProjectCondition()
    {
        SQLFragment sql = new SQLFragment("Container IS NULL");
        Container project = getContainer().getProject();

        // Include this condition outside the root
        if (null != project)
        {
            sql.append(" OR Container = ?");
            sql.add(project);
        }

        return sql;
    }


    public TableInfo getUsers()
    {
        if (getContainer().isRoot())
            return getSiteUsers();

        FilteredTable<UserSchema> users = _getUserTable();

        //if the user is a guest, add a filter to produce a null set
        if (getUser().isGuest())
        {
            addNullSetFilter(users);
        }
        else
        {
            if (_projectUserIds == null)
            {
                _projectUserIds = new HashSet<>(SecurityManager.getFolderUserids(getContainer()));
                Group siteAdminGroup = SecurityManager.getGroup(Group.groupAdministrators);

                for (UserPrincipal adminUser : SecurityManager.getGroupMembers(siteAdminGroup, MemberType.ACTIVE_AND_INACTIVE_USERS))
                {
                    _projectUserIds.add(adminUser.getUserId());
                }
            }
            ColumnInfo userid = users.getRealTable().getColumn("userid");
            users.addInClause(userid, _projectUserIds);

            addGroupsColumn(users);
        }

        users.setDescription("Contains all users who are members of the current project." +
        " The data in this table are available only to users who are signed-in (not guests). Guests will see no rows." +
        " All signed-in users will see the columns UserId, EntityId, DisplayName, Email, FirstName, LastName, Description, Created, Modified," +
        " although non-administrator users must be added to the \"See Email Addresses\" role to see values in the Email column." +
        " Users with administrator permissions will also see the columns Phone, Mobile, Pager, IM, Active and LastLogin.");

        return users;
    }



    private void addGroupsColumn(FilteredTable users)
    {
        ColumnInfo groupsCol = users.wrapColumn("Groups", users.getRealTable().getColumn("userid"));
        groupsCol.setFk(new MultiValuedForeignKey(new LookupForeignKey("User")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return getMembersTable();
            }
        }, "Group"));
        users.addColumn(groupsCol);
        List<FieldKey> visibleColumns = new ArrayList<>(users.getDefaultVisibleColumns());
        visibleColumns.add(groupsCol.getFieldKey());
        users.setDefaultVisibleColumns(visibleColumns);
    }



    protected TableInfo getMembersTable()
    {
        TableInfo membersBase = CoreSchema.getInstance().getTableInfoMembers();
        FilteredTable result = new FilteredTable<>(membersBase, this);

        ColumnInfo userColumn = result.wrapColumn("User", membersBase.getColumn("UserId"));
        result.addColumn(userColumn);
        userColumn.setFk(new LookupForeignKey("UserId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return getUser().isSiteAdmin() ? getSiteUsers() : getUsers();
            }
        });

        ColumnInfo groupColumn = result.wrapColumn("Group", membersBase.getColumn("GroupId"));
        result.addColumn(groupColumn);
        groupColumn.setFk(new LookupForeignKey("UserId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return getGroups();
            }
        });

        return result;
    }

    private FilteredTable<UserSchema> _getUserTable()
    {
        UsersTable table = new UsersTable(this, CoreSchema.getInstance().getSchema().getTable(USERS_TABLE_NAME));
        table.init();
        table.setMustCheckPermissions(_mustCheckPermissions);
        return table;
    }

    protected ModulesTableInfo getModules()
    {
        return new ModulesTableInfo(this).init();
    }

    protected TableInfo getUsersAndGroupsTable()
    {
        QueryDefinition def = QueryService.get().createQueryDef(getUser(), getContainer(), this, "UsersAndGroups");
        def.setSql(
                "SELECT \n" +
                "  Users.UserId,\n" +
                "  Users.DisplayName,\n" +
                "  Users.Email,\n" +
                "  'u' AS Type,\n" +
                "  NULL AS Container\n" +
                "FROM Users\n" +
                "\n" +
                "UNION\n" +
                "\n" +
                "SELECT \n" +
                "  Groups.Userid,\n" +
                "  Groups.Name as DisplayName,\n" +
                "  NULL AS Email,\n" +
                "  Groups.Type,\n" +
                "  Groups.Container\n" +
                "FROM Groups");
        def.setMetadataXml(
                "<ns:tables xmlns:ns=\"http://labkey.org/data/xml\">\n" +
                        "  <ns:table tableName=\"UsersAndGroups\" tableDbType=\"NOT_IN_DB\">\n" +
                        "    <ns:description>Union of the Users and Groups tables</ns:description>\n" +
                        "    <ns:pkColumnName>UserId</ns:pkColumnName>\n" +
                        "    <ns:columns>\n" +
                        "      <ns:column columnName=\"UserId\">\n" +
                        "        <ns:isKeyField>true</ns:isKeyField>\n" +
                        "        <ns:dimension>true</ns:dimension>\n" +
                        "        <ns:fk>\n" +
                        "          <ns:fkDbSchema>core</ns:fkDbSchema>\n" +
                        "          <ns:fkTable>Users</ns:fkTable>\n" +
                        "          <ns:fkColumnName>UserId</ns:fkColumnName>\n" +
                        "        </ns:fk>\n" +
                        "      </ns:column>\n" +
                        "      <ns:column columnName=\"Container\">\n" +
                        "        <ns:dimension>true</ns:dimension>\n" +
                        "        <ns:fk>\n" +
                        "          <ns:fkDbSchema>core</ns:fkDbSchema>\n" +
                        "          <ns:fkTable>Containers</ns:fkTable>\n" +
                        "          <ns:fkColumnName>EntityId</ns:fkColumnName>\n" +
                        "        </ns:fk>\n" +
                        "      </ns:column>\n" +
                        "    </ns:columns>\n" +
                        "  </ns:table>\n" +
                        "</ns:tables>");
        List<QueryException> errors = new ArrayList<>();
        TableInfo t;
        t = def.getTable(this, errors, true);
        if (!errors.isEmpty())
            throw errors.get(0);
        t.getColumn("UserId").setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new DataColumn(colInfo, false)
                {
                    public String renderURL(RenderContext ctx)
                    {
                        Object type = ctx.get(FieldKey.fromParts("Type"));
                        if (!"u".equals(type))
                            return null;
                        return super.renderURL(ctx);
                    }
                };
            }

        });
        return t;
    }


    protected TableInfo getFilesTable()
    {
        return new FileListTableInfo(this);
    }

    protected void addNullSetFilter(FilteredTable table)
    {
        table.addCondition(new SQLFragment("1=2"), FieldKey.fromParts("UserId"));
    }

    public boolean getMustCheckPermissions()
    {
        return _mustCheckPermissions;
    }

    @Override
    protected boolean canReadSchema()
    {
        SecurityLogger.indent("CoreQuerySchema.canReadSchema()");
        try
        {
            if (!getMustCheckPermissions())
                SecurityLogger.log("getMustCheckPermissions()==false", getUser(), null, true);
            return !getMustCheckPermissions() || super.canReadSchema();
        }
        finally
        {
            SecurityLogger.outdent();
        }
    }

    public static boolean requiresProfileUpdate(User user)
    {
        // This gets called on every authentication, possibly including admins installing or upgrading the server. Skip
        // the check if the server is upgrading or starting up... the exp schema might not even exist.
        if (!ModuleLoader.getInstance().isStartupComplete())
            return false;

        Container c = ContainerManager.getRoot();
        String domainURI = UsersDomainKind.getDomainURI("core", CoreQuerySchema.USERS_TABLE_NAME, UsersDomainKind.getDomainContainer(), user);
        Domain domain = PropertyService.get().getDomain(UsersDomainKind.getDomainContainer(), domainURI);

        if (domain != null)
        {
            try
            {
                List<String> requiredFields = new ArrayList<>();
                for (DomainProperty prop : domain.getProperties())
                {
                    if (prop.isRequired() && prop.isShownInUpdateView())
                        requiredFields.add(prop.getName());
                }

                if (!requiredFields.isEmpty())
                {
                    UserSchema schema = new CoreQuerySchema(user, c, false);
                    QuerySettings settings = schema.getSettings(QueryView.DATAREGIONNAME_DEFAULT, CoreQuerySchema.USERS_TABLE_NAME);

                    settings.setBaseFilter(new SimpleFilter(FieldKey.fromParts("UserId"), user.getUserId()));

                    Map<String, Object> params = Collections.emptyMap();
                    TableInfo table = schema.getTable(CoreQuerySchema.USERS_TABLE_NAME);

                    try (Results results = QueryService.get().select(table, table.getColumns(),
                            new SimpleFilter(FieldKey.fromParts("UserId"), user.getUserId()), null, params, true))
                    {
                        if (results.next())
                        {
                            for (String fieldName : requiredFields)
                            {
                                FieldKey fieldKey = FieldKey.fromParts(fieldName);
                                if (results.hasColumn(fieldKey))
                                {
                                    Object val = results.getObject(fieldKey);
                                    if (val == null || val.toString().trim().length() == 0)
                                        return true;
                                }
                            }
                        }
                    }
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        return false;
    }
}
