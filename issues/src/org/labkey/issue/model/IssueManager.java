/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
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
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.Group;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserDisplayNameComparator;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.HString;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
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
    public static final int NOTIFY_SUBSCRIBE = 16;           // send email on all changes

    public static final int NOTIFY_SELF_SPAM = 8;           // spam me when I enter/edit a bug
    public static final int DEFAULT_EMAIL_PREFS = NOTIFY_ASSIGNEDTO_OPEN | NOTIFY_ASSIGNEDTO_UPDATE | NOTIFY_CREATED_UPDATE;

    private static final String ISSUES_PREF_MAP = "IssuesPreferencesMap";
    private static final String ISSUES_REQUIRED_FIELDS = "IssuesRequiredFields";

    private static final String CAT_ENTRY_TYPE_NAMES = "issueEntryTypeNames";
    private static final String PROP_ENTRY_TYPE_NAME_SINGULAR = "issueEntryTypeNameSingular";
    private static final String PROP_ENTRY_TYPE_NAME_PLURAL = "issueEntryTypeNamePlural";

    private static final String CAT_ASSIGNED_TO_LIST = "issueAssignedToList";
    private static final String PROP_ASSIGNED_TO_GROUP = "issueAssignedToGroup";

    private static final String CAT_DEFAULT_ASSIGNED_TO_LIST = "issueDefaultAsignedToList";
    private static final String PROP_DEFAULT_ASSIGNED_TO_USER = "issueDefaultAssignedToUser";

    private static final String CAT_DEFAULT_MOVE_TO_LIST = "issueDefaultMoveToList";
    private static final String PROP_DEFAULT_MOVE_TO_CONTAINER = "issueDefaultMoveToContainer";

    private static final String CAT_COMMENT_SORT = "issueCommentSort";
    public static final String PICK_LIST_NAME = "pickListColumns";

    private IssueManager()
    {
    }


    public static Issue getIssue(@Nullable Container c, int issueId)
    {
        SimpleFilter f = new SimpleFilter(FieldKey.fromParts("issueId"), issueId);
        if (null != c)
            f.addCondition(FieldKey.fromParts("container"), c);

        TableSelector selector = new TableSelector(_issuesSchema.getTableInfoIssues(), f, null);
        selector.setForDisplay(true);
        Issue issue = selector.getObject(Issue.class);
        if (issue == null)
            return null;

        List<Issue.Comment> comments = new TableSelector(_issuesSchema.getTableInfoComments(),
                new SimpleFilter(FieldKey.fromParts("issueId"), issue.getIssueId()),
                new Sort("CommentId")).getArrayList(Issue.Comment.class);
        issue.setComments(comments);

        Collection<Integer> dups = new TableSelector(_issuesSchema.getTableInfoIssues().getColumn("IssueId"),
                new SimpleFilter(FieldKey.fromParts("Duplicate"), issueId),
                new Sort("IssueId")).getCollection(Integer.class);
        issue.setDuplicates(dups);

        Collection<Integer> rels = new TableSelector(_issuesSchema.getTableInfoRelatedIssues().getColumn("RelatedIssueId"),
                new SimpleFilter(FieldKey.fromParts("IssueId"), issueId),
                new Sort("IssueId")).getCollection(Integer.class);

        ArrayList<Integer> related = new ArrayList<>();
        related.addAll(rels);
        issue.setRelatedIssues(related);
        // the related string is only used when rendering the update form
        issue.setRelated(StringUtils.join(related, ", "));
        return issue;
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
        saveRelatedIssues(user, issue);

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

            Map<String, Object> m = new HashMap<>();
            m.put("issueId", issue.getIssueId());
            m.put("comment", comment.getComment());
            m.put("entityId", comment.getEntityId());
            Table.insert(user, _issuesSchema.getTableInfoComments(), m);
        }
        issue._added = null;
    }

    protected static void saveRelatedIssues(User user, Issue issue) throws SQLException
    {
        Collection<Integer> rels = issue.getRelatedIssues();
        // This shouldn't ever be null but I am a paranoid android
        if (null == rels) return;

        int issueId = issue.getIssueId();

        Table.delete(_issuesSchema.getTableInfoRelatedIssues(), new SimpleFilter(FieldKey.fromParts("IssueId"), issueId));

        for (Integer rel : rels)
        {
            Map<String, Object> m = new HashMap<>();
            m.put("issueId", issueId);
            m.put("relatedIssueId", rel);
            Table.insert(user, _issuesSchema.getTableInfoRelatedIssues(), m);
        }
    }


    public static Map<ColumnType, String> getAllDefaults(Container container) throws SQLException
    {
        final Map<ColumnType, String> defaults = new HashMap<>();
        SimpleFilter filter = SimpleFilter.createContainerFilter(container).addCondition(FieldKey.fromParts("Default"), true);
        Selector selector = new TableSelector(_issuesSchema.getTableInfoIssueKeywords(), PageFlowUtil.set("Type", "Keyword", "Container", "Default"), filter, null);

        selector.forEach(new Selector.ForEachBlock<ResultSet>() {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                ColumnType type = ColumnType.forOrdinal(rs.getInt("Type"));

                assert null != type;

                if (null != type)
                    defaults.put(type,rs.getString("Keyword"));
            }
        });

        return defaults;
    }


    public static CustomColumnConfiguration getCustomColumnConfiguration(Container c)
    {
        return ColumnConfigurationCache.get(c);
    }


    public static class CustomColumn
    {
        private Container _container;
        private String _name;
        private String _caption;
        private boolean _pickList;
        private Class<? extends Permission> _permissionClass;

        // Used via reflection by data access layer
        @SuppressWarnings({"UnusedDeclaration"})
        public CustomColumn()
        {
        }

        public CustomColumn(Container container, String name, String caption, boolean pickList, Class<? extends Permission> permissionClass)
        {
            setContainer(container);
            setName(name);
            setCaption(caption);
            setPickList(pickList);
            setPermission(permissionClass);
        }

        public Container getContainer()
        {
            return _container;
        }

        public void setContainer(Container container)
        {
            _container = container;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            assert name.equals(name.toLowerCase());
            _name = name;
        }

        public String getCaption()
        {
            return _caption;
        }

        public void setCaption(String caption)
        {
            _caption = caption;
        }

        public boolean isPickList()
        {
            return _pickList;
        }

        public void setPickList(boolean pickList)
        {
            _pickList = pickList;
        }

        public Class<? extends Permission> getPermission()
        {
            return _permissionClass;
        }

        public void setPermission(Class<? extends Permission> permissionClass)
        {
            _permissionClass = permissionClass;
        }

        public boolean hasPermission(User user)
        {
            return _container.hasPermission(user, _permissionClass);
        }
    }


    static class CustomColumnMap extends LinkedHashMap<String, CustomColumn>
    {
        private CustomColumnMap(Map<String, CustomColumn> map)
        {
            // Copy the map ensuring the canonical order specified by COLUMN_NAMES
            for (String name : CustomColumnConfiguration.COLUMN_NAMES)
            {
                CustomColumn cc = map.get(name);

                if (null != cc)
                    put(name, cc);
            }
        }
    }


    // Delete all old rows and insert the new rows; we don't bother detecting changes because this operation should be infrequent.
    public static void saveCustomColumnConfiguration(Container c, CustomColumnConfiguration ccc)
    {
        TableInfo table = IssuesSchema.getInstance().getTableInfoCustomColumns();
        Filter filter = new SimpleFilter(new FieldKey(null, "Container"), c);

        try (DbScope.Transaction transaction = table.getSchema().getScope().ensureTransaction())
        {
            Table.delete(table, filter);

            for (CustomColumn cc : ccc.getCustomColumns())
                Table.insert(null, table, cc);

            transaction.commit();
        }
        finally
        {
            ColumnConfigurationCache.uncache(c);
        }
    }


    public static class CustomColumnConfiguration
    {
        private static final String[] COLUMN_NAMES = {"type", "area", "priority", "milestone", "resolution", "related", "int1", "int2", "string1", "string2", "string3", "string4", "string5"};

        private final CustomColumnMap _map;

        // Values are being loaded from the database
        public CustomColumnConfiguration(@NotNull Map<String, CustomColumn> map)
        {
            _map = new CustomColumnMap(map);
        }

        // Valued are being posted from the admin page
        public CustomColumnConfiguration(ViewContext context)
        {
            Container c = context.getContainer();
            Map<String, Object> map = context.getExtendedProperties();

            // Could be null, a single String, or List<String>
            Object pickList = map.get(PICK_LIST_NAME);

            Set<String> pickListColumnNames;

            if (null == pickList)
                pickListColumnNames = Collections.emptySet();
            else if (pickList instanceof String)
                pickListColumnNames = Collections.singleton((String)pickList);
            else
                pickListColumnNames = new HashSet<>((List<String>)pickList);

            List<String> perms = context.getList("permissions");
            // Should have one for each string column (we don't support permissions on int columns yet)
            assert perms.size() == 5;
            Map<String, Class<? extends Permission>> permMap = new HashMap<>();

            for (int i = 0; i < 5; i++)
            {
                String simplePerm = perms.get(i);
                Class<? extends Permission> perm = "admin".equals(simplePerm) ? AdminPermission.class : "insert".equals(simplePerm) ? InsertPermission.class : ReadPermission.class;
                permMap.put("string" + (i + 1), perm);
            }

            Map<String, CustomColumn> ccMap = new HashMap<>();

            for (String columnName : COLUMN_NAMES)
            {
                String caption = (String)map.get(columnName);

                if (!StringUtils.isEmpty(caption))
                {
                    Class<? extends Permission> perm = permMap.get(columnName);
                    CustomColumn cc = new CustomColumn(c, columnName, caption, pickListColumnNames.contains(columnName), null != perm ? perm : ReadPermission.class);
                    ccMap.put(columnName, cc);
                }
            }

            _map = new CustomColumnMap(ccMap);
        }

        public CustomColumn getCustomColumn(String name)
        {
            return _map.get(name);
        }

        @Deprecated
        public Collection<CustomColumn> getCustomColumns()
        {
            return _map.values();
        }

        public Collection<CustomColumn> getCustomColumns(User user)
        {
            List<CustomColumn> list = new LinkedList<>();

            for (CustomColumn customColumn : _map.values())
                if (customColumn.hasPermission(user))
                    list.add(customColumn);

            return list;
        }

        @Deprecated
        public boolean shouldDisplay(String name)
        {
            return _map.containsKey(name);
        }

        public boolean shouldDisplay(User user, String name)
        {
            CustomColumn cc = getCustomColumn(name);

            return null != cc && cc.getContainer().hasPermission(user, cc.getPermission());
        }

        public boolean hasPickList(String name)
        {
            CustomColumn cc = getCustomColumn(name);

            return null != cc && cc.isPickList();
        }

        public @Nullable String getCaption(String name)
        {
            CustomColumn cc = getCustomColumn(name);

            return null != cc ? cc.getCaption() : null;
        }

        // TODO: If we need this, then pre-compute it
        public Map<String, String> getColumnCaptions()
        {
            Map<String, String> map = new HashMap<>();

            for (CustomColumn cc : _map.values())
                map.put(cc.getName(), cc.getCaption());

            return map;
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

        return new SqlSelector(_issuesSchema.getSchema(), sql).getMapArray();
    }


    private static final Comparator<User> USER_COMPARATOR = new UserDisplayNameComparator();

    public static @NotNull Collection<User> getAssignedToList(Container c, Issue issue)
    {
        Collection<User> initialAssignedTo = getInitialAssignedToList(c);

        // If this is an existing issue, add the user who opened the issue, unless they are a guest, inactive, already in the list, or don't have permissions.
        if (issue != null && 0 != issue.getIssueId())
        {
            User createdByUser = UserManager.getUser(issue.getCreatedBy());

            if (createdByUser != null && !createdByUser.isGuest() && !initialAssignedTo.contains(createdByUser) && canAssignTo(c, createdByUser))
            {
                Set<User> modifiedAssignedTo = new TreeSet<>(USER_COMPARATOR);
                modifiedAssignedTo.addAll(initialAssignedTo);
                modifiedAssignedTo.add(createdByUser);
                return Collections.unmodifiableSet(modifiedAssignedTo);
            }
        }

        return initialAssignedTo;
    }


    private static final StringKeyCache<Set<User>> ASSIGNED_TO_CACHE = new DatabaseCache<>(IssuesSchema.getInstance().getSchema().getScope(), 1000, "AssignedTo");

    // Returns the assigned to list that is used for every new issue in this container.  We can cache it and share it
    // across requests.  The collection is unmodifiable.
    private static @NotNull Collection<User> getInitialAssignedToList(final Container c)
    {
        String cacheKey = getCacheKey(c);

        return ASSIGNED_TO_CACHE.get(cacheKey, null, new CacheLoader<String, Set<User>>() {
            @Override
            public Set<User> load(String key, @Nullable Object argument)
            {
                Group group = getAssignedToGroup(c);

                if (null != group)
                    return createAssignedToList(c, SecurityManager.getAllGroupMembers(group, MemberType.ACTIVE_USERS, true));
                else
                    return createAssignedToList(c, SecurityManager.getProjectUsers(c.getProject()));
            }
        });
    }


    public static String getCacheKey(@Nullable Container c)
    {
        String key = "AssignedTo";
        return null != c ? key + c.getId() : key;
    }


    private static Set<User> createAssignedToList(Container c, Collection<User> candidates)
    {
        Set<User> assignedTo = new TreeSet<>(USER_COMPARATOR);

        for (User candidate : candidates)
            if (canAssignTo(c, candidate))
                assignedTo.add(candidate);

        // Cache an unmodifiable version
        return Collections.unmodifiableSet(assignedTo);
    }


    private static boolean canAssignTo(Container c, @NotNull User user)
    {
        return user.isActive() && c.hasPermission(user, UpdatePermission.class);
    }


    static @Nullable Integer validateAssignedTo(Container c, Integer candidate)
    {
        if (null != candidate)
        {
            User user = UserManager.getUser(candidate);

            if (null != user && canAssignTo(c, user))
                return candidate;
        }

        return null;
    }


    public static int getUserEmailPreferences(Container c, int userId)
    {
        Integer[] emailPreference;

        //if the user is inactive, don't send email
        User user = UserManager.getUser(userId);
        if(null != user && !user.isActive())
            return 0;

        emailPreference = new SqlSelector(
                _issuesSchema.getSchema(),
                "SELECT EmailOption FROM " + _issuesSchema.getTableInfoEmailPrefs() + " WHERE Container=? AND UserId=?",
                c, userId).getArray(Integer.class);

        if (emailPreference.length == 0)
        {
            if (userId == UserManager.getGuestUser().getUserId())
            {
                return 0; 
            }
            return DEFAULT_EMAIL_PREFS;
        }
        return emailPreference[0];
    }

    public static class EntryTypeNames
    {
        public HString singularName = new HString("Issue", false);
        public HString pluralName = new HString("Issues", false);

        public String getIndefiniteSingularArticle()
        {
            if (singularName.length() == 0)
                return "";
            char first = Character.toLowerCase(singularName.charAt(0));
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

        return SecurityManager.getGroup(Integer.valueOf(groupId));
    }

    public static void saveAssignedToGroup(Container c, @Nullable Group group)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(c, CAT_ASSIGNED_TO_LIST, true);
        props.put(PROP_ASSIGNED_TO_GROUP, null != group ? String.valueOf(group.getUserId()) : "0");
        PropertyManager.saveProperties(props);
        uncache(c);  // uncache the assigned to list
    }

    public static @Nullable User getDefaultAssignedToUser(Container c)
    {
        Map<String, String> props = PropertyManager.getProperties(c, CAT_DEFAULT_ASSIGNED_TO_LIST);
        String userId = props.get(PROP_DEFAULT_ASSIGNED_TO_USER);
        if (null == userId)
            return null;
        User user = UserManager.getUser(Integer.parseInt(userId));
        if (user == null)
            return null;
        if (!canAssignTo(c, user))
            return null;
        return user;
    }

    public static void saveDefaultAssignedToUser(Container c, @Nullable User user)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(c, CAT_DEFAULT_ASSIGNED_TO_LIST, true);
        props.put(PROP_DEFAULT_ASSIGNED_TO_USER, null != user ? String.valueOf(user.getUserId()) : null);
        PropertyManager.saveProperties(props);
    }

    public static @Nullable List<Container> getMoveDestinationContainers(Container c)
    {
        Map<String, String> props = PropertyManager.getProperties(c, CAT_DEFAULT_MOVE_TO_LIST);
        String propsValue = props.get(PROP_DEFAULT_MOVE_TO_CONTAINER);
        if (null == propsValue)
            return null;

        List<Container> containers = new LinkedList<>();
        for (String containerId : StringUtils.split(propsValue, ';'))
            containers.add( ContainerManager.getForId(containerId));

        return containers;
    }

    public static void saveMoveDestinationContainers(Container c, @Nullable List<Container> containers)
    {
        String propsValue = null;
        if (containers != null && containers.size() != 0)
        {
            StringBuilder sb = new StringBuilder();
            for (Container container : containers)
                sb.append(String.format(";%s", container.getId()));
            propsValue = sb.toString().substring(1);
        }

        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(c, CAT_DEFAULT_MOVE_TO_LIST, true);
        props.put(PROP_DEFAULT_MOVE_TO_CONTAINER, propsValue);
        PropertyManager.saveProperties(props);
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
        int ret = new SqlExecutor(_issuesSchema.getSchema()).execute(
                "UPDATE " + _issuesSchema.getTableInfoEmailPrefs() + " SET EmailOption=? WHERE Container=? AND UserId=?",
                emailPrefs, c, userId);


        if (ret == 0)
        {
            // record doesn't exist yet...
            new SqlExecutor(_issuesSchema.getSchema()).execute(
                    "INSERT INTO " + _issuesSchema.getTableInfoEmailPrefs() + " (Container, UserId, EmailOption ) VALUES (?, ?, ?)",
                    c, userId, emailPrefs);
        }
    }

    public static List<ValidEmail> getSubscribedUserEmails(Container c)
    {
        List<ValidEmail> emails = new ArrayList<>();

        SqlSelector ss = new SqlSelector(_issuesSchema.getSchema().getScope(), new SQLFragment("SELECT UserId FROM " + _issuesSchema.getTableInfoEmailPrefs() + " WHERE Container = ? and (EmailOption & ?) = ?", c.getId(), NOTIFY_SUBSCRIBE, NOTIFY_SUBSCRIBE));
        Integer[] userIds = ss.getArray(Integer.class);

        for (Integer userId : userIds)
        {
            String email = UserManager.getEmailForId(userId);
            if (email != null)
            {
                try
                {
                    ValidEmail ve = new ValidEmail(email);
                    emails.add(ve);
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    //ignore
                }
            }
        }

        return emails;
    }

    public static void deleteUserEmailPreferences(User user)
    {
        Table.delete(_issuesSchema.getTableInfoEmailPrefs(), new SimpleFilter(FieldKey.fromParts("UserId"), user.getUserId()));
    }

    public static long getIssueCount(Container c)
    {
        return new TableSelector(_issuesSchema.getTableInfoIssues(), SimpleFilter.createContainerFilter(c), null).getRowCount();
    }

    public static void uncache(@Nullable Container c)
    {
        if (c != null)
            ASSIGNED_TO_CACHE.remove(getCacheKey(c));
        else
            ASSIGNED_TO_CACHE.clear();
    }

    public static void purgeContainer(Container c)
    {
        try (DbScope.Transaction transaction = _issuesSchema.getSchema().getScope().ensureTransaction())
        {
            String deleteStmt = "DELETE FROM %s WHERE IssueId IN (SELECT IssueId FROM %s WHERE Container = ?)";

            String deleteComments = String.format(deleteStmt, _issuesSchema.getTableInfoComments(), _issuesSchema.getTableInfoIssues());
            new SqlExecutor(_issuesSchema.getSchema()).execute(deleteComments, c.getId());

            String deleteRelatedIssues = String.format(deleteStmt, _issuesSchema.getTableInfoRelatedIssues(), _issuesSchema.getTableInfoIssues());
            new SqlExecutor(_issuesSchema.getSchema()).execute(deleteRelatedIssues, c.getId());

            ContainerUtil.purgeTable(_issuesSchema.getTableInfoIssues(), c, null);
            ContainerUtil.purgeTable(_issuesSchema.getTableInfoIssueKeywords(), c, null);
            ContainerUtil.purgeTable(_issuesSchema.getTableInfoEmailPrefs(), c, null);
            ContainerUtil.purgeTable(_issuesSchema.getTableInfoCustomColumns(), c, null);

            transaction.commit();
        }
    }


    public static String purge() throws SQLException
    {
        String message = "";

        try (DbScope.Transaction transaction = _issuesSchema.getSchema().getScope().ensureTransaction())
        {
            String subQuery = String.format("SELECT IssueId FROM %s WHERE Container NOT IN (SELECT EntityId FROM core.Containers)", _issuesSchema.getTableInfoIssues());

            String deleteComments = String.format("DELETE FROM %s WHERE IssueId IN (%s)", _issuesSchema.getTableInfoComments(), subQuery);
            int commentsDeleted = new SqlExecutor(_issuesSchema.getSchema()).execute(deleteComments);

            String deleteOrphanedComments =
                    "DELETE FROM " + _issuesSchema.getTableInfoComments() + " WHERE IssueId NOT IN (SELECT IssueId FROM " + _issuesSchema.getTableInfoIssues() + ")";
            commentsDeleted += new SqlExecutor(_issuesSchema.getSchema()).execute(deleteOrphanedComments);

            // NOTE: this is ugly...
            String deleteRelatedIssues = String.format("DELETE FROM %s WHERE IssueId IN (%s) OR RelatedIssueId IN (%s)", _issuesSchema.getTableInfoRelatedIssues(), subQuery, subQuery);
            int relatedIssuesDeleted = new SqlExecutor(_issuesSchema.getSchema()).execute(deleteRelatedIssues);

            int issuesDeleted = ContainerUtil.purgeTable(_issuesSchema.getTableInfoIssues(), null);
            ContainerUtil.purgeTable(_issuesSchema.getTableInfoIssueKeywords(), null);
            transaction.commit();

            message = String.format("deleted %d issues<br>\ndeleted %d comments<br>\ndeleted %d relatedIssues", issuesDeleted, commentsDeleted, relatedIssuesDeleted);
        }

        return message;
    }

    public static String getRequiredIssueFields(Container container)
    {
        Map<String, String> map = PropertyManager.getProperties(container, ISSUES_PREF_MAP);
        String requiredFields = map.get(ISSUES_REQUIRED_FIELDS);
        return null == requiredFields ? IssuesController.DEFAULT_REQUIRED_FIELDS : requiredFields.toLowerCase();
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
        new SqlExecutor(_issuesSchema.getSchema()).execute(
            "UPDATE issues.issues SET lastIndexed=? WHERE container=? AND issueId=?",
            new Timestamp(ms), containerId, issueId);
    }
    

    public static void indexIssues(final SearchService.IndexTask task, @NotNull Container c, Date modifiedSince)
    {
        SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null == ss)
            return;
        
        SimpleFilter f = SimpleFilter.createContainerFilter(c);
        SearchService.LastIndexedClause incremental = new SearchService.LastIndexedClause(_issuesSchema.getTableInfoIssues(), modifiedSince, null);
        if (!incremental.toSQLFragment(null,null).isEmpty())
            f.addClause(incremental);
        if (f.getClauses().isEmpty())
            f = null;

        final ArrayList<Integer> ids = new ArrayList<>(100);

        new TableSelector(_issuesSchema.getTableInfoIssues(), PageFlowUtil.set("issueid"), f, null).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                int id = rs.getInt(1);
                ids.add(id);

                if (ids.size() == 100)
                {
                    task.addRunnable(new IndexGroup(task, ids), SearchService.PRIORITY.group);
                    ids.clear();
                }
            }
        });

        task.addRunnable(new IndexGroup(task, ids), SearchService.PRIORITY.group);
    }

    private static class IndexGroup implements Runnable
    {
        private final List<Integer> _ids;
        private final SearchService.IndexTask _task;
        
        IndexGroup(SearchService.IndexTask task, List<Integer> ids)
        {
            _ids = ids;
            _task = task;
        }

        public void run()
        {
            indexIssues(_task, _ids);
        }
    }


    /* CONSIDER: some sort of generator interface instead */
    public static void indexIssues(SearchService.IndexTask task, Collection<Integer> ids)
    {
        if (ids.isEmpty())
            return;

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
        for (Integer id : ids)
        {
            f.append(comma).append(id);
            comma = ",";
        }
        f.append(")\n");
        f.append("ORDER BY I.issueid, C.created");

        try (ResultSet rs = new SqlSelector(_issuesSchema.getSchema(), f).getResultSet(false))
        {
            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);
            int currentIssueId = -1;

            Map<String, Object> m = null;
            ArrayList<Issue.Comment> comments = new ArrayList<>();

            while (rs.next())
            {
                int id = rs.getInt(1);
                if (id != currentIssueId)
                {
                    queueIssue(task, currentIssueId, m, comments);
                    comments = new ArrayList<>();
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
        indexIssues(task, Collections.singleton(issue.getIssueId()));
    }


    static void queueIssue(SearchService.IndexTask task, int id, Map<String,Object> m, ArrayList<Issue.Comment> comments)
    {
        if (null == task || null == m)
            return;
        String title = String.valueOf(m.get("title"));
        m.put(SearchService.PROPERTY.title.toString(), id + " : " + title);
        m.put("comment", null);
        m.put("_row", null);
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
                if (null != c.getComment())
                    out.write(c.getComment());
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
                issue.setTitle("This is a junit test bug");
                issue.setTag("junit");
                issue.addComment(user, "new issue");
                issue.setPriority(3);

                IssueManager.saveIssue(user, c, issue);
                issueId = issue.getIssueId();
            }

            // verify
            {
                Issue issue = IssueManager.getIssue(c, issueId);
                assertEquals("This is a junit test bug", issue.getTitle());
                assertEquals(user.getUserId(), issue.getCreatedBy());
                assertTrue(issue.getCreated().getTime() != 0);
                assertTrue(issue.getModified().getTime() != 0);
                assertEquals(user.getUserId(), issue.getAssignedTo().intValue());
                assertEquals(Issue.statusOPEN, issue.getStatus());
                assertEquals(1, issue.getComments().size());
				String comment = (issue.getComments().iterator().next()).getComment();
                assertTrue("new issue".equals(comment));
            }

            //
            // ADD COMMENT
            //
            {
                Issue issue = IssueManager.getIssue(c, issueId);
                issue.addComment(user, "what was I thinking");
                IssueManager.saveIssue(user, c, issue);
            }

            // verify
            {
                Issue issue = IssueManager.getIssue(c, issueId);
                assertEquals(2, issue.getComments().size());
                Iterator it = issue.getComments().iterator();
                assertEquals("new issue", ((Issue.Comment) it.next()).getComment());
                assertEquals("what was I thinking", ((Issue.Comment) it.next()).getComment());
            }

            //
            // ADD INVALID COMMENT
            //
            {
                Issue issue = IssueManager.getIssue(c, issueId);
                issue.addComment(user, "invalid character <\u0010>");
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
                copy.setResolution("fixed");
                copy.addComment(user, "fixed it");
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
                copy.addComment(user, "closed");
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

            for (AjaxCompletion completion : UserManager.getAjaxCompletions(possibleUsers, user, fakeRoot))
            {
                User u = UserManager.getUserByDisplayName(completion.getInsertionText());
                if (u != null)
                    assertFalse("readers should not see emails", completion.getDisplayText().toLowerCase().contains(u.getEmail().toLowerCase()));
            }

            // this should be an admin...
            user = TestContext.get().getUser();
            showEmailAddresses = SecurityManager.canSeeEmailAddresses(fakeRoot, user);
            assertTrue("admins should see emails", showEmailAddresses);

            for (AjaxCompletion completion : UserManager.getAjaxCompletions(possibleUsers, user, fakeRoot))
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
            SqlExecutor executor = new SqlExecutor(_issuesSchema.getSchema());

            SQLFragment deleteComments = new SQLFragment("DELETE FROM " + _issuesSchema.getTableInfoComments() +
                " WHERE IssueId IN (SELECT IssueId FROM " + _issuesSchema.getTableInfoIssues() + " WHERE Container = ?)", c.getId());
            executor.execute(deleteComments);
            SQLFragment deleteIssues = new SQLFragment("DELETE FROM " + _issuesSchema.getTableInfoIssues() + " WHERE Container = ?", c.getId());
            executor.execute(deleteIssues);
        }
    }
}
