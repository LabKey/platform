/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.di;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.di.pipeline.TransformRun;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * User: Dax
 * Date: 9/13/13
 * Time: 12:27 PM
 */
abstract public class TransformBaseTable extends VirtualTable
{
    protected SQLFragment _sql;
    private final UserSchema _schema;
    // map base table column name to alias name
    private HashMap<String, String> _nameMap;

    protected HashMap<String, String> buildNameMap()
    {
        HashMap<String, String> colMap = new HashMap<>();
        colMap.put("TransformId", "Name");
        colMap.put("Container", "Folder");
        colMap.put("TransformVersion", "Version");
        colMap.put("RecordCount", "RecordsProcessed");
        colMap.put("ExecutionTime", "ExecutionTime");
        colMap.put("JobId", "JobInfo");
        colMap.put("ExpRunId", "RunInfo");
        colMap.put("TransformRunLog","TransformRunLog");
        return colMap;
    }

    protected HashMap<String, String> getNameMap()
    {
        return _nameMap;
    }


    @NotNull
    public SQLFragment getFromSQL()
    {
        return _sql;
    }

    public TransformBaseTable(UserSchema schema, String name)
    {
        super(DataIntegrationQuerySchema.getSchema(), name);
        _nameMap = buildNameMap();
        _schema = schema;
    }

    protected String getBaseSql()
    {
        SqlDialect dialect = getSqlDialect();
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT t.TransformId AS ");
        sql.append(_nameMap.get("TransformId"));
        sql.append(", t.TransformVersion AS ");
        sql.append(_nameMap.get("TransformVersion"));
        sql.append(", t.Container AS ");
        sql.append(_nameMap.get("Container"));
        sql.append(", t.StartTime AS ");
        sql.append(_nameMap.get("StartTime"));
        sql.append(", t.Status AS ");
        sql.append(_nameMap.get("Status"));
        sql.append(", t.RecordCount AS ");
        sql.append(_nameMap.get("RecordCount"));
        sql.append(", (CAST("); // get the number of seconds as n.nnn
        sql.append(dialect.getDateDiff(Calendar.MILLISECOND, "t.EndTime", "t.StartTime"));
        sql.append(" AS FLOAT)/1000)");
        sql.append(" AS ");
        sql.append(_nameMap.get("ExecutionTime"));
        sql.append(", t.JobId AS ");
        sql.append(_nameMap.get("JobId"));
        sql.append(", e.RowId AS ");
        sql.append(_nameMap.get("ExpRunId"));
        sql.append(", t.TransformRunLog AS ");
        sql.append(_nameMap.get("TransformRunLog"));
        sql.append(", t.TransformRunId");
        sql.append(" FROM ");
        sql.append(DataIntegrationQuerySchema.getTransformRunTableName());
        sql.append(" t\n");
        sql.append(" JOIN exp.experimentRun e ON t.JobId = e.JobId\n");
        return sql.toString();
    }

    protected String getWhereClause()
    {
        return getWhereClause(null);
    }

    // filter out NO_WORK as well as
    // scope to the current container
    protected String getWhereClause(String tableAlias)
    {
        StringBuilder sqlWhere = new StringBuilder();
        sqlWhere.append("WHERE ");
        appendAlias(tableAlias, sqlWhere);
        sqlWhere.append("Status <> '");
        sqlWhere.append(TransformRun.TransformRunStatus.NO_WORK.getDisplayName());
        sqlWhere.append("'");
        if (!_schema.getContainer().isRoot())
        {
            sqlWhere.append(" AND ");
            appendAlias(tableAlias, sqlWhere);
            sqlWhere.append(" Container = '");
            sqlWhere.append(_schema.getContainer().getId());
            sqlWhere.append("'");
        }
        return sqlWhere.toString();
    }

    private void appendAlias(String tableAlias, StringBuilder sqlWhere)
    {
        if (!StringUtils.isEmpty(tableAlias))
        {
            sqlWhere.append(tableAlias);
            sqlWhere.append(".");
        }
    }

