package org.labkey.study.qc;

import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.qc.QCState;
import org.labkey.api.qc.QCStateManager;
import org.labkey.api.qc.export.QCStateImportExportHelper;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.qcStates.StudyqcDocument;

public class StudyQCImportExportHelper implements QCStateImportExportHelper
{
    @Override
    public boolean matches(Container container)
    {
        Study study = StudyService.get().getStudy(container);
        return study != null;
    }

    @Override
    public int getPriority()
    {
        return 5;
    }

    @Override
    public void write(Container container, ImportContext<FolderDocument.Folder> ctx, StudyqcDocument.Studyqc qcXml)
    {
        StudyImpl study = (StudyImpl)StudyService.get().getStudy(container);

        qcXml.setShowPrivateDataByDefault(study.isShowPrivateDataByDefault());
        qcXml.setBlankQCStatePublic(study.isBlankQCStatePublic());

        // set the default states for each import type
        QCState pipelineImportState = getQCStateFromRowId(ctx.getContainer(), study.getDefaultPipelineQCState());
        if (pipelineImportState != null)
            qcXml.setPipelineImportDefault(pipelineImportState.getLabel());

        QCState assayCopyState = getQCStateFromRowId(ctx.getContainer(), study.getDefaultAssayQCState());
        if (assayCopyState != null)
            qcXml.setAssayDataDefault(assayCopyState.getLabel());

        QCState datasetInsertState = getQCStateFromRowId(ctx.getContainer(), study.getDefaultDirectEntryQCState());
        if (datasetInsertState != null)
            qcXml.setInsertUpdateDefault(datasetInsertState.getLabel());
    }

    private QCState getQCStateFromRowId(Container container, Integer rowId)
    {
        if (rowId != null)
            return QCStateManager.getInstance().getQCStateForRowId(container, rowId);

        return null;
    }

    @Override
    public boolean isQCStateInUse(Container container, QCState state)
    {
        StudyQCStateHandler handler = new StudyQCStateHandler();

        return handler.isQCStateInUse(container, state);
    }

    @Override
    public QCState insertQCState(User user, QCState state)
    {
        return StudyManager.getInstance().insertQCState(user, state);
    }

    @Override
    public void setDefaultAssayQCState(Container container, User user, Integer stateId)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (study != null)
        {
            study = study.createMutable();
            study.setDefaultAssayQCState(stateId);
            StudyManager.getInstance().updateStudy(user, study);
        }
    }

    @Override
    public void setDefaultPipelineQCState(Container container, User user, Integer stateId)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (study != null)
        {
            study = study.createMutable();
            study.setDefaultPipelineQCState(stateId);
            StudyManager.getInstance().updateStudy(user, study);
        }
    }

    @Override
    public void setDefaultDirectEntryQCState(Container container, User user, Integer stateId)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (study != null)
        {
            study = study.createMutable();
            study.setDefaultDirectEntryQCState(stateId);
            StudyManager.getInstance().updateStudy(user, study);
        }
    }

    @Override
    public void setShowPrivateDataByDefault(Container container, User user, boolean showPrivate)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (study != null)
        {
            study = study.createMutable();
            study.setShowPrivateDataByDefault(showPrivate);
            StudyManager.getInstance().updateStudy(user, study);
        }
    }

    @Override
    public void setBlankQCStatePublic(Container container, User user, boolean isPublic)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (study != null)
        {
            study = study.createMutable();
            study.setBlankQCStatePublic(isPublic);
            StudyManager.getInstance().updateStudy(user, study);
        }
    }
}
