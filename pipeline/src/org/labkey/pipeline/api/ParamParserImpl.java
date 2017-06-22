/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.pipeline.api;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.util.StringUtilsLabKey;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.*;

/**
 * <code>InputParser</code> is used to parse a set of name-value pair
 * input parameters from a XML document.  The document uses the BioML
 * document format, with values expressed as &lt;note&gt; tags, as in
 * X! Tandem input files.
 *
 * See the X! Tandem API at:
 *
 * http://www.thegpm.org/TANDEM/api/index.html
 *
 * @author brendanx
 */

public class ParamParserImpl implements ParamParser
{
    private static Logger _log = Logger.getLogger(ParamParserImpl.class);

    private static String TAG_BIOML = "bioml";
    private static String TAG_NOTE = "note";
    private static String ATTR_LABEL = "label";
    private static String ATTR_TYPE = "type";
    private static String VAL_INPUT = "input";

    private Document _doc;
    private Validator _validator;
    private List<Error> _errors;

    static public class ErrorImpl implements Error
    {
        String _message;
        int _line;
        int _column;

        public ErrorImpl(String message)
        {
            this(message, 0, 0);
        }

        public ErrorImpl(String message, int line, int column)
        {
            _message = message;
            _line = line;
            _column = column;
        }

        public ErrorImpl(SAXParseException spe)
        {
            this(spe.getLocalizedMessage(), spe.getLineNumber(), spe.getColumnNumber());
        }

        public String getMessage()
        {
            return _message;
        }

        public int getLine()
        {
            return _line;
        }

        public int getColumn()
        {
            return _column;
        }
    }

    protected void addError(Error error)
    {
        if (_errors == null)
            _errors = new ArrayList<>();
        _errors.add(error);
    }

    public void parse(InputStream inputStream)
    {
        try
        {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(false);
            DocumentBuilder db = dbf.newDocumentBuilder();

            InputSource source = new InputSource(new InputStreamReader(inputStream));
            _doc = db.parse(source);
            _doc.setXmlStandalone(true);  // Added to help with new Transformer-based getXML()
            validateDocument();
        }
        catch (SAXParseException e)
        {
            // Subtract 1 from the line number, since we added the DOCTYPE line
            addError(new ErrorImpl(e.getMessage(), e.getLineNumber(), e.getColumnNumber()));
        }
        catch (Exception e)
        {
            addError(new ErrorImpl(e.toString()));
        }
        finally
        {
            try { inputStream.close(); } catch (IOException ignored) {}
        }
    }

    public void setValidator(Validator validator)
    {
        _validator = validator;
    }

    public void addError(String paramName, String message)
    {
        // TODO: use the paramName
        addError(new ErrorImpl(message));
    }

    public Error[] getErrors()
    {
        if (_errors == null || _errors.size() == 0)
            return null;
        return _errors.toArray(new ErrorImpl[_errors.size()]);
    }

    public String getXML()
    {
        // If nothing parsed yet, return the empty parameter set.
        if (_doc == null)
            return getXMLFromMap(new HashMap<>());

        try
        {
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer trans = factory.newTransformer();
            trans.setOutputProperty(OutputKeys.INDENT, "yes");
            trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            //create string from xml tree
            StringWriter sw = new StringWriter();
            StreamResult result = new StreamResult(sw);
            DOMSource source = new DOMSource(_doc);
            trans.transform(source, result);

            return sw.toString();
        }
        catch (TransformerException e)
        {
            _log.error("Failure writing DOM document to string.", e);
        }

        return null;
    }


    /**
     * Override this function to further validate specific parameters.
     */
    protected void validateDocument()
    {
        Element el = _doc.getDocumentElement();
        if (!TAG_BIOML.equals(el.getTagName()))
            addError(new ErrorImpl("Root tag name should be 'bioml'"));
        NodeList notes = el.getChildNodes();
        for (int i = 0; i < notes.getLength(); i++)
        {
            Node child = notes.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE)
                continue;
            Element elNote = (Element) child;
            if (!TAG_NOTE.equals(elNote.getNodeName()))
            {
                addError(new ErrorImpl("Tag '" + elNote.getNodeName() + "' not supported."));
                continue;
            }

            String type = elNote.getAttribute(ATTR_TYPE);
            if (type == null || type.length() == 0 || "description".equals(type))
                continue;

            if (!VAL_INPUT.equals(type))
            {
                addError(new ErrorImpl("Note type '" + type + "' not supported."));
                continue;
            }
        }

