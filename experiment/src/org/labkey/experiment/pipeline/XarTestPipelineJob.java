package org.labkey.experiment.pipeline;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.WorkbookContainerType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.pipeline.XarGeneratorId;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.TaskPipelineSettings;
import org.labkey.api.pipeline.file.AbstractFileAnalysisJob;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.User;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.experiment.ExperimentModule;
import org.labkey.vfs.FileLike;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The goal is this class is to provide a "minimal" test case to exercise the Pipeline/XarGenerator code.
 * As filesystem-related permissions are tightened, this can be extended to include more test cases, such as:
 *
 * - PipelineJob in Container1 referencing a file in /Shared
 * - PipelineJob in a workbook that references a file in the parent container
 * - PipelineJob in a workbook that references a file in a sibling workbook
 * - Check that inappropriate cross-folder access is disallowed
 * - If LK implements a scheme to determine allowable locations outside the LK root, test that here.
 */
public class XarTestPipelineJob extends PipelineJob implements FileAnalysisJobSupport
{
    public static final String PROVIDER_NAME = "XarTestPipelineJob Provider";

    private TaskId _taskPipelineId;

    private String _jobName;
    private FileLike _webserverJobDir;
    private List<File> _inputFiles;
    private List<File> _outputFiles;

    // Default constructor for serialization
    protected XarTestPipelineJob()
    {
    }

    private XarTestPipelineJob(Container c, User user, PipeRoot pipeRoot, String jobName, List<File> inputFiles, List<File> outputFiles)
    {
        super(PROVIDER_NAME, new ViewBackgroundInfo(c, user, null), pipeRoot);

        _taskPipelineId = getTaskIdForEngine();
        _webserverJobDir = getOrCreateBaseDir(c, jobName);
        setLogFile(getOrCreateLogFile(c, jobName));

        _jobName = jobName;
        _inputFiles = inputFiles;
        _outputFiles = outputFiles;
    }

    private static Path getOrCreateLogFile(Container c, String jobName)
    {
        // TODO: There must be a more convenient way to work with FileLike...
        return(FileUtil.appendPath(getOrCreateBaseDir(c, jobName), new org.labkey.api.util.Path(FileUtil.makeLegalName(jobName) + ".log")).toNioPathForWrite());
    }

    private static FileLike getOrCreateBaseDir(Container c, String jobName)
    {
        PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(c);
        if (pipeRoot == null)
        {
            throw new IllegalStateException("Pipeline root not found for: " + c.getPath());
        }

        FileLike baseDir = FileUtil.appendPath(pipeRoot.getRootFileLike(), new org.labkey.api.util.Path(FileUtil.makeLegalName(jobName)));
        if (!baseDir.exists())
        {
            try
            {
                baseDir.mkdirs();
            }
            catch (IOException e)
            {
                throw new RuntimeException("Unable to create baseDir: " + c.getPath(), e);
            }
        }

        return baseDir;
    }

    public static XarTestPipelineJob createJob(Container c, User user, String jobName, List<File> inputFiles, List<File> outputFiles) throws PipelineValidationException
    {
        PipeRoot pipelineRoot = PipelineService.get().getPipelineRootSetting(c);

        return new XarTestPipelineJob(c, user, pipelineRoot, jobName, inputFiles, outputFiles);
    }

    @Nullable
    @Override
    public TaskId getActiveTaskId()
    {
        //ensure this TaskFactory is registered:
        try
        {
            TaskId taskFactoryId = getTaskFactoryId();
            try
            {
                if (PipelineJobService.get().getTaskFactory(taskFactoryId) == null)
                {
                    registerTaskPipeline();
                }
            }
            catch (NullPointerException e)
            {
                //this indicates the TaskFactory has not been registered yet
                getLogger().error("A NullPointerException was thrown in XarTestPipelineJob", e);
                registerTaskPipeline();
            }
        }
        catch (CloneNotSupportedException e)
        {
            getLogger().error(e.getMessage(), e);
        }

        return super.getActiveTaskId();
    }

    private static TaskId getTaskIdForEngine()
    {
        return new TaskId(XarTestPipelineJob.class, PipelineJobService.LocationType.WebServer.name());
    }

