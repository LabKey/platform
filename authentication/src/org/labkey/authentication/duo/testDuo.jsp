<%
/*
* Copyright (c) 2008-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.authentication.duo.DuoController" %>
<%@ page import="org.labkey.authentication.duo.DuoController.TestDuoForm" %>
<%@ page import="com.duosecurity.duoweb.DuoWeb" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.authentication.duo.DuoManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<TestDuoForm> me = (JspView<TestDuoForm>)HttpView.currentView();
    TestDuoForm form = me.getModelBean();
%>
Use this page to test Duo Two-Factor authentication setting. Duo Two-factor authentication, as the name implicates,
has two parts to the sign-in process: (i) Successful sign-in to the Labkey server,
and (ii) Secondary login via Duo. The secondary login will require you to have an account with Duo. You can sign up for
free here: https://signup.duosecurity.com/.
<ul>
    <li>For testing purpose, we will skip signing onto the Labkey server, and only perform the secondary login via Duo.</li>
</ul>
<br>

<labkey:form name="testDuo" method="post" action="testDuo.post">
    <table>
        <tr><td>Duo Authentication</td></tr>
    </table>
</labkey:form>

<iframe id="duo_iframe" width="620" height="330" frameborder="0"></iframe>

<script src="<%=h(request.getContextPath())%>/authentication/duo/Duo-Web-v1.js"></script>

<script>
//    'Integration key', 'Secret key', 'API hostname' was obtained by creating a "New Integration" (Integration type: Web SDK)
//    on Duo's Admin. 'Application key' (or Application Secret key) was created via executing these commands from terminal (on Mac OSX):
//    >python
//    import os, hashlib
//    print hashlib.sha1(os.urandom(32)).hexdigest()

    var apihostname ="<%=h(DuoManager.getAPIHostname())%>"; //API host name //
    var sigRequest = "<%=h(DuoManager.generateSignedRequest(getUser()))%>"; // TODO: Is getUser() okay to do? Is there another way?
    var postActionURL = LABKEY.ActionURL.buildURL("duo", "testDuoResult.view"); //TODO: find a better way rather than building a URL here.

    Duo.init({
        'host': apihostname,
        'sig_request': sigRequest ,
        'post_action': postActionURL
    });
</script>

