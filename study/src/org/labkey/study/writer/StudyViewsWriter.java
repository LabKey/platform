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

    public static final String SELECTION_TEXT = "Custom Participant View";

    @Nullable
    @Override
    public String getSelectionText()
    {
        return SELECTION_TEXT;
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
        if (customParticipantView != null)
        {
            ViewsDocument.Views.ParticipantView participantView = views.addNewParticipantView();

            participantView.setFile(DEFAULT_PARTICIPANT_VIEW_FILE);
            participantView.setActive(customParticipantView.isActive());

            PrintWriter pw = vf.getPrintWriter(DEFAULT_PARTICIPANT_VIEW_FILE);
            pw.write(customParticipantView.getBody());
            pw.close();
        }
        vf.saveXmlBean(DEFAULT_SETTINGS_FILE, doc);
    }
}
