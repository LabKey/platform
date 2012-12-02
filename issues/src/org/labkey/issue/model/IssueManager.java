/*
 * Copyright (c) 2005-2012 Fred Hutchinson Cancer Research Center
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
package org.labkey.issue.model;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.Group;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserDisplayNameComparator;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.HString;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.webdav.AbstractDocumentResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.issue.ColumnType;
import org.labkey.issue.IssuesController;

import javax.servlet.ServletException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.labkey.api.search.SearchService.PROPERTY.categories;

/**
 * User: mbellew
 * Date: Mar 11, 2005
 * Time: 11:07:27 AM
 */
public class IssueManager
{
    public static final SearchService.SearchCategory searchCategory = new SearchService.SearchCategory("issue", "Issues");
    // UNDONE: Keywords, Summary, etc.

    private static IssuesSchema _issuesSchema = IssuesSchema.getInstance();
    
    public static final int NOTIFY_ASSIGNEDTO_OPEN = 1;     // if a bug is assigned to me
    public static final int NOTIFY_ASSIGNEDTO_UPDATE = 2;   // if a bug assigned to me is modified
    public static final int NOTIFY_CREATED_UPDATE = 4;      // if a bug I created is modified
    public static final int NOTIFY_SELF_SPAM = 8;           // spam me when I enter/edit a bug
    public static final int DEFAULT_EMAIL_PREFS = NOTIFY_ASSIGNEDTO_OPEN | NOTIFY_ASSIGNEDTO_UPDATE | NOTIFY_CREATED_UPDATE;

    private static final String ISSUES_PREF_MAP = "IssuesPreferencesMap";
    private static final String ISSUES_REQUIRED_FIELDS = "IssuesRequiredFields";

    private static final String CAT_ENTRY_TYPE_NAMES = "issueEntryTypeNames";
    private static final String PROP_ENTRY_TYPE_NAME_SINGULAR = "issueEntryTypeNameSingular";
    private static final String PROP_ENTRY_TYPE_NAME_PLURAL = "issueEntryTypeNamePlural";

    private static final String CAT_ASSIGNED_TO_LIST = "issueAssignedToList";
    private static final String PROP_ASSIGNED_TO_GROUP = "issueAssignedToGroup";

    private static final String CAT_COMMENT_SORT = "issueCommentSort";

    private IssueManager()
    {
    }


    public static Issue getIssue(@Nullable Container c, int issueId)
    {
        try
        {
            SimpleFilter f = new SimpleFilter("issueId", issueId);
            if (null != c)
                f.addCondition("container", c.getId());

            Issue[] issues = Table.selectForDisplay(
                    _issuesSchema.getTableInfoIssues(),
                    Table.ALL_COLUMNS, f, null, Issue.class);
            if (null == issues || issues.length < 1)
                return null;
            Issue issue = issues[0];

            Collection<Issue.Comment> comments = new TableSelector(_issuesSchema.getTableInfoComments(),
                    new SimpleFilter("issueId", issue.getIssueId()),
                    new Sort("CommentId")).getCollection(Issue.Comment.class);
            issue.setComments(new ArrayList<Issue.Comment>(comments));

            Collection<Integer> dups = new TableSelector(_issuesSchema.getTableInfoIssues().getColumn("IssueId"),
                    new SimpleFilter("Duplicate", issueId),
                    new Sort("IssueId")).getCollection(Integer.class);
            issue.setDuplicates(dups);
            return issue;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static void saveIssue(User user, Container c, Issue issue) throws SQLException
    {
        if (issue.assignedTo == null)
            issue.assignedTo = 0;

        if (issue.issueId == 0)
        {
            issue.beforeInsert(user, c.getId());
            Table.insert(user, _issuesSchema.getTableInfoIssues(), issue);
        }
        else
        {
            issue.beforeUpdate(user);
            Table.update(user, _issuesSchema.getTableInfoIssues(), issue, issue.getIssueId());
        }
        saveComments(user, issue);

        indexIssue(null, issue);
    }


    protected static void saveComments(User user, Issue issue) throws SQLException
    {
        Collection<Issue.Comment> comments = issue._added;
        if (null == comments)
            return;
        for (Issue.Comment comment : comments)
        {
            // NOTE: form has already validated comment text, but let's be extra paranoid.
            if (!ViewServlet.validChars(comment.getComment()))
                throw new ConversionException("comment has invalid characters");

            Map<String, Object> m = new HashMap<String, Object>();
            m.put("issueId", issue.getIssueId());
            m.put("comment", comment.getComment());
            m.put("entityId", comment.getEntityId());
            Table.insert(user, _issuesSchema.getTableInfoComments(), m);
        }
        issue._added = null;
    }


    public static Map<ColumnType, HString> getAllDefaults(Container container) throws SQLException
    {
        final Map<ColumnType, HString> defaults = new HashMap<ColumnType, HString>();
        SimpleFilter filter = new SimpleFilter("container", container.getId()).addCondition("Default", true);
        Selector selector = new TableSelector(_issuesSchema.getTableInfoIssueKeywords(), PageFlowUtil.set("Type", "Keyword", "Container", "Default"), filter, null);

        selector.forEach(new Selector.ForEachBlock<ResultSet>() {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                ColumnType type = ColumnType.forOrdinal(rs.getInt("Type"));

                assert null != type;

                if (null != type)
                    defaults.put(type, new HString(rs.getString("Keyword"), true));
            }
        });

        return defaults;
    }


    private static final String CUSTOM_COLUMN_CONFIGURATION = "IssuesCaptions";

    public static CustomColumnConfiguration getCustomColumnConfiguration(Container c)
    {
        Map<String, String> map = PropertyManager.getProperties(c, CUSTOM_COLUMN_CONFIGURATION);

        return new CustomColumnConfiguration(map);
    }


    public static void saveCustomColumnConfiguration(Container c, CustomColumnConfiguration ccc)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(c, CUSTOM_COLUMN_CONFIGURATION, true);

        map.clear();
        map.putAll(ccc.getColumnCaptions());
        map.put(CustomColumnConfiguration.PICK_LIST_NAME, StringUtils.join(ccc.getPickListColumns().iterator(), ","));

        PropertyManager.saveProperties(map);
    }


