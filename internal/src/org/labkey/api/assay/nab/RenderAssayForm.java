package org.labkey.api.assay.nab;

import org.labkey.api.assay.dilution.DilutionCurve;

/**
* Created by IntelliJ IDEA.
* User: klum
* Date: 5/15/13
*/
public class RenderAssayForm
{
    private boolean _newRun;
    private int _rowId = -1;
    protected DilutionCurve.FitType _fitType;

    public boolean isNewRun()
    {
        return _newRun;
    }

    public void setNewRun(boolean newRun)
    {
        _newRun = newRun;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getFitType()
    {
        return _fitType != null ? _fitType.name() : null;
    }

    public void setFitType(String fitType)
    {
        _fitType = fitType != null ? DilutionCurve.FitType.valueOf(fitType) : null;
    }

    public DilutionCurve.FitType getFitTypeEnum()
    {
        return _fitType;
    }
}
