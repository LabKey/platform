<%
/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.assay.dilution.DilutionAssayRun" %>
<%@ page import="org.labkey.api.assay.nab.Luc5Assay" %>
<%@ page import="org.labkey.api.assay.nab.RenderAssayBean" %>
<%@ page import="org.labkey.api.study.Plate" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<RenderAssayBean> me = (JspView<RenderAssayBean>) HttpView.currentView();
    RenderAssayBean bean = me.getModelBean();
    DilutionAssayRun assay = bean.getAssay();
    String wellFormat = bean.getPlateDataFormat();
%>
<table>
<%
    int plateIndex = 0;
    List<Plate> plates = assay.getPlates();
    boolean multiPlate = plates.size() > 1;
    for (Plate plate : plates)
    {
%>
<tr>
    <td valign="top" style="padding-bottom: 20px;">
<%
        if (multiPlate)
        {
%>
            <h3 style="font-size: 16px">Plate <%= plateIndex+1 %></h3>
<%
        }
%>
        <table class="labkey-data-region-legacy labkey-show-borders plate-summary">
            <tr>
                <td>&nbsp;</td>
                <%
                    for (int c = 1; c <= plate.getColumns(); c++)
                    {
                %>
                <td style="border-bottom:1px solid;text-align:center;"><%=c %></td>
                <%
                    }
                %>
            </tr>
            <%
                for (int row = 0; row < plate.getRows(); row++)
                {
            %>
            <tr>
                <td style="border-right:1px solid"><%=(char) ('A' + row)%></td>

                <%
                    for (int col = 0; col < plate.getColumns(); col++)
                    {
                %>
                <td align=right class="<%=String.format("%s-%s-%s", plateIndex+1, row, col)%>">
                    <%=h(wellFormat != null ? String.format(wellFormat, plate.getWell(row, col).getValue()) : Luc5Assay.intString(plate.getWell(row, col).getValue()))%></td>
                <%
                    }
                %>
            </tr>
            <%
                }
            %>
        </table>
    </td>
</tr>
<%
        plateIndex++;
    }
%>
</table>

<script type="text/javascript">

    Ext4.onReady(function(){

        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL('nabassay', 'getExcludedWells.api'),
            params : {rowId : <%=bean.getRunId()%>},
            scope: this,
            success: function(response){
                var json = Ext4.decode(response.responseText);
                if (json && json.excluded){

                    Ext4.each(json.excluded, function(rec){

                        // mark excluded wells
                        var key = LABKEY.nab.QCUtil.getModelKey(rec);
                        LABKEY.nab.QCUtil.setWellExclusion(key, true, rec.comment, this);
                    }, this);
                }
            }
        });
    });

</script>