    public static class CustomColumnConfiguration
    {
        public static final String PICK_LIST_NAME = "pickListColumns";
        private static String[] _tableColumns = {"type", "area", "priority", "milestone", "resolution", "int1", "int2", "string1", "string2", "string3", "string4", "string5"};
        private Map<String, String> _columnCaptions = new CaseInsensitiveHashMap<String>();
        private Map<String, HString> _columnHCaptions = new CaseInsensitiveHashMap<HString>();
        private Set<String> _pickListColumns = new HashSet<String>();

        public CustomColumnConfiguration(@NotNull Map<String, ?> map)
        {
            setColumnCaptions(map);
            setPickListColumns(map);
        }

        private void setColumnCaptions(Map<String, ?> map)
        {
            for (String tableColumn : _tableColumns)
            {
                String caption = (String)map.get(tableColumn);

                if (!StringUtils.isEmpty(caption))
                {
                    _columnCaptions.put(tableColumn, caption);
                    _columnHCaptions.put(tableColumn, new HString(caption, true));
                }
            }
        }

        @Deprecated
        public Map<String, String> getColumnCaptions()
        {
            return _columnCaptions;
        }

        public Map<String, HString> getColumnHCaptions()
        {
            return _columnHCaptions;
        }

        private void setPickListColumns(Map<String, ?> map)
        {
            Object pickListColumnNames = map.get(PICK_LIST_NAME);

            if (null == pickListColumnNames)
                return;

            List<String> columns;

            if (pickListColumnNames instanceof String)
                columns = Arrays.asList(((String) pickListColumnNames).split(","));
            else
                columns = (List<String>)pickListColumnNames;  // This is the "post values from admin page" case

            for (String column : columns)
                if (null != _columnCaptions.get(column))
                    _pickListColumns.add(column);
        }

        public Set<String> getPickListColumns()  // TODO: Set<ColumnType>?
        {
            return _pickListColumns;
        }
    }


    public static Map[] getSummary(Container c) throws SQLException
    {
        SQLFragment sql = new SQLFragment("SELECT DisplayName, SUM(CASE WHEN Status='open' THEN 1 ELSE 0 END) AS " +
            _issuesSchema.getSqlDialect().makeLegalIdentifier("Open") + ", SUM(CASE WHEN Status='resolved' THEN 1 ELSE 0 END) AS " +
            _issuesSchema.getSqlDialect().makeLegalIdentifier("Resolved") + "\n" +
            "FROM " + _issuesSchema.getTableInfoIssues() + " LEFT OUTER JOIN " + CoreSchema.getInstance().getTableInfoUsers() +
            " ON AssignedTo = UserId\n" +
                "WHERE Status in ('open', 'resolved') AND Container = ?\n" +
                "GROUP BY DisplayName",
                c.getId());

        return new SqlSelector(_issuesSchema.getSchema(), sql).getArray(Map.class);
    }


