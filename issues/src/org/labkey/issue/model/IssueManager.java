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
package org.labkey.issue.model;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.cache.Cache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Entity;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.issues.IssuesListDefProvider;
import org.labkey.api.issues.IssuesListDefService;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.search.SearchService;
import org.labkey.api.search.SearchService.IndexTask;
import org.labkey.api.security.Group;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.webdav.AbstractDocumentResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.issue.IssuesController;
import org.labkey.issue.IssuesModule;
import org.labkey.issue.query.IssueDefDomainKind;
import org.labkey.issue.query.IssuesListDefTable;
import org.labkey.issue.query.IssuesQuerySchema;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.labkey.api.search.SearchService.PROPERTY.categories;
import static org.labkey.api.security.UserManager.USER_DISPLAY_NAME_COMPARATOR;

/**
 * User: mbellew
 * Date: Mar 11, 2005
 * Time: 11:07:27 AM
 */
public class IssueManager
{
    private static final Logger _log = LogManager.getLogger(IssueManager.class);
    public static final SearchService.SearchCategory searchCategory = new SearchService.SearchCategory("issue", "Issues");
    // UNDONE: Keywords, Summary, etc.

    private static final IssuesSchema _issuesSchema = IssuesSchema.getInstance();
    
    public static final int NOTIFY_ASSIGNEDTO_OPEN = 1;     // if a bug is assigned to me
    public static final int NOTIFY_ASSIGNEDTO_UPDATE = 2;   // if a bug assigned to me is modified
    public static final int NOTIFY_CREATED_UPDATE = 4;      // if a bug I created is modified
    public static final int NOTIFY_SUBSCRIBE = 16;           // send email on all changes

    public static final int NOTIFY_SELF_SPAM = 8;           // spam me when I enter/edit a bug
    public static final int DEFAULT_EMAIL_PREFS = NOTIFY_ASSIGNEDTO_OPEN | NOTIFY_ASSIGNEDTO_UPDATE | NOTIFY_CREATED_UPDATE;

    private static final String ISSUES_PREF_MAP = "IssuesPreferencesMap";
    private static final String ISSUES_REQUIRED_FIELDS = "IssuesRequiredFields";

    private static final String CAT_ISSUE_DEF_PROPERTIES = "IssueDefProperties-";

    private static final String CAT_ENTRY_TYPE_NAMES = "issueEntryTypeNames";
    private static final String PROP_ENTRY_TYPE_NAME_SINGULAR = "issueEntryTypeNameSingular";
    private static final String PROP_ENTRY_TYPE_NAME_PLURAL = "issueEntryTypeNamePlural";

    private static final String CAT_ASSIGNED_TO_LIST = "issueAssignedToList";
    private static final String PROP_ASSIGNED_TO_GROUP = "issueAssignedToGroup";

    private static final String CAT_DEFAULT_ASSIGNED_TO_LIST = "issueDefaultAsignedToList";
    private static final String PROP_DEFAULT_ASSIGNED_TO_USER = "issueDefaultAssignedToUser";

    private static final String CAT_DEFAULT_MOVE_TO_LIST = "issueDefaultMoveToList";
    private static final String PROP_DEFAULT_MOVE_TO_CONTAINER = "issueDefaultMoveToContainer";

    private static final String CAT_DEFAULT_INHERIT_FROM_CONTAINER = "issueDefaultInheritFromCategory";
    private static final String PROP_DEFAULT_INHERIT_FROM_CONTAINER = "issueDefaultInheritFromProperty";

    private static final String CAT_DEFAULT_RELATED_ISSUES_LIST = "issueRelatedIssuesList";
    private static final String PROP_DEFAULT_RELATED_ISSUES_LIST = "issueRelatedIssuesList";

    private static final String CAT_COMMENT_SORT = "issueCommentSort";
    public static final String PICK_LIST_NAME = "pickListColumns";

    private IssueManager()
    {
    }

    private static IssueObject _getIssue(@Nullable Container c, int issueId)
    {
        SimpleFilter filter = null;
        if (null != c)
            filter = new SimpleFilter(FieldKey.fromParts("Container"), c);

        IssueObject issue = new TableSelector(_issuesSchema.getTableInfoIssues(), filter, null).getObject(issueId, IssueObject.class);
        if (issue == null)
            return null;

        List<IssueObject.CommentObject> comments = new TableSelector(_issuesSchema.getTableInfoComments(),
                new SimpleFilter(FieldKey.fromParts("issueId"), issue.getIssueId()),
                new Sort("CommentId")).getArrayList(IssueObject.CommentObject.class);
        issue.setComments(comments);

        Collection<Integer> dups = new TableSelector(_issuesSchema.getTableInfoIssues().getColumn("IssueId"),
                new SimpleFilter(FieldKey.fromParts("Duplicate"), issueId),
                new Sort("IssueId")).getCollection(Integer.class);
        issue.setDuplicates(dups);

        Collection<Integer> rels = new TableSelector(_issuesSchema.getTableInfoRelatedIssues().getColumn("RelatedIssueId"),
                new SimpleFilter(FieldKey.fromParts("IssueId"), issueId),
                new Sort("IssueId")).getCollection(Integer.class);

        issue.setRelatedIssues(rels);
        // the related string is only used when rendering the update form
        issue.setRelated(StringUtils.join(rels, ", "));
        return issue;
    }

