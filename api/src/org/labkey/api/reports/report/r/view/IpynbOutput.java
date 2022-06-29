/*
 * Copyright (c) 2013-2019 LabKey Corporation
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

package org.labkey.api.reports.report.r.view;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.data.JdbcType;
import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.reports.report.ScriptOutput;
import org.labkey.api.reports.report.python.IpynbReport;
import org.labkey.api.settings.AppProps;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import javax.script.ScriptException;
import java.io.File;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handle rendering Ipynb directly.  It may be better to use nbconvert to render .html or .md for us.  But here's an attempt
 * at doing it ourselves which might be simpler/faster in some ways.  It may also be less complete/compatible.
  */
public class IpynbOutput extends HtmlOutput
{
    public static final String ID = "ipynbout:";

    public IpynbOutput()
    {
        super(ID);
    }

    /* create an output for a known file */
    public IpynbOutput(File output)
    {
        super(ID);
        addFile(output);
    }

    @Override
    public ScriptOutput renderAsScriptOutput(File file) throws Exception
    {
        IpynbOutputView view = new IpynbOutputView(this, getLabel());
        String html = view.renderInternalAsString(file);

        if (null != html)
            return new ScriptOutput(ScriptOutput.ScriptOutputType.html, getName(), html);

        return null;
    }

    @Override
    public HttpView<?> getView(ViewContext context)
    {
        return new IpynbOutputView(this, getLabel());
    }

    @Override
    public @Nullable Thumbnail renderThumbnail(ViewContext context)
    {
        for (File file : getFiles())
        {
            IpynbOutputView view = new IpynbOutputView(this, getLabel());
            Thumbnail thumb = null;

            try
            {
                String html = view.renderInternalAsString(file);
                URI baseURI = new URI(AppProps.getInstance().getBaseServerUrl());
                if (html != null && baseURI != null)
                    thumb = ImageUtil.webThumbnail(context, html, baseURI);
            }
            catch(Exception ignore){}// if we can't get a thumbnail then that is okay; LabKey should use a default

            return thumb;
        }
        return null;
    }

    public static class IpynbOutputView extends HtmlOutputView
    {
        final IpynbReport _report;

        public IpynbOutputView(IpynbOutput param, String label)
        {
            super(param, label);
            _report = (IpynbReport) param.getReport();
        }

        private String getSource(JSONObject cell)
        {
            Object o = cell.get("source");
            if (null == o)
                o = cell.get("input");      // non-compiled ipynb???
            if (o instanceof JSONArray jarray)
            {
                Object[] sourceArray = jarray.toArray();
                return StringUtils.join(sourceArray,"");
            }
            return null;
        }


        Pattern ansiColor = Pattern.compile("\u001B\\[[;0-9]+m");

        String stripAnsiColors(String s)
        {
            if (!s.contains("\u001B["))
                return s;
            StringBuilder sb = new StringBuilder();
            int sub = 0;
            Matcher m = ansiColor.matcher(s);
            while (m.find())
            {

                sb.append(s, sub, m.start());
                sub = m.end();
            }
            sb.append(s, sub, s.length());
            return(sb.toString());
        }


        @Override
        protected String renderInternalAsString(File file) throws Exception
        {
            String result = super.renderInternalAsString(file);
            JSONObject obj;
            try
            {
                obj = new JSONObject(result);
            }
            catch (JSONException ex)
            {
                return null;
            }
            HtmlStringBuilder sb = HtmlStringBuilder.of();
            sb.append(HtmlString.unsafe("<div class=labkey-wiki>"));

            JSONArray arr = (JSONArray) obj.get("cells");
            if (null == arr)
            {
                JSONArray worksheets = (JSONArray)obj.get("worksheets");
                if (null != worksheets && worksheets.length() > 0)
                    arr = (JSONArray)((JSONObject)worksheets.get(0)).get("cells");
            }

            // TODO move to stylesheet
            renderStylesheet(sb);

            for (int cellindex = 0; null != arr && cellindex < arr.length(); cellindex++)
            {
                JSONObject cell = (JSONObject)arr.get(cellindex);
                String cell_type = String.valueOf(cell.get("cell_type"));
                int execution_count = -1;
                try
                {
                    if (null != cell.get("execution_count"))
                        execution_count = ((Integer) JdbcType.INTEGER.convert(cell.get("execution_count")));
                    else if (null != cell.get("prompt_number"))
                        execution_count = ((Integer) JdbcType.INTEGER.convert(cell.get("prompt_number")));
                }
                catch (ConversionException x)
                {
                    // pass
                }

                sb.append(HtmlString.unsafe("<div class='ipynb-cell'>"));
                sb.append(HtmlString.unsafe("<div class='ipynb-cell-index'>"));
                if (execution_count > 0)
                    sb.append("[ " + execution_count + " ]");
                sb.append(HtmlString.unsafe("</div>"));

                sb.append(HtmlString.unsafe("<div class='ipynb-cell-content'>"));

                switch (cell_type)
                {
                    case "markdown":
                        renderMarkdownCell(sb, cell);
                        break;
                    case "code":
                        renderCodeCell(sb, cell);
                        break;
                    case "header": // old format
                        renderHeaderCell(sb, cell);
                        break;
                }

                sb.append(HtmlString.unsafe("</div>"));
            }
            sb.append(HtmlString.unsafe("</div class=labkey-wiki>"));
            return sb.toString();
        }

