<%
/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.data.CompareType"%>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.api.data.Sort" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.query.FilteredTable" %>
<%@ page import="org.labkey.api.query.QueryAction" %>
<%@ page import="org.labkey.api.query.QueryForm" %>
<%@ page import="org.labkey.api.query.QueryView" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.DataView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.query.StudyQuerySchema" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    final ViewContext context = getViewContext();
    JspView<StudyManager.ParticipantViewConfig> me = (JspView<StudyManager.ParticipantViewConfig>) HttpView.currentView();
    final StudyManager.ParticipantViewConfig bean = me.getModelBean();

    QueryForm form = new QueryForm();
    form.setViewContext(context);
    form.setSchemaName(StudyQuerySchema.SCHEMA_NAME);
    form.setQueryName("StudyData");

    QueryView queryView = new QueryView(form, null)
    {
        protected void setupDataView(DataView view)
        {
            view.getRenderContext().setBaseSort(new Sort("-Date"));
            super.setupDataView(view);
        }

        @Override
        protected TableInfo createTable()
        {
            TableInfo table = super.createTable();
            if (bean.getParticipantId() != null)
            {
                if (table instanceof FilteredTable)
                    ((FilteredTable)table).addCondition(new SimpleFilter(FieldKey.fromParts("ParticipantID"), bean.getParticipantId()));

                // remove 'ParticipantId' from the default column list
                List<FieldKey> visible = new ArrayList<>(table.getDefaultVisibleColumns());
                visible.remove(FieldKey.fromParts(StudyService.get().getSubjectColumnName(getContainer())));
                table.setDefaultVisibleColumns(visible);
            }

            return table;
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
    queryView.getSettings().setAllowChooseView(true);
    queryView.getSettings().setAllowCustomizeView(false);

    include(queryView, out);
%>

