/*
 * Copyright (c) 2020-2026 LabKey Corporation
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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.JavaScriptDisplayColumn;
import org.labkey.api.view.UnauthorizedException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.Set;

/**
 * Used to assert that a character sequence is valid, properly encoded JavaScript. Similar to HtmlString, though this class
 * is just a simple wrapper; it doesn't (yet) provide filtering, a builder, or other useful mechanisms of HtmlString.
 */
public class JavaScriptFragment implements SafeToRender, DOM.Renderable
{
    public static final JavaScriptFragment EMPTY = new JavaScriptFragment("");
    public static final JavaScriptFragment EMPTY_STRING = JavaScriptFragment.unsafe("''");
    public static final JavaScriptFragment NULL = JavaScriptFragment.unsafe(" null ");
    public static final JavaScriptFragment TRUE = JavaScriptFragment.unsafe(" true ");
    public static final JavaScriptFragment FALSE = JavaScriptFragment.unsafe(" false ");

    public static JavaScriptFragment bool(boolean b) { return b ? TRUE : FALSE;}

    private final @NotNull String _s;

    /**
     * Returns a JavaScriptFragment that wraps the passed in String.
     * @param s A String. A null value results in an empty JavaScriptFragment (equivalent of JavaScriptFragment.unsafe("")).
     * @return A JavaScriptFragment that wraps the String.
     */
    public static @NotNull JavaScriptFragment unsafe(@Nullable String s)
    {
        if (null == s)
            return EMPTY;
        // even with unsafe() a javascript fragment can never contain the sequence "</[Ss][Cc][Rr][Ii][Pp][Tt]>"
        // since </ is not legal javascript syntax we escape it
        s = Strings.CS.replace(s, "</", "<\\/");
        return new JavaScriptFragment(s);
    }

    /** Create escaped javascript string literal */
    public static @NotNull JavaScriptFragment asString(String s)
    {
        if (null == s)
            return JavaScriptFragment.NULL;
        var js = PageFlowUtil.jsString(s);
        assert !Strings.CS.contains(js, "</");
        return new JavaScriptFragment(js);
    }

    /** Format "Object value" as JSON and render into a JavaScriptFragment */
    public static @NotNull JavaScriptFragment asJson(Object value)
    {
        if (null == value)
            return NULL;
        try
        {
            String s = JsonUtil.DEFAULT_MAPPER.writeValueAsString(value);
            if (Strings.CS.contains(s, "</"))
                throw new IllegalStateException("Error encoding JSON object");
            return new JavaScriptFragment(s);
        }
        catch (JsonProcessingException x)
        {
            throw UnexpectedException.wrap(x);
        }
    }

    private static final Set<String> DISALLOWED_SCRIPT_ELEMENTS = Collections.unmodifiableSet(new CaseInsensitiveHashSet("onClick", "onRender", "includeScript"));
    private static final String CLASS_NAME_ELEMENT = "className";
    public static void ensureXMLMetadataNoJavaScript(String metadataText)
    {
        try
        {
            XMLStreamReader reader = XmlBeansUtil.XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(metadataText));

            // Issue 48660 - disallow JavaScriptDisplayColumn for non-developers
            // When we're inside a <className> element, accumulate the contents to check when we hit the closing tag
            StringBuilder className = null;

            while (reader.hasNext())
            {
                reader.next();
                if (reader.isStartElement())
                {
                    String localPath = reader.getName().getLocalPart();
                    // These three elements directly include JavaScript or pointers to script files
                    if (DISALLOWED_SCRIPT_ELEMENTS.contains(localPath))
                    {
                        throw new UnauthorizedException("Illegal element <" + localPath + ">. For permissions to use this element, contact your system administrator");
                    }
                    if (CLASS_NAME_ELEMENT.equalsIgnoreCase(localPath))
                    {
                        className = new StringBuilder();
                    }
                }

                if (reader.isCharacters() && className != null)
                {
                    // Accumulate the content of the <className>
                    className.append(reader.getText());
                }

                if (reader.isEndElement())
                {
                    String localPath = reader.getName().getLocalPart();
                    if (CLASS_NAME_ELEMENT.equalsIgnoreCase(localPath) && className != null)
                    {
                        if (className.toString().contains(JavaScriptDisplayColumn.class.getName()))
                        {
                            throw new UnauthorizedException("For permissions to use JavaScriptDisplayColumn, contact your system administrator");
                        }
                        className = null;
                    }
                }
            }
        }
        catch (XMLStreamException ignored)
        {
            // Let other XML validation and error feedback handle malformed XML
        }
    }

    // Callers use factory method unsafe() instead
    private JavaScriptFragment(String s)
    {
        _s = null == s ? "" : s;
    }

    @Override
    public String toString()
    {
        return _s;
    }

    @Override
    public Appendable appendTo(Appendable sb)
    {
        try
        {
            return sb.append(toString());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
