/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
package org.labkey.study.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceLoadException;
import org.labkey.api.module.ModuleResourceLoader;
import org.labkey.api.resource.Resource;
import org.labkey.study.SpecimenManager;
import org.labkey.study.model.StudyManager;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * User: brittp
 * Date: Oct 4, 2010 2:37:55 PM
 */
public class StudyViewLoader implements ModuleResourceLoader
{
    /*package*/ static final String VIEWS_DIR_NAME = "views";

    @NotNull
    @Override
    public Set<String> getModuleDependencies(Module module, File explodedModuleDir)
    {
        // We used to return any module with a "views" directory... but that made 41 modules study dependencies
        return Collections.emptySet();
    }

    @Override
    public void registerResources(Module module) throws IOException, ModuleResourceLoadException
    {
        Resource viewsDir = module.getModuleResource(VIEWS_DIR_NAME);
        if (viewsDir != null && viewsDir.exists() && viewsDir.isCollection())
        {
            Resource participantView = viewsDir.find("participant.html");
            if (participantView != null && participantView.exists() && participantView.isFile())
                StudyManager.getInstance().registerParticipantView(module, participantView);

            Resource extendedSpecimenRequestView = viewsDir.find("extendedrequest.html");
            if (extendedSpecimenRequestView != null && extendedSpecimenRequestView.exists() && extendedSpecimenRequestView.isFile())
                SpecimenManager.getInstance().registerExtendedSpecimenRequestView(module, extendedSpecimenRequestView);
        }
    }
}
