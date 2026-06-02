<%
/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("mothership/deepException.js");
        dependencies.add("mothership/clientException.js");
    }
%>
<labkey:errors/>
<labkey:button id="inline-script" text="Inline script error" />
<labkey:button id="resource-script" text="Resource script error" />
<labkey:button id="nested-script" text="Nested resource script error" />
<labkey:button id="async-script" text="Async script error (500ms)" />
<div style="margin-top: 15px;">Opening the browser console will give more insight into thrown errors.</div>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    (() => {
        function throwInlineScriptError() {
            const x = undefined;
            const a = x.y.z; // Fail to dereference "x"
        }

        document.getElementById('inline-script').addEventListener('click', throwInlineScriptError);
    })();
</script>