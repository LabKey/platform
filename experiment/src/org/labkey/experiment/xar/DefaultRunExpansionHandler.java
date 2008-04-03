package org.labkey.experiment.xar;

import org.fhcrc.cpas.exp.xml.ProtocolApplicationBaseType;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.RunExpansionHandler;
import org.labkey.api.exp.api.ExpRun;

/**
 * User: jeckels
 * Date: Dec 7, 2005
 */
public class DefaultRunExpansionHandler implements RunExpansionHandler
{
    public void protocolApplicationExpanded(ProtocolApplicationBaseType xbProtApp, ExpRun run)
    {
        // No-op
    }

    public Handler.Priority getPriority(String cpasType)
    {
        if (cpasType.equals("ProtocolApplication"))
        {
            return Handler.Priority.LOW;
        }
        return null;
    }
}
