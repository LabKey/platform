/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.util.SkipMothershipLogging;

import javax.servlet.http.HttpServletResponse;

/**
 * Throw to indicate that the client has referred to a resource that does not exist. Uncaught, this will
 * bubble up to the top level and be returned via a 404 HTTP response code.
 */
public class NotFoundException extends RuntimeException implements SkipMothershipLogging
{
    public NotFoundException()
    {
        super("" + HttpServletResponse.SC_NOT_FOUND + ": page not found");
    }

    public NotFoundException(String string)
    {
        super(string);
    }
    
    public NotFoundException(String string, Throwable cause)
    {
        super(string, cause);
    }
}
