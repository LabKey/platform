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

package org.labkey.study.pipeline;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.util.DateUtil;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.ZipUtil;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.importer.StudyJobSupport;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.io.File;
import java.sql.BatchUpdateException;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/*
* User: adam
* Date: Aug 31, 2009
* Time: 9:12:22 AM
*/
public abstract class AbstractSpecimenTask<FactoryType extends AbstractSpecimenTaskFactory<FactoryType>> extends PipelineJob.Task<TaskFactory>
{
    protected AbstractSpecimenTask(FactoryType factory, PipelineJob job)
    {
        super(factory, job);
    }

    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        try
        {
            PipelineJob job = getJob();
            SpecimenJobSupport support = job.getJobSupport(SpecimenJobSupport.class);
            File specimenArchive = support.getSpecimenArchive();
            StudyImportContext ctx = job.getJobSupport(StudyJobSupport.class).getImportContext();

            doImport(specimenArchive, job, ctx, isMerge());
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

    public static void doImport(@Nullable File inputFile, PipelineJob job, StudyImportContext ctx, boolean merge) throws PipelineJobException
    {
        doImport(inputFile, job, ctx, merge, true);
    }


    public static void doImport(@Nullable File inputFile, PipelineJob job, StudyImportContext ctx, boolean merge,
            boolean syncParticipantVisit) throws PipelineJobException
    {
        VirtualFile specimenDir;
        File unzipDir = null;

        // do nothing if we've specified data types and specimen is not one of them
        if (!ctx.isDataTypeSelected(StudyImportSpecimenTask.getType()))
            return;

        try
        {
            // backwards compatibility, if we are given a specimen archive as a zip file, we need to extract it
            if (inputFile != null)
            {
                // Might need to transform to a file type that we know how to import

                File specimenArchive = inputFile;
                for (SpecimenTransform transformer : SpecimenService.get().getSpecimenTransforms(ctx.getContainer()))
                {
                    if (transformer.getFileType().isType(inputFile))
                    {
                        if (job != null)
                            job.setStatus("TRANSFORMING " + transformer.getName() + " DATA");
                        specimenArchive = SpecimenBatch.ARCHIVE_FILE_TYPE.getFile(inputFile.getParentFile(), transformer.getFileType().getBaseName(inputFile));
                        transformer.transform(job, inputFile, specimenArchive);
                        break;
                    }
                }

                if (null == specimenArchive)
                {
                    ctx.getLogger().info("No specimen archive");
                    return;
                }
                else
                {
                    if (job != null)
                        job.setStatus("UNZIPPING SPECIMEN ARCHIVE");

                    ctx.getLogger().info("Unzipping specimen archive " + specimenArchive.getPath());
                    String tempDirName = DateUtil.formatDateTime(new Date(), "yyMMddHHmmssSSS");
                    unzipDir = new File(specimenArchive.getParentFile(), tempDirName);
                    List<File> files = ZipUtil.unzipToDirectory(specimenArchive, unzipDir, ctx.getLogger());

                    ctx.getLogger().info("Archive unzipped to " + unzipDir.getPath());
                    specimenDir = new FileSystemFile(unzipDir);
                }
            }
            else
            {
                // the specimen files must already be "extracted" in the specimens directory
                specimenDir = ctx.getDir("specimens");
            }

            if (specimenDir != null)
            {
                if (job != null)
                    job.setStatus("PROCESSING SPECIMENS");
                ctx.getLogger().info("Starting specimen import...");
                SpecimenImporter importer = new SpecimenImporter(ctx.getContainer(), ctx.getUser());
                importer.process(specimenDir, merge, ctx, job, syncParticipantVisit);
            }

            // perform any tasks after the transform and import has been completed
            for (SpecimenTransform transformer : SpecimenService.get().getSpecimenTransforms(ctx.getContainer()))
            {
                if (transformer.getFileType().isType(inputFile))
                {
                    if (job != null)
                        job.setStatus("OPTIONAL POST TRANSFORMING STEP " + transformer.getName() + " DATA");
                    File specimenArchive = SpecimenBatch.ARCHIVE_FILE_TYPE.getFile(inputFile.getParentFile(), transformer.getFileType().getBaseName(inputFile));
                    transformer.postTransform(job, inputFile, specimenArchive);
                    break;
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
            if (unzipDir != null && unzipDir.exists())
                delete(unzipDir, ctx.getLogger());

            // Since changing specimens in this study will impact specimens in ancillary studies dependent on this study,
            // we need to force a participant/visit refresh in those study containers (if any):
            for (StudyImpl dependentStudy : StudyManager.getInstance().getAncillaryStudies(ctx.getContainer()))
                StudyManager.getInstance().getVisitManager(dependentStudy).updateParticipantVisits(ctx.getUser(), Collections.emptySet());
        }
    }

    protected boolean isMerge()
    {
        return getJob().getJobSupport(SpecimenJobSupport.class).isMerge();
    }

    private static void delete(File file, Logger log)
    {
        if (file.isDirectory())
        {
            for (File child : file.listFiles())
                delete(child, log);
        }
        log.info("Deleting " + file.getPath());
        if (!file.delete())
            log.error("Unable to delete file: " + file.getPath());
    }
}
