package org.labkey.study.model;

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
}
