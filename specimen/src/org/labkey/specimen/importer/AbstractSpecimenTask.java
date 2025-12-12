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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportException;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.study.importer.SimpleStudyImportContext;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.ZipUtil;
import org.labkey.specimen.pipeline.SpecimenJobSupport;
import org.labkey.specimen.writer.SpecimenArchiveDataTypes;
import org.labkey.vfs.FileLike;

import java.io.IOException;
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
            FileLike specimenArchive = getSpecimenFile(job);
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

    protected FileLike getSpecimenFile(PipelineJob job) throws Exception
    {
        SpecimenJobSupport support = job.getJobSupport(SpecimenJobSupport.class);
        return support.getSpecimenArchivePath();
    }

    protected SimpleStudyImportContext getImportContext(PipelineJob job)
    {
        return job.getJobSupport(SpecimenJobSupport.class).getImportContext();
    }

    public static void doImport(@Nullable FileLike inputFile, PipelineJob job, SimpleStudyImportContext ctx, boolean merge,
                                boolean syncParticipantVisit) throws PipelineJobException
    {
        doImport(inputFile, job, ctx, merge, syncParticipantVisit, new DefaultImportHelper());
    }

    private static void doPostTransform(SpecimenTransform transformer, FileLike inputFile, PipelineJob job) throws PipelineJobException
    {
        if (transformer.getFileType().isType(inputFile))
        {
            if (job != null)
                job.setStatus("OPTIONAL POST TRANSFORMING STEP " + transformer.getName() + " DATA");
            FileLike specimenArchive = ARCHIVE_FILE_TYPE.getFile(inputFile.getParent(), transformer.getFileType().getBaseName(inputFile));
            transformer.postTransform(job, inputFile, specimenArchive);
        }
    }

    public AbstractSpecimenTask(TaskFactory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static void doImport(@Nullable FileLike inputFile, PipelineJob job, SimpleStudyImportContext ctx, boolean merge,
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
            if (e instanceof BatchUpdateException bue && null != bue.getNextException())
                e = bue.getNextException();
            throw new PipelineJobException(e);
        }
        finally
        {
            // do any temp file cleanup
            importHelper.afterImport(ctx);
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
        VirtualFile getSpecimenDir(PipelineJob job, SimpleStudyImportContext ctx, @Nullable FileLike inputFile) throws IOException, ImportException, PipelineJobException;
        void afterImport(SimpleStudyImportContext ctx);
    }

    protected static class DefaultImportHelper implements ImportHelper
    {
        private FileLike _unzipDir;

        @Override
        public VirtualFile getSpecimenDir(PipelineJob job, SimpleStudyImportContext ctx, @Nullable FileLike inputFile) throws IOException, ImportException, PipelineJobException
        {
            // backwards compatibility, if we are given a specimen archive as a zip file, we need to extract it
            if (inputFile != null)
            {
                // Might need to transform to a file type that we know how to import
                FileLike specimenArchive = inputFile;

                for (SpecimenTransform transformer : SpecimenService.get().getSpecimenTransforms(ctx.getContainer()))
                {
                    if (transformer.getFileType().isType(inputFile.getName()))
                    {
                        if (job != null)
                            job.setStatus("TRANSFORMING " + transformer.getName() + " DATA");
                        specimenArchive = ARCHIVE_FILE_TYPE.getFile(inputFile.getParent(), transformer.getFileType().getBaseName(inputFile));
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
                    _unzipDir = specimenArchive.getParent().resolveChild(tempDirName);
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

        protected void delete(FileLike path, SimpleStudyImportContext ctx)
        {
            if (path.isDirectory())
            {
                FileUtil.deleteDir(path);
            }
        }
    }
}
