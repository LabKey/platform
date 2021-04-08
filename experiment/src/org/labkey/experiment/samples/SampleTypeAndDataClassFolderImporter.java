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
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
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
import org.labkey.experiment.xar.XarImportContext;

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
import static org.labkey.experiment.samples.SampleTypeAndDataClassFolderWriter.XAR_RUNS_NAME;
import static org.labkey.experiment.samples.SampleTypeAndDataClassFolderWriter.XAR_TYPES_NAME;

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
        return getDataType().toLowerCase();
    }

    @Override
    public void process(@Nullable PipelineJob job, ImportContext ctx, VirtualFile root) throws Exception
    {
        VirtualFile xarDir = root.getDir(DEFAULT_DIRECTORY);

        if (xarDir != null)
        {
            File typesXarFile = null;
            File runsXarFile = null;
            Map<String, String> sampleTypeDataFiles = new HashMap<>();
            Map<String, String> dataClassDataFiles = new HashMap<>();
            Logger log = ctx.getLogger();

            if (null != job)
                job.setStatus("IMPORT " + getDescription());
            log.info("Starting Sample Type and Data Class import");

            for (String file: xarDir.list())
            {
                if (file.equalsIgnoreCase(XAR_TYPES_NAME))
                {
                    if (typesXarFile == null)
                        typesXarFile = new File(xarDir.getLocation(), file);
                    else
                        log.error("More than one types XAR file found in the sample type directory: ", file);
                }
                else if (file.equalsIgnoreCase(XAR_RUNS_NAME))
                {
                    if (runsXarFile == null)
                        runsXarFile = new File(xarDir.getLocation(), file);
                    else
                        log.error("More than one runs XAR file found in the sample type directory: ", file);
                }
                else if (file.toLowerCase().endsWith(".tsv"))
                {
                    if (file.startsWith(SampleTypeAndDataClassFolderWriter.SAMPLE_TYPE_PREFIX))
                    {
                        sampleTypeDataFiles.put(FileUtil.getBaseName(file.substring(SampleTypeAndDataClassFolderWriter.SAMPLE_TYPE_PREFIX.length())), file);
                    }
                    else if (file.startsWith(SampleTypeAndDataClassFolderWriter.DATA_CLASS_PREFIX))
                    {
                        dataClassDataFiles.put(FileUtil.getBaseName(file.substring(SampleTypeAndDataClassFolderWriter.DATA_CLASS_PREFIX.length())), file);
                    }
                }
            }

            // push in an additional context so that other XAR Importers can share states
            XarImportContext xarCtx = new XarImportContext();
            xarCtx.setStrictValidateExistingSampleType(false);
            ctx.addContext(XarImportContext.class, xarCtx);

            try (DbScope.Transaction transaction = ExperimentService.get().getSchema().getScope().ensureTransaction())
            {
                if (typesXarFile != null)
                {
                    File logFile = null;
                    // we don't need the log file in cases where the xarFile is a virtual file and not in the file system
                    if (typesXarFile.exists())
                        logFile = CompressedInputStreamXarSource.getLogFileFor(typesXarFile);

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
                                return "Sample Type and Data Class XAR Import";
                            }
                        };
                    }

                    XarSource typesXarSource = new CompressedInputStreamXarSource(xarDir.getInputStream(typesXarFile.getName()), typesXarFile, logFile, job);
                    try
                    {
                        typesXarSource.init();
                    }
                    catch (Exception e)
                    {
                        log.error("Failed to initialize types XAR source", e);
                        throw(e);
                    }
                    log.info("Importing the types XAR file: " + typesXarFile.getName());
                    XarReader typesReader = new XarReader(typesXarSource, job);
                    typesReader.setStrictValidateExistingSampleType(xarCtx.isStrictValidateExistingSampleType());
                    typesReader.parseAndLoad(false, ctx.getAuditBehaviorType());

                    // process any sample type data files and data class files
                    importTsvData(ctx, SamplesSchema.SCHEMA_NAME, typesReader.getSampleTypes().stream().map(Identifiable::getName).collect(Collectors.toList()),
                            sampleTypeDataFiles, xarDir);
                    importTsvData(ctx, ExpSchema.SCHEMA_EXP_DATA.toString(), typesReader.getDataClasses().stream().map(Identifiable::getName).collect(Collectors.toList()),
                            dataClassDataFiles, xarDir);

                    // handle wiring up any derivation runs
                    if (runsXarFile != null)
                    {
                        XarSource runsXarSource = new CompressedInputStreamXarSource(xarDir.getInputStream(runsXarFile.getName()), runsXarFile, logFile, job);
                        try
                        {
                            runsXarSource.init();
                        }
                        catch (Exception e)
                        {
                            log.error("Failed to initialize runs XAR source", e);
                            throw(e);
                        }
                        log.info("Importing the runs XAR file: " + runsXarFile.getName());
                        XarReader runsReader = new XarReader(runsXarSource, job);
                        runsReader.setStrictValidateExistingSampleType(xarCtx.isStrictValidateExistingSampleType());
                        runsReader.parseAndLoad(false, ctx.getAuditBehaviorType());
                    }
                }
                else
                    log.info("No types XAR file to process.");

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
                    TableInfo tinfo = userSchema.getTable(tableName);
                    if (tinfo != null)
                    {
                        log.info("Importing data file: " + dataFileName);
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
                                    Map<Enum, Object> options = new HashMap<>();
                                    try
                                    {
                                        options.put(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior, ctx.getAuditBehaviorType());
                                    }
                                    catch (Exception e)
                                    {
                                        log.error("Unable to get audit behavior for import. Default behavior will be used.");
                                    }
                                    context.setConfigParameters(options);

                                    int count = qus.loadRows(ctx.getUser(), ctx.getContainer(), loader, context, null);
                                    log.info("Imported a total of " + count + " rows into : " + tableName);
                                    if (context.getErrors().hasErrors())
                                    {
                                        for (ValidationException error : context.getErrors().getRowErrors())
                                            log.error(error.getMessage());
                                    }
                                }
                                else
                                    log.error("Unable to import TSV data for " + dataFileName + ". Could not find query update service for table " + tableName + ".");
                            }
                        }
                    }
                    else
                    {
                        log.error("Failed to find table '" + schemaName + "." + tableName + "' to import data file: " + dataFileName);
                    }
                }
                else
                {
                    log.error("Unable to import TSV data for table " + tableName + ". File not found.");
                }
            }
        }
        else
        {
            log.error("Could not find " + schemaName + " schema.");
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
            return new SampleTypeAndDataClassFolderImporter();
        }

        @Override
        public int getPriority()
        {
            // make sure this importer runs before the FolderXarImporter (i.e. "Experiments and runs")
            return 65;
        }
    }
}
