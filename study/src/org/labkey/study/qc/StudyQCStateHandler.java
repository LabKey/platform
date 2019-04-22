package org.labkey.study.qc;

import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.qc.QCState;
import org.labkey.api.qc.QCStateHandler;
import org.labkey.api.qc.QCStateManager;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.util.List;

public class StudyQCStateHandler implements QCStateHandler
{
    protected List<QCState> _states = null;
    protected StudyImpl _study;

    public StudyQCStateHandler (StudyImpl study)
    {
        _study = study;
    }

    @Override
    public List<QCState> getQCStates()
    {
        if (_states == null)
            _states = QCStateManager.getInstance().getQCStates(_study.getContainer());
        return _states;
    }

    @Override
    public boolean isQCStateInUse(QCState state)
    {
        if (StudyManager.safeIntegersEqual(_study.getDefaultAssayQCState(), state.getRowId()) ||
                StudyManager.safeIntegersEqual(_study.getDefaultDirectEntryQCState(), state.getRowId()) ||
                StudyManager.safeIntegersEqual(_study.getDefaultPipelineQCState(), state.getRowId()))
        {
            return true;
        }
        SQLFragment f = new SQLFragment();
        f.append("SELECT * FROM ").append(
                StudySchema.getInstance().getTableInfoStudyData(_study, null).getFromSQL("SD")).append(
                " WHERE QCState = ? AND Container = ?");
        f.add(state.getRowId());
        f.add(state.getContainer());

        return new SqlSelector(StudySchema.getInstance().getSchema(), f).exists();
    }

    @Override
    public boolean isBlankQCStatePublic()
    {
        return _study.isBlankQCStatePublic();
    }

    @Override
    public Integer getDefaultPipelineQCState()
    {
        return _study.getDefaultPipelineQCState();
    }

    @Override
    public Integer getDefaultAssayQCState()
    {
        return _study.getDefaultAssayQCState();
    }

    @Override
    public Integer getDefaultDirectEntryQCState()
    {
        return _study.getDefaultDirectEntryQCState();
    }

    @Override
    public boolean isShowPrivateDataByDefault()
    {
        return _study.isShowPrivateDataByDefault();
    }
}
