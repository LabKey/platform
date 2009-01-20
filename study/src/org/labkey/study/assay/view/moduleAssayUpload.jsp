<%
/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.actions.AssayRunUploadForm" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AssayRunUploadForm> me = (JspView<AssayRunUploadForm>) HttpView.currentView();
    AssayRunUploadForm bean = me.getModelBean();
    ExpProtocol protocol = bean.getProtocol();
    int batchId = bean.getBatchId() == null ? 0 : bean.getBatchId().intValue();
%>
<script type="text/javascript">
    LABKEY.requiresClientAPI();
</script>
<script type="text/javascript">
    var assay = null;
    LABKEY.Assay.getById({
        id: <%=protocol.getRowId()%>,
        containerPath: LABKEY.ActionURL.getContainer(),
        successCallback: function (assayDesigns) {
            if (!assayDesigns || assayDesigns.length != 1)
                Ext.Msg.alert("Expected an assay design for assay");
            else
                assay = assayDesigns[0];
        },
        failureCallback: function (response, options) {
            Ext.Msg.alert("failed to get assay design");
        }
    });

    var batch = new LABKEY.Assay.Batch(<%=protocol.getRowId()%>, <%=batchId%>);
</script>

<p>
<%
    if (me.getView("nested") == null)
        throw new IllegalStateException("expected nested view");
    me.include(me.getView("nested"), out);
%>
</p>
