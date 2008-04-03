package org.labkey.study.samples.notifications;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.view.ViewContext;
import org.labkey.study.model.SampleRequest;
import org.labkey.study.model.SampleRequestRequirement;

import java.util.List;
import java.sql.SQLException;
import java.io.IOException;

/**
 * User: brittp
 * Date: May 4, 2007
 * Time: 3:34:13 PM
 */
public interface RequestNotification
{
    List<? extends NotificationRecipientSet> getRecipients();
    SampleRequest getSampleRequest();
    String getComments();
    String getEventSummary();
    Attachment[] getAttachments();
    String getSpecimenListHTML(ViewContext context) throws SQLException, IOException;
    SampleRequestRequirement getRequirement();
}
