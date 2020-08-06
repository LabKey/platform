/*
 * Copyright (c) 2017-2018 LabKey Corporation
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

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.docker.DockerService;
import org.labkey.api.miniprofiler.CustomTiming;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.pipeline.file.PathMapperImpl;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.services.ServiceRegistry;
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
    private static final Logger LOG = LogManager.getLogger(RDockerScriptEngine.class);
    private static DockerService _ds;

    private final String _remoteWorkingDir;
    private final DockerService.DockerImage _dockerImage;

    /**
     * If an RStudioService is instance is passed to this constructor, it provides an instance which can both
     * prepare and run script files.
     */
    public RDockerScriptEngine(@NotNull ExternalScriptEngineDefinition def, @Nullable DockerService ds, DockerService.DockerImage dockerImage) //TODO update @NotNull after merge
    {
        super(def);
        _ds = ds;
        _dockerImage = dockerImage;
        _remoteWorkingDir = dockerImage == null ? "" : dockerImage.getMount() + "/R_Sandbox";

        def.setPathMapper(new PathMapperImpl(){

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
        if (null != _ds)
        {
            try (CustomTiming t = MiniProfiler.custom("docker", "execute r in docker container"))
            {
                _ds.executeR(_dockerImage, scriptFile, getRWorkingDir(context), _remoteWorkingDir, InputFiles());
                appendConsoleOutput(context, output);
            }
            catch (Exception e)
            {
                throw new ScriptException("An error occurred when running the script '" + scriptFile.getName() + "', msg " + e.getMessage() + ").\n" + e.toString());
            }
        }
        else
        {
            throw new ScriptException("Script evaluation attempted with no DockerService instance available.");
        }

        String scriptOut = output.toString();
        if (scriptOut.contains("Execution halted")) // unable to capture R command error when run in docker container
            throw new ScriptException("An error occurred when running the script '" + scriptFile.getName() + "'.\n" + scriptOut);
        return scriptOut;
    }

    /**
     * Filter for the input script files in the report temp folder to copy to the Docker container which will run R
     * @return
     */
    private static FileFilter InputFiles()
    {
        return pathname ->
                pathname.isFile() &&
                        (RScriptEngineFactory.isRScriptEngine(new String[] {FilenameUtils.getExtension(pathname.getName())})
                                || RReport.DATA_INPUT.equals(pathname.getName()));
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

    @NotNull
    private static ExternalScriptEngineDefinition mockEngineDefinition()
    {
        LabkeyScriptEngineManager svc = ServiceRegistry.get().getService(LabkeyScriptEngineManager.class);

        ExternalScriptEngineDefinition engineDef = svc.createEngineDefinition();
        engineDef.setExtensions(new String[]{"R", "r"});
        engineDef.setPandocEnabled(true); // TODO: ?
        return engineDef;
    }
}
