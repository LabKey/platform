package org.labkey.study.qc;

import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.qc.QCState;
import org.labkey.api.qc.QCStateHandler;
import org.labkey.api.qc.QCStateManager;
import org.labkey.api.security.User;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.util.List;

public class StudyQCStateHandler implements QCStateHandler<StudyController.ManageQCStatesForm>
{
    protected List<QCState> _states = null;

    @Override
    public List<QCState> getQCStates(Container container)
    {
        StudyImpl study = StudyController.getStudyThrowIfNull(container);

        if (_states == null)
            _states = QCStateManager.getInstance().getQCStates(study.getContainer());
        return _states;
    }

    @Override
    public boolean isQCStateInUse(Container container, QCState state)
    {
        StudyImpl study = StudyController.getStudyThrowIfNull(container);

        if (StudyManager.safeIntegersEqual(study.getDefaultAssayQCState(), state.getRowId()) ||
                StudyManager.safeIntegersEqual(study.getDefaultDirectEntryQCState(), state.getRowId()) ||
                StudyManager.safeIntegersEqual(study.getDefaultPipelineQCState(), state.getRowId()))
        {
            return true;
        }
        SQLFragment f = new SQLFragment();
        f.append("SELECT * FROM ").append(
                StudySchema.getInstance().getTableInfoStudyData(study, null).getFromSQL("SD")).append(
                " WHERE QCState = ? AND Container = ?");
        f.add(state.getRowId());
        f.add(state.getContainer());

        return new SqlSelector(StudySchema.getInstance().getSchema(), f).exists();
    }

    @Override
    public void updateQcState(Container container, StudyController.ManageQCStatesForm form, User user)
    {
        StudyImpl study = StudyController.getStudyThrowIfNull(container);

        if (!QCStateHandler.nullSafeEqual(study.getDefaultAssayQCState(), form.getDefaultAssayQCState()) ||
                !QCStateHandler.nullSafeEqual(study.getDefaultPipelineQCState(), form.getDefaultPipelineQCState()) ||
                !QCStateHandler.nullSafeEqual(study.getDefaultDirectEntryQCState(), form.getDefaultDirectEntryQCState()) ||
                !QCStateHandler.nullSafeEqual(study.isBlankQCStatePublic(), form.isBlankQCStatePublic()) ||
                study.isShowPrivateDataByDefault() != form.isShowPrivateDataByDefault())
        {
            study = study.createMutable();
            study.setDefaultAssayQCState(form.getDefaultAssayQCState());
            study.setDefaultPipelineQCState(form.getDefaultPipelineQCState());
            study.setDefaultDirectEntryQCState(form.getDefaultDirectEntryQCState());
            study.setShowPrivateDataByDefault(form.isShowPrivateDataByDefault());
            study.setBlankQCStatePublic(form.isBlankQCStatePublic());
            StudyManager.getInstance().updateStudy(user, study);
        }
    }

    public boolean isBlankQCStatePublic(Container container)
    {
        StudyImpl study = StudyController.getStudyThrowIfNull(container);
        return study.isBlankQCStatePublic();
    }
}
