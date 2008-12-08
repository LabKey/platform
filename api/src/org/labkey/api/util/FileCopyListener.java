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
package org.labkey.api.util;

import java.io.File;

/*
* User: Dave
* Date: Dec 2, 2008
* Time: 4:21:23 PM
*/

/**
 * Used by FileUtil copy methods to ask if files should be copied
 * and to notify of actual files copied
 */
public interface FileCopyListener
{
    /**
     * Return true from this method if the src file should be copied
     * to the destination, false if not. For example, if the source
     * file is unchanged, return false to skip copying
     * @param src The source file
     * @param dest The destination file (may not yet exist)
     * @return True to allow the copy, false to prohibit
     */
    public boolean shouldCopy(File src, File dest);

    /**
     * Called after a file has been successfully copied. Note that
     * if the implementation returned false from shouldCopy, this
     * method will not be called. This is called only when the file
     * is actually copied.
     * @param src The source file
     * @param dest The destination file
     */
    public void afterFileCopy(File src, File dest);
}