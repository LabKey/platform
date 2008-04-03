<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.samples.SamplesController"%>
<%@ page import="org.labkey.study.pipeline.SpecimenBatch"%>
<%@ page import="java.util.List"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<org.labkey.study.controllers.samples.SamplesController.ImportSpecimensBean> me =
            (JspView<SamplesController.ImportSpecimensBean>) HttpView.currentView();
    SamplesController.ImportSpecimensBean bean = me.getModelBean();
    boolean hasError = !bean.getErrors().isEmpty();
    List<SpecimenBatch.EntryDescription> entries = bean.getBatch().getEntryDescriptions();
%>
Specimen archive <b><%= bean.getBatch().getDefinitionFile().getName() %></b> contains the following files:<br><br>
<table cellspacing="0" class="normal">
    <%
        int row = 0;
        for (SpecimenBatch.EntryDescription entry : entries)
        {
    %>
        <tr bgcolor="<%= row++ % 2 == 1 ? "FFFFFF" : "EEEEEE"%>">
            <th align="left"><%= h(entry.getName()) %></th>
            <td>
                <table>
                    <tr>
                        <th align="right">Size</th>
                        <td><%= entry.getSize() / 1000 %> kb</td>
                    </tr>
                    <tr>
                        <th align="right">Modified</th>
                        <td><%= h(formatDateTime(entry.getDate())) %></td>
                    </tr>
                </table>
            </td>
        </tr>
    <%
        }
    %>
</table><br>

<div class="normal">
    <%
        if (hasError)
        {
            for (String error : bean.getErrors())
            {
    %>
            <br><font color=red><%= h(error) %></font>
    <%
            }
        }
        else
        {
            if (bean.isPreviouslyRun())
            {
    %>
    <span style="color:red">WARNING: A file by this name appears to have been previously imported.</span><br>
    To import a file by this name, the old log file must be deleted.<br><br>
    <a href="<%= ActionURL.toPathString("Pipeline-Status", "showList", bean.getContainer())%>">
        Click here</a> to view previous pipeline runs.<br><br>

        <form action="importSpecimenData.post" method=POST>
            <input type="hidden" name="deleteLogfile" value="true">
            <input type="hidden" name="path" value="<%= h(bean.getPath())%>">
            <%= buttonImg("Delete logfile")%>&nbsp;<%= buttonLink("Cancel", ActionURL.toPathString("Pipeline", "begin", bean.getContainer()))%>
        </form>
    <%
            }
            else
            {
    %>
        <form action="submitSpecimenImport.post" method=POST>
            <input type="hidden" name="path" value="<%= h(bean.getPath())%>">
            <%= buttonImg("Start Import")%>
        </form>
    <%
            }
        }
    %>
</div>
