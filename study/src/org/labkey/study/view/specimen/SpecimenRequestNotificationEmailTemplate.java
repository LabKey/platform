/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.study.view.specimen;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.study.SpecimenManager;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.controllers.specimen.SpecimenUtils;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.specimen.settings.RequestNotificationSettings;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: 11/11/13
 */
public class SpecimenRequestNotificationEmailTemplate extends EmailTemplate
{
    protected static final String DEFAULT_SUBJECT =
            "^studyName^: ^subjectSuffix^";
    protected static final String DEFAULT_SENDER = "^userDisplayName^";
    public static final String NAME = "Specimen request notification";
    private List<EmailTemplate.ReplacementParam> _replacements = new ArrayList<>();

    private String _subjectSuffix;
    private SpecimenUtils.NotificationBean _notification;
    private User _originatingUser;

    /** Instead of in-lining a long String, we store the default body template as a ClassLoader resource */
    private static String loadBody()
    {
        try
        {
            try (InputStream is = SpecimenRequestNotificationEmailTemplate.class.getResourceAsStream("/org/labkey/study/view/specimen/notification.txt"))
            {
                return PageFlowUtil.getStreamContentsAsString(is);
            }
        }
        catch (IOException e)
        {
            throw new UnexpectedException(e);
        }
    }

    public SpecimenRequestNotificationEmailTemplate()
    {
        super(NAME, DEFAULT_SUBJECT, loadBody(), "Sent to users when a specimen request has been edited or inserted.", ContentType.HTML, DEFAULT_SENDER, DEFAULT_REPLY_TO);
        setEditableScopes(EmailTemplate.Scope.SiteOrFolder);

        _replacements.add(new EmailTemplate.ReplacementParam<String>("studyName", String.class, "The name of this folder's study")
        {
            public String getValue(Container c)
            {
                StudyImpl study = StudyManager.getInstance().getStudy(c);
                return study == null ? "<No study>" : study.getLabel();
            }
        });
        _replacements.add(new EmailTemplate.ReplacementParam<String>("subjectSuffix", String.class, "Email subject suffix, configured in specimen request notification settings")
        {
            public String getValue(Container c)
            {
                return _subjectSuffix;
            }
        });
        _replacements.add(new NotificationReplacement<Integer>("specimenRequestNumber", Integer.class, "Unique, auto-incrementing number for this request")
        {
            public Integer getNotificationValue(Container c)
            {
                return _notification.getRequestId();
            }
        });
        _replacements.add(new NotificationReplacement<String>("destinationLocation", String.class, "The location to which the specimen is to be sent")
        {
            public String getNotificationValue(Container c)
            {
                return _notification.getRequestingSiteName();
            }
        });
        _replacements.add(new NotificationReplacement<String>("status", String.class, "The status of the request (submitted, approved, etc)")
        {
            public String getNotificationValue(Container c)
            {
                return _notification.getStatus();
            }
        });
        _replacements.add(new NotificationReplacement<String>("simpleStatus", String.class, "Either 'submitted' for brand new requests, or 'updated' for all other changes")
        {
            public String getNotificationValue(Container c)
            {
                return "submitted".equalsIgnoreCase(_notification.getStatus()) ? "submitted" : "updated";
            }
        });
        _replacements.add(new NotificationReplacement<String>("modifiedBy", String.class, "The user who created or modified the request")
        {
            public String getNotificationValue(Container c)
            {
                return _notification.getModifyingUser();
            }
        });
        _replacements.add(new NotificationReplacement<String>("action", String.class, "A description of what action was performed, such as 'New Request Created'")
        {
            public String getNotificationValue(Container c)
            {
                return _notification.getEventDescription();
            }
        });
        _replacements.add(new NotificationReplacement<String>("attachments", String.class, "A list of direct links to all the attachments associated with the request", ContentType.HTML)
        {
            public String getNotificationValue(Container c)
            {
                List<Attachment> attachments = _notification.getAttachments();
                if (attachments.isEmpty())
                {
                    return null;
                }
                StringBuilder sb = new StringBuilder();
                for (Attachment att : attachments)
                {
                    sb.append("<a href=\"");
                    sb.append(PageFlowUtil.filter(SpecimenController.getDownloadURL(_notification.getEvent(), att.getName()).getURIString()));
                    sb.append("\">");
                    sb.append(PageFlowUtil.filter(att.getName()));
                    sb.append("</a><br>");
                }
                return sb.toString();
            }
        });
        _replacements.add(new NotificationReplacement<String>("specimenList", String.class, "A list of the specimens in the request, as configured in the Specimen Notification settings", ContentType.HTML)
        {
            public String getNotificationValue(Container c)
            {
                if (_notification.getSpecimenList() != null)
                {
                    StringBuilder sb = new StringBuilder("<p>");
                    sb.append("<b>Specimen&nbsp;List</b> (<a href=\"");
                    sb.append(PageFlowUtil.filter(_notification.getRequestURI()));
                    sb.append("id=");
                    sb.append(_notification.getRequestId());
                    sb.append("\">Request Link</a>)<br><br>\n");
                    if (_notification.getIncludeSpecimensInBody())
                    {
                        sb.append(_notification.getSpecimenList());
                    }
                    sb.append("</p>");
                    return sb.toString();
                }
                return null;
            }
        });
        _replacements.add(new NotificationReplacement<String>("requestDescription", String.class, "Typically includes assay plan, shipping information, etc")
        {
            @Override
            protected String getNotificationValue(Container c)
            {
                return _notification.getRequestDescription();
            }
        });
        _replacements.add(new NotificationReplacement<String>("comments", String.class, "Text submitter wrote in Comments text box, or '[Not provided]'")
        {
            @Override
            protected String getNotificationValue(Container c)
            {
                String comments = _notification.getComments();
                return comments == null ? "[Not provided]" : comments;
            }
        });
        _replacements.add(new ReplacementParam<String>("userFirstName", String.class, "First name of the user performing the operation, only works when reply to current user"){
            public String getValue(Container c) {
                return _originatingUser == null ? null : _originatingUser.getFirstName();
            }
        });
        _replacements.add(new ReplacementParam<String>("userLastName", String.class, "Last name of the user performing the operation, only works when reply to current user"){
            public String getValue(Container c) {
                return _originatingUser == null ? null : _originatingUser.getLastName();
            }
        });
        _replacements.add(new ReplacementParam<String>("userDisplayName", String.class, "Display name of the user performing the operation, only works when reply to current user"){
            public String getValue(Container c) {
                return _originatingUser == null ? null : _originatingUser.getFriendlyName();
            }
        });

        _replacements.addAll(super.getValidReplacements());
    }

