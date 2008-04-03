/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
package org.labkey.mousemodel;

import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.sample.*;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.mousemodel.mouse.MouseController;
import org.labkey.mousemodel.necropsy.NecropsyController;
import org.labkey.mousemodel.sample.SampleController;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * User: migra
 * Date: Jul 18, 2005
 * Time: 3:23:34 PM
 */
public class MouseModelModule extends DefaultModule implements LsidManager.LsidHandler, ContainerManager.ContainerListener
{
    public static final String NAME = "MouseModels";

    private static Logger _log = Logger.getLogger(MouseModelModule.class);


    public MouseModelModule()
    {
        super(NAME, 2.30, "/org/labkey/mousemodel", true,
                new WebPartFactory("Mouse Models"){
                    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart)
                    {
                        return new MouseModelController.MouseModelView();
                    }
                });
        addController("mousemodel", MouseModelController.class);
        addController("mousemodel-mouse", MouseController.class);
        addController("mousemodel-sample", SampleController.class);
        addController("mousemodel-necropsy", NecropsyController.class);
    }

    public Identifiable getObject(Lsid lsid)
    {
        String objectType = lsid.getNamespaceSuffix();
        if ("Mouse".equals(objectType))
        {
            return MouseModelManager.getMouse(Integer.parseInt(lsid.getObjectId()));
        }
        else
        {
            return SampleManager.getSample(lsid.toString());
        }
    }

    public String getDisplayURL(Lsid lsid)
    {
        String objectType = lsid.getNamespaceSuffix();
        if ("Mouse".equals(objectType))
        {
            Mouse mouse = MouseModelManager.getMouse(Integer.parseInt(lsid.getObjectId()));
            ActionURL url = new ActionURL();
            url.setExtraPath(ContainerManager.getForId(mouse.getContainer()).getPath());

            return url.relativeUrl("details.view", PageFlowUtil.map("entityId", mouse.getEntityId(), "modelId", mouse.getModelId()), "MouseModel-Mouse", false);
        }
        else //Sample
        {
            Sample sample = SampleManager.getSample(lsid.toString());
            if (null == sample)
                return null;

            ActionURL url = new ActionURL();
            Container c = ContainerManager.getForId(sample.getContainer());
            url.setExtraPath(c.getPath());
            Mouse mouse = MouseModelManager.getMouse(c, sample.getOrganismId());
            return url.relativeUrl("details.view", PageFlowUtil.map("LSID", lsid.toString(), "modelId", mouse.getModelId()), "MouseModel-Sample", false);
        }
    }


    @Override
    public void startup(ModuleContext moduleContext)
    {
        LsidManager.get().registerHandler("MouseModel", this);
        ContainerManager.addContainerListener(this);
        super.startup(moduleContext);
    }


    @Override
    public Collection<String> getSummary(Container c)
    {
        try
        {
            return MouseModelManager.getSummary(c);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    @Override
    public void afterSchemaUpdate(ModuleContext moduleContext, ViewContext viewContext)
    {
        DbSchema sampleSchema = DbSchema.get("mousemod");
        Container c = MouseModelManager.getLookupContainer();

        //Old installation may have mis-named container & upgraded via external script. Fix up here.
        if (null == c)
        {
            c = ContainerManager.getForPath("/_mouselookups");
            if (null != c)
            {
                Group[] groups = SecurityManager.getGroups(c, false);
                for (Group g : groups)
                    SecurityManager.deleteGroup(c.getPath() + "/" + g.getName());

                ContainerManager.move(c, ContainerManager.getSharedContainer());
                ContainerManager.rename(c, MouseModelManager.LOOKUP_CONTAINER_NAME);
            }
        }

        // If Container exists, assume we are done
        //TODO: Fix up partial installations?
        if (null == c)
        {
            try
            {
                c = MouseModelManager.ensureLookupContainer();
                _log.debug("Populating lookup tables in samples database");
                String lookupContainerId = c.getId();
                String select = sampleSchema.getSqlDialect().execute(sampleSchema, "populateLookups", "'" + lookupContainerId + "'");
                Table.execute(sampleSchema, select, new Object[]{});
            }
            catch (SQLException e)
            {
                if (null != c)
                    ContainerManager.delete(c, viewContext.getUser());
                _log.error("SQL Error while creating ensuring mouse lookup container", e);
                throw new RuntimeSQLException(e);
            }
        }

    }


    @Override
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(MouseSchema.getSchemaName());
    }

    @Override
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(MouseSchema.getSchema());
    }

    //void wantsToDelete(Container c, List<String> messages);
    public void containerCreated(Container c) {

    }

    public void containerDeleted(Container c, User user)
    {
        try
        {
            MouseModelManager.deleteAll(c);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }


    @Override
    public Set<String> getModuleDependencies()
    {
        Set<String> result = new HashSet<String>();
        result.add("Experiment");
        // we depond on "comm" for notes
        result.add("Wiki");
        result.add("Announcements");
        return result;
    }
}