    private static final Comparator<User> USER_COMPARATOR = new UserDisplayNameComparator();

    public static @NotNull Collection<User> getAssignedToList(Container c, Issue issue)
    {
        Collection<User> initialAssignedTo = getInitialAssignedToList(c);

        // If this is an existing issue, add the user who opened the issue, unless they are a guest, inactive, or already in the list.
        if (issue != null && 0 != issue.getIssueId())
        {
            User createdByUser = UserManager.getUser(issue.getCreatedBy());

            if (createdByUser != null && !createdByUser.isGuest() && createdByUser.isActive() && !initialAssignedTo.contains(createdByUser))
            {
                Set<User> modifiedAssignedTo = new TreeSet<User>(USER_COMPARATOR);
                modifiedAssignedTo.addAll(initialAssignedTo);
                modifiedAssignedTo.add(createdByUser);
                return Collections.unmodifiableSet(modifiedAssignedTo);
            }
        }

        return initialAssignedTo;
    }


    private static final StringKeyCache<Set<User>> ASSIGNED_TO_CACHE = new DatabaseCache<Set<User>>(IssuesSchema.getInstance().getSchema().getScope(), 1000, "AssignedTo");

    // Returns the assigned to list that is used for every new issue in this container.  We can cache it and share it
    // across requests.  The collection is unmodifiable.
    private static @NotNull Collection<User> getInitialAssignedToList(final Container c)
    {
        String cacheKey = getCacheKey(c);

        return ASSIGNED_TO_CACHE.get(cacheKey, null, new CacheLoader<String, Set<User>>() {
            @Override
            public Set<User> load(String key, @Nullable Object argument)
            {
                Set<User> initialAssignedTo = new TreeSet<User>(USER_COMPARATOR);
                Group group = getAssignedToGroup(c);

                if (null != group)
                {
                    Set<User> groupMembers = SecurityManager.getAllGroupMembers(group, MemberType.USERS);
                    if (!groupMembers.isEmpty())
                        initialAssignedTo.addAll(groupMembers);
                }
                else
                {
                    List<User> projectUsers = SecurityManager.getProjectUsers(c.getProject());
                    if (!projectUsers.isEmpty())
                        initialAssignedTo.addAll(projectUsers);
                }

                Iterator it = initialAssignedTo.iterator();

                while (it.hasNext())
                {
                    User user = (User)it.next();
                    if (!user.isActive())
                    {
                        it.remove();
                    }
                }

                // Cache an unmodifiable version
                return Collections.unmodifiableSet(initialAssignedTo);
            }
        });
    }


    public static String getCacheKey(@Nullable Container c)
    {
        String key = "AssignedTo";
        return null != c ? key + c.getId() : key;
    }

