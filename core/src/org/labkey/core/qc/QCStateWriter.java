package org.labkey.core.qc;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.qc.QCState;
import org.labkey.api.qc.QCStateManager;
import org.labkey.api.qc.export.QCStateImportExportHelper;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.xml.qcStates.StudyqcDocument;

import java.util.List;

public class QCStateWriter extends BaseFolderWriter
{
    public static final String QC_STATE_SETTINGS = "QC State Settings";
    private static final String DEFAULT_SETTINGS_FILE = "quality_control_states.xml";

    @Override
    public boolean show(Container c)
    {
        QCStateImportExportHelper helper = getHelper(c);
        return helper != null;
    }

    @Override
    public String getDataType()
    {
        return QC_STATE_SETTINGS;
    }

    @Override
    public boolean selectedByDefault(AbstractFolderContext.ExportType type)
    {
        return AbstractFolderContext.ExportType.ALL == type || AbstractFolderContext.ExportType.STUDY == type;
    }

    @Override
    public void write(Container container, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
    {
        QCStateImportExportHelper helper = getHelper(container);

        if (helper != null)
        {
            List<QCState> qcStates = QCStateManager.getInstance().getQCStates(ctx.getContainer());

            FolderDocument.Folder.QcStates qcStatesXml = ctx.getXml().addNewQcStates();
            StudyqcDocument doc = StudyqcDocument.Factory.newInstance();
            StudyqcDocument.Studyqc qcXml = doc.addNewStudyqc();

            if (!qcStates.isEmpty())
            {
                StudyqcDocument.Studyqc.Qcstates states = qcXml.addNewQcstates();
                for (QCState qc : qcStates)
                {
                    StudyqcDocument.Studyqc.Qcstates.Qcstate state = states.addNewQcstate();

                    state.setName(qc.getLabel());
                    state.setDescription(qc.getDescription());
                    state.setPublic(qc.isPublicData());
                }
            }
            helper.write(container, ctx, qcXml);

            qcStatesXml.setFile(DEFAULT_SETTINGS_FILE);
            vf.saveXmlBean(DEFAULT_SETTINGS_FILE, doc);
        }
    }

    @Nullable
    private QCStateImportExportHelper getHelper(Container c)
    {
        QCStateImportExportHelper helper = QCStateImportExportHelper.getProvider(c);
        if (helper != null)
            return helper;
        else
            return null;
    }

    public static class Factory implements FolderWriterFactory
    {
        public FolderWriter create()
        {
            return new QCStateWriter();
        }
    }
}
