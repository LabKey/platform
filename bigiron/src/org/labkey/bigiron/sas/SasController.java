/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.bigiron.sas;

import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Table;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.view.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;
import org.apache.log4j.Logger;

import java.sql.*;

/**
 * User: adam
 * Date: Jan 21, 2009
 * Time: 10:12:04 AM
 */
public class SasController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SasController.class);
    private static final Logger _log = Logger.getLogger(SasController.class);

    public SasController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresSiteAdmin
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            DbSchema sas = SasDbSchema.get("work");
            DbScope scope = sas.getScope();
            Connection con = scope.getConnection();
            DatabaseMetaData dbmd = con.getMetaData();

            ResultSet rs = dbmd.getSchemas();
            ActionURL url = new ActionURL(SchemaAction.class, getContainer());
            ModelAndView data = new ResultSetView(rs, "Schemas", 1, url.getEncodedLocalURIString() + "schema=");

            scope.releaseConnection(con);

            return data;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("SAS Data Set Browser", new ActionURL(BeginAction.class, getContainer()));
        }
    }

    @RequiresSiteAdmin
    public class SchemaAction extends SimpleViewAction<SchemaForm>
    {
        private String _schema;

        public SchemaAction()
        {
        }

        private SchemaAction(String schema)
        {
            _schema = schema;
        }

        public ModelAndView getView(SchemaForm form, BindException errors) throws Exception
        {
            DbSchema sas = SasDbSchema.get(form.getSchema());
            DbScope scope = sas.getScope();
            Connection con = scope.getConnection();
            DatabaseMetaData dma = con.getMetaData();
            ResultSet rs = dma.getTables(null, form.getSchema(), null, null);

            ActionURL url = new ActionURL(TableAction.class, getContainer());
            url.addParameter("schema", form.getSchema());

            ModelAndView mv = new ResultSetView(rs, "Tables", 3, url.getEncodedLocalURIString() + "&table=");

            scope.releaseConnection(con);

            _schema = form.getSchema();

            return mv;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root);
            ActionURL url = new ActionURL(SchemaAction.class, getContainer());
            url.addParameter("schema", _schema);
            root.addChild("schema " + _schema, url);

            return root;
        }
    }

    public static class SchemaForm
    {
        private String _schema;

        public String getSchema()
        {
            return _schema;
        }

        public void setSchema(String schema)
        {
            _schema = schema;
        }
    }

    @RequiresSiteAdmin
    public class TableAction extends SimpleViewAction<TableForm>
    {
        private String _schema;
        private String _table;

        public ModelAndView getView(TableForm form, BindException errors) throws Exception
        {
            DbSchema sas = SasDbSchema.get(form.getSchema());
            ResultSet rs = Table.executeQuery(sas, new SQLFragment("SELECT * FROM " + form.getSchema() + "." + form.getTable()));
            ModelAndView dataView = new ResultSetView(rs, "Table Data");

            DbScope scope = sas.getScope();
            Connection con = scope.getConnection();
            DatabaseMetaData dbmd = con.getMetaData();
            ResultSet md = dbmd.getColumns(null, form.getSchema(), form.getTable(), null);
            ModelAndView metaDataView = new ResultSetView(md, "Table Meta Data");
            ResultSet pk = dbmd.getPrimaryKeys(null, sas.getName(), form.getTable());
            ModelAndView pkView = new ResultSetView(pk, "Primary Key Meta Data");
            scope.releaseConnection(con);

            _schema = form.getSchema();
            _table = form.getTable();

            return new VBox(dataView, metaDataView, pkView);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new SchemaAction(_schema).appendNavTrail(root);
            root.addChild("table " + _table);

            return root;
        }
    }

    public static class TableForm extends SchemaForm
    {
        private String _table;

        public String getTable()
        {
            return _table;
        }

        public void setTable(String table)
        {
            _table = table;
        }
    }

    private static class ResultSetView extends HtmlView
    {
        private ResultSetView(ResultSet rs, String title) throws SQLException
        {
            this(rs, title, 0, null);
        }

        private ResultSetView(ResultSet rs, String title, int column, String link) throws SQLException
        {
            super(getHtml(rs, column, link));
            setTitle(title);
        }

        private static String getHtml(ResultSet rs, int column, String link) throws SQLException
        {
            StringBuilder sb = new StringBuilder("<table>\n");

            ResultSetMetaData md = rs.getMetaData();
            int columnCount = md.getColumnCount();

            sb.append("  <tr>");

            for (int i = 1; i <= columnCount; i++)
            {
                sb.append("<th>").append(md.getColumnName(i)).append("</th>");
            }

            sb.append("</tr>\n");

            while (rs.next())
            {
                sb.append("  <tr>");

                for (int i = 1; i <= columnCount; i++)
                {
                    Object val = rs.getObject(i);

                    sb.append("<td>");

                    if (null != link && column == i)
                        sb.append("<a href=\"").append(link).append(val.toString()).append("\">");

                    sb.append(null == val ? "&nbsp;" : val.toString());

                    if (null != link && column == i)
                        sb.append("</a>");

                    sb.append("</td>");
                }

                sb.append("</tr>\n");
            }

            sb.append("</table>\n");
            rs.close();

            return sb.toString();
        }
    }
}
