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
package org.labkey.api.reports.report.r;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.impl.common.IOUtil;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewContext;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.labkey.api.reports.report.r.RReport.getLocalPath;
import static org.labkey.api.reports.report.r.RReport.toR;


public class RserveScriptEngine extends RScriptEngine
{
    private static final Logger LOG = LogManager.getLogger(RserveScriptEngine.class);

    public enum ModusOperandi
    {
        Local(false, false),         // similar usage to classic "R.exe" reports: local execution with access to entire file system (no file mapping needed)
        FileShare(false, true),      // original rserve implementation: assumes a shared file system (supports remapping file paths)
        Cloud(true, false);          // similar usage to docker R configuration: assumes no sharing files are copied/sent to  remote service

        ModusOperandi(boolean copy, boolean remap)
        {
            this.copy = copy;
            this.cwd = !copy;
            this.remap = remap;
        }

        final boolean copy, cwd, remap;

        // copy files before eval()?
        boolean requiresCopyFiles()
        {
            return copy;
        }

        // change working directory of R connection?
        boolean requiresChangeWorkingDirectory()
        {
            return cwd;
        }

        // remap files outside of working directory?
        // files in working directory are always remapped to ./
        boolean requiresFileRemap()
        {
            return remap;
        }
    }


    protected static final String localHostIP = "127.0.0.1";
    protected static final String localHostName = "localhost";
    //
    // "share" is a bad name here - what we really mean is the name of the mounted
    // volume and path to the share on the labkey server reports directory
    //
    public static final String TEMP_ROOT = "rserve.script.engine.tempRoot";
    public static final String R_SESSION = "rserve.script.engine.session";
    public static final String PIPELINE_ROOT = "rserve.script.engine.pipelineRoot";
    public static final String PROJECT_PIPELINE_ROOT = "rserve.script.engine.projectPipelineRoot";


    protected final ModusOperandi mo;
    protected String rserveWorkingDirectory;


    public RserveScriptEngine(ExternalScriptEngineDefinition def)
    {
        super(def);
        mo = getModusOperandi(def);
    }


    // NB: we are inferring MO, we could make this explicit in the configuration
    static ModusOperandi getModusOperandi(ExternalScriptEngineDefinition def)
    {
        var local = localHostName.equals(def.getMachine()) || localHostIP.equals(def.getMachine());
        var sandboxed = def.isSandboxed();
        var hasPathMapping = null != def.getPathMap() && !def.getPathMap().getURIPathMap().isEmpty();

        if (local && !sandboxed && !hasPathMapping)
            return ModusOperandi.Local;
        if (hasPathMapping)
            return ModusOperandi.FileShare;
        return ModusOperandi.Cloud;
    }


    @Override
    protected String getInputFilename(File inputScript)
    {
        return getRemotePath(inputScript);
    }


    // clean absolute path
    File workingDirectory;

    @Override
    public File getWorkingDir(ScriptContext context)
    {
        if (null == workingDirectory)
            workingDirectory = FileUtil.getAbsoluteCaseSensitiveFile(super.getWorkingDir(getContext()));
        return workingDirectory;
    }


    @Override
    protected String getRWorkingDir(ScriptContext context)
    {
        File workingDir = getWorkingDir(context);
        if (!mo.requiresFileRemap())
            return workingDir.toString();
        else
        {
            // getRemotePath(workingDir) will return ./ so call makeLocalToRemotePath() directly
            URI remote = makeLocalToRemotePath(_def, null, workingDir.toURI());
            return PathMapper.UriToPath(remote);
        }
    }


