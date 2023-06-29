package org.labkey.experiment.samples;

import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.XarReader;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.labkey.experiment.samples.SampleTypeFolderWriter.DEFAULT_DIRECTORY;
import static org.labkey.experiment.samples.SampleTypeFolderWriter.XAR_TYPES_NAME;
import static org.labkey.experiment.samples.SampleTypeFolderWriter.XAR_TYPES_XML_NAME;

public class SampleTypeFolderImporter extends AbstractExpFolderImporter
{
    protected SampleTypeFolderImporter()
    {
    }

    @Override
    public String getDataType()
    {
        return FolderArchiveDataTypes.SAMPLE_TYPE_DATA;
    }

    @Override
    public String getDescription()
    {
        return "Sample Type Importer";
    }

    @Override
    protected VirtualFile getXarDir(VirtualFile root)
    {
        return root.getDir(DEFAULT_DIRECTORY);
    }

    @Override
    protected boolean isXarTypesFile(String fileName)
    {
        return  fileName.equalsIgnoreCase(XAR_TYPES_NAME) || fileName.equalsIgnoreCase(XAR_TYPES_XML_NAME);
    }

    @Override
    protected boolean excludeTable(String tableName)
    {
        return SampleTypeFolderWriter.EXCLUDED_TYPES.contains(tableName);
    }

    @Override
    protected void importDataFiles(FolderImportContext ctx, VirtualFile xarDir, XarReader typesReader, XarContext xarContext) throws IOException, SQLException
    {
        // the legacy sample type folder export also included data classes, check for any data class tsv exports
        DataClassFolderImporter.get().importDataFiles(ctx, xarDir, typesReader, xarContext);

        Map<String, String> sampleTypeDataFiles = new HashMap<>();

        for (String file: xarDir.list())
        {
            if (file.toLowerCase().endsWith(".tsv") && file.startsWith(SampleTypeFolderWriter.SAMPLE_TYPE_PREFIX))
            {
                sampleTypeDataFiles.put(FileUtil.getBaseName(file.substring(SampleTypeFolderWriter.SAMPLE_TYPE_PREFIX.length())), file);
            }
        }

        if (!sampleTypeDataFiles.isEmpty())
        {
            importTsvData(ctx, xarContext, SamplesSchema.SCHEMA_NAME, typesReader.getSampleTypes(), sampleTypeDataFiles, xarDir, true, false);
        }
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
            // make sure this importer runs before the FolderXarImporter (i.e. "Experiments and runs")
            return 65;
        }
    }
}
