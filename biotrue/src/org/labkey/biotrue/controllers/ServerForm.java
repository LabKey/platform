package org.labkey.biotrue.controllers;

import org.labkey.api.view.ViewForm;
import org.labkey.biotrue.objectmodel.BtServer;
import org.apache.struts.action.ActionMapping;

import javax.servlet.http.HttpServletRequest;

public class ServerForm extends ViewForm
{
    private int _serverId;

    public int getServerId()
    {
        return _serverId;
    }

    public void setServerId(int serverId)
    {
        _serverId = serverId;
    }

    public BtServer getServer()
    {
        return BtServer.fromId(_serverId);
    }
}
