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
package org.labkey.core.qc;

import org.labkey.api.assay.AssayQCService;
import org.labkey.api.data.Container;
import org.labkey.api.qc.QCState;
import org.labkey.api.qc.QCStateHandler;
import org.labkey.api.qc.QCStateManager;
import org.labkey.api.security.User;
import org.labkey.core.CoreController;

import java.util.List;

public class CoreQCStateHandler implements QCStateHandler<CoreController.ManageQCStatesForm>
{
    protected List<QCState> _states = null;

    @Override
    public boolean isBlankQCStatePublic(Container container)
    {
        return AssayQCService.getProvider().isBlankQCStatePublic(container);
    }

    public Integer getDefaultQCState(Container container)
    {
        QCState state = AssayQCService.getProvider().getDefaultDataImportState(container);
        return state != null ? state.getRowId() : null;
    }

    private void setProps(Container container, boolean isBlankQCStatePublic, Integer defaultQCState)
    {
        AssayQCService.getProvider().setIsBlankQCStatePublic(container, isBlankQCStatePublic);

        QCState state = null;
        if (defaultQCState != null)
            state = QCStateManager.getInstance().getQCStateForRowId(container, defaultQCState);

        AssayQCService.getProvider().setDefaultDataImportState(container, state);
    }

    @Override
    public List<QCState> getQCStates(Container container)
    {
        if (_states == null)
            _states = QCStateManager.getInstance().getQCStates(container);
        return _states;
    }

    @Override
    public boolean isQCStateInUse(Container container, QCState state)
    {
        return AssayQCService.getProvider().isQCStateInUse(container, state);
    }

    @Override
    public void updateQcState(Container container, CoreController.ManageQCStatesForm form, User user)
    {
        if (!QCStateHandler.nullSafeEqual(getDefaultQCState(container), form.getDefaultQCState()) ||
                isBlankQCStatePublic(container) != form.isBlankQCStatePublic())
        {
            setProps(container, form.isBlankQCStatePublic(), form.getDefaultQCState());
        }
    }
}
