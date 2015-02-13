/*
 * Copyright (c) 2005-2015 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.data.AttachmentParentEntity;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Entity;
import org.labkey.api.data.Sort;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.MemTracker;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;


public class Issue extends Entity implements Serializable, Cloneable
{
    public static String statusOPEN = "open";
    public static String statusRESOLVED = "resolved";
    public static String statusCLOSED = "closed";

    protected byte[] _ts;
    protected int issueId;
    protected String title;
    protected String status;
    protected Integer assignedTo;
    protected String type;

    protected String area;
    protected Integer priority;
    protected String milestone;
    protected String buildFound;

    protected String tag;

    protected Integer resolvedBy;
    protected Date resolved;
    protected String resolution;
    protected Integer duplicate;
    protected Collection<Integer> duplicates;

    protected String related;
    protected ArrayList<Integer> relatedIssues;

    protected Integer closedBy;
    protected Date closed;

    protected String string1;
    protected String string2;
    protected String string3;
    protected String string4;
    protected String string5;
    protected Integer int1;
    protected Integer int2;

    protected List<Comment> _comments = new ArrayList<>();
    protected List<Comment> _added = null;

    protected String _notifyList;

    public Issue()
    {
        MemTracker.getInstance().put(this);
    }


    public void change(User u)
    {
        beforeUpdate(u);
    }

    public void open(Container c, User u) throws SQLException
    {
        if (0 == getCreatedBy()) // TODO: Check for brand new issue (vs. reopen)?  What if guest opens issue?
        {
            beforeInsert(u, c.getId());
        }
        change(u);

        status = statusOPEN;
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
        resolution = null;
        resolved = null;
        duplicate = null;

        if (resolvedBy != null && beforeView)
            assignedTo = IssueManager.validateAssignedTo(c, resolvedBy);

        resolvedBy = null;
    }

    public void beforeUpdate(Container c)
    {
        // Make sure assigned to user still has permission
        assignedTo = IssueManager.validateAssignedTo(c, assignedTo);
    }


    public void beforeResolve(Container c, User u)
    {
        status = statusRESOLVED;

        resolvedBy = u.getUserId(); // Current user
        resolved = new Date();      // Current date

        assignedTo = IssueManager.validateAssignedTo(c, getCreatedBy());
        if (getTitle().startsWith("**"))
        {
            setTitle(getTitle().substring(2));
        }
    }


    public void resolve(User u)
    {
        change(u);
        status = statusRESOLVED;

        resolvedBy = getModifiedBy();
        resolved = getModified();
    }


    public void close(User u)
    {
        change(u);
        status = statusCLOSED;

        closedBy = getModifiedBy();
        setClosed(getModified());
        // UNDONE: assignedTo is not nullable in database
        // UNDONE: let application enforce non-null for open/resolved bugs
        // UNDONE: currently AssignedTo list defaults to Guest (user 0)
        assignedTo = 0;
    }


    public int getIssueId()
    {
        return issueId;
    }


    public void setIssueId(int issueId)
    {
        this.issueId = issueId;
    }

    public String getTitle()
    {
        return title;
    }


    public void setTitle(String title)
    {
        this.title = title;
    }


    public String getStatus()
    {
        return status;
    }


    public void setStatus(String status)
    {
        this.status = status;
    }


    public Integer getAssignedTo()
    {
        return assignedTo;
    }


    public void setAssignedTo(Integer assignedTo)
    {
        if (null != assignedTo && assignedTo == 0) assignedTo = null;
        this.assignedTo = assignedTo;
    }


    public String getAssignedToName(User currentUser)
    {
        return UserManager.getDisplayName(assignedTo, currentUser);
    }


    public String getType()
    {
        return type;
    }


    public void setType(String type)
    {
        this.type = type;
    }


    public String getArea()
    {
        return area;
    }


    public void setArea(String area)
    {
        this.area = area;
    }


    public Integer getPriority()
    {
        return priority;
    }


    public void setPriority(Integer priority)
    {
        if (priority != null)
            this.priority = priority;
    }


    public String getMilestone()
    {
        return milestone;
    }


    public void setMilestone(String milestone)
    {
        this.milestone = milestone;
    }


/*
    public String getBuildFound()
    {
        return buildFound;
    }


    public void setBuildFound(String buildFound)
    {
        this.buildFound = buildFound;
    }
*/


    public String getCreatedByName(User currentUser)
    {
        return UserManager.getDisplayName(getCreatedBy(), currentUser);
    }


    public String getTag()
    {
        return tag;
    }


    public void setTag(String tag)
    {
        this.tag = tag;
    }


    public Integer getResolvedBy()
    {
        return resolvedBy;
    }


    public void setResolvedBy(Integer resolvedBy)
    {
        if (null != resolvedBy && resolvedBy.intValue() == 0) resolvedBy = null;
        this.resolvedBy = resolvedBy;
    }


    public String getResolvedByName(User currentUser)
    {
        return UserManager.getDisplayName(getResolvedBy(), currentUser);
    }


    public Date getResolved()
    {
        return resolved;
    }


    public void setResolved(Date resolved)
    {
        this.resolved = resolved;
    }


    public String getResolution()
    {
        return resolution;
    }


    public void setResolution(String resolution)
    {
        this.resolution = resolution;
    }


    public Integer getDuplicate()
    {
        return duplicate;
    }


    public void setDuplicate(Integer duplicate)
    {
        if (null != duplicate && duplicate.intValue() == 0) duplicate = null;
        this.duplicate = duplicate;
    }

    public Collection<Integer> getDuplicates()
    {
        return duplicates != null ? duplicates : Collections.<Integer>emptyList();
    }

    public void setDuplicates(Collection<Integer> dups)
    {
        if (dups != null) duplicates = dups;
    }


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

    @NotNull
    public ArrayList<Integer> getRelatedIssues()
    {
        return relatedIssues != null ? relatedIssues : new ArrayList<Integer>();
    }

    public void setRelatedIssues(ArrayList<Integer> rels)
    {
        if (rels != null)
        {
            Collections.sort(rels);
            relatedIssues = rels;
        }
    }


    public Integer getClosedBy()
    {
        return closedBy;
    }


    public void setClosedBy(Integer closedBy)
    {
        if (null != closedBy && closedBy.intValue() == 0) closedBy = null;
        this.closedBy = closedBy;
    }


    public String getClosedByName(User currentUser)
    {
        return UserManager.getDisplayName(getClosedBy(), currentUser);
    }


    public Date getClosed()
    {
        return closed;
    }


    public void setClosed(Date closed)
    {
        this.closed = closed;
    }


    public String getString2()
    {
        return string2;
    }

    public void setString2(String string2)
    {
        this.string2 = string2;
    }

    public String getString1()
    {
        return string1;
    }

    public void setString1(String string1)
    {
        this.string1 = string1;
    }

    public String getString3()
    {
        return string3;
    }

    public void setString3(String string3)
    {
        this.string3 = string3;
    }

    public String getString4()
    {
        return string4;
    }

    public void setString4(String string4)
    {
        this.string4 = string4;
    }

    public String getString5()
    {
        return string5;
    }

    public void setString5(String string5)
    {
        this.string5 = string5;
    }

    public Integer getInt2()
    {
        return int2;
    }

    public void setInt2(Integer int2)
    {
        this.int2 = int2;
    }

    public Integer getInt1()
    {
        return int1;
    }

    public void setInt1(Integer int1)
    {
        this.int1 = int1;
    }

    public Collection<Issue.Comment> getComments()
    {
        List<Issue.Comment> result = new ArrayList<>(_comments);
        final Sort.SortDirection sort = IssueManager.getCommentSortDirection(ContainerManager.getForId(getContainerId()));
        Collections.sort(result, new Comparator<Comment>()
        {
            @Override
            public int compare(Comment o1, Comment o2)
            {
                return o1.getCreated().compareTo(o2.getCreated()) * (sort == Sort.SortDirection.ASC ? 1 : -1);
            }
        });
        return result;
    }

    public Issue.Comment getLastComment()
    {
        if (null == _comments || _comments.isEmpty())
            return null;

        return _comments.get(_comments.size()-1);
    }

    public boolean setComments(List<Issue.Comment> comments)
    {
        if (comments != null)
        {
            _comments = comments;
            for (Comment comment : _comments)
                comment.setIssue(this);
        }
        return _comments.isEmpty();
    }


    public String getModifiedByName(User currentUser)
    {
        return UserManager.getDisplayName(getModifiedBy(), currentUser);
    }


    public Object clone() throws CloneNotSupportedException
    {
        return super.clone();
    }


    // UNDONE: MAKE work in Table version
    public Comment addComment(User user, String text)
    {
        Comment comment = new Comment();
        comment.beforeInsert(user, getContainerId());
        comment.setIssue(this);
        comment.setComment(text);

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
        _notifyList = notifyList;
    }


    public void parseNotifyList(String notifyList)
    {
        _notifyList = UserManager.parseUserListInput(notifyList);
    }

    public String getNotifyList()
    {
        return _notifyList;
    }


    public List<String> getNotifyListDisplayNames(User user)
    {
        ArrayList<String> ret = new ArrayList<>();
        String[] raw = StringUtils.split(null == _notifyList ? "" :_notifyList, ";\n");
        for (String id : raw)
        {
            if (null == (id = StringUtils.trimToNull(id)))
                continue;
            ValidEmail v = null;
            User u = null;
            try
            {
                v = new ValidEmail(id);
                u = UserManager.getUser(v);
            } catch (ValidEmail.InvalidEmailException x) { }
            if (v == null || u == null)
                try { u = UserManager.getUser(Integer.parseInt(id)); } catch (NumberFormatException x) { };
            if (v == null && u == null)
                u = UserManager.getUserByDisplayName(id);

            // filter out inactive users
            if (u != null && !u.isActive())
                continue;

            String display = null != u ? u.getAutocompleteName(ContainerManager.getForId(getContainerId()), user) : null != v ? v.getEmailAddress() : id;
            ret.add(display);
        }
        return ret;
    }


    public List<ValidEmail> getNotifyListEmail()
    {
        ArrayList<ValidEmail> ret = new ArrayList<>();
        String[] raw = StringUtils.split(null == _notifyList ? "" : _notifyList, ";\n");
        for (String id : raw)
        {
            if (null == (id=StringUtils.trimToNull(id)))
                continue;
            ValidEmail v = null;
            User u = null;
            try {
                v = new ValidEmail(id);
                u = UserManager.getUser(v);
            } catch (ValidEmail.InvalidEmailException x) { }

            if (v == null || u == null)
                try { u = UserManager.getUser(Integer.parseInt(id)); } catch (NumberFormatException x) { };
            if (v == null && u == null)
                u = UserManager.getUserByDisplayName(id);
            if (u != null)
                try { v = new ValidEmail(u.getEmail()); } catch (ValidEmail.InvalidEmailException x) { }

            // filter out inactive users
            if (u != null && !u.isActive())
                continue;

            if (null != v)
                ret.add(v);
        }
        return ret;
    }

    /* CONSIDER: use Announcements/Notes instead of special Comments class */

    public static class Comment extends AttachmentParentEntity implements Serializable
    {
        private Issue issue;
        private int commentId;
        private String comment;

        public Comment()
        {
        }

        public Comment(String comment)
        {
            this.comment = comment;
        }

        public Issue getIssue()
        {
            return issue;
        }

        public void setIssue(Issue issue)
        {
            this.issue = issue;
        }

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

        public String getComment()
        {
            return comment;
        }

        public void setComment(String comment)
        {
            this.comment = comment;
        }

        public String getContainerId()
        {
            if (issue != null)
                return issue.getContainerId();
            return super.getContainerId();
        }
    }
}
