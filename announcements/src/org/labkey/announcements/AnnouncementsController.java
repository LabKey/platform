/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
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

package org.labkey.announcements;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.labkey.announcements.EmailNotificationPage.Reason;
import org.labkey.announcements.model.*;
import org.labkey.announcements.model.AnnouncementManager.EmailOption;
import org.labkey.announcements.model.AnnouncementManager.EmailPref;
import org.labkey.api.action.*;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.announcements.DiscussionService.Settings;
import org.labkey.api.attachments.*;
import org.labkey.api.data.*;
import org.labkey.api.jsp.JspLoader;
import org.labkey.api.query.UserIdRenderer;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.*;
import org.labkey.api.util.MailHelper.ViewMessage;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;


/**
 * Shows a set of announcement or bulletin board items with replies.
 * Sends email to subscribers.
 * Properties are stored under the following keys:
 *   user=user,container,Object="Announcements"
 *              key="email":0 (no email), 1 (email all entries), 2 (email responses to messages I've created or replied to)
 */
public class AnnouncementsController extends SpringActionController
{
    private static CommSchema _comm = CommSchema.getInstance();

    private static DefaultActionResolver _actionResolver = new DefaultActionResolver(AnnouncementsController.class,
            SendMessageAction.class);

    public AnnouncementsController() throws Exception
    {
        setActionResolver(_actionResolver);
    }


    private DiscussionService.Settings getSettings()
    {
        return getSettings(getContainer());
    }


