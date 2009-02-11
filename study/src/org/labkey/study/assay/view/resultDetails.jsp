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
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.study.assay.ModuleAssayProvider.ResultDetailsBean" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.exp.property.DomainUtil" %>
<%@ page import="org.labkey.study.assay.ModuleAssayProvider" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ModuleAssayProvider.ResultDetailsBean> me = (JspView<ModuleAssayProvider.ResultDetailsBean>) HttpView.currentView();
    ModuleAssayProvider.ResultDetailsBean bean = me.getModelBean();
    ExpProtocol protocol = bean.expProtocol;
%>
<script type="text/javascript">
    LABKEY.requiresClientAPI();
</script>
<script type="text/javascript">
    var loader = new LABKEY.MultiRequest();
    loader.add(LABKEY.Assay.getById, {
        id: <%=protocol.getRowId()%>,
        containerPath: LABKEY.ActionURL.getContainer(),
        successCallback: function (assayDesigns) {
            if (!assayDesigns || assayDesigns.length != 1)
                Ext.Msg.alert("Expected an assay design for assay");
            else
                loader.assay = assayDesigns[0];
        },
        failureCallback: function (response, options) {
            Ext.Msg.alert("failed to get assay design for assay id: <%=protocol.getRowId()%>");
        }
    });
    loader.add(LABKEY.Query.selectRows, {
        schemaName : "assay",
        queryName : "<%=protocol.getName()%> Data",
        filterArray : [ LABKEY.Filter.create("ObjectId", <%=bean.objectId%>, LABKEY.Filter.Types.EQUAL) ],
        successCallback: function (data, options, response) {
            if (!data || data.rowCount != 1)
                Ext.Msg.alert("Expected a single row of data");
            else
                loader.data = data;
        },
        failureCallback: function (response, options) {
            Ext.Msg.alert("failed to get data for row: <%=bean.objectId%>");
        }
    });
</script>
<p>
<%
    if (me.getView("nested") == null)
        throw new IllegalStateException("expected nested view");
    me.include(me.getView("nested"), out);
%>
