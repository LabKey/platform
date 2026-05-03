package org.labkey.devtools;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedLinkedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.ArrayListValuedTreeMap;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfo.IndexDefinition;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SupportedDatabase;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.BaseScanner.Handler;
import org.labkey.api.util.ButtonBuilder;
import org.labkey.api.util.DOM;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.LinkBuilder;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.labkey.api.data.TableInfo.IndexType.NonUnique;
import static org.labkey.api.data.TableInfo.IndexType.Primary;
import static org.labkey.api.data.TableInfo.IndexType.Unique;
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
            return new ActionListView(ToolsController.this, actionDescriptor -> BeginAction.class != actionDescriptor.getActionClass());
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
                        missing.forEach(filename -> out.println(filter(filename)));
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
                jspFiles.forEach(path -> out.println(filter(path)));

                out.println();
                out.println("JSP references that couldn't be resolved to JSP files [plus any candidates for resolution]:");
                out.println();

                jspReferences.removeAll(copyOfJspFiles);
                jspReferences.forEach(path -> {
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
                    jspFiles.forEach(path -> out.println(filter(path)));
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

                                    scanner.scan(0, new Handler()
                                    {
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

            // As of now, these are the only classes that specify crawler actions
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
                missingModuleActions.forEach(id -> builder.append(id.toString()).unsafeAppend("<br>\n"));
                builder.unsafeAppend("<br>\n");
                builder.append("The associated module(s) might not support " + DbScope.getLabKeyScope().getDatabaseProductName() + ".");
                builder.unsafeAppend("<br><br>\n");
            }

            if (!missingActions.isEmpty())
            {
                builder
                    .append("The following " + (missingActions.size() > 1 ? "actions were" : "action was") + " not found in the action's controller:")
                    .unsafeAppend("<br><br>\n");
                missingActions.forEach(id -> builder.append(id.toString()).unsafeAppend("<br>\n"));
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

    public record OverlappingIndicesForm(String schemaName, Boolean clearCaches) {}
    public record IndexChange(TableInfo table, IndexDefinition index, ChangeType type, String description) {}
    public record IndexOverlap(TableInfo table, String description) {}

    @RequiresPermission(AdminPermission.class)
    public class OverlappingIndicesAction extends FormViewAction<OverlappingIndicesForm>
    {
        @Override
        public ModelAndView getView(OverlappingIndicesForm form, boolean reshow, BindException errors)
        {
            ActionURL url = getViewContext().getActionURL().clone();

            if (Boolean.TRUE.equals(form.clearCaches()))
            {
                CacheManager.clearAllKnownCaches();
                url.deleteParameter("clearCaches");
            }

            OverlappingIndicesAnalyzer analyzer = new OverlappingIndicesAnalyzer();
            MultiValuedMap<String, IndexOverlap> allOverlaps = analyzer.getOverlaps(form.schemaName());
            MultiValuedMap<String, IndexChange> changes = analyzer.getChanges(form.schemaName());

            return new VBox(
                new HtmlView(DOM.createHtmlFragment(
                    DOM.H3("List of all overlapping indices"),
                    "Some overlapping indices are expected and legitimate, typically because a non-unique index has " +
                        "a longer column list than a unique index or primary key, a unique index has a shorter (more " +
                        "restrictive) column list than the primary key, or the indices have different filter conditions.",
                    BR(),
                    allOverlaps.keySet().stream().flatMap(schemaName ->
                        Stream.of(
                            BR(),
                            DOM.STRONG("Schema ", LinkBuilder.simpleLink(schemaName, new ActionURL(OverlappingIndicesAction.class, getContainer()).addParameter("schemaName", schemaName)), ":", BR()),
                            DOM.TABLE(
                                allOverlaps.get(schemaName).stream()
                                    .sorted(Comparator.comparing(change -> change.table().getName()))
                                    .map(overlap -> DOM.TR(
                                    DOM.TD(at(style, "width:200px;"), overlap.table().getName()),
                                    DOM.TD(overlap.description()),
                                    "\n"
                                ))
                            )
                        )
                    )
                )),
                new HtmlView(DOM.createHtmlFragment(
                    BR(),
                    DOM.H3("Total number of changes needed: " + changes.keys().size()),
                    BR(),
                    changes.keySet().stream().flatMap(schemaName ->
                        Stream.of(
                            BR(),
                            DOM.STRONG("Schema ", LinkBuilder.simpleLink(schemaName, new ActionURL(OverlappingIndicesAction.class, getContainer()).addParameter("schemaName", schemaName)), " needs " + StringUtilsLabKey.pluralize(changes.get(schemaName).size(), "change") + ":", BR()),
                            DOM.TABLE(
                                changes.get(schemaName).stream()
                                    .sorted(Comparator.comparing(change -> change.table().getName()))
                                    .map(change -> DOM.TR(
                                        DOM.TD(at(style, "width:200px;"), change.table().getName()),
                                        DOM.TD(change.description()),
                                        "\n"
                                    ))
                            )
                        )
                    )
                )),
                new HtmlView(DOM.createHtmlFragment(
                    BR(),
                    changes.isEmpty() ? null : new ButtonBuilder("Create SQL Scripts That Drop Redundant Indices").href(url).usePost(),
                    "  ",
                    new ButtonBuilder("Clear Caches and Refresh").href(url.addParameter("clearCaches", true))
                ))
            );
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addBeginNavTrail(root);
            root.addChild("Overlapping Indices");
        }

        @Override
        public boolean handlePost(OverlappingIndicesForm form, BindException errors)
        {
            MultiValuedMap<String, IndexChange> multiMap = new OverlappingIndicesAnalyzer().getChanges(form.schemaName());

            try
            {
                multiMap.keySet()
                    .forEach(schemaName -> multiMap.get(schemaName).forEach(change -> {
                        try
                        {
                            // All writers are closed below in the finally
                            WriterContext context = getWriterContext(schemaName);
                            change.type().writeScript(context.getWriter(), change);
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

        @Override
        public void validateCommand(OverlappingIndicesForm form, Errors errors)
        {
        }

        @Override
        public URLHelper getSuccessURL(OverlappingIndicesForm form)
        {
            return new ActionURL(OverlappingIndicesAction.class, getContainer());
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
            return _writerContextMap.computeIfAbsent(schemaName, _ -> {

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
    }

    private static class OverlappingIndicesAnalyzer
    {
        private final String delim = Character.toString(31); // Non-printing character that's very unlikely to be in a column name

        private void enumerateTables(@Nullable String schemaName, Consumer<TableInfo> tableConsumer)
        {
            ModuleLoader.getInstance().getModules().stream()
                .flatMap(module -> module.getSchemaNames().stream().filter(name -> !module.getProvisionedSchemaNames().contains(name)))
                .filter(name -> schemaName == null || name.equalsIgnoreCase(schemaName))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> DbScope.getLabKeyScope().getSchema(name, DbSchemaType.Module))
                .flatMap(schema -> schema.getTableNames().stream().map(schema::getTable))
                .forEach(tableConsumer);
        }

        private MultiValuedMap<String, IndexChange> getChanges(@Nullable String schemaName)
        {
            MultiValuedMap<String, IndexChange> multiMap = new ArrayListValuedLinkedHashMap<>();
            enumerateTables(schemaName, table ->
                analyzeTable(table, new LinkedHashSet<>(table.getAllIndices()))
                    .forEach(change -> multiMap.put(change.table().getSchema().getName(), change)));
            return multiMap;
        }

        // Package-visible for testing. Pass null for table only in unit tests that don't inspect change.table().
        List<IndexChange> analyzeTable(@Nullable TableInfo table, LinkedHashSet<IndexDefinition> indices)
        {
            var changes = new LinkedList<IndexChange>();
            Set<IndexDefinition> droppedIndices = new HashSet<>();

            // Step #1: Find the PK (if present), and drop non-unique indices whose columns are a prefix of the
            // PK, plus unique indices that cover the exact same column set as the PK. A unique index with FEWER
            // columns than the PK enforces a strictly stronger uniqueness guarantee (the PK cannot replace it),
            // so it is left for Step #2 to evaluate.
            indices.stream()
                .filter(ix -> ix.indexType() == Primary)
                .findFirst()
                .ifPresent(pk -> indices.stream()
                    .filter(index -> isOverlap(pk, index))
                    .filter(index -> index.indexType() != Unique || index.columns().size() == pk.columns().size())
                    .forEach(index -> {
                        changes.add(new IndexChange(table, index, ChangeType.Drop, getDropDescription(index, pk)));
                        droppedIndices.add(index);
                    })
                );

            Set<IndexDefinition> convertedUniqueIndices = new HashSet<>();

            // Step #2: For each unique index, switch it to a non-unique index if there's any UQ or PK that
            // overlaps with a smaller or equal column set.
            streamIndices(indices, droppedIndices)
                .filter(index -> index.indexType() == Unique)
                .forEach(uq -> streamIndices(indices, droppedIndices)
                    .filter(index -> index.indexType() == Primary || index.indexType() == Unique)
                    .filter(index -> !convertedUniqueIndices.contains(index))
                    .filter(index -> isOverlap(uq, index))
                    .findFirst()
                    .ifPresent(index -> {
                        changes.add(new IndexChange(table, uq, ChangeType.Convert, String.format("Converting %s from unique to non-unique index because %s overlaps it with a smaller column set", uq.display(), index.display())));
                        convertedUniqueIndices.add(uq);
                    })
                );

            // Step #3: For each index (unique or non-unique), delete all other non-unique indices that overlap
            // with a smaller or equal column set.
            streamIndices(indices, droppedIndices)
                .filter(index -> index.indexType() != Primary)
                .forEach(index -> streamIndices(indices, droppedIndices)
                    .filter(ix -> ix.indexType() == NonUnique || convertedUniqueIndices.contains(ix))
                    .filter(ix -> isOverlap(index, ix))
                    .forEach(ix -> {
                        String description = getDropDescription(ix, index);
                        if (convertedUniqueIndices.contains(ix))
                        {
                            // Index was converted to non-unique but now needs to be dropped. Adjust changes, description, etc.
                            IndexChange convert = changes.stream()
                                .filter(change -> change.index().equals(ix))
                                .findFirst()
                                .orElseThrow();
                            description = description + (!description.endsWith(".") ? "." : "") + " Prior to this drop, the index was converted: " + convert.description();
                            changes.remove(convert);
                            convertedUniqueIndices.remove(ix);
                        }
                        changes.add(new IndexChange(table, ix, ChangeType.Drop, description));
                        droppedIndices.add(ix);
                    })
                );

            return changes;
        }

        private MultiValuedMap<String, IndexOverlap> getOverlaps(@Nullable String schemaName)
        {
            MultiValuedMap<String, IndexOverlap> multiMap = new ArrayListValuedLinkedHashMap<>();

            enumerateTables(schemaName, table -> {
                var indices = table.getAllIndices();
                indices.forEach(index1 -> indices.stream()
                    .filter(index2 -> isSimpleOverlap(index1, index2))
                    .forEach(index2 -> multiMap.put(table.getSchema().getName(), new IndexOverlap(table, String.format("%s overlaps with %s", index2.display(), index1.display()))))
                );
            });

            return multiMap;
        }

        // Helper that filters out the dropped indices
        private Stream<IndexDefinition> streamIndices(Set<IndexDefinition> indices, Set<IndexDefinition> droppedIndices)
        {
            return indices.stream().filter(index -> !droppedIndices.contains(index));
        }

        private String getDropDescription(IndexDefinition dropIndex, IndexDefinition otherIndex)
        {
            String warning = dropIndex.indexType() == otherIndex.indexType() && dropIndex.columns().size() == otherIndex.columns().size() ?
                ". Note: You may want to drop " + otherIndex.display() + " instead." : "";
            return String.format("Dropping %s because it overlaps with %s", dropIndex.display(), otherIndex.display()) + warning;
        }

        // Returns true if index2 has an overlapping column set that's equal to or smaller than index1's
        private boolean isSimpleOverlap(IndexDefinition index1, IndexDefinition index2)
        {
            boolean ret = false;

            if (!index1.equals(index2))
            {
                String key1 = getKey(index1.columns());
                String key2 = getKey(index2.columns());
                ret = key1.startsWith(key2);
            }

            return ret;
        }

        // Returns true if index2 overlaps index1, and they have the same filter condition
        private boolean isOverlap(IndexDefinition index1, IndexDefinition index2)
        {
            return Objects.equals(index1.filterCondition(), index2.filterCondition()) && isSimpleOverlap(index1, index2);
        }

        private String getKey(List<ColumnInfo> cols)
        {
            return cols.stream()
                .map(col -> col.getName().toLowerCase())
                .collect(Collectors.joining(delim)) + delim;
        }
    }

    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        @Test
        public void testOverlappingIndices()
        {
            Assume.assumeTrue("Skipping because this server is not running on PostgreSQL", DbScope.getLabKeyScope().getSqlDialect().isPostgreSQL());
            var map = new OverlappingIndicesAnalyzer().getChanges(null);
            var keys = map.keySet();
            if (!keys.isEmpty())
                fail(StringUtilsLabKey.pluralize(keys.size(), "redundant index", "redundant indices") + " detected: " + map);
        }

        // Helper to build an IndexDefinition with named columns in order
        private static IndexDefinition idx(String name, TableInfo.IndexType type, String... columnNames)
        {
            var cols = Arrays.stream(columnNames)
                .map(n -> (ColumnInfo) new BaseColumnInfo(n, JdbcType.VARCHAR))
                .collect(Collectors.toCollection(ArrayList::new));
            return new IndexDefinition(name, type, cols, null);
        }

        private static List<IndexChange> analyze(IndexDefinition... indexDefs)
        {
            return new OverlappingIndicesAnalyzer().analyzeTable(null, new LinkedHashSet<>(Arrays.asList(indexDefs)));
        }

        @Test
        public void testNonUniqueIndexIsRedundantWithPk()
        {
            // A non-unique index on (A) is redundant when the PK is on (A, B): the PK B-tree satisfies
            // all the same prefix queries. This should be dropped.
            var pk = idx("pk_ab", Primary, "A", "B");
            var ix = idx("ix_a", NonUnique, "A");

            var changes = analyze(pk, ix);

            boolean ixDropped = changes.stream().anyMatch(c -> c.index().equals(ix) && c.type() == ChangeType.Drop);
            assertTrue("ix_a (non-unique on A) should be dropped when PK is on (A, B)", ixDropped);
        }

        @Test
        public void testIdenticalUniqueIndexIsRedundantWithPk()
        {
            // A unique index on exactly the same columns as the PK is a true duplicate: the PK already
            // enforces the same uniqueness guarantee, so the separate index should be removed.
            var pk = idx("pk_ab", Primary, "A", "B");
            var uq = idx("uq_ab", Unique, "A", "B");

            var changes = analyze(pk, uq);

            boolean uqActedOn = changes.stream()
                .anyMatch(c -> c.index().equals(uq) && (c.type() == ChangeType.Drop || c.type() == ChangeType.Convert));
            assertTrue("uq_ab (unique on A, B) should be dropped or converted when PK is also on (A, B)", uqActedOn);
        }

        @Test
        public void testUniqueNotDroppedByLongerPk()
        {
            // PK(A,B,C) allows rows (A=1,B=1,C=1) and (A=1,B=1,C=2); Unique(A,B) does not.
            // Dropping it would silently relax the uniqueness guarantee.
            var pk = idx("pk_abc", Primary, "A", "B", "C");
            var uq = idx("uq_ab", Unique, "A", "B");

            var changes = analyze(pk, uq);

            boolean uqDropped = changes.stream().anyMatch(c -> c.index().equals(uq) && c.type() == ChangeType.Drop);
            assertFalse("uq_ab (unique on A,B) must not be dropped when pk is on (A,B,C). Changes: " + changes, uqDropped);
        }

        @Test
        public void testStep2UniqueConvertedWhenPrefixUniqueExists()
        {
            // If Unique(A) exists, the (A,B) pair is already guaranteed unique by the A constraint alone,
            // so Unique(A,B) provides no additional uniqueness and should be converted to non-unique.
            // The narrower Unique(A) must not be touched.
            var uqA = idx("uq_a", Unique, "A");
            var uqAB = idx("uq_ab", Unique, "A", "B");

            var changes = analyze(uqA, uqAB);

            boolean uqABConverted = changes.stream().anyMatch(c -> c.index().equals(uqAB) && c.type() == ChangeType.Convert);
            boolean uqAConverted = changes.stream().anyMatch(c -> c.index().equals(uqA) && c.type() == ChangeType.Convert);
            assertTrue("uq_ab (unique on A,B) should be converted when uq_a (unique on A) exists. Changes: " + changes, uqABConverted);
            assertFalse("uq_a (unique on A) should not be converted. Changes: " + changes, uqAConverted);
        }

        @Test
        public void testStep3NonUniqueDroppedByWiderNonUnique()
        {
            // A B-tree index on (A,B,C) can serve all prefix queries on (A,B), making a separate
            // NonUnique(A,B) index redundant. The narrower one should be dropped; the wider one kept.
            var ixABC = idx("ix_abc", NonUnique, "A", "B", "C");
            var ixAB = idx("ix_ab", NonUnique, "A", "B");

            var changes = analyze(ixABC, ixAB);

            boolean ixABDropped = changes.stream().anyMatch(c -> c.index().equals(ixAB) && c.type() == ChangeType.Drop);
            boolean ixABCDropped = changes.stream().anyMatch(c -> c.index().equals(ixABC) && c.type() == ChangeType.Drop);
            assertTrue("ix_ab (non-unique on A,B) should be dropped when ix_abc (non-unique on A,B,C) exists. Changes: " + changes, ixABDropped);
            assertFalse("ix_abc (non-unique on A,B,C) should not be dropped. Changes: " + changes, ixABCDropped);
        }

        @Test
        public void testStep2And3ConvertThenDrop()
        {
            // Step 2 converts Unique(A,B) because Unique(A) makes its uniqueness redundant.
            // Step 3 then drops the (now non-unique) Unique(A,B) because NonUnique(A,B,C) covers it.
            // The final changes list must show a single Drop for uq_ab — no separate Convert entry.
            var uqA = idx("uq_a", Unique, "A");
            var uqAB = idx("uq_ab", Unique, "A", "B");
            var ixABC = idx("ix_abc", NonUnique, "A", "B", "C");

            var changes = analyze(uqA, uqAB, ixABC);

            boolean uqABDropped = changes.stream().anyMatch(c -> c.index().equals(uqAB) && c.type() == ChangeType.Drop);
            boolean uqABConverted = changes.stream().anyMatch(c -> c.index().equals(uqAB) && c.type() == ChangeType.Convert);
            assertTrue("uq_ab should appear as Drop (convert folded in). Changes: " + changes, uqABDropped);
            assertFalse("uq_ab should not have a separate Convert entry. Changes: " + changes, uqABConverted);
        }

        @Test
        public void testNoChangesForDisjointIndices()
        {
            // Indices on completely different columns have no prefix relationship; nothing should change.
            var ixA = idx("ix_a", NonUnique, "A");
            var ixB = idx("ix_b", NonUnique, "B");
            var uqC = idx("uq_c", Unique, "C");

            var changes = analyze(ixA, ixB, uqC);

            assertTrue("Disjoint indices should produce no changes. Changes: " + changes, changes.isEmpty());
        }

        @Test
        public void testFilteredIndexNotDroppedByFullIndex()
        {
            // A partial (filtered) index covers only a subset of rows. Even when its column set is a
            // prefix of the PK, the filter condition means the two indices are not interchangeable.
            var pk = idx("pk_ab", Primary, "A", "B");
            var cols = Arrays.stream(new String[]{"A"})
                .map(n -> (ColumnInfo) new BaseColumnInfo(n, JdbcType.VARCHAR))
                .collect(Collectors.toCollection(ArrayList::new));
            var filteredIx = new IndexDefinition("ix_a_partial", NonUnique, cols, "active = 1");

            var changes = analyze(pk, filteredIx);

            boolean filteredDropped = changes.stream().anyMatch(c -> c.index().equals(filteredIx) && c.type() == ChangeType.Drop);
            assertFalse(
                "ix_a_partial (filtered non-unique on A) must not be dropped by pk_ab: different filter conditions. Changes: " + changes,
                filteredDropped);
        }
    }

    private enum ChangeType
    {
        Drop
        {
            @Override
            void writeScript(Writer writer, IndexChange change, String schemaName, String tableName, IndexDefinition dropIndex) throws IOException
            {
                writer.write("-- " + change.description() + "\n");

                if (DbScope.getLabKeyScope().getSqlDialect().isPostgreSQL())
                {
                    if (dropIndex.indexType() == Unique)
                    {
                        String constraintName = getConstraintForIndex(schemaName, dropIndex.name());
                        if (constraintName != null)
                        {
                            writer.write("ALTER TABLE " + schemaName + "." + tableName + " DROP CONSTRAINT " + constraintName + ";\n");
                            return;
                        }
                    }

                    writer.write("DROP INDEX " + schemaName + "." + dropIndex.name() + ";\n");
                }
                else
                    writer.write("DROP INDEX " + dropIndex.name() + " ON " + schemaName + "." + tableName + ";\n");
            }
        },
        Convert
        {
            @Override
            void writeScript(Writer writer, IndexChange change, String schemaName, String tableName, IndexDefinition changeIndex) throws IOException
            {
                Drop.writeScript(writer, change);
                String indexName = changeIndex.name().replaceFirst("^uq", "ix").replaceFirst("^unique", "index");
                writer.write("CREATE INDEX " + indexName + " ON " + schemaName + "." + tableName + "(" + changeIndex.columns().stream().map(ColumnInfo::getName).collect(Collectors.joining(", ")) + ");\n");
            }
        };

        final void writeScript(Writer writer, IndexChange change) throws IOException
        {
            TableInfo table = change.table();
            IndexDefinition index = change.index();

            if (index.indexType() == Primary)
                throw new IllegalStateException("Should not modify a PK! (" + change + ")");

            writeScript(writer, change, table.getSchema().getName(), table.getName(), index);
        }

        abstract void writeScript(Writer writer, IndexChange change, String schemaName, String tableName, IndexDefinition index) throws IOException;
    }

    private record IndexKey(String schemaName, String indexName) {}

    // If this is a unique index associated with a constraint, return that constraint name. Otherwise, return null.
    // Most, but not all, unique indices are created by adding a unique constraint; in those cases, we need to drop
    // the associated constraint. However, for explicitly created unique indices, we need to drop the index instead.
    private static @Nullable String getConstraintForIndex(String schemaName, String indexName)
    {
        Cache<String, Map<IndexKey, String>> sharedCache = CacheManager.getSharedCache();
        var constraintMap = sharedCache.get(OverlappingIndicesAction.class.getName() + "/ConstraintForIndexMap", null, (_, _) -> Collections.unmodifiableMap(
            new SqlSelector(DbScope.getLabKeyScope(), new SQLFragment("""
                SELECT NspName AS SchemaName, RelName AS IndexName, ConName AS ConstraintName FROM pg_index i
                INNER JOIN pg_class cl ON cl.oid = i.indexrelid
                INNER JOIN pg_namespace schema ON schema.oid = cl.relnamespace
                INNER JOIN pg_constraint c ON ConNamespace = schema.oid AND ConIndId = cl.oid AND ConType = 'u'
                WHERE IndIsUnique AND NOT NspName IN ('pg_toast', 'pg_catalog')"""
            )).mapStream()
                .collect(Collectors.toMap(map -> new IndexKey((String)map.get("SchemaName"), (String)map.get("IndexName")), map -> (String)map.get("ConstraintName")))));
        return constraintMap.get(new IndexKey(schemaName, indexName));
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
