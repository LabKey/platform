/*
 * Copyright (c) 2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * User: tgaluhn
 * Date: 4/8/2015
 */
public abstract class SiteValidationProviderImpl implements SiteValidationProvider
{
    @Override
    public boolean shouldRun(Container c, User u)
    {
        return true;
    }

    @Override
    public boolean isSiteScope()
    {
        return true;
    }

    @Override
    public int compareTo(@NotNull SiteValidationProvider p)
    {
        return getName().compareToIgnoreCase(p.getName());
    }

    @Override
    public String toString()
    {
        return getName();
    }
}
