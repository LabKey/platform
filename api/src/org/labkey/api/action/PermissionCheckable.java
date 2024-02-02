/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

package org.labkey.api.action;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.UnauthorizedException;

/**
 * Interface for {@link Action} classes that want to handle permission checks
 * in a way that's more complex than allowed by the standard
 * {@link org.labkey.api.security.RequiresPermission} style of annotations.
 * User: jeckels
 * Date: Apr 9, 2008
 */
public interface PermissionCheckable
{
    void checkPermissions() throws UnauthorizedException;

    /**
     * @return  the preferred response format. Primarily utilized so that API actions can return JSON for
     * 401, 404, 500, and other error conditions. Null indicates no preference, which will typically result in an HTML
     * response
     */
    @Nullable
    default ApiResponseWriter.Format getDefaultResponseFormat() { return null; }
}
