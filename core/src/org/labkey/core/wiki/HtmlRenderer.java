/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.core.wiki;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.JSoupUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.wiki.FormattedHtml;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRenderingService.SubstitutionMode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlRenderer implements WikiRenderer
{
    private final SubstitutionMode _substitutionMode;
    private final String _hrefPrefix;
    private final String _attachPrefix;
    private final Map<String, String> _nameTitleMap;
    private final Map<String, Attachment> _attachments;

    private static final Map<String, SubstitutionHandler> _substitutionHandlers = new HashMap<>();

    static
    {
        // Add SubstitutionHandlers for each ${labkey.<type>()}
        _substitutionHandlers.put("webPart", new WebPartSubstitutionHandler());
        _substitutionHandlers.put("dependency", new ClientDependencySubstitutionHandler());
    }

    /**
     * HTML wiki pages allow webpart and dependency substitutions ({@code ${labkey.webPart}} and {@code ${labkey.dependency}});
     * HTML announcements and Markdown wikis (which are wrapped by this renderer) do not allow substitutions
     */
    public HtmlRenderer(SubstitutionMode substitutionMode, String hrefPrefix, String attachPrefix, Map<String, String> nameTitleMap, @Nullable Collection<? extends Attachment> attachments)
    {
        _substitutionMode = substitutionMode;
        _hrefPrefix = hrefPrefix;
        _attachPrefix = attachPrefix;
        _nameTitleMap = nameTitleMap == null ? new HashMap<>() : nameTitleMap;
        _attachments = new HashMap<>();

        if (null != attachments)
            for (Attachment a : attachments)
                _attachments.put(a.getName(), a);
    }
    
    @Override
    public FormattedHtml format(String text)
    {
        LinkedList<String> errors = new LinkedList<>();
        if (text == null)
            return new FormattedHtml(HtmlString.EMPTY_STRING);

        // Remove degenerate comments (e.g. "<!-->") as Tidy does not handle them properly
        text = text.replaceAll("<!-+>|<!>", "");

        FormattedHtml formattedHtml = handleLabKeySubstitutions(text);
        boolean volatilePage = formattedHtml.isVolatile();
        LinkedHashSet<ClientDependency> cds = new LinkedHashSet<>(formattedHtml.getClientDependencies());

        Document doc = JSoupUtil.convertHtmlToDocument("<html><body>" + StringUtils.trimToEmpty(formattedHtml.getHtml().toString()) + "</body></html>", false, errors);
        if (!errors.isEmpty() || doc == null)
        {
            StringBuilder innerHtml = new StringBuilder("<div class=\"labkey-error\"><b>An exception occurred while generating the HTML. Please correct this content.</b></div><br>The error message was: ");
            if (!errors.isEmpty())
            {
                for (String error : errors)
                    innerHtml.append(PageFlowUtil.filter(error)).append("<hr>");
            }
            return new FormattedHtml(HtmlString.unsafe(innerHtml.toString()), false, cds);
        }

        Set<String> wikiDependencies = new HashSet<>();

        // process A and IMG
        NodeList nl = doc.getElementsByTagName("a");
        Map<Element, String> linkExceptions = new HashMap<>();
        // Tidy recalculates the length on all calls to getLength(), which is expensive, so remember the value
        for (int i = 0, length = nl.getLength(); i < length ; i++)
        {
            Element a = (Element)nl.item(i);
            try
            {
                String href = PageFlowUtil.decode(a.getAttribute("href"));

                Attachment at = _attachments.get(href);
                if (null != at)
                {
                    a.setAttribute("href", _attachPrefix + PageFlowUtil.encode(at.getName()));
                    wikiDependencies.add(at.getName());
                    continue;
                }

                if (href.isBlank() || href.startsWith("#"))
                    continue;

                String wikiName = StringUtils.substringBefore(href, "#");
                String title = _nameTitleMap.get(wikiName);
                if (null != title)
                {
                    // UNDONE: why is l.getName() null???
                    //a.setAttribute("href", _hrefPrefix + PageFlowUtil.encode(.getName()));
                    a.setAttribute("href", _hrefPrefix + PageFlowUtil.encode(href));
                    wikiDependencies.add(wikiName);
                    continue;
                }

                // if this doesn't look like a url, then link to it as a wiki page
                if (StringUtils.containsNone(href, "?:/.&"))
                {
                    a.setAttribute("href", _hrefPrefix + PageFlowUtil.encode(href));
                    wikiDependencies.add(wikiName);
                }
            }
            catch (IllegalArgumentException e)
            {
                // Issue 12160: there was an issue with decoding the href for the link, so store the exception text and link index for later
                linkExceptions.put(a, e.getMessage());
            }
        }

        nl = doc.getElementsByTagName("img");
        for (int i = 0, length = nl.getLength(); i < length; i++)
        {
            Element img = (Element)nl.item(i);
            String src = img.getAttribute("src");
            src = PageFlowUtil.decode(src);
            Attachment at = _attachments.get(src);
            if (null != at)
            {
                img.setAttribute("src", _attachPrefix + PageFlowUtil.encode(at.getName()));
                wikiDependencies.add(at.getName());
            }
        }

        // Add nonces to all script elements. Mark the page as volatile if any script elements are present. This is a
        // little heavy-handed.  We could add a "post-render" step instead.
        if (PageFlowUtil.addScriptNonces(doc) > 0)
            volatilePage = true;

        // back to html
        StringBuilder innerHtml;

        try
        {
            innerHtml = new StringBuilder();

            //look for style elements, as tidy moves them to the head section
            NodeList styleNodes = doc.getElementsByTagName("style");
            for (int idx = 0, length = styleNodes.getLength(); idx < length; ++idx)
            {
                innerHtml.append(PageFlowUtil.convertNodeToHtml(styleNodes.item(idx)));
                innerHtml.append('\n');
            }

            Node bodyNode = doc.getElementsByTagName("body").item(0);
            // Uncomment the below to debug bodyNode contents
            // inspectNode(bodyNode, 0);

            String bodyHtml = PageFlowUtil.convertNodeToHtml(bodyNode);

            // Issue 12160: if there were any link decoding exceptions, replace the link with the error message
            if (!linkExceptions.isEmpty())
            {
                for (Map.Entry<Element, String> link : linkExceptions.entrySet())
                {
                    String linkHtml = "<span class='labkey-error'>" + link.getValue() + "</span>";
                    bodyHtml = bodyHtml.replace(PageFlowUtil.convertNodeToHtml(link.getKey()), linkHtml);
                }
            }

            assert bodyHtml.startsWith("<body") && bodyHtml.endsWith("</body>") : "bodyHtml did not start with \"<body\" and end with \"</body>\"";
            innerHtml.append(bodyHtml, bodyHtml.indexOf('>') + 1, bodyHtml.length()-"</body>".length());
        }
        catch (Exception e)
        {
            innerHtml = new StringBuilder("<div class=\"labkey-error\"><b>An exception occurred while generating the HTML.  Please correct this content.</b></div><br>The error message was: ");
            innerHtml.append(e.getMessage()); 
            volatilePage = false;
        }

        return new FormattedHtml(HtmlString.unsafe(innerHtml.toString()), volatilePage, wikiDependencies, Collections.emptySet(), cds);
    }

    private FormattedHtml handleLabKeySubstitutions(final String text)
    {
        if (text == null)
            return new FormattedHtml(HtmlString.EMPTY_STRING);

        // I'd rather push these calls into the enum, but the enum is defined in API and these method implementations are in core
        return switch (_substitutionMode)
        {
            case Ignore -> new FormattedHtml(HtmlString.unsafe(text));
            case Remove -> removeSubstitutions(text);
            case Substitute -> handleSubstitutions(text);
        };
    }

    // ${labkey.<type>(<any_stream of characters>)}
    // Pattern.DOTALL allows the parameter list to span multiple lines
    private static final Pattern SUBSTITUTION_PATTERN = Pattern.compile("\\$\\{labkey\\.(\\w+)\\((.*?)\\)}", Pattern.DOTALL);

    // Find all substitution templates embedded in HTML wiki text that have the form ${labkey.<type>(<any_stream of characters>)}.
    private Matcher getSubstitutionMatcher(String text)
    {
        return SUBSTITUTION_PATTERN.matcher(text);
    }

    // TODO: Since the Remove option is used by the Wiki validation provider, it may be more appropriate for this
    // method to parse and verify the substitution & parameter syntax before removing them, reporting errors as
    // appropriate. This would mean executing most of handleSubstitutions() before doing the replacement and wiring
    // up a way to report errors to the validator (i.e., not just write errors into the rendered HTML).
    private FormattedHtml removeSubstitutions(String text)
    {
        text = getSubstitutionMatcher(text).replaceAll("");
        return new FormattedHtml(HtmlString.unsafe(text));
    }

    // <any word>='<any value>', allowing whitespace before, after, and in-between
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\s*(\\w+)\\s*=\\s*'(.*)'\\s*");

    private FormattedHtml handleSubstitutions(String text)
    {
        boolean volatilePage = false;
        LinkedHashSet<ClientDependency> cds = new LinkedHashSet<>();

        Matcher substitutionMatcher = getSubstitutionMatcher(text);

        // If we find none, return immediately
        if (!substitutionMatcher.find())
            return new FormattedHtml(HtmlString.unsafe(text));

        List<Definition> definitions = new ArrayList<>(10);
        Map<Definition, List<String>> wikiErrors = new HashMap<>();
        do
        {
            List<String> paramErrors = new ArrayList<>();
            String substitutionType = substitutionMatcher.group(1);          // type
            String params = substitutionMatcher.group(2).replace(",", "");
            // Parse the parameters with the symbols in parseWith, they can be used in any order
            // as long as the same symbol starts and completes a parameter value
            List<String> paramList = new ArrayList<>();
            paramList.add(params);
            // Support either HTML encoded or unencoded apostrophe
            String[] parseWith = {"&#39;", "'"};
            for (String parser : parseWith)
            {
                List<String> paramListTemp = new ArrayList<>();
                for (String paramSection : paramList)
                {
                    String[] paramSplit = paramSection.split(parser);
                    for (int i = 0; i < paramSplit.length; i += 2)
                    {
                        paramListTemp.add(paramSplit[i] + (paramSplit.length > i + 1 ? "'" + paramSplit[i + 1] + "'" : ""));
                    }
                }
                paramList = paramListTemp;
            }

            Map<String, String> paramMap = new HashMap<>(10);
            for (String param : paramList)
            {
                Matcher paramMatcher = PARAM_PATTERN.matcher(param);

                if (paramMatcher.matches())
                {
                    if (paramMap.containsKey(paramMatcher.group(1)))
                        paramErrors.add(param.trim() + ", there are multiple parameters with this name");
                    else
                        paramMap.put(paramMatcher.group(1), paramMatcher.group(2));
                }
                else if (!param.trim().isEmpty())
                    paramErrors.add(param.trim());
            }

            // Stick new definition at beginning of list -- we want to replace them in reverse order
            Definition definition = new Definition(substitutionType, substitutionMatcher.start(), substitutionMatcher.end(), paramMap);
            definitions.addFirst(definition);
            wikiErrors.put(definition, paramErrors);
        }
        while (substitutionMatcher.find());

        StringBuilder sb = new StringBuilder(text);

        // Get the corresponding substitution handler for each type and replace template with substitution
        for (Definition definition : definitions)
        {
            SubstitutionHandler handler = _substitutionHandlers.get(definition.getType());
            FormattedHtml substitution;

            if (null != handler)
                substitution = handler.getSubstitution(definition.getParams());
            else
                substitution = new FormattedHtml(HtmlString.unsafe("<br><font class='error' color='red'>Error: unknown type, \"labkey." + PageFlowUtil.filter(definition.getType()) + "\"</font>"));

            sb.replace(definition.getStart(), definition.getEnd(), substitution.getHtml().toString());

            if (substitution.isVolatile())
                volatilePage = true;

            cds.addAll(substitution.getClientDependencies());

            List<String> paramErrors = wikiErrors.get(definition);
            if (!paramErrors.isEmpty())
            {
                String errorHTML = "<br>";
                for (String error : paramErrors)
                    errorHTML = errorHTML.concat("<font class='error' color='red'>Error with parameter " +
                            error + " in " + definition.getType() + "</font><br><br>");
                sb.insert(definition.getStart() + substitution.getHtml().toString().length(), errorHTML);
            }
        }

        text = sb.toString();

        return new FormattedHtml(HtmlString.unsafe(text), volatilePage, cds);
    }

    private static class Definition
    {
        private final String _type;
        private final int _start;
        private final int _end;
        private final Map<String, String> _params;

        private Definition(String type, int start, int end, Map<String, String> params)
        {
            _type = type;
            _start = start;
            _end = end;
            _params = params;
        }

        public String getType()
        {
            return _type;
        }

        private int getEnd()
        {
            return _end;
        }

        private Map<String, String> getParams()
        {
            return _params;
        }

        private int getStart()
        {
            return _start;
        }
    }


    interface SubstitutionHandler
    {
        @NotNull FormattedHtml getSubstitution(Map<String, String> params);
    }
}
