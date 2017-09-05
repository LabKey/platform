<%
/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.AdminController.ImportFolderForm" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    ImportFolderForm form = (ImportFolderForm) HttpView.currentModel();
    Container c = getViewContext().getContainerNoTab();
    Container project = c.getProject();
    String requestOrigin = (request.getParameter("origin") != null) ? request.getParameter("origin") : "here";
    boolean isStudyRequest = requestOrigin.equals("Study") || requestOrigin.equals("Reload");
    boolean canCreateSharedDatasets = false;

    if (isStudyRequest && !c.isProject() && null != project && project != c)
    {
        if (project.hasPermission(getViewContext().getUser(), AdminPermission.class))
        {
            StudyService svc = StudyService.get();
            if (svc != null)
            {
                Study studyProject = svc.getStudy(project);
                if (null != studyProject && studyProject.getShareDatasetDefinitions())
                    canCreateSharedDatasets = true;
            }
        }
    }

    // default to the display options for a folder import and change the wording/actions based on if this is a
    // first time study import or a study reload
    String noun = "Folder";
    String action = "Import";
    String mainDescription = "You can import a folder archive to populate a folder with data and configuration. A folder archive is " +
            "a .folder.zip file or a collection of individual files that comforms to the LabKey folder export conventions " +
            "and formats.  In most cases, a folder archive is created using the folder export feature.  Using export " +
            "and import, a folder can be moved from one server to another or a new folder can be created using a " +
            "standard template. You can also populate a new folder from a template folder on the current server using " +
            "the \"Create Folder From Template\" option from the folder creation page.";
    String helpLinkTxt = "For more information about exporting and importing folders, see " +
            helpLink("importExportFolder", "the folder documentation") + ".";

    if (requestOrigin.equals("Study"))
    {
        noun = "Study";
        action = "Import";
        mainDescription = "You can import a study archive, or a folder containing a study, to create and populate a new " +
            "study.  A study archive is a .study.zip file or a collection of individual files that conforms to the LabKey " +
            "study export conventions and formats.  In most cases, a study archive is created using the study export " +
            "feature. Using export and import, a study can be moved from one server to another or a new study can be " +
            "created using a standard template.";
        helpLinkTxt = "For more information about exporting, importing, and reloading studies, see " +
                helpLink("importExportStudy", "the study documentation") + ".";
    }
    else if(requestOrigin.equals("Reload"))
    {
        noun = "Study";
        action = "Reload";
        mainDescription = "You can reload a folder archive to update an existing study with new settings and data. " +
            "A folder archive is a .folder.zip file or a collection of individual files that conforms to the LabKey " +
            "study export conventions and formats.  A folder archive can be created using the study export feature " +
            "or via scripts that write data from a master repository into the correct formats.  You may also reload " +
            "using a study archive, which has the format .study.zip. Note: Reloading a study will replace existing " +
            "study data with the data in the archive.";
        helpLinkTxt = "For more information about exporting, importing, and reloading studies, see " +
                helpLink("importExportStudy", "the study documentation") + ".";
    }
%>
<style type="text/css">
    .lk-import-folder label {
        font-weight: normal;
    }
</style>
<labkey:form action="" name="import" enctype="multipart/form-data" method="post">
<table class="lk-import-folder" cellpadding=0>
    <%=formatMissedErrorsInTable("form", 2)%>

    <tr>
        <td>
            <%=h(mainDescription)%>
            <br/><br/>
            <%=text(helpLinkTxt)%>
        </td>
    </tr>
    <tr>
        <td class="labkey-announcement-title" align=left>
            <span><%=h(action)%> <%=h(noun)%> From Local Source</span>
        </td>
    </tr>
    <tr>
        <td class="labkey-title-area-line"></td>
    </tr>
    <tr>
         <td>
            To <%=h(action.toLowerCase())%> from a local source, please choose whether you will be <%=h(action.toLowerCase())%>ing from a zip archive on your local machine or an existing <%=h(noun.toLowerCase())%> on this server.
        </td>
    </tr>
    <tr>
        <td id="SourcePicker" style="padding-top: 5px;"/>
    </tr>
<%
    if (canCreateSharedDatasets)
    {
%>
    <tr>
        <td style="padding-left: 15px; padding-top: 5px;">
            <label><input type="checkbox" name="createSharedDatasets" <%=h(form.isCreateSharedDatasets() ? "checked" : "")%> value="true"> Create shared datasets</label>
        </td>
    </tr>
<%
    }
