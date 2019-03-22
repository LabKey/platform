/*
 * Copyright (c) 2009-2015 LabKey Corporation
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

import org.apache.xmlbeans.*;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.settings.LookAndFeelProperties;

import java.util.Collection;
import java.util.Date;
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
        options.setSaveAggressiveNamespaces(); // causes the saver to reduce the number of namespace declarations

        return options;
    }

    // Standard options used for parsing to enable validation.
    public static XmlOptions getDefaultParseOptions()
    {
        XmlOptions options = new XmlOptions();
        options.setLoadLineNumbers();

        return options;
    }

    @Deprecated  // Use the version below, and pass in details (filename, etc.)
    public static void validateXmlDocument(XmlObject doc) throws XmlValidationException
    {
        validateXmlDocument(doc, null);
    }

    // Details can be filename, etc. to help admin narrow down the source of the problem
    public static void validateXmlDocument(XmlObject doc, @Nullable String details) throws XmlValidationException
    {
        XmlOptions options = getDefaultParseOptions();
        Collection<XmlError> errorList = new LinkedList<>();
        options.setErrorListener(errorList);

        if (!doc.validate(options))
            throw new XmlValidationException(errorList, doc.schemaType().toString(), details);
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

    // Insert standard export comment explaining where the data lives, who exported it, and when
    public static void addStandardExportComment(XmlTokenSource doc, Container c, User user)
    {
        String urlString = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c).getURIString();
        if (urlString.endsWith("?"))
            urlString = urlString.substring(0, urlString.length() - 1);
        String shortName = LookAndFeelProperties.getInstance(c).getShortName();
        String comment = "Exported from " + shortName + " at " + urlString + " by " + user.getFriendlyName() + " on " + new Date();
        addComment(doc, comment);
    }

    public static void addComment(XmlTokenSource doc, String comment)
    {
        XmlCursor cursor = doc.newCursor();
        cursor.insertComment(comment);
        cursor.dispose();
    }
}
