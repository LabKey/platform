/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.api.settings;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.HtmlString;

import java.util.Collection;

/**
 * Manages the optional features that can be enabled or disabled within a given deployment, and their current state.
 * Optional features may be experimental (not ready for production use), deprecated (supported at the moment, but will
 * be removed soon), or simply optional (not widely used, perhaps client-specific). Optional features are off by default.
 */
public interface OptionalFeatureService
{
    static @NotNull OptionalFeatureService get()
    {
        OptionalFeatureService svc = ServiceRegistry.get().getService(OptionalFeatureService.class);
        if (null == svc)
            throw new IllegalStateException("OptionalFeatureService not found");
        return svc;
    }

    static void setInstance(OptionalFeatureService impl)
    {
        ServiceRegistry.get().registerService(OptionalFeatureService.class, impl);
    }

    /**
     * @param flag must be unique and conform to the Java identifier rules (e.g., alphanumeric plus _, start with a
     *             letter, no spaces). That way it can be used as a startup property to enable/disable the task. If
     *             you must use a flag that doesn't conform to these rules (why?) the call the other variant.
     */
    default void addExperimentalFeatureFlag(String flag, String title, String description, boolean requiresRestart)
    {
        addExperimentalFeatureFlag(flag, title, description, requiresRestart, false);
    }

    /**
     * This is left for backward compatibility. Use the variant above and provide flag that follows Java identifier rules.
     */
    default void addExperimentalFeatureFlag(String flag, String title, String description, boolean requiresRestart, boolean useDumbName)
    {
        addFeatureFlag(new OptionalFeatureFlag(flag, title, description, requiresRestart, false, FeatureType.Experimental, useDumbName));
    }

    void addFeatureFlag(OptionalFeatureFlag optionalFeatureFlag);

    // Return all optional features, regardless of type
    Collection<OptionalFeatureFlag> getOptionalFeatureFlags();

    // Return all optional features having the specified type
    Collection<OptionalFeatureFlag> getOptionalFeatureFlags(FeatureType type);

    void addFeatureListener(String feature, OptionalFeatureListener listener);

    boolean isFeatureEnabled(String feature);

    void removeFeatureListener(String feature, OptionalFeatureListener listener);

    void setFeatureEnabled(String feature, boolean enabled, User user);

    interface OptionalFeatureListener
    {
        void featureChanged(String feature, boolean enabled);
    }

    // FeatureType determines the admin page on which an optional feature appears. The property is used at run-time
    // registration only; it is not persisted. All optional properties are persisted and retrieved the same way, and can
    // be populated using the "ExperimentalFeature" startup property prefix. This means features can be switched to a
    // different FeatureType at any time.
    enum FeatureType
    {
        Deprecated
        {
            @Override
            public HtmlString getAdminGuidance()
            {
                return HtmlString.unsafe(
                    """
                    <strong>WARNING</strong>:
                    Deprecated features will be removed very soon, most likely for the next major release.
                    If you enable one of these features you should also create a plan to stop relying on it.
                    """
                );
            }
        },
        Experimental
        {
            @Override
            public HtmlString getAdminGuidance()
            {
                return HtmlString.unsafe(
                    """
                    <strong>WARNING</strong>:
                    Experimental features may change, break, or disappear at any time.
                    We make absolutely no guarantee about what will happen if you turn on any experimental feature.
                    """
                );
            }
        },
        Optional
        {
            @Override
            public HtmlString getAdminGuidance()
            {
                return HtmlString.unsafe(
                    """
                    Optional features are not typically used; discuss with your account manager before enabling any
                    optional feature.
                    """
                );
            }
        };

        public abstract HtmlString getAdminGuidance();
    }
}
