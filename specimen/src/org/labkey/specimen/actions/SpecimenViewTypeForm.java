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

import org.labkey.api.action.QueryViewAction;
import org.labkey.specimen.query.SpecimenQueryView;

public class SpecimenViewTypeForm extends QueryViewAction.QueryExportForm
{
    public enum PARAMS
    {
        showVials,
        viewMode
    }

    private boolean _showVials;
    private SpecimenQueryView.Mode _viewMode = SpecimenQueryView.Mode.DEFAULT;

    public boolean isShowVials()
    {
        return _showVials;
    }

    public void setShowVials(boolean showVials)
    {
        _showVials = showVials;
    }

    public String getViewMode()
    {
        return _viewMode.name();
    }

    public SpecimenQueryView.Mode getViewModeEnum()
    {
        return _viewMode;
    }

    public void setViewMode(String viewMode)
    {
        if (viewMode != null)
            _viewMode = SpecimenQueryView.Mode.valueOf(viewMode);
    }
}
