/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.specimen.actions;

import org.labkey.api.data.Container;
import org.labkey.api.view.ViewContext;
import org.labkey.specimen.SpecimenRequestManager;

import java.sql.SQLException;

public class ManageRequestInputsBean
{
    private final SpecimenRequestManager.SpecimenRequestInput[] _inputs;
    private final Container _container;
    private final String _contextPath;

    public ManageRequestInputsBean(ViewContext context) throws SQLException
    {
        _container = context.getContainer();
        _inputs = SpecimenRequestManager.get().getNewSpecimenRequestInputs(_container);
        _contextPath = context.getContextPath();
    }

    public SpecimenRequestManager.SpecimenRequestInput[] getInputs()
    {
        return _inputs;
    }

    public Container getContainer()
    {
        return _container;
    }

    public String getContextPath()
    {
        return _contextPath;
    }
}
