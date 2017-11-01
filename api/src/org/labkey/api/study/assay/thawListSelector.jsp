<%
/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.RenderContext" %>
<%@ page import="org.labkey.api.study.actions.ParticipantVisitResolverChooser.RenderSubSelectors" %>
<%@ page import="org.labkey.api.study.assay.ThawListResolverType" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3");
        dependencies.add("Ext4");
    }
%>
<%
    JspView<RenderContext> thisView = (JspView<RenderContext>)HttpView.currentView();
    RenderContext ctx = thisView.getModelBean();
    boolean renderAll = ctx.get(RenderSubSelectors.class.getSimpleName()) == null ? true : ctx.get(RenderSubSelectors.class.getSimpleName()).equals(RenderSubSelectors.ALL);
    boolean listType = ThawListResolverType.LIST_NAMESPACE_SUFFIX.equalsIgnoreCase((String)ctx.getForm().get(ThawListResolverType.THAW_LIST_TYPE_INPUT_NAME));
    boolean textType = !listType;

    String containerPath = (String)ctx.getForm().get(ThawListResolverType.THAW_LIST_LIST_CONTAINER_INPUT_NAME);
    Container container = containerPath == null ? null : ContainerManager.getForPath(containerPath);

    String textTypeId = "RadioBtn-" + ThawListResolverType.THAW_LIST_TYPE_INPUT_NAME + "-" + ThawListResolverType.TEXT_NAMESPACE_SUFFIX;
    String listTypeId = "RadioBtn-" + ThawListResolverType.THAW_LIST_TYPE_INPUT_NAME + "-" + ThawListResolverType.LIST_NAMESPACE_SUFFIX;
%>
<table>
    <% if (renderAll)
    { %>
        <tr>
            <td><input type="radio" id="<%= text(textTypeId)%>" name="<%= text(ThawListResolverType.THAW_LIST_TYPE_INPUT_NAME) %>" <%=checked(textType)%> value="<%= text(ThawListResolverType.TEXT_NAMESPACE_SUFFIX) %>" onClick="document.getElementById('SQVPicker').style.display='none'; document.getElementById('ThawListDiv-TextArea').style.display='block'; toggleDisableResetDefault(true);"></td>
            <td>Paste a sample list as a TSV (tab-separated values)<%= helpPopup("Sample Lookup", "A lookup lets you assign a mapping from your own specimen numbers to participants and visits. The format is a tab-separated values (TSV), requiring the columns 'Index' and using the values of the columns 'SpecimenID', 'ParticipantID', and 'VisitID', and 'Date'. All columns headers are required, even if the fields are not populated.<p>To use the template, fill in the values, select the entire spreadsheet (Ctrl-A), copy it to the clipboard, and paste it into the text area below.", true) %> <%=textLink("download template", request.getContextPath()+"/study/assay/SampleLookupTemplate.xls")%></td>
        </tr>
        <tr>
            <td></td>
            <td><div id="ThawListDiv-TextArea" style="display:<%= text(textType ? "block" : "none") %>;"><textarea id="<%= text(ThawListResolverType.THAW_LIST_TEXT_AREA_INPUT_NAME) %>" name="<%= text(ThawListResolverType.THAW_LIST_TEXT_AREA_INPUT_NAME) %>" rows="4" cols="50"><%= h(ctx.get(ThawListResolverType.THAW_LIST_TEXT_AREA_INPUT_NAME)) %></textarea></div></td>
        </tr>
    <%
    } %>
    <tr>
        <td ><input type="radio" id="<%= text(listTypeId)%>" name="<%= text(ThawListResolverType.THAW_LIST_TYPE_INPUT_NAME) %>"<%=checked(listType || !renderAll)%> value="<%= text(ThawListResolverType.LIST_NAMESPACE_SUFFIX) %>" onClick="showChooseList(); toggleDisableResetDefault(false);"></td>
        <td>Use an existing sample list<%= helpPopup("Sample Lookup", "A lookup lets you assign a mapping from your own specimen numbers to participants and visits. The target list must have your own specimen identifier as its primary key, and uses the values of the 'SpecimenID', 'ParticipantID', 'Date', and 'VisitID' columns. All columns are required, even if they are not populated in the list.") %></td>
    </tr>
    <tr>
        <td></td>
        <td>
            <div id="SQVPicker" style="display:<%= text(listType ? "block" : "none") %>;"></div>
        </td>
    </tr>
