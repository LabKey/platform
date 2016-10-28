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
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.demo.DemoController.BindActionBean" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="org.springframework.validation.Errors" %>
<%@ page import="org.springframework.validation.FieldError" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.demo.DemoController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<form method=post>
<%
    BindActionBean form = (BindActionBean)getModelBean();
    Errors errors = getErrors("form");

    if (null == errors)
        errors = new BindException(null, "form");

    if (errors.getErrorCount() == 0)
    {
        %><h1>SUCCESS</h1><%
    }
    else
    {
        %><h1>ERRORS</h1><%
    }

    if (null != form)
    {
        %><table><%
        %><tr><td>d</td><td><input name="d" value="<%=formatDateTime(form.getD())%>"></td><td><%=h(getMessage(errors.getFieldError("d")))%></td></tr><%
        %><tr><td>i</td><td><input name="i" value="<%=h(form.getI())%>"></td><td><%=h(getMessage(errors.getFieldError("i")))%></td></tr><%
        %><tr><td>j</td><td><input name="j" value="<%=h(form.getJ())%>"></td><td><%=h(getMessage(errors.getFieldError("j")))%></td></tr><%
        %><tr><td>k</td><td><input name="k" value="<%=h(form.getK())%>"></td><td><%=h(getMessage(errors.getFieldError("k")))%></td></tr><%
        %><tr><td>l</td><td><input name="l" value="<%=h(form.getL())%>"></td><td><%=h(getMessage(errors.getFieldError("l")))%></td></tr><%
        %><tr><td>s</td><td><input name="s" value="<%=h(form.getS())%>"></td><td><%=h(getMessage(errors.getFieldError("s")))%></td></tr><%
        %><tr><td>multiString</td><td><input disabled=true value="<%=h(form.getMultiString())%>"></td><td><%=h(getMessage(errors.getFieldError("multiString")))%></td></tr><%
        %><tr><td>multiString[0]</td><td><input name="multiString" value="<%=h(_get(form.getMultiString(),0))%>"></td><td><%=h(getMessage(errors.getFieldError("multiString[0]")))%></td></tr><%
        %><tr><td>multiString[1]</td><td><input name="multiString" value="<%=h(_get(form.getMultiString(),1))%>"></td><td><%=h(getMessage(errors.getFieldError("multiString[1]")))%></td></tr><%
        %><tr><td>multiString[2]</td><td><input name="multiString" value="<%=h(_get(form.getMultiString(),2))%>"></td><td><%=h(getMessage(errors.getFieldError("multiString[2]")))%></td></tr><%
        %><tr><td>sub</td><td><input disabled value="<%=h(form.getSub())%>"></td><td><%=h(getMessage(errors.getFieldError("sub")))%></td></tr><%
        %><tr><td>sub.s</td><td><input name="sub.s" value="<%=h(form.getSub().getS())%>"></td><td><%=h(getMessage(errors.getFieldError("sub.s")))%></td></tr><%
        %><tr><td>sub.x</td><td><input name="sub.x" value="<%=h(form.getSub().getX())%>"></td><td><%=h(getMessage(errors.getFieldError("sub.x")))%></td></tr><%
        %><tr><td>sub.y</td><td><input name="sub.y" value="<%=h(form.getSub().getY())%>"></td><td><%=h(getMessage(errors.getFieldError("sub.y")))%></td></tr><%

        %><tr><td>listString[0]</td><td><input name="listString[0]" value="<%=h(_get(form.getListString(),0))%>"></td><td><%=h(getMessage(errors.getFieldError("listString[0]")))%></td></tr><%
        %><tr><td>listString[1]</td><td><input name="listString[1]" value="<%=h(_get(form.getListString(),1))%>"></td><td><%=h(getMessage(errors.getFieldError("listString[1]")))%></td></tr><%
        %><tr><td>listString[2]</td><td><input name="listString[2]" value="<%=h(_get(form.getListString(),2))%>"></td><td><%=h(getMessage(errors.getFieldError("listString[2]")))%></td></tr><%

    for (int i = 0; i < Math.max(form.getListBean().size(), 3); i++)
    {
        DemoController.SubBean bean = (DemoController.SubBean) _get(form.getListBean(), i);
        %><tr><td colspan=3><b>bean <%=i%></b></td></tr><%
        %><tr><td>listBean[<%=i%>].s</td><td><input name="listBean[<%=i%>].s" value="<%=h(null==bean?null:bean.getS())%>"></td><td><%=h(getMessage(errors.getFieldError("listBean["+i+"].s")))%></td></tr><%
        %><tr><td>listBean[<%=i%>].x</td><td><input name="listBean[<%=i%>].x" value="<%=h(null==bean?null:bean.getX())%>"></td><td><%=h(getMessage(errors.getFieldError("listBean["+i+"].x")))%></td></tr><%
        %><tr><td>listBean[<%=i%>].y</td><td><input name="listBean[<%=i%>].y" value="<%=h(null==bean?null:bean.getY())%>"></td><td><%=h(getMessage(errors.getFieldError("listBean["+i+"].y")))%></td></tr><%
    }

        %><tr><td colspan=3><b>these don't work</b></td></tr><%
        %><tr><td>indexString[0]</td><td><input name="indexString[0]" value="<%=h(form.getIndexString(0))%>"></td><td><%=h(getMessage(errors.getFieldError("indexString[0]")))%></td></tr><%
        %><tr><td>indexString[1]</td><td><input name="indexString[1]" value="<%=h(form.getIndexString(1))%>"></td><td><%=h(getMessage(errors.getFieldError("indexString[1]")))%></td></tr><%
        %><tr><td>indexString[2]</td><td><input name="indexString[2]" value="<%=h(form.getIndexString(2))%>"></td><td><%=h(getMessage(errors.getFieldError("indexString[2]")))%></td></tr><%
        %></table><%
    }
    else
    {
        if (null == form)
        {
            %><font class="labkey-error"><em>form</em> is NULL</font><br><%
        }
        // since we're not showing errors field-by-field don't show duplicates
        if (null == errors)
        {
            %><font class="labkey-error"><em>errors</em> is NULL</font><br><%
        }
        else
        {
            for (ObjectError e : errors.getAllErrors())
            {
                String message = getMessage(e);
                %><font class="labkey-error"><%=h(message)%></font><br><%
            }
        }
    }
%>
<input type=submit>
</form>

<hr>
<b>All errors</b><br><%
for (ObjectError e : errors.getAllErrors())
{
    %><%=(e instanceof FieldError)?((FieldError)e).getField()+": ":""%><%=h(getMessage(e))%><br><%
}
%>
<%!
    Object _get(List l, int i)
    {
        return l == null ? null : i >= l.size() ? null : l.get(i);
    }

    Object _get(String[] l, int i)
    {
        return l == null ? null : i >= l.length ? null : l[i];
    }
%>