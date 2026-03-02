/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.labkey.api.util.XmlBeansUtil;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.InputStream;

/**
 * User: arauch
 * Date: May 25, 2005
 * Time: 12:16:40 PM
 */
public class SimpleXMLStreamReader extends XMLStreamReaderWrapper
{
    public SimpleXMLStreamReader(InputStream stream) throws XMLStreamException
    {
        super(XmlBeansUtil.XML_INPUT_FACTORY.createXMLStreamReader(stream));
    }


    public boolean skipToStart(String element) throws XMLStreamException
    {
        return skipTo(element, true);
    }


    public boolean skipToEnd(String element) throws XMLStreamException
    {
        return skipTo(element, false);
    }


    private boolean skipTo(String element, boolean start) throws XMLStreamException
    {
        while (hasNext())
        {
            next();

            if ((start ? isStartElement() : isEndElement()) && getLocalName().equals(element))
                return true;
        }

        return false;
    }


    public String getHref()
            throws XMLStreamException
    {
        while (hasNext())
        {
            int event = next();

            if (event == XMLStreamConstants.START_ELEMENT && "A".equals(getLocalName()))
                return getAttributeValue("", "HREF");
        }
        // UNDONE: Raise exception instead of returning null
        return null;
    }
}
