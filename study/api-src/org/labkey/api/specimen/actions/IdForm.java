package org.labkey.api.specimen.actions;

import org.labkey.api.action.ReturnUrlForm;

public class IdForm extends ReturnUrlForm
{
    public enum PARAMS
    {
        id
    }

    private int _id;

    public IdForm()
    {
    }

    public IdForm(int id)
    {
        _id = id;
    }

    public int getId()
    {
        return _id;
    }

    public void setId(int id)
    {
        _id = id;
    }
}
