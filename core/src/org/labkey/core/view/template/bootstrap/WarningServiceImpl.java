/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.core.view.template.bootstrap;

import org.labkey.api.admin.CoreUrls;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.WarningProvider;
import org.labkey.api.view.template.WarningService;
import org.labkey.api.view.template.Warnings;
import org.labkey.core.CoreModule;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.labkey.api.util.PageFlowUtil.urlProvider;

public class WarningServiceImpl implements WarningService
{
    private static Collection<HtmlString> STATIC_ADMIN_WARNINGS = null;

    private final Collection<WarningProvider> _providers = new CopyOnWriteArrayList<>();

    @Override
    public void register(WarningProvider provider)
    {
        _providers.add(provider);
    }

    @Override
    public void forEachProvider(Consumer<WarningProvider> consumer)
    {
        _providers.forEach(consumer);
    }

    // Check warning conditions that will never change after the server has started up. This will be called
    // once per server session; no need to test on every request.
    private static Collection<HtmlString> getStaticAdminWarnings()
    {
        if (STATIC_ADMIN_WARNINGS != null)
        {
            return STATIC_ADMIN_WARNINGS;
        }

        List<HtmlString> messages = new LinkedList<>();
        Warnings warnings = Warnings.of(messages);
        WarningService.get().forEachProvider(p -> p.addStaticWarnings(warnings, WarningService.get().showAllWarnings()));

        messages = Collections.unmodifiableList(messages);
        if (ModuleLoader.getInstance().isStartupComplete())
        {
            // We should have our full list of warnings at this point, so safe to cache them
            STATIC_ADMIN_WARNINGS = messages;
        }
        return messages;
    }

    private static final String DISMISSAL_SCRIPT_FORMAT =
        """
        <script type="text/javascript">
            (function($) {
                function dismissMessage() {
                    var config = {
                        url: %1$s,
                        method: 'POST',
                        success: function () {$('.lk-dismissable-warn').hide();$('#headerWarningIcon').show();},
                        failure: LABKEY.Utils.displayAjaxErrorResponse
                    };
                    LABKEY.Ajax.request(config);\s
                    return false;
                }
                $('body').on('click', 'a.lk-dismissable-warn-close', function() {
                    dismissMessage();
                });
            })(jQuery);
        </script>
        """;

    @Override
    public Warnings getWarnings(ViewContext context)
    {
        if (null == context)
            throw new IllegalStateException("ViewContext was null");
        if (null == context.getUser())
            throw new IllegalStateException("ViewContext.getUser() was null");
        if (null == context.getRequest())
            throw new IllegalStateException("ViewContext.getUser() was null");

        // Collect warnings
        List<HtmlString> warningMessages = new LinkedList<>();
        User user = context.getUser();

        if (null != user && user.hasSiteAdminPermission())
            warningMessages.addAll(getStaticAdminWarnings());

        Warnings warnings = Warnings.of(warningMessages);
        WarningService.get().forEachProvider(p->p.addDynamicWarnings(warnings, context, showAllWarnings()));

        return warnings;
    }

    @Override
    public HtmlString getWarningsHtml(Warnings warnings, ViewContext context)
    {
        HtmlStringBuilder html = HtmlStringBuilder.of(HtmlString.unsafe("<div class=\"alert alert-warning alert-dismissable\">\n<a href=\"#\" class=\"close lk-dismissable-warn-close\" data-dismiss=\"alert\" aria-label=\"dismiss\" title=\"dismiss\">×</a>\n<div class=\"lk-dismissable-warn\">"));
        appendMessageContent(warnings, html);
        html.append(HtmlString.unsafe("</div>\n"));
        CoreUrls coreUrls = urlProvider(CoreUrls.class);
        if (coreUrls != null)
        {
            String dismissURL = coreUrls.getDismissWarningsActionURL(context).toString();
            html.append(HtmlString.unsafe(String.format(DISMISSAL_SCRIPT_FORMAT, PageFlowUtil.jsString(dismissURL))));
        }
        html.append(HtmlString.unsafe("</div>"));

        return html.getHtmlString();
    }

    private void appendMessageContent(Warnings warnings, HtmlStringBuilder html)
    {
        if (!warnings.isEmpty())
        {
            List<HtmlString> messages = warnings.getMessages();

            if (messages.size() == 1)
                html.append(messages.get(0));
            else
            {
                html.startTag("ul");
                for (HtmlString msg : messages)
                    html.startTag("li").append(msg).endTag("li");
                html.endTag("ul");
            }
        }
    }

    @Override
    public void rerunSchemaCheck()
    {
        ((CoreModule)ModuleLoader.getInstance().getCoreModule()).rerunSchemaCheck();
    }
}
