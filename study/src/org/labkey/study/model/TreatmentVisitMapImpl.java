/*
 * Copyright (c) 2014-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.study.TreatmentVisitMap;

/**
 * User: cnathe
 * Date: 12/30/13
 */
public class TreatmentVisitMapImpl implements TreatmentVisitMap
{
    private int _cohortId;
    private int _treatmentId;
    private String _tempTreatmentId;  // used to map new treatment records used in mappings to the tempRowId in TreatmentImpl
    private int _visitId;
    private Container _container;

    public TreatmentVisitMapImpl()
    {
    }

    public int getCohortId()
    {
        return _cohortId;
    }

    public void setCohortId(int cohortId)
    {
        _cohortId = cohortId;
    }

    public int getTreatmentId()
    {
        return _treatmentId;
    }

    public void setTreatmentId(int treatmentId)
    {
        _treatmentId = treatmentId;
    }

    public String getTempTreatmentId()
    {
        return _tempTreatmentId;
    }

    private void setTempTreatmentId(String tempTreatmentId)
    {
        _tempTreatmentId = tempTreatmentId;
    }

    public int getVisitId()
    {
        return _visitId;
    }

    public void setVisitId(int visitId)
    {
        _visitId = visitId;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        final TreatmentVisitMapImpl o = (TreatmentVisitMapImpl) obj;

        return o.getCohortId() == getCohortId()
                && o.getTreatmentId() == getTreatmentId()
                && o.getVisitId() == getVisitId()
                && ((o.getContainer() == null && getContainer() == null) || o.getContainer().equals(getContainer()));
    }

    public static TreatmentVisitMapImpl fromJSON(@NotNull JSONObject o)
    {
        TreatmentVisitMapImpl visitMap = new TreatmentVisitMapImpl();
        visitMap.setVisitId(o.getInt("VisitId"));
        if (o.containsKey("CohortId"))
            visitMap.setCohortId(o.getInt("CohortId"));
        if (o.containsKey("TreatmentId"))
        {
            if (o.get("TreatmentId") instanceof Integer)
                visitMap.setTreatmentId(o.getInt("TreatmentId"));
            else
                visitMap.setTempTreatmentId(o.getString("TreatmentId"));
        }

        return visitMap;
    }
}
