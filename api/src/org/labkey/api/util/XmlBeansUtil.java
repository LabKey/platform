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
package org.labkey.api.util;

import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;

import java.util.Collection;
import java.util.LinkedList;

/**
 * User: adam
 * Date: May 25, 2009
 * Time: 9:26:59 AM
 */
public class XmlBeansUtil
{
    private XmlBeansUtil()
    {
    }

    // Standard options used by study export.
    public static XmlOptions getDefaultSaveOptions()
    {
        XmlOptions options = new XmlOptions();
        options.setSavePrettyPrint();
        options.setUseDefaultNamespace();
        options.setCharacterEncoding("UTF-8");
        options.setSaveCDataEntityCountThreshold(0);
        options.setSaveCDataLengthThreshold(0);

        return options;
    }

    // Standard options used for parsing to enable validation.
    public static XmlOptions getDefaultParseOptions()
    {
        XmlOptions options = new XmlOptions();
        options.setLoadLineNumbers();

        return options;
    }

    public static void validateXmlDocument(XmlObject doc) throws XmlValidationException
    {
        XmlOptions options = new XmlOptions();
        Collection<XmlError> errorList = new LinkedList<XmlError>();
        options.setErrorListener(errorList);

        if (!doc.validate(options))
            throw new XmlValidationException(errorList);
    }

    public static String getErrorMessage(XmlException ex)
    {
        if (ex.getError() != null)
            return getErrorMessage(ex.getError());
        return ex.getMessage();
    }

    public static String getErrorMessage(XmlError error)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(error.toString());
        if (error.getLine() > 0)
        {
            sb.append(" (line ").append(error.getLine());
            if (error.getColumn() > 0)
                sb.append(", column ").append(error.getColumn());
            sb.append(")");
        }
        return sb.toString();
    }
}
