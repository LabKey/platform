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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.announcements.AnnouncementsController;
import org.labkey.announcements.AnnouncementsController.ThreadAction;
import org.labkey.api.announcements.DiscussionService.StatusOption;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.data.Transient;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.HtmlString;
import org.labkey.api.view.ActionURL;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiRenderingService;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.labkey.announcements.model.AnnouncementManager.DEFAULT_MESSAGE_RENDERER_TYPE;

/**
 * Bean Class for AnnouncementModel.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnnouncementModel extends Entity implements Serializable
{
    private int _rowId = 0;
    private String _parentId = null;
    private WikiRendererType _rendererType;
    private List<Integer> _memberListIds = null; // The list of userIds persisted/retrieved from the comm.userList table
    private List<String> _memberListDisplay = null; // The sanitized list displayed to the user, emails or display names dependent on permissions
    private String _memberListInput = null; // The value coming back on form submission
    private String _body = null;

    private Date _expires = null;
    private Integer _assignedTo = null;
    private StatusOption _status = null;
    private String _title = null;

    // for discussions
    private String _discussionSrcIdentifier = null;
    private String _discussionSrcEntityType = null;
    private String _discussionSrcURL = null;

    private int _responseCount = 0;

    private Collection<AnnouncementModel> _responses = null;
    private Set<User> _authors;
    private Date _approved = null;

    /**
     * Standard constructor.
     */
    public AnnouncementModel()
    {
    }

    /**
     * Returns the rowId
     *
     * @return the rowId
     */
    public int getRowId()
    {
        return _rowId;
    }

    /**
     * Sets the rowId
     *
     * @param rowId the new rowId value
     */
    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    /**
     * Returns the title
     *
     * @return the title
     */
    public String getTitle()
    {
        return _title;
    }

    /**
     * Sets the title
     *
     * @param title the new title value
     */
    public void setTitle(String title)
    {
        _title = title;
    }

    /**
     * Returns the expires
     *
     * @return the expires
     */
    public Date getExpires()
    {
        return _expires;
    }

    /**
     * Sets the expires
     *
     * @param expires the new expires value
     */
    public void setExpires(Date expires)
    {
        _expires = expires;
    }

    /**
     * Returns the body
     *
     * @return the body
     */
    public String getBody()
    {
        return _body;
    }

    /**
     * Sets the body
     *
     * @param body the new body value
     */
    public void setBody(String body)
    {
        _body = body == null ? null : body.replaceAll("\\r\\n", "\n"); // Handle OS-specific new lines;
    }

    public String getParent()
    {
        return _parentId;
    }

    public void setParent(String parentId)
    {
        _parentId = parentId;
    }

    public String getAssignedToName(User currentUser)
    {
        return UserManager.getDisplayNameOrUserId(getAssignedTo(), currentUser);
    }

    public @NotNull Collection<Attachment> getAttachments()
    {
        return AttachmentService.get().getAttachments(getAttachmentParent());
    }

    public @NotNull Collection<AnnouncementModel> getResponses()
    {
        if (null == _responses)
        {
            _responses = AnnouncementManager.getResponses(this);
        }
        return _responses;
    }

    public void setResponses(Collection<AnnouncementModel> responses)
    {
        _responses = responses;
    }

    public ActionURL getThreadURL(Container container)
    {
        return new ActionURL(ThreadAction.class, container);
    }

    public HtmlString translateBody()
    {
        ActionURL attachPrefix = AnnouncementsController.getDownloadURL(this, "");

        return getFormattedHtml(attachPrefix.getLocalURIString());
    }

    //returns string corresponding to name of enum entry
    public String getRendererType()
    {
        if (_rendererType == null)
        {
            _rendererType = DEFAULT_MESSAGE_RENDERER_TYPE;
        }

        return _rendererType.name();
    }

    public void setRendererType(String rendererType)
    {
        _rendererType = WikiRendererType.valueOf(rendererType);
    }

    @Transient
    public HtmlString getFormattedHtml()
    {
        return getFormattedHtml(null);
    }

    private HtmlString getFormattedHtml(@Nullable String attachPrefix)
    {
        WikiRenderingService renderingService = WikiRenderingService.get();

        if (_rendererType == null)
            _rendererType = DEFAULT_MESSAGE_RENDERER_TYPE;

        if (null == attachPrefix)
            return renderingService.getFormattedHtml(_rendererType, _body);
        else
            return renderingService.getFormattedHtml(_rendererType, _body, attachPrefix, getAttachments());
    }

    public String getStatus()
    {
        return null == _status ? null : _status.name();
    }

    public void setStatus(String status)
    {
        _status = EnumUtils.getEnum(StatusOption.class, status);
    }

    public Integer getAssignedTo()
    {
        return _assignedTo;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setAssignedTo(Integer assignedTo)
    {
        _assignedTo = assignedTo;
    }

    @NotNull
    @JsonIgnore
    public List<Integer> getMemberListIds()
    {
        if (_memberListIds == null)
        {
            _memberListIds = AnnouncementManager.getMemberList(this);
        }
        return _memberListIds;
    }

    public void setMemberListIds(List<Integer> memberListIds)
    {
        _memberListIds = memberListIds;
    }

    @JsonIgnore
    public String getMemberListInput()
    {
        return _memberListInput;
    }

    public void setMemberListInput(String memberListInput)
    {
        _memberListInput = memberListInput;
    }

    @JsonIgnore
    public List<String> getMemberListDisplay(Container c, User currentUser)
    {
        if (_memberListDisplay == null)
        {
            _memberListDisplay = new ArrayList<>();
            for (Integer userId : getMemberListIds())
            {
                _memberListDisplay.add(UserManager.getUser(userId).getAutocompleteName(c, currentUser));
            }
        }
        return _memberListDisplay;
    }

    public String getMemberListDisplayString(Container c, User currentUser)
    {
        return StringUtils.join(getMemberListDisplay(c, currentUser), " ");
    }

    public String getDiscussionSrcIdentifier()
    {
        return _discussionSrcIdentifier;
    }

    public void setDiscussionSrcIdentifier(String discussionSrcIdentifier)
    {
        _discussionSrcIdentifier = discussionSrcIdentifier;
    }

    public String getDiscussionSrcEntityType()
    {
        return _discussionSrcEntityType;
    }

    public void setDiscussionSrcEntityType(String discussionSrcEntityType)
    {
        _discussionSrcEntityType = discussionSrcEntityType;
    }

    public String getDiscussionSrcURL()
    {
        return _discussionSrcURL;
    }

    public void setDiscussionSrcURL(String discussionSrcURL)
    {
        _discussionSrcURL = discussionSrcURL;
    }

    @JsonIgnore
    public int getResponseCount()
    {
        return _responseCount;
    }

    public void setResponseCount(int responseCount)
    {
        _responseCount = responseCount;
    }

    @JsonProperty("author")
    public @Nullable JSONObject getAuthor()
    {
        var author = UserManager.getUser(getCreatedBy());
        if (author != null)
            return author.getUserProps();
        return null;
    }

    @JsonIgnore
    public Set<User> getAuthors()
    {
        if (null == _authors)
        {
            AnnouncementModel a = AnnouncementManager.getAnnouncement(lookupContainer(), getParent() == null ? getEntityId() : getParent());

            if (a == null)
            {
                // We haven't been saved to the database yet, so don't stash a list of authors yet
                return Collections.emptySet();
            }

            Collection<AnnouncementModel> responses = a.getResponses();
            Set<User> responderSet = new HashSet<>();

            //add creator of each response to responder set
            for (AnnouncementModel response : responses)
            {
                //do we need to handle case where responder is not in a project group?
                User user = UserManager.getUser(response.getCreatedBy());
                //add to responder set, so we know who responders are
                responderSet.add(user);
            }

            //add creator of parent to responder set
            responderSet.add(UserManager.getUser(a.getCreatedBy()));
            //add creator of this message/response
            responderSet.add(UserManager.getUser(getCreatedBy()));

            _authors = responderSet;
        }

        return _authors;
    }

    /** Find the srcIdentifier for this thread. It's only stored at the parent level, so look there if needed */
    public String lookupSrcIdentifer()
    {
        if (getParent() == null)
        {
            return getDiscussionSrcIdentifier();
        }
        else
        {
            AnnouncementModel parent = AnnouncementManager.getAnnouncement(lookupContainer(), getParent());

            // Null check to avoid race condition (e.g., post message -> delete folder -> notification email
            // thread tries to access parent, a sequence that can happen in the tests)
            return null != parent ? parent.getDiscussionSrcIdentifier() : null;
        }
    }

    @JsonIgnore
    public AttachmentParent getAttachmentParent()
    {
        return new AnnouncementAttachmentParent(this);
    }

    public Date getApproved()
    {
        return _approved;
    }

    public void setApproved(Date approved)
    {
        _approved = approved;
    }

    @JsonIgnore
    public boolean isSpam()
    {
        return AnnouncementManager.isSpam(this);
    }
}

