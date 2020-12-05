package org.labkey.experiment.samples;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.exp.CompressedXarSource;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.XarReader;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SampleTypeFolderImporter implements FolderImporter
{
    private static final String DEFAULT_DIRECTORY = "sample-types";
    private static final String XAR_FILE_NAME = "sample_types.xar";
    private static final String XAR_XML_FILE_NAME = XAR_FILE_NAME + ".xml";

    private SampleTypeFolderImporter()
    {
    }

    @Override
    public String getDataType()
    {
        return FolderArchiveDataTypes.SAMPLE_TYPES;
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    @Override
    public void process(@Nullable PipelineJob job, ImportContext ctx, VirtualFile root) throws Exception
    {
        VirtualFile xarDir = root.getDir(DEFAULT_DIRECTORY);

        if (xarDir != null)
        {
            File xarFile = null;
            Set<String> dataFiles = new HashSet<>();
            Logger log = job.getLogger();

            log.info("Starting Sample Type import");
            for (String file: xarDir.list())
            {
                if (file.toLowerCase().endsWith(".xar"))
                {
                    if (xarFile == null)
                        xarFile = new File(xarDir.getLocation(), file);
                    else
                        ctx.getLogger().error("More than one XAR file found in the sample type directory: ", file);
                }
                else if (file.toLowerCase().endsWith(".tsv"))
                {
                    dataFiles.add(file);
                }
            }

            try (DbScope.Transaction transaction = ExperimentService.get().getSchema().getScope().ensureTransaction())
            {
                if (xarFile != null)
                {
                    XarSource xarSource = new CompressedXarSource(xarFile, job, ctx.getContainer());
                    try
                    {
                        xarSource.init();
                    }
                    catch (Exception e)
                    {
                        ctx.getLogger().error("Failed to initialize XAR source", e);
                        throw(e);
                    }
                    log.info("Importing XAR file: " + xarFile.getName());
                    XarReader reader = new XarReader(xarSource, job);
                    reader.parseAndLoad(false);
                }
                else
                    ctx.getLogger().info("No xar file to process.");

                // process any sample type data files
                UserSchema userSchema = QueryService.get().getUserSchema(ctx.getUser(), ctx.getContainer(), SamplesSchema.SCHEMA_NAME);
                if (userSchema != null)
                {
                    for (String dataFileName : dataFiles)
                    {
                        log.info("Importing Sample Type data file: " + dataFileName);
                        String sampleTypeName = FileUtil.getBaseName(dataFileName);
                        TableInfo tinfo = userSchema.getTable(sampleTypeName);
                        if (tinfo != null)
                        {
                            try (InputStream is = xarDir.getInputStream(dataFileName))
                            {
                                if (null != is)
                                {
                                    BatchValidationException errors = new BatchValidationException();
                                    DataLoader loader = DataLoader.get().createLoader(dataFileName, null, is, true, null, null);

                                    QueryUpdateService qus = tinfo.getUpdateService();
                                    if (qus != null)
                                    {
                                        DataIteratorContext context = new DataIteratorContext(errors);
                                        context.setInsertOption(QueryUpdateService.InsertOption.MERGE);
                                        context.setAllowImportLookupByAlternateKey(true);

                                        qus.loadRows(ctx.getUser(), ctx.getContainer(), loader, context, null);
                                    }
                                    else
                                        log.error("Unable to import Sample Type data, could not find QUS : " + sampleTypeName);
                                }
                            }
                        }
                        else
                            log.error("Unable to import Sample Type data, table not found: " + sampleTypeName);
                    }
                }
                transaction.commit();
                log.info("Finished importing Sample Types");
            }
        }
    }

    @Override
    public @NotNull Collection<PipelineJobWarning> postProcess(ImportContext ctx, VirtualFile root) throws Exception
    {
        return Collections.emptyList();
    }

    public static class Factory implements FolderImporterFactory
    {
        @Override
        public FolderImporter create()
        {
            return new SampleTypeFolderImporter();
        }

        @Override
        public int getPriority()
        {
            return DEFAULT_PRIORITY;
        }
    }
}
