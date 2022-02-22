/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

package org.labkey.specimen.importer;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportException;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.specimen.importer.SpecimenImporter;
import org.labkey.api.specimen.pipeline.SpecimenJobSupport;
import org.labkey.api.specimen.writer.SpecimenArchiveDataTypes;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.importer.SimpleStudyImportContext;
import org.labkey.api.study.model.VisitService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.BatchUpdateException;
import java.util.Date;

/*
* User: adam
* Date: Aug 31, 2009
* Time: 9:12:22 AM
*/
public abstract class AbstractSpecimenTask<FactoryType extends AbstractSpecimenTaskFactory<FactoryType>> extends PipelineJob.Task<TaskFactory>
{
    public static final FileType ARCHIVE_FILE_TYPE = new FileType(".specimens");

    protected AbstractSpecimenTask(FactoryType factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        try
        {
            PipelineJob job = getJob();
            Path specimenArchive = getSpecimenFile(job);
            SimpleStudyImportContext ctx = getImportContext(job);

            doImport(specimenArchive, job, ctx, isMerge(), true, getImportHelper());
        }
        catch (CancelledException | PipelineJobException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new PipelineJobException("Error attempting to load specimen archive", e);
        }

        return new RecordedActionSet();
    }

    protected Path getSpecimenFile(PipelineJob job) throws Exception
    {
        SpecimenJobSupport support = job.getJobSupport(SpecimenJobSupport.class);
        return support.getSpecimenArchivePath();
    }

    protected SimpleStudyImportContext getImportContext(PipelineJob job)
    {
        return job.getJobSupport(SpecimenJobSupport.class).getImportContext();
    }

    public static void doImport(@Nullable Path inputFile, PipelineJob job, SimpleStudyImportContext ctx, boolean merge,
            boolean syncParticipantVisit) throws PipelineJobException
    {
        doImport(inputFile, job, ctx, merge, syncParticipantVisit, new DefaultImportHelper());
    }

    private static void doPostTransform(SpecimenTransform transformer, Path inputFile, PipelineJob job) throws PipelineJobException
    {
        if (transformer.getFileType().isType(inputFile))
        {
            if (job != null)
                job.setStatus("OPTIONAL POST TRANSFORMING STEP " + transformer.getName() + " DATA");
            File specimenArchive = ARCHIVE_FILE_TYPE.getFile(inputFile.getParent().toFile(), transformer.getFileType().getBaseName(inputFile));
            transformer.postTransform(job, inputFile.toFile(), specimenArchive);
        }
    }

    public static void doImport(@Nullable Path inputFile, PipelineJob job, SimpleStudyImportContext ctx, boolean merge,
                                boolean syncParticipantVisit, ImportHelper importHelper) throws PipelineJobException
    {
        // do nothing if we've specified data types and specimen is not one of them
        if (!ctx.isDataTypeSelected(SpecimenArchiveDataTypes.SPECIMENS))
            return;

        try
        {
            VirtualFile specimenDir;
            specimenDir = importHelper.getSpecimenDir(job, ctx, inputFile);
            if (specimenDir == null)
                return;

            if (job != null)
                job.setStatus("PROCESSING SPECIMENS");
            ctx.getLogger().info("Starting specimen import...");
            SpecimenImporter importer = new SpecimenImporter(ctx.getContainer(), ctx.getUser());
            importer.process(specimenDir, merge, ctx, job, syncParticipantVisit);

            // perform any tasks after the transform and import has been completed
            String activeImporter = SpecimenService.get().getActiveSpecimenImporter(ctx.getContainer());
            if (null != activeImporter)
            {
                SpecimenTransform activeTransformer = SpecimenService.get().getSpecimenTransform(activeImporter);
                if (activeTransformer != null && activeTransformer.getFileType().isType(inputFile))
                    doPostTransform(activeTransformer, inputFile, job);
            }
            else
            {
                for (SpecimenTransform transformer : SpecimenService.get().getSpecimenTransforms(ctx.getContainer()))
                {
                    if (transformer.getFileType().isType(inputFile))
                    {
                        doPostTransform(transformer, inputFile, job);
                        break;
                    }
                }
            }
        }
        catch (CancelledException e)
        {
            throw e;        // rethrow so pipeline marks it canceled
        }
        catch (Exception e)
        {
            if (e instanceof BatchUpdateException && null != ((BatchUpdateException)e).getNextException())
                e = ((BatchUpdateException)e).getNextException();
            throw new PipelineJobException(e);
        }
        finally
        {
            // do any temp file cleanup
            importHelper.afterImport(ctx);

            // Since changing specimens in this study will impact specimens in ancillary studies dependent on this study,
            // we need to force a participant/visit refresh in those study containers (if any):
            for (Study dependentStudy : StudyService.get().getAncillaryStudies(ctx.getContainer()))
                VisitService.get().updateParticipantVisits(dependentStudy, ctx.getUser());
        }
    }

