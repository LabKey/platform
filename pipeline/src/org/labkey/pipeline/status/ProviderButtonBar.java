/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

import org.labkey.api.data.*;
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
    private String _containerCurrent;
    private Map<String, Map<String, List<DisplayElement>>> _providerContainerElements =
            new HashMap<String, Map<String, List<DisplayElement>>>();

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        if (!shouldRender(ctx))
            return;

        Map cols = ctx.getRow();
        if (cols == null)
            return;
        
        String providerName = (String) cols.get("Provider");
        String containerId = (String) cols.get("Container");
        if (providerName != null && containerId != null)
        {
            List<DisplayElement> elements = getList();

            _providerCurrent = providerName;
            _containerCurrent = containerId;

            if (getList() == null)
            {
                PipelineProvider provider = PipelineService.get().getPipelineProvider(providerName);
                Container container = ContainerManager.getForId(containerId);
                if (provider != null && container != null)
                {
                    List<PipelineProvider.StatusAction> actions = provider.addStatusActions(container);
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

                Map<String, List<DisplayElement>> containerElements = _providerContainerElements.get(_providerCurrent);
                if (containerElements == null)
                {
                    containerElements = new HashMap<String, List<DisplayElement>>();
                    _providerContainerElements.put(_providerCurrent, containerElements);
                }
                containerElements.put(_containerCurrent, elements);
            }
        }

        super.render(ctx, out);

        _providerCurrent = null;
        _containerCurrent = null;
    }

    public List<DisplayElement> getList()
    {
        if (_providerCurrent != null && _containerCurrent != null)
        {
            Map<String, List<DisplayElement>> containerElemenets = _providerContainerElements.get(_providerCurrent);
            if (containerElemenets == null)
                return null;            
            return containerElemenets.get(_containerCurrent);
        }
        return super.getList();
    }
}
