/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.reports;

import org.labkey.api.util.ExceptionUtil;

import javax.script.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
* User: Karl Lum
* Date: Dec 2, 2008
* Time: 4:33:23 PM
*/
public class ExternalScriptEngine extends AbstractScriptEngine
{
    /**
     * external script engines rely on the file system to exchange data between the server and script process,
     * the working directory is the folder where files used to run a single instance of a script will appear.
     */
    public static final String WORKING_DIRECTORY = "external.script.engine.workingDirectory";

    private static final String DEFAULT_WORKING_DIRECTORY = "ExternalScript";
    private static final Pattern scriptCmdPattern = Pattern.compile("'([^']+)'|\\\"([^\\\"]+)\\\"|(^[^\\s]+)|(\\s[^\\s^'^\\\"]+)");

    private File _workingDirectory;

    private ExternalScriptEngineDefinition _def;

    public ExternalScriptEngine(ExternalScriptEngineDefinition def)
    {
        _def = def;
    }

    public ScriptEngineFactory getFactory()
    {
        return new ExternalScriptEngineFactory(_def);
    }

    public Object eval(String script, ScriptContext context) throws ScriptException
    {
        Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
        List<String> extensions = getFactory().getExtensions();

        if (!extensions.isEmpty())
        {
            // write out the script file to disk using the first extension as the default
            File scriptFile = new File(getWorkingDir(context), "script." + extensions.get(0));

            try
            {
                PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(scriptFile)));
                pw.write(script);
                pw.close();
            }
            catch(IOException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }

            String[] params = formatCommand(scriptFile);
            ProcessBuilder pb = new ProcessBuilder(params);
            pb = pb.directory(getWorkingDir(context));

            StringBuffer output = new StringBuffer();

            if (runProcess(context, pb, output) != 0)
            {
                throw new ScriptException(output.toString());
            }
            else
                return output.toString();
        }
        else
            throw new ScriptException("There are no file name extensions registered for this ScriptEngine : " + getFactory().getLanguageName());
    }

    public Object eval(Reader reader, ScriptContext context) throws ScriptException
    {
        BufferedReader br = new BufferedReader(reader);

        try {
            String l;
            StringBuffer sb = new StringBuffer();
            while ((l = br.readLine()) != null)
            {
                sb.append(l);
                sb.append('\n');
            }
            return eval(sb.toString(), context);
        }
        catch (IOException ioe)
        {
            ExceptionUtil.logExceptionToMothership(null, ioe);
        }
        finally
        {
            try {br.close();} catch(IOException ioe) {}
        }
        return null;
    }

    public Bindings createBindings()
    {
        return new SimpleBindings();
    }

    protected File getWorkingDir(ScriptContext context)
    {
        if (_workingDirectory == null)
        {
            Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
            if (bindings.containsKey(WORKING_DIRECTORY))
            {
                String dir = (String)bindings.get(WORKING_DIRECTORY);
                _workingDirectory = new File(dir);
            }
            else
            {
                File tempDir = new File(System.getProperty("java.io.tmpdir"));
                _workingDirectory = new File(tempDir, DEFAULT_WORKING_DIRECTORY);
            }

            if (!_workingDirectory.exists())
                _workingDirectory.mkdirs();
        }
        return _workingDirectory;
    }

    /**
     * Returns the external script command to run given the specified script file. The
     * command must be in the form that is acceptable to ProcessBuilder.
     *
     * @param scriptFile
     * @return an array of command parameters
     */
    protected String[] formatCommand(File scriptFile)
    {
        List<String> params = new ArrayList<String>();
        String exe = _def.getExePath();
        String cmd = _def.getExeCommand();

        params.add(exe);

        String scriptFilePath = scriptFile.getAbsolutePath();

        // see if the command contains parameter substitutions
        int idx = cmd.indexOf('%');
        if (idx != -1)
            cmd = String.format(cmd, scriptFilePath);

        Matcher m = scriptCmdPattern.matcher(cmd);
        while (m.find())
        {
            String value = m.group().trim();
            if (value.startsWith("'"))
                value = m.group(1).trim();
            else if (value.startsWith("\""))
                value = m.group(2).trim();

            params.add(value);
        }

        // append the script file, if it wasn't part of the command
        if (idx == -1)
            params.add(scriptFilePath);

        return params.toArray(new String[params.size()]);
    }

    /**
     * Execute the external script engine in separate process
     * @param pb
     * @param output
     * @return 0 if the process completed successfully.
     */
    protected int runProcess(ScriptContext context, ProcessBuilder pb, StringBuffer output)
    {
        Process proc;
        try
        {
            pb.redirectErrorStream(true);
            proc = pb.start();
        }
        catch (SecurityException se)
        {
            throw new RuntimeException(se);
        }
        catch (IOException eio)
        {
            Map<String, String> env = pb.environment();
            throw new RuntimeException("Failed starting process '" + pb.command() + "'. " +
                    "Must be on server path. (PATH=" + env.get("PATH") + ")", eio);
        }

        BufferedReader procReader = null;
        FileWriter writer = null;
        try
        {
            procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = procReader.readLine()) != null)
            {
                output.append(line);
                output.append('\n');
            }
        }
        catch (IOException eio)
        {
            throw new RuntimeException("Failed writing output for process in '" + pb.directory().getPath() + "'.", eio);
        }
        finally
        {
            if (procReader != null)
                try {procReader.close();} catch(IOException ioe) {}

            if (writer != null)
                try {writer.close();} catch(IOException ioe) {}
        }

        try
        {
            int code = proc.waitFor();
            appendConsoleOutput(context, output);

            return code;
        }
        catch (InterruptedException ei)
        {
            throw new RuntimeException("Interrupted process for '" + pb.command() + " in " + pb.directory() + "'.", ei);
        }
    }

    protected void appendConsoleOutput(ScriptContext context, StringBuffer sb)
    {
        // if additional console output is written to a file, append it to the output string.
        BufferedReader br = null;
        try {
            String fileName = _def.getOutputFileName();
            if (fileName != null)
            {
                File file = new File(getWorkingDir(context), fileName);
                if (file.exists())
                {
                    br = new BufferedReader(new FileReader(file));
                    String l;
                    while ((l = br.readLine()) != null)
                    {
                        sb.append(l);
                        sb.append('\n');
                    }
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed reading console output from external process", e);
        }
        finally
        {
            if (br != null)
                try {br.close();} catch(IOException ioe) {}
        }
    }
}