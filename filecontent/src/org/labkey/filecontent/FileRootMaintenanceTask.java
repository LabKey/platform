package org.labkey.filecontent;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Sort;
import org.labkey.api.data.StopIteratingException;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileContentService;
import org.labkey.api.security.User;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    public void run(Logger log)
    {
        FileContentService service = FileContentService.get();

        if (service != null)
        {
            long deadline = HeartBeat.currentTimeMillis() + DateUtils.MILLIS_PER_MINUTE * MAX_MINUTES;
            MutableInt rootCount = new MutableInt();
            MutableBoolean finished = new MutableBoolean(true);

            new TableSelector(CoreSchema.getInstance().getTableInfoContainers(), Set.of("RowId", "EntityId", "FileRootSize"), null, new Sort("LastCrawled"))
                .forEach(FileRootRecord.class, record -> {
                    Container c = ContainerManager.getForId(record.entityId());
                    if (c != null)
                    {
                        File root = service.getFileRoot(c);
                        Long size = null != root && root.isDirectory() ? FileUtils.sizeOfDirectory(root) : null;

                        // Always update LastCrawled, even for invalid file roots
                        Map<String, Object> map = new HashMap<>();
                        long current = HeartBeat.currentTimeMillis();
                        map.put("LastCrawled", new Date(current));

                        // Update FileRootSize if it changed
                        boolean sizeChanged = !Objects.equals(record.fileRootSize(), size);
                        if (sizeChanged)
                            map.put("FileRootSize", size);

                        Table.update(User.getAdminServiceUser(), CoreSchema.getInstance().getTableInfoContainers(), map, record.rowId());

                        if (sizeChanged)
                            ContainerManager.uncache(c);  // Container stashes FileRootSize

                        rootCount.increment();

                        if (current >= deadline)
                        {
                            finished.setFalse();
                            throw new StopIteratingException();
                        }
                    }
                });

            if (finished.getValue())
                //noinspection StringConcatenationArgumentToLogCall - pipeline logger doesn't support parameterized messages yet! Issue #51480
                log.info("Completed crawling all " + rootCount.getValue() + " file roots");
            else
                //noinspection StringConcatenationArgumentToLogCall
                log.info("Crawled " + rootCount.getValue() + " file roots before reaching the " + MAX_MINUTES + "-minute deadline. Crawling will continue during the next system maintenance run.");
        }
    }

    private record FileRootRecord(int rowId, String entityId, Long fileRootSize) {}
}