    public void appendScriptProlog(StringBuilder labkey, ViewContext context)
    {
        if (!mo.requiresCopyFiles())    // requiresCopyFiles implies there is no shared file-system
        {
            File pipelineRoot = RReport.getPipelineRoot(context);
            String localPath = getLocalPath(pipelineRoot);
            labkey.append("labkey.pipeline.root <- \"").append(localPath).append("\"\n");

            // include remote paths so that the client can fixup any file references
            String remotePath = getRemotePath(pipelineRoot);
            labkey.append("labkey.remote.pipeline.root <- \"").append(remotePath).append("\"\n");
        }
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
        function = function + "(" + paramsList + ")";

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


    protected void copyWorkingDirectoryToRemote(RConnection rconn) throws IOException
    {
        if (!mo.requiresCopyFiles())
            return;

        LOG.debug("Copy files in working directory to remote server");
        File wd = getWorkingDir(getContext());
        // recursive???
        File[] files = Objects.requireNonNullElse(wd.listFiles(), new File[0]);
        for (var file : files)
        {
            try (OutputStream os = rconn.createFile(file.getName());
                 FileInputStream fis = new FileInputStream(file))
            {
                IOUtil.copyCompletely(fis,os);
            }
        }
    }


    protected void copyWorkingDirectoryFromRemote(RConnection rconn) throws IOException, RserveException
    {
        if (!mo.requiresCopyFiles())
            return;

        LOG.debug("Copy files from remote server to local working directory");
        File wd = getWorkingDir(getContext());
        try
        {
            String[] names = rconn.eval("list.files("+ toR(defaultIfBlank(rserveWorkingDirectory, ".")) +")").asStrings();
            for (var name : names)
            {
                if ("input_data.tsv".equalsIgnoreCase(name))
                    continue;
                if ("script.R".equalsIgnoreCase(name))
                    continue;
                try (InputStream is = rconn.openFile(name);
                     FileOutputStream fos = new FileOutputStream(new File(wd,name)))
                {
                    IOUtil.copyCompletely(is, fos);
                }
            }
        }
        catch (REXPMismatchException x)
        {
            throw new IOException(x);
        }
    }


    @Override
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

            // no logging here, because this is a no-op by default
            copyWorkingDirectoryToRemote(rconn);

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

            // no logging here, because this is a no-op by default
            copyWorkingDirectoryFromRemote(rconn);

            return output;
        }
        catch (IOException|RserveException x)
        {
            throw new ScriptException(x);
        }
        finally
        {
            closeConnection(rconn, rh);
        }
    }


    protected String eval(RConnection rconn, String script)
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
        String rserveOut = sb.toString();
        //
        // Don't bother returning an empty string here as we'll just
        // write out an empty file
        //
        return StringUtils.isEmpty(rserveOut) ? null : rserveOut;
     }


/*
    @Override
    public String getRemotePath(File localFile)
    {
        // see RScriptEngine.getRemotePath(localFile);
        return relativizeWorkingDirectory(RReport.getLocalPath(localFile));
    }


    @Override
    public String getRemotePath(String localURI)
    {
        // see RScriptEngine.getRemotePath(localFile);
        return relativizeWorkingDirectory(localURI);
    }
     */


    @Override
    public String getRemotePath(File localFile)
    {
        // get absolute path to make sure the paths are consistent
        localFile = FileUtil.getAbsoluteCaseSensitiveFile(localFile);
        if (!mo.requiresFileRemap())
            return localFile.toString();
        URI remote = makeLocalToRemotePath(_def, getWorkingDir(getContext()), localFile.toURI());
        return PathMapper.UriToPath(remote);
    }


    @Override
    public String getRemotePath(String local)
    {
        try
        {
            URI localURI = PathMapper.pathToUri(local);
            URI remote = makeLocalToRemotePath(_def, getWorkingDir(getContext()), localURI);
            return PathMapper.UriToPath(remote);
        }
        catch (URISyntaxException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    // generate path relative to the working directory, return null for paths outside directory
    static String relativizeWorkingDirectory(File workingDirectory, String strPath)
    {
        Path path = new File(strPath).toPath().normalize();
        if (path.equals(path.getRoot()) || path.startsWith(".."))
            return null;
        if (!path.isAbsolute())
            return path.toString();
        Path wd = workingDirectory.toPath();
        Path relative = wd.relativize(path);
        if (relative.startsWith("../") || relative.startsWith("/"))
            return null;
        return "./" + relative;
    }


    // It's confusing to have methods that take URI and return path (or vice versa)
    // Let's stick to methods that take/return the same type (using URI here since pathMap.localToRemote() wants URI)
    static public URI makeLocalToRemotePath(ExternalScriptEngineDefinition def, File workingDirectory, URI localURI)
    {
        // let's first try to relative relative to the working directory
        // We could do this in the other order.  However, since pathMap.localToRemote() doesn't tell us when it did not do anything,
        // this works better for now.
        if (null != workingDirectory)
        {
            String workingFile = relativizeWorkingDirectory(workingDirectory, localURI.getPath());
            if (null != workingFile)
            {
                try
                {
                    return new URI(workingFile);
                }
                catch (URISyntaxException x)
                {
                    throw UnexpectedException.wrap(x);
                }
            }
        }

        PathMapper pathMap = def.getPathMap();
        if (pathMap != null && !pathMap.getURIPathMap().isEmpty())
        {
            URI remoteURI = pathMap.localToRemote(localURI);
            LOG.debug("Mapped path '" + localURI + "' ==> '" + remoteURI + "'");
            return remoteURI;
        }
        else
        {
            LOG.warn("No path mapping configured; using localURI '" + localURI + "' on remote RServe");
        }
        return localURI;
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
    protected void initEnv(RConnection rconn, ScriptContext context) throws IOException
    {
        assert( getContext() == context ); // why pass around context???

        if (!mo.requiresChangeWorkingDirectory())
            return;

        String workingDir = getRWorkingDir(context);
        if (workingDir != null)
        {
            LOG.debug("Setting RServe working directory to '" + workingDir + "'");
            eval(rconn, "setwd(" + toR(workingDir) + ")\n");
        }

        rserveWorkingDirectory = eval(rconn, "getwd()\n");
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
            catch(IOException|RserveException rse)
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
    // todo: could be expensive on an windows box
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
