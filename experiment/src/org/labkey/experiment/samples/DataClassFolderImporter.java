package org.labkey.experiment.samples;

import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.XarReader;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.labkey.experiment.samples.DataClassFolderWriter.EXCLUDED_TYPES;
import static org.labkey.experiment.samples.DataClassFolderWriter.XAR_TYPES_NAME;
import static org.labkey.experiment.samples.DataClassFolderWriter.XAR_TYPES_XML_NAME;

public class DataClassFolderImporter extends AbstractExpFolderImporter
{
    private static final DataClassFolderImporter _instance = new DataClassFolderImporter();

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

    private DataClassFolderImporter(){}

    public static DataClassFolderImporter get()
    {
        return _instance;
    }

    public String getDataType()
    {
        return FolderArchiveDataTypes.DATA_CLASS_DATA;
    }

    @Override
    public String getDescription()
    {
        return "Data Class Importer";
    }

    @Override
    protected VirtualFile getXarDir(VirtualFile root)
    {
        return root.getDir(DataClassFolderWriter.DEFAULT_DIRECTORY);
    }

    @Override
    protected boolean isXarTypesFile(String fileName)
    {
        return  fileName.equalsIgnoreCase(XAR_TYPES_NAME) || fileName.equalsIgnoreCase(XAR_TYPES_XML_NAME);
    }

    @Override
    protected boolean excludeTable(String tableName)
    {
        return EXCLUDED_TYPES.contains(tableName);
    }

    @Override
    protected void importDataFiles(FolderImportContext ctx, VirtualFile xarDir, XarReader typesReader, XarContext xarContext) throws IOException, SQLException
    {
        Map<String, String> dataClassDataFiles = new HashMap<>();

        for (String file: xarDir.list())
        {
            if (file.toLowerCase().endsWith(".tsv") && file.startsWith(DataClassFolderWriter.DATA_CLASS_PREFIX))
            {
                dataClassDataFiles.put(FileUtil.getBaseName(file.substring(DataClassFolderWriter.DATA_CLASS_PREFIX.length())), file);
            }
        }

        if (!dataClassDataFiles.isEmpty())
        {
            ArrayList<ExpObject> sortedDataClasses = new ArrayList<>(typesReader.getDataClasses());
            sortedDataClasses.sort(Comparator.comparingInt(dc -> REGISTRY_CLASS_ORDER.indexOf(dc.getName())));

            importTsvData(ctx, xarContext, ExpSchema.SCHEMA_EXP_DATA.toString(), sortedDataClasses, dataClassDataFiles, xarDir, true, false);
        }
    }

    public static class Factory implements FolderImporterFactory
    {
        @Override
        public FolderImporter create()
        {
            return new DataClassFolderImporter();
        }

        @Override
        public int getPriority()
        {
            // Import data classes first because the media sample type (RawMaterials) has a lookup to the ingredient data class.
            return 60;
        }
    }
}
