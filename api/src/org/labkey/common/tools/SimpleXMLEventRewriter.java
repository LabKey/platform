/*
 * Copyright (c) 2006-2008 Fred Hutchinson Cancer Research Center
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * Helper class to pull events and rewrite them with modification or with new
 * elements interspersed. Override handleStartElement, handleEndElement, etc.,
 * instantiate, and call rewrite(). See the Q3.java code for an example of
 * rewriting a pepXML file.
 */
public class SimpleXMLEventRewriter
{
    private static Logger _log = Logger.getLogger(SimpleXMLEventRewriter.class);

    private static XMLEventFactory eventFactory = XMLEventFactory.newInstance();
    private static DatatypeFactory typeFactory = null;

    static
    {
        // Rethrow any DatatypeFactory setup errors
        try
        {
            typeFactory = DatatypeFactory.newInstance();
        }
        catch (DatatypeConfigurationException e)
        {
            throw new ExceptionInInitializerError(e);
        }
    }

    private InputStream in = null;
    private PrintStream out = null;
    private String inFileName = null;
    private String outFileName = null;
    private XMLEventReader parser = null;
    private XMLEventWriter writer = null;

    public SimpleXMLEventRewriter(String inFileName, String outFileName)
    {
        this.inFileName = inFileName;
        this.outFileName = outFileName;
    }

    /**
     *
     */
    private static XMLEventReader createParser(InputStream in)
        throws XMLStreamException
    {
        XMLInputFactory inFactory = XMLInputFactory.newInstance();
        inFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return inFactory.createXMLEventReader(in);
    }

    /**
     *
     */
    private static XMLEventWriter createWriter(OutputStream out)
        throws XMLStreamException
    {
        XMLOutputFactory outFactory = XMLOutputFactory.newInstance();
        return outFactory.createXMLEventWriter(out);
    }

    protected void emit(String content)
        throws XMLStreamException
    {
        writer.flush();
        out.println(content);
    }

    public void add(SimpleElement element)
        throws XMLStreamException
    {
        writer.add(element.getEvent());
    }

    public void add(XMLEvent event)
        throws XMLStreamException
    {
        writer.add(event);
    }

    public void addNewline()
        throws XMLStreamException
    {
        // TODO: Should really make line end style consistent for entire file
        writer.add(eventFactory.createSpace("\n"));
    }

    public void handleDefault(XMLEvent event)
        throws XMLStreamException
    {
        add(event);
    }

    public void handleDiscard(XMLEvent event)
        throws XMLStreamException
    {
        // Discard; take no action
    }

    public void handleStartDocument(XMLEvent event)
        throws XMLStreamException
    {
        add(event);
    }

    public void handleEndDocument(XMLEvent event)
        throws XMLStreamException
    {
        add(event);
    }

    public void handleStartElement(StartElement event)
        throws XMLStreamException
    {
        add(event);
    }

    public void handleEndElement(EndElement event)
        throws XMLStreamException
    {
        add(event);
    }

    /**
     *
     */
    public void rewrite()
        throws IOException, XMLStreamException
    {
        try
        {
            in = new FileInputStream(inFileName);
            out = new PrintStream(outFileName);

            parser = createParser(in);
            writer = createWriter(out);

            while (parser.hasNext())
            {
                XMLEvent event = parser.nextEvent();
                switch (event.getEventType())
                {
                case XMLStreamConstants.ATTRIBUTE:
                case XMLStreamConstants.CDATA:
                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.COMMENT:
                case XMLStreamConstants.DTD:
                case XMLStreamConstants.ENTITY_DECLARATION:
                case XMLStreamConstants.ENTITY_REFERENCE:
                case XMLStreamConstants.NAMESPACE:
                case XMLStreamConstants.NOTATION_DECLARATION:
                case XMLStreamConstants.PROCESSING_INSTRUCTION:
                case XMLStreamConstants.SPACE:
                    handleDefault(event);
                    break;
                case XMLStreamConstants.START_DOCUMENT:
                    handleStartDocument(event);
                    break;
                case XMLStreamConstants.END_DOCUMENT:
                    handleEndDocument(event);
                    break;
                case XMLStreamConstants.START_ELEMENT:
                    handleStartElement(event.asStartElement());
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    handleEndElement(event.asEndElement());
                    break;
                default:
                    _log.error("Unrecognized event type " + event.getEventType());
                    throw new XMLStreamException("Unrecognized event type " + event.getEventType());
                }
            }
        }
        finally
        {
            close();
        }
    }

