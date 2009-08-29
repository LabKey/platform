/*
 * Copyright (c) 2009 LabKey Corporation
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

package org.labkey.api.study;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlError;

import java.io.File;

public class InvalidFileException extends StudyImportException
{
    public InvalidFileException(File root, File file, Throwable t)
    {
        super(getErrorString(root, file, t.getMessage()));
    }

    public InvalidFileException(File root, File file, XmlException e)
    {
        super(getErrorString(root, file, e));
    }

    // Special handling for XmlException: e.getMessage() includes absolute path to file, which we don't want to display
    private static String getErrorString(File root, File file, XmlException e)
    {
        XmlError error = e.getError();
        return getErrorString(root, file, error.getLine() + ":" + error.getColumn() + ": " + error.getMessage());
    }

    private static String getErrorString(File root, File file, String message)
    {
        return getRelativePath(root, file) + " is not valid: " + message;
    }
}
