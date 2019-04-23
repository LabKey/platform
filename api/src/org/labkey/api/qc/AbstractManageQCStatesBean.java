package org.labkey.api.qc;

import org.springframework.web.servlet.mvc.Controller;

public class AbstractManageQCStatesBean
{
    private String _returnUrl;
    protected QCStateHandler _qcStateHandler;
    protected Class<? extends Controller> _controllerClass;
    protected Class<? extends AbstractManageQCStatesAction> _manageAction;
    protected Class<? extends AbstractDeleteQCStateAction> _deleteAction;

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

    public Class<? extends Controller> getControllerClass()
    {
        return _controllerClass;
    }

    public Class<? extends AbstractManageQCStatesAction> getManageAction()
    {
        return _manageAction;
    }

    public Class<? extends AbstractDeleteQCStateAction> getDeleteAction()
    {
        return _deleteAction;
    }
}
