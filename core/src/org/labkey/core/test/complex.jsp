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
<%@ page import="org.apache.commons.collections4.IteratorUtils" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    void errorRow(JspWriter out, String path) throws IOException
    {
        String err = formatErrorsForPathStr(path);
        if (null != err && err.length() > 0)
        {
            out.println("<tr><td colspan=2>" + err + "</td></tr>");
        }
    }

    String get(String[] a, int i)
    {
        return a.length > i ? a[i] : null;
    }
%>
<%
    TestController.ComplexForm form = (TestController.ComplexForm) getModelBean();
    String enctype = StringUtils.defaultString((String) request.getAttribute("enctype"), "application/x-www-form-urlencoded");
    assert enctype.equals("multipart/form-data") || enctype.equals("application/x-www-form-urlencoded");
%>

<labkey:form enctype="<%=text(enctype)%>" method="POST">

    <%=formatErrorsForPath("form")%>

    <table class="labkey-bordered labkey-alternate-row">
        <tr><th>strings</th></tr>
        <tr><td>a</td><td><input name="strings" value="<%=h(get(form.getStrings(),0))%>"></td></tr>
        <tr><td>a</td><td><input name="strings" value="<%=h(get(form.getStrings(),1))%>"></td></tr>
        <tr><td>a</td><td><input name="strings" value="<%=h(get(form.getStrings(),2))%>"></td></tr>
    </table>
    <p />
    <% for (int i=0 ; i<2 ; i++)
    {%>
        <table class="labkey-bordered labkey-alternate-row">
            <%errorRow(out,"form.beans["+i+"].a");%>
            <tr><td>a</td><td><input type=checkbox name="beans[<%=i%>].a" <%=checked(form.getBeans().get(i).getA())%>><input type=hidden name="<%=SpringActionController.FIELD_MARKER%>beans[<%=i%>].a"></td></tr>
            <%errorRow(out,"form.beans["+i+"].b");%>
            <tr><td>b</td><td><input name="beans[<%=i%>].b" value="<%=h(form.getBeans().get(i).getB())%>"></td></tr>
            <%errorRow(out,"form.beans["+i+"].c");%>
            <tr><td colspan=2><%=formatErrorsForPath("form.c")%></td></tr>
            <tr><td>c</td><td><input name="beans[<%=i%>].c" value="<%=h(form.getBeans().get(0).getC())%>"></td></tr>
            <%errorRow(out,"form.beans["+i+"].int");%>
            <tr><td>int</td><td><input name="beans[<%=i%>].int" value="<%=h(form.getBeans().get(0).getInt())%>"></td></tr>
            <%errorRow(out,"form.beans["+i+"].positive");%>
            <tr><td>Positive Number</td><td><input name="beans[<%=i%>].positive" value="<%=h(form.getBeans().get(0).getPositive())%>"></td></tr>
            <%errorRow(out,"form.beans["+i+"].required");%>
            <tr><td>Required String</td><td><input name="beans[<%=i%>].required" value="<%=h(form.getBeans().get(0).getRequired())%>"></td></tr>
            <tr><td>Text</td><td><textarea name="beans[<%=i%>].text" rows="12" cols="60"><%=h(form.getBeans().get(0).getText())%></textarea></td></tr>
            <tr><td>x</td><td><input name="beans[<%=i%>].x" value="<%=h(form.getBeans().get(0).getX())%>"></td></tr>
            <tr><td>y</td><td><input name="beans[<%=i%>].y" value="<%=h(form.getBeans().get(0).getY())%>"></td></tr>
            <tr><td>z</td><td><input name="beans[<%=i%>].z" value="<%=h(form.getBeans().get(0).getZ())%>"></td></tr>
    <%
        if (enctype.startsWith("multipart"))
        {
            %><tr><td>file</td><td><input name="file"></td></tr><%
        }
    %>
        </table>
    <p />
    <% } %>
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