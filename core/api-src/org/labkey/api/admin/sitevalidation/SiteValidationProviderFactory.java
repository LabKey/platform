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
package org.labkey.api.admin.sitevalidation;

/**
 * We register a factory that creates a new validation provider on each run. This gives the provider an opportunity
 * to initialize state before being called on every container. The provider could, for example, execute a single
 * cross-container query instead of one query per container.
 */
public interface SiteValidationProviderFactory extends SiteValidatorDescriptor
{
    /**
     * Return true to indicate this is a site-wide validator.
     * False to indicate the validator should only run at container scope
     */
    default boolean isSiteScope()
    {
        return false;
    }

    SiteValidationProvider getSiteValidationProvider();
}
