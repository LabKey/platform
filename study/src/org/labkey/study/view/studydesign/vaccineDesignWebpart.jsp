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
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.api.view.WebThemeManager" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.study.controllers.StudyDesignController" %>
<%@ page import="org.labkey.study.model.ProductImpl" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("study/ImmunizationSchedule.js"));
        resources.add(ClientDependency.fromFilePath("study/StudyVaccineDesign.css"));
        return resources;
    }
%>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    Container c = getContainer();
    StudyImpl study = StudyManager.getInstance().getStudy(c);
    User user = getUser();
    boolean canEdit  = c.hasPermission(user, UpdatePermission.class);

    WebTheme theme = WebThemeManager.getTheme(c);
    String link        = theme.getLinkColor();
    String grid        = theme.getGridColor();
%>

<style type="text/css">

    table.study-vaccine-design .labkey-col-header {
        background-color: #<%= grid %>;
    }

    table.study-vaccine-design .labkey-col-header-active {
        background-color: #<%= grid %>;
    }

    table.study-vaccine-design .labkey-col-header-active .gwt-Label {
        color: #<%= link %>;
    }

    table.study-vaccine-design .labkey-col-header-active .gwt-Label:hover {
        color: #<%= link %>;
    }

    table.study-vaccine-design .labkey-row-header {
        background-color: #<%= grid %>;
    }

    table.study-vaccine-design .labkey-row-active .gwt-Label, table.study-vaccine-design .labkey-row-header .gwt-Label {
        color: #<%= link %>;
    }

    table.study-vaccine-design .labkey-row-active .gwt-Label:hover, table.study-vaccine-design .labkey-row-header .gwt-Label:hover {
        color: #<%= link %>;
    }

    table.study-vaccine-design .assay-corner {
        background-color: #<%= grid %>;
    }

    a.labkey-button, a.labkey-button:visited, a.gwt-Anchor {
        color: #<%= link %>;
    }

</style>

<%
    if (study != null)
    {
        %>This section describes the immunogens and adjuvants evaluated in the study.</br><%
        if (canEdit)
        {
%>
            To change the set of immunogens and adjuvants, click the edit button below.<br/>
            <%=generateButton("Edit", StudyDesignController.ManageStudyProductsAction.class)%>
<%
        }
%>
        <table class='study-vaccine-design'>
            <tr><td><h2>Immunogens</h2></td></tr>
<%
        List<ProductImpl> immunogens = study.getStudyProducts(user, "Immunogen");
        if (immunogens.size() == 0)
        {
            %><tr><td>No immunogens have been defined.</td></tr><%
        }
        else
        {
%>
            <tr><td>
                <table class="labkey-read-only labkey-data-region labkey-show-borders" style="border: solid #ddd 1px;">
                    <tr>
                        <td class="labkey-col-header">Label</td>
                        <td class="labkey-col-header">Type</td>
                        <td class="labkey-col-header">HIV Antigens</td>
                    </tr>
<%
                for (ProductImpl immunogen : immunogens)
                {
%>
                    <tr>
                        <td class="assay-row-padded-view"><%=h(immunogen.getLabel())%></td>
                        <td class="assay-row-padded-view"><%=h(immunogen.getType())%></td>
                        <td class="assay-row-padded-view"><div class="immunogen-hiv-antigen" id="<%=immunogen.getRowId()%>">...</div></td>
                    </tr>
<%
                }
%>
                </table>
            </td></tr>
<%
        }
%>
            <tr><td><h2>Adjuvants</h2></td></tr>
<%
        List<ProductImpl> adjuvants = study.getStudyProducts(user, "Adjuvant");
        if (adjuvants.size() == 0)
        {
            %><tr><td>No adjuvants have been defined.</td></tr><%
        }
        else
        {
%>
            <tr><td>
                <table class="labkey-read-only labkey-data-region labkey-show-borders" style="border: solid #ddd 1px;">
                    <tr><td class="labkey-col-header">Label</td></tr>
<%
                for (ProductImpl adjuvant : adjuvants)
                {
                    %><tr><td class="assay-row-padded-view"><%=h(adjuvant.getLabel())%></td></tr><%
                }
%>
                </table>
            </td></tr>
<%
        }
%>
        </table>
<%
    }
    else
    {
%>
        <p>The folder must contain a study in order to display a vaccine design.</p>
<%
    }
%>

<script type="text/javascript">
    Ext4.onReady(function(){

        // query and display the html table for each immunogen's HIV antigens
        Ext4.each(Ext4.dom.Query.select('.immunogen-hiv-antigen'), function(div){
            LABKEY.Query.selectRows({
                schemaName: 'study',
                queryName: 'ProductAntigen',
                columns: 'Gene,SubType,GenBankId,Sequence',
                filterArray: [LABKEY.Filter.create('ProductId', div.id)],
                sort: 'RowId',
                success: function(data) {
                    div.innerHTML = new LABKEY.ext4.VaccineDesignDisplayHelper().getHIVAntigenDisplay(data.rows);
                }
            })
        });

    });
</script>