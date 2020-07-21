package org.labkey.pipeline.status;

import edu.emory.mathcs.backport.java.util.Collections;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Used for Jackson serialization
 * @see StatusController.Details2Action
 * @see StatusController.StatusDetailLog
 */
public class StatusDetailsBean
{
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

    public final List<StatusDetailFile> files;
    public final List<StatusDetailRun> runs;
    public final StatusDetailLog log;

    public StatusDetailsBean(Container c, PipelineStatusFile psf, List<StatusDetailFile> files, List<StatusDetailRun> runs, StatusDetailLog log)
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

        // TODO: include split and join job info -- see JobDisplayColumn

        this.files = files;
        this.runs = runs;
        this.log = log;
    }

    public static StatusDetailsBean create(Container c, PipelineStatusFile psf, long logOffset) throws IOException
    {
        var statusRuns = ExperimentService.get().getExpRunsForJobId(psf.getRowId()).stream().map(StatusDetailRun::create).collect(Collectors.toList());
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
                List<File> files = FileDisplayColumn.listFiles(path.toFile(), c, provider);
                if (files != null && !files.isEmpty())
                {
                    statusFiles = files.stream().map(f -> new StatusDetailFile(c, psf.getRowId(), f)).collect(Collectors.toList());
                }


                // read from the offset to the end of the file content
                StringBuilder sb = new StringBuilder();
                long count = transferTo(sb, path, logOffset);
                if (psf.isActive())
                {
                    // if the job is still running and we aren't on an end-of-lne,
                    // wind back to the most recent newline.
                    while (sb.length() > 1)
                    {
                        char ch = sb.charAt(sb.length()-1);
                        if (ch == '\n' || ch == '\r')
                            break;
                        sb.setLength(sb.length()-1);
                        count -= 1;
                    }
                }
                statusLog = new StatusDetailLog(sb.toString(), logOffset + count);
            }
        }

        return new StatusDetailsBean(c, psf, statusFiles, statusRuns, statusLog);
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
            this.name = file.getName();
            this.viewUrl = StatusController.urlShowFile(c, rowId, name, false);
            this.downloadUrl = StatusController.urlShowFile(c, rowId, name, true);
        }
    }

    public static class StatusDetailLog
    {
        public final long nextOffset;
        public final List<LogFileParser.Record> records;

        public StatusDetailLog(String data, long nextOffset)
        {
            this.records = LogFileParser.parseLines(data);
            this.nextOffset = nextOffset;
        }
    }

}
