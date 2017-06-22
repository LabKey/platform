/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.api.security.roles;

/**
 * Developers are trusted to create executable code that will be run on the server (like R scripts) or sent to browsers
 * to execute (like JavaScript in wiki pages).
 * User: Dave
 * Date: May 7, 2009
 */
public class DeveloperRole extends AbstractContextualRole
{
    public DeveloperRole()
    {
        super("Developer", "Contextual role for developers");
    }
}