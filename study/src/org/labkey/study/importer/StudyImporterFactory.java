package org.labkey.study.importer;

import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.admin.*;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.security.User;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.pipeline.StudyImportDatasetTask;
import org.labkey.study.pipeline.StudyImportSpecimenTask;
import org.labkey.study.writer.StudyWriterFactory;
import org.springframework.validation.BindException;

import java.io.File;
import java.util.Collection;

/**
 * User: cnathe
 * Date: Apr 11, 2012
 */
public class StudyImporterFactory implements FolderImporterFactory
{
    @Override
    public FolderImporter create()
    {
        return new StudyFolderImporter();
    }

    @Override
    public boolean isFinalImporter()
    {
        return false;
    }

    public class StudyFolderImporter implements FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getDescription()                                    
        {
            return "study";
        }

        @Override
        public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            File studyDir = ctx.getDir(StudyWriterFactory.DEFAULT_DIRECTORY);
            if (null != studyDir)
            {
                job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());

                Container c = ctx.getContainer();
                User user = ctx.getUser();
                File studyFile = new File(studyDir, "study.xml");
                BindException errors = new NullSafeBindException(c, "import");
                StudyImportContext studyImportContext = new StudyImportContext(user, c, studyFile, ctx.getLogger(), studyFile.getParentFile());

                StudyImportInitialTask.doImport(job, studyImportContext, errors, studyFile.getName());

                File datasetsFile = StudyImportDatasetTask.getDatasetsFile(studyImportContext, studyImportContext.getRoot());
                StudyImpl study = StudyManager.getInstance().getStudy(c);
                StudyImportDatasetTask.doImport(datasetsFile, job, study);

                File specimenFile = StudyImportSpecimenTask.getSpecimenArchive(studyImportContext, studyImportContext.getRoot());
                StudyImportSpecimenTask.doImport(specimenFile, job, false);

                StudyImportFinalTask.doImport(job, studyImportContext, errors);

                // TODO: review how errors are handled in the various study import tasks
                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return null;
        }
    }
}
