/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
package org.labkey.core.data;

import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionMapping;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.data.*;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.common.util.Pair;
import org.labkey.data.xml.TablesDocument;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletRequest;
import java.io.StringWriter;

@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
/**
 * This controller uses the following format to edit any table
 * /contextPath/Data/schema-table/extraPath/action.view
 */
public class DataController extends ViewController
{
    static Logger _log = Logger.getLogger(DataController.class);
    private String _returnURL = null;
    public static final String RETURN_URL_PARAM = "returnURL";
    public static final String SCHEMA_PARAM = "_schema";
    public static final String TABLE_PARAM = "_table";
    private static final String TINFO_SESSION_ATTRIB = "_tinfo";

    private static final String copyrightblock = "<!-- \n\n" +
            " * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center\n" +
            " *\n" +
            " * Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
            " * you may not use this file except in compliance with the License.\n" +
            " * You may obtain a copy of the License at\n" +
            " *\n" +
            " *     http://www.apache.org/licenses/LICENSE-2.0\n" +
            " *\n" +
            " * Unless required by applicable law or agreed to in writing, software\n" +
            " * distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
            " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
            " * See the License for the specific language governing permissions and\n" +
            " * limitations under the License.\n\n" +
            " -->\n";



    // annotations in {project}/WEB-INF/src/global/Global.app

    @Jpf.Action
    /**
     * This method represents the point of entry into the pageflow
     */
    protected Forward begin(TableForm form) throws Exception
   {
        requiresAdmin();
        if (null != form.getReturnURL())
            _returnURL = form.getReturnURL();

        DataRegion dr = new DataRegion();
        dr.setName("DataRegion");
        dr.setColumns(form.getTable().getUserEditableColumns());
        dr.setShowRecordSelectors(true);
        dr.setButtonBar(getButtonBar(DataRegion.MODE_GRID), DataRegion.MODE_GRID);

        dr.getDisplayColumn(0).setURL(dr.getDetailsLink());
        GridView gridView = new GridView(dr);
        gridView.setContainer(form.getContainer());
        _renderInTemplate(form.getContainer(), gridView);

        return null;
    }

    @Jpf.Action
    protected Forward delete(TableForm form) throws Exception
        {
        requiresAdmin();
        form.doDelete();
        return form.getForward("begin", (Pair) null, true);
        }

    @Jpf.Action(validationErrorForward = @Jpf.Forward(path="showInsert.do", name = "validate"))
    protected Forward insert(TableForm form) throws Exception
        {
        requiresAdmin();
        form.doInsert();
        return form.getPkForward("details");
    }

    @Jpf.Action(validationErrorForward = @Jpf.Forward(path="showUpdate.do", name = "validate"))
    protected Forward update(TableForm form) throws Exception
        {
        requiresAdmin();
        form.doUpdate();
        return form.getPkForward("details");
    }

    @Jpf.Action
    protected Forward showInsert(TableForm form) throws Exception
        {
        BindException errors = null;
        requiresAdmin();
        if (null != form.getReturnURL())
            _returnURL = form.getReturnURL();

        DataRegion dr = new DataRegion();
        dr.setColumns(form.getTable().getUserEditableColumns());
        dr.setButtonBar(getButtonBar(DataRegion.MODE_INSERT), DataRegion.MODE_INSERT);
        InsertView insertView = new InsertView(dr, form, errors);
        _renderInTemplate(form.getContainer(), insertView);

        return null;
    }

    @Jpf.Action
    protected Forward showUpdate(TableForm form) throws Exception
        {
        BindException errors = null;            
        requiresAdmin();
        if (null != form.getReturnURL())
            _returnURL = form.getReturnURL();

        DataRegion dr = new DataRegion();
        dr.setColumns(form.getTable().getUserEditableColumns());
        dr.setButtonBar(getButtonBar(DataRegion.MODE_UPDATE), DataRegion.MODE_UPDATE);
        UpdateView updateView = new UpdateView(dr, form, errors);
        _renderInTemplate(form.getContainer(), updateView);

        return null;
    }

    @Jpf.Action
    protected Forward details(TableForm form) throws Exception
        {
        requiresAdmin();
        if (null != form.getReturnURL())
            _returnURL = form.getReturnURL();

        form.refreshFromDb(false);
        DataRegion dr = new DataRegion();
        dr.setColumns(form.getTable().getUserEditableColumns());
        dr.setButtonBar(getButtonBar(DataRegion.MODE_DETAILS), DataRegion.MODE_DETAILS);
        DetailsView detailsView = new DetailsView(dr, form);
        _renderInTemplate(form.getContainer(), detailsView);
        return null;
    }

