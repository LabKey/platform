<%@ page import="org.labkey.biotrue.controllers.NewServerForm" %>
<%@ page import="org.labkey.biotrue.controllers.BtController" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<% NewServerForm form = (NewServerForm) __form;%>
<labkey:errors />
<form action="<%=BtController.Action.newServer.url(getContainer())%>" method="POST">
    <p>
        What do you want to name this BioTrue server?<br>
        <input type="text" name="ff_name" value="<%=h(form.ff_name)%>">
    </p>
    <p>
        What is the URL for the WSDL of your BioTrue server?  The path is probably '/cdms_browse_soap.php?wsdl' or maybe
        '/lib/soap/cdms_browse_soap.php?wsdl'.  If you use https to access your server, this URL will probably also be https.<br>
        <input type="text" name="ff_wsdlURL" style="width:300px" value="<%=h(form.ff_wsdlURL)%>">
    </p>
    <p>
        What is the target namespace of the web service?  This is probably the base URL of your server, but always with the protocol "http".<br>
        <input type="text" name="ff_serviceNamespaceURI" style="width:200px" value="<%=h(form.ff_serviceNamespaceURI)%>">
    </p>
    <p>
        What is the name of the service?  It's probably 'cdms_browse'.<br>
        <input type="text" name="ff_serviceLocalPart" value="<%=h(form.ff_serviceLocalPart)%>">
    </p>
    <p>
        What is the username?<br>
        <input type="text" name="ff_username" value="<%=h(form.ff_username)%>">

    </p>
    <p>
        What is the password?<br>
        <input type="password" name="ff_password" value="<%=h(form.ff_password)%>">
    </p>
    <p>
        Where in this web server's file system do you want to download files to?<br>
        <input type="text" name="ff_physicalRoot" style="width:200px" value="<%=h(form.ff_physicalRoot)%>">
    </p>
    <labkey:button text="Define Server" />
</form>