    /**
     *
     */
    public void close()
    {
        try {
            if (null != parser) parser.close();
        } catch (Exception e) {
        } finally {
            parser = null;
        }

        try {
            if (null != in) in.close();
        } catch (Exception e) {
        } finally {
            in = null;
        }

        try {
            if (null != writer) writer.close();
        } catch (Exception e) {
        } finally {
            writer = null;
        }

        try {
            if (null != out) out.close();
        } catch (Exception e) {
        } finally {
            out = null;
        }
    }

    /**
     * 
     */
    protected static interface SimpleElement
    {
        XMLEvent getEvent();
    }

    /**
     * Helpers for consing up StartElements
     */
    public static class SimpleStartElement implements SimpleElement
    {
        private String name = null;
        private ArrayList<Attribute> attrs = null;

        /**
         * Create a start tag with the given name
         */
        public SimpleStartElement(String name)
        {
            this.name = name;
        }

        /**
         * Add an array of attributes to this start tag
         */
        public SimpleStartElement(String name, Attribute[] attrs)
        {
            this.name = name;
            this.attrs = new ArrayList<Attribute>(Arrays.asList(attrs));
        }

        /**
         * Add a string valued attribute
         */
        public void addAttribute(String name, String value)
            throws XMLStreamException
        {
            if (null == attrs)
                attrs = new ArrayList<Attribute>();
            attrs.add(eventFactory.createAttribute(name, value));
        }

        /**
         * Add a char valued attribute
         */
        public void addAttribute(String name, char value)
            throws XMLStreamException
        {
            addAttribute(name, "" + value);
        }

        /**
         * Add a int valued attribute
         */
        public void addAttribute(String name, int value)
            throws XMLStreamException
        {
            addAttribute(name, "" + value);
        }

        /**
         * Add a floatvalued attribute
         */
        public void addAttribute(String name, float value)
            throws XMLStreamException
        {
            addAttribute(name, String.format("%f", value));
        }

        /**
         * Add a double valued attribute
         */
        public void addAttribute(String name, double value)
            throws XMLStreamException
        {
            addAttribute(name, String.format("%f", value));
        }

        /**
         * Add a timestamp attribute
         * @throws NullPointerException if <code>value</code> is <code>null</code>.
         */
        public void addAttribute(String name, GregorianCalendar value)
            throws XMLStreamException
        {
            addAttribute(name, "" + typeFactory.newXMLGregorianCalendar(value).toXMLFormat());
        }

        /**
         * Create an event for this element
         * @return XMLEvent for this StartElement
         */
        public XMLEvent getEvent()
        {
            return eventFactory.createStartElement("", "", name, attrs.iterator(), null);
        }
    }

    /**
     * Helpers for creating end elements
     */
    public static class SimpleEndElement implements SimpleElement
    {
        private String name = null;

        /**
         * Create an end element with the given name
         */
        public SimpleEndElement(String name)
        {
            this.name = name;
        }

        /**
         * Create an event for this element
         * @return XMLEvent for this StartElement
         */
        public XMLEvent getEvent()
        {
            return eventFactory.createEndElement("", "", name);
        }
    }

    /**
     * Create a space element
     */
    public static class SimpleSpaceElement implements SimpleElement
    {
        private String content = null;

        public SimpleSpaceElement(String content)
        {
            this.content = content;
        }

        public XMLEvent getEvent()
        {
            return eventFactory.createSpace(content);
        }
    }

    /**
     * Convert an XML time element to a Date
     */
    public static Date convertXMLTimeToDate(String lexTime)
    {
        XMLGregorianCalendar xgc = typeFactory.newXMLGregorianCalendar(lexTime);
        return xgc.toGregorianCalendar().getTime();
    }

    /**
     *
     */
    public static void main(String[] av)
    {
        try
        {
            SimpleXMLEventRewriter s = new SimpleXMLEventRewriter("/home/mfitzgib/AcrylForMarc/data/L_04_IPAS0012_AX01_RP_SG04to10.pep.xml", "foo.pep.xml");
            s.rewrite();
            s.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

}
