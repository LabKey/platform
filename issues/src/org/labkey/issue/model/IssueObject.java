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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Entity;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Sort;
import org.labkey.api.issues.Issue;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Tuple3;
import org.springframework.validation.Errors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;


public class IssueObject extends Entity implements Serializable, Cloneable, Issue
{
    public static String statusOPEN = "open";
    public static String statusRESOLVED = "resolved";
    public static String statusCLOSED = "closed";

    protected byte[] _ts;
    protected int issueId;
    protected Collection<Integer> duplicates;
    protected String related;
    protected Set<Integer> relatedIssues;
    protected List<CommentObject> _comments = new ArrayList<>();
    protected List<CommentObject> _added = null;
    protected Integer _issueDefId;
    protected String _issueDefName;         // used only in the actions

    private Map<String, Object> _properties = new CaseInsensitiveHashMap<>();

    // built in but optional properties
    public enum Prop
    {
        area,
        type,
        priority,
        milestone
    }

    public IssueObject()
    {
        MemTracker.getInstance().put(this);
    }

    public IssueObject(Issue issue)
    {
        issueId = issue.getIssueId();
        _issueDefId = issue.getIssueDefId();
        _issueDefName = issue.getIssueDefName();
        _properties.putAll(issue.getProperties());
        duplicates = new ArrayList<>(issue.getDuplicates());
        relatedIssues = new TreeSet<>(issue.getRelatedIssues());
        related = issue.getRelated();
        setContainerId(issue.getContainerId());

        if (issue instanceof Entity entity)
        {
            setEntityId(entity.getEntityId());
            setCreatedBy(entity.getCreatedBy());
            setCreated(entity.getCreated());
            setModifiedBy(entity.getModifiedBy());
            setModified(entity.getModified());
        }
    }

    public void change(User u)
    {
        beforeUpdate(u);
    }

    public void open(Container c, User u)
    {
        if (getCreatedBy() != 0)
            u = UserManager.getUser(getCreatedBy()) != null ? UserManager.getUser(getCreatedBy()) : u;
        beforeInsert(u, c.getId());
        change(u);

        setStatus(statusOPEN);
    }

    public void beforeReOpen(Container c)
    {
        beforeReOpen(c, false);
    }

    /**
     * Sets resolution, resolved, duplicate, and resolvedBy to null, also assigns a value to assignedTo.
     * @param beforeView This is necessary because beforeReOpen gets called twice. Once before we display the view
     * and once during handlePost. If we change assignedTo during handlePost then we overwrite the user's choice,
     * beforeView prevents that from happening.
     */
    public void beforeReOpen(Container c, boolean beforeView)
    {
        setResolution(null);
        setResolved(null);
        setDuplicate(null);

        if (getResolvedBy() != null && beforeView)
            setAssignedTo(IssueManager.validateAssignedTo(c, getResolvedBy()));

        setResolvedBy(null);
    }

    public void beforeUpdate(Container c)
    {
        // Make sure assigned to user still has permission
        setAssignedTo(IssueManager.validateAssignedTo(c, getAssignedTo()));
    }


    public void beforeResolve(Container c, User u)
    {
        setStatus(statusRESOLVED);

        setResolvedBy(u.getUserId()); // Current user
        setResolved(new Date());      // Current date

        setAssignedTo(IssueManager.validateAssignedTo(c, getCreatedBy()));
        if (getTitle() != null && getTitle().startsWith("**"))
        {
            setTitle(getTitle().substring(2));
        }
    }

    public void resolve(User u)
    {
        change(u);
        setStatus(statusRESOLVED);

        setResolvedBy(getModifiedBy());
        setResolved(getModified());
    }

    public void close(User u)
    {
        change(u);
        setStatus(statusCLOSED);

        setClosedBy(getModifiedBy());
        setClosed(getModified());
        // UNDONE: assignedTo is not nullable in database
        // UNDONE: let application enforce non-null for open/resolved bugs
        // UNDONE: currently AssignedTo list defaults to Guest (user 0)
        _properties.put("assignedTo", 0);
    }

    @Override
    public int getIssueId()
    {
        return issueId;
    }

    public void setIssueId(int issueId)
    {
        this.issueId = issueId;
    }

