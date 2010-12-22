/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
import org.labkey.api.view.ViewContext;
import org.labkey.study.model.SampleRequest;
import org.labkey.study.model.SampleRequestRequirement;
import org.labkey.study.model.Specimen;
import org.labkey.study.query.SpecimenQueryView;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

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
            SpecimenQueryView view = SpecimenQueryView.createView(context, specimens, SpecimenQueryView.ViewType.VIALS_EMAIL);
            view.setDisableLowVialIndicators(true);
            return view.getSimpleHtmlTable();
        }
        return null;
    }

    protected Specimen[] getSpecimenList() throws SQLException
    {
        return _request.getSpecimens();
    }

    public @NotNull List<Attachment> getAttachments()
    {
        return Collections.emptyList();
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
