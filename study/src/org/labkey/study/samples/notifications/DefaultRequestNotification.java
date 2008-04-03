package org.labkey.study.samples.notifications;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.view.ViewContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.study.model.SampleRequest;
import org.labkey.study.model.SampleRequestRequirement;
import org.labkey.study.model.Specimen;
import org.labkey.study.query.SpecimenQueryView;

import java.util.List;
import java.sql.SQLException;
import java.io.IOException;

/**
 * User: brittp
 * Date: May 4, 2007
 * Time: 3:41:44 PM
 */
public class DefaultRequestNotification implements RequestNotification
{
    protected List<? extends NotificationRecipientSet> _recipients;
    protected SampleRequest _request;
    protected String _eventSummary;

    public DefaultRequestNotification(SampleRequest request, List<? extends NotificationRecipientSet> recipients, String eventSummary)
    {
        _request = request;
        _recipients = recipients;
        _eventSummary = eventSummary;
    }

    public final String getSpecimenListHTML(ViewContext context) throws SQLException, IOException
    {
        Specimen[] specimens = getSpecimenList();
        if (specimens != null && specimens.length > 0)
        {
            SpecimenQueryView view = SpecimenQueryView.createView(context, specimens, true);
            view.setDisableLowVialIndicators(true);
            return view.getSimpleHtmlTable();
        }
        return null;
    }

    protected Specimen[] getSpecimenList() throws SQLException
    {
        return _request.getSpecimens();
    }

    public Attachment[] getAttachments()
    {
        return null;
    }

    public String getComments()
    {
        return null;
    }

    public SampleRequestRequirement getRequirement()
    {
        return null;
    }

    final public List<? extends NotificationRecipientSet> getRecipients()
    {
        return _recipients;
    }

    final public SampleRequest getSampleRequest()
    {
        return _request;
    }

    final public String getEventSummary()
    {
        return _eventSummary;
    }
}
