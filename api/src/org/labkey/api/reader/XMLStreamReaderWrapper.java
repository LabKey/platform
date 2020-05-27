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

    @Override
    public void close() throws XMLStreamException
    {
        streamReader.close();
    }

    @Override
    public int getAttributeCount()
    {
        return streamReader.getAttributeCount();
    }

    @Override
    public String getAttributeLocalName(int i)
    {
        return streamReader.getAttributeLocalName(i);
    }

    @Override
    public QName getAttributeName(int i)
    {
        return streamReader.getAttributeName(i);
    }

    @Override
    public String getAttributeNamespace(int i)
    {
        return streamReader.getAttributeNamespace(i);
    }

    @Override
    public String getAttributePrefix(int i)
    {
        return streamReader.getAttributePrefix(i);
    }

    @Override
    public String getAttributeType(int i)
    {
        return streamReader.getAttributeType(i);
    }

    @Override
    public String getAttributeValue(int i)
    {
        return streamReader.getAttributeValue(i);
    }

    @Override
    public String getAttributeValue(String s, String s1)
    {
        return streamReader.getAttributeValue(s, s1);
    }

    @Override
    public String getCharacterEncodingScheme()
    {
        return streamReader.getCharacterEncodingScheme();
    }

    @Override
    public String getElementText() throws XMLStreamException
    {
        return streamReader.getElementText();
    }

    @Override
    public String getEncoding()
    {
        return streamReader.getEncoding();
    }

    @Override
    public int getEventType()
    {
        return streamReader.getEventType();
    }

    @Override
    public String getLocalName()
    {
        return streamReader.getLocalName();
    }

    @Override
    public Location getLocation()
    {
        return streamReader.getLocation();
    }

    @Override
    public QName getName()
    {
        return streamReader.getName();
    }

    @Override
    public NamespaceContext getNamespaceContext()
    {
        return streamReader.getNamespaceContext();
    }

    @Override
    public int getNamespaceCount()
    {
        return streamReader.getNamespaceCount();
    }

    @Override
    public String getNamespacePrefix(int i)
    {
        return streamReader.getNamespacePrefix(i);
    }

    @Override
    public String getNamespaceURI()
    {
        return streamReader.getNamespaceURI();
    }

    @Override
    public String getNamespaceURI(int i)
    {
        return streamReader.getNamespaceURI(i);
    }

    @Override
    public String getNamespaceURI(String s)
    {
        return streamReader.getNamespaceURI(s);
    }

    @Override
    public String getPIData()
    {
        return streamReader.getPIData();
    }

    @Override
    public String getPITarget()
    {
        return streamReader.getPITarget();
    }

    @Override
    public String getPrefix()
    {
        return streamReader.getPrefix();
    }

    @Override
    public Object getProperty(String s) throws IllegalArgumentException
    {
        return streamReader.getProperty(s);
    }

    @Override
    public String getText()
    {
        return streamReader.getText();
    }

    @Override
    public char[] getTextCharacters()
    {
        return streamReader.getTextCharacters();
    }

    @Override
    public int getTextCharacters(int i, char[] chars, int i1, int i2) throws XMLStreamException
    {
        return streamReader.getTextCharacters(i, chars, i1, i2);
    }

    @Override
    public int getTextLength()
    {
        return streamReader.getTextLength();
    }

    @Override
    public int getTextStart()
    {
        return streamReader.getTextStart();
    }

    @Override
    public String getVersion()
    {
        return streamReader.getVersion();
    }

    @Override
    public boolean hasName()
    {
        return streamReader.hasName();
    }

    @Override
    public boolean hasNext() throws XMLStreamException
    {
        return streamReader.hasNext();
    }

    @Override
    public boolean hasText()
    {
        return streamReader.hasText();
    }

    @Override
    public boolean isAttributeSpecified(int i)
    {
        return streamReader.isAttributeSpecified(i);
    }

    @Override
    public boolean isCharacters()
    {
        return streamReader.isCharacters();
    }

    @Override
    public boolean isEndElement()
    {
        return streamReader.isEndElement();
    }

    @Override
    public boolean isStandalone()
    {
        return streamReader.isStandalone();
    }

    @Override
    public boolean isStartElement()
    {
        return streamReader.isStartElement();
    }

    @Override
    public boolean isWhiteSpace()
    {
        return streamReader.isWhiteSpace();
    }

    @Override
    public int next() throws XMLStreamException
    {
        return streamReader.next();
    }

    @Override
    public int nextTag() throws XMLStreamException
    {
        return streamReader.nextTag();
    }

    @Override
    public void require(int i, String s, String s1) throws XMLStreamException
    {
        streamReader.require(i, s, s1);
    }

    @Override
    public boolean standaloneSet()
    {
        return streamReader.standaloneSet();
    }
}
