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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONArray;
import org.json.old.JSONException;
import org.json.old.JSONObject;
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
            String result = StringUtils.trimToEmpty(super.renderInternalAsString(file));
            try
            {
                final JSONObject obj = new JSONObject(result);
                HtmlStringBuilder sb = HtmlStringBuilder.of();
                sb.append(HtmlString.unsafe("<div class=labkey-wiki>"));

                JSONArray arr = (JSONArray) obj.get("cells");
                if (null == arr)
                {
                    JSONArray worksheets = (JSONArray) obj.get("worksheets");
                    if (null != worksheets && worksheets.length() > 0)
                        arr = (JSONArray) ((JSONObject) worksheets.get(0)).get("cells");
                }

                // TODO move to stylesheet
                renderStylesheet(sb);

                for (int cellindex = 0; null != arr && cellindex < arr.length(); cellindex++)
                {
                    JSONObject cell = (JSONObject) arr.get(cellindex);
                    String cell_type = String.valueOf(cell.get("cell_type"));
                    String execution_count = null;
                    if (null != cell.get("execution_count"))
                        execution_count = String.valueOf(cell.get("execution_count"));
                    else if (null != cell.get("prompt_number"))
                        execution_count = String.valueOf(cell.get("prompt_number"));

                    HtmlString executionCountDiv = HtmlString.unsafe("<div class='ipynb-cell-index'>" + (execution_count != null ? "[ " + execution_count + " ]" : "") + "</div>");
                    sb.append(HtmlString.unsafe("<div class='ipynb-cell'>"));
                    sb.append(executionCountDiv);

                    sb.append(HtmlString.unsafe("<div class='ipynb-cell-source'>"));

                    switch (cell_type)
                    {
                        case "markdown":
                            renderMarkdownSource(sb, cell);
                            break;
                        case "raw":
                            renderRawSource(sb, cell);
                            break;
                        case "code":
                            renderCodeSource(sb, cell);
                            break;
                        case "header": // old format
                            renderHeaderCell(sb, cell);
                            break;
                    }

                    sb.append(HtmlString.unsafe("</div>")); // ipynb-cell-source

                    JSONArray outputs = (JSONArray) cell.get("outputs");
                    if (outputs != null && outputs.length() > 0)
                    {
                        sb.append(executionCountDiv);
                        sb.append(HtmlString.unsafe("<div class='ipynb-cell-outputs'>"));

                        for (int outputindex = 0; outputindex < outputs.length(); outputindex++)
                        {
                            renderOutput(sb, (JSONObject) outputs.get(outputindex));
                        }

                        sb.append(HtmlString.unsafe("</div>")); // ipynb-cell-outputs
                    }
                    sb.append(HtmlString.unsafe("</div>")); // ipynb-cell
                }
                sb.append(HtmlString.unsafe("</div>")); // labkey-wiki
                return sb.toString();
            }
            catch (Exception ex)
            {
                HtmlStringBuilder sb = HtmlStringBuilder.of();
                sb.append(HtmlString.unsafe("<div class=\"labkey-error\">"));
                sb.append(HtmlString.of(ex.getMessage()));
                sb.append(HtmlString.unsafe("</div>"));
                if (ex instanceof JSONException)
                {
                    sb.append(HtmlString.unsafe("<div>"));
                    sb.append(HtmlString.of(result));
                    sb.append(HtmlString.unsafe("</div>"));
                }
                return sb.toString();
            }
        }

        private void renderStylesheet(HtmlStringBuilder sb)
        {
            sb.append(HtmlString.unsafe(
                """
                <style type="text/css">
                div.ipynb-cell { display:grid; grid-template-columns: 50px auto; }
                
                div.ipynb-cell-index {
                    padding:5px; color:rgb(100,100,100); font-size:small;
                    clear:both; text-align:right; text-overflow:ellipsis, overflow-x:hidden; border:solid blue 0px;
                }
                
                div.ipynb-cell-source { border:solid red 0px;}

                div.ipynb-markdown { padding:5px; }

                div.ipynb-code { padding:5px; }
                div.ipynb-code pre {
                  overflow-x:scroll;
                }

                div.ipynb-outputs {
                  display: inline-block;
                }

                div.ipynb-output { padding:5px; }
                div.ipynb-output pre {
                  border: none;
                  margin: 0px;
                  padding: 0px;
                  overflow-x: auto;
                  overflow-y: auto;
                  word-break: break-all;
                  word-wrap: break-word;
                  white-space: pre-wrap;
                }
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
                    sb.append(HtmlString.unsafe("<div class=\"ipynb-output\"><div class=\"ipynb-error\">"));
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
                                sb.append(HtmlString.unsafe((String)textArray.get(i)));
                            sb.append(HtmlString.unsafe("</div>"));
                            sbOutput.append(sb);
                            return;
                        }
                        if (null != data.get("text/plain") || null != data.get("text"))
                        {
                            boolean isError = "stderr".equals(data.get("name"));
                            var textArray = (JSONArray) Objects.requireNonNullElse(data.get("text/plain"), data.get("text"));
                            sb.append(HtmlString.unsafe("<div class=\"ipynb-output\"><div class=\"" + (isError ? "ipynb-error" : "ipynb-text") + "\">"));
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


        private void renderCodeSource(HtmlStringBuilder sb, JSONObject cell)
        {
            String source = getSource(cell);
            if (null != source)
            {
                sb.append(HtmlString.unsafe("<div class=\"ipynb-code\">"));
                sb.append(HtmlString.unsafe("<code data-language='python'><pre>\n"));
                sb.append(HtmlString.of(source, false));
                sb.append(HtmlString.unsafe("\n</pre></code></div>"));
            }
        }


        private void renderRawSource(HtmlStringBuilder sb, JSONObject cell)
        {
            String source = getSource(cell);
            if (null != source)
            {
                sb.append(HtmlString.unsafe("<div class=\"ipynb-code\">"));
                sb.append(HtmlString.unsafe("<pre>\n"));
                sb.append(HtmlString.of(source, false));
                sb.append(HtmlString.unsafe("\n</pre></div>"));
            }
        }


        private void renderMarkdownSource(HtmlStringBuilder sb, JSONObject cell) throws NoSuchMethodException, ScriptException
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