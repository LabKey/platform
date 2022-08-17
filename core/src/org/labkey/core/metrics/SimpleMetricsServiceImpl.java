package org.labkey.core.metrics;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.Module;
import org.labkey.api.settings.AppProps;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.usageMetrics.UsageMetricsProvider;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.logging.LogHelper;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implements simple counter-based metrics from either server- or client-side code. Persists the
 * current tally to the DB asynchronously to make the tally a cumulative one for the server.
 *
 * Backed by PropertyManager. Uses a single map to track all the feature areas for each module, and then a separate
 * map for each module/area combination to persist the counts.
 */
public class SimpleMetricsServiceImpl implements SimpleMetricsService
{
    private static final Logger LOG = LogHelper.getLogger(SimpleMetricsServiceImpl.class, "Tallies and persists simple counter metrics");

    /** Organized by module, featureArea, and metricName */
    private final Map<Module, Map<String, Map<String, AtomicLong>>> _counts = new ConcurrentHashMap<>();

    /** Timestamp when we last saved the counts to the DB */
    private Runnable _saver;
    private boolean _shutdownStarted = false;

    public SimpleMetricsServiceImpl()
    {
        load();
        ContextListener.addShutdownListener(new ShutdownListener()
        {
            @Override
            public String getName()
            {
                return "Simple Metrics Service";
            }

            @Override
            public void shutdownPre() { _shutdownStarted = true; }

            @Override
            public void shutdownStarted()
            {
                save();
            }
        });
    }

    private class SimpleMetricsProvider implements UsageMetricsProvider
    {
        private final Module _module;

        public SimpleMetricsProvider(Module module)
        {
            _module = module;
        }

        @Override
        public Map<String, Object> getUsageMetrics()
        {
            return Collections.singletonMap("simpleMetricCounts", _counts.get(_module));
        }
    }

    private String getScoping(@NotNull Module module, String featureArea)
    {
        return SimpleMetricsServiceImpl.class.getName() + "." + module.getName() + "." + featureArea;
    }

    private String getRootScoping()
    {
        return SimpleMetricsServiceImpl.class.getName();
    }

    private void load()
    {
        // Get the root map, which is moduleName->comma separated feature areas
        PropertyManager.PropertyMap map = PropertyManager.getProperties(getRootScoping());
        for (Map.Entry<String, String> entry : map.entrySet())
        {
            String moduleName = entry.getKey();
            Module module = ModuleLoader.getInstance().getModule(moduleName);
            // Skip these metrics if module doesn't exist. (Perhaps the module been removed from this deployment.)
            if (null == module)
                continue;
            Map<String, Map<String, AtomicLong>> moduleMetrics = getModuleMetrics(module);
            String featureAreas = entry.getValue();
            if (featureAreas != null)
            {
                for (String featureArea : featureAreas.split(","))
                {
                    String scoping = getScoping(module, featureArea);

                    // Load from the properties stored in the DB
                    PropertyManager.PropertyMap storedProps = PropertyManager.getProperties(scoping);
                    Map<String, AtomicLong> counts = new ConcurrentHashMap<>();
                    for (Map.Entry<String, String> metric : storedProps.entrySet())
                    {
                        try
                        {
                            counts.put(metric.getKey(), new AtomicLong(Long.parseLong(metric.getValue())));
                        }
                        catch (NumberFormatException ignored) {}
                    }
                    moduleMetrics.put(featureArea, counts);
                    LOG.debug("Loaded " + counts.size() + " counts for " + scoping + ": " + counts);
                }
            }
        }
    }

    private Map<String, Map<String, AtomicLong>> getModuleMetrics(Module module)
    {
        return _counts.computeIfAbsent(module, (k) ->
        {
            // The first time a module is referenced, register a new provider
            UsageMetricsService.get().registerUsageMetrics(module.getName(), new SimpleMetricsProvider(module));
            return new ConcurrentHashMap<>();
        });
    }

    @Override
    public long increment(@NotNull String requestedModuleName, @NotNull String featureArea, @NotNull String metricName)
    {
        if (featureArea.contains(","))
        {
            throw new IllegalArgumentException("Feature area names cannot contain commas");
        }

        Module module = ModuleLoader.getInstance().getModule(requestedModuleName);
        if (null == module)
        {
            throw new IllegalArgumentException("Unknown module: " + requestedModuleName);
        }

        String scoping = getScoping(module, featureArea);
        Map<String, Map<String, AtomicLong>> moduleMetrics = getModuleMetrics(module);
        String moduleName = module.getName();  // Use canonical name to ensure consistent casing

        Map<String, AtomicLong> counts = moduleMetrics.computeIfAbsent(featureArea, k ->
        {
            try (var ignored = SpringActionController.ignoreSqlUpdates())
            {
                // This is the first time we've seen a module/area combination so save the new listing immediately,
                // which is important so that we know to reload it after the server restarts
                PropertyManager.PropertyMap updatedListing = PropertyManager.getWritableProperties(getRootScoping(), true);
                String existingAreas = updatedListing.get(moduleName);
                String updatedAreas = existingAreas == null ? featureArea : existingAreas + "," + featureArea;
                updatedListing.put(moduleName, updatedAreas);
                updatedListing.save();
            }

            return new ConcurrentHashMap<>();
        });

        long result = counts.computeIfAbsent(metricName, s -> new AtomicLong(0)).incrementAndGet();
        LOG.trace("Incremented " + scoping + "." + metricName + " to " + result);
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

            // We're OK losing a few events if they're happening mid-shutdown
            if (!_shutdownStarted)
            {
                try
                {
                    // Save more frequently on dev machines to facilitate testing
                    JobRunner.getDefault().execute(_saver, TimeUnit.MINUTES.toMillis(AppProps.getInstance().isDevMode() ? 1 : 15));
                }
                catch (RejectedExecutionException e)
                {
                    LOG.warn("Failed to queue saving simple metric values. Saving synchronously");
                    save();
                }
            }
        }
    }

    private void save()
    {
        LOG.debug("Saving counts");
        for (var modules : _counts.entrySet())
        {
            for (var areas : modules.getValue().entrySet())
            {
                PropertyManager.PropertyMap storedProps = PropertyManager.getWritableProperties(getScoping(modules.getKey(), areas.getKey()), true);
                for (Map.Entry<String, AtomicLong> metricCount : areas.getValue().entrySet())
                {
                    storedProps.put(metricCount.getKey(), Long.toString(metricCount.getValue().longValue()));
                }
                storedProps.save();
            }
        }
    }
}