    @Override
    public String getTitle()
    {
        return (String)_properties.get("title");
    }

    public void setTitle(String title)
    {
        _properties.put("title", title);
    }

    public String getComment()
    {
        return (String)_properties.get("comment");
    }

    public void setComment(String comment)
    {
        _properties.put("comment", comment);
    }

    @Override
    public String getStatus()
    {
        return (String)_properties.get("status");
    }

    public void setStatus(String status)
    {
        _properties.put("status", status);
    }

    @Override
    public Integer getAssignedTo()
    {
        return (Integer)_properties.get("assignedTo");
    }


    public void setAssignedTo(Integer assignedTo)
    {
        //if (null != assignedTo && assignedTo == 0) assignedTo = null;

        _properties.put("assignedTo", assignedTo);
    }


    public String getAssignedToName(User currentUser)
    {
        return UserManager.getDisplayName(getAssignedTo() == null ? 0 : getAssignedTo(), currentUser);
    }

    @Deprecated
    // user setProperty instead
    public void setType(String type)
    {
        _properties.put("type", type);
    }

    @Deprecated
    // user setProperty instead
    public void setArea(String area)
    {
        _properties.put("area", area);
    }

    @Deprecated
    // user setProperty instead
    public void setPriority(String priority)
    {
        if (priority != null)
            _properties.put("priority", priority);
    }

    @Deprecated
    // user setProperty instead
    public void setMilestone(String milestone)
    {
        _properties.put("milestone", milestone);
    }

    public String getCreatedByName(User currentUser)
    {
        return UserManager.getDisplayName(getCreatedBy(), currentUser);
    }

    @Override
    public Integer getResolvedBy()
    {
        return (Integer)_properties.get("resolvedBy");
    }

    public void setResolvedBy(Integer resolvedBy)
    {
        if (null != resolvedBy && resolvedBy == 0) resolvedBy = null;
        _properties.put("resolvedBy", resolvedBy);
    }

    public String getResolvedByName(User currentUser)
    {
        return UserManager.getDisplayName(getResolvedBy(), currentUser);
    }

    @Override
    public Date getResolved()
    {
        return (Date) JdbcType.TIMESTAMP.convert(_properties.get("resolved"));
    }

    public void setResolved(Date resolved)
    {
        _properties.put("resolved", resolved);
    }

    @Override
    public String getResolution()
    {
        return (String)_properties.get("resolution");
    }

    public void setResolution(String resolution)
    {
        _properties.put("resolution", resolution);
    }

    public Integer getDuplicate()
    {
        return (Integer)_properties.get("duplicate");
    }

    public void setDuplicate(Integer duplicate)
    {
        if (null != duplicate && duplicate == 0) duplicate = null;
        _properties.put("duplicate", duplicate);
    }

    @Override
    public Collection<Integer> getDuplicates()
    {
        return duplicates != null ? duplicates : Collections.emptyList();
    }

    public void setDuplicates(Collection<Integer> dups)
    {
        if (dups != null) duplicates = dups;
    }

    @Override
    public String getRelated()
    {
        return related;
    }

    // this is used for form-binding
    public void setRelated(String relatedText)
    {
        if (null != relatedText && relatedText.equals("")) relatedText = null;
        this.related = relatedText;
    }

    @Override
    @NotNull
    public Set<Integer> getRelatedIssues()
    {
        return relatedIssues != null ? Collections.unmodifiableSet(relatedIssues) : Collections.emptySet();
    }

    public void setRelatedIssues(@NotNull Collection<Integer> rels)
    {
        // Use a TreeSet so that the IDs are ordered ascending
        relatedIssues = new TreeSet<>(rels);
    }

    @Override
    public Integer getClosedBy()
    {
        return (Integer)_properties.get("closedBy");
    }

    public void setClosedBy(Integer closedBy)
    {
        if (null != closedBy && closedBy == 0) closedBy = null;
        _properties.put("closedBy", closedBy);
    }

    public String getClosedByName(User currentUser)
    {
        return UserManager.getDisplayName(getClosedBy(), currentUser);
    }

    @Override
    public Date getClosed()
    {
        return (Date) JdbcType.DATE.convert(_properties.get("closed"));
    }

    public void setClosed(Date closed)
    {
        _properties.put("closed", closed);
    }

