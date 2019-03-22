/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.reader;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.Location;
import javax.xml.namespace.QName;
import javax.xml.namespace.NamespaceContext;

/**
 * User: arauch
 * Date: May 25, 2005
 * Time: 11:34:10 AM
 */
public class XMLStreamReaderWrapper implements XMLStreamReader
{
    private XMLStreamReader streamReader;

    public XMLStreamReaderWrapper(XMLStreamReader streamReader)
    {
        this.streamReader = streamReader;
    }

    public void close() throws XMLStreamException
    {
        streamReader.close();
    }

    public int getAttributeCount()
    {
        return streamReader.getAttributeCount();
    }

    public String getAttributeLocalName(int i)
    {
        return streamReader.getAttributeLocalName(i);
    }

    public QName getAttributeName(int i)
    {
        return streamReader.getAttributeName(i);
    }

    public String getAttributeNamespace(int i)
    {
        return streamReader.getAttributeNamespace(i);
    }

    public String getAttributePrefix(int i)
    {
        return streamReader.getAttributePrefix(i);
    }

    public String getAttributeType(int i)
    {
        return streamReader.getAttributeType(i);
    }

    public String getAttributeValue(int i)
    {
        return streamReader.getAttributeValue(i);
    }

    public String getAttributeValue(String s, String s1)
    {
        return streamReader.getAttributeValue(s, s1);
    }

    public String getCharacterEncodingScheme()
    {
        return streamReader.getCharacterEncodingScheme();
    }

    public String getElementText() throws XMLStreamException
    {
        return streamReader.getElementText();
    }

    public String getEncoding()
    {
        return streamReader.getEncoding();
    }

    public int getEventType()
    {
        return streamReader.getEventType();
    }

    public String getLocalName()
    {
        return streamReader.getLocalName();
    }

    public Location getLocation()
    {
        return streamReader.getLocation();
    }

    public QName getName()
    {
        return streamReader.getName();
    }

    public NamespaceContext getNamespaceContext()
    {
        return streamReader.getNamespaceContext();
    }

    public int getNamespaceCount()
    {
        return streamReader.getNamespaceCount();
    }

    public String getNamespacePrefix(int i)
    {
        return streamReader.getNamespacePrefix(i);
    }

    public String getNamespaceURI()
    {
        return streamReader.getNamespaceURI();
    }

    public String getNamespaceURI(int i)
    {
        return streamReader.getNamespaceURI(i);
    }

    public String getNamespaceURI(String s)
    {
        return streamReader.getNamespaceURI(s);
    }

    public String getPIData()
    {
        return streamReader.getPIData();
    }

    public String getPITarget()
    {
        return streamReader.getPITarget();
    }

    public String getPrefix()
    {
        return streamReader.getPrefix();
    }

    public Object getProperty(String s) throws IllegalArgumentException
    {
        return streamReader.getProperty(s);
    }

    public String getText()
    {
        return streamReader.getText();
    }

    public char[] getTextCharacters()
    {
        return streamReader.getTextCharacters();
    }

    public int getTextCharacters(int i, char[] chars, int i1, int i2) throws XMLStreamException
    {
        return streamReader.getTextCharacters(i, chars, i1, i2);
    }

    public int getTextLength()
    {
        return streamReader.getTextLength();
    }

    public int getTextStart()
    {
        return streamReader.getTextStart();
    }

    public String getVersion()
    {
        return streamReader.getVersion();
    }

    public boolean hasName()
    {
        return streamReader.hasName();
    }

    public boolean hasNext() throws XMLStreamException
    {
        return streamReader.hasNext();
    }

    public boolean hasText()
    {
        return streamReader.hasText();
    }

    public boolean isAttributeSpecified(int i)
    {
        return streamReader.isAttributeSpecified(i);
    }

    public boolean isCharacters()
    {
        return streamReader.isCharacters();
    }

    public boolean isEndElement()
    {
        return streamReader.isEndElement();
    }

    public boolean isStandalone()
    {
        return streamReader.isStandalone();
    }

    public boolean isStartElement()
    {
        return streamReader.isStartElement();
    }

    public boolean isWhiteSpace()
    {
        return streamReader.isWhiteSpace();
    }

    public int next() throws XMLStreamException
    {
        return streamReader.next();
    }

    public int nextTag() throws XMLStreamException
    {
        return streamReader.nextTag();
    }

    public void require(int i, String s, String s1) throws XMLStreamException
    {
        streamReader.require(i, s, s1);
    }

    public boolean standaloneSet()
    {
        return streamReader.standaloneSet();
    }
}
