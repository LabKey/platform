/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

package org.labkey.query;

import org.apache.log4j.Logger;
import org.labkey.api.util.XmlValidationException;

import java.io.File;

/*
* User: Dave
* Date: Jan 14, 2009
* Time: 12:43:18 PM
*/

/**
 * A bean that represents a custom view definition stored
 * in a module resource file. This is separate from ModuleCustomView
 * because that class cannot be cached, as it must hold a reference
 * to the source QueryDef, which holds a reference to the QueryView,
 * etc., etc.
 */
public class ModuleCustomViewDef extends CustomViewXmlReader
{
    private long _lastModified;

    public ModuleCustomViewDef(File sourceFile) throws XmlValidationException
    {
        super(sourceFile);

        _lastModified = sourceFile.lastModified();

        String fileName = _sourceFile.getName();
        assert fileName.length() >= XML_FILE_EXTENSION.length();

        if (fileName.length() > XML_FILE_EXTENSION.length())
        {
            // Module custom views always use the file name as the name
            _name = fileName.substring(0, fileName.length() - XML_FILE_EXTENSION.length());
        }
    }

    public boolean isStale()
    {
        return _sourceFile.lastModified() != _lastModified;
    }
}