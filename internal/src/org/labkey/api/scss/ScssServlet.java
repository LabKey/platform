/*
 * Copyright (c) 2011-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.scss;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.reader.Readers;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.webdav.ModuleStaticResolverImpl;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavResource;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NOTE: assumes that the generated .css file is in the same directory as the source .scss file
 */

public class ScssServlet extends HttpServlet
{
    private static final Logger _log = Logger.getLogger(ScssServlet.class);
    private final Cache<Path, ScssContent> _cache = CacheManager.getCache(CacheManager.UNLIMITED, CacheManager.YEAR, "SCSS cache");

    public ScssServlet()
    {
    }

    @Override
    public void init(ServletConfig servletConfig) throws ServletException
    {
    }


    @Override
    public void service(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException
    {
        HttpServletResponse response = (HttpServletResponse)servletResponse;
        HttpServletRequest request = (HttpServletRequest)servletRequest;

        String pathStr = ((HttpServletRequest)servletRequest).getServletPath();
        Path path = Path.parse(pathStr);

        WebdavResource scss = lookup(path);
        if (null == scss || !scss.isFile() || !(pathStr.endsWith(".scss") || pathStr.endsWith(".sass")))
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!scss.canRead((User)request.getUserPrincipal(),true))
        {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        ScssContent content = _cache.get(path);
        if (null == content || content.isStale())
        {
            content = compileShell(scss);
            if (null != content)
                _cache.put(scss.getPath(), content);
        }

        if (null == content)
        {
            ((HttpServletResponse)servletResponse).sendError(404);
            return;
        }

        PageFlowUtil.sendContent(request, response, content, "text/css");
        return;
    }


    @Override
    public String getServletInfo()
    {
        return "SCSS Servlet";
    }


    @Override
    public void log(String msg)
    {
        _log.warn(msg);
    }

    public void debug(String msg)
    {
        _log.debug(msg);
    }

    public void info(String msg)
    {
        _log.info(msg);
    }

    @Override
    public void log(String message, Throwable t)
    {
        _log.error(message, t);
    }


    @Override
    public void destroy()
    {
        super.destroy();
    }


    public ScssContent compileShell(WebdavResource scss) throws IOException
    {
        // Compute dependencies
        Set<Path> dependencies = new TreeSet<>();
        findDependencies(scss, dependencies);

        String dir = scss.getFile().getParent();

        Path path = scss.getPath();
        String scssName = path.getName();
        String cssName = scssName.substring(0,scssName.length()-5) + ".css";
        Path targetPath = path.getParent().append(cssName);
        Resource cssFile = lookup(targetPath);
        ScssContent ret = null;

        if (null != cssFile && cssFile.isFile())
            ret = new ScssContent(cssFile, dependencies);

        if (!isDevMode() || (null != ret && !ret.isStale()))
            return ret;

        // devMode only try to compile

        String OS = System.getProperty("os.name").toLowerCase();
        String compass = -1==OS.indexOf("windows") ? "compass" : "compass.bat";
        ProcessBuilder pb = new ProcessBuilder(compass, "compile", "--boring", dir);
        runProcess(pb);

        cssFile = lookup(targetPath);
        if (null != cssFile && cssFile.isFile())
            ret = new ScssContent(cssFile, dependencies);
        return ret;
    }


    protected int runProcess(ProcessBuilder pb)
    {
        Process proc;
        try
        {
            pb.redirectErrorStream(true);
            proc = pb.start();
        }
        catch (SecurityException se)
        {
            throw new RuntimeException(se);
        }
        catch (IOException eio)
        {
            Map<String, String> env = pb.environment();
            throw new RuntimeException("Failed starting process '" + pb.command() + "'. " +
                    "Must be on server path. (PATH=" + env.get("PATH") + ")", eio);
        }

        try (BufferedReader procReader = Readers.getReader(proc.getInputStream()))
        {
            String line;
            while ((line = procReader.readLine()) != null)
                info("SASS compile: " + line);
        }
        catch (IOException eio)
        {
            throw new RuntimeException("Failed writing output for process in '" + pb.directory().getPath() + "'.", eio);
        }

        try
        {
            return proc.waitFor();
        }
        catch (InterruptedException ei)
        {
            throw new RuntimeException("Interrupted process for '" + pb.command() + " in " + pb.directory() + "'.", ei);
        }
    }


    Pattern extractLog = Pattern.compile("([a-zA-Z_0-9-]+[.]s[ca]ss:\\d+:.+)$", Pattern.MULTILINE);
    Pattern extractLog2 = Pattern.compile("([(]s[ca]ss[)]:\\d+:.+)$", Pattern.MULTILINE);
    ScriptEngine _engine = null;

    ScriptEngine getEngine()
    {
        if (null == _engine)
        {
            System.setProperty("jruby.jit.codeCache", new File(FileUtil.getTempDirectory(), "jruby-cache").getAbsolutePath());
            List<String> rubyPath = Arrays.asList(
                "/Library/Ruby/Gems/1.8/gems/sass-3.1.10/lib",
                "/Library/Ruby/Gems/1.8/gems/compass-0.11.5/lib",
                "/Library/Ruby/Gems/1.8/gems/chunky_png-1.2.5/lib",
                "/Library/Ruby/Gems/1.8/gems/fssm-0.2.7/lib",
                "/opt/local/share/java/jruby/lib/ruby/1.8"
            );
            System.setProperty("org.jruby.embed.class.path", StringUtils.join(rubyPath, File.pathSeparator));
            _engine = new ScriptEngineManager().getEngineByName("jruby");
        }
        return _engine;
    }


    public ScssContent compileJRuby(WebdavResource css) throws IOException
    {
        // Compute dependencies
        Set<Path> dependencies = new TreeSet<>();
        findDependencies(css, dependencies);

        StringWriter errors = new StringWriter();
        boolean devMode = isDevMode();

        ScriptEngine engine = getEngine();

        // Compile
        synchronized (engine)
        {
            try
            {
                ScriptContext context = engine.getContext();
                context.setErrorWriter(errors);

                String sourcePath = css.getFile().getAbsolutePath();
                String outputPath = sourcePath.substring(0,sourcePath.length()-5) + ".css";

                String program = StringUtils.join(Arrays.asList(
                        "require 'sass'",
                        "require 'compass'",
                        "options = {}",
                        "options[:load_paths] = ['/Library/Ruby/Gems/1.8/gems/compass-0.11.5/lib']",
                        "options[:cache_location] = '" + new File(FileUtil.getTempDirectory(), "sass-cache").getAbsolutePath() + "'",
                        "options[:style] = " + (devMode ? ":expanded" : ":compressed") + "",
                        "options[:line_comments] = " + (devMode ? "true" : "false") + "",
                        "options[:syntax] = " + (css.getName().endsWith(".scss") ? ":scss" : ":sass") + "",
                        "input = File.new('" + sourcePath + "', 'r')",
                        "tree = ::Sass::Engine.new(input.read(), options).to_tree",
                        "File.new('" + outputPath + "', 'w').write(tree.render)"
//                        , "@result.append(tree.render)"
                ),"\n");
                engine.eval(program);
                String result = read(new File(outputPath));
                ScssContent content = new ScssContent(result.toString(), dependencies);
                return content;
            }
            catch (Exception e)
            {
                // Log ?
                String error = "";
                Matcher matcher = extractLog.matcher(errors.toString());
                while (matcher.find())
                {
                    error = matcher.group(1);
                    log(error);
                }
                matcher = extractLog2.matcher(errors.toString());
                while (matcher.find())
                {
                    error = matcher.group(1).replace("(sass)", css.getName());
                    log(error);
                }
                if (error.equals(""))
                {
                    log("SASS Error", e);
                    error = "Check logs";
                }
                String content = "";
                if (isDevMode())
                {
                    content =  "/** The CSS was not generated because the " + css.getName() + " file has errors; check logs **/\n\n"
                            + "body:before {display: block; color: #c00; white-space: pre; font-family: monospace; background: #FDD9E1; border-top: 1px solid pink; border-bottom: 1px solid pink; padding: 10px; content: \"[SASS ERROR] " + error.replace("\"", "'") + "\"; }";
                }
                ScssContent ret = new ScssContent(content,dependencies);
                ret.setHasError(true);
                return ret;
            }
        }
    }


    private String read(File file) throws IOException
    {
        try (InputStream in = new FileInputStream(file))
        {
            return PageFlowUtil.getStreamContentsAsString(in);
        }
    }


    private String read(Resource r) throws IOException
    {
        try (InputStream in = r.getInputStream())
        {
            return PageFlowUtil.getStreamContentsAsString(in);
        }
    }


    Pattern imports = Pattern.compile("@import\\s+[\"']?([^\\s'\";]+)[\"']?");

    private void findDependencies(Resource scss, Set<Path> dependencies) throws IOException
    {
        if (!isDevMode())
            return;
        if (!scss.isFile())
            return;
        if (!dependencies.add(scss.getPath()))
            return;

        Matcher m = imports.matcher(PageFlowUtil.getStreamContentsAsString(scss.getInputStream()));
        while (m.find())
        {
            Path fileName = Path.parse(m.group(1));
            Path importPath = scss.getPath().getParent().append(fileName).normalize();
            Resource dep;
findFile:   {
                dep = lookup(importPath);
                if (null != dep && dep.isFile())
                    break findFile;
                if (importPath.getName().endsWith(".sass") || importPath.getName().endsWith(".scss"))
                    continue;
                dep = lookup(importPath.getParent().append(importPath.getName() + ".scss"));
                if (null != dep && dep.isFile())
                    break findFile;
                dep = lookup(importPath.getParent().append(importPath.getName() + ".sass"));
            }
            if (null != dep && dep.isFile())
            {
                debug( scss.getPath() + " depends on " + dep.getPath() );
                findDependencies(dep, dependencies);
            }
        }
    }


    private Boolean _devmode = null;
    boolean isDevMode()
    {
        if (null == _devmode)
            _devmode = AppProps.getInstance().isDevMode();
        return _devmode;
    }


    WebdavResolver getResolver()
    {
        return ModuleStaticResolverImpl.get();
    }


    WebdavResource lookup(Path path)
    {
        return getResolver().lookup(path);
    }


    class ScssContent extends PageFlowUtil.Content
    {
        boolean _hasError = false;

        ScssContent(String css, Set<Path> dependencies)
        {
            super(css, css.getBytes(), HeartBeat.currentTimeMillis());
            this.compressed = Compress.compressGzip(this.encoded);
            this.dependencies = dependencies;
        }

        ScssContent(Resource cssFile, Set<Path> dependencies) throws IOException
        {
            this(read(cssFile), dependencies);
            this.modified = cssFile.getLastModified();
        }

        Set<Path> getDependencies()
        {
            if (null == this.dependencies)
                return Collections.emptySet();
            return (Set<Path>)this.dependencies;
        }

        public void setHasError(boolean e)
        {
            _hasError = e;
        }

        public boolean isStale()
        {
            if (!isDevMode())
                return false;

            if (_hasError)
                return true;

            for (Path p : getDependencies())
            {
                Resource r = lookup(p);
                if (null == r || !r.isFile() || r.getLastModified() > modified)
                    return true;
            }
            return false;
        }
    }
}