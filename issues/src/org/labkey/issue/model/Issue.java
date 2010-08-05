/*
 * Copyright (c) 2005-2010 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.data.AttachmentParentEntity;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.HString;
import org.labkey.api.view.ViewContext;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.*;


public class Issue extends Entity implements Serializable, Cloneable
{
    public static HString statusOPEN = new HString("open",false);
    public static HString statusRESOLVED = new HString("resolved",false);
    public static HString statusCLOSED = new HString("closed",false);

    protected byte[] _ts;
    protected int issueId;
    protected HString title;
    protected HString status;
    protected Integer assignedTo;
    protected HString type;

    protected HString area;
    protected Integer priority;
    protected HString milestone;
    protected HString buildFound;

    protected HString tag;

    protected Integer resolvedBy;
    protected Date resolved;
    protected HString resolution;
    protected Integer duplicate;
    protected List<Integer> duplicates;

    protected Integer closedBy;
    protected Date closed;

    protected HString string1;
    protected HString string2;
    protected HString string3;
    protected HString string4;
    protected HString string5;
    protected Integer int1;
    protected Integer int2;

    protected List<Comment> _comments = new ArrayList<Comment>();
    protected List<Comment> _added = null;

    protected HString _notifyList;

    public Issue()
    {
        assert MemTracker.put(this);
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

    public void beforeReOpen()
    {
        resolution = null;
        resolved = null;
        duplicate = null;

        if (resolvedBy != null)
            assignedTo = resolvedBy;

        resolvedBy = null;
    }

    public void beforeResolve(User u)
    {
        status = statusRESOLVED;

        resolvedBy = u.getUserId(); // Current user
        resolved = new Date();      // Current date

        assignedTo = getCreatedBy();
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
        assignedTo = new Integer(0);
    }


    public int getIssueId()
    {
        return issueId;
    }


    public void setIssueId(int issueId)
    {
        this.issueId = issueId;
    }

    public HString getTitle()
    {
        return title;
    }


    public void setTitle(HString title)
    {
        this.title = title;
    }


    public HString getStatus()
    {
        return status;
    }


    public void setStatus(HString status)
    {
        this.status = status;
    }


    public Integer getAssignedTo()
    {
        return assignedTo;
    }


    public void setAssignedTo(Integer assignedTo)
    {
        if (null != assignedTo && assignedTo.intValue() == 0) assignedTo = null;
        this.assignedTo = assignedTo;
    }


    public HString getAssignedToName(ViewContext context)
    {
        return new HString(UserManager.getDisplayName(assignedTo, context));
    }


    public HString getType()
    {
        return type;
    }


    public void setType(HString type)
    {
        this.type = type;
    }


    public HString getArea()
    {
        return area;
    }


    public void setArea(HString area)
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


    public HString getMilestone()
    {
        return milestone;
    }


    public void setMilestone(HString milestone)
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


    public String getCreatedByName(ViewContext context)
    {
        return UserManager.getDisplayName(getCreatedBy(), context);
    }


    public HString getTag()
    {
        return tag;
    }


    public void setTag(HString tag)
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


    public String getResolvedByName(ViewContext context)
    {
        return UserManager.getDisplayName(getResolvedBy(), context);
    }


    public Date getResolved()
    {
        return resolved;
    }


    public void setResolved(Date resolved)
    {
        this.resolved = resolved;
    }


    public HString getResolution()
    {
        return resolution;
    }


    public void setResolution(HString resolution)
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

    public List<Integer> getDuplicates()
    {
        return duplicates != null ? duplicates : Collections.<Integer>emptyList();
    }

    public void setDuplicates(List<Integer> dups)
    {
        if (dups != null)
            duplicates = dups;
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


    public String getClosedByName(ViewContext context)
    {
        return UserManager.getDisplayName(getClosedBy(), context);
    }


    public Date getClosed()
    {
        return closed;
    }


    public void setClosed(Date closed)
    {
        this.closed = closed;
    }


    public HString getString2()
    {
        return string2;
    }

    public void setString2(HString string2)
    {
        this.string2 = string2;
    }

    public HString getString1()
    {
        return string1;
    }

    public void setString1(HString string1)
    {
        this.string1 = string1;
    }

    public HString getString3()
    {
        return string3;
    }

    public void setString3(HString string3)
    {
        this.string3 = string3;
    }

    public HString getString4()
    {
        return string4;
    }

    public void setString4(HString string4)
    {
        this.string4 = string4;
    }

    public HString getString5()
    {
        return string5;
    }

    public void setString5(HString string5)
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
//        if (null == comments)
//            comments = new ArrayList();
        return _comments;
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

    public String getModifiedByName(ViewContext context)
    {
        return UserManager.getDisplayName(getModifiedBy(), context);
    }


    public Object clone() throws CloneNotSupportedException
    {
        return super.clone();
    }


    // UNDONE: MAKE work in Table version
    public Comment addComment(User user, HString text)
    {
        Comment comment = new Comment();
        comment.beforeInsert(user, getContainerId());
        comment.setIssue(this);
        comment.setComment(text);

        _comments.add(comment);
        if (null == _added)
            _added = new ArrayList<Issue.Comment>(1);
        _added.add(comment);
        return comment;
    }

    public void setNotifyList(HString notifyList)
    {
        _notifyList = notifyList;
    }

    public HString getNotifyList()
    {
        return _notifyList;
    }

    /* CONSIDER: use Announcements/Notes instead of special Comments class */

    public static class Comment extends AttachmentParentEntity implements Serializable
    {
        private Issue issue;
        private int commentId;
        HString comment;

        public Comment()
        {

        }

        public Comment(String comment)
        {
            this.comment = new HString(comment, false);
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

        public String getCreatedByName(ViewContext context)
        {
            return UserManager.getDisplayName(getCreatedBy(), context);
        }

        public HString getComment()
        {
            return comment;
        }

        public void setComment(HString comment)
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
