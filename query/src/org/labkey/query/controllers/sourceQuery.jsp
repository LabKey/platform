<%
/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.query.QueryAction"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.query.controllers.SourceForm" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    SourceForm form = (SourceForm)HttpView.currentModel();
    boolean canEdit = form.canEdit();
    boolean editableSQL = canEdit && !form.getQueryDef().isTableQueryDefinition();
%>
<div class="extContainer">
<labkey:errors />
<form method="POST" action="<%=form.urlFor(QueryAction.sourceQuery)%>">
    <input type="hidden" id="redirect" name="ff_redirect" value="<%=form.ff_redirect%>">
    <p>SQL:<br>
    <% if (editableSQL) { %>
        <textarea style="width: 100%;" rows="20" cols="80" wrap="off" id="queryText" name="ff_queryText"><%=h(form.ff_queryText)%></textarea>
        <script type="text/javascript">
            Ext.EventManager.on('queryText', 'keydown', handleTabsInTextArea);
        </script>
    <% } else { %>
        <input type="hidden" name="ff_queryText" value="<%=h(form.ff_queryText)%>" />
        <pre><%=h(form.ff_queryText)%></pre>
    <% } %>
</p><%
if (canEdit)
{
    %><labkey:button text="Save" onclick="submit_onclick('sourceQuery')" />&nbsp;<%
}
if (!form.getQueryDef().isTableQueryDefinition())
{
    %><labkey:button text="Design Query" onclick="submit_onclick('designQuery')" />&nbsp;<%
}%>
<% if(canEdit && form.getQueryDef().isMetadataEditable()) { %>
    <labkey:button text="Edit Metadata" onclick="submit_onclick('metadataQuery')" />&nbsp;
<% } %>
    <labkey:button text="View Data" onclick="submit_onclick('executeQuery')" />
<p>Metadata XML<%=PageFlowUtil.helpPopup("Metadata XML", "This XML lets you configure how your columns are displayed and other query-level attributes. See the <a target='_blank' href='https://www.labkey.org/download/schema-docs/xml-schemas/schemas/tableInfo_xsd/schema-summary.html'>XSD documentation</a> to learn more.", true)%>:<br>
    <textarea style="width: 100%;" rows="20" cols="80" wrap="off" id="metadataText" name="ff_metadataText"<%=canEdit ? "" : " READONLY"%>><%=h(form.ff_metadataText)%></textarea>
    <script type="text/javascript">
         Ext.EventManager.on('metadataText', 'keydown', handleTabsInTextArea);
     </script>
 </p>

</form>
</div>

<script type="text/javascript">
function _id(s) {return document.getElementById(s);}

var origQueryText = _id("queryText") == null ? null : _id("queryText").value;
var origMetadataText = _id("metadataText").value;

function isDirty()
{
    return (origQueryText != null && origQueryText != _id("queryText").value) || origMetadataText != _id("metadataText").value;  
}
window.onbeforeunload = LABKEY.beforeunload(isDirty);

function submit_onclick(method)
{
    _id('redirect').value = method;
    window.onbeforeunload = null;
}

Ext.onReady(function(){
    var e = Ext.get('queryText');
    if (e)
    {
        Ext.DomHelper.applyStyles(e,{margin:"1px"});
        new Ext.Resizable(e, { handles:'se', minWidth:200, minHeight:100, wrap:true, style:{border:"1px solid black"}});
    }
    e = Ext.get('metadataText');
    if (e)
    {
        Ext.DomHelper.applyStyles(e,{margins:1, padding:2});
        new Ext.Resizable(e, { handles:'se', minWidth:200, minHeight:100, wrap:true, style:{padding:2}});
    }
});
</script>