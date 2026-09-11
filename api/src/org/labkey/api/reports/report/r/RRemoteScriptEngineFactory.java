/*
 * Copyright (c) 2026 LabKey Corporation. All rights reserved. No portion of this work may be reproduced
 * in any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.api.reports.report.r;

import org.labkey.api.remoterunner.RemoteRunnerService;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.ExternalScriptEngineFactory;

import javax.script.ScriptEngine;

public class RRemoteScriptEngineFactory extends ExternalScriptEngineFactory
{
    public RRemoteScriptEngineFactory(ExternalScriptEngineDefinition def)
    {
        super(def);
    }

    @Override
    public synchronized ScriptEngine getScriptEngine()
    {
        RemoteRunnerService service = RemoteRunnerService.get();
        if (null != service && service.isEnabled())
            return new RRemoteScriptEngine(_def, service);
        return null;
    }
}
