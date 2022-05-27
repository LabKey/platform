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
import org.json.JSONArray;
import org.json.JSONObject;
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


        @Override
        protected String renderInternalAsString(File file) throws Exception
        {
            String result = super.renderInternalAsString(file);
            JSONObject obj = new JSONObject(result);
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
                if (null != cell.get("execution_count"))
                    execution_count = ((Number)cell.get("execution_count")).intValue();

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
                    case "header":
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


        private void renderOutput(HtmlStringBuilder sb, JSONObject output)
        {
            JSONObject data = output;
            if (null != output.get("data"))
                data = (JSONObject)output.get("data");
            if (null != data)
            {
                String imagePng = StringUtils.defaultString((String)data.get("image/png"),(String)data.get("png"));
                if (null != imagePng)
                {
                    // let's validate that this at least might be base64
                    if (StringUtils.containsNone(imagePng, "<&-\"'%\\"))
                    {
                        sb.append(HtmlString.unsafe("<div class=\"ipynb-output\"><div class=\"ipynb-image\">"));
                        sb.append(HtmlString.unsafe("<img src=\"data:image/png;base64,"));
                        sb.append(HtmlString.unsafe(imagePng));
                        sb.append(HtmlString.unsafe("\"></div></div>"));
                        return;
                    }
                }
                if (null != data.get("text/plain"))
                {
                    String plain = StringUtils.join(((JSONArray) data.get("text/plain")).toArray(), "");
                    sb.append(HtmlString.unsafe("<div class=\"ipynb-output\"><div class=\"ipynb-text\">"));
                    sb.append(HtmlString.unsafe("<pre>\n")).append(HtmlString.of(plain, false)).append(HtmlString.unsafe("</pre></div></div>"));
                    return;
                }
            }
            sb.append(output.toString());
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
                if (source.startsWith("<h2>") && source.endsWith("</h2>")) // BUG
                {
                    sb.append(HtmlString.unsafe("<h2>"));
                    sb.append(HtmlString.unsafe(MarkdownService.get().toHtml(source.substring(4,source.length()-5))));
                    sb.append(HtmlString.unsafe("</h2>"));
                }
                else
                {
                    sb.append(HtmlString.unsafe(MarkdownService.get().toHtml(source)));
                }
                sb.append(HtmlString.unsafe("</div>"));
            }
        }

        private void renderHeaderCell(HtmlStringBuilder sb, JSONObject cell)
        {
            sb.append(HtmlString.unsafe("<h3>HEADER</h3>"));
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
