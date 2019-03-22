/*
 * Copyright (c) 2012-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ViewContext;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;

public class RserveScriptEngine extends RScriptEngine
{
    private static final Logger LOG = Logger.getLogger(RserveScriptEngine.class);

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
    public static final String PROJECT_PIPELINE_ROOT = "rserve.script.engine.projectPipelineRoot";

    public RserveScriptEngine(ExternalScriptEngineDefinition def)
    {
        super(def);
    }

    @Override
    protected String getInputFilename(File inputScript)
    {
        return getRemotePath(inputScript);
    }

    @Override
    protected String getRWorkingDir(ScriptContext context)
    {
        File workingDir = getWorkingDir(context);
        return getRemotePath(workingDir);
    }

    //
    // note this is only run in the context of Rserve (callers need to ensure this).  The incoming script must already
    // have been parsed (i.e. we are evaluating a function that has already been loaded into the existing R sesssion on Rserve)
    //
    public static Object eval(ViewContext context, String function, String reportSessionId, Map<String, Object> inputParameters) throws ScriptException
    {
        //
        // We never want to create a connection under the covers if one doesn't exist in this case (since we are
        // about to execute a function that only exists in the session).  The only thing we can do is exit here if
        // the connection has been closed out from under us.
        //
        RConnectionHolder rh = (RConnectionHolder) context.getSession().getAttribute(reportSessionId);
        RConnection rconn = getConnectionFromHolder(rh);
        if (rconn == null)
        {
            throw new ScriptException("The connection bound to this report session is no longer valid!");
        }

        //
        // Verify that the function the user is trying to execute has been declared in our whitelist.  Callable functions are declared
        // in the report metadata file by the script report authors.
        //
        if (!rh.isFunctionCallable(function))
        {
            throw new ScriptException("The function [" + function +"] is not allowed to be called in this session.  Please see your administrator.");
        }

        StringBuilder paramsList = new StringBuilder();
        RReport.appendParamList(paramsList, inputParameters);
        function = function + "(" + paramsList.toString() + ")";

        try
        {
            rh.acquire();
            REXP rexp = rconn.eval(function);
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
            rh.release();
        }
    }

    public Object eval(String script, ScriptContext context) throws ScriptException
    {
        List<String> extensions = getFactory().getExtensions();
        RConnection rconn = null;
        RConnectionHolder rh = null;

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
            LOG.info("Reusing RServe connection in use: " + rh.isInUse());
        }

        File scriptFile = prepareScriptFile(script, context, extensions, true);

        try
        {
            rconn = getConnection(rh, context);

            String remoteInputFile = getInputFilename(scriptFile);
            LOG.debug("Executing remote script '" + remoteInputFile + "'...");
            String cmdFormat = RReport.DEFAULT_RSERVE_CMD;

            // override our rserve invocation command if the exeCommand property is supplied in the engine definition
            if (_def.getExeCommand() != null)
                cmdFormat = _def.getExeCommand();

            String rserveCmd = String.format(cmdFormat, remoteInputFile);
            LOG.debug("Evaluating command:  " + rserveCmd);
            String output = eval(rconn, rserveCmd);
            LOG.debug("Executed remote script '" + scriptFile + "' successfully");
            return output;
        }
        finally
        {
            closeConnection(rconn, rh);
        }
    }

    private String eval(RConnection rconn, String script)
    {
        try
        {
            REXP rexp = rconn.eval(script);
            if (rexp.inherits("try-error"))
            {
                LOG.debug("Failed to execute script; rexp inherits 'try-error'!");
                throw new RuntimeException(getRserveOutput(rexp));
            }

            return getRserveOutput(rexp);
        }
        catch (RserveException re)
        {
            LOG.debug("Failed to execute script; eval threw an RserveException!");
            throw new RuntimeException(getRserveError(rconn, re));
        }
    }

    public static String getRserveError(RConnection rconn, RserveException re)
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
    public static String getRserveOutput(REXP rexp)
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


    @Override
    public String getRemotePath(File localFile)
    {
        // get absolute path to make sure the paths are consistent
        String localPath = FileUtil.getAbsoluteCaseSensitiveFile(localFile).toURI().toString();
        return getRemotePath(localPath);
    }


    @Override
    public String getRemotePath(String localURI)
    {
        return makeLocalToRemotePath(_def, localURI);
    }


    static public String makeLocalToRemotePath(ExternalScriptEngineDefinition def, String localURI)
    {
        PathMapper pathMap = def.getPathMap();
        if (pathMap != null && !pathMap.getPathMap().isEmpty())
        {
            // CONSIDER: Move converting file path to URI into the PathMapper.
            if (!localURI.startsWith("file:"))
                localURI = FileUtil.getAbsoluteCaseSensitiveFile(new File(localURI)).toURI().toString();

            String remoteURI = pathMap.localToRemote(localURI);
            remoteURI = remoteURI.replace('\\', '/');
            try
            {
                // check that the remoteURI is valid
                URI uri = new URI(remoteURI);
                LOG.debug("Mapped path '" + localURI + "' ==> '" + remoteURI + "'");

                if (remoteURI.startsWith("file:"))
                {
                    remoteURI = URLDecoder.decode(remoteURI, "UTF-8");
                    return remoteURI.substring(5);
                }

                return remoteURI;
            }
            catch (URISyntaxException | UnsupportedEncodingException e)
            {
                LOG.warn("Error mapping localURI '" + localURI + "' to remote RServe path: " + e.getMessage());
            }
        }
        else
        {
            LOG.warn("No path mapping configured; using localURI '" + localURI + "' on remote RServe");
        }

        return localURI;
    }


    /*
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
            // TODO: RServe currently only configures site-wide pipeline share.
            // TODO: We check that the current folder pipeline root is either equal to or is under the project's pipeline root.
            // TODO: This could fail if the project pipeline root isn't the same as the RServe script engine settings pipeline share
            File projectRoot = (File) getBindings(ScriptContext.ENGINE_SCOPE).get(RserveScriptEngine.PROJECT_PIPELINE_ROOT);
            if (projectRoot != null)
                f = projectRoot;

            String pipelineRoot = RReport.getLocalPath(f);
            return localPath.replaceAll(pipelineRoot, _def.getPipelineShare());
        }

        return localPath;
    }
    */


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

    private static RConnection getConnectionFromHolder(RConnectionHolder rh)
    {
        if (rh != null)
        {
            RConnection rconn = rh.getConnection();
            if (rconn != null && rconn.isConnected())
                return rconn;
        }

        return null;
    }

    /** Change to the R working directory on the remote RServe. */
    private void initEnv(RConnection rconn, ScriptContext context)
    {
        String workingDir = getRWorkingDir(context);
        if (workingDir != null)
        {
            LOG.debug("Setting RServe working directory to '" + workingDir + "'");
            String script = "setwd(\"" + workingDir + "\")\n";

            eval(rconn, script);
        }
    }

    private RConnection getConnection(RConnectionHolder rh, ScriptContext context)
    {
        RConnection rconn = getConnectionFromHolder(rh);

        //
        // todo: on windows this will create a connection against the same environment
        // todo: on unix this will create a new separate environment
        //
        if (null == rconn)
        {
            try
            {
                //
                // get a new connection (will HANG on a windows server)
                //
                LOG.debug("Creating new RServe connection to " + _def.getMachine() + ":" + _def.getPort());
                rconn = new RConnection(_def.getMachine(), _def.getPort());

                if (rconn.needLogin())
                {
                    LOG.debug("Logging in to RServe as '" + _def.getUser() + "'");
                    rconn.login(_def.getUser(), _def.getPassword());
                }

                initEnv(rconn, context);
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
                LOG.info("Closing RServe connection");
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
