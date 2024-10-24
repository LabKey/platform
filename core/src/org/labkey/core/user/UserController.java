/*
 * Copyright (c) 2003-2018 Fred Hutchinson Cancer Research Center
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

package org.labkey.core.user;

import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiVersion;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.BaseDownloadAction;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.GroupAuditProvider;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UrlColumn;
import org.labkey.api.data.validator.ColumnValidators;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.UserSchemaAction;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AvatarThumbnailProvider;
import org.labkey.api.security.Group;
import org.labkey.api.security.LimitActiveUsersService;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.RequiresAllOf;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityMessage;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.security.impersonation.GroupImpersonationContextFactory;
import org.labkey.api.security.impersonation.ImpersonationContext;
import org.labkey.api.security.impersonation.RoleImpersonationContextFactory;
import org.labkey.api.security.impersonation.UnauthorizedImpersonationException;
import org.labkey.api.security.impersonation.UserImpersonationContextFactory;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AddUserPermission;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.CanImpersonateSiteRolesPermission;
import org.labkey.api.security.permissions.DeleteUserPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdateUserPermission;
import org.labkey.api.security.permissions.UserManagementPermission;
import org.labkey.api.security.roles.OwnerRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.util.emailTemplate.UserOriginatedEmailTemplate;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.DisplayElement;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.UpdateView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.core.CoreModule;
import org.labkey.core.login.DbLoginConfiguration;
import org.labkey.core.login.DbLoginManager;
import org.labkey.core.login.LoginController;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.query.UserAuditProvider;
import org.labkey.core.query.UserAvatarDisplayColumnFactory;
import org.labkey.core.query.UsersDomainKind;
import org.labkey.core.query.UsersTable;
import org.labkey.core.security.SecurityController.AdminDeletePasswordAction;
import org.labkey.core.security.SecurityController.AdminResetPasswordAction;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.security.UserManager.UserDetailsButtonCategory.Account;
import static org.labkey.api.security.UserManager.UserDetailsButtonCategory.Authentication;
import static org.labkey.api.security.UserManager.UserDetailsButtonCategory.Permissions;

public class UserController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(UserController.class);

    static
    {
        EmailTemplateService.get().registerTemplate(RequestAddressEmailTemplate.class);
        EmailTemplateService.get().registerTemplate(ChangeAddressEmailTemplate.class);
    }

    public UserController()
    {
        setActionResolver(_actionResolver);
    }

    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig ret = super.defaultPageConfig();
        if (!AppProps.getInstance().isOptionalFeatureEnabled(LoginController.FEATUREFLAG_DISABLE_LOGIN_XFRAME))
            ret.setFrameOption(PageConfig.FrameOption.DENY);
        return ret;
    }

    public static class UserUrlsImpl implements UserUrls
    {
        @Override
        public ActionURL getSiteUsersURL()
        {
            return new ActionURL(ShowUsersAction.class, ContainerManager.getRoot());
            // TODO: Always add lastFilter?
        }

        @Override
        public ActionURL getProjectUsersURL(Container container)
        {
            return new ActionURL(ShowUsersAction.class, container.getProject());
        }

        @Override
        public ActionURL getUserAccessURL(Container container, int userId)
        {
            ActionURL url = getUserAccessURL(container);
            url.addParameter("userId", userId);
            return url;
        }

        public ActionURL getUserAccessURL(Container container)
        {
            return new ActionURL(UserAccessAction.class, container);
        }

        @Override
        public ActionURL getUserDetailsURL(Container c, int userId, @Nullable URLHelper returnURL)
        {
            ActionURL url = new ActionURL(DetailsAction.class, c);
            url.addParameter("userId", userId);

            if (null != returnURL)
                url.addReturnURL(returnURL);

            return url;
        }

        @Override
        public ActionURL getUserDetailsURL(Container c, @Nullable URLHelper returnURL)
        {
            ActionURL url = new ActionURL(DetailsAction.class, c);

            if (null != returnURL)
                url.addReturnURL(returnURL);

            return url;
        }

        @Override
        public ActionURL getUserUpdateURL(Container c, URLHelper returnURL, int userId)
        {
            ActionURL url = new ActionURL(ShowUpdateAction.class, c);
            url.addParameter("userId", userId);
            url.addParameter(QueryParam.schemaName.toString(), CoreQuerySchema.NAME);
            url.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, CoreQuerySchema.USERS_TABLE_NAME);
            url.addReturnURL(returnURL);

            return url;
        }

        @Override
        public ActionURL getUserAttachmentDownloadURL(User user, String name)
        {
            ActionURL url = new ActionURL(AttachmentDownloadAction.class, ContainerManager.getRoot());
            url.addParameter("userId", user.getUserId());
            url.addParameter("name", name);
            return url;
        }

        @Override
        public boolean requiresProfileUpdate(User user)
        {
            return CoreQuerySchema.requiresProfileUpdate(user);
        }
    }

    public static void registerAdminConsoleLinks()
    {
        if (null != PropertyService.get())
            AdminConsole.addLink(AdminConsole.SettingsLinkType.Configuration, "change user properties", new ActionURL(ShowUserPreferencesAction.class, ContainerManager.getRoot()), AdminPermission.class);
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Configuration, "Limit Active Users", new ActionURL(LimitActiveUsersAction.class, ContainerManager.getRoot()));
    }

    private void setDataRegionButtons(DataRegion rgn, boolean isOwnRecord, boolean canManageDetailsUser)
    {
        final User user = getUser();
        Container c = getContainer();
        ActionURL currentURL = getViewContext().getActionURL();
        boolean isUserManager = getUser().hasRootPermission(UserManagementPermission.class);
        boolean isAnyAdmin = isUserManager || c.hasPermission(user, AdminPermission.class);

        ButtonBar gridButtonBar = new ButtonBar();

        populateUserGridButtonBar(gridButtonBar, isAnyAdmin);
        rgn.setButtonBar(gridButtonBar, DataRegion.MODE_GRID);

        ButtonBar detailsButtonBar = new ButtonBar();

        ActionURL editURL = new ActionURL(ShowUpdateAction.class, getContainer());
        editURL.addParameter(QueryParam.schemaName.toString(), "core");
        editURL.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, CoreQuerySchema.SITE_USERS_TABLE_NAME);
        editURL.addParameter("userId", NumberUtils.toInt(currentURL.getParameter("userId")));
        editURL.addReturnURL(currentURL);

        if (isOwnRecord || (getUser().hasRootPermission(UpdateUserPermission.class) && canManageDetailsUser))
        {
            ActionButton edit = new ActionButton(editURL, "Edit");
            edit.setActionType(ActionButton.Action.LINK);
            edit.addContextualRole(OwnerRole.class);
            detailsButtonBar.add(edit);
        }
        rgn.setButtonBar(detailsButtonBar, DataRegion.MODE_DETAILS);

        ButtonBar updateButtonBar = new ButtonBar();
        updateButtonBar.setStyle(ButtonBar.Style.separateButtons);
        ActionButton update = new ActionButton(editURL, "Submit");
        if (isOwnRecord)
        {
            updateButtonBar.addContextualRole(OwnerRole.class);
            update.addContextualRole(OwnerRole.class);
        }
        updateButtonBar.add(update);
        if (isUserManager)
            updateButtonBar.add(getShowGridButton(c));
        rgn.setButtonBar(updateButtonBar, DataRegion.MODE_UPDATE);
    }

    private ActionButton getShowGridButton(Container c)
    {
        ActionURL showUsersURL = new ActionURL(ShowUsersAction.class, c);
        showUsersURL.addParameter(DataRegion.LAST_FILTER_PARAM, true);
        ActionButton showGrid = new ActionButton(showUsersURL, c.isRoot() ? "Show Users" : "Show Project Users");
        showGrid.setActionType(ActionButton.Action.LINK);

        return showGrid;
    }

    private void populateUserGridButtonBar(ButtonBar gridButtonBar, boolean isAnyAdmin)
    {
        User user = getUser();
        boolean canAddUser = user.hasRootPermission(AddUserPermission.class) || getContainer().hasPermission(user, AddUserPermission.class);
        boolean canDeleteUser = user.hasRootPermission(DeleteUserPermission.class);
        boolean canUpdateUser = user.hasRootPermission(UpdateUserPermission.class);
        boolean noMoreUsers = new LimitActiveUsersSettings().isUserLimitReached();

        if (canAddUser)
        {
            ActionButton insert = new ActionButton(urlProvider(SecurityUrls.class).getAddUsersURL(getContainer()), "Add Users");
            if (noMoreUsers)
            {
                insert.setEnabled(false);
                insert.setTooltip("User limit has been reached");
            }
            else
            {
                insert.setActionType(ActionButton.Action.LINK);
            }
            gridButtonBar.add(insert);
        }

        if (getContainer().isRoot())
        {
            if (canUpdateUser)
            {
                ActionButton deactivate = new ActionButton(DeactivateUsersAction.class, "Deactivate");
                deactivate.setRequiresSelection(true);
                deactivate.setActionType(ActionButton.Action.POST);
                gridButtonBar.add(deactivate);

                ActionButton activate = new ActionButton(ActivateUsersAction.class, "Reactivate");
                if (noMoreUsers)
                {
                    activate.setEnabled(false);
                    activate.setTooltip("User limit has been reached");
                }
                else
                {
                    activate.setRequiresSelection(true);
                    activate.setActionType(ActionButton.Action.POST);
                }
                gridButtonBar.add(activate);
            }

            if (canDeleteUser)
            {
                ActionButton delete = new ActionButton(DeleteUsersAction.class, "Delete");
                delete.setRequiresSelection(true);
                delete.setActionType(ActionButton.Action.POST);
                gridButtonBar.add(delete);
            }

            Domain domain = null;
            if (null != PropertyService.get())
            {
                String domainURI = UsersDomainKind.getDomainURI("core", CoreQuerySchema.USERS_TABLE_NAME, UsersDomainKind.getDomainContainer(), getUser());
                domain = PropertyService.get().getDomain(UsersDomainKind.getDomainContainer(), domainURI);
            }

            if (domain != null)
            {
                ActionURL url = domain.getDomainKind().urlEditDefinition(domain, getViewContext());
                ActionURL returnURL = getViewContext().getActionURL();
                if (returnURL != null)
                    url.addReturnURL(returnURL);

                if (canUpdateUser)
                {
                    ActionButton preferences = new ActionButton(url, "Change User Properties");
                    preferences.setActionType(ActionButton.Action.LINK);
                    gridButtonBar.add(preferences);
                }
            }
        }

        if (isAnyAdmin)
        {
            if (AuditLogService.get().isViewable())
            {
                gridButtonBar.add(new ActionButton(ShowUserHistoryAction.class, "History",
                        ActionButton.Action.LINK));
            }
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class BeginAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return HttpView.redirect(new UserUrlsImpl().getSiteUsersURL());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    public static class UserIdForm extends ReturnUrlForm
    {
        private Integer[] _userId;

        public Integer[] getUserId()
        {
            return _userId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setUserId(Integer[] userId)
        {
            _userId = userId;
        }
    }

    public abstract class BaseActivateUsersAction extends FormViewAction<UserIdForm>
    {
        private final boolean _active;

        protected BaseActivateUsersAction(boolean active)
        {
            _active = active;
        }

        @Override
        public void validateCommand(UserIdForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(UserIdForm form, boolean reshow, BindException errors)
        {
            if (errors.hasErrors())
                return new SimpleErrorView(errors, false);

            DeactivateUsersBean bean = new DeactivateUsersBean(_active, form.getReturnActionURL(new UserUrlsImpl().getSiteUsersURL()));
            if (null != form.getUserId())
            {
                for (Integer userId : form.getUserId())
                {
                    if (isValidUserToUpdate(userId, getUser()))
                        bean.addUser(UserManager.getUser(userId));
                }
            }
            else
            {
                //try to get a user selection list from the dataregion
                Set<Integer> userIds = DataRegionSelection.getSelectedIntegers(getViewContext(), true);
                if (userIds.isEmpty())
                    throw new RedirectException(new UserUrlsImpl().getSiteUsersURL());

                for (Integer id : userIds)
                {
                    if (isValidUserToUpdate(id, getUser()))
                        bean.addUser(UserManager.getUser(id));
                }
            }

            if (bean.getUsers().isEmpty())
                throw new RedirectException(bean.getReturnUrl());

            return new JspView<>("/org/labkey/core/user/deactivateUsers.jsp", bean, errors);
        }

        @Override
        public boolean handlePost(UserIdForm form, BindException errors) throws Exception
        {
            if (null == form.getUserId())
                return false;

            User curUser = getUser();
            for (Integer userId : form.getUserId())
            {
                if (isValidUserToUpdate(userId, curUser))
                    try
                    {
                        UserManager.setUserActive(curUser, userId, _active);
                    }
                    catch (SecurityManager.UserManagementException e)
                    {
                        User failedUser = UserManager.getUser(userId);
                        String displayName = null == failedUser ? "user " + userId : failedUser.getDisplayName(curUser);
                        errors.reject(ERROR_MSG, "Failed to " + (_active ? "activate" : "deactivate") + " " + displayName + ": " + e.getMessage());
                    }
            }
            return !errors.hasErrors();
        }

        @Override
        public ActionURL getSuccessURL(UserIdForm form)
        {
            return form.getReturnActionURL(new UserUrlsImpl().getSiteUsersURL());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Site Users", new UserUrlsImpl().getSiteUsersURL());
            String title = _active ? "Reactivate Users" : "Deactivate Users";
            root.addChild(title);
        }
    }

    @RequiresPermission(UpdateUserPermission.class)
    public class DeactivateUsersAction extends BaseActivateUsersAction
    {
        public DeactivateUsersAction()
        {
            super(false);
        }
    }

    @RequiresPermission(UpdateUserPermission.class)
    public class ActivateUsersAction extends BaseActivateUsersAction
    {
        public ActivateUsersAction()
        {
            super(true);
        }
    }

    private boolean isValidUserToUpdate(Integer formUserId, User currentUser)
    {
        User formUser = null != formUserId ? UserManager.getUser(formUserId) : null;

        return null != formUser
                && formUserId != currentUser.getUserId() // don't let a user activate/deactivate/delete themselves
                && (currentUser.hasSiteAdminPermission() || !formUser.hasPrivilegedRole()); // don't let non-site admin deactivate/delete a user with a privileged role (site admin, platform dev)
    }

    @RequiresAllOf({UpdateUserPermission.class, DeleteUserPermission.class})
    public class UpdateUsersStateApiAction extends MutatingApiAction<UpdateUserStateForm>
    {
        private final List<Integer> validUserIds = new ArrayList<>();
        private final List<Integer> invalidUserIds = new ArrayList<>();

        @Override
        public void validateForm(UpdateUserStateForm form, Errors errors)
        {
            if (form.getUserId() == null || form.getUserId().length == 0)
            {
                errors.reject(ERROR_MSG, "UserId parameter must be provided.");
            }
            else
            {
                for (Integer userId : form.getUserId())
                {
                    if (isValidUserToUpdate(userId, getUser()))
                        validUserIds.add(userId);
                    else
                        invalidUserIds.add(userId);
                }

                if (!invalidUserIds.isEmpty())
                    errors.reject(ERROR_MSG, "Invalid user id(s) provided: " + StringUtils.join(invalidUserIds, ", ") + ".");
            }
        }

        @Override
        public Object execute(UpdateUserStateForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            for (Integer userId : validUserIds)
            {
                if (form.isDelete())
                    UserManager.deleteUser(userId);
                else
                    UserManager.setUserActive(getUser(), userId, form.isActivate());
            }

            response.put("success", true);
            response.put("delete", form.isDelete());
            response.put("activate", form.isActivate());
            response.put("userIds", validUserIds);
            return response;
        }
    }

    public static class UpdateUserStateForm extends UserIdForm
    {
        private boolean activate;
        private boolean delete;

        public boolean isActivate()
        {
            return activate;
        }

        public void setActivate(boolean activate)
        {
            this.activate = activate;
        }

        public boolean isDelete()
        {
            return delete;
        }

        public void setDelete(boolean delete)
        {
            this.delete = delete;
        }
    }

    @RequiresPermission(DeleteUserPermission.class)
    public class DeleteUsersAction extends FormViewAction<UserIdForm>
    {
        @Override
        public void validateCommand(UserIdForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(UserIdForm form, boolean reshow, BindException errors)
        {
            ActionURL siteUsersUrl = new UserUrlsImpl().getSiteUsersURL();
            DeleteUsersBean bean = new DeleteUsersBean();

            if (null != form.getUserId())
            {
                for (Integer userId : form.getUserId())
                {
                    if (isValidUserToUpdate(userId, getUser()))
                        bean.addUser(UserManager.getUser(userId));
                }
            }
            else
            {
                //try to get a user selection list from the dataregion
                Set<Integer> userIds = DataRegionSelection.getSelectedIntegers(getViewContext(), true);
                if (userIds.isEmpty())
                    throw new RedirectException(siteUsersUrl);

                for (Integer id : userIds)
                {
                    if (isValidUserToUpdate(id, getUser()))
                        bean.addUser(UserManager.getUser(id));
                }
            }

            if (bean.getUsers().isEmpty())
                throw new RedirectException(siteUsersUrl);

            return new JspView<>("/org/labkey/core/user/deleteUsers.jsp", bean, errors);
        }

        @Override
        public boolean handlePost(UserIdForm form, BindException errors) throws Exception
        {
            if (null == form.getUserId())
                return false;

            User curUser = getUser();
            for (Integer userId : form.getUserId())
            {
                if (isValidUserToUpdate(userId, curUser))
                    UserManager.deleteUser(userId);
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(UserIdForm userIdForm)
        {
            return new UserUrlsImpl().getSiteUsersURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Site Users", new UserUrlsImpl().getSiteUsersURL());
            root.addChild("Delete Users");
        }
    }

    public static class ShowUsersForm extends QueryViewAction.QueryExportForm
    {
        private boolean _inactive;
        private boolean _temporary;

        public boolean isInactive()
        {
            return _inactive;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setInactive(boolean inactive)
        {
            _inactive = inactive;
        }

        public boolean isTemporary()
        {
            return _temporary;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setTemporary(boolean temporary)
        {
            _temporary = temporary;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ShowUsersAction extends QueryViewAction<ShowUsersForm, QueryView>
    {
        private static final String DATA_REGION_NAME = "Users";

        public ShowUsersAction()
        {
            super(ShowUsersForm.class);
        }

        @Override
        protected QueryView createQueryView(final ShowUsersForm form, BindException errors, boolean forExport, String dataRegion)
        {
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), SchemaKey.fromParts(CoreQuerySchema.NAME));
            QuerySettings settings = schema.getSettings(getViewContext(), DATA_REGION_NAME, getContainer().isRoot() ? CoreQuerySchema.SITE_USERS_TABLE_NAME : CoreQuerySchema.USERS_TABLE_NAME);
            settings.setAllowChooseView(true);
            settings.getBaseSort().insertSortColumn("email");

            if (!form.isInactive())
            {
                //filter out inactive users by default
                settings.getBaseFilter().addAllClauses(new SimpleFilter(FieldKey.fromString("Active"), true));
            }

            if (form.isTemporary())
            {
                settings.getBaseFilter().addCondition(FieldKey.fromString("ExpirationDate"), null, CompareType.NONBLANK);
            }

            final boolean isUserManager = getUser().hasRootPermission(UserManagementPermission.class);
            final boolean isProjectAdminOrBetter = isUserManager || isProjectAdmin();

            QueryView queryView = new QueryView(schema, settings, errors)
            {
                @Override
                protected void setupDataView(DataView ret)
                {
                    super.setupDataView(ret);

                    if (!forExport && isProjectAdminOrBetter)
                    {
                        ActionURL permissions = new UserUrlsImpl().getUserAccessURL(getContainer());
                        permissions.addParameter("userId", "${UserId}");
                        SimpleDisplayColumn securityDetails = new UrlColumn(StringExpressionFactory.createURL(permissions), "permissions");
                        ret.getDataRegion().addDisplayColumn(1, securityDetails);
                    }
                }

                @Override
                protected void populateButtonBar(DataView view, ButtonBar bar)
                {
                    super.populateButtonBar(view, bar);
                    populateUserGridButtonBar(bar, isProjectAdminOrBetter);
                }
            };
            queryView.setUseQueryViewActionExportURLs(true);
            queryView.setShadeAlternatingRows(true);
            queryView.setShowBorders(true);
            queryView.setShowDetailsColumn(true);
            queryView.setFrame(WebPartView.FrameType.NONE);
            queryView.disableContainerFilterSelection();
            queryView.setShowUpdateColumn(false);
            queryView.setShowInsertNewButton(false);
            queryView.setShowImportDataButton(false);
            queryView.setShowDeleteButton(false);
            return queryView;
        }

        @Override
        protected ModelAndView getHtmlView(ShowUsersForm form, BindException errors)
        {
            VBox users = new VBox();
            users.setTitle("Users");
            users.setFrame(WebPartView.FrameType.PORTAL);

            JspView<ShowUsersForm> toggleInactiveView = new JspView<>("/org/labkey/core/user/toggleInactive.jsp", form);

            users.addView(toggleInactiveView);
            users.addView(createQueryView(form, errors, false, "Users"));

            return users;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            if (getContainer().isRoot())
            {
                setHelpTopic("manageUsers");
                root.addChild("Site Users");
            }
            else
            {
                setHelpTopic("manageProjectMembers");
                root.addChild("Project Users");
            }
        }
    }

    // Site admins and Application admins can act on any user
    // Project admins can only act on users who are project users
    private void authorizeUserAction(Integer targetUserId, String action, boolean allowFolderAdmins) throws UnauthorizedException
    {
        User user = getUser();

        // Site admin and Application admin can do anything
        if (user.hasRootPermission(UserManagementPermission.class))
            return;

        Container c = getContainer();

        if (c.isRoot())
        {
            // Only site and app admin can view at the root (all users)
            throw new UnauthorizedException();
        }
        else
        {
            if (!allowFolderAdmins)
                requiresProjectAdminOrBetter();

            // ...and user must be a project user
            if (!SecurityManager.getProjectUsersIds(c.getProject()).contains(targetUserId))
                throw new UnauthorizedException("You can only " + action + " project users");
        }
    }


    private void requiresProjectAdminOrBetter() throws UnauthorizedException
    {
        User user = getUser();

        if (!(user.hasRootPermission(UserManagementPermission.class) || isProjectAdmin(user)))
            throw new UnauthorizedException();
    }


    private boolean isProjectAdmin()
    {
        return isProjectAdmin(getUser());
    }


    private boolean isProjectAdmin(User user)
    {
        Container project = getContainer().getProject();
        return (null != project && project.hasPermission(user, AdminPermission.class));
    }


    @RequiresPermission(AdminPermission.class)
    public class ShowUserHistoryAction extends SimpleViewAction
    {
        @Override
        public void checkPermissions() throws UnauthorizedException
        {
            super.checkPermissions();

            requiresProjectAdminOrBetter();
        }

        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            UserSchema schema = AuditLogService.getAuditLogSchema(getUser(), getContainer());
            if (schema != null)
            {
                QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);

                SimpleFilter projectMemberFilter = UsersTable.authorizeAndGetProjectMemberFilter(getContainer(), getUser(), UserAuditProvider.COLUMN_NAME_USER);

                settings.setBaseFilter(projectMemberFilter);
                settings.setQueryName(UserManager.USER_AUDIT_EVENT);
                return schema.createView(getViewContext(), settings, errors);
            }
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            if (getContainer().isRoot())
            {
                root.addChild("Site Users", new UserUrlsImpl().getSiteUsersURL());
                root.addChild("Site Users History");
            }
            else
            {
                root.addChild("Project Users", new UserUrlsImpl().getProjectUsersURL(getContainer()));
                root.addChild("Project Users History");
            }
        }
    }

    @AdminConsoleAction
    @RequiresPermission(AdminPermission.class)
    public static class ShowUserPreferencesAction extends SimpleRedirectAction<Object>
    {
        @Override
        public URLHelper getRedirectURL(Object form)
        {
            String domainURI = UsersDomainKind.getDomainURI(CoreQuerySchema.NAME, CoreQuerySchema.USERS_TABLE_NAME, UsersDomainKind.getDomainContainer(), getUser());
            Domain domain = PropertyService.get().getDomain(UsersDomainKind.getDomainContainer(), domainURI);

            ActionURL successUrl = null;

            if (domain != null)
            {
                successUrl = domain.getDomainKind().urlEditDefinition(domain, getViewContext());
                successUrl.addReturnURL(getViewContext().getActionURL());
            }

            return successUrl;
        }
    }

    @RequiresLogin
    public class AttachmentDownloadAction extends BaseDownloadAction<AttachmentForm>
    {
        @Override
        public @Nullable Pair<AttachmentParent, String> getAttachment(AttachmentForm form)
        {
            User user = form.getUserId() == null ? null : UserManager.getUser(form.getUserId());
            if (null == user)
            {
                throw new NotFoundException("Unable to find user");
            }

            return new Pair<>(new AvatarThumbnailProvider(user), form.getName());
        }
    }

    public static class AttachmentForm implements BaseDownloadAction.InlineDownloader
    {
        private Integer _userId;
        private String _name;

        public Integer getUserId()
        {
            return _userId;
        }

        public void setUserId(Integer userId)
        {
            _userId = userId;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }
    }

    @RequiresLogin
    public class ShowUpdateAction extends UserSchemaAction
    {
        Integer _userId;
        Integer _pkVal;

        @Override
        public ModelAndView getView(QueryUpdateForm form, boolean reshow, BindException errors)
        {
            User user = getUser();
            _userId = user.getUserId();
            if (null == form.getPkVal())
                form.setPkVal(_userId);

            _pkVal = NumberUtils.toInt(form.getPkVal().toString());
            boolean isOwnRecord = _pkVal.equals(_userId);
            HttpView view;

            User userToUpdate = getModifiableUser(_pkVal); // Will throw if specified user is guest or non-existent

            if (user.hasRootPermission(UserManagementPermission.class) || isOwnRecord)
            {
                ButtonBar bb = createSubmitCancelButtonBar(form);
                bb.addContextualRole(OwnerRole.class);
                for (DisplayElement button : bb.getList())
                    button.addContextualRole(OwnerRole.class);
                view = new UpdateView(form, errors);

                DataRegion rgn = ((UpdateView)view).getDataRegion();
                rgn.setButtonBar(bb);

                TableInfo table = ((UpdateView) view).getTable();
                if (table instanceof UsersTable)
                    ((UsersTable)table).setMustCheckPermissions(false);
            }
            else
            {
                throw new UnauthorizedException();
            }

            HtmlStringBuilder builder = HtmlStringBuilder.of();
            if (user.hasSiteAdminPermission() && LimitActiveUsersService.get().isUserLimitReached() && userToUpdate.isSystem())
                builder.unsafeAppend("<strong>Warning:</strong> User limit has been reached so the System field can't be cleared.<br><br>");
            if (isOwnRecord)
                builder.unsafeAppend("<div>Please enter your contact information.</div></br>");

            if (!builder.isEmpty())
                view = new VBox(new HtmlView(builder), view);

            return view;
        }

        @Override
        protected QueryForm createQueryForm(ViewContext context)
        {
            QueryForm form = new UserQueryForm();
            form.setViewContext(context);
            PropertyValues propertyValues = context.getBindPropertyValues();
            form.bindParameters(propertyValues);
            return form;
        }

        @Override
        public void validateCommand(QueryUpdateForm form, Errors errors)
        {
            TableInfo table = form.getTable();
            if (!(table instanceof UsersTable))
            {
                errors.reject(ERROR_MSG, "Unexpected table parameter " + form.getTable() + ".");
                return;
            }
            else
            {
                for (Map.Entry<String, Object> entry : form.getTypedColumns().entrySet())
                {
                    if (entry.getKey().equals("ExpirationDate") && !AuthenticationManager.canSetUserExpirationDate(getUser(), getContainer()))
                        errors.reject(ERROR_MSG, "User does not have permission to edit the ExpirationDate field.");

                    if (entry.getValue() != null)
                    {
                        ColumnInfo col = table.getColumn(FieldKey.fromParts(entry.getKey()));
                        try
                        {
                            ColumnValidators.validate(col, null, 1, entry.getValue());
                        }
                        catch (ValidationException e)
                        {
                            errors.reject(ERROR_MSG, e.getMessage());
                        }
                    }
                }
            }

            String userId = form.getPkVal().toString();
            if (userId == null)
            {
                errors.reject(SpringActionController.ERROR_MSG, "UserId parameter must be provided.");
                return;
            }
        }

        private boolean isOwnRecord(QueryUpdateForm form)
        {
            User user = getUser();
            _userId = user.getUserId();
            _pkVal = NumberUtils.toInt(form.getPkVal().toString());
            return _pkVal.equals(_userId);
        }

        @Override
        public boolean handlePost(QueryUpdateForm form, BindException errors) throws Exception
        {
            User postingUser = getUser();
            boolean isOwnRecord = isOwnRecord(form);

            if (postingUser.hasRootPermission(UserManagementPermission.class) || isOwnRecord)
            {
                TableInfo table = form.getTable();
                if (table instanceof UsersTable)
                    ((UsersTable)table).setMustCheckPermissions(false);

                doInsertUpdate(form, errors, false);
            }

            return 0 == errors.getErrorCount();
        }

        @Override
        public ActionURL getSuccessURL(QueryUpdateForm form)
        {
            return form.getReturnActionURL(urlProvider(UserUrls.class).getUserDetailsURL(getContainer(), NumberUtils.toInt(form.getPkVal().toString()), null));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addUserDetailsNavTrail(getContainer(), getUser(), root, _form.getReturnActionURL());
            root.addChild("Update");
            root.addChild(UserManager.getEmailForId(_pkVal));
        }
    }

    public static class UpdateUserDetailsForm extends UserQueryForm
    {
        // Specifying "merge" as true allows for passing sparse row data and assumes the original row
        // values that are not explicitly specified should be left unchanged.
        private boolean _merge;

        public boolean isMerge()
        {
            return _merge;
        }

        public void setMerge(boolean merge)
        {
            _merge = merge;
        }
    }

    @RequiresLogin // permission check will happen with form.mustCheckPermissions
    public class UpdateUserDetailsAction extends MutatingApiAction<UpdateUserDetailsForm>
    {
        @Override
        public void validateForm(UpdateUserDetailsForm form, Errors errors)
        {
            if (form.getUserId() <= 0)
            {
                errors.reject(ERROR_MSG, "UserId parameter must be provided.");
            }
            else if (UserManager.getUser(form.getUserId()) == null || form.mustCheckPermissions(getUser(), form.getUserId()))
            {
                errors.reject(ERROR_MSG, "You do not have permissions to update user details.");
            }
        }

        @Override
        public Object execute(UpdateUserDetailsForm form, BindException errors) throws Exception
        {
            Container root = ContainerManager.getRoot();
            UserSchema schema = form.getSchema();
            UsersTable table = (UsersTable)schema.getTable(CoreQuerySchema.USERS_TABLE_NAME, false);
            table.setMustCheckPermissions(form.mustCheckPermissions(getUser(), form.getUserId()));

            ApiSimpleResponse response = new ApiSimpleResponse();
            try (DbScope.Transaction transaction = table.getSchema().getScope().ensureTransaction())
            {
                QueryUpdateForm queryUpdateForm = new QueryUpdateForm(table, getViewContext(), true);
                queryUpdateForm.bindParameters(form.getInitParameters());
                Map<String, Object> row = queryUpdateForm.getTypedColumns();

                // TODO: Update this to use our normal strategy for updating (assume null values are not being touched)
                if (form.isMerge())
                {
                    Map<String, Object> props = getUserProps(form);

                    // Remove properties that are not to be updated
                    props.remove("userid");
                    props.remove("haspassword");
                    props.remove("lastlogin");
                    props.remove("expirationdate");
                    props.remove("active");
                    props.remove("_row");
                    props.remove("_ts");
                    props.remove("groups");
                    props.remove("owner");
                    props.remove("avatar");
                    props.remove("created");
                    props.remove("createdby");
                    props.remove("modified");
                    props.remove("modifiedby");
                    props.remove("entityid");

                    props.putAll(row);
                    row = props;
                }

                // handle file attachment for avatar file
                Map<String, MultipartFile> fileMap = getFileMap();
                if (fileMap != null && fileMap.containsKey(UserAvatarDisplayColumnFactory.FIELD_KEY))
                {
                    SpringAttachmentFile file = new SpringAttachmentFile(fileMap.get(UserAvatarDisplayColumnFactory.FIELD_KEY));
                    if (!file.isEmpty())
                        row.put(UserAvatarDisplayColumnFactory.FIELD_KEY, file);
                }
                // handle deletion of the current avatar file for a user
                else if ("null".equals(row.get(UserAvatarDisplayColumnFactory.FIELD_KEY)))
                    row.put(UserAvatarDisplayColumnFactory.FIELD_KEY, null);

                List<Map<String, Object>> rows = new ArrayList<>(Arrays.asList(row));
                List<Map<String, Object>> keys = new ArrayList<>(Arrays.asList(Map.of("UserId", form.getUserId())));

                BatchValidationException batchErrors = new BatchValidationException();
                response.put("updatedRows", table.getUpdateService().updateRows(getUser(), root, rows, keys, batchErrors, null, null));
                if (batchErrors.hasErrors())
                    throw batchErrors;

                response.put("success", true);

                transaction.commit();
            }
            return response;
        }
    }

    public static class UserQueryForm extends QueryForm
    {
        private int _userId;

        public int getUserId()
        {
            return _userId;
        }

        public void setUserId(int userId)
        {
            _userId = userId;
        }

        @Override
        public UserSchema createSchema()
        {
            int userId = getUserId();
            boolean checkPermission = mustCheckPermissions(getUser(), userId);

            return new CoreQuerySchema(getViewContext().getUser(), getViewContext().getContainer(), checkPermission);
        }

        public boolean mustCheckPermissions(User user, int userRecordId)
        {
            if (user.hasRootPermission(UserManagementPermission.class))
                return false;

            return user.getUserId() != userRecordId;
        }
    }

    public static class AccessDetail
    {
        private List<AccessDetailRow> _rows;
        private boolean _showGroups;
        private boolean _showUserCol;
        private boolean _active = true;

        public AccessDetail(List<AccessDetailRow> rows)
        {
            this(rows, true);
        }
        public AccessDetail(List<AccessDetailRow> rows, boolean showGroups)
        {
            this(rows, showGroups, false);
        }
        public AccessDetail(List<AccessDetailRow> rows, boolean showGroups, boolean showUserCol)
        {
            _rows = rows;
            _showGroups = showGroups;
            _showUserCol = showUserCol;
        }

        public List<AccessDetailRow> getRows()
        {
            return _rows;
        }
        public boolean showGroups()
        {
            return _showGroups;
        }
        
        public boolean showUserCol()
        {
            return _showUserCol;
        }

        public boolean isActive()
        {
            return _active;
        }

        public void setActive(boolean active)
        {
            _active = active;
        }
    }

    public static class AccessDetailRow implements Comparable<AccessDetailRow>
    {
        private final User _currentUser;
        private final Container _container;
        private final UserPrincipal _userPrincipal;
        private final Map<String, List<Group>> _accessGroups;
        private final int _depth;

        public AccessDetailRow(User currentUser, Container container, UserPrincipal userPrincipal, Map<String, List<Group>> accessGroups, int depth)
        {
            _currentUser = currentUser;
            _container = container;
            _userPrincipal = userPrincipal;
            _accessGroups = accessGroups;
            _depth = depth;
        }

        public String getAccess()
        {
            if (null == _accessGroups || _accessGroups.isEmpty())
                return "";

            String sep = "";
            StringBuilder access = new StringBuilder();
            for (String roleName : _accessGroups.keySet())
            {
                access.append(sep);
                access.append(roleName);
                sep = ", ";
            }
            return access.toString();
        }

        public Container getContainer()
        {
            return _container;
        }

        public UserPrincipal getUser()
        {
            return _userPrincipal;
        }

        public int getDepth()
        {
            return _depth;
        }

        public List<Group> getGroups()
        {
            if (null == _accessGroups || _accessGroups.isEmpty())
                return Collections.emptyList();

            List<Group> allGroups = new ArrayList<>();
            for (List<Group> groups : _accessGroups.values())
            {
                allGroups.addAll(groups);
            }
            return allGroups;
        }

        public Map<String, List<Group>> getAccessGroups()
        {
            return _accessGroups;
        }

        private User getCurrentUser()
        {
            return _currentUser;
        }

        public boolean isInheritedAcl()
        {
            return _container.isInheritedAcl();
        }

        @Override
        public int compareTo(AccessDetailRow o)
        {
            // if both UserPrincipals are Users, compare based on the DisplayName
            User thisUser = UserManager.getUser(this.getUser().getUserId());
            User thatUser = UserManager.getUser(o.getUser().getUserId());
            if (null != thisUser && null != thatUser)
                return thisUser.getDisplayName(getCurrentUser()).compareToIgnoreCase(thatUser.getDisplayName(getCurrentUser()));
            else
                return this.getUser().getName().compareToIgnoreCase(o.getUser().getName());
        }
    }

    private Map<String, Object> getUserProps(UserQueryForm form) throws NotFoundException
    {
        UserSchema schema = form.getSchema();
        if (schema == null)
            throw new NotFoundException("Schema not found");

        TableInfo table = schema.getTable(CoreQuerySchema.USERS_TABLE_NAME);
        if (table == null)
            throw new NotFoundException("Query not found");

        return new TableSelector(table).getMap(form.getUserId());
    }

    @RequiresLogin
    public class GetUserPropsAction extends ReadOnlyApiAction<UserQueryForm>
    {
        @Override
        public ApiResponse execute(UserQueryForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Map<String, Object> props = getUserProps(form);

            if (props != null)
            {
                response.put("success", true);
                response.put("props", props);
            }

            return response;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class UserAccessAction extends QueryViewAction<UserAccessForm, QueryView>
    {
        private boolean _showNavTrail = false;
        private Integer _userId;

        public UserAccessAction()
        {
            super(UserAccessForm.class);
        }

        @Override
        public void checkPermissions() throws UnauthorizedException
        {
            super.checkPermissions();

            // Folder admins can't view permissions, #13465
            requiresProjectAdminOrBetter();
        }

        @Override
        protected ModelAndView getHtmlView(UserAccessForm form, BindException errors) throws Exception
        {
            String email = form.getNewEmail();

            if (email != null)
            {
                try
                {
                    User user = UserManager.getUser(new ValidEmail(email));
                    if (user != null)
                    {
                        _userId = user.getUserId();
                    }
                    else
                    {
                        throw new NotFoundException("An existing user with that email address was not found");
                    }
                }
                catch (InvalidEmailException e)
                {
                    throw new NotFoundException("Invalid email address");
                }
            }

            if (_userId == null)
            {
                _userId = form.getUserId();
            }

            User requestedUser = UserManager.getUser(_userId);

            if (requestedUser == null)
                throw new NotFoundException("User not found");

            VBox view = new VBox();
            view.addView(new SecurityAccessView(getContainer(), getUser(), requestedUser, form.getShowAll()));
            view.addView(createInitializedQueryView(form, errors, false, QueryView.DATAREGIONNAME_DEFAULT));

            if (form.getRenderInHomeTemplate())
            {
                _showNavTrail = true;
            }
            else
            {
                getPageConfig().setTemplate(PageConfig.Template.Print);
            }

            return view;
        }

        @Override
        protected QueryView createQueryView(UserAccessForm form, BindException errors, boolean forExport, String dataRegion)
        {
            if (getContainer().isRoot())
            {
                return GroupAuditProvider.createSiteUserView(getViewContext(), form.getUserId(), errors);
            }
            else
            {
                return GroupAuditProvider.createProjectMemberView(getViewContext(), form.getUserId(), getContainer().getProject(), errors);
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            if (_showNavTrail)
            {
                addUserDetailsNavTrail(getContainer(), getUser(), root, _form.getReturnActionURL());
                root.addChild("Permissions");
                root.addChild("Role Assignments for User: " + UserManager.getEmailForId(_userId));
            }
        }
    }

    public static void addUserDetailsNavTrail(Container c, User currentUser, NavTree root, @Nullable ActionURL userDetailsUrl)
    {
        if (c.isRoot())
        {
            if (currentUser.hasRootPermission(UserManagementPermission.class))
                root.addChild("Site Users", new UserUrlsImpl().getSiteUsersURL());
        }
        else
        {
            if (c.hasPermission(currentUser, AdminPermission.class))
                root.addChild("Project Users", new UserUrlsImpl().getProjectUsersURL(c));
        }

        if (null == userDetailsUrl)
            root.addChild("User Details");
        else
            root.addChild("User Details", userDetailsUrl);
    }

    @RequiresLogin
    public class DetailsAction extends SimpleViewAction<UserQueryForm>
    {
        private int _detailsUserId;

        @Override
        public ModelAndView getView(UserQueryForm form, BindException errors)
        {
            User currentUser = getUser();
            int userId = currentUser.getUserId();
            _detailsUserId = form.getUserId();
            User detailsUser = getModifiableUser(_detailsUserId);

            boolean isOwnRecord = (_detailsUserId == userId);

            // Anyone can view their own record; otherwise, make sure current user can view the details of this user
            if (!isOwnRecord)
                authorizeUserAction(_detailsUserId, "view details of", true);

            Container c = getContainer();
            ActionURL currentUrl = getViewContext().getActionURL();
            boolean isUserManager = currentUser.hasRootPermission(UserManagementPermission.class);
            boolean isProjectAdminOrBetter = isUserManager || isProjectAdmin();

            // don't let a non-site admin manage certain parts of a site-admin's account
            boolean canManageDetailsUser = currentUser.hasSiteAdminPermission() || !detailsUser.hasSiteAdminPermission();
            String detailsEmail = detailsUser.getEmail();
            boolean loginExists = SecurityManager.loginExists(detailsEmail); // Note: Not using ValidEmail variant since existing address could be invalid

            UserSchema schema = form.getSchema();
            if (schema == null)
                throw new NotFoundException(CoreQuerySchema.NAME + " schema");

            // for the root container or if the user is site/app admin, use the site users table
            String userTableName = c.isRoot() || c.hasPermission(currentUser, UserManagementPermission.class) ? CoreQuerySchema.SITE_USERS_TABLE_NAME : CoreQuerySchema.USERS_TABLE_NAME;
            // use getTable(forWrite=true) because we hack on this TableInfo
            // TODO don't hack on the TableInfo, shouldn't the schema check canSeeUserDetails() and has AdminPermission?
            TableInfo table = schema.getTable(userTableName, null, true, true);
            if (table == null)
                throw new NotFoundException(userTableName + " table");
            else if (table instanceof AbstractTableInfo)
            {
                // conditionally remove the email and groups columns only for this view
                if (!SecurityManager.canSeeUserDetails(getContainer(), getUser()))
                {
                    ColumnInfo col = table.getColumn(FieldKey.fromParts("Email"));
                    if (col != null)
                        ((AbstractTableInfo)table).removeColumn(col);
                }

                if (!c.hasPermission(currentUser, AdminPermission.class))
                {
                    ColumnInfo col = table.getColumn(FieldKey.fromParts("Groups"));
                    if (col != null)
                        ((AbstractTableInfo)table).removeColumn(col);
                }
            }

            QueryUpdateForm quf = new QueryUpdateForm(table, getViewContext());
            DetailsView detailsView = new DetailsView(quf);
            detailsView.setFrame(WebPartView.FrameType.PORTAL);
            DataRegion rgn = detailsView.getDataRegion();

            setDataRegionButtons(rgn, isOwnRecord, canManageDetailsUser);
            ButtonBar bb = rgn.getButtonBar(DataRegion.MODE_DETAILS);
            bb.setStyle(ButtonBar.Style.separateButtons);

            // see if any of the SSO auth configurations are set to autoRedirect from the login action
            boolean isLoginAutoRedirect = AuthenticationManager.getAutoRedirectSSOAuthConfiguration() != null;

            if (isOwnRecord && loginExists && !isLoginAutoRedirect)
            {
                ActionButton changePasswordButton = new ActionButton(urlProvider(LoginUrls.class).getChangePasswordURL(c, currentUser, currentUrl, null), "Change Password");
                changePasswordButton.setActionType(ActionButton.Action.LINK);
                changePasswordButton.addContextualRole(OwnerRole.class);
                bb.add(changePasswordButton);
            }

            if (isUserManager)
            {
                // Always display "Reset/Create Password" button (even for LDAP and SSO users)... except for admin's own record
                if (null != detailsEmail && !isOwnRecord && canManageDetailsUser && !isLoginAutoRedirect)
                {
                    // Allow admins to create a logins entry if it doesn't exist. Addresses scenario of user logging in
                    // with LDAP/SSO and later needing to use database authentication. Also allows site admin to have
                    // an alternate login, e.g., in case LDAP server goes down or configuration changes.
                    ActionURL resetURL = new ActionURL(AdminResetPasswordAction.class, c);
                    resetURL.addParameter("email", detailsEmail);
                    resetURL.addReturnURL(currentUrl);
                    ActionButton reset = new ActionButton(resetURL, loginExists ? "Reset Password" : "Create Password");
                    reset.setActionType(ActionButton.Action.LINK);
                    bb.add(reset);

                    if (loginExists)
                    {
                        ActionURL deleteURL = new ActionURL(AdminDeletePasswordAction.class, c);
                        deleteURL.addParameter("email", detailsEmail);
                        deleteURL.addReturnURL(currentUrl);
                        ActionButton delete = new ActionButton(deleteURL, "Delete Password");
                        delete.setActionType(ActionButton.Action.LINK);
                        bb.add(delete);
                    }
                }
            }

            UserManager.addCustomButtons(Authentication, bb, c, currentUser, detailsUser, currentUrl);

            if (isUserManager)
            {
                if (canManageDetailsUser && !isLoginAutoRedirect) // Issue 33393
                    bb.add(makeChangeEmailButton(c, detailsUser));

                if (!isOwnRecord && canManageDetailsUser)
                {
                    // Can't reactivate any users if user limit has been reached
                    if (new LimitActiveUsersSettings().isUserLimitReached() && !detailsUser.isActive())
                    {
                        ActionButton disabledDeactivate = new ActionButton("Deactivate");
                        disabledDeactivate.setEnabled(false);
                        disabledDeactivate.setTooltip("User limit has been reached");
                        bb.add(disabledDeactivate);
                    }
                    else
                    {
                        ActionURL deactivateUrl = new ActionURL(detailsUser.isActive() ? DeactivateUsersAction.class : ActivateUsersAction.class, c);
                        deactivateUrl.addParameter("userId", _detailsUserId);
                        deactivateUrl.addReturnURL(currentUrl);
                        bb.add(new ActionButton(detailsUser.isActive() ? "Deactivate" : "Reactivate", deactivateUrl));
                    }

                    ActionURL deleteUrl = new ActionURL(DeleteUsersAction.class, c);
                    deleteUrl.addParameter("userId", _detailsUserId);
                    bb.add(new ActionButton("Delete", deleteUrl));
                }
            }
            else
            {
                if (isOwnRecord
                    && loginExists  // only show link to users where LabKey manages the password
                    && AuthenticationManager.isSelfServiceEmailChangesEnabled()
                    && !isLoginAutoRedirect) // Issue 33393
                {
                    bb.add(makeChangeEmailButton(c, detailsUser));
                }
            }

            UserManager.addCustomButtons(Account, bb, c, currentUser, detailsUser, currentUrl);

            if (isProjectAdminOrBetter)
            {
                ActionURL viewPermissionsURL = new UserUrlsImpl().getUserAccessURL(c, _detailsUserId);
                viewPermissionsURL.addReturnURL(currentUrl);
                ActionButton viewPermissions = new ActionButton(viewPermissionsURL, "View Permissions");
                viewPermissions.setActionType(ActionButton.Action.LINK);
                bb.add(viewPermissions);
            }

            if (isUserManager && canManageDetailsUser)
            {
                ActionURL cloneUrl = urlProvider(SecurityUrls.class).getClonePermissionsURL(detailsUser, currentUrl);
                ActionButton cloneButton = new ActionButton(cloneUrl, "Clone Permissions");
                cloneButton.setActionType(ActionButton.Action.LINK);
                cloneButton.setTooltip("Replace this user's permissions with those of another user");

                bb.add(cloneButton);
            }

            UserManager.addCustomButtons(Permissions, bb, c, currentUser, detailsUser, currentUrl);

            if (isProjectAdminOrBetter)
            {
                ActionButton showGrid = getShowGridButton(c);
                if (!isOwnRecord)
                    showGrid.setPrimary(true);
                bb.add(showGrid);
            }

            if (isOwnRecord)
            {
                final ActionButton doneButton;

                if (null != form.getReturnUrl())
                {
                    doneButton = new ActionButton("Done", form.getReturnURLHelper());
                    rgn.addHiddenFormField(ActionURL.Param.returnUrl, form.getReturnUrl());
                }
                else
                {
                    Container doneContainer = c.getProject();

                    // Root or no permission means redirect to home, #12947
                    if (null == doneContainer || !doneContainer.hasPermission(currentUser, ReadPermission.class))
                        doneContainer = ContainerManager.getHomeContainer();

                    ActionURL doneURL = doneContainer.getStartURL(currentUser);
                    doneButton = new ActionButton(doneURL, "Go to " + doneContainer.getName());
                    doneButton.setActionType(ActionButton.Action.LINK);
                }

                doneButton.addContextualRole(OwnerRole.class);
                doneButton.setPrimary(true);
                bb.add(doneButton);
                bb.addContextualRole(OwnerRole.class);
            }

            VBox view = new VBox(detailsView);

            if (isProjectAdminOrBetter)
            {
                UserSchema auditLogSchema = AuditLogService.getAuditLogSchema(getUser(), getContainer());
                if (auditLogSchema != null)
                {
                    QuerySettings settings = new QuerySettings(getViewContext(), "auditHistory");
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(UserAuditProvider.COLUMN_NAME_USER), _detailsUserId);
                    if (getContainer().isRoot())
                        settings.setContainerFilterName(ContainerFilter.Type.AllFolders.name());

                    settings.setBaseFilter(filter);
                    settings.setQueryName(UserManager.USER_AUDIT_EVENT);

                    QueryView auditView = auditLogSchema.createView(getViewContext(), settings, errors);
                    auditView.setTitle("History:");

                    view.addView(auditView);
                }
            }

            return view;
        }

        private ActionButton makeChangeEmailButton(Container c, User user)
        {
            ActionURL changeEmailURL = getChangeEmailURL(c, user);
            changeEmailURL.addParameter("isChangeEmailRequest", true);
            changeEmailURL.addReturnURL(getViewContext().getActionURL());
            ActionButton changeEmail = new ActionButton(changeEmailURL, "Change Email");
            changeEmail.setActionType(ActionButton.Action.LINK);
            return changeEmail;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild(UserManager.getEmailForId(_detailsUserId));
        }
    }

    private static ActionURL getChangeEmailURL(Container c, User user)
    {
        ActionURL url = new ActionURL(ChangeEmailAction.class, c);
        url.addParameter("userId", user.getUserId());

        return url;
    }

    @RequiresLogin
    public class ChangeEmailAction extends FormViewAction<UserForm>
    {
        private boolean _isPasswordPrompt = false;
        private ValidEmail _validRequestedEmail;
        private UserForm _form;

        @Override
        public void validateCommand(UserForm target, Errors errors)
        {
            if (target.getIsChangeEmailRequest())
            {
                String requestedEmail = target.getRequestedEmail();
                String requestedEmailConfirmation = target.getRequestedEmailConfirmation();

                if (requestedEmail == null || requestedEmailConfirmation == null)
                {
                    errors.reject(ERROR_MSG, "Both new email address inputs must be provided.");
                }
                else if (!requestedEmail.equals(requestedEmailConfirmation))
                {
                    errors.reject(ERROR_MSG, "The email addresses you have entered do not match. Please verify your email addresses below.");
                }
                else  // email addresses match
                {
                    try
                    {
                        ValidEmail validRequestedEmail = new ValidEmail(requestedEmail);

                        if (UserManager.userExists(validRequestedEmail))
                        {
                            errors.reject(ERROR_MSG, requestedEmail + " already exists in the system. Please choose another email.");
                        }
                    }
                    catch (InvalidEmailException e)
                    {
                        errors.reject(ERROR_MSG, "Invalid email address.");
                    }
                }
            }
        }

        @Override
        public ModelAndView getView(UserForm form, boolean reshow, BindException errors) throws Exception
        {
            _form = form;
            boolean canUpdateUser = getUser().hasRootPermission(UpdateUserPermission.class);
            if (!canUpdateUser)
            {
                if (!AuthenticationManager.isSelfServiceEmailChangesEnabled())  // uh oh, shouldn't be here
                {
                    throw new UnauthorizedException("User attempted to access self-service email change, but this function is disabled.");
                }

                if (form.getUserId() != getUser().getUserId())
                {
                    throw new UnauthorizedException("You aren't allowed to change another user's email!");
                }

                form._userToChange = getUser();  // Push validated user into form for JSP
            }
            else  // site or app admin, could be another user's ID
            {
                form._userToChange = getModifiableUser(form.getUserId());  // Push validated user into form for JSP
            }

            // Consider: create a separate "change password from verification link in an email" action (and JSP?)
            if (form.getIsFromVerifiedLink())
            {
                User loggedInUser = getUser();
                User userToChange = UserManager.getUser(form.getUserId());

                if (null == userToChange)
                {
                    errors.reject(ERROR_MSG, "Specified user was not found.");
                }
                else if (!userToChange.equals(loggedInUser))
                {
                    errors.reject(ERROR_MSG, "The current user is not the same user that initiated this request. " +
                            "Please log in with the account you used to make this email change request.");
                }
                else
                {
                    String oldEmail = userToChange.getEmail();
                    UserManager.VerifyEmail verifyEmail = UserManager.getVerifyEmail(oldEmail);
                    assert oldEmail.equals(verifyEmail.getEmail());
                    String requestedEmail = verifyEmail.getRequestedEmail();
                    String verificationToken = form.getVerificationToken();
                    boolean isVerified = verifyEmail.isVerified(verificationToken);
                    LoginController.checkVerificationErrors(isVerified, loggedInUser, oldEmail, verificationToken, errors);

                    if (errors.getErrorCount() == 0)  // verified and active
                    {
                        // Note: Verification timeout only applies to self-service change email workflow currently
                        Instant verificationTimeoutInstant = verifyEmail.getVerificationTimeout().toInstant();
                        if (Instant.now().isAfter(verificationTimeoutInstant))
                        {
                            if (!canUpdateUser)  // don't bother auditing admin password link clicks
                            {
                                UserManager.auditEmailTimeout(loggedInUser.getUserId(), oldEmail, requestedEmail, verificationToken, getUser());
                            }
                            errors.reject(ERROR_MSG, "This verification link has expired. Please try to change your email address again.");
                        }
                    }
                    else
                    {
                        if (verificationToken == null || verificationToken.length() != SecurityManager.tempPasswordLength)
                        {
                            if (!canUpdateUser)  // don't bother auditing admin verification link clicks
                            {
                                UserManager.auditBadVerificationToken(loggedInUser.getUserId(), oldEmail, verifyEmail.getRequestedEmail(), verificationToken, loggedInUser);
                            }
                            errors.reject(ERROR_MSG, "Verification was incorrect. Make sure you've copied the entire link into your browser's address bar.");  // double error, to better explain to user
                        }
                        else if (!verificationToken.equals(verifyEmail.getVerification()))
                        {
                            if (!canUpdateUser)  // don't bother auditing admin verification link clicks
                            {
                                UserManager.auditBadVerificationToken(loggedInUser.getUserId(), oldEmail, verifyEmail.getRequestedEmail(), verificationToken, loggedInUser);
                            }
                        }
                    }

                    if (errors.getErrorCount() == 0)
                    {
                        // don't let non-site admin change email of site admin
                        if (!loggedInUser.hasSiteAdminPermission() && userToChange.hasSiteAdminPermission())
                            throw new UnauthorizedException("Can not change email of a Site Admin user");

                        // Allow mutating SQL from GET requests, since links in verification emails must use GET. Database updates
                        // occur only if a secret (validation token) is provided, which prevents CSRF attacks on this action.
                        try (var ignored = SpringActionController.ignoreSqlUpdates())
                        {
                            // update email in database
                            UserManager.changeEmail(loggedInUser, userToChange, canUpdateUser, requestedEmail, form.getVerificationToken());
                        }

                        // Set values for JSP confirmation
                        form.setIsFromVerifiedLink(false);  // will still be true in URL, though, confusingly
                        form.setIsVerified(true);
                        form.setRequestedEmail(requestedEmail);  // for benefit of the page display

                        // Post-verification courtesy email to old email address, if old address is valid
                        try
                        {
                            new ValidEmail(oldEmail);
                            MimeMessage m = getChangeEmailMessage(oldEmail, requestedEmail);
                            MailHelper.send(m, loggedInUser, getContainer());
                            form.setSentCourtesyEmail(true);
                        }
                        catch (InvalidEmailException ignored)
                        {
                            // Invalid old email... skip the courtesy email
                        }
                    }
                }
            }

            return new JspView<>("/org/labkey/core/user/changeEmail.jsp", form, errors);
        }

        @Override
        public boolean handlePost(UserForm form, BindException errors) throws Exception
        {
            boolean canUpdateUser = getUser().hasRootPermission(UpdateUserPermission.class);
            User userToChange;

            if (!canUpdateUser)
            {
                userToChange = getUser();
            }
            else  // admin, so use form user ID, which may be different from administrator's ID
            {
                userToChange = getModifiableUser(form.getUserId());
            }

            if (!canUpdateUser)  // need to verify email before changing if not site or app admin
            {
                if (!AuthenticationManager.isSelfServiceEmailChangesEnabled())  // uh oh, shouldn't be here
                {
                    throw new UnauthorizedException("User attempted to access self-service email change, but this function is disabled.");
                }

                if (form.getIsChangeEmailRequest())
                {
                    // do nothing, validation happened earlier
                }
                else if (form.getIsPasswordPrompt() || _isPasswordPrompt)
                {
                    _isPasswordPrompt = true; // in case we get an error and need to come back to this action again

                    ValidEmail validRequestedEmail;
                    try
                    {
                        if (form.getRequestedEmail() != null)
                        {
                            validRequestedEmail = new ValidEmail(form.getRequestedEmail());  // validate in case user altered this in the URL parameter
                            _validRequestedEmail = validRequestedEmail;
                        }
                        else
                        {
                            validRequestedEmail = _validRequestedEmail;  // try and get it from internal variable, in case we're re-trying here
                        }
                    }
                    catch (InvalidEmailException e)
                    {
                        errors.reject(ERROR_MSG, "Invalid requested email address.");
                        return false;
                    }

                    String userEmail = userToChange.getEmail();
                    boolean isAuthenticated = authenticate(userEmail, form.getPassword(), getViewContext().getActionURL().getReturnURL(), errors);
                    if (isAuthenticated)
                    {
                        String verificationToken = SecurityManager.createTempPassword();
                        UserManager.requestEmailChange(userToChange, validRequestedEmail, verificationToken, getUser());
                        // verification email
                        Container c = getContainer();

                        ActionURL verifyLinkUrl = getChangeEmailURL(c, userToChange);  // note that user ID added to URL here will only be used if an admin clicks the link, and the link won't work
                        verifyLinkUrl.addParameter("verificationToken", verificationToken);
                        verifyLinkUrl.addParameter("isFromVerifiedLink", true);

                        SecurityManager.sendEmail(c, userToChange, getRequestEmailMessage(userEmail, validRequestedEmail.getEmailAddress()), validRequestedEmail.getEmailAddress(), verifyLinkUrl);
                    }
                    else
                    {
                        errors.reject(ERROR_MSG, "Incorrect password.");
                        Container c = getContainer();
                        ActionURL passwordPromptRetryUrl = getChangeEmailURL(c, userToChange);
                        passwordPromptRetryUrl.addParameter("isPasswordPrompt", true);
                        passwordPromptRetryUrl.addParameter("requestedEmail", form.getRequestedEmail());
                    }
                }
                else  // something strange happened
                {
                    throw new IllegalStateException("Unknown page state after change email POST.");
                }
            }
            else  // site admin, so make change directly
            {
                if (userToChange == null)
                    throw new IllegalStateException("Unknown user for change email POST.");

                // don't let non-site admin change email of site admin
                if (!getUser().hasSiteAdminPermission() && userToChange.hasSiteAdminPermission())
                    throw new UnauthorizedException("Can not change email of a Site Admin user");

                // use "ADMIN" as verification token for debugging, but should never be checked/used
                UserManager.changeEmail(getUser(), userToChange, true, form.getRequestedEmail(), "ADMIN");
            }

            return !errors.hasErrors();
        }

        @Override
        public ActionURL getSuccessURL(UserForm form)
        {
            boolean canUpdateUser = getUser().hasRootPermission(UpdateUserPermission.class);

            if (!canUpdateUser)
            {
                User currentUser = getUser();

                if (form.getIsChangeEmailRequest())
                {
                    Container c = getContainer();
                    ActionURL passwordPromptUrl = getChangeEmailURL(c, currentUser);
                    passwordPromptUrl.addParameter("isPasswordPrompt", true);
                    passwordPromptUrl.addParameter("requestedEmail", form.getRequestedEmail());
                    return passwordPromptUrl;
                }
                else if (form.getIsPasswordPrompt())
                {
                    Container c = getContainer();
                    ActionURL verifyRedirectUrl = getChangeEmailURL(c, currentUser);
                    verifyRedirectUrl.addParameter("isVerifyRedirect", true);
                    verifyRedirectUrl.addParameter("requestedEmail", form.getRequestedEmail());
                    return verifyRedirectUrl;
                }
                else  // actually making self-service email change, so redirect to user details page
                {
                    return new UserUrlsImpl().getUserDetailsURL(getContainer(), currentUser.getUserId(), form.getReturnURLHelper());
                }
            }
            else  // admin email change, so redirect to user details page
            {
                return new UserUrlsImpl().getUserDetailsURL(getContainer(), form.getUserId(), form.getReturnURLHelper());
            }
        }

        boolean authenticate(String email, String password, URLHelper returnUrlHelper, BindException errors)
        {
            try
            {
                DbLoginConfiguration configuration = DbLoginManager.getConfiguration();
                return configuration.getAuthenticationProvider().authenticate(configuration, email, password, returnUrlHelper).isAuthenticated();
            }
            catch (InvalidEmailException e)
            {
                String defaultDomain = ValidEmail.getDefaultDomain();
                StringBuilder sb = new StringBuilder();
                sb.append("Please sign in using your full email address, for example: ");
                if (!defaultDomain.isEmpty())
                {
                    sb.append("employee@");
                    sb.append(defaultDomain);
                    sb.append(" or ");
                }
                sb.append("employee@domain.com");
                errors.reject(ERROR_MSG, sb.toString());
            }

            return false;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addUserDetailsNavTrail(getContainer(), getUser(), root, _form.getReturnActionURL());
            String email = UserManager.getEmailForId(_form.getUserId());
            root.addChild("Change Email Address" + (null != email ? ": " + email : ""));
        }
    }

    public static class UserForm extends ReturnUrlForm
    {
        private int _userId;
        private String _password;
        private String _requestedEmail;
        private String _requestedEmailConfirmation;
        private String _message = null;
        private boolean _isChangeEmailRequest = false;
        private boolean _isPasswordPrompt = false;
        private boolean _isVerifyRedirect = false;
        private boolean _isFromVerifiedLink = false;
        private boolean _isVerified = false;
        private String _verificationToken = null;
        private User _userToChange;
        private boolean _sentCourtesyEmail = false;

        public User getUserToChange()
        {
            return _userToChange;
        }

        public int getUserId()
        {
            return _userId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setUserId(int userId)
        {
            _userId = userId;
        }

        public String getPassword()
        {
            return _password;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setPassword(String password)
        {
            _password = password;
        }

        public String getRequestedEmail()
        {
            return _requestedEmail;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRequestedEmail(String requestedEmail)
        {
            _requestedEmail = requestedEmail;
        }

        public String getRequestedEmailConfirmation()
        {
            return _requestedEmailConfirmation;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRequestedEmailConfirmation(String requestedEmailConfirmation)
        {
            _requestedEmailConfirmation = requestedEmailConfirmation;
        }

        public String getMessage()
        {
            return _message;
        }

        public void setMessage(String message)
        {
            _message = message;
        }

        public boolean getIsChangeEmailRequest()
        {
            return _isChangeEmailRequest;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setIsChangeEmailRequest(boolean isChangeEmailRequest)
        {
            _isChangeEmailRequest = isChangeEmailRequest;
        }

        public boolean getIsPasswordPrompt()
        {
            return _isPasswordPrompt;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setIsPasswordPrompt(boolean isPasswordPrompt)
        {
            _isPasswordPrompt = isPasswordPrompt;
        }

        public boolean getIsVerifyRedirect()
        {
            return _isVerifyRedirect;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setIsVerifyRedirect(boolean isVerifyRedirect)
        {
            _isVerifyRedirect = isVerifyRedirect;
        }

        public boolean getIsFromVerifiedLink()
        {
            return _isFromVerifiedLink;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setIsFromVerifiedLink(boolean isFromVerifiedLink)
        {
            _isFromVerifiedLink = isFromVerifiedLink;
        }

        public boolean getIsVerified()
        {
            return _isVerified;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setIsVerified(boolean isVerified)
        {
            _isVerified = isVerified;
        }

        public String getVerificationToken()
        {
            return _verificationToken;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setVerificationToken(String verificationToken)
        {
            _verificationToken = verificationToken;
        }

        public boolean isSentCourtesyEmail()
        {
            return _sentCourtesyEmail;
        }

        public void setSentCourtesyEmail(boolean sentCourtesyEmail)
        {
            _sentCourtesyEmail = sentCourtesyEmail;
        }
    }

    public static class UserAccessForm extends QueryViewAction.QueryExportForm
    {
        private boolean _showAll = false;
        private int _userId;
        private String _newEmail;
        private String _message = null;
        private boolean _renderInHomeTemplate = true;

        public String getNewEmail()
        {
            return _newEmail;
        }

        public void setNewEmail(String newEmail)
        {
            _newEmail = newEmail;
        }

        public int getUserId()
        {
            return _userId;
        }

        public void setUserId(int userId)
        {
            _userId = userId;
        }

        public boolean getRenderInHomeTemplate()
        {
            return _renderInHomeTemplate;
        }

        public void setRenderInHomeTemplate(boolean renderInHomeTemplate)
        {
            _renderInHomeTemplate = renderInHomeTemplate;
        }

        public String getMessage()
        {
            return _message;
        }

        public void setMessage(String message)
        {
            _message = message;
        }

        public boolean getShowAll()
        {
            return _showAll;
        }

        public void setShowAll(boolean showAll)
        {
            _showAll = showAll;
        }
    }

    private static SecurityMessage getRequestEmailMessage(String currentEmailAddress, String requestedEmailAddress)
    {
        SecurityMessage sm = new SecurityMessage();
        RequestAddressEmailTemplate et = EmailTemplateService.get().getEmailTemplate(RequestAddressEmailTemplate.class);
        et.setCurrentEmailAddress(currentEmailAddress);
        et.setRequestedEmailAddress(requestedEmailAddress);

        sm.setEmailTemplate(et);
        sm.setType("Request email address");

        return sm;
    }

    /**
     * Ensure that the specified user exists and is not the "Guest" user
     * @param userId The user ID to validate
     * @return The {@link User} corresponding to the userId provided, according to the above requirements
     */
    @NotNull private static User getModifiableUser(int userId)
    {
        User user = UserManager.getUser(userId);
        if (null == user)
            throw new NotFoundException("User not found :" + userId);
        if (user.isGuest())
            throw new NotFoundException("Action not valid for Guest user");
        return user;
    }

    public static class RequestAddressEmailTemplate extends SecurityManager.SecurityEmailTemplate
    {
        static final String DEFAULT_SUBJECT =
                "Verification link for ^organizationName^ ^siteShortName^ Web Site email change";
        static final String DEFAULT_BODY =
                "You recently requested a change to the email address associated with your account on the " +
                "^organizationName^ ^siteShortName^ Web Site. If you did not issue this request, you can ignore this email.\n\n" +
                "To complete the process of changing your account's email address from ^currentEmailAddress^ to ^newEmailAddress^, " +
                "simply click the link below or copy it to your browser's address bar.\n\n" +
                "^verificationURL^\n\n" +
                "This step confirms that you are the owner of the new email account. After you click the link, you will need " +
                "to use the new email address when logging into the server.";
        String _currentEmailAddress;
        String _requestedEmailAddress;

        @SuppressWarnings("UnusedDeclaration") // Constructor called via reflection
        public RequestAddressEmailTemplate()
        {
            super("Request email address", "Sent to the user and administrator when a user requests to change their email address.", DEFAULT_SUBJECT, DEFAULT_BODY);
        }

        void setCurrentEmailAddress(String currentEmailAddress) { _currentEmailAddress = currentEmailAddress; }
        void setRequestedEmailAddress(String requestedEmailAddress) { _requestedEmailAddress = requestedEmailAddress; }

        @Override
        protected void addCustomReplacements(Replacements replacements)
        {
            super.addCustomReplacements(replacements);
            replacements.add("currentEmailAddress", String.class, "Current email address for the current user", ContentType.Plain, c -> _currentEmailAddress);
            replacements.add("newEmailAddress", String.class, "Requested email address for the current user", ContentType.Plain, c -> _requestedEmailAddress);
        }
    }

    private MimeMessage getChangeEmailMessage(String oldEmailAddress, String newEmailAddress) throws Exception
    {
        MailHelper.MultipartMessage m = MailHelper.createMultipartMessage();

        ChangeAddressEmailTemplate et = EmailTemplateService.get().getEmailTemplate(ChangeAddressEmailTemplate.class);
        et.setOldEmailAddress(oldEmailAddress);
        et.setNewEmailAddress(newEmailAddress);

        Container c = getContainer();
        m.setTemplate(et, c);
        et.renderSenderToMessage(m, c);
        m.addRecipients(Message.RecipientType.TO, MailHelper.createAddressArray(oldEmailAddress + ";"));

        return m;
    }

    public static class ChangeAddressEmailTemplate extends UserOriginatedEmailTemplate
    {
        static final String DEFAULT_SUBJECT =
                "Notification that ^organizationName^ ^siteShortName^ Web Site email has changed";
        static final String DEFAULT_BODY =
                "The email address associated with your account on the ^organizationName^ ^siteShortName^ Web Site has been updated, " +
                "from ^oldEmailAddress^ to ^newEmailAddress^.\n\n" +
                "If you did not request this change, please contact the server administrator immediately. Otherwise, no further action " +
                "is required, and you may use the new email address when logging into the server going forward.";
        String _oldEmailAddress;
        String _newEmailAddress;

        @SuppressWarnings("UnusedDeclaration") // Constructor called via reflection
        public ChangeAddressEmailTemplate()
        {
            super("Change email address", "Sent to the user and administrator when a user has changed their email address.", DEFAULT_SUBJECT, DEFAULT_BODY, ContentType.Plain, Scope.Site);
        }

        void setOldEmailAddress(String oldEmailAddress) { _oldEmailAddress = oldEmailAddress; }
        void setNewEmailAddress(String newEmailAddress) { _newEmailAddress = newEmailAddress; }

        @Override
        protected void addCustomReplacements(Replacements replacements)
        {
            super.addCustomReplacements(replacements);
            replacements.add("oldEmailAddress", String.class, "Old email address for the current user", ContentType.Plain, c -> _oldEmailAddress);
            replacements.add("newEmailAddress", String.class, "New email address for the current user", ContentType.Plain, c -> _newEmailAddress);
        }
    }

    public static class GetUsersForm
    {
        private String _group;
        private Integer _groupId;
        private String _name; // prefix to match against displayName and email address
        private boolean _allMembers; // when getting members of a group, should we get the direct members of the group (allMembers=false) or also members of subgroups (allMembers=true)
        private boolean _active; // should we get only active members (relevant only if permissions is empty)
        private Permission[] _permissions; // the  permissions each user must have (They must have all of these)
        private Set<Class<? extends Permission>> _permissionClasses = Collections.emptySet(); // the set of permission classes corresponding to the permissions array
        private boolean _includeInactive; // Should we include inactive members, only used in the 23.11 version of GetUsersWithPermissionsAction

        public String getGroup()
        {
            return _group;
        }

        public void setGroup(String group)
        {
            _group = group;
        }

        public Integer getGroupId()
        {
            return _groupId;
        }

        public void setGroupId(Integer groupId)
        {
            _groupId = groupId;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public boolean isAllMembers()
        {
            return _allMembers;
        }

        public void setAllMembers(boolean allMembers)
        {
            _allMembers = allMembers;
        }

        public Permission[] getPermissions()
        {
            return _permissions;
        }

        public Set<Class<? extends Permission>> getPermissionClasses()
        {
           return _permissionClasses;
        }

        public void setPermissions(Permission[] permission)
        {
            _permissions = permission;
            if (_permissions == null)
                _permissionClasses = Collections.emptySet();
            else
                _permissionClasses = Arrays.stream(_permissions).filter(Objects::nonNull).map(Permission::getClass).collect(Collectors.toSet());
        }

        public boolean getActive()
        {
            return _active;
        }

        public void setActive(boolean active)
        {
            _active = active;
        }

        public boolean getIncludeInactive()
        {
            return _includeInactive;
        }

        public void setIncludeInactive(boolean includeInactive)
        {
            _includeInactive = includeInactive;
        }
    }


    /**
     * Collects a set of users either from a particular group or from any of the project groups of the current container.
     * Optionally filters for those users who have a given set of permissions. Can also include deactivated users (though if
     * checking for permissions, no deactivated users will be included).
     *
     * N.B. Users that have permissions within the current project but are not part of any project group WILL NOT be returned unless
     * the user is in one of the global groups (such as SiteAdmins) and you set allMembers=true. In other words, this is probably
     * not the API you're looking for. Consider using GetUsersWithPermissions instead.
     */
    @RequiresLogin
    @RequiresPermission(ReadPermission.class)
    public static class GetUsersAction extends ReadOnlyApiAction<GetUsersForm>
    {
        @Override
        public ApiResponse execute(GetUsersForm form, BindException errors)
        {
            Container container = getContainer();
            User currentUser = getUser();

            if (container.isRoot() && !currentUser.hasRootPermission(UserManagementPermission.class))
                throw new UnauthorizedException("Only site/application administrators may see users in the root container!");

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("container", container.getPath());

            Collection<User> users;

            //if requesting users in a specific group...
            if (null != StringUtils.trimToNull(form.getGroup()) || null != form.getGroupId())
            {
                users = getProjectGroupUsers(form, response, !form.getActive());
            }
            else
            {
                //special-case: if container is root, return all active users
                //else, return all users in the current project
                //we've already checked above that the current user is a system admin
                if (container.isRoot())
                    users = UserManager.getUsers(!form.getActive());
                else
                    users = SecurityManager.getProjectUsers(container, form.isAllMembers(), !form.getActive());
            }

            this.setUsersList(form, filterForPermissions(form, users), response);
            return response;
        }

        // Filter the collection of users to those that have all of the permissions provided in the form.
        // If no permissions are provided, no filtering will occur.
        protected Set<User> filterForPermissions(GetUsersForm form, Collection<User> users)
        {
            Container container = getContainer();
            return users.stream().filter(user -> {
                boolean userHasPermission = true;
                for (Class<? extends Permission> permClass : form.getPermissionClasses())
                {
                    if (!container.hasPermission(user, permClass))
                    {
                        userHasPermission = false;
                        break;
                    }
                }
                return userHasPermission;
            }).collect(Collectors.toSet());
        }

        @NotNull
        protected Collection<User> getProjectGroupUsers(GetUsersForm form, ApiSimpleResponse response, boolean includeInactive)
        {
            Container project = getContainer().getProject();

            //get users in given group/role name
            Integer groupId = form.getGroupId();

            if (null == groupId)
                groupId = SecurityManager.getGroupId(project, form.getGroup(), false);

            if (null == groupId)
                throw new IllegalArgumentException("The group '" + form.getGroup() + "' does not exist in the project '"
                        + (project != null ? project.getPath() : "" )+ "'");

            Group group = SecurityManager.getGroup(groupId);

            if (null == group)
                throw new NotFoundException("Cannot find group with id " + groupId);

            response.put("groupId", group.getUserId());
            response.put("groupName", group.getName());
            response.put("groupCaption", SecurityManager.getDisambiguatedGroupName(group));

            MemberType<User> userMemberType;
            if (includeInactive)
                userMemberType = MemberType.ACTIVE_AND_INACTIVE_USERS;
            else
                userMemberType = MemberType.ACTIVE_USERS;

            // if the allMembers flag is set, then recurse and if group is site users group then return all site users
            Collection<User> users;
            if (form.isAllMembers())
                users = SecurityManager.getAllGroupMembers(group, userMemberType, group.isUsers());
            else
                users = SecurityManager.getGroupMembers(group, userMemberType);

            return users;
        }

        protected void setUsersList(GetUsersForm form, Collection<User> users, ApiSimpleResponse response)
        {
            Container container = getContainer();
            User currentUser = getUser();
            List<JSONObject> userResponseList = new ArrayList<>();
            //trim name filter to empty so we are guaranteed a non-null string
            //and convert to lower-case for the compare below
            String nameFilter = StringUtils.trimToEmpty(form.getName()).toLowerCase();

            if (!nameFilter.isEmpty())
                response.put("name", nameFilter);

            for (User user : users)
            {
                //according to the docs, startsWith will return true even if nameFilter is empty string
                if (user.getEmail().toLowerCase().startsWith(nameFilter) || user.getDisplayName(null).toLowerCase().startsWith(nameFilter))
                {
                    JSONObject userInfo = User.getUserProps(user, currentUser, container, false);

                    // Backwards compatibility. This interface specifies "userId" while User.getUserProps() specifies "id"
                    userInfo.put("userId", user.getUserId());

                    userResponseList.add(userInfo);
                }
            }
            response.put("users", userResponseList);
        }
    }

    /**
     * Retrieves the set of users that have all of a specified set of permissions. A group
     * may be provided and only users within that group will be returned. A name (prefix) may be
     * provided and only users whose email or display name starts with the prefix will be returned.
     * This will not return any deactivated users (since they do not have permissions of any sort).
     */
    @RequiresLogin
    @RequiresPermission(ReadPermission.class)
    @ApiVersion(23.10)
    public static class GetUsersWithPermissionsAction extends GetUsersAction
    {
        @Override
        public void validateForm(GetUsersForm form, Errors errors)
        {
            if (form.getPermissions() == null || form.getPermissions().length == 0)
            {
                errors.reject(ERROR_REQUIRED, "Permissions are required");
            }
            else if (form.getPermissionClasses().isEmpty())
            {
                errors.reject(ERROR_GENERIC, "No valid permission classes provided.");
            }
        }

        /**
         * The older 23.10 response format does not honor the newer includeInactive flag. It only honors the active
         * flag when requesting users of a group, and ignores the flag (only ever returning active users) when not
         * requesting a group.
         */
        private ApiResponse response2310(ApiSimpleResponse response, GetUsersForm form)
        {
            SimpleMetricsService.get().increment(CoreModule.CORE_MODULE_NAME, "GetUsersWithPermissionsAction", "OldVersionCalls");
            Collection<User> users;

            //if requesting users in a specific group...
            if (null != StringUtils.trimToNull(form.getGroup()) || null != form.getGroupId())
            {
                users = filterForPermissions(form, getProjectGroupUsers(form, response, !form.getActive()));
            }
            else
            {
                users = SecurityManager.getUsersWithPermissions(getContainer(), form.getPermissionClasses());
            }

            this.setUsersList(form, users, response);

            return response;
        }

        /**
         * The 23.11 response format does not honor the active flag, instead it honors the includeInactive flag, and
         * it honors it when requesting users or groups. The flag defaults to false, so by default only active users
         * will be returned.
         */
        private ApiResponse response2311(ApiSimpleResponse response, GetUsersForm form)
        {
            boolean includeInactive = form.getIncludeInactive();
            Collection<User> users;

            //if requesting users in a specific group...
            if (null != StringUtils.trimToNull(form.getGroup()) || null != form.getGroupId())
            {
                users = filterForPermissions(form, getProjectGroupUsers(form, response, includeInactive));
            }
            else
            {
                users = SecurityManager.getUsersWithPermissions(getContainer(), includeInactive, form.getPermissionClasses());
            }

            this.setUsersList(form, users, response);

            return response;
        }

        @Override
        public ApiResponse execute(GetUsersForm form, BindException errors)
        {
            Container container = getContainer();
            User currentUser = getUser();

            if (container.isRoot() && !currentUser.hasRootPermission(UserManagementPermission.class))
                throw new UnauthorizedException("Only site/application administrators may see users in the root container!");

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("container", container.getPath());

            if (getRequestedApiVersion() <= 23.10)
                return response2310(response, form);

            return response2311(response, form);
        }
    }


    @RequiresPermission(AdminPermission.class)
    public static class GetImpersonationUsersAction extends MutatingApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object object, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            User currentUser = getUser();
            Container project = currentUser.hasRootAdminPermission() ? null : getContainer().getProject();
            Collection<User> users = UserImpersonationContextFactory.getValidImpersonationUsers(project, getUser());

            Collection<Map<String, Object>> responseUsers = new LinkedList<>();

            for (User user : users)
            {
                Map<String, Object> map = new HashMap<>();
                map.put("userId", user.getUserId());
                map.put("email", user.getEmail());
                map.put("displayName", user.getDisplayName(currentUser));
                map.put("active", user.isActive());
                responseUsers.add(map);
            }

            response.put("users", responseUsers);

            return response;
        }
    }


    // Need returnUrl because we stash the current URL in session and return to it after impersonation is complete
    public static class ImpersonateUserForm extends ReturnUrlForm
    {
        private Integer _userId;
        private ValidEmail _email;

        public Integer getUserId()
        {
            return _userId;
        }

        public void setUserId(Integer userId)
        {
            _userId = userId;
        }

        public ValidEmail getEmail()
        {
            return _email;
        }

        public void setEmail(ValidEmail email)
        {
            _email = email;
        }
    }


    // All three impersonate API actions have the same form
    private abstract class ImpersonateApiAction<FORM> extends MutatingApiAction<FORM>
    {
        @Override
        protected String getCommandClassMethodName()
        {
            return "impersonate";
        }

        @Override
        public ApiResponse execute(FORM form, BindException errors)
        {
            String error = impersonate(form);

            if (null != error)
            {
                errors.reject(null, error);
                return null;
            }

            return new ApiSimpleResponse("success", true);
        }

        // Non-null return value means send back an error message
        public abstract @Nullable String impersonate(FORM form);
    }


    @RequiresPermission(AdminPermission.class)
    public class ImpersonateUserAction extends ImpersonateApiAction<ImpersonateUserForm>
    {
        @Override
        public @Nullable String impersonate(ImpersonateUserForm form)
        {
            if (getUser().isImpersonated())
                return "Can't impersonate; you're already impersonating";

            User impersonatedUser;
            if (form.getUserId() != null)
            {
                impersonatedUser = UserManager.getUser(form.getUserId());
            }
            else if (form.getEmail() != null)
            {
                impersonatedUser = UserManager.getUser(form.getEmail());
            }
            else
            {
                return "Must specify an email or userId";
            }

            if (null == impersonatedUser)
                return "User doesn't exist";

            try
            {
                SecurityManager.impersonateUser(getViewContext(), impersonatedUser, form.getReturnActionURL(AppProps.getInstance().getHomePageActionURL()));
            }
            catch (UnauthorizedImpersonationException uie)
            {
                return uie.getMessage();
            }

            return null;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class GetImpersonationGroupsAction extends MutatingApiAction
    {
        @Override
        public ApiResponse execute(Object object, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Collection<Group> groups = GroupImpersonationContextFactory.getValidImpersonationGroups(getContainer(), getUser());
            Collection<Map<String, Object>> responseGroups = new LinkedList<>();

            for (Group group : groups)
            {
                Map<String, Object> map = new HashMap<>();
                map.put("groupId", group.getUserId());
                map.put("displayName", (group.isProjectGroup() ? "" : "Site: ") + group.getName());
                responseGroups.add(map);
            }

            response.put("groups", responseGroups);

            return response;
        }
    }


    public static class ImpersonateGroupForm extends ReturnUrlForm
    {
        private Integer _groupId = null;

        public Integer getGroupId()
        {
            return _groupId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setGroupId(Integer groupId)
        {
            _groupId = groupId;
        }
    }


    // TODO: Better instructions
    // TODO: Messages for no groups, no users
    @RequiresPermission(AdminPermission.class)
    public class ImpersonateGroupAction extends ImpersonateApiAction<ImpersonateGroupForm>
    {
        @Override
        public @Nullable String impersonate(ImpersonateGroupForm form)
        {
            if (getUser().isImpersonated())
                return "Can't impersonate; you're already impersonating";

            Integer groupId = form.getGroupId();

            if (null == groupId)
                return "Must specify a group ID";

            Group group = SecurityManager.getGroup(groupId);

            if (null == group)
                return "Group doesn't exist";

            ActionURL returnURL = form.getReturnActionURL(AppProps.getInstance().getHomePageActionURL());

            try
            {
                SecurityManager.impersonateGroup(getViewContext(), group, returnURL);
            }
            catch (UnauthorizedImpersonationException uie)
            {
                return uie.getMessage();
            }

            return null;
        }
    }


    @RequiresNoPermission
    public class GetImpersonationRolesAction extends MutatingApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object object, BindException errors)
        {
            ImpersonationContext context = authorizeImpersonateRoles();
            Set<Role> impersonationRoles = context.isImpersonating() ? context.getAssignedRoles(getUser(), getContainer()).collect(Collectors.toSet()) : Collections.emptySet();

            User user = context.isImpersonating() ? context.getAdminUser() : getUser();
            ApiSimpleResponse response = new ApiSimpleResponse();
            Collection<Map<String, Object>> responseRoles = RoleImpersonationContextFactory.getValidImpersonationRoles(getContainer(), user)
                .map(role -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("displayName", role.getDisplayName());
                    map.put("roleName", role.getUniqueName());
                    map.put("hasRead", role.getPermissions().contains(ReadPermission.class));
                    map.put("selected", impersonationRoles.contains(role));
                    return map;
                })
                .toList();

            response.put("roles", responseRoles);

            return response;
        }
    }


    private ImpersonationContext authorizeImpersonateRoles()
    {
        User user = getUser();
        ImpersonationContext context = user.getImpersonationContext();

        if (context.isImpersonating())
            user = context.getAdminUser();

        if (getContainer().isRoot() && user.hasRootPermission(CanImpersonateSiteRolesPermission.class))
            return context;

        if (!getContainer().hasPermission(user, AdminPermission.class))
            throw new UnauthorizedException();

        return context;
    }


    public static class ImpersonateRolesForm extends ReturnUrlForm
    {
        private String[] _roleNames;

        public String[] getRoleNames()
        {
            return _roleNames;
        }

        public void setRoleNames(String[] roleNames)
        {
            _roleNames = roleNames;
        }
    }


    // Permissions are checked in impersonate() to let an admin adjust an existing impersonation
    @RequiresNoPermission
    public class ImpersonateRolesAction extends ImpersonateApiAction<ImpersonateRolesForm>
    {
        @Nullable
        @Override
        public String impersonate(ImpersonateRolesForm form)
        {
            ImpersonationContext context = authorizeImpersonateRoles();
            Set<Role> currentImpersonationRoles = context.isImpersonating() ? context.getAssignedRoles(getUser(), getContainer()).collect(Collectors.toSet()) : Collections.emptySet();

            String[] roleNames = form.getRoleNames();

            if (ArrayUtils.isEmpty(roleNames))
                return "Must provide roles";

            Collection<Role> newImpersonationRoles = new LinkedList<>();

            for (String roleName : roleNames)
            {
                Role role = RoleManager.getRole(roleName);
                if (null == role)
                    return "Role not found: " + roleName;
                newImpersonationRoles.add(role);
            }

            ActionURL returnURL = context.isImpersonating() ? context.getReturnURL() : form.getReturnActionURL(AppProps.getInstance().getHomePageActionURL());
            SecurityManager.impersonateRoles(getViewContext(), newImpersonationRoles, currentImpersonationRoles, returnURL);

            return null;
        }
    }

    public static class LimitActiveUsersForm
    {
        private boolean _userWarning = false;
        private int _userWarningLevel = 0;
        private String _userWarningMessage = "";
        private boolean _userLimit = false;
        private int _userLimitLevel = 0;
        private String _userLimitMessage = "";

        public boolean isUserWarning()
        {
            return _userWarning;
        }

        @SuppressWarnings("unused")
        public void setUserWarning(boolean userWarning)
        {
            _userWarning = userWarning;
        }

        public int getUserWarningLevel()
        {
            return _userWarningLevel;
        }

        @SuppressWarnings("unused")
        public void setUserWarningLevel(int userWarningLevel)
        {
            _userWarningLevel = userWarningLevel;
        }

        public String getUserWarningMessage()
        {
            return _userWarningMessage;
        }

        @SuppressWarnings("unused")
        public void setUserWarningMessage(String userWarningMessage)
        {
            _userWarningMessage = userWarningMessage;
        }

        public boolean isUserLimit()
        {
            return _userLimit;
        }

        @SuppressWarnings("unused")
        public void setUserLimit(boolean userLimit)
        {
            _userLimit = userLimit;
        }

        public int getUserLimitLevel()
        {
            return _userLimitLevel;
        }

        @SuppressWarnings("unused")
        public void setUserLimitLevel(int userLimitLevel)
        {
            _userLimitLevel = userLimitLevel;
        }

        public String getUserLimitMessage()
        {
            return _userLimitMessage;
        }

        @SuppressWarnings("unused")
        public void setUserLimitMessage(String userLimitMessage)
        {
            _userLimitMessage = userLimitMessage;
        }
    }

    @AdminConsoleAction(AdminOperationsPermission.class)
    public static class LimitActiveUsersAction extends FormViewAction<LimitActiveUsersForm>
    {
        @Override
        public void validateCommand(LimitActiveUsersForm form, Errors errors)
        {
            if (form.isUserWarning() && form.getUserWarningLevel() <= 0)
                errors.reject(ERROR_MSG, "User warning level must be a positive integer.");
            if (form.isUserLimit() && form.getUserLimitLevel() <= 0)
                errors.reject(ERROR_MSG, "User limit level must be a positive integer.");
            if (form.isUserWarning() && form.isUserLimit() && form.getUserWarningLevel() > form.getUserLimitLevel())
                errors.reject(ERROR_MSG, "User limit level must be greater than or equal to user warning level.");
        }

        @Override
        public ModelAndView getView(LimitActiveUsersForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/core/user/limitActiveUsers.jsp", null, errors);
        }

        @Override
        public boolean handlePost(LimitActiveUsersForm form, BindException errors)
        {
            LimitActiveUsersSettings settings = new LimitActiveUsersSettings();
            settings.setUserWarning(form.isUserWarning());
            settings.setUserWarningLevel(form.getUserWarningLevel());
            settings.setUserWarningMessage(form.getUserWarningMessage());
            settings.setUserLimitLevel(form.getUserLimitLevel());
            settings.setUserLimitMessage(form.getUserLimitMessage());
            settings.setUserLimit(form.isUserLimit());
            settings.save(getUser());

            return true;
        }

        @Override
        public URLHelper getSuccessURL(LimitActiveUsersForm form)
        {
            return urlProvider(AdminUrls.class).getAdminConsoleURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            urlProvider(AdminUrls.class).addAdminNavTrail(root, "Limit Active Users", getClass(), getContainer());
        }
    }

    @RequiresPermission(AddUserPermission.class)
    public static class GetUserLimitSettingsAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            return LimitActiveUsersSettings.getApiResponse(getContainer(), getUser());
        }
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.hasSiteAdminPermission());

            UserController controller = new UserController();

            // @RequiresPermission(ReadPermission.class)
            assertForReadPermission(user, false,
                new BeginAction(),
                new GetUsersAction(),
                new GetUsersWithPermissionsAction()
            );

            // @RequiresPermission(AdminPermission.class)
            assertForAdminPermission(user,
                controller.new ShowUsersAction(),
                //TODO controller.new ShowUserHistoryAction(),
                //TODO controller.new UserAccessAction(),
                new GetImpersonationUsersAction(),
                controller.new ImpersonateUserAction(),
                controller.new GetImpersonationGroupsAction(),
                controller.new ImpersonateGroupAction()
//                controller.new GetImpersonationRolesAction()   Annotated as "no permission", to allow impersonation adjustments
//                controller.new ImpersonateRolesAction()        Annotated as "no permission", to allow impersonation adjustments
            );

            // @RequiresPermission(UserManagementPermission.class)
            assertForUserPermissions(user,
                controller.new DeactivateUsersAction(),
                controller.new ActivateUsersAction(),
                controller.new DeleteUsersAction()
            );

            // @AdminConsoleAction
            assertForAdminPermission(ContainerManager.getRoot(), user,
                new ShowUserPreferencesAction(),
                new LimitActiveUsersAction()
            );
        }
    }
}
