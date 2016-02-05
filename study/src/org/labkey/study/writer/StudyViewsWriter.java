/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.study.writer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.CustomParticipantView;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.studyViews.ViewsDocument;

import java.io.PrintWriter;

/**
 * User: klum
 * Date: 10/8/13
 */
public class StudyViewsWriter implements InternalStudyWriter
{
    private static final String DEFAULT_VIEWS_DIRECTORY = "views";
    private static final String DEFAULT_SETTINGS_FILE = "settings.xml";
    private static final String DEFAULT_PARTICIPANT_VIEW_FILE = "participant.html";

    @Nullable
    @Override
    public String getDataType()
    {
        return StudyArchiveDataTypes.CUSTOM_PARTICIPANT_VIEWS;
    }

    @Override
    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile root) throws Exception
    {
        StudyDocument.Study studyXml = ctx.getXml();
        StudyDocument.Study.StudyViews studyViews = studyXml.addNewStudyViews();

        studyViews.setDir(DEFAULT_VIEWS_DIRECTORY);
        studyViews.setSettings(DEFAULT_SETTINGS_FILE);

        VirtualFile vf = root.getDir(DEFAULT_VIEWS_DIRECTORY);
        ViewsDocument doc = ViewsDocument.Factory.newInstance();

        ViewsDocument.Views views = doc.addNewViews();

        // export a custom participant page if present
        CustomParticipantView customParticipantView = StudyManager.getInstance().getCustomParticipantView(study);

        if (customParticipantView != null && !customParticipantView.isModuleParticipantView())
        {
            ViewsDocument.Views.ParticipantView participantView = views.addNewParticipantView();

            participantView.setFile(DEFAULT_PARTICIPANT_VIEW_FILE);
            participantView.setActive(customParticipantView.isActive());

            try (PrintWriter pw = vf.getPrintWriter(DEFAULT_PARTICIPANT_VIEW_FILE))
            {
                pw.write(customParticipantView.getBody());
            }
        }

        vf.saveXmlBean(DEFAULT_SETTINGS_FILE, doc);
    }
}
