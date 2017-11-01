<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController"%>
<%@ page import="org.labkey.study.pipeline.SpecimenArchive" %>
<%@ page import="java.util.zip.ZipException" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpecimenController.ImportSpecimensBean> me =
            (JspView<SpecimenController.ImportSpecimensBean>) HttpView.currentView();
    SpecimenController.ImportSpecimensBean bean = me.getModelBean();
    boolean hasError = !bean.getErrors().isEmpty();
    int archiveCount = bean.getArchives().size();
    String replaceOrMerge = bean.isDefaultMerge() ? "merge" : "replace";
    String mergeText = bean.isEditableSpecimens() ? "Insert new specimens. An error will result if any existing specimens are in the archive." : "Insert new specimens and update existing specimens.";
    String replaceText = bean.isEditableSpecimens() ? "Replace all of the existing specimens. All edits will be lost." : "Replace all of the existing specimens.";
%>
<%= archiveCount %> specimen archive<%= text(archiveCount > 1 ? "s" : "") %> selected.<br><br>
<%
    for (SpecimenArchive archive : bean.getArchives())
    {
        String archiveName = archive.getDefinitionFile().getName();
        try
        {
            List<SpecimenArchive.EntryDescription> entries = archive.getEntryDescriptions(getContainer());
%>
    Specimen archive <b><%= h(archiveName) %></b> contains the following files:<br><br>
    <table class="labkey-data-region-legacy labkey-show-borders">
        <tr><th>File</th><th>Size</th><th>Modified</th></tr>
        <%
            int row = 0;
            for (SpecimenArchive.EntryDescription entry : entries)
            {
        %>
            <tr class="<%=getShadeRowClass(row++ % 2 == 0)%>">
                <td><%= h(entry.getName()) %></td>
                <td align="right"><%= entry.getSize() == 0 ? "0" : Math.max(1, entry.getSize() / 1000) %> kb</td>
                <td><%=formatDateTime(entry.getDate())%></td>
            </tr>
        <%
            }
        %>
    </table><br>
<%
        }
        catch (ZipException z)
        {
            hasError = true;
%>
<p class="labkey-error"> Specimen archive <b><%=h(archiveName)%></b> is corrupt and cannot be read.</p><br/>
<%
        }
    }
%>

<div>
    <%
        if (hasError)
        {
            for (String error : bean.getErrors())
            {
    %>
            <br><font class=labkey-error><%= h(error) %></font>
    <%
            }
        }
        else
        {
%>
    <labkey:form action="<%=h(buildURL(SpecimenController.SubmitSpecimenBatchImport.class))%>" method="POST">
        <input type="hidden" name="path" value="<%= h(bean.getPath()) %>">
        <%
            for (String file : bean.getFiles())
            {
        %>
        <input type="hidden" name="file" value="<%= h(file) %>">
        <%
            }
        %>
        <%
            if (!bean.isNoSpecimens())
            {
        %>
        <labkey:radio id="replace" name="replaceOrMerge" value="replace" currentValue="<%=text(replaceOrMerge)%>" />
        <label for="merge"><b>Replace</b>: <%=text(replaceText)%></label>
        <br>
        <labkey:radio id="merge" name="replaceOrMerge" value="merge" currentValue="<%=text(replaceOrMerge)%>"/>
        <label for="merge"><b>Merge</b>: <%=text(mergeText)%></label>
        <%
            }
            else
            {
        %>
        <input type="hidden" name="replaceOrMerge" value="replace">
        <%
            }
        %>
        <p>

        <%= button("Start Import").submit(true) %>
    </labkey:form>
<%
        }
    %>
</div>
