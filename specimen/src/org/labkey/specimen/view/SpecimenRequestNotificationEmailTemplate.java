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
package org.labkey.specimen.view;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.specimen.settings.RequestNotificationSettings;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.specimen.actions.SpecimenController;

import java.util.List;
import java.util.function.Function;

/**
 * User: jeckels
 * Date: 11/11/13
 */
public class SpecimenRequestNotificationEmailTemplate extends EmailTemplate
{
    public static final String NAME = "Specimen request notification";

    protected static final String DEFAULT_SUBJECT = "^studyName^: ^subjectSuffix^";
    protected static final String DEFAULT_SENDER = "^userDisplayName^";

    private String _subjectSuffix;
    private NotificationBean _notification;
    private User _originatingUser;

    private static String loadBody()
    {
        return
            """
            <div>
                <br>
                Specimen request #^specimenRequestNumber^ was ^simpleStatus^ in ^studyName^.
                <br>
                <br>
            </div>
            <table width="500px">
                <tr>
                    <td valign="top"><b>Request&nbsp;Details</b></td>
                </tr>
                <tr>
                    <td valign="top"><b>Specimen&nbsp;Request</b></td>
                    <td align="left">^specimenRequestNumber^</td>
                </tr>
                <tr>
                    <td valign="top"><b>Destination</b></td>
                    <td align="left">^destinationLocation^</td>
                </tr>
                <tr>
                    <td valign="top"><b>Status</b></td>
                    <td align="left">^status^</td>
                </tr>
                <tr>
                    <td valign="top"><b>Modified&nbsp;by</b></td>
                    <td align="left">^modifiedBy^</td>
                </tr>
                <tr>
                    <td valign="top"><b>Action</b></td>
                    <td align="left">^action^</td>
                </tr>
                ^attachments|<tr><td valign="top"><b>Attachments</b></td><td align="left">%s</td></tr>^
            </table>
            ^comments|<p><b>Current&nbsp;Comments</b><br>%s</p>^
            <p>
                ^requestDescription^
            </p>
            ^specimenList^
            """;
    }

    public SpecimenRequestNotificationEmailTemplate()
    {
        super(NAME, "Sent to users when a specimen request has been edited or inserted.", DEFAULT_SUBJECT, loadBody(), ContentType.HTML, Scope.SiteOrFolder);
        setSenderName(DEFAULT_SENDER);
    }

    /**
     * Evaluates to null if there's no associated specimen request (as there would be in the admin preview mode),
     * otherwise delegates to subclass to determine the actual value.
     */
    private final class NotificationReplacement<Type> extends ReplacementParam<Type>
    {
        private final Function<Container, Type> _valueGetter;

        public NotificationReplacement(String name, Class<Type> valueType, String description, Function<Container, Type> valueGetter)
        {
            super(name, valueType, description, ContentType.Plain);
            _valueGetter = valueGetter;
        }

        public NotificationReplacement(String name, Class<Type> valueType, String description, ContentType contentType, Function<Container, Type> valueGetter)
        {
            super(name, valueType, description, contentType);
            _valueGetter = valueGetter;
        }

        @Override
        public Type getValue(Container c)
        {
            // No request available, so can't come up with an actual value
            if (_notification == null)
            {
                return null;
            }

            return _valueGetter.apply(c);
        }
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
    protected void addCustomReplacements(Replacements replacements)
    {
        replacements.add("studyName", String.class, "The name of this folder's study", ContentType.Plain, c -> {
            Study study = StudyService.get().getStudy(c);
            return study == null ? "<No study>" : study.getLabel();
        });
        replacements.add("subjectSuffix", String.class, "Email subject suffix, configured in specimen request notification settings", ContentType.Plain, c -> _subjectSuffix);
        replacements.add(new NotificationReplacement<>("specimenRequestNumber", Integer.class, "Unique, auto-incrementing number for this request", c -> _notification.getRequestId()));
        replacements.add(new NotificationReplacement<>("destinationLocation", String.class, "The location to which the specimen is to be sent", c -> _notification.getRequestingSiteName()));
        replacements.add(new NotificationReplacement<>("status", String.class, "The status of the request (submitted, approved, etc)", c -> _notification.getStatus()));
        replacements.add(new NotificationReplacement<>("simpleStatus", String.class, "Either 'submitted' for brand new requests, or 'updated' for all other changes", c -> "submitted".equalsIgnoreCase(_notification.getStatus()) ? "submitted" : "updated"));
        replacements.add(new NotificationReplacement<>("modifiedBy", String.class, "The user who created or modified the request", c -> _notification.getModifyingUser()));
        replacements.add(new NotificationReplacement<>("action", String.class, "A description of what action was performed, such as 'New Request Created'", c -> _notification.getEventDescription()));
        replacements.add(new NotificationReplacement<>("attachments", String.class, "A list of direct links to all the attachments associated with the request", ContentType.HTML, c -> {
            List<Attachment> attachments = _notification.getAttachments();
            if (attachments.isEmpty())
            {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (Attachment att : attachments)
            {
                sb.append(new LinkBuilder(att.getName()).href(SpecimenController.getDownloadURL(_notification.getEvent(), att.getName())).clearClasses());
                sb.append("<br>");
            }
            return sb.toString();
        }));
        replacements.add(new NotificationReplacement<>("specimenList", String.class, "A list of the specimens in the request, as configured in the Specimen Notification settings", ContentType.HTML, c -> {
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
        }));
        replacements.add(new NotificationReplacement<>("requestDescription", String.class, "Typically includes assay plan, shipping information, etc", c -> _notification.getRequestDescription()));
        replacements.add(new NotificationReplacement<>("comments", String.class, "Text submitter wrote in Comments text box, or '[Not provided]'", c -> {
            String comments = _notification.getComments();
            return comments == null ? "[Not provided]" : comments;
        }));
        replacements.add("userFirstName", String.class, "First name of the user performing the operation, only works when reply to current user", ContentType.Plain, c -> _originatingUser == null ? null : _originatingUser.getFirstName());
        replacements.add("userLastName", String.class, "Last name of the user performing the operation, only works when reply to current user", ContentType.Plain, c -> _originatingUser == null ? null : _originatingUser.getLastName());
        replacements.add("userDisplayName", String.class, "Display name of the user performing the operation, only works when reply to current user", ContentType.Plain, c -> _originatingUser == null ? null : _originatingUser.getFriendlyName());
    }
}
