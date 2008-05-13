/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.wiki.renderer;

import org.apache.log4j.Logger;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NavTreeManager;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.menu.MenuService;
import org.labkey.api.view.menu.NavTreeMenu;
import org.labkey.api.wiki.FormattedHtml;
import org.labkey.api.wiki.WikiRenderer;
import org.radeox.api.engine.RenderEngine;
import org.radeox.api.engine.WikiRenderEngine;
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.engine.BaseRenderEngine;
import org.radeox.engine.context.BaseInitialRenderContext;
import org.radeox.engine.context.BaseRenderContext;
import org.radeox.filter.CacheFilter;
import org.radeox.filter.context.FilterContext;
import org.radeox.filter.interwiki.InterWiki;
import org.radeox.filter.regex.LocaleRegexReplaceFilter;
import org.radeox.filter.regex.LocaleRegexTokenFilter;
import org.radeox.filter.regex.RegexTokenFilter;
import org.radeox.macro.BaseMacro;
import org.radeox.macro.MacroRepository;
import org.radeox.macro.parameter.MacroParameter;
import org.radeox.regex.MatchResult;
import org.radeox.util.Encoder;
import org.radeox.util.StringBufferWriter;

import java.io.IOException;
import java.io.Writer;
import java.text.MessageFormat;
import java.text.ParsePosition;
import java.util.Map;


public class RadeoxRenderer extends BaseRenderEngine implements WikiRenderEngine, WikiRenderer
{
    private static MimeMap mimeMap = new MimeMap();

    boolean _asWiki = true;
    boolean _translateSpace = true;
    String _linkClassName = "link";
    String _missingClassName = "missing";

    String _wikiHrefPrefix = "?name=";
    String _createPrefix = null;
    String _attachmentPrefix = null;

    MessageFormat _wikiLinkFormat = new MessageFormat("<a class=\"{2}\" href=\"{0}\">{1}</a>");
    MessageFormat _wikiImgFormat = new MessageFormat("<img class=\"{2}\" src=\"{0}\" title=\"{1}\" border=0>");
    MessageFormat _wikiCreateLinkFormat = new MessageFormat("<a class=\"{2}\" href=\"{0}\">Create Page: {1}</a>");

    Map<String, WikiLinkable> _pages;
    Attachment[] _attachments;

    public RadeoxRenderer()
    {
        super(new MyInitialRenderContext());
        MemTracker.put(this);
    }

    // UNDONE: switch to format from prefix
    public RadeoxRenderer(String hrefPrefix, String attachPrefix,
                          Map<String, WikiLinkable> pages, Attachment[] attachments)
    {
        this();
        if (null != hrefPrefix)
            _wikiHrefPrefix = hrefPrefix;
        _attachmentPrefix = attachPrefix;
        //all callers currently use the same prefix for href and create
        _createPrefix = hrefPrefix;
        _pages = pages;
        _attachments = attachments;
    }

    public FormattedHtml format(String text)
    {
        if (text == null)
            text = "";

        RenderContext context = new BaseRenderContext();
        context.setRenderEngine(this);
        return new FormattedHtml(render(text, context), false);  // TODO: Are there wiki pages we don't want to cache?
    }

    public Object parseObject(String s, ParsePosition parsePosition)
    {
        throw new UnsupportedOperationException();
    }


    public static class MyInitialRenderContext
            extends BaseInitialRenderContext
    {
        public MyInitialRenderContext()
        {
//          Locale languageLocale = Locale.getDefault();
//          Locale locale = new Locale("mywiki", "mywiki");
//          set(RenderContext.INPUT_LOCALE, locale);
//          set(RenderContext.OUTPUT_LOCALE, locale);
//          set(RenderContext.LANGUAGE_LOCALE, languageLocale);
            set(RenderContext.INPUT_BUNDLE_NAME, "org.labkey.api.util.wiki_markup");
            set(RenderContext.OUTPUT_BUNDLE_NAME, "org.labkey.api.util.wiki_markup");
//          set(RenderContext.LANGUAGE_BUNDLE_NAME, "my_messages");
        }
    }

    private static class LabKeyMacro extends BaseMacro
    {
        public String getName()
        {
            return "labkey";
        }

