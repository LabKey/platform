/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.study.samples.notifications;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.ByteArrayAttachmentFile;
import org.labkey.api.view.ViewContext;
import org.labkey.study.SampleManager;
import org.labkey.study.model.SampleRequest;
import org.labkey.study.model.SampleRequestEvent;
import org.labkey.study.model.SampleRequestRequirement;
import org.labkey.study.model.Specimen;
import org.labkey.study.query.SpecimenQueryView;
import org.labkey.study.samples.settings.RequestNotificationSettings;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: brittp
 * Date: May 4, 2007
 * Time: 3:41:44 PM
 */
public class DefaultRequestNotification
{
    protected List<? extends NotificationRecipientSet> _recipients;
    protected SampleRequest _request;
    protected String _eventSummary;
    protected SampleRequestRequirement _sampleRequestRequirement;
    protected String _comments;
    protected SampleRequestEvent _event;

    public DefaultRequestNotification(SampleRequest request, List<? extends NotificationRecipientSet> recipients, String eventSummary,
                                      SampleRequestEvent event, String comments, SampleRequestRequirement sampleRequestRequirement,
                                      ViewContext context) throws Exception
    {
        _request = request;
        _recipients = recipients;
        _eventSummary = eventSummary;
        _comments = comments;
        _sampleRequestRequirement = sampleRequestRequirement;
        _event = event;
        addSpecimenListFileIfNeeded(context);
    }

    public final String getSpecimenListHTML(ViewContext context) throws SQLException, IOException
    {
        List<Specimen> specimens = getSpecimenList();
        if (specimens != null && specimens.size() > 0)
        {
            SpecimenQueryView view = SpecimenQueryView.createView(context, specimens, SpecimenQueryView.ViewType.VIALS_EMAIL);
            view.setDisableLowVialIndicators(true);
            return view.getSimpleHtmlTable();
        }
        return null;
    }

    private void addSpecimenListFileIfNeeded(ViewContext context) throws Exception
    {
        RequestNotificationSettings settings = SampleManager.getInstance().getRequestNotificationSettings(_request.getContainer());
        if (RequestNotificationSettings.SpecimensAttachmentEnum.ExcelAttachment == settings.getSpecimensAttachmentEnum() ||
            RequestNotificationSettings.SpecimensAttachmentEnum.TextAttachment == settings.getSpecimensAttachmentEnum())
        {
            ByteArrayAttachmentFile specimenListFile = null;
            List<Specimen> specimens = getSpecimenList();
            if (specimens != null && specimens.size() > 0)
            {
                SpecimenQueryView view = SpecimenQueryView.createView(context, specimens, SpecimenQueryView.ViewType.VIALS_EMAIL);
                view.setDisableLowVialIndicators(true);
                if (RequestNotificationSettings.SpecimensAttachmentEnum.ExcelAttachment == settings.getSpecimensAttachmentEnum())
                    specimenListFile = view.exportToExcelFile();
                else
                    specimenListFile = view.exportToTsvFile();
                if (null != specimenListFile)
                {
                    List<AttachmentFile> attachments = new ArrayList<>();
                    attachments.add(specimenListFile);
                    AttachmentService.get().addAttachments(_event, attachments, context.getUser());
                }
            }
        }
    }

    protected List<Specimen> getSpecimenList()
    {
        return _request.getSpecimens();
    }

    public @NotNull List<Attachment> getAttachments()
    {
        return AttachmentService.get().getAttachments(_event);
    }

    public String getComments()
    {
        return _comments;
    }

    public SampleRequestRequirement getRequirement()
    {
        return _sampleRequestRequirement;
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
