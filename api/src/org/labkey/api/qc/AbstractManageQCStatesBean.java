package org.labkey.api.qc;

public class AbstractManageQCStatesBean
{
    private String _returnUrl;
    protected QCStateHandler _qcStateHandler;
    protected AbstractManageQCStatesAction _manageAction;
    protected Class<? extends AbstractDeleteQCStateAction> _deleteAction;
    protected String _noun;
    protected String _dataNoun;

    public AbstractManageQCStatesBean(String returnUrl)
    {
        _returnUrl = returnUrl;
    }

    public String getReturnUrl()
    {
        return _returnUrl;
    }

    public QCStateHandler getQCStateHandler()
    {
        return _qcStateHandler;
    }

    public AbstractManageQCStatesAction getManageAction()
    {
        return _manageAction;
    }

    public Class<? extends AbstractDeleteQCStateAction> getDeleteAction()
    {
        return _deleteAction;
    }

    public String getNoun()
    {
        return _noun;
    }

    public String getDataNoun() { return _dataNoun; }
}
