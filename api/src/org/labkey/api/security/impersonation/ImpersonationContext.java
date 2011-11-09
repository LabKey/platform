/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.api.security.impersonation;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;

import java.io.Serializable;

/**
 * User: adam
 * Date: 11/8/11
 * Time: 8:01 PM
 */
public interface ImpersonationContext extends Serializable
{
    public boolean isImpersonated();
    public boolean isAllowedRoles();
    public Container getStartingProject();
    public Container getImpersonationProject();
    public User getImpersonatingUser();
    public String getNavTreeCacheKey();  // Caching permission-related state is very tricky with impersonation; context needs to provide the cache key suffix
    public URLHelper getReturnURL();
}
