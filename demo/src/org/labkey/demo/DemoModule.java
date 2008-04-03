package org.labkey.demo;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.security.User;
import org.labkey.demo.model.Person;
import org.labkey.demo.model.DemoManager;
import org.labkey.demo.view.DemoWebPart;

import java.beans.PropertyChangeEvent;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Set;


public class DemoModule extends DefaultModule implements ContainerManager.ContainerListener
{
    private static final Logger _log = Logger.getLogger(DefaultModule.class);
    public static final String NAME = "Demo";

    public DemoModule()
    {
        super(NAME, 1.00, null, true,
            new WebPartFactory("Demo Summary") {
                public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
                {
                    return new DemoWebPart();
                }
            },
            new WebPartFactory("Demo Summary", WebPartFactory.LOCATION_RIGHT) {
                {
                    addLegacyNames("Narrow Demo Summary");
                }

                public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
                {
                    return new DemoWebPart();
                }
            });
        addController("demo", DemoController.class);
    }

    public void containerCreated(Container c)
    {
    }

    public void containerDeleted(Container c, User user)
    {
        try
        {
            DemoManager.getInstance().deleteAllData(c);
        }
        catch (SQLException e)
        {
            // ignore any failures.
            _log.error("Failure cleaning up demo data when deleting container " + c.getPath(), e);
        }
    }

    public Collection<String> getSummary(Container c)
    {
        try
        {
            Person[] people = DemoManager.getInstance().getPeople(c);
            if (people != null && people.length > 0)
            {
                Collection<String> list = new LinkedList<String>();
                list.add("Demo Module: " + people.length + " person records.");
                return list;
            }
        }
        catch (SQLException e)
        {
            _log.error("Failure checking for demo data in container " + c.getPath(), e);
        }
        return Collections.emptyList();
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }

    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(this);
    }

    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(DemoSchema.getInstance().getSchema());
    }


    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(DemoSchema.getInstance().getSchemaName());
    }
}