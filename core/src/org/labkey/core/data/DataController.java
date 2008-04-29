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
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableXmlUtils;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewController;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.data.xml.TablesDocument;

import java.io.StringWriter;

@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
/**
 * This controller uses the following format to edit any table
 * /contextPath/Data/schema-table/extraPath/action.view
 */
public class DataController extends ViewController
{
    static Logger _log = Logger.getLogger(DataController.class);

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
    protected Forward begin() throws Exception
   {
        return null;
    }


    @Jpf.Action
    protected Forward getSchemaXmlDoc() throws Exception
    {
        if (!getUser().isAdministrator())
            HttpView.throwUnauthorized();

        String dbSchemaName = getRequest().getParameter("_dbschema");
        if (null == dbSchemaName)
            HttpView.throwNotFound("Must specify _dbschema parameter");

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
            HttpView.throwNotFound("Must specify _dbschema parameter");

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
            HttpView.throwNotFound("Must specify _dbschema parameter");

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
}
