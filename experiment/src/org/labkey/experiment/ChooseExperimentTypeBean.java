/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.experiment;

import org.labkey.api.view.ActionURL;
import org.labkey.api.exp.ExperimentRunType;

import java.util.Set;

/**
 * User: jeckels
 * Date: Oct 11, 2006
 */
public class ChooseExperimentTypeBean
{
    private final Set<ExperimentRunType> _types;
    private final ExperimentRunType _selectedType;
    private final ActionURL _url;

    public ChooseExperimentTypeBean(Set<ExperimentRunType> types, ExperimentRunType selectedType, ActionURL url)
    {
        _types = types;
        if (selectedType == null)
        {
            _selectedType = ExperimentRunType.ALL_RUNS_TYPE;
        }
        else
        {
            _selectedType = selectedType;
        }

        _url = url;
    }

    public Set<ExperimentRunType> getFilters()
    {
        return _types;
    }

    public ActionURL getUrl()
    {
        return _url;
    }

    public ExperimentRunType getSelectedFilter()
    {
        return _selectedType;
    }
}
