/*
 * Copyright (c) 2011-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.announcements.model;

import org.apache.log4j.Logger;
import org.labkey.announcements.AnnouncementsController;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.message.digest.MessageDigest;
import org.labkey.api.security.User;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;

import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * User: klum
 * Date: Jan 13, 2011
 * Time: 5:29:04 PM
 */
public class AnnouncementDigestProvider implements MessageDigest.Provider
{
    private static final CommSchema _comm = CommSchema.getInstance();
    private static final CoreSchema _core = CoreSchema.getInstance();
    private static final Logger _log = Logger.getLogger(AnnouncementDigestProvider.class);

    // Retrieve from this container all messages with a body or attachments posted during the given timespan
    // Messages are grouped by thread and threads are sorted by earliest post within each thread
    private static final String RECENT_ANN_SQL = "SELECT annModel.* FROM\n" +
            "\t(\n" +
            "\tSELECT Thread, MIN(Created) AS Earliest FROM\n" +
            "\t\t(SELECT Created, CASE WHEN Parent IS NULL THEN EntityId ELSE Parent END AS Thread FROM " + _comm.getTableInfoAnnouncements() + " annModel LEFT OUTER JOIN\n" +
            "\t\t\t(SELECT DISTINCT(Parent) AS DocParent FROM " + _core.getTableInfoDocuments() + ") doc ON annModel.EntityId = DocParent\n" +
            "\t\t\tWHERE Container = ? AND Created >= ? AND Created < ? AND (Body IS NOT NULL OR DocParent IS NOT NULL)) x\n" +
            "\tGROUP BY Thread\n" +
            "\t) X LEFT OUTER JOIN " + _comm.getTableInfoAnnouncements() + " annModel ON Parent = Thread OR EntityId = Thread LEFT OUTER JOIN\n" +
            "\t\t(SELECT DISTINCT(Parent) AS DocParent FROM " + _core.getTableInfoDocuments() + ") doc ON annModel.EntityId = DocParent\n" +
            "WHERE Container = ? AND Created >= ? AND Created < ? AND (Body IS NOT NULL OR DocParent IS NOT NULL)\n" +
            "ORDER BY Earliest, Thread, Created";

    @Override
    public void sendDigestForAllContainers(Date start, Date end) throws Exception
    {
        SQLFragment sql = new SQLFragment("SELECT DISTINCT(Container) FROM " + _comm.getTableInfoAnnouncements() + " WHERE Created >= ? and Created < ?", start, end);
        Collection<String> containerIds = new SqlSelector(_comm.getSchema(), sql).getCollection(String.class);

        for (String id : containerIds)
        {
            Container c = ContainerManager.getForId(id);
            if (c != null)
                sendDigest(c, start, end);
        }
    }

    private void sendDigest(Container c, Date start, Date end) throws Exception
    {
        DiscussionService.Settings settings = AnnouncementManager.getMessageBoardSettings(c);
        Collection<AnnouncementModel> announcements = getRecentAnnouncementsInContainer(c, start, end);

        DailyDigestEmailPrefsSelector sel = new DailyDigestEmailPrefsSelector(c);

        for (User recipient : sel.getNotificationCandidates())
        {
            List<AnnouncementModel> announcementsForRecipient = new ArrayList<>(announcements.size());

            for (AnnouncementModel ann : announcements)
                if (sel.shouldSend(ann, recipient))
                    announcementsForRecipient.add(ann);

            if (!announcementsForRecipient.isEmpty())
            {
                Permissions perm = AnnouncementsController.getPermissions(c, recipient, settings);
                MailHelper.MultipartMessage m = getDailyDigestMessage(c, settings, perm, announcementsForRecipient, recipient);

                try
                {
                    MailHelper.send(m, null, c);
                }
                catch (ConfigurationException e)
                {
                    // Just record these exceptions to the local log (don't send to mothership)
                    _log.error(e.getMessage());
                }
            }
        }
    }

    private static Collection<AnnouncementModel> getRecentAnnouncementsInContainer(Container c, Date min, Date max)
    {
        return new SqlSelector(_comm.getSchema(), RECENT_ANN_SQL, c, min, max, c, min, max).getCollection(AnnouncementModel.class);
    }

