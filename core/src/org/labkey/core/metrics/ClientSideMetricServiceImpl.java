package org.labkey.core.metrics;

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.usageMetrics.ClientSideMetricService;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.logging.LogHelper;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implements simple counter-based metrics with an eye towards ease of use from browser/client code. Persists the
 * current tally to the DB asynchronously to make the tally a cumulative one for the server.
 */
public class ClientSideMetricServiceImpl implements ClientSideMetricService
{
    private static final Logger LOG = LogHelper.getLogger(ClientSideMetricServiceImpl.class, "Tallies and persists counters from client-side code");

    private final Map<String, Map<String, AtomicLong>> _counts = new ConcurrentHashMap<>();

    /** Timestamp when we last saved the counts to the DB */
    private Runnable _saver;

    public ClientSideMetricServiceImpl()
    {
        ContextListener.addShutdownListener(new ShutdownListener()
        {
            @Override
            public String getName()
            {
                return "Client Side Metrics Service";
            }

            @Override
            public void shutdownPre() {}

            @Override
            public void shutdownStarted()
            {
                save();
            }
        });
    }

    @Override
    public long increment(String featureArea, String metricName)
    {
        Map<String, AtomicLong> counts = _counts.computeIfAbsent(featureArea, k ->
        {
            // Load from the properties stored in the DB the first time a feature area is hit
            PropertyManager.PropertyMap storedProps = PropertyManager.getProperties(ClientSideMetricServiceImpl.class.getName() + "." + featureArea);
            Map<String, AtomicLong> result = new ConcurrentHashMap<>();
            for (Map.Entry<String, String> entry : storedProps.entrySet())
            {
                try
                {
                    result.put(entry.getKey(), new AtomicLong(Long.parseLong(entry.getValue())));
                }
                catch (NumberFormatException ignored) {}
            }
            LOG.debug("Loading counts for " + featureArea + ": " + result);
            return result;
        });

        long result = counts.computeIfAbsent(metricName, s -> new AtomicLong(0)).incrementAndGet();
        LOG.trace("Incremented " + featureArea + "." + metricName + " to " + result);
        queueSave();

        return result;
    }

    /**
     * Persist the counts to the DB asynchronously. We don't save immediately, but persist whatever changes have happened
     * in a batch. We're more concerned about having a low impact on perf than we are on ensuring that every tally
     * gets persisted, and can tolerate dropping a few updates if the server crashes.
     */
    private void queueSave()
    {
        // If we already have a save pending, no need to queue another one
        if (_saver == null)
        {
            LOG.debug("Queuing a save");
            _saver = () ->
            {
                save();
                _saver = null;
            };
            // Save more frequently on dev machines to facilitate testing
            JobRunner.getDefault().execute(_saver, TimeUnit.MINUTES.toMillis(AppProps.getInstance().isDevMode() ? 1 : 15));
        }
    }

    private void save()
    {
        LOG.debug("Saving counts");
        for (var areas : _counts.entrySet())
        {
            PropertyManager.PropertyMap storedProps = PropertyManager.getWritableProperties(ClientSideMetricServiceImpl.class.getName() + "." + areas.getKey(), true);
            for (Map.Entry<String, AtomicLong> metricCount : areas.getValue().entrySet())
            {
                storedProps.put(metricCount.getKey(), Long.toString(metricCount.getValue().longValue()));
            }
            storedProps.save();
        }
    }

    @Override
    public void registerUsageMetrics(String moduleName)
    {
        UsageMetricsService svc = UsageMetricsService.get();
        if (null != svc)
        {
            svc.registerUsageMetrics(moduleName, () -> Collections.singletonMap("clientSideMetricCounts", _counts));
        }
    }
}
