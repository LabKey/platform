/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.experiment.controllers.exp;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.reader.Readers;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.experiment.ExperimentRunGraph;
import org.labkey.experiment.api.ExpRunImpl;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;

/**
 * User: jeckels
 * Date: Dec 18, 2007
 */
public class ExperimentRunGraphView extends WebPartView
{
    private static final Logger _log = Logger.getLogger(ExperimentRunGraphView.class);

    private ExpRunImpl _run;
    private boolean _detail;
    private String _focus;
    private String _focusType;

    public ExperimentRunGraphView(ExpRunImpl run, boolean detail)
    {
        super(FrameType.DIV);
        _run = run;
        _detail = detail;
    }

    public void setFocus(String f)
    {
        if (null != f && !"null".equals(f))
            _focus = f;
    }

    @Override
    protected void renderView(Object model, PrintWriter out)
    {
        try
        {
            ViewContext context = getViewContext();

            out.println("<p>Click on a node in the graph below for details. Run outputs have a bold outline.</p>");

            ExperimentRunGraph.RunGraphFiles files = ExperimentRunGraph.generateRunGraph(context, _run, _detail, _focus, _focusType);
            out.println("<img src=\"" + ExperimentController.ExperimentUrlsImpl.get().getDownloadGraphURL(_run, _detail, _focus, _focusType) + "\" usemap=\"#graphmap\" >");
            
            if (files.getMapFile().exists())
            {
                out.println("<map name=\"graphmap\">");

                try (Reader reader = Readers.getReader(files.getMapFile()))
                {
                    IOUtils.copy(reader, out);
                }
                finally
                {
                    files.release();
                }

                out.write("</map>");
            }
        }
        catch (ExperimentException | InterruptedException e)
        {
            out.println("<p>" + e.getMessage() + "</p>");
        }
        catch (IOException e)
        {
            out.println("<p> Error in generating graph:</p>");
            out.println("<pre>" + PageFlowUtil.filter(e.getMessage()) + "</pre>");
            _log.error("Error generating graph", e);
        }
    }

    public void setFocusType(String focusType)
    {
        _focusType = focusType;
    }

    public String getFocusType()
    {
        return _focusType;
    }
}
