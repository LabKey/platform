<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.study.assay.ModuleAssayProvider.DataDetailsModel" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.exp.property.DomainUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DataDetailsModel> me = (JspView<DataDetailsModel>) HttpView.currentView();
    DataDetailsModel model = me.getModelBean();

//    Map<String, Object> dataDomainMap = DomainUtil.convertDomainToMap(model.dataDomain);
%>
<%--
<script type="text/javascript">
    var dataDomain = <%=new JSONObject(dataDomainMap).toString()%>
    var dataValues = <%=new JSONObject(model.values)%>
</script>
--%>
<div id="msgbox" style="padding:0.2em;"></div>
<script type="text/javascript">
    LABKEY.requiresClientAPI();
</script>
<script type="text/javascript">
    function showMessage(msg, className)
    {
        var msgbox = Ext.get("msgbox");
        if (!msgbox)
        {
            alert(msg);
        }
        else
        {
            msgbox.dom.innerHTML = msg;
            msgbox.dom.className = className;
            msgbox.enableDisplayMode();
            msgbox.setVisible(true, true);
        }
    }

    function showErrorMessage(msg)
    {
        showMessage(msg, "labkey-error");
    }

    function showInfoMessage(msg)
    {
        showMessage(msg, "labkey-message");
    }

    function hideMessage()
    {
        var msgbox = Ext.get("msgbox");
        msgbox.setVisible(false, false);
        msgbox.dom.innerHTML = "";
    }

    var assay = null;
    var dataDetails = null;

    function fireDataReady()
    {
        if (onDataReady && assay && dataDetails)
            onDataReady();
    }

    Ext.onReady(function () {
        LABKEY.Assay.getById(
                function (assayDesigns) {
                    if (!assayDesigns || assayDesigns.length != 1) {
                        showErrorMessage("Expected an assay design for assay id <%=model.expProtocol.getRowId()%>");
                        return;
                    }
                    assay = assayDesigns[0];
                    fireDataReady();
                },
                function (response, options) {
                    showErrorMessage(response.responseText);
                },
                <%=model.expProtocol.getRowId()%>,
                '<%=getViewContext().getContainer().getPath()%>');

        LABKEY.Query.selectRows({
            schemaName : "assay",
            queryName : "<%=model.expProtocol.getName() + " Data"%>",
            filterArray : [ LABKEY.Filter.create("ObjectId", <%=model.objectId%>, LABKEY.Filter.Types.EQUAL) ],
            failureCallback: function (errorInfo, options, response) {
                showErrorMessage(errorInfo);
            },
            successCallback: function (data, options, response) {
                if (!data || data.rowCount != 1) {
                    showErrorMessage("Expected data row for ObjectId=<%=model.objectId%>.");
                    return;
                }
                dataDetails = data;
                fireDataReady();
            }
        });
    });
</script>

<p>
<%
    if (me.getView("nested") == null)
        throw new IllegalStateException("expected nested view");
    me.include(me.getView("nested"), out);
%>
</p>
