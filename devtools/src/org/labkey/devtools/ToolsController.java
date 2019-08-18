package org.labkey.devtools;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.BufferedReader;
import java.io.File;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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
            return new ActionListView(ToolsController.this);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            appendBeginNavTrail(root);
            return root;
        }
    }


    private void appendBeginNavTrail(NavTree root)
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

                Module module = ModuleLoader.getInstance().getModule(_moduleName);

                if (null == module)
                {
                    out.println("Module " + _moduleName + " not running");
                }
                else
                {
                    String sourcePath = module.getSourcePath();

                    if (null == sourcePath)
                    {
                        out.println(module.getName() + " module source path not found");
                    }
                    else
                    {
                        Path gaPath = Path.of(sourcePath).getParent().resolve(".gitattributes");

                        if (!gaPath.toFile().exists())
                        {
                            out.println("File " + gaPath + " not found");
                        }
                        else
                        {
                            File gaDirFile = gaPath.getParent().toFile();
                            out.println("Files listed in " + gaPath + " that don't exist:\n");

                            try (BufferedReader reader = Files.newBufferedReader(gaPath, StringUtilsLabKey.DEFAULT_CHARSET))
                            {
                                reader.lines()
                                    .filter(line -> !line.isEmpty() && !line.startsWith("*"))
                                    .forEach(line -> {
                                        int idx = line.indexOf(' ');
                                        String filename = line.substring(0, idx);
                                        File file = new File(gaDirFile, filename);

                                        if (!file.exists())
                                            out.println(filter(filename));
                                    });
                            }
                        }
                    }
                }

                out.println("</pre>");
            }
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            appendBeginNavTrail(root);
            root.addChild(".gitattributes File Check");
            return root;
        }
    }


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
                out.println("JSP references that couldn't be resolved to JSP files:");
                out.println();

                jspReferences.removeAll(copyOfJspFiles);
                jspReferences.forEach(path->out.println(filter(path)));

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
                                if (file.toString().endsWith(".java"))
                                {
                                    String code = PageFlowUtil.getFileContentsAsString(file.toFile());
                                    JavaScanner scanner = new JavaScanner(code);

                                    // TODO: super("org/labkey/*.jsp")
                                    // TODO: warn for duplicates - suspicious
                                    int idx = scanner.indexOf("new JspView");

                                    while (-1 != idx)
                                    {
                                        // We've found a JspView constructor; extract its parameters
                                        Pair<Integer, Integer> startEnd = findParameters(code, idx);
                                        String parameters = code.substring(startEnd.first, startEnd.second);

                                        // JspView() parameters usually include a path to the JSP, but not always. Try to find a string...
                                        int openingQuote = parameters.indexOf('"');

                                        if (-1 != openingQuote)
                                        {
                                            // TODO: find multiple strings, see ProteinSearchWebPart
                                            int closingQuote = parameters.indexOf('"', openingQuote + 1);
                                            String jspPath = parameters.substring(openingQuote + 1, closingQuote);
                                            handleJspPath(out, root, file, jspPath);
                                            ret.add(jspPath);
                                        }
                                        else
                                        {
                                            out.println(filter(module.getName() + ": Could not extract a JSP path from: " + parameters + " [" + file + "]"));
                                        }

                                        idx = scanner.indexOf("new JspView", startEnd.second + 1);
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
                                if (filePath.endsWith(".jsp"))
                                {
                                    int idx = filePath.indexOf("/org/labkey");

                                    if (-1 != idx)
                                        ret.add(filePath.substring(idx));
                                    else
                                        out.println(filter("Can't find \"/org/labkey\": " + filePath));
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

            // Return start and end characters of the parameters
            private Pair<Integer, Integer> findParameters(String code, int idx)
            {
                int start = idx = code.indexOf('(', idx);
                assert -1 != start;
                int openingCount = 1;

                while (openingCount > 0)
                {
                    char c = code.charAt(++idx);
                    if (c == '(')
                        openingCount++;
                    else if (c == ')')
                        openingCount--;
                }

                return Pair.of(start, idx + 1);
            }

            private void handleJspPath(PrintWriter out, Path root, Path file, String jspPath)
            {
                File jsp = new File(root.resolve("src").toFile(), jspPath);

                if (!jsp.exists())
                    out.println(filter(jspPath + " not found [" + file + "]"));
            }
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            appendBeginNavTrail(root);
            root.addChild("JSP Finder");
            return root;
        }
    }
}
