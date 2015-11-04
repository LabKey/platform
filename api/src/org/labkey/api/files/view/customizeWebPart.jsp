<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.attachments.AttachmentDirectory" %>
<%@ page import="org.labkey.api.cloud.CloudStoreService" %>
<%@ page import="org.labkey.api.files.FileContentService" %>
<%@ page import="org.labkey.api.files.FileUrls" %>
<%@ page import="org.labkey.api.files.view.CustomizeFilesWebPartView" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Collections" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    CustomizeFilesWebPartView me = (CustomizeFilesWebPartView) HttpView.currentView();
    CustomizeFilesWebPartView.CustomizeWebPartForm form = me.getModelBean();
    ViewContext ctx = getViewContext();
    ActionURL postUrl = form.getWebPart().getCustomizePostURL(ctx);
    FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
    AttachmentDirectory [] attDirs = svc.getRegisteredDirectories(getContainer());

    Collection<String> cloudStoreNames = Collections.emptyList();
    CloudStoreService cloud = ServiceRegistry.get().getService(CloudStoreService.class);
    if (cloud != null)
    {
        cloudStoreNames = cloud.getEnabledCloudStores(getContainer());
    }

    boolean small = false;
    boolean medium = false;
    boolean large = false;

    if (form.getSize() == 350)
        small = true;
    else if(form.getSize() == 650)
        medium = true;
    else
        large = true;

%>
<labkey:form action="<%=postUrl%>" method="POST">
    You can configure this web part to show files from the default directory for this folder or
    from a directory configured by a site administrator.<br><br>

    <table>
        <tr>
            <td class="labkey-form-label">File Root</td>
            <td><select name="fileSet">
                <option value="" <%=selected(null == form.getFileSet())%>>&lt;Default&gt;</option>
                <option value="<%=h(FileContentService.PIPELINE_LINK)%>" <%=selected(FileContentService.PIPELINE_LINK.equals(form.getFileSet()))%>>Pipeline files</option>

<%          for (AttachmentDirectory attDir : attDirs) { %>
                <option value="<%=h(attDir.getLabel())%>" <%=selected(attDir.getLabel().equals(form.getFileSet()))%>><%=h(attDir.getLabel())%></option>
<%          } %>
<%          for (String storeName : cloudStoreNames) {
                String value = CloudStoreService.CLOUD_NAME + "/" + storeName;
%>
                <option value="<%=h(value)%>" <%=selected(value.equals(form.getFileSet()))%>><%=h("Cloud storage: " + storeName)%></option>
<%          } %>
            </select>
<%          if (getUser().isSiteAdmin())
            {
                ActionURL configUrl = urlProvider(FileUrls.class).urlShowAdmin(getContainer()); %>
                <a href="<%=h(configUrl)%>">Configure File Roots</a>
<%          } %>
            </td>
        </tr>

<%      if (HttpView.BODY.equals(form.getLocation())) { %>
            <tr>
                <td class="labkey-form-label">Folder Tree visible&nbsp;<%=PageFlowUtil.helpPopup("Folder Tree", "Checking this selection will display the folder tree by default. The user can toggle the show or hide state of the folder tree using a button on the toolbar.")%></td>
                <td><input type="checkbox" name="folderTreeVisible"<%=checked(form.isFolderTreeVisible())%>></td>
            </tr>
<%      } %>
        <tr>
            <td class="labkey-form-label">Webpart Size</td>
            <td>
                <input type="radio" name="size" value="350"<%=checked(small)%>/>Small<br>
                <input type="radio" name="size" value="650"<%=checked(medium)%>/>Medium<br>
                <input type="radio" name="size" value="1000"<%=checked(large)%>/>Large<br>
            </td>
        </tr>

        <tr><td></td></tr>
        <tr><td></td><td><%= button("Submit").submit(true) %></td></tr>
    </table>
</labkey:form>
