package org.labkey.study.importer;

import org.apache.commons.io.IOUtils;
import org.apache.xmlbeans.XmlObject;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.CustomParticipantView;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.studyViews.ViewsDocument;
import org.springframework.validation.BindException;

import java.io.InputStream;

/**
 * User: klum
 * Date: 10/8/13
 */
public class StudyViewsImporter implements InternalStudyImporter
{
    @Override
    public String getDescription()
    {
        return "custom participant view";
    }

    @Override
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        StudyImpl study = StudyManager.getInstance().getStudy(ctx.getContainer());
        StudyDocument.Study.StudyViews viewsXml = ctx.getXml().getStudyViews();

        if (viewsXml != null)
        {
            VirtualFile folder = root.getDir(viewsXml.getDir());
            String settingsFileName = viewsXml.getSettings();

            if (settingsFileName != null)
            {
                XmlObject doc = folder.getXmlBean(settingsFileName);
                if (doc instanceof ViewsDocument)
                {
                    XmlBeansUtil.validateXmlDocument(doc, "Validating ViewsDocument during study import");
                    ViewsDocument.Views views = ((ViewsDocument) doc).getViews();
                    if (views != null)
                    {
                        ViewsDocument.Views.ParticipantView participantView = views.getParticipantView();
                        if (participantView != null)
                        {
                            String participantFileName = participantView.getFile();
                            InputStream is = folder.getInputStream(participantFileName);

                            CustomParticipantView view = StudyManager.getInstance().getCustomParticipantView(study);
                            if (view == null)
                                view = new CustomParticipantView();

                            view.setBody(IOUtils.toString(is));
                            view.setActive(participantView.getActive());
                            StudyManager.getInstance().saveCustomParticipantView(study, ctx.getUser(), view);
                            IOUtils.closeQuietly(is);
                        }
                    }
                }
            }
        }
    }
}
