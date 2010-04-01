/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
    public final static String xsdString = "http://www.w3.org/2001/XMLSchema#string";
    public final static String xsdMultiLine = "http://www.w3.org/2001/XMLSchema#multiLine";
    public final static String xsdBoolean = "http://www.w3.org/2001/XMLSchema#boolean";
    public final static String xsdInt =  "http://www.w3.org/2001/XMLSchema#int";
    public final static String xsdDouble = "http://www.w3.org/2001/XMLSchema#double";
    public final static String xsdDateTime = "http://www.w3.org/2001/XMLSchema#dateTime";
    public final static String xsdFileLink = "http://cpas.fhcrc.org/exp/xml#fileLink";
    public final static String xsdAttachment = "http://www.labkey.org/exp/xml#attachment";


    static Map<String,String> synonyms = new HashMap<String,String>();
    static Map<String,String> displayMap = new HashMap<String,String>();

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


        displayMap.put(xsdString, "Text (String)");
        displayMap.put(xsdMultiLine, "Multi-Line Text");
        displayMap.put(xsdBoolean, "Boolean");
        displayMap.put(xsdInt, "Integer");
        displayMap.put(xsdDouble, "Number (Double)");
        displayMap.put(xsdDateTime, "DateTime");
        displayMap.put(xsdFileLink, "File");
        displayMap.put(xsdAttachment, "Attachment");
    }


    public static String getDisplayString(String type)
    {
        String syn = synonyms.get(type);
        if (null != syn)
            type = syn;
        String display = displayMap.get(type);
        return null==display?type:display;
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
