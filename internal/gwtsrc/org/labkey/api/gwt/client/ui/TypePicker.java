package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.ui.ListBox;

import java.util.Map;
import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 23, 2007
 * Time: 5:16:30 PM
 */
public class TypePicker extends ListBox
{
    final static String xsdString = "http://www.w3.org/2001/XMLSchema#string";
    final static String xsdMultiLine = "http://www.w3.org/2001/XMLSchema#multiLine";
    final static String xsdBoolean = "http://www.w3.org/2001/XMLSchema#boolean";
    final static String xsdInt =  "http://www.w3.org/2001/XMLSchema#int";
    final static String xsdDouble = "http://www.w3.org/2001/XMLSchema#double";
    final static String xsdDateTime = "http://www.w3.org/2001/XMLSchema#dateTime";
    final static String xsdFileLink = "http://cpas.fhcrc.org/exp/xml#fileLink";
    final static String xsdAttachment = "http://www.labkey.org/exp/xml#attachment";


    static Map synonyms = new HashMap();

    static
    {
        synonyms.put(xsdString.toLowerCase(), xsdString);
        synonyms.put(xsdMultiLine.toLowerCase(), xsdMultiLine);
        synonyms.put(xsdBoolean.toLowerCase(), xsdBoolean);
        synonyms.put(xsdInt.toLowerCase(), xsdInt);
        synonyms.put(xsdDouble.toLowerCase(), xsdDouble);
        synonyms.put(xsdDateTime.toLowerCase(), xsdDateTime);
        synonyms.put(xsdFileLink.toLowerCase(), xsdFileLink);
        synonyms.put(xsdAttachment.toLowerCase(), xsdAttachment);
        synonyms.put("string", xsdString);
        synonyms.put("boolean", xsdBoolean);
        synonyms.put("integer", xsdInt);
        synonyms.put("int", xsdInt);
        synonyms.put("double", xsdDouble);
        synonyms.put("file", xsdFileLink);
        synonyms.put("datetime", xsdDateTime);
        synonyms.put("xsd:string", xsdString);
        synonyms.put("xsd:boolean", xsdBoolean);
        synonyms.put("xsd:int", xsdInt);
        synonyms.put("xsd:double", xsdDouble);
        synonyms.put("xsd:datetime", xsdDateTime);
        synonyms.put("xsd:file", xsdFileLink);
        synonyms.put("xsd:attachment", xsdAttachment);
    }

    public TypePicker(boolean allowFileLinkProperties, boolean allowAttachmentProperties)
    {
        addItem("Text (String)", xsdString);
        addItem("Multi-Line Text", xsdMultiLine);
        addItem("Boolean", xsdBoolean);
        addItem("Integer", xsdInt);
        addItem("Number (Double)", xsdDouble);
        addItem("DateTime", xsdDateTime);
        if (allowFileLinkProperties)
            addItem("File", xsdFileLink);
        if (allowAttachmentProperties)
            addItem("Attachment", xsdAttachment);
    }

    public TypePicker(String rangeURI, boolean allowFileLinkProperties, boolean allowAttachmentProperties)
    {
        this(allowFileLinkProperties, allowAttachmentProperties);
        setRangeURI(rangeURI);
    }

    public void setRangeURI(String uri)
    {
        String rangeURI = (String)synonyms.get(String.valueOf(uri).toLowerCase());

        int select = 0;
        if (rangeURI != null)
        {
            for (int i=0 ; i<getItemCount(); i++)
            {
                if (rangeURI.equalsIgnoreCase(getValue(i)))
                {
                    select = i;
                    break;
                }
            }
        }
        setSelectedIndex(select);
    }

    public String getRangeURI()
    {
        return getValue(getSelectedIndex());
    }

    public String getDisplayText()
    {
        return getItemText(getSelectedIndex());
    }
}
