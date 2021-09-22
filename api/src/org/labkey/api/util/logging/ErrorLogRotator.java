package org.labkey.api.util.logging;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.rolling.action.PathWithAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper class to retain labkey-errors.log file content if it's in danger of rotating out and potentially losing
 * the root cause of a problem.
 *
 * We will retain up to three log files of 100 MB (as configured in log4j2.xml). We'll keep the first log from a given
 * webapp startup, moving it to labkey-errors-yyyy-MM-dd.log for archive purposes.
 *
 * See issue 43686.
 */
public class ErrorLogRotator
{
    public static final int MAX_RETAINED = 3;
    /** Don't retain more than one file per session */
    private static boolean _copiedOriginal = false;

    /** Remember when we started up so we can compare file timestamps against it */
    private static final Date _startup = new Date();

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private static final Logger LOG = LogHelper.getLogger(ErrorLogRotator.class, "Retains original set of errors from labkey-errors.log when rotating");

    public static void init()
    {

    }

    public List<PathWithAttributes> filter(List<PathWithAttributes> paths)
    {
        // Narrow to just the error log files
        paths = paths.stream().filter(p -> p.getPath().toString().contains("labkey-errors.log")).collect(Collectors.toList());

        List<PathWithAttributes> result = new ArrayList<>();

        // Look for the first file from the current set of logging to move it away instead of rotating it
        PathWithAttributes logToRetain = null;
        long bestTimeVersusStartup = Long.MAX_VALUE;

        // Per Log4J specs, paths are sorted with most recently modified files first
        for (int i = MAX_RETAINED; i < paths.size(); i++)
        {
            PathWithAttributes path = paths.get(i);
            long timeVersusStartup = Math.abs(path.getAttributes().creationTime().toMillis() - _startup.getTime());
            // Look for a file that's the closest to the time we started up, and within 20 seconds of startup
            if (timeVersusStartup < bestTimeVersusStartup && timeVersusStartup < 20_000)
            {
                bestTimeVersusStartup = timeVersusStartup;
                logToRetain = path;
            }

            result.add(path);
        }

        if (logToRetain != null && !_copiedOriginal && logToRetain.getAttributes().size() > 0)
        {
            Path target = logToRetain.getPath().getParent().resolve("labkey-errors-" + DATE_FORMAT.format(new Date()) + ".log");
            LOG.info("Retaining labkey-errors.log file before it gets deleted by rotation. Copying to " + target);

            try
            {
                Files.move(logToRetain.getPath(), target);
                // Don't need to mark it for deletion, as it's already been moved
                result.remove(logToRetain);
            }
            catch (IOException e)
            {
                LOG.warn("Failed to retain error log file " + logToRetain.getPath(), e);
            }
            _copiedOriginal = true;
        }

        return result;
    }
}
