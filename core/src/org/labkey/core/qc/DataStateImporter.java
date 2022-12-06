package org.labkey.core.qc;

import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.qc.export.AbstractDataStateImporter;
import org.labkey.api.qc.export.DataStateImportExportHelper;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument.Folder;
import org.labkey.study.xml.qcStates.StudyqcDocument;

import java.util.Collection;
import java.util.Collections;

public class DataStateImporter extends AbstractDataStateImporter implements FolderImporter
{
    public static final String QC_STATE_SETTINGS = "QC State Settings";

    @Override
    public String getDataType()
    {
        return QC_STATE_SETTINGS;
    }

    @Override
    public String getDescription()
    {
        return "QC States Importer";
    }

    @Override
    public void process(@Nullable PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        if (isValidForImportArchive(ctx))
        {
            Folder.QcStates qcStates = ctx.getXml().getQcStates();
            ctx.getLogger().info("Loading QC states");
            StudyqcDocument doc = getSettingsFile(ctx, root);
            DataStateImportExportHelper helper = DataStateImportExportHelper.getProvider(ctx.getContainer());

            if (helper != null)
            {
                if (doc != null)
                {
                    importQCStates(ctx, doc, helper);
                }
                else
                {
                    helper.setShowPrivateDataByDefault(ctx.getContainer(), ctx.getUser(), qcStates.getShowPrivateDataByDefault());
                }
                ctx.getLogger().info("Done importing QC states");
            }
        }
    }

    @Nullable
    private StudyqcDocument getSettingsFile(FolderImportContext ctx, VirtualFile root) throws Exception
    {
        Folder.QcStates qcXml  = ctx.getXml().getQcStates();

        if (qcXml != null)
        {
            String fileName = qcXml.getFile();

            if (fileName != null)
            {
                XmlObject doc = root.getXmlBean(fileName);
                if (doc instanceof StudyqcDocument)
                    return (StudyqcDocument)doc;
            }
        }
        return null;
    }

    @Override
    public boolean isValidForImportArchive(FolderImportContext ctx) throws ImportException
    {
        return ctx.getXml() != null && ctx.getXml().getQcStates() != null;
    }

    public static class Factory extends AbstractFolderImportFactory
    {
        @Override
        public FolderImporter create()
        {
            return new DataStateImporter();
        }

        @Override
        public int getPriority()
        {
            // want to follow the study importer
            return 65;
        }
    }
}
