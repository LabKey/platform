package org.labkey.experiment.samples;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.exp.CompressedInputStreamXarSource;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.XarReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.labkey.experiment.samples.SampleTypeAndDataClassFolderWriter.DEFAULT_DIRECTORY;

public class SampleTypeAndDataClassFolderImporter implements FolderImporter
{
    private SampleTypeAndDataClassFolderImporter()
    {
    }

    @Override
    public String getDataType()
    {
        return FolderArchiveDataTypes.SAMPLE_TYPES_AND_DATA_CLASSES;
    }

    @Override
    public String getDescription()
    {
        return "Sample Types and Data Class Importer";
    }

    @Override
    public void process(@Nullable PipelineJob job, ImportContext ctx, VirtualFile root) throws Exception
    {
        VirtualFile xarDir = root.getDir(DEFAULT_DIRECTORY);

        if (xarDir != null)
        {
            File xarFile = null;
            Map<String, String> sampleTypeDataFiles = new HashMap<>();
            Map<String, String> dataClassDataFiles = new HashMap<>();
            Logger log = ctx.getLogger();

            log.info("Starting Sample Type and Data Class import");
            for (String file: xarDir.list())
            {
                if (file.toLowerCase().endsWith(".xar"))
                {
                    if (xarFile == null)
                        xarFile = new File(xarDir.getLocation(), file);
                    else
                        log.error("More than one XAR file found in the sample type directory: ", file);
                }
                else if (file.toLowerCase().endsWith(".tsv"))
                {
                    if (file.startsWith(SampleTypeAndDataClassFolderWriter.SAMPLE_TYPE_PREFIX))
                        sampleTypeDataFiles.put(FileUtil.getBaseName(file.substring(SampleTypeAndDataClassFolderWriter.SAMPLE_TYPE_PREFIX.length())), file);
                    else if (file.startsWith(SampleTypeAndDataClassFolderWriter.DATA_CLASS_PREFIX))
                        dataClassDataFiles.put(FileUtil.getBaseName(file.substring(SampleTypeAndDataClassFolderWriter.DATA_CLASS_PREFIX.length())), file);
                }
            }

            try (DbScope.Transaction transaction = ExperimentService.get().getSchema().getScope().ensureTransaction())
            {
                if (xarFile != null)
                {
                    File logFile = CompressedInputStreamXarSource.getLogFileFor(xarFile);

                    if (job == null)
                    {
                        // need to fake up a job for the XarReader
                        job = new PipelineJob()
                        {
                            @Override
                            public User getUser()
                            {
                                return ctx.getUser();
                            }

                            @Override
                            public Container getContainer()
                            {
                                return ctx.getContainer();
                            }

                            @Override
                            public synchronized Logger getLogger()
                            {
                                return ctx.getLogger();
                            }

                            @Override
                            public URLHelper getStatusHref()
                            {
                                return null;
                            }

                            @Override
                            public String getDescription()
                            {
                                return "Sample Type XAR Import";
                            }
                        };
                    }
                    XarSource xarSource = new CompressedInputStreamXarSource(xarDir.getInputStream(xarFile.getName()), xarFile, logFile, job);
                    try
                    {
                        xarSource.init();
                    }
                    catch (Exception e)
                    {
                        log.error("Failed to initialize XAR source", e);
                        throw(e);
                    }
                    log.info("Importing XAR file: " + xarFile.getName());
                    XarReader reader = new XarReader(xarSource, job);
                    reader.setStrictValidateExistingSampleType(false);
                    reader.parseAndLoad(false);

                    // process any sample type data files and data class files
                    importTsvData(ctx, SamplesSchema.SCHEMA_NAME, reader.getSampleTypes().stream().map(Identifiable::getName).collect(Collectors.toList()),
                            sampleTypeDataFiles, xarDir);
                    importTsvData(ctx, ExpSchema.SCHEMA_EXP_DATA.toString(), reader.getDataClasses().stream().map(Identifiable::getName).collect(Collectors.toList()),
                            dataClassDataFiles, xarDir);
                }
                else
                    log.info("No xar file to process.");

                transaction.commit();
                log.info("Finished importing Sample Types and Data Classes");
            }
        }
    }

    private void importTsvData(ImportContext ctx, String schemaName, List<String> tableNames, Map<String, String> dataFileMap, VirtualFile dir) throws IOException, SQLException
    {
        Logger log = ctx.getLogger();
        UserSchema userSchema = QueryService.get().getUserSchema(ctx.getUser(), ctx.getContainer(), schemaName);
        if (userSchema != null)
        {
            for (String tableName : tableNames)
            {
                // tsv file name will have been generated from a sanitized table name
                String fileName = FileUtil.makeLegalName(tableName);
                if (dataFileMap.containsKey(fileName))
                {
                    String dataFileName = dataFileMap.get(fileName);
                    log.info("Importing data file: " + dataFileName);
                    TableInfo tinfo = userSchema.getTable(tableName);
                    if (tinfo != null)
                    {
                        try (InputStream is = dir.getInputStream(dataFileName))
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
                                    ((AbstractQueryUpdateService)qus).setAttachmentDirectory(dir.getDir(tableName));

                                    qus.loadRows(ctx.getUser(), ctx.getContainer(), loader, context, null);
                                    if (context.getErrors().hasErrors())
                                    {
                                        for (ValidationException error : context.getErrors().getRowErrors())
                                            log.error(error.getMessage());
                                    }
                                }
                                else
                                    log.error("Unable to import TSV data, could not find QUS for table : " + tableName);
                            }
                        }
                    }
                }
                else
                    log.error("Unable to import TSV data, data for table not found: " + tableName);
            }
        }
        else
            log.error("Unable to import TSV data, schema not found: " + schemaName);
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
            return new SampleTypeAndDataClassFolderImporter();
        }

        @Override
        public int getPriority()
        {
            return 75;
        }
    }
}
