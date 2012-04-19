<%
/*
 * Copyright (c) 2012 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getViewContext().getContainer();
%>

<script type="text/javascript">
    LABKEY.requiresExt4ClientAPI();
</script>

<form action="" name="import" enctype="multipart/form-data" method="post">
<table cellpadding=0>
    <%=formatMissedErrorsInTable("form", 2)%>
<tr><td>
You can import a folder archive to create and populate a new folder.  A folder archive is a .folder.zip file or a collection of
individual files that comforms to the LabKey folder export conventions and formats.  In most cases, a folder archive is created
using the folder export feature.  Using export and import, a folder can be moved from one server to another or a new folder can
be created using a standard template. You can also populate a new folder from a template folder on the current server using the "Create
Folder From Template" option.

<%--<p>For more information about exporting, importing, and reloading folders, see <%=helpLink("importExportFolder", "the folder documentation")%>.</p>--%>
</td></tr>
<tr><td class="labkey-announcement-title" align=left><span>Import Folder From Local Zip Archive</span></td></tr>
<tr><td class="labkey-title-area-line"></td></tr>
<tr><td>To import a folder from a zip archive on your local machine (for example, a folder that you have exported and saved
        to your local hard drive), browse to a .folder.zip file, open it, and click the "Import Folder From Local Zip Archive" button below.</td></tr>
<tr><td><input type="file" name="folderZip" size="50"></td></tr>
<tr>
    <td><%=generateSubmitButton("Import Folder From Local Zip Archive")%></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<tr><td class="labkey-announcement-title" align=left><span>Import Folder From Server-Accessible Archive</span></td></tr>
<tr><td class="labkey-title-area-line"></td></tr>
    <tr><td>
        To import a folder from a server-accessible archive, click the "Import Folder Using Pipeline"
        button below, navigate to a .folder.zip archive or a folder.xml file, and click the "Import Data" button.
    </td></tr>
<tr>
    <td><%=generateButton("Import Folder Using Pipeline", urlProvider(PipelineUrls.class).urlBrowse(c, "pipeline"))%></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<tr><td class="labkey-announcement-title" align=left><span>Create Folder From Template</span></td></tr>
<tr><td class="labkey-title-area-line"></td></tr>
    <tr><td>
        To populate the current folder based on a template folder that you have created somewhere on the current server,
        select the template folder and click the "Create Folder From Template" button. From there, you will be given a set
        of folder objects to choose from that you can use from the template folder to populate the current folder.
    </td></tr>
<tr>
    <td><div id="templateSourceFolderDiv"></div></td>
</tr>
<tr>
    <td><%=generateButton("Create Folder From Template", urlProvider(AdminUrls.class).getImportFolderURL(c), "return checkSourceFolderSelection();")%></td>
</tr>
</table>
</form>

<script type="text/javascript">

    var sourceContainers = [];
    var sourceFolderCombo;
    Ext4.onReady(function(){

        LABKEY.Security.getContainers({
            containerPath: '/',
            includeSubfolders: true,
            success: getAdminContainers
        });

        // populate the combobox with the options for the template source folder
        sourceFolderCombo = Ext4.create('Ext.form.ComboBox', {
            itemId: 'sourceId',
            name: 'sourceId',
            fieldLabel: 'Select Template Folder',
            labelWidth: 150,
            width: 400, // TODO: get combo to resize width based on content
            editable: false,
            displayField: 'path',
            valueField: 'id',
            store: Ext4.create('Ext.data.ArrayStore', {
                fields: ['id', 'path'],
                data: sourceContainers
            })
        }).render('templateSourceFolderDiv');
    });

    function getAdminContainers(data)
    {
        // add the container itself to the sourceData
        if (data.id != LABKEY.container.id && data.path != "/" && LABKEY.Security.hasPermission(data.userPermissions, LABKEY.Security.permissions.admin))
        {
            sourceContainers.push([data.id, data.path]);
        }

        // add the container's children to the sourceData
        if (data.children.length > 0)
        {
            for (var i = 0; i < data.children.length; i++)
                getAdminContainers(data.children[i]);
        }
    }

    function checkSourceFolderSelection()
    {
        // if the template source folder is not selected return false
        if (null == sourceFolderCombo.getValue())
        {
            Ext4.Msg.alert('Error', 'Template folder must be selected.');
        }
        else
        {
            window.location = LABKEY.ActionURL.buildURL('admin', 'createFromTemplate', LABKEY.ActionURL.getContainer(),  {sourceId: sourceFolderCombo.getValue()})
        }
        return false;
    }

</script>