/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
package org.labkey.api.specimen.view;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.specimen.SpecimenMigrationService;
import org.labkey.api.specimen.settings.RequestNotificationSettings;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.emailTemplate.EmailTemplate;

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
    public static final String NAME = "Specimen request notification";

    protected static final String DEFAULT_SUBJECT = "^studyName^: ^subjectSuffix^";
    protected static final String DEFAULT_SENDER = "^userDisplayName^";

    private final List<EmailTemplate.ReplacementParam> _replacements = new ArrayList<>();

    private String _subjectSuffix;
    private NotificationBean _notification;
    private User _originatingUser;

    /** Instead of in-lining a long String, we store the default body template as a module resource */
    // TODO: Use a text block once we support source=15
    private static String loadBody()
    {
        try
        {
            try (InputStream is = ModuleLoader.getInstance().getModule("Specimen").getModuleResource("notification.txt").getInputStream())
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
        super(NAME, "Sent to users when a specimen request has been edited or inserted.", DEFAULT_SUBJECT, loadBody(), ContentType.HTML, Scope.SiteOrFolder);
        setSenderName(DEFAULT_SENDER);

        _replacements.add(new EmailTemplate.ReplacementParam<>("studyName", String.class, "The name of this folder's study")
        {
            @Override
            public String getValue(Container c)
            {
                Study study = StudyService.get().getStudy(c);
                return study == null ? "<No study>" : study.getLabel();
            }
        });
        _replacements.add(new EmailTemplate.ReplacementParam<>("subjectSuffix", String.class, "Email subject suffix, configured in specimen request notification settings")
        {
            @Override
            public String getValue(Container c)
            {
                return _subjectSuffix;
            }
        });
        _replacements.add(new NotificationReplacement<>("specimenRequestNumber", Integer.class, "Unique, auto-incrementing number for this request")
        {
            @Override
            public Integer getNotificationValue(Container c)
            {
                return _notification.getRequestId();
            }
        });
        _replacements.add(new NotificationReplacement<>("destinationLocation", String.class, "The location to which the specimen is to be sent")
        {
            @Override
            public String getNotificationValue(Container c)
            {
                return _notification.getRequestingSiteName();
            }
        });
        _replacements.add(new NotificationReplacement<>("status", String.class, "The status of the request (submitted, approved, etc)")
        {
            @Override
            public String getNotificationValue(Container c)
            {
                return _notification.getStatus();
            }
        });
        _replacements.add(new NotificationReplacement<>("simpleStatus", String.class, "Either 'submitted' for brand new requests, or 'updated' for all other changes")
        {
            @Override
            public String getNotificationValue(Container c)
            {
                return "submitted".equalsIgnoreCase(_notification.getStatus()) ? "submitted" : "updated";
            }
        });
        _replacements.add(new NotificationReplacement<>("modifiedBy", String.class, "The user who created or modified the request")
        {
            @Override
            public String getNotificationValue(Container c)
            {
                return _notification.getModifyingUser();
            }
        });
        _replacements.add(new NotificationReplacement<>("action", String.class, "A description of what action was performed, such as 'New Request Created'")
        {
            @Override
            public String getNotificationValue(Container c)
            {
                return _notification.getEventDescription();
            }
        });
        _replacements.add(new NotificationReplacement<>("attachments", String.class, "A list of direct links to all the attachments associated with the request", ContentType.HTML)
        {
            @Override
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
                    sb.append(new LinkBuilder(att.getName()).href(SpecimenMigrationService.get().getSpecimenRequestEventDownloadURL(_notification.getEvent(), att.getName())).clearClasses());
                    sb.append("<br>");
                }
                return sb.toString();
            }
        });
        _replacements.add(new NotificationReplacement<>("specimenList", String.class, "A list of the specimens in the request, as configured in the Specimen Notification settings", ContentType.HTML)
        {
            @Override
            public String getNotificationValue(Container c)
            {
                if (_notification.getSpecimenList() != null)
                {
                    StringBuilder sb = new StringBuilder("<p>");
                    sb.append("<b>Specimen&nbsp;List</b> (<a href=\"");
                    sb.append(PageFlowUtil.filter(_notification.getRequestURL()));
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
        _replacements.add(new NotificationReplacement<>("requestDescription", String.class, "Typically includes assay plan, shipping information, etc")
        {
            @Override
            protected String getNotificationValue(Container c)
            {
                return _notification.getRequestDescription();
            }
        });
        _replacements.add(new NotificationReplacement<>("comments", String.class, "Text submitter wrote in Comments text box, or '[Not provided]'")
        {
            @Override
            protected String getNotificationValue(Container c)
            {
                String comments = _notification.getComments();
                return comments == null ? "[Not provided]" : comments;
            }
        });
        _replacements.add(new ReplacementParam<>("userFirstName", String.class, "First name of the user performing the operation, only works when reply to current user")
        {
            @Override
            public String getValue(Container c)
            {
                return _originatingUser == null ? null : _originatingUser.getFirstName();
            }
        });
        _replacements.add(new ReplacementParam<>("userLastName", String.class, "Last name of the user performing the operation, only works when reply to current user")
        {
            @Override
            public String getValue(Container c)
            {
                return _originatingUser == null ? null : _originatingUser.getLastName();
            }
        });
        _replacements.add(new ReplacementParam<>("userDisplayName", String.class, "Display name of the user performing the operation, only works when reply to current user")
        {
            @Override
            public String getValue(Container c)
            {
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

    public void init(NotificationBean notification)
    {
        _notification = notification;

        // For backwards compatibility, use the template specified in the notification management UI
        RequestNotificationSettings settings =
                SettingsManager.get().getRequestNotificationSettings(notification.getContainer());

        _subjectSuffix = settings.getSubjectSuffix().replaceAll("%requestId%", "" + notification.getRequestId());
    }

    public void setOriginatingUser(User user){_originatingUser = user;}

    @Override
    public List<EmailTemplate.ReplacementParam> getValidReplacements(){return _replacements;}
}
