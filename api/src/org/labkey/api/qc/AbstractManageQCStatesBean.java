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

public class AbstractManageQCStatesBean
{
    private String _returnUrl;
    protected QCStateHandler _qcStateHandler;
    protected AbstractManageQCStatesAction _manageAction;
    protected Class<? extends AbstractDeleteQCStateAction> _deleteAction;
    protected String _noun;
    protected String _dataNoun;

    public AbstractManageQCStatesBean(String returnUrl)
    {
        _returnUrl = returnUrl;
    }

    public String getReturnUrl()
    {
        return _returnUrl;
    }

    public QCStateHandler getQCStateHandler()
    {
        return _qcStateHandler;
    }

    public AbstractManageQCStatesAction getManageAction()
    {
        return _manageAction;
    }

    public Class<? extends AbstractDeleteQCStateAction> getDeleteAction()
    {
        return _deleteAction;
    }

    public String getNoun()
    {
        return _noun;
    }

    public String getDataNoun() { return _dataNoun; }
}
