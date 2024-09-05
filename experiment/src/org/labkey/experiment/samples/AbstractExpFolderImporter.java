package org.labkey.experiment.samples;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.FileXarSource;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.pipeline.PipelineJob;
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
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.CompressedInputStreamXarSource;
import org.labkey.experiment.XarReader;
import org.labkey.experiment.api.SampleTypeServiceImpl;
import org.labkey.experiment.xar.FolderXarImporterFactory;
import org.labkey.experiment.xar.XarImportContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.labkey.api.admin.FolderImportContext.IS_NEW_FOLDER_IMPORT_KEY;
import static org.labkey.api.dataiterator.SimpleTranslator.getContainerFileRootPath;
import static org.labkey.api.dataiterator.SimpleTranslator.getFileRootSubstitutedFilePath;
import static org.labkey.api.exp.XarContext.XAR_JOB_ID_NAME;
import static org.labkey.experiment.api.SampleTypeServiceImpl.SampleChangeType.insert;
import static org.labkey.experiment.api.SampleTypeServiceImpl.SampleChangeType.update;
import static org.labkey.experiment.samples.AbstractExpFolderWriter.XAR_RUNS_NAME;
import static org.labkey.experiment.samples.AbstractExpFolderWriter.XAR_RUNS_XML_NAME;

public abstract class AbstractExpFolderImporter implements FolderImporter
{
    protected abstract VirtualFile getXarDir(VirtualFile root);
    protected abstract void importDataFiles(FolderImportContext ctx, VirtualFile xarDir, XarReader typesReader, XarContext xarContext) throws IOException, SQLException;
    protected abstract boolean isXarTypesFile(String fileName);
    protected abstract boolean excludeTable(String tableName);

    @Override
    public String getDescription()
    {
        return getDataType().toLowerCase();
    }

