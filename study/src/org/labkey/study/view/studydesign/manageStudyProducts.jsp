<%
/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.controllers.StudyDesignController" %>
<%@ page import="org.labkey.api.action.ReturnUrlForm" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("study/vaccineDesign/vaccineDesign.lib.xml");
        dependencies.add("study/vaccineDesign/VaccineDesign.css");
    }
%>
<%
    JspView<ReturnUrlForm> me = (JspView<ReturnUrlForm>) HttpView.currentView();
    ReturnUrlForm bean = me.getModelBean();

    Container c = getContainer();
    boolean isDataspaceStudy = c.getProject() != null && c.getProject().isDataspace() && !c.isDataspace();

    String returnUrl = bean.getReturnUrl() != null ? bean.getReturnUrl() : PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c).toString();
%>

<script type="text/javascript">
    Ext4.onReady(function()
    {
        configureStudyProductGrids();
        configureMenu();
    });

    var configureStudyProductGrids = function()
    {
        var immunogensGrid = Ext4.create('LABKEY.VaccineDesign.ImmunogensGrid', {
            renderTo : 'immunogens-grid',
            studyDesignQueryNames : ['StudyDesignImmunogenTypes', 'StudyDesignGenes', 'StudyDesignSubTypes'],
            disableEdit : <%=isDataspaceStudy%>
        });

        var adjuvantsGrid = Ext4.create('LABKEY.VaccineDesign.AdjuvantsGrid', {
            renderTo : 'adjuvants-grid',
            disableEdit : <%=isDataspaceStudy%>
        });

        var saveBtn = Ext4.create('Ext.button.Button', {
            renderTo : 'save-btn',
            width: 75,
            text: 'Save',
            disabled: true,
            hidden: <%=isDataspaceStudy%>,
            handler: function()
            {
                immunogensGrid.getEl().mask('Saving...');
                adjuvantsGrid.getEl().mask('Saving...');
                saveBtn.disable();
                cancelBtn.disable();

                var studyProducts = [];
                Ext4.each(immunogensGrid.getStore().getRange(), function(record)
                {
                    // drop any empty rows that were just added
                    if (Ext4.isDefined(record.get('RowId')) || record.get('Label') != '' || record.get('Type') != '' || record.get('Antigens').length > 0)
                    {
                        var recData = record.data;

                        // drop and empty antigen rows that were just added
                        var antigenArr = [];
                        Ext4.each(recData['Antigens'], function(antigen)
                        {
                            var hasNonNull = false;
                            Ext4.Object.each(antigen, function(key, value){
                                if (value != null && value != '')
                                {
                                    hasNonNull = true;
                                    return false;
                                }
                            });

                            if (Ext4.isDefined(antigen['RowId']) || hasNonNull)
                                antigenArr.push(antigen);
                        });
                        recData['Antigens'] = antigenArr;

                        studyProducts.push(recData);
                    }
                });
                Ext4.each(adjuvantsGrid.getStore().getRange(), function(record)
                {
                    // drop any empty rows that were just added
                    if (Ext4.isDefined(record.get('RowId')) || record.get('Label') != '')
                        studyProducts.push(record.data);
                });

                LABKEY.Ajax.request({
                    url     : LABKEY.ActionURL.buildURL('study-design', 'updateStudyProducts.api'),
                    method  : 'POST',
                    jsonData: {
                        products: studyProducts
                    },
                    success: function(response)
                    {
                        var resp = Ext4.decode(response.responseText);
                        if (resp.success)
                            window.location = <%=q(returnUrl)%>;
                        else
                            immunogensGrid.onFailure("Unknown failure updating study products.");
                    },
                    failure: function(response)
                    {
                        var resp = Ext4.decode(response.responseText);
                        immunogensGrid.onFailure(resp.exception);

                        immunogensGrid.getEl().unmask();
                        adjuvantsGrid.getEl().unmask();
                        saveBtn.enable();
                        cancelBtn.enable();
                    },
                    scope   : this
                });
            }
        });

        var cancelBtn = Ext4.create('Ext.button.Button', {
            renderTo : 'cancel-btn',
            width: 75,
            margin: <%=q(isDataspaceStudy ? "0" : "0 0 0 10px")%>,
            text: <%=q(isDataspaceStudy ? "Done" : "Cancel")%>,
            handler: function() {
                window.location = <%=q(returnUrl)%>;
            }
        });

        // TODO handle listener for page nav to check for dirty state
        immunogensGrid.on('dirtychange', function() { saveBtn.enable(); });
        adjuvantsGrid.on('dirtychange', function() { saveBtn.enable(); });
    };

    var configureMenu = function()
    {
        var projectMenu = null;
        if (LABKEY.container.type != "project")
        {
            var projectPath = LABKEY.container.path.substring(0, LABKEY.container.path.indexOf("/", 1));
            projectMenu = {
                text: 'Project',
                menu: {
                    items: [{
                        text: 'Immunogen Types',
                        href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignImmunogenTypes'}),
                        hrefTarget: '_blank'  // issue 19493
                    },{
                        text: 'Genes',
                        href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignGenes'}),
                        hrefTarget: '_blank'  // issue 19493
                    },{
                        text: 'SubTypes',
                        href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignSubTypes'}),
                        hrefTarget: '_blank'  // issue 19493
                    }]
                }
            };
        }

        var folderMenu = {
            text: 'Folder',
            menu: {
                items: [{
                    text: 'Immunogen Types',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignImmunogenTypes'}),
                    hrefTarget: '_blank'  // issue 19493
                },{
                    text: 'Genes',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignGenes'}),
                    hrefTarget: '_blank'  // issue 19493
                },{
                    text: 'SubTypes',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignSubTypes'}),
                    hrefTarget: '_blank'  // issue 19493
                }]
            }
        };

        Ext4.create('Ext.button.Button', {
            text: 'Configure',
            renderTo: 'config-dropdown-menu',
            menu: projectMenu ? {items: [projectMenu, folderMenu]} : folderMenu.menu
        });
    };
</script>

Enter vaccine design information in the grids below.
<div style="width: 810px;">
    <ul>
        <li>
            Configure dropdown options for immunogen types, genes, and subtypes at the project level to be shared across study designs or within this folder for
            study specific properties: <span id='config-dropdown-menu'></span>
        </li>
        <li>Each immunogen and adjuvant in the study should be listed on one row of the grids below.</li>
        <li>Immunogens and adjuvants should have unique labels.</li>
        <li>If possible, the immunogen description should include specific sequences of HIV Antigens included in the immunogen.</li>
        <li>
            Use the manage treatments page to describe the schedule of treatments and combinations of immunogens and adjuvants administered at each timepoint.
            <%
                ActionURL manageTreatmentsURL = new ActionURL(StudyDesignController.ManageTreatmentsAction.class, getContainer());
                manageTreatmentsURL.addReturnURL(getActionURL());
            %>
            <%=textLink("Manage Treatments", manageTreatmentsURL)%>
        </li>
    </ul>
</div>
<div id="immunogens-grid"></div>
<br/>
<div id="adjuvants-grid"></div>
<br/>
<span id="save-btn"></span>
<span id="cancel-btn"></span>

