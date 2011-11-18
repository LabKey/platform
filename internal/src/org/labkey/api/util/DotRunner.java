/*
 * Copyright (c) 2011 LabKey Corporation
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
    private final List<String> _parameters = new LinkedList<String>();

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
            throw new IOException("Graph generation failed with error code " + result._returnCode + " - " + result._output);
        }
    }

    public static boolean testDotPath(File baseDirectory)
    {
        try
        {
            File testVersion = new File(baseDirectory, "dottest.txt");
            if (testVersion.exists())
                return true;

            ProcessBuilder pb = new ProcessBuilder(dotExePath, "-V", "-o" + testVersion.getName());
            pb = pb.directory(baseDirectory);
            Process p = pb.start();
            int err = p.waitFor();

            if (err == 0)
                return true;
        }
        catch (IOException e)
        {
            return false;
        }
        catch (InterruptedException e)
        {
            return false;
        }

        return false;
    }

    public static String getConfigurationErrorHtml()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Unable to display graph view: cannot run ");
        sb.append(dotExePath);
        sb.append(" due to system configuration error. \n<BR>");
        sb.append("For help on fixing your system configuration, please consult the Graphviz section of the <a href=\"");
        sb.append((new HelpTopic("thirdPartyCode")).getHelpTopicLink());
        sb.append("\" target=\"_new\">LabKey Server documentation on third party components</a>.<br>");

        return sb.toString();
    }

    private static ProcessResult executeProcess(ProcessBuilder pb, String stdIn) throws IOException, InterruptedException
    {
        StringBuilder sb = new StringBuilder();
        pb.redirectErrorStream(true);
        Process p = pb.start();
        PrintWriter writer = null;
        BufferedReader procReader = null;

        try
        {
            writer = new PrintWriter(p.getOutputStream());
            writer.write(stdIn);
            writer.close();
            writer = null;
            procReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;

            while ((line = procReader.readLine()) != null)
            {
                sb.append(line);
                sb.append("\n");
            }
        }
        finally
        {
            if (procReader != null)
            {
                try { procReader.close(); } catch (IOException eio) {}
            }
            if (writer != null)
            {
                writer.close();
            }
        }

        int returnCode = p.waitFor();
        return new ProcessResult(returnCode, sb.toString());
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
