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
package org.labkey.api.util;

import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import java.io.File;

/**
 * A session holder for temporary files. This will hold a file in the session,
 * and then delete it when the session expires. Note: DELETE IT.
 *
 * User: jgarms
 * Date: Nov 4, 2008
 */
public class SessionTempFileHolder implements HttpSessionBindingListener
{
    private final File file;
    private boolean deleted = false;

    public SessionTempFileHolder(File file)
    {
        this.file = file;
    }

    public File getFile()
    {
        return file;
    }

    public boolean delete()
    {
        deleted = true;
        return this.file.delete();
    }

    public void valueBound(HttpSessionBindingEvent event)
    {
        // nothing to do
    }

    public void valueUnbound(HttpSessionBindingEvent event)
    {
        if (!deleted)
            file.delete();
    }
}