    @Override
    public void process(@Nullable PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception
    {
        VirtualFile xarDir = getXarDir(root);

        if (xarDir != null)
        {
            // #44384 Generate a relative Path object for the folder's VirtualFile
            Path xarDirPath = Path.of(xarDir.getLocation());
            Path typesXarFile = null;
            Path runsXarFile = null;
            Logger log = ctx.getLogger();

            if (null != job)
                job.setStatus("IMPORT " + getDescription());
            log.info("Starting " + getDescription());

            for (String file: xarDir.list())
            {
                if (isXarTypesFile(file))
                {
                    if (typesXarFile == null)
                        typesXarFile = xarDirPath.resolve(file);
                    else
                        log.error("More than one types XAR file found in the sample type directory: ", file);
                }
                else if (file.equalsIgnoreCase(XAR_RUNS_NAME) || file.equalsIgnoreCase(XAR_RUNS_XML_NAME))
                {
                    if (runsXarFile == null)
                        runsXarFile = xarDirPath.resolve(file);
                    else
                        log.error("More than one runs XAR file found in the sample type directory: ", file);
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
                    Path logFile = null;
                    if (Files.exists(typesXarFile))
                        logFile = CompressedInputStreamXarSource.getLogFileFor(typesXarFile);
                    XarReader typesReader = getXarReader(job, ctx, root, typesXarFile);
                    XarContext xarContext = typesReader.getXarSource().getXarContext();

                    typesReader.setStrictValidateExistingSampleType(xarCtx.isStrictValidateExistingSampleType());
                    typesReader.parseAndLoad(false, ctx.getAuditBehaviorType());

                    // import any exported tsv data files
                    importDataFiles(ctx, xarDir, typesReader, xarContext);

                    // handle wiring up any derivation runs
                    if (runsXarFile != null)
                    {
                        XarSource runsXarSource;
                        if (runsXarFile.getFileName().toString().toLowerCase().endsWith(".xar.xml"))
                            runsXarSource = new FileXarSource(runsXarFile, job, ctx.getContainer(), ctx.getXarJobIdContext());
                        else
                            runsXarSource = new CompressedInputStreamXarSource(xarDir.getInputStream(runsXarFile.getFileName().toString()), runsXarFile, logFile, job, ctx.getUser(), ctx.getContainer(), ctx.getXarJobIdContext());
                        try
                        {
                            runsXarSource.init();
                        }
                        catch (Exception e)
                        {
                            log.error("Failed to initialize runs XAR source", e);
                            throw(e);
                        }
                        log.info("Importing the runs XAR file: " + runsXarFile.getFileName().toString());
                        XarReader runsReader = new FolderXarImporterFactory.FolderExportXarReader(runsXarSource, job);
                        runsReader.setStrictValidateExistingSampleType(xarCtx.isStrictValidateExistingSampleType());
                        runsReader.parseAndLoad(false, ctx.getAuditBehaviorType());
                    }
                    // handle aliquot rollup calculation for all sample types, this is a noop for the data class importer
                    for (ExpSampleType sampleType : typesReader.getSampleTypes())
                    {
                        int count = SampleTypeService.get().recomputeSampleTypeRollup(sampleType, ctx.getContainer());
                        SampleTypeServiceImpl.SampleChangeType reason = count > 0 ? update : insert;
                        SampleTypeServiceImpl.get().refreshSampleTypeMaterializedView(sampleType, reason);
                    }
                }
                else
                    log.info("No types XAR file to process.");

                transaction.commit();
                log.info("Finished " + getDescription());
            }
        }
    }

    protected XarReader getXarReader(@Nullable PipelineJob job, FolderImportContext ctx, VirtualFile root, Path typesXarFile) throws IOException, ExperimentException
    {
        VirtualFile xarDir = getXarDir(root);
        Logger log = ctx.getLogger();

        Path logFile = null;
        // we don't need the log file in cases where the xarFile is a virtual file and not in the file system
        if (Files.exists(typesXarFile))
            logFile = CompressedInputStreamXarSource.getLogFileFor(typesXarFile);

        if (job == null)
        {
            // need to fake up a job for the XarReader
            job = getDummyPipelineJob(ctx);
        }

        XarSource typesXarSource;

        if (typesXarFile.getFileName().toString().toLowerCase().endsWith(".xar.xml"))
            typesXarSource = new FileXarSource(typesXarFile, job, ctx.getContainer(), ctx.getXarJobIdContext());
        else
            typesXarSource = new CompressedInputStreamXarSource(xarDir.getInputStream(typesXarFile.getFileName().toString()), typesXarFile, logFile, job, ctx.getUser(), ctx.getContainer(), ctx.getXarJobIdContext());
        try
        {
            typesXarSource.init();
        }
        catch (Exception e)
        {
            log.error("Failed to initialize types XAR source", e);
            throw(e);
        }
        return new FolderXarImporterFactory.FolderExportXarReader(typesXarSource, job);
    }

    protected PipelineJob getDummyPipelineJob(FolderImportContext ctx)
    {
        return new PipelineJob()
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

    protected void importTsvData(FolderImportContext ctx, XarContext xarContext, String schemaName, List<? extends ExpObject> expObjects,
                                 Map<String, String> dataFileMap, VirtualFile dir, boolean fileRequired, boolean isUpdate) throws IOException, SQLException
    {
        Logger log = ctx.getLogger();
        UserSchema userSchema = QueryService.get().getUserSchema(ctx.getUser(), ctx.getContainer(), schemaName);
        if (userSchema != null)
        {
            Map<String, String> xarJobIdContext = ctx.getXarJobIdContext();
            if (xarJobIdContext != null)
                xarContext.addSubstitution(XAR_JOB_ID_NAME, xarJobIdContext.get(XAR_JOB_ID_NAME));

            for (ExpObject expObject : expObjects)
            {
                String tableName = expObject.getName();
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
                                    context.putConfigParameter(QueryUpdateService.ConfigParameters.SkipInsertOptionValidation, Boolean.TRUE); // allow merge during folder import, needed for eval data loading
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
                                    options.put(SampleTypeService.ConfigParameters.DeferAliquotRuns, true);
                                    if (isUpdate)
                                        options.put(QueryUpdateService.ConfigParameters.SkipRequiredFieldValidation, true);
                                    options.put(ExperimentService.QueryOptions.UseLsidForUpdate, !isUpdate);
                                    context.setConfigParameters(options);

                                    Map<String, Object> extraContext = null;
                                    if (ctx.isNewFolderImport())
                                    {
                                        extraContext = new HashMap<>();
                                        extraContext.put(IS_NEW_FOLDER_IMPORT_KEY, true);
                                    }

                                    DataIterator data = new ResolveLsidAndFileLinkDataIterator(loader.getDataIterator(context), xarContext, expObject instanceof ExpDataClass ? "DataClass" : "Material", tinfo);
                                    int count = qus.loadRows(ctx.getUser(), ctx.getContainer(), data, context, extraContext);
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
                else if (fileRequired && !excludeTable(tableName))
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

    private class ResolveLsidAndFileLinkDataIterator extends WrapperDataIterator
    {
        final XarContext _xarContext;
        final String _baseType;
        int _lsidColumnIndex = -1;
        Supplier<Object> _lsidTemplateSupplier = null;
        Supplier<Object> _lsidResolvedSupplier = null;
        final String _fileRootPath;
        Set<Integer> _fileColumnIndexes = new HashSet<>();

        ResolveLsidAndFileLinkDataIterator(DataIterator delegate, XarContext xarContext, String baseType, @NotNull TableInfo tinfo)
        {
            super(delegate);
            _xarContext = xarContext;
            _baseType = baseType;

            for (int i=1 ; i<=delegate.getColumnCount() ; i++)
            {
                if ("lsid".equalsIgnoreCase(delegate.getColumnInfo(i).getName()))
                {
                    _lsidColumnIndex = i;
                    _lsidTemplateSupplier = delegate.getSupplier(i);
                    _lsidResolvedSupplier = () -> {
                        try
                        {
                            return LsidUtils.resolveLsidFromTemplate((String)_lsidTemplateSupplier.get(), _xarContext, _baseType);
                        }
                        catch (XarFormatException xfe)
                        {
                            throw UnexpectedException.wrap(xfe);
                        }
                    };
                }
                else
                {
                    ColumnInfo column = tinfo.getColumn(delegate.getColumnInfo(i).getFieldKey());
                    if (column != null && PropertyType.FILE_LINK == column.getPropertyType())
                        _fileColumnIndexes.add(i);
                }
            }


            String fileRootPath = null;
            if (!_fileColumnIndexes.isEmpty())
                fileRootPath = getContainerFileRootPath(xarContext.getContainer());
            _fileRootPath = fileRootPath;

        }

        @Override
        public Supplier<Object> getSupplier(int i)
        {
            if (i==_lsidColumnIndex)
                return _lsidResolvedSupplier;

            if (_fileColumnIndexes.contains(i))
                return () -> get(i);

            return _delegate.getSupplier(i);
        }

        @Override
        public Object get(int i)
        {
            if (i==_lsidColumnIndex)
                return _lsidResolvedSupplier.get();

            Object value = _delegate.get(i);
            if (_fileColumnIndexes.contains(i))
            {
                if (value instanceof String filePath)
                    return getFileRootSubstitutedFilePath(filePath, _fileRootPath);
            }
            return value;
        }
    }
}
