package org.labkey.api.qc;

public class ManageQCStatesBean
{
    private String _returnUrl;
    protected static QCStateHandler _qcStateHandler;

    public QCStateHandler getQCStateHandler()
    {
        return _qcStateHandler;
    }

    public static void setQCStateHandler(QCStateHandler _qcStateHandler)
    {
        _qcStateHandler = _qcStateHandler;
    }

    public ManageQCStatesBean(String returnUrl)
    {
        _returnUrl = returnUrl;
    }

    public String getReturnUrl()
    {
        return _returnUrl;
    }
}