    public static int getUserEmailPreferences(Container c, int userId)
    {
        Integer[] emailPreference;

        //if the user is inactive, don't send email
        User user = UserManager.getUser(userId);
        if(null != user && !user.isActive())
            return 0;

        try
        {
            emailPreference = Table.executeArray(
                    _issuesSchema.getSchema(),
                    "SELECT EmailOption FROM " + _issuesSchema.getTableInfoEmailPrefs() + " WHERE Container=? AND UserId=?",
                    new Object[]{c.getId(), userId},
                    Integer.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        if (emailPreference.length == 0)
        {
            if (userId == UserManager.getGuestUser().getUserId())
            {
                return 0; 
            }
            return DEFAULT_EMAIL_PREFS;
        }
        return emailPreference[0].intValue();
    }

    public static class EntryTypeNames
    {
        public HString singularName = new HString("Issue", false);
        public HString pluralName = new HString("Issues", false);

        public String getIndefiniteSingularArticle()
        {
            if (singularName.length() == 0)
                return "";
            char first = singularName.toLowerCase().charAt(0);
            if (first == 'a' || first == 'e' || first == 'i' || first == 'o' || first == 'u')
                return "an";
            else
                return "a";
        }
    }

    public static EntryTypeNames getEntryTypeNames(Container container)
    {
        Map<String,String> props = PropertyManager.getProperties(container, CAT_ENTRY_TYPE_NAMES);
        EntryTypeNames ret = new EntryTypeNames();
        if (props.containsKey(PROP_ENTRY_TYPE_NAME_SINGULAR))
            ret.singularName = new HString(props.get(PROP_ENTRY_TYPE_NAME_SINGULAR), true);
        if (props.containsKey(PROP_ENTRY_TYPE_NAME_PLURAL))
            ret.pluralName = new HString(props.get(PROP_ENTRY_TYPE_NAME_PLURAL), true);
        return ret;
    }

    public static void saveEntryTypeNames(Container container, EntryTypeNames names)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(container, CAT_ENTRY_TYPE_NAMES, true);
        props.put(PROP_ENTRY_TYPE_NAME_SINGULAR, names.singularName.getSource());
        props.put(PROP_ENTRY_TYPE_NAME_PLURAL, names.pluralName.getSource());
        PropertyManager.saveProperties(props);
    }


    public static @Nullable Group getAssignedToGroup(Container c)
    {
        Map<String, String> props = PropertyManager.getProperties(c, CAT_ASSIGNED_TO_LIST);

        String groupId = props.get(PROP_ASSIGNED_TO_GROUP);

        if (null == groupId)
            return null;

        return SecurityManager.getGroup(Integer.valueOf(groupId).intValue());
    }

    public static void saveAssignedToGroup(Container c, @Nullable Group group)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(c, CAT_ASSIGNED_TO_LIST, true);
        props.put(PROP_ASSIGNED_TO_GROUP, null != group ? String.valueOf(group.getUserId()) : "0");
        PropertyManager.saveProperties(props);
        uncache(c);  // uncache the assigned to list
    }

    public static Sort.SortDirection getCommentSortDirection(Container c)
    {
        Map<String, String> props = PropertyManager.getProperties(c, CAT_COMMENT_SORT);
        String direction = props.get(CAT_COMMENT_SORT);
        if (direction != null)
        {
            try
            {
                return Sort.SortDirection.valueOf(direction);
            }
            catch (IllegalArgumentException e) {}
        }
        return Sort.SortDirection.ASC; 
    }

