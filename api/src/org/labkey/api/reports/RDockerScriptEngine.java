package org.labkey.api.reports;

import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;
import org.labkey.api.miniprofiler.CustomTiming;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.pipeline.file.PathMapperImpl;
import org.labkey.api.query.ValidationException;
import org.labkey.api.rstudio.RStudioService;
import org.labkey.api.util.FileUtil;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileFilter;
import java.util.Collections;
import java.util.Map;

/**
 * Created by matthew on 7/14/17.
 */
public class RDockerScriptEngine extends RScriptEngine
{
    private static final Logger LOG = Logger.getLogger(RDockerScriptEngine.class);
    private static RStudioService _rs;
    private static String _remoteWorkingDir;

    public RDockerScriptEngine(ExternalScriptEngineDefinition def, RStudioService rs)
    {
        super(def);
        _rs = rs;
        _remoteWorkingDir = _rs.getMount() + "/" + "R_Sandbox";

        def.setPathMap(new PathMapperImpl(){

            void setMapping()
            {
                String wd = getWorkingDir(getContext()).getAbsolutePath().replace("\\","/").replace("/./","/");
                super.setPathMap(Collections.singletonMap(
                        _remoteWorkingDir,
                        new File(wd).toURI().toString()));
            }

            @Override
            public Map<String, String> getPathMap()
            {
                setMapping();
                return super.getPathMap();
            }

            @Override
            public String remoteToLocal(String remoteURI)
            {
                setMapping();
                return super.remoteToLocal(remoteURI);
            }

            @Override
            public String localToRemote(String localURI)
            {
                setMapping();
                return super.localToRemote(localURI);
            }

            @Override
            public ValidationException getValidationErrors()
            {
                setMapping();
                return super.getValidationErrors();
            }
        });
    }

    @Override
    protected Object eval(File scriptFile, ScriptContext context) throws ScriptException
    {
        StringBuffer output = new StringBuffer();
        try (CustomTiming t = MiniProfiler.custom("docker", "execute r in docker container"))
        {
            _rs.executeR(scriptFile, getRWorkingDir(context), _remoteWorkingDir, InputFiles());
            appendConsoleOutput(context, output);
            return output.toString();
        }
        catch (Exception e)
        {
            throw new ScriptException("An error occurred when running the script '" + scriptFile.getName() + "', msg " + e.getMessage() + ").\n" + e.toString());
        }
    }

    /**
     * Filter for the input script files in the report temp folder to copy to the Docker container which will run R
     * @return
     */
    private static FileFilter InputFiles()
    {
        return pathname ->
                pathname.isFile() &&
                        RScriptEngineFactory.isRScriptEngine(new String[] {FilenameUtils.getExtension(pathname.getName())});
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
        return RserveScriptEngine.makeLocalToRemotePath(_def, localURI);
    }
}
