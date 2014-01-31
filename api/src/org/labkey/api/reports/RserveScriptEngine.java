/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.apache.axis.utils.StringUtils;
import org.labkey.api.reports.report.RReport;

import javax.script.*;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

public class RserveScriptEngine extends RScriptEngine
{
    private String localHostIP = "127.0.0.1";
    private String localHostName = "localhost";
    private static final int INITIAL_R_SESSONS = 5;
    //
    // "share" is a bad name here - what we really mean is the name of the mounted
    // volume and path to the share on the labkey server reports directory
    //
    public static final String TEMP_ROOT = "rserve.script.engine.tempRoot";
    public static final String R_SESSION = "rserve.script.engine.session";
    public static final String PIPELINE_ROOT = "rserve.script.engine.pipelineRoot";

    public RserveScriptEngine(ExternalScriptEngineDefinition def)
    {
        super(def);
    }

    @Override
    protected String getInputFilename(File inputScript)
    {
        String inputPath = RReport.getLocalPath(inputScript);
        String remotePath = getRemoteReportPath(inputPath);
        if (inputPath.equals(remotePath))
            remotePath = getRemotePipelinePath(inputPath);

        return remotePath;
    }

    @Override
    protected String getRWorkingDir(ScriptContext context)
    {
        String workingDir = RReport.getLocalPath(getWorkingDir(context));
        String remoteDir = getRemoteReportPath(workingDir);
        if (workingDir.equals(remoteDir))
            remoteDir = getRemotePipelinePath(workingDir);

        return remoteDir;
    }

    public Object eval(String script, ScriptContext context) throws ScriptException
    {
        List<String> extensions = getFactory().getExtensions();
        String rcmd = "";
        RConnection rconn = null;
        RConnectionHolder rh = null;

        String connectionId = null;

        if (extensions.isEmpty())
        {
            throw new ScriptException("There are no file name extensions registered for this ScriptEngine : " + getFactory().getLanguageName());
        }

        //
        // see if session sharing was requested
        //
        if (context.getBindings(ScriptContext.ENGINE_SCOPE).containsKey(RserveScriptEngine.R_SESSION))
        {
            rh = (RConnectionHolder) context.getBindings(ScriptContext.ENGINE_SCOPE).get(RserveScriptEngine.R_SESSION);
            //
            // if we have a session in the bindings then the connection holder better be there and marked as in use
            //
            assert (rh!=null) && (rh.isInUse());
        }

        File scriptFile = prepareScriptFile(script, context, extensions);

        try
        {
            rconn = getConnection(rh);

            //
            // use the source command to load in the script.  this is good because we aren't parsing the script at all
            // but for short scripts on a local machine it is slower than evaluating the script
            // line by line
            //
            boolean output = false;
            StringBuilder sb = new StringBuilder();
            //
            // wrap the command to capture the output
            //
            sb.append("try(capture.output(source(\"");
            sb.append(getInputFilename(scriptFile));
            sb.append("\")), silent=TRUE)");

            REXP rexp = rconn.eval(sb.toString());
            if (rexp.inherits("try-error"))
                throw new RuntimeException(getRserveOutput(rexp));

            return getRserveOutput(rexp);
        }
        catch (RserveException re)
        {
            throw new RuntimeException(getRserveError(rconn, re));
        }
        finally
        {
            closeConnection(rconn, rh);
        }
    }

    public String getRserveError(RConnection rconn, RserveException re)
    {
        String rserveErr = re.getMessage();
        //
        // See if we can get the actual error from R.  If this isn't an eval
        // error then at least we'll have the original exception message.  If it
        // is an eval err then we can augment with richer information by getting the
        // actual error message back
        //
        try
        {
            REXP rexp = rconn.parseAndEval("geterrmessage()");
            rserveErr = rserveErr + "\n" + getRserveOutput(rexp);
        }
        catch(Exception e)
        {
            //
            // we can't do anything about an error and we don't know what it was so just return
            // the original message
            //
        }
        return rserveErr;
    }

    //
    // Generate the output of Rserve.  Currently we only spit out what is explicitly "printed"
    // by the R script itself.  Since we run the script as is using the Source command and don't
    // evaluate line by line, it is up to the author to determine what to print out
    //
    public String getRserveOutput(REXP rexp)
    {
        StringBuilder sb = new StringBuilder();
        String rserveOut = null;

        if (null != rexp)
        {
            try
            {
                String[] lines = rexp.asStrings();
                for (int i = 0; i < lines.length; i++)
                {
                    if (i > 0)
                    {
                        sb.append("\n");
                    }
                    sb.append(lines[i]);
                }
            }
            catch (REXPMismatchException re)
            {
                //
                // it's fine to not have any output
                //
            }
        }
        rserveOut = sb.toString();
        //
        // Don't bother returning an empty string here as we'll just
        // write out an empty file
        //
        return StringUtils.isEmpty(rserveOut) ? null : rserveOut;
     }

    public String getRemoteReportPath(String localPath)
    {
        File f = (File) getBindings(ScriptContext.ENGINE_SCOPE).get(RserveScriptEngine.TEMP_ROOT);
        if (!StringUtils.isEmpty(_def.getReportShare()) && f != null)
        {
            String tempRoot = RReport.getLocalPath(f);
            return localPath.replaceAll(tempRoot, _def.getReportShare());
        }

        return localPath;
    }