    @Override
    public Collection<Comment> getComments()
    {
        return new ArrayList<>(getCommentObjects());
    }

    public Collection<CommentObject> getCommentObjects()
    {
        List<CommentObject> result = new ArrayList<>(_comments);

        IssueListDef issueListDef = IssueManager.getIssueListDef(this);
        final Sort.SortDirection sort = IssueManager.getCommentSortDirection(ContainerManager.getForId(getContainerId()), issueListDef != null ? issueListDef.getName() : IssueListDef.DEFAULT_ISSUE_LIST_NAME);
        result.sort((o1, o2) -> o1.getCreated().compareTo(o2.getCreated()) * (sort == Sort.SortDirection.ASC ? 1 : -1));

        return new ArrayList<>(result);
    }

    public CommentObject getLastComment()
    {
        if (null == _comments || _comments.isEmpty())
            return null;

        return _comments.get(_comments.size()-1);
    }

    public boolean setComments(List<CommentObject> comments)
    {
        if (comments != null)
        {
            _comments = comments;
            for (CommentObject comment : _comments)
                comment.setIssue(this);
        }
        return _comments.isEmpty();
    }


    public String getModifiedByName(User currentUser)
    {
        return UserManager.getDisplayName(getModifiedBy(), currentUser);
    }


    @Override
    public IssueObject clone() throws CloneNotSupportedException
    {
        IssueObject clone = (IssueObject)super.clone();

        clone._properties = new CaseInsensitiveHashMap<>();
        clone._properties.putAll(_properties);

        return clone;
    }


    // UNDONE: MAKE work in Table version
    public CommentObject addComment(User user, HtmlString html)
    {
        CommentObject comment = new CommentObject();
        comment.beforeInsert(user, getContainerId());
        comment.setIssue(this);
        comment.setHtmlComment(html);

        _comments.add(comment);
        if (null == _added)
            _added = new ArrayList<>(1);
        _added.add(comment);
        return comment;
    }

    public void setNotifyList(String notifyList)
    {
        if (null != notifyList)
            notifyList = notifyList.replace(";","\n");
        _properties.put("notifyList", notifyList);
    }

    @Override
    public Integer getIssueDefId()
    {
        return _issueDefId;
    }

    public void setIssueDefId(Integer issueDefId)
    {
        _issueDefId = issueDefId;
    }

    @Override
    public String getIssueDefName()
    {
        if (_issueDefName == null && _issueDefId != null)
        {
            IssueListDef issueListDef = IssueManager.getIssueListDef(getContainerFromId(), _issueDefId);
            _issueDefName = issueListDef != null ? issueListDef.getName() : null;
        }
        return _issueDefName;
    }

    public Container getContainerFromId()
    {
        return ContainerManager.getForId(getContainerId());
    }

    public void setIssueDefName(String issueDefName)
    {
        _issueDefName = issueDefName;
    }

    public void parseNotifyList(String notifyList)
    {
        _properties.put("notifyList", UserManager.parseUserListInput(notifyList));
    }

    public String getNotifyList()
    {
        return (String)_properties.get("notifyList");
    }

    @Override
    public List<String> getNotifyListDisplayNames(User user)
    {
        Container c = lookupContainer();
        return getNotifyListUserEmails(getNotifyList(), null).stream()
                .map(t -> {
                    String id = t.first;
                    User u = t.second;
                    ValidEmail v = t.third;
                    return null != u ? u.getAutocompleteName(c, user) : null != v ? v.getEmailAddress() : id;
                })
                .collect(Collectors.toList());
    }


    @Override
    public List<ValidEmail> getNotifyListEmail()
    {
        return getNotifyListEmail(getNotifyList(), null);
    }

    @Override
    public List<Pair<User, ValidEmail>> getNotifyListUserEmail()
    {
        return getNotifyListUserEmails(getNotifyList(), null).stream()
                .map(t -> new Pair<>(t.second, t.third))
                .collect(Collectors.toList());
    }

