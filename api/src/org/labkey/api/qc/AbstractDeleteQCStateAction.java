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
public abstract class AbstractDeleteQCStateAction extends FormHandlerAction<DeleteQCStateForm>
{
    protected static QCStateHandler _qcStateHandler;
    public abstract QCStateHandler getQCStateHandler();
    @Override
    public abstract ActionURL getSuccessURL(DeleteQCStateForm form);

    @Override
    public void validateCommand(DeleteQCStateForm target, Errors errors)
    {
    }

    @Override
    public boolean handlePost(DeleteQCStateForm form, BindException errors) throws Exception
    {
        if (form.isAll())
        {
            for (QCState state : QCStateManager.getInstance().getQCStates(getContainer()))
            {
                if (!getQCStateHandler().isQCStateInUse(getContainer(), state))
                    QCStateManager.getInstance().deleteQCState(state);
            }
        }
        else
        {
            QCState state = QCStateManager.getInstance().getQCStateForRowId(getContainer(), form.getId());
            if (state != null)
                QCStateManager.getInstance().deleteQCState(state);
        }
        return true;
    }
}
