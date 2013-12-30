
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("Ext4ClientApi"));
        resources.add(ClientDependency.fromFilePath("study/ImmunizationSchedule.js"));
        resources.add(ClientDependency.fromFilePath("dataview/DataViewsPanel.css"));
        resources.add(ClientDependency.fromFilePath("study/StudyVaccineDesign.css"));
        return resources;
    }
%>

<style>
    .x4-panel-header-default
    {
        background-color: transparent;
        background-image: none !important;
        border: none;
    }
    .x4-panel-header-text-container-default
    {
        font-size: 15px;
        color: black;
    }

    .x4-grid-cell-inner
    {
        white-space: normal;
    }
</style>

<script type="text/javascript">
    Ext4.onReady(function(){
        var immunogensGrid = Ext4.create('LABKEY.ext4.ImmunogensGrid', {
            renderTo : "immunogens-grid"
        });

        var adjuvantsGrid = Ext4.create('LABKEY.ext4.AdjuvantsGrid', {
            renderTo : "adjuvants-grid"
        });

        var projectMenu = null;
        if (LABKEY.container.type != "project")
        {
            var projectPath = LABKEY.container.path.substring(0, LABKEY.container.path.indexOf("/", 1));
            projectMenu = {
                text: 'Project',
                menu: {
                    items: [{
                        text: 'Immunogen Types',
                        href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignImmunogenTypes'})
                    },{
                        text: 'Genes',
                        href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignGenes'})
                    },{
                        text: 'SubTypes',
                        href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignSubTypes'})
                    }]
                }
            };
        }

        var folderMenu = {
            text: 'Folder',
            menu: {
                items: [{
                    text: 'Immunogen Types',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignImmunogenTypes'})
                },{
                    text: 'Genes',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignGenes'})
                },{
                    text: 'SubTypes',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignSubTypes'})
                }]
            }
        };

        var menu = Ext4.create('Ext.button.Button', {
            text: 'Configure',
            renderTo: 'config-dropdown-menu',
            menu: projectMenu ? {items: [projectMenu, folderMenu]} : folderMenu.menu
        });
    });
</script>

Enter vaccine design information in the grids below.
<div style="width: 810px;">
    <ul>
        <li>Each immunogen and adjuvant in the study should be listed on one row of the grids below.</li>
        <li>Immunogens and adjuvants should have unique names.</li>
        <li>If possible the immunogen description should include specific sequences of HIV Antigens included in the immunogen.</li>
        <li>Use the manage immunizations page to describe the schedule of immunizations and combinations of immunogens and adjuvants administered at each timepoint.</li>
        <li>
            Configure dropdown options at the project level to be shared across study designs or within this folder for
            study specific properties: <span id='config-dropdown-menu'></span>
        </li>
    </ul>
</div>
<div id="immunogens-grid"></div>
<span style='font-style: italic; font-size: smaller;'>* Double click a row to edit the label and type, double click the HIV Antigens cell to edit them separately</span>
<br/><br/>
<div id="adjuvants-grid"></div>
<span style='font-style: italic; font-size: smaller;'>* Double click a row to edit the label</span>
<br/><br/>
<%=textLink("Manage Immunizations", StudyController.ManageImmunizationsAction.class)%>

