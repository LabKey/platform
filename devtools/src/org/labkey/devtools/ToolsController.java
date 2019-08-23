package org.labkey.devtools;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
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
import java.nio.file.Files;
import java.nio.file.Path;

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


    @RequiresPermission(AdminPermission.class)
    public class GitAttributesAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new VBox(
                new GitAttributesView("core"),
                new GitAttributesView("ms2")
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
                                            out.println(PageFlowUtil.filter(filename));
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
}
