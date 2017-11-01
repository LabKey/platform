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
<%@ page import="org.labkey.api.assay.dilution.DilutionSummary" %>
<%@ page import="org.labkey.api.assay.dilution.SampleInfoMethod" %>
<%@ page import="org.labkey.api.assay.nab.RenderAssayBean" %>
<%@ page import="org.labkey.api.data.statistics.FitFailedException" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.text.DecimalFormat" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<RenderAssayBean> me = (JspView<RenderAssayBean>) HttpView.currentView();
    RenderAssayBean bean = me.getModelBean();
    DilutionAssayRun assay = bean.getAssay();
%>
<style type="text/css">
	table.cutoff-table {
        border-collapse:collapse;
	}

    table.cutoff-table td {
        padding: 1px 4px;
    }

	td.cutoff-data {
		background-color: #FFFFA0;
        border: 1px solid #DDDDDD;
        text-align:right;
    }

    td.cutoff-heading {
        background-color: #FFFFCC;
        border: 1px solid #AAAAAA;
        font-weight:bold;
        text-align: center;
    }

    td.sample-heading {
        background-color: #FFFFCC;
        border: 1px solid #AAAAAA;
    }
</style>
<table class="cutoff-table" cellspacing="0px">
    <tr>
        <td class="cutoff-heading">&nbsp;</td>
        <td class="cutoff-heading" colspan="<%= 2* assay.getCutoffs().length %>">Cutoff Dilutions</td>
    </tr>
    <tr>
        <td class="cutoff-heading">&nbsp;</td>
        <td class="cutoff-heading" colspan=<%= assay.getCutoffs().length %>>Curve Based</td>
        <td class="cutoff-heading" colspan=<%= assay.getCutoffs().length %>>Point Based</td>
    </tr>
    <tr>
        <td class="cutoff-heading">&nbsp;</td>
        <%
            for (int set = 0; set < 2; set++)
            {
                for (int cutoff : assay.getCutoffs())
                {
            %>
            <td class="cutoff-heading"><%= cutoff %>%</td>
            <%
                }
            }
        %>
    </tr>
    <%
        for (DilutionAssayRun.SampleResult results : bean.getSampleResults())
        {
            String unableToFitMessage = null;

            DilutionSummary summary = results.getDilutionSummary();
            try
            {
                summary.getCurve();
            }
            catch (FitFailedException e)
            {
                unableToFitMessage = e.getMessage();
            }
    %>
    <tr>
        <td class="sample-heading">
            <%=h(results.getCaption(bean.getDataIdentifier()))%>
        </td>
        <%
            for (int set = 0; set < 2; set++)
            {
                for (int cutoff : assay.getCutoffs())
                {
                    %>
        <td class="cutoff-data">
                    <%
                    boolean curveBased = set == 0;

                    if (curveBased && unableToFitMessage != null)
                    {
            %>
                N/A<%= helpPopup("Unable to fit curve", unableToFitMessage)%>
            <%
                    }
                    else
                    {
                        double val = curveBased ? summary.getCutoffDilution(cutoff / 100.0, assay.getRenderedCurveFitType()) :
                                summary.getInterpolatedCutoffDilution(cutoff / 100.0, assay.getRenderedCurveFitType());

                        String modifier = "";
                        if (val == Double.NEGATIVE_INFINITY)
                        {
                            modifier = "&lt; ";
                            val = summary.getMinDilution(assay.getRenderedCurveFitType());
                        }
                        else if (val == Double.POSITIVE_INFINITY)
                        {
                            modifier = "&gt; ";
                            val = summary.getMaxDilution(assay.getRenderedCurveFitType());
                        }

                        DecimalFormat shortDecFormat;
                        if (summary.getMethod() == SampleInfoMethod.Concentration || (val > -1 && val < 1))
                            shortDecFormat = new DecimalFormat("0.###");
                        else
                            shortDecFormat = new DecimalFormat("0");

                        out.write(modifier + shortDecFormat.format(val));
                    }
                        %>
            </td>
                        <%
                }
            }
        %>
    </tr>
    <%
        }
    %>
</table>
