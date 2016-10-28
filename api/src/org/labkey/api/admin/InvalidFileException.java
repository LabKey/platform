/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

package org.labkey.api.admin;

import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.labkey.api.admin.ImportException;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;

import java.io.File;

public class InvalidFileException extends ImportException
{
    public InvalidFileException(File root, File file, Throwable t)
    {
        super(getErrorString(root, file, t.getMessage()));
    }

    public InvalidFileException(VirtualFile root, File file, Throwable t)
    {
        super(getErrorString(root.getRelativePath(file.getName()), t.getMessage()));
    }    

    public InvalidFileException(File root, File file, XmlException e)
    {
        super(getErrorString(root, file, e));
    }

    public InvalidFileException(VirtualFile root, File file, XmlException e)
    {
        super(getErrorString(root, file, e));
    }

    public InvalidFileException(File root, File file, XmlValidationException e)
    {
        super(getErrorString(root, file, (String)null), e);
    }

    public InvalidFileException(VirtualFile root, File file, XmlValidationException e)
    {
        super(getErrorString(root.getRelativePath(file.getName()), null), e);
    }

    public InvalidFileException(String relativePath, Throwable t)
    {
        super(getErrorString(relativePath, t.getMessage()));
    }

    // Special handling for XmlException: e.getMessage() includes absolute path to file, which we don't want to display
    private static String getErrorString(File root, File file, XmlException e)
    {
        XmlError error = e.getError();
        return getErrorString(root, file, error.getLine() + ":" + error.getColumn() + ": " + error.getMessage());
    }

    // Special handling for XmlException: e.getMessage() includes absolute path to file, which we don't want to display
    private static String getErrorString(VirtualFile root, File file, XmlException e)
    {
        XmlError error = e.getError();
        return getErrorString(root.getRelativePath(file.getName()), error.getLine() + ":" + error.getColumn() + ": " + error.getMessage());
    }

    private static String getErrorString(File root, File file, String message)
    {
        return getRelativePath(root, file) + " is not valid" + (null != message ? ": " + message : "");
    }

    private static String getErrorString(String relativePath, String message)
    {
        return relativePath + " is not valid" + (null != message ? ": " + message : "");
    }
}
