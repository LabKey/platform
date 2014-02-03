package org.labkey.study.model;

import org.labkey.api.data.Container;
import org.labkey.api.study.AssaySpecimenVisit;

/**
 * Created by cnathe on 2/3/14.
 */
public class AssaySpecimenVisitImpl implements AssaySpecimenVisit
{
    private int _assaySpecimenId;
    private int _visitId;
    private Container _container;

    public AssaySpecimenVisitImpl()
    {
    }

    public AssaySpecimenVisitImpl(Container container, int assaySpecimenId, int visitId)
    {
        _container = container;
        _assaySpecimenId = assaySpecimenId;
        _visitId = visitId;
    }

    public int getAssaySpecimenId()
    {
        return _assaySpecimenId;
    }

    public void setAssaySpecimenId(int assaySpecimenId)
    {
        _assaySpecimenId = assaySpecimenId;
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
