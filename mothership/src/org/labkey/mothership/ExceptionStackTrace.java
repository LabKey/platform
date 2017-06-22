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

package org.labkey.mothership;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.security.User;
import org.labkey.api.util.HashHelpers;

import java.io.StringReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Date;

/**
 * User: jeckels
 * Date: Apr 20, 2006
 */
public class ExceptionStackTrace
{
    private String _container;
    private int _exceptionStackTraceId;
    private String _stackTrace;
    private String _stackTraceHash;
    private Integer _assignedTo;
    private Integer _bugNumber;
    private String _comments;
    private Date _modified;
    private User _modifiedBy;
    private Integer _instances;
    private Date _lastReport;
    private Date _firstReport;

    public String getStackTrace()
    {
        return _stackTrace;
    }

    public void setStackTrace(String stackTrace)
    {
        _stackTrace = stackTrace;
    }

    public int getExceptionStackTraceId()
    {
        return _exceptionStackTraceId;
    }

    public void setExceptionStackTraceId(int exceptionStackTraceId)
    {
        _exceptionStackTraceId = exceptionStackTraceId;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public String getStackTraceHash()
    {
        return _stackTraceHash;
    }

    public void setStackTraceHash(String stackTraceHash)
    {
        _stackTraceHash = stackTraceHash;
    }

    public void hashStackTrace()
    {
        String[] ignoreLineNumberList = { "at java.", "at org.apache.", "at javax.", "at sun." };
        if (_stackTrace != null)
        {
            BufferedReader reader = new BufferedReader(new StringReader(_stackTrace));
            StringBuilder sb = new StringBuilder();
            try
            {
                String line = reader.readLine();
                // Strip off the message part of the exception for hashing
                if (line != null)
                {
                    int index = line.indexOf(":");
                    if (index != -1)
                    {
                        sb.append(line.substring(0, index));
                    }
                    else
                    {
                        sb.append(line);
                    }
                }
                while ((line = reader.readLine()) != null)
                {
                    // Don't include lines that vary based on reflection
                    // Don't include lines that depend on Groovy view numbers
                    if (line.trim().startsWith("at ") && 
                            !line.trim().startsWith("at sun.reflect.")
                            && !(line.trim().startsWith("..."))
                            && !(line.trim().startsWith("at script") && line.contains("run(script") && line.contains(".groovy:"))
                            && !line.trim().startsWith("Position: ")    // Postgres stack traces can include a third line that indicates the position of the error in the SQL
                            && !line.trim().startsWith("Detail:")    // Postgres stack traces can include a second details line
                            && !line.trim().startsWith("Detalhe:"))  // which is oddly sometimes prefixed by "Detalhe:" instead of "Detail:"
                    {
                        // Don't include line numbers that depend on non-labkey version install
                        if (line.trim().startsWith("Caused by:") && line.indexOf(":", line.indexOf(":") + 1) != -1)
                        {
                            line = line.substring(0, line.indexOf(":", line.indexOf(":") + 1));
                        }
                        else
                        {
                            for (String ignoreLineNumber : ignoreLineNumberList)
                            {
                                if (line.trim().startsWith(ignoreLineNumber) && line.contains("("))
                                {
                                    line = line.substring(0, line.lastIndexOf("("));
                                    break;
                                }
                            }
                        }

                        sb.append(line);
                        sb.append("\n");
                    }
                }
            }
            catch (IOException e)
            {
                // Shouldn't happen - this is an in-memory source
                throw new RuntimeException(e);
            }
            _stackTraceHash = HashHelpers.hash(sb.toString());
        }
    }

    public Integer getAssignedTo()
    {
        return _assignedTo;
    }

    public void setAssignedTo(Integer assignedTo)
    {
        _assignedTo = assignedTo;
    }

    public Integer getBugNumber()
    {
        return _bugNumber;
    }

    public void setBugNumber(Integer bugNumber)
    {
        _bugNumber = bugNumber;
    }

    public String getComments()
    {
        return _comments;
    }

    public void setComments(String comments)
    {
        _comments = comments;
    }

    public Date getModified()
    {
        return _modified;
    }

    public User getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public void setModifiedBy(User modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public Integer getInstances()
    {
        return _instances == null ? 0 : _instances;
    }

    public void setInstances(Integer instances)
    {
        _instances = instances;
    }

    public Date getLastReport()
    {
        return _lastReport;
    }

    public void setLastReport(Date lastReport)
    {
        _lastReport = lastReport;
    }

    public Date getFirstReport()
    {
        return _firstReport;
    }

    public void setFirstReport(Date firstReport)
    {
        _firstReport = firstReport;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testJSONExceptionCombining()
        {
            ExceptionStackTrace stackTrace1 = new ExceptionStackTrace();
            stackTrace1.setStackTrace("org.json.JSONException: Expected a ',' or '}' at character 95 of {\"schemaName\": \"lists\",\n" +
                    "           \"queryName\": \"VBD-list\",\n" +
                    "           \"sql\": \"select * from \"VBD-list\" where repositoryName=\"Breast Specimen Repository\"\"\n" +
                    "         }\n" +
                    "\tat org.json.JSONTokener.syntaxError(JSONTokener.java:450)\n" +
                    "\tat org.json.JSONObject.<init>(JSONObject.java:169)\n" +
                    "\tat org.json.JSONObject.<init>(JSONObject.java:292)\n" +
                    "\tat org.labkey.api.action.ApiAction.getJsonObject(ApiAction.java:255)\n" +
                    "\tat org.labkey.api.action.ApiAction.handlePost(ApiAction.java:108)\n" +
                    "\tat org.labkey.api.action.ApiAction.handleRequest(ApiAction.java:85)\n" +
                    "\tat org.labkey.api.action.BaseViewAction.handleRequestInternal(BaseViewAction.java:173)\n" +
                    "\tat org.springframework.web.servlet.mvc.AbstractController.handleRequest(AbstractController.java:153)\n" +
                    "\tat org.labkey.api.action.SpringActionController.handleRequest(SpringActionController.java:346)\n" +
                    "\tat org.labkey.api.module.DefaultModule.dispatch(DefaultModule.java:874)\n" +
                    "\tat org.labkey.api.view.ViewServlet.service(ViewServlet.java:156)\n");

            ExceptionStackTrace stackTrace2 = new ExceptionStackTrace();
            stackTrace2.setStackTrace("org.json.JSONException: Expected a ',' or '}' at character 127 of {\"schemaName\": \"lists\",\n" +
                    "           \"queryName\": \"VBD-list\",\n" +
                    "           \"sql\": \"select * from 'VBD-list' where repositoryName=\"Breast Specimen Repository\"\"\n" +
                    "         }\n" +
                    "\tat org.json.JSONTokener.syntaxError(JSONTokener.java:450)\n" +
                    "\tat org.json.JSONObject.<init>(JSONObject.java:169)\n" +
                    "\tat org.json.JSONObject.<init>(JSONObject.java:292)\n" +
                    "\tat org.labkey.api.action.ApiAction.getJsonObject(ApiAction.java:255)\n" +
                    "\tat org.labkey.api.action.ApiAction.handlePost(ApiAction.java:108)\n" +
                    "\tat org.labkey.api.action.ApiAction.handleRequest(ApiAction.java:85)\n" +
                    "\tat org.labkey.api.action.BaseViewAction.handleRequestInternal(BaseViewAction.java:173)\n" +
                    "\tat org.springframework.web.servlet.mvc.AbstractController.handleRequest(AbstractController.java:153)\n" +
                    "\tat org.labkey.api.action.SpringActionController.handleRequest(SpringActionController.java:346)\n" +
                    "\tat org.labkey.api.module.DefaultModule.dispatch(DefaultModule.java:874)\n" +
                    "\tat org.labkey.api.view.ViewServlet.service(ViewServlet.java:156)\n");

            stackTrace1.hashStackTrace();
            stackTrace2.hashStackTrace();
            assertEquals(stackTrace1.getStackTraceHash(), stackTrace2.getStackTraceHash());

            // Change a line number and make sure we get a different hash
            ExceptionStackTrace stackTrace3 = new ExceptionStackTrace();
            stackTrace3.setStackTrace("org.json.JSONException: Expected a ',' or '}' at character 127 of {\"schemaName\": \"lists\",\n" +
                    "           \"queryName\": \"VBD-list\",\n" +
                    "           \"sql\": \"select * from 'VBD-list' where repositoryName=\"Breast Specimen Repository\"\"\n" +
                    "         }\n" +
                    "\tat org.json.JSONTokener.syntaxError(JSONTokener.java:451)\n" +
                    "\tat org.json.JSONObject.<init>(JSONObject.java:169)\n" +
                    "\tat org.json.JSONObject.<init>(JSONObject.java:292)\n" +
                    "\tat org.labkey.api.action.ApiAction.getJsonObject(ApiAction.java:255)\n" +
                    "\tat org.labkey.api.action.ApiAction.handlePost(ApiAction.java:108)\n" +
                    "\tat org.labkey.api.action.ApiAction.handleRequest(ApiAction.java:85)\n" +
                    "\tat org.labkey.api.action.BaseViewAction.handleRequestInternal(BaseViewAction.java:173)\n" +
                    "\tat org.springframework.web.servlet.mvc.AbstractController.handleRequest(AbstractController.java:153)\n" +
                    "\tat org.labkey.api.action.SpringActionController.handleRequest(SpringActionController.java:346)\n" +
                    "\tat org.labkey.api.module.DefaultModule.dispatch(DefaultModule.java:874)\n" +
                    "\tat org.labkey.api.view.ViewServlet.service(ViewServlet.java:156)\n");

            stackTrace3.hashStackTrace();
            assertNotSame(stackTrace1.getStackTraceHash(), stackTrace3.getStackTraceHash());
        }

        @Test
        public void testReflectionHashCombining()
        {
            ExceptionStackTrace stackTrace1 = new ExceptionStackTrace();
            stackTrace1.setStackTrace("java.lang.NullPointerException\n" +
                    "\tat org.labkey.api.view.ViewController.requiresPermission(ViewController.java:231)\n" +
                    "\tat Experiment.ExperimentController.showRun(ExperimentController.java:788)\n" +
                    "\tat Experiment.ExperimentController.showRunGraphDetail(ExperimentController.java:380)\n" +
                    "\tat sun.reflect.GeneratedMethodAccessor709.invoke(Unknown Source)\n" +
                    "\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:25)\n" +
                    "\tat java.lang.reflect.Method.invoke(Method.java:585)");

            ExceptionStackTrace stackTrace2 = new ExceptionStackTrace();
            stackTrace2.setStackTrace("java.lang.NullPointerException\n" +
                    "\tat org.labkey.api.view.ViewController.requiresPermission(ViewController.java:231)\n" +
                    "\tat Experiment.ExperimentController.showRun(ExperimentController.java:788)\n" +
                    "\tat Experiment.ExperimentController.showRunGraphDetail(ExperimentController.java:380)\n" +
                    "\tat sun.reflect.GeneratedMethodAccessor105.invoke(Unknown Source)\n" +
                    "\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:25)\n" +
                    "\tat java.lang.reflect.Method.invoke(Method.java:585)");

            stackTrace1.hashStackTrace();
            stackTrace2.hashStackTrace();
            assertEquals(stackTrace1.getStackTraceHash(), stackTrace2.getStackTraceHash());
        }

        @Test
        public void testPostgresPositionCombining()
        {
            ExceptionStackTrace stackTrace1 = new ExceptionStackTrace();
            stackTrace1.setStackTrace("org.postgresql.util.PSQLException: ERROR: function rowidin(unknown) does not exist\n" +
                    "  Hint: No function matches the given name and argument types. You might need to add explicit type casts.\n" +
                    "  Position: 2473\n" +
                    "\tat org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2103)\n" +
                    "\tat org.postgresql.core.v3.QueryExecutorImpl.processResults(QueryExecutorImpl.java:1836)\n" +
                    "\tat org.postgresql.core.v3.QueryExecutorImpl.execute(QueryExecutorImpl.java:257)\n" +
                    "\tat org.postgresql.jdbc2.AbstractJdbc2Statement.execute(AbstractJdbc2Statement.java:512)\n" +
                    "\tat org.postgresql.jdbc2.AbstractJdbc2Statement.executeWithFlags(AbstractJdbc2Statement.java:388)\n" +
                    "\tat org.postgresql.jdbc2.AbstractJdbc2Statement.executeQuery(AbstractJdbc2Statement.java:273)\n" +
                    "\tat org.apache.tomcat.dbcp.dbcp.DelegatingPreparedStatement.executeQuery(DelegatingPreparedStatement.java:96)\n" +
                    "\tat org.apache.tomcat.dbcp.dbcp.DelegatingPreparedStatement.executeQuery(DelegatingPreparedStatement.java:96)\n" +
                    "\tat org.labkey.api.data.dialect.StatementWrapper.executeQuery(StatementWrapper.java:570)\n" +
                    "\tat org.labkey.api.data.Table._executeQuery(Table.java:153)\n" +
                    "\tat org.labkey.api.data.Table.executeQuery(Table.java:324)\n" +
                    "\tat org.labkey.api.data.Table.selectForDisplay(Table.java:1219)\n" +
                    "\tat org.labkey.api.data.Table.selectForDisplay(Table.java:1191)\n" +
                    "\tat org.labkey.ms2.peptideview.NestedRenderContext.selectForDisplay(NestedRenderContext.java:224)\n" +
                    "\tat org.labkey.api.data.RenderContext.getResultSet(RenderContext.java:277)\n" +
                    "\tat org.labkey.api.data.DataRegion.getResultSet(DataRegion.java:604)\n" +
                    "\tat org.labkey.api.data.DataRegion.getResultSet(DataRegion.java:587)\n" +
                    "\tat org.labkey.api.query.QueryView.getExcelWriter(QueryView.java:1697)\n" +
                    "\tat org.labkey.api.query.QueryView.exportToExcel(QueryView.java:1757)\n" +
                    "\tat org.labkey.api.query.QueryView.exportToExcel(QueryView.java:1742)\n" +
                    "\tat org.labkey.api.query.QueryView.exportToExcel(QueryView.java:1737)\n" +
                    "\tat org.labkey.ms2.peptideview.AbstractQueryMS2RunView$AbstractMS2QueryView.exportToExcel(AbstractQueryMS2RunView.java:227)\n" +
                    "\tat org.labkey.ms2.peptideview.AbstractQueryMS2RunView.exportToExcel(AbstractQueryMS2RunView.java:149)\n" +
                    "\tat org.labkey.ms2.MS2Controller.exportPeptides(MS2Controller.java:3641)\n" +
                    "\tat org.labkey.ms2.MS2Controller.access$8500(MS2Controller.java:188)\n" +
                    "\tat org.labkey.ms2.MS2Controller$ExportSelectedPeptidesAction.export(MS2Controller.java:3596)\n" +
                    "\tat org.labkey.ms2.MS2Controller$ExportSelectedPeptidesAction.export(MS2Controller.java:3591)\n" +
                    "\tat org.labkey.api.action.ExportAction.getView(ExportAction.java:41)\n" +
                    "\tat org.labkey.api.action.SimpleViewAction.handleRequest(SimpleViewAction.java:61)\n" +
                    "\tat org.labkey.api.action.BaseViewAction.handleRequestInternal(BaseViewAction.java:173)\n" +
                    "\tat org.springframework.web.servlet.mvc.AbstractController.handleRequest(AbstractController.java:153)\n" +
                    "\tat org.labkey.api.action.SpringActionController.handleRequest(SpringActionController.java:345)\n" +
                    "\tat org.labkey.api.module.DefaultModule.dispatch(DefaultModule.java:890)\n" +
                    "\tat org.labkey.api.view.ViewServlet.service(ViewServlet.java:157)\n" +
                    "\tat javax.servlet.http.HttpServlet.service(HttpServlet.java:717)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:290)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:206)\n" +
                    "\tat org.labkey.api.data.TransactionFilter.doFilter(TransactionFilter.java:36)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:235)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:206)\n" +
                    "\tat org.labkey.core.filters.SetCharacterEncodingFilter.doFilter(SetCharacterEncodingFilter.java:118)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:235)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:206)\n" +
                    "\tat org.labkey.api.module.ModuleLoader.doFilter(ModuleLoader.java:694)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:235)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:206)\n" +
                    "\tat org.labkey.api.security.AuthFilter.doFilter(AuthFilter.java:147)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:235)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:206)\n" +
                    "\tat org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:233)\n" +
                    "\tat org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:191)\n" +
                    "\tat org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:127)\n" +
                    "\tat org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:102)\n" +
                    "\tat org.apache.catalina.valves.AccessLogValve.invoke(AccessLogValve.java:554)\n" +
                    "\tat org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:109)\n" +
                    "\tat org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:298)\n" +
                    "\tat org.apache.coyote.http11.Http11Processor.process(Http11Processor.java:859)\n" +
                    "\tat org.apache.coyote.http11.Http11Protocol$Http11ConnectionHandler.process(Http11Protocol.java:588)\n" +
                    "\tat org.apache.tomcat.util.net.JIoEndpoint$Worker.run(JIoEndpoint.java:489)\n" +
                    "\tat java.lang.Thread.run(Thread.java:662)");

            ExceptionStackTrace stackTrace2 = new ExceptionStackTrace();
            stackTrace2.setStackTrace("org.postgresql.util.PSQLException: ERROR: function rowidin(unknown) does not exist\n" +
                    "  Hint: No function matches the given name and argument types. You might need to add explicit type casts.\n" +
                    "  Position: 2426\n" +
                    "\tat org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2103)\n" +
                    "\tat org.postgresql.core.v3.QueryExecutorImpl.processResults(QueryExecutorImpl.java:1836)\n" +
                    "\tat org.postgresql.core.v3.QueryExecutorImpl.execute(QueryExecutorImpl.java:257)\n" +
                    "\tat org.postgresql.jdbc2.AbstractJdbc2Statement.execute(AbstractJdbc2Statement.java:512)\n" +
                    "\tat org.postgresql.jdbc2.AbstractJdbc2Statement.executeWithFlags(AbstractJdbc2Statement.java:388)\n" +
                    "\tat org.postgresql.jdbc2.AbstractJdbc2Statement.executeQuery(AbstractJdbc2Statement.java:273)\n" +
                    "\tat org.apache.tomcat.dbcp.dbcp.DelegatingPreparedStatement.executeQuery(DelegatingPreparedStatement.java:96)\n" +
                    "\tat org.apache.tomcat.dbcp.dbcp.DelegatingPreparedStatement.executeQuery(DelegatingPreparedStatement.java:96)\n" +
                    "\tat org.labkey.api.data.dialect.StatementWrapper.executeQuery(StatementWrapper.java:570)\n" +
                    "\tat org.labkey.api.data.Table._executeQuery(Table.java:153)\n" +
                    "\tat org.labkey.api.data.Table.executeQuery(Table.java:324)\n" +
                    "\tat org.labkey.api.data.Table.selectForDisplay(Table.java:1219)\n" +
                    "\tat org.labkey.api.data.Table.selectForDisplay(Table.java:1191)\n" +
                    "\tat org.labkey.ms2.peptideview.NestedRenderContext.selectForDisplay(NestedRenderContext.java:224)\n" +
                    "\tat org.labkey.api.data.RenderContext.getResultSet(RenderContext.java:277)\n" +
                    "\tat org.labkey.api.data.DataRegion.getResultSet(DataRegion.java:604)\n" +
                    "\tat org.labkey.api.data.DataRegion.getResultSet(DataRegion.java:587)\n" +
                    "\tat org.labkey.api.query.QueryView.getExcelWriter(QueryView.java:1697)\n" +
                    "\tat org.labkey.api.query.QueryView.exportToExcel(QueryView.java:1757)\n" +
                    "\tat org.labkey.api.query.QueryView.exportToExcel(QueryView.java:1742)\n" +
                    "\tat org.labkey.api.query.QueryView.exportToExcel(QueryView.java:1737)\n" +
                    "\tat org.labkey.ms2.peptideview.AbstractQueryMS2RunView$AbstractMS2QueryView.exportToExcel(AbstractQueryMS2RunView.java:227)\n" +
                    "\tat org.labkey.ms2.peptideview.AbstractQueryMS2RunView.exportToExcel(AbstractQueryMS2RunView.java:149)\n" +
                    "\tat org.labkey.ms2.MS2Controller.exportPeptides(MS2Controller.java:3641)\n" +
                    "\tat org.labkey.ms2.MS2Controller.access$8500(MS2Controller.java:188)\n" +
                    "\tat org.labkey.ms2.MS2Controller$ExportSelectedPeptidesAction.export(MS2Controller.java:3596)\n" +
                    "\tat org.labkey.ms2.MS2Controller$ExportSelectedPeptidesAction.export(MS2Controller.java:3591)\n" +
                    "\tat org.labkey.api.action.ExportAction.getView(ExportAction.java:41)\n" +
                    "\tat org.labkey.api.action.SimpleViewAction.handleRequest(SimpleViewAction.java:61)\n" +
                    "\tat org.labkey.api.action.BaseViewAction.handleRequestInternal(BaseViewAction.java:173)\n" +
                    "\tat org.springframework.web.servlet.mvc.AbstractController.handleRequest(AbstractController.java:153)\n" +
                    "\tat org.labkey.api.action.SpringActionController.handleRequest(SpringActionController.java:345)\n" +
                    "\tat org.labkey.api.module.DefaultModule.dispatch(DefaultModule.java:890)\n" +
                    "\tat org.labkey.api.view.ViewServlet.service(ViewServlet.java:157)\n" +
                    "\tat javax.servlet.http.HttpServlet.service(HttpServlet.java:717)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:290)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:206)\n" +
                    "\tat org.labkey.api.data.TransactionFilter.doFilter(TransactionFilter.java:36)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:235)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:206)\n" +
                    "\tat org.labkey.core.filters.SetCharacterEncodingFilter.doFilter(SetCharacterEncodingFilter.java:118)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:235)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:206)\n" +
                    "\tat org.labkey.api.module.ModuleLoader.doFilter(ModuleLoader.java:694)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:235)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:206)\n" +
                    "\tat org.labkey.api.security.AuthFilter.doFilter(AuthFilter.java:147)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:235)\n" +
                    "\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:206)\n" +
                    "\tat org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:233)\n" +
                    "\tat org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:191)\n" +
                    "\tat org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:127)\n" +
                    "\tat org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:102)\n" +
                    "\tat org.apache.catalina.valves.AccessLogValve.invoke(AccessLogValve.java:554)\n" +
                    "\tat org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:109)\n" +
                    "\tat org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:298)\n" +
                    "\tat org.apache.coyote.http11.Http11Processor.process(Http11Processor.java:859)\n" +
                    "\tat org.apache.coyote.http11.Http11Protocol$Http11ConnectionHandler.process(Http11Protocol.java:588)\n" +
                    "\tat org.apache.tomcat.util.net.JIoEndpoint$Worker.run(JIoEndpoint.java:489)\n" +
                    "\tat java.lang.Thread.run(Thread.java:662)");

            stackTrace1.hashStackTrace();
            stackTrace2.hashStackTrace();
            assertEquals(stackTrace1.getStackTraceHash(), stackTrace2.getStackTraceHash());
        }

        @Test
        public void testMessageHashCombining()
        {
            ExceptionStackTrace stackTrace1 = new ExceptionStackTrace();
            stackTrace1.setStackTrace("org.apache.commons.beanutils.ConversionException: For input string: \"null\"\n" +
                    "\tat org.apache.commons.beanutils.converters.IntegerConverter.convert(IntegerConverter.java:118)\n" +
                    "\tat org.apache.commons.beanutils.ConvertUtilsBean.convert(ConvertUtilsBean.java:428)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtilsBean.setProperty(BeanUtilsBean.java:1004)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtilsBean.populate(BeanUtilsBean.java:811)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtils.populate(BeanUtils.java:298)");

            ExceptionStackTrace stackTrace2 = new ExceptionStackTrace();
            stackTrace2.setStackTrace("org.apache.commons.beanutils.ConversionException: For input string: \"something\"\n" +
                    "\tat org.apache.commons.beanutils.converters.IntegerConverter.convert(IntegerConverter.java:118)\n" +
                    "\tat org.apache.commons.beanutils.ConvertUtilsBean.convert(ConvertUtilsBean.java:428)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtilsBean.setProperty(BeanUtilsBean.java:1004)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtilsBean.populate(BeanUtilsBean.java:811)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtils.populate(BeanUtils.java:298)");

            ExceptionStackTrace stackTrace3 = new ExceptionStackTrace();
            stackTrace3.setStackTrace("org.apache.commons.beanutils.ConversionException2: For input string: \"something\"\n" +
                    "\tat org.apache.commons.beanutils.converters.IntegerConverter.convert(IntegerConverter.java:118)\n" +
                    "\tat org.apache.commons.beanutils.ConvertUtilsBean.convert(ConvertUtilsBean.java:428)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtilsBean.setProperty(BeanUtilsBean.java:1004)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtilsBean.populate(BeanUtilsBean.java:811)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtils.populate(BeanUtils.java:298)");

            stackTrace1.hashStackTrace();
            stackTrace2.hashStackTrace();
            stackTrace3.hashStackTrace();
            assertEquals(stackTrace1.getStackTraceHash(), stackTrace2.getStackTraceHash());
            assertFalse(stackTrace1.getStackTraceHash().equals(stackTrace3.getStackTraceHash()));
        }

        @Test
        public void testGroovyHashCombining()
        {
            ExceptionStackTrace stackTrace1 = new ExceptionStackTrace();
            stackTrace1.setStackTrace("java.lang.NoSuchMethodError: java.util.AbstractMap.get(Ljava/lang/Object;)Lorg/labkey/experiment/CustomPropertyRenderer;\n" +
                    "\tat gjdk.org.labkey.experiment.ExperimentController$CustomPropertiesView$1_GroovyReflector.invoke(Unknown Source)\n" +
                    "\tat org.codehaus.groovy.runtime.ScriptBytecodeAdapter.invokeMethodN(ScriptBytecodeAdapter.java:187)\n" +
                    "\tat script1184078621037.run(script1184078621037.groovy:27)\n" +
                    "\tat org.labkey.api.view.GroovyView.renderView(GroovyView.java:476)\n" +
                    "\tat org.labkey.api.view.HttpView.render(HttpView.java:113)\n");

            ExceptionStackTrace stackTrace2 = new ExceptionStackTrace();
            stackTrace2.setStackTrace("java.lang.NoSuchMethodError: get\n" +
                    "\tat gjdk.org.labkey.experiment.ExperimentController$CustomPropertiesView$1_GroovyReflector.invoke(Unknown Source)\n" +
                    "\tat org.codehaus.groovy.runtime.ScriptBytecodeAdapter.invokeMethodN(ScriptBytecodeAdapter.java:187)\n" +
                    "\tat script1184310712861.run(script1184310712861.groovy:27)\n" +
                    "\tat org.labkey.api.view.GroovyView.renderView(GroovyView.java:476)\n" +
                    "\tat org.labkey.api.view.HttpView.render(HttpView.java:113)");

            stackTrace1.hashStackTrace();
            stackTrace2.hashStackTrace();
            assertEquals(stackTrace1.getStackTraceHash(), stackTrace2.getStackTraceHash());
        }

        @Test
        public void testVersionHashCombining()
        {
            ExceptionStackTrace stackTrace1 = new ExceptionStackTrace();
            stackTrace1.setStackTrace("org.jfree.data.general.SeriesException: X-value already exists.\n" +
                    "\tat org.labkey.ms2.MS2Controller.showCombinedElutionGraph(MS2Controller.java:2065)\n" +
                    "\tat java.lang.reflect.Method.invoke(Method.java:597)\n" +
                    "\tat javax.servlet.http.HttpServlet.service(HttpServlet.java:802)\n" +
                    "\tat org.apache.beehive.netui.pageflow.FlowController.invokeActionMethod(FlowController.java:815)\n" +
                    "\tat org.apache.beehive.netui.pageflow.FlowController.execute(FlowController.java:308)\n" +
                    "\tCaused by: Exception: 24\n" +
                    "\t... 61 more");

            ExceptionStackTrace stackTrace2 = new ExceptionStackTrace();
            stackTrace2.setStackTrace("org.jfree.data.general.SeriesException: X-value already exists.\n" +
                    "\tat org.labkey.ms2.MS2Controller.showCombinedElutionGraph(MS2Controller.java:2065)\n" +
                    "\tat java.lang.reflect.Method.invoke(Method.java:601)\n" +
                    "\tat javax.servlet.http.HttpServlet.service(HttpServlet.java:805)\n" +
                    "\tat org.apache.beehive.netui.pageflow.FlowController.invokeActionMethod(FlowController.java:815)\n" +
                    "\tat org.apache.beehive.netui.pageflow.FlowController.execute(FlowController.java:32)\n" +
                    "\tCaused by: Exception: 23\n" +
                    "\t... 62 more");

            stackTrace1.hashStackTrace();
            stackTrace2.hashStackTrace();
            assertEquals(stackTrace1.getStackTraceHash(), stackTrace2.getStackTraceHash());
        }
    }
}