    protected boolean isMerge()
    {
        return getJob().getJobSupport(SpecimenJobSupport.class).isMerge();
    }

    public ImportHelper getImportHelper()
    {
        return new DefaultImportHelper();
    }

    public interface ImportHelper
    {
        VirtualFile getSpecimenDir(PipelineJob job, SimpleStudyImportContext ctx, @Nullable Path inputFile) throws IOException, ImportException, PipelineJobException;
        void afterImport(SimpleStudyImportContext ctx);
    }

    protected static class DefaultImportHelper implements ImportHelper
    {
        private Path _unzipDir;

        @Override
        public VirtualFile getSpecimenDir(PipelineJob job, SimpleStudyImportContext ctx, @Nullable Path inputFile) throws IOException, ImportException, PipelineJobException
        {
            // backwards compatibility, if we are given a specimen archive as a zip file, we need to extract it
            if (inputFile != null)
            {
                // Might need to transform to a file type that we know how to import
                Path specimenArchive = inputFile;

                for (SpecimenTransform transformer : SpecimenService.get().getSpecimenTransforms(ctx.getContainer()))
                {
                    if (transformer.getFileType().isType(inputFile.getFileName().toString()))
                    {
                        if (job != null)
                            job.setStatus("TRANSFORMING " + transformer.getName() + " DATA");
                        specimenArchive = ARCHIVE_FILE_TYPE.getPath(inputFile.getParent(), transformer.getFileType().getBaseName(inputFile));
                        transformer.transform(job, inputFile, specimenArchive);
                        break;
                    }
                }

                if (null == specimenArchive)
                {
                    ctx.getLogger().info("No specimen archive");
                    return null;
                }
                else
                {
                    if (job != null)
                        job.setStatus("UNZIPPING SPECIMEN ARCHIVE");

                    ctx.getLogger().info("Unzipping specimen archive " + specimenArchive);
                    String tempDirName = DateUtil.formatDateTime(new Date(), "yyMMddHHmmssSSS");
                    _unzipDir = specimenArchive.getParent().resolve(tempDirName);
                    ZipUtil.unzipToDirectory(specimenArchive, _unzipDir, ctx.getLogger());

                    ctx.getLogger().info("Archive unzipped to " + _unzipDir);
                    return new FileSystemFile(_unzipDir);
                }
            }
            else
            {
                // the specimen files must already be "extracted" in the specimens directory
                return ctx.getDir("specimens");
            }
        }

        @Override
        public void afterImport(SimpleStudyImportContext ctx)
        {
            if (_unzipDir != null)
                delete(_unzipDir, ctx);
        }

        protected void delete(Path path, SimpleStudyImportContext ctx)
        {
            Logger log = ctx.getLogger();
            if (Files.isDirectory(path))
            {
                try
                {
                    FileUtil.deleteDir(path);
                }
                catch (IOException e)
                {
                    log.error("Error deleting files from " + path, e);
                }
            }
        }
    }
}