    /**
     * Evaluates to null if there's no associated specimen request (as there would be in the admin preview mode),
     * otherwise delegates to subclass to determine the actual value.
     */
    private abstract class NotificationReplacement<Type> extends ReplacementParam<Type>
    {
        public NotificationReplacement(String name, Class<Type> valueType, String description)
        {
            super(name, valueType, description);
        }

        public NotificationReplacement(String name, Class<Type> valueType, String description, ContentType contentType)
        {
            super(name, valueType, description, contentType);
        }

        @Override
        public final Type getValue(Container c)
        {
            // No request available, so can't come up with an actual value
            if (_notification == null)
            {
                return null;
            }

            return getNotificationValue(c);
        }

        /** Get the actual value for the specimen request - only called when there is a request available */
        protected abstract Type getNotificationValue(Container c);
    }

    public void init(SpecimenUtils.NotificationBean notification)
    {
        _notification = notification;

        // For backwards compatibility, use the template specified in the notification management UI
        RequestNotificationSettings settings =
                SpecimenManager.getInstance().getRequestNotificationSettings(notification.getContainer());

        _subjectSuffix = settings.getSubjectSuffix().replaceAll("%requestId%", "" + notification.getRequestId());
    }

    public void setOriginatingUser(User user){_originatingUser = user;}

    public List<EmailTemplate.ReplacementParam> getValidReplacements(){return _replacements;}

}
