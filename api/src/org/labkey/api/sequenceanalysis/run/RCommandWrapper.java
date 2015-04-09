package org.labkey.api.sequenceanalysis.run;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.reports.RScriptEngineFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 3/24/2015.
 */
public class RCommandWrapper extends AbstractCommandWrapper
{
    public RCommandWrapper(Logger logger)
    {
        super(logger);
    }

    public void executeScript(List<String> params) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getRPath());
        args.addAll(params);

        execute(args);
    }

    private String getRPath()
    {
        String exePath = "Rscript";

        //NOTE: this was added to better support team city agents, where R is not in the PATH, but RHOME is defined
        String packagePath = inferRPath(getLogger());
        if (StringUtils.trimToNull(packagePath) != null)
        {
            exePath = (new File(packagePath, exePath)).getPath();
        }

        return exePath;
    }

    private String inferRPath(Logger log)
    {
        String path;

        //preferentially use R config setup in scripting props.  only works if running locally.
        if (PipelineJobService.get().getLocationType() == PipelineJobService.LocationType.WebServer)
        {
            for (ExternalScriptEngineDefinition def : LabkeyScriptEngineManager.getEngineDefinitions())
            {
                if (RScriptEngineFactory.isRScriptEngine(def.getExtensions()))
                {
                    path = new File(def.getExePath()).getParent();
                    log.info("Using RSciptEngine path: " + path);
                    return path;
                }
            }
        }

        //then pipeline config
        String packagePath = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("R");
        if (StringUtils.trimToNull(packagePath) != null)
        {
            log.info("Using path from pipeline config: " + packagePath);
            return packagePath;
        }

        //then RHOME
        Map<String, String> env = System.getenv();
        if (env.containsKey("RHOME"))
        {
            log.info("Using path from RHOME: " + env.get("RHOME"));
            return env.get("RHOME");
        }

        //else assume it's in the PATH
        log.info("Unable to infer R path, using null");

        return null;
    }
}
