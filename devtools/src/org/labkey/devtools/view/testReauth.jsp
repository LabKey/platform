<%
/*
 * Copyright (c) 2026 LabKey Corporation
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
<%@ page import="org.labkey.devtools.TestController.ReauthForm" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ReauthForm> me = HttpView.currentView();
    ReauthForm form = me.getModelBean();
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    LABKEY.Utils.onReady(function() {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('login', 'getAuthenticationConfiguration.api'),
            params: {
                returnUrl: LABKEY.ActionURL.buildURL('test', 'testReauth.view'),
            },
            success: function(response) {
                const needReauth = <%=form.reauthToken() == null%>;
                const data = JSON.parse(response.responseText).data;
                document.getElementById("description").textContent = data.description; // Setting textContent HTML encodes the value
                if (needReauth) {
                    document.getElementById("link").href = data.reauthUrl;
                }
            },
            failure: LABKEY.Utils.getCallbackWrapper(function(errorInfo) {
                document.getElementById("content").innerHTML = '<span>' + LABKEY.Utils.encodeHtml(errorInfo.exception ?? 'Failed to retrieve configuration') + '</span>';
            }, this, true)
        });
    });
</script>

<div id="content">

    You authenticated with: <span id="description"></span><br/>

<%
    if (form.reauthToken() != null)
    {
%>
Looks like you successfully re-authenticated and received token: <%=h(form.reauthToken())%><br/>

<labkey:form method="post">
    <input type="hidden" name="reauthToken" value="<%=h(form.reauthToken())%>">
    <input class="labkey-button primary" type="submit" value="Sign!">
</labkey:form>
<%
    }
    else
    {
        if (form.errorMessage() != null)
        {
%>
Looks like your reauthentication failed: <span class="labkey-error"><%=h(form.errorMessage())%></span>. Try again?
<%
        }
        else
        {
%>
You need to re-authenticate.
<%
        }
%>
        <a id="link" href="">Click here</a>
<%
    }
%>

</div>
