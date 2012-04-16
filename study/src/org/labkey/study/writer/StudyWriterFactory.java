package org.labkey.study.writer;

import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;


/**
 * User: cnathe
 * Date: Apr 11, 2012
 */
public class StudyWriterFactory implements FolderWriterFactory
{
    public static final String DEFAULT_DIRECTORY = "study";

    @Override
    public FolderWriter create()
    {
        return new StudyFolderWriter();
    }

    public class StudyFolderWriter implements FolderWriter
    {
        @Override
        public String getSelectionText()
        {
            return "Study";
        }

        @Override
        public boolean show(Container c)
        {
            StudyImpl study = StudyManager.getInstance().getStudy(c);
            return null != study;
        }

        @Override
        public boolean includeInType(AbstractFolderContext.ExportType type)
        {
            return AbstractFolderContext.ExportType.ALL == type || AbstractFolderContext.ExportType.STUDY == type; 
        }

        @Override
        public void write(Container c, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
        {
            StudyImpl study = StudyManager.getInstance().getStudy(c);
            if (null != study)
            {
                ctx.getXml().addNewStudy().setDir(DEFAULT_DIRECTORY);
                VirtualFile studyDir = vf.getDir(DEFAULT_DIRECTORY);

                StudyWriter writer = new StudyWriter();
                StudyExportContext exportCtx = new StudyExportContext(study, ctx.getUser(), c, "old".equals(ctx.getFormat()), ctx.getDataTypes(), ctx.getLogger());
                writer.write(study, exportCtx, studyDir);
            }
        }

        @Override
        public Set<Writer> getChildren()
        {
            Set<Writer> children = new HashSet<Writer>();
            Collection<InternalStudyWriter> writers = StudySerializationRegistryImpl.get().getInternalStudyWriters();
            for (InternalStudyWriter writer : writers)
            {
                children.add(writer);
            }
            return children;
        }
    }
}
