<%@ page import="static org.labkey.api.util.DOM.*" %>
<%@ page import="static org.labkey.api.util.DOM.Attribute.*" %>
<%@ page import="org.labkey.api.util.DOM" %>
<%@ page import="org.labkey.core.portal.ProjectController" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.stream.Stream" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="static org.labkey.api.util.HtmlString.unsafe" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    // TODO fix some collisions between DOM and JspBase
    // DOM.id
    public void dom(JspWriter out, Renderable... nodes) throws IOException
    {
        for (var n : nodes)
            if (n != null)
                n.appendTo(out);
    }
    // Allow passing CharSequence directly as body content, or require wrapping?
    public HtmlString txt(String s)
    {
        return HtmlString.of(s);
    }
%>
<% dom(out,

        A(DOM.id("id").cl("class").at(href,"https://www.google.com/"), "GOOGLE"),

        HR(),

        TABLE(TR(TD(cl("link").at(style,"border:solid 1px red"),
                link("begin", ProjectController.BeginAction.class)))),

        HR(),

        H1(css("#header1.bold").cl(true,"t").cl(false,"f"), "Hello World"),

        HR(),

        DIV(
                H1(css("#header1"),
                        "Hello", unsafe("&nbsp;"), "World",
                        H2(css("#header2"),
                                "Hello Seattle & Tacoma"))),

        HR(),

        SPAN(A(at(onclick,"alert('hello world')"), "<>>click here<<>")),

        HR(),

        SPAN(css("a.b.c"), unsafe("A&nbsp;B&nbsp;C")),

        HR(),

        SELECT(css("#element"),
                Stream.of("alice","bob","charles","denise").map(DOM::OPTION),
                OPTION(at(selected,true), "A&W")
        ),

        HR(),

        DIV(this.button("button").onClick("alert('hello world')")),

        HR(),

        X.FORM(at(method,"POST"),
                "This is a POST form ", X.FA("plus-square")),

        HR(),

        X.FORM(at(method,"GET"),
                "This is a GET form ", X.FA("minus-square")),

        HR()
);%>