        if (_validator != null)
            _validator.validate(this);
    }

    public String getInputParameter(String name)
    {
        Element el = _doc.getDocumentElement();
        NodeList notes = el.getElementsByTagName("note");
        for (int i = 0; i < notes.getLength(); i++)
        {
            Element elNote = (Element) notes.item(i);
            if (isInputParameterElement(name, elNote))
                return elNote.getTextContent();
        }
        return null;
    }

    public void setInputParameter(String name, String value)
    {
        setInputParameter(name, value, null);
    }

    public void setInputParameter(String name, String value, String before)
    {
        removeInputParameter(name);

        Element el = _doc.getDocumentElement();
        Element elParameter = _doc.createElement(TAG_NOTE);
        elParameter.setAttribute(ATTR_TYPE, VAL_INPUT);
        elParameter.setAttribute(ATTR_LABEL, name);
        elParameter.setTextContent(value);

        Node beforeNode = null;
        if (before != null)
        {
            NodeList notes = el.getElementsByTagName(TAG_NOTE);
            for (int i = 0; i < notes.getLength(); i++)
            {
                Element elNote = (Element) notes.item(i);
                if (isInputParameterElement(name, elNote))
                {
                    beforeNode = elNote;
                    break;
                }
            }
        }

        if (beforeNode == null)
            el.appendChild(elParameter);
        else
            el.insertBefore(elParameter, beforeNode);
    }

    @Override
    public void addInputParameters(Map<String, Object> parameters)
    {
        parameters.forEach((key, value) -> setInputParameter(key, value == null ? null : value.toString()));
    }

    public String removeInputParameter(String name)
    {
        String value = null;
        Element el = _doc.getDocumentElement();
        NodeList notes = el.getElementsByTagName(TAG_NOTE);
        for (int i = 0; i < notes.getLength(); i++)
        {
            Element elNote = (Element) notes.item(i);
            if (isInputParameterElement(name, elNote))
            {
                value = elNote.getTextContent();
                el.removeChild(elNote);
                break;
            }
        }
        return value;
    }

    public String[] getInputParameterNames()
    {
        ArrayList<String> names = new ArrayList<>();
        Element el = _doc.getDocumentElement();
        NodeList notes = el.getElementsByTagName(TAG_NOTE);
        for (int i = 0; i < notes.getLength(); i++)
        {
            Element elNote = (Element) notes.item(i);
            if (VAL_INPUT.equals(elNote.getAttribute(ATTR_TYPE)))
            {
                names.add(elNote.getAttribute(ATTR_LABEL));
            }
        }
        return names.toArray(new String[names.size()]);
    }

    public Map<String, String> getInputParameters()
    {
        Map<String, String> parameters = new HashMap<>();
        if (_doc != null)
        {
            Element el = _doc.getDocumentElement();
            NodeList notes = el.getElementsByTagName(TAG_NOTE);
            for (int i = 0; i < notes.getLength(); i++)
            {
                Element elNote = (Element) notes.item(i);
                if (VAL_INPUT.equals(elNote.getAttribute(ATTR_TYPE)))
                {
                    parameters.put(elNote.getAttribute(ATTR_LABEL), elNote.getTextContent());
                }
            }
        }

        return parameters;
    }

    private boolean isInputParameterElement(String name, Element elNote)
    {
        String type = elNote.getAttribute(ATTR_TYPE);
        return (VAL_INPUT.equals(type) && name.equals(elNote.getAttribute(ATTR_LABEL)));
    }

    public String getXMLFromMap(Map<String, String> params)
    {
        String xmlEmpty = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<bioml>\n" +
                "</bioml>";
        parse(new ByteArrayInputStream(xmlEmpty.getBytes(StringUtilsLabKey.DEFAULT_CHARSET)));
        String[] keys = params.keySet().toArray(new String[params.size()]);
        Arrays.sort(keys);
        for (String key : keys)
            setInputParameter(key, params.get(key));

        return getXML();
    }

    public void writeFromMap(Map<String, String> params, File fileDest) throws IOException
    {
        try (BufferedWriter inputWriter = new BufferedWriter(new FileWriter(fileDest)))
        {
            String xml = getXMLFromMap(params);
            _log.debug("Writing " + params.size() + " parameters (" + fileDest + "):");
            _log.debug(xml);
            inputWriter.write(xml);
        }
    }
}