    protected void addBaseColumns()
    {
        // name
        ColumnInfo transformId = new ColumnInfo(_nameMap.get("TransformId"), this);
        transformId.setJdbcType(JdbcType.VARCHAR);
        addColumn(transformId);

        // container
        if (_schema.getContainer().isRoot())
        {
            ColumnInfo container = new ColumnInfo(_nameMap.get("Container"), this);
            container.setJdbcType(JdbcType.VARCHAR);
            container.setFk(new ContainerForeignKey(_schema));
            addColumn(container);
        }

        // version
        ColumnInfo transformVersion = new ColumnInfo(_nameMap.get("TransformVersion"), this);
        transformVersion.setJdbcType(JdbcType.INTEGER);
        addColumn(transformVersion);

        //last run
        ColumnInfo startTime = new ColumnInfo(_nameMap.get("StartTime"), this);
        startTime.setJdbcType(JdbcType.TIMESTAMP);
        startTime.setFormat("MM/dd/yy HH:mm");
        startTime.setSortDirection(Sort.SortDirection.DESC);
        addColumn(startTime);

        // last status
        ColumnInfo status = new ColumnInfo(_nameMap.get("Status"), this);
        status.setJdbcType(JdbcType.VARCHAR);
        addColumn(status);

        // records processed
        ColumnInfo recordCount = new ColumnInfo(_nameMap.get("RecordCount"), this);
        recordCount.setJdbcType(JdbcType.INTEGER);
        addColumn(recordCount);

        // execution time
        ColumnInfo execTime = new ColumnInfo(_nameMap.get("ExecutionTime"), this);
        execTime.setJdbcType(JdbcType.DOUBLE);
        addColumn(execTime);

        // job id lookup to log file path
        ColumnInfo jobId = new ColumnInfo(_nameMap.get("JobId"), this);
        jobId.setJdbcType(JdbcType.INTEGER);
        jobId.setFk(new LookupForeignKey("rowId", "FilePath")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return PipelineService.get().getJobsTable(_schema.getUser(), _schema.getContainer());
            }
        });
        jobId.setHidden(true);
        addColumn(jobId);

        status.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new StatusColumn(colInfo, _nameMap);
            }
        });

        ColumnInfo transformRunId = new ColumnInfo("TransformRunId", this);
        transformRunId.setJdbcType(JdbcType.INTEGER);
        transformRunId.setHidden(true);
        addColumn(transformRunId);

        ColumnInfo expRunId = new ColumnInfo(_nameMap.get("ExpRunId"), this);
        expRunId.setJdbcType(JdbcType.INTEGER);
        expRunId.setHidden(true);
        addColumn(expRunId);

        ColumnInfo transformRunLog = new ColumnInfo(_nameMap.get("TransformRunLog"), this);
        transformRunLog.setJdbcType(JdbcType.VARCHAR);
        addColumn(transformRunLog);

    }

    @Override
    public String getSelectName()
    {
        return null;
    }

    public static class StatusColumn extends DataColumn
    {
        private final ColumnInfo _statusColumn;
        FieldKey _jobFieldKey;
        FieldKey _filePathFieldKey;
        private final String ShowLog = "_showLog";
        private boolean _includeShowLogScript;

        public StatusColumn(ColumnInfo status, Map<String, String> nameMap)
        {
            super(status);
            _statusColumn = status;
            String jobColumn = nameMap.get("JobId");
            _jobFieldKey = FieldKey.fromString(_statusColumn.getFieldKey().getParent(), jobColumn);
            _filePathFieldKey = FieldKey.fromString(_statusColumn.getFieldKey().getParent(), jobColumn + "/FilePath");
            _includeShowLogScript = true;
        }

        @Override
        public boolean isSortable()
        {
            return true;
        }

        //
        // ideally we would have another event/hook to render column contents just once for
        // a specific region.  We can't render this in the data region since the column could
        // be used outside a specific view designed for it
        //
        private String getShowLogScript(String dataRegionName)
        {
            StringBuilder script = new StringBuilder();
            script.append("<script type=\"text/javascript\">");
            script.append("function ");
            script.append(dataRegionName).append(ShowLog);
            script.append("(url, title) {");
            script.append("Ext4.Ajax.request({");
            script.append("url:url, method: 'GET', success: function(response) {");
            script.append("var win = new Ext4.Window({");
            script.append("title: title, border: false, html: response.responseText.replace(/\\r\\n/g, \"<br>\"),");
            script.append("closeAction: 'close', autoScroll : true, buttons : [{");
            script.append("text: 'Close', handler : function() { win.close(); } }]");
            script.append("});");
            script.append("win.show();");
            script.append("}");
            script.append("});");
            script.append("}");
            script.append("</script>\n");
            return script.toString();
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            super.addQueryFieldKeys(keys);
            keys.add(_jobFieldKey);
            keys.add(_filePathFieldKey);
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            String dataRegionName = ctx.getCurrentRegion() != null ? ctx.getCurrentRegion().getName() : null;

            if (null != dataRegionName && _includeShowLogScript)
            {
                out.write(getShowLogScript(dataRegionName));
                _includeShowLogScript = false;
            }

            Integer jobId = ctx.get(_jobFieldKey, Integer.class);
            String filePath = ctx.get(_filePathFieldKey, String.class);
            String statusValue = getFormattedValue(ctx);

            if (null != jobId && null != filePath && null != dataRegionName)
            {
                File logFile = new File(filePath);
                if (logFile.exists())
                {
                    String filename = filePath.substring(filePath.lastIndexOf("/") + 1);
                    ActionURL jobAction = PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlShowFile(ctx.getContainer(), jobId, filename);
                    String onClickScript = dataRegionName + ShowLog + "(" + hq(jobAction.toString()) + "," + hq(filename) + ")";
                    String link = PageFlowUtil.unstyledTextLink(statusValue, "#viewLog", onClickScript, null /*id*/);
                    out.write(link);
                    return;
                }
            }

            // if none of the conditions are met above then just write out the status
            // value without the link
            out.write(statusValue);
        }
    }
}