    public String getRemotePipelinePath(String localPath)
    {
        File f = (File) getBindings(ScriptContext.ENGINE_SCOPE).get(RserveScriptEngine.PIPELINE_ROOT);
        if (!StringUtils.isEmpty(_def.getPipelineShare()) && f != null)
        {
            String pipelineRoot = RReport.getLocalPath(f);
            return localPath.replaceAll(pipelineRoot, _def.getPipelineShare());
        }

        return localPath;
    }


    /*
    private void EvalLines()
    {
            //
            // eval source line by line
            //

            RserveScriptUtil su = new RserveScriptUtil(script);
            StringBuilder sb = new StringBuilder();

            rcmd = su.getNextCommand();
            while (null != rcmd)
            {
                rconn.eval(rcmd);

                //
                // this will print out expression evaluations
                // which is not all that useful unless
                // we wrap with capture.output (which does some
                // funky things to the environment unless you use force(environ)
                //
                //REXP rexp = rconn.eval(rcmd);
                //
                // todo: is this logic correct? why is the first output always thrown away?
                //
                //if (output && rexp != null)
                //{
                //    outputEval(rexp, sb);
                //    sb.append("\n");
                //}
                //output = true;
                //

                rcmd = su.getNextCommand();
            }


    //
    // if (isDebug())
    //  view.addView(new HtmlView(err))
    //

    }
    */

    private void EnsureRserveStarted()
    {
        /*
        if (isRserveRunning())
        {
            return;
        }
        */


        //
        //  todo:  do we want to try to start a remote instance?
        //  todo:  on windows, we do need to do some session pooling here
        //  todo:  do we want to do this on an existing labkey server?
        //  todo:  note that starting rserve requires knowing the rserve path which we may want
        //  todo:  request.  for now, I don't have a 64 bit binary
        //

        /*
        if (localHostIP.equalsIgnoreCase(_def.getMachineName()) ||
            localHostName.equalsIgnoreCase(_def.getMachineName()))
        {
        }
        */
    }

    private RConnection getConnection(RConnectionHolder rh)
    {
        //
        // todo: on windows this will create a connection against the same environment
        // todo: on unix this will create a new separate environment
        //
        RConnection rconn = null;

        //
        // by passing in an RConnectionHolder, the user has indicated that
        // we should attempt to reuse an existing connection.  If the connection
        // is null, then create one
        //
        if (rh != null)
        {
            rconn = rh.getConnection();

            if (rconn != null)
            {
                if (!rconn.isConnected())
                {
                    //
                    // connection got closed from underneath us
                    //
                    rconn = null;
                }
            }
        }

        if (null == rconn)
        {
            try
            {
                //
                // get a new connection (will HANG on a windows server)
                //
                rconn = new RConnection(_def.getMachine(), _def.getPort());

                if (rconn != null &&
                    rconn.needLogin())
                {
                    rconn.login(_def.getUser(), _def.getPassword());
                }
            }
            catch(RserveException rse)
            {
                String message;

                if (rconn == null)
                {
                    message = "Could not connect to: " + _def.getMachine() + ":" + String.valueOf(_def.getPort());
                }
                else
                {
                    message = "Could not login to Rserve with user: " + _def.getUser();
                }

                throw new RuntimeException(message);
            }
        }

        return rconn;
    }

    private void closeConnection(RConnection conn, RConnectionHolder rh)
    {
        //
        // if an RConnectionHolder exists then we want to reuse
        // this connection.  Otherwise, be sure to close it
        //
        if (conn != null)
        {
            if (rh != null)
            {
                if (conn.isConnected())
                {
                    rh.setConnection(conn);
                }
            }
            else
            {
                conn.close();
            }
        }
    }

    /*
    not used yet

    private void outputEval(REXP eval, StringBuilder sb)
    {
        try {
            if (eval.isString())
            {
                for (String s : eval.asStrings())
                {
                    sb.append(s);
                    sb.append(' ');
                }
            }
            else if (eval.isInteger())
            {
                for (int i : eval.asIntegers())
                {
                    sb.append(i);
                    sb.append(' ');
                }
            }
            else if (REXPDouble.class.isAssignableFrom(eval.getClass()))
            {
                for (double d : eval.asDoubles())
                {
                    sb.append(d);
                    sb.append(' ');
                }
            }
            else if (eval.isList())
            {
                for (Object o : eval.asList().values())
                {
                    if (REXP.class.isAssignableFrom(o.getClass()))
                        outputEval((REXP)o, sb);
                    sb.append('\n');
                }
            }
            else
                sb.append(eval.toDebugString());
        }
        catch (REXPMismatchException me)
        {
            sb.append("conversion error for object: " + eval.toDebugString());
            sb.append("\n");
        }
    }
    */

    //
    // todo: just get the first one you can from the map (i.e., don't burn a connection just to check if rserve is running or not as this
    // todo: could be expensinve on an windows box
    //
    private boolean isRserveRunning()
    {
        /*
        boolean isRunning = false;
        RConnection c = getConnection();

        if (null != c)
        {
            isRunning = c.isConnected();
            //
            // todo: when caching connections or managing sessions you will not want
            // todo: close the opened cached connection
            //
            c.close();
        }

        return isRunning;                                                                     ;
        */

        //
        // undone: may not need to do this.
        //
        return true;
    }
 }
