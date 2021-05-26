package org.labkey.api.specimen.actions;

public class RequestIdForm extends SpecimenApiForm
{
    private int _requestId;

    public int getRequestId()
    {
        return _requestId;
    }

    public void setRequestId(int requestId)
    {
        _requestId = requestId;
    }
}
