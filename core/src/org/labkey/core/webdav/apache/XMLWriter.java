/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.core.webdav.apache;

import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.Writer;

/**
 * XMLWriter helper class.
 *
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 */
public class XMLWriter {


    boolean canonical = false;
    boolean qualifiedNames = false;

    // -------------------------------------------------------------- Constants


    /**
     * Opening tag.
     */
    public static final int OPENING = 0;


    /**
     * Closing tag.
     */
    public static final int CLOSING = 1;


    /**
     * Element with no content.
     */
    public static final int NO_CONTENT = 2;


    // ----------------------------------------------------- Instance Variables


    /**
     * Buffer.
     */
    protected StringBuffer buffer = new StringBuffer();


    /**
     * Writer.
     */
    protected Writer writer = null;


    // ----------------------------------------------------------- Constructors


    /**
     * Constructor.
     */
    public XMLWriter() {
    }


    /**
     * Constructor.
     */
    public XMLWriter(Writer writer)
    {
        this.writer = writer;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Retrieve generated XML.
     *
     * @return String containing the generated XML
     */
    public String toString() {
        return buffer.toString();
    }


    /**
     * Write property to the XML.
     *
     * @param namespace Namespace
     * @param namespaceInfo Namespace info
     * @param name Property name
     * @param value Property value
     */
    public void writeProperty(String namespace, String namespaceInfo,
                              String name, String value) {
        writeElement(namespace, namespaceInfo, name, OPENING);
        buffer.append(value);
        writeElement(namespace, namespaceInfo, name, CLOSING);

    }


    /**
     * Write property to the XML.
     *
     * @param namespace Namespace
     * @param name Property name
     * @param value Property value
     */
    public void writeProperty(@Nullable String namespace, String name, String value) {
        writeElement(namespace, name, OPENING);
        buffer.append(value);
        writeElement(namespace, name, CLOSING);
    }


    /**
     * Write property to the XML.
     *
     * @param namespace Namespace
     * @param name Property name
     */
    public void writeProperty(String namespace, String name) {
        writeElement(namespace, name, NO_CONTENT);
    }


    /**
     * Write an element.
     *
     * @param name Element name
     * @param namespace Namespace abbreviation
     * @param type Element type
     */
    public void writeElement(@Nullable String namespace, String name, int type) {
        writeElement(namespace, null, name, type);
    }


    /**
     * Write an element.
     *
     * @param namespace Namespace abbreviation
     * @param namespaceInfo Namespace info
     * @param name Element name
     * @param type Element type
     */
    public void writeElement(@Nullable String namespace, @Nullable String namespaceInfo,
                             String name, int type) {
        if ((namespace != null) && (namespace.length() > 0)) {
            switch (type) {
            case OPENING:
                if (namespaceInfo != null) {
                    buffer.append("<" + namespace + ":" + name + " xmlns:"
                                  + namespace + "=\""
                                  + namespaceInfo + "\">");
                } else {
                    buffer.append("<" + namespace + ":" + name + ">");
                }
                break;
            case CLOSING:
                buffer.append("</" + namespace + ":" + name + ">\n");
                break;
            case NO_CONTENT:
            default:
                if (namespaceInfo != null) {
                    buffer.append("<" + namespace + ":" + name + " xmlns:"
                                  + namespace + "=\""
                                  + namespaceInfo + "\"/>");
                } else {
                    buffer.append("<" + namespace + ":" + name + "/>");
                }
                break;
            }
        } else {
            switch (type) {
            case OPENING:
                buffer.append("<" + name + ">");
                break;
            case CLOSING:
                buffer.append("</" + name + ">\n");
                break;
            case NO_CONTENT:
            default:
                buffer.append("<" + name + "/>");
                break;
            }
        }
    }


    /**
     * Write text.
     *
     * @param text Text to append
     */
    public void writeText(String text) {
        buffer.append(text);
    }


    /**
     * Write data.
     *
     * @param data Data to append
     */
    public void writeData(String data) {
        buffer.append("<![CDATA[" + data + "]]>");
    }


    /**
     * Write XML Header.
     */
    public void writeXMLHeader() {
        buffer.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n");
    }


    /** Prints the specified node, recursively. */
    public void print(Node node) {

        // is there anything to do?
        if ( node == null ) {
            return;
        }

        int type = node.getNodeType();
        switch ( type ) {
            // print document
            case Node.DOCUMENT_NODE: {
                if ( !canonical ) {
                    String  Encoding = "UTF-8";
                    buffer.append("<?xml version=\"1.0\" encoding=\"" + Encoding + "\"?>\n");
                }
                print(((Document) node).getDocumentElement());
                break;
            }

            // print element with attributes
            case Node.ELEMENT_NODE: {
                buffer.append('<');
                if (this.qualifiedNames) {
                    buffer.append(node.getNodeName());
                } else {
                    buffer.append(node.getLocalName());
                }
                Attr attrs[] = sortAttributes(node.getAttributes());
                for ( int i = 0; i < attrs.length; i++ ) {
                    Attr attr = attrs[i];
                    buffer.append(' ');
                    if (this.qualifiedNames) {
                        buffer.append(attr.getNodeName());
                    } else {
                        buffer.append(attr.getLocalName());
                    }

                    buffer.append("=\"");
                    buffer.append(normalize(attr.getNodeValue()));
                    buffer.append('"');
                }
                buffer.append('>');
                NodeList children = node.getChildNodes();
                if ( children != null ) {
                    int len = children.getLength();
                    for ( int i = 0; i < len; i++ ) {
                        print(children.item(i));
                    }
                }
                break;
            }

            // handle entity reference nodes
            case Node.ENTITY_REFERENCE_NODE: {
                if ( canonical ) {
                    NodeList children = node.getChildNodes();
                    if ( children != null ) {
                        int len = children.getLength();
                        for ( int i = 0; i < len; i++ ) {
                            print(children.item(i));
                        }
                    }
                } else {
                    buffer.append('&');
                    if (this.qualifiedNames) {
                        buffer.append(node.getNodeName());
                    } else {
                        buffer.append(node.getLocalName());
                    }
                    buffer.append(';');
                }
                break;
            }

            // print cdata sections
            case Node.CDATA_SECTION_NODE: {
                if ( canonical ) {
                    buffer.append(normalize(node.getNodeValue()));
                } else {
                    buffer.append("<![CDATA[");
                    buffer.append(node.getNodeValue());
                    buffer.append("]]>");
                }
                break;
            }

            // print text
            case Node.TEXT_NODE: {
                buffer.append(normalize(node.getNodeValue()));
                break;
            }

            // print processing instruction
            case Node.PROCESSING_INSTRUCTION_NODE: {
                buffer.append("<?");
                if (this.qualifiedNames) {
                    buffer.append(node.getNodeName());
                } else {
                    buffer.append(node.getLocalName());
                }

                String data = node.getNodeValue();
                if ( data != null && data.length() > 0 ) {
                    buffer.append(' ');
                    buffer.append(data);
                }
                buffer.append("?>");
                break;
            }
        }

        if ( type == Node.ELEMENT_NODE ) {
            buffer.append("</");
            if (this.qualifiedNames) {
                buffer.append(node.getNodeName());
            } else {
                buffer.append(node.getLocalName());
            }
            buffer.append('>');
        }
    } // print(Node)


    protected Attr[] sortAttributes(NamedNodeMap attrs) {
        if (attrs == null) {
            return new Attr[0];
        }

        int len = attrs.getLength();
        Attr array[] = new Attr[len];
        for ( int i = 0; i < len; i++ ) {
            array[i] = (Attr)attrs.item(i);
        }
        for ( int i = 0; i < len - 1; i++ ) {
            String name = null;
            if (this.qualifiedNames) {
                name  = array[i].getNodeName();
            } else {
                name  = array[i].getLocalName();
            }
            int    index = i;
            for ( int j = i + 1; j < len; j++ ) {
                String curName = null;
                if (this.qualifiedNames) {
                    curName = array[j].getNodeName();
                } else {
                    curName = array[j].getLocalName();
                }
                if ( curName.compareTo(name) < 0 ) {
                    name  = curName;
                    index = j;
                }
            }
            if ( index != i ) {
                Attr temp    = array[i];
                array[i]     = array[index];
                array[index] = temp;
            }
        }

        return (array);

    } // sortAttributes(NamedNodeMap):Attr[]

    protected String normalize(String s) {
        if (s == null) {
            return "";
        }

        StringBuilder str = new StringBuilder();

        int len = s.length();
        for ( int i = 0; i < len; i++ ) {
            char ch = s.charAt(i);
            switch ( ch ) {
                case '<': {
                    str.append("&lt;");
                    break;
                }
                case '>': {
                    str.append("&gt;");
                    break;
                }
                case '&': {
                    str.append("&amp;");
                    break;
                }
                case '"': {
                    str.append("&quot;");
                    break;
                }
                case '\r':
                case '\n': {
                    if ( canonical ) {
                        str.append("&#");
                        str.append(Integer.toString(ch));
                        str.append(';');
                        break;
                    }
                    // else, default append char
                }
                //$FALL-THROUGH$
                default: {
                    str.append(ch);
                }
            }
        }

        return (str.toString());

    } // normalize(String):String



    /**
     * Send data and reinitializes buffer.
     */
    public void sendData() throws IOException
    {
        if (writer != null)
        {
            writer.write(buffer.toString());
            buffer = new StringBuffer();
        }
    }
}
