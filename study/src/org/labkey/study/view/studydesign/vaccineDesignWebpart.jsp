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
<%@ page import="org.labkey.api.view.WebThemeManager" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.study.controllers.StudyDesignController" %>
<%@ page import="org.labkey.study.model.ProductImpl" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.TreatmentManager" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext4ClientApi"));
        resources.add(ClientDependency.fromPath("study/StudyVaccineDesign.js"));
        resources.add(ClientDependency.fromPath("study/StudyVaccineDesign.css"));
        return resources;
    }
%>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    Container c = getContainer();
    StudyImpl study = StudyManager.getInstance().getStudy(c);
    User user = getUser();
    boolean canEdit  = c.hasPermission(user, UpdatePermission.class);
%>

<style type="text/css">

    table.study-vaccine-design .labkey-col-header {
        background-color: #<%= WebThemeManager.getTheme(c).getGridColor() %>;
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
            <%= button("Edit").href(StudyDesignController.ManageStudyProductsAction.class, getContainer()) %>
<%
        }
%>
        <table class='study-vaccine-design'>
            <tr>
<%
        List<ProductImpl> immunogens = study.getStudyProducts(user, "Immunogen");
        if (immunogens.size() == 0)
        {
            %><td valign="top">No immunogens have been defined.</td><%
        }
        else
        {
%>
            <td valign="top" style="max-width: 800px;">
                <table class="labkey-read-only labkey-data-region labkey-show-borders" style="border: solid #ddd 1px;">
                    <tr>
                        <td class="labkey-col-header" colspan="3"><div class="study-vaccine-design-header">Immunogens</div></td>
                    </tr>
                    <tr>
                        <td class="labkey-col-header">Label</td>
                        <td class="labkey-col-header">Type</td>
                        <td class="labkey-col-header">HIV Antigens</td>
                    </tr>
<%
                for (ProductImpl immunogen : immunogens)
                {
                    String typeLabel = TreatmentManager.getInstance().getStudyDesignImmunogenTypeLabelByName(c, immunogen.getType());
%>
                    <tr>
                        <td class="assay-row-padded-view"><%=h(immunogen.getLabel())%></td>
                        <td class="assay-row-padded-view"><%=h(typeLabel != null ? typeLabel : immunogen.getType())%></td>
                        <td class="assay-row-padded-view"><div class="immunogen-hiv-antigen" id="<%=immunogen.getRowId()%>">...</div></td>
                    </tr>
<%
                }
%>
                </table>
            </td>
<%
        }
%>
            <td style="width: 20px;">&nbsp;</td>
<%
        List<ProductImpl> adjuvants = study.getStudyProducts(user, "Adjuvant");
        if (adjuvants.size() == 0)
        {
            %><td valign="top">No adjuvants have been defined.</td><%
        }
        else
        {
%>
            <td valign="top">
                <table class="labkey-read-only labkey-data-region labkey-show-borders" style="border: solid #ddd 1px;">
                    <tr>
                        <td class="labkey-col-header" colspan="1"><div class="study-vaccine-design-header">Adjuvants</div></td>
                    </tr>
                    <tr><td class="labkey-col-header">Label</td></tr>
<%
                for (ProductImpl adjuvant : adjuvants)
                {
                    %><tr><td class="assay-row-padded-view"><%=h(adjuvant.getLabel())%></td></tr><%
                }
%>
                </table>
            </td>
<%
        }
%>
        </tr></table>
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

        var vaccineDesignHelper = new LABKEY.ext4.VaccineDesignDisplayHelper();
        var geneStore = vaccineDesignHelper.getStudyDesignStore("StudyDesignGenes");
        var subtypeStore = vaccineDesignHelper.getStudyDesignStore("StudyDesignSubtypes");

        // query and display the html table for each immunogen's HIV antigens
        Ext4.each(Ext4.dom.Query.select('.immunogen-hiv-antigen'), function(div){
            LABKEY.Query.selectRows({
                schemaName: 'study',
                queryName: 'ProductAntigen',
                columns: 'Gene,SubType,GenBankId,Sequence',
                filterArray: [LABKEY.Filter.create('ProductId', div.id)],
                sort: 'RowId',
                success: function(data) {
                    div.innerHTML = vaccineDesignHelper.getHIVAntigenDisplay(data.rows, geneStore, subtypeStore);
                }
            });
        });

    });
</script>