    @Nullable
    public static IssueObject getIssue(@Nullable Container c, User user, int issueId)
    {
        IssueObject issue = _getIssue(c, issueId);

        if (issue != null && issue.getIssueDefId() != null)
        {
            // container may initially be null if we don't care about a specific folder, but we need the
            // correct domain for the provisioned table properties associated with the issue
            if (c == null)
                c = ContainerManager.getForId(issue.getContainerId());

            IssueListDef issueListDef = getIssueListDef(issue.getContainerFromId(), issue.getIssueDefId());
            UserSchema userSchema = QueryService.get().getUserSchema(user, c, IssuesQuerySchema.SCHEMA_NAME);
            TableInfo table = userSchema.getTable(issueListDef.getName());

            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("IssueId"), issueId);

            if (table != null)
            {
                try (Results rs = QueryService.get().select(table, table.getColumns(), filter, null, null, false))
                {
                    Map<String, Object> rowMap = new CaseInsensitiveHashMap<>();
                    if (rs.next())
                    {
                        for (String colName : table.getColumnNameSet())
                        {
                            Object value = rs.getObject(FieldKey.fromParts(colName));
                            if (value != null)
                                rowMap.put(colName, value);
                        }
                    }
                    issue.setProperties(rowMap);
                }
                catch (SQLException e)
                {
                    throw new RuntimeException(e);
                }
            }
            else
                return null;
        }
        return issue;
    }

    /**
     * Returns a linked list of all comments for the argument Issue together with comments
     * of all related issues sorted by creation date.
     *
     * @param   issue   an issue to retrieve comments from
     * @return          the sorted linked list of all related comments
     */
    public static List<IssueObject.CommentObject> getCommentsForRelatedIssues(IssueObject issue, User user)
    {
        // Get related issues for optional display
        Set<Integer> relatedIssues = issue.getRelatedIssues();
        List<IssueObject.CommentObject> commentLinkedList = new LinkedList<>();

        // Add related issue comments
        for (Integer relatedIssueInt : relatedIssues)
        {
            // only add related issues that the user has permission to see
            IssueObject relatedIssue = IssueManager.getIssue(null, user, relatedIssueInt);
            if (relatedIssue != null)
            {
                boolean hasReadPermission = ContainerManager.getForId(relatedIssue.getContainerId()).hasPermission(user, ReadPermission.class);
                if (hasReadPermission)
                    commentLinkedList.addAll(relatedIssue.getCommentObjects());
            }
        }
        // Add all current issue comments
        commentLinkedList.addAll(issue.getCommentObjects());

        Comparator<IssueObject.CommentObject> comparator = Comparator.comparing(Entity::getCreated);
        // Respect the configuration's sorting order - issue 23524
        Container issueContainer = issue.lookupContainer();
        IssueListDef issueListDef = IssueManager.getIssueListDef(issue);
        if (Sort.SortDirection.DESC == getCommentSortDirection(issueContainer, issueListDef.getName()))
        {
            comparator = comparator.reversed();
        }

        commentLinkedList.sort(comparator);
        return commentLinkedList;
    }

    /**
     * Determine if the parameter issue has related issues.  Returns true if the issue has related
     * issues and false otherwise.
     *
     * @param   issue   The issue to query
     * @return          boolean return value
     */
    public static boolean hasRelatedIssues(IssueObject issue, User user)
    {
        for (Integer relatedIssueInt : issue.getRelatedIssues())
        {
            IssueObject relatedIssue = IssueManager.getIssue(null, user, relatedIssueInt);
            if (relatedIssue != null && relatedIssue.getCommentObjects().size() > 0)
            {
                boolean hasReadPermission = ContainerManager.getForId(relatedIssue.getContainerId()).hasPermission(user, ReadPermission.class);
                if (hasReadPermission)
                    return true;
            }
        }
        return false;
    }

    public static void saveIssue(User user, Container container, IssueObject issue)
    {
        if (issue.getAssignedTo() == null)
            issue.setAssignedTo(0);

        IssueListDef issueDef = getIssueListDef(issue);
        if (issueDef != null)
        {
            try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                // if this is an existing issue, we want the container the issue is associated with, otherwise use the
                // passed in container
                Container c = ContainerManager.getForId(issue.getContainerId());
                if (c != null)
                    container = c;

                UserSchema userSchema = QueryService.get().getUserSchema(user, container, IssuesQuerySchema.SCHEMA_NAME);
                TableInfo table = userSchema.getTable(issueDef.getName());
                QueryUpdateService qus = table.getUpdateService();
                Map<String, Object> row = new CaseInsensitiveHashMap<>();

                ObjectFactory factory = ObjectFactory.Registry.getFactory(IssueObject.class);
                String related = issue.getRelated();
                issue.setRelated(null);

                factory.toMap(issue, row);
                row.putAll(issue.getProperties());
                row.remove("Related");

                BatchValidationException batchErrors = new BatchValidationException();
                List<Map<String, Object>> results;

                if (issue.issueId == 0)
                {
                    issue.beforeInsert(user, container.getId());
                    results = qus.insertRows(user, container, Collections.singletonList(row), batchErrors, null, null);

                    if (!batchErrors.hasErrors())
                    {
                        assert results.size() == 1;
                        issue.setIssueId((int)results.get(0).get("IssueId"));
                        issue.setIssueDefId((Integer)results.get(0).get("issueDefId"));
                    }
                    else
                        throw batchErrors;
                }
                else
                {
                    issue.beforeUpdate(user);
                    qus.updateRows(user, container, Collections.singletonList(row), Collections.singletonList(row) , null, null);
                }
                issue.setRelated(related);
                saveComments(user, issue);
                saveRelatedIssues(user, issue);

                indexIssue(container, user, null, issue);

                transaction.commit();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    protected static void saveComments(User user, IssueObject issue)
    {
        Collection<IssueObject.CommentObject> comments = issue._added;
        if (null == comments)
            return;
        for (IssueObject.CommentObject comment : comments)
        {
            // NOTE: form has already validated comment text, but let's be extra paranoid.
            if (!ViewServlet.validChars(comment.getHtmlComment().toString()))
                throw new ConversionException("comment has invalid characters");

            Map<String, Object> m = new HashMap<>();
            m.put("issueId", issue.getIssueId());
            m.put("comment", comment.getHtmlComment().toString());
            m.put("entityId", comment.getEntityId());
            Table.insert(user, _issuesSchema.getTableInfoComments(), m);
        }
        issue._added = null;
    }

    protected static void saveRelatedIssues(User user, IssueObject issue)
    {
        Collection<Integer> rels = issue.getRelatedIssues();

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

    public static Collection<Map<String, Object>> getSummary(Container c, User user, @Nullable IssueListDef issueListDef)
    {
        if (issueListDef != null)
        {
            TableInfo tableInfo = issueListDef.createTable(user);
            Collection<Object> params = new ArrayList<>();
            SQLFragment sql = new SQLFragment("SELECT DisplayName, SUM(CASE WHEN Status='open' THEN 1 ELSE 0 END) AS " +
                    _issuesSchema.getSqlDialect().makeLegalIdentifier("Open") + ", SUM(CASE WHEN Status='resolved' THEN 1 ELSE 0 END) AS " +
                    _issuesSchema.getSqlDialect().makeLegalIdentifier("Resolved") + "\n" +
                    "FROM " + tableInfo + " LEFT OUTER JOIN " + CoreSchema.getInstance().getTableInfoUsers() +
                    " ON AssignedTo = UserId\n" +
                    "WHERE Status in ('open', 'resolved') AND Container = ? ");
            params.add(c);
            sql.append("GROUP BY DisplayName");
            sql.addAll(params);

            return new SqlSelector(_issuesSchema.getSchema(), sql).getMapCollection();
        }
        else
            return Collections.emptyList();
    }


    public static @NotNull Collection<User> getAssignedToList(Container c, @Nullable String issueDefName, @Nullable IssueObject issue)
    {
        Collection<User> initialAssignedTo = getInitialAssignedToList(c, issueDefName);

        // If this is an existing issue, add the user who opened the issue, unless they are a guest, inactive, already in the list, or don't have permissions.
        if (issue != null && 0 != issue.getIssueId())
        {
            User createdByUser = UserManager.getUser(issue.getCreatedBy());

            if (createdByUser != null && !createdByUser.isGuest() && !initialAssignedTo.contains(createdByUser) && canAssignTo(c, createdByUser))
            {
                Set<User> modifiedAssignedTo = new TreeSet<>(USER_DISPLAY_NAME_COMPARATOR);
                modifiedAssignedTo.addAll(initialAssignedTo);
                modifiedAssignedTo.add(createdByUser);
                return Collections.unmodifiableSet(modifiedAssignedTo);
            }
        }

        return initialAssignedTo;
    }


    private static final Cache<String, Set<User>> ASSIGNED_TO_CACHE = new DatabaseCache<>(IssuesSchema.getInstance().getSchema().getScope(), 1000, "AssignedTo");

    // Returns the assigned to list that is used for every new issue in this container.  We can cache it and share it
    // across requests.  The collection is unmodifiable.
    private static @NotNull Collection<User> getInitialAssignedToList(final Container c, @Nullable String issueDefName)
    {
        issueDefName = issueDefName != null ? issueDefName : IssueListDef.DEFAULT_ISSUE_LIST_NAME;
        String cacheKey = getCacheKey(c, issueDefName);

        return ASSIGNED_TO_CACHE.get(cacheKey, issueDefName, (key, issueDefName1) ->
        {
            Group group = getAssignedToGroup(c, String.valueOf(issueDefName1));

            if (null != group)
                return createAssignedToList(c, SecurityManager.getAllGroupMembers(group, MemberType.ACTIVE_USERS, true));
            else
                return createAssignedToList(c, SecurityManager.getProjectUsers(c.getProject()));
        });
    }


    public static String getCacheKey(@Nullable Container c, String issueDefName)
    {
        String key = "AssignedTo-" + issueDefName;
        return null != c ? key + c.getId() : key;
    }


    private static Set<User> createAssignedToList(Container c, Collection<User> candidates)
    {
        Set<User> assignedTo = new TreeSet<>(USER_DISPLAY_NAME_COMPARATOR);

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


    public static int getUserEmailPreferences(Container c, Integer userId)
    {
        if (userId != null)
        {
            Integer[] emailPreference;

            //if the user is inactive, don't send email
            User user = UserManager.getUser(userId);
            if (null != user && !user.isActive())
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
        else
            return 0;
    }

    public static class EntryTypeNames
    {
        public String singularName = IssueDefDomainKind.DEFAULT_ENTRY_TYPE_SINGULAR;
        public String pluralName = IssueDefDomainKind.DEFAULT_ENTRY_TYPE_PLURAL;

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

    /**
     * @param c
     * @return Container c itself or the container from which admin settings were inherited from
     */
    public static Container getInheritFromOrCurrentContainer(Container c)
    {
        Container inheritFrom = getInheritFromContainer(c);

        //Return the container from which admin settings were inherited from
        if(inheritFrom != null)
            return inheritFrom;
        return c;
    }


    @NotNull
    public static EntryTypeNames getEntryTypeNames(Container container, String issueDefName)
    {
        Map<String,String> props = PropertyManager.getProperties(container, getPropMapName(issueDefName));
        EntryTypeNames ret = new EntryTypeNames();
        if (props.containsKey(PROP_ENTRY_TYPE_NAME_SINGULAR))
            ret.singularName =props.get(PROP_ENTRY_TYPE_NAME_SINGULAR);
        if (props.containsKey(PROP_ENTRY_TYPE_NAME_PLURAL))
            ret.pluralName = props.get(PROP_ENTRY_TYPE_NAME_PLURAL);
        return ret;
    }

    private static String getPropMapName(String issueDefName)
    {
        if (issueDefName == null)
            throw new IllegalArgumentException("Issue def name must be specified");

        return CAT_ISSUE_DEF_PROPERTIES + issueDefName;
    }

    /**
     *
     * @param container
     * @param inheritingVals
     * @return EntryTypeNames of a container with inherited settings, or of current container.
     */
    public static EntryTypeNames getEntryTypeNames(Container container, boolean inheritingVals)
    {
        Container c;
        if(inheritingVals)
            c = getInheritFromOrCurrentContainer(container);
        else
            c = container;

        Map<String,String> props = PropertyManager.getProperties(c, CAT_ENTRY_TYPE_NAMES);
        EntryTypeNames ret = new EntryTypeNames();
        if (props.containsKey(PROP_ENTRY_TYPE_NAME_SINGULAR))
            ret.singularName = props.get(PROP_ENTRY_TYPE_NAME_SINGULAR);
        if (props.containsKey(PROP_ENTRY_TYPE_NAME_PLURAL))
            ret.pluralName = props.get(PROP_ENTRY_TYPE_NAME_PLURAL);
        return ret;
    }

    public static void saveEntryTypeNames(Container container, String issueDefName, EntryTypeNames names)
    {
        saveEntryTypeNames(container, issueDefName, names.singularName, names.pluralName);
    }

    public static void saveEntryTypeNames(Container container, String issueDefName, String singularName, String pluralName)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(container, getPropMapName(issueDefName), true);
        props.put(PROP_ENTRY_TYPE_NAME_SINGULAR, singularName);
        props.put(PROP_ENTRY_TYPE_NAME_PLURAL, pluralName);
        props.save();
    }

    public static @Nullable Group getAssignedToGroup(Container c, String issueDefName)
    {
        Map<String, String> props = PropertyManager.getProperties(c, getPropMapName(issueDefName));

        String groupId = props.get(PROP_ASSIGNED_TO_GROUP);

        if (null == groupId)
            return null;

        return SecurityManager.getGroup(Integer.valueOf(groupId));
    }

    public static void saveAssignedToGroup(Container c, String issueDefName,  @Nullable Group group)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(c, getPropMapName(issueDefName), true);
        props.put(PROP_ASSIGNED_TO_GROUP, null != group ? String.valueOf(group.getUserId()) : "0");
        props.save();
        uncache();  // uncache the assigned to list
    }

    public static @Nullable User getDefaultAssignedToUser(Container c, String issueDefName)
    {
        Map<String, String> props = PropertyManager.getProperties(c, getPropMapName(issueDefName));
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

    public static void saveDefaultAssignedToUser(Container c, String issueDefName, @Nullable User user)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(c, getPropMapName(issueDefName), true);
        props.put(PROP_DEFAULT_ASSIGNED_TO_USER, null != user ? String.valueOf(user.getUserId()) : null);
        props.save();
    }

    public static Collection<Container> getMoveDestinationContainers(Container c, User user, String issueDefName)
    {
        List<Container> containers = new ArrayList<>();

        if (issueDefName != null)
        {
            IssueListDef issueListDef = IssueManager.getIssueListDef(c, issueDefName);
            if (issueListDef != null)
            {
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("name"), issueDefName);
                SimpleFilter.FilterClause filterClause = IssueListDef.createFilterClause(issueListDef, user);

                if (filterClause != null)
                    filter.addClause(filterClause);
                else
                    filter.addCondition(FieldKey.fromParts("container"), c);

                for (IssueListDef def : new TableSelector(IssuesSchema.getInstance().getTableInfoIssueListDef(), filter, null).getArrayList(IssueListDef.class))
                {
                    // exclude current container
                    if (!def.getContainerId().equals(c.getId()))
                        containers.add(ContainerManager.getForId(def.getContainerId()));
                }
            }
        }
        return containers;
    }

   public static void moveIssues(User user, List<Integer> issueIds, Container dest) throws IOException
    {
        DbSchema schema = IssuesSchema.getInstance().getSchema();
        try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
        {
            List<AttachmentParent> attachmentParents = new ArrayList<>();
            Integer issueDefId = null;
            Container issueDefContainer = null;
            List<String> entityIds = new ArrayList<>();
            for (int issueId : issueIds)
            {
                IssueObject issue = IssueManager.getIssue(null, user, issueId);
                if (issue != null)
                {
                    if (issue.getIssueDefId() != null && issueDefId == null)
                    {
                        issueDefId = issue.getIssueDefId();
                        issueDefContainer = issue.getContainerFromId();
                    }
                    entityIds.add(issue.getEntityId());

                    issue.getCommentObjects().forEach(comment -> attachmentParents.add(new CommentAttachmentParent(comment)));
                }
            }
            if (issueDefId != null)
            {
                // these should all be within the same domain so we don't care which issuedef we get, they
                // should all be the same provisioned table
                IssueListDef issueDef = getIssueListDef(issueDefContainer, issueDefId);
                if (issueDef != null)
                {
                    // get the destination issue definition
                    IssueListDef destIssueDef = getIssueListDef(dest, issueDef.getName());

                    if (destIssueDef != null)
                    {
                        SQLFragment update = new SQLFragment("UPDATE issues.issues SET Container = ?, IssueDefId = ? ", dest, destIssueDef.getRowId());
                        update.append("WHERE issueId ");
                        schema.getSqlDialect().appendInClauseSql(update, issueIds);
                        new SqlExecutor(schema).execute(update);

                        // change the container for the provisioned table provided all issues are moving within
                        // the same domain
                        TableInfo table = issueDef.createTable(user);
                        SQLFragment sql = new SQLFragment("UPDATE ").append(table, "").
                                append("SET container = ? WHERE entityId ");
                        sql.add(dest);
                        schema.getSqlDialect().appendInClauseSql(sql, entityIds);

                        new SqlExecutor(schema).execute(sql);

                        AttachmentService.get().moveAttachments(dest, attachmentParents, user);
                        transaction.commit();
                    }
                    else
                        _log.warn("Unable to locate the destination issue list definition");
                }
                else
                    _log.warn("Attempting to move an issue not associated with a domain");
            }
            else
            {
                _log.warn("Attempting to move an issue not all within the same domain");
            }
        }
    }

    /**
     * @param current
     * @return Container from which current's admin settings are inherited from
     */
    public static Container getInheritFromContainer(Container current)
    {
        Map<String, String> props = PropertyManager.getProperties(current, CAT_DEFAULT_INHERIT_FROM_CONTAINER);
        String propsValue = props.get(PROP_DEFAULT_INHERIT_FROM_CONTAINER);

        Container inheritFromContainer = null;

        if(propsValue != null)
        {
            inheritFromContainer = ContainerManager.getForId(propsValue);
        }

        return inheritFromContainer;
    }

    public static Sort.SortDirection getCommentSortDirection(Container c, String issueDefName)
    {
        Map<String, String> props = PropertyManager.getProperties(c, getPropMapName(issueDefName));
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

    public static void saveCommentSortDirection(Container c, String issueDefName, @NotNull Sort.SortDirection direction)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(c, getPropMapName(issueDefName), true);
        props.put(CAT_COMMENT_SORT, direction.toString());
        props.save();
        uncache();  // uncache the assigned to list
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

    public static void uncache()
    {
        ASSIGNED_TO_CACHE.clear(); //Lazy uncache: uncache ALL the containers for updated values in case any folder is inheriting its Admin settings.
    }

    public static void purgeContainer(Container c, User user)
    {
        try (DbScope.Transaction transaction = _issuesSchema.getSchema().getScope().ensureTransaction())
        {
            for (IssueListDef issueListDef : getIssueListDefs(c))
            {
                deleteIssueListDef(issueListDef.getRowId(), c, user);
            }
            ContainerUtil.purgeTable(_issuesSchema.getTableInfoEmailPrefs(), c, null);
            transaction.commit();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }


    /**
     *
     * @param container
     * @return combined Required fields of "current" and "inherited from" container if admin settings are inherited
     */
    public static String getRequiredIssueFields(Container container)
    {
        return getRequiredIssueFields(container, IssuesController.DEFAULT_REQUIRED_FIELDS);
    }

    public static String getRequiredIssueFields(Container container, @Nullable String defaultValue)
    {
        Map<String, String> map = PropertyManager.getProperties(getInheritFromOrCurrentContainer(container), ISSUES_PREF_MAP);
        String requiredFields = map.get(ISSUES_REQUIRED_FIELDS);

        if(getInheritFromContainer(container) != null)
        {
            return getMyRequiredIssueFields(container) + ";" + requiredFields;
        }
        return null == requiredFields ? defaultValue : requiredFields.toLowerCase();
    }

    /**
     *
     * @param container
     * @return Required fields of the current container
     */
    public static String getMyRequiredIssueFields(Container container)
    {
        Map<String, String> mapCurrent = PropertyManager.getProperties(container, ISSUES_PREF_MAP);
        String requiredFieldsCurrent = mapCurrent.get(ISSUES_REQUIRED_FIELDS);
        return (null == requiredFieldsCurrent) ? IssuesController.DEFAULT_REQUIRED_FIELDS : requiredFieldsCurrent.toLowerCase();
    }

    /**
     *
     * @param container
     * @return Required fields of the container from which current container's admin settings were inherited from
     */
    public static String getInheritedRequiredIssueFields(Container container)
    {
        Map<String, String> map = PropertyManager.getProperties(getInheritFromOrCurrentContainer(container), ISSUES_PREF_MAP);
        String requiredFields = map.get(ISSUES_REQUIRED_FIELDS);
        return null == requiredFields ? IssuesController.DEFAULT_REQUIRED_FIELDS : requiredFields.toLowerCase();
    }


    public static void setRequiredIssueFields(Container container, String requiredFields)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(container, ISSUES_PREF_MAP, true);

        if (!StringUtils.isEmpty(requiredFields))
            requiredFields = requiredFields.toLowerCase();
        map.put(ISSUES_REQUIRED_FIELDS, requiredFields);
        map.save();
    }

    public static void setRequiredIssueFields(Container container, String[] requiredFields)
    {
            final StringBuilder sb = new StringBuilder();
            if (requiredFields.length > 0)
            {
                String sep = "";
                for (String field : requiredFields)
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
    

    public static void indexIssues(IndexTask task, @NotNull Container c, Date modifiedSince)
    {
        SearchService ss = SearchService.get();
        if (null == ss)
            return;
        
        SimpleFilter f = SimpleFilter.createContainerFilter(c);
        SearchService.LastIndexedClause incremental = new SearchService.LastIndexedClause(_issuesSchema.getTableInfoIssues(), modifiedSince, null);
        if (!incremental.isEmpty())
            f.addClause(incremental);
        if (f.getClauses().isEmpty())
            f = null;

        // Index issues in batches of 100
        new TableSelector(_issuesSchema.getTableInfoIssues(), PageFlowUtil.set("issueid"), f, null)
            .forEachBatch(Integer.class, 100, batch -> task.addRunnable(new IndexGroup(task, batch), SearchService.PRIORITY.group));
    }

    private static class IndexGroup implements Runnable
    {
        private final List<Integer> _ids;
        private final IndexTask _task;
        
        IndexGroup(IndexTask task, List<Integer> ids)
        {
            _ids = ids;
            _task = task;
        }

        @Override
        public void run()
        {
            User user = new LimitedUser(UserManager.getGuestUser(), new int[0], Collections.singleton(RoleManager.getRole(ReaderRole.class)), false);
            indexIssues(null, user, _task, _ids);
        }
    }


    /* CONSIDER: some sort of generator interface instead */
    public static void indexIssues(@Nullable Container container, User user, IndexTask task, Collection<Integer> ids)
    {
        if (ids.isEmpty())
            return;

        SQLFragment f = new SQLFragment();
        f.append("SELECT I.issueId, I.container, I.entityid, I.duplicate, ")
                .append("C.comment\n");
        f.append("FROM issues.issues I \n")
                .append("LEFT OUTER JOIN issues.comments C ON I.issueid = C.issueid\n");

        for (Integer id : ids)
        {
            IssueObject issue = IssueManager.getIssue(container, user, id);
            if (issue != null)
                queueIssue(task, id, issue.getProperties(), issue.getCommentObjects());
        }
    }


    static void indexIssue(Container container, User user, @Nullable IndexTask task, IssueObject issue)
    {
        if (task == null)
        {
            SearchService ss = SearchService.get();
            if (null == ss)
                return;
            task = ss.defaultTask();
        }

        // UNDONE: broken ??
        // task.addResource(new IssueResource(issue), SearchService.PRIORITY.item);

        // try requery instead
        indexIssues(container, user, task, Collections.singleton(issue.getIssueId()));
    }


    static void queueIssue(IndexTask task, int id, Map<String,Object> m, Collection<IssueObject.CommentObject> comments)
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
            @Override
            public WebdavResource resolve(@NotNull String resourceIdentifier)
            {
                return IssueManager.resolve(resourceIdentifier);
            }

            @Override
            public HttpView getCustomSearchResult(User user, @NotNull String resourceIdentifier)
            {
                int issueId;
                try
                {
                    issueId = Integer.parseInt(resourceIdentifier);
                }
                catch (NumberFormatException x)
                {
                    return null;
                }

                final IssueObject issue = getIssue(null, user, issueId);
                if (null == issue)
                    return null;
                Container c = issue.lookupContainer();
                if (null == c || !c.hasPermission(user, ReadPermission.class))
                    return null;

                return new IssueSummaryView(issue);
            }
        };
    }

    public static class IssueSummaryView extends JspView
    {
        IssueSummaryView(IssueObject issue)
        {
            super("/org/labkey/issue/view/searchSummary.jsp", issue);
        }
    }

    public static Collection<IssueListDef> getIssueListDefsByKind(@NotNull String kind, Container container)
    {
        return IssueListDefCache.getForDomainKind(container, kind);
    }

    public static Collection<IssueListDef> getIssueListDefs(Container container)
    {
        if (container != null)
            return IssueListDefCache.getIssueListDefs(container);
        else
            return new TableSelector(IssuesSchema.getInstance().getTableInfoIssueListDef(), null, null).getArrayList(IssueListDef.class);
    }

    @Nullable
    public static IssueListDef getIssueListDef(Container container, String name)
    {
        return IssueListDefCache.getIssueListDef(container, name);
    }

    @Nullable
    public static IssueListDef getIssueListDef(Container container, int rowId)
    {
        return IssueListDefCache.getIssueListDef(container, rowId);
    }

    /**
     * Returns the default issue list definition if there is only one in scope.
     */
    @Nullable
    public static String getDefaultIssueListDefName(Container container)
    {
        IssueListDef defaultIssueListDef = getDefaultIssueListDef(container);
        if (defaultIssueListDef != null)
            return defaultIssueListDef.getName();

        return null;
    }

    /**
     * Returns the default issue list definition if there is only one in scope.
     */
    public static IssueListDef getDefaultIssueListDef(Container container)
    {
        IssueListDef defaultIssueListDef = getIssueListDef(container, IssueListDef.DEFAULT_ISSUE_LIST_NAME);
        if (defaultIssueListDef != null)
            return defaultIssueListDef;

        Collection<IssueListDef> issueListDefs = IssueManager.getIssueListDefs(container);
        if (issueListDefs.size() == 1)
            return issueListDefs.iterator().next();

        return null;
    }

    public static IssuesListDefProvider getIssuesListDefProvider(Container container, @NotNull String providerName)
    {
        List<IssuesListDefProvider> providers = IssuesListDefService.get().getEnabledIssuesListDefProviders(container);
        if (providers == null || providers.isEmpty())
            throw new IllegalArgumentException("No IssuesListDefProviders available");

        IssuesListDefProvider provider = null;
        for (IssuesListDefProvider enabledProvider : providers)
        {
            if (enabledProvider.getName().equalsIgnoreCase(providerName))
            {
                provider = enabledProvider;
                break;
            }
        }

        if (provider == null)
            throw new IllegalArgumentException("Could not find the IssuesListDefProvider with the following name: " + providerName);

        return provider;
    }

    public static IssueListDef createIssueListDef(Container container, User user, @NotNull String providerName, @NotNull String label)
    {
        String name = IssuesListDefTable.nameFromLabel(label);
        IssueListDef existingDef = getIssueListDef(container, name);
        if (existingDef != null)
            throw new IllegalArgumentException("An IssueListDef already exists for this container with the following name: " + name);

        IssuesListDefProvider provider = getIssuesListDefProvider(container, providerName);

        IssueListDef def = new IssueListDef();
        def.setName(name);
        def.setLabel(label);
        def.setKind(provider.getName());
        def.beforeInsert(user, container.getId());
        return def.save(user);
    }

    public static void deleteIssueDefsForDomain(User user, Domain domain)
    {
        if (domain != null)
        {
            try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                String issueListDefName = IssuesListDefTable.nameFromLabel(domain.getName());
                ContainerFilter containerFilter = IssueListDef.getContainerFilter(domain, user);
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Name"), issueListDefName);

                if (containerFilter != null)
                    filter.addCondition(containerFilter.createFilterClause(IssuesSchema.getInstance().getSchema(), FieldKey.fromParts("container")));
                else
                    filter.addCondition(FieldKey.fromParts("container"), domain.getContainer());

                List<IssueListDef> issueListDefList = new TableSelector(IssuesSchema.getInstance().getTableInfoIssueListDef(), filter, null).getArrayList(IssueListDef.class);
                for (IssueListDef def : issueListDefList)
                {
                    Container defContainer = ContainerManager.getForId(def.getContainerId());
                    _deleteIssueListDef(def, defContainer, user, false);
                }
                transaction.commit();
            }
        }
    }

    public static void deleteIssueListDef(int rowId, Container c, User user)
    {
        IssueListDef def = getIssueListDef(c, rowId);
        if (def == null)
            throw new IllegalArgumentException("Can't find IssueDef with rowId " + rowId);

        _deleteIssueListDef(def, c, user, true);
    }

    private static void _deleteIssueListDef(IssueListDef def, Container c, User user, boolean deleteDomain)
    {
        Domain d = def.getDomain(user);
        if (d == null && deleteDomain)
            throw new IllegalArgumentException("Unable to find the domain for this IssueDef");

        try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            TableInfo issueDefTable = IssuesSchema.getInstance().getTableInfoIssueListDef();

            truncateIssueList(def, c, user);
            Table.delete(issueDefTable, def.getRowId());

            if (deleteDomain)
            {
                // if there are no other containers referencing this domain, then it's safe to delete
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Name"), def.getName());
                SimpleFilter.FilterClause filterClause = IssueListDef.createFilterClause(def, user);

                if (filterClause != null)
                    filter.addClause(filterClause);
                else
                    filter.addCondition(FieldKey.fromParts("container"), c);

                if (new TableSelector(issueDefTable, filter, null).getRowCount() == 0)
                {
                    d.getDomainKind().deleteDomain(user, d);
                }
            }
            IssueListDefCache.uncache(c);
            transaction.commit();
        }
    }

    public static int truncateIssueList(IssueListDef def, Container c, User user)
    {
        if (!c.hasPermission(user, AdminPermission.class))
            throw new UnauthorizedException();

        try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            int rows = deleteIssueRecords(def, c, user);
            transaction.commit();
            return rows;
        }
    }

    /**
     * Delete all records from the issues provisioned table and the issues hard
     * table for the specified folder.
     */
    private static int deleteIssueRecords(IssueListDef issueDef, Container c, User user)
    {
        assert IssuesSchema.getInstance().getSchema().getScope().isTransactionActive();
        TableInfo issueDefTable = issueDef.createTable(user);

        List<AttachmentParent> attachmentParents = new ArrayList<>();

        // get the list of comments for all issues being deleted (these are the attachment parents)
        SQLFragment commentsSQL = new SQLFragment("SELECT EntityId FROM ").append(IssuesSchema.getInstance().getTableInfoComments(), "").
                append(" WHERE IssueId IN (SELECT IssueId FROM ").append(IssuesSchema.getInstance().getTableInfoIssues(), "").
                append(" WHERE IssueDefId = ?)");
        commentsSQL.add(issueDef.getRowId());
        new SqlSelector(IssuesSchema.getInstance().getSchema(), commentsSQL).forEach(String.class, entityId -> {
            CommentAttachmentParent parent = new CommentAttachmentParent(c.getId(), entityId);
            attachmentParents.add(parent);
        });

        AttachmentService.get().deleteAttachments(attachmentParents);

        // clean up comments
        SQLFragment deleteCommentsSQL = new SQLFragment("DELETE FROM ").append(IssuesSchema.getInstance().getTableInfoComments(), "").
                append(" WHERE IssueId IN (SELECT issueId FROM ").append(IssuesSchema.getInstance().getTableInfoIssues(), "").
                append(" WHERE IssueDefId = ? )");
        deleteCommentsSQL.add(issueDef.getRowId());
        new SqlExecutor(IssuesSchema.getInstance().getSchema()).execute(deleteCommentsSQL);

        // clean up related issues
        SQLFragment deleteRelatedSQL = new SQLFragment("DELETE FROM ").append(IssuesSchema.getInstance().getTableInfoRelatedIssues(), "").
                append(" WHERE IssueId IN (SELECT issueId FROM ").append(IssuesSchema.getInstance().getTableInfoIssues(), "").
                append(" WHERE IssueDefId = ? )");
        deleteRelatedSQL.addAll(issueDef.getRowId());
        new SqlExecutor(IssuesSchema.getInstance().getSchema()).execute(deleteRelatedSQL);

        deleteRelatedSQL = new SQLFragment("DELETE FROM ").append(IssuesSchema.getInstance().getTableInfoRelatedIssues(), "").
                append(" WHERE RelatedIssueId IN (SELECT IssueId FROM ").append(IssuesSchema.getInstance().getTableInfoIssues(), "").
                append(" WHERE IssueDefId = ? )");
        deleteRelatedSQL.addAll(issueDef.getRowId());
        new SqlExecutor(IssuesSchema.getInstance().getSchema()).execute(deleteRelatedSQL);

        // delete records from the issue table
        SQLFragment deleteIssuesSQL = new SQLFragment("DELETE FROM ").append(IssuesSchema.getInstance().getTableInfoIssues(), "").
                append(" WHERE IssueDefId = ?");
        deleteIssuesSQL.add(issueDef.getRowId());
        int count = new SqlExecutor(IssuesSchema.getInstance().getSchema()).execute(deleteIssuesSQL);

        // delete records from the provisioned table
        SQLFragment deleteProvisionedSQL = new SQLFragment("DELETE FROM ").append(issueDefTable, "").
                append(" WHERE Container = ?");
        deleteProvisionedSQL.add(c);
        int count2 = new SqlExecutor(IssuesSchema.getInstance().getSchema()).execute(deleteProvisionedSQL);

        assert count == count2;
        return count;
    }

    @Nullable
    public static IssueListDef getIssueListDef(IssueObject issue)
    {
        if (issue.getIssueDefId() != null)
        {
            return getIssueListDef(issue.getContainerFromId(), issue.getIssueDefId());
        }
        else if (issue.getIssueDefName() != null)
        {
            return getIssueListDef(issue.getContainerFromId(), issue.getIssueDefName());
        }
        return null;
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

        final IssueObject issue = getIssue(null, User.getSearchUser(), issueId);
        if (null == issue)
            return null;

        return new IssueResource(issue);
    }


    static final ObjectFactory _issueFactory = ObjectFactory.Registry.getFactory(IssueObject.class);

    private static class IssueResource extends AbstractDocumentResource
    {
        Collection<IssueObject.CommentObject> _comments;
        final int _issueId;

        IssueResource(IssueObject issue)
        {
            super(new Path("issue:" + issue.getIssueId()));
            _issueId = issue.issueId;
            Map<String,Object> m = _issueFactory.toMap(issue, null);
            // UNDONE: custom field names
            // UNDONE: user names
            m.remove("comments");
            _containerId = issue.getContainerId();
            _properties = m;
            _comments = issue.getCommentObjects();
            _properties.put(categories.toString(), searchCategory.getName());
        }


        IssueResource(int issueId, Map<String,Object> m, Collection<IssueObject.CommentObject> comments)
        {
            super(new Path("issue:"+ issueId));
            _issueId = issueId;
            _containerId = (String)m.get("folder");
            _properties = m;
            _comments = comments;
            _properties.put(categories.toString(), searchCategory.getName());
        }

        @Override
        public long getCreated()
        {
            Date date = (Date)_properties.get("created");
            if (date != null)
                return date.getTime();
            return Long.MIN_VALUE;
        }

        @Override
        public User getCreatedBy()
        {
            Integer userId = (Integer)_properties.get("createdBy");
            if (userId != null)
                return UserManager.getUser(userId);
            return null;
        }

        @Override
        public User getModifiedBy()
        {
           Integer userId = (Integer)_properties.get("modifiedBy");
           if (userId != null)
               return UserManager.getUser(userId);
            return null;
        }

        @Override
        public long getLastModified()
        {
            Date date = (Date)_properties.get("modified");
            if (date != null)
                return date.getTime();
            return Long.MIN_VALUE;
        }

        @Override
        public void setLastIndexed(long ms, long modified)
        {
            IssueManager.setLastIndexed(_containerId, _issueId, ms);
        }

        @Override
        public String getDocumentId()
        {
            return "issue:"+ _properties.get("issueid");
        }


        @Override
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


        @Override
        public FileStream getFileStream(User user) throws IOException
        {
            String title = String.valueOf(_properties.get("title"));

            try (ByteArrayOutputStream bos = new ByteArrayOutputStream())
            {
                try (Writer out = new OutputStreamWriter(bos, StringUtilsLabKey.DEFAULT_CHARSET))
                {
                    out.write("<html><head><title>");
                    out.write(PageFlowUtil.filter(title));
                    out.write("</title></head><body>");
                    out.write(PageFlowUtil.filter(title));
                    out.write("\n");
                    for (IssueObject.CommentObject c : _comments)
                        if (!HtmlString.isBlank(c.getHtmlComment()))
                            out.write(c.getHtmlComment().toString());
                }
                return new FileStream.ByteArrayFileStream(bos.toByteArray());
            }
        }
        
        @Override
        public InputStream getInputStream(User user) throws IOException
        {
            return getFileStream(user).openInputStream();
        }

        @Override
        public long copyFrom(User user, FileStream in)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getContentLength()
        {
            throw new UnsupportedOperationException();
        }
    }


    public static class TestCase extends Assert
    {
        @BeforeClass
        public static void setUp()
        {
            TestContext context = TestContext.get();
            Container c = JunitUtil.getTestContainer();

            if (IssueManager.getIssueListDef(c, IssueListDef.DEFAULT_ISSUE_LIST_NAME) == null)
            {
                // ensure the issue module is enabled for this folder
                Module issueModule = ModuleLoader.getInstance().getModule(IssuesModule.NAME);
                Set<Module> activeModules = c.getActiveModules();
                if (!activeModules.contains(issueModule))
                {
                    Set<Module> newActiveModules = new HashSet<>();
                    newActiveModules.addAll(activeModules);
                    newActiveModules.add(issueModule);

                    c.setActiveModules(newActiveModules);
                }
                IssueListDef def = new IssueListDef();
                def.setName(IssueListDef.DEFAULT_ISSUE_LIST_NAME);
                def.setLabel(IssueListDef.DEFAULT_ISSUE_LIST_NAME);
                def.setKind(IssueDefDomainKind.NAME);
                def.beforeInsert(context.getUser(), c.getId());
                def.save(context.getUser());
            }
        }

        @Test
        public void testIssues()
        {
            TestContext context = TestContext.get();

            User user = context.getUser();
            assertTrue("login before running this test", null != user);
            assertFalse("login before running this test", user.isGuest());

            Container c = JunitUtil.getTestContainer();
            ObjectFactory factory = ObjectFactory.Registry.getFactory(IssueObject.class);

            int issueId;

            //
            // INSERT
            //
            {
                IssueObject issue = new IssueObject();
                issue.open(c, user);
                issue.setAssignedTo(user.getUserId());
                issue.setTitle("This is a junit test bug");
                issue.addComment(user, HtmlString.unsafe("new issue"));
                issue.setPriority("3");
                issue.setIssueDefName(IssueListDef.DEFAULT_ISSUE_LIST_NAME);

                factory.toMap(issue, issue.getProperties());
                IssueManager.saveIssue(user, c, issue);
                issueId = issue.getIssueId();
            }

            // verify
            {
                IssueObject issue = IssueManager.getIssue(c, user, issueId);
                assertEquals("This is a junit test bug", issue.getTitle());
                assertEquals(user.getUserId(), issue.getCreatedBy());
                assertTrue(issue.getCreated().getTime() != 0);
                assertTrue(issue.getModified().getTime() != 0);
                assertEquals(user.getUserId(), issue.getAssignedTo().intValue());
                assertEquals(IssueObject.statusOPEN, issue.getStatus());
                assertEquals(1, issue.getCommentObjects().size());
				String comment = (issue.getCommentObjects().iterator().next()).getHtmlComment().toString();
                assertTrue("new issue".equals(comment));
            }

            //
            // ADD COMMENT
            //
            {
                IssueObject issue = IssueManager.getIssue(c, user, issueId);
                issue.addComment(user, HtmlString.unsafe("what was I thinking"));
                factory.toMap(issue, issue.getProperties());
                IssueManager.saveIssue(user, c, issue);
            }

            // verify
            {
                IssueObject issue = IssueManager.getIssue(c, user, issueId);
                assertEquals(2, issue.getCommentObjects().size());
                Iterator it = issue.getCommentObjects().iterator();
                assertEquals("new issue", ((IssueObject.CommentObject) it.next()).getHtmlComment().toString());
                assertEquals("what was I thinking", ((IssueObject.CommentObject) it.next()).getHtmlComment().toString());
            }

            //
            // ADD INVALID COMMENT
            //
            {
                IssueObject issue = IssueManager.getIssue(c, user, issueId);
                issue.addComment(user, HtmlString.unsafe("invalid character <\u0010>"));
                try
                {
                    IssueManager.saveIssue(user, c, issue);
                    fail("Expected to throw exception for an invalid character.");
                }
                catch (Exception e)
                {
                    assertEquals("comment has invalid characters", e.getCause().getMessage());
                }
            }

            //
            // RESOLVE
            //
            {
                IssueObject issue = IssueManager.getIssue(c, user, issueId);
                assertNotNull("issue not found", issue);
                issue.resolve(user);
                issue.setResolution("fixed");
                issue.addComment(user, HtmlString.unsafe("fixed it"));
                factory.toMap(issue, issue.getProperties());
                IssueManager.saveIssue(user, c, issue);
            }

            // verify
            {
                IssueObject issue = IssueManager.getIssue(c, user, issueId);
                assertEquals(IssueObject.statusRESOLVED, issue.getStatus());
                assertEquals(3, issue.getCommentObjects().size());
            }

            //
            // CLOSE
            //
            {
                IssueObject issue = IssueManager.getIssue(c, user, issueId);
                assertNotNull("issue not found", issue);
                issue.close(user);
                issue.addComment(user, HtmlString.unsafe("closed"));
                factory.toMap(issue, issue.getProperties());
                IssueManager.saveIssue(user, c, issue);
            }

            // verify
            {
                IssueObject issue = IssueManager.getIssue(c, user, issueId);
                assertEquals(IssueObject.statusCLOSED, issue.getStatus());
                assertEquals(4, issue.getCommentObjects().size());
            }
        }

        @Test
        public void testEmailHiding()
        {
            Container fakeRoot = ContainerManager.createFakeContainer(null, null);

            User user = UserManager.getGuestUser();
            boolean showEmailAddresses = SecurityManager.canSeeUserDetails(fakeRoot, user);
            assertFalse("readers should not see emails", showEmailAddresses);
            List<User> possibleUsers = SecurityManager.getUsersWithPermissions(fakeRoot, Collections.singleton(ReadPermission.class));

            for (AjaxCompletion completion : UserManager.getAjaxCompletions(possibleUsers, user, fakeRoot))
            {
                User u = UserManager.getUserByDisplayName(completion.getInsertionText());
                if (u != null)
                    assertFalse("readers should not see emails", completion.getDisplayText().toLowerCase().contains(u.getEmail().toLowerCase()));
            }

            // this should be an admin...
            user = TestContext.get().getUser();
            showEmailAddresses = SecurityManager.canSeeUserDetails(fakeRoot, user);
            assertTrue("admins should see emails", showEmailAddresses);

            for (AjaxCompletion completion : UserManager.getAjaxCompletions(possibleUsers, user, fakeRoot))
            {
                User u = UserManager.getUserByDisplayName(completion.getInsertionText());
                if (u != null)
                    assertTrue("admins should see emails", completion.getDisplayText().toLowerCase().contains(u.getEmail().toLowerCase()));
            }
        }

        @AfterClass
        public static void tearDown()
        {
            Container c = JunitUtil.getTestContainer();
            IssueListDef issueListDef = IssueManager.getIssueListDef(c, IssueListDef.DEFAULT_ISSUE_LIST_NAME);
            if (issueListDef != null)
                IssueManager.deleteIssueListDef(issueListDef.getRowId(), c, TestContext.get().getUser());
        }
    }
}
