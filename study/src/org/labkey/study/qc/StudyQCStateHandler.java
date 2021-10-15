/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.study.qc;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.DataStateHandler;
import org.labkey.api.qc.QCStateManager;
import org.labkey.api.security.User;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.util.List;
import java.util.Map;

public class StudyQCStateHandler implements DataStateHandler<StudyController.ManageQCStatesForm>
{
    protected List<DataState> _states = null;
    public static final String HANDLER_NAME = "StudyQCStateHandler";

    @Override
    public String getHandlerType()
    {
        return HANDLER_NAME;
    }

    @Override
    public List<DataState> getStates(Container container)
    {
        StudyImpl study = StudyController.getStudyThrowIfNull(container);

        if (_states == null)
            _states = QCStateManager.getInstance().getStates(study.getContainer());
        return _states;
    }

    @Override
    public boolean isStateInUse(Container container, DataState state)
    {
        StudyImpl study = StudyController.getStudy(container);
        if (study == null)
            return false;

        if (StudyManager.safeIntegersEqual(study.getDefaultPublishDataQCState(), state.getRowId()) ||
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
    public void updateState(Container container, StudyController.ManageQCStatesForm form, User user)
    {
        StudyImpl study = StudyController.getStudyThrowIfNull(container);

        if (!DataStateHandler.nullSafeEqual(study.getDefaultPublishDataQCState(), form.getDefaultPublishDataQCState()) ||
                !DataStateHandler.nullSafeEqual(study.getDefaultPipelineQCState(), form.getDefaultPipelineQCState()) ||
                !DataStateHandler.nullSafeEqual(study.getDefaultDirectEntryQCState(), form.getDefaultDirectEntryQCState()) ||
                study.isBlankQCStatePublic() != form.isBlankQCStatePublic() ||
                study.isShowPrivateDataByDefault() != form.isShowPrivateDataByDefault())
        {
            study = study.createMutable();
            study.setDefaultPublishDataQCState(form.getDefaultPublishDataQCState());
            study.setDefaultPipelineQCState(form.getDefaultPipelineQCState());
            study.setDefaultDirectEntryQCState(form.getDefaultDirectEntryQCState());
            study.setShowPrivateDataByDefault(form.isShowPrivateDataByDefault());
            study.setBlankQCStatePublic(form.isBlankQCStatePublic());
            StudyManager.getInstance().updateStudy(user, study);
        }
    }

    @Override
    public boolean isBlankStatePublic(Container container)
    {
        StudyImpl study = StudyController.getStudyThrowIfNull(container);
        return study.isBlankQCStatePublic();
    }

    @Override
    public @Nullable String isStateChangeAllowed(Container container, DataState state, Map<String, Object> rowUpdates)
    {
        return null;
    }
}
