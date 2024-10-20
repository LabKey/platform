package org.labkey.filecontent;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.StopIteratingException;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.files.FileContentService;
import org.labkey.api.security.User;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class FileRootMaintenanceTask implements MaintenanceTask
{
    // Spend no more than 10 minutes crawling and computing folder sizes
    private static final int MAX_MINUTES = 10;

    @Override
    public String getDescription()
    {
        return "Calculate file root sizes";
    }

    @Override
    public String getName()
    {
        return "FileRootMaintenanceTask";
    }

    @Override
    public boolean canDisable()
    {
        return false;
    }

    @Override
    public void run(Logger log)
    {
        FileContentService service = FileContentService.get();

        if (service != null)
        {
            long deadline = HeartBeat.currentTimeMillis() + DateUtils.MILLIS_PER_MINUTE * MAX_MINUTES;
            MutableInt rootCount = new MutableInt();
            MutableBoolean finished = new MutableBoolean(true);
            TableInfo containers = CoreSchema.getInstance().getTableInfoContainers();
            String orderBy = " ORDER BY FileRootLastCrawled" + (containers.getSqlDialect().isPostgreSQL() ? " NULLS FIRST" : "");
            SQLFragment sql = new SQLFragment("SELECT RowId, EntityId, FileRootSize FROM " + containers.getSelectName() + orderBy);

            new SqlSelector(containers.getSchema(), sql)
                .forEach(FileRootRecord.class, record -> {
                    Container c = ContainerManager.getForId(record.entityId());
                    if (c != null)
                    {
                        File root = service.getFileRoot(c);
                        Long size = null != root && root.isDirectory() ? FileUtils.sizeOfDirectory(root) : null;

                        // Always update LastCrawled, even for invalid file roots and non-changing sizes
                        Map<String, Object> map = new HashMap<>();
                        long current = HeartBeat.currentTimeMillis();
                        map.put("FileRootLastCrawled", new Date(current));

                        // Update FileRootSize if it changed
                        boolean sizeChanged = !Objects.equals(record.fileRootSize(), size);
                        if (sizeChanged)
                            map.put("FileRootSize", size);

                        Table.update(User.getAdminServiceUser(), containers, map, record.rowId());
                        ContainerManager.uncache(c);  // Container stashes FileRootLastCrawled & FileRootSize

                        rootCount.increment();

                        if (current >= deadline)
                        {
                            finished.setFalse();
                            throw new StopIteratingException();
                        }
                    }
                });

            if (finished.getValue())
                log.info("Completed crawling all {} file roots", rootCount.getValue());
            else
                log.info("Crawled {} file roots before reaching the {}-minute deadline. Crawling will continue during the next system maintenance run.", rootCount.getValue(), MAX_MINUTES);
        }
    }

    private record FileRootRecord(int rowId, String entityId, Long fileRootSize) {}
}
