/*
 * Copyright (c) 2015-2016 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * User: tgaluhn
 * Date: 4/8/2015
 */
public interface SiteValidationProvider extends SiteValidatorDescriptor
{
    /**
     *
     * Return false to indicate the validator shouldn't be run for that container.
     * Useful if we know in advance the validator isn't applicable; e.g., the
     * validator is module-dependent and that module isn't enabled in this container.
     *
     */
    default boolean shouldRun(Container c, User u)
    {
        return true;
    }

    /**
     * Return tru to indicate this is a site-wide validator.
     * False to indicate the validator should only run at container scope
     */
    default boolean isSiteScope()
    {
        return true;
    }

    @Nullable
    SiteValidationResultList runValidation(Container c, User u);
}
