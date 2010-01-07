package org.labkey.core.workbook;

import org.labkey.api.module.DefaultFolderType;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.Portal;
import org.labkey.api.data.Container;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: labkey
 * Date: Jan 6, 2010
 * Time: 3:01:45 PM
 */
public class WorkbookFolderType extends DefaultFolderType
{
    public static final String NAME = "Workbook";

    public WorkbookFolderType()
    {
        super(NAME,
                "A workbook containing files and experiment runs.",
                null,
                Arrays.asList(
                        Portal.getPortalPart("Workbook Description").createWebPart(),
                        Portal.getPortalPart("Files").createWebPart(),
                        Portal.getPortalPart("Experiment Runs").createWebPart()
                ),
                getDefaultModuleSet(ModuleLoader.getInstance().getCoreModule(), getModule("Experiment")),
                ModuleLoader.getInstance().getCoreModule());
    }
}
