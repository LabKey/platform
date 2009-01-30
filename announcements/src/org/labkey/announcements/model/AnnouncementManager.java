/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.*;
import org.labkey.api.view.NotFoundException;
import org.labkey.common.util.Pair;

import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * User: mbellew
 * Date: Mar 11, 2005
 * Time: 10:08:26 AM
 */
public class AnnouncementManager
{
    private static CommSchema _comm = CommSchema.getInstance();
    private static CoreSchema _core = CoreSchema.getInstance();

    public static final int EMAIL_PREFERENCE_DEFAULT = -1;
    public static final int EMAIL_PREFERENCE_NONE = 0;
    public static final int EMAIL_PREFERENCE_ALL = 1;
    public static final int EMAIL_PREFERENCE_MINE = 2;    //Only threads I've posted to or where I'm on the member list
    public static final int EMAIL_PREFERENCE_MASK = 255; 

    public static final int EMAIL_NOTIFICATION_TYPE_DIGEST = 256; // If this bit is set, send daily digest instead of individual email for each post

    public static final int EMAIL_DEFAULT_OPTION = EMAIL_PREFERENCE_MINE;

    //    public static final int EMAIL_FORMAT_TEXT = 0;
    public static final int EMAIL_FORMAT_HTML = 1;

    public static final int PAGE_TYPE_MESSAGE = 0;
    public static final int PAGE_TYPE_WIKI = 1;

    private static Logger _log = Logger.getLogger(AnnouncementManager.class);

    private AnnouncementManager()
    {
    }

    protected static void attachAttachments(Announcement[] announcements) throws SQLException
    {
        AttachmentService.get().setAttachments(Arrays.<AttachmentParent>asList(announcements));
    }


    protected static void attachResponses(Container c, Announcement[] announcements) throws SQLException
    {
        for (Announcement announcement : announcements)
        {
            Announcement[] res = getAnnouncements(c, announcement.getEntityId());
            announcement.setResponses(Arrays.asList(res));
        }
    }


    protected static void attachMemberLists(Announcement[] announcements) throws SQLException
    {
        for (Announcement announcement : announcements)
            announcement.setMemberList(getMemberList(announcement));
    }


