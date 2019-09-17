/*
 * Copyright (c) 2005-2018 Fred Hutchinson Cancer Research Center
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
package org.labkey.announcements.model;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.announcements.AnnouncementsController;
import org.labkey.announcements.AnnouncementsController.ModeratorReviewAction;
import org.labkey.announcements.config.AnnouncementEmailConfig;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.announcements.DiscussionService.Settings;
import org.labkey.api.announcements.EmailOption;
import org.labkey.api.announcements.api.Announcement;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.MailHelper.BulkEmailer;
import org.labkey.api.util.MailHelper.MultipartMessage;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.util.emailTemplate.UserOriginatedEmailTemplate;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: mbellew
 * Date: Mar 11, 2005
 * Time: 10:08:26 AM
 */
public class AnnouncementManager
{
    public static final SearchService.SearchCategory searchCategory = new SearchService.SearchCategory("message", "Messages");

    private static final CommSchema _comm = CommSchema.getInstance();
    private static final CoreSchema _core = CoreSchema.getInstance();

    public static EmailOption EMAIL_DEFAULT_OPTION = EmailOption.MESSAGES_MINE;

    private AnnouncementManager()
    {
    }

    // Get first rowlimit threads in this container, filtered using filter
    public static Pair<Collection<AnnouncementModel>, Boolean> getAnnouncements(Container c, SimpleFilter filter, Sort sort, int rowLimit)
    {
        filter.addCondition(FieldKey.fromParts("Container"), c);
        ArrayList<AnnouncementModel> recent = new TableSelector(_comm.getTableInfoThreads(), filter, sort).setMaxRows(rowLimit + 1).getArrayList(AnnouncementModel.class);

        Boolean limited = (recent.size() > rowLimit);

        if (limited)
            recent.remove(rowLimit); // Remove the last element to get back to size == rowLimit

        return new Pair<>(recent, limited);
    }


    // Get all threads in this container, filtered using filter
    public static @NotNull List<AnnouncementModel> getAnnouncements(Container c, SimpleFilter filter, Sort sort)
    {
        filter.addCondition(FieldKey.fromParts("Container"), c);

        return new TableSelector(_comm.getTableInfoThreads(), filter, sort).getArrayList(AnnouncementModel.class);
    }