</table>

<script type="text/javascript">
    var thawListQueryPickerPanel;
    Ext4.onReady(function(){
        var sqvModel = Ext4.create('LABKEY.sqv.Model', {});

        var sourceContainerCombo = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeContainerComboConfig({
            name: '<%= text(ThawListResolverType.THAW_LIST_LIST_CONTAINER_INPUT_NAME) %>',
            id : 'thawListContainer',
            fieldLabel: 'Folder',
            value: <%= text(container == null ? "null" : PageFlowUtil.jsString(container.getPath())) %>,
            width: 500,
            typeAhead: true,
            forceSelection: true
        }));

        var schemaCombo = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeSchemaComboConfig({
            id : 'thawListSchemaName',
            editable : true,
            typeAhead : true,
            typeAheadDelay : 250,
            forceSelection : true,
            initialValue : <%=PageFlowUtil.jsString((String)ctx.getForm().get(ThawListResolverType.THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME))%>,
            fieldLabel : 'Schema',
            name: '<%= text(ThawListResolverType.THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME) %>',
            validateOnBlur: false,
            width: 500
        }));

        var queryCombo = ('Ext.form.field.ComboBox', sqvModel.makeQueryComboConfig({
            defaultSchema : <%=PageFlowUtil.jsString((String)ctx.getForm().get(ThawListResolverType.THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME))%>,
            id : 'thawListQueryName',
            includeUserQueries: true,
            typeAhead : true,
            typeAheadDelay : 250,
            fieldLabel : 'Query',
            name: '<%= text(ThawListResolverType.THAW_LIST_LIST_QUERY_NAME_INPUT_NAME) %>',
            initialValue : <%=PageFlowUtil.jsString((String)ctx.getForm().get(ThawListResolverType.THAW_LIST_LIST_QUERY_NAME_INPUT_NAME))%>,
            width: 500
        }));

        thawListQueryPickerPanel = Ext4.create('Ext.form.Panel', {
            border : false,
            bodyStyle : 'background-color: transparent;',
            renderTo : 'SQVPicker',
            standardSubmit: true,
            width: 600,
            items : [sourceContainerCombo, schemaCombo, queryCombo, { xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF } ]
        });
    });

    // Register so that we can force ExtJS to size the panel correctly when it becomes visible
    //noinspection JSUnresolvedFunction
    addParticipantVisitResolverSelectionChangeListener(function()
    {
        handleAllowedDefaultOptionsForThawList();
        if (document.getElementById('<%=text(listTypeId)%>').checked)
            showChooseList();
        else
            thawListQueryPickerPanel.doLayout();
    });

    var showChooseList = function(){
        document.getElementById('SQVPicker').style.display='block';
        thawListQueryPickerPanel.doLayout();
        var thawListTextArea = document.getElementById('ThawListDiv-TextArea');
        if (thawListTextArea != null)
                thawListTextArea.style.display='none';
    };

    var handleAllowedDefaultOptionsForThawList = function() {

        if (document.getElementById('RadioBtn-Lookup').checked)
        {
            var textRadio = Ext.get('<%=text(textTypeId)%>');
            if ((textRadio && textRadio.dom.checked) || <%=textType%>)
                toggleDisableResetDefault(true); // Don't allow trying to set the default to the text type, as this is not supported.
        }
        else
            toggleDisableResetDefault(false);
    };

    var toggleDisableResetDefault = function(disabled) {
        var resetDefaultBtn = Ext.get('Btn-ResetDefaultValues');
        if (resetDefaultBtn)
        {
            if (disabled)
                resetDefaultBtn.replaceClass('labkey-button', 'labkey-disabled-button');
            else resetDefaultBtn.replaceClass('labkey-disabled-button', 'labkey-button');
        }
    };

    <% if (renderAll)
    { %>
    // Allow tabs in the TSV text area
    Ext.EventManager.on('<%= text(ThawListResolverType.THAW_LIST_TEXT_AREA_INPUT_NAME) %>', 'keydown', LABKEY.ext.Utils.handleTabsInTextArea);
    <% } %>
</script>

