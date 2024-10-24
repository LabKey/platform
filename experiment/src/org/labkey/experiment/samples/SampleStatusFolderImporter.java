package org.labkey.experiment.samples;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.XarReader;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.labkey.experiment.samples.SampleTypeFolderWriter.DEFAULT_DIRECTORY;
import static org.labkey.experiment.samples.SampleTypeFolderWriter.XAR_TYPES_NAME;
import static org.labkey.experiment.samples.SampleTypeFolderWriter.XAR_TYPES_XML_NAME;

public class SampleStatusFolderImporter extends SampleTypeFolderImporter
{
    private SampleStatusFolderImporter()
    {
    }

    @Override
    public String getDataType()
    {
        return "Sample Status Data";
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
            Map<String, String> sampleStatusDataFiles = new HashMap<>();
            Logger log = ctx.getLogger();

            if (null != job)
                job.setStatus("IMPORT " + getDescription());
            log.info("Starting Sample Status Data import");

            for (String file: xarDir.list())
            {
                if (file.equalsIgnoreCase(XAR_TYPES_NAME) || file.equalsIgnoreCase(XAR_TYPES_XML_NAME))
                {
                    if (typesXarFile == null)
                        typesXarFile = xarDirPath.resolve(file);
                    else
                        log.error("More than one types XAR file found in the sample type directory: ", file);
                }
                else if (file.toLowerCase().endsWith(".tsv"))
                {
                    if (file.startsWith(SampleTypeFolderWriter.SAMPLE_STATUS_PREFIX))
                    {
                        sampleStatusDataFiles.put(FileUtil.getBaseName(file.substring(SampleTypeFolderWriter.SAMPLE_STATUS_PREFIX.length())), file);
                    }
                }
            }

            try (DbScope.Transaction transaction = ExperimentService.get().getSchema().getScope().ensureTransaction())
            {
                if (typesXarFile != null)
                {
                    XarReader typesReader = getXarReader(job, ctx, root, typesXarFile);
                    XarContext xarContext = typesReader.getXarSource().getXarContext();
                    List<String> sampleTypeNames = typesReader.getSampleTypeNames();
                    List<ExpSampleType> sampleTypes = SampleTypeService.get().getSampleTypes(ctx.getContainer(), ctx.getUser(), false)
                            .stream().filter(sampleType -> sampleTypeNames.contains(sampleType.getName())).collect(Collectors.toList());

                    // process any sample status data files
                    importTsvData(ctx, xarContext, SamplesSchema.SCHEMA_NAME, sampleTypes, sampleStatusDataFiles, xarDir, false, true);
                }
                else
                    log.info("No sample types XAR file to process.");

                transaction.commit();
                log.info("Finished importing Sample Status Data");
            }
        }
    }

    public static class Factory implements FolderImporterFactory
    {
        @Override
        public FolderImporter create()
        {
            return new SampleStatusFolderImporter();
        }

        @Override
        public int getPriority()
        {
            // make sure this importer runs after everything that imports data related to samples
            return 85;
        }
    }
}
