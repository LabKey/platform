package org.labkey.devtools;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.ArrayListValuedTreeMap;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfo.IndexDefinition;
import org.labkey.api.data.TableInfo.IndexType;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SupportedDatabase;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.BaseScanner.Handler;
import org.labkey.api.util.ButtonBuilder;
import org.labkey.api.util.DOM;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.URLHelper;
import org.labkey.api.vcs.Vcs;
import org.labkey.api.vcs.VcsService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.VBox;
import org.labkey.api.writer.PrintWriters;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.BR;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.PageFlowUtil.filter;

public class ToolsController extends SpringActionController
{
    private static final ActionResolver RESOLVER = new DefaultActionResolver(ToolsController.class);

    public static final String NAME = "tools";

    public ToolsController()
    {
        setActionResolver(RESOLVER);
    }

    @RequiresPermission(AdminPermission.class)
    public class BeginAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new ActionListView(ToolsController.this, actionDescriptor->BeginAction.class != actionDescriptor.getActionClass());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addBeginNavTrail(root);
        }
    }


    private void addBeginNavTrail(NavTree root)
    {
        root.addChild("Tools", new ActionURL(BeginAction.class, getContainer()));
    }


    /**
     * This action "validates" our two non-trivial .gitattributes files (in platform and commonAssays) by outputting all
     * filenames in those files that don't exist in the source code. This highlights files that have been moved, renamed,
     * or deleted, making it easy to update the .gitattributes files with their new locations.
     */
    @SuppressWarnings("unused")
    @RequiresPermission(AdminPermission.class)
    public class GitAttributesAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            // The .gitattributes files we care about live at the root of the platform and commonAssays repos. Pick an
            // arbitrary module from each of these repos and use it to locate each root and .gitattributes file.
            return new VBox(
                new GitAttributesView("core"), // Use the "core" module to locate the platform repo source root
                new GitAttributesView("ms2")   // Use the "ms2" module to locate the commonAssays repo source root
            );
        }

        private class GitAttributesView extends HttpView<Object>
        {
            private final String _moduleName;

            public GitAttributesView(String moduleName)
            {
                _moduleName = moduleName;
            }

            @Override
            protected void renderInternal(Object model, PrintWriter out) throws IOException
            {
                out.println("<pre>");

                String errorMessage = new GitAttributesParser().parse(_moduleName, new GitAttributesHandler()
                {
                    @Override
                    public void handle(Path gaPath, Stream<String> stream)
                    {
                        out.println("Files listed in " + gaPath + " that don't exist:\n");
                        List<String> missing = getMissingFiles(gaPath, stream);
                        missing.forEach(filename->out.println(filter(filename)));
                        if (!missing.isEmpty())
                        {
                            out.println();
                            out.println(
                                new ButtonBuilder("Delete All " + missing.size() + " File Paths from .gitattributes")
                                    .href(new ActionURL(DeleteMissingFilesAction.class, getContainer()).addParameter("module", _moduleName))
                                    .usePost()
                            );
                        }
                    }
                });

                if (null != errorMessage)
                    out.println(errorMessage);

                out.println("</pre>");
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addBeginNavTrail(root);
            root.addChild(".gitattributes File Check");
        }
    }

    private static abstract class GitAttributesHandler
    {
        abstract void handle(Path gaPath, Stream<String> stream);

        // Called after the stream is closed. Optional post handling.
        void postHandle(Path gaPath)
        {
        }
    }

    private static class GitAttributesParser
    {
        public String parse(String moduleName, GitAttributesHandler handler) throws IOException
        {
            Module module = ModuleLoader.getInstance().getModule(moduleName);

            if (null == module)
                return "Module " + moduleName + " not running";

            String sourcePath = module.getSourcePath();

            if (null == sourcePath)
                return module.getName() + " module source path not found";

            Path gaPath = Path.of(sourcePath).getParent().resolve(".gitattributes");

            if (!gaPath.toFile().exists())
                return "File " + gaPath + " not found";

            try (BufferedReader reader = Files.newBufferedReader(gaPath, StringUtilsLabKey.DEFAULT_CHARSET))
            {
                handler.handle(gaPath, reader.lines());
            }

            handler.postHandle(gaPath);

            return null;
        }
    }

    private List<String> getMissingFiles(Path gaPath, Stream<String> filepaths)
    {
        File gaDirFile = gaPath.getParent().toFile();

        return filepaths
            .filter(line -> !line.isEmpty() && !line.startsWith("*") && !line.startsWith("#"))
            .filter(line -> {
                int idx = line.indexOf(' ');
                String filename = line.substring(0, idx);
                File file = FileUtil.appendPath(gaDirFile, org.labkey.api.util.Path.parse(filename));

                return !file.exists();
            })
            .collect(Collectors.toList());
    }

    public static class DeleteMissingFilesForm
    {
        private String _module;

        public String getModule()
        {
            return _module;
        }

        @SuppressWarnings("unused")
        public void setModule(String module)
        {
            _module = module;
        }
    }

    @RequiresPermission(AdminPermission.class)
    @SuppressWarnings("unused")
    public class DeleteMissingFilesAction extends FormHandlerAction<DeleteMissingFilesForm>
    {
        @Override
        public void validateCommand(DeleteMissingFilesForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(DeleteMissingFilesForm form, BindException errors) throws Exception
        {
            Set<String> missingFiles = new HashSet<>();

            String errorMessage = new GitAttributesParser().parse(form.getModule(), new GitAttributesHandler()
            {
                @Override
                public void handle(Path gaPath, Stream<String> stream)
                {
                    missingFiles.addAll(getMissingFiles(gaPath, stream));
                }
            });

            if (null != errorMessage)
                throw new NotFoundException(errorMessage);

            if (!missingFiles.isEmpty())
            {
                errorMessage = new GitAttributesParser().parse(form.getModule(), new GitAttributesHandler()
                {
                    @Override
                    public void handle(Path gaPath, Stream<String> stream)
                    {
                        try (PrintWriter output = PrintWriters.getPrintWriter(FileUtil.appendName(gaPath.getParent().toFile(), "gitattributes.temp")))
                        {
                            stream
                                .filter(o -> !missingFiles.contains(o))
                                .forEach(output::println);
                        }
                        catch (FileNotFoundException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    void postHandle(Path gaPath)
                    {
                        try
                        {
                            Files.delete(gaPath);
                            Files.move(gaPath.getParent().resolve("gitattributes.temp"), gaPath);
                        }
                        catch (IOException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }

            if (null != errorMessage)
                throw new NotFoundException(errorMessage);

            return true;
        }

        @Override
        public URLHelper getSuccessURL(DeleteMissingFilesForm form)
        {
            return new ActionURL(GitAttributesAction.class, getContainer());
        }
    }

    private static final Set<String> JSPS_TO_IGNORE = Set.of(
        "/org/labkey/testresults/view/menu.jsp"  // Invoked from some MacCoss JSPs via @include
    );

    @RequiresPermission(AdminPermission.class)
    @SuppressWarnings("unused")
    public class JspFinderAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspFinderView(ModuleLoader.getInstance().getModules());
        }

        private static class JspFinderView extends HttpView<Object>
        {
            private final Collection<Module> _modules;

            public JspFinderView(Collection<Module> modules)
            {
                _modules = modules;
            }

            @Override
            protected void renderInternal(Object model, PrintWriter out)
            {
                out.println("<pre>");

                Set<String> jspReferences = _modules.stream()
                    .flatMap(m->findJspReferences(m, out).stream())
                    .collect(Collectors.toCollection(TreeSet::new));

                out.println();
                out.println("JSP files that don't seem to be referenced in the code:");
                out.println();

                Set<String> jspFiles =_modules.stream()
                    .flatMap(m->findJspFiles(m, out).stream())
                    .collect(Collectors.toCollection(TreeSet::new));

                Set<String> copyOfJspFiles = new HashSet<>(jspFiles);

                jspFiles.removeAll(jspReferences);
                jspFiles.forEach(path->out.println(filter(path)));

                out.println();
                out.println("JSP references that couldn't be resolved to JSP files [plus any candidates for resolution]:");
                out.println();

                jspReferences.removeAll(copyOfJspFiles);
                jspReferences.forEach(path-> {
                    List<String> candidates = jspFiles.stream()
                        .filter(s -> s.endsWith(path))
                        .toList();

                    // If a JSP file is referenced twice (say, once with an absolute path and once with a relative path) then
                    // we might have already removed the candidate from jspFiles. If no match, check the full list of JSPs.
                    if (candidates.isEmpty())
                    {
                        candidates = copyOfJspFiles.stream()
                            .filter(s -> s.endsWith(path))
                            .toList();
                    }

                    out.println(filter(path + (candidates.isEmpty() ? "" : StringUtils.repeat(' ', Math.max(53 - path.length(), 0)) + " " + candidates)));
                    candidates.forEach(jspFiles::remove);
                });

                if (!jspFiles.isEmpty())
                {
                    out.println();
                    out.println("The following " + (jspFiles.size() == 1 ? "JSP file is a strong candidate" : jspFiles.size() + " JSP files are strong candidates") + " for removal:");
                    out.println();
                    jspFiles.forEach(path->out.println(filter(path)));
                }

                out.println("</pre>");
                out.flush();
            }

            private @Nullable Path getSourceRoot(Module m, PrintWriter out)
            {
                Path root = null;
                String sourcePath = m.getSourcePath();

                if (null == sourcePath)
                {
                    out.println(m.getName() + " module source path not found");
                }
                else
                {
                    root = Path.of(sourcePath);

                    if (!root.toFile().isDirectory())
                    {
                        out.println("Directory " + root + " not found");
                        root = null;
                    }
                }

                return root;
            }

            // TODO: warn for duplicates - suspicious
            private Collection<String> findJspReferences(Module module, PrintWriter out)
            {
                List<String> ret = new LinkedList<>();
                Path root = getSourceRoot(module, out);

                if (null != root)
                {
                    try
                    {
                        Files.walkFileTree(root, new SimpleFileVisitor<>()
                        {
                            @Override
                            public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs)
                            {
                                String filePath = file.toString();
                                if (filePath.endsWith(".java"))
                                {
                                    String code = PageFlowUtil.getFileContentsAsString(file.toFile());
                                    JavaScanner scanner = new JavaScanner(code);

                                    scanner.scan(0, new Handler(){
                                        @Override
                                        public boolean string(int beginIndex, int endIndex)
                                        {
                                            String s = endIndex - beginIndex > 6 && JavaScanner.TEXT_BLOCK_DELIMITER.equals(code.substring(beginIndex, beginIndex + 3)) ?
                                                code.substring(beginIndex + 3, endIndex - 3) :  // Strip text block delimiters
                                                code.substring(beginIndex + 1, endIndex - 1);   // Strip double quotes from standard string
                                            if (s.length() > 4 && s.contains("/") && s.endsWith(".jsp"))
                                                ret.add(s);
                                            return true;
                                        }
                                    });
                                }

                                return FileVisitResult.CONTINUE;
                            }
                        });
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }

                return ret;
            }

            private Collection<String> findJspFiles(Module module, PrintWriter out)
            {
                List<String> ret = new LinkedList<>();
                Path root = getSourceRoot(module, out);

                if (null != root)
                {
                    try
                    {
                        Files.walkFileTree(root, new SimpleFileVisitor<>()
                        {
                            @Override
                            public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs)
                            {
                                String filePath = file.toString().replaceAll("\\\\", "/");
                                if (filePath.endsWith(".jsp") && !JSPS_TO_IGNORE.contains(filePath))
                                {
                                    // Accommodates /org/labkey, /org/scharp, and /com/hphc
                                    int idx = StringUtils.indexOfAny(filePath, "/org/", "/com/");

                                    if (-1 != idx)
                                    {
                                        ret.add(filePath.substring(idx));
                                    }
                                    else
                                    {
                                        out.println(filter("Can't find \"/org/\" or \"/com/\": " + filePath));
                                    }
                                }

                                return FileVisitResult.CONTINUE;
                            }
                        });
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }

                return ret;
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addBeginNavTrail(root);
            root.addChild("JSP Finder");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CheckCrawlerActionsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws IOException
        {
            String sourcePath = ModuleLoader.getInstance().getModule(DevtoolsModule.NAME).getSourcePath();

            if (null == sourcePath)
            {
                errors.reject(ERROR_MSG, "Source path is null!");
                return new SimpleErrorView(errors);
            }

            List<ControllerActionId> actionIds = new LinkedList<>();

            // As of now, Crawler.java and the study tests are the only classes that specify crawler actions
            for (String path : List.of(
                sourcePath + "/../../clientModules/adjudication/test/src/org/labkey/test/tests/adjudication/AdjudicationAbstractBaseTest.java",
                sourcePath + "/../../ehrModules/ehr/test/src/org/labkey/test/tests/ehr/ComplianceTrainingTest.java",
                sourcePath + "/../../limsModules/biologics/test/src/org/labkey/test/tests/biologics/BiologicsReportTest.java",
                sourcePath + "/../study/test/src/org/labkey/test/tests/study",
                sourcePath + "/../../../testAutomation/src/org/labkey/test/stress/HarConverter.java",
                sourcePath + "/../../../testAutomation/src/org/labkey/test/util/Crawler.java"
            ))
            {
                File file = new File(path);
                if (!file.exists())
                {
                    errors.reject(ERROR_MSG, FileUtil.getAbsoluteCaseSensitiveFile(file).getAbsolutePath() + ": path not found!");
                    return new SimpleErrorView(errors);
                }
                addActionIds(actionIds, file);
            }

            Set<ControllerActionId> missingModuleActions = new TreeSet<>();
            Set<ControllerActionId> missingActions = new TreeSet<>();

            for (ControllerActionId actionId : actionIds)
            {
                Module module = ModuleLoader.getInstance().getModuleForController(actionId.getController().toLowerCase());

                if (null == module)
                {
                    missingModuleActions.add(actionId);
                }
                else
                {
                    SpringActionController controller = (SpringActionController) module.getController(null, actionId.getController());
                    if (null == controller || controller.resolveAction(actionId.getAction()) == null)
                    {
                        missingActions.add(actionId);
                    }
                }
            }

            HtmlStringBuilder builder = HtmlStringBuilder.of();

            if (!missingModuleActions.isEmpty())
            {
                builder
                    .append("The following " + (missingModuleActions.size() > 1 ? "actions' controllers" : "action's controller") + " could not be resolved to a module running in this deployment:")
                    .unsafeAppend("<br><br>\n");
                missingModuleActions.forEach(id->builder.append(id.toString()).unsafeAppend("<br>\n"));
                builder.unsafeAppend("<br>\n");
                builder.append("The associated module(s) might not support " + DbScope.getLabKeyScope().getDatabaseProductName() + ".");
                builder.unsafeAppend("<br><br>\n");
            }

            if (!missingActions.isEmpty())
            {
                builder
                    .append("The following " + (missingActions.size() > 1 ? "actions were" : "action was") + " not found in the action's controller:")
                    .unsafeAppend("<br><br>\n");
                missingActions.forEach(id->builder.append(id.toString()).unsafeAppend("<br>\n"));
            }

            return new HtmlView(builder);
        }

        private void addActionIds(List<ControllerActionId> actionIds, File file) throws IOException
        {
            if (file.isDirectory())
            {
                // Crawl all the files in this directory
                for (File f : file.listFiles(File::isFile))
                {
                    addActionIds(actionIds, f);
                }
            }
            else
            {
                Pattern pattern = Pattern.compile("ControllerActionId\\(\"(.+?)\", \"(.+?)\"\\)");
                Matcher matcher = pattern.matcher("");

                String line;

                try (BufferedReader br = Readers.getReader(file))
                {
                    while ((line = br.readLine()) != null)
                    {
                        matcher.reset(line);
                        if (matcher.find())
                            actionIds.add(new ControllerActionId(matcher.group(1), matcher.group(2)));
                    }
                }
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addBeginNavTrail(root);
            root.addChild("Check Crawler Actions");
        }

        private static class ControllerActionId implements Comparable<ControllerActionId>
        {
            private final String _controller;
            private final String _action;

            public ControllerActionId(String controller, String action)
            {
                _controller = controller;
                _action = action;
            }

            public String getController()
            {
                return _controller;
            }

            public String getAction()
            {
                return _action;
            }

            @Override
            public String toString()
            {
                return "/" + _controller + "-" + _action;
            }

            @Override
            public boolean equals(Object o)
            {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                ControllerActionId that = (ControllerActionId) o;
                return _controller.equals(that._controller) && _action.equals(that._action);
            }

            @Override
            public int hashCode()
            {
                return Objects.hash(_controller, _action);
            }

            @Override
            public int compareTo(@NotNull ControllerActionId o)
            {
                return toString().compareTo(o.toString());
            }
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class PostgreSqlOnlyModulesThatHaveSqlServerScriptsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            List<String> names = ModuleLoader.getInstance().getModules().stream()
                .filter(m -> !m.getSupportedDatabasesSet().contains(SupportedDatabase.mssql))
                .filter(m -> !StringUtils.isBlank(m.getSourcePath()))
                .filter(m -> new File(m.getSourcePath(), "resources/schemas/dbscripts/sqlserver").exists())
                .map(Module::getName)
                .toList();

            return new HtmlView(HtmlString.of(names.isEmpty() ? "None" : names.toString()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addBeginNavTrail(root);
            root.addChild("PostgreSQL-Only Modules That Still Have SQL Server Scripts");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class OverlappingIndicesAction extends AbstractOverlappingIndicesAction
    {
        @Override
        public ModelAndView getView(Object o, boolean reshow, BindException errors)
        {
            MultiValuedMap<OverlapType, Overlap> multiMap = getOverlappingIndices();

            return new VBox(
                new HtmlView(DOM.createHtmlFragment(
                    Arrays.stream(OverlapType.values()).flatMap(type ->
                        Stream.of(
                            type != OverlapType.UniqueOverlappingNonUnique ? BR() : null,
                            DOM.STRONG(StringUtilsLabKey.pluralize(multiMap.get(type).size(), "index has ", "indices have ") + type.getDescription() + ":", BR()),
                            DOM.TABLE(
                                multiMap.get(type).stream()
                                    .map(overlap -> DOM.TR(
                                        DOM.TD(at(style, "width:120px;"), overlap.schemaName()),
                                        DOM.TD(type.getMessage(overlap)),
                                        "\n"
                                    ))
                            )
                        )
                    )
                )),
                new HtmlView(DOM.createHtmlFragment(
                    BR(),
                    new ButtonBuilder("Create SQL Scripts That Drop Overlapping Indices").href(OverlappingIndicesAction.class, getContainer()).usePost())
                )
            );
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addBeginNavTrail(root);
            root.addChild("Overlapping Indices");
        }

        @Override
        public boolean handlePost(Object o, BindException errors)
        {
            MultiValuedMap<OverlapType, Overlap> multiMap = getOverlappingIndices();

            try
            {
                Arrays.stream(OverlapType.values()).forEach(type -> multiMap.get(type).forEach(overlap -> {
                    try
                    {
                        // All writers are closed below
                        WriterContext context = getWriterContext(overlap.schemaName());
                        if (type.writeScript(context.getWriter(), overlap))
                            context.setModified();
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }));
            }
            finally
            {
                closeAllContexts();
            }

            return true;
        }

        private static class WriterContext
        {
            private final File _scriptDirectory;
            private final String _filename;
            private final File _scriptFile;
            private final Writer _writer;

            private boolean _modified = false;

            public WriterContext(File scriptDirectory, String filename) throws IOException
            {
                _scriptDirectory = scriptDirectory;
                _filename = filename;
                // Create script file
                _scriptFile = FileUtil.appendName(scriptDirectory, filename);
                FileUtil.createNewFile(_scriptFile);
                _writer = new BufferedWriter(new FileWriter(_scriptFile, StringUtilsLabKey.DEFAULT_CHARSET));
            }

            public Writer getWriter()
            {
                return _writer;
            }

            public void setModified()
            {
                _modified = true;
            }

            public void close() throws IOException
            {
                _writer.close();

                if (_modified)
                {
                    // Add to VCS
                    Vcs vcs = VcsService.get().getVcs(_scriptDirectory);
                    if (null != vcs)
                        vcs.addFile(_filename);
                }
                else
                {
                    _scriptFile.delete();
                }
            }
        }

        private final Map<String, WriterContext> _writerContextMap = new HashMap<>();

        private WriterContext getWriterContext(String schemaName) throws IOException
        {
            return _writerContextMap.computeIfAbsent(schemaName, n -> {

                DbSchema schema = DbSchema.get(schemaName, DbSchemaType.Module);
                Module module = schema.getModule();
                Double schemaVersion = module.getSchemaVersion();
                if (schemaVersion == null)
                    throw new IllegalStateException("Schema version was null for " + module.getName());

                FileSqlScriptProvider provider = new FileSqlScriptProvider(module);
                SqlDialect dialect = DbScope.getLabKeyScope().getSqlDialect();
                File scriptDirectory = provider.getScriptDirectory(dialect);
                if (scriptDirectory == null)
                    throw new IllegalStateException("No scripts path found for " + module.getName());

                String filename = getScriptFilename(schemaName, schemaVersion);

                try
                {
                    return new WriterContext(scriptDirectory, filename);
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            });
        }

        private String getScriptFilename(String schemaName, double startVersion)
        {
            return schemaName + "-" + Formats.f3.format(startVersion) + "-" + Formats.f3.format(startVersion + 0.001) + ".sql";
        }

        private void closeAllContexts()
        {
            _writerContextMap.values().forEach(context -> {
                try
                {
                    context.close();
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            });
        }

        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return new ActionURL(BeginAction.class, getContainer());
        }
    }

    protected static abstract class AbstractOverlappingIndicesAction extends FormViewAction<Object>
    {
        protected MultiValuedMap<OverlapType, Overlap> getOverlappingIndices()
        {
            MultiValuedMap<OverlapType, Overlap> multiMap = new ArrayListValuedHashMap<>();
            DbScope scope = DbScope.getLabKeyScope();

            ModuleLoader.getInstance().getModules().stream()
                .flatMap(module -> module.getSchemaNames().stream().filter(name -> !module.getProvisionedSchemaNames().contains(name)))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> scope.getSchema(name, DbSchemaType.Module))
                .flatMap(schema -> schema.getTableNames().stream().map(schema::getTable))
                .forEach(table -> {
                    var indices = table.getAllIndices();
                    indices.forEach(indexDef1 -> indices.forEach(indexDef2 -> {
                        if (indexDef1 != indexDef2)
                        {
                            OverlapType type = overlap(indexDef1, indexDef2);

                            if (type != null)
                            {
                                if (type != OverlapType.Identical || !alreadySeen(indexDef1.name(), indexDef2.name()))
                                    multiMap.put(type, new Overlap(table.getSchema().getName(), table.getName(), indexDef1, indexDef2));
                            }
                        }
                    }));
                });

            return multiMap;
        }

        private final Set<String> _alreadySeen = new HashSet<>();

        // Keep track of the identical indexes we've seen so we don't repeat them for both directions
        private boolean alreadySeen(String name1, String name2)
        {
            String key = name1.compareTo(name2) < 0 ? name1 + delim + name2 : name2 + delim + name1;
            return !_alreadySeen.add(key);
        }

        private @Nullable OverlapType overlap(IndexDefinition index1, IndexDefinition index2)
        {
            String key1 = getKey(index1.columns());
            String key2 = getKey(index2.columns());
            boolean sameFilterConditions = Objects.equals(index1.filterCondition(), index2.filterCondition());
            if (key1.equals(key2))
                return sameFilterConditions ? OverlapType.Identical : OverlapType.OverlappingWithDifferentFilter;
            if (key2.startsWith(key1))
            {
                if (index2.indexType() == IndexType.NonUnique && (index1.indexType() == IndexType.Primary || index1.indexType() == IndexType.Unique))
                    return OverlapType.UniqueOverlappingNonUnique;
                else
                    return sameFilterConditions ? OverlapType.Overlapping : OverlapType.OverlappingWithDifferentFilter;
            }
            return null;
        }

        private final String delim = Character.toString(31); // Non-printing character that's very unlikely to be in a column name

        private String getKey(List<ColumnInfo> cols)
        {
            return cols.stream()
                .map(col -> col.getName().toLowerCase())
                .collect(Collectors.joining(delim)) + delim;
        }

        private List<String> join(List<ColumnInfo> cols)
        {
            return cols.stream()
                .map(ColumnInfo::getName)
                .toList();
        }
    }

    protected record Overlap(String schemaName, String tableName, IndexDefinition indexDef1, IndexDefinition indexDef2) {}

    protected enum OverlapType
    {
        UniqueOverlappingNonUnique("a column list that overlaps another index's column list at the start, but the first index is a unique constraint. These are likely valid")
        {
            @Override
            boolean writeScript(Writer writer, Overlap overlap)
            {
                return false; // Write nothing
            }
        },
        OverlappingWithDifferentFilter("a column list that overlaps another index's column list at the start, but with different filter conditions. These are likely valid")
        {
            @Override
            boolean writeScript(Writer writer, Overlap overlap)
            {
                return false; // Write nothing
            }
        },
        Identical("a column list that's identical to another index's column list")
        {
            @Override
            boolean writeScript(Writer writer, Overlap overlap) throws IOException
            {
                IndexType type1 = overlap.indexDef1.indexType();
                IndexType type2 = overlap.indexDef2.indexType();
                String dropIndex = null;
                String otherIndex = null;

                // Prefer to drop the non-PK, then prefer the non-unique, otherwise "drop" them both (let the human decide)
                if (type1 == IndexType.Primary)
                {
                    dropIndex = overlap.indexDef2.name();
                    otherIndex = overlap.indexDef1.name();
                }
                else if (type2 == IndexType.Primary)
                {
                    dropIndex = overlap.indexDef1.name();
                    otherIndex = overlap.indexDef2.name();
                }
                else if (type1 == IndexType.Unique && type2 == IndexType.NonUnique)
                {
                    dropIndex = overlap.indexDef2.name();
                    otherIndex = overlap.indexDef1.name();
                }
                else if (type2 == IndexType.Unique && type1 == IndexType.NonUnique)
                {
                    dropIndex = overlap.indexDef1.name();
                    otherIndex = overlap.indexDef2.name();
                }

                if (dropIndex != null)
                {
                    dropIndex(writer, overlap.schemaName, overlap.tableName, dropIndex, otherIndex);
                }
                else
                {
                    writer.write("TODO: Human, please help!! You should drop only one of the following, but I couldn't decide which one:\n");
                    dropIndex(writer, overlap.schemaName, overlap.tableName, overlap.indexDef1.name(), overlap.indexDef2.name());
                    dropIndex(writer, overlap.schemaName, overlap.tableName, overlap.indexDef2.name(), overlap.indexDef1.name());
                    writer.write('\n');
                }

                return true;
            }

            @Override
            String getMessage(Overlap overlap)
            {
                return overlap.indexDef1.name() + " vs. " + overlap.indexDef2.name() + ": " + join(overlap.indexDef1.columns());
            }
        },
        Overlapping("a column list that overlaps another index's column list at the start")
        {
            @Override
            boolean writeScript(Writer writer, Overlap overlap) throws IOException
            {
                dropIndex(writer, overlap.schemaName, overlap.tableName, overlap.indexDef1.name(), overlap.indexDef2.name());
                return true;
            }
        };

        private final String _description;

        OverlapType(String description)
        {
            _description = description;
        }

        public String getDescription()
        {
            return _description;
        }

        // Return true if content has been written to the script file
        abstract boolean writeScript(Writer writer, Overlap overlap) throws IOException;

        String getMessage(Overlap overlap)
        {
            return overlap.indexDef1.name() + " " + join(overlap.indexDef1.columns()) + " vs. " + overlap.indexDef2.name() + " " + join(overlap.indexDef2.columns());
        }

        protected List<String> join(List<ColumnInfo> cols)
        {
            return cols.stream()
                .map(ColumnInfo::getName)
                .toList();
        }

        protected void dropIndex(Writer writer, String schemaName, String tableName, String dropIndex, String otherIndex) throws IOException
        {
            SqlDialect dialect = DbScope.getLabKeyScope().getSqlDialect();
            writer.write("-- This index overlaps with " + otherIndex + "\n");

            if (dialect.isPostgreSQL())
                writer.write("DROP INDEX " + schemaName + "." + dropIndex + ";\n");
            else
                writer.write("DROP INDEX " + dropIndex + " ON " + schemaName + "." + tableName + ";\n");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ForeignKeysAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            DbScope scope = DbScope.getLabKeyScope();
            MultiValuedMap<TableInfo, ColumnInfo> map = scope.getSchemaNames().stream()
                .map(name -> scope.getSchema(name, DbSchemaType.Bare))
                .flatMap(schema -> schema.getTableNames().stream().map(schema::getTable))
                .flatMap(table -> {
                    try
                    {
                        // We're querying the metadata directly (not using cached FK information) because we want to
                        // capture every FK in the database (not just those owned by the currently deployed modules) and
                        // we want to ignore "virtual" FKs.
                        return BaseColumnInfo.createFromDatabaseMetaData(table.getSchema().getName(), (SchemaTableInfo) table, null).stream();
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeException(e);
                    }
                })
                .filter(col -> col.getFk() != null)
                .collect(LabKeyCollectors.toMultiValuedMap(
                    BaseColumnInfo::getFkTableInfo,
                    col -> col,
                    () -> new ArrayListValuedTreeMap<>(Comparator.comparing(TableInfo::getSelectName))
                ));

            HtmlString delim = HtmlStringBuilder.of(HtmlString.BR).append("\n").append(HtmlString.NBSP).append(HtmlString.NBSP).getHtmlString();
            HtmlStringBuilder builder = HtmlStringBuilder.of();
            map.asMap().forEach((targetTable, columns) -> builder.append(targetTable.getSchema().getName() + "." + targetTable.getName() + "\n")
                .append(delim)
                .append(columns.stream().map(column -> {
                    TableInfo sourceTable = column.getParentTable();
                    return HtmlString.of(sourceTable.getSchema().getName() + "." + sourceTable.getName() + "." + column.getName() + "\n");
                }).collect(LabKeyCollectors.joining(delim)))
                .append(HtmlString.BR)
                .append(HtmlString.BR)
            );


            return new VBox(
                new HtmlView(DOM.createHtmlFragment(
                    DIV(at(style, "width: 1200px;"), """
                        A simple report that shows the incoming foreign keys that target each table in the database. This report is most useful
                        when attempting to optimize the performance of deletes from a particular target table (and potentially updates to its
                        PK, though that's not a common operation). Note that all tables and foreign keys in the database are shown here since
                        they all can affect performance, regardless of whether their owning modules are deployed currently. This report will
                        be improved in the future by adding index information.
                        """),
                    BR()
                )),
                new HtmlView(builder)
            );
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addBeginNavTrail(root);
            root.addChild("Foreign Keys");
        }
    }
}
