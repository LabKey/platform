<%
    /*
     * Copyright (c) 2015-2017 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ page import="org.labkey.api.exp.property.Lookup" %>
<%@ page import="org.labkey.api.settings.ConceptURIProperties" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.template.FrameFactoryClassic" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("sqv");
    }
%>
<%
    Map<String, Lookup> conceptURIs = ConceptURIProperties.getMappings(getContainer());
%>
<style type="text/css">
    form {
        width: 514px;
    }

    .form-buttons {
        padding: 3px 0;
    }

    .concept-tile {
        border: solid #C0C0C0 1px;
        border-radius: 5px;
        margin-top: 10px;
        padding: 5px;
        background-color: #F8F8F8;
        width: 500px;
    }

    .concept-title {
        color: #303030;
        font-weight: bold;
    }
</style>
<%
    FrameFactoryClassic.startTitleFrame(out, "Insert/Update Mapping");
%>
<labkey:errors/>
<labkey:form action="" method="POST">
    <div id="SQVPicker"></div>
    <div class="form-buttons">
        <%=button("Save").submit(true)%>
    </div>
</labkey:form>
<%
    FrameFactoryClassic.endTitleFrame(out);

    if (conceptURIs.size() > 0)
    {
        FrameFactoryClassic.startTitleFrame(out, "Existing Mappings");

        for (String uri : conceptURIs.keySet())
        {
            Lookup lookup = conceptURIs.get(uri);
%>
<div class="concept-tile">
    <div class="concept-title"><%=h(uri)%></div>
    <ul>
        <li>
            Container: <%=h(lookup.getContainer() != null ? lookup.getContainer().getPath() : "[current folder]")%></li>
        <li>Schema Name: <%=h(lookup.getSchemaName())%></li>
        <li>Query Name: <%=h(lookup.getQueryName())%></li>
    </ul>
    <%=textLink("remove", (URLHelper) null, "removeConceptMapping(" + q(uri) + "); return false;", "removeConceptId")%>
</div>
<%
        }

        FrameFactoryClassic.endTitleFrame(out);
    }
%>

<script type="text/javascript">
    var removeConceptMapping = function(uri)
    {
        Ext4.Msg.confirm('Confirm deletion', 'Are you sure you want to remove the Concept URI mapping for "' + uri + '"?',
                function(buttonId)
                {
                    if (buttonId == 'yes')
                    {
                        LABKEY.Ajax.request({
                            url: LABKEY.ActionURL.buildURL('experiment', 'removeConceptMapping.api'),
                            params: {conceptURI: uri},
                            scope: this,
                            success: LABKEY.Utils.getCallbackWrapper(function (response)
                            {
                                window.location.reload();
                            }, this, false),
                            failure: LABKEY.Utils.getCallbackWrapper(function (response)
                            {
                                Ext4.Msg.alert('Error', response.exception);
                            }, this, true)
                        });
                    }
                }
        );
    };

    // note: client dependencies declared in FolderManagementTabStrip
    Ext4.onReady(function()
    {
        var sqvModel = Ext4.create('LABKEY.sqv.Model', {});

        var conceptTextField = Ext4.create('Ext.form.field.Text', {
            name: 'conceptURI',
            fieldLabel: 'Concept URI',
            allowBlank: false,
            width: 510
        });

        var containerIdTextField = Ext4.create('Ext.form.field.Text', {
            name: 'containerId',
            hidden: true
        });

        var containerComboField = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeContainerComboConfig({
            name: 'container',
            editable: false,
            width: 510,
            listeners: {
                select: function(combo) {
                    containerIdTextField.setValue(combo.getValue());
                }
            }
        }));

        var schemaComboField = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeSchemaComboConfig({
            name: 'schemaName',
            forceSelection: true,
            width: 300
        }));

        var queryComboField = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeQueryComboConfig({
            name: 'queryName',
            forceSelection: true,
            width: 300
        }));

        Ext4.create('Ext.form.Panel', {
            border : false,
            renderTo : 'SQVPicker',
            items : [
                conceptTextField,
                containerIdTextField,
                containerComboField,
                schemaComboField,
                queryComboField,
                { xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF }
            ]
        });
    });
</script>