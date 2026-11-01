/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.cache;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.logging.LogHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class NightlyRecalculatingSuppliers
{
    private static final Logger LOG = LogHelper.getLogger(NightlyRecalculatingSuppliers.class, "Nightly recalculated values");
    private static final Map<String, NightlyRecalculatingSupplier<?>> SUPPLIERS = new ConcurrentHashMap<>();
    private static final BlockingCache<String, Object> CACHE =
        CacheManager.getBlockingCache(String.class, 100, CacheManager.WEEK, "Nightly recalculated values", null);

    private NightlyRecalculatingSuppliers()
    {
    }

    /**
     * Returns a supplier that caches the value retrieved during the first call to {@code supplier.get()} and returns
     * that value on subsequent calls to {@code supplier.get()}. Every call to {@code NightlyRecalculatingSuppliers.get()}
     * with the same unique name and delegate supplier class returns the same caching supplier, so the efficiency
     * continues across multiple calls, with no need to hold the supplier. All suppliers proactively recalculate during
     * nightly maintenance, so their values are never more than 24 hours out-of-date.
     * @param uniqueName unique across the server; include anything the delegate captures that varies between calls
     * @param delegate a supplier for the value that's cached. All cached values, and therefore, anything a delegate
     *                 supplier calculates, must be unmodifiable.
     * @return the same instance for every call with this name; the first registered delegate is kept
     */
    @SuppressWarnings("unchecked")
    public static <T> Supplier<T> get(@NotNull String uniqueName, @NotNull Supplier<T> delegate)
    {
        NightlyRecalculatingSupplier<?> supplier = SUPPLIERS.computeIfAbsent(uniqueName, _ -> new NightlyRecalculatingSupplier<>(uniqueName, delegate));
        Class<?> registered = supplier.delegate().getClass();
        if (registered != delegate.getClass())
            reportConflict("Nightly recalculating supplier \"" + uniqueName + "\" was registered by " + registered.getName() + " and again by " + delegate.getClass().getName());
        return (Supplier<T>) supplier;
    }

    /** Computes each new value before replacing the old one, so callers rarely block on a recalculation. A failing delegate keeps its old value. */
    private static void recalculateAll(Logger pipelineLog)
    {
        SUPPLIERS.values().forEach(supplier -> {
            try
            {
                pipelineLog.info("Recalculating {}", supplier.name());
                CACHE.put(supplier.name(), supplier.calculate());
            }
            catch (Exception e)
            {
                ExceptionUtil.decorateException(e, ExceptionUtil.ExceptionInfo.ExtraMessage, "Nightly recalculating supplier: " + supplier.name(), true);
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        });
    }

    public static class RecalculationMaintenanceTask implements SystemMaintenance.MaintenanceTask
    {
        @Override
        public String getDescription()
        {
            return "Recalculate nightly recalculated values, such as slow usage metrics";
        }

        @Override
        public String getName()
        {
            return "RecalculateNightlyValues";
        }

        @Override
        public void run(Logger pipelineLog)
        {
            pipelineLog.info("Recalculating {} values", SUPPLIERS.size());
            recalculateAll(pipelineLog);
        }

        // Disabling would leave every value frozen at its first calculation
        @Override
        public boolean canDisable()
        {
            return false;
        }
    }

    /** Throws in dev mode; otherwise warns and keeps the first delegate */
    private static void reportConflict(String message)
    {
        if (AppProps.getInstance().isDevMode())
            throw new IllegalStateException(message);
        LOG.warn(message);
    }

    private record NightlyRecalculatingSupplier<T>(@NotNull String name, @NotNull Supplier<T> delegate) implements Supplier<T>
    {
        @Override
        @SuppressWarnings("unchecked")
        public T get()
        {
            return (T) CACHE.get(name, null, (_, _) -> calculate());
        }

        private T calculate()
        {
            LOG.debug("Calculating {}", name);
            T value = delegate.get();
            LOG.debug("Calculated {}", name);
            return value;
        }
    }
}
