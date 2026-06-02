/*
 * Copyright (c) 2021-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.exp.xar;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.security.User;

import java.util.HashMap;
import java.util.Map;

public class XarReaderRegistry
{
    private static final Logger _logger = LogManager.getLogger(XarReaderRegistry.class);
    private final Map<String, XarReaderDelegate> _delegateMap = new HashMap<>();
    private static final XarReaderRegistry _instance = new XarReaderRegistry();

    private XarReaderRegistry()
    {
    }

    public static XarReaderRegistry get()
    {
        return _instance;
    }

    public void registerDelegate(String protocolPattern, XarReaderDelegate delegate)
    {
        if (_delegateMap.containsKey(protocolPattern))
            _logger.warn("Existing delegate '{}' for protocol pattern '{}' being replaced.", _delegateMap.get(protocolPattern).getXarDelegateName(), protocolPattern);

        _delegateMap.put(protocolPattern, delegate);
    }

    public void postProcessImportedProtocol(Container container, User user, ExpProtocol protocol, Logger logger)
    {
        if (protocol == null)
            return;

        _delegateMap.keySet().stream().filter(protocol.getLSID()::contains).forEach(key -> {
            XarReaderDelegate delegate = _delegateMap.get(key);
            try
            {
                delegate.postProcessImportedProtocol(container, user, protocol, logger);
            }
            catch (Exception e)
            {
                logger.error("There was a problem during postprocessing with delegate '{}'", delegate.getXarDelegateName(), e);
            }
        });
    }

    public void postProcessImportedRun(Container container, User user, ExpRun run, Logger logger)
    {
        if (run == null)
            return ;

        _delegateMap.keySet().stream().filter(run.getProtocol().getLSID()::contains).forEach(key -> {
            XarReaderDelegate delegate = _delegateMap.get(key);
            try
            {
                delegate.postProcessImportedRun(container, user, run, logger);
            }
            catch (Exception e)
            {
                logger.error("There was a problem during postprocessing with delegate '{}'", delegate.getXarDelegateName(), e);
            }
        });
    }
}
