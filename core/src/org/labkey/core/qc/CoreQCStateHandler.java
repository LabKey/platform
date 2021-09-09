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
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.DataStateHandler;
import org.labkey.api.qc.DataStateManager;
import org.labkey.api.security.User;
import org.labkey.core.CoreController;

import java.util.List;

public class CoreQCStateHandler implements DataStateHandler<CoreController.ManageQCStatesForm>
{
    protected List<DataState> _states = null;
    public static final String HANDLER_NAME = "CoreQCStateHandler";

    @Override
    public String getHandlerType()
    {
        return HANDLER_NAME;
    }

    @Override
    public boolean isBlankStatePublic(Container container)
    {
        return AssayQCService.getProvider().isBlankQCStatePublic(container);
    }

    public Integer getDefaultQCState(Container container)
    {
        DataState state = AssayQCService.getProvider().getDefaultDataImportState(container);
        return state != null ? state.getRowId() : null;
    }

    private void setProps(Container container, boolean isBlankQCStatePublic, Integer defaultQCState)
    {
        AssayQCService.getProvider().setIsBlankQCStatePublic(container, isBlankQCStatePublic);

        DataState state = null;
        if (defaultQCState != null)
            state = DataStateManager.getInstance().getStateForRowId(container, defaultQCState);

        AssayQCService.getProvider().setDefaultDataImportState(container, state);
    }

    @Override
    public List<DataState> getStates(Container container)
    {
        if (_states == null)
            _states = DataStateManager.getInstance().getStates(container);
        return _states;
    }

    @Override
    public boolean isStateInUse(Container container, DataState state)
    {
        return AssayQCService.getProvider().isQCStateInUse(container, state);
    }

    @Override
    public void updateState(Container container, CoreController.ManageQCStatesForm form, User user)
    {
        if (!DataStateHandler.nullSafeEqual(getDefaultQCState(container), form.getDefaultQCState()) ||
                isBlankStatePublic(container) != form.isBlankQCStatePublic())
        {
            setProps(container, form.isBlankQCStatePublic(), form.getDefaultQCState());
        }
    }
}
