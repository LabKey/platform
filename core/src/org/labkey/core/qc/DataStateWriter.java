package org.labkey.core.qc;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.AbstractFolderContext.ExportType;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportExportContext;
import org.labkey.api.data.Container;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.DataStateManager;
import org.labkey.api.qc.SampleStatusService;
import org.labkey.api.qc.export.DataStateImportExportHelper;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.xml.qcStates.StateTypeEnum;
import org.labkey.study.xml.qcStates.StudyqcDocument;

import java.util.List;

public class DataStateWriter extends BaseFolderWriter
{
    public static final String QC_STATE_SETTINGS = "QC State Settings";
    public static final String DATA_STATE_SETTINGS = "Sample Status and QC State Settings";
    private static final String DEFAULT_SETTINGS_FILE = "data_states.xml";

    @Override
    public boolean show(Container c)
    {
        DataStateImportExportHelper helper = getHelper(c);
        return helper != null;
    }

    @Override
    public String getDataType()
    {
        if (SampleStatusService.get().supportsSampleStatus())
            return DATA_STATE_SETTINGS;
        return QC_STATE_SETTINGS;
    }

    @Override
    public boolean selectedByDefault(ExportType type)
    {
        return ExportType.ALL == type || ExportType.STUDY == type;
    }

    @Override
    public void write(Container container, ImportExportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
    {
        DataStateImportExportHelper helper = getHelper(container);

        if (helper != null)
        {
            List<DataState> qcStates = DataStateManager.getInstance().getStates(ctx.getContainer());

            FolderDocument.Folder.QcStates qcStatesXml = ctx.getXml().addNewQcStates();
            StudyqcDocument doc = StudyqcDocument.Factory.newInstance();
            StudyqcDocument.Studyqc qcXml = doc.addNewStudyqc();

            if (!qcStates.isEmpty())
            {
                StudyqcDocument.Studyqc.Qcstates states = qcXml.addNewQcstates();
                for (DataState qc : qcStates)
                {
                    StudyqcDocument.Studyqc.Qcstates.Qcstate state = states.addNewQcstate();

                    state.setName(qc.getLabel());
                    state.setDescription(qc.getDescription());
                    state.setPublic(qc.isPublicData());
                    if (qc.getStateType() != null)
                        state.setType(StateTypeEnum.Enum.forString(qc.getStateType()));
                }
            }
            helper.write(container, ctx, qcXml);

            qcStatesXml.setFile(DEFAULT_SETTINGS_FILE);
            vf.saveXmlBean(DEFAULT_SETTINGS_FILE, doc);
        }
    }

    @Nullable
    private DataStateImportExportHelper getHelper(Container c)
    {
        return DataStateImportExportHelper.getProvider(c);
    }

    public static class Factory implements FolderWriterFactory
    {
        @Override
        public FolderWriter create()
        {
            return new DataStateWriter();
        }
    }
}
