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
package org.labkey.announcements.model;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.DownloadURL;
import org.labkey.api.data.AttachmentParentEntity;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.announcements.AnnouncementsController;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;


/**
 * Bean Class for AnnouncementModel.
 */
public class AnnouncementModel extends AttachmentParentEntity implements Serializable
{
    private int _rowId = 0;
//    private AnnouncementModel _parentAnnouncement = null;
    private String _parentId = null;
    private boolean _broadcast;
    private WikiRendererType _rendererType;
    private List<User> _memberList = null;
    private String _body = null;

    private String _emailList = null;
    private Date _expires = null;
    private Integer _assignedTo = null;
    private String _status = null;
    private String _title = null;

    // for discussions
    private String _discussionSrcIdentifier = null;
    private String _discussionSrcURL = null;

    private int _responseCount = 0;


    Collection<Attachment> _attachments = null;
    Collection<AnnouncementModel> _responses = new ArrayList<AnnouncementModel>();

//    static AnnouncementModel[] noResponses = new AnnouncementModel[0];


    /**
     * Standard constructor.
     */
    public AnnouncementModel()
    {
    }

/*
    public static AnnouncementModel[] loadAnnouncements(String container, String parent) throws SQLException
        {
        SimpleFilter filter = _dialect.createSimpleFilter("container", container);
        if (null != parent)
            filter.addCondition("parent", parent);
        else
            filter.addCondition("parent", null, CompareType.ISBLANK);
        
        AnnouncementModel[] announcementModels = (AnnouncementModel[]) Table.select(_tinfo,
                        Table.ALL_COLUMNS,
                        filter,
                        new Sort("Created"),
                        AnnouncementModel.class);
        
        return announcementModels;
        }
        
    public static AnnouncementModel loadParentAnnouncement(String parent) throws SQLException
        {
        SimpleFilter filter = _dialect.createSimpleFilter("entityId", parent);
        
        AnnouncementModel[] announcementModels = (AnnouncementModel[]) Table.select(_tinfo,
                        Table.ALL_COLUMNS,
                        filter,
                        null,
                        AnnouncementModel.class);
        
        return (announcementModels.length == 0 ? null : announcementModels[0]);
        }
*/

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
    public void setTitle(java.lang.String title)
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
    public void setExpires(java.util.Date expires)
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
        _body = body;
    }

/*      @ManyToOne
        @JoinColumn(name = "parent")
    public AnnouncementModel getParent()
        {
        return _parentAnnouncement;
        }


    public void setParent(AnnouncementModel parent)
        {
        _parentAnnouncement = parent;
        }
*/

    public String getParent()
    {
        return _parentId;
    }


    public void setParent(String parentId)
    {
        _parentId = parentId;
    }

    public boolean isBroadcast()
    {
        return _broadcast;
    }

    public void setBroadcast(boolean broadcast)
    {
        _broadcast = broadcast;
    }

    public String getCreatedByName(ViewContext context)
    {
        return getCreatedByName(false, context);
    }


    public String getCreatedByName(boolean includeGroups, ViewContext context)
    {
        return getDisplayName(getCreatedBy(), includeGroups, context);
    }


    private String getDisplayName(int userId, boolean includeGroups, ViewContext context)
    {
        String name = UserManager.getDisplayNameOrUserId(userId, context);

        if (includeGroups)
        {
            User user = UserManager.getUser(userId);

            if (null != user)
            {
                String groupList = SecurityManager.getGroupList(ContainerManager.getForId(getContainerId()), user);

                if (groupList.length() > 0)
                    return name + " (" + groupList + ")";
            }
        }

        return name;
    }


    public String getModifiedByName(ViewContext context)
    {
        return UserManager.getDisplayNameOrUserId(getModifiedBy(), context);
    }


    public String getAssignedToName(ViewContext context)
    {
        return UserManager.getDisplayNameOrUserId(getAssignedTo(), context);
    }


    public Collection<Attachment> getAttachments() throws SQLException
    {
        if (null == _attachments)
            AnnouncementManager.attachAttachments(new AnnouncementModel[] {this});

        return _attachments;
    }


    public void setAttachments(Collection<Attachment> attachments)
    {
        this._attachments = attachments;
    }


    public Collection<AnnouncementModel> getResponses()
    {
        return _responses;
    }


    public void setResponses(Collection<AnnouncementModel> responses)
    {
        this._responses = responses;
    }


    public ActionURL getThreadURL(Container container)
    {
        return new ActionURL(AnnouncementsController.ThreadAction.class, container);
    }

    public String translateBody(Container container)
    {
        DownloadURL urlAttach = new DownloadURL("announcements", container.getPath(), getEntityId(), "");
        Attachment[] attach = _attachments == null ? null : _attachments.toArray(new Attachment[_attachments.size()]);

        WikiRenderer renderer = this.getRenderer(urlAttach.getLocalURIString(), attach);
        return renderer.format(_body).getHtml();
    }

    //returns string corresponding to name of enum entry
    public String getRendererType()
    {
        if (_rendererType == null)
        {
            WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
            _rendererType = null != wikiService ? wikiService.getDefaultMessageRendererType() : null;
        }

        return null != _rendererType ? _rendererType.name() : "none";
    }

    public void setRendererType(String rendererType)
    {
        _rendererType = WikiRendererType.valueOf(rendererType);
    }

    public WikiRenderer getRenderer()
    {
        return getRenderer(null, null);
    }

    public WikiRenderer getRenderer(String attachPrefix, Attachment[] attachments)
    {
        WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
        if (_rendererType == null)
        {
            _rendererType = null != wikiService ? wikiService.getDefaultMessageRendererType() : null;
        }

        return null != wikiService ? wikiService.getRenderer(_rendererType, attachPrefix, attachments) : null;
    }

    public String getStatus()
    {
        return _status;
    }

    public void setStatus(String status)
    {
        _status = status;
    }

    public Integer getAssignedTo()
    {
        return _assignedTo;
    }

    public void setAssignedTo(Integer assignedTo)
    {
        _assignedTo = assignedTo;
    }

    public String getEmailList()
    {
        return _emailList;
    }

    public void setEmailList(String emailList)
    {
        _emailList = emailList;
    }

    public List<User> getMemberList()
    {
        return _memberList;
    }

    public void setMemberList(List<User> memberList)
    {
        _memberList = memberList;
    }

    public String getDiscussionSrcIdentifier()
    {
        return _discussionSrcIdentifier;
    }

    public void setDiscussionSrcIdentifier(String discussionSrcIdentifier)
    {
        _discussionSrcIdentifier = discussionSrcIdentifier;
    }

    public String getDiscussionSrcURL()
    {
        return _discussionSrcURL;
    }

    public void setDiscussionSrcURL(String discussionSrcURL)
    {
        _discussionSrcURL = discussionSrcURL;
    }

    public int getResponseCount()
    {
        return _responseCount;
    }

    public void setResponseCount(int responseCount)
    {
        _responseCount = responseCount;
    }
}

