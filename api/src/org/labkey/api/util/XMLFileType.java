/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Convenience class for creating <code>FileType</code>s for xml files.
 * The XMLFileType matches by parsing the root XML element namespace and name.
 *
 * User: kevink
 * Date: 10/1/12
 */
public class XMLFileType extends FileType
{
    private String _namespace;
    private Collection<String> _rootElements;

    public XMLFileType(String namespaceURI, String rootElement)
    {
        this(namespaceURI, Collections.singleton(rootElement));
    }

    public XMLFileType(String namespaceURI, Collection<String> rootElements)
    {
        super(".xml");
        _namespace = namespaceURI;
        _rootElements = rootElements;
    }

    @Override
    public boolean isHeaderMatch(@NotNull byte[] header)
    {
        ByteArrayInputStream bais = null;
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = null;
        try
        {
            bais = new ByteArrayInputStream(header);
            reader = factory.createXMLStreamReader(bais);
            return isHeaderMatch(reader);
        }
        catch (XMLStreamException e)
        {
            return false;
        }
        finally
        {
            if (reader != null)
                try { reader.close(); } catch (Exception e) { }
            
            IOUtils.closeQuietly(bais);
        }
    }

    private boolean isHeaderMatch(XMLStreamReader reader) throws XMLStreamException
    {
        while (reader.hasNext())
        {
            switch (reader.next())
            {
                case XMLStreamConstants.START_ELEMENT:
                    QName name = reader.getName();
                    return isQNameMatch(name);

                // We only consider the root element
                case XMLStreamConstants.END_DOCUMENT:
                case XMLStreamConstants.END_ELEMENT:
                    break;
            }
        }

        return false;
    }

    private boolean isQNameMatch(QName name)
    {
        return Objects.equals(_namespace, name.getNamespaceURI()) &&
               _rootElements.contains(name.getLocalPart());
    }
}
