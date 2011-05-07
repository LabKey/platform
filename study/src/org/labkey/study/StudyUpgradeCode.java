/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
package org.labkey.study;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.StartupListener;
import org.labkey.api.util.UnexpectedException;
import org.labkey.study.model.DataSetDefinition;

import javax.servlet.ServletContext;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Nov 25, 2008
 * Time: 4:54:36 PM
 */
public class StudyUpgradeCode implements UpgradeCode
{
    /* called at 10.20->10.21 */
    @SuppressWarnings({"UnusedDeclaration"})
    public void materializeDatasets(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        try
        {
            DataSetDefinition.upgradeAll();
        }
        catch (SQLException se)
        {
            throw UnexpectedException.wrap(se);
        }
    }

    /** called at 10.30->10.31 */
    @SuppressWarnings({"UnusedDeclaration"})
    public void materializeAssayResults(final ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        // This needs to happen later, after all of the AssayProviders have been registered
        ContextListener.addStartupListener(new StartupListener()
        {
            @Override
            public void moduleStartupComplete(ServletContext servletContext)
            {
                AssayService.get().upgradeAssayDefinitions(moduleContext.getUpgradeUser(), 11.1);
            }
        });
    }

    /** called at 11.10->11.101 */
    @SuppressWarnings({"UnusedDeclaration"})
    public void renameObjectIdToRowId(final ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        // This needs to happen later, after all of the AssayProviders have been registered
        ContextListener.addStartupListener(new StartupListener()
        {
            @Override
            public void moduleStartupComplete(ServletContext servletContext)
            {
                AssayService.get().upgradeAssayDefinitions(moduleContext.getUpgradeUser(), 11.101);
            }
        });
    }

    /**
     * Called at 10.31->10.32
     * Get rid of the duplicate assay data in datasets and rely on a join to the original data on the assay side
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void deleteDuplicateAssayDatasetFields(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        deleteDuplicateAssayDatasetFields(context.getUpgradeUser(), ContainerManager.getRoot());
    }

    public void deleteDuplicateAssayDatasetFields(User user, Container c)
    {
        try
        {
            Study study = StudyService.get().getStudy(c);
            if (study != null)
            {
                for (DataSet dataSet : study.getDataSets())
                {
                    if (dataSet.getProtocolId() != null)
                    {
                        Domain domain = dataSet.getDomain();
                        for (org.labkey.api.exp.property.DomainProperty prop : domain.getProperties())
                        {
                            String keyName = dataSet.getKeyPropertyName();
                            if (keyName == null || !keyName.equalsIgnoreCase(prop.getName()))
                            {
                                prop.delete();
                            }
                        }
                        domain.save(user);
                    }
                }
            }

            // Recurse through the children
            for (Container child : c.getChildren())
            {
                deleteDuplicateAssayDatasetFields(user, child);
            }
        }
        catch (ChangePropertyDescriptorException e)
        {
            throw new UnexpectedException(e);
        }
    }
}
