/*
 * Copyright (c) 2005 LabKey Software, LLC
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
package org.labkey.pipeline.status;

import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.ActionButton;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.ACL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.DisplayElement;

import java.io.Writer;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * ProviderButtonBar class
 * <p/>
 * Created: Nov 1, 2005
 *
 * @author bmaclean
 */
public class ProviderButtonBar extends ButtonBar
{
    private String _providerCurrent;
    private Map<String, List<DisplayElement>> _providerElements =
            new HashMap<String, List<DisplayElement>>();

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        if (!shouldRender(ctx))
            return;

        Map cols = ctx.getRow();
        if (cols == null)
            return;
        
        String providerName = (String) cols.get("provider");
        if (providerName != null)
        {
            List<DisplayElement> elements = getList();

            _providerCurrent = providerName;

            if (getList() == null)
            {
                PipelineProvider provider = PipelineService.get().getPipelineProvider(providerName);
                if (provider != null)
                {
                    List<PipelineProvider.StatusAction> actions = provider.addStatusActions();
                    if (actions != null && actions.size() > 0)
                    {
                        List<DisplayElement> baseElements = elements;
                        elements = new ArrayList<DisplayElement>();
                        for (DisplayElement element : baseElements)
                            elements.add(element);

                        for (PipelineProvider.StatusAction action : actions)
                        {
                            ActionButton button = new ActionButton("providerAction.view?" +
                                    "name=" + PageFlowUtil.encode(action.getLabel()) + "&amp;" +
                                    "rowId=${rowId}", action.getLabel());
                            button.setActionType(ActionButton.Action.LINK);
                            if (!ctx.getViewContext().getUser().isAdministrator())
                                button.setDisplayPermission(ACL.PERM_UPDATE);

                            String visible = action.getVisible();
                            if (visible != null)
                            {
                                try
                                {
                                    button.setVisibleExpr(visible);
                                }
                                catch (Exception e)
                                {
                                    assert false : "Compile error";
                                }
                            }

                            elements.add(button);
                        }
                    }
                }

                _providerElements.put(_providerCurrent, elements);
            }
        }

        super.render(ctx, out);

        _providerCurrent = null;
    }

    public List<DisplayElement> getList()
    {
        if (_providerCurrent != null)
            return _providerElements.get(_providerCurrent);
        return super.getList();
    }
}
