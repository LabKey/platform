/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.files;

import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ExceptionUtil;

import java.io.IOException;

/**
 * User: adam
 * Date: 9/19/13
 * Time: 10:06 AM
 */
public class FileSystemWatchers
{
    public static FileSystemWatcher get(String name)
    {
        // TODO: For now, return a real FileSystemWatcher in dev mode only. In the future,
        if (AppProps.getInstance().isDevMode())
        {
            try
            {
                return new FileSystemWatcherImpl(name);
            }
            catch (IOException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }

        return new NoopFileSystemWatcher();
    }
}
