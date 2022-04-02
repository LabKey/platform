package org.labkey.study.qc;

import org.labkey.api.admin.ImportExportContext;
import org.labkey.api.data.Container;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.DataStateManager;
import org.labkey.api.qc.export.DataStateImportExportHelper;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.qcStates.StudyqcDocument;

public class StudyQCImportExportHelper implements DataStateImportExportHelper
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
    public void write(Container container, ImportExportContext<FolderDocument.Folder> ctx, StudyqcDocument.Studyqc qcXml)
    {
        StudyImpl study = (StudyImpl)StudyService.get().getStudy(container);

        qcXml.setShowPrivateDataByDefault(study.isShowPrivateDataByDefault());
        qcXml.setBlankQCStatePublic(study.isBlankQCStatePublic());
        qcXml.setRequireCommentOnQCStateChange(false);

        // set the default states for each import type
        DataState pipelineImportState = getQCStateFromRowId(ctx.getContainer(), study.getDefaultPipelineQCState());
        if (pipelineImportState != null)
            qcXml.setPipelineImportDefault(pipelineImportState.getLabel());

        DataState assayCopyState = getQCStateFromRowId(ctx.getContainer(), study.getDefaultPublishDataQCState());
        if (assayCopyState != null)
            qcXml.setAssayDataDefault(assayCopyState.getLabel());

        DataState datasetInsertState = getQCStateFromRowId(ctx.getContainer(), study.getDefaultDirectEntryQCState());
        if (datasetInsertState != null)
            qcXml.setInsertUpdateDefault(datasetInsertState.getLabel());
    }

    private DataState getQCStateFromRowId(Container container, Integer rowId)
    {
        if (rowId != null)
            return DataStateManager.getInstance().getStateForRowId(container, rowId);

        return null;
    }

    @Override
    public boolean isDataStateInUse(Container container, DataState state)
    {
        StudyQCStateHandler handler = new StudyQCStateHandler();

        return handler.isStateInUse(container, state);
    }

    @Override
    public DataState insertDataState(User user, DataState state)
    {
        return StudyManager.getInstance().insertQCState(user, state);
    }

    @Override
    public DataState updateDataState(User user, DataState state)
    {
        return DataStateManager.getInstance().updateState(user, state);
    }

    @Override
    public void setDefaultPublishedDataQCState(Container container, User user, Integer stateId)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (study != null)
        {
            study = study.createMutable();
            study.setDefaultPublishDataQCState(stateId);
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

    @Override
    public void setRequireCommentOnQCStateChange(Container container, User user, boolean requireCommentOnQCStateChange)
    {

    }
}
