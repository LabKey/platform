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
package org.labkey.api.qc;

import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.ActionURL;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

@RequiresPermission(AdminPermission.class)
public abstract class AbstractDeleteDataStateAction extends FormHandlerAction<DeleteDataStateForm>
{
    protected static DataStateHandler _dataStateHandler;
    public abstract DataStateHandler getDataStateHandler();
    @Override
    public abstract ActionURL getSuccessURL(DeleteDataStateForm form);

    @Override
    public void validateCommand(DeleteDataStateForm target, Errors errors)
    {
    }

    @Override
    public boolean handlePost(DeleteDataStateForm form, BindException errors) throws Exception
    {
        if (form.isAll())
        {
            for (Object state : _dataStateHandler.getStates(getContainer()))
            {
                if (!_dataStateHandler.isStateInUse(getContainer(), (DataState) state))
                    DataStateManager.getInstance().deleteState((DataState) state);
            }
        }
        else
        {
            DataState state = DataStateManager.getInstance().getStateForRowId(getContainer(), form.getId());
            if (state != null)
                DataStateManager.getInstance().deleteState(state);
        }
        return true;
    }
}
