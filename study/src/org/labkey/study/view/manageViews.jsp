<%
/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.query.QueryParam" %>
<%@ page import="org.labkey.api.reports.report.RReport" %>
<%@ page import="org.labkey.api.reports.report.view.RReportBean" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.visualization.VisualizationUrls" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.reports.StudyManageReportsBean" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.query.DataSetQueryView" %>
<%@ page import="org.labkey.study.reports.EnrollmentReport" %>
<%@ page import="org.labkey.study.reports.StudyQueryReport" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>

<script type="text/javascript">
    LABKEY.requiresClientAPI(true);
    LABKEY.requiresScript("reports/rowExpander.js");
    LABKEY.requiresScript("reports/manageViews.js");
</script>

<%
    JspView<StudyManageReportsBean> me = (JspView<StudyManageReportsBean>) HttpView.currentView();
    StudyManageReportsBean form = me.getModelBean();

    ViewContext context = HttpView.currentContext();
    Container c = context.getContainer();

    String schemaName = form.getSchemaName();
    String queryName = form.getQueryName();

    ActionURL permissionURL = new ActionURL(SecurityController.ReportPermissionsAction.class, c);

    RReportBean reportBean = new RReportBean();
    reportBean.setReportType(RReport.TYPE);
    reportBean.setRedirectUrl(context.getActionURL().getLocalURIString());

    ActionURL newRView = ReportUtil.getRReportDesignerURL(context, reportBean);
    ActionURL newTimeChart = PageFlowUtil.urlProvider(VisualizationUrls.class).getTimeChartDesignerURL(c);

    boolean hasEnrollmentReport = EnrollmentReport.getEnrollmentReport(context.getUser(), StudyManager.getInstance().getStudy(c), false) != null;

    Study study = StudyManager.getInstance().getStudy(c);
    ActionURL customizeParticipantURL = new ActionURL(StudyController.CustomizeParticipantViewAction.class, study.getContainer());
    boolean showCustomizeParticipant = context.hasPermission(AdminPermission.class) && context.getUser().isDeveloper();

    // add a sample participant to our URL so that users can see the results of their customization.  This needs to be on the URL
    // since the default custom script reads the participant ID parameter from the URL:
    String[] participantIds = StudyManager.getInstance().getParticipantIds(study, 1);
    if (participantIds != null && participantIds.length > 0)
        customizeParticipantURL.addParameter("participantId", participantIds[0]);

    org.json.JSONArray reportButtons = ReportUtil.getCreateReportButtons(context);
%>

<script type="text/javascript">
    var panel;