        private void renderStylesheet(HtmlStringBuilder sb)
        {
            sb.append(HtmlString.unsafe(
                """
                <style type="text/css">
                div.ipynb-cell { }
                
                div.ipynb-cell-index {
                    padding:5px; color:rgb(100,100,100); font-size:small;
                    width:50px; float:left; clear:both; text-align:right; text-overflow:ellipsis, overflow-x:hidden; border:solid blue 0px;
                }
                
                div.ipynb-cell-content { float:left; border:solid red 0px;}

                div.ipynb-markdown { padding:5px; }
                div.ipynb-code { padding:5px; }
                div.ipynb-output { padding:5px; }

                </style>
                """
            ));
        }


        /* see https://ipython.org/ipython-doc/3/notebook/nbformat.html */
        /* TODO it would be nice if Outputs implemented Renderable to avoid putting this all into HtmlStringBuilder */
        private void renderOutput(HtmlStringBuilder sbOutput, JSONObject output)
        {
            HtmlStringBuilder sb = HtmlStringBuilder.of();
            JSONObject data = output;

            // TODO collapsed sections
            boolean collapsed = false;
            if (null != output.get("collapsed"))
                collapsed = (Boolean)JdbcType.BOOLEAN.convert(output.get("collapsed"));

            switch ((String)output.get("output_type"))
            {
                case "error":
                case "pyerr": // old format
                {
                    sb.append(HtmlString.unsafe("<div class=\"ipynb-error\"><div class=\"ipynb-text\">"));
                    sb.append(HtmlString.unsafe("<pre>\n"));
                    sb.append(HtmlString.unsafe("<b>")).append((String)output.get("ename")).append(": ").append((String)output.get("evalue")).append(HtmlString.unsafe("</b><br>"));
                    JSONArray traceback = (JSONArray)output.get("traceback");
                    if (null != traceback)
                    {
                        for (int i=0 ; i<traceback.length() ; i++)
                        {
                            String line = ((String) traceback.get(i));
                            String clean = stripAnsiColors(line);
                            sb.append("    ").append(clean).append("\n") ;
                        }
                    }
                    sb.append(HtmlString.unsafe("</pre></div></div>"));
                    sbOutput.append(sb);
                    return;
                }
                /* ERROR
                {
                  "ename": "KeyError",
                  "output_type": "error",
                  "evalue": "'domain'",
                  "traceback": [
                    "\u001b[0;31m---------------------------------------------------------------------------\u001b[0m",
                    "\u001b[0;31mKeyError\u001b[0m Traceback (most recent call last)",
                    "Input \u001b[0;32mIn [1]\u001b[0m, in \u001b[0;36m<cell line: 3>\u001b[0;34m()\u001b[0m\n\u001b[1;32m 1\u001b[0m \u001b[38;5;28;01mfrom\u001b[39;00m \u001b[38;5;21;01mReportConfig\u001b[39;00m \u001b[38;5;28;01mimport\u001b[39;00m ReportConfig\n\u001b[0;32m----> 3\u001b[0m report \u001b[38;5;241m=\u001b[39m \u001b[43mReportConfig\u001b[49m\u001b[43m(\u001b[49m\u001b[43mconfig_file\u001b[49m\u001b[38;5;241;43m=\u001b[39;49m\u001b[38;5;124;43m'\u001b[39;49m\u001b[38;5;124;43mreport_config.json\u001b[39;49m\u001b[38;5;124;43m'\u001b[39;49m\u001b[43m)\u001b[49m\n\u001b[1;32m 6\u001b[0m \u001b[38;5;28;01mfrom\u001b[39;00m \u001b[38;5;21;01mIPython\u001b[39;00m\u001b[38;5;21;01m.\u001b[39;00m\u001b[38;5;21;01mdisplay\u001b[39;00m \u001b[38;5;28;01mimport\u001b[39;00m display\n\u001b[1;32m 8\u001b[0m \u001b[38;5;28;01mfrom\u001b[39;00m \u001b[38;5;21;01msympy\u001b[39;00m\u001b[38;5;21;01m.\u001b[39;00m\u001b[38;5;21;01minteractive\u001b[39;00m \u001b[38;5;28;01mimport\u001b[39;00m printing\n",
                    "File \u001b[0;32m/tmp/tmp.g3BnHdQFtN/ReportConfig.py:75\u001b[0m, in \u001b[0;36mReportConfig.__init__\u001b[0;34m(self, config, config_file)\u001b[0m\n\u001b[1;32m 72\u001b[0m \u001b[38;5;28;01mif\u001b[39;00m \u001b[38;5;28mself\u001b[39m\u001b[38;5;241m.\u001b[39mconfig\u001b[38;5;241m.\u001b[39mget(\u001b[38;5;124m'\u001b[39m\u001b[38;5;124mcontextPath\u001b[39m\u001b[38;5;124m'\u001b[39m) \u001b[38;5;129;01mis\u001b[39;00m \u001b[38;5;28;01mNone\u001b[39;00m:\n\u001b[1;32m 73\u001b[0m \u001b[38;5;28mself\u001b[39m\u001b[38;5;241m.\u001b[39mconfig[\u001b[38;5;124m'\u001b[39m\u001b[38;5;124mcontextPath\u001b[39m\u001b[38;5;124m'\u001b[39m] \u001b[38;5;241m=\u001b[39m url\u001b[38;5;241m.\u001b[39mpath\n\u001b[0;32m---> 75\u001b[0m \u001b[38;5;28;01mif\u001b[39;00m \u001b[38;5;129;01mnot\u001b[39;00m \u001b[38;5;28;43mself\u001b[39;49m\u001b[38;5;241;43m.\u001b[39;49m\u001b[43mconfig\u001b[49m\u001b[43m[\u001b[49m\u001b[38;5;124;43m'\u001b[39;49m\u001b[38;5;124;43mdomain\u001b[39;49m\u001b[38;5;124;43m'\u001b[39;49m\u001b[43m]\u001b[49m \u001b[38;5;129;01mor\u001b[39;00m \u001b[38;5;28mself\u001b[39m\u001b[38;5;241m.\u001b[39mconfig[\u001b[38;5;124m'\u001b[39m\u001b[38;5;124mcontainerPath\u001b[39m\u001b[38;5;124m'\u001b[39m] \u001b[38;5;129;01mis\u001b[39;00m \u001b[38;5;28;01mNone\u001b[39;00m \u001b[38;5;129;01mor\u001b[39;00m \u001b[38;5;28mself\u001b[39m\u001b[38;5;241m.\u001b[39mconfig[\u001b[38;5;124m'\u001b[39m\u001b[38;5;124mcontextPath\u001b[39m\u001b[38;5;124m'\u001b[39m] \u001b[38;5;129;01mis\u001b[39;00m \u001b[38;5;28;01mNone\u001b[39;00m \u001b[38;5;129;01mor\u001b[39;00m \u001b[38;5;28mself\u001b[39m\u001b[38;5;241m.\u001b[39mconfig[\u001b[38;5;124m'\u001b[39m\u001b[38;5;124museSsl\u001b[39m\u001b[38;5;124m'\u001b[39m] \u001b[38;5;129;01mis\u001b[39;00m \u001b[38;5;28;01mNone\u001b[39;00m:\n\u001b[1;32m 76\u001b[0m \u001b[38;5;28;01mraise\u001b[39;00m \u001b[38;5;167;01mException\u001b[39;00m(\u001b[38;5;124m\"\u001b[39m\u001b[38;5;124mCould not construct LabKey server URL\u001b[39m\u001b[38;5;124m\"\u001b[39m)\n\u001b[1;32m 78\u001b[0m \u001b[38;5;28mself\u001b[39m\u001b[38;5;241m.\u001b[39mapi \u001b[38;5;241m=\u001b[39m APIWrapper(\u001b[38;5;28mself\u001b[39m\u001b[38;5;241m.\u001b[39mconfig[\u001b[38;5;124m'\u001b[39m\u001b[38;5;124mdomain\u001b[39m\u001b[38;5;124m'\u001b[39m], \u001b[38;5;28mself\u001b[39m\u001b[38;5;241m.\u001b[39mconfig[\u001b[38;5;124m'\u001b[39m\u001b[38;5;124mcontainerPath\u001b[39m\u001b[38;5;124m'\u001b[39m], \u001b[38;5;28mself\u001b[39m\u001b[38;5;241m.\u001b[39mconfig[\u001b[38;5;124m'\u001b[39m\u001b[38;5;124mcontextPath\u001b[39m\u001b[38;5;124m'\u001b[39m], \u001b[38;5;28mself\u001b[39m\u001b[38;5;241m.\u001b[39mconfig[\u001b[38;5;124m'\u001b[39m\u001b[38;5;124museSsl\u001b[39m\u001b[38;5;124m'\u001b[39m],\n\u001b[1;32m 79\u001b[0m api_key\u001b[38;5;241m=\u001b[39m\u001b[38;5;28mself\u001b[39m\u001b[38;5;241m.\u001b[39mapi_key)\n",
                    "\u001b[0;31mKeyError\u001b[0m: 'domain'"
                  ]
                }
                */
                case "display_data":
                case "execute_result":
                case "pyout": // old format
                case "raw":
                case "stream":
                    if (null != output.get("data"))
                        data = (JSONObject) output.get("data");
                    if (null != data)
                    {
                        String imagePng = StringUtils.defaultString((String) data.get("image/png"), (String) data.get("png"));
                        if (null != imagePng)
                        {
                            // let's validate that this at least might be base64
                            if (StringUtils.containsNone(imagePng, "<&-\"'%\\"))
                            {
                                sb.append(HtmlString.unsafe("<div class=\"ipynb-output\"><div class=\"ipynb-image\">"));
                                sb.append(HtmlString.unsafe("<img src=\"data:image/png;base64,"));
                                sb.append(HtmlString.unsafe(imagePng));
                                sb.append(HtmlString.unsafe("\"></div></div>"));
                                sbOutput.append(sb);
                                return;
                            }
                        }
                        if (null != data.get("image/svg+xml"))
                        {
                            var textArray = (JSONArray)data.get("image/svg+xml");
                            sb.append(HtmlString.unsafe("<div class=\"ipynb-svg\">"));
                            for (int i=0 ; i<textArray.length() ; i++)
                                sb.append((String)textArray.get(i));
                            sb.append(HtmlString.unsafe("</div>"));
                            sbOutput.append(sb);
                            return;
                        }
                        if (null != data.get("text/plain") || null != data.get("text"))
                        {
                            var textArray = (JSONArray) Objects.requireNonNullElse(data.get("text/plain"), data.get("text"));
                            sb.append(HtmlString.unsafe("<div class=\"ipynb-output\"><div class=\"ipynb-text\">"));
                            sb.append(HtmlString.unsafe("<pre>\n"));
                            for (int i=0 ; i<textArray.length() ; i++)
                                sb.append((String)textArray.get(i));
                            sb.append(HtmlString.unsafe("</pre></div></div>"));
                            sbOutput.append(sb);
                            return;
                        }
                    }
                    break;
            } // switch

            sbOutput.append(output.toString());
        }


