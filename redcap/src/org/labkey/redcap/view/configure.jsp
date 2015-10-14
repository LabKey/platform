<%
/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
%>
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.redcap.RedcapManager" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext4"));
        resources.add(ClientDependency.fromPath("redcap/settings.js"));
        return resources;
    }
%>
<%
    JspView<RedcapManager.RedcapSettings> me = (JspView<RedcapManager.RedcapSettings>)HttpView.currentView();
    RedcapManager.RedcapSettings bean = me.getModelBean();

    ObjectMapper jsonMapper = new ObjectMapper();
%>

<labkey:errors/>
<script type="text/javascript">

    Ext4.onReady(function() {

        new LABKEY.ext4.RedcapSettings({
            renderTo    : 'rc-config-div',
            bean        : <%=text(jsonMapper.writeValueAsString(bean))%>,
            helpLink    : <%=q(helpLink("redcap", "link"))%>
        });
    });

</script>

<div>
    <div id='rc-config-div'></div>
</div>
