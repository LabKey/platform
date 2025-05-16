package org.labkey.studydesign.model;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.TreatmentVisitMap;
import org.labkey.api.util.JsonUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Used as a bean in the treatment schedule to map to actual study cohorts
 */
public class StudyDesignCohort
{
    private int _rowId;
    private String _label;
    private Integer _subjectCount;
    List<TreatmentVisitMap> _treatmentVisitMap = new ArrayList<>();

    public StudyDesignCohort()
    {
    }

    public StudyDesignCohort(Cohort cohort)
    {
        _rowId = cohort.getRowId();
        _label = cohort.getLabel();
        _subjectCount = cohort.getSubjectCount();
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public Integer getSubjectCount()
    {
        return _subjectCount;
    }

    public void setSubjectCount(Integer subjectCount)
    {
        _subjectCount = subjectCount;
    }

    public List<TreatmentVisitMap> getTreatmentVisitMap()
    {
        return _treatmentVisitMap;
    }

    public void setTreatmentVisitMap(List<TreatmentVisitMap> treatmentVisitMap)
    {
        _treatmentVisitMap = treatmentVisitMap;
    }

    public static StudyDesignCohort fromJSON(@NotNull JSONObject o)
    {
        StudyDesignCohort cohort = new StudyDesignCohort();
        cohort.setLabel(o.getString("Label"));
        if (o.has("SubjectCount") && !"".equals(o.getString("SubjectCount")))
            cohort.setSubjectCount(o.getInt("SubjectCount"));
        if (o.has("RowId"))
            cohort.setRowId(o.getInt("RowId"));

        JSONArray visitMapJSON = o.optJSONArray("VisitMap");
        if (visitMapJSON != null)
        {
            List<TreatmentVisitMap> treatmentVisitMap = new ArrayList<>();
            for (JSONObject json : JsonUtil.toJSONObjectList(visitMapJSON))
                treatmentVisitMap.add(TreatmentVisitMapImpl.fromJSON(json));

            cohort.setTreatmentVisitMap(treatmentVisitMap);
        }

        return cohort;
    }
}
