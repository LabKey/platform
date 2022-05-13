/*
 * Copyright (c) 2012-2019 LabKey Corporation
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnRenderPropertiesImpl;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
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
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleQueryUpdateService;
import org.labkey.api.query.SimpleTableDomainKind;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AvatarThumbnailProvider;
import org.labkey.api.security.LimitActiveUsersService;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AddUserPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeleteUserPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.SeeUserDetailsPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.permissions.UserManagementPermission;
import org.labkey.api.security.roles.SeeUserAndGroupDetailsRole;
import org.labkey.api.thumbnail.ImageStreamThumbnailProvider;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.core.security.SecurityController;
import org.labkey.core.user.UserController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
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
    private Set<String> _illegalColumns;
    private boolean _mustCheckPermissions = true;
    private final boolean _canSeeDetails;
    private static final String EXPIRATION_DATE_KEY = "ExpirationDate";
    private static final String SYSTEM = "System";
    private static final Set<FieldKey> ALWAYS_AVAILABLE_FIELDS;

    static
    {
        ALWAYS_AVAILABLE_FIELDS = new HashSet<>();
        ALWAYS_AVAILABLE_FIELDS.addAll(Arrays.asList(
                FieldKey.fromParts("EntityId"),
                FieldKey.fromParts("UserId"),
                FieldKey.fromParts("DisplayName")));
    }

    private final static Logger LOG = LogManager.getLogger(UsersTable.class);

    public UsersTable(UserSchema schema, TableInfo table)
    {
        super(schema, table, null);

        setDescription("Contains all users who are members of the current project." +
            " The data in this table are available only to users who are signed-in (not guests). Guests see no rows." +
            " Signed-in users see the columns UserId, EntityId, and DisplayName." +
            " Users granted the '" + SeeUserAndGroupDetailsRole.NAME + "' role see all standard and custom columns.");

        setImportURL(LINK_DISABLER);
        setInsertURL(schema.getContainer().hasPermission(schema.getUser(), AddUserPermission.class)
                ? new DetailsURL(new ActionURL(SecurityController.AddUsersAction.class, schema.getContainer()))
                : LINK_DISABLER);
        setDeleteURL(schema.getContainer().hasPermission(schema.getUser(), DeleteUserPermission.class)
                ? new DetailsURL(new ActionURL(UserController.DeleteUsersAction.class, schema.getContainer()))
                : LINK_DISABLER);

        if (!schema.getContainer().hasPermission(schema.getUser(), UpdatePermission.class))
            setUpdateURL(LINK_DISABLER);

        _canSeeDetails = SecurityManager.canSeeUserDetails(getContainer(), getUser()) || getUser().isSearchUser();
    }

    public boolean isCanSeeDetails()
    {
        return _canSeeDetails;
    }

    @Override
    public void addColumns()
    {
        List<FieldKey> defaultColumns = new ArrayList<>();

        // wrap all the columns in the real table
        for (ColumnInfo col : getRealTable().getColumns())
        {
            if (acceptColumn(col))
            {
                if (ALWAYS_AVAILABLE_FIELDS.contains(col.getFieldKey()))
                    wrapColumn(col);
                else
                    addUserDetailColumn( (BaseColumnInfo)col, isCanSeeDetails(), true);
            }
        }

        // add the standard default columns
        defaultColumns.add(FieldKey.fromParts("UserId"));
        defaultColumns.add(FieldKey.fromParts("DisplayName"));
        defaultColumns.add(FieldKey.fromParts("Email"));
        defaultColumns.add(FieldKey.fromParts("Active"));
        defaultColumns.add(FieldKey.fromParts("HasPassword"));
        defaultColumns.add(FieldKey.fromParts("LastLogin"));
        defaultColumns.add(FieldKey.fromParts("Created"));

        if (null != PropertyService.get())
        {
            Collection<FieldKey> domainDefaultCols = addDomainColumns();
            defaultColumns.addAll(domainDefaultCols);
        }
        setDefaultVisibleColumns(defaultColumns);

        // expiration date will only show for admins under Site Users, if enabled
        hideExpirationDateColumn();
        // System column will be shown to all, but only editable by site administrators
        hideSystemColumn();

        // The details action requires admin permission so don't offer the link if they can't see it
        if (getUser().hasRootPermission(UserManagementPermission.class) || getContainer().hasPermission(getUser(), AdminPermission.class))
        {
            var userIdCol = getMutableColumn(FieldKey.fromParts("UserId"));
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

    /**
     * Add the specified column if the user has permission to see user details or add a column which displays only
     * blank values.
     *
     * @param wrapColumn true to wrap the column when it is already in the underlying physical table
     */
    private void addUserDetailColumn(BaseColumnInfo col, boolean canSeeDetails, boolean wrapColumn)
    {
        if (canSeeDetails || !_mustCheckPermissions)
        {
            if (wrapColumn)
                wrapColumn(col);
            else
                addColumn(col);
        }
        else
        {
            // display a column with blank results
            var nullColumn = addColumn(new NullColumnInfo(this, col.getName(), col.getJdbcType()));
            nullColumn.setHidden(col.isHidden());
            nullColumn.setNullable(col.isNullable());
            nullColumn.setRequired(col.isRequired());

            // add these for the app ProfilePage usage
            nullColumn.setUserEditable(col.isUserEditable());
            nullColumn.setInputType(col.getInputType());
            nullColumn.setRangeURI(col.getRangeURI());
        }
    }

    private void hideExpirationDateColumn()
    {
        var expirationDateCol = getMutableColumn(FieldKey.fromParts("ExpirationDate"));
        if (expirationDateCol != null)
        {
            expirationDateCol.setHidden(true);
            expirationDateCol.setShownInDetailsView(false);
            expirationDateCol.setUserEditable(false);
            expirationDateCol.setShownInInsertView(false);
            expirationDateCol.setShownInUpdateView(false);
        }
    }

    private void hideSystemColumn()
    {
        var expirationDateCol = getMutableColumn(FieldKey.fromParts(SYSTEM));
        if (expirationDateCol != null)
        {
            boolean siteAdmin = getUser().hasSiteAdminPermission();
            expirationDateCol.setUserEditable(siteAdmin);
            expirationDateCol.setShownInInsertView(siteAdmin);
            expirationDateCol.setShownInUpdateView(siteAdmin);
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
            _illegalColumns = Sets.newCaseInsensitiveHashSet();
            if (!getUser().hasRootPermission(UserManagementPermission.class) && !getContainer().hasPermission(getUser(), AdminPermission.class))
            {
                _illegalColumns.add("Active");
                _illegalColumns.add("HasPassword");
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
                    var propColumn = new PropertyColumn(pd, getObjectUriColumn(), getContainer(), user, false);

                    if (reserved.contains(propColumn.getName()))
                    {
                        // merge property descriptor settings into the built in columns
                        var col = getMutableColumn(propColumn.getName());
                        if (col != null)
                        {
                            if (!(col instanceof NullColumnInfo))
                            {
                                if (col.getScale() != pd.getScale())
                                    LOG.warn("Scale doesn't match for column " + col.getName() + ": " + col.getScale() + " vs " + pd.getScale());
                                pd.copyTo( (ColumnRenderPropertiesImpl)col );
                                if (!col.isHidden())
                                    defaultCols.add(FieldKey.fromParts(col.getName()));
                            }
                        }
                    }
                    else if (getColumn(propColumn.getName()) == null)
                    {
                        if (!pd.isHidden())
                            defaultCols.add(FieldKey.fromParts(propColumn.getName()));
                        addUserDetailColumn(propColumn, isCanSeeDetails(), false);
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

    @Override
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

            return new CacheClearingQueryUpdateService(new UsersTableQueryUpdateService(this, table, helper))
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
        if (!getMustCheckPermissions())
            return true;
        if (perm == ReadPermission.class && user instanceof User)
            return _userSchema.getContainer().hasOneOf((User)user, Set.of(ReadPermission.class, SeeUserDetailsPermission.class));
        else
            return super.hasPermission(user, perm);
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
    public void fireRowTrigger(Container c, User user, TriggerType type, boolean before, int rowNumber, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, Map<String, Object> extraContext) throws ValidationException
    {
        super.fireRowTrigger(c, user, type, before, rowNumber, newRow, oldRow, extraContext);
        Integer userId = null!=oldRow ? (Integer)oldRow.get("UserId") : null!=newRow ? (Integer)newRow.get("UserId") : null;
        if (null != userId && !before)
            UserManager.fireUserPropertiesChanged(userId);
    }

    @Override
    public @NotNull Map<String, Pair<IndexType, List<ColumnInfo>>> getUniqueIndices()
    {
        Map<String, Pair<IndexType, List<ColumnInfo>>> unique = new HashMap<>(super.getUniqueIndices());
        unique.put("uq_users_email", Pair.of(IndexType.Unique, getColumns("Email")));
        return unique;
    }

    private static class UsersTableQueryUpdateService extends SimpleQueryUpdateService
    {
        public UsersTableQueryUpdateService(SimpleUserSchema.SimpleTable queryTable, TableInfo dbTable, DomainUpdateHelper helper)
        {
            super(queryTable, dbTable, helper);
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws QueryUpdateServiceException, SQLException, InvalidKeyException
        {
            throw new UnsupportedOperationException("Delete not supported.");
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Integer pkVal = (Integer)oldRow.get("UserId");
            User userToUpdate = pkVal != null ? UserManager.getUser(pkVal) : null;
            if (userToUpdate == null)
                throw new NotFoundException("Unable to find user for " + pkVal + ".");
            if (userToUpdate.isGuest())
                throw new ValidationException("Action not valid for Guest user.");

            validatePermissions(user, userToUpdate);
            validateUpdatedUser(userToUpdate, row);
            validateExpirationDate(userToUpdate, user, container, row);
            validateSystem(user, userToUpdate, row);

            SpringAttachmentFile avatarFile = (SpringAttachmentFile)row.get(UserAvatarDisplayColumnFactory.FIELD_KEY);
            validateAvatarFile(avatarFile);

            Map<String, Object> ret = super.updateRow(user, container, row, oldRow);
            updateAvatarFile(userToUpdate, avatarFile, row);

            if (row.containsKey(EXPIRATION_DATE_KEY))
                auditExpirationDateChange(userToUpdate, user, ContainerManager.getRoot(), userToUpdate.getExpirationDate(), (Date)ret.get(EXPIRATION_DATE_KEY));

            return ret;
        }

        private void validateExpirationDate(User userToUpdate, User editingUser, Container container, Map<String, Object> row) throws ValidationException
        {
            if (row.containsKey(EXPIRATION_DATE_KEY))
            {
                if (!AuthenticationManager.canSetUserExpirationDate(editingUser, container))
                    throw new UnauthorizedException("User does not have permission to edit the Expiration Date field.");

                try
                {
                    Timestamp expirationDate = (Timestamp)row.get(EXPIRATION_DATE_KEY);
                    if (expirationDate != null)
                    {
                        if ((new Date()).compareTo(new Date(expirationDate.getTime())) > 0)
                            throw new ValidationException("Expiration Date cannot be in the past.");

                        boolean isOwnRecord = editingUser.equals(userToUpdate);
                        if (isOwnRecord)
                            throw new ValidationException("Cannot set your own Expiration Date.");
                    }
                }
                catch (ClassCastException e)
                {
                    throw new ValidationException("Invalid value for Expiration Date.");
                }
            }
        }

        private void validateSystem(User editingUser, User userToUpdate, Map<String, Object> row)
        {
            if (row.containsKey(SYSTEM))
            {
                if (!editingUser.hasSiteAdminPermission())
                    throw new UnauthorizedException("User does not have permission to edit the System field.");

                if (userToUpdate.isSystem() && !(boolean) row.get(SYSTEM) && LimitActiveUsersService.get().isUserLimitReached())
                    throw new UnauthorizedException("User limit has been reached so you can't clear this user's System field.");
            }
        }

        private void validatePermissions(User editingUser, User userToUpdate)
        {
            // only allow update to your own record or if you are a site level user manager
            boolean isOwnRecord = editingUser.equals(userToUpdate);
            if (!editingUser.hasRootPermission(UserManagementPermission.class) && !isOwnRecord)
                throw new UnauthorizedException();

            // don't let non-site admin edit details of site admin account
            if (userToUpdate.hasSiteAdminPermission() && !editingUser.hasSiteAdminPermission())
                throw new UnauthorizedException("Cannot edit details for a Site Admin user.");
        }

        private void validateUpdatedUser(User userToUpdate, Map<String, Object> row) throws ValidationException
        {
            String userEmailAddress = userToUpdate.getEmail();
            String displayName = (String)row.get("DisplayName");

            if (displayName != null)
            {
                if (displayName.contains("@") && !displayName.equalsIgnoreCase(userEmailAddress))
                    throw new ValidationException("User display name should not contain '@'. Please enter a different value.");

                //ensure that display name is unique
                //error if there's a user with this display name and it's not the user currently being edited
                User existingUser = UserManager.getUserByDisplayName(displayName);
                if (existingUser != null && !existingUser.equals(userToUpdate))
                    throw new ValidationException("The specified display name is already in use. Please enter a different value.");
            }
        }

        private void validateAvatarFile(SpringAttachmentFile file) throws ValidationException
        {
            // validate the original size of the avatar image
            if (file != null)
            {
                try (InputStream is = file.openInputStream())
                {
                    BufferedImage image = ImageIO.read(is);
                    file.closeInputStream();
                    float desiredSize = ThumbnailService.ImageType.Large.getHeight();

                    if (image == null)
                    {
                        throw new ValidationException("Avatar file must be an image file.");
                    }
                    else if (image.getHeight() < desiredSize || image.getWidth() < desiredSize)
                    {
                        throw new ValidationException("Avatar file must have a height and width of at least " + desiredSize + "px.");
                    }
                }
                catch (IOException e)
                {
                    throw new ValidationException("Unable to open avatar file.");
                }
            }
        }

        private void updateAvatarFile(User user, SpringAttachmentFile file, Map<String, Object> row) throws ValidationException
        {
            ThumbnailService.ImageType imageType = ThumbnailService.ImageType.Large;
            ThumbnailService svc = ThumbnailService.get();

            if (svc != null)
            {
                // add any new avatars, or replace existing, by using the ThumbnailService to generate and attach to the User's entityid
                if (file != null)
                {
                    try (InputStream is = file.openInputStream())
                    {
                        ImageStreamThumbnailProvider wrapper = new ImageStreamThumbnailProvider(new AvatarThumbnailProvider(user), is, file.getContentType(), imageType, true);
                        svc.replaceThumbnail(wrapper, imageType, null, null);
                        file.closeInputStream();
                    }
                    catch (IOException e)
                    {
                        throw new ValidationException("Unable to open avatar file.");
                    }
                }
                // call delete thumbnail in case there was an existing one which is being removed
                else if (row.containsKey(UserAvatarDisplayColumnFactory.FIELD_KEY))
                {
                    svc.deleteThumbnail(new AvatarThumbnailProvider(user), imageType);
                }
            }
            else
                throw new ValidationException("Unable to update avatar file.");
        }

        private void auditExpirationDateChange(User userToUpdate, User editingUser, Container c, Date oldExpirationDate, Date newExpirationDate)
        {
            String targetUserEmail = userToUpdate.getEmail();
            String currentUserEmail = editingUser.getEmail();
            String message;

            if (oldExpirationDate == null && newExpirationDate == null)
                return;
            else if (oldExpirationDate == null)
            {
                message = String.format("%1$s set expiration date for %2$s to %3$s.",
                        currentUserEmail, targetUserEmail, DateUtil.formatDateTime(c, newExpirationDate));
            }
            else if (newExpirationDate == null)
            {
                message = String.format("%1$s removed expiration date for %2$s. Previous value was %3$s",
                        currentUserEmail, targetUserEmail, DateUtil.formatDateTime(c, oldExpirationDate));
            }
            else if (oldExpirationDate.compareTo(newExpirationDate) != 0)
            {
                message = String.format("%1$s changed expiration date for %2$s from %3$s to %4$s.",
                        currentUserEmail, targetUserEmail, DateUtil.formatDateTime(c, oldExpirationDate), DateUtil.formatDateTime(c, newExpirationDate));
            }
            else
                return;

            UserManager.UserAuditEvent event = new UserManager.UserAuditEvent(c.getId(), message, userToUpdate);
            AuditLogService.get().addEvent(editingUser, event);
        }
    }
}