        public void execute(Writer writer, MacroParameter params) throws IllegalArgumentException, IOException
        {
            String macroName = params.get(0);
            if ("tree".equals(macroName))
                executeTree(writer, params);
            else
                writer.write("Unknown LabKey macro: " + macroName);
        }

        @Override
        public String getDescription()
        {
            return "Base LabKey macro, used for putting data from the LabKey Server portal into wikis.";
        }

        @Override
        public String[] getParamDescription()
        {
            return super.getParamDescription();    //To change body of overridden methods use File | Settings | File Templates.
        }

        public void executeTree(Writer writer, MacroParameter params) throws IOException
        {
            String treeId = params.get("name", 1);
            NavTree navTree = null;
            ViewContext ctx = HttpView.currentContext();
            if (null == ctx)
                throw new IllegalStateException("No view context.");
            Container c = ctx.getContainer();
            if (null == c)
                throw new IllegalStateException("No current container");

            Container project = c.getProject();
            //TODO: Have registry of trees. Need registry of trees instead of just arbitrary macros to support graphical tree editor
            if ("core.projects".equals(treeId))
                navTree = ContainerManager.getProjectList(ctx);
            else if ("core.currentProject".equals(treeId))
                navTree = ContainerManager.getFolderListForUser(project, ctx);
            else if ("core.projectAdmin".equals(treeId))
                navTree = MenuService.get().getProjectAdminTree(ctx);
            else if ("core.siteAdmin".equals(treeId))
                navTree = MenuService.get().getSiteAdminTree(ctx);

            NavTreeManager.applyExpandState(navTree, ctx);
            if (null == navTree)
            {
                writer.write("<!-- labkey:tree|id=" + treeId + " no permissions or not found. -->");
                return;
            }

            NavTreeMenu mv = new NavTreeMenu(ctx, "wiki-menu", navTree);
            try
            {
            mv.include(mv, writer);
            }
            catch (IOException iox)
            {
                throw iox;
            }
            catch (Exception ex)
            {
                throw new RuntimeException(ex);
            }
        }
    }

    private static class ImageMacro extends BaseMacro
    {
        public void execute(Writer writer, MacroParameter macroParameter) throws IllegalArgumentException, IOException
        {
            String img = macroParameter.get("img");
            String alt = null;
            String align = null;
            if (img != null)
            {
                alt = macroParameter.get("alt");
                align = macroParameter.get("align");
            }
            else
                img = macroParameter.get(0);

            if (img != null)
            {
                StringBuffer buf = new StringBuffer();
                buf.append("<img src=\"").append(img).append("\"");
                if (alt != null)
                    buf.append(" alt=\"").append(alt).append("\"");
                if (align != null)
                    buf.append(" align=\"").append(align).append("\"");
                buf.append(">");
                writer.write(buf.toString());
            }
        }

        public String getName()
        {
            return "image";
        }

        public String getDescription()
        {
            return "Displays an image file.";
        }

        private final String[] PARAMS = new String[]
                {
                        "img: the path to the image.",
                        "alt: alt text (optional)",
                        "align: alignment of the image (left, right, flow-left, flow-right) (optional)",
                };

        public String[] getParamDescription()
        {
            return PARAMS;
        }
    }


    public boolean attachmentExists(String s)
    {
        if (null == _attachments)
            return false;
        for (int i = 0; i < _attachments.length; i++)
        {
            Attachment attachment = _attachments[i];
            if (s.equalsIgnoreCase(attachment.getName()))
                return true;
        }
        return false;
    }

    static
    {
        MacroRepository repository = MacroRepository.getInstance();
        repository.put("image", new ImageMacro());
        repository.put("labkey", new LabKeyMacro());
    }

    //
    // BaseRenderEngine
    //
    protected void init()
    {
        super.init();

        fp.removeFilter("org.radeox.filter.StrikeThroughFilter");
        fp.addFilter(new UnderlineFilter());
        fp.addFilter(new IndentFilter());
        fp.addFilter(new LinkTestFilter());
        fp.init();
    }

