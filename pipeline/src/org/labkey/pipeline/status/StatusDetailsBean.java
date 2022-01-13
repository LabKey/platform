package org.labkey.pipeline.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.StringBuilderWriter;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ResourceURL;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.LimitedSizeInputStream;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.pipeline.api.PipelineStatusFileImpl;
import org.labkey.pipeline.api.PipelineStatusManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.labkey.pipeline.api.PipelineStatusManager.getSplitStatusFiles;

/**
 * Used for Jackson serialization
 * @see StatusController.DetailsAction
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // Don't serialize null values
public class StatusDetailsBean
{
    public static final Logger LOG = Logger.getLogger(StatusDetailsBean.class);

    public final int rowId;
    public final String jobId;
    public final String created;
    public final String modified;
    public final String email;
    public final String status;
    public final Container container;
    public final String description;
    public final String info;
    public final boolean active;
    public final boolean hadError;
    public final String dataUrl;
    public final String activeHostName;
    public final String filePath;

    public final StatusDetailsBean parentStatus;
    public final List<StatusDetailsBean> splitStatus;

    public final List<StatusDetailFile> files;
    public final List<StatusDetailRun> runs;
    public final StatusDetailLog log;
    public final Integer fetchCount;
    public final Integer queuePosition;

    // private constructor for parent/split job status
    private StatusDetailsBean(Container c, PipelineStatusFile psf)
    {
        this(c, psf, null, null, null, null, null, null, null);
    }

    public StatusDetailsBean(Container c, PipelineStatusFile psf, List<StatusDetailFile> files, List<StatusDetailRun> runs, StatusDetailsBean parentStatus, List<StatusDetailsBean> splitStatus, StatusDetailLog log, Integer fetchCount, Integer queuePosition)
    {
        this.rowId = psf.getRowId();
        this.jobId = psf.getJobId();
        this.created = DateUtil.formatDateTime(c, psf.getCreated());
        this.modified = DateUtil.formatDateTime(c, psf.getModified());
        this.email = psf.getEmail();
        this.status = psf.getStatus();
        this.container = psf.lookupContainer();
        this.description = psf.getDescription();
        this.info = psf.getInfo();
        this.active = psf.isActive();
        this.hadError = psf.isHadError();
        this.dataUrl = psf.getDataUrl();
        this.activeHostName = psf.getActiveHostName();
        this.filePath = psf.getFilePath();

        this.parentStatus = parentStatus;
        this.splitStatus = splitStatus;

        this.files = files;
        this.runs = runs;
        this.log = log;
        this.fetchCount = fetchCount;
        this.queuePosition = queuePosition;
    }

    public static StatusDetailsBean create(Container c, PipelineStatusFile psf, long logOffset, int fetchCount)
    {
        var statusRuns = ExperimentService.get().getExpRunsForJobId(psf.getRowId()).stream().map(StatusDetailRun::create).collect(toList());
        var statusFiles = Collections.emptyList();
        StatusDetailLog statusLog = null;

        String strPath = psf.getFilePath();
        if (null != strPath)
        {
            Path path = FileUtil.stringToPath(c, strPath, false);
            if (Files.exists(path))
            {
                // get other log files in the directory
                var provider = PipelineService.get().getPipelineProvider(psf.getProvider());
                List<Path> files = FileDisplayColumn.listFiles(path, c, provider);
                if (files != null && !files.isEmpty())
                {
                    statusFiles = files.stream().map(f -> new StatusDetailFile(c, psf.getRowId(), f)).collect(toList());
                }


                // read from the offset to the end of the file content
                try
                {
                    StringBuilder sb = new StringBuilder();
                    long count = transferTo(sb, path, logOffset);
                    if (psf.isActive())
                    {
                        // if the job is still running and we aren't on an end-of-lne,
                        // wind back to the most recent newline.
                        while (sb.length() > 1)
                        {
                            char ch = sb.charAt(sb.length() - 1);
                            if (ch == '\n' || ch == '\r')
                                break;
                            sb.setLength(sb.length() - 1);
                            count -= 1;
                        }
                    }
                    var records = LogFileParser.parseLines(sb.toString());
                    statusLog = StatusDetailLog.createSuccess(records, logOffset + count);
                }
                catch (IOException e)
                {
                    LOG.warn("Error reading log file '" + strPath + "' at offset " + logOffset + ": " + e.getMessage(), e);
                    statusLog = StatusDetailLog.createError("Error reading log file at offset " + logOffset + ": " + e.getMessage(), logOffset);
                }
            }
        }

        StatusDetailsBean parentStatus = null;
        if (psf.getJobParentId() != null)
        {
            var parent = PipelineStatusManager.getJobStatusFile(psf.getJobParentId());
            if (parent != null)
                parentStatus = new StatusDetailsBean(parent.lookupContainer(), parent);
        }

        List<StatusDetailsBean> splitStatus = null;
        List<PipelineStatusFileImpl> splitStatusFiles = getSplitStatusFiles(psf.getJobId());
        if (!splitStatusFiles.isEmpty())
        {
            splitStatus = splitStatusFiles.stream()
                    .sorted(Comparator.comparing(PipelineStatusFile::getDescription, String.CASE_INSENSITIVE_ORDER))
                    .map(split -> new StatusDetailsBean(split.lookupContainer(), split))
                    .collect(toList());
        }

        return new StatusDetailsBean(c, psf, statusFiles, statusRuns, parentStatus, splitStatus, statusLog, fetchCount, PipelineService.get().getPipelineQueue().getQueuePositions().get(psf.getJobId()));
    }

    /** Issue 44540 - any log files bigger than this will effectively be truncated for the job details page's log view */
    private static final long MAX_LOG_SIZE = 10_000_000;

    // Copy the file content from Path to the PrintWriter,
    // skipping offset characters and closing the PrintWriter when complete.
    private static long transferTo(StringBuilder out, Path p, long offset) throws IOException
    {
        // Pipeline log files are written in platform default encoding.
        // See PipelineJob.createPrintWriter() and PipelineJob.OutputLogger.write()
        // Use platform default encoding when reading the log file.
        try (LimitedSizeInputStream in = new LimitedSizeInputStream(Files.newInputStream(p), MAX_LOG_SIZE);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, Charset.defaultCharset()));
             PrintWriter pw = new PrintWriter(new StringBuilderWriter(out)))
        {
            if (offset > 0)
                br.skip(offset);

            try
            {
                return br.transferTo(pw);
            }
            catch (LimitedSizeInputStream.LimitReachedException e)
            {
                String extraMessage = System.lineSeparator() + System.lineSeparator() +
                        "Log file viewing is capped at " + MAX_LOG_SIZE + " bytes. Use the raw the full file to view the remainder locally."
                        + System.lineSeparator();
                out.append(extraMessage);
                return e.getBytesRead() + extraMessage.getBytes(Charset.defaultCharset()).length;
            }
        }
    }

    public static class StatusDetailRun
    {
        public final int rowId;
        public final String name;
        public final String lsid;
        public final ActionURL url;

        public StatusDetailRun(int rowId, String name, String lsid, ActionURL detailsURL)
        {
            this.rowId = rowId;
            this.name = name;
            this.lsid = lsid;
            this.url = detailsURL;
        }

        public static StatusDetailRun create(ExpRun run)
        {
            return new StatusDetailRun(run.getRowId(), run.getName(), run.getLSID(), run.detailsURL());
        }
    }

    public static class StatusDetailFile
    {
        public final String name;
        public final ActionURL viewUrl;
        public final ActionURL downloadUrl;

        public StatusDetailFile(Container c, int rowId, File file)
        {
            this(c, rowId, file.getName());
        }

        public StatusDetailFile(Container c, int rowId, Path path)
        {
            this(c, rowId, path.getFileName().toString());
        }

        public StatusDetailFile(Container c, int rowId, String fileName)
        {
            this.name = fileName;
            this.viewUrl = StatusController.urlShowFile(c, rowId, name, false);
            this.downloadUrl = StatusController.urlShowFile(c, rowId, name, true);
        }
    }

    public static class StatusDetailLog
    {
        public final boolean success;
        public final String message;
        public final List<LogFileParser.Record> records;
        public final long nextOffset;

        protected StatusDetailLog(boolean success, String message, List<LogFileParser.Record> records, long nextOffset)
        {
            this.success = success;
            this.message = message;
            this.records = records;
            this.nextOffset = nextOffset;
        }

        /** Read log successfully */
        public static StatusDetailLog createSuccess(List<LogFileParser.Record> records, long nextOffset)
        {
            return new StatusDetailLog(true, null, records, nextOffset);
        }

        /** Error reading log */
        public static StatusDetailLog createError(String message, long nextOffset)
        {
            return new StatusDetailLog(false, message, null, nextOffset);
        }
    }

    public @Nullable URLHelper getWebDavUrl()
    {
        if (filePath == null)
        {
            return null;
        }

        Path logPath = Path.of(filePath);
        if (!Files.exists(logPath))
        {
            return null;
        }

        if (container == null)
        {
            return null;
        }

        String url = FileContentService.get().getWebDavUrl(logPath, container, FileContentService.PathType.serverRelative);
        try
        {
            return url == null ? null : new URLHelper(url);
        }
        catch (URISyntaxException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }
}
