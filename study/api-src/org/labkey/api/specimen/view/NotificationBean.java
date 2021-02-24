package org.labkey.api.specimen.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.specimen.SpecimenRequestManager;
import org.labkey.api.specimen.SpecimenRequestStatus;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.specimen.model.SpecimenRequestEvent;
import org.labkey.api.specimen.notifications.DefaultRequestNotification;
import org.labkey.api.study.Location;
import org.labkey.api.study.SpecimenUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;

import java.util.List;

public class NotificationBean
{
    private final DefaultRequestNotification _notification;
    private final User _user;
    private final String _specimenList;
    private final String _studyName;
    private final String _requestURI;

    private boolean _includeSpecimensInBody;

    public NotificationBean(ViewContext context, DefaultRequestNotification notification, String specimenList, String studyName)
    {
        _notification = notification;
        _user = context.getUser();
        _specimenList = specimenList;
        _studyName = studyName;
        _requestURI = PageFlowUtil.urlProvider(SpecimenUrls.class).getManageRequestURL(context.getContainer()).getURIString();

        _includeSpecimensInBody = null != specimenList;
    }

    public @NotNull List<Attachment> getAttachments()
    {
        return _notification.getAttachments();
    }

    public String getComments()
    {
        return _notification.getComments();
    }

    public int getRequestId()
    {
        return _notification.getSpecimenRequest().getRowId();
    }

    public String getModifyingUser()
    {
        return _user.getEmail();
    }

    public String getRequestingSiteName()
    {
        Location destLocation = LocationManager.get().getLocation(_notification.getSpecimenRequest().getContainer(),
                _notification.getSpecimenRequest().getDestinationSiteId());
        if (destLocation != null)
            return destLocation.getDisplayName();
        else
            return null;
    }

    public String getStatus()
    {
        SpecimenRequestStatus status = SpecimenRequestManager.get().getRequestStatus(_notification.getSpecimenRequest().getContainer(),
                _notification.getSpecimenRequest().getStatusId());
        return status != null ? status.getLabel() : "Unknown";
    }

    public Container getContainer()
    {
        return _notification.getSpecimenRequest().getContainer();
    }

    public String getEventDescription()
    {
        return _notification.getEventSummary();
    }

    public String getRequestDescription()
    {
        return _notification.getSpecimenRequest().getComments();
    }

    public String getSpecimenList()
    {
        return _specimenList;
    }

    public String getStudyName()
    {
        return _studyName;
    }

    public String getRequestURI()
    {
        return _requestURI;
    }

    public boolean getIncludeSpecimensInBody()
    {
        return _includeSpecimensInBody;
    }

    public void setIncludeSpecimensInBody(boolean includeSpecimensInBody)
    {
        _includeSpecimensInBody = includeSpecimensInBody;
    }

    public SpecimenRequestEvent getEvent()
    {
        return _notification.getEvent();
    }
}
