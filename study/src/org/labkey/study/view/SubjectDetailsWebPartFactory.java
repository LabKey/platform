/*
 * Copyright (c) 2010-2019 LabKey Corporation
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
package org.labkey.study.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.study.StudyModule;
import org.labkey.study.model.Participant;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.StudyManager.ParticipantViewConfig;

import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.PageFlowUtil.filter;

/**
 * User: brittp
 * Created: May 2, 2008 2:13:02 PM
 */
public class SubjectDetailsWebPartFactory extends BaseWebPartFactory
{
    public enum DataType
    {
        ALL("All Data")
        {
            @Override
            public WebPartView<?> createView(Container c, ParticipantViewConfig config)
            {
                VBox vbox = new VBox();
                vbox.addView(StudyManager.getInstance().getParticipantDemographicsView(c, config, null));
                // put a little space between the two views:
                vbox.addView(new HtmlView("<p/>"));
                vbox.addView(StudyManager.getInstance().getParticipantView(c, config));
                return vbox;
            }
        },
        DEMOGRAPHIC("Demographic Data")
        {
            @Override
            public WebPartView<ParticipantViewConfig> createView(Container c, ParticipantViewConfig config)
            {
                return StudyManager.getInstance().getParticipantDemographicsView(c, config, null);
            }
        },
        NON_DEMOGRAPHIC("Non-Demographic Data")
        {
            @Override
            public WebPartView<ParticipantViewConfig> createView(Container c, ParticipantViewConfig config)
            {
                return StudyManager.getInstance().getParticipantView(c, config);
            }
        };

        private final String _description;

        DataType(String description)
        {
            _description = description;
        }

        public String getDescription()
        {
            return _description;
        }

        public abstract WebPartView<?> createView(Container c, ParticipantViewConfig config);
    }

    public static final String PARTICIPANT_ID_KEY = "participantId";
    public static final String DATA_TYPE_KEY = "dataType";
    public static final String SOURCE_DATASET_ID_KEY = "datasetId";
    public static final String CURRENT_URL_KEY = "currentUrl";

    public SubjectDetailsWebPartFactory()
    {
        super("Subject Details", true, true);
        addLegacyNames("Participant Details");
    }

    @Override
    public Set<String> getAllowableLocations()
    {
        return PageFlowUtil.set(LOCATION_BODY, Participant.class.getName() + ":" + LOCATION_BODY);
    }

    @Override
    public String getDisplayName(Container container, String location)
    {
        return StudyModule.getWebPartSubjectNoun(container) + " Details";
    }

    @Override
    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        String participantId = webPart.getPropertyMap().get(PARTICIPANT_ID_KEY);
        String currentUrl = webPart.getPropertyMap().get(CURRENT_URL_KEY);
        String sourceDatasetIdString = webPart.getPropertyMap().get(SOURCE_DATASET_ID_KEY);

        // check if we are in a participant portal page
        Participant participant = (Participant)portalCtx.get(Participant.class.getName());
        if (null != participant)
            participantId = participant.getParticipantId();

        String dataTypeString = webPart.getPropertyMap().get(DATA_TYPE_KEY);
        DataType dataType = DataType.ALL; // We default to showing ALL
        if (dataTypeString != null)
        {
            try
            {
                dataType = DataType.valueOf(dataTypeString);
            }
            catch (IllegalArgumentException iae)
            {
                // Do nothing -- this can be called via user-edited javascript.
                // If we can't figure out what it is, display "ALL".
            }
        }

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
        WebPartView view = createView(portalCtx.getContainer(), portalCtx.getUser(), participantId, sourceDatasetId, currentUrl, dataType);
        view.setFrame(WebPartView.FrameType.PORTAL);
        view.setTitle(StudyService.get().getSubjectNounSingular(portalCtx.getContainer()) + " " + (participantId != null ? participantId : "unknown"));
        return view;
    }

    private WebPartView createView(final Container container,
                                   final User user,
                                   final String participantId,
                                   final int sourceDatasetId,
                                   final String currentUrl,
                                   DataType type)
    {
        String subjectNoun = StudyService.get().getSubjectNounSingular(container);
        if (participantId == null)
            return new HtmlView("This webpart does not reference a valid " + subjectNoun + " ID.  Please customize the webpart.");
        Study study = StudyManager.getInstance().getStudy(container);
        if (study == null)
            return new HtmlView("This folder does not contain a study.");
        Participant participant = StudyManager.getInstance().getParticipant(study, participantId);
        if (participant == null)
            return new HtmlView(filter(subjectNoun) + " \"" + filter(participantId) + "\" does not exist in study \"" + study.getLabel() + "\".");

        ParticipantViewConfig config = new ParticipantViewConfig()
        {
            private final Map<String, String> aliases = StudyManager.getInstance().getAliasMap(StudyManager.getInstance().getStudy(container), user, participantId);

            @Override
            public String getParticipantId()
            {
                return participantId;
            }

            @Override
            public Map<String, String> getAliases()
            {
                return aliases;
            }

            @Override
            public int getDatasetId()
            {
                return sourceDatasetId;
            }

            @Override
            public String getRedirectUrl()
            {
                return currentUrl;
            }
        };

        return type.createView(container, config);
    }


    @Override
    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new JspView<>("/org/labkey/study/view/customizeParticipantWebPart.jsp", webPart);
    }
}
