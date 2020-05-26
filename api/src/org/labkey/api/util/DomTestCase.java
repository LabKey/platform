package org.labkey.api.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

import static org.labkey.api.util.DOM.*;
import static org.labkey.api.util.DOM.Attribute.*;


public class DomTestCase extends Assert
{
    @Test
    public void test()
    {
        HtmlString h;

        h = createHtml(
                H1(at(id,"header1").cl("bold").cl(true,"t").cl(false,"f"),
                    "Hello World"));
        assertEquals("<h1 id=\"header1\" class=\"bold t\">Hello World</h1>", h.toString());

        h = createHtml(
                DIV(
                    H1(id("header1"),
                            "Hello", HtmlString.NBSP, "World"),
                    H2(at(id,"header2"),
                            "Hello Seattle"),
                    "A&B"));
        assertEquals("<div><h1 id=\"header1\">Hello&nbsp;World</h1><h2 id=\"header2\">Hello Seattle</h2>A&amp;B</div>", h.toString());

        h = createHtml(
                SPAN(at(onclick, "alert('hello world')"),
                        HtmlString.unsafe(">>here&lt;&lt")));
        assertEquals("<span onclick=\"alert(&#039;hello world&#039;)\">>>here&lt;&lt</span>", h.toString());

        h = createHtml(
                SPAN(cl("a","b","c"),
                        HtmlString.unsafe("&nbsp;")));
        assertEquals("<span class=\"a b c\">&nbsp;</span>", h.toString());

        h = createHtml(
            SELECT(id("element"),
                    Arrays.stream(Element.values()).map(el -> OPTION(el.name())),
                    OPTION(at(selected,true), "A&W"))
        );
        assertTrue(h.toString().startsWith("<select id=\"element\"><option>a</option>"));
        assertTrue(h.toString().endsWith("<option selected>A&amp;W</option></select>"));

        h = createHtml(
                DIV(new Button.ButtonBuilder("button").build())
        );
        assertTrue(h.toString().contains("labkey-button"));


        h = createHtml(
                LK.FORM(at(method,"POST"), new Button.ButtonBuilder("button").build())
        );
        assertTrue(h.toString().contains("labkey-button"));
        assertTrue(h.toString().contains("POST"));
        assertTrue(h.toString().contains(CSRFUtil.csrfName));
    }

    /** Nulls will be ignored, and are a convenient value to pass when there are conditional elements in your HTML */
    @Test
    public void testNulls()
    {
        //noinspection ConstantConditions
        HtmlString h = createHtml(
                DIV(
                        1 == 2 ? "1 equals 2" : null,
                        DIV("I don't care if they equal"),
                        1 != 2 ? "1 does not equal 2" : null));
        assertEquals("<div><div>I don&#039;t care if they equal</div>1 does not equal 2</div>", h.toString());
    }

    /**
     * You can use DOM.createHtmlFragment() to stitch together multiple children when you need to collapse to a single
     * element to add, perhaps the return value from a method
     */
    @Test
    public void testCreateHtmlFragment()
    {
        HtmlString h = createHtml(
                DIV(
                        // Not a typical usage, but demonstrates how to collapse an arbitrary number of child elements
                        createHtmlFragment(
                                SPAN("value1"),
                                SPAN("value2"))));
        assertEquals("<div><span>value1</span><span>value2</span></div>", h.toString());
    }
}
