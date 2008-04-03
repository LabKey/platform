package org.labkey.api.exp;

import org.fhcrc.cpas.exp.xml.ProtocolApplicationBaseType;
import org.labkey.api.exp.api.ExpRun;

/**
 * User: jeckels
 * Date: Dec 7, 2005
 */
public interface RunExpansionHandler extends Handler<String>
{
    public void protocolApplicationExpanded(ProtocolApplicationBaseType xbProtApp, ExpRun run);
}
