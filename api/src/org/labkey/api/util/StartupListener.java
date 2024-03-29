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
package org.labkey.api.util;

import jakarta.servlet.ServletContext;

/**
 * Callback for when the server is up and running, and all modules have completed their startup.
 * Useful for launching background work that relies on all modules being ready, or for doing work
 * that shouldn't block users from being able to log in and use the site. Complement to {@link ShutdownListener}.
 * @author brendanx
 */
public interface StartupListener
{
    String getName();
    void moduleStartupComplete(ServletContext servletContext);
}
