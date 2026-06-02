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

import org.labkey.api.view.ViewContext;
import org.labkey.specimen.SpecimenRequestManager;
import org.labkey.specimen.SpecimenRequestStatus;
import org.labkey.specimen.query.SpecimenRequestQueryView;

import java.util.Collection;

public class ViewRequestsHeaderBean
{
    public static final String PARAM_STATUSLABEL = "SpecimenRequest.Status/Label~eq";
    public static final String PARAM_CREATEDBY = "SpecimenRequest.CreatedBy/DisplayName~eq";

    private final SpecimenRequestQueryView _view;
    private final ViewContext _context;

    public ViewRequestsHeaderBean(ViewContext context, SpecimenRequestQueryView view)
    {
        _view = view;
        _context = context;
    }

    public SpecimenRequestQueryView getView()
    {
        return _view;
    }

    public Collection<SpecimenRequestStatus> getStauses()
    {
        return SpecimenRequestManager.get().getRequestStatuses(_context.getContainer(), _context.getUser());
    }

    public boolean isFilteredStatus(SpecimenRequestStatus status)
    {
        return status.getLabel().equals(_context.getActionURL().getParameter(PARAM_STATUSLABEL));
    }
}