        private void renderCodeCell(HtmlStringBuilder sb, JSONObject cell)
        {
            String source = getSource(cell);
            if (null != source)
            {
                sb.append(HtmlString.unsafe("<div class=\"ipynb-code\">"));
                sb.append(HtmlString.unsafe("<code language-python' style='display:block;'><pre>\n"));
                sb.append(HtmlString.of(source, false));
                sb.append(HtmlString.unsafe("\n</pre></code></div>"));
            }
            if (null != cell.get("outputs"))
            {
                JSONArray outputs = (JSONArray) cell.get("outputs");
                for (int outputindex=0 ; outputindex<outputs.length() ; outputindex++)
                {
                    renderOutput(sb, (JSONObject)outputs.get(outputindex));
                }
            }
        }


        private void renderMarkdownCell(HtmlStringBuilder sb, JSONObject cell) throws NoSuchMethodException, ScriptException
        {
            String source = getSource(cell);
            if (null != source)
            {
                sb.append(HtmlString.unsafe("<div class=\"ipynb-markdown\">"));
                sb.append(HtmlString.unsafe(MarkdownService.get().toHtml(source, Map.of(MarkdownService.Options.html,true))));
                sb.append(HtmlString.unsafe("</div>"));
            }
        }


        private void renderHeaderCell(HtmlStringBuilder sb, JSONObject cell)
        {
            int level = 1;
            if (null != cell.get("level"))
                level = Integer.parseInt((String)cell.get("level"));
            String header = getSource(cell);
            sb.append("<h" + level + ">").append(header).append("</h" + level + ">\n");
        }


        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            String delim = "";
            for (File file : getFiles())
            {
                String html = renderInternalAsString(file);
                if (null != html)
                {
                    out.write(delim);
                    out.write(html);

                    delim = "<br>";
                }
            }
        }
    }
}
