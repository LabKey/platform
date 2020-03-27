package org.labkey.api.exp.api;

import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

/**
 * Identifies a run editor entry point like the Sample Derivation run editor in the Provenance module.  An ExpRunEditor
 * class instance can be registered in the ExperimentService and create run links will be added on the Runs grid,
 * samples content grid and file browser webpart.  Right now this only supports a single run editor.
 */
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
        ActionURL editUrl = _editUrl.clone();
        return editUrl.setContainer(c);
    }

    public String getDisplayName()
    {
        return _displayName;
    }

    public String getProtocolName()
    {
        return _protocolName;
    }

    public boolean isProtocolEditor(ExpProtocol protocol)
    {
        return protocol.getName().equals(_protocolName);
    }
}
