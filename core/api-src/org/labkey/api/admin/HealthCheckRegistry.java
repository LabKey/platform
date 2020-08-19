/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.admin;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry for health check methods to be used when monitoring a server.  These checks are called quite frequently
 * and should be extremely light weight (e.g., should not do much in the way of database queries).
 *
 * You can register checks in different categories to allow the caller of AdminController.HealtCheckAction to choose
 * the scope of health they want to check.  Each health check has a unique name within its category.
 */
public class HealthCheckRegistry
{
    private static final Logger LOG = LogManager.getLogger(HealthCheckRegistry.class);

    public static final String DEFAULT_CATEGORY = "default";
    public static final String TRIAL_INSTANCES_CATEGORY = "trial";

    private Map<String, Map<String, HealthCheck>> _healthCheckCategories = new HashMap<>();
    private static HealthCheckRegistry _instance = new HealthCheckRegistry();

    public static HealthCheckRegistry get()
    {
        return _instance;
    }

    /**
     * Registers a health check in the default category.
     * @param name name of the health check
     * @param healthCheck the implementation of the check
     */
    public void registerHealthCheck(@NotNull String name, @NotNull HealthCheck healthCheck)
    {
        registerHealthCheck(name, DEFAULT_CATEGORY, healthCheck);
    }

    /**
     * Get the current health check categories
     * @return
     */
    public Set<String> getCategories()
    {
        return _healthCheckCategories.keySet();
    }

    /**
     * Register a health check in a given category.  If there is already a check with the same name in the given category,
     * there will be no changes.
     * @param name name of the health check
     * @param category category of the check
     * @param healthCheck the implementation of the check
     */
    public void registerHealthCheck(@NotNull String name, @NotNull String category, @NotNull HealthCheck healthCheck)
    {
        if (!_healthCheckCategories.containsKey(category))
            _healthCheckCategories.put(category, new HashMap<>());
        Map<String, HealthCheck> categoryChecks = _healthCheckCategories.get(category);
        if (categoryChecks.containsKey(name))
        {
            LOG.error("Health check with name '" + name + "' already exists in category '" + category + "'.  New check not registered.");
            return;
        }
        _healthCheckCategories.get(category).put(name, healthCheck);
    }

    /**
     * Check health for a given collection of categories
     * @param categories the collection of categories to check.  If the value "all" is contained in this collection,
     *                   this will cause checks in all categories to be run
     * @return the coalesced health check result from all results in the given categories
     */
    public HealthCheck.Result checkHealth(Collection<String> categories)
    {
        boolean overallHealth = true;
        Map<String, Object> details = new HashMap<>();
        if (categories.contains("all"))
        {
            categories = HealthCheckRegistry.get().getCategories();
        }
        for (String category : categories)
        {
            HealthCheck.Result result = checkHealth(category);
            overallHealth = overallHealth && result.isHealthy();
            details.put(category, result);
        }
        return new HealthCheck.Result(overallHealth, details);
    }

    /**
     * Check health for a given category.  If a category is given that does not exist, the result will be unhealthy.
     * @param category the category to check
     * @return the coalesced health check result from all results in the given category.
     */
    public HealthCheck.Result checkHealth(@NotNull String category)
    {
        boolean overallHealth = true;
        Map<String, Object> details = new HashMap<>();

        if (_healthCheckCategories.containsKey(category))
        {
            for (Map.Entry<String, HealthCheck> categoryChecks : _healthCheckCategories.get(category).entrySet())
            {
                HealthCheck.Result result = categoryChecks.getValue().checkHealth();
                overallHealth = overallHealth && result.isHealthy();
                details.put(categoryChecks.getKey(), result);
            }

            return new HealthCheck.Result(overallHealth, details);
        }
        else
        {
            details.put(category, "No such category of health checks.");
            return new HealthCheck.Result(false, details);
        }
    }
}
