/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineAction;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ActionURL;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.AdminPermission;

import java.util.List;
import java.util.Collections;
import java.io.File;

/**
 * User: jeckels
 * Date: Dec 16, 2009
 */
public class AssayPipelineProvider extends PipelineProvider
{
    private final FileEntryFilter _filter;
    private AssayProvider _assayProvider;
    private final String _actionDescription;

    public AssayPipelineProvider(Class<? extends Module> moduleClass, FileEntryFilter filter, AssayProvider assayProvider, String actionDescription)
    {
        this(assayProvider.getName(), moduleClass, filter, assayProvider, actionDescription);
    }

    /** Default pipeline provider name is the same as the provider's name, but allow it to be overridden by subclasses */
    protected AssayPipelineProvider(String name, Class<? extends Module> moduleClass, FileEntryFilter filter, AssayProvider assayProvider, String actionDescription)
    {
        super(name, ModuleLoader.getInstance().getModule(moduleClass));
        _filter = filter;
        _assayProvider = assayProvider;
        _actionDescription = actionDescription;

        setShowActionsIfModuleInactive(true);
    }

    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        if (!context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
            return;

        File[] files = directory.listFiles(_filter);
        if (includeAll || (files != null && files.length > 0))
        {
            List<ExpProtocol> assays = AssayService.get().getAssayProtocols(context.getContainer());
            Collections.sort(assays);
            String id = _assayProvider.getClass().getName();
            NavTree navTree = new NavTree(_actionDescription);
            navTree.setId(id);
            for (ExpProtocol protocol : assays)
            {
                if (AssayService.get().getProvider(protocol) == _assayProvider)
                {
                    ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getImportURL(context.getContainer(), protocol, pr.relativePath(new File(directory.getURI())), new File[0]); 
                    NavTree child = new NavTree("Use " + protocol.getName(), url);
                    child.setId(id + ":Use " + protocol.getName());
                    navTree.addChild(child);
                }
            }

            if (context.getContainer().hasPermission(context.getUser(), AdminPermission.class))
            {
                if (navTree.getChildCount() > 0)
                {
                    navTree.addSeparator();
                }

                ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(context.getContainer(), _assayProvider.getName(), context.getActionURL());
                NavTree child = new NavTree("Create New Assay Design", url);
                child.setId(id + ":Create Assay Definition");
                navTree.addChild(child);
            }

            if (navTree.getChildCount() > 0)
            {
                directory.addAction(new PipelineAction(navTree, files, true, false));
            }
        }
    }
}
