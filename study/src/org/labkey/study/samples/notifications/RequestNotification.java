/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