    @Jpf.Action
    protected Forward getSchemaXmlDoc() throws Exception
    {
        if (!getUser().isAdministrator())
            HttpView.throwUnauthorized();

        String dbSchemaName = getRequest().getParameter("_dbschema");
        if (null == dbSchemaName)
            HttpView.throwNotFound();

        boolean bFull = false;
        String full = getRequest().getParameter("_full");
        if (null != full)
            bFull = true;


        TablesDocument tdoc = TableXmlUtils.getXmlDocumentFromMetaData(dbSchemaName, bFull);
        StringWriter sw = new StringWriter();

        XmlOptions xOpt = new XmlOptions();
        xOpt.setSavePrettyPrint();

        tdoc.save(sw, xOpt);

        sw.flush();
        HtmlView htmlView = new HtmlView("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + sw.toString() + "");
        htmlView.setFrame(WebPartView.FrameType.NONE);
        includeView(htmlView);

        return null;
    }


    @Jpf.Action
    protected Forward getMergedSchemaXmlDoc() throws Exception
    {
        if (!getUser().isAdministrator())
            HttpView.throwUnauthorized();

        String dbSchemaName = getRequest().getParameter("_dbschema");
        if (null == dbSchemaName)
            HttpView.throwNotFound();

        TablesDocument tdoc = TableXmlUtils.getMergedXmlDocument(dbSchemaName);
        StringWriter sw = new StringWriter();

        sw.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sw.append(copyrightblock);

        XmlOptions xOpt = new XmlOptions();
        xOpt.setSavePrettyPrint();

        tdoc.save(sw, xOpt);

        sw.flush();
        PageFlowUtil.streamFileBytes(getResponse(), dbSchemaName + ".xml", sw.toString().getBytes(), true);

        return null;
    }

    @Jpf.Action
    protected Forward verifySchema() throws Exception
    {
        boolean bCaseSensitive = false;
        boolean bFull = false;
        if (!getUser().isAdministrator())
            HttpView.throwUnauthorized();

        String dbSchemaName = getRequest().getParameter("_dbschema");
        if (null == dbSchemaName)
            HttpView.throwNotFound();

        String caseSensitive = getRequest().getParameter("_caseSensitive");
        if (null != caseSensitive)
            bCaseSensitive = true;

        String full = getRequest().getParameter("_full");
        if (null != full)
            bFull = true;

        String sOut = TableXmlUtils.compareXmlToMetaData(dbSchemaName, bFull, bCaseSensitive);

        HtmlView errView = new HtmlView("<table class=\"DataRegion\"><tr><td>" + sOut + "</td></tr></table>");
        errView.setTitle("DbSchema " + dbSchemaName);
        _renderInTemplate(getContainer(), errView);

        return null;
    }


    private void _renderInTemplate(Container c, HttpView view) throws Exception
    {
        HttpView template = new HomeTemplate(getViewContext(), c, view);
        includeView(template);
    }


    private static TableInfo getTableInfo(HttpServletRequest request)
    {
        TableInfo tinfo = null;

        String schemaName = request.getParameter(SCHEMA_PARAM);
        if (null != schemaName)
        {
            DbSchema schema = DbSchema.get(schemaName);
            if (null != schema)
            {
                String tableName = request.getParameter(TABLE_PARAM);
                tinfo = schema.getTable(tableName);
                if (null == tinfo)
                    HttpView.throwNotFound();
            }
        }

        if (null != tinfo)
            request.getSession(true).setAttribute(TINFO_SESSION_ATTRIB, tinfo);
        else
            tinfo = (TableInfo) request.getSession(true).getAttribute(TINFO_SESSION_ATTRIB);

        if (null == tinfo)
            HttpView.throwNotFound();

        return tinfo;
    }


    private ButtonBar getButtonBar(int mode)
    {
        ButtonBar bb = new ButtonBar();
        switch (mode)
        {
            case DataRegion.MODE_DETAILS:
                bb.add(ActionButton.BUTTON_SHOW_UPDATE);
                bb.add(ActionButton.BUTTON_SHOW_GRID);
                break;
            case DataRegion.MODE_GRID:
                bb.add(ActionButton.BUTTON_DELETE);
                bb.add(ActionButton.BUTTON_SHOW_INSERT);
                break;
            case DataRegion.MODE_UPDATE:
                bb.add(ActionButton.BUTTON_DO_UPDATE);
                break;
            case DataRegion.MODE_INSERT:
                bb.add(ActionButton.BUTTON_DO_INSERT);
                break;
        }

        if (null != _returnURL)
        {
            ActionButton ab = new ActionButton("Done", "Done");
            ab.setActionType(ActionButton.Action.LINK);
            ab.setURL(_returnURL);
            bb.add(ab);
        }

        return bb;
    }

    public static class TableForm extends TableViewForm
    {
        private String _returnURL;

        public String getReturnURL()
        {
            return _returnURL;
        }

        public void setReturnURL(String returnURL)
        {
            _returnURL = returnURL;
        }

        public void reset(ActionMapping arg0, HttpServletRequest request)
        {
            super.reset(arg0, request);

            String returnURL = request.getParameter(RETURN_URL_PARAM);
            if (null != returnURL && returnURL.length() > 0)
                _returnURL = returnURL;

            setTable(getTableInfo(request));
            }
        }
    }