    public static List<ValidEmail> getNotifyListEmail(String notifyList, @Nullable Errors errors)
    {
        return getNotifyListUserEmails(notifyList, errors).stream()
                .map(t -> t.third)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static List<Tuple3<@NotNull String, @Nullable User, @Nullable ValidEmail>> getNotifyListUserEmails(String notifyList, @Nullable Errors errors)
    {
        String[] raw = StringUtils.split(null == notifyList ? "" : notifyList, ";\n");
        ArrayList<Tuple3<String, User, ValidEmail>> ret = new ArrayList<>(raw.length);
        for (String id : raw)
        {
            if (null == (id=StringUtils.trimToNull(id)))
                continue;

            ValidEmail v = null;
            User u = null;

            try { u = UserManager.getUser(Integer.parseInt(id)); } catch (NumberFormatException x) { };

            if (u == null)
                u = UserManager.getUserByDisplayName(id);

            if (u == null)
            {
                try
                {
                    v = new ValidEmail(id);
                    u = UserManager.getUser(v);
                }
                catch (ValidEmail.InvalidEmailException x) { }
            }

            if (u == null && errors != null)
            {
                errors.reject(ERROR_MSG, "Invalid user '" + id + "'");
                continue;
            }

            // filter out inactive users
            if (u != null && !u.isActive())
                continue;

            if (u != null)
                try { v = new ValidEmail(u.getEmail()); } catch (ValidEmail.InvalidEmailException x) { }

            ret.add(Tuple3.of(id, u, v));
        }
        return ret;
    }

    public Map<String, Object> getProperties()
    {
        return _properties;
    }

    /**
     * Bulk add properties to the issue object
     */
    public void setProperties(Map<String, Object> properties)
    {
        _properties.putAll(properties);
        setSpecialFields(properties);
    }

    public String getProperty(Prop prop)
    {
        Object o = _properties.get(prop.name());
        return o != null ? String.valueOf(o) : "";
    }

    private void setSpecialFields(Map<String, Object> properties)
    {
        for (Map.Entry<String, Object> prop : properties.entrySet())
        {
            if (prop.getKey().equalsIgnoreCase("created") && prop.getValue() instanceof Date)
                setCreated((Date)prop.getValue());
            else if (prop.getKey().equalsIgnoreCase("createdBy") && prop.getValue() instanceof Integer)
                setCreatedBy((Integer)prop.getValue());
            else if (prop.getKey().equalsIgnoreCase("modified") && prop.getValue() instanceof Date)
                setModified((Date)prop.getValue());
            else if (prop.getKey().equalsIgnoreCase("modifiedBy") && prop.getValue() instanceof Integer)
                setModifiedBy((Integer)prop.getValue());
        }
    }

    public void setProperty(String name, Object value)
    {
        _properties.put(name, value);
    }

    public IssueEvent getMostRecentEvent(User user)
    {
        ArrayList<IssueEvent> arr = getOrderedEventArray(user);
        return !arr.isEmpty() ? arr.get(0) : null;
    }

    public ArrayList<IssueEvent> getOrderedEventArray(User user)
    {

        final String DATE_PATTERN = "EEE, d MMM yyyy HH:mm:ss z";
        ArrayList<IssueEvent> activityList = new ArrayList<>();

        if (getCreated() != null)
        {
            activityList.add(new IssueEvent(
                    DateUtil.formatDateTime(getContainerFromId(), getCreated()),
                    DateUtil.formatDateTime(getCreated(), DATE_PATTERN),
                    getCreated().getTime(),
                    "Created",
                    getCreatedByName(user)
            ));
        }

        if (getModified() != null)
        {
            activityList.add(new IssueEvent(
                    DateUtil.formatDateTime(getContainerFromId(), getModified()),
                    DateUtil.formatDateTime(getModified(), DATE_PATTERN),
                    getModified().getTime(),
                    "Modified",
                    getModifiedByName(user)
            ));
        }

        if (getResolved() != null)
        {
            activityList.add(new IssueEvent(
                    DateUtil.formatDateTime(getContainerFromId(), getResolved()),
                    DateUtil.formatDateTime(getResolved(), DATE_PATTERN),
                    getResolved().getTime(),
                    "Resolved",
                    getResolvedByName(user)
            ));
        }

        if (getClosed() != null)
        {
            activityList.add(new IssueEvent(
                    DateUtil.formatDateTime(getContainerFromId(), getClosed()),
                    DateUtil.formatDateTime(getClosed(), DATE_PATTERN),
                    getClosed().getTime(),
                    "Closed",
                    getClosedByName(user)
            ));
        }

        activityList.sort(Comparator.comparing(IssueEvent::getMillis).reversed());
        return activityList;
    }

    @Override
    public boolean equals(Object o)
    {
        // NOTE: the way we use this bean/form, validating _comments will not work for seeing if there is a new comment (which is stored on the form)
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IssueObject issue = (IssueObject) o;

        if (issueId != issue.issueId) return false;
        if (_added != null ? !_added.equals(issue._added) : issue._added != null) return false;
        if (!Arrays.equals(_ts, issue._ts)) return false;
        //if (duplicate != null ? !duplicate.equals(issue.duplicate) : issue.duplicate != null) return false;
        if (duplicates != null ? !duplicates.equals(issue.duplicates) : issue.duplicates != null) return false;
        if (related != null ? !related.equals(issue.related) : issue.related != null) return false;
        if (relatedIssues != null ? !relatedIssues.equals(issue.relatedIssues) : issue.relatedIssues != null)
            return false;
        if (!_properties.equals(issue._properties)) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _ts != null ? Arrays.hashCode(_ts) : 0;
        result = 31 * result + issueId;
        //result = 31 * result + (duplicate != null ? duplicate.hashCode() : 0);
        result = 31 * result + (duplicates != null ? duplicates.hashCode() : 0);
        result = 31 * result + (related != null ? related.hashCode() : 0);
        result = 31 * result + (relatedIssues != null ? relatedIssues.hashCode() : 0);
        result = 31 * result + (_comments != null ? _comments.hashCode() : 0);
        result = 31 * result + (_added != null ? _added.hashCode() : 0);
        result = 31 * result + (_properties.hashCode());
        return result;
    }


    /* CONSIDER: use Announcements/Notes instead of special Comments class */

    public static class CommentObject extends Entity implements Serializable, IssueObject.Comment
    {
        private IssueObject issue;
        private int commentId;
        private HtmlString comment;

        public CommentObject()
        {
        }

        public String getCreatedFullString()
        {
            return DateUtil.formatDateTime(getCreated(),"EEE, d MMM yyyy HH:mm:ss");
        }

        public CommentObject(HtmlString comment)
        {
            this.comment = comment;
        }

        @Override
        public IssueObject getIssue()
        {
            return issue;
        }

        public void setIssue(IssueObject issue)
        {
            this.issue = issue;
        }

        @Override
        public int getCommentId()
        {
            return commentId;
        }

        public void setCommentId(int commentId)
        {
            this.commentId = commentId;
        }

        public String getCreatedByName(User currentUser)
        {
            return UserManager.getDisplayName(getCreatedBy(), currentUser);
        }

        @Override
        public HtmlString getHtmlComment()
        {
            return comment;
        }

        public void setHtmlComment(HtmlString comment)
        {
            this.comment = comment;
        }

        public void setComment(String comment)
        {
            setHtmlComment(HtmlString.unsafe(comment));
        }

        @Override
        public String getContainerId()
        {
            if (issue != null)
                return issue.getContainerId();
            return super.getContainerId();
        }
    }

    public static class IssueEvent implements Comparable<IssueEvent>
    {
        private final String containerFormattedDate;
        private final String fullTimestamp;
        private final Long millis;
        private final String name;
        private final String user;

        public IssueEvent(String containerFormattedDate, String fullTimestamp, Long millis, String name, String user)
        {
            this.containerFormattedDate = containerFormattedDate;
            this.fullTimestamp = fullTimestamp;
            this.millis = millis;
            this.name = name;
            this.user = user;
        }

        public String getContainerFormattedDate()
        {
            return containerFormattedDate;
        }

        public String getFullTimestamp()
        {
            return fullTimestamp;
        }

        public Long getMillis()
        {
            return this.millis;
        }

        public String getName()
        {
            return name;
        }

        public String getUser()
        {
            return user;
        }

        @Override
        public int compareTo(@NotNull IssueEvent other)
        {
            return this.millis.compareTo(other.millis);
        }

        public String toString()
        {
            return getName() + ": " + getContainerFormattedDate() + " by " + getUser();
        }
    }
}
