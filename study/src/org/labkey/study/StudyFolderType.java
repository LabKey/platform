package org.labkey.study;

import org.labkey.api.module.DefaultFolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.ViewContext;
import org.labkey.study.model.Study;

import java.util.Arrays;
import java.util.Set;

import org.labkey.study.model.StudyManager;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Aug 8, 2006
 * Time: 4:21:56 PM
 */
public class StudyFolderType extends DefaultFolderType
{
    public static final String NAME = "Study";

    StudyFolderType(StudyModule module)
    {
        super(NAME,
                "Manage human and animal studies involving long-term observations at distributed sites. " +
                        "Use specimen repository for samples. Design and manage specialized assays. " +
                        "Analyze, visualize and share results.",
                null,
                Arrays.asList(StudyModule.manageStudyPartFactory.createWebPart(), StudyModule.reportsPartFactory.createWebPart(), StudyModule.samplesPartFactory.createWebPart(),
                        StudyModule.datasetsPartFactory.createWebPart()),
                getActiveModulesForOwnedFolder(module), module);

    }

    @Override
    public String getStartPageLabel(ViewContext ctx)
    {
        Study study = StudyManager.getInstance().getStudy(ctx.getContainer());
        return study == null ? "New Study" : study.getLabel();
    }

    private static Set<Module> _activeModulesForOwnedFolder = null;
    private synchronized static Set<Module> getActiveModulesForOwnedFolder(StudyModule module)
    {
        if (null != _activeModulesForOwnedFolder)
            return _activeModulesForOwnedFolder;

        Set<Module> active = getDefaultModuleSet();
        active.add(module);
        Set<String> dependencies = module.getModuleDependencies();
        for (String moduleName : dependencies)
            active.add(ModuleLoader.getInstance().getModule(moduleName));
       _activeModulesForOwnedFolder = active;
        return active;
    }
}
