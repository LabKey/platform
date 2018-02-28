/*
 * Copyright (c) 2003-2017 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
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
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UrlColumn;
import org.labkey.api.data.validator.ColumnValidators;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
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
import org.labkey.api.security.CSRF;
import org.labkey.api.security.Group;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.MemberType;
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
import org.labkey.api.security.impersonation.GroupImpersonationContextFactory;
import org.labkey.api.security.impersonation.ImpersonationContext;
import org.labkey.api.security.impersonation.RoleImpersonationContextFactory;
import org.labkey.api.security.impersonation.UnauthorizedImpersonationException;
import org.labkey.api.security.impersonation.UserImpersonationContextFactory;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UserManagementPermission;
import org.labkey.api.security.roles.OwnerRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.thumbnail.ImageStreamThumbnailProvider;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.emailTemplate.EmailTemplate;
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
import org.labkey.core.view.template.bootstrap.PrintTemplate;
import org.labkey.core.login.DbLoginAuthenticationProvider;
import org.labkey.core.login.LoginController;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.query.UserAuditProvider;
import org.labkey.core.query.UserAvatarDisplayColumnFactory;
import org.labkey.core.query.UsersDomainKind;
import org.labkey.core.query.UsersTable;
import org.labkey.core.security.SecurityController;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.imageio.ImageIO;
import javax.mail.Message;
import javax.mail.internet.MimeMessage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            return new ActionURL(ShowUsersAction.class, container);
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
    }

    private void setDataRegionButtons(DataRegion rgn, boolean isOwnRecord, boolean canManageDetailsUser)
    {
        final User user = getUser();
        Container c = getContainer();
        ActionURL currentURL = getViewContext().getActionURL();
        boolean isUserManager = getUser().hasRootPermission(UserManagementPermission.class);
        boolean isAnyAdmin = isUserManager || c.hasPermission(user, AdminPermission.class);

        assert isOwnRecord || isAnyAdmin;

        SimpleDisplayColumn accountDetails = new UrlColumn(new UserUrlsImpl().getUserDetailsURL(c, currentURL) + "userId=${UserId}", "details");
        accountDetails.setDisplayModes(DataRegion.MODE_GRID);
        rgn.addDisplayColumn(0, accountDetails);

        if (isAnyAdmin)
        {
            SimpleDisplayColumn securityDetails = new UrlColumn(new UserUrlsImpl().getUserAccessURL(c) + "userId=${UserId}", "permissions");
            securityDetails.setDisplayModes(DataRegion.MODE_GRID);
            rgn.addDisplayColumn(1, securityDetails);
        }

        ButtonBar gridButtonBar = new ButtonBar();

        if (isUserManager)
        {
            rgn.setShowRecordSelectors(true);
        }

        populateUserGridButtonBar(gridButtonBar, isUserManager, isAnyAdmin);
        rgn.setButtonBar(gridButtonBar, DataRegion.MODE_GRID);

        ActionURL showUsersURL = new ActionURL(ShowUsersAction.class, c);
        showUsersURL.addParameter(DataRegion.LAST_FILTER_PARAM, true);
        ActionButton showGrid = new ActionButton(showUsersURL, c.isRoot() ? "Show Users" : "Show Project Users");
        showGrid.setActionType(ActionButton.Action.LINK);

        ButtonBar detailsButtonBar = new ButtonBar();
        if (isAnyAdmin)
            detailsButtonBar.add(showGrid);

        ActionURL editURL = new ActionURL(ShowUpdateAction.class, getContainer());
        editURL.addParameter(QueryParam.schemaName.toString(), "core");
        editURL.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, CoreQuerySchema.SITE_USERS_TABLE_NAME);
        editURL.addParameter("userId", NumberUtils.toInt(currentURL.getParameter("userId")));
        editURL.addReturnURL(currentURL);

        if (isOwnRecord || (isUserManager && canManageDetailsUser))
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
            updateButtonBar.add(showGrid);
        rgn.setButtonBar(updateButtonBar, DataRegion.MODE_UPDATE);
    }

    private void populateUserGridButtonBar(ButtonBar gridButtonBar, boolean isUserManager, boolean isProjectAdminOrBetter)
    {
        if (isUserManager && getContainer().isRoot())
        {
            ActionButton deactivate = new ActionButton(DeactivateUsersAction.class, "Deactivate");
            deactivate.setRequiresSelection(true);
            deactivate.setActionType(ActionButton.Action.POST);
            gridButtonBar.add(deactivate);

            ActionButton activate = new ActionButton(ActivateUsersAction.class, "Re-Activate");
            activate.setRequiresSelection(true);
            activate.setActionType(ActionButton.Action.POST);
            gridButtonBar.add(activate);

            ActionButton delete = new ActionButton(DeleteUsersAction.class, "Delete");
            delete.setRequiresSelection(true);
            delete.setActionType(ActionButton.Action.POST);
            gridButtonBar.add(delete);

            ActionButton insert = new ActionButton(PageFlowUtil.urlProvider(SecurityUrls.class).getAddUsersURL(), "Add Users");
            insert.setActionType(ActionButton.Action.LINK);
            gridButtonBar.add(insert);

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

                ActionButton preferences = new ActionButton(url, "Change User Properties");
                preferences.setActionType(ActionButton.Action.LINK);
                gridButtonBar.add(preferences);
            }
        }

        if (isProjectAdminOrBetter)
        {
            if (AuditLogService.get().isViewable())
            {
                gridButtonBar.add(new ActionButton(ShowUserHistoryAction.class, "History",
                        DataRegion.MODE_ALL, ActionButton.Action.LINK));
            }
        }
    }

    /**
     * This method represents the point of entry into the pageflow
     */
    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return HttpView.redirect(new UserUrlsImpl().getSiteUsersURL());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class UserIdForm
    {
        private Integer[] _userId;
        private String _redirUrl;

        public Integer[] getUserId()
        {
            return _userId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setUserId(Integer[] userId)
        {
            _userId = userId;
        }

        // TODO: Switch to ReturnUrlForm and standard param
        public String getRedirUrl()
        {
            return _redirUrl;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRedirUrl(String redirUrl)
        {
            _redirUrl = redirUrl;
        }
    }

    public abstract class BaseActivateUsersAction extends FormViewAction<UserIdForm>
    {
        private boolean _active = true;

        protected BaseActivateUsersAction(boolean active)
        {
            _active = active;
        }

        public void validateCommand(UserIdForm form, Errors errors)
        {
        }

        public ModelAndView getView(UserIdForm form, boolean reshow, BindException errors) throws Exception
        {
            DeactivateUsersBean bean = new DeactivateUsersBean(_active, null == form.getRedirUrl() ? null : new ActionURL(form.getRedirUrl()));
            if (null != form.getUserId())
            {
                for (Integer userId : form.getUserId())
                {
                    if (isValidUserToUpdate(userId))
                        bean.addUser(UserManager.getUser(userId));
                }
            }
            else
            {
                //try to get a user selection list from the dataregion
                Set<Integer> userIds = DataRegionSelection.getSelectedIntegers(getViewContext(), true);
                if (userIds.isEmpty())
                    throw new RedirectException(new UserUrlsImpl().getSiteUsersURL().getLocalURIString());

                for (Integer id : userIds)
                {
                    if (isValidUserToUpdate(id))
                        bean.addUser(UserManager.getUser(id));
                }
            }

            if (bean.getUsers().size() == 0)
                throw new RedirectException(bean.getRedirUrl().getLocalURIString());

            return new JspView<>("/org/labkey/core/user/deactivateUsers.jsp", bean, errors);
        }

        public boolean handlePost(UserIdForm form, BindException errors) throws Exception
        {
            if (null == form.getUserId())
                return false;

            User curUser = getUser();
            for (Integer userId : form.getUserId())
            {
                if (isValidUserToUpdate(userId))
                    UserManager.setUserActive(curUser, userId, _active);
            }
            return true;
        }

        private boolean isValidUserToUpdate(Integer formUserId)
        {
            User curUser = getUser();
            User formUser = null != formUserId ? UserManager.getUser(formUserId) : null;

            return null != formUser
                && formUserId != curUser.getUserId() // don't let a user activate/deactivate themselves
                && (curUser.isInSiteAdminGroup() || !formUser.isInSiteAdminGroup()); // don't let non-site admin deactivate a site admin
        }

        public ActionURL getSuccessURL(UserIdForm form)
        {
            return null != form.getRedirUrl() ? new ActionURL(form.getRedirUrl())
                    : new UserUrlsImpl().getSiteUsersURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Site Users", new UserUrlsImpl().getSiteUsersURL());
            String title = _active ? "Re-activate Users" : "Deactivate Users";
            return root.addChild(title);
        }
    }

    @RequiresPermission(UserManagementPermission.class)
    @CSRF
    public class DeactivateUsersAction extends BaseActivateUsersAction
    {
        public DeactivateUsersAction()
        {
            super(false);
        }
    }

    @RequiresPermission(UserManagementPermission.class)
    @CSRF
    public class ActivateUsersAction extends BaseActivateUsersAction
    {
        public ActivateUsersAction()
        {
            super(true);
        }
    }

    @RequiresPermission(UserManagementPermission.class)
    @CSRF
    public class DeleteUsersAction extends FormViewAction<UserIdForm>
    {
        public void validateCommand(UserIdForm target, Errors errors)
        {
        }

        public ModelAndView getView(UserIdForm form, boolean reshow, BindException errors) throws Exception
        {
            String siteUsersUrl = new UserUrlsImpl().getSiteUsersURL().getLocalURIString();
            DeleteUsersBean bean = new DeleteUsersBean();

            if (null != form.getUserId())
            {
                for (Integer userId : form.getUserId())
                {
                    if (isValidUserToDelete(userId))
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
                    if (isValidUserToDelete(id))
                        bean.addUser(UserManager.getUser(id));
                }
            }

            if (bean.getUsers().size() == 0)
                throw new RedirectException(siteUsersUrl);

            return new JspView<>("/org/labkey/core/user/deleteUsers.jsp", bean, errors);
        }

        public boolean handlePost(UserIdForm form, BindException errors) throws Exception
        {
            if (null == form.getUserId())
                return false;

            User curUser = getUser();

            for (Integer userId : form.getUserId())
            {
                if (null != userId && userId != curUser.getUserId())
                    UserManager.deleteUser(userId);
            }
            return true;
        }

        private boolean isValidUserToDelete(Integer formUserId)
        {
            User curUser = getUser();
            User formUser = null != formUserId ? UserManager.getUser(formUserId) : null;

            return null != formUser
                && formUserId != curUser.getUserId() // don't let a user delete themselves
                && (curUser.isInSiteAdminGroup() || !formUser.isInSiteAdminGroup()); // don't let non-site admin delete a site admin
        }

        public ActionURL getSuccessURL(UserIdForm userIdForm)
        {
            return new UserUrlsImpl().getSiteUsersURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Site Users", new UserUrlsImpl().getSiteUsersURL());
            return root.addChild("Delete Users");
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

        protected QueryView createQueryView(final ShowUsersForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
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
                    populateUserGridButtonBar(bar, isUserManager, isProjectAdminOrBetter);
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
        protected ModelAndView getHtmlView(ShowUsersForm form, BindException errors) throws Exception
        {
            VBox users = new VBox();
            users.setTitle("Users");
            users.setFrame(WebPartView.FrameType.PORTAL);

            JspView<ShowUsersForm> toggleInactiveView = new JspView<>("/org/labkey/core/user/toggleInactive.jsp", form);

            users.addView(toggleInactiveView);
            users.addView(createQueryView(form, errors, false, "Users"));

            return users;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (getContainer().isRoot())
            {
                setHelpTopic(new HelpTopic("manageUsers"));
                return root.addChild("Site Users");
            }
            else
            {
                setHelpTopic(new HelpTopic("manageProjectMembers"));
                return root.addChild("Project Users");
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

        public ModelAndView getView(Object o, BindException errors) throws Exception
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

        public NavTree appendNavTrail(NavTree root)
        {
            if (getContainer().isRoot())
            {
                root.addChild("Site Users", new UserUrlsImpl().getSiteUsersURL());
                return root.addChild("Site Users History");
            }
            else
            {
                root.addChild("Project Users", new UserUrlsImpl().getProjectUsersURL(getContainer()));
                return root.addChild("Project Users History");
            }
        }
    }

    @AdminConsoleAction
    @RequiresPermission(AdminPermission.class)
    public class ShowUserPreferencesAction extends RedirectAction<Object>
    {
        ActionURL _successUrl;

        @Override
        public URLHelper getSuccessURL(Object form)
        {
            return _successUrl;
        }

        @Override
        public boolean doAction(Object form, BindException errors) throws Exception
        {
            String domainURI = UsersDomainKind.getDomainURI(CoreQuerySchema.NAME, CoreQuerySchema.USERS_TABLE_NAME, UsersDomainKind.getDomainContainer(), getUser());
            Domain domain = PropertyService.get().getDomain(UsersDomainKind.getDomainContainer(), domainURI);

            if (domain != null)
            {
                _successUrl = domain.getDomainKind().urlEditDefinition(domain, getViewContext());
                _successUrl.addReturnURL(getViewContext().getActionURL());

                return true;
            }
            return false;
        }
    }

    @RequiresLogin
    public class AttachmentDownloadAction extends BaseDownloadAction<AttachmentForm>
    {
        @Override
        public @Nullable Pair<AttachmentParent, String> getAttachment(AttachmentForm form)
        {
            boolean isUserManager = getUser().hasRootPermission(UserManagementPermission.class);
            User user = UserManager.getUser(form.getUserId());
            if (null == user)
            {
                throw new NotFoundException("Unable to find user");
            }
            else if (!isUserManager && user.getUserId() != getUser().getUserId())
            {
                throw new IllegalArgumentException("Unable to download user attachment");
            }

            return new Pair<>(new AvatarThumbnailProvider(user), form.getName());
        }
    }

    public static class AttachmentForm
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

    @RequiresLogin @CSRF
    public class ShowUpdateAction extends UserSchemaAction
    {
        Integer _userId;
        Integer _pkVal;
        PropertyValue _deletedAttachments;

        public ModelAndView getView(QueryUpdateForm form, boolean reshow, BindException errors) throws Exception
        {
            User user = getUser();
            _userId = user.getUserId();
            if (null == form.getPkVal())
                form.setPkVal(_userId);

            _pkVal = NumberUtils.toInt(form.getPkVal().toString());
            boolean isOwnRecord = _pkVal.equals(_userId);
            HttpView view;

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
/*
                view.getViewContext().addContextualRole(ReadPermission.class);
                view.getViewContext().addContextualRole(UpdatePermission.class);
*/
            }
            else
            {
                throw new UnauthorizedException();
            }

            if (isOwnRecord)
                view =  new VBox(new HtmlView("<div>Please enter your contact information.</div></br>"), view);

            return view;
        }

        @Override
        protected QueryForm createQueryForm(ViewContext context)
        {
            QueryForm form = new UserQueryForm();
            form.setViewContext(context);
            PropertyValues propertyValues = context.getBindPropertyValues();
            _deletedAttachments = propertyValues.getPropertyValue("deletedAttachments");
            form.bindParameters(propertyValues);
            return form;
        }

        @Override
        public void validateCommand(QueryUpdateForm form, Errors errors)
        {
            TableInfo table = form.getTable();

            if (table instanceof UsersTable)
            {
                for (Map.Entry<String, Object> entry : form.getTypedColumns().entrySet())
                {
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
                errors.reject(SpringActionController.ERROR_MSG, "User Id cannot be null");
                return;
            }

            User user = UserManager.getUser(NumberUtils.toInt(userId));
            if (null == user)
                throw new NotFoundException("User not found :" + userId);

            // don't let non-site admin edit details of site admin account
            if (user.isInSiteAdminGroup() && !getUser().isInSiteAdminGroup())
                throw new UnauthorizedException("Can not edit details for a Site Admin user");

            String userEmailAddress = user.getEmail();
            String displayName = (String)form.getTypedColumns().get("DisplayName");

            if (displayName != null)
            {
                if (displayName.contains("@"))
                {
                    if (!displayName.equalsIgnoreCase(userEmailAddress))
                        errors.reject(SpringActionController.ERROR_MSG, "The value of the 'Display Name' should not contain '@'.");
                }

                //ensure that display name is unique
                User existingUser = UserManager.getUserByDisplayName(displayName);
                //if there's a user with this display name and it's not the user currently being edited
                if (existingUser != null && !existingUser.equals(user))
                {
                    errors.reject(SpringActionController.ERROR_MSG, "The value of the 'Display Name' field conflicts with another value in the database. Please enter a different value");
                }
            }

            Timestamp expirationDate = (Timestamp) form.getTypedColumns().get("ExpirationDate");
            if (expirationDate != null)
            {
                if ((new Date()).compareTo(new Date(expirationDate.getTime())) > 0)
                    errors.reject(SpringActionController.ERROR_MSG, "Expiration Date cannot be in the past.");
            }

            // validate the original size of the avatar image
            SpringAttachmentFile file = getAvatarFileFromFileMap();
            if (file != null)
            {
                try (InputStream is = file.openInputStream())
                {
                    BufferedImage image = ImageIO.read(is);
                    float desiredSize = ThumbnailService.ImageType.Large.getHeight();

                    if (image == null)
                    {
                        errors.reject(SpringActionController.ERROR_MSG, "Avatar file must be an image file.");
                    }
                    else if (image.getHeight() < desiredSize || image.getWidth() < desiredSize)
                    {
                        errors.reject(SpringActionController.ERROR_MSG, "Avatar file must have a height and width of at least " + desiredSize + "px.");
                    }
                }
                catch (IOException e)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Unable to open avatar file.");
                }
            }
        }

        public boolean handlePost(QueryUpdateForm form, BindException errors) throws Exception
        {
            User user = getUser();
            _userId = user.getUserId();
            _pkVal = NumberUtils.toInt(form.getPkVal().toString());
            boolean isOwnRecord = _pkVal.equals(_userId);

            Date oldExpirationDate = null;
            User targetUser = UserManager.getUser(_pkVal);
            if (targetUser != null)
                oldExpirationDate = targetUser.getExpirationDate();

            if (user.hasRootPermission(UserManagementPermission.class) || isOwnRecord)
            {
                TableInfo table = form.getTable();
                if (table instanceof UsersTable)
                    ((UsersTable)table).setMustCheckPermissions(false);
                doInsertUpdate(form, errors, false);

                if (0 == errors.getErrorCount())
                {
                    auditExpirationDateChange(oldExpirationDate, form);
                }

                updateAvatarThumbnail();
            }
            return 0 == errors.getErrorCount();
        }

        private void auditExpirationDateChange(Date oldExpirationDate, QueryUpdateForm form)
        {
            Date newExpirationDate = null;
            Timestamp expirationDateTimestamp = (Timestamp) form.getTypedColumns().get("ExpirationDate");
            if (expirationDateTimestamp != null)
                newExpirationDate = new Date(expirationDateTimestamp.getTime());
            User targetUser = UserManager.getUser(_pkVal);
            if (targetUser == null)
                return;
            String currentUserEmail = getUser().getEmail();
            String targetUserEmail = targetUser.getEmail();
            Container c = getContainer();

            StringBuilder message = new StringBuilder(currentUserEmail);

            if (oldExpirationDate == null && newExpirationDate == null)
                return;
            else if (oldExpirationDate == null)
            {
                message.append(" set expiration date for ").append(targetUserEmail).append(" to ").append(DateUtil.formatDateTime(c, newExpirationDate));
            }
            else if (newExpirationDate == null)
            {
                message.append(" removed expiration date for ").append(targetUserEmail).append(" from ").append(DateUtil.formatDateTime(c, oldExpirationDate));
            }
            else if (oldExpirationDate.compareTo(newExpirationDate) != 0)
            {
                message.append(" changed expiration date for ").append(targetUserEmail).append(" from ").append(DateUtil.formatDateTime(c, oldExpirationDate)).append(" to ").append(DateUtil.formatDateTime(c, newExpirationDate));
            }
            else
                return;

            UserManager.UserAuditEvent event = new UserManager.UserAuditEvent(getContainer().getId(), message.toString(), targetUser);
            AuditLogService.get().addEvent(getUser(), event);
        }

        private SpringAttachmentFile getAvatarFileFromFileMap()
        {
            String avatarFieldKey = "quf_" + UserAvatarDisplayColumnFactory.FIELD_KEY;
            if (getFileMap().containsKey(avatarFieldKey) && !getFileMap().get(avatarFieldKey).isEmpty())
            {
                return new SpringAttachmentFile(getFileMap().get(avatarFieldKey));
            }

            return null;
        }

        private void updateAvatarThumbnail() throws IOException
        {
            User user = UserManager.getUser(_pkVal);
            ThumbnailService.ImageType imageType = ThumbnailService.ImageType.Large;
            ThumbnailService svc = ServiceRegistry.get().getService(ThumbnailService.class);

            if (svc != null)
            {
                // check if there is a request to delete the existing avatar
                if (_deletedAttachments != null && UserAvatarDisplayColumnFactory.FIELD_KEY.equalsIgnoreCase(_deletedAttachments.getValue().toString()))
                {
                    svc.deleteThumbnail(new AvatarThumbnailProvider(user), imageType);
                }

                // add any new avatars by using the ThumbnailService to generate and attach to the User's entityid
                SpringAttachmentFile file = getAvatarFileFromFileMap();
                if (file != null)
                {
                    try (InputStream is = file.openInputStream())
                    {
                        ImageStreamThumbnailProvider wrapper = new ImageStreamThumbnailProvider(new AvatarThumbnailProvider(user), is, file.getContentType(), imageType, true);
                        svc.replaceThumbnail(wrapper, imageType, null, getViewContext());
                    }
                }
            }
        }

        @Override
        public ActionURL getSuccessURL(QueryUpdateForm form)
        {
            return form.getReturnActionURL(PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(getContainer(), NumberUtils.toInt(form.getPkVal().toString()), null));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            addUserDetailsNavTrail(root, _pkVal);
            root.addChild("Update");
            return root.addChild(UserManager.getEmailForId(_pkVal));
        }
    }

    private static class UserQueryForm extends QueryForm
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
        public UserSchema getSchema()
        {
            int userId = getUserId();
            boolean checkPermission = mustCheckPermissions(getUser(), userId);

            return new CoreQuerySchema(getViewContext().getUser(), getViewContext().getContainer(), checkPermission);
        }

        private boolean mustCheckPermissions(User user, int userRecordId)
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
            if (null == _accessGroups || _accessGroups.size() == 0)
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
            if (null == _accessGroups || _accessGroups.size() == 0)
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

    @RequiresPermission(AdminPermission.class)
    public class UserAccessAction extends QueryViewAction<UserAccessForm, QueryView>
    {
        private boolean _showNavTrail;
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
                        throw new NotFoundException();
                    }
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    throw new NotFoundException();
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
                return view;
            }

            return new PrintTemplate(getViewContext(), view, getPageConfig());
        }

        @Override
        protected QueryView createQueryView(UserAccessForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
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

        public NavTree appendNavTrail(NavTree root)
        {
            if (_showNavTrail)
            {
                addUserDetailsNavTrail(root, _userId);
                root.addChild("Permissions");
                return root.addChild("User Access Details: " + UserManager.getEmailForId(_userId));
            }
            return null;
        }
    }


    private void addUserDetailsNavTrail(NavTree root, Integer userId)
    {
        Container c = getContainer();
        if (c.isRoot())
        {
            if (getUser().hasRootPermission(UserManagementPermission.class))
                root.addChild("Site Users", new UserUrlsImpl().getSiteUsersURL());
        }
        else
        {
            if (c.hasPermission(getUser(), AdminPermission.class))
                root.addChild("Project Users", new UserUrlsImpl().getProjectUsersURL(c));
        }

        if (null == userId)
            root.addChild("User Details");
        else
            root.addChild("User Details", new UserUrlsImpl().getUserDetailsURL(c, getViewContext().getActionURL()).addParameter("userId", userId));
    }


    @RequiresLogin
    public class DetailsAction extends SimpleViewAction<UserQueryForm>
    {
        private int _detailsUserId;

        public ModelAndView getView(UserQueryForm form, BindException errors) throws Exception
        {
            User user = getUser();
            int userId = user.getUserId();
            _detailsUserId = form.getUserId();
            User detailsUser = UserManager.getUser(_detailsUserId);

            boolean isOwnRecord = (_detailsUserId == userId);

            // Anyone can view their own record; otherwise, make sure current user can view the details of this user
            if (!isOwnRecord)
                authorizeUserAction(_detailsUserId, "view details of", true);

            if (null == detailsUser || detailsUser.isGuest())
                throw new NotFoundException("User does not exist");

            Container c = getContainer();
            boolean isUserManager = user.hasRootPermission(UserManagementPermission.class);
            boolean isProjectAdminOrBetter = isUserManager || isProjectAdmin();

            // don't let a non-site admin manage certain parts of a site-admin's account
            boolean canManageDetailsUser = user.isInSiteAdminGroup() || !detailsUser.isInSiteAdminGroup();

            ValidEmail detailsEmail = null;
            boolean loginExists = false;

            try
            {
                detailsEmail = new ValidEmail(detailsUser.getEmail());
                loginExists = SecurityManager.loginExists(detailsEmail);
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                // Allow display and edit of users with invalid email addresses so they can be fixed, #12276.
            }

            UserSchema schema = form.getSchema();
            if (schema == null)
                throw new NotFoundException(CoreQuerySchema.NAME + " schema");

            // for the root container or if the user is site/app admin, use the site users table
            String userTableName = c.isRoot() || c.hasPermission(user, UserManagementPermission.class) ? CoreQuerySchema.SITE_USERS_TABLE_NAME : CoreQuerySchema.USERS_TABLE_NAME;
            TableInfo table = schema.getTable(userTableName);
            if (table == null)
                throw new NotFoundException(userTableName + " table");
            else if (table instanceof AbstractTableInfo)
            {
                // conditionally remove the email and groups columns only for this view
                if (!SecurityManager.canSeeEmailAddresses(getContainer(), getUser()))
                {
                    ColumnInfo col = table.getColumn(FieldKey.fromParts("Email"));
                    if (col != null)
                        ((AbstractTableInfo)table).removeColumn(col);
                }

                if (!c.hasPermission(user, AdminPermission.class))
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

            // see if any of the SSO auth providers are set to autoRedirect from the login action
            boolean isLoginAutoRedirect = AuthenticationManager.getSSOAuthProviderAutoRedirect() != null;

            if (isOwnRecord && loginExists && !isLoginAutoRedirect)
            {
                ActionButton changePasswordButton = new ActionButton(PageFlowUtil.urlProvider(LoginUrls.class).getChangePasswordURL(c, user, getViewContext().getActionURL(), null), "Change Password");
                changePasswordButton.setActionType(ActionButton.Action.LINK);
                changePasswordButton.addContextualRole(OwnerRole.class);
                bb.add(changePasswordButton);
            }

            if (isUserManager)
            {
                // Always display "Reset/Create Password" button (even for LDAP and OpenSSO users)... except for admin's own record
                if (null != detailsEmail && !isOwnRecord && canManageDetailsUser && !isLoginAutoRedirect)
                {
                    // Allow admins to create a logins entry if it doesn't exist.  Addresses scenario of user logging
                    // in with SSO and later needing to use database authentication.  Also allows site admin to have
                    // an alternate login, in case LDAP server goes down (this happened recently on one of our
                    // production installations).
                    ActionURL resetURL = new ActionURL(SecurityController.AdminResetPasswordAction.class, c);
                    resetURL.addParameter("email", detailsEmail.getEmailAddress());
                    resetURL.addReturnURL(getViewContext().getActionURL());
                    ActionButton reset = new ActionButton(resetURL, loginExists ? "Reset Password" : "Create Password");
                    reset.setActionType(ActionButton.Action.LINK);

                    String message;

                    if (loginExists)
                        message = "You are about to clear the user's current password, send the user a reset password email, and force the user to pick a new password to access the site.";
                    else
                        message = "You are about to send the user a reset password email, letting the user pick a password to access the site.";

                    reset.setScript("return confirm(" + PageFlowUtil.jsString(message) + ");", true);

                    bb.add(reset);
                }

                if (canManageDetailsUser)
                    bb.add(makeChangeEmailButton(c, detailsUser));

                if (!isOwnRecord && canManageDetailsUser)
                {
                    ActionURL deactivateUrl = new ActionURL(detailsUser.isActive() ? DeactivateUsersAction.class : ActivateUsersAction.class, c);
                    deactivateUrl.addParameter("userId", _detailsUserId);
                    deactivateUrl.addParameter("redirUrl", getViewContext().getActionURL().getLocalURIString());
                    bb.add(new ActionButton(detailsUser.isActive() ? "Deactivate" : "Re-Activate", deactivateUrl));

                    ActionURL deleteUrl = new ActionURL(DeleteUsersAction.class, c);
                    deleteUrl.addParameter("userId", _detailsUserId);
                    bb.add(new ActionButton("Delete", deleteUrl));
                }
            }

            if (isProjectAdminOrBetter)
            {
                ActionURL viewPermissionsURL = new UserUrlsImpl().getUserAccessURL(c, _detailsUserId);
                ActionButton viewPermissions = new ActionButton(viewPermissionsURL, "View Permissions");
                viewPermissions.setActionType(ActionButton.Action.LINK);
                bb.add(viewPermissions);
            }

            if (isOwnRecord)
            {
                if (!isUserManager  // site/app admin already had this link added above
                        && loginExists  // only show link to users where LabKey manages the password
                        && AuthenticationManager.isSelfServiceEmailChangesEnabled())
                {
                    bb.add(makeChangeEmailButton(c, detailsUser));
                }

                ActionButton doneButton;
                if (null != form.getReturnUrl())
                {
                    doneButton = new ActionButton("Done", form.getReturnURLHelper());
                    rgn.addHiddenFormField(ActionURL.Param.returnUrl, form.getReturnUrl());
                }
                else
                {
                    Container doneContainer = c.getProject();

                    // Root or no permission means redirect to home, #12947
                    if (null == doneContainer || !doneContainer.hasPermission(user, ReadPermission.class))
                        doneContainer = ContainerManager.getHomeContainer();

                    ActionURL doneURL = doneContainer.getStartURL(user);
                    doneButton = new ActionButton(doneURL, "Go to " + doneContainer.getName());
                    doneButton.setActionType(ActionButton.Action.LINK);
                }

                doneButton.addContextualRole(OwnerRole.class);
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


                    List<FieldKey> columns = new ArrayList<>();

                    columns.add(FieldKey.fromParts(UserAuditProvider.COLUMN_NAME_CREATED));
                    columns.add(FieldKey.fromParts(UserAuditProvider.COLUMN_NAME_CREATED_BY));
                    columns.add(FieldKey.fromParts(UserAuditProvider.COLUMN_NAME_COMMENT));

                    settings.setFieldKeys(columns);
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
            ActionURL changeEmailURL = getChangeEmailAction(c, user);
            changeEmailURL.addParameter("isChangeEmailRequest", true);
            ActionButton changeEmail = new ActionButton(changeEmailURL, "Change Email");
            changeEmail.setActionType(ActionButton.Action.LINK);
            return changeEmail;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(UserManager.getEmailForId(_detailsUserId));
        }
    }

    private static ActionURL getChangeEmailAction(Container c, User user)
    {
        ActionURL url = new ActionURL(ChangeEmailAction.class, c);
        url.addParameter("userId", user.getUserId());

        return url;
    }

    @RequiresLogin @CSRF
    public class ChangeEmailAction extends FormViewAction<UserForm>
    {
        private String _currentEmailFromDatabase;
        private String _requestedEmailFromDatabase;
        private int _urlUserId;
        private boolean _isPasswordPrompt = false;
        private ValidEmail _validRequestedEmail;

        public void validateCommand(UserForm target, Errors errors)
        {
            if(target.getIsChangeEmailRequest())
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
                    catch (ValidEmail.InvalidEmailException e)
                    {
                        errors.reject(ERROR_MSG, "Invalid email address.");
                    }
                }
            }
        }

        public ModelAndView getView(UserForm form, boolean reshow, BindException errors) throws Exception
        {
            boolean isUserManager = getUser().hasRootPermission(UserManagementPermission.class);
            if (!isUserManager)
            {
                if(!AuthenticationManager.isSelfServiceEmailChangesEnabled())  // uh oh, shouldn't be here
                {
                    throw new UnauthorizedException("User attempted to access self-service email change, but this function is disabled.");
                }
                _urlUserId = getUser().getUserId();
            }
            else  // site or app admin, could be another user's ID
            {
                _urlUserId = form.getUserId();
            }

            if(form.getIsFromVerifiedLink())
            {
                validateVerification(form, errors);
                if(errors.getErrorCount() == 0)
                {
                    User user = getUser();
                    int userId = user.getUserId();

                    // don't let non-site admin reset password of site admin
                    User formUser = UserManager.getUser(_urlUserId);
                    if (formUser != null && !user.isInSiteAdminGroup() && formUser.isInSiteAdminGroup())
                        throw new UnauthorizedException("Can not reset password for a Site Admin user");

                    // update email in database
                    UserManager.changeEmail(isUserManager, userId, _currentEmailFromDatabase, _requestedEmailFromDatabase, form.getVerificationToken(), getUser());
                    // post-verification email to old account
                    Container c = getContainer();
                    MimeMessage m = getChangeEmailMessage(_currentEmailFromDatabase, _requestedEmailFromDatabase);
                    MailHelper.send(m, user, c);
                    form.setIsFromVerifiedLink(false);  // will still be true in URL, though, confusingly
                    form.setIsVerified(true);
                    form.setOldEmail(_currentEmailFromDatabase);  // for benefit of the page display, since it has changed
                    form.setRequestedEmail(_requestedEmailFromDatabase);  // for benefit of the page display
                }
            }

            return new JspView<>("/org/labkey/core/user/changeEmail.jsp", form, errors);
        }

        void validateVerification(UserForm target, Errors errors)
        {
            boolean isUserManager = getUser().hasRootPermission(UserManagementPermission.class);

            try
            {
                User loggedInUser = getUser();
                String loggedInUserEmail = loggedInUser.getEmail();
                ValidEmail validUserEmail = new ValidEmail(loggedInUser.getEmail());  // use logged-in email throughout

                String verificationToken = target.getVerificationToken();
                UserManager.VerifyEmail verifyEmail = UserManager.getVerifyEmail(validUserEmail);
                String currentEmailFromDatabase = verifyEmail.getEmail();
                String requestedEmailFromDatabase = verifyEmail.getRequestedEmail();

                boolean isVerified = SecurityManager.verify(validUserEmail, verificationToken);
                LoginController.checkVerificationErrors(isVerified, loggedInUser, validUserEmail, verificationToken, errors);

                if(errors.getErrorCount() == 0)  // verified
                {
                    if(!(currentEmailFromDatabase.equals(loggedInUserEmail)))
                    {
                        errors.reject(ERROR_MSG, "The current user is not the same user that initiated this request.  Please log in with the account you used to make " +
                                "this email change request.");
                    }
                    else
                    {
                        Instant verificationTimeoutInstant = verifyEmail.getVerificationTimeout().toInstant();
                        if (Instant.now().isAfter(verificationTimeoutInstant))
                        {
                            if (!isUserManager)  // don't bother auditing admin password link clicks
                            {
                                UserManager.auditEmailTimeout(loggedInUser.getUserId(), validUserEmail.getEmailAddress(), requestedEmailFromDatabase, verificationToken, getUser());
                            }
                            errors.reject(ERROR_MSG, "This verification link has expired.  Please try to change your email address again.");
                        }
                        else
                        {
                            // everything is good, update database emails and return for update
                            _currentEmailFromDatabase = currentEmailFromDatabase;
                            _requestedEmailFromDatabase = requestedEmailFromDatabase;
                            return;
                        }
                    }
                }
                else
                {
                    if ((verificationToken == null) || (verificationToken.length() != SecurityManager.tempPasswordLength))
                    {
                        if (!isUserManager)  // don't bother auditing admin password link clicks
                        {
                            UserManager.auditBadVerificationToken(loggedInUser.getUserId(), validUserEmail.getEmailAddress(), verifyEmail.getRequestedEmail(), verificationToken, loggedInUser);
                        }
                        errors.reject(ERROR_MSG, "Verification was incorrect.  Make sure you've copied the entire link into your browser's address bar.");  // double error, to better explain to user
                    }
                    else if(!(verificationToken.equals(verifyEmail.getVerification())))
                    {
                        if (!isUserManager)  // don't bother auditing admin password link clicks
                        {
                            UserManager.auditBadVerificationToken(loggedInUser.getUserId(), validUserEmail.getEmailAddress(), verifyEmail.getRequestedEmail(), verificationToken, loggedInUser);
                        }
                        errors.reject(ERROR_MSG, "The current user is not the same user that initiated this request.  Please log in with the account you used to make " +
                                "this email change request.");  // double error, to better explain to user
                    }
                    else  // not sure if/how this can happen
                    {
                        errors.reject(ERROR_MSG, "Unknown verification error.  Make sure you've copied the entire link into your browser's address bar.");
                    }
                }
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(ERROR_MSG, "Invalid current email address.");
            }
        }

        public boolean handlePost(UserForm form, BindException errors) throws Exception
        {
            boolean isUserManager = getUser().hasRootPermission(UserManagementPermission.class);
            User user;
            int userId;

            if (!isUserManager)
            {
                user = getUser();
                userId = user.getUserId();
            }
            else  // admin, so use form user ID, which may be different from admin's
            {
                userId = form.getUserId();
                user = UserManager.getUser(userId);
            }

            if (!isUserManager)  // need to verify email before changing if not site or app admin
            {
                if(!AuthenticationManager.isSelfServiceEmailChangesEnabled())  // uh oh, shouldn't be here
                {
                    throw new UnauthorizedException("User attempted to access self-service email change, but this function is disabled.");
                }

                if(form.getIsChangeEmailRequest())
                {
                    // do nothing, validation happened earlier
                }
                else if(form.getIsPasswordPrompt() || _isPasswordPrompt)
                {
                    _isPasswordPrompt = true; // in case we get an error and need to come back to this action again

                    ValidEmail validRequestedEmail;
                    try
                    {
                        if(form.getRequestedEmail() != null)
                        {
                            validRequestedEmail = new ValidEmail(form.getRequestedEmail());  // validate in case user altered this in the URL parameter
                            _validRequestedEmail = validRequestedEmail;
                        }
                        else
                        {
                            validRequestedEmail = _validRequestedEmail;  // try and get it from internal variable, in case we're re-trying here
                        }
                    }
                    catch (ValidEmail.InvalidEmailException e)
                    {
                        errors.reject(ERROR_MSG, "Invalid requested email address.");
                        return false;
                    }

                    String userEmail = user.getEmail();
                    boolean isAuthenticated = authenticate(userEmail, form.getPassword(), getViewContext().getActionURL().getReturnURL(), errors);
                    if (isAuthenticated)
                    {
                        String verificationToken = SecurityManager.createTempPassword();
                        UserManager.requestEmailChange(userId, userEmail, validRequestedEmail.getEmailAddress(), verificationToken, getUser());
                        // verification email
                        Container c = getContainer();

                        ActionURL verifyLinkUrl = getChangeEmailAction(c, user);  // note that user ID added to URL here will only be used if an admin clicks the link, and the link won't work
                        verifyLinkUrl.addParameter("verificationToken", verificationToken);
                        verifyLinkUrl.addParameter("isFromVerifiedLink", true);

                        SecurityManager.sendEmail(c, user, getRequestEmailMessage(userEmail, validRequestedEmail.getEmailAddress()), validRequestedEmail.getEmailAddress(), verifyLinkUrl);
                    }
                    else
                    {
                        errors.reject(ERROR_MSG, "Incorrect password.");
                        Container c = getContainer();
                        ActionURL passwordPromptRetryUrl = getChangeEmailAction(c, user);
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
                if (user == null)
                    throw new IllegalStateException("Unknown user for change email POST.");

                // don't let non-site admin reset password of site admin
                if (!getUser().isInSiteAdminGroup() && user.isInSiteAdminGroup())
                    throw new UnauthorizedException("Can not reset password for a Site Admin user");

                // use "ADMIN" as verification token for debugging, but should never be checked/used
                UserManager.changeEmail(true, userId, user.getEmail(), form.getRequestedEmail(), "ADMIN", getUser());
            }

            return !errors.hasErrors();
        }

        public ActionURL getSuccessURL(UserForm form)
        {
            boolean isUserManager = getUser().hasRootPermission(UserManagementPermission.class);

            if (!isUserManager)
            {
                User user = getUser();

                if (form.getIsChangeEmailRequest())
                {
                    Container c = getContainer();
                    ActionURL passwordPromptUrl = getChangeEmailAction(c, user);
                    passwordPromptUrl.addParameter("isPasswordPrompt", true);
                    passwordPromptUrl.addParameter("requestedEmail", form.getRequestedEmail());
                    return passwordPromptUrl;
                }
                else if (form.getIsPasswordPrompt())
                {
                    Container c = getContainer();
                    ActionURL verifyRedirectUrl = getChangeEmailAction(c, user);
                    verifyRedirectUrl.addParameter("isVerifyRedirect", true);
                    verifyRedirectUrl.addParameter("requestedEmail", form.getRequestedEmail());
                    return verifyRedirectUrl;
                }
                else  // actually making self-service email change, so redirect to user details page
                {
                    return new UserUrlsImpl().getUserDetailsURL(getContainer(), user.getUserId(), form.getReturnURLHelper());
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
                DbLoginAuthenticationProvider loginProvider = (DbLoginAuthenticationProvider) AuthenticationManager.getProvider("Database");
                return loginProvider.authenticate(email, password, returnUrlHelper).isAuthenticated();
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                String defaultDomain = ValidEmail.getDefaultDomain();
                StringBuilder sb = new StringBuilder();
                sb.append("Please sign in using your full email address, for example: ");
                if (defaultDomain != null && defaultDomain.length() > 0)
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

        public NavTree appendNavTrail(NavTree root)
        {
            addUserDetailsNavTrail(root, _urlUserId);
            return root.addChild("Change Email Address: " + UserManager.getEmailForId(_urlUserId));
        }
    }

    public static class UserForm extends ReturnUrlForm
    {
        private int _userId;
        private String _password;
        private String _oldEmail;
        private String _requestedEmail;
        private String _requestedEmailConfirmation;
        private String _message = null;
        private boolean _isChangeEmailRequest = false;
        private boolean _isPasswordPrompt = false;
        private boolean _isVerifyRedirect = false;
        private boolean _isFromVerifiedLink = false;
        private boolean _isVerified = false;
        private String _verificationToken = null;

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

        public String getOldEmail()
        {
            return _oldEmail;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setOldEmail(String oldEmail)
        {
            _oldEmail = oldEmail;
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

    private static SecurityMessage getRequestEmailMessage(String currentEmailAddress, String requestedEmailAddress) throws Exception
    {
        SecurityMessage sm = new SecurityMessage();
        RequestAddressEmailTemplate et = EmailTemplateService.get().getEmailTemplate(RequestAddressEmailTemplate.class);
        et.setCurrentEmailAddress(currentEmailAddress);
        et.setRequestedEmailAddress(requestedEmailAddress);

        sm.setEmailTemplate(et);
        sm.setType("Request email address");

        return sm;
    }

    public static class RequestAddressEmailTemplate extends SecurityManager.SecurityEmailTemplate
    {
        static final String DEFAULT_SUBJECT =
                "Verification link for ^organizationName^ ^siteShortName^ Web Site email change";
        static final String DEFAULT_BODY =
                "You recently requested a change to the email address associated with your account on the " +
                "^organizationName^ ^siteShortName^ Web Site.  If you did not issue this request, you can ignore this email.\n\n" +
                "To complete the process of changing your account's email address from ^currentEmailAddress^ to ^newEmailAddress^, " +
                "simply click the link below or copy it to your browser's address bar.\n\n" +
                "^verificationURL^\n\n" +
                "This step confirms that you are the owner of the new email account.  After you click the link, you will need " +
                "to use the new email address when logging into the server.";
        String _currentEmailAddress;
        String _requestedEmailAddress;
        List<EmailTemplate.ReplacementParam> _replacements = new ArrayList<>();

        @SuppressWarnings("UnusedDeclaration") // Constructor called via reflection
        public RequestAddressEmailTemplate()
        {
            this("Request email address");
        }

        RequestAddressEmailTemplate(String name)
        {
            super(name);
            setSubject(DEFAULT_SUBJECT);
            setBody(DEFAULT_BODY);
            setDescription("Sent to the user and administrator when a user requests to change their email address.");
            setPriority(1);
            _replacements.add(new ReplacementParam<String>("currentEmailAddress", String.class, "Current email address for the current user"){
                public String getValue(Container c) {return _currentEmailAddress;}
            });
            _replacements.add(new ReplacementParam<String>("newEmailAddress", String.class, "Requested email address for the current user"){
                public String getValue(Container c) {return _requestedEmailAddress;}
            });
            _replacements.addAll(super.getValidReplacements());
        }

        void setCurrentEmailAddress(String currentEmailAddress) { _currentEmailAddress = currentEmailAddress; }
        void setRequestedEmailAddress(String requestedEmailAddress) { _requestedEmailAddress = requestedEmailAddress; }
        @Override
        public List<ReplacementParam> getValidReplacements(){ return _replacements; }
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
                "If you did not request this change, please contact the server administrator immediately.  Otherwise, no further action " +
                "is required, and you may use the new email address when logging into the server going forward.";
        String _oldEmailAddress;
        String _newEmailAddress;
        List<EmailTemplate.ReplacementParam> _replacements = new ArrayList<>();

        @SuppressWarnings("UnusedDeclaration") // Constructor called via reflection
        public ChangeAddressEmailTemplate()
        {
            this("Change email address");
        }

        ChangeAddressEmailTemplate(String name)
        {
            super(name);
            setSubject(DEFAULT_SUBJECT);
            setBody(DEFAULT_BODY);
            setDescription("Sent to the user and administrator when a user has changed their email address.");
            setPriority(1);
            _replacements.add(new ReplacementParam<String>("oldEmailAddress", String.class, "Old email address for the current user"){
                public String getValue(Container c) {return _oldEmailAddress;}
            });
            _replacements.add(new ReplacementParam<String>("newEmailAddress", String.class, "New email address for the current user"){
                public String getValue(Container c) {return _newEmailAddress;}
            });
            _replacements.addAll(super.getValidReplacements());
        }

        void setOldEmailAddress(String oldEmailAddress) { _oldEmailAddress = oldEmailAddress; }
        void setNewEmailAddress(String newEmailAddress) { _newEmailAddress = newEmailAddress; }
        @Override
        public List<ReplacementParam> getValidReplacements(){ return _replacements; }
    }

    /**
     * Wrapper to create required columninfos
     */
    private static class RequiredColumn extends ColumnInfo
    {
        public RequiredColumn(ColumnInfo col)
        {
            super(col, col.getParentTable());
        }

        public boolean isNullable()
        {
            return false;
        }
    }

    public static class GetUsersForm
    {
        private String _group;
        private Integer _groupId;
        private String _name;
        private boolean _allMembers;
        private boolean _active;
        private Permission[] _permissions;

        //Flag indicating inclusion of deactivated user accounts in the result set
        //Since _active in this context seems to mean login activity not account state
        private boolean _includeDeactivatedAccounts;

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

        public void setPermissions(Permission[] permission)
        {
            _permissions = permission;
        }

        public boolean isActive()
        {
            return _active;
        }

        public void setActive(boolean active)
        {
            _active = active;
        }

        public boolean includeDeactivatedAccounts()
        {
            return _includeDeactivatedAccounts;
        }

        public void setIncludeDeactivatedAccounts(boolean includeDisabledUsers)
        {
            _includeDeactivatedAccounts = includeDisabledUsers;
        }
    }

    @RequiresLogin
    @RequiresPermission(ReadPermission.class)
    public class GetUsersAction extends ApiAction<GetUsersForm>
    {
        protected static final String PROP_USER_ID = "userId";
        protected static final String PROP_USER_NAME = "displayName";

        public ApiResponse execute(GetUsersForm form, BindException errors) throws Exception
        {
            Container container = getContainer();
            User currentUser = getUser();

            if (container.isRoot() && !currentUser.hasRootPermission(UserManagementPermission.class))
                throw new UnauthorizedException("Only site/application administrators may see users in the root container!");

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("container", container.getPath());

            Collection<User> users;
            List<Map<String,Object>> userResponseList = new ArrayList<>();

            //if requesting users in a specific group...
            if (null != StringUtils.trimToNull(form.getGroup()) || null != form.getGroupId())
            {
                users = getGroupUsers(form, container, currentUser, response);
            }
            else
            {
                //special-case: if container is root, return all active users
                //else, return all users in the current project
                //we've already checked above that the current user is a system admin
                if (container.isRoot())
                    users = UserManager.getActiveUsers(form.includeDeactivatedAccounts());
                else
                    users = SecurityManager.getProjectUsers(container, form.isAllMembers());
            }

            if (null != users)
            {
                //trim name filter to empty so we are guaranteed a non-null string
                //and conver to lower-case for the compare below
                String nameFilter = StringUtils.trimToEmpty(form.getName()).toLowerCase();

                if (nameFilter.length() > 0)
                    response.put("name", nameFilter);

                boolean includeEmail = SecurityManager.canSeeEmailAddresses(getContainer(), currentUser);
                boolean userHasPermission;

                for (User user : users)
                {
                    // TODO: consider performance here
                    // if permissions passed, then validate the user has all of such permissions
                    if (form.getPermissions() != null)
                    {
                        userHasPermission = true;
                        for (Permission permission : form.getPermissions())
                        {
                            if (permission != null && !container.hasPermission(user, permission.getClass()))
                            {
                                userHasPermission = false;
                                break;
                            }
                        }
                        if (!userHasPermission)
                            continue;
                    }

                    //according to the docs, startsWith will return true even if nameFilter is empty string
                    if (user.getEmail().toLowerCase().startsWith(nameFilter) || user.getDisplayName(null).toLowerCase().startsWith(nameFilter))
                    {
                        Map<String,Object> userInfo = new HashMap<>();
                        userInfo.put(PROP_USER_ID, user.getUserId());

                        //force sanitize of the display name, even for logged-in users
                        userInfo.put(PROP_USER_NAME, user.getDisplayName(currentUser));

                        //include email address, if user is allowed to see them
                        if (includeEmail)
                            userInfo.put("email", user.getEmail());

                        userResponseList.add(userInfo);
                    }
                }
            }

            response.put("users", userResponseList);
            return response;
        }

        private Collection<User> getGroupUsers(GetUsersForm form, Container container, User currentUser, ApiSimpleResponse response)
        {
            Container project = container.getProject();

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
            if (form.isActive())
                userMemberType = MemberType.ACTIVE_USERS;
            else
                userMemberType = MemberType.ACTIVE_AND_INACTIVE_USERS;

            // if the allMembers flag is set, then recurse and if group is users then return all site users
            Collection<User> users;
            if (form.isAllMembers())
                users = SecurityManager.getAllGroupMembers(group, userMemberType, group.isUsers());
            else
                users = SecurityManager.getGroupMembers(group, userMemberType);

            return users;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class GetImpersonationUsersAction extends ApiAction
    {
        @Override
        public ApiResponse execute(Object object, BindException errors) throws Exception
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
                map.put("displayName", user.getEmail() + " (" + user.getDisplayName(currentUser) + ")");
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
        private Integer _userId = null;

        public Integer getUserId()
        {
            return _userId;
        }

        public void setUserId(Integer userId)
        {
            _userId = userId;
        }
    }


    // All three impersonate API actions have the same form
    private abstract class ImpersonateApiAction<FORM> extends MutatingApiAction<FORM>
    {
        protected String getCommandClassMethodName()
        {
            return "impersonate";
        }

        @Override
        public ApiResponse execute(FORM form, BindException errors) throws Exception
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


    @RequiresPermission(AdminPermission.class) @CSRF
    public class ImpersonateUserAction extends ImpersonateApiAction<ImpersonateUserForm>
    {
        @Override
        public @Nullable String impersonate(ImpersonateUserForm form)
        {
            if (getUser().isImpersonated())
                return "Can't impersonate; you're already impersonating";

            Integer userId = form.getUserId();

            if (null == userId)
                return "Must specify a user ID";

            User impersonatedUser = UserManager.getUser(userId);

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
    public class GetImpersonationGroupsAction extends ApiAction
    {
        @Override
        public ApiResponse execute(Object object, BindException errors) throws Exception
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
    @RequiresPermission(AdminPermission.class) @CSRF
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
    public class GetImpersonationRolesAction extends ApiAction
    {
        @Override
        public ApiResponse execute(Object object, BindException errors) throws Exception
        {
            ImpersonationContext context = authorizeImpersonateRoles();
            Set<Role> impersonationRoles = context.isImpersonating() ? context.getContextualRoles(getUser(), getContainer().getPolicy()) : Collections.emptySet();

            ApiSimpleResponse response = new ApiSimpleResponse();
            Collection<Role> roles = RoleImpersonationContextFactory.getValidImpersonationRoles(getContainer());
            Collection<Map<String, Object>> responseRoles = new LinkedList<>();

            for (Role role : roles)
            {
                Map<String, Object> map = new HashMap<>();
                map.put("displayName", role.getName());
                map.put("roleName", role.getUniqueName());
                map.put("hasRead", role.getPermissions().contains(ReadPermission.class));
                map.put("selected", impersonationRoles.contains(role));
                responseRoles.add(map);
            }

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
    @RequiresNoPermission @CSRF
    public class ImpersonateRolesAction extends ImpersonateApiAction<ImpersonateRolesForm>
    {
        @Nullable
        @Override
        public String impersonate(ImpersonateRolesForm form)
        {
            ImpersonationContext context = authorizeImpersonateRoles();
            Set<Role> currentImpersonationRoles = context.isImpersonating() ? context.getContextualRoles(getUser(), getContainer().getPolicy()) : Collections.emptySet();

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

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.isInSiteAdminGroup());

            UserController controller = new UserController();

            // @RequiresPermission(ReadPermission.class)
            assertForReadPermission(user,
                controller.new BeginAction(),
                controller.new GetUsersAction()
            );

            // @RequiresPermission(AdminPermission.class)
            assertForAdminPermission(user,
                controller.new ShowUsersAction(),
                //TODO controller.new ShowUserHistoryAction(),
                //TODO controller.new UserAccessAction(),
                controller.new GetImpersonationUsersAction(),
                controller.new ImpersonateUserAction(),
                controller.new GetImpersonationGroupsAction(),
                controller.new ImpersonateGroupAction()
//                controller.new GetImpersonationRolesAction()   Annotated as "no permission", to allow impersonation adjustments
//                controller.new ImpersonateRolesAction()        Annotated as "no permission", to allow impersonation adjustments
            );

            // @RequiresPermission(UserManagementPermission.class)
            assertForUserManagementPermission(user,
                controller.new DeactivateUsersAction(),
                controller.new ActivateUsersAction(),
                controller.new DeleteUsersAction()
            );

            // @AdminConsoleAction
            assertForAdminPermission(ContainerManager.getRoot(), user,
                controller.new ShowUserPreferencesAction()
            );
        }
    }
}
