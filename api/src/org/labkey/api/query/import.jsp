<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%
    String importDiv = "importDiv" + getRequestScopedUID();
%>
<labkey:errors></labkey:errors>
<div id="<%=importDiv%>">
</div>
<script> (function(){


    var importDiv = '<%=importDiv%>';
    Ext.onReady(function(){
        alert("hello world");
    });


})(); </script>
