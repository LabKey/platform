package org.labkey.api.issues;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Pair;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Issue
{
    enum action
    {
        insert(InsertPermission.class),
        update(UpdatePermission.class),
        close(UpdatePermission.class),
        reopen(UpdatePermission.class),
        resolve(UpdatePermission.class);

        private final Class<? extends Permission> _requiredPermission;

        action(Class<? extends Permission> requiredPermission)
        {
            _requiredPermission = requiredPermission;
        }

        // verify permission for the action
        public void checkPermission(Container c, User user, Issue issue)
        {
            if (!c.hasPermission(user, _requiredPermission))
                throw new UnauthorizedException();
        }
    }

    int getIssueId();
    String getContainerId();

    String getTitle();
    String getStatus();
    String getResolution();
    Collection<Integer> getDuplicates();
    @NotNull Set<Integer> getRelatedIssues();
    String getRelated();
    Collection<Issue.Comment> getComments();
    Integer getIssueDefId();
    String getIssueDefName();
    List<String> getNotifyListDisplayNames(User user);
    List<ValidEmail> getNotifyListEmail();
    List<Pair<User, ValidEmail>> getNotifyListUserEmail();
    Map<String, Object> getProperties();

    Integer getAssignedTo();
    int getCreatedBy();
    Date getCreated();
    int getModifiedBy();
    Date getModified();
    Integer getResolvedBy();
    Date getResolved();
    Integer getClosedBy();
    Date getClosed();

    interface Comment
    {
        Issue getIssue();
        int getCommentId();
        int getCreatedBy();
        Date getCreated();
        int getModifiedBy();
        Date getModified();
        HtmlString getHtmlComment();
        String getContainerId();
    }

    class Builder implements org.labkey.api.data.Builder<Issue>
    {
        private final Container _container;
        private final User _user;
        private final action _action;
        private Integer _issueId = 0;
        private Integer _issueDefId;
        private String _issueDefName;
        private final Map<String, Object> _properties = new CaseInsensitiveHashMap<>();
        private Integer _createdBy;
        private Long _created;
        private Integer _modifiedBy;
        private Long _modified;
        private String _related;

        public Builder(Container container, User user, action action)
        {
            _container = container;
            _user = user;
            _action = action;
        }

        public Builder issueId(Integer issueId)
        {
            if (issueId != null)
                _issueId = issueId;
            return this;
        }

        public Builder issueDefId(Integer issueDefId)
        {
            if (issueDefId != null)
                _issueDefId = issueDefId;
            return this;
        }

        public Builder issueDefName(String issueDefName)
        {
            if (issueDefName != null)
                _issueDefName = issueDefName;
            return this;
        }

        public Builder title(String title)
        {
            return prop("title", title);
        }

        public Builder status(String status)
        {
            return prop("status", status);
        }

        public Builder comment(String comment)
        {
            return prop("comment", comment);
        }

        public Builder notifyList(String notifyList)
        {
            return prop("notifyList", notifyList);
        }

        public Builder resolution(String resolution)
        {
            return prop("resolution", resolution);
        }

        public Builder assignedTo(Integer assignedTo)
        {
            return  prop("assignedTo", assignedTo);
        }

        public Builder createdBy(Integer createdBy)
        {
            if (createdBy != null)
                _createdBy = createdBy;
            return this;
        }

        public Builder created(Long created)
        {
            if (created != null)
                _created = created;
            return this;
        }

        public Builder modifiedBy(Integer modifiedBy)
        {
            if (modifiedBy != null)
                _modifiedBy = modifiedBy;
            return this;
        }

        public Builder modified(Long modified)
        {
            if (modified != null)
                _modified = modified;
            return this;
        }

        public Builder resolvedBy(Integer resolvedBy)
        {
            return prop("resolvedBy", resolvedBy);
        }

        public Builder resolved(Date resolved)
        {
            return prop("resolved", resolved);
        }

        public Builder closedBy(Integer closedBy)
        {
            return  prop("closedBy", closedBy);
        }

        public Builder closed(Date closed)
        {
            return prop("closed", closed);
        }

        public Builder related(String related)
        {
            if (related != null)
                _related = related;
            return this;
        }

        public Builder prop(String key, Object value)
        {
            if (value != null)
                _properties.put(key, value);
            return this;
        }

        @Override
        public Issue build()
        {
            IssueImpl issue = new IssueImpl(_container, _issueId, _issueDefId, _issueDefName, _related, _properties);

            if (_createdBy != null) issue.setCreatedBy(_createdBy);
            if (_created != null) issue.setCreated(new Date(_created));
            if (_modifiedBy != null) issue.setModifiedBy(_modifiedBy);
            if (_modified != null) issue.setModified(new Date(_modified));

            if (_action != action.insert)
            {
                // if not a new issue, we need to merge the existing issue with new changes
                ObjectFactory<IssueImpl> factory = ObjectFactory.Registry.getFactory(IssueImpl.class);
                Map<String, Object> updates = new HashMap<>();
                Map<String, Object> bean = factory.toMap(issue, null);

                // remove entries with null values
                bean.forEach((k, v) -> {if (v != null) updates.put(k, v);});
                Issue updatedIssue = IssueService.get().getIssueForUpdate(_container, _user, _issueId, updates);
                if (updatedIssue == null)
                    throw new NotFoundException("Existing issue with id: " + _issueId + " was not found");

                return updatedIssue;
            }
            else
                return issue;
        }

        public static class IssueImpl extends Entity implements Issue
        {
            private Container _container;
            private Integer _issueId;
            private Integer _issueDefId;
            private String _issueDefName;
            private Map<String, Object> _properties = new CaseInsensitiveHashMap<>();
            private String _related;

            // for ObjectFactory
            public IssueImpl()
            {
            }

            public IssueImpl(Container container, @Nullable Integer issueId, @Nullable Integer issueDefId, @Nullable String issueDefName,
                             @Nullable String related, Map<String, Object> properties)
            {
                _container = container;
                _issueId = issueId;
                _issueDefId = issueDefId;
                _issueDefName = issueDefName;
                _related = related;
                _properties = properties;
            }

            @Override
            public int getIssueId()
            {
                return _issueId;
            }

            @Override
            public Integer getIssueDefId()
            {
                return _issueDefId;
            }

            @Override
            public String getIssueDefName()
            {
                return _issueDefName;
            }

            @Override
            public String getContainerId()
            {
                return _container.getId();
            }

            @Override
            public String getTitle()
            {
                return (String)_properties.get("title");
            }

            @Override
            public String getStatus()
            {
                return (String)_properties.get("status");
            }

            @Override
            public String getResolution()
            {
                return (String)_properties.get("resolution");
            }

            @Override
            public Collection<Integer> getDuplicates()
            {
                // not used to update issues
                return null;
            }

            @Override
            public @NotNull Set<Integer> getRelatedIssues()
            {
                // not used to update issues
                return null;
            }

            @Override
            public String getRelated()
            {
                return _related;
            }

            @Override
            public Collection<Comment> getComments()
            {
                // not used to update issues
                return null;
            }

            @Override
            public List<String> getNotifyListDisplayNames(User user)
            {
                // not used to update issues
                return null;
            }

            @Override
            public List<ValidEmail> getNotifyListEmail()
            {
                // not used to update issues
                return null;
            }

            @Override
            public List<Pair<User, ValidEmail>> getNotifyListUserEmail()
            {
                // not used to update issues
                return null;
            }

            @Override
            public Map<String, Object> getProperties()
            {
                if (_properties.isEmpty())
                    return null;
                return _properties;
            }

            @Override
            public Integer getAssignedTo()
            {
                return (Integer)_properties.get("assignedTo");
            }

            @Override
            public Integer getResolvedBy()
            {
                return (Integer)_properties.get("resolvedBy");
            }

            @Override
            public Date getResolved()
            {
                return (Date)_properties.get("resolved");
            }

            @Override
            public Integer getClosedBy()
            {
                return (Integer)_properties.get("closedBy");
            }

            @Override
            public Date getClosed()
            {
                return (Date)_properties.get("closed");
            }
        }
    }
}