/*
    function securityRenderer(value, p, record)
    {
        var href = '<%=permissionURL.getLocalURIString()%>';

        href = href.concat('reportId=');
        href = href.concat(record.data.reportId);

        return "<a href='" + href + "'>" + value + "</a>";
    }

*/
    function renderViews()
    {
        // subclass the views panel
        StudyViewsPanel = Ext.extend(LABKEY.ViewsPanel, {
            /**
             * Override and append a custom button
             */
            getButtons : function() {
                var buttons = StudyViewsPanel.superclass.getButtons.call(this);
                var btnCfg = {
                    text:'Customize <%= h(StudyService.get().getSubjectNounSingular(study.getContainer())) %> View',
                    disabled: <%= !showCustomizeParticipant %>,
                    listeners:{click:function(button, event) {window.location = '<%=customizeParticipantURL%>';}}
                };
                if (this.searchField)
                    buttons.splice(buttons.length-2, 0, btnCfg);
                else
                    buttons.push(btnCfg);
                return buttons;
            },

            convertQuery : function(schema, query, view) {
                var params = [];

                params.push("schemaName=" + schema);
                params.push("queryName=" + query);
                params.push("viewName=" + view);
                params.push("<%=QueryParam.dataRegionName.toString()%>" + "=", "<%=DataSetQueryView.DATAREGION%>");
                params.push("reportType=", "<%=StudyQueryReport.TYPE%>");

                Ext.Msg.show({
                        title : 'Convert Query',
                        msg : "Converting a query view into a top-level view will allow finer grained permission control. Are you sure you want to continue?",
                        buttons: Ext.Msg.YESNO,
                        icon: Ext.Msg.QUESTION,
                        fn: function(btn, text) {
                            if (btn == 'yes')
                            {
                                Ext.Ajax.request({

                                    url: LABKEY.ActionURL.buildURL("study-reports", "convertQueryToReport"), // + '?' + params.join('&'),
                                    method: "POST",
                                    scope: this,
                                    params: {
                                        schemaName: schema,
                                        queryName: query,
                                        viewName: view,
                                        reportType: '<%=StudyQueryReport.TYPE%>',
                                        '<%=QueryParam.dataRegionName%>': '<%=DataSetQueryView.DATAREGION%>'
                                    },
                                    success: function(){panel.grid.store.load();},
                                    failure: function(){Ext.Msg.alert("Convert Query", "Conversion Failed");}
                                });
                            }},
                        id: 'convert_queryView'
                });
            },

            /**
             * Edit the selected view
             */
            editSelected : function(button) {
                var selections = this.grid.selModel.getSelections();

                if (selections.length == 0)
                {
                    Ext.Msg.alert("Rename Views", "There are no views selected");
                    return false;
                }

                if (selections.length > 1)
                {
                    Ext.Msg.alert("Rename Views", "Only one view can be edited at a time");
                    return false;
                }

                if (selections[0].data.inherited)
                {
                    Ext.Msg.alert("Rename Views", "This view is shared from another container. A shared view can be edited only in the container that it was created in.");
                    return;
                }

                if (selections[0].data.queryView)
                {
                    Ext.Msg.alert("Rename Views", "Only views can be renamed. To convert a custom query to a view, expand the row and click on the [convert to view] link");
                    return;
                }
                doEditRecord(button, this.grid, selections[0].data);
            }
        });

        panel = new StudyViewsPanel({
            renderTo: 'viewsGrid',
            <% if (schemaName != null && queryName != null) { %>
                baseQuery: {
                    schemaName: '<%=schemaName%>',
                    queryName: '<%=queryName%>',
                    baseFilterItems: '<%=form.getBaseFilterItems()%>'
                },
                filterDiv: 'filterMsg',
            <% } %>
            container: '<%=c.getPath()%>',
            isAdmin : <%=context.hasPermission(AdminPermission.class)%>,
            dataConnection : new Ext.data.Connection({
                url: LABKEY.ActionURL.buildURL("study-reports", "manageViewsSummary", this.container),
                method: 'GET',
                timeout: 300000
            }),
            expander : new LABKEY.grid.RowExpander({
                tpl : new Ext.XTemplate(
                        '<table>',
                            '<tpl if="description != undefined"><tr><td><b>description</b></td><td>{description}</td></tr></tpl>',
                            '<tr><td><b>folder</b></td><td>{container}</td></tr>',
                            '<tpl if="query != queryLabel"><tr><td><b>query name</b></td><td>{query} ({queryLabel})</td></tr></tpl>',
                            '<tpl if="query == queryLabel"><tr><td><b>query name</b></td><td>{query}</td></tr></tpl>',
                            '<tpl if="schema != undefined"><tr><td><b>schema name</b></td><td>{schema}</td></tr></tpl>',
                            '<tr><td><b>permissions</b></td><td>{permissions}</td>',
                            '<tr><td></td><td>',
                                '<tpl if="runUrl != undefined">&nbsp;<a href="{runUrl}" {[ this.getTarget(values.runTarget) ]} class="labkey-text-link">view</a></tpl>',
                                '<tpl if="editUrl != undefined">&nbsp;<a href="{editUrl}" class="labkey-text-link">edit</a></tpl>',
                                '<tpl if="detailsUrl != undefined">&nbsp;<a href="{detailsUrl}" class="labkey-text-link">details</a></tpl>',
                            <% if (context.hasPermission(AdminPermission.class)) { %>
                                '<tpl if="!queryView && !inherited">&nbsp;<a class="labkey-text-link" href="<%=permissionURL.getLocalURIString()%>reportId={reportId}">permissions</a></tpl>',
                            <% } %>
                                '<tpl if="queryView && !inherited">&nbsp;<a class="labkey-text-link" href=\'#\' onclick=\'panel.convertQuery("{schema}","{query}","{name}");return false;\'>make top-level view</a></tpl></td></tr>',
                        '</table>',
                        {
                            getTarget : function (target) {
                                if (target)
                                    return "target=\"" + target + "\"";
                                else
                                    return "";
                            }
                        })
                }),
            createMenu : <%=reportButtons%>,

            /**
             * Creates the grid row context menu
             */
            getContextMenu : function(grid, data) {
                var menu = StudyViewsPanel.superclass.getContextMenu.call(this, grid, data);

                if (menu != undefined)
                {
                    menu.add('-', {text: 'Permissions', disabled : (data.queryView || data.inherited), handler: function(){window.location = "<%=permissionURL.getLocalURIString()%>reportId=" + data.reportId;}});
                }
                return menu;
            }
        });

        var grid = panel.show();
        var _resize = function(w,h) {
            LABKEY.Utils.resizeToViewport(grid, w, -1); // don't fit to height
        }
        Ext.EventManager.onWindowResize(_resize);
    }

    Ext.onReady(function(){renderViews();});
</script>

<i><p id="filterMsg"></p></i>
<div id="viewsGrid" class="extContainer"></div>