    public static DiscussionService.Settings getSettings(Container c)
    {
        try
        {
            return AnnouncementManager.getMessageBoardSettings(c);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);  // Not great... but this method is called from all over (webpart constructors, etc.)
        }
    }


    private Permissions getPermissions()
    {
        return getPermissions(getContainer(), getUser(), getSettings(getContainer()));
    }


    public static Permissions getPermissions(Container c, User user, DiscussionService.Settings settings)
    {
        if (settings.isSecure())
            return new SecureMessageBoardPermissions(c, user, settings);
        else
            return new NormalMessageBoardPermissions(c, user, settings);
    }


    protected ActionURL getActionURL()
    {
        return getViewContext().getActionURL();
    }


    protected HttpServletRequest getRequest()
    {
        return getViewContext().getRequest();
    }


    protected HttpServletResponse getResponse()
    {
        return getViewContext().getResponse();
    }


    public static ActionURL getBeginURL(Container c)
    {
        return new ActionURL(BeginAction.class, c);
    }


    // Anyone with read permission can attempt to view the list.  AnnouncementWebPart will do further permission checking.  For example,
    //   in a secure message board, those without Editor permissions will only see messages when they are on the member list
    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Settings settings = getSettings();
            boolean displayAll = getActionURL().getPageFlow().equalsIgnoreCase("announcements");
            WebPartView v = new AnnouncementWebPart(getContainer(), getActionURL(), getUser(), settings, displayAll);
            v.setFrame(WebPartView.FrameType.DIV);
            getPageConfig().setRssProperties(new RssAction().getURL(), settings.getBoardName());

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(getSettings().getBoardName(), getBeginURL(getContainer()));
        }
    }


    private static ActionURL getListURL(Container c)
    {
        return new ActionURL(ListAction.class, c).addParameter(".lastFilter", "true");
    }


    @RequiresPermission(ACL.PERM_READ)
    public class ListAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            AnnouncementListView view = new AnnouncementListView(getViewContext());
            view.setFrame(WebPartView.FrameType.DIV);
            getPageConfig().setRssProperties(new RssAction().getURL(), getSettings().getBoardName());

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(getSettings().getBoardName() + " List", getListURL(getContainer()));
        }
    }

    public static ActionURL getAdminEmailURL(Container c, URLHelper returnURL)
    {
        ActionURL url = new ActionURL(AdminEmailAction.class, c);
        url.addReturnURL(returnURL);
        return url;
    }

    @RequiresSiteAdmin
    public class AdminEmailAction extends SimpleViewAction<ReturnUrlForm>
    {
        private int realRowIndex = 0;

        public ModelAndView getView(ReturnUrlForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            // Render users only if they have read permissions in this folder.  This helps with admin usability,
            // especially in folders that are part of very large projects.  See #5499. 
            DataRegion rgn = new DataRegion() {
                @Override
                protected void renderTableRow(RenderContext ctx, Writer out, List<DisplayColumn> renderers, int rowIndex) throws SQLException, IOException
                {
                    User user = UserManager.getUser(((Integer)ctx.get("userId")).intValue());

                    if (ctx.getContainer().hasPermission(user, ACL.PERM_READ))
                        super.renderTableRow(ctx, out, renderers, realRowIndex++);  // rowIndex doesn't know anything about filtering  TODO: Change DataRegion to handle this better in 8.2
                }
            };
            rgn.setName("EmailPreferences");
            rgn.setTable(_comm.getTableInfoEmailPrefs());
            rgn.setShowFilters(false);
            rgn.setSortable(false);
            rgn.setShowBorders(true);
            rgn.setShadeAlternatingRows(true);

            ButtonBar bb = new ButtonBar();

            ActionButton bulkEdit = new ActionButton(getBulkEditURL(new URLHelper(form.getReturnUrl())), "Bulk Edit");
            bulkEdit.setActionType(ActionButton.Action.LINK);
            bb.add(bulkEdit);
            rgn.setButtonBar(bb);

            GridView gridView = new GridView(rgn);

            ResultSet rs = null;
            try
            {
                rs = AnnouncementManager.getEmailPrefsResultset(c);
                gridView.setResultSet(rs);
                rgn.setColumns(DataRegion.colInfoFromMetaData(rs.getMetaData()));
            }
            finally
            {
                ResultSetUtil.close(rs);
            }

            DisplayColumn colGroupMembership = new GroupMembershipDisplayColumn(c);
            colGroupMembership.setCaption("Project&nbsp;Member?");
            rgn.addDisplayColumn(colGroupMembership);

            DisplayColumn colFirstName = rgn.getDisplayColumn("FirstName");
            if (colFirstName != null)
                colFirstName.setCaption("First Name");

            DisplayColumn colLastName = rgn.getDisplayColumn("LastName");
            if (colLastName != null)
                colLastName.setCaption("Last Name");

            DisplayColumn colDisplayName = rgn.getDisplayColumn("DisplayName");
            if (colDisplayName != null)
                colDisplayName.setCaption("Display Name");

            DisplayColumn colEmailOption = rgn.getDisplayColumn("EmailOption");
            if (colEmailOption != null)
                colEmailOption.setCaption("Email Option");

            DisplayColumn colLastModifiedByName = rgn.getDisplayColumn("LastModifiedByName");
            if (colLastModifiedByName != null)
                colLastModifiedByName.setCaption("Last Modified By");

            DisplayColumn colUserId = rgn.getDisplayColumn("UserId");
            if (colUserId != null)
                colUserId.setVisible(false);

            DisplayColumn colEmailOptionId = rgn.getDisplayColumn("EmailOptionId");
            if (colEmailOptionId != null)
                colEmailOptionId.setVisible(false);

            DisplayColumn colLastModifiedBy = rgn.getDisplayColumn("LastModifiedBy");
            if (colLastModifiedBy != null)
                colLastModifiedBy.setVisible(false);

            VBox vbox = new VBox();
            vbox.addView(new AnnouncementEmailDefaults(c, new URLHelper(form.getReturnUrl())));
            vbox.addView(gridView);
            vbox.addView(new HtmlView("<br>" + PageFlowUtil.generateButton("Done", form.getReturnUrl())));

            return vbox;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return new BeginAction().appendNavTrail(root).addChild("Admin Email Preferences");
        }
    }


    public ActionURL getBulkEditURL(URLHelper returnURL)
    {
        ActionURL url = new ActionURL(BulkEditAction.class, getContainer());
        url.addReturnURL(returnURL);
        return url;
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class BulkEditAction extends FormViewAction<BulkEditEmailPrefsForm>
    {
        private URLHelper _returnUrl;

        public ActionURL getSuccessURL(BulkEditEmailPrefsForm form)
        {
            try
            {
                return getAdminEmailURL(getContainer(), new URLHelper(form.getReturnUrl()));
            }
            catch (URISyntaxException e)
            {
                return null;
            }
        }

        public ModelAndView getView(BulkEditEmailPrefsForm form, boolean reshow, BindException errors) throws Exception
        {
            Container c = getContainer();

            ResultSet rs = null;
            List<EmailPref> emailPrefList = new ArrayList<EmailPref>();
            try
            {
                rs = AnnouncementManager.getEmailPrefsResultset(c);
                List<User> memberList = SecurityManager.getProjectMembers(c.getProject(), false);

                //get resultset data
                while(rs.next())
                {
                    int userId = rs.getInt("UserId");
                    User user = UserManager.getUser(userId);

                    if (!c.hasPermission(user, ACL.PERM_READ))
                        continue;

                    AnnouncementManager.EmailPref emailPref = new AnnouncementManager.EmailPref();

                    emailPref.setUserId(userId);
                    emailPref.setEmail(rs.getString("Email"));
                    emailPref.setFirstName(StringUtils.trimToEmpty(rs.getString("FirstName")));
                    emailPref.setLastName(StringUtils.trimToEmpty(rs.getString("LastName")));
                    emailPref.setDisplayName(StringUtils.trimToEmpty(rs.getString("DisplayName")));
                    emailPref.setEmailOptionId((Integer) rs.getObject("EmailOptionId"));

                    //specify whether user is member of a project group
                    if (memberList.contains(user))
                        emailPref.setProjectMember(true);

                    emailPrefList.add(emailPref);
                }
            }
            finally
            {
                ResultSetUtil.close(rs);
            }

            _returnUrl = new URLHelper(form.getReturnUrl());  // NavTrail needs this

            return new BulkEditView(c, emailPrefList, _returnUrl);
        }

        public boolean handlePost(BulkEditEmailPrefsForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            int[] userId = form.getUserId();
            int[] emailOptionId = form.getEmailOptionId();

            if (null == userId || null == emailOptionId)
                return true;

            for (int i = 0; i < userId.length; i++)
            {
                User projectUser = UserManager.getUser(userId[i]);
                int currentEmailOption = AnnouncementManager.getUserEmailOption(c, projectUser);

                //has this projectUser's option changed? if so, update
                //creating new record in EmailPrefs table if there isn't one, or deleting if set back to folder default
                if (currentEmailOption != emailOptionId[i])
                {
                    AnnouncementManager.saveEmailPreference(getUser(), c, projectUser, emailOptionId[i]);
                }
            }

            return true;
        }

        public void validateCommand(BulkEditEmailPrefsForm target, Errors errors)
        {
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root)
                             .addChild("Admin Email Preferences", getAdminEmailURL(getContainer(), _returnUrl))
                             .addChild("Bulk Edit");
            return root;
        }
    }


    public static class BulkEditView extends JspView<BulkEditView.BulkEditBean>
    {
        private BulkEditView(Container c, List<EmailPref> emailPrefList, URLHelper returnUrl) throws SQLException
        {
            super("/org/labkey/announcements/bulkEdit.jsp", new BulkEditBean(c, emailPrefList, returnUrl));
            setTitle("Admin Email Preferences");
        }

        public static class BulkEditBean
        {
            public List<EmailPref> emailPrefList;
            public String folderEmailOption;
            public URLHelper returnURL;

            private BulkEditBean(Container c, List<EmailPref> emailPrefList, URLHelper returnURL) throws SQLException
            {
                EmailOption[] emailOptions = AnnouncementManager.getEmailOptions();
                int defaultEmailOptionId = AnnouncementManager.getDefaultEmailOption(c);

                for (EmailOption emailOption : emailOptions)
                {
                    if (defaultEmailOptionId == emailOption.getEmailOptionId())
                    {
                        folderEmailOption = emailOption.getEmailOption();
                        break;
                    }
                }

                this.emailPrefList = emailPrefList;
                this.returnURL = returnURL;
            }
        }
    }


    @RequiresSiteAdmin
    public class SendDailyDigestAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            DailyDigest.sendDailyDigest();

            return new HtmlView("Daily digest sent");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return new BeginAction().appendNavTrail(root).addChild("Send daily digest");
        }
    }


    @RequiresPermission(ACL.PERM_DELETE)
    public class DeleteThreadsAction extends RedirectAction
    {
        public boolean doAction(Object o, BindException errors) throws Exception
        {
            if (!getPermissions().allowDeleteAnyThread())
                HttpView.throwUnauthorized();

            Container c = getContainer();

            Set<String> deleteRows = DataRegionSelection.getSelected(getViewContext(), true);
            if (deleteRows != null)
            {
                for (String deleteRow : deleteRows)
                {
                    int rowId = Integer.parseInt(deleteRow);
                    AnnouncementManager.deleteAnnouncement(c, rowId);
                }
            }

            return true;
        }

        public ActionURL getSuccessURL(Object o)
        {
            return getListURL(getContainer());
        }

        public void validateCommand(Object target, Errors errors)
        {
        }
    }


    public abstract class DeleteMessageAction extends ConfirmAction<AnnouncementDeleteForm>
    {
        protected URLHelper _returnUrl;
        protected URLHelper _cancelUrl;

        public ModelAndView getConfirmView(AnnouncementDeleteForm form, BindException errors) throws Exception
        {
            Permissions perm = getPermissions();

            Announcement ann = null;
            if (null != form.getEntityId())
                ann = AnnouncementManager.getAnnouncement(getContainer(), form.getEntityId(), true);
            if (null == ann)
                ann = AnnouncementManager.getAnnouncement(getContainer(), form.getRowId(), AnnouncementManager.INCLUDE_RESPONSES);

            if (null == ann)
                HttpView.throwNotFound();
            if (!perm.allowDeleteMessage(ann))
                HttpView.throwUnauthorized();

            _returnUrl = new URLHelper(form.getReturnUrl());

            if (null != form.getCancelUrl())
                _cancelUrl = new URLHelper(form.getCancelUrl());
            else
                _cancelUrl = _returnUrl;

            return new ConfirmDeleteView(ann, getWhat(), getSettings(getContainer()));
        }

        public ActionURL getSuccessURL(AnnouncementDeleteForm form)
        {
            throw new IllegalStateException("Shouldn't get here; post handler should have redirected.");
        }

        public boolean handlePost(AnnouncementDeleteForm form, BindException errors) throws Exception
        {
            Permissions perm = getPermissions();
            Container c = getContainer();

            Announcement ann = null;
            if (null != form.getEntityId())
                ann = AnnouncementManager.getAnnouncement(c, form.getEntityId(), true);
            if (null == ann)
                ann = AnnouncementManager.getAnnouncement(c, form.getRowId(), AnnouncementManager.INCLUDE_RESPONSES);

            if (ann == null)
                return throwResponseNotFound();
            if (!perm.allowDeleteMessage(ann))
                HttpView.throwUnauthorized();

            AnnouncementManager.deleteAnnouncement(c, ann.getRowId());

            // Can't use getSuccessURL since this is a URLHelper, not an ActionURL
            HttpView.throwRedirect(form.getReturnUrl());

            return true;
        }

        public void validateCommand(AnnouncementDeleteForm announcementDeleteForm, Errors errors)
        {
        }

        @Override
        public String getConfirmText()
        {
            return "Delete";
        }

        abstract String getWhat();
    }


    public static class ConfirmDeleteView extends JspView<ConfirmDeleteView.DeleteBean>
    {
        public ConfirmDeleteView(Announcement ann, String what, Settings settings)
        {
            super("/org/labkey/announcements/confirmDelete.jsp", new DeleteBean(ann, what, settings));
        }

        public static class DeleteBean
        {
            public String title;
            public String what;
            public String conversationName;

            private DeleteBean(Announcement ann, String what, Settings settings)
            {
                title = ann.getTitle();
                this.what = what;
                conversationName = settings.getConversationName().toLowerCase();
            }
        }
    }


    @RequiresPermission(ACL.PERM_NONE)  // Custom permission checking in base class to handle owner-delete
    public class DeleteThreadAction extends DeleteMessageAction
    {
        String getWhat()
        {
            return "entire";
        }
    }


    public static ActionURL getDeleteResponseURL(Container c, String entityId, URLHelper returnUrl)
    {
        ActionURL url = new ActionURL(DeleteResponseAction.class, c);
        url.addParameter("entityId", entityId);
        url.addReturnURL(returnUrl);

        return url;
    }


    @RequiresPermission(ACL.PERM_NONE)  // Custom permission checking in base class to handle owner-delete
    public class DeleteResponseAction extends DeleteMessageAction
    {
        String getWhat()
        {
            return "response from the";
        }

        public URLHelper getCancelUrl()
        {
            return _returnUrl;
        }
    }


    @RequiresLogin @ActionNames("removeFromMemberList, confirmRemove")
    public class RemoveFromMemberListAction extends ConfirmAction<MemberListRemovalForm>
    {
        public ModelAndView getConfirmView(MemberListRemovalForm form, BindException errors) throws Exception
        {
            Announcement thread = validateAndGetThread(form, errors);

            if (errors.hasErrors())
                return new SimpleErrorView(errors);
            else
                return new RemoveUserView(thread, getUser().getEmail(), getSettings());
        }

        @Override
        public String getConfirmText()
        {
            return "Remove";
        }

        public ActionURL getSuccessURL(MemberListRemovalForm memberListRemovalForm)
        {
            return getBeginURL(getContainer());
        }

        @Override
        public ActionURL getCancelUrl()
        {
            return getBeginURL(getContainer());
        }

        public boolean handlePost(MemberListRemovalForm form, BindException errors) throws Exception
        {
            if (form.getUserId() != getUser().getUserId())
                HttpView.throwUnauthorized();

            // TODO: Make this insert a new message to get history?
            AnnouncementManager.deleteUserFromMemberList(getUser(), form.getMessageId());

            return true;
        }

        public void validateCommand(MemberListRemovalForm form, Errors errors)
        {
            validateAndGetThread(form, errors);
        }

        private Announcement validateAndGetThread(MemberListRemovalForm form, Errors errors)
        {
            User user = getUser();
            Settings settings = getSettings();

            Announcement thread = null;

            try
            {
                thread = AnnouncementManager.getAnnouncement(getContainer(), form.getMessageId(), AnnouncementManager.INCLUDE_MEMBERLIST);
            }
            catch (SQLException e)
            {
                //
            }

            if (form.getUserId() != user.getUserId())
            {
                User removeUser = UserManager.getUser(form.getUserId());

                if (null == removeUser)
                    errors.reject(ERROR_MSG, "User could not be found.");
                else
                    errors.reject(ERROR_MSG, "You need to be logged in as " + removeUser.getEmail() + ".");
            }
            else if (null == thread)
            {
                errors.reject(ERROR_MSG, settings.getConversationName() + " not found.");
            }
            else if (!thread.getMemberList().contains(getUser()))
            {
                errors.reject(ERROR_MSG, "You are not on the member list for this " + settings.getConversationName().toLowerCase() + ".");
            }

            return thread;
        }
    }


    public static class RemoveUserView extends JspView<RemoveUserView.RemoveUserBean>
    {
        public RemoveUserView(Announcement ann, String email, DiscussionService.Settings settings)
        {
            super("/org/labkey/announcements/confirmRemoveUser.jsp", new RemoveUserBean(ann, email, settings));
        }

        public static class RemoveUserBean
        {
            public String title;
            public String email;
            public String conversationName;

            private RemoveUserBean(Announcement ann, String email, Settings settings)
            {
                title = ann.getTitle();
                this.email = email;
                conversationName = settings.getConversationName().toLowerCase();
            }
        }
    }


    private Announcement getAnnouncement(AttachmentForm form) throws SQLException, ServletException
    {
        Announcement ann = AnnouncementManager.getAnnouncement(getContainer(), form.getEntityId(), true);  // Force member list to be selected

        if (null == ann)
            throwThreadNotFound(getContainer());

        return ann;
    }


    public abstract class AttachmentAction extends FormViewAction<AttachmentForm>
    {
        AttachmentAction()
        {
            super(AttachmentForm.class);
        }

        public ModelAndView getView(AttachmentForm form, boolean reshow, BindException errors) throws Exception
        {
            Announcement ann = getAnnouncement(form);
            verifyPermissions(ann);
            getPageConfig().setTemplate(PageConfig.Template.None);
            return getAttachmentView(form, ann);
        }

        public boolean handlePost(AttachmentForm attachmentForm, BindException errors) throws Exception
        {
            return true;
        }

        public ActionURL getSuccessURL(AttachmentForm attachmentForm)
        {
            return null;
        }

        public abstract ModelAndView getAttachmentView(AttachmentForm form, AttachmentParent parent) throws Exception;

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }

        public void validateCommand(AttachmentForm target, Errors errors)
        {
        }

        // Further permissions check (ensure non-editors are on the member list in secure board, handle owner-update, etc.)
        // Most actions require update permission
        protected void verifyPermissions(Announcement ann) throws ServletException
        {
            if (!getPermissions().allowUpdate(ann))
                HttpView.throwUnauthorized();
        }
    }

    @RequiresPermission(ACL.PERM_NONE)    // Permission checking done in verifyPermissions() to handle owner-update, etc.
    public class ShowAddAttachmentAction extends AttachmentAction
    {
        public ModelAndView getAttachmentView(AttachmentForm form, AttachmentParent parent)
        {
            return AttachmentService.get().getAddAttachmentView(getContainer(), parent);
        }
    }

    @RequiresPermission(ACL.PERM_NONE)    // Permission checking done in verifyPermissions() to handle owner-update, etc.
    public class AddAttachmentAction extends AttachmentAction
    {
        public ModelAndView getAttachmentView(AttachmentForm form, AttachmentParent parent) throws Exception
        {
            return AttachmentService.get().add(getUser(), parent, getAttachmentFileList());
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DownloadAction extends AttachmentAction
    {
        public ModelAndView getAttachmentView(final AttachmentForm form, final AttachmentParent parent) throws Exception
        {
            return new HttpView()
            {
                protected void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
                {
                    AttachmentService.get().download(response, parent, form.getName());
                }
            };
        }

        // Override since this action only requires read permission
        @Override
        protected void verifyPermissions(Announcement ann) throws ServletException
        {
            if (!getPermissions().allowRead(ann))
                HttpView.throwUnauthorized();
        }
    }

    @RequiresPermission(ACL.PERM_NONE)    // Permission checking done in verifyPermissions() to handle owner-update, etc.
    public class ShowConfirmDeleteAction extends AttachmentAction
    {
        public ModelAndView getAttachmentView(AttachmentForm form, AttachmentParent parent) throws Exception
        {
            return AttachmentService.get().getConfirmDeleteView(getContainer(), getViewContext().getActionURL(), parent, form.getName());
        }
    }

    @RequiresPermission(ACL.PERM_NONE)    // Permission checking done in verifyPermissions() to handle owner-update, etc.
    public class DeleteAttachmentAction extends AttachmentAction
    {
        public ModelAndView getAttachmentView(AttachmentForm form, AttachmentParent parent) throws Exception
        {
            return AttachmentService.get().delete(getUser(), parent, form.getName());
        }
    }


    public static class MemberListRemovalForm
    {
        private int _userId;
        private int _messageId;

        public int getMessageId()
        {
            return _messageId;
        }

        public void setMessageId(int messageId)
        {
            _messageId = messageId;
        }

        public int getUserId()
        {
            return _userId;
        }

        public void setUserId(int userId)
        {
            _userId = userId;
        }
    }


    private URLHelper getReturnURL() throws URISyntaxException
    {
        String url = StringUtils.trimToNull((String)getViewContext().get("returnUrl"));
        if (null != url)
            return new URLHelper(url);
        return null;
    }

    
    public static ActionURL getCustomizeURL(Container c, URLHelper returnUrl)
    {
        ActionURL url = new ActionURL(CustomizeAction.class, c);
        url.addReturnURL(returnUrl);
        return url;
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class CustomizeAction extends FormViewAction<DiscussionService.Settings>
    {
        public ActionURL getSuccessURL(DiscussionService.Settings form)
        {
            throw new IllegalStateException("Shouldn't get here; post handler should have redirected.");
        }

        public ModelAndView getView(DiscussionService.Settings form, boolean reshow, BindException errors) throws Exception
        {
            CustomizeBean bean = new CustomizeBean();

            bean.settings = getSettings();
            bean.returnURL = getReturnURL();
            bean.assignedToSelect = getAssignedToSelect(getContainer(), bean.settings.getDefaultAssignedTo(), "defaultAssignedTo", getViewContext());

            if (hasEditorPerm(Group.groupGuests))
                bean.securityWarning = "Warning: guests have been granted editor permissions in this folder.  As a result, any anonymous user will be able to view, create, and respond to posts, regardless of the security setting below.  You may want to change permissions in this folder.";
            else if (hasEditorPerm(Group.groupUsers))
                bean.securityWarning = "Warning: all site users have been granted editor permissions in this folder.  As a result, any logged in user will be able to view, create, and respond to posts, regardless of the security setting below.  You may want to change permissions in this folder.";

            return new JspView<CustomizeBean>("/org/labkey/announcements/customize.jsp", bean);
        }

        public boolean handlePost(DiscussionService.Settings form, BindException errors) throws Exception
        {
            AnnouncementManager.saveMessageBoardSettings(getContainer(), form);

            HttpView.throwRedirect(getReturnURL().getLocalURIString());
            return true;
        }

        public void validateCommand(DiscussionService.Settings settings, Errors errors)
        {
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root)
                             .addChild("Customize " + getSettings().getBoardName());

            return root;
        }
    }


    public static class CustomizeBean
    {
        public DiscussionService.Settings settings;
        public URLHelper returnURL;
        public String securityWarning;
        public String assignedToSelect;
    }


    private boolean hasEditorPerm(int groupId) throws ServletException
    {
        Role editorRole = RoleManager.getRole(EditorRole.class);
        Group group = SecurityManager.getGroup(groupId);
        return null != group && getContainer().getPolicy().hasPermissions(group, editorRole.getPermissions());
    }


    @RequiresPermission(ACL.PERM_INSERT)
    public abstract class BaseInsertAction extends FormViewAction<AnnouncementForm>
    {
        private URLHelper _returnURL;
        protected HttpView _attachmentErrorView;

        protected abstract ModelAndView getInsertUpdateView(AnnouncementForm announcementForm, boolean reshow, BindException errors) throws Exception;

        public ModelAndView getView(AnnouncementForm form, boolean reshow, BindException errors) throws Exception
        {
            if (null != _attachmentErrorView)
            {
                getPageConfig().setTemplate(PageConfig.Template.Dialog);
                return _attachmentErrorView;
            }

            return getInsertUpdateView(form, reshow, errors);
        }

        public void validateCommand(AnnouncementForm form, Errors errors)
        {
            form.validate(errors);
        }

        public boolean handlePost(AnnouncementForm form, BindException errors) throws Exception
        {
            Permissions perm = getPermissions();

            if (!perm.allowInsert())
                HttpView.throwUnauthorized();

            User u = getUser();
            Container c = getContainer();

            List<AttachmentFile> files = getAttachmentFileList();

            Announcement insert = form.getBean();
            if (null == insert.getParent() || 0 == insert.getParent().length())
                insert.setParent(form.getParentId());

            if (!isNote() && getSettings().hasMemberList() && null == form.getMemberList())
                insert.setMemberList(Collections.<User>emptyList());  // Force member list to get deleted, bug #2484
            else
                insert.setMemberList(form.getMemberList());  // TODO: Do this in validate()?

            try
            {
                AnnouncementManager.insertAnnouncement(c, u, insert, files);
            }
            catch (AttachmentService.DuplicateFilenameException e)
            {
                errors.reject(ERROR_MSG, "Your changes have been saved, but some attachments had duplicate names:");

                for (String error: e.getErrors())
                    errors.reject(ERROR_MSG, error);
            }

            // Don't send email for notes.  For messages, send email if there's body text or an attachment.
            if (!isNote() && (null != insert.getBody() || !insert.getAttachments().isEmpty()))
            {
                String rendererTypeName = (String) form.get("rendererType");
                WikiRendererType currentRendererType = (null == rendererTypeName ? null : WikiRendererType.valueOf(rendererTypeName));
                if(null == currentRendererType)
                {
                    WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
                    if(null != wikiService)
                        currentRendererType = wikiService.getDefaultMessageRendererType();
                }
                sendNotificationEmails(insert, currentRendererType);
            }

            URLHelper returnURL = form.getReturnUrl();

            // Null in insert/update message case, since we want to redirect to thread view anchoring to new post
            if (null == returnURL)
            {
                Announcement thread = insert;
                if (null != insert.getParent())
                    thread = AnnouncementManager.getAnnouncement(getContainer(), insert.getParent(), true);

                if (form.isFromDiscussion() && null != thread.getDiscussionSrcIdentifier())
                {
                    returnURL = DiscussionServiceImpl.fromSaved(thread.getDiscussionSrcURL());
                    returnURL.addParameter("discussion.id", "" + thread.getRowId());
                    returnURL.addParameter("_anchor", "discussionArea");               // TODO: insert.getRowId() instead? -- target just inserted response
                }
                else
                {
                    String threadId = thread.getEntityId();
                    returnURL = getThreadURL(c, threadId, insert.getRowId());
                }
            }

            _attachmentErrorView = AttachmentService.get().getErrorView(files, errors, returnURL);
            _returnURL = returnURL;

            boolean success = (null == _attachmentErrorView);

            // Can't use getSuccessURL since this is a URLHelper, not an ActionURL
            if (success)
                HttpView.throwRedirect(_returnURL.getLocalURIString());

            return false;
        }

        public ActionURL getSuccessURL(AnnouncementForm announcementForm)
        {
            throw new IllegalStateException("Shouldn't get here; post handler should have redirected.");
        }


        protected boolean isNote()
        {
            return false;
        }
    }


    public static ActionURL getInsertURL(Container c)
    {
        return new ActionURL(InsertAction.class, c);
    }


    @RequiresPermission(ACL.PERM_INSERT)
    public class InsertAction extends BaseInsertAction
    {
        public void validateCommand(AnnouncementForm form, Errors errors)
        {
            super.validateCommand(form, errors);

            if (form.isFromDiscussion() && !form.allowMultipleDiscussions())
            {
                if (DiscussionService.get().hasDiscussions(getContainer(), form.getBean().getDiscussionSrcIdentifier()))
                    errors.reject(ERROR_MSG, "Can't post a new discussion -- a discussion already exists and multiple discussions are not allowed");
            }
        }

        @Override
        public ModelAndView getInsertUpdateView(AnnouncementForm form, boolean reshow, BindException errors) throws Exception
        {
            Container c = getContainer();
            DiscussionService.Settings settings = getSettings(c);
            Permissions perm = getPermissions(c, getUser(), settings);

            if (!perm.allowInsert())
                HttpView.throwUnauthorized();

            InsertMessageView insertView = new InsertMessageView(form, "New " + settings.getConversationName(), errors, reshow, form.getReturnUrl(), false, true);

            getPageConfig().setFocusId("title");

            return insertView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root).addChild("New " + getSettings().getConversationName());
            return root;
        }
    }


    public static ActionURL getRespondURL(Container c)
    {
        return new ActionURL(RespondAction.class, c);
    }


    @RequiresPermission(ACL.PERM_INSERT)
    public class RespondAction extends BaseInsertAction
    {
        private Announcement _parent;

        public ModelAndView getInsertUpdateView(AnnouncementForm form, boolean reshow, BindException errors) throws Exception
        {
            Permissions perm = getPermissions();
            Announcement parent = null;
            Container c = getContainer();

            if (null != form.getParentId())
                parent = AnnouncementManager.getAnnouncement(c, form.getParentId(), true);

            if (null == parent)
            {
                throwThreadNotFound(c);
                return null;
            }

            if (!perm.allowResponse(parent))
                HttpView.throwUnauthorized();

            ThreadView threadView = new ThreadView(c, getActionURL(), parent, perm);
            threadView.setFrame(WebPartView.FrameType.DIV);

            HttpView respondView = new RespondView(c, parent, form, form.getReturnUrl(), errors, reshow, false);

            getPageConfig().setFocusId("body");
            _parent = parent;

            return new VBox(threadView, respondView);
        }


        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root)
                             .addChild(_parent.getTitle(), "thread.view?rowId=" + _parent.getRowId())
                             .addChild("Respond to " + getSettings().getConversationName());
            return root;
        }
    }


    // Different action to prevent validation and add a different NavTrail
    @RequiresPermission(ACL.PERM_INSERT)
    public class InsertNoteAction extends InsertAction
    {
        @Override
        public void validateCommand(AnnouncementForm target, Errors errors)
        {
            // Do no validation
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("New Note");
            return root;
        }

        @Override
        protected boolean isNote()
        {
            return true;
        }
    }


    private static String getStatusSelect(DiscussionService.Settings settings, String currentValue)
    {
        List<String> options = Arrays.asList(settings.getStatusOptions().split(";"));

        StringBuilder sb = new StringBuilder(options.size() * 30);
        sb.append("    <select name=\"status\">\n");

        for (String word : options)
        {
            sb.append("      <option");

            if (word.equals(currentValue))
                sb.append(" selected");

            sb.append(">");
            sb.append(PageFlowUtil.filter(word));
            sb.append("</option>\n");
        }
        sb.append("    </select>");

        return sb.toString();
    }


    // AssignedTo == null => assigned to no one.
    private static String getAssignedToSelect(Container c, Integer assignedTo, String name, final ViewContext context)
    {
        List<User> possibleAssignedTo;

        try
        {
            Set<Class<? extends Permission>> perms = new TreeSet<Class<? extends Permission>>();
            perms.add(InsertPermission.class);
            possibleAssignedTo = SecurityManager.getUsersWithPermissions(c, perms);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        Collections.sort(possibleAssignedTo, new Comparator<User>()
        {
            public int compare(User u1, User u2)
            {
                return u1.getDisplayName(context).compareToIgnoreCase(u2.getDisplayName(context));
            }
        });

        // TODO: Should merge all this with IssuesManager.getAssignedToList()
        StringBuilder select = new StringBuilder("    <select name=\"" + name + "\">\n");
        select.append("      <option value=\"\"");
        select.append(null == assignedTo ? " selected" : "");
        select.append("></option>\n");

        for (User user : possibleAssignedTo)
        {
            select.append("      <option value=").append(user.getUserId());

            if (assignedTo != null && assignedTo.intValue() == user.getUserId())
                select.append(" selected");

            select.append(">");
            select.append(user.getDisplayName(context));
            select.append("</option>\n");
        }

        select.append("    </select>");

        return select.toString();
    }


    private static ActionURL getCompleteUserURL(Container c)
    {
        return new ActionURL(CompleteUserAction.class, c);
    }


    @RequiresPermission(ACL.PERM_INSERT)
    public class CompleteUserAction extends AjaxCompletionAction<AjaxCompletionForm>
    {
        public List<AjaxCompletion> getCompletions(AjaxCompletionForm form, BindException errors) throws Exception
        {
            // Limit member list lookup to those with read permissions in this container.
            Set<Class<? extends Permission>> perms = new TreeSet<Class<? extends Permission>>();
            perms.add(ReadPermission.class);
            List<User> completionUsers = SecurityManager.getUsersWithPermissions(getContainer(), perms);
            return UserManager.getAjaxCompletions(form.getPrefix(), completionUsers.toArray(new User[completionUsers.size()]), getViewContext());
        }
    }


    // TODO: Move to /view and use/extend in other controllers
    public static class AjaxCompletionForm
    {
        String _prefix;

        public String getPrefix()
        {
            return _prefix;
        }

        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }
    }


    private static String getMemberListTextArea(User user, Container c, Announcement ann, String emailList)
    {
        String completeUserUrl = getCompleteUserURL(c).getLocalURIString();

        StringBuilder sb = new StringBuilder();
        sb.append("<script type=\"text/javascript\">LABKEY.requiresScript('completion.js');</script>");
        sb.append("<textarea name=\"emailList\" id=\"emailList\" cols=\"30\" rows=\"5\"" );
        sb.append(" onKeyDown=\"return ctrlKeyCheck(event);\"");
        sb.append(" onBlur=\"hideCompletionDiv();\"");
        sb.append(" autocomplete=\"off\"");
        sb.append(" onKeyUp=\"return handleChange(this, event, '");
        sb.append(completeUserUrl);
        sb.append("prefix=');\"");
        sb.append(">");

        if (emailList != null)
        {
            sb.append(emailList);
        }
        else if (null != ann)
        {
            List<User> users = ann.getMemberList();
            sb.append(StringUtils.join(users.iterator(), "\n"));
        }
        else if (!user.isGuest())
        {
            sb.append(user.getEmail());
        }

        sb.append("</textarea>");

        return sb.toString();
    }


    private static ActionURL getInsertURL(Container c, ActionURL returnURL)
    {
        return new ActionURL(InsertAction.class, c).addReturnURL(returnURL);
    }


    public abstract static class BaseInsertView extends JspView<BaseInsertView.InsertBean>
    {
        public BaseInsertView(String page, InsertBean bean, AnnouncementForm form, URLHelper cancelURL, String title, BindException errors, Announcement latestPost, boolean reshow, boolean fromDiscussion)
        {
            super(page, bean, errors);
            setTitle(title);
            Container c = getViewContext().getContainer();

            // In reshow case we leave all form values as is so user can correct the errors.
            WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
            WikiRendererType currentRendererType;
            Integer assignedTo;

            DiscussionService.Settings settings = getSettings(c);

            if (reshow)
            {
                String rendererTypeName = (String) form.get("rendererType");

                if (null == rendererTypeName && null != wikiService)
                    currentRendererType = wikiService.getDefaultMessageRendererType();
                else
                    currentRendererType = WikiRendererType.valueOf(rendererTypeName);

                Announcement ann = form.getBean();
                assignedTo = ann.getAssignedTo();
            }
            else if (null == latestPost)
            {
                // New thread... set base defaults
                Calendar cal = new GregorianCalendar();
                cal.setTime(new Date());
                cal.add(Calendar.MONTH, 1);

                String expires = DateUtil.formatDate(cal.getTime());
                form.set("expires", expires);
                currentRendererType = null != wikiService ? wikiService.getDefaultMessageRendererType() : null;
                assignedTo = settings.getDefaultAssignedTo();
            }
            else
            {
                // Response... set values to match most recent properties on this thread
                assert null == form.get("title");
                assert null == form.get("expires");

                form.set("title", latestPost.getTitle());
                form.set("status", latestPost.getStatus());
                form.setTypedValue("expires", DateUtil.formatDate(latestPost.getExpires()));

                assignedTo = latestPost.getAssignedTo();
                currentRendererType = WikiRendererType.valueOf(latestPost.getRendererType());
            }

            bean.assignedToSelect = getAssignedToSelect(c, assignedTo, "assignedTo", getViewContext());
            bean.settings = settings;
            bean.statusSelect = getStatusSelect(settings, (String)form.get("status"));
            bean.memberList = getMemberListTextArea(form.getUser(), c, latestPost, (String)(reshow ? form.get("emailList") : null));
            bean.currentRendererType = currentRendererType;
            bean.renderers = WikiRendererType.values();
            bean.form = form;
            bean.cancelURL = cancelURL;
            bean.fromDiscussion = fromDiscussion;
        }

        public static class InsertBean
        {
            public boolean allowBroadcast = false;
            public DiscussionService.Settings settings;
            public String assignedToSelect;
            public String statusSelect;
            public String memberList;
            public WikiRendererType[] renderers;
            public WikiRendererType currentRendererType;
            public AnnouncementForm form;
            public URLHelper cancelURL;
            public Announcement parentAnnouncement;   // Used by RespondView only... move to subclass?
            public boolean fromDiscussion;
            public boolean allowMultipleDiscussions = true;
        }
    }


    public static class InsertMessageView extends BaseInsertView
    {
        public InsertMessageView(AnnouncementForm form, String title, BindException errors, boolean reshow, URLHelper cancelURL, boolean fromDiscussion, boolean allowMultipleDiscussions)
        {
            super("/org/labkey/announcements/insert.jsp", new InsertBean(), form, cancelURL, title, errors, null, reshow, fromDiscussion);

            InsertBean bean = getModelBean();
            bean.allowBroadcast = !bean.settings.isSecure() && form.getUser().isAdministrator();
            bean.allowMultipleDiscussions = allowMultipleDiscussions;
        }
    }


    public static class RespondView extends BaseInsertView
    {
        public RespondView(Container c, Announcement parent, AnnouncementForm form, URLHelper cancelURL, BindException errors, boolean reshow, boolean fromDiscussion)
        {
            super("/org/labkey/announcements/respond.jsp", new InsertBean(), form, cancelURL, "Response", errors, AnnouncementManager.getLatestPost(c, parent), reshow, fromDiscussion);

            getModelBean().parentAnnouncement = parent;
        }

        public RespondView(Container c, Announcement parent, URLHelper cancelURL, boolean fromDiscussion)
        {
            this(c, parent, new AnnouncementForm(), cancelURL, null, false, fromDiscussion);
        }
    }


    public static ActionURL getUpdateURL(Container c, String threadId, URLHelper returnUrl)
    {
        ActionURL url = new ActionURL(UpdateAction.class, c);
        url.addParameter("entityId", threadId);
        url.addReturnURL(returnUrl);
        return url;
    }


    @RequiresPermission(ACL.PERM_NONE)   // Custom permission checking below to handle owner-update
    public class UpdateAction extends FormViewAction<AnnouncementForm>
    {
        private Announcement _ann;

        public ActionURL getSuccessURL(AnnouncementForm form)
        {
            throw new IllegalStateException("Shouldn't get here; post handler should have redirected.");
        }

        public ModelAndView getView(AnnouncementForm form, boolean reshow, BindException errors) throws Exception
        {
            Announcement ann = form.selectAnnouncement();
            if (null == ann)
                HttpView.throwNotFound();

            if (!getPermissions().allowUpdate(ann))
                HttpView.throwUnauthorized();

            _ann = ann;

            return new AnnouncementUpdateView(form, ann, errors);
        }

        public boolean handlePost(AnnouncementForm form, BindException errors) throws Exception
        {
            Announcement ann = form.selectAnnouncement();

            if (!getPermissions().allowUpdate(ann))
                HttpView.throwUnauthorized();

            Container c = getContainer();
            Announcement update = form.getBean();

            // TODO: What is this checking for?
            if (!c.getId().equals(update.getContainerId()))
                HttpView.throwUnauthorized();

            AnnouncementManager.updateAnnouncement(getUser(), update);

            // Needs to support non-ActionURL (e.g., an HTML page using the client API with embedded discussion webpart)
            // so we can't use getSuccessURL()
            HttpView.throwRedirect(form.getReturnUrl().getLocalURIString());

            return true;
        }

        public void validateCommand(AnnouncementForm form, Errors errors)
        {
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root)
                             .addChild(_ann.getTitle(), "thread.view?rowId=" + _ann.getRowId())
                             .addChild("Respond to " + getSettings().getConversationName());
            return root;
        }
    }


    public static ActionURL getThreadURL(Container c, String threadId, int rowId)
    {
        ActionURL url = new ActionURL(ThreadAction.class, c);
        url.addParameter("entityId", threadId);
        url.addParameter("_anchor", rowId);
        return url;
    }


    @RequiresPermission(ACL.PERM_READ)
    public class ThreadAction extends SimpleViewAction<AnnouncementForm>
    {
        private String _title;

        public ModelAndView getView(AnnouncementForm form, BindException errors) throws Exception
        {
            ThreadView threadView = new ThreadView(form, getContainer(), getActionURL(), getPermissions(), isPrint());
            threadView.setFrame(WebPartView.FrameType.DIV);

            Announcement ann = threadView.getAnnouncement();
            _title = ann != null ? ann.getTitle() : "Error";

            String anchor = getActionURL().getParameter("_anchor");

            if (null != anchor)
                getPageConfig().setAnchor("row:" + anchor);

            return threadView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root).addChild(_title, getActionURL());
            return root;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class RssAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();

            // getFilter performs further permission checking on secure board (e.g., non-Editors only see threads where they're on the member list)
            SimpleFilter filter = getFilter(getSettings(), getPermissions(), true);

            // TODO: This only grabs announcements... add responses too?
            Pair<Announcement[], Boolean> pair = AnnouncementManager.getAnnouncements(c, filter, getSettings().getSort(), 100);

            ActionURL url = getThreadURL(c, "", 0).deleteParameters().addParameter("rowId", null);

            WebPartView v = new RssView(pair.first, url.getURIString());

//            v.addObject("homePageUrl", ActionURL.getBaseServerURL(request));

            getResponse().setContentType("text/xml");
            getPageConfig().setTemplate(PageConfig.Template.None);

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }

        public ActionURL getURL()
        {
            return new ActionURL(RssAction.class, getContainer());
        }
    }


    public static class RssView extends JspView<RssView.RssBean>
    {
        private RssView(Announcement[] announcements, String url)
        {
            super("/org/labkey/announcements/rss.jsp", new RssBean(announcements, url));
            setFrame(WebPartView.FrameType.NONE);
        }

        public static class RssBean
        {
            public Announcement[] announcements;
            public String url;

            private RssBean(Announcement[] announcements, String url)
            {
                this.announcements = announcements;
                this.url = url;
            }
        }
    }


    @RequiresSiteAdmin
    public class PurgeAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            int rows = ContainerUtil.purgeTable(_comm.getTableInfoAnnouncements(), null);
            return new HtmlView("deleted " + rows + " pages<br>");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static ActionURL getEmailPreferencesURL(Container c, URLHelper srcUrl)
    {
        return new ActionURL(EmailPreferencesAction.class, c).addParameter("srcUrl", srcUrl.getLocalURIString());
    }


    @RequiresLogin
    public class EmailPreferencesAction extends FormViewAction<EmailOptionsForm>
    {
        private String _message = null;

        public ActionURL getSuccessURL(EmailOptionsForm form)
        {
            return null;  // Reshow the page with success message
        }

        public ModelAndView getView(EmailOptionsForm form, boolean reshow, BindException errors) throws Exception
        {
            Container c = getContainer();

            User user = getUser();
            List<User> projectMembers = SecurityManager.getProjectMembers(c, false);

            int emailOption = AnnouncementManager.getUserEmailOption(c, user);
            if (emailOption == AnnouncementManager.EMAIL_PREFERENCE_DEFAULT)
            {
                if (projectMembers.contains(user))
                    emailOption = AnnouncementManager.getDefaultEmailOption(c);
                else
                    emailOption = AnnouncementManager.EMAIL_PREFERENCE_NONE;
            }

            form.setEmailOption(emailOption);

            JspView view = new JspView("/org/labkey/announcements/emailPreferences.jsp");
            view.setFrame(WebPartView.FrameType.DIV);
            EmailPreferencesPage page = (EmailPreferencesPage)view.getPage();
            view.setTitle("Email Preferences");

            Settings settings = getSettings();
            page.emailPreference = form.getEmailPreference();
            page.notificationType = form.getNotificationType();
            page.srcURL = form.getSrcUrl();
            page.message = _message;
            page.hasMemberList = settings.hasMemberList();
            page.conversationName = settings.getConversationName().toLowerCase();

            return view;
        }

        public boolean handlePost(EmailOptionsForm form, BindException errors) throws Exception
        {
            AnnouncementManager.saveEmailPreference(getUser(), getContainer(), form.getEmailOption());

            _message = "Setting changed successfully.";

            return true;
        }

        public void validateCommand(EmailOptionsForm target, Errors errors)
        {
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root)
                             .addChild("Email Preferences");

            return root;
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class SetDefaultEmailOptionsAction extends RedirectAction<EmailDefaultSettingsForm>
    {
        public boolean doAction(EmailDefaultSettingsForm form, BindException errors) throws Exception
        {
            //save the default settings
            AnnouncementManager.saveDefaultEmailOption(getContainer(), form.getDefaultEmailOption());

            return true;
        }

        public ActionURL getSuccessURL(EmailDefaultSettingsForm form)
        {
            try
            {
                return getAdminEmailURL(getContainer(), new URLHelper(form.getReturnUrl()));
            }
            catch (URISyntaxException e)
            {
                return null;
            }
        }

        public void validateCommand(EmailDefaultSettingsForm target, Errors errors)
        {
        }
    }


    private void sendNotificationEmails(Announcement a, WikiRendererType currentRendererType) throws Exception
    {
        Container c = getContainer();
        DiscussionService.Settings settings = getSettings();

        boolean isResponse = null != a.getParent();
        Announcement parent = a;
        if (isResponse)
            parent = AnnouncementManager.getAnnouncement(c, a.getParent());

        //  See bug #6585 -- thread might have been deleted already
        if (null == parent)
            return;

        String messageId = "<" + a.getEntityId() + "@" + AppProps.getInstance().getDefaultDomain() + ">";
        String references = messageId + " <" + parent.getEntityId() + "@" + AppProps.getInstance().getDefaultDomain() + ">";

        // Email all copies of this message in a background thread
        MailHelper.BulkEmailer emailer = new MailHelper.BulkEmailer();

        if (a.isBroadcast())
        {
            // Allow broadcast only if message board is not secure and user is site administrator
            if (settings.isSecure() || !getUser().isAdministrator())
                HttpView.throwUnauthorized();

            // Get all site users' email addresses
            List<String> emails = UserManager.getUserEmailList();
            ViewMessage m = getMessage(c, settings, null, parent, a, isResponse, null, currentRendererType, Reason.broadcast);
            m.setHeader("References", references);
            emailer.addMessage(emails, m);
        }
        else
        {
            // Send a notification email to everyone on the member list.  This email will include a link that removes the user from the member list.
            IndividualEmailPrefsSelector sel = new IndividualEmailPrefsSelector(c);

            List<User> users = sel.getNotificationUsers(a);

            if (!users.isEmpty())
            {
                List<User> memberList;

                if (settings.hasMemberList() && null != a.getMemberList())
                    memberList = a.getMemberList();
                else
                    memberList = Collections.emptyList();

                for (User user : users)
                {
                    ViewMessage m;
                    Permissions perm = getPermissions(c, user, settings);

                    if (memberList.contains(user))
                    {
                        ActionURL removeMeURL = new ActionURL(RemoveFromMemberListAction.class, c);
                        removeMeURL.addParameter("userId", String.valueOf(user.getUserId()));
                        removeMeURL.addParameter("messageId", String.valueOf(parent.getRowId()));
                        m = getMessage(c, settings, perm, parent, a, isResponse, removeMeURL.getURIString(), currentRendererType, Reason.memberList);
                    }
                    else
                    {
                        ActionURL changeEmailURL = getEmailPreferencesURL(c, getBeginURL(c));
                        m = getMessage(c, settings, perm, parent, a, isResponse, changeEmailURL.getURIString(), currentRendererType, Reason.signedUp);
                    }

                    m.setHeader("References", references);
                    m.setHeader("Message-ID", messageId);
                    emailer.addMessage(user.getEmail(), m);
                }
            }
        }

        emailer.start();
    }


    private ViewMessage getMessage(Container c, DiscussionService.Settings settings, Permissions perm, Announcement parent, Announcement a, boolean isResponse, String removeUrl, WikiRendererType currentRendererType, Reason reason) throws Exception
    {
        ViewMessage m = MailHelper.createMultipartViewMessage(LookAndFeelProperties.getInstance(c).getSystemEmailAddress(), null);
        m.setSubject(StringUtils.trimToEmpty(isResponse ? "RE: " + parent.getTitle() : a.getTitle()));
        HttpServletRequest request = AppProps.getInstance().createMockRequest();

        EmailNotificationPage page = createEmailNotificationTemplate("emailNotificationPlain.jsp", false, c, settings, perm, parent, a, removeUrl, currentRendererType, reason);
        JspView view = new JspView(page);
        view.setFrame(WebPartView.FrameType.NONE);
        m.setTemplateContent(request, view, "text/plain");

        page = createEmailNotificationTemplate("emailNotification.jsp", true, c, settings, perm, parent, a, removeUrl, currentRendererType, reason);
        view = new JspView(page);
        view.setFrame(WebPartView.FrameType.NONE);
        m.setTemplateContent(request, view, "text/html");

        return m;
    }


    private EmailNotificationPage createEmailNotificationTemplate(String templateName, boolean includeBody, Container c, Settings settings, Permissions perm, Announcement parent,
            Announcement a, String removeUrl, WikiRendererType currentRendererType, Reason reason)
    {
        HttpServletRequest request = AppProps.getInstance().createMockRequest();

        EmailNotificationPage page = (EmailNotificationPage) JspLoader.createPage(request, AnnouncementsController.class, templateName);

        page.settings = settings;
        page.threadURL = getThreadURL(c, parent.getEntityId(), a.getRowId()).getURIString();
        page.boardPath = c.getPath();
        ActionURL boardURL = getBeginURL(c);
        page.boardURL = boardURL.getURIString();
        page.removeUrl = removeUrl;
        page.siteURL = ActionURL.getBaseServerURL();
        page.announcement = a;
        page.reason = reason;
        page.includeGroups = (null != perm && perm.includeGroups());  // perm will be null for broadcast since we send a single message to everyone

        // for plain text email messages, we don't ever want to include the body since we can't translate HTML into
        // plain text
        if (includeBody && !settings.isSecure())
        {
            //format email using same renderer chosen for message
            //note that we still send all messages, including plain text, as html-formatted messages; only the inserted body text differs between renderers.
            WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
            if(null != wikiService)
            {
                WikiRenderer w = wikiService.getRenderer(currentRendererType);
                page.body = w.format(a.getBody()).getHtml();
            }
        }
        return page;
    }


    private static boolean throwThreadNotFound(Container c)
    {
        HttpView.throwNotFound("Could not find " + getSettings(c).getConversationName().toLowerCase());
        return false;
    }


    private boolean throwResponseNotFound() throws ServletException
    {
        HttpView.throwNotFound("Could not find response");
        return false;
    }


    public static class BulkEditEmailPrefsForm extends ReturnUrlForm
    {
        private int[] _userId;
        private int[] _emailOptionId;

        public int[] getEmailOptionId()
        {
            return _emailOptionId;
        }

        public void setEmailOptionId(int[] emailOptionId)
        {
            _emailOptionId = emailOptionId;
        }

        public int[] getUserId()
        {
            return _userId;
        }

        public void setUserId(int[] userId)
        {
            _userId = userId;
        }
    }

    public static class AnnouncementDeleteForm extends ReturnUrlForm
    {
        private int _rowId;
        private String _entityId;
        private String _cancelUrl;

        public String getEntityId()
        {
            return _entityId;
        }

        public void setEntityId(String entityId)
        {
            _entityId = entityId;
        }

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public String getCancelUrl()
        {
            return _cancelUrl;
        }

        public void setCancelUrl(String cancelUrl)
        {
            _cancelUrl = cancelUrl;
        }
    }

    public static class AnnouncementForm extends BeanViewForm<Announcement>
    {
        Announcement _selectedAnnouncement = null;
        List<User> _memberList = null;

        public AnnouncementForm()
        {
            super(Announcement.class, null, new String[]{"parentid"});
        }

        public String getParentId()
        {
            return _stringValues.get("parentid");
        }

        public List<User> getMemberList()
        {
            return _memberList;
        }

        public void setMemberList(List<User> memberList)
        {
            _memberList = memberList;
        }

        Announcement selectAnnouncement() throws SQLException
        {
            if (null == _selectedAnnouncement)
            {
                Announcement bean = getBean();
                if (null != bean.getEntityId())
                    _selectedAnnouncement = AnnouncementManager.getAnnouncement(getContainer(), bean.getEntityId(), true);  // Need member list
                if (null == _selectedAnnouncement)
                    _selectedAnnouncement = AnnouncementManager.getAnnouncement(getContainer(), bean.getRowId(), AnnouncementManager.INCLUDE_MEMBERLIST);
            }
            return _selectedAnnouncement;
        }

        public void validate(Errors errors)
        {
            Settings settings = getSettings(getContainer());
            Announcement bean = getBean();

            // Title can never be null.  If title is not editable, it will still be posted in a hidden field.
            if (StringUtils.trimToNull(bean.getTitle()) == null)
                errors.reject(ERROR_MSG, "Title must not be blank.");

            try
            {
                String expires = StringUtils.trimToNull((String) get("expires"));
                if (null != expires)
                    DateUtil.parseDateTime(expires);
            }
            catch (ConversionException x)
            {
                errors.reject(ERROR_MSG, "Expires must be blank or a valid date.");
            }

            String emailList = bean.getEmailList();
            List<User> memberList = Collections.emptyList();

            if (null != emailList)
            {
                String[] rawEmails = emailList.split("\n");
                List<String> invalidEmails = new ArrayList<String>();
                List<ValidEmail> emails = SecurityManager.normalizeEmails(rawEmails, invalidEmails);

                for (String rawEmail : invalidEmails)
                {
                    // Ignore lines of all whitespace, otherwise show an error.
                    if (!"".equals(rawEmail.trim()))
                        errors.reject(ERROR_MSG, rawEmail.trim() + ": Invalid email address");
                }

                memberList = new ArrayList<User>(emails.size());

                for (ValidEmail email : emails)
                {
                    User user = UserManager.getUser(email);

                    if (null == user)
                        errors.reject(ERROR_MSG, email.getEmailAddress() + ": Doesn't exist");
                    else if (!memberList.contains(user))
                        memberList.add(user);
                }

                // New up an announcement to check permissions for the member list
                Announcement ann = new Announcement();
                ann.setMemberList(memberList);

                for (User user : memberList)
                {
                    Permissions perm = getPermissions(getContainer(), user, settings);

                    if (!perm.allowRead(ann))
                        errors.reject(ERROR_MSG, "Can't add " + user.getEmail() + " to the member list: This user doesn't have permission to read the thread.");
                }

                setMemberList(memberList);
            }

            Integer assignedTo = bean.getAssignedTo();

            if (null != assignedTo)
            {
                User assignedToUser = UserManager.getUser(assignedTo.intValue());

                if (null == assignedToUser)
                {
                    errors.reject(ERROR_MSG, "Assigned to user " + assignedTo + ": Doesn't exist");
                }
                else
                {
                    Permissions perm = getPermissions(getContainer(), assignedToUser, settings);

                    // New up an announcement to check permissions for the assigned to user
                    Announcement ann = new Announcement();
                    ann.setMemberList(memberList);

                    if (!perm.allowRead(ann))
                        errors.reject(ERROR_MSG, "Can't assign to " + assignedToUser.getEmail() + ": This user doesn't have permission to read the thread.");
                }
            }

            if ("HTML".equals(bean.getRendererType()))
            {
                Collection<String> validateErrors = new LinkedList<String>();
                PageFlowUtil.validateHtml(bean.getBody(), validateErrors, UserManager.mayWriteScript(getUser()));
                for (String err : validateErrors)
                    errors.reject(ERROR_MSG, err);
            }
        }

        public URLHelper getReturnUrl()
        {
            String urlString = StringUtils.trimToNull((String)get("returnUrl"));

            if (null != urlString)
            {
                try
                {
                    return new URLHelper(urlString);
                }
                catch (URISyntaxException e)
                {
                }
            }

            return null;
        }

        public ActionURL getCancelUrl()
        {
            String urlString = StringUtils.trimToNull((String)get("cancelUrl"));

            if (null == urlString)
                return null;
            else
                return new ActionURL(urlString);
        }

        public boolean isFromDiscussion()
        {
            String fromDiscussion = (String)get("fromDiscussion");

            return Boolean.parseBoolean(fromDiscussion);
        }

        public boolean allowMultipleDiscussions()
        {
            String fromDiscussion = (String)get("allowMultipleDiscussions");

            return Boolean.parseBoolean(fromDiscussion);
        }
    }


    public static class EmailDefaultSettingsForm extends ReturnUrlForm
    {
        private int _defaultEmailOption;
        private int _defaultEmailFormat;

        public int getDefaultEmailFormat()
        {
            return _defaultEmailFormat;
        }

        public void setDefaultEmailFormat(int defaultEmailFormat)
        {
            _defaultEmailFormat = defaultEmailFormat;
        }

        public int getDefaultEmailOption()
        {
            return _defaultEmailOption;
        }

        public void setDefaultEmailOption(int defaultEmailOption)
        {
            _defaultEmailOption = defaultEmailOption;
        }
    }

    public static class EmailOptionsForm
    {
        private int _emailPreference = AnnouncementManager.EMAIL_PREFERENCE_NONE;
        private int _notificationType = 0;
        private String _srcUrl = null;

        // Email option is a single int that contains the conversation preference AND a bit for digest vs. individual
        // This method splits them apart
        public void setEmailOption(int emailOption)
        {
            _emailPreference = emailOption & AnnouncementManager.EMAIL_PREFERENCE_MASK;
            _notificationType = emailOption & AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST;
        }

        public int getEmailOption()
        {
            // Form allows "no email" + "daily digest" -- change this to "no email" + "individual" since they are equivalent
            // and we don't want to deal with the former option in the database, with foreign keys, on the admin pages, etc. 
            if (_emailPreference == AnnouncementManager.EMAIL_PREFERENCE_NONE)
                _notificationType = 0;

            return _emailPreference | _notificationType;
        }

        public int getEmailPreference()
        {
            return _emailPreference;
        }

        public void setEmailPreference(int emailPreference)
        {
            _emailPreference = emailPreference;
        }

        public int getNotificationType()
        {
            return _notificationType;
        }

        public void setNotificationType(int notificationType)
        {
            _notificationType = notificationType;
        }

        public String getSrcUrl()
        {
            return _srcUrl;
        }

        public void setSrcUrl(String srcUrl)
        {
            _srcUrl = srcUrl;
        }
    }


    public static class AnnouncementEmailDefaults extends JspView<AnnouncementEmailDefaults.EmailDefaultsBean>
    {
        public AnnouncementEmailDefaults(Container c, URLHelper returnURL) throws SQLException
        {
            super("/org/labkey/announcements/announcementEmailDefaults.jsp", new EmailDefaultsBean(c, returnURL));
        }

        public static class EmailDefaultsBean
        {
            public List<AnnouncementManager.EmailOption> emailOptionsList;
            public int defaultEmailOption;
            public URLHelper returnURL;

            private EmailDefaultsBean(Container c, URLHelper returnURL) throws SQLException
            {
                emailOptionsList = Arrays.asList(AnnouncementManager.getEmailOptions());
                defaultEmailOption = AnnouncementManager.getDefaultEmailOption(c);
                this.returnURL = returnURL;
            }
        }
    }


    public abstract static class LinkBarBean
    {
        public DiscussionService.Settings settings;
        public String filterText;
        public ActionURL customizeURL;
        public ActionURL emailPrefsURL;
        public ActionURL emailManageURL;
        public ActionURL insertURL;
        public boolean includeGroups;

        protected void init(Container c, ActionURL url, User user, DiscussionService.Settings settings, Permissions perm, boolean displayAll, boolean isFiltered, int rowLimit)
        {
            this.settings = settings;
            filterText = getFilterText(settings, displayAll, isFiltered, rowLimit);
            customizeURL = c.hasPermission(user, ACL.PERM_ADMIN) ? getCustomizeURL(c, url) : null;
            emailPrefsURL = user.isGuest() ? null : getEmailPreferencesURL(c, url);
            emailManageURL = c.hasPermission(user, ACL.PERM_ADMIN) ? getAdminEmailURL(c, url) : null;
            insertURL = perm.allowInsert() ? getInsertURL(c, url) : null;
            includeGroups = perm.includeGroups();
        }
    }


    public static class ListLinkBar extends JspView<ListLinkBar.ListBean>
    {
        private ListLinkBar(Container c, ActionURL url, User user, DiscussionService.Settings settings, Permissions perm, boolean displayAll)
        {
            super("/org/labkey/announcements/announcementListLinkBar.jsp", new ListBean(c, url, user, settings, perm, displayAll));
        }

        public static class ListBean extends LinkBarBean
        {
            public ActionURL messagesURL;
            public String urlFilterText;

            private ListBean(Container c, ActionURL url, User user, DiscussionService.Settings settings, Permissions perm, boolean displayAll)
            {
                SimpleFilter urlFilter = new SimpleFilter(url, "Threads");
                boolean isFiltered = !urlFilter.getWhereParamNames().isEmpty();

                init(c, url, user, settings, perm, displayAll, isFiltered, 0);

                messagesURL = getBeginURL(c);
                urlFilterText = isFiltered ? urlFilter.getFilterText(
                    new SimpleFilter.ColumnNameFormatter()
                    {
                        @Override
                        public String format(String columnName)
                        {
                            return super.format(columnName).replaceFirst(".DisplayName", "");
                        }
                    }) : null;
            }
        }
    }


    public static class AnnouncementWebPart extends JspView<AnnouncementWebPart.MessagesBean>
    {
        public AnnouncementWebPart(Container c, ActionURL url, User user, Settings settings, boolean displayAll) throws SQLException, ServletException
        {
            super("/org/labkey/announcements/announcementWebPart.jsp", new MessagesBean(c, url, user, settings, displayAll));
            setTitle(settings.getBoardName());
            setTitleHref(getBeginURL(c));
        }

        public AnnouncementWebPart(ViewContext ctx) throws SQLException, ServletException
        {
            this(ctx.getContainer(), ctx.getActionURL(), ctx.getUser(), getSettings(ctx.getContainer()), false);
        }

        public static class MessagesBean extends LinkBarBean
        {
            public Announcement[] announcements;
            public ActionURL listURL;

            private MessagesBean(Container c, ActionURL url, User user, Settings settings, boolean displayAll)
            {
                Permissions perm = getPermissions(c, user, settings);
                SimpleFilter filter = getFilter(settings, perm, displayAll);
                Pair<Announcement[], Boolean> pair = AnnouncementManager.getAnnouncements(c, filter, settings.getSort(), 100);

                init(c, url, user, settings, perm, displayAll, false, pair.second.booleanValue() ? 100 : 0);
                
                announcements = pair.first;
                listURL = getListURL(c);
            }
        }
    }


    private static SimpleFilter getFilter(DiscussionService.Settings settings, Permissions perm, boolean displayAll)
    {
        // Filter out threads that this user can't read
        SimpleFilter filter = perm.getThreadFilter();

        if (!displayAll)
        {
            if (settings.hasExpires())
                filter.addWhereClause("Expires IS NULL OR Expires > ?", new Object[]{new Date(System.currentTimeMillis() - DateUtils.MILLIS_PER_DAY)});

            if (settings.hasStatus())
                filter.addCondition("Status", "Closed", CompareType.NEQ_OR_NULL);
        }

        return filter;
    }


    private static String getFilterText(DiscussionService.Settings settings, boolean displayAll, boolean isFiltered, int rowLimit)
    {
        StringBuilder sb = new StringBuilder();

        String separator = "";

        if (!displayAll)
        {
            if (settings.hasExpires())
            {
                sb.append("recent");
                separator = ", ";
            }

            if (settings.hasStatus())
            {
                sb.append(separator);
                sb.append("open");
                separator = ", ";
            }
        }

        if (isFiltered)
        {
            sb.append(separator);
            sb.append("filtered");
            separator = ", ";
        }

        if (rowLimit > 0)
        {
            sb.append(separator);
            sb.append("limited to ");
            sb.append(rowLimit);
        }

        if (sb.length() == 0)
            sb.append("all");

        sb.append(" ");
        sb.append(settings.getConversationName().toLowerCase());
        sb.append("s");

        return sb.toString();
    }


    public static class AnnouncementListWebPart extends WebPartView
    {
        private VBox _vbox;

        public AnnouncementListWebPart(ViewContext ctx) throws ServletException
        {
            this(ctx, false);
        }

        private AnnouncementListWebPart(ViewContext ctx, boolean displayAll) throws ServletException
        {
            Container c = ctx.getContainer();
            User user = ctx.getUser();
            ActionURL url = ctx.getActionURL();

            DiscussionService.Settings settings = getSettings(c);
            Permissions perm = getPermissions(c, user, settings);
            DataRegion rgn = getDataRegion(perm, settings);

            setTitle(settings.getBoardName() + " List");
            setTitleHref(getListURL(c));

            TableInfo tinfo = _comm.getTableInfoThreads();
            DisplayColumn title = new DataColumn(tinfo.getColumn("Title"));
            title.setURL(url.relativeUrl("thread", "rowId=${RowId}", "announcements"));
            rgn.addDisplayColumn(title);

            if (settings.hasStatus())
                rgn.addColumn(tinfo.getColumn("Status"));

            if (settings.hasAssignedTo())
            {
                DisplayColumn dc = new UserIdRenderer(tinfo.getColumn("AssignedTo"));
                rgn.addDisplayColumn(dc);
            }

            if (settings.hasExpires())
                rgn.addColumn(tinfo.getColumn("Expires"));

            ColumnInfo colCreatedBy = tinfo.getColumn("CreatedBy"); // TODO: setRenderClass?
            DisplayColumn dc = new UserIdRenderer(colCreatedBy);
            rgn.addDisplayColumn(dc);

            if (perm.includeGroups())
            {
                DisplayColumn createGroups = new GroupColumn(colCreatedBy);
                createGroups.setCaption("Groups");
                rgn.addDisplayColumn(createGroups);
            }

            rgn.addColumn(tinfo.getColumn("Created"));

            ColumnInfo colResponseCreatedBy = tinfo.getColumn("ResponseCreatedBy"); // TODO: setRenderClass?
            DisplayColumn lastDc = new UserIdRenderer(colResponseCreatedBy);
            rgn.addDisplayColumn(lastDc);

            if (perm.includeGroups())
            {
                DisplayColumn responseGroups = new GroupColumn(colResponseCreatedBy);
                responseGroups.setCaption("Groups");
                rgn.addDisplayColumn(responseGroups);
            }

            rgn.addColumn(tinfo.getColumn("ResponseCreated"));

            GridView gridView = new GridView(rgn);
            gridView.setFrame(FrameType.DIV);  // Prevent double title
            gridView.setContainer(c);
            gridView.setSort(settings.getSort());

            SimpleFilter filter = getFilter(settings, perm, displayAll);
            gridView.setFilter(filter);

            _vbox = new VBox(new ListLinkBar(c, url, user, settings, perm, displayAll), gridView);
        }

        protected DataRegion getDataRegion(Permissions perm, DiscussionService.Settings settings)
        {
            DataRegion rgn = new DataRegion();
            rgn.setName("Announcements");
            rgn.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
            rgn.setShadeAlternatingRows(true);
            return rgn;
        }

        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            include(_vbox);
        }

        private static class GroupColumn extends DataColumn
        {
            public GroupColumn(ColumnInfo column)
            {
                super(column);
            }


            @Override
            public String getFormattedValue(RenderContext ctx)
            {
                Integer userId = (Integer)getValue(ctx);

                if (null != userId)
                {
                    User user = UserManager.getUser(userId.intValue());

                    if (null != user)
                        return SecurityManager.getGroupList(ctx.getContainer(), user);
                }

                return "";
            }
        }
    }


    public static class AnnouncementListView extends AnnouncementListWebPart
    {
        public AnnouncementListView(ViewContext ctx) throws ServletException
        {
            super(ctx, true);
        }

        @Override
        protected DataRegion getDataRegion(Permissions perm, DiscussionService.Settings settings)
        {
            DataRegion rgn = super.getDataRegion(perm, settings);

            if (perm.allowDeleteAnyThread())
            {
                ButtonBar bb = new ButtonBar();
                rgn.setShowRecordSelectors(true);

                String conversations = settings.getConversationName().toLowerCase() + "s";
                ActionButton delete = new ActionButton("deleteThreads.post", "Delete");
                delete.setActionType(ActionButton.Action.GET);
                delete.setDisplayPermission(ACL.PERM_DELETE);
                delete.setRequiresSelection(true, "Are you sure you want to delete these " + conversations + "?");
                bb.add(delete);

                rgn.setButtonBar(bb);
            }
            else
                rgn.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);

            return rgn;
        }
    }


    public static class ThreadViewBean
    {
        public Announcement announcement;
        public String message = "";
        public Permissions perm = null;
        public boolean isResponse = false;
        public DiscussionService.Settings settings;
        public ActionURL messagesURL;
        public ActionURL listURL;
        public URLHelper printURL;
        public URLHelper currentURL;
        public boolean print = false;
        public boolean includeGroups;
    }


    public static class ThreadView extends JspView<ThreadViewBean>
    {
        private ThreadView()
        {
            super("/org/labkey/announcements/announcementThread.jsp", new ThreadViewBean());
        }

        public ThreadView(Container c, URLHelper currentURL, User user, String rowId, String entityId) throws ServletException
        {
            this();
            init(c, findThread(c, rowId, entityId), currentURL, getPermissions(c, user, getSettings(c)), false, false);
        }

        public ThreadView(Container c, ActionURL url, Announcement ann, Permissions perm) throws ServletException
        {
            this();
            init(c, ann, url, perm, true, false);
        }
        
        public ThreadView(AnnouncementForm form, Container c, ActionURL url, Permissions perm, boolean print)
                throws ServletException
        {
            this();
            Announcement ann = findThread(c, (String)form.get("rowId"), (String)form.get("entityId"));
            init(c, ann, url, perm, false, print);
        }

        protected void init(Container c, Announcement ann, URLHelper currentURL, Permissions perm, boolean isResponse, boolean print)
                throws ServletException
        {
            if (null == c || !perm.allowRead(ann))
                HttpView.throwUnauthorized();

            if (ann instanceof AnnouncementManager.BareAnnouncement)
                throw new IllegalArgumentException("can't use getBareAnnoucements() with this view");

            ThreadViewBean bean = getModelBean();
            bean.announcement = ann;
            bean.currentURL = currentURL;
            bean.settings = getSettings(c);
            bean.message = null;
            bean.perm = perm;
            bean.isResponse = isResponse;
            bean.messagesURL = getBeginURL(c);  // TODO: Used as returnURL after delete thread... should be messages or list, as appropriate
            bean.listURL = getListURL(c);
            bean.printURL = null == currentURL ? null : currentURL.clone().replaceParameter("_print", "1");
            bean.print = print;
            bean.includeGroups = perm.includeGroups();

            setTitle("View " + bean.settings.getConversationName());
        }

        public Announcement getAnnouncement()
        {
            return getModelBean().announcement;
        }
    }


    private static Announcement findThread(Container c, String rowIdVal, String entityId)
    {
        int rowId = 0;
        if (rowIdVal != null)
        {
            try
            {
                rowId = Integer.parseInt(rowIdVal);
            }
            catch(NumberFormatException e)
            {
                throwThreadNotFound(c);
            }
        }

        try
        {
            if (0 != rowId)
                return AnnouncementManager.getAnnouncement(c, rowId, AnnouncementManager.INCLUDE_ATTACHMENTS + AnnouncementManager.INCLUDE_RESPONSES + AnnouncementManager.INCLUDE_MEMBERLIST);
            else if (null == entityId)
                throwThreadNotFound(c);

            return AnnouncementManager.getAnnouncement(c, entityId, true);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    
    public static class AnnouncementUpdateView extends JspView<AnnouncementUpdateView.UpdateBean>
    {
        public AnnouncementUpdateView(AnnouncementForm form, Announcement ann, BindException errors)
        {
            super("/org/labkey/announcements/update.jsp", null, errors);
            setModelBean(new UpdateBean(form, ann));

            if (ann.getParent() == null)
            {
                setTitle("Edit Message");
            }
            else
            {
                setTitle("Edit Response");
            }
        }

        public class UpdateBean
        {
            public Announcement ann;
            public Settings settings;
            public String assignedToSelect;
            public String statusSelect;
            public String memberList;
            public WikiRendererType[] renderers;
            public WikiRendererType currentRendererType;
            public DownloadURL deleteURL;
            public ActionURL addAttachmentURL;
            public URLHelper returnURL;

            private UpdateBean(AnnouncementForm form, Announcement ann)
            {
                Container c = form.getContainer();
                String reshowEmailList = (String)form.get("emailList");
                DownloadURL attachmentURL = new DownloadURL(ShowConfirmDeleteAction.class, c, ann.getEntityId(), null);

                this.ann = ann;
                settings = getSettings(c);
                currentRendererType = WikiRendererType.valueOf(ann.getRendererType());
                renderers = WikiRendererType.values();
                memberList = getMemberListTextArea(form.getUser(), c, ann, null != reshowEmailList ? reshowEmailList : null);
                deleteURL = attachmentURL;
                addAttachmentURL = attachmentURL.clone().setAction(ShowAddAttachmentAction.class);
                statusSelect = getStatusSelect(settings, ann.getStatus());
                assignedToSelect = getAssignedToSelect(c, ann.getAssignedTo(), "assignedTo", getViewContext());
                returnURL = form.getReturnUrl();// getThreadUrl(c, ann.getEntityId(), ann.getRowId());  // TODO: Use URL to object instead
            }
        }
    }

    public static class GroupMembershipDisplayColumn extends SimpleDisplayColumn
    {
        private List<User> _memberList;

        public GroupMembershipDisplayColumn(Container c)
        {
            super();
            _memberList = SecurityManager.getProjectMembers(c.getProject(), false);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Map row = ctx.getRow();

            Container c = (Container) ctx.get("container");
            //resultset query represents union between two queries, so not all rows include container value
            if (c == null)
                out.write("Yes");
            else
            {
                int userId = ((Integer)row.get("UserId")).intValue();

                if (_memberList.contains(UserManager.getUser(userId)))
                    out.write("Yes");
                else
                    out.write("No");
            }
        }
    }
}