    // Return a collection of announcementModels from a set of containers sorted by date created (newest first).
    public static @NotNull Collection<AnnouncementModel> getAnnouncements(Container... containers)
    {
        List<String> ids = new ArrayList<>();

        for (Container container : containers)
            ids.add(container.getId());

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), ids, CompareType.IN);
        filter.addAllClauses(AnnouncementManager.IS_APPROVED_FILTER);
        Sort sort = new Sort("-Created");

        return new TableSelector(_comm.getTableInfoAnnouncements(), filter, sort).getCollection(AnnouncementModel.class);
    }

    public static Collection<AnnouncementModel> getResponses(AnnouncementModel parent)
    {
//        assert null == parent.getParent();  // TODO: Either assert or short circuit with empty collection

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("container"), parent.getContainerId());
        filter.addCondition(FieldKey.fromParts("parent"), parent.getEntityId());
        filter.addAllClauses(AnnouncementManager.IS_APPROVED_FILTER);

        Sort sort = new Sort("Created");

        return new TableSelector(_comm.getTableInfoAnnouncements(), filter, sort).getCollection(AnnouncementModel.class);
    }


    public static @Nullable AnnouncementModel getAnnouncement(@Nullable Container c, String entityId)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("EntityId"), new GUID(entityId));
            if (c != null)
            {
                filter.addCondition(FieldKey.fromParts("Container"), c);
            }
            return new TableSelector(_comm.getTableInfoAnnouncements(), filter, null).getObject(AnnouncementModel.class);
        }
        catch (IllegalArgumentException e)
        {
            // Bad GUID, no match!
            return null;
        }
    }

    private static MessageConfigService.ConfigTypeProvider _configProvider;
    public static MessageConfigService.ConfigTypeProvider getAnnouncementConfigProvider()
    {
        if (_configProvider == null)
        {
            _configProvider = MessageConfigService.get().getConfigType(AnnouncementEmailConfig.TYPE);
            assert(_configProvider != null);
        }
        return _configProvider;
    }

    public static void saveEmailPreference(User user, Container c, int emailPreference, String srcIdentifier)
    {
        saveEmailPreference(user, c, user, emailPreference, srcIdentifier);
    }

    public static synchronized void saveEmailPreference(User currentUser, Container c, User projectUser, int emailPreference, String srcIdentifier)
    {
        getAnnouncementConfigProvider().savePreference(currentUser, c, projectUser, emailPreference, srcIdentifier);
    }


    public static AnnouncementModel getAnnouncement(@Nullable Container c, int rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowId);
        if (c != null)
        {
            filter.addCondition(FieldKey.fromParts("Container"), c);
        }
        Selector selector = new TableSelector(_comm.getTableInfoAnnouncements(), filter, null);
        return selector.getObject(AnnouncementModel.class);
    }


    public static AnnouncementModel getLatestPost(Container c, AnnouncementModel parent)
    {
        SQLFragment sql = new SQLFragment( "SELECT LatestId FROM ");
        sql.append(_comm.getTableInfoThreads(), "t");
        sql.append(" WHERE RowId = ?");
        sql.add(parent.getRowId());
        Integer postId = new SqlSelector(_comm.getSchema(), sql).getObject(Integer.class);

        if (null == postId)
            throw new NotFoundException("Can't find most recent post");

        return getAnnouncement(c, postId);
    }


    public static AnnouncementModel insertAnnouncement(Container c, User user, AnnouncementModel insert, List<AttachmentFile> files) throws IOException
    {
        return insertAnnouncement(c, user, insert, files, true);
    }

    public static AnnouncementModel insertAnnouncement(Container c, User user, AnnouncementModel insert, List<AttachmentFile> files, boolean sendEmailNotifications) throws IOException
    {
        // If no srcIdentifier is set and this is a parent message, set its source to the container
        if (insert.getDiscussionSrcIdentifier() == null && insert.getParent() == null)
        {
            insert.setDiscussionSrcIdentifier(c.getEntityId().toString());
        }
        insert.beforeInsert(user, c.getId());
        AnnouncementModel ann = Table.insert(user, _comm.getTableInfoAnnouncements(), insert);

        try
        {
            List<Integer> userIds = ann.getMemberListIds();

            // Attach member list to current message/response. This gives us a history of changes and is needed to handle moderator reviews.
            saveMemberList(user, userIds, ann.getRowId());
        }
        catch (Exception e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }

        try
        {
            AttachmentService.get().addAttachments(ann.getAttachmentParent(), files, user);
        }
        finally
        {
            // If addAttachment() throws, still call approve() or notifyModerators, as appropriate. #30178

            ModeratorReview mr = ModeratorReview.get(getMessageBoardSettings(c).getModeratorReview());

            if (mr.isApproved(c, user))
                approve(c, user, sendEmailNotifications, ann, ann.getCreated());
            else
                notifyModerators(c, user, ann);
        }

        return ann;
    }

    public static void approve(Container c, User user, boolean sendEmailNotifications, AnnouncementModel ann, Date date)
    {
        // This checks for multiple moderator race condition, #34266
        if (updateApproved(c, ann, date))
        {
            // Send email if there's body text or an attachment.
            if (sendEmailNotifications && (null != ann.getBody() || !ann.getAttachments().isEmpty()))
            {
                String rendererTypeName = ann.getRendererType();
                WikiRendererType currentRendererType = (null == rendererTypeName ? null : WikiRendererType.valueOf(rendererTypeName));
                if (null == currentRendererType)
                {
                    WikiService wikiService = WikiService.get();
                    if (null != wikiService)
                        currentRendererType = wikiService.getDefaultMessageRendererType();
                }
                sendNotificationEmails(ann, currentRendererType, c, user);
            }

            indexThread(ann);
        }
    }

    private static void notifyModerators(Container c, User user, AnnouncementModel ann)
    {
        String name = AnnouncementManager.getMessageBoardSettings(c).getConversationName();
        BulkEmailer emailer = new BulkEmailer(user);
        List<String> toList = SecurityManager.getUsersWithPermissions(c, Collections.singleton(AdminPermission.class)).stream()
            .map(User::getEmail)
            .collect(Collectors.toList());

        if (toList.isEmpty())
        {
            Logger.getLogger(AnnouncementManager.class).warn("New " + name.toLowerCase() + " requires moderator review, but no moderators are authorized in this folder: " + c.getPath());
        }
        else
        {
            MultipartMessage msg = MailHelper.createMultipartMessage();

            try
            {
                msg.setFrom(LookAndFeelProperties.getInstance(c).getSystemEmailAddress());
                msg.setSubject("New " + name.toLowerCase() + " in " + c.getPath() + " (" + c.getTitle() + ") requires moderator review");

                StringBuilder content = new StringBuilder();
                content.append("Please visit the Moderator Review page at ").append(new ActionURL(ModeratorReviewAction.class, c).getURIString());
                content.append("\n\nSubject: ").append(ann.getTitle());
                content.append("\nUser: ").append(user.getDisplayName(user)).append(" (").append(user.getEmail()).append(")");
                content.append("\n").append(name).append(": ").append(AnnouncementsController.getThreadURL(c, ann.getEntityId(), ann.getRowId()).getURIString());

                msg.setTextContent(content.toString());
                emailer.addMessage(toList, msg);
                emailer.start();
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }
    }

    // Magic date value used to mark an announcement that a moderator has reviewed and marked as spam
    private static final Date SPAM_MAGIC_DATE = new Date(0);

    // Standard filters for retrieving specific classes of messages (approved, spam, needs review)
    public static final SimpleFilter IS_APPROVED_FILTER = new SimpleFilter(FieldKey.fromParts("Approved"), AnnouncementManager.SPAM_MAGIC_DATE, CompareType.GT);
    public static final SimpleFilter IS_SPAM_FILTER = new SimpleFilter(FieldKey.fromParts("Approved"), AnnouncementManager.SPAM_MAGIC_DATE, CompareType.EQUAL);
    public static final SimpleFilter REQUIRES_REVIEW_FILTER = new SimpleFilter(FieldKey.fromParts("Approved"), null, CompareType.ISBLANK);

    public static void markAsSpam(Container c, AnnouncementModel ann)
    {
        updateApproved(c, ann, SPAM_MAGIC_DATE);
    }

    public static boolean isSpam(AnnouncementModel ann)
    {
        return SPAM_MAGIC_DATE.equals(ann.getApproved());
    }

    // Execute direct SQL (not Table.update())... I don't think we want to change Modified or ModifiedBy. Could consider adding column for Moderator, though.
    // Returns true if an update was made, false if not (e.g., message was already reviewed).
    private static boolean updateApproved(Container c, AnnouncementModel ann, Date date)
    {
        TableInfo ti = CommSchema.getInstance().getTableInfoAnnouncements();
        return new SqlExecutor(ti.getSchema()).execute("UPDATE " + ti.getSelectName() + " SET Approved = ? WHERE Container = ? AND RowId = ? AND Approved IS NULL", date, c, ann.getRowId()) > 0;
    }

    // Render and send all the email notifications on a background thread, #13143
    private static void sendNotificationEmails(final AnnouncementModel a, final WikiRendererType currentRendererType, final Container c, final User user)
    {
        Thread renderAndEmailThread = new Thread(() -> {
            Settings settings = DiscussionService.get().getSettings(c);

            boolean isResponse = null != a.getParent();
            AnnouncementModel parent = a;
            if (isResponse)
                parent = AnnouncementManager.getAnnouncement(c, a.getParent());

            //  See bug #6585 -- thread might have been deleted already
            if (null == parent)
                return;

            // Send a notification email to everyone on the member list.
            IndividualEmailPrefsSelector sel = new IndividualEmailPrefsSelector(c);
            Set<User> recipients = sel.getNotificationUsers(a);

            if (!recipients.isEmpty())
            {
                BulkEmailer emailer = new BulkEmailer(user);

                String messageId = "<" + a.getEntityId() + "@" + AppProps.getInstance().getDefaultDomain() + ">";
                String references = messageId + " <" + parent.getEntityId() + "@" + AppProps.getInstance().getDefaultDomain() + ">";

                List<Integer> memberList = a.getMemberListIds();

                for (User recipient : recipients)
                {
                    // Make sure the user hasn't lost their permission to read in this container since they were
                    // subscribed
                    if (c.hasPermission(recipient, ReadPermission.class))
                    {
                        Permissions perm = AnnouncementsController.getPermissions(c, recipient, settings);
                        ActionURL changePreferenceURL;
                        EmailNotificationBean.Reason reason;

                        if (memberList.contains(recipient.getUserId()))
                        {
                            reason = EmailNotificationBean.Reason.memberList;
                            changePreferenceURL = new ActionURL(AnnouncementsController.RemoveFromMemberListAction.class, c);
                            changePreferenceURL.addParameter("userId", String.valueOf(recipient.getUserId()));
                            changePreferenceURL.addParameter("messageId", String.valueOf(a.getRowId())); // Most recent member list is always attached to the most recent response
                        }
                        else
                        {
                            reason = EmailNotificationBean.Reason.signedUp;
                            changePreferenceURL = AnnouncementsController.getEmailPreferencesURL(c, AnnouncementsController.getBeginURL(c), a.lookupSrcIdentifer());
                        }

                        try
                        {
                            MultipartMessage m = getMessage(c, recipient, settings, perm, parent, a, isResponse, changePreferenceURL, currentRendererType, reason, user);
                            m.setHeader("References", references);
                            m.setHeader("Message-ID", messageId);

                            emailer.addMessage(recipient.getEmail(), m);
                        }
                        catch (Exception e)
                        {
                            ExceptionUtil.logExceptionToMothership(null, e);
                        }
                    }
                }

                emailer.run();  // We're already in a background thread... no need to start another one
            }
        });

        renderAndEmailThread.start();
    }

    private static MultipartMessage getMessage(Container c, User recipient, Settings settings, @NotNull Permissions perm, AnnouncementModel parent, AnnouncementModel a, boolean isResponse, ActionURL removeURL, WikiRendererType currentRendererType, EmailNotificationBean.Reason reason, User sender) throws Exception
    {
        ActionURL threadURL = AnnouncementsController.getThreadURL(c, parent.getEntityId(), a.getRowId());

        try (ViewContext.StackResetter ignore = ViewContext.pushMockViewContext(recipient, c, threadURL))
        {
            EmailNotificationBean notificationBean = new EmailNotificationBean(c, recipient, settings, perm, parent, a, isResponse, removeURL, currentRendererType, reason);

            NotificationEmailTemplate template = EmailTemplateService.get().getEmailTemplate(NotificationEmailTemplate.class, c);
            template.init(notificationBean, sender);
            MultipartMessage message = MailHelper.createMultipartMessage();
            template.renderAllToMessage(message, c);
            message.addHeader("List-Unsubscribe", template.emailPreferencesURL);

            return message;
        }
    }

    private static synchronized void saveMemberList(User user, List<Integer> userIds, int messageId)
    {
        // TODO: Should delete/insert only on diff
        if (null != userIds)
        {
            Table.delete(_comm.getTableInfoMemberList(), new SimpleFilter(FieldKey.fromParts("MessageId"), messageId));

            for (Integer userId : userIds)
                Table.insert(user, _comm.getTableInfoMemberList(), PageFlowUtil.map("MessageId", messageId, "UserId", userId));
        }
    }


    // Most recent member list is attached to the most recent approved post
    static List<Integer> getMemberList(AnnouncementModel ann)
    {
        SQLFragment sql = new SQLFragment("SELECT UserId FROM " + _comm.getTableInfoMemberList() + " WHERE MessageId = ?", ann.getRowId());

        return new SqlSelector(_comm.getSchema(), sql).getArrayList(Integer.class);
    }


    public static AnnouncementModel updateAnnouncement(User user, AnnouncementModel update, List<AttachmentFile> files) throws IOException
    {
        update.beforeUpdate(user);
        AnnouncementModel result = Table.update(user, _comm.getTableInfoAnnouncements(), update, update.getRowId());

        // Member list is attached to each post/response
        saveMemberList(user, result.getMemberListIds(), result.getRowId());

        try
        {
            AttachmentService.get().addAttachments(update.getAttachmentParent(), files, user);
        }
        finally
        {
            indexThread(update);
        }

        return result;
    }


    private static void deleteAnnouncement(AnnouncementModel ann)
    {
        Table.delete(_comm.getTableInfoAnnouncements(), ann.getRowId());
        // Delete the member list associated with this announcement
        Table.delete(_comm.getTableInfoMemberList(), new SimpleFilter(FieldKey.fromParts("MessageId"), ann.getRowId()));
        AttachmentService.get().deleteAttachments(ann.getAttachmentParent());
    }


    public static void deleteAnnouncement(Container c, int rowId)
    {
        DbSchema schema = _comm.getSchema();

        AnnouncementModel ann;

        try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
        {
            ann = getAnnouncement(c, rowId);
            if (ann != null)
            {
                deleteAnnouncement(ann);

                ann.getResponses().forEach(AnnouncementManager::deleteAnnouncement);
            }

            transaction.commit();
        }

        if (ann != null)
        {
            unindexThread(ann);
        }
    }


    public static void deleteUserFromAllMemberLists(User user)
    {
        Table.delete(_comm.getTableInfoMemberList(), new SimpleFilter(FieldKey.fromParts("UserId"), user.getUserId()));
    }

    // Deletes user from a single announcement (we should be passing in the most recent response). If the most recent response is then
    // deleted, the user will be back on the member list.
    public static void deleteUserFromMemberList(User user, int messageId)
    {
        Table.delete(_comm.getTableInfoMemberList(), new SimpleFilter(FieldKey.fromParts("UserId"), user.getUserId()).addCondition(FieldKey.fromParts("MessageId"), messageId));
    }

    public static int getUserEmailOption(Container c, User user, String srcIdentifier)
    {
        MessageConfigService.UserPreference emailPref = getAnnouncementConfigProvider().getPreference(c, user, srcIdentifier);

        //user has not yet defined email preference; return project default
        if (emailPref == null)
            return EmailOption.NOT_SET.getValue();
        else
            return emailPref.getEmailOptionId();
    }

    public static long getMessageCount(Container c)
    {
        return new TableSelector( _comm.getTableInfoAnnouncements(), SimpleFilter.createContainerFilter(c), null).getRowCount();
    }

    public static MessageConfigService.NotificationOption[] getEmailOptions()
    {
        return getAnnouncementConfigProvider().getOptions();
    }

    public static void saveDefaultEmailOption(Container c, int emailOption)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(c, "defaultEmailSettings", true);
        props.put("defaultEmailOption", Integer.toString(emailOption));
        props.save();
    }

    public static int getDefaultEmailOption(Container c)
    {
        Map<String, String> props = PropertyManager.getProperties(c, "defaultEmailSettings");

        if (props.isEmpty())
        {
            return EMAIL_DEFAULT_OPTION.getValue();
        }
        else
        {
            String option = props.get("defaultEmailOption");

            if (option != null)
                return validate(Integer.parseInt(option));
            else
                throw new IllegalStateException("Invalid stored property value.");
        }
    }

    private static int validate(int option)
    {
        if (EmailOption.isValid(option))
            return option;
        else
            return EmailOption.MESSAGES_NONE.getValue();
    }

    private static final String MESSAGE_BOARD_SETTINGS = "messageBoardSettings";

    public static void saveMessageBoardSettings(Container c, Settings settings)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(c, MESSAGE_BOARD_SETTINGS, true);
        props.clear();  // Get rid of old props (e.g., userList, see #13882)
        props.put("boardName", settings.getBoardName());
        props.put("conversationName", settings.getConversationName());
        props.put("secure", String.valueOf(settings.isSecure()));
        props.put("status", String.valueOf(settings.hasStatus()));
        props.put("expires", String.valueOf(settings.hasExpires()));
        props.put("assignedTo", String.valueOf(settings.hasAssignedTo()));
        props.put("formatPicker", String.valueOf(settings.hasFormatPicker()));
        props.put("memberList", String.valueOf(settings.hasMemberList()));
        props.put("sortOrderIndex", String.valueOf(settings.getSortOrderIndex()));
        props.put("defaultAssignedTo", null == settings.getDefaultAssignedTo() ? null : settings.getDefaultAssignedTo().toString());
        props.put("titleEditable", String.valueOf(settings.isTitleEditable()));
        props.put("includeGroups", String.valueOf(settings.includeGroups()));
        props.put("moderatorReview", settings.getModeratorReview());
        props.save();
    }

    public static Settings getMessageBoardSettings(Container c)
    {
        Map<String, String> props = PropertyManager.getProperties(c, MESSAGE_BOARD_SETTINGS);
        Settings settings = new Settings();
        settings.setDefaults();
        try
        {
            BeanUtils.populate(settings, props);
        }
        catch (IllegalAccessException | InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }
        return settings;
    }


    public static void purgeContainer(Container c)
    {
        // Attachments are handled by AttachmentServiceImpl
        ContainerUtil.purgeTable(_comm.getTableInfoEmailPrefs(), c, null);
        ContainerUtil.purgeTable(_comm.getTableInfoAnnouncements(), c, null);
    }

    public static void indexMessages(SearchService.IndexTask task, @NotNull Container c, Date modifiedSince)
    {
        indexMessages(task, c.getId(), modifiedSince, null);
    }


    // TODO: Fix inconsistency -- cid is @NotNull and we check c != null, yet some code below allows for c == null
    public static void indexMessages(final SearchService.IndexTask task, final @NotNull String containerId, Date modifiedSince, @Nullable String threadId)
    {
        assert null != containerId;
        if (null == containerId || (null != modifiedSince && null != threadId))
            throw new IllegalArgumentException();
        // make sure container still exists
        Container c = ContainerManager.getForId(containerId);
        if (null == c || isSecure(c))
            return;

        SQLFragment sql = new SQLFragment("SELECT EntityId FROM " + _comm.getTableInfoThreads());
        sql.append(" WHERE Container = ?");
        sql.add(containerId);
        String and = " AND ";

        if (null != threadId)
        {
            sql.append(and).append(" EntityId = ?");
            sql.add(threadId);
        }
        else
        {
            SQLFragment modified = new SearchService.LastIndexedClause(_comm.getTableInfoThreads(), modifiedSince, null).toSQLFragment(null, null);
            if (!modified.isEmpty())
                sql.append(and).append(modified);
        }

        // Push a ViewContext onto the stack before translating the bodies; announcements may need this to render embedded webparts.
        try (ViewContext.StackResetter ignored = ViewContext.pushMockViewContext(User.getSearchUser(), c, new ActionURL()))
        {
            new SqlSelector(_comm.getSchema(), sql).forEach(rs ->
            {
                String entityId = rs.getString(1);
                AnnouncementModel ann = AnnouncementManager.getAnnouncement(c, entityId);

                if (null != ann)
                {
                    String docid = "thread:" + entityId;
                    ActionURL url = new ActionURL(AnnouncementsController.ThreadAction.class, null);
                    url.setExtraPath(containerId);
                    url.addParameter("entityId", entityId);

                    Map<String, Object> props = new HashMap<>();
                    props.put(SearchService.PROPERTY.categories.toString(), searchCategory.toString());
                    props.put(SearchService.PROPERTY.title.toString(), ann.getTitle());  // Title is fine for both indexing and displaying

                    StringBuilder html = new StringBuilder(ann.translateBody());

                    for (AnnouncementModel response : ann.getResponses())
                        html.append(" ").append(response.translateBody());

                    SimpleDocumentResource sdr = new SimpleDocumentResource(
                            new Path(docid),
                            docid,
                            containerId,
                            "text/html",
                            html.toString(),
                            url,
                            UserManager.getUser(ann.getCreatedBy()), ann.getCreated(),
                            UserManager.getUser(ann.getModifiedBy()), ann.getModified(),
                            props)
                    {
                        @Override
                        public void setLastIndexed(long ms, long modified)
                        {
                            AnnouncementManager.setLastIndexed(entityId, ms);
                        }
                    };

                    task.addResource(sdr, SearchService.PRIORITY.item);
                }
            });
        }

        // Get the attachments... unfortunately, they're attached to individual announcementModels, not to the thread,
        // so we need a different query.
        // find all messages that have attachments
        sql = new SQLFragment("SELECT a.EntityId, MIN(CAST(a.Parent AS VARCHAR(36))) as parent, MIN(a.Title) AS title FROM " + _comm.getTableInfoAnnouncements() + " a INNER JOIN core.Documents d ON a.entityid = d.parent");
        sql.append("\nWHERE a.container = ?");
        sql.add(containerId);
        and = " AND ";
        if (null != threadId)
        {
            sql.append(and).append("(a.entityId = ? OR a.parent = ?)");
            sql.add(threadId);
            sql.add(threadId);
        }
        else
        {
            SQLFragment modified = new SearchService.LastIndexedClause(CoreSchema.getInstance().getTableInfoDocuments(), modifiedSince, "d").toSQLFragment(null, null);
            if (!modified.isEmpty())
                sql.append(and).append(modified);
        }
        sql.append("\nGROUP BY a.EntityId");

        final Collection<String> annIds = new HashSet<>();
        final Map<String, AnnouncementModel> map = new HashMap<>();

        new SqlSelector(_comm.getSchema(), sql).forEach(rs ->
        {
            String entityId = rs.getString(1);
            String parent = rs.getString(2);
            String title = rs.getString(3);

            annIds.add(entityId);
            AnnouncementModel ann = new AnnouncementModel();
            ann.setEntityId(entityId);
            ann.setParent(parent);
            ann.setContainer(containerId);
            ann.setTitle(title);
            map.put(entityId, ann);
        });

        if (!annIds.isEmpty())
        {
            List<Pair<String, String>> list = AttachmentService.get().listAttachmentsForIndexing(annIds, modifiedSince);
            ActionURL urlThread = new ActionURL(AnnouncementsController.ThreadAction.class, null);
            urlThread.setExtraPath(containerId);

            for (Pair<String, String> pair : list)
            {
                String entityId = pair.first;
                String documentName = pair.second;
                AnnouncementModel ann = map.get(entityId);
                ActionURL attachmentUrl = AnnouncementsController.getDownloadURL(ann, documentName);
                attachmentUrl.setExtraPath(ann.getContainerId());

                String e = StringUtils.isEmpty(ann.getParent()) ? ann.getEntityId() : ann.getParent();
                NavTree t = new NavTree("message", urlThread.clone().addParameter("entityId", e));
                String nav = NavTree.toJS(Collections.singleton(t), null, false, true).toString();

                String displayTitle = "\"" + documentName + "\" attached to message \"" + ann.getTitle() + "\"";
                WebdavResource attachmentRes = AttachmentService.get().getDocumentResource(
                        new Path(entityId, documentName),
                        attachmentUrl, displayTitle,
                        ann.getAttachmentParent(),
                        documentName, searchCategory);
                attachmentRes.getMutableProperties().put(SearchService.PROPERTY.navtrail.toString(), nav);
                task.addResource(attachmentRes, SearchService.PRIORITY.item);
            }
        }
    }


    private static boolean isSecure(@NotNull Container c)
    {
        return AnnouncementManager.getMessageBoardSettings(c).isSecure();
    }


    public static void setLastIndexed(String entityId, long ms)
    {
        new SqlExecutor(_comm.getSchema()).execute(
                "UPDATE comm.Announcements SET LastIndexed=? WHERE EntityId=?",
                new Timestamp(ms), entityId);
    }


    private static void unindexThread(@NotNull AnnouncementModel ann)
    {
        String docid = "thread:" + ann.getEntityId();
        SearchService ss = SearchService.get();
        if (null != ss)
        {
            ss.deleteResource(docid);
        }
        // Note: Attachments are unindexed by attachment service
    }


    static void indexThread(AnnouncementModel ann)
    {
        String parent = null == ann.getParent() ? ann.getEntityId() : ann.getParent();
        String container = ann.getContainerId();
        SearchService svc = SearchService.get();
        if (svc != null)
        {
            SearchService.IndexTask task = svc.defaultTask();
            // indexMessages is overkill, but I don't want to duplicate the code
            indexMessages(task, container, null, parent);
        }
    }

    public static String getUserDetailsLink(Container container, User currentUser, int formattedUserId, boolean includeGroups, boolean forEmail)
    {
        String result;

        // Email link shows display name and not html link
        if (forEmail)
        {
            result = PageFlowUtil.filter(UserManager.getDisplayName(formattedUserId, currentUser));
        }
        else
        {
            result = UserManager.getUserDetailsHTMLLink(container, currentUser, formattedUserId);
        }

        if (includeGroups)
        {
            String groupList = SecurityManager.getGroupList(container, UserManager.getUser(formattedUserId));
            if (groupList.length() > 0)
            {
                result += " (" + (groupList) + ")";
            }
        }

        return result;
    }


    public static class TestCase extends Assert
    {
        private void purgeAnnouncements(Container c, boolean verifyEmpty)
        {
            String deleteDocuments = "DELETE FROM " + _core.getTableInfoDocuments() + " WHERE Container = ? AND Parent IN (SELECT EntityId FROM " + _comm.getTableInfoAnnouncements() + " WHERE Container = ?)";
            int docs = new SqlExecutor(_comm.getSchema()).execute(deleteDocuments, c, c);
            String deleteAnnouncements = "DELETE FROM " + _comm.getTableInfoAnnouncements() + " WHERE Container = ?";
            int pages = new SqlExecutor(_comm.getSchema()).execute(deleteAnnouncements, c);

            if (verifyEmpty)
            {
                assertEquals(0, docs);
                assertEquals(0, pages);
            }
        }


        @Test
        public void testAnnouncements() throws Exception
        {
            TestContext context = TestContext.get();

            User user = context.getUser();
            assertNotNull("login before running this test", user);
            assertFalse("login before running this test", user.isGuest());

            Container c = JunitUtil.getTestContainer();

            purgeAnnouncements(c, false);

            int rowA;
            int rowResponse;
            {
                AnnouncementModel a = new AnnouncementModel();
                a.setTitle("new announcementModel");
                a.setBody("look at this");
                AnnouncementManager.insertAnnouncement(c, user, a, null);
                rowA = a.getRowId();
                assertTrue(0 != rowA);

                AnnouncementModel response = new AnnouncementModel();
                response.setParent(a.getEntityId());
                response.setTitle("response");
                response.setBody("bah");
                AnnouncementManager.insertAnnouncement(c, user, response, null);
                rowResponse = response.getRowId();
                assertTrue(0 != rowResponse);
            }

            {
                AnnouncementModel a = AnnouncementManager.getAnnouncement(c, rowA);
                assertNotNull(a);
                assertEquals("new announcementModel", a.getTitle());
                Collection<AnnouncementModel> responses = a.getResponses();
                assertEquals(1, responses.size());
                AnnouncementModel response = responses.iterator().next();
                assertEquals(a.getEntityId(), response.getParent());
                assertEquals("response", response.getTitle());
                assertEquals("bah", response.getBody());
            }

            {
                // this test makes more sense if/when getParent() return an AnnouncementModel
                AnnouncementModel response = AnnouncementManager.getAnnouncement(c, rowResponse);
                assertNotNull(response.getParent());
            }

            {
                AnnouncementManager.deleteAnnouncement(c, rowA);
                assertNull(AnnouncementManager.getAnnouncement(c, rowA));
            }

            // UNDONE: attachments, update, responses, ....

            purgeAnnouncements(c, true);
        }
    }

    public static class NotificationEmailTemplate extends UserOriginatedEmailTemplate
    {
        protected static final String DEFAULT_SUBJECT = "^messageSubject^";
        protected static final String DEFAULT_DESCRIPTION = "Message board notification for individual new post";
        protected static final String NAME = "Message board notification";
        protected static final String BODY_PATH = "/org/labkey/announcements/emailNotification.txt";

        private final List<EmailTemplate.ReplacementParam> _replacements = new ArrayList<>();

        private EmailNotificationBean notificationBean = null;
        private String reasonForEmail = "";
        private String attachments = "";
        private String messageUrl = "";
        private String emailPreferencesURL = "";

        public NotificationEmailTemplate()
        {
            super(NAME, DEFAULT_SUBJECT, loadBody(), DEFAULT_DESCRIPTION, ContentType.HTML);
            setEditableScopes(EmailTemplate.Scope.SiteOrFolder);

            _replacements.add(new ReplacementParam<>("createdByUser", String.class, "User that generated the message", ContentType.Plain)
            {
                public String getValue(Container c)
                {
                    if (notificationBean == null)
                        return null;

                    return getUserDetailsLink(c, notificationBean.recipient,  notificationBean.announcementModel.getCreatedBy(),notificationBean.includeGroups, true);
                }
            });

            _replacements.add(new ReplacementParam<>("createdOrResponded", String.class, "Created or Responded to a message", ContentType.HTML)
            {
                public String getValue(Container c)
                {
                    if (notificationBean == null)
                        return null;
                    return notificationBean.announcementModel.getParent() != null ? " responded" : " created a new " + PageFlowUtil.filter(notificationBean.settings.getConversationName().toLowerCase());
                }
            });

            _replacements.add(new ReplacementParam<>("messageDatetime", Date.class, "Date and time the message is created", ContentType.HTML)
            {
                public Date getValue(Container c)
                {
                    if (notificationBean == null)
                        return null;
                    return notificationBean.announcementModel.getCreated();
                }
            });

            _replacements.add(new ReplacementParam<>("messageUrl", String.class, "Link to the original message", ContentType.Plain)
            {
                public String getValue(Container c)
                {
                    return messageUrl;
                }
            });

            _replacements.add(new ReplacementParam<>("messageBody", String.class, "Message content, formatted as HTML", ContentType.HTML)
            {
                public String getValue(Container c)
                {
                    if (notificationBean == null)
                        return null;
                    return notificationBean.body == null ? "" : notificationBean.body;
                }
            });

            _replacements.add(new ReplacementParam<>("messageBodyText", String.class, "Message content plain text", ContentType.Plain)
            {
                public String getValue(Container c)
                {
                    if (notificationBean == null)
                        return null;
                    return notificationBean.bodyText == null ? "" : notificationBean.bodyText;
                }
            });

            _replacements.add(new ReplacementParam<>("messageSubject", String.class, "Message subject", ContentType.Plain)
            {
                public String getValue(Container c)
                {
                    if (notificationBean == null)
                        return null;
                    return StringUtils.trimToEmpty(notificationBean.isResponse ? "RE: " + notificationBean.parentModel.getTitle() : notificationBean.announcementModel.getTitle());
                }
            });

            _replacements.add(new ReplacementParam<>("attachments", String.class, "Attachments for this message", ContentType.HTML)
            {
                public String getValue(Container c)
                {
                    return attachments;
                }
            });

            _replacements.add(new ReplacementParam<>("reasonFooter", String.class, "Footer information explaining why user is receiving this message", ContentType.HTML)
            {
                public String getValue(Container c)
                {
                    return reasonForEmail;
                }
            });

            _replacements.add(new ReplacementParam<>("emailPreferencesURL", String.class, "Link to allow users to configure their notification preferences", ContentType.Plain)
            {
                public String getValue(Container c)
                {
                    return emailPreferencesURL;
                }
            });

            _replacements.addAll(super.getValidReplacements());
        }

        @Override
        public boolean hasMultipleContentTypes()
        {
            // Don't include plain text variant if the source of the message is HTML
            return super.hasMultipleContentTypes() &&
                    (notificationBean == null ||
                            notificationBean.announcementModel == null ||
                            !WikiRendererType.HTML.name().equals(notificationBean.announcementModel.getRendererType()));
        }

        public List<ReplacementParam> getValidReplacements()
        {
            return _replacements;
        }

        private static String loadBody()
        {
            try
            {
                try (InputStream is = NotificationEmailTemplate.class.getResourceAsStream(BODY_PATH))
                {
                    return PageFlowUtil.getStreamContentsAsString(is);
                }
            }
            catch (IOException e)
            {
                throw new UnexpectedException(e);
            }
        }

        public void init(EmailNotificationBean notification, User sender)
        {
            notificationBean = notification;
            setOriginatingUser(sender);
            initReason();
            initAttachments();
        }

        private void initReason()
        {
            StringBuilder sb = new StringBuilder();

            if (notificationBean.reason == EmailNotificationBean.Reason.signedUp)
            {
                sb.append("You have received this email because you are signed up to receive notifications about new posts to <a href=\"");
                sb.append(PageFlowUtil.filter(notificationBean.boardURL.getURIString()));
                sb.append("\">");
                sb.append(PageFlowUtil.filter(notificationBean.boardPath));
                sb.append("</a> at <a href=\"");
                sb.append(PageFlowUtil.filter(notificationBean.siteURL));
                sb.append("\">");
                sb.append(PageFlowUtil.filter(notificationBean.siteURL));
                sb.append("</a>. If you no longer wish to receive these notifications you can <a href=\"");
                sb.append(PageFlowUtil.filter(notificationBean.removeURL.getURIString()));
                sb.append("\">change your email preferences</a>.");
            }
            else
            {
                sb.append("<p>You have received this email because you are on the member list for this ");
                sb.append(PageFlowUtil.filter(notificationBean.settings.getConversationName().toLowerCase()));
                sb.append(". You must login to respond to this message. If you no longer wish to receive these notifications you can remove yourself from the member list by ");
                sb.append("<a href=\"");
                sb.append(PageFlowUtil.filter(notificationBean.removeURL.getURIString()));
                sb.append("\">clicking here</a>.</p>");
            }

            reasonForEmail = sb.toString();
            emailPreferencesURL = notificationBean.removeURL.getURIString();
        }

        private void initAttachments()
        {
            AnnouncementModel announcementModel = notificationBean.announcementModel;
            if (announcementModel == null)
                return;

            StringBuilder sb = new StringBuilder();
            String separator = "";

            if (!announcementModel.getAttachments().isEmpty())
            {
                sb.append("Attachments: ");
                for (Attachment attachment : announcementModel.getAttachments())
                {
                    ActionURL downloadURL = AnnouncementsController.getDownloadURL(announcementModel, attachment.getName());
                    sb.append(separator);
                    separator = ", ";
                    sb.append("<a href=\"").append(PageFlowUtil.filter(downloadURL.getURIString())).append("\">").append(PageFlowUtil.filter(attachment.getName())).append("</a>");
                }
            }
            attachments = sb.toString();
            messageUrl = announcementModel.getParent() == null ? notificationBean.threadURL.getURIString() : notificationBean.threadParentURL.getURIString();
        }
    }

    public static class EmailNotificationBean
    {
        private final User recipient;
        private final ActionURL threadURL;
        private final ActionURL threadParentURL;
        private final String boardPath;
        private final ActionURL boardURL;
        private final String siteURL;
        private final AnnouncementModel announcementModel;
        private final AnnouncementModel parentModel;
        private final boolean isResponse;
        private final String body;
        private final String bodyText;
        private final Settings settings;
        private final ActionURL removeURL;
        private final Reason reason;
        private final boolean includeGroups;

        public enum Reason { signedUp, memberList }

        public EmailNotificationBean(Container c,
                                     User recipient, Settings settings, @NotNull Permissions perm, AnnouncementModel parent,
                                     AnnouncementModel a, boolean isResponse, ActionURL removeURL, WikiRendererType currentRendererType, EmailNotificationBean.Reason reason)
        {
            this.recipient = recipient;
            this.threadURL = new ActionURL(AnnouncementsController.ThreadAction.class, c).addParameter("rowId", a.getRowId());
            this.threadParentURL = new ActionURL(AnnouncementsController.ThreadAction.class, c).addParameter("rowId", parent.getRowId());
            this.boardPath = c.getPath();
            this.boardURL = AnnouncementsController.getBeginURL(c);
            this.siteURL = ActionURL.getBaseServerURL();
            this.announcementModel = a;
            this.parentModel = parent;
            this.isResponse = isResponse;
            this.removeURL = removeURL;
            this.settings = settings;
            this.reason = reason;
            this.includeGroups = perm.includeGroups();
            this.bodyText = a.getBody();
            if (!settings.isSecure())
            {
                WikiService wikiService = WikiService.get();
                this.body = null != wikiService ? wikiService.getFormattedHtml(currentRendererType, a.getBody()) : null;
            }
            else
            {
                this.body = a.getBody();
            }
        }
    }
}
