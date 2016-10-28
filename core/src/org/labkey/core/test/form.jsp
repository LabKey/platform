<%
/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
%>
<%@ page import="org.apache.commons.collections4.IteratorUtils" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.action.SpringActionController" %>
<%@ page import="org.labkey.core.test.TestController" %>
<%@ page import="org.springframework.validation.BindingResult" %>
<%@ page import="org.springframework.validation.Errors" %>
<%@ page import="org.springframework.validation.FieldError" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Enumeration" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    void errorRow(JspWriter out, String path) throws IOException
    {
        String err = formatErrorsForPathStr(path);
        if (!StringUtils.isEmpty(err))
        {
            out.println("<tr><td colspan=2>" + err + "</td></tr>");
        }
    }
%>
<%
    TestController.SimpleForm form = (TestController.SimpleForm) getModelBean();
    String enctype = StringUtils.defaultString(form.encType, "application/x-www-form-urlencoded");
    assert enctype.equals("multipart/form-data") || enctype.equals("application/x-www-form-urlencoded");
%>
<%=formatErrorsForPath("form")%>
<labkey:form enctype="<%=text(enctype)%>" method="POST">
    <table>
        <%errorRow(out,"form.a");%>
        <tr><td>a</td><td><input type=checkbox name="a" <%=checked(form.getA())%>><input type=hidden name="<%=h(SpringActionController.FIELD_MARKER)%>a"></td></tr>
        <%errorRow(out,"form.b");%>
        <tr><td>b</td><td><input name="b" value="<%=h(form.getB())%>"></td></tr>
        <%errorRow(out,"form.c");%>
        <tr><td colspan=2><%=formatErrorsForPath("form.c")%></td></tr>
        <tr><td>c</td><td><input name="c" value="<%=h(form.getC())%>"></td></tr>
        <%errorRow(out,"form.int");%>
        <tr><td>int</td><td><input name="int" value="<%=h(form.getInt())%>"></td></tr>
        <%errorRow(out,"form.positive");%>
        <tr><td>Positive Number</td><td><input name="positive" value="<%=h(form.getPositive())%>"></td></tr>
        <%errorRow(out,"form.required");%>
        <tr><td>Required String</td><td><input name="required" value="<%=h(form.getRequired())%>"></td></tr>
        <tr><td>Text</td><td><textarea name="text" rows="12" cols="60"><%=h(form.getText())%></textarea></td></tr>
        <tr><td>x</td><td><input name="x" value="<%=h(form.getX())%>"></td></tr>
        <tr><td>y</td><td><input name="y" value="<%=h(form.getY())%>"></td></tr>
        <tr><td>z</td><td><input name="z" value="<%=h(form.getZ())%>"></td></tr>
<%
    if (enctype.startsWith("multipart"))
    {
        %><tr><td>file</td><td><input name="file"></td></tr><%
    }
%>
    </table>
    <%=formatMissedErrors("form")%><br>
    <%= button("Submit").submit(true) %>
</labkey:form>
<%--



// DEBUG OUTPUT BELOW

--%>
<br><br><br>
<div class="labkey-alternate-row">
<hr><b>errors</b><br>
<%
for (ObjectError e : getAllErrors(pageContext))
{
    String path = e.getObjectName();
    if (e instanceof FieldError)
        path = path + "." + ((FieldError)e).getField();
    %><b><%=h(path)%>:</b>&nbsp;<%=h(getViewContext().getMessage(e))%><br><%
}
%>
<hr><b>form</b>
<pre>
<%=h(form.toString())%>
</pre>
<%
    out.println("<hr><b>attributes</b><br>");
    Enumeration<String> e = request.getAttributeNames();
    while (e.hasMoreElements())
    {
        String name = e.nextElement();
        out.println("<b>" + h(name) + ":</b> " + h(String.valueOf(request.getAttribute(name))) + "<br>");
    }

    out.println("<hr><b>parameters</b><br>");
    Enumeration<String> f = request.getParameterNames();
    while (f.hasMoreElements())
    {
        String name = f.nextElement();
        out.println("<b>" + h(name) + ":</b> " + h(String.valueOf(request.getParameter(name))) + "<br>");
    }
    out.println("<br>");
%><hr>
</div>
<%!
    List<ObjectError> getAllErrors(PageContext pageContext)
    {
        List<ObjectError> l = new ArrayList<>();

        IteratorUtils.asIterator(pageContext.getAttributeNamesInScope(PageContext.REQUEST_SCOPE)).forEachRemaining(s -> {
            if (s.startsWith(BindingResult.MODEL_KEY_PREFIX))
            {
                Object o = pageContext.getAttribute(s, PageContext.REQUEST_SCOPE);
                if (o instanceof Errors)
                    l.addAll(((BindingResult) o).getAllErrors());
            }
        });
        return l;
    }
%>