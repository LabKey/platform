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

package org.labkey.specimen.notifications;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.ByteArrayAttachmentFile;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.specimen.Vial;
import org.labkey.specimen.query.SpecimenQueryView;
import org.labkey.api.view.ViewContext;
import org.labkey.specimen.model.SpecimenRequestEvent;
import org.labkey.specimen.requirements.SpecimenRequest;
import org.labkey.specimen.requirements.SpecimenRequestRequirement;
import org.labkey.specimen.settings.RequestNotificationSettings;
import org.labkey.specimen.settings.SettingsManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DefaultRequestNotification
{
    protected List<? extends NotificationRecipientSet> _recipients;
    protected SpecimenRequest _request;
    protected String _eventSummary;
    protected SpecimenRequestRequirement _specimenRequestRequirement;
    protected String _comments;
    protected SpecimenRequestEvent _event;

    public DefaultRequestNotification(SpecimenRequest request, List<? extends NotificationRecipientSet> recipients, String eventSummary,
                                      SpecimenRequestEvent event, String comments, SpecimenRequestRequirement specimenRequestRequirement,
                                      ViewContext context) throws Exception
    {
        _request = request;
        _recipients = recipients;
        _eventSummary = eventSummary;
        _comments = comments;
        _specimenRequestRequirement = specimenRequestRequirement;
        _event = event;
        addSpecimenListFileIfNeeded(context);
    }

    public final String getSpecimenListHTML(ViewContext context) throws SQLException, IOException
    {
        List<Vial> vials = getSpecimenList();
        if (vials != null && !vials.isEmpty())
        {
            SpecimenQueryView view = SpecimenQueryView.createView(context, vials, SpecimenQueryView.ViewType.VIALS_EMAIL);
            view.setDisableLowVialIndicators(true);
            view.getSettings().setMaxRows(-1);
            return view.getSimpleHtmlTable();
        }
        return null;
    }

    private void addSpecimenListFileIfNeeded(ViewContext context) throws Exception
    {
        RequestNotificationSettings settings = SettingsManager.get().getRequestNotificationSettings(_request.getContainer());
        if (RequestNotificationSettings.SpecimensAttachmentEnum.ExcelAttachment == settings.getSpecimensAttachmentEnum() ||
            RequestNotificationSettings.SpecimensAttachmentEnum.TextAttachment == settings.getSpecimensAttachmentEnum())
        {
            final ByteArrayAttachmentFile specimenListFile;
            List<Vial> vials = getSpecimenList();
            if (vials != null && !vials.isEmpty())
            {
                SpecimenQueryView view = SpecimenQueryView.createView(context, vials, SpecimenQueryView.ViewType.VIALS_EMAIL);
                view.getSettings().setMaxRows(-1);
                view.setDisableLowVialIndicators(true);
                if (RequestNotificationSettings.SpecimensAttachmentEnum.ExcelAttachment == settings.getSpecimensAttachmentEnum())
                    specimenListFile = view.exportToExcelFile(ExcelWriter.ExcelDocumentType.xls, null, null, false);
                else
                    specimenListFile = view.exportToTsvFile(TSVWriter.DELIM.TAB, TSVWriter.QUOTE.DOUBLE, ColumnHeaderType.Caption, null, null, false);
                if (null != specimenListFile)
                {
                    List<AttachmentFile> attachments = new ArrayList<>();
                    attachments.add(specimenListFile);
                    AttachmentService.get().addAttachments(_event, attachments, context.getUser());
                }
            }
        }
    }

    protected List<Vial> getSpecimenList()
    {
        return _request.getVials();
    }

    public SpecimenRequestEvent getEvent()
    {
        return _event;
    }

    public @NotNull List<Attachment> getAttachments()
    {
        return AttachmentService.get().getAttachments(_event);
    }

    public String getComments()
    {
        return _comments;
    }

    public SpecimenRequestRequirement getRequirement()
    {
        return _specimenRequestRequirement;
    }

    final public List<? extends NotificationRecipientSet> getRecipients()
    {
        return _recipients;
    }

    final public SpecimenRequest getSpecimenRequest()
    {
        return _request;
    }

    final public String getEventSummary()
    {
        return _eventSummary;
    }
}
