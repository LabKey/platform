/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.study.model;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiJsonForm;
import org.labkey.api.data.Container;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.view.HttpView;

import java.util.ArrayList;
import java.util.List;

public class StudyAssaySchedule implements ApiJsonForm
{
    Container _container;
    List<AssaySpecimenConfigImpl> _assays;
    String _assayPlan;

    public StudyAssaySchedule()
    {}

    public StudyAssaySchedule(Container container)
    {
        _container = container;
    }

    public void setAssays(List<AssaySpecimenConfigImpl> assays)
    {
        _assays = assays;
    }

    public List<AssaySpecimenConfigImpl> getAssays()
    {
        return _assays;
    }

    public void setAssayPlan(String assayPlan)
    {
        _assayPlan = assayPlan;
    }

    public String getAssayPlan()
    {
        return _assayPlan;
    }

    @Override
    public void bindJson(JSONObject json)
    {
        _container = HttpView.currentContext().getContainer();

        JSONArray assaysJSON = json.optJSONArray("assays");
        if (assaysJSON != null)
        {
            _assays = new ArrayList<>();
            for (JSONObject assayJSON : JsonUtil.toJSONObjectList(assaysJSON))
                _assays.add(AssaySpecimenConfigImpl.fromJSON(assayJSON, _container));
        }

        _assayPlan = json.optString("assayPlan", null);
    }
}
