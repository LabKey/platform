/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

package org.labkey.api.study.actions;

import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.study.assay.*;
import org.labkey.api.gwt.client.DefaultValueType;

import java.io.Writer;
import java.io.IOException;
import java.util.List;

/**
 * User: jeckels
 * Date: Sep 20, 2007
 */
public class ParticipantVisitResolverChooser extends SimpleDisplayColumn
{
    private List<ParticipantVisitResolverType> _resolvers;
    private String _typeInputName;
    private ColumnInfo _boundColumn;

    public ParticipantVisitResolverChooser(String typeInputName, List<ParticipantVisitResolverType> resolvers, ColumnInfo boundColumn)
    {
        _resolvers = resolvers;
        _typeInputName = typeInputName;
        _boundColumn = boundColumn;
        setCaption("Participant/Visit");
    }

    public boolean isEditable()
    {
        return true;
    }

    protected boolean isDisabledInput()
    {
        return _boundColumn.getDefaultValueType() == DefaultValueType.FIXED_NON_EDITABLE;
    }

    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        boolean disabledInput = isDisabledInput();
        ParticipantVisitResolverType selected = null;
        for (ParticipantVisitResolverType resolver : _resolvers)
        {
            if (resolver.getName().equals(value))
            {
                selected = resolver;
                break;
            }
        }
        if (selected == null)
        {
            selected = _resolvers.get(0);
        }

        // Keep track of listeners that want to know when the resolver selection has changed
        out.write("<script type=\"text/javascript\">var participantVisitResolverSelectionListeners = []; function addParticipantVisitResolverSelectionChangeListener(callback){ participantVisitResolverSelectionListeners.push(callback); }</script>");

        if (_resolvers.size() < 2)
        {
            out.write("<input type=\"hidden\" name = \"" + _typeInputName + "\" value=\"" + PageFlowUtil.filter(selected.getName()) + "\"/>" + PageFlowUtil.filter(selected.getDescription()) + "<br/> ");
            try
            {
                selected.render(ctx);
            }
            catch (Exception e)
            {
                throw new IOException(e);
            }
        }
        else
        {
            out.write("<table>\n");
            out.write("<tr><td colspan=\"2\">My data is identified by:</td></tr>");

            for (ParticipantVisitResolverType resolver : _resolvers)
            {
                out.write("<tr><td>");
                out.write("<input");
                out.write(" onClick=\"typeElements = document.getElementsByName('" + _typeInputName+ "'); " +
                        "for (i = 0; i < typeElements.length; i++) " +
                        "{ var resolverSubSectionDiv = document.getElementById('ResolverDiv-' + typeElements[i].value); " +
                        " if (resolverSubSectionDiv != null) resolverSubSectionDiv.style.display='none'; } "
                );

                RenderSubSelectors renderSubs = renderResolverSubSelectors(resolver);
                if (renderSubs != RenderSubSelectors.NONE)
                    out.write("document.getElementById('ResolverDiv-' + this.value).style.display='block';");

                // Notify listeners that the selection has changed
                out.write("for (i = 0; i < participantVisitResolverSelectionListeners.length; i++) { participantVisitResolverSelectionListeners[i].call(this); } ");

                out.write("\" ");
                out.write(" type=\"radio\" " +
                        "name=\"" + _typeInputName + "\"" +
                        ( resolver == selected ? " checked=\"true\"" : "") + " " +
                        "value=\"" + PageFlowUtil.filter(resolver.getName()) + "\"" +
                        "id=\"RadioBtn-" + PageFlowUtil.filter(resolver.getName()) + "\"" +
                        (disabledInput ? " DISABLED" : "") +
                        ">");
                
                out.write("</td><td>");
                out.write(PageFlowUtil.filter(resolver.getDescription()));
                out.write("</td></tr>");

                if (renderSubs != RenderSubSelectors.NONE)
                {

                    out.write("<tr><td></td><td>");
                    out.write("<div id=\"ResolverDiv-" + resolver.getName() + "\"" + (selected == resolver ? "" : "style=\"display:none\"") +  ">");
                    try
                    {
                        ctx.put(RenderSubSelectors.class.getSimpleName(), renderSubs);
                        resolver.render(ctx);
                    }
                    catch (Exception e)
                    {
                        throw (IOException)new IOException().initCause(e);
                    }
                    out.write("</div>");
                    if (disabledInput)
                        out.write("<input type=\"hidden\" name=\"" + _typeInputName + "\" value=\"" + PageFlowUtil.filter(selected.getName()) + "\">");
                    out.write("</td></tr>");
                }
            }
            out.write("</table>");
        }
    }

    /**
     * Rather than a boolean to render subselectors for the resolver type, there are cases where
     * some, but not all, of the resolver's subselection options are to be displayed.
     */
    public static enum RenderSubSelectors
    {
        ALL,
        NONE,
        PARTIAL
    }

    protected RenderSubSelectors renderResolverSubSelectors(ParticipantVisitResolverType resolver)
    {
        return RenderSubSelectors.ALL;
    }

    protected Object getInputValue(RenderContext ctx)
    {
        TableViewForm viewForm = ctx.getForm();
        // check to see if our insert view has explicit initial values:
        if (null != viewForm && viewForm.getStrings().containsKey(_typeInputName))
            return viewForm.get(_typeInputName);
        return ctx.get(_typeInputName);
    }
}
