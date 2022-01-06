package org.labkey.devtools;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.BaseScanner.Handler;
import org.labkey.api.util.Button;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.URLHelper;
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
import org.springframework.web.servlet.mvc.Controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        private class GitAttributesView extends HttpView
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
                                new Button.ButtonBuilder("Delete All " + missing.size() + " File Paths from .gitattributes")
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
                File file = new File(gaDirFile, filename);

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
                        try (PrintWriter output = PrintWriters.getPrintWriter(new File(gaPath.getParent().toFile(), "gitattributes.temp")))
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

        private class JspFinderView extends HttpView
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
                        .filter(s->s.endsWith(path))
                        .collect(Collectors.toUnmodifiableList());

                    // If a JSP file is referenced twice (say, once with an absolute path and once with a relative path) then
                    // we might have already removed the candidate from jspFiles. If no match, check the full list of JSPs.
                    if (candidates.isEmpty())
                    {
                        candidates = copyOfJspFiles.stream()
                            .filter(s->s.endsWith(path))
                            .collect(Collectors.toUnmodifiableList());
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
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
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
                                            String s = code.substring(beginIndex + 1, endIndex - 1);
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
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
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
    public class CheckCrawlerActionsAction extends SimpleViewAction
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
                sourcePath + "/../../../testAutomation/src/org/labkey/test/util/Crawler.java",
                sourcePath + "/../study/test/src/org/labkey/test/tests/study")
            )
            {
                File file = new File(path);
                if (!file.exists())
                {
                    errors.reject(ERROR_MSG, FileUtil.getAbsoluteCaseSensitiveFile(file).getAbsolutePath() + ": path not found!");
                    return new SimpleErrorView(errors);
                }
                addActionIds(actionIds, file);
            }

            Set<ControllerActionId> missingModules = new TreeSet<>();
            Set<ControllerActionId> missingActions = new TreeSet<>();

            for (ControllerActionId actionId : actionIds)
            {
                Module module = ModuleLoader.getInstance().getModuleForController(actionId.getController().toLowerCase());

                if (null == module)
                {
                    missingModules.add(actionId);
                }
                else
                {
                    SpringActionController controller = (SpringActionController) module.getController(null, actionId.getController());
                    Controller actionController = controller.resolveAction(actionId.getAction());

                    if (null == actionController)
                    {
                        missingActions.add(actionId);
                    }
                }
            }

            HtmlStringBuilder builder = HtmlStringBuilder.of();

            if (!missingModules.isEmpty())
            {
                builder
                    .append("The following actions could not be resolved to a module running in this deployment:")
                    .append(HtmlString.unsafe("<br><br>\n"));
                missingModules.forEach(id->builder.append(id.toString()).append(HtmlString.unsafe("<br>\n")));
                builder.append(HtmlString.unsafe("<br>\n"));
            }

            if (!missingActions.isEmpty())
            {
                builder
                    .append("The following actions do not exist:")
                    .append(HtmlString.unsafe("<br><br>\n"));
                missingActions.forEach(id->builder.append(id.toString()).append(HtmlString.unsafe("<br>\n")));
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

        private class ControllerActionId implements Comparable<ControllerActionId>
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
}