    private static MailHelper.MultipartMessage getDailyDigestMessage(Container c, DiscussionService.Settings settings, Permissions perm, List<AnnouncementModel> announcementModels, User recipient) throws Exception
    {
        DailyDigestBean bean = new DailyDigestBean(c, recipient, settings, perm, announcementModels);
        DailyDigestEmailTemplate template = EmailTemplateService.get().getEmailTemplate(DailyDigestEmailTemplate.class, c);
        template.init(bean);

        MailHelper.MultipartMessage message = MailHelper.createMultipartMessage();
        template.renderAllToMessage(message, c);
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipient.getEmail()));

        return message;
    }

    public static class DailyDigestEmailTemplate extends EmailTemplate
    {
        protected static final String DEFAULT_SUBJECT = "New posts to ^folderName^";
        protected static final String DEFAULT_DESCRIPTION = "Message board daily digest notification";
        protected static final String NAME = "Message board daily digest";
        protected static final String BODY_PATH = "/org/labkey/announcements/dailyDigest.txt";

        private final List<EmailTemplate.ReplacementParam> _replacements = new ArrayList<>();

        private DailyDigestBean dailyDigestBean = null;
        private String reasonForEmail;
        private String posts;

        public DailyDigestEmailTemplate()
        {
            super(NAME, DEFAULT_SUBJECT, loadBody(), DEFAULT_DESCRIPTION, ContentType.HTML);
            setEditableScopes(EmailTemplate.Scope.SiteOrFolder);

            _replacements.add(new ReplacementParam<String>("folderName", String.class, "Folder that user subscribed to", ContentType.Plain)
            {
                public String getValue(Container c)
                {
                    return c.getPath();
                }
            });

            _replacements.add(new ReplacementParam<String>("postList", String.class, "List of new posts", ContentType.HTML)
            {
                public String getValue(Container c)
                {
                    return posts;
                }
            });

            _replacements.add(new ReplacementParam<String>("reasonFooter", String.class, "Footer message explaining why user is receiving this digest", ContentType.HTML)
            {
                public String getValue(Container c)
                {
                    return reasonForEmail;
                }
            });

            _replacements.addAll(super.getValidReplacements());
        }

        public List<ReplacementParam> getValidReplacements()
        {
            return _replacements;
        }

        private static String loadBody()
        {
            try
            {
                try (InputStream is = DailyDigestEmailTemplate.class.getResourceAsStream(BODY_PATH))
                {
                    return PageFlowUtil.getStreamContentsAsString(is);
                }
            }
            catch (IOException e)
            {
                throw new UnexpectedException(e);
            }
        }

        public void init(DailyDigestBean bean)
        {
            this.dailyDigestBean = bean;
            initReason();
            initPosts();
        }

        private void initReason()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("You have received this email because you are signed up for a daily digest of new posts to <a href=\"");
            sb.append(PageFlowUtil.filter(dailyDigestBean.boardURL.getURIString()));
            sb.append("\">");
            sb.append(PageFlowUtil.filter(dailyDigestBean.boardPath));
            sb.append("</a> at <a href=\"");
            sb.append(PageFlowUtil.filter(dailyDigestBean.siteUrl));
            sb.append("\">");
            sb.append(PageFlowUtil.filter(dailyDigestBean.siteUrl));
            sb.append("</a>. If you no longer wish to receive these notifications, please <a href=\"");
            sb.append(PageFlowUtil.filter(dailyDigestBean.removeURL.getURIString()));
            sb.append("\">change your email preferences</a>.");

            reasonForEmail = sb.toString();
        }

        private void initPosts()
        {
            StringBuilder sb = new StringBuilder();
            String previousThread = null;
            ActionURL threadURL = null;

            for (AnnouncementModel ann : dailyDigestBean.announcementModels)
            {
                if (null == ann.getParent() || !ann.getParent().equals(previousThread))
                {
                    if (null == ann.getParent())
                        previousThread = ann.getEntityId();
                    else
                        previousThread = ann.getParent();

                    if (null != threadURL)
                    {
                        sb.append("<tr><td><a href=\"")
                                .append(threadURL.getURIString())
                                .append("\">View this ")
                                .append(PageFlowUtil.filter(dailyDigestBean.conversationName))
                                .append("</a></td></tr>");
                    }

                    threadURL = AnnouncementsController.getThreadURL(dailyDigestBean.c, previousThread, ann.getRowId());
                    sb.append("<tr><td>&nbsp;</td></tr><tr style=\"background:#F4F4F4;\"><td colspan=\"2\" style=\"border: solid 1px #808080\">");
                    sb.append(PageFlowUtil.filter(ann.getTitle())).append("</td></tr>");
                }

                int attachmentCount = ann.getAttachments().size();
                sb.append("<tr><td>");
                sb.append(ann.getCreatedByName(dailyDigestBean.includeGroups, dailyDigestBean.recipient, true, true));

                if (null == ann.getParent())
                {
                    sb.append(" created this ");
                    sb.append(PageFlowUtil.filter(dailyDigestBean.conversationName));
                }
                else
                {
                    sb.append(" responded");
                }
                sb.append(" at ");

                // Always filter formatted dates in HTML, #30986
                sb.append(PageFlowUtil.filter(DateUtil.formatDateTime(dailyDigestBean.c, ann.getCreated())));

                if (attachmentCount > 0)
                {
                    sb.append(" and attached ");
                    sb.append(attachmentCount);
                    sb.append(" document");
                    if (attachmentCount > 1)
                        sb.append("s");
                }

                if (!dailyDigestBean.settings.isSecure())
                {
                    String body = ann.getFormattedHtml();
                    sb.append("<tr><td style=\"padding-left:35px;\">");
                    sb.append(body);
                    sb.append("</td></tr>");
                }
            }

            if (null != threadURL)
            {
                sb.append("<tr><td><a href=\"");
                sb.append(threadURL.getURIString());
                sb.append("\">View this ");
                sb.append(PageFlowUtil.filter(dailyDigestBean.conversationName));
                sb.append("</a></td></tr>");
            }
            posts = sb.toString();
        }
    }

    public static class DailyDigestBean
    {
        private final Container c;
        private final User recipient;
        private final List<AnnouncementModel> announcementModels;
        private final String conversationName;
        private final DiscussionService.Settings settings;
        private final ActionURL boardURL;
        private final String boardPath;
        private final String siteUrl;
        private final ActionURL removeURL;
        private final boolean includeGroups;

        public DailyDigestBean(Container c, User recipient, DiscussionService.Settings settings, Permissions perm, List<AnnouncementModel> announcementModels)
        {
            this.c = c;
            this.recipient = recipient;
            this.conversationName = settings.getConversationName().toLowerCase();
            this.settings = settings;
            this.announcementModels = announcementModels;
            this.boardPath = c.getPath();
            this.boardURL = AnnouncementsController.getBeginURL(c);
            this.siteUrl = ActionURL.getBaseServerURL();
            this.removeURL = new ActionURL(AnnouncementsController.EmailPreferencesAction.class, c);
            this.includeGroups = perm.includeGroups();
        }
    }
}

