package org.labkey.biotrue.controllers;

import org.labkey.api.view.ViewForm;
import org.labkey.biotrue.objectmodel.BtServer;
import org.apache.struts.action.ActionMapping;

import javax.servlet.http.HttpServletRequest;

public class ServerForm extends ViewForm
{
    BtServer _server;
    public void reset(ActionMapping actionMapping, HttpServletRequest request)
    {
        super.reset(actionMapping, request);
        String serverId = request.getParameter("serverId");
        if (serverId != null)
        {
            _server = BtServer.fromId(Integer.valueOf(serverId));
        }
    }

    public BtServer getServer()
    {
        return _server;
    }
}
