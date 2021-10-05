/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.announcements.model.AnnouncementDigestProvider;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.announcements.model.AnnouncementModel;
import org.labkey.announcements.model.DailyDigestEmailPrefsSelector;
import org.labkey.announcements.model.DiscussionServiceImpl;
import org.labkey.announcements.model.IndividualEmailPrefsSelector;
import org.labkey.announcements.model.InsertMessagePermission;
import org.labkey.announcements.model.Permissions;
import org.labkey.announcements.query.AnnouncementSchema;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.announcements.DiscussionService.Settings;
import org.labkey.api.announcements.DiscussionService.StatusOption;
import org.labkey.api.announcements.EmailOption;
import org.labkey.api.announcements.api.AnnouncementService;
import org.labkey.api.announcements.api.DiscussionSrcTypeProvider;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.BaseDownloadAction;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.BeanViewForm;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.message.digest.DailyMessageDigest;
import org.labkey.api.message.settings.AbstractConfigTypeProvider;
import org.labkey.api.message.settings.MessageConfigService.NotificationOption;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.query.UserIdRenderer;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.Group;
import org.labkey.api.security.RequiresAnyOf;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.SecurityLogger;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.element.Option.OptionBuilder;
import org.labkey.api.util.element.Select.SelectBuilder;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.DataView;
import org.labkey.api.view.GridView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewForm;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.wiki.WikiRendererType;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.labkey.announcements.model.AnnouncementManager.DEFAULT_MESSAGE_RENDERER_TYPE;

/**
 * Shows a set of announcementModels or bulletin board items with replies.
 * Sends email to subscribers.
 * Properties are stored under the following keys:
 *   user=user,container,Object="Announcements"
 *              key="email":0 (no email), 1 (email all entries), 2 (email responses to messages I've created or replied to)
 */
@Marshal(Marshaller.Jackson)
public class AnnouncementsController extends SpringActionController
{
    private static final CommSchema _comm = CommSchema.getInstance();
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(AnnouncementsController.class, SendMessageAction.class);

    public AnnouncementsController()
    {
        setActionResolver(_actionResolver);
    }


    private Settings getSettings()
    {
        return getSettings(getContainer());
    }


    public static Settings getSettings(Container c)
    {
        return AnnouncementManager.getMessageBoardSettings(c);
    }


    private Permissions getPermissions()
    {
        return getPermissions(getContainer(), getUser(), getSettings(getContainer()));
    }


    public static Permissions getPermissions(Container c, User user, Settings settings)
    {
        return AnnouncementManager.getPermissions(c, user, settings);
    }


    protected ActionURL getActionURL()
    {
        return getViewContext().getActionURL();
    }


    protected HttpServletResponse getResponse()
    {
        return getViewContext().getResponse();
    }


    public static ActionURL getBeginURL(Container c)
    {
        return new ActionURL(BeginAction.class, c);
    }


    public static AnnouncementModel copyEditableProps(AnnouncementModel target, AnnouncementModel source, boolean isInsert)
    {
        if (source.getApproved() != null) target.setApproved(source.getApproved());
        if (source.getAssignedTo() != null) target.setAssignedTo(source.getAssignedTo());
        if (source.getBody() != null) target.setBody(source.getBody());
        if (source.getExpires() != null) target.setExpires(source.getExpires());
        if (source.getRendererType() != null) target.setRendererType(source.getRendererType());
        if (source.getStatus() != null) target.setStatus(source.getStatus());
        if (source.getTitle() != null) target.setTitle(source.getTitle());

        if (isInsert)
        {
            if (source.getDiscussionSrcIdentifier() != null) target.setDiscussionSrcIdentifier(source.getDiscussionSrcIdentifier());
            if (source.getDiscussionSrcEntityType() != null) target.setDiscussionSrcEntityType(source.getDiscussionSrcEntityType());
            if (source.getParent() != null) target.setParent(source.getParent());
        }

        return target;
    }


    // Anyone with read permission can attempt to view the list. AnnouncementWebPart will do further permission checking. For example,
    //   in a secure message board, those without Editor permissions will only see messages when they are on the member list
    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        // Invoked via reflection
        @SuppressWarnings("UnusedDeclaration")
        public BeginAction()
        {
        }

        // Called directly by other actions
        public BeginAction(ViewContext ctx)
        {
            setViewContext(ctx);
        }

        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            Settings settings = getSettings();
            boolean displayAll = getActionURL().getController().equalsIgnoreCase("announcements");
            AnnouncementWebPart v = new AnnouncementWebPart(getContainer(), getActionURL(), getUser(), settings, displayAll, false);
            v.getModelBean().isPrint = isPrint();
            if (isPrint())
                v.setFrame(WebPartView.FrameType.NONE);
            else
                v.setFrame(WebPartView.FrameType.PORTAL);
            v.setShowTitle(false);
            getPageConfig().setRssProperties(new RssAction(getViewContext()).getURL(), settings.getBoardName());

