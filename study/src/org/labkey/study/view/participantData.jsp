<%
/*
 * Copyright (c) 2010 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.study.query.StudyQuerySchema" %>
<%@ page import="org.labkey.api.util.IdentifierString" %>
<%@ page import="org.labkey.api.data.CompareType" %>
<%@ page import="org.labkey.api.data.Sort" %>
<%@ page import="org.labkey.api.data.DisplayColumn" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.api.query.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    final ViewContext context = getViewContext();
    JspView<StudyManager.ParticipantViewConfig> me = (JspView<StudyManager.ParticipantViewConfig>) HttpView.currentView();
    final StudyManager.ParticipantViewConfig bean = me.getModelBean();

    QueryForm form = new QueryForm();
    form.setViewContext(context);
    form.setSchemaName(new IdentifierString(StudyQuerySchema.SCHEMA_NAME, false));
    form.setQueryName("StudyData");

    QueryView queryView = new QueryView(form, null)
    {
        protected void setupDataView(DataView view)
        {
            view.getRenderContext().setBaseSort(new Sort("-Date"));

            SimpleFilter filter = (SimpleFilter)view.getRenderContext().getBaseFilter();
            if (filter == null)
                filter = new SimpleFilter();
            if (bean.getParticipantId() != null)
                filter.addCondition("ParticipantID", bean.getParticipantId());
            view.getRenderContext().setBaseFilter(filter);

            super.setupDataView(view);
        }

        protected ActionURL urlFor(QueryAction action)
        {
            ActionURL url = super.urlFor(action);
            if (bean.getParticipantId() != null)
                url.addFilter("query", FieldKey.fromParts("ParticipantId"), CompareType.EQUAL, bean.getParticipantId());
            return url;
        }
    };
    queryView.setShadeAlternatingRows(true);
    queryView.setShowBorders(true);
    queryView.setShowRecordSelectors(true);
    queryView.getSettings().setAllowChooseQuery(false);
    queryView.getSettings().setAllowChooseView(true);
    queryView.getSettings().setAllowCustomizeView(false);

    include(queryView, out);
%>

