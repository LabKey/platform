/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.api.study.actions;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.ViewForm;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:02:45 PM
*/
public class ProtocolIdForm extends ViewForm
{
    private ExpProtocol _protocol;

    private Integer _rowId;
    private String _providerName;

    public String getProviderName()
    {
        return _providerName;
    }

    public void setProviderName(String providerName)
    {
        _providerName = providerName;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    @NotNull
    public ExpProtocol getProtocol(boolean validateContainer)
    {
        if (_protocol == null)
            _protocol = BaseAssayAction.getProtocol(this, validateContainer);
        return _protocol;
    }

    @NotNull
    public ExpProtocol getProtocol()
    {
        return getProtocol(true);
    }

    public AssayProvider getProvider()
    {
        return AssayService.get().getProvider(getProtocol());
    }
}

