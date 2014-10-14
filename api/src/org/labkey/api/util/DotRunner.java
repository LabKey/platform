/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
package org.labkey.api.util;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.miniprofiler.CustomTiming;
import org.labkey.api.miniprofiler.MiniProfiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;

/**
 * User: adam
 * Date: 11/13/11
 * Time: 10:33 AM
 */

// Handles the basics of testing the dot configuration, creating the dot command line, and invoking dot. This code
// originated in ExperimentRunGraph, but was pulled out to support group diagrams.
public class DotRunner
{
    private static final String dotExePath = "dot";

    private final String _dotInput;
    private final File _directory;
    private final List<String> _parameters = new LinkedList<>();

    public DotRunner(File directory, String dotInput)
    {
        _directory = directory;
        _dotInput = dotInput;
        _parameters.add(dotExePath);
    }

    public void addPngOutput(File pngFile)
    {
        addOutputParameter("png", pngFile);
    }

    public void addCmapOutput(File cmapFile)
    {
        addOutputParameter("cmap", cmapFile);
    }

    public void addSvgOutput(File svgFile)
    {
        addOutputParameter("svg", svgFile);
    }

    private void addOutputParameter(String typeName, File file)
    {
        _parameters.add("-T" + typeName);
        _parameters.add("-o" + file.getName());
    }

    public void execute() throws IOException, InterruptedException
    {
        ProcessBuilder pb = new ProcessBuilder(_parameters);
        pb.directory(_directory);
        ProcessResult result = executeProcess(pb, _dotInput);

        if (result._returnCode != 0)
        {
            throw new IOException("Graph generation failed with error code " + result._returnCode + ":\n" + result._output);
        }
    }

    public static void testDotPath(File baseDirectory) throws ExperimentException
    {
        try
        {
            File testVersion = new File(baseDirectory, "dottest.txt");
            if (testVersion.exists())
                return;

            ProcessBuilder pb = new ProcessBuilder(dotExePath, "-V", "-o" + testVersion.getName());
            pb = pb.directory(baseDirectory);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int err = p.waitFor();

            if (err != 0)
            {
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null)
                {
                    sb.append(line);
                    sb.append("\n");
                }
                sb.append("(Exit code: ");
                sb.append(err);
                sb.append(")");
                throw new ExperimentException(getConfigurationErrorHtml(sb.toString()));
            }
        }
        catch (IOException e)
        {
            throw new ExperimentException(getConfigurationErrorHtml(e));
        }
        catch (InterruptedException e)
        {
            throw new ExperimentException("InterruptedException - web server may be shutting down");
        }
    }

    public static String getConfigurationErrorHtml(IOException e)
    {
        if (e.getMessage() != null)
        {
            return getConfigurationErrorHtml(e.getMessage());
        }
        else
        {
            return getConfigurationErrorHtml(e.toString());
        }
    }

    public static String getConfigurationErrorHtml(String message)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Unable to display graph view: cannot run ");
        sb.append(dotExePath);
        sb.append(" due to an error.\n</p><pre>");
        sb.append(PageFlowUtil.filter(message));
        sb.append("</pre>");
        sb.append("<p>Regardless of the specific error message, please first verify that the program '" + dotExePath + "' is on your system PATH");
        if (!StringUtils.isEmpty(System.getenv("PATH")))
        {
            sb.append(" (");
            sb.append(PageFlowUtil.filter(System.getenv("PATH")));
            sb.append(")");
        }
        sb.append(".</p>");
        sb.append("<p>For help on fixing your system configuration, please consult the Graphviz section of the ");
        sb.append((new HelpTopic("thirdPartyCode")).getSimpleLinkHtml("LabKey Server documentation on third party components"));
        sb.append(".</p>");

        return sb.toString();
    }

    private static ProcessResult executeProcess(ProcessBuilder pb, String stdIn) throws IOException, InterruptedException
    {
        try (CustomTiming t = MiniProfiler.custom("exec", StringUtils.join(pb.command(), " ")))
        {
            StringBuilder sb = new StringBuilder();
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (PrintWriter writer = new PrintWriter(p.getOutputStream()))
            {
                writer.write(stdIn);
            }

            try (BufferedReader procReader = new BufferedReader(new InputStreamReader(p.getInputStream())))
            {
                String line;

                while ((line = procReader.readLine()) != null)
                {
                    sb.append(line);
                    sb.append("\n");
                }
            }

            int returnCode = p.waitFor();
            return new ProcessResult(returnCode, sb.toString());
        }
    }

    private static class ProcessResult
    {
        private final int _returnCode;
        private final String _output;

        public ProcessResult(int returnCode, String output)
        {
            _returnCode = returnCode;
            _output = output;
        }
    }
}
