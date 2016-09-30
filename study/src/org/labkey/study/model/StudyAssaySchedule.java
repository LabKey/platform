package org.labkey.study.model;

import org.json.JSONArray;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.data.Container;
import org.labkey.api.view.HttpView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StudyAssaySchedule implements CustomApiForm
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
    public void bindProperties(Map<String, Object> props)
    {
        _container = HttpView.currentContext().getContainer();

        Object assaysInfo = props.get("assays");
        if (assaysInfo != null && assaysInfo instanceof JSONArray)
        {
            _assays = new ArrayList<>();

            JSONArray assaysJSON = (JSONArray) assaysInfo;
            for (int i = 0; i < assaysJSON.length(); i++)
                _assays.add(AssaySpecimenConfigImpl.fromJSON(assaysJSON.getJSONObject(i), _container));
        }

        _assayPlan = null != props.get("assayPlan") ? props.get("assayPlan").toString() : null;
    }
}
