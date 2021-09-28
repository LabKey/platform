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

import org.labkey.api.view.ActionURL;

public class AbstractManageQCStatesBean
{
    private ActionURL _returnUrl;
    protected DataStateHandler _qcStateHandler;
    protected AbstractManageQCStatesAction _manageAction;
    protected Class<? extends AbstractDeleteDataStateAction> _deleteAction;
    protected String _noun;
    protected String _dataNoun;

    public AbstractManageQCStatesBean(ActionURL returnUrl)
    {
        _returnUrl = returnUrl;
    }

    public ActionURL getReturnUrl()
    {
        return _returnUrl;
    }

    public DataStateHandler getQCStateHandler()
    {
        return _qcStateHandler;
    }

    public AbstractManageQCStatesAction getManageAction()
    {
        return _manageAction;
    }

    public Class<? extends AbstractDeleteDataStateAction> getDeleteAction()
    {
        return _deleteAction;
    }

    public String getNoun()
    {
        return _noun;
    }

    public String getDataNoun() { return _dataNoun; }
}
