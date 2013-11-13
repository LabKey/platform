package org.labkey.study.view.samples;

import org.apache.commons.io.IOUtils;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpecimenController;
import org.labkey.study.controllers.samples.SpecimenUtils;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.samples.settings.RequestNotificationSettings;

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
    public static final String NAME = "Specimen request notification";
    private List<EmailTemplate.ReplacementParam> _replacements = new ArrayList<>();

    private String _subjectSuffix;
    private SpecimenUtils.NotificationBean _notification;

    /** Instead of in-lining a long String, we store the default body template as a ClassLoader resource */
    private static String loadBody()
    {
        try
        {
            try (InputStream is = SpecimenRequestNotificationEmailTemplate.class.getResourceAsStream("/org/labkey/study/view/samples/notification.txt"))
            {
                return IOUtils.toString(is);
            }
        }
        catch (IOException e)
        {
            throw new UnexpectedException(e);
        }
    }

    public SpecimenRequestNotificationEmailTemplate()
    {
        super(NAME, DEFAULT_SUBJECT, loadBody(), "Sent to the users when a specimen requests has been edited or inserted.", ContentType.HTML);
        setEditableScopes(EmailTemplate.Scope.SiteOrFolder);

        _replacements.add(new EmailTemplate.ReplacementParam("studyName", "The name of this folder's study")
        {
            public String getValue(Container c)
            {
                StudyImpl study = StudyManager.getInstance().getStudy(c);
                return study == null ? "<No study>" : study.getLabel();
            }
        });
        _replacements.add(new EmailTemplate.ReplacementParam("subjectSuffix", "Email subject suffix, configured in specimen request notification settings")
        {
            public String getValue(Container c)
            {
                return _subjectSuffix;
            }
        });
        _replacements.add(new NotificationReplacement("specimenRequestNumber", "Unique, auto-incrementing number for this request")
        {
            public String getStringValue(Container c)
            {
                return Integer.toString(_notification.getRequestId());
            }
        });
        _replacements.add(new NotificationReplacement("destinationLocation", "The location to which the specimen is to be sent")
        {
            public String getStringValue(Container c)
            {
                return _notification.getRequestingSiteName();
            }
        });
        _replacements.add(new NotificationReplacement("status", "The status of the request (submitted, approved, etc)")
        {
            public String getStringValue(Container c)
            {
                return _notification.getStatus();
            }
        });
        _replacements.add(new NotificationReplacement("simpleStatus", "Either 'submitted' for brand new requests, or 'updated' for all other changes")
        {
            public String getStringValue(Container c)
            {
                return "submitted".equalsIgnoreCase(_notification.getStatus()) ? "submitted" : "updated";
            }
        });
        _replacements.add(new NotificationReplacement("modifiedBy", "The user who created or modified the request")
        {
            public String getStringValue(Container c)
            {
                return _notification.getModifyingUser();
            }
        });
        _replacements.add(new NotificationReplacement("action", "A description of what action was performed, such as 'New Request Created'")
        {
            public String getStringValue(Container c)
            {
                return _notification.getEventDescription();
            }
        });
        _replacements.add(new NotificationReplacement("attachments", "A list of direct links to all the attachments associated with the request", ContentType.HTML)
        {
            public String getStringValue(Container c)
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
                    sb.append(PageFlowUtil.filter(_notification.getBaseServerURI()));
                    sb.append(att.getDownloadUrl(SpecimenController.DownloadAction.class));
                    sb.append("\">");
                    sb.append(PageFlowUtil.filter(att.getName()));
                    sb.append("</a><br>");
                }
                return sb.toString();
            }
        });
        _replacements.add(new NotificationReplacement("specimenList", "A list of the specimens in the request, as configured in the Specimen Notification settings", ContentType.HTML)
        {
            public String getStringValue(Container c)
            {
                if (_notification.getSpecimenList() != null)
                {
                    StringBuilder sb = new StringBuilder("<p>");
                    sb.append("<b> Specimen &nbsp;List</b> (<a href=\"");
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
        _replacements.add(new NotificationReplacement("requestDescription", "Typically includes assay plan, shipping information, etc")
        {
            @Override
            protected String getStringValue(Container c)
            {
                return _notification.getRequestDescription();
            }
        });
        _replacements.add(new NotificationReplacement("comments", "Text submitter wrote in Comments text box, or '[Not provided]'")
        {
            @Override
            protected String getStringValue(Container c)
            {
                return _notification.getComments();
            }
        });
        _replacements.add(new NotificationReplacement("specimenList", "List of specimens in table form including details")
        {
            @Override
            protected String getStringValue(Container c)
            {
                return _notification.getSpecimenList();
            }
        });

        _replacements.addAll(super.getValidReplacements());
    }

    /**
     * Evaluates to null if there's no associated specimen request (as there would be in the admin preview mode),
     * otherwise delegates to subclass to determine the actual value.
     */
    private abstract class NotificationReplacement extends ReplacementParam
    {
        public NotificationReplacement(String name, String description)
        {
            super(name, description);
        }

        public NotificationReplacement(String name, String description, ContentType contentType)
        {
            super(name, description, contentType);
        }

        @Override
        public final String getValue(Container c)
        {
            // No request available, so can't come up with an actual value
            if (_notification == null)
            {
                return null;
            }

            return getStringValue(c);
        }

        /** Get the actual value for the specimen request - only called when there is a request available */
        protected abstract String getStringValue(Container c);
    }

    public void init(SpecimenUtils.NotificationBean notification)
    {
        _notification = notification;

        // For backwards compatibility, use the template specified in the notification management UI
        RequestNotificationSettings settings =
                SampleManager.getInstance().getRequestNotificationSettings(notification.getContainer());

        _subjectSuffix = settings.getSubjectSuffix().replaceAll("%requestId%", "" + notification.getRequestId());
    }

    public List<EmailTemplate.ReplacementParam> getValidReplacements(){return _replacements;}

}
