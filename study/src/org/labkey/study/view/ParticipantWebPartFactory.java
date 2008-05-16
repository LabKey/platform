package org.labkey.study.view;

import org.labkey.api.view.*;
import org.labkey.api.data.Container;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Study;
import org.labkey.study.model.Participant;

import java.sql.SQLException;

/**
 * Copyright (c) 2008 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * User: brittp
 * Created: May 2, 2008 2:13:02 PM
 */
public class ParticipantWebPartFactory extends WebPartFactory
{
    public static final String PARTICIPANT_ID_KEY = "participantId";
    public static final String SOURCE_DATASET_ID_KEY = "datasetId";
    public static final String CURRENT_URL_KEY = "currentUrl";

    public ParticipantWebPartFactory()
    {
        super("Participant Details", null, true, true);
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        String participantId = webPart.getPropertyMap().get(PARTICIPANT_ID_KEY);
        String currentUrl = webPart.getPropertyMap().get(CURRENT_URL_KEY);
        String sourceDatasetIdString = webPart.getPropertyMap().get(SOURCE_DATASET_ID_KEY);
        int sourceDatasetId = -1;
        try
        {
            if (sourceDatasetIdString != null)
                sourceDatasetId = Integer.parseInt(sourceDatasetIdString);
        }
        catch (NumberFormatException e)
        {
            // fall through; the default of -1 is fine.
        }
        WebPartView view = createView(portalCtx.getContainer(), participantId, sourceDatasetId, currentUrl);
        view.setTitle("Participant " + (participantId != null ? participantId : "unknown"));
        return view;
    }

    private WebPartView createView(Container container, final String participantId, final int sourceDatasetId, final String currentUrl) throws SQLException
    {
        if (participantId == null)
            return new HtmlView("This webpart does not reference a valid participant ID.  Please customize the webpart.");
        Study study = StudyManager.getInstance().getStudy(container);
        if (study == null)
            return new HtmlView("This folder does not contain a study.");
        Participant participant = StudyManager.getInstance().getParticipant(study, participantId);
        if (participant == null)
            return new HtmlView("Participant \"" + participantId + "\" does not exist in study \"" + study.getLabel() + "\".");

        return StudyManager.getInstance().getParticipantView(container, new StudyManager.ParticipantViewConfig()
        {
            public String getParticipantId()
            {
                return participantId;
            }

            public int getDatasetId()
            {
                return sourceDatasetId;
            }

            public String getRedirectUrl()
            {
                return currentUrl;
            }
        });
    }

    @Override
    public HttpView getEditView(Portal.WebPart webPart)
    {
        return new JspView<Portal.WebPart>("/org/labkey/study/view/customizeParticipantWebPart.jsp", webPart);
    }

}
