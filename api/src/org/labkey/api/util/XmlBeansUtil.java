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

import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.XmlTokenSource;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.settings.LookAndFeelProperties;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;

public class XmlBeansUtil
{
    private XmlBeansUtil()
    {
    }

    // Standard options used by folder export
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

    /** XML parsing factories preconfigured to prevent XML external entity references (XXE) */
    public static final SAXParserFactory SAX_PARSER_FACTORY;
    public static final XMLInputFactory XML_INPUT_FACTORY;
    public static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY;

    static
    {
        XML_INPUT_FACTORY = XMLInputFactory.newInstance();
        XML_INPUT_FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        XML_INPUT_FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        SAX_PARSER_FACTORY = SAXParserFactory.newInstance();
        try
        {
            SAX_PARSER_FACTORY.setNamespaceAware(true);
            SAX_PARSER_FACTORY.setFeature("http://xml.org/sax/features/validation", false);
            SAX_PARSER_FACTORY.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            // Disable features that could lead to XXE or other vulnerabilities
            // Keep in sync with ModuleArchive.nameFromModuleXML()
            SAX_PARSER_FACTORY.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            SAX_PARSER_FACTORY.setFeature("http://xml.org/sax/features/external-general-entities", false);
            SAX_PARSER_FACTORY.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            SAX_PARSER_FACTORY.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();
            DOCUMENT_BUILDER_FACTORY.setNamespaceAware(true);
            DOCUMENT_BUILDER_FACTORY.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DOCUMENT_BUILDER_FACTORY.setFeature("http://xml.org/sax/features/external-general-entities", false);
            DOCUMENT_BUILDER_FACTORY.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DOCUMENT_BUILDER_FACTORY.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DOCUMENT_BUILDER_FACTORY.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DOCUMENT_BUILDER_FACTORY.setXIncludeAware(false);
            DOCUMENT_BUILDER_FACTORY.setExpandEntityReferences(false);
        }
        catch (ParserConfigurationException | SAXException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }
}
