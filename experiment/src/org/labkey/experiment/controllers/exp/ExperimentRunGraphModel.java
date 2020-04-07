package org.labkey.experiment.controllers.exp;

import org.labkey.experiment.api.ExpRunImpl;

public class ExperimentRunGraphModel
{
    private boolean _detail;
    private String _focus;
    private String _focusType;
    private ExpRunImpl _run;

    public boolean isDetail()
    {
        return _detail;
    }

    public String getFocus()
    {
        return _focus;
    }

    public void setFocus(String focus)
    {
        _focus = focus;
    }

    public void setDetail(boolean detail)
    {
        _detail = detail;
    }

    public String getFocusType()
    {
        return _focusType;
    }

    public void setFocusType(String focusType)
    {
        _focusType = focusType;
    }

    public ExpRunImpl getRun()
    {
        return _run;
    }

    public void setRun(ExpRunImpl run)
    {
        _run = run;
    }
}
