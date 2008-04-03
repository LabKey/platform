package org.labkey.biotrue.task;

import org.labkey.biotrue.soapmodel.Browse_response;
import org.labkey.biotrue.soapmodel.Entityinfo;
import org.labkey.biotrue.datamodel.Task;
import org.labkey.biotrue.objectmodel.BtEntity;
import org.apache.log4j.Logger;

import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Arrays;

public class BrowseTask extends BtTask
{
    static final private Logger _log = Logger.getLogger(BrowseTask.class);
    static Set<String> directoryEnts = new LinkedHashSet(Arrays.asList(new String[] { "lab", "project", "run" }));
    public BrowseTask(Task task)
    {
        super(task);
    }

    public void doRun() throws Exception
    {
        BtEntity entity = getEntity();
        Browse_response entityResponse = loginBrowse(entity);
        if (entity != null)
        {
            entity.ensurePhysicalDirectory();
        }
        for (Entityinfo ei : entityResponse.getData().getAllContent())
        {
            BtEntity child = BtEntity.ensureChild(getServer(), entity, ei.getId(), ei.getType(), ei.getName());
            boolean browse = directoryEnts.contains(ei.getType());
            if (browse)
            {
                newTask(child, Operation.view);
            }
            else
            {
                if (!child.hasPhysicalName())
                {
                    newTask(child, Operation.download);
                }
            }
        }
    }
}
