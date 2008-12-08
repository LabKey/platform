/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.module;

import org.labkey.api.util.FileCopyListener;

import java.io.File;
import java.util.Set;

/*
* User: Dave
* Date: Dec 2, 2008
* Time: 4:25:06 PM
*/
public class ModuleFileCopyListener implements FileCopyListener
{
    private Set<File> _unclaimedFiles;

    public ModuleFileCopyListener(Set<File> unclaimedFiles)
    {
        _unclaimedFiles = unclaimedFiles;
    }

    public boolean shouldCopy(File src, File dest)
    {
        _unclaimedFiles.remove(dest);
        return !dest.exists() ||
            src.lastModified() < 0 ||
            src.lastModified() < (dest.lastModified() - 2000) ||
            src.lastModified() > (dest.lastModified() + 2000) ||
            src.length() < 0 ||
            src.length() != dest.length();
    }

    public void afterFileCopy(File src, File dest)
    {
        //reset the last modified on the destination to match the source
        dest.setLastModified(src.lastModified());
    }

    public Set<File> getUnclaimedFiles()
    {
        return _unclaimedFiles;
    }
}