    //
    // WikiRenderEngine
    //
    public boolean exists(String s)
    {
        if (attachmentExists(s))
            return true;
        if (null == _pages)
            return false;
        return _pages.containsKey(s);
    }


    public boolean showCreate()
    {
        return null != _createPrefix;
    }


    public void appendLink(StringBuffer sb, String name, String view)
    {
        _appendLink(sb, name, view, null, _linkClassName);
    }


    public void appendLink(StringBuffer sb, String name, String view, String hash)
    {
        _appendLink(sb, name, view, hash, _linkClassName);
    }


    public void appendCreateLink(StringBuffer sb, String name, String wikiName)
    {
        String mime = mimeMap.getContentTypeFor(name.toLowerCase());
        if (null != mime)
        {
            // CONSIDER: upload link?
            sb.append("<span class=\"" + _missingClassName + "\">" + name + "</span>");
        }
        else
        {
            _appendLink(sb, name, wikiName, null, _missingClassName);
        }
    }


    private void _appendLink(StringBuffer sb, String name, String view, String hash, String className)
    {
        boolean isAttachment = attachmentExists(name);
        String href = (attachmentExists(name) ? _attachmentPrefix : _wikiHrefPrefix) + PageFlowUtil.encode(name);

        // if view is null or empty, try to lookup the
        // target page's title, or at least set it equal to the last part of name
        if (null == view || view.length() == 0)
        {
            view = name.lastIndexOf('/') >= 0 ? name.substring(name.lastIndexOf('/')) : name;
            if (null != _pages)
            {
                WikiLinkable w = _pages.get(name);
                if (null != w)
                    view = w.getTitle();
            }
        }

        if (isAttachment)
        {
            String mime = mimeMap.getContentTypeFor(name.toLowerCase());
            if (null != mime && mime.startsWith("image/"))
            {
                _wikiImgFormat.format(new Object[]{PageFlowUtil.filter(href), PageFlowUtil.filter(view), className}, sb, null);
            }
            else
            {
                // CONSIDER document icon...
                _wikiLinkFormat.format(new Object[]{PageFlowUtil.filter(href), PageFlowUtil.filter(view), className}, sb, null);
            }
            return;
        }

        _wikiLinkFormat.format(new Object[]{PageFlowUtil.filter(href), PageFlowUtil.filter(view), className}, sb, null);
        if (null != hash)
            sb.append("#" + hash);
    }


    public static class UnderlineFilter extends LocaleRegexReplaceFilter implements CacheFilter
    {
        protected String getLocaleKey()
        {
            return "filter.underline";
        }
    }


    public static class IndentFilter extends RegexTokenFilter implements CacheFilter
    {
        IndentFilter()
        {
            super("^(\\s+)(\\S[^\\r\\n]*)");
        }

