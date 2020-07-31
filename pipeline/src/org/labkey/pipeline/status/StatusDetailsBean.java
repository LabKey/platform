package org.labkey.pipeline.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.StringBuilderWriter;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;
import org.labkey.pipeline.api.PipelineStatusFileImpl;
import org.labkey.pipeline.api.PipelineStatusManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.labkey.pipeline.api.PipelineStatusManager.getSplitStatusFiles;

/**
 * Used for Jackson serialization
 * @see StatusController.Details2Action
 * @see StatusController.StatusDetailLog
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

    // private constructor for parent/split job status
    private StatusDetailsBean(Container c, PipelineStatusFile psf)
    {
        this(c, psf, null, null, null, null, null);
    }

    public StatusDetailsBean(Container c, PipelineStatusFile psf, List<StatusDetailFile> files, List<StatusDetailRun> runs, StatusDetailsBean parentStatus, List<StatusDetailsBean> splitStatus, StatusDetailLog log)
    {
        this.rowId = psf.getRowId();
        this.jobId = psf.getJobId();
        this.created = DateUtil.formatDateTime(c, psf.getCreated());
        this.modified = DateUtil.formatDateTime(c, psf.getModified());
        this.email = psf.getEmail();
        this.status = psf.getStatus();
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
    }

    public static StatusDetailsBean create(Container c, PipelineStatusFile psf, long logOffset)
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

        return new StatusDetailsBean(c, psf, statusFiles, statusRuns, parentStatus, splitStatus, statusLog);
    }

    // Copy the file content from Path to the PrintWriter,
    // skipping offset characters and closing the PrintWriter when complete.
    private static long transferTo(StringBuilder out, Path p, long offset) throws IOException
    {
        try (BufferedReader br = Files.newBufferedReader(p, StringUtilsLabKey.DEFAULT_CHARSET);
             PrintWriter pw = new PrintWriter(new StringBuilderWriter(out)))
        {
            if (offset > 0)
                br.skip(offset);

            return br.transferTo(pw);
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

}
