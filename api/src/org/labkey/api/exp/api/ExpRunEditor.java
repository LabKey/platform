package org.labkey.api.exp.api;

import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

public class ExpRunEditor
{
    private ActionURL _editUrl;
    private String _displayName;
    private String _protocolName;

    public ExpRunEditor(String displayName, String protocolName, ActionURL editUrl)
    {
        _displayName = displayName;
        _protocolName = protocolName;
        _editUrl = editUrl;
    }

    public ActionURL getEditUrl(Container c)
    {
        // new action url so parameters don't get added repeatedly to _editUrl
        ActionURL editUrl = new ActionURL(_editUrl.getLocalURIString());
        return editUrl.setContainer(c);
    }

    public String getDisplayName()
    {
        return _displayName;
    }

    public boolean isProtocolEditor(ExpProtocol protocol)
    {
        return protocol.getName().equals(_protocolName);
    }
}