    // Get first rowlimit threads in this container, filtered using filter
    public static Pair<Announcement[], Boolean> getAnnouncements(Container c, SimpleFilter filter, Sort sort, int rowLimit)
    {
        filter.addCondition("Container", c.getId());

        try
        {
            Announcement[] recent = Table.select(_comm.getTableInfoThreads(), Table.ALL_COLUMNS, filter, sort, Announcement.class, rowLimit + 1, 0);

            Boolean limited = (recent.length > rowLimit);

            if (limited)
                recent = (Announcement[])ArrayUtils.subarray(recent, 0, rowLimit);

            attachAttachments(recent);
            return new Pair<Announcement[], Boolean>(recent, limited);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    // marker for non fully loaded announcement
    public static class BareAnnouncement extends Announcement
    {
    }


    // Get all threads in this container, filtered using filter, no attachments, no responses
    public static Announcement[] getBareAnnouncements(Container c, SimpleFilter filter, Sort sort)
    {
        filter.addCondition("Container", c.getId());

        try
        {
            Announcement[] recent = Table.select(_comm.getTableInfoThreads(), Table.ALL_COLUMNS, filter, sort, BareAnnouncement.class);
            return recent;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static Announcement[] getAnnouncements(Container c, String parent)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("container", c.getId());
        if (null == parent)
            filter.addCondition("parent", null, CompareType.ISBLANK);
        else
            filter.addCondition("parent", parent);

        Sort sort = new Sort("Created");

        try
        {
            Announcement[] ann = Table.select(_comm.getTableInfoAnnouncements(),
                    Table.ALL_COLUMNS, filter, sort, Announcement.class);

            attachAttachments(ann);
            return ann;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static Announcement getAnnouncement(Container c, String entityId) throws SQLException
    {
        return getAnnouncement(c, entityId, false);
    }


    public static Announcement getAnnouncement(Container c, String entityId, boolean eager) throws SQLException
    {
        Announcement[] ann = Table.select(_comm.getTableInfoAnnouncements(), Table.ALL_COLUMNS,
                new SimpleFilter("container", c.getId()).addCondition("entityId", entityId),
                null, Announcement.class);
        if (ann.length < 1)
            return null;
        attachAttachments(ann);
        if (eager)
        {
            attachResponses(c, ann);
            attachMemberLists(ann);
        }
        return ann[0];
    }


    public static Announcement getAnnouncement(Container c, int rowId) throws SQLException
    {
        return getAnnouncement(c, rowId, INCLUDE_ATTACHMENTS);
    }

    public static void saveEmailPreference(User user, Container c, int emailPreference) throws SQLException
    {
        saveEmailPreference(user, c, user, emailPreference);
    }

    public static synchronized void saveEmailPreference(User currentUser, Container c, User projectUser, int emailPreference) throws SQLException
    {
        //determine whether user has record in EmailPrefs table.
        EmailPref emailPref = getUserEmailPrefRecord(c, projectUser);

        //insert new if user preference record does not yet exist
        if (null == emailPref && emailPreference != EMAIL_PREFERENCE_DEFAULT)
        {
            emailPref = new EmailPref();
            emailPref.setContainer(c.getId());
            emailPref.setUserId(projectUser.getUserId());
            emailPref.setEmailFormatId(EMAIL_FORMAT_HTML);
            emailPref.setEmailOptionId(emailPreference);
            emailPref.setPageTypeId(PAGE_TYPE_MESSAGE);
            emailPref.setLastModifiedBy(currentUser.getUserId());
            Table.insert(currentUser, _comm.getTableInfoEmailPrefs(), emailPref);
        }
        else
        {
            if (emailPreference == EMAIL_PREFERENCE_DEFAULT)
            {
                //if preference has been set back to default, delete user's email pref record
                SimpleFilter filter = new SimpleFilter();
                filter.addCondition("UserId", projectUser.getUserId());
                Table.delete(_comm.getTableInfoEmailPrefs(), filter);
            }
            else
            {
                //otherwise update if it already exists
                emailPref.setEmailOptionId(emailPreference);
                emailPref.setLastModifiedBy(currentUser.getUserId());
                Table.update(currentUser, _comm.getTableInfoEmailPrefs(), emailPref,
                        new Object[]{c.getId(), projectUser.getUserId()}, null);
            }
        }
    }


    public static final int INCLUDE_ATTACHMENTS = 1;
    public static final int INCLUDE_RESPONSES = 2;
    public static final int INCLUDE_MEMBERLIST = 4;

    public static Announcement getAnnouncement(Container c, int rowId, int mask) throws SQLException
    {
        Announcement[] ann = Table.select(_comm.getTableInfoAnnouncements(), Table.ALL_COLUMNS,
                new SimpleFilter("container", c.getId()).addCondition("rowId", new Integer(rowId)),
                null, Announcement.class);
        if (ann.length < 1)
            return null;

        if ((mask & INCLUDE_ATTACHMENTS) != 0)
            attachAttachments(ann);
        if ((mask & INCLUDE_RESPONSES) != 0)
            attachResponses(c, ann);
        if ((mask & INCLUDE_MEMBERLIST) != 0)
            attachMemberLists(ann);
        return ann[0];
    }


    public static Announcement getLatestPost(Container c, Announcement parent)
    {
        try
        {
            Integer postId = Table.executeSingleton(_comm.getSchema(), "SELECT LatestId FROM " + _comm.getTableInfoThreads() + " WHERE RowId=?", new Object[]{parent.getRowId()}, Integer.class);

            if (null == postId)
                throw new NotFoundException("Can't find most recent post");

            return getAnnouncement(c, postId, INCLUDE_MEMBERLIST);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static void insertAnnouncement(Container c, User user, Announcement insert, List<AttachmentFile> files) throws SQLException, IOException, AttachmentService.DuplicateFilenameException
    {
        insert.beforeInsert(user, c.getId());
        Announcement ann = Table.insert(user, _comm.getTableInfoAnnouncements(), insert);

        List<User> users = ann.getMemberList();
        
        if (users != null)
        {
            // Always attach member list to initial message
            int rowId = (null == ann.getParent() ? ann.getRowId() : getParentRowId(ann));
            insertMemberList(user, ann.getMemberList(), rowId);
        }

        AttachmentService.get().addAttachments(user, insert, files);
    }


    private static synchronized void insertMemberList(User user, List<User> users, int messageId) throws SQLException
    {
        // TODO: Should delete/insert only on diff
        if (null != users)
        {
            Table.delete(_comm.getTableInfoMemberList(), new SimpleFilter("MessageId", messageId));

            for (User u : users)
                Table.insert(user, _comm.getTableInfoMemberList(), PageFlowUtil.map("MessageId", messageId, "UserId", u.getUserId()));
        }
    }


    private static int getParentRowId(Announcement ann) throws SQLException
    {
        return Table.executeSingleton(_comm.getSchema(), "SELECT RowId FROM " + _comm.getTableInfoAnnouncements() + " WHERE EntityId=?", new Object[]{ann.getParent()}, Integer.class);
    }


    private static List<User> getMemberList(Announcement ann) throws SQLException
    {
        Integer[] userIds;

        if (null == ann.getParent())
            userIds = Table.executeArray(_comm.getSchema(), "SELECT UserId FROM " + _comm.getTableInfoMemberList() + " WHERE MessageId = ?", new Object[]{ann.getRowId()}, Integer.class);
        else
            userIds = Table.executeArray(_comm.getSchema(), "SELECT UserId FROM " + _comm.getTableInfoMemberList() + " WHERE MessageId = (SELECT RowId FROM " + _comm.getTableInfoAnnouncements() + " WHERE EntityId=?)", new Object[]{ann.getParent()}, Integer.class);

        List<User> users = new ArrayList<User>(userIds.length);

        for (int userId : userIds)
            users.add(UserManager.getUser(userId));

        return users;
    }


    public static void updateAnnouncement(User user, Announcement update) throws SQLException
    {
        update.beforeUpdate(user);
        Table.update(user, _comm.getTableInfoAnnouncements(), update, update.getRowId(), null);
    }


    private static void deleteAnnouncement(Announcement ann) throws SQLException
    {
        Table.delete(_comm.getTableInfoAnnouncements(), ann.getRowId(), null);
        AttachmentService.get().deleteAttachments(ann);
    }


    public static void deleteAnnouncement(Container c, int rowId) throws SQLException
    {
        DbSchema schema = _comm.getSchema();
        boolean startedTransaction = false;

        try
        {
            if (!schema.getScope().isTransactionActive())
            {
                schema.getScope().beginTransaction();
                startedTransaction = true;
            }
            Announcement ann = getAnnouncement(c, rowId, INCLUDE_RESPONSES);
            if (ann != null)
            {
                deleteAnnouncement(ann);

                // Delete the member list associated with this thread
                Table.delete(_comm.getTableInfoMemberList(), new SimpleFilter("MessageId", ann.getRowId()));

                Collection<Announcement> responses = ann.getResponses();
                if (null == responses)
                    return;
                for (Announcement response : responses)
                {
                    deleteAnnouncement(response);
                }
            }

            if (startedTransaction)
                schema.getScope().commitTransaction();
        }
        finally
        {
            if (startedTransaction && schema.getScope().isTransactionActive())
                schema.getScope().rollbackTransaction();
        }
    }
    

    public static void deleteUserFromAllMemberLists(User user) throws SQLException
    {
        Table.delete(_comm.getTableInfoMemberList(), new SimpleFilter("UserId", user.getUserId()));
    }

    public static void deleteUserFromMemberList(User user, int messageId) throws SQLException
    {
        Table.delete(_comm.getTableInfoMemberList(), new SimpleFilter("UserId", user.getUserId()).addCondition("MessageId", messageId));
    }

    public static Set<User> getAuthors(Container c, Announcement a) throws SQLException
    {
        Set<User> responderSet = new HashSet<User>();
        boolean isResponse = null != a.getParent();

        // if this is a response get parent and all previous responses
        if (isResponse)
        {
            a = AnnouncementManager.getAnnouncement(c, a.getParent(), true);

            Collection<Announcement> responses = a.getResponses();

            //add creator of each response to responder set
            for (Announcement response : responses)
            {
                //do we need to handle case where responder is not in a project group?
                User user = UserManager.getUser(response.getCreatedBy());
                //add to responder set, so we know who responders are
                responderSet.add(user);
            }
        }

        //add creator of parent to responder set
        responderSet.add(UserManager.getUser(a.getCreatedBy()));

        return responderSet;
    }

    public static int getUserEmailOption(Container c, User user) throws SQLException
    {
        EmailPref emailPref = getUserEmailPrefRecord(c, user);

        //user has not yet defined email preference; return project default
        if (emailPref == null)
            return EMAIL_PREFERENCE_DEFAULT;
        else
            return emailPref.getEmailOptionId();
    }

    //returns explicit record from EmailPrefs table for this user if there is one.
    private static EmailPref getUserEmailPrefRecord(Container c, User user) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("Container", c.getId());
        filter.addCondition("UserId", user.getUserId());

        //return records only for those users who have explicitly set a preference for this container.
        EmailPref[] emailPrefs = Table.select(
                _comm.getTableInfoEmailPrefs(),
                Table.ALL_COLUMNS,
                filter,
                null,
                EmailPref.class
                );

        if (emailPrefs.length == 0)
            return null;
        else
            return emailPrefs[0];
    }

    static EmailPref[] getEmailPrefs(Container c, User user) throws SQLException
    {
        EmailPref[] emailPrefs;

        //return records for all project users, including those who have not explicitly set preference
        Object[] param;
        String sql = _emailPrefsSql;
        if (user != null)
        {
            //if looking for single user, add in user criteria
            sql += "AND core.Members.UserId = ?";
            param =  new Object[]{c.getId(), c.getProject().getId(), c.getId(), user.getUserId()};
        }
        else
            param = new Object[]{c.getId(), c.getProject().getId(), c.getId()};

        emailPrefs = Table.executeQuery(
                _comm.getSchema(),
                sql,
                param,
                EmailPref.class
                );

        return emailPrefs;
    }

    public static ResultSet getEmailPrefsResultset(Container c) throws SQLException
    {
        Container cProject = c.getProject();
        Object[] param = new Object[]{c.getId(), cProject.getId(), c.getId()};
        return Table.executeQuery(CommSchema.getInstance().getSchema(), _emailPrefsSql, param);
    }

    private static final String _emailPrefsSql;

    static
    {
        StringBuilder sql = new StringBuilder();

        sql.append("(SELECT DISTINCT core.Members.UserId, core.Principals.Name AS Email, core.UsersData.FirstName, core.UsersData.LastName, core.UsersData.DisplayName, ");
        sql.append("\nCASE WHEN comm.EmailPrefs.EmailOptionId IS NULL THEN '<folder default>' ELSE comm.EmailOptions.EmailOption END AS EmailOption, comm.EmailOptions.EmailOptionId, ");
        sql.append("\nP1.UserId AS LastModifiedBy, P1.Name AS LastModifiedByName ");
        sql.append("\nFROM core.Members ");
        sql.append("\nLEFT JOIN core.UsersData ON core.Members.UserId = core.UsersData.UserId ");
        sql.append("\nLEFT JOIN comm.EmailPrefs ON core.Members.UserId = comm.EmailPrefs.UserId AND comm.EmailPrefs.Container = ? ");
        sql.append("\nLEFT JOIN comm.EmailOptions ON comm.EmailPrefs.EmailOptionId = comm.EmailOptions.EmailOptionId ");
        sql.append("\nLEFT JOIN core.Principals ON core.Members.UserId = core.Principals.UserId ");
        sql.append("\nLEFT JOIN core.Principals AS P1 ON P1.UserId = comm.EmailPrefs.LastModifiedBy ");
        sql.append("\nWHERE (core.Members.GroupId IN (SELECT UserId FROM core.Principals WHERE Type = 'g' AND Container = ?))) ");
        sql.append("\nUNION ");
        sql.append("\n(SELECT comm.EmailPrefs.UserId, core.Principals.Name AS Email, core.UsersData.FirstName, ");
        sql.append("\ncore.UsersData.LastName, core.UsersData.DisplayName, comm.EmailOptions.EmailOption, comm.EmailPrefs.EmailOptionId, ");
        sql.append("\nP1.UserId AS LastModifiedBy, P1.Name AS LastModifiedByName ");
        sql.append("\nFROM comm.EmailPrefs ");
        sql.append("\nLEFT JOIN core.Principals ON comm.EmailPrefs.UserId = core.Principals.UserId ");
        sql.append("\nLEFT JOIN core.UsersData ON core.Principals.UserId = core.UsersData.UserId ");
        sql.append("\nLEFT JOIN comm.EmailOptions ON comm.EmailPrefs.EmailOptionId = comm.EmailOptions.EmailOptionId ");
        sql.append("\nLEFT JOIN core.Principals AS P1 ON P1.UserId = comm.EmailPrefs.LastModifiedBy ");
        sql.append("\nWHERE comm.EmailPrefs.Container = ?) ");
        sql.append("\nORDER BY Email");

        _emailPrefsSql = sql.toString();
    }


    public static long getMessageCount(Container c)
            throws SQLException
    {
        return Table.executeSingleton(_comm.getSchema(), "SELECT COUNT(*) FROM " + _comm.getTableInfoAnnouncements() + " WHERE Container = ?", new Object[]{c.getId()}, Long.class);
    }

    public static EmailOption[] getEmailOptions() throws SQLException
    {
        return Table.select(_comm.getTableInfoEmailOptions(),
                Table.ALL_COLUMNS,
                null,
                new Sort("EmailOptionId"),
                EmailOption.class
                );
    }

    public static void saveDefaultEmailOption(Container c, int emailOption) throws SQLException
    {
        Map<String, String> props = PropertyManager.getWritableProperties(0, c.getId(), "defaultEmailSettings", true);
        props.put("defaultEmailOption", Integer.toString(emailOption));
        PropertyManager.saveProperties(props);
    }

    public static int getDefaultEmailOption(Container c) throws SQLException
    {
        Map<String, String> props = PropertyManager.getWritableProperties(0, c.getId(), "defaultEmailSettings", false);
        if (props != null && props.size() > 0)
        {
            String option = props.get("defaultEmailOption");
            if (option != null)
                return Integer.parseInt(option);
            else
                throw new IllegalStateException("Invalid stored property value.");
        }
        else
            return EMAIL_DEFAULT_OPTION;
    }

    //delete all user records regardless of container
    public static void deleteUserEmailPref(User user, List<Container> containerList) throws SQLException
    {
        if (containerList == null)
        {
            Table.delete(_comm.getTableInfoEmailPrefs(),
                    new SimpleFilter("UserId", user.getUserId()));
        }
        else
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("UserId", user.getUserId());
            StringBuilder whereClause = new StringBuilder("Container IN (");
            for (int i = 0; i < containerList.size(); i++)
            {
                Container c = containerList.get(i);
                whereClause.append("'");
                whereClause.append(c.getId());
                whereClause.append("'");
                if (i < containerList.size() - 1)
                    whereClause.append(", ");
            }
            whereClause.append(")");
            filter.addWhereClause(whereClause.toString(), null);

            Table.delete(_comm.getTableInfoEmailPrefs(), filter);
        }
    }


    private static final String MESSAGE_BOARD_SETTINGS = "messageBoardSettings";

    public static void saveMessageBoardSettings(Container c, DiscussionService.Settings settings) throws SQLException, IllegalAccessException, InvocationTargetException
    {
        Map<String, String> props = PropertyManager.getWritableProperties(0, c.getId(), MESSAGE_BOARD_SETTINGS, true);
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
        PropertyManager.saveProperties(props);
    }

    public static DiscussionService.Settings getMessageBoardSettings(Container c) throws SQLException, IllegalAccessException, InvocationTargetException
    {
        Map<String, String> props = PropertyManager.getProperties(0, c.getId(), MESSAGE_BOARD_SETTINGS, false);
        DiscussionService.Settings settings = new DiscussionService.Settings();
        settings.setDefaults();
        BeanUtils.populate(settings, props);
        return settings;
    }


    public static void purgeContainer(Container c)
    {
        try
        {
            // Attachments are handled by AttachmentServiceImpl
            ContainerUtil.purgeTable(_comm.getTableInfoEmailPrefs(), c, null);
            ContainerUtil.purgeTable(_comm.getTableInfoAnnouncements(), c, null);
        }
        catch (SQLException x)
        {
            _log.error("purgeContainer", x);
        }
    }

    public static class EmailPref
    {
        String _container;
        int _userId;
        String _email;
        String _firstName;
        String _lastName;
        String _displayName;
        Integer _emailOptionId;
        Integer _emailFormatId;
        String _emailOption;
        Integer _lastModifiedBy;
        String _lastModifiedByName;

        boolean _projectMember;

        int _pageTypeId;

        public String getLastModifiedByName()
        {
            return _lastModifiedByName;
        }

        public void setLastModifiedByName(String lastModifiedByName)
        {
            _lastModifiedByName = lastModifiedByName;
        }

        public Integer getLastModifiedBy()
        {
            return _lastModifiedBy;
        }

        public void setLastModifiedBy(Integer lastModifiedBy)
        {
            _lastModifiedBy = lastModifiedBy;
        }

        public String getEmail()
        {
            return _email;
        }

        public void setEmail(String email)
        {
            _email = email;
        }

        public String getContainer()
        {
            return _container;
        }

        public void setContainer(String container)
        {
            _container = container;
        }

        public String getEmailOption()
        {
            return _emailOption;
        }

        public void setEmailOption(String emailOption)
        {
            _emailOption = emailOption;
        }

        public String getFirstName()
        {
            return _firstName;
        }

        public void setFirstName(String firstName)
        {
            _firstName = firstName;
        }

        public String getLastName()
        {
            return _lastName;
        }

        public void setLastName(String lastName)
        {
            _lastName = lastName;
        }

        public String getDisplayName()
        {
            return _displayName;
        }

        public void setDisplayName(String displayName)
        {
            _displayName = displayName;
        }

        public Integer getEmailFormatId()
        {
            return _emailFormatId;
        }

        public void setEmailFormatId(Integer emailFormatId)
        {
            _emailFormatId = emailFormatId;
        }

        public Integer getEmailOptionId()
        {
            return _emailOptionId;
        }

        public void setEmailOptionId(Integer emailOptionId)
        {
            _emailOptionId = emailOptionId;
        }

        public int getUserId()
        {
            return _userId;
        }

        public void setUserId(int userId)
        {
            _userId = userId;
        }

        public int getPageTypeId()
        {
            return _pageTypeId;
        }

        public void setPageTypeId(int pageTypeId)
        {
            _pageTypeId = pageTypeId;
        }

        public boolean isProjectMember()
        {
            return _projectMember;
        }

        public void setProjectMember(boolean projectMember)
        {
            _projectMember = projectMember;
        }

        public User getUser()
        {
            return UserManager.getUser(_userId);
        }
    }

    public static class EmailOption
    {
        int _emailOptionId;
        String _emailOption;

        public String getEmailOption()
        {
            return _emailOption;
        }

        public void setEmailOption(String emailOption)
        {
            _emailOption = emailOption;
        }

        public int getEmailOptionId()
        {
            return _emailOptionId;
        }

        public void setEmailOptionId(int emailOptionId)
        {
            _emailOptionId = emailOptionId;
        }
    }

    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


        private void purgeAnnouncements(Container c, boolean verifyEmpty) throws SQLException
        {
            String deleteDocuments = "DELETE FROM " + _core.getTableInfoDocuments() + " WHERE Container = ? AND Parent IN (SELECT EntityId FROM " + _comm.getTableInfoAnnouncements() + " WHERE Container = ?)";
            int docs = Table.execute(_comm.getSchema(), deleteDocuments, new Object[]{c.getId(), c.getId()});
            String deleteAnnouncements = "DELETE FROM " + _comm.getTableInfoAnnouncements() + " WHERE Container = ?";
            int pages = Table.execute(_comm.getSchema(), deleteAnnouncements, new Object[]{c.getId()});
            if (verifyEmpty)
            {
                assertEquals(0, docs);
                assertEquals(0, pages);
            }
        }


        public void testAnnouncements()
                throws SQLException, ServletException, IOException, AttachmentService.DuplicateFilenameException
        {
            TestContext context = TestContext.get();

            User user = context.getUser();
            assertTrue("login before running this test", null != user);
            assertFalse("login before running this test", user.isGuest());

            Container c = JunitUtil.getTestContainer();

            purgeAnnouncements(c, false);

            int rowA;
            int rowResponse;
            {
                Announcement a = new Announcement();
                a.setTitle("new announcement");
                a.setBody("look at this");
                AnnouncementManager.insertAnnouncement(c, user, a, null);
                rowA = a.getRowId();
                assertTrue(0 != rowA);

                Announcement response = new Announcement();
                response.setParent(a.getEntityId());
                response.setTitle("response");
                response.setBody("bah");
                AnnouncementManager.insertAnnouncement(c, user, response, null);
                rowResponse = response.getRowId();
                assertTrue(0 != rowResponse);
            }

            {
                Announcement a = AnnouncementManager.getAnnouncement(c, rowA, INCLUDE_ATTACHMENTS + INCLUDE_RESPONSES);
                assertNotNull(a);
                assertEquals("new announcement", a.getTitle());
                Announcement[] responses = a.getResponses().toArray(new Announcement[a.getResponses().size()]);
                assertEquals(1, responses.length);
                Announcement response = responses[0];
                assertEquals(a.getEntityId(), response.getParent());
                assertEquals("response", response.getTitle());
                assertEquals("bah", response.getBody());
            }

            {
                // this test makes more sense if/when getParent() return an Announcement
                Announcement response = AnnouncementManager.getAnnouncement(c, rowResponse);
                assertNotNull(response.getParent());
            }

            {
                AnnouncementManager.deleteAnnouncement(c, rowA);
                assertNull(AnnouncementManager.getAnnouncement(c, rowA));
            }

            // UNDONE: attachments, update, responses, ....

            purgeAnnouncements(c, true);
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
