/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

import org.apache.xmlbeans.XmlError;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/*
* User: adam
* Date: Oct 5, 2009
* Time: 9:15:42 PM
*/
public class XmlValidationException extends Exception
{
    private final Collection<XmlError> _errorList;

    public XmlValidationException(Collection<XmlError> errorList, String schemaName, @Nullable String additionalMessage)
    {
        super("Document does not conform to its XML schema, " + schemaName + (null != additionalMessage ? " (" + additionalMessage + ")" : ""));
        _errorList = errorList;
    }

    public Collection<XmlError> getErrorList()
    {
        return _errorList;
    }

    public String getDetails()
    {
        StringBuilder sb = new StringBuilder();

        for (XmlError error : _errorList)
            sb.append("line ").append(error.getLine()).append(": ").append(error.getMessage()).append('\n');

        return sb.toString();
    }

    @Override
    public String toString()
    {
        return super.toString() + "\n\n" + getDetails();
    }
}
