/*
 * Copyright (c) 2020-2026 LabKey Corporation
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

/**
 * This is the helper class to provide a custom logger writer which can be
 * used to route log messages to a different location than the logger. ex - to a file.
 *
 * User : ankurj
 * Date : Jul 23, 2020
 * */

public interface LoggerWriter
{
    void write(String message, @Nullable Throwable t);

    void debug(String message);

    void debug(String message, Throwable t);

    void error(String message);

    void error(String message, Throwable t);

    void info(String message);

    void info(String message, Throwable t);

    void fatal(String message);

    void fatal(String message, Throwable t);

    void trace(String message);

    void trace(String message, Throwable t);
}
