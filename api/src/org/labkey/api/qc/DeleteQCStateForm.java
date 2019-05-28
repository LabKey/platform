package org.labkey.api.qc;

public class DeleteQCStateForm
{
    private int _id;
    private boolean _all = false;
    private String _manageReturnUrl;

    public int getId() {return _id;}

    public void setId(int id) {_id = id;}

    public boolean isAll()
    {
        return _all;
    }

    public void setAll(boolean all)
    {
        _all = all;
    }

    public String getManageReturnUrl()
    {
        return _manageReturnUrl;
    }

    public void setManageReturnUrl(String manageReturnUrl)
    {
        _manageReturnUrl = manageReturnUrl;
    }
}
