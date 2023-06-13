package org.labkey.experiment.samples;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.FileXarSource;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
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
import org.labkey.experiment.xar.FolderXarImporterFactory;
import org.labkey.experiment.xar.XarImportContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.labkey.api.admin.FolderImportContext.IS_NEW_FOLDER_IMPORT_KEY;
import static org.labkey.experiment.samples.SampleTypeAndDataClassFolderWriter.DEFAULT_DIRECTORY;
import static org.labkey.experiment.samples.SampleTypeAndDataClassFolderWriter.EXCLUDED_TYPES;
import static org.labkey.experiment.samples.SampleTypeAndDataClassFolderWriter.XAR_RUNS_NAME;
import static org.labkey.experiment.samples.SampleTypeAndDataClassFolderWriter.XAR_RUNS_XML_NAME;
import static org.labkey.experiment.samples.SampleTypeAndDataClassFolderWriter.XAR_TYPES_NAME;
import static org.labkey.experiment.samples.SampleTypeAndDataClassFolderWriter.XAR_TYPES_XML_NAME;

public class SampleTypeAndDataClassFolderImporter implements FolderImporter
{
    // registry data classes have to be imported in a particular order because they reference each other
    private static final List<String> REGISTRY_CLASS_ORDER = List.of(
            "ProtSequence",
            "NucSequence",
            "Molecule",
            "Vector",
            "Construct",
            "CellLine",
            "ExpressionSystem",
            "Compound"
    );

    protected SampleTypeAndDataClassFolderImporter()
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
    public void process(@Nullable PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception
    {
        VirtualFile xarDir = root.getDir(DEFAULT_DIRECTORY);

        if (xarDir != null)
        {
            // #44384 Generate a relative Path object for the folder's VirtualFile
            Path xarDirPath = Path.of(xarDir.getLocation());
            Path typesXarFile = null;
            Path runsXarFile = null;
            Map<String, String> sampleTypeDataFiles = new HashMap<>();
            Map<String, String> dataClassDataFiles = new HashMap<>();
            Logger log = ctx.getLogger();

            if (null != job)
                job.setStatus("IMPORT " + getDescription());
            log.info("Starting Sample Type and Data Class import");

            for (String file: xarDir.list())
            {
                if (file.equalsIgnoreCase(XAR_TYPES_NAME) || file.equalsIgnoreCase(XAR_TYPES_XML_NAME))
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
                    Path logFile = null;
                    if (Files.exists(typesXarFile))
                        logFile = CompressedInputStreamXarSource.getLogFileFor(typesXarFile);
                    XarReader typesReader = getXarReader(job, ctx, root, typesXarFile);
                    XarContext xarContext = typesReader.getXarSource().getXarContext();

                    typesReader.setStrictValidateExistingSampleType(xarCtx.isStrictValidateExistingSampleType());
                    typesReader.parseAndLoad(false, ctx.getAuditBehaviorType());

                    // Import data classes first because the media sample type (RawMaterials) has a lookup to the ingredient data class.
                    // Registry files need to imported in a particular order because some files rely on data from other files.
                    ArrayList<ExpObject> sortedDataClasses = new ArrayList<>(typesReader.getDataClasses());
                    sortedDataClasses.sort(Comparator.comparingInt(dc -> REGISTRY_CLASS_ORDER.indexOf(dc.getName())));
                    importTsvData(ctx, xarContext, ExpSchema.SCHEMA_EXP_DATA.toString(), sortedDataClasses, dataClassDataFiles, xarDir, true, false);
                    importTsvData(ctx, xarContext, SamplesSchema.SCHEMA_NAME, typesReader.getSampleTypes(), sampleTypeDataFiles, xarDir, true, false);

                    // handle wiring up any derivation runs
                    if (runsXarFile != null)
                    {
                        XarSource runsXarSource;
                        if (runsXarFile.getFileName().toString().toLowerCase().endsWith(".xar.xml"))
                            runsXarSource = new FileXarSource(runsXarFile, job, ctx.getContainer());
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
                }
                else
                    log.info("No types XAR file to process.");

                transaction.commit();
                log.info("Finished importing Sample Types and Data Classes");
            }
        }
    }

    protected XarReader getXarReader(@Nullable PipelineJob job, FolderImportContext ctx, VirtualFile root, Path typesXarFile) throws IOException, ExperimentException
    {
        VirtualFile xarDir = root.getDir(DEFAULT_DIRECTORY);
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
            typesXarSource = new FileXarSource(typesXarFile, job, ctx.getContainer());
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

    protected void importTsvData(FolderImportContext ctx, XarContext xarContext, String schemaName, List<? extends ExpObject> expObjects, Map<String, String> dataFileMap, VirtualFile dir, boolean fileRequired, boolean isUpdate) throws IOException, SQLException
    {
        Logger log = ctx.getLogger();
        UserSchema userSchema = QueryService.get().getUserSchema(ctx.getUser(), ctx.getContainer(), schemaName);
        if (userSchema != null)
        {
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
                                    options.put(ExperimentService.QueryOptions.UseLsidForUpdate, true);
                                    context.setConfigParameters(options);

                                    Map<String, Object> extraContext = null;
                                    if (ctx.isNewFolderImport())
                                    {
                                        extraContext = new HashMap<>();
                                        extraContext.put(IS_NEW_FOLDER_IMPORT_KEY, true);
                                    }

                                    DataIterator data = new ResolveLsidDataIterator(loader.getDataIterator(context), xarContext, expObject instanceof ExpDataClass ? "DataClass" : "Material");
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
                else if (fileRequired && !EXCLUDED_TYPES.contains(tableName))
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

    
    private class ResolveLsidDataIterator extends WrapperDataIterator
    {
        final XarContext _xarContext;
        final String _baseType;
        int _lsidColumnIndex = -1;
        Supplier<Object> _lsidTemplateSupplier = null;
        Supplier<Object> _lsidResolvedSupplier = null;

        ResolveLsidDataIterator(DataIterator delegate, XarContext xarContext, String baseType)
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
                    break;
                }
            }
        }

        @Override
        public Supplier<Object> getSupplier(int i)
        {
            return i==_lsidColumnIndex ? _lsidResolvedSupplier : _delegate.getSupplier(i);
        }

        @Override
        public Object get(int i)
        {
            return i==_lsidColumnIndex ? _lsidResolvedSupplier.get() : _delegate.get(i);
        }
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
