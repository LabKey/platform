package org.labkey.lab;

import org.labkey.api.data.Container;
import org.labkey.api.module.DefaultFolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.MultiPortalFolderType;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.view.template.PageConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * Created by IntelliJ IDEA.
 * User: markigra
 * Date: 11/7/11
 * Time: 3:17 PM
 */
public class LabFolderType extends MultiPortalFolderType
{
    private static final List<FolderTab> PAGES = Arrays.asList(
            (FolderTab) new LabFolderTabs.OverviewPage("Overview"),
            new LabFolderTabs.ExperimentsPage("Experiments"),
            new LabFolderTabs.AssaysPage("Assays"),
            new LabFolderTabs.MaterialsPage("Materials")
    );


    public LabFolderType(Module module)
    {
        super("Lab", "A folder type to to store experiments, assays, and materials",
                                Collections.<Portal.WebPart>emptyList(),
                null,
                getDefaultModuleSet(module, getModule("Experiment"), getModule("Study"), getModule("Pipeline")),
                module);
        this.setForceAssayUploadIntoWorkbooks(true);
    }

    @Override
    public List<FolderTab> getDefaultTabs()
    {
        return PAGES;
    }


    @Override
    protected String getFolderTitle(ViewContext context)
    {
        return context.getContainer().getName();
    }
}