        public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context)
        {
            int len = result.group(1).length();
            for (int i = 0; i < len - 1; i++)
                buffer.append("&nbsp;");
            buffer.append(result.group(2));
        }
    }

    public static class ImageFilter extends RegexTokenFilter implements CacheFilter
    {
        ImageFilter()
        {
            super("^(\\s+)(\\S[^\\r\\n]*)");
        }

        public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context)
        {
            int len = result.group(1).length();
            for (int i = 0; i < len - 1; i++)
                buffer.append("&nbsp;");
            buffer.append(result.group(2));
        }
    }

    /**
     * replace org.radeox.filter.LinkTestFilter, which does not handle
     * [name|http://url/] syntax
     */
    public static class LinkTestFilter extends LocaleRegexTokenFilter
    {
        private static Logger log = Logger.getLogger(org.radeox.filter.LinkTestFilter.class);
        private MessageFormat urlFormatter;


        public void setInitialContext(InitialRenderContext context)
        {
            super.setInitialContext(context);
            String outputTemplate = outputMessages.getString("filter.url.print");
            urlFormatter = new MessageFormat("");
            urlFormatter.applyPattern(outputTemplate);
        }


        public String[] replaces()
        {
            return new String[]{"org.radeox.filter.LinkTestFilter"};
        }


        /**
         * The regular expression for detecting WikiLinks.
         * Overwrite in subclass to support other link styles like
         * OldAndUglyWikiLinking :-)
         * <p/>
         * /[A-Z][a-z]+([A-Z][a-z]+)+/
         * wikiPattern = "\\[(.*?)\\]";
         */

        protected String getLocaleKey()
        {
            return "filter.linktest";
        }

        protected void setUp(FilterContext context)
        {
            context.getRenderContext().setCacheable(true);
        }

        public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context)
        {
            RenderEngine engine = context.getRenderContext().getRenderEngine();

            if (engine instanceof WikiRenderEngine)
            {
                WikiRenderEngine wikiEngine = (WikiRenderEngine) engine;
                Writer writer = new StringBufferWriter(buffer);

                String name = result.group(1);
                if (name != null)
                {
                    // trim the name and unescape it
                    name = Encoder.unescape(name.trim());

                    // Is there an alias like [alias|link] ?
                    int pipeIndex = name.indexOf('|');
                    String alias = "";
                    if (-1 != pipeIndex)
                    {
                        alias = name.substring(0, pipeIndex);
                        name = name.substring(pipeIndex + 1);
                    }

                    if (name.startsWith("http:") || name.startsWith("https:") || name.startsWith("ftp:"))
                    {
                        // just treat like UrlFilter
                        if (alias.length() == 0)
                            alias = name;
                        String tag = urlFormatter.format(new Object[]{"", name, alias});
                        buffer.append(tag);
                        return;
                    }

                    int hashIndex = name.lastIndexOf('#');

                    String hash = "";
                    if (-1 != hashIndex && hashIndex != name.length() - 1)
                    {
                        hash = name.substring(hashIndex + 1);
                        name = name.substring(0, hashIndex);
                    }

                    int colonIndex = name.indexOf(':');
                    // typed link ?
                    if (-1 != colonIndex)
                    {
                        // for now throw away the type information
                        name = name.substring(colonIndex + 1);
                    }

                    int atIndex = name.lastIndexOf('@');
                    // InterWiki link ?
                    if (-1 != atIndex)
                    {
                        String extSpace = name.substring(atIndex + 1);
                        // known extarnal space ?
                        InterWiki interWiki = InterWiki.getInstance();
                        if (interWiki.contains(extSpace))
                        {
                            String view = name;
                            if (-1 != pipeIndex)
                            {
                                view = alias;
                            }

                            name = name.substring(0, atIndex);
                            try
                            {
                                if (-1 != hashIndex)
                                {
                                    interWiki.expand(writer, extSpace, name, view, hash);
                                }
                                else
                                {
                                    interWiki.expand(writer, extSpace, name, view);
                                }
                            }
                            catch (IOException e)
                            {
                                log.debug("InterWiki " + extSpace + " not found.");
                            }
                        }
                        else
                        {
                            buffer.append("&#91;<span class=\"error\">");
                            buffer.append(result.group(1));
                            buffer.append("?</span>&#93;");
                        }
                    }
                    else
                    {
                        // internal link

                        if (wikiEngine.exists(name))
                        {
                            String view = "";
                            if (-1 != pipeIndex)
                            {
                                view = alias;
                            }
                            // Do not add hash if an alias was given
                            if (-1 != hashIndex)
                            {
                                wikiEngine.appendLink(buffer, name, view, hash);
                            }
                            else
                            {
                                wikiEngine.appendLink(buffer, name, view);
                            }
                        }
                        else if (wikiEngine.showCreate())
                        {
                            wikiEngine.appendCreateLink(buffer, name, alias.length() > 0 ? alias : getWikiView(name));

                            // links with "create" are not cacheable because
                            // a missing wiki could be created
                            context.getRenderContext().setCacheable(false);
                        }
                        else
                        {
                            // cannot display/create wiki, so just display the text
                            buffer.append(name);
                        }
                    }
                }
                else
                {
                    buffer.append(Encoder.escape(result.group(0)));
                }
            }
        }

        /**
         * Returns the view of the wiki name that is shown to the
         * user. Overwrite to support other views for example
         * transform "WikiLinking" to "Wiki Linking".
         * Does nothing by default.
         *
         * @return view The view of the wiki name
         */

        protected String getWikiView(String name)
        {
            return name;
        }
    }
}
