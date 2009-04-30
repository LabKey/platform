/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.workbook;

import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.data.Container;
import org.labkey.api.module.DefaultFolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;

/*
* User: markigra
* Date: Jan 11, 2009
* Time: 11:58:47 AM
*/
public class WorkbookFolderType extends DefaultFolderType
{
    public WorkbookFolderType()
    {
        super("Workbook", "Workbook containing assays, lists, files and other pages", null, null,
                getDefaultModuleSet(),
                ModuleLoader.getInstance().getModule("Workbook"));
    }

    @Override
    public void configureContainer(Container c)
    {
        WikiService wikiSvc = ServiceRegistry.get().getService(WikiService.class);
        String wikiText = wikiSvc.getHtml(c, WorkbookModule.EXPERIMENT_DESCRIPTION_WIKI_NAME, false);
        if (null == wikiText)
        {
            wikiSvc.insertWiki(HttpView.currentContext().getUser(), c, WorkbookModule.EXPERIMENT_DESCRIPTION_WIKI_NAME, 
                    "Edit this to change the experiment description", WikiRendererType.HTML, WorkbookModule.EXPERIMENT_DESCRIPTION_WIKI_NAME);

        }
        DiscussionService.Settings settings = new DiscussionService.Settings();
        settings.setBoardName("Notes");
        settings.setConversationName("Note");
        settings.setTitleEditable(true);
        settings.setFormatPicker(false);
        settings.setMemberList(false);
        settings.setExpires(false);
        settings.setAssignedTo(false);
        settings.setExpires(false);
        DiscussionService.get().setSettings(c, settings);

        //Use saveParts instead??
        WebPartFactory wikiFactory = Portal.getPortalPart("Wiki");
        try
        {
            Portal.addPart(c, wikiFactory, HttpView.BODY, Collections.singletonMap("name", WorkbookModule.EXPERIMENT_DESCRIPTION_WIKI_NAME));
            WebPartFactory notesFactory = Portal.getPortalPart("Messages");
            Portal.addPart(c, notesFactory, null);
            WebPartFactory filesFactory = Portal.getPortalPart("Files");
            Portal.addPart(c, filesFactory, "right");
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static Set<Module> _activeModulesForOwnedFolder = null;
    private synchronized static Set<Module> getActiveModulesForOwnedFolder(Module module)
    {
        if (null != _activeModulesForOwnedFolder)
            return _activeModulesForOwnedFolder;

        Set<Module> active = getDefaultModuleSet();
        active.add(module);
        Set<String> dependencies = module.getModuleDependenciesAsSet();
        for (String moduleName : dependencies)
            active.add(ModuleLoader.getInstance().getModule(moduleName));
       _activeModulesForOwnedFolder = active;
        return active;
    }

}
