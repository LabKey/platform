package org.labkey.redcap;

import com.drew.lang.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyReloadSource;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.DefaultContainerUser;

/**
 * Created by klum on 2/19/2015.
 */
public class RedcapReloadSource implements StudyReloadSource
{
    public static final String NAME = "REDCap";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public boolean isEnabled(Container container)
    {
        Module module = ModuleLoader.getInstance().getModule(RedcapModule.NAME);
        return container.getActiveModules().contains(module);
    }

    @Override
    public ActionURL getManageAction(Container c, User user)
    {
        if (c.hasPermission(user, AdminPermission.class))
            return new ActionURL(RedcapController.ConfigureAction.class, c);
        return null;
    }

    @Override
    public void generateReloadSource(@Nullable PipelineJob job, Study study) throws PipelineJobException
    {
        RedcapExport export = new RedcapExport(job);
        export.exportProject();
    }
}
