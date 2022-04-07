package org.labkey.api.study.importer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.LoggerGetter;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.writer.AbstractStudyContext;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.xml.StudyDocument;

import java.util.Set;

public class SimpleStudyImportContext extends AbstractStudyContext
{
    public SimpleStudyImportContext(User user, Container c, StudyDocument studyDoc, Set<String> dataTypes, LoggerGetter logger, @Nullable VirtualFile root)
    {
        super(user, c, studyDoc, dataTypes, logger, root);
    }

    public Study getStudy()
    {
        StudyService ss = StudyService.get();
        assert null != ss;

        return ss.getStudy(getContainer());
    }
}