            return v;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild(getSettings().getBoardName(), getBeginURL(getContainer()));
        }
    }


    private static ActionURL getListURL(Container c)
    {
        return new ActionURL(ListAction.class, c).addParameter(DataRegion.LAST_FILTER_PARAM, "true");
    }


    @RequiresPermission(ReadPermission.class)
    public class ListAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            AnnouncementListView view = new AnnouncementListView(getViewContext());
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setShowTitle(false);
            getPageConfig().setRssProperties(new RssAction(getViewContext()).getURL(), getSettings().getBoardName());

            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild(getSettings().getBoardName() + " List", getListURL(getContainer()));
        }
    }

    public static ActionURL getAdminEmailURL(Container c, @Nullable URLHelper returnURL)
    {
        ActionURL url = urlProvider(AdminUrls.class).getNotificationsURL(c);
        if (returnURL != null)
            url.addReturnURL(returnURL);

        return url;
    }

    @RequiresPermission(DeletePermission.class)
    public class DeleteThreadsAction extends FormHandlerAction
    {
        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(Object o, BindException errors)
        {
            if (!getPermissions().allowDeleteAnyThread())
            {
                throw new UnauthorizedException();
            }

            Container c = getContainer();

            Set<Integer> deleteRows = DataRegionSelection.getSelectedIntegers(getViewContext(), true);

            for (Integer rowId : deleteRows)
                AnnouncementManager.deleteAnnouncement(c, rowId);

            return true;
        }

        @Override
        public ActionURL getSuccessURL(Object o)
        {
            return getListURL(getContainer());
        }
    }


    public abstract class DeleteMessageAction extends ConfirmAction<AnnouncementDeleteForm>
    {
        protected URLHelper _cancelUrl;

        @Override
        public ModelAndView getConfirmView(AnnouncementDeleteForm form, BindException errors)
        {
            if (getPageConfig().getTitle() == null)
                setTitle("Delete Message");

            Permissions perm = getPermissions();

            AnnouncementModel ann = null;
            if (null != form.getEntityId())
                ann = AnnouncementManager.getAnnouncement(getContainer(), form.getEntityId());
            if (null == ann)
                ann = AnnouncementManager.getAnnouncement(getContainer(), form.getRowId());

            if (null == ann)
            {
                throw new NotFoundException();
            }
            if (!perm.allowDeleteMessage(ann))
            {
                throw new UnauthorizedException();
            }

            _cancelUrl = form.getCancelActionURL(form.getReturnActionURL(getThreadURL(AnnouncementsController.this.getContainer(), ann.getEntityId(), ann.getRowId())));

            return new ConfirmDeleteView(ann, getWhat(), getSettings(getContainer()));
        }

        @Override
        public @NotNull URLHelper getSuccessURL(AnnouncementDeleteForm form)
        {
            return form.getReturnURLHelper(new ActionURL(BeginAction.class, getContainer()));
        }

        @Override
        public boolean handlePost(AnnouncementDeleteForm form, BindException errors)
        {
            Permissions perm = getPermissions();
            Container c = getContainer();

            AnnouncementModel ann = null;
            if (null != form.getEntityId())
                ann = AnnouncementManager.getAnnouncement(c, form.getEntityId());
            if (null == ann)
                ann = AnnouncementManager.getAnnouncement(c, form.getRowId());

            if (ann == null)
                throw new NotFoundException("Could not find response");
            if (!perm.allowDeleteMessage(ann))
            {
                throw new UnauthorizedException();
            }

            AnnouncementManager.deleteAnnouncement(c, ann.getRowId());

            return true;
        }

        @Override
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
        public ConfirmDeleteView(AnnouncementModel ann, String what, Settings settings)
        {
            super("/org/labkey/announcements/confirmDelete.jsp", new DeleteBean(ann, what, settings));
        }

        public static class DeleteBean
        {
            public String title;
            public String what;
            public String conversationName;

            private DeleteBean(AnnouncementModel ann, String what, Settings settings)
            {
                title = ann.getTitle();
                this.what = what;
                conversationName = settings.getConversationName().toLowerCase();
            }
        }
    }


    @RequiresNoPermission  // Custom permission checking in base class to handle owner-delete
    public class DeleteAction extends DeleteMessageAction
    {
        @Override
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


    @RequiresNoPermission  // Custom permission checking in base class to handle owner-delete
    public class DeleteResponseAction extends DeleteMessageAction
    {
        @Override
        String getWhat()
        {
            return "response from the";
        }

        @Override
        public URLHelper getCancelUrl()
        {
            return _cancelUrl;
        }
    }


    @RequiresLogin @ActionNames("removeFromMemberList, confirmRemove")
    public class RemoveFromMemberListAction extends ConfirmAction<MemberListRemovalForm>
    {
        @Override
        public ModelAndView getConfirmView(MemberListRemovalForm form, BindException errors)
        {
            AnnouncementModel ann = validateAndGetAnnouncement(form, errors);

            if (errors.hasErrors())
                return new SimpleErrorView(errors);
            else
                return new RemoveUserView(ann, getUser().getEmail(), getSettings());
        }

        @Override
        public String getConfirmText()
        {
            return "Remove";
        }

        @Override
        @NotNull
        public ActionURL getSuccessURL(MemberListRemovalForm memberListRemovalForm)
        {
            return getBeginURL(getContainer());
        }

        @Override
        public ActionURL getCancelUrl()
        {
            return getBeginURL(getContainer());
        }

        @Override
        public boolean handlePost(MemberListRemovalForm form, BindException errors)
        {
            if (form.getUserId() != getUser().getUserId())
            {
                throw new UnauthorizedException();
            }

            // TODO: Make this insert a new message to get history?
            AnnouncementManager.deleteUserFromMemberList(getUser(), form.getMessageId());

            return true;
        }

        @Override
        public void validateCommand(MemberListRemovalForm form, Errors errors)
        {
            validateAndGetAnnouncement(form, errors);
        }

        private AnnouncementModel validateAndGetAnnouncement(MemberListRemovalForm form, Errors errors)
        {
            User user = getUser();
            Settings settings = getSettings();

            AnnouncementModel ann = AnnouncementManager.getAnnouncement(getContainer(), form.getMessageId());

            if (form.getUserId() != user.getUserId())
            {
                User removeUser = UserManager.getUser(form.getUserId());

                if (null == removeUser)
                    errors.reject(ERROR_MSG, "User could not be found.");
                else
                    errors.reject(ERROR_MSG, "You need to be logged in as " + removeUser.getEmail() + ".");
            }
            else if (null == ann)
            {
                errors.reject(ERROR_MSG, settings.getConversationName() + " not found.");
            }
            else if (!ann.getMemberListIds().contains(getUser().getUserId()))
            {
                errors.reject(ERROR_MSG, "You are not on the member list for this " + settings.getConversationName().toLowerCase() + ".");
            }

            return ann;
        }
    }


    public static class RemoveUserView extends JspView<RemoveUserView.RemoveUserBean>
    {
        public RemoveUserView(AnnouncementModel ann, String email, Settings settings)
        {
            super("/org/labkey/announcements/confirmRemoveUser.jsp", new RemoveUserBean(ann, email, settings));
        }

        public static class RemoveUserBean
        {
            public String title;
            public String email;
            public String conversationName;

            private RemoveUserBean(AnnouncementModel ann, String email, Settings settings)
            {
                title = ann.getTitle();
                this.email = email;
                conversationName = settings.getConversationName().toLowerCase();
            }
        }
    }


    private AnnouncementModel getAnnouncement(AttachmentForm form)
    {
        AnnouncementModel ann = AnnouncementManager.getAnnouncement(getContainer(), form.getEntityId());  // Force member list to be selected

        if (null == ann)
            throw createThreadNotFoundException(getContainer());

        return ann;
    }


    public static ActionURL getDownloadURL(AnnouncementModel ann, String filename)
    {
        return new ActionURL(DownloadAction.class, ann.lookupContainer())
            .addParameter("entityId", ann.getEntityId())
            .addParameter("name", filename);
    }


    @RequiresPermission(ReadPermission.class)
    public class DownloadAction extends BaseDownloadAction<AttachmentForm>
    {
        protected void verifyPermissions(AnnouncementModel ann)
        {
            if (!getPermissions().allowRead(ann))
            {
                throw new UnauthorizedException();
            }
        }

        @Nullable
        @Override
        public Pair<AttachmentParent, String> getAttachment(AttachmentForm form)
        {
            AnnouncementModel ann = getAnnouncement(form);
            verifyPermissions(ann);

            return new Pair<>(ann.getAttachmentParent(), form.getName());
        }
    }


    // Invoked by discuss.js
    @RequiresNoPermission    // Permission checking done in verifyPermissions() to handle owner-update, etc.
    public class DeleteAttachmentAction extends MutatingApiAction<AttachmentForm>
    {
        // Permissions check (ensure non-editors are on the member list in secure board, handle owner-update, etc.)
        protected void verifyPermissions(AnnouncementModel ann)
        {
            if (!getPermissions().allowUpdate(ann))
            {
                throw new UnauthorizedException();
            }
        }

        @Override
        public Object execute(AttachmentForm form, BindException errors)
        {
            AnnouncementModel ann = getAnnouncement(form);
            verifyPermissions(ann);
            AttachmentService.get().deleteAttachment(ann.getAttachmentParent(), form.getName(), getUser());

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("success", !errors.hasErrors());

            return response;
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

        @SuppressWarnings({"UnusedDeclaration"})
        public void setMessageId(int messageId)
        {
            _messageId = messageId;
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
    }


    public static ActionURL getAdminURL(Container c, @Nullable URLHelper returnUrl)
    {
        ActionURL url = new ActionURL(CustomizeAction.class, c);
        if (returnUrl != null)
            url.addReturnURL(returnUrl);

        return url;
    }


    @RequiresPermission(AdminPermission.class)
    public class CustomizeAction extends FormViewAction<Settings>
    {
        @Override
        public URLHelper getSuccessURL(Settings form)
        {
            return form.getReturnURLHelper();
        }

        @Override
        public ModelAndView getView(Settings form, boolean reshow, BindException errors)
        {
            CustomizeBean bean = new CustomizeBean();

            bean.settings = getSettings();   // TODO: Just use form?
            bean.returnURL = form.getReturnURLHelper();
            bean.assignedToSelect = getAssignedToSelect(getContainer(), bean.settings.getDefaultAssignedTo(), "defaultAssignedTo", getUser());

            if (hasEditorPerm(Group.groupGuests))
                bean.securityWarning = "Warning: guests have been granted editor permissions in this folder.  As a result, any anonymous user will be able to view, create, and respond to posts, regardless of the security setting below.  You may want to change permissions in this folder.";
            else if (hasEditorPerm(Group.groupUsers))
                bean.securityWarning = "Warning: all site users have been granted editor permissions in this folder.  As a result, any logged in user will be able to view, create, and respond to posts, regardless of the security setting below.  You may want to change permissions in this folder.";

            setHelpTopic("adminMessages");
            return new JspView<>("/org/labkey/announcements/customize.jsp", bean);
        }

        @Override
        public boolean handlePost(Settings form, BindException errors)
        {
            AnnouncementManager.saveMessageBoardSettings(getContainer(), form);

            return true;
        }

        @Override
        public void validateCommand(Settings settings, Errors errors)
        {
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).addNavTrail(root);
            root.addChild("Customize " + getSettings().getBoardName());
        }
    }


    public static class CustomizeBean
    {
        public Settings settings;
        public URLHelper returnURL;    // TODO: Settings has a returnUrl
        public String securityWarning;
        public SelectBuilder assignedToSelect;
    }


    private boolean hasEditorPerm(int groupId)
    {
        Role editorRole = RoleManager.getRole(EditorRole.class);
        Group group = SecurityManager.getGroup(groupId);
        return null != group && SecurityManager.hasAllPermissions(null, getContainer().getPolicy(), group, editorRole.getPermissions(), Set.of());
    }


    @RequiresAnyOf({InsertMessagePermission.class, InsertPermission.class})
    public abstract class BaseInsertAction extends FormViewAction<AnnouncementForm>
    {
        private URLHelper _returnURL;
        protected HttpView _attachmentErrorView;

        protected abstract ModelAndView getInsertUpdateView(AnnouncementForm announcementForm, boolean reshow, BindException errors);

        @Override
        public ModelAndView getView(AnnouncementForm form, boolean reshow, BindException errors)
        {
            if (null != _attachmentErrorView)
            {
                getPageConfig().setTemplate(PageConfig.Template.Dialog);
                return _attachmentErrorView;
            }

            return getInsertUpdateView(form, reshow, errors);
        }

        @Override
        public void validateCommand(AnnouncementForm form, Errors errors)
        {
            form.validate(errors);
        }

        @Override
        public boolean handlePost(AnnouncementForm form, BindException errors)
        {
            if (!getPermissions().allowInsert())
            {
                throw new UnauthorizedException();
            }

            User u = getUser();
            Container c = getContainer();

            List<AttachmentFile> files = getAttachmentFileList();

            AnnouncementModel insert = form.getBean();
            if (null == insert.getParent() || 0 == insert.getParent().length())
                insert.setParent(form.getParentId());

            try
            {
                AnnouncementManager.insertAnnouncement(c, u, insert, files);
            }
            catch (RuntimeValidationException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }
            catch (IOException e)
            {
                errors.reject(ERROR_MSG, "Your changes have been saved, though some file attachments were not:");
                errors.reject(ERROR_MSG, e.getMessage() == null ? e.toString() : e.getMessage());
                return false;
            }

            URLHelper returnURL = form.getReturnURLHelper();

            // Null in insert/update message case, since we want to redirect to thread view anchoring to new post
            if (null == returnURL)
            {
                AnnouncementModel thread = insert;
                if (null != insert.getParent())
                    thread = AnnouncementManager.getAnnouncement(getContainer(), insert.getParent());

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
                throw new RedirectException(_returnURL);

            return false;
        }

        @Override
        public ActionURL getSuccessURL(AnnouncementForm announcementForm)
        {
            throw new IllegalStateException("Shouldn't get here; post handler should have redirected.");
        }
    }


    public static ActionURL getInsertURL(Container c)
    {
        return new ActionURL(InsertAction.class, c);
    }


    @RequiresAnyOf({InsertMessagePermission.class, InsertPermission.class})
    public class InsertAction extends BaseInsertAction
    {
        @Override
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
        public ModelAndView getInsertUpdateView(AnnouncementForm form, boolean reshow, BindException errors)
        {
            Container c = getContainer();
            Settings settings = getSettings(c);
            Permissions perm = getPermissions(c, getUser(), settings);

            if (!perm.allowInsert())
            {
                throw new UnauthorizedException();
            }

            InsertMessageView insertView = new InsertMessageView(form, "New " + settings.getConversationName(), errors, reshow, form.getReturnURLHelper(), false, true);
            insertView.setShowTitle(false);

            getPageConfig().setFocusId("title");

            return insertView;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).addNavTrail(root);
            root.addChild("New " + getSettings().getConversationName());
        }
    }


    public static ActionURL getRespondURL(Container c)
    {
        return new ActionURL(RespondAction.class, c);
    }


    @RequiresAnyOf({InsertPermission.class, InsertMessagePermission.class})
    public class RespondAction extends BaseInsertAction
    {
        private AnnouncementModel _parent;

        @Override
        public BindException bindParameters(PropertyValues m) throws Exception
        {
            // issue 16732: check that if a parentId is present, it is a GUID
            if (m.getPropertyValue("parentId") != null && !GUID.isGUID(m.getPropertyValue("parentId").getValue().toString()))
                throw createThreadNotFoundException(getContainer());

            return super.bindParameters(m);
        }

        @Override
        public ModelAndView getInsertUpdateView(AnnouncementForm form, boolean reshow, BindException errors)
        {
            Permissions perm = getPermissions();
            AnnouncementModel parent = null;
            Container c = getContainer();

            if (null != form.getParentId())
                parent = AnnouncementManager.getAnnouncement(c, form.getParentId());

            if (null == parent)
            {
                throw createThreadNotFoundException(c);
            }

            if (!perm.allowResponse(parent))
            {
                throw new UnauthorizedException();
            }

            ThreadView threadView = new ThreadView(c, getActionURL(), parent, perm);
            threadView.setFrame(WebPartView.FrameType.DIV);

            HttpView respondView = new RespondView(c, parent, form, form.getReturnURLHelper(), errors, reshow, false);

            getPageConfig().setFocusId("body");
            _parent = parent;

            return new VBox(threadView, respondView);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).addNavTrail(root);
            if (_parent != null)
            {
                root.addChild(_parent.getTitle(), getThreadURL(getContainer(), _parent.getRowId()));
                root.addChild("Respond to " + getSettings().getConversationName());
            }
         }
    }


    private static SelectBuilder getStatusSelect(String currentValue)
    {
        return new SelectBuilder().name("status").className(null).selected(currentValue)
            .addOptions(Arrays.stream(StatusOption.values()).map(Enum::name));
    }


    // AssignedTo == null => assigned to no one.
    private static SelectBuilder getAssignedToSelect(Container c, Integer assignedTo, String name, final User currentUser)
    {
        Set<Class<? extends Permission>> perms = Collections.singleton(InsertPermission.class);
        List<User> possibleAssignedTo = SecurityManager.getUsersWithPermissions(c, perms);
        possibleAssignedTo.sort(Comparator.comparing(user -> user.getDisplayName(currentUser), String.CASE_INSENSITIVE_ORDER));

        SelectBuilder builder = new SelectBuilder().name(name).className(null)
            .addOption(new OptionBuilder("", "").selected(null == assignedTo));

        return builder.addOptions(possibleAssignedTo.stream()
            .map(user->new OptionBuilder(user.getDisplayName(currentUser), user.getUserId())
                .selected(assignedTo != null && assignedTo == user.getUserId())));
    }


    @RequiresPermission(InsertPermission.class)
    public class CompleteUserAction extends ReadOnlyApiAction<AjaxCompletionForm>
    {
        @Override
        public ApiResponse execute(AjaxCompletionForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            List<JSONObject> completions = new ArrayList<>();
            Set<Class<? extends Permission>> perms = Collections.singleton(ReadPermission.class);
            List<User> completionUsers = SecurityManager.getUsersWithPermissions(getContainer(), perms);

            for (AjaxCompletion completion : UserManager.getAjaxCompletions(completionUsers, getUser(), getContainer()))
                completions.add(completion.toJSON());

            response.put("completions", completions);

            return response;
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


    private static String getMemberList(User user, Container c, AnnouncementModel ann, String memberList)
    {
        StringBuilder sb = new StringBuilder();
        if (memberList != null)
        {
            sb.append(memberList);
        }
        else if (null != ann)
        {
            List<String> users = ann.getMemberListDisplay(c, user);
            sb.append(StringUtils.join(users, "\n"));
        }
        else if (!user.isGuest())
        {
            sb.append(user.getEmail());
        }
        return sb.toString();
    }


    private static ActionURL getInsertURL(Container c, @Nullable ActionURL returnURL)
    {
        ActionURL url = new ActionURL(InsertAction.class, c);
        if (returnURL != null)
            url.addReturnURL(returnURL);

        return url;
    }


    public abstract static class BaseInsertView extends JspView<BaseInsertView.InsertBean>
    {
        public BaseInsertView(String page, InsertBean bean, AnnouncementForm form, URLHelper cancelURL, String title, BindException errors, @Nullable AnnouncementModel latestPost, boolean reshow, boolean fromDiscussion)
        {
            super(page, bean, errors);
            setTitle(title);
            Container c = getViewContext().getContainer();

            // In reshow case we leave all form values as is so user can correct the errors.
            WikiRendererType currentRendererType;
            Integer assignedTo;

            Settings settings = getSettings(c);

            if (reshow)
            {
                String rendererTypeName = (String) form.get("rendererType");

                if (null == rendererTypeName)
                    currentRendererType = DEFAULT_MESSAGE_RENDERER_TYPE;
                else
                    currentRendererType = WikiRendererType.valueOf(rendererTypeName);

                AnnouncementModel ann = form.getBean();
                assignedTo = ann.getAssignedTo();
            }
            else if (null == latestPost)
            {
                // New thread... set base defaults
                Calendar cal = new GregorianCalendar();
                cal.setTime(new Date());
                cal.add(Calendar.MONTH, 1);

                String expires = DateUtil.formatDate(c, cal.getTime());
                form.set("expires", expires);
                currentRendererType = DEFAULT_MESSAGE_RENDERER_TYPE;
                assignedTo = settings.getDefaultAssignedTo();
            }
            else
            {
                // Response... set values to match most recent properties on this thread
                assert null == form.get("title");
                assert null == form.get("expires");

                form.set("title", latestPost.getTitle());
                form.set("status", "Active");  // By default, every new response resets status to active, #35047
                form.setTypedValue("expires", DateUtil.formatDate(c, latestPost.getExpires()));

                assignedTo = latestPost.getAssignedTo();
                currentRendererType = WikiRendererType.valueOf(latestPost.getRendererType());
            }

            bean.assignedToSelect = getAssignedToSelect(c, assignedTo, "assignedTo", getViewContext().getUser());
            bean.settings = settings;
            bean.statusSelect = getStatusSelect(form.get("status"));

            User u = form.getUser() == null ? getViewContext().getUser() : form.getUser();
            bean.memberList = getMemberList(u, c, latestPost, reshow ? form.get("memberList") : null);
            bean.currentRendererType = currentRendererType;
            bean.renderers = WikiRendererType.values();
            bean.form = form;
            bean.cancelURL = cancelURL;
            bean.fromDiscussion = fromDiscussion;

            // If default email option is "all messages" (or "all messages daily digest") then gently warn
            // that a bunch of users are about to be emailed.
            int defaultEmailOption = AnnouncementManager.getDefaultEmailOption(c);

            if ((EmailOption.MESSAGES_ALL.getValue() == defaultEmailOption)
                    || (EmailOption.MESSAGES_ALL_DAILY_DIGEST.getValue() == defaultEmailOption))
            {
                bean.emailUsers = new IndividualEmailPrefsSelector(c).getNotificationUsers(latestPost).size() + new DailyDigestEmailPrefsSelector(c).getNotificationUsers(latestPost).size();
            }
        }

        public static class InsertBean
        {
            public Settings settings;
            public SelectBuilder assignedToSelect;
            public SelectBuilder statusSelect;
            public String memberList;
            public WikiRendererType[] renderers;
            public WikiRendererType currentRendererType;
            public AnnouncementForm form;
            public URLHelper cancelURL;
            public AnnouncementModel parentAnnouncementModel;   // Used by RespondView only... move to subclass?
            public boolean fromDiscussion;
            public boolean allowMultipleDiscussions = true;
            public Integer emailUsers = null;
        }
    }


    public static class InsertMessageView extends BaseInsertView
    {
        public InsertMessageView(AnnouncementForm form, String title, BindException errors, boolean reshow, URLHelper cancelURL, boolean fromDiscussion, boolean allowMultipleDiscussions)
        {
            super("/org/labkey/announcements/insert.jsp", new InsertBean(), form, cancelURL, title, errors, null, reshow, fromDiscussion);

            InsertBean bean = getModelBean();
            bean.allowMultipleDiscussions = allowMultipleDiscussions;
        }
    }


    public static class RespondView extends BaseInsertView
    {
        public RespondView(Container c, AnnouncementModel parent, AnnouncementForm form, URLHelper cancelURL, BindException errors, boolean reshow, boolean fromDiscussion)
        {
            super("/org/labkey/announcements/respond.jsp", new InsertBean(), form, cancelURL, "Response", errors, AnnouncementManager.getLatestPost(c, parent), reshow, fromDiscussion);

            getModelBean().parentAnnouncementModel = parent;
        }

        public RespondView(Container c, AnnouncementModel parent, URLHelper cancelURL, boolean fromDiscussion)
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


    @RequiresNoPermission   // Custom permission checking below to handle owner-update
    public class UpdateAction extends FormViewAction<AnnouncementForm>
    {
        private AnnouncementModel _ann;

        @Override
        public BindException bindParameters(PropertyValues m) throws Exception
        {
            // issue 16731: check that if an entityId is present, it is a GUID
            if (m.getPropertyValue("entityId") != null && !GUID.isGUID(m.getPropertyValue("entityId").getValue().toString()))
                throw new NotFoundException();

            return super.bindParameters(m);
        }

        @Override
        public ActionURL getSuccessURL(AnnouncementForm form)
        {
            throw new IllegalStateException("Shouldn't get here; post handler should have redirected.");
        }

        @Override
        public ModelAndView getView(AnnouncementForm form, boolean reshow, BindException errors)
        {
            AnnouncementModel ann = form.selectAnnouncement();
            if (null == ann)
            {
                throw new NotFoundException();
            }

            if (!getPermissions().allowUpdate(ann))
            {
                throw new UnauthorizedException();
            }

            _ann = ann;

            return new AnnouncementUpdateView(form, ann, errors);
        }

        @Override
        public boolean handlePost(AnnouncementForm form, BindException errors)
        {
            AnnouncementModel ann = form.selectAnnouncement();

            if (null == ann)
            {
                throw new NotFoundException("Announcement");
            }

            if (!getPermissions().allowUpdate(ann))
            {
                throw new UnauthorizedException();
            }

            Container c = getContainer();

            AnnouncementModel update = form.getBean();

            // TODO: What is this checking for?
            if (!c.getId().equals(update.getContainerId()))
            {
                throw new UnauthorizedException();
            }

            try
            {
                AnnouncementManager.updateAnnouncement(getUser(), update, getAttachmentFileList());
            }
            catch (RuntimeValidationException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }
            catch (IOException e)
            {
                errors.reject(ERROR_MSG, "Your changes have been saved, though some file attachments were not:");
                errors.reject(ERROR_MSG, e.getMessage() == null ? e.toString() : e.getMessage());
                form._selectedAnnouncementModel = null; // Force reload of the changes that were saved
                return false;
            }

            // Needs to support non-ActionURL (e.g., an HTML page using the client API with embedded discussion webpart)
            // so we can't use getSuccessURL()
            URLHelper urlHelper = form.getReturnURLHelper();
            if (null != urlHelper)
                throw new RedirectException(urlHelper);
            else
                throw new RedirectException(getThreadURL(getContainer(), ann.getParent(), ann.getRowId()));
        }

        @Override
        public void validateCommand(AnnouncementForm form, Errors errors)
        {
            form.validate(errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).addNavTrail(root);
            root.addChild(_ann.getTitle(), getThreadURL(getContainer(), _ann.getParent(), _ann.getRowId()));
            root.addChild("Edit Response to " + getSettings().getConversationName());
        }
    }

    public static ActionURL getThreadURL(Container c, @Nullable String threadEntityId, int rowId)
    {
        final ActionURL url;

        // threadEntityId is null if called on the parent message with ann.getParent(), which several actions like doing.
        // In this case, just pass the rowId. #41040
        if (null == threadEntityId)
        {
            url = getThreadURL(c, rowId);
        }
        else
        {
            url = new ActionURL(ThreadAction.class, c);
            url.addParameter("entityId", threadEntityId);
            url.addParameter("_anchor", rowId);
        }

        return url;
    }

    // Note: ThreadAction expects that rowId is a top-level message, not a response
    public static ActionURL getThreadURL(Container c, int rowId)
    {
        ActionURL url = new ActionURL(ThreadAction.class, c);
        url.addParameter("rowId", rowId);
        return url;
    }

    public static ActionURL getThreadURL(Container c, User user, AnnouncementModel ann)
    {
        DiscussionSrcTypeProvider typeProvider = AnnouncementService.get().getDiscussionSrcTypeProvider(ann.getDiscussionSrcEntityType());

        if (typeProvider != null)
        {
            ActionURL threadUrl = typeProvider.getThreadURL(c, user, ann.getRowId(), ann.getDiscussionSrcIdentifier());

            if (threadUrl != null)
                return threadUrl;
        }

        return getThreadURL(c, ann.getRowId());
    }

    @RequiresPermission(ReadPermission.class)
    public class ThreadAction extends SimpleViewAction<AnnouncementForm>
    {
        private String _title;

        @Override
        public ThreadView getView(AnnouncementForm form, BindException errors) throws Exception
        {
            ThreadView threadView = new ThreadView(form, getContainer(), getActionURL(), getPermissions(), isPrint());
            threadView.setFrame(WebPartView.FrameType.PORTAL);

            AnnouncementModel ann = threadView.getAnnouncement();
            _title = ann != null ? ann.getTitle() : "Error";

            String anchor = getActionURL().getParameter("_anchor");

            if (null != anchor)
                getPageConfig().setAnchor("row:" + anchor);

            return threadView;
        }

        @Override
        public ModelAndView getPrintView(AnnouncementForm form, BindException errors) throws Exception
        {
            ThreadView tv = getView(form, errors);
            // title is already in the thread view don't need to repeat it
            tv.setFrame(WebPartView.FrameType.NONE);
            tv.getModelBean().print = true;
            return tv;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).addNavTrail(root);
            root.addChild(_title, getActionURL());
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class ThreadBareAction extends ThreadAction
    {
        @Override
        public ThreadView getView(AnnouncementForm form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            ThreadView tv = super.getView(form, errors);
            tv.setFrame(WebPartView.FrameType.NONE);
            tv.getModelBean().embedded = true;
            return tv;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class RssAction extends SimpleViewAction
    {
        // Invoked via reflection
        @SuppressWarnings("UnusedDeclaration")
        public RssAction()
        {
        }

        public RssAction(ViewContext ctx)
        {
            setViewContext(ctx);
        }

        // Support basic auth challenge #8520
        @Override
        public void checkPermissions() throws UnauthorizedException
        {
            setUnauthorizedType(UnauthorizedException.Type.sendBasicAuth);
            super.checkPermissions();
        }

        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            Container c = getContainer();

            // getFilter performs further permission checking on secure board (e.g., non-Editors only see threads where they're on the member list)
            SimpleFilter filter = getFilter(getSettings(), getPermissions(), true);

            // TODO: This only grabs announcementModels... add responses too?
            Pair<Collection<AnnouncementModel>, Boolean> pair = AnnouncementManager.getAnnouncements(c, filter, getSettings().getSort(), 100);

            ActionURL url = new ActionURL(ThreadAction.class, c).addParameter("rowId", null);

            WebPartView v = new RssView(pair.first, url.getURIString());

            getResponse().setContentType("text/xml");
            getPageConfig().setTemplate(PageConfig.Template.None);

            return v;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }

        public ActionURL getURL()
        {
            return new ActionURL(RssAction.class, getContainer());
        }
    }


    public static class RssView extends JspView<RssView.RssBean>
    {
        private RssView(Collection<AnnouncementModel> announcementModels, String url)
        {
            super("/org/labkey/announcements/rss.jsp", new RssBean(announcementModels, url));
            setFrame(WebPartView.FrameType.NOT_HTML);
        }

        public static class RssBean
        {
            public Collection<AnnouncementModel> announcementModels;
            public String url;

            private RssBean(Collection<AnnouncementModel> announcementModels, String url)
            {
                this.announcementModels = announcementModels;
                this.url = url;
            }
        }
    }


    public static ActionURL getEmailPreferencesURL(Container c, @Nullable URLHelper returnUrl, String srcIdentifier)
    {
        ActionURL result = new ActionURL(EmailPreferencesAction.class, c);
        result.addParameter("srcIdentifier", srcIdentifier);
        if (returnUrl != null)
            result.addReturnURL(returnUrl);

        return result;
    }


    @RequiresLogin
    public class EmailPreferencesAction extends FormViewAction<EmailOptionsForm>
    {
        private String _message = null;

        @Override
        public ActionURL getSuccessURL(EmailOptionsForm form)
        {
            return null;  // Reshow the page with success message
        }

        @Override
        public ModelAndView getView(EmailOptionsForm form, boolean reshow, BindException errors)
        {
            Container c = getContainer();

            User user = getUser();
            List<User> projectUsers = SecurityManager.getProjectUsers(c, false);

            int emailOption = getEmailOptionIncludingInherited(c, user, form.getSrcIdentifier());

            form.setEmailOptionsOnPage(emailOption);

            setHelpTopic("createMessage");
            JspView view = new JspView("/org/labkey/announcements/emailPreferences.jsp");
            view.setFrame(WebPartView.FrameType.NONE);
            EmailPreferencesPage page = (EmailPreferencesPage)view.getPage();
            view.setTitle("Email Preferences");
            view.setShowTitle(false);

            Settings settings = getSettings();
            page.emailPreference = form.getEmailPreference();
            page.srcIdentifier = form.getSrcIdentifier();
            page.notificationType = form.getNotificationType();
            if (form.getReturnActionURL() != null)
                page.returnUrl = form.getReturnActionURL();
            page.message = _message;
            page.hasMemberList = settings.hasMemberList();
            page.conversationName = settings.getConversationName().toLowerCase();
            page.isProjectMember = projectUsers.contains(user);

            return view;
        }

        @Override
        public boolean handlePost(EmailOptionsForm form, BindException errors)
        {
            int emailOption = form.getResetFolderDefault() ? -1 : form.getEmailOption(errors);
            AnnouncementManager.saveEmailPreference(getUser(), getContainer(), emailOption, form.getSrcIdentifier());

            _message = "Setting changed successfully.";

            // TODO: display why the user had their preferences reset if it happened, and potentially other errors someday, with code like this
            /*if(errors.hasErrors())
            {
                for (ObjectError error : errors.getAllErrors())
                {
                    _message = _message + " " + error.getDefaultMessage();
                }
            }*/

            return true;
        }

        @Override
        public void validateCommand(EmailOptionsForm target, Errors errors)
        {
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).addNavTrail(root);
            root.addChild("Email Preferences");
        }
    }

    /** Resolve the effective subscription level for the object. If not explicitly set, check for a user subscription
     * at the container level, and then fall back to the container default. */
    private static int getEmailOptionIncludingInherited(Container c, User user, String srcIdentifier)
    {
        int emailOption = AnnouncementManager.getUserEmailOption(c, user, srcIdentifier);
        if (emailOption == EmailOption.NOT_SET.getValue())
        {
            if (!srcIdentifier.equals(c.getId()))
            {
                emailOption = AnnouncementManager.getUserEmailOption(c, user, c.getId());
            }
            if (emailOption == EmailOption.NOT_SET.getValue())
            {
                emailOption = AnnouncementManager.getDefaultEmailOption(c);
            }
        }
        return emailOption;
    }

    @RequiresPermission(AdminPermission.class)
    public class SetEmailDefaultAction extends MutatingApiAction<AbstractConfigTypeProvider.EmailConfigFormImpl>
    {
        @Override
        public ApiResponse execute(AbstractConfigTypeProvider.EmailConfigFormImpl form, BindException errors)
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            StringBuilder message = new StringBuilder("The current default has been updated to: ");

            //save the default settings
            AnnouncementManager.saveDefaultEmailOption(getContainer(), form.getDefaultEmailOption());

            for (NotificationOption option : AnnouncementManager.getEmailOptions())
            {
                if (option.getEmailOptionId() == form.getDefaultEmailOption())
                {
                    message.append(option.getEmailOption());
                    break;
                }
            }
            resp.put("success", true);
            resp.put("message", message.toString());

            return resp;
        }
    }

    // Used for testing announcement daily digest email notifications
    @RequiresSiteAdmin
    public class SendDailyDigestAction extends MutatingApiAction
    {
        @Override
        public Object execute(Object o, BindException errors)
        {
            Thread digestThread = new Thread(() -> {
                // Normally, daily digest stops at previous midnight; override to include all messages through now
                DailyMessageDigest messageDigest = new DailyMessageDigest() {
                    @Override
                    protected Date getEndRange(Date current, Date last)
                    {
                        return current;
                    }
                };

                // Just announcements
                messageDigest.addProvider(new AnnouncementDigestProvider());

                try
                {
                    messageDigest.sendMessageDigest();
                }
                catch (Exception e)
                {
                    LogManager.getLogger(AnnouncementsController.class).error(e);
                }
            });
            digestThread.start();

            return success("Announcements daily digest sent");
        }
    }

    private static NotFoundException createThreadNotFoundException(Container c)
    {
        return new NotFoundException("Could not find " + getSettings(c).getConversationName().toLowerCase());
    }
    
    public static class AnnouncementDeleteForm extends ReturnUrlForm
    {
        private int _rowId;
        private String _entityId;

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
    }

    public static class AnnouncementForm extends BeanViewForm<AnnouncementModel>
    {
        AnnouncementModel _selectedAnnouncementModel = null;

        public AnnouncementForm()
        {
            super(AnnouncementModel.class, null, Collections.singletonMap("parentid", GUID.class));
        }

        // XXX: change return value to typed GuidString
        public String getParentId()
        {
            return _stringValues.get("parentid");
        }

        AnnouncementModel selectAnnouncement()
        {
            if (null == _selectedAnnouncementModel)
            {
                AnnouncementModel bean;
                try
                {
                    bean = getBean();
                }
                catch (ConversionException e)
                {
                    return null;
                }
                if (null != bean.getEntityId())
                    _selectedAnnouncementModel = AnnouncementManager.getAnnouncement(getContainer(), bean.getEntityId());  // Need member list
                if (null == _selectedAnnouncementModel)
                    _selectedAnnouncementModel = AnnouncementManager.getAnnouncement(getContainer(), bean.getRowId());
            }
            return _selectedAnnouncementModel;
        }

        public void validate(Errors errors)
        {
            // Validate "expires" conversion from String to Date
            try
            {
                String expires = StringUtils.trimToNull((String) get("expires"));
                if (null != expires)
                    DateUtil.parseDateTime(getContainer(), expires);
            }
            catch (ConversionException x)
            {
                errors.reject(ERROR_MSG, "Expires must be blank or a valid date.");
            }
        }

        public boolean isFromDiscussion()
        {
            return Boolean.parseBoolean(get("fromDiscussion"));
        }

        public boolean allowMultipleDiscussions()
        {
            return Boolean.parseBoolean(get("allowMultipleDiscussions"));
        }
    }


    public static class EmailOptionsForm extends ViewForm
    {
        private int _emailPreference = EmailOption.MESSAGES_NONE.getValue();
        private int _notificationType = EmailOption.MESSAGES_NO_DAILY_DIGEST.getValue();
        private String _srcIdentifier;
        private boolean _resetFolderDefault = false;

        // Email option is a single int that contains the conversation preference AND choice for digest vs. individual
        // This method splits them apart

        public void setEmailOptionsOnPage(int emailOptionInt) throws IllegalStateException
        {
            EmailOption emailOption = EmailOption.intToEmailOptionMap.get(emailOptionInt);

            switch(emailOption)
            {
                case NOT_SET:
                case MESSAGES_NONE:
                    _emailPreference = EmailOption.MESSAGES_NONE.getValue();
                    _notificationType = EmailOption.MESSAGES_NO_DAILY_DIGEST.getValue();
                break;
                case MESSAGES_MINE:
                    _emailPreference = EmailOption.MESSAGES_MINE.getValue();
                    _notificationType = EmailOption.MESSAGES_NO_DAILY_DIGEST.getValue();
                break;
                case MESSAGES_ALL:
                    _emailPreference = EmailOption.MESSAGES_ALL.getValue();
                    _notificationType = EmailOption.MESSAGES_NO_DAILY_DIGEST.getValue();
                break;
                case MESSAGES_MINE_DAILY_DIGEST:
                    _emailPreference = EmailOption.MESSAGES_MINE.getValue();
                    _notificationType = EmailOption.MESSAGES_DAILY_DIGEST.getValue();
                break;
                case MESSAGES_ALL_DAILY_DIGEST:
                    _emailPreference = EmailOption.MESSAGES_ALL.getValue();
                    _notificationType = EmailOption.MESSAGES_DAILY_DIGEST.getValue();
                break;
                default:  // should never happen
                    throw new IllegalStateException("emailOption is set to an invalid value.");
            }
        }

        // Email option is a single int that contains the conversation preference AND choice for digest vs. individual
        // This method puts them together

        public int getEmailOption(BindException errors) throws IllegalStateException
        {
            if (_emailPreference == EmailOption.MESSAGES_NONE.getValue())
            {
                // Form allows "no email" + "daily digest" -- always return "no email" + "individual" since they are equivalent
                // and we don't want to deal with the former option in the database, with foreign keys, on the admin pages, etc.
                errors.reject(ERROR_MSG, "None + Daily Digest is not allowed, and was converted to None + Individual.");

                return EmailOption.MESSAGES_NONE.getValue();
            }
            else if (_emailPreference == EmailOption.MESSAGES_MINE.getValue())
            {
                if (_notificationType == EmailOption.MESSAGES_NO_DAILY_DIGEST.getValue())
                {
                    return EmailOption.MESSAGES_MINE.getValue();
                }
                else  // daily digest specified, so combine values
                {
                    return EmailOption.MESSAGES_MINE_DAILY_DIGEST.getValue();
                }
            }
            else if (_emailPreference == EmailOption.MESSAGES_ALL.getValue())
            {
                if (_notificationType == EmailOption.MESSAGES_NO_DAILY_DIGEST.getValue())
                {
                    return EmailOption.MESSAGES_ALL.getValue();
                }
                else  // daily digest specified, so combine values
                {
                    return EmailOption.MESSAGES_ALL_DAILY_DIGEST.getValue();
                }
            }
            else  // should never happen
            {
                throw new IllegalStateException("_emailPreference and _notificationType are set to an invalid combination.");
            }
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

        public String getSrcIdentifier()
        {
            return _srcIdentifier == null ? getContainer().getEntityId().toString() : _srcIdentifier;
        }

        public void setSrcIdentifier(String srcIdentifier)
        {
            _srcIdentifier = srcIdentifier;
        }

        public boolean getResetFolderDefault()
        {
            return _resetFolderDefault;
        }

        public void setResetFolderDefault(boolean resetFolderDefault)
        {
            _resetFolderDefault = resetFolderDefault;
        }
    }


    public abstract static class LinkBarBean
    {
        public Settings settings;
        public String filterText;
        public ActionURL adminURL;
        public ActionURL emailPrefsURL;
        public ActionURL emailManageURL;
        public ActionURL insertURL;
        public ActionURL containerEmailTemplateURL;
        public ActionURL siteEmailTemplateURL;
        public boolean includeGroups;

        protected void init(Container c, ActionURL url, User user, Settings settings, Permissions perm, boolean displayAll, boolean isFiltered, int rowLimit)
        {
            SecurityLogger.indent(getClass().getName());
            try
            {
                this.settings = settings;
                filterText = getFilterText(settings, displayAll, isFiltered, rowLimit);
                adminURL = c.hasPermission(user, AdminPermission.class) ? getAdminURL(c, url) : null;
                emailPrefsURL = user.isGuest() ? null : getEmailPreferencesURL(c, url, c.getId());
                emailManageURL = c.hasPermission(user, AdminPermission.class) ? getAdminEmailURL(c, url) : null;
                containerEmailTemplateURL = c.hasPermission(user, AdminPermission.class) ? urlProvider(AdminUrls.class).getCustomizeEmailURL(c, AnnouncementManager.NotificationEmailTemplate.class, url) : null;
                siteEmailTemplateURL = user.hasSiteAdminPermission() ? urlProvider(AdminUrls.class).getCustomizeEmailURL(ContainerManager.getRoot(), AnnouncementManager.NotificationEmailTemplate.class, url) : null;
                insertURL = perm.allowInsert() ? getInsertURL(c, url) : null;
                includeGroups = perm.includeGroups();
            }
            finally
            {
                SecurityLogger.outdent();
            }
        }
    }


    private static void addAdminMenus(LinkBarBean bean, NavTree menu, ViewContext context)
    {
        NavTree email = new NavTree("Email", "", context.getContextPath() + "/_images/email.png");
        if (bean.emailPrefsURL != null)
            email.addChild("Preferences", bean.emailPrefsURL);
        if (bean.emailManageURL != null)
            email.addChild("Administration", bean.emailManageURL);
        if (bean.siteEmailTemplateURL != null)
            email.addChild("Site-Wide Email Template", bean.siteEmailTemplateURL);
        if (bean.containerEmailTemplateURL != null)
            email.addChild(StringUtils.capitalize(context.getContainer().getContainerNoun()) + " Email Template", bean.containerEmailTemplateURL);
        if (email.hasChildren())
            menu.addChild(email);

        if (bean.adminURL != null)
            menu.addChild("Admin", bean.adminURL);
    }


    public static class ListLinkBar extends JspView<ListLinkBar.ListBean>
    {
        private ListLinkBar(Container c, ActionURL url, User user, Settings settings, Permissions perm, boolean displayAll)
        {
            super("/org/labkey/announcements/announcementListLinkBar.jsp", new ListBean(c, url, user, settings, perm, displayAll));

            ListBean bean = new ListBean(c, url, user, settings, perm, displayAll);
            NavTree menu = new NavTree("");
            ViewContext context = getViewContext();
            boolean isAdminMode = PageFlowUtil.isPageAdminMode(context);

            if ((bean.emailPrefsURL != null) && !isAdminMode)
                menu.addChild("Email Preferences", bean.emailPrefsURL);

            if (isAdminMode)
                addAdminMenus(bean, menu, getViewContext());

            setNavMenu(menu);
        }

        public static class ListBean extends LinkBarBean
        {
            public ActionURL messagesURL;
            public String urlFilterText;

            private ListBean(Container c, ActionURL url, User user, Settings settings, Permissions perm, boolean displayAll)
            {
                SimpleFilter urlFilter = new SimpleFilter(url, "Threads");
                boolean isFiltered = !urlFilter.isEmpty();

                init(c, url, user, settings, perm, displayAll, isFiltered, 0);

                messagesURL = getBeginURL(c);
                urlFilterText = isFiltered ? urlFilter.getFilterText(
                    new SimpleFilter.ColumnNameFormatter()
                    {
                        @Override
                        public String format(FieldKey fieldKey)
                        {
                            return super.format(fieldKey).replaceFirst(".DisplayName", "");
                        }
                    }) : null;
            }
        }
    }


    public static class AnnouncementWebPart extends JspView<AnnouncementWebPart.MessagesBean>
    {
        public AnnouncementWebPart(String jsp, Container c, ActionURL url, User user, Settings settings, boolean displayAll, boolean asWebPart)
        {
            super(jsp,
                new MessagesBean(c, url, user, settings, displayAll));
            setTitle(settings.getBoardName());
            setTitleHref(getBeginURL(c));

            ViewContext context = getViewContext();
            boolean isAdminMode = PageFlowUtil.isPageAdminMode(context);
            MessagesBean bean = getModelBean(); 
            NavTree menu = new NavTree("");
            if (bean.insertURL != null)
                menu.addChild("New", bean.insertURL);
            if (bean.listURL != null)
                menu.addChild("View List", bean.listURL);
            if ((bean.emailPrefsURL != null) && !isAdminMode)
                menu.addChild("Email Preferences", bean.emailPrefsURL);

            if (isAdminMode)
                addAdminMenus(bean, menu, context);

            setIsWebPart(asWebPart);
            setNavMenu(menu);
        }

        public AnnouncementWebPart(String jsp, ViewContext ctx)
        {
            this(jsp, ctx.getContainer(), getPageURL(ctx), ctx.getUser(), getSettings(ctx.getContainer()), false, true);
        }

        public AnnouncementWebPart(Container c, ActionURL url, User user, Settings settings, boolean displayAll, boolean asWebPart)
        {
            this("/org/labkey/announcements/announcementWebPartWithExpandos.jsp", c, url, user, settings, displayAll, asWebPart);
        }

        private static ActionURL getPageURL(ViewContext ctx)
        {
            // This is set to the outer page URL in the case of rendering a dynamic webpart; use it instead of
            // the getWebPart URL.
            String returnURL = (String)ctx.get(ActionURL.Param.returnUrl.name());

            if (null != returnURL)
            {
                try
                {
                    return new ActionURL(returnURL);
                }
                catch (IllegalArgumentException x)
                {
                    // pass
                }
            }
            return ctx.getActionURL();
        }

        public static class MessagesBean extends LinkBarBean
        {
            public Collection<AnnouncementModel> announcementModels;
            public ActionURL listURL;
            public boolean isPrint=false;

            private MessagesBean(Container c, ActionURL url, User user, Settings settings, boolean displayAll)
            {
                Permissions perm = getPermissions(c, user, settings);
                SimpleFilter filter = getFilter(settings, perm, displayAll);
                Pair<Collection<AnnouncementModel>, Boolean> pair = AnnouncementManager.getAnnouncements(c, filter, settings.getSort(), 100);

                //Issue 33501: Don't include return URL if the web part was called via getWebPart API
                if (url.getAction().equalsIgnoreCase("getWebPart"))
                    init(c, null, user, settings, perm, displayAll, false, pair.second ? 100 : 0);
                else
                    init(c, url, user, settings, perm, displayAll, false, pair.second ? 100 : 0);
                
                announcementModels = pair.first;
                listURL = getListURL(c);
            }
        }
    }


    public static class CustomizeAnnouncementWebPart extends JspView<Portal.WebPart>
    {
        public CustomizeAnnouncementWebPart(Portal.WebPart webPart)
        {
            super("/org/labkey/announcements/customizeAnnouncementWebPart.jsp", webPart);
        }
    }


    public static class AnnouncementWebPartFactory extends AlwaysAvailableWebPartFactory
    {
        AnnouncementWebPartFactory(String name)
        {
            super(name);
        }

        @Override
        public WebPartView getWebPartView(@NotNull ViewContext parentCtx, @NotNull Portal.WebPart webPart)
        {
            String jsp = "/org/labkey/announcements/announcementWebPartWithExpandos.jsp";
            if ("simple".equals(webPart.getPropertyMap().get("style")))
                jsp = "/org/labkey/announcements/announcementWebPartSimple.jsp";
            return new AnnouncementsController.AnnouncementWebPart(jsp, parentCtx);
        }

        @Override
        public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
        {
            return new AnnouncementsController.CustomizeAnnouncementWebPart(webPart);
        }

        @Override
        public boolean isEditable()
        {
            return true;
        }
    }


    private static SimpleFilter getFilter(Settings settings, Permissions perm, boolean displayAll)
    {
        // Filter out threads that this user can't read
        SimpleFilter filter = perm.getThreadFilter();

        if (!displayAll)
        {
            if (settings.hasExpires())
                filter.addWhereClause("Expires IS NULL OR Expires > ?", new Object[]{new Date(System.currentTimeMillis() - DateUtils.MILLIS_PER_DAY)});
        }

        return filter;
    }


    // Requires HTML encoding for display
    private static String getFilterText(Settings settings, boolean displayAll, boolean isFiltered, int rowLimit)
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

        public AnnouncementListWebPart(ViewContext ctx)
        {
            this(ctx, false, true);
        }

        private AnnouncementListWebPart(ViewContext ctx, boolean displayAll, boolean asWebPart)
        {
            super(FrameType.PORTAL);
            Container c = ctx.getContainer();
            User user = ctx.getUser();
            ActionURL url = ctx.getActionURL();

            Settings settings = getSettings(c);
            Permissions perm = getPermissions(c, user, settings);
            DataRegion rgn = getDataRegion(perm, settings);

            setTitle(settings.getBoardName() + " List");
            setTitleHref(getListURL(c));

            TableInfo tinfo = _comm.getTableInfoThreads();
            DisplayColumn title = new DataColumn(tinfo.getColumn("Title"));
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
                createGroups.setCaption("Created By Groups");
                rgn.addDisplayColumn(createGroups);
            }

            rgn.addColumn(tinfo.getColumn("Created"));

            ColumnInfo colResponseCreatedBy = tinfo.getColumn("ResponseCreatedBy"); // TODO: setRenderClass?
            DisplayColumn lastDc = new UserIdRenderer(colResponseCreatedBy);
            rgn.addDisplayColumn(lastDc);

            if (perm.includeGroups())
            {
                DisplayColumn responseGroups = new GroupColumn(colResponseCreatedBy);
                responseGroups.setCaption("Most Recent Groups");
                rgn.addDisplayColumn(responseGroups);
            }

            rgn.addColumn(tinfo.getColumn("ResponseCreated"));

            GridView gridView = new GridView(rgn, (BindException)null);
            gridView.setContainer(c);
            gridView.setSort(settings.getSort());
            addClientDependencies(gridView.getClientDependencies());

            SimpleFilter filter = getFilter(settings, perm, displayAll);
            gridView.setFilter(filter);

            ListLinkBar bar = new ListLinkBar(c, url, user, settings, perm, displayAll);
            setNavMenu(bar.getNavMenu());
            setIsWebPart(asWebPart);
            _vbox = new VBox(bar, gridView);
        }

        protected DataRegion getDataRegion(Permissions perm, Settings settings)
        {
            QuerySettings qs = new QuerySettings(getViewContext(), "Announcements");
            DataRegion rgn = new DataRegion();
            rgn.setSettings(qs);
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


            @Override @NotNull
            public HtmlString getFormattedHtml(RenderContext ctx)
            {
                Integer userId = (Integer)getValue(ctx);

                if (null != userId)
                {
                    User user = UserManager.getUser(userId);

                    if (null != user)
                        return SecurityManager.getGroupList(ctx.getContainer(), user);
                }

                return HtmlString.EMPTY_STRING;
            }
        }
    }


    public static class AnnouncementListView extends AnnouncementListWebPart
    {
        public AnnouncementListView(ViewContext ctx)
        {
            super(ctx, true, false);
        }

        @Override
        protected DataRegion getDataRegion(Permissions perm, Settings settings)
        {
            DataRegion rgn = super.getDataRegion(perm, settings);

            ButtonBar bb = new ButtonBar();
            if (perm.allowDeleteAnyThread())
            {
                rgn.setShowRecordSelectors(true);

                String conversation = settings.getConversationName().toLowerCase();
                String conversations = conversation + "s";
                ActionButton delete = new ActionButton(DeleteThreadsAction.class, "Delete");
                delete.setActionType(ActionButton.Action.POST);
                delete.setDisplayPermission(DeletePermission.class);
                delete.setRequiresSelection(true, "Are you sure you want to delete this " + conversation + "?", "Are you sure you want to delete these " + conversations + "?");
                bb.add(delete);

            }
            rgn.setButtonBar(bb);

            return rgn;
        }
    }


    public static class ThreadViewBean
    {
        public AnnouncementModel announcementModel;
        public String message = "";
        public Permissions perm = null;
        public boolean isResponse = false;
        public Settings settings;
        public ActionURL messagesURL;
        public ActionURL listURL;
        public URLHelper printURL;
        public URLHelper currentURL;
        public boolean print = false;
        public boolean includeGroups;
        public boolean embedded;
    }


    public static class ThreadView extends JspView<ThreadViewBean>
    {
        private ThreadView()
        {
            super("/org/labkey/announcements/announcementThread.jsp", new ThreadViewBean());
        }

        public ThreadView(Container c, URLHelper currentURL, User user, String rowId, String entityId)
        {
            this();
            init(c, findThread(c, rowId, entityId), currentURL, getPermissions(c, user, getSettings(c)), false, false);
        }

        public ThreadView(Container c, ActionURL url, AnnouncementModel ann, Permissions perm)
        {
            this();
            init(c, ann, url, perm, true, false);
        }
        
        public ThreadView(AnnouncementForm form, Container c, ActionURL url, Permissions perm, boolean print)
        {
            this();
            AnnouncementModel ann = findThread(c, (String)form.get("rowId"), (String)form.get("entityId"));
            init(c, ann, url, perm, false, print);
        }

        protected void init(Container c, AnnouncementModel ann, URLHelper currentURL, Permissions perm, boolean isResponse, boolean print)
        {
            if (null == c || !perm.allowRead(ann))
            {
                throw new UnauthorizedException();
            }

            if (null == ann)
                throw createThreadNotFoundException(c);

            ThreadViewBean bean = getModelBean();
            bean.announcementModel = ann;
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
            bean.embedded = (null != ann.getDiscussionSrcURL() && !getViewContext().getActionURL().getController().equalsIgnoreCase("announcements"));  // TODO: Should have explicit flag for discussion case

            if (!bean.print && !bean.embedded)
            {
                NavTree buttons = new NavTree();
                if (null != bean.listURL)
                {
                    buttons.addChild("view list", bean.listURL);
                }
                if (!bean.isResponse)
                {
                    buttons.addChild("print", bean.printURL);
                }

                // Create buttons to subscribe or unsubscribe from the forum and/or individual thread
                if (!getViewContext().getUser().isGuest())
                {
                    // First check if the user is subscribed to this specific thread
                    if (ann.getMemberListIds().contains(getViewContext().getUser().getUserId()))
                    {
                        // Build up a link to unsubscribe from the thread
                        ActionURL url = new ActionURL(SubscribeThreadAction.class, c);
                        url.addParameter("threadId", ann.getParent() == null ? ann.getEntityId() : ann.getParent());
                        url.addReturnURL(getViewContext().getActionURL());
                        url.addParameter("unsubscribe", true);
                        buttons.addChild("unsubscribe", url).usePost();
                    }
                    else
                    {
                        // See if they're subscribed to the whole forum
                        int emailOption = getEmailOptionIncludingInherited(c, getViewContext().getUser(), ann.lookupSrcIdentifer());

                        // Or if they're subscribed because they've posted to this thread already
                        // Remember the emailOption is a bitmask, so don't use simple equality checks
                        boolean forumSubscription;
                        if ((emailOption == EmailOption.MESSAGES_ALL.getValue())
                                || (emailOption == EmailOption.MESSAGES_ALL_DAILY_DIGEST.getValue()))
                        {
                            forumSubscription = true;
                        }
                        else if ((emailOption == EmailOption.MESSAGES_MINE.getValue())
                                || (emailOption == EmailOption.MESSAGES_MINE_DAILY_DIGEST.getValue()))
                        {
                            forumSubscription = ann.getAuthors().contains(getViewContext().getUser());
                        }
                        else
                        {
                            forumSubscription = false;
                        }
                        
                        if (forumSubscription)
                        {
                            // Give them a link to the forum level subscription UI
                            buttons.addChild("unsubscribe", getEmailPreferencesURL(c, getViewContext().getActionURL(), ann.lookupSrcIdentifer()));
                        }
                        else
                        {
                            // Otherwise, let them subscribe to either the forum or the specific thread
                            NavTree subscribeTree = new NavTree("subscribe");
                            subscribeTree.addChild("forum", getEmailPreferencesURL(c, getViewContext().getActionURL(), ann.lookupSrcIdentifer()));
                            ActionURL subscribeThreadURL = new ActionURL(SubscribeThreadAction.class, c);
                            subscribeThreadURL.addParameter("threadId", ann.getParent() == null ? ann.getEntityId() : ann.getParent());
                            subscribeThreadURL.addReturnURL(getViewContext().getActionURL());
                            subscribeTree.addChild("thread", subscribeThreadURL).usePost();
                            buttons.addChild(subscribeTree);
                        }
                    }
                }

                setNavMenu(buttons);
                setIsWebPart(false);
            }

            setTitle("View " + bean.settings.getConversationName());
        }

        public AnnouncementModel getAnnouncement()
        {
            return getModelBean().announcementModel;
        }
    }


    private static @Nullable AnnouncementModel findThread(Container c, String rowIdVal, String entityId)
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
                throw createThreadNotFoundException(c);
            }
        }

        if (0 != rowId)
            return AnnouncementManager.getAnnouncement(c, rowId);
        else if (null == entityId)
            throw createThreadNotFoundException(c);

        return AnnouncementManager.getAnnouncement(c, entityId);
    }

    public static class SubscriptionBean extends ReturnUrlForm
    {
        private String _threadId;
        private boolean _unsubscribe;

        public String getThreadId()
        {
            return _threadId;
        }

        public void setThreadId(String threadId)
        {
            _threadId = threadId;
        }

        public boolean isUnsubscribe()
        {
            return _unsubscribe;
        }

        public void setUnsubscribe(boolean unsubscribe)
        {
            _unsubscribe = unsubscribe;
        }
    }

    @RequiresLogin @RequiresPermission(ReadPermission.class)
    public class SubscribeThreadAction extends FormHandlerAction<SubscriptionBean>
    {
        @Override
        public void validateCommand(SubscriptionBean target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(SubscriptionBean bean, BindException errors)
        {
            String id = bean.getThreadId();
            if (id == null)
            {
                throw new NotFoundException("No message thread specified");
            }
            // Don't filter by container to make it easier for client API users that are going cross-container
            AnnouncementModel ann = AnnouncementManager.getAnnouncement(null, id);
            if (ann == null)
            {
                throw new NotFoundException("No such message thread: " + id);
            }
            // Make sure they have permission to see the container for the specific message they're
            // requesting
            if (!ann.lookupContainer().hasPermission(getUser(), ReadPermission.class))
            {
                throw new UnauthorizedException();
            }

            // Remove or add the thread-level subscription from the database table
            if (bean.isUnsubscribe())
            {
                new SqlExecutor(CommSchema.getInstance().getSchema()).execute("DELETE FROM comm.userlist WHERE UserId = ? AND MessageId = ?", getUser(), ann.getRowId());
            }
            else if (!ann.getMemberListIds().contains(getUser().getUserId()))
            {
                new SqlExecutor(CommSchema.getInstance().getSchema()).execute("INSERT INTO comm.userlist (UserId, MessageId) VALUES (?, ?)", getUser(), ann.getRowId());
            }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(SubscriptionBean subscriptionBean)
        {
            return subscriptionBean.getReturnActionURL(getBeginURL(getContainer()));
        }
    }

    
    public static class AnnouncementUpdateView extends JspView<AnnouncementUpdateView.UpdateBean>
    {
        public AnnouncementUpdateView(AnnouncementForm form, AnnouncementModel ann, BindException errors)
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
            public AnnouncementModel annModel;
            public Settings settings;
            public SelectBuilder assignedToSelect;
            public SelectBuilder statusSelect;
            public String memberList;
            public WikiRendererType[] renderers;
            public WikiRendererType currentRendererType;
            public URLHelper returnURL;

            private UpdateBean(AnnouncementForm form, AnnouncementModel ann)
            {
                Container c = form.getContainer();
                String reshowMemberList = form.get("memberList");

                annModel = ann;
                settings = getSettings(c);
                currentRendererType = WikiRendererType.valueOf(ann.getRendererType());
                renderers = WikiRendererType.values();
                memberList = getMemberList(form.getUser(), c, ann, reshowMemberList);
                statusSelect = getStatusSelect(ann.getStatus());
                assignedToSelect = getAssignedToSelect(c, ann.getAssignedTo(), "assignedTo", getViewContext().getUser());
                returnURL = form.getReturnURLHelper();
            }
        }
    }


    public static class ModeratorReviewForm extends QueryForm
    {
        private boolean _approve = false;
        private boolean _spam = false;

        public boolean isApprove()
        {
            return _approve;
        }

        public void setApprove(boolean approve)
        {
            _approve = approve;
        }

        public boolean isSpam()
        {
            return _spam;
        }

        public void setSpam(boolean spam)
        {
            _spam = spam;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ModeratorReviewAction extends FormViewAction<ModeratorReviewForm>
    {
        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Moderator Review for " + getSettings().getBoardName(), getBeginURL(getContainer()));
        }

        @Override
        public void validateCommand(ModeratorReviewForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(ModeratorReviewForm form, boolean reshow, BindException errors)
        {
            UserSchema schema = new AnnouncementSchema(getUser(), getContainer());
            QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);
            settings.setQueryName(AnnouncementSchema.MODERATOR_REVIEW_TABLE_NAME);

            return new QueryView(schema, settings, errors) {
                @Override
                protected void populateButtonBar(DataView view, ButtonBar bar)
                {
                    ActionButton spamButton = new ActionButton(new ActionURL(ModeratorReviewAction.class, getContainer()).addParameter("spam", 1), "Mark As Spam");
                    spamButton.setRequiresSelection(true, "Are you sure you want to mark this message as spam?", "Are you sure you want to mark these messages as spam?");
                    bar.add(spamButton);

                    ActionButton approveButton = new ActionButton(new ActionURL(ModeratorReviewAction.class, getContainer()).addParameter("approve", 1), "Approve");
                    approveButton.setRequiresSelection(true, "Are you sure you want to approve this message?", "Are you sure you want to approve these messages?");
                    bar.add(approveButton);
                }
            };
        }

        @Override
        public boolean handlePost(ModeratorReviewForm form, BindException errors)
        {
            Stream<AnnouncementModel> stream = getViewContext().getList(DataRegion.SELECT_CHECKBOX_NAME).stream()
                .map(Integer::parseInt)
                .map(rowId -> AnnouncementManager.getAnnouncement(getContainer(), rowId));

            if (form.isSpam())
            {
                stream.forEach(ann -> AnnouncementManager.markAsSpam(getContainer(), ann));
            }
            else if (form.isApprove())
            {
                stream.forEach(ann -> AnnouncementManager.approve(getContainer(), getUser(), true, ann, new Date()));
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(ModeratorReviewForm form)
        {
            return null;
        }
    }

    public static class ThreadIdentityForm
    {
        private String _entityId;
        private Integer _rowId;

        public String getEntityId()
        {
            return _entityId;
        }

        public void setEntityId(String entityId)
        {
            _entityId = entityId;
        }

        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }
    }

    public static class ThreadForm
    {
        private AnnouncementModel _thread;

        public AnnouncementModel getThread()
        {
            return _thread;
        }

        public void setThread(AnnouncementModel thread)
        {
            _thread = thread;
        }
    }

    private @Nullable AnnouncementModel getThread(Container container, Integer rowId, String entityId)
    {
        AnnouncementModel thread = null;

        if (rowId != null)
            thread = AnnouncementManager.getAnnouncement(container, rowId);
        else if (entityId != null)
            thread = AnnouncementManager.getAnnouncement(container, entityId);

        return thread;
    }

    public static class CreateThreadForm extends ThreadForm
    {
        private boolean _reply;

        public boolean isReply()
        {
            return _reply;
        }

        public void setReply(boolean reply)
        {
            _reply = reply;
        }
    }

    @RequiresAnyOf({InsertMessagePermission.class, InsertPermission.class})
    public class CreateThreadAction extends MutatingApiAction<CreateThreadForm>
    {
        @Override
        public void validateForm(CreateThreadForm form, Errors errors)
        {
            if (form.getThread() == null)
                errors.reject(ERROR_MSG, "A \"thread\" object is required to create a thread.");
            else if (form.isReply() && form.getThread().getParent() == null)
                errors.reject(ERROR_MSG, "Failed to reply to thread. Improper request for a reply as a parent was not specified.");
            else if (!form.isReply() && form.getThread().getParent() != null)
                errors.reject(ERROR_MSG, "Failed to create thread. Improper request for create as a parent was specified.");
        }

        @Override
        public Object execute(CreateThreadForm form, BindException errors)
        {
            if (!getPermissions().allowInsert())
                throw new UnauthorizedException();

            var newThread = copyEditableProps(new AnnouncementModel(), form.getThread(), true);

            // Ensure parent exists
            if (form.isReply())
            {
                var parentThread = getThread(getContainer(), null, newThread.getParent());

                if (parentThread == null)
                {
                    errors.reject(ERROR_MSG, "Failed to reply to thread. Unable to find parent thread \"" + newThread.getParent() + "\".");
                    return null;
                }
                else if (AnnouncementManager.getLatestPostId(parentThread) == null)
                {
                    errors.reject(ERROR_MSG, "Failed to reply to thread. Could not locate most recent response for thread \"" + parentThread.getEntityId() + "\".");
                    return null;
                }
            }

            try
            {
                var insertedThread = AnnouncementManager.insertAnnouncement(getContainer(), getUser(), newThread, getAttachmentFileList());
                return success(insertedThread);
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "Failed to create thread in folder " + getContainer().getPath());
                errors.reject(ERROR_MSG, e.getMessage() == null ? e.toString() : e.getMessage());
            }

            return null;
        }
    }

    @RequiresNoPermission // Checked by action
    public class DeleteThreadAction extends MutatingApiAction<ThreadIdentityForm>
    {
        @Override
        public void validateForm(ThreadIdentityForm form, Errors errors)
        {
            if (form.getRowId() == null && form.getEntityId() == null)
                errors.reject(ERROR_MSG, "A \"rowId\" or an \"entityId\" must be provided to delete a thread.");
        }

        @Override
        public Object execute(ThreadIdentityForm form, BindException errors)
        {
            var thread = getThread(getContainer(), form.getRowId(), form.getEntityId());

            if (thread == null)
            {
                errors.reject(ERROR_MSG, "Unable to find thread to delete in folder " + getContainer().getPath());
                return null;
            }

            if (!getPermissions().allowDeleteMessage(thread))
                throw new UnauthorizedException();

            AnnouncementManager.deleteAnnouncement(getContainer(), thread.getRowId());

            return success();
        }
    }

    public static class GetDiscussionsForm
    {
        private String _discussionSrcIdentifier;

        public String getDiscussionSrcIdentifier()
        {
            return _discussionSrcIdentifier;
        }

        public void setDiscussionSrcIdentifier(String discussionSrcIdentifier)
        {
            _discussionSrcIdentifier = discussionSrcIdentifier;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetDiscussionsAction extends ReadOnlyApiAction<GetDiscussionsForm>
    {
        @Override
        public void validateForm(GetDiscussionsForm form, Errors errors)
        {
            if (form.getDiscussionSrcIdentifier() == null)
                errors.reject(ERROR_MSG, "A \"discussionSrcIdentifier\" must be provided to retrieve discussions.");
        }

        @Override
        public Object execute(GetDiscussionsForm form, BindException errors)
        {
            return success(AnnouncementManager.getDiscussions(getContainer(), form.getDiscussionSrcIdentifier()));
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetThreadAction extends ReadOnlyApiAction<ThreadIdentityForm>
    {
        @Override
        public void validateForm(ThreadIdentityForm form, Errors errors)
        {
            if (form.getRowId() == null && form.getEntityId() == null)
                errors.reject(ERROR_MSG, "A \"rowId\" or an \"entityId\" must be provided to retrieve a thread.");
        }

        @Override
        public Object execute(ThreadIdentityForm form, BindException errors)
        {
            AnnouncementModel thread = getThread(getContainer(), form.getRowId(), form.getEntityId());

            if (thread == null)
            {
                errors.reject(ERROR_MSG, "Unable to find thread in folder " + getContainer().getPath());
                return null;
            }

            return success(thread);
        }
    }

    @RequiresNoPermission   // Custom permission checking below to handle owner-update
    public class UpdateThreadAction extends MutatingApiAction<ThreadForm>
    {
        @Override
        public void validateForm(ThreadForm form, Errors errors)
        {
            if (form.getThread() == null)
                errors.reject(ERROR_MSG, "A \"thread\" object is required to create a thread.");
        }

        @Override
        public Object execute(ThreadForm form, BindException errors)
        {
            var rawThread = form.getThread();
            var thread = getThread(getContainer(), rawThread.getRowId(), rawThread.getEntityId());

            if (thread == null)
            {
                errors.reject(ERROR_MSG, "Unable to find thread to update in folder " + getContainer().getPath());
                return null;
            }

            if (!getPermissions().allowUpdate(thread))
                throw new UnauthorizedException();

            var updatedThread = copyEditableProps(thread, rawThread, false);

            try
            {
                AnnouncementManager.updateAnnouncement(getUser(), updatedThread, getAttachmentFileList());
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "Failed to update thread in folder " + getContainer().getPath());
                errors.reject(ERROR_MSG, e.getMessage() == null ? e.toString() : e.getMessage());
            }

            updatedThread = getThread(getContainer(), updatedThread.getRowId(), updatedThread.getEntityId());

            if (updatedThread == null)
            {
                errors.reject(ERROR_MSG, "Failed to find thread after update in folder " + getContainer().getPath());
                return null;
            }

            return success(updatedThread);
        }
    }
}
