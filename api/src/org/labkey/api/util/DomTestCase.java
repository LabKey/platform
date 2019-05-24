package org.labkey.api.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

import static org.labkey.api.util.HtmlStringBuilder.DOM.*;
import static org.labkey.api.util.HtmlStringBuilder.Attribute.*;


public class DomTestCase extends Assert
{
    @Test
    public void test()
    {
        // NOTE: I can't figure out how to do the equivalent of static import Attributes.* here
        // so this is more verbose than
        HtmlString h;

        h = createHtml(
                H1(at(id,"header1"), cl("bold").add(true,"t").add(false,"f"),
                    "Hello World"));
        assertEquals("<h1 id=\"header1\" class=\"bold t\">Hello World</h1>", h.toString());

        h = createHtml(
                DIV(NOAT, NOCLASS,
                    H1(at(id,"header1"), NOCLASS,
                            "Hello", HtmlString.NBSP, "World"),
                    H2(at(id,"header2"), NOCLASS,
                            "Hello Seattle"),
                    "A&B"));
        assertEquals("<div><h1 id=\"header1\">Hello&nbsp;World</h1><h2 id=\"header2\">Hello Seattle</h2>A&amp;B</div>", h.toString());

        h = createHtml(
                SPAN(at(onclick, "alert('hello world')"), NOCLASS,
                        HtmlString.unsafe(">>here&lt;&lt")));
        assertEquals("<span onclick=\"alert(&#039;hello world&#039;)\">>>here&lt;&lt</span>", h.toString());

        h = createHtml(
                SPAN(NOAT, cl("a","b","c"),
                        HtmlString.unsafe("&nbsp;")));
        assertEquals("<span class=\"a b c\">&nbsp;</span>", h.toString());

        h = createHtml(
            SELECT(at(id,"element"), NOCLASS,
                    Arrays.stream(HtmlStringBuilder.Element.values()).map(el -> OPTION(NOAT, NOCLASS, el.name())),
                    OPTION(NOAT, NOCLASS, "A&W"))
        );
        assertTrue(h.toString().startsWith("<select id=\"element\"><option>a</option>"));
        assertTrue(h.toString().endsWith("<option>A&amp;W</option></select>"));

        h = createHtml(
                DIV(NOAT, NOCLASS, new Button.ButtonBuilder("button").build())
        );
        assertTrue(h.toString().contains("labkey-button"));
    }
}
