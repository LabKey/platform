/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.api.view;

import org.jetbrains.annotations.Nullable;

/**
 * Thrown when a user accesses a forbidden project, for example, when an admin is impersonating within a project and
 * attempts to access a folder outside that project or when a non-project admin attempts to access a locked project.
 * Used to provide a more helpful error message compared with a vanilla {@link UnauthorizedException}.
 * User: adam
 * Date: Aug 27, 2008
 */
public class ForbiddenProjectException extends UnauthorizedException
{
    public ForbiddenProjectException(String message)
    {
        super(message);
    }

    @Override
    public @Nullable String getAdvice()
    {
        return null;
    }
}
