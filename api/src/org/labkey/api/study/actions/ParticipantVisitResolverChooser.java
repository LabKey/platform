/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.InputBuilder;
import org.labkey.api.util.JavaScriptFragment;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.writer.HtmlWriter;

import java.util.List;

import static org.labkey.api.util.DOM.Attribute.colspan;
import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.SCRIPT;
import static org.labkey.api.util.DOM.TABLE;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.id;

public class ParticipantVisitResolverChooser extends SimpleDisplayColumn
{
    private final List<ParticipantVisitResolverType> _resolvers;
    private final String _typeInputName;
    private final ColumnInfo _boundColumn;

    public ParticipantVisitResolverChooser(String typeInputName, List<ParticipantVisitResolverType> resolvers, ColumnInfo boundColumn)
    {
        _resolvers = resolvers;
        _typeInputName = typeInputName;
        _boundColumn = boundColumn;
        setCaption("Participant/Visit");
    }

    @Override
    public boolean isEditable()
    {
        return true;
    }

    protected boolean isDisabledInput()
    {
        return _boundColumn.getDefaultValueType() == DefaultValueType.FIXED_NON_EDITABLE;
    }

    @Override
    public void renderInputHtml(RenderContext ctx, HtmlWriter out, Object value)
    {
        if (_resolvers.isEmpty())
        {
            InputBuilder.hidden().name(_typeInputName).appendTo(out);
            out.write("None available");
            out.write(HtmlString.BR);
            return;
        }

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
            selected = _resolvers.getFirst();
        }

        ParticipantVisitResolverType finalSelected = selected;

        // Keep track of listeners that want to know when the resolver selection has changed
        String listenerScript = "var participantVisitResolverSelectionListeners = []; function addParticipantVisitResolverSelectionChangeListener(callback){ participantVisitResolverSelectionListeners.push(callback); }\n";
        SCRIPT(JavaScriptFragment.unsafe(listenerScript)).appendTo(out);

        if (_resolvers.size() < 2)
        {
            InputBuilder.hidden().name(_typeInputName).value(selected.getName()).appendTo(out);
            out.write(selected.getDescription());
            out.write(HtmlString.BR);
            try
            {
                selected.render(ctx);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        else
        {
            TABLE(
                TR(
                    TD(
                        at(colspan, 2),
                        "My data is identified by:"
                    )
                ),
                (DOM.Renderable) ret -> {
                    for (ParticipantVisitResolverType resolver : _resolvers)
                    {
                        String script =
                            "typeElements = document.getElementsByName(" + PageFlowUtil.jsString(_typeInputName)+ "); " +
                            "for (i = 0; i < typeElements.length; i++) " +
                            "{ var resolverSubSectionDiv = document.getElementById('ResolverDiv-' + typeElements[i].value); " +
                            " if (resolverSubSectionDiv != null) resolverSubSectionDiv.style.display='none'; } ";

                        RenderSubSelectors renderSubs = renderResolverSubSelectors(resolver);
                        if (renderSubs != RenderSubSelectors.NONE)
                            script += "document.getElementById('ResolverDiv-' + this.value).style.display='block';";

                        // Notify listeners that the selection has changed
                        script += "for (i = 0; i < participantVisitResolverSelectionListeners.length; i++) { participantVisitResolverSelectionListeners[i].call(this); } ";
                        HttpView.currentPageConfig().addHandler("RadioBtn-" + resolver.getName(), "click", script);

                        TR(
                            TD(
                                InputBuilder.radio().name(_typeInputName).value(resolver.getName()).id("RadioBtn-" + resolver.getName()).checked(finalSelected == resolver).disabled(disabledInput)
                            ),
                            TD(
                                resolver.getDescription()
                            )
                        ).appendTo(out);

                        if (renderSubs != RenderSubSelectors.NONE)
                        {
                            TR(
                                TD(),
                                TD(
                                    DIV(
                                        id("ResolverDiv-" + resolver.getName()).at(finalSelected != resolver, style, "display:none"),
                                        (DOM.Renderable) ret2 -> {
                                            try
                                            {
                                                ctx.put(RenderSubSelectors.class.getSimpleName(), renderSubs);
                                                resolver.render(ctx);
                                            }
                                            catch (Exception e)
                                            {
                                                throw new RuntimeException(e);
                                            }
                                            return ret2;
                                        }
                                    ),
                                    disabledInput ? InputBuilder.hidden().name(_typeInputName).value(finalSelected.getName()).getHtmlString() : null
                                )
                            ).appendTo(out);
                        }
                    }

                    return ret;
                }
            ).appendTo(out);
        }
    }

    /**
     * Rather than a boolean to render subselectors for the resolver type, there are cases where
     * some, but not all, of the resolver's subselection options are to be displayed.
     */
    public enum RenderSubSelectors
    {
        ALL,
        NONE,
        PARTIAL
    }

    protected RenderSubSelectors renderResolverSubSelectors(ParticipantVisitResolverType resolver)
    {
        return RenderSubSelectors.ALL;
    }

    @Override
    protected Object getInputValue(RenderContext ctx)
    {
        TableViewForm viewForm = ctx.getForm();
        // check to see if our insert view has explicit initial values:
        if (null != viewForm && viewForm.getValuesToBind().containsKey(_typeInputName))
            return viewForm.getAsString(_typeInputName);
        return ctx.get(_typeInputName);
    }
}
