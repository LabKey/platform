/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.common.tools;

import org.apache.log4j.Logger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.regex.Pattern;

/**
 * User: arauch
 * Date: May 25, 2005
 * Time: 12:16:40 PM
 */
public class SimpleXMLStreamReader extends XMLStreamReaderWrapper
{
    private static Logger _log = Logger.getLogger(SimpleXMLStreamReader.class);
    private static Pattern _blankPattern = Pattern.compile("");

    public SimpleXMLStreamReader(InputStream stream) throws XMLStreamException
    {
        super(getFactory().createXMLStreamReader(stream));
    }


    // Make sure XMLStreamReader doesn't freak if it can't resolve DTD URL
    private static XMLInputFactory getFactory()
    {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return factory;
    }


    public void logElement()
    {
        _log.debug("----" + (getEventType() == XMLStreamConstants.END_ELEMENT ? "/" : "") + getLocalName() + "----");
        int count = getAttributeCount();

        for (int i = 0; i < count; i++)
            _log.debug(getAttributeLocalName(i) + "=" + getAttributeValue(i));
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


    public String getAllText() throws XMLStreamException
    {
        String token = "";

        // Skip until we find a character event
        while (hasNext())
        {
            int event = next();

            if (event == XMLStreamConstants.CHARACTERS)
            {
                // Characters can come in chunks... so loop until no more character events
                while (event == XMLStreamConstants.CHARACTERS)
                {
                    token += getText().trim();

                    if (!hasNext())
                        break;

                    event = next();
                }

                return token;
            }
        }

        return null;
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


    public String getAllText(Pattern validationExpression) throws XMLStreamException
    {
        String token = getAllText();

        if (validationExpression.matcher(token).matches())
        {
            return token;
        }
        else
        {
            _log.error("Unexpected token: " + token + " doesn't match " + validationExpression.pattern());
            return token;
        }
    }


    public void skipBlank()
            throws XMLStreamException
    {
        getAllText(_blankPattern);
    }
}
