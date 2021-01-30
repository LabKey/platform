package org.labkey.api.exp.xar;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocolApplication;
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
            _logger.warn(String.format("Existing delegate '%s' for protocol pattern '%s' being replaced.", _delegateMap.get(protocolPattern).getXarDelegateName(), protocolPattern));

        _delegateMap.put(protocolPattern, delegate);
    }

    public void postProcessImportedRun(Container container, User user, ExpRun run, Logger logger)
    {
        if (run == null)
            return ;

        _delegateMap.keySet().stream().filter(run.getProtocol().getLSID()::contains).forEach(key -> {
            XarReaderDelegate delegate = _delegateMap.get(key);
            try
            {
                delegate.postProcessImportedRun(container, user, run);
            }
            catch (Exception e)
            {
                logger.error(String.format("There was a problem during postprocessing with delegate '%s'", delegate.getXarDelegateName()), e);
            }
        });
    }
}