%>
    <tr>
        <td style="padding-left: 15px; padding-top: 5px;">
            <label><input type="checkbox" name="validateQueries" <%=h(form.isValidateQueries() ? "checked" : "")%> value="true"> Validate all queries after <%=h(action.toLowerCase())%></label>
        </td>
    </tr>
    <tr>
        <td style="padding-left: 15px; padding-top: 5px; padding-bottom: 5px;">
            <label><input type="checkbox" name="advancedImportOptions" <%=h(form.isAdvancedImportOptions() ? "checked" : "")%> value="true"> Show advanced import options</label>
        </td>
    </tr>
    <tr>
        <td>
            <%= button(action + " " + noun).submit(true) %>
        </td>
    </tr>
    <tr>
        <td class="labkey-announcement-title" align=left>
            <span><%=h(action)%> <%=h(noun)%> From Server-Accessible Archive</span>
        </td>
    </tr>
    <tr>
        <td class="labkey-title-area-line"></td>
    </tr>
    <tr>
        <td>
            To <%=h(action.toLowerCase())%> from a server-accessible archive, click the button below to <%=h(action.toLowerCase())%> via Pipeline.
         </td>
    </tr>
    <tr>
        <td>
            <%= button("Use Pipeline").href(urlProvider(PipelineUrls.class).urlBrowse(c, getViewContext().getActionURL())) %>
        </td>
    </tr>
    <tr>
        <td>&nbsp;</td>
    </tr>
</table>
    <input type="hidden" name="sourceTemplateFolderId"/>
</labkey:form>

<script type="text/javascript">
    Ext4.onReady(function()  {
        // note: client dependencies declared in FolderManagementTabStrip
        var templateFolders = [];
        var sourceRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            width: 300,
            columns: 2,
            items: [
                {
                    name: 'folderSource',
                    inputValue: 'zipFile',
                    boxLabel: 'Local zip archive',
                    checked: true
                },
                {
                    name: 'folderSource',
                    inputValue: 'templateFolder',
                    boxLabel: 'Existing folder',
                    checked: false
                }
            ],
            listeners: {
                scope: this,
                change: function(rg, newValue)
                {
                    this.isZipFile = newValue.folderSource == 'zipFile';
                    folderTemplatesComponent.setVisible(!this.isZipFile);
                    zipFileComponent.setVisible(this.isZipFile);
                }
            }
        });

        // todo: this combobox of available template folders script is very similar to script in createFolder.jsp and should be broken out into a common js file
        var folderTemplatesComponent = Ext4.create('Ext.form.field.ComboBox', {
                xtype: 'combo',
                name: 'sourceTemplateFolder',
                hiddenName: 'sourceTemplateFolderId',
                itemId: 'sourceFolderCombo',
                width: 500,
                allowBlank: false,
                displayField: 'path',
                valueField: 'id',
                editable: false,
                validateOnBlur: false,
                hidden: true,
                store: Ext4.create('Ext.data.ArrayStore', {
                    fields: ['id', 'path'],
                    data: templateFolders
                })
                }
         );

        var zipFileComponent = Ext4.create('Ext.Component', {
            html: '<input type="file" name="folderZip" size="50" style="border: none;">',
            hidden: false
        });

        var initTemplateFolders = function(combo) {
            return function(data) {
                getTemplateFolders(data);
                templateFolders = templateFolders.sort(function(a,b) {
                    return (a[1].toUpperCase() > b[1].toUpperCase()) ? 1
                            : (a[1].toUpperCase() < b[1].toUpperCase()) ? -1
                            : 0
                });
                combo.setLoading(false);
            }
        };

        var getTemplateFolders = function(data) {
            // add the container itself to the templateFolder object if it is not the root and the user has admin perm to it
            // and if it is not a workbook or container tab folder
            if (data.path != "/" && LABKEY.Security.hasPermission(data.userPermissions, LABKEY.Security.permissions.admin)
                    && !data.isWorkbook && !data.isContainerTab) {
                templateFolders.push([data.id, data.path]);
            }
            // add the container's children to the templateFolder object
            if (data.children.length > 0) {
                for (var i = 0; i < data.children.length; i++)
                    getTemplateFolders(data.children[i]);
            }
        };

        var folderTemplatesPanel = Ext4.create('Ext.form.Panel', {
            border : false,
            renderTo : 'SourcePicker',
            items : [
                sourceRadioGroup,
                folderTemplatesComponent,
                zipFileComponent]
        });

        if (templateFolders.length == 0) {
            folderTemplatesComponent.setLoading(true);
            LABKEY.Security.getContainers({
                containerPath: '/',
                includeSubfolders: true,
                success: initTemplateFolders(folderTemplatesComponent)
            });
        }
    });
</script>