    private static TaskId getTaskFactoryId()
    {
        return new TaskId(XarTestTaskFactory.class, PipelineJobService.LocationType.WebServer.name());
    }

    public static void registerTaskPipeline() throws CloneNotSupportedException
    {
        //first register TaskFactory
        TaskId taskFactoryId = getTaskFactoryId();
        PipelineJobService.get().addTaskFactory(new XarTestTaskFactory(taskFactoryId));

        //then TaskPipeline
        TaskId taskPipelineId = getTaskIdForEngine();
        TaskFactory<?> xarFact = PipelineJobService.get().getTaskFactory(new TaskId(XarGeneratorId.class));
        if (xarFact == null)
        {
            throw new IllegalStateException("Unable to find TaskFactory for XarGeneratorId.class");
        }

        TaskPipelineSettings settings = new TaskPipelineSettings(taskPipelineId);
        settings.setTaskProgressionSpec(new Object[]{taskFactoryId, xarFact.getId()});
        settings.setDeclaringModule(ModuleLoader.getInstance().getModule(ExperimentModule.class));
        PipelineJobService.get().addTaskPipeline(settings);
    }

    @Override
    public TaskPipeline<?> getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(_taskPipelineId);
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return _jobName;
    }

    private static final String INPUT_ROLE = "Input File";
    private static final String OUTPUT_ROLE = "Output File";

    private static class XarTestTaskFactory extends AbstractTaskFactory<AbstractTaskFactorySettings, XarTestTaskFactory>
    {
        public XarTestTaskFactory(TaskId id)
        {
            super(id);

            setLocation(PipelineJobService.LocationType.WebServer.name());
        }

        @Override
        public Task<XarTestTaskFactory> createTask(PipelineJob job)
        {
            return new Task<>(this, job)
            {
                @NotNull
                @Override
                public RecordedActionSet run() throws PipelineJobException
                {
                    // The purpose of this is to specify files outside the LK folder root:
                    RecordedAction action = new RecordedAction(XarTestTaskFactory.class.getName());

                    if (getJob() instanceof XarTestPipelineJob xj)
                    {
                        xj._inputFiles.forEach(x -> {
                            action.addInput(x.toURI(), INPUT_ROLE);
                        });

                        xj._outputFiles.forEach(x -> {
                            action.addOutput(x.toURI(), OUTPUT_ROLE, false);
                        });
                    }

                    return new RecordedActionSet(action);
                }
            };
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return null;
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Arrays.asList(XarTestTaskFactory.class.getName());
        }

        @Override
        public String getStatusName()
        {
            return "RUNNING";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    @Override
    public String getProtocolName()
    {
        return _jobName;
    }

    @Override
    public String getJoinedBaseName()
    {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public List<String> getSplitBaseNames()
    {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public String getBaseName()
    {
        return getAnalysisDirectory().getName();
    }

    @Override
    public String getBaseNameForFileType(FileType fileType)
    {
        return getBaseName();
    }

    @Override
    public File getDataDirectory()
    {
        return _webserverJobDir.toNioPathForWrite().toFile();
    }

    @Override
    public File getAnalysisDirectory()
    {
        return _webserverJobDir.toNioPathForWrite().toFile();
    }

    @Override
    public File findInputFile(String name)
    {
        return FileUtil.appendName(getAnalysisDirectory(), name);
    }

    @Override
    public File findOutputFile(String name)
    {
        return FileUtil.appendName(getAnalysisDirectory(), name);
    }

    @Override
    public File findOutputFile(@NotNull String outputDir, @NotNull String fileName)
    {
        return AbstractFileAnalysisJob.getOutputFile(outputDir, fileName, getPipeRoot(), getLogger(), getAnalysisDirectory());
    }

    @Override
    public ParamParser createParamParser()
    {
        return PipelineJobService.get().createParamParser();
    }

    @Override
    public @Nullable File getParametersFile()
    {
        return null;
    }

    @Override
    public @Nullable File getJobInfoFile()
    {
        return FileUtil.appendName(_webserverJobDir.toNioPathForWrite().toFile(), FileUtil.makeLegalName(_jobName) + ".job.json");
    }

    @Override
    public List<File> getInputFiles()
    {
        return _inputFiles;
    }

    @Override
    public FileType.gzSupportLevel getGZPreference()
    {
        return FileType.gzSupportLevel.PREFER_GZ;
    }

    public static class TestCase extends Assert
    {
        private static final String PROJECT_NAME = "XarPipelineTestProject";

        @BeforeClass
        public static void initialSetUp() throws Exception
        {
            //pre-clean
            cleanup();

            Container project = ContainerManager.getForPath(PROJECT_NAME);
            if (project == null)
            {
                project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, TestContext.get().getUser());
                Set<Module> modules = new HashSet<>(project.getActiveModules());
                modules.add(ModuleLoader.getInstance().getModule(ExperimentModule.class));
                project.setActiveModules(modules);
            }
        }

        @AfterClass
        public static void cleanup()
        {
            Container project = ContainerManager.getForPath(PROJECT_NAME);
            if (project != null)
            {
                File pipelineRoot = PipelineService.get().getPipelineRootSetting(project).getRootPath();
                try
                {
                    if (pipelineRoot.exists())
                    {
                        FileUtils.deleteDirectory(pipelineRoot);
                    }
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }

                ContainerManager.deleteAll(project, TestContext.get().getUser());
            }
        }

        @Test
        public void xarTest() throws Exception
        {
            doXarTest(
                    "XarTestJob1",
                    Arrays.asList(new File("/arbitrary/path/outside/lkRoot/myFile.txt")),
                    Arrays.asList(new File("/another/arbitrary/path/outside/lkRoot/myFile.txt"))
            );

            Container project = ContainerManager.getForPath(PROJECT_NAME);
            PipeRoot projectRoot = PipelineService.get().getPipelineRootSetting(project);
            Assert.assertNotNull(PROJECT_NAME + " pipeline root is null", projectRoot);

            // We expect /Shared to be an allowable output location:
            PipeRoot sharedRoot = PipelineService.get().getPipelineRootSetting(ContainerManager.getSharedContainer());
            Assert.assertNotNull("Shared pipeline root is null", sharedRoot);

            // A pipeline job submitted to a project/folder should be able to access files in /Shared
            doXarTest(
                    "XarTestJob_UsingShared",
                    Arrays.asList(
                            FileUtil.appendPath(projectRoot.getRootFileLike(), org.labkey.api.util.Path.parse("myFileInJobFolder.txt")).toNioPathForWrite().toFile(),
                            FileUtil.appendPath(sharedRoot.getRootFileLike(), org.labkey.api.util.Path.parse("myFileInSharedFolder.txt")).toNioPathForWrite().toFile()
                    ),
                    Arrays.asList(
                            FileUtil.appendPath(projectRoot.getRootFileLike(), org.labkey.api.util.Path.parse("myOutputFileInJobFolder.txt")).toNioPathForWrite().toFile(),
                            FileUtil.appendPath(sharedRoot.getRootFileLike(), org.labkey.api.util.Path.parse("myOutputFileInSharedFolder.txt")).toNioPathForWrite().toFile()
                    )
            );

            // Now create workbooks in this folder:
            Container wb1 = ContainerManager.createContainer(project, null, "WB1", null, WorkbookContainerType.NAME, TestContext.get().getUser());
            Container wb2 = ContainerManager.createContainer(project, null, "WB2", null, WorkbookContainerType.NAME, TestContext.get().getUser());

            PipeRoot wb1Root = PipelineService.get().getPipelineRootSetting(wb1);
            Assert.assertNotNull("wb1Root is null", wb1Root);
            PipeRoot wb2Root = PipelineService.get().getPipelineRootSetting(wb2);
            Assert.assertNotNull("wb2Root is null", wb2Root);

            // A pipeline job submitted to a workbook should be able to reference files in /Shared, the parent folder, or sibling workbooks:
            doXarTest(
                    "XarTestJob_AcrossWorkbooks",
                    Arrays.asList(
                            FileUtil.appendPath(projectRoot.getRootFileLike(), org.labkey.api.util.Path.parse("myFileInJobFolder.txt")).toNioPathForWrite().toFile(),
                            FileUtil.appendPath(sharedRoot.getRootFileLike(), org.labkey.api.util.Path.parse("myFileInSharedFolder.txt")).toNioPathForWrite().toFile(),
                            FileUtil.appendPath(wb1Root.getRootFileLike(), org.labkey.api.util.Path.parse("myFileInWB1Folder.txt")).toNioPathForWrite().toFile(),
                            FileUtil.appendPath(wb2Root.getRootFileLike(), org.labkey.api.util.Path.parse("myFileInWB2Folder.txt")).toNioPathForWrite().toFile()
                    ),
                    Arrays.asList(
                            FileUtil.appendPath(projectRoot.getRootFileLike(), org.labkey.api.util.Path.parse("myOutputFileInJobFolder.txt")).toNioPathForWrite().toFile(),
                            FileUtil.appendPath(sharedRoot.getRootFileLike(), org.labkey.api.util.Path.parse("myOutputFileInSharedFolder.txt")).toNioPathForWrite().toFile(),
                            FileUtil.appendPath(wb1Root.getRootFileLike(), org.labkey.api.util.Path.parse("myOutputFileInWB1Folder.txt")).toNioPathForWrite().toFile(),
                            FileUtil.appendPath(wb2Root.getRootFileLike(), org.labkey.api.util.Path.parse("myOutputFileInWB2Folder.txt")).toNioPathForWrite().toFile()
                    ),
                    wb2
            );
        }

        @Test
        public void xarTestRelativePaths() throws Exception
        {
            // NOTE: these will get converted to absolute paths by the pipeline code when they are saved, so this passes right now:
            //Assert.assertThrows("Maybe LabKey shouldn't allow this", Exception.class, () -> {
                doXarTest(
                        "XarTestJob_QuestionablePaths",
                        Arrays.asList(new File("../../../root/myFile.txt")),
                        Arrays.asList(new File("../../../users/root/anotherFile.txt"))
                );
            //});
        }

        private void doXarTest(String jobName, List<File> inputFiles, List<File> outputFiles) throws Exception
        {
            doXarTest(jobName, inputFiles, outputFiles, ContainerManager.getForPath(PROJECT_NAME));
        }

        private void doXarTest(String jobName, List<File> inputFiles, List<File> outputFiles, Container c) throws Exception
        {
            PipelineJob job1 = XarTestPipelineJob.createJob(c, TestContext.get().getUser(), jobName, inputFiles, outputFiles);
            PipelineService.get().queueJob(job1);
            long start = System.currentTimeMillis();
            long timeout = 10 * 1000; //10 secs
            while (!isJobDone(job1))
            {
                Thread.sleep(1000);

                long duration = System.currentTimeMillis() - start;
                if (duration > timeout)
                {
                    //NOTE: it's possible a job could time out on a busy cluster.  rather than fail, continue in case there's a second engine to test
                    _log.warn("timed out waiting for job: " + job1.getDescription());
                    break;
                }
            }

            PipelineStatusFile sf = PipelineService.get().getStatusFile(job1.getJobGUID());
            Assert.assertNotNull("Missing status file", sf);

            List<? extends ExpRun> runs = ExperimentService.get().getExpRunsForJobId(sf.getRowId());
            Assert.assertEquals("Wrong run number", 1, runs.size());

            ExpRun run = runs.get(0);
            List<? extends ExpData> inputs = run.getInputDatas(INPUT_ROLE, null);
            Assert.assertEquals("Wrong input number", inputFiles.size(), inputs.size());

            List<? extends ExpData> outputs = run.getOutputDatas(null);
            Assert.assertEquals("Wrong output number", outputFiles.size(), outputs.size());
        }

        private static boolean isJobDone (PipelineJob job) throws Exception
        {
            TableInfo ti = PipelineService.get().getJobsTable(job.getUser(), job.getContainer());
            TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("job"), job.getJobGUID()), null);
            Map<String, Object> map = ts.getMap();

            if (PipelineJob.TaskStatus.complete.matches((String) map.get("status")))
                return true;

            //look for errors
            boolean error = PipelineJob.TaskStatus.error.matches((String) map.get("status"));
            if (error)
            {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = Readers.getReader(job.getLogFile()))
                {
                    sb.append("*******************\n");
                    sb.append("Error running XarTestPipelineJob.TestCase.  Pipeline log:\n");
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        sb.append(line).append('\n');
                    }

                    sb.append("*******************\n");
                }

                _log.error(sb.toString());

                throw new Exception("There was an error running job: " + job.getDescription());
            }

            return false;
        }
    }
}
