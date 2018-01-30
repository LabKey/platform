/*
 * Copyright (c) 2010-2015 LabKey Corporation
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
package org.labkey.api.util;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.UnauthorizedException;

import javax.servlet.http.HttpServletRequest;

/**
 * User: matthewb
 * Date: May 11, 2010
 * Time: 12:54:24 PM
 */
public class CSRFException extends UnauthorizedException
{
    private final String referer;

    CSRFException(@Nullable HttpServletRequest request)
    {
        super("This request has an invalid security context.  You may have signed in or signed out of this session.  Try again by using the 'back' and 'refresh' button in your browser.");
        referer = null==request ? null : request.getHeader("referer");
    }

    public String getReferer()
    {
        return referer;
    }
}
