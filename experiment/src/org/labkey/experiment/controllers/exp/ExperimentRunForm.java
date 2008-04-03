package org.labkey.experiment.controllers.exp;

import org.labkey.api.view.ViewForm;
import org.labkey.api.view.HttpView;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;

/**
 * User: jeckels
* Date: Dec 19, 2007
*/
public class ExperimentRunForm extends ViewForm
{
    private int _rowId;
    private String _lsid;
    private boolean _detail;
    private String _focus;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    public ExpRun lookupRun()
    {
        ExpRun run = ExperimentService.get().getExpRun(getRowId());
        if (run == null && getLsid() != null)
        {
            run = ExperimentService.get().getExpRun(getLsid());
        }
        if (run == null)
        {
            HttpView.throwNotFound("Could not find experiment run");
        }
        return run;
    }

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
}
