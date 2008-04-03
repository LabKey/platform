/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.exp;

import java.io.File;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Oct 14, 2005
 */
public class FileXarSource extends AbstractFileXarSource
{

    public FileXarSource(File file)
    {
        _xmlFile = file;
    }

    public void init() throws IOException
    {
    }

    public File getLogFile() throws IOException
    {
        return getLogFileFor(_xmlFile);
    }

    public String toString()
    {
        return _xmlFile.getPath();
    }
}
