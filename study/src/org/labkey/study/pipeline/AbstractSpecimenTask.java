/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.pipeline.*;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderFactory;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.ZipUtil;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.importer.StudyJobSupport;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Date;
import java.io.File;
import java.util.Map;

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
        catch (Exception e)
        {
            throw new PipelineJobException("Error attempting to load specimen archive", e);
        }

        return new RecordedActionSet();
    }

    public static void doImport(@Nullable File inputFile, PipelineJob job, StudyImportContext ctx, boolean merge) throws PipelineJobException
    {
        VirtualFile specimenDir;
        File unzipDir = null;

        // CONSIDER: use subclass or something to isolate sample-mined code.
        boolean isSampleMinded = false;

        try
        {
            // backwards compatibility, if we are given a specimen archive as a zip file, we need to extract it
            if (inputFile != null)
            {
                // Might need to transform to a file type that we know how to import
                File specimenArchive;
                isSampleMinded = SampleMindedTransformTask.SAMPLE_MINDED_FILE_TYPE.isType(inputFile);
                if (isSampleMinded)
                {
                    if (job != null)
                        job.setStatus("TRANSFORMING SAMPLEMINDED DATA");
                    specimenArchive = SpecimenBatch.ARCHIVE_FILE_TYPE.getFile(inputFile.getParentFile(), SampleMindedTransformTask.SAMPLE_MINDED_FILE_TYPE.getBaseName(inputFile));
                    SampleMindedTransformTask transformer = new SampleMindedTransformTask(job);
                    transformer.transform(inputFile, specimenArchive);
                }
                else
                {
                    specimenArchive = inputFile;
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
                SpecimenImporter importer = new SpecimenImporter();
                importer.process(ctx.getUser(), ctx.getContainer(), specimenDir, merge, ctx.getLogger());
            }

            if (isSampleMinded)
            {
                // TODO this really doesn't belong in study module.
                // This should be some sort of plug-in
                String filename = inputFile.getName();
                String base = FileUtil.getBaseName(filename);
                String ext = FileUtil.getExtension(filename);
                if (base.endsWith("_data"))
                    base = base.substring(0,base.length()-"_data".length());
                File notdone = new File(inputFile.getParentFile(), base + "_notdone." + ext);
                File skipvis = new File(inputFile.getParentFile(), base + "_skipvis." + ext);

                if (notdone.exists())
                {
                    importNotDone(notdone, job, ctx);
                }
                if (skipvis.exists())
                {
                    importSkipVisit(skipvis, job, ctx);
                }
            }
        }
        catch (Exception e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            if (unzipDir != null && unzipDir.exists())
                delete(unzipDir, ctx.getLogger());

            // Since changing specimens in this study will impact specimens in ancillary studies dependent on this study,
            // we need to force a participant/visit refresh in those study containers (if any):
            StudyImpl[] dependentStudies = StudyManager.getInstance().getAncillaryStudies(ctx.getContainer());
            for (StudyImpl dependentStudy : dependentStudies)
                StudyManager.getInstance().getVisitManager(dependentStudy).updateParticipantVisits(ctx.getUser(), Collections.<DataSetDefinition>emptySet());
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


    static void importNotDone(File source, PipelineJob job, StudyImportContext ctx)
    {
        job.setStatus("Importing missing specimens");
        QuerySchema schema = DefaultSchema.get(job.getUser(), job.getContainer()).getSchema("rho");
        if (null == schema)
        {
            job.warn("Rho module is not installed in this folder.");
            return;
        }
        TableInfo target = schema.getTable("MissingSpecimen");
        if (null == target)
        {
            job.warn("Table not found: rho.MissingSpecimen");
            return;
        }
        importXls(target, source, job, ctx);
    }


    static void importSkipVisit(File source, PipelineJob job, StudyImportContext ctx)
    {
        job.setStatus("Importing missing visits");
        QuerySchema schema = DefaultSchema.get(job.getUser(), job.getContainer()).getSchema("rho");
        if (null == schema)
        {
            job.warn("Rho module is not installed in this folder.");
            return;
        }
        TableInfo target = schema.getTable("MissingVisit");
        if (null == target)
        {
            job.warn("Table not found: rho.MissingVisit");
            return;
        }
        importXls(target, source, job, ctx);
    }


    static void importXls(@NotNull TableInfo target, @NotNull File source, PipelineJob job, StudyImportContext ctx)
    {
        Study study = StudyManager.getInstance().getStudy(ctx.getContainer());
        if (null == study)
            return;
        DbScope scope = target.getSchema().getScope();
        DataIteratorContext context = new DataIteratorContext();

        try
        {
            scope.beginTransaction();

            DataLoaderFactory df = DataLoader.get().findFactory(source, null);
            if (null == df)
                return;
            DataLoader dl = df.createLoader(source, true, study.getContainer());
            DataIteratorBuilder sampleminded = StudyService.get().wrapSampleMindedTransform(dl, context, study, target);
            Map<String,Object> empty = new HashMap<String,Object>();

            // would be nice to have deleteAll() in QueryUpdateService
            new SqlExecutor(scope).execute("DELETE FROM rho." + target.getName() + " WHERE container=?", study.getContainer());
            int count = target.getUpdateService().importRows(job.getUser(), study.getContainer(), sampleminded, context.getErrors(), empty);
            if (!context.getErrors().hasErrors())
            {
                scope.commitTransaction();
                return;
            }
            {
                // TODO write errors to log
                return;
            }
        }
        catch (SQLException x)
        {
            boolean isConstraint = SqlDialect.isConstraintException(x);
            if (isConstraint)
                context.getErrors().addRowError(new ValidationException(x.getMessage()));
            else
                throw new RuntimeSQLException(x);
        }
        catch (IOException x)
        {
            context.getErrors().addRowError(new ValidationException(x.getMessage()));
        }
        finally
        {
            // write errors to log
            for (ValidationException error : context.getErrors().getRowErrors())
                job.error(error.getMessage());
            scope.closeConnection();
        }
    }
}