    public static void saveCommentSortDirection(Container c, @NotNull Sort.SortDirection direction)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(c, CAT_COMMENT_SORT, true);
        props.put(CAT_COMMENT_SORT, direction.toString());
        PropertyManager.saveProperties(props);
        uncache(c);  // uncache the assigned to list
    }


    public static void setUserEmailPreferences(Container c, int userId, int emailPrefs, int currentUser)
    {
        try
        {
            int ret = Table.execute(_issuesSchema.getSchema(),
                    "UPDATE " + _issuesSchema.getTableInfoEmailPrefs() + " SET EmailOption=? WHERE Container=? AND UserId=?",
                    emailPrefs, c.getId(), userId);


            if (ret == 0)
            {
                // record doesn't exist yet...
                Table.execute(_issuesSchema.getSchema(),
                        "INSERT INTO " + _issuesSchema.getTableInfoEmailPrefs() + " (Container, UserId, EmailOption ) VALUES (?, ?, ?)",
                        c.getId(), userId, emailPrefs);
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public static void deleteUserEmailPreferences(User user) throws SQLException
    {
        Table.delete(_issuesSchema.getTableInfoEmailPrefs(), new SimpleFilter("UserId", user.getUserId()));
    }

    public static long getIssueCount(Container c)
    {
        try
        {
            Long l = Table.executeSingleton(_issuesSchema.getSchema(), "SELECT COUNT(*) FROM " + _issuesSchema.getTableInfoIssues() + " WHERE Container = ?", new Object[]{c.getId()}, Long.class);
            return l.longValue();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public static void uncache(@Nullable Container c)
    {
        if (c != null)
            ASSIGNED_TO_CACHE.remove(getCacheKey(c));
        else
            ASSIGNED_TO_CACHE.removeUsingPrefix(getCacheKey(null));
    }

    public static void purgeContainer(Container c)
    {
        try
        {
            _issuesSchema.getSchema().getScope().ensureTransaction();
            String deleteComments = "DELETE FROM " + _issuesSchema.getTableInfoComments() + " WHERE IssueId IN (SELECT IssueId FROM " + _issuesSchema.getTableInfoIssues() + " WHERE Container = ?)";
            Table.execute(_issuesSchema.getSchema(), deleteComments, c.getId());
            ContainerUtil.purgeTable(_issuesSchema.getTableInfoIssues(), c, null);
            ContainerUtil.purgeTable(_issuesSchema.getTableInfoIssueKeywords(), c, null);
            ContainerUtil.purgeTable(_issuesSchema.getTableInfoEmailPrefs(), c, null);

            _issuesSchema.getSchema().getScope().commitTransaction();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            _issuesSchema.getSchema().getScope().closeConnection();
        }
    }


    public static String purge() throws SQLException
    {
        String message = "";

        try
        {
            _issuesSchema.getSchema().getScope().ensureTransaction();
            String deleteComments =
                    "DELETE FROM " + _issuesSchema.getTableInfoComments() + " WHERE IssueId IN (SELECT IssueId FROM " + _issuesSchema.getTableInfoIssues() + " WHERE Container NOT IN (SELECT EntityId FROM core.Containers))";
            int commentsDeleted = Table.execute(_issuesSchema.getSchema(), deleteComments);
            String deleteOrphanedComments =
                    "DELETE FROM " + _issuesSchema.getTableInfoComments() + " WHERE IssueId NOT IN (SELECT IssueId FROM " + _issuesSchema.getTableInfoIssues() + ")";
            commentsDeleted += Table.execute(_issuesSchema.getSchema(), deleteOrphanedComments);
            int issuesDeleted = ContainerUtil.purgeTable(_issuesSchema.getTableInfoIssues(), null);
            ContainerUtil.purgeTable(_issuesSchema.getTableInfoIssueKeywords(), null);
            _issuesSchema.getSchema().getScope().commitTransaction();

            message = "deleted " + issuesDeleted + " issues<br>\ndeleted " + commentsDeleted + " comments<br>\n";
        }
        finally
        {
            _issuesSchema.getSchema().getScope().closeConnection();
        }

        return message;
    }

    public static HString getRequiredIssueFields(Container container)
    {
        Map<String, String> map = PropertyManager.getProperties(container, ISSUES_PREF_MAP);
        String requiredFields = map.get(ISSUES_REQUIRED_FIELDS);
        return new HString(null == requiredFields ? IssuesController.DEFAULT_REQUIRED_FIELDS : requiredFields, true);
    }


    public static void setRequiredIssueFields(Container container, String requiredFields)
    {
        Map<String, String> map = PropertyManager.getWritableProperties(container, ISSUES_PREF_MAP, true);

        if (!StringUtils.isEmpty(requiredFields))
            requiredFields = requiredFields.toLowerCase();
        map.put(ISSUES_REQUIRED_FIELDS, requiredFields);
        PropertyManager.saveProperties(map);
    }

    public static void setRequiredIssueFields(Container container, HString[] requiredFields)
    {
        final StringBuilder sb = new StringBuilder();
        if (requiredFields.length > 0)
        {
            String sep = "";
            for (HString field : requiredFields)
            {
                sb.append(sep);
                sb.append(field);
                sep = ";";
            }
        }
        setRequiredIssueFields(container, sb.toString());
    }

    public static void setLastIndexed(String containerId, int issueId, long ms)
    {
        try
        {
        Table.execute(_issuesSchema.getSchema(),
                "UPDATE issues.issues SET lastIndexed=? WHERE container=? AND issueId=?",
                new Timestamp(ms), containerId, issueId);
        }
        catch (SQLException sql)
        {
            throw new RuntimeSQLException(sql);
        }
    }
    

    public static void indexIssues(SearchService.IndexTask task, @NotNull Container c, Date modifiedSince)
    {
        assert null != c;
        SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null == ss || null == c)
            return;
        
        ResultSet rs = null;
        try
        {
            SimpleFilter f = new SimpleFilter();
            f.addCondition("container", c);
            SearchService.LastIndexedClause incremental = new SearchService.LastIndexedClause(_issuesSchema.getTableInfoIssues(), modifiedSince, null);
            if (!incremental.toSQLFragment(null,null).isEmpty())
                f.addClause(incremental);
            if (f.getClauses().isEmpty())
                f = null;

            rs = Table.select(_issuesSchema.getTableInfoIssues(), PageFlowUtil.set("issueid"), f, null);
            int[] ids = new int[100];
            int count = 0;
            while (rs.next())
            {
                int id = rs.getInt(1);
                ids[count++] = id;
                if (count == ids.length)
                {
                    task.addRunnable(new IndexGroup(task, ids, count), SearchService.PRIORITY.group);
                    count = 0;
                    ids = new int[ids.length];
                }
            }
            task.addRunnable(new IndexGroup(task, ids, count), SearchService.PRIORITY.group);
        }
        catch (SQLException x)
        {
            Logger.getLogger(IssueManager.class).error(x);
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }

    private static class IndexGroup implements Runnable
    {
        int[] ids; int len;
        SearchService.IndexTask _task;
        
        IndexGroup(SearchService.IndexTask task, int[] ids, int len)
        {
            this.ids = ids; this.len = len;
            _task = task;
        }

        public void run()
        {
            indexIssues(_task, ids, len);
        }
    }


    /* CONSIDER: some sort of generator interface instead */
    public static void indexIssues(SearchService.IndexTask task, int[] ids, int count)
    {
        if (count == 0) return;

        SQLFragment f = new SQLFragment();
        f.append("SELECT I.issueId, I.container, I.entityid, I.title, I.status, AssignedTo$.searchTerms as assignedto, I.type, I.area, ")
            .append("I.priority, I.milestone, I.buildfound, ModifiedBy$.searchTerms as modifiedby, ")
            .append("I.modified, CreatedBy$.searchTerms as createdby, I.created, I.tag, ResolvedBy$.searchTerms as resolvedby, ")
            .append("I.resolved, I.resolution, I.duplicate, ClosedBy$.searchTerms as closedby, I.closed, ")
            .append("I.int1, I.int2, I.string1, I.string2, ")
            .append("C.comment\n");
        f.append("FROM issues.issues I \n")
            .append("LEFT OUTER JOIN issues.comments C ON I.issueid = C.issueid\n")
            .append("LEFT OUTER JOIN core.usersearchterms AS AssignedTo$ ON I.assignedto = AssignedTo$.userid\n")
            .append("LEFT OUTER JOIN core.usersearchterms AS ClosedBy$  ON I.createdby = ClosedBy$.userid\n")
            .append("LEFT OUTER JOIN core.usersearchterms AS CreatedBy$  ON I.createdby = CreatedBy$.userid\n")
            .append("LEFT OUTER JOIN core.usersearchterms AS ModifiedBy$ ON I.modifiedby = ModifiedBy$.userid\n")
            .append("LEFT OUTER JOIN core.usersearchterms AS ResolvedBy$ ON I.modifiedby = ResolvedBy$.userid\n");
        f.append("WHERE I.issueid IN ");

        String comma = "(";
        for (int i=0 ; i<count ; i++)
        {
            int id = ids[i];
            f.append(comma).append(id);
            comma = ",";
        }
        f.append(")\n");
        f.append("ORDER BY I.issueid, C.created");

        ResultSet rs = null;
        try
        {
            rs = Table.executeQuery(_issuesSchema.getSchema(), f, false, false);
            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);
            int currentIssueId = -1;

            Map<String,Object> m = null;
            ArrayList<Issue.Comment> comments = new ArrayList<Issue.Comment>();

            while (rs.next())
            {
                int id = rs.getInt(1);
                if (id != currentIssueId)
                {
                    queueIssue(task, currentIssueId, m, comments);
                    comments = new ArrayList<Issue.Comment>();
                    m = factory.getRowMap(rs);
                    currentIssueId = id;
                }
                comments.add(new Issue.Comment(rs.getString("comment")));
            }
            queueIssue(task, currentIssueId, m, comments);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }


    static void indexIssue(@Nullable SearchService.IndexTask task, Issue issue)
    {
        if (task == null)
        {
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);
            if (null == ss)
                return;
            task = ss.defaultTask();
        }

        // UNDONE: broken ??
        // task.addResource(new IssueResource(issue), SearchService.PRIORITY.item);

        // try requery instead
        indexIssues(task, new int[]{issue.getIssueId()}, 1);
    }


    static void queueIssue(SearchService.IndexTask task, int id, Map<String,Object> m, ArrayList<Issue.Comment> comments)
    {
        if (null == task || null == m)
            return;
        String title = String.valueOf(m.get("title"));
        m.put(SearchService.PROPERTY.displayTitle.toString(), id + " : " + title);
        m.put("comment",null);
        m.put("_row",null);
        task.addResource(new IssueResource(id, m, comments), SearchService.PRIORITY.item);
    }


    public static SearchService.ResourceResolver getSearchResolver()
    {
        return new SearchService.ResourceResolver()
        {
            public WebdavResource resolve(@NotNull String resourceIdentifier)
            {
                return IssueManager.resolve(resourceIdentifier);
            }
        };
    }


    public static WebdavResource resolve(String id)
    {
        int issueId;
        try
        {
            issueId = Integer.parseInt(id);
        }
        catch (NumberFormatException x)
        {
            return null;
        }

        final Issue issue = getIssue(null, issueId);
        if (null == issue)
            return null;

        return new IssueResource(issue);
    }


    static final ObjectFactory _issueFactory = ObjectFactory.Registry.getFactory(Issue.class);

    private static class IssueResource extends AbstractDocumentResource
    {
        Collection<Issue.Comment> _comments;
        final int _issueId;

        IssueResource(Issue issue)
        {
            super(new Path("issue:" + String.valueOf(issue.getIssueId())));
            _issueId = issue.issueId;
            Map<String,Object> m = _issueFactory.toMap(issue, null);
            for (Map.Entry<String,Object> e : m.entrySet())
            {
                if (e.getValue() instanceof HString)
                    e.setValue(((HString)e.getValue()).getSource());
            }
            // UNDONE: custom field names
            // UNDONE: user names
            m.remove("comments");
            _containerId = issue.getContainerId();
            _properties = m;
            _comments = issue.getComments();
            _properties.put(categories.toString(), searchCategory.getName());
        }


        IssueResource(int issueId, Map<String,Object> m, List<Issue.Comment> comments)
        {
            super(new Path("issue:"+String.valueOf(issueId)));
            _issueId = issueId;
            _containerId = (String)m.get("container");
            _properties = m;
            _comments = comments;
            _properties.put(categories.toString(), searchCategory.getName());
        }


        @Override
        public void setLastIndexed(long ms, long modified)
        {
            IssueManager.setLastIndexed(_containerId, _issueId, ms);
        }

        public String getDocumentId()
        {
            return "issue:"+String.valueOf(_properties.get("issueid"));
        }


        public boolean exists()
        {
            return true;
        }

        @Override
        public String getExecuteHref(ViewContext context)
        {
            ActionURL url = new ActionURL(IssuesController.DetailsAction.class, null).addParameter("issueId", String.valueOf(_properties.get("issueid")));
            url.setExtraPath(_containerId);
            return url.getLocalURIString();
        }

        @Override
        public String getContentType()
        {
            return "text/html";
        }


        public FileStream getFileStream(User user) throws IOException
        {
            String title = String.valueOf(_properties.get("title"));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Writer out = new OutputStreamWriter(bos);
            out.write("<html><head><title>");
            out.write(PageFlowUtil.filter(title));
            out.write("</title></head><body>");
            out.write(PageFlowUtil.filter(title));
            out.write("\n");
            for (Issue.Comment c : _comments)
                out.write(c.getComment().getSource());
            out.close();
            return new FileStream.ByteArrayFileStream(bos.toByteArray());
        }
        
        public InputStream getInputStream(User user) throws IOException
        {
            return getFileStream(user).openInputStream();
        }

        public long copyFrom(User user, FileStream in) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        public long getContentLength() throws IOException
        {
            throw new UnsupportedOperationException();
        }
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testIssues() throws IOException, SQLException, ServletException
        {
            TestContext context = TestContext.get();

            User user = context.getUser();
            assertTrue("login before running this test", null != user);
            assertFalse("login before running this test", user.isGuest());

            Container c = JunitUtil.getTestContainer();

            int issueId;

            //
            // INSERT
            //
            {
                Issue issue = new Issue();
                issue.open(c, user);
                issue.setAssignedTo(user.getUserId());
                issue.setTitle(new HString("This is a junit test bug",false));
                issue.setTag(new HString("junit",false));
                issue.addComment(user, new HString("new issue",false));
                issue.setPriority(3);

                IssueManager.saveIssue(user, c, issue);
                issueId = issue.getIssueId();
            }

            // verify
            {
                Issue issue = IssueManager.getIssue(c, issueId);
                assertEquals("This is a junit test bug", issue.getTitle().getSource());
                assertEquals(user.getUserId(), issue.getCreatedBy());
                assertTrue(issue.getCreated().getTime() != 0);
                assertTrue(issue.getModified().getTime() != 0);
                assertEquals(user.getUserId(), issue.getAssignedTo().intValue());
                assertEquals(Issue.statusOPEN, issue.getStatus());
                assertEquals(1, issue.getComments().size());
				HString comment = (issue.getComments().iterator().next()).getComment();
                assertTrue(HString.eq("new issue", comment));
            }

            //
            // ADD COMMENT
            //
            {
                Issue issue = IssueManager.getIssue(c, issueId);
                issue.addComment(user, new HString("what was I thinking"));
                IssueManager.saveIssue(user, c, issue);
            }

            // verify
            {
                Issue issue = IssueManager.getIssue(c, issueId);
                assertEquals(2, issue.getComments().size());
                Iterator it = issue.getComments().iterator();
                assertEquals("new issue", ((Issue.Comment) it.next()).getComment().getSource());
                assertEquals("what was I thinking", ((Issue.Comment) it.next()).getComment().getSource());
            }

            //
            // ADD INVALID COMMENT
            //
            {
                Issue issue = IssueManager.getIssue(c, issueId);
                issue.addComment(user, new HString("invalid character <\u0010>"));
                try
                {
                    IssueManager.saveIssue(user, c, issue);
                    fail("Expected to throw exception for an invalid character.");
                }
                catch (ConversionException ex)
                {
                    // expected exception.
                    assertEquals("comment has invalid characters", ex.getMessage());
                }
            }

            //
            // RESOLVE
            //
            {
                Issue issue = IssueManager.getIssue(c, issueId);
                assertNotNull("issue not found", issue);
                issue.resolve(user);

                Issue copy = (Issue) JunitUtil.copyObject(issue);
                copy.setResolution(new HString("fixed"));
                copy.addComment(user, new HString("fixed it"));
                IssueManager.saveIssue(user, c, copy);
            }

            // verify
            {
                Issue issue = IssueManager.getIssue(c, issueId);
                assertEquals(Issue.statusRESOLVED, issue.getStatus());
                assertEquals(3, issue.getComments().size());
            }

            //
            // CLOSE
            //
            {
                Issue issue = getIssue(c, issueId);
                assertNotNull("issue not found", issue);
                issue.close(user);

                Issue copy = (Issue) JunitUtil.copyObject(issue);
                copy.addComment(user, new HString("closed"));
                IssueManager.saveIssue(user, c, copy);
            }

            // verify
            {
                Issue issue = IssueManager.getIssue(c, issueId);
                assertEquals(Issue.statusCLOSED, issue.getStatus());
                assertEquals(4, issue.getComments().size());
            }
        }

        @Test
        public void testEmailHiding() throws IOException, SQLException, ServletException
        {
            Container fakeRoot = ContainerManager.createFakeContainer(null, null);

            User user = UserManager.getGuestUser();
            boolean showEmailAddresses = SecurityManager.canSeeEmailAddresses(fakeRoot, user);
            assertFalse("readers should not see emails", showEmailAddresses);
            List<User> possibleUsers = SecurityManager.getUsersWithPermissions(fakeRoot, Collections.<Class<? extends Permission>>singleton(ReadPermission.class));
            for (AjaxCompletion completion : UserManager.getAjaxCompletions(possibleUsers, user, showEmailAddresses, true))
            {
                User u = UserManager.getUserByDisplayName(completion.getInsertionText());
                if (u != null)
                    assertFalse("readers should not see emails", completion.getDisplayText().toLowerCase().contains(u.getEmail().toLowerCase()));
            }

            // this should be an admin...
            user = TestContext.get().getUser();
            showEmailAddresses = SecurityManager.canSeeEmailAddresses(fakeRoot, user);
            assertTrue("admins should see emails", showEmailAddresses);
            for (AjaxCompletion completion : UserManager.getAjaxCompletions(possibleUsers, user, showEmailAddresses, true))
            {
                User u = UserManager.getUserByDisplayName(completion.getInsertionText());
                if (u != null)
                    assertTrue("admins should see emails", completion.getDisplayText().toLowerCase().contains(u.getEmail().toLowerCase()));
            }
        }

        @After
        public void tearDown()
        {
            Container c = JunitUtil.getTestContainer();

            SQLFragment deleteComments = new SQLFragment("DELETE FROM " + _issuesSchema.getTableInfoComments() +
                " WHERE IssueId IN (SELECT IssueId FROM " + _issuesSchema.getTableInfoIssues() + " WHERE Container = ?)", c.getId());
            new SqlExecutor(_issuesSchema.getSchema(), deleteComments);
            SQLFragment deleteIssues = new SQLFragment("DELETE FROM " + _issuesSchema.getTableInfoIssues() + " WHERE Container = ?", c.getId());
            new SqlExecutor(_issuesSchema.getSchema(), deleteIssues);
        }
    }
}
