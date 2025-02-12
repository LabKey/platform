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
<labkey:button id="nested-script" text="Resource script error" />
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