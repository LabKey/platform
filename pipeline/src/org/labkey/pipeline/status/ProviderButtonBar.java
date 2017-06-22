/*
 * Copyright (c) 2005-2017 LabKey Corporation
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
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.ActionURL;
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
            new HashMap<>();
    private PipelineStatusFile _statusFile;

    public ProviderButtonBar(PipelineStatusFile sf)
    {
        _statusFile = sf;
        setStyle(Style.separateButtons);
    }


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
                    List<PipelineProvider.StatusAction> actions = provider.addStatusActions();
                    if (actions != null && actions.size() > 0)
                    {
                        List<DisplayElement> baseElements = elements;
                        elements = new ArrayList<>();
                        for (DisplayElement element : baseElements)
                            elements.add(element);

                        for (PipelineProvider.StatusAction action : actions)
                        {
                            if (action.isVisible(_statusFile))
                            {
                                ActionURL url = new ActionURL(StatusController.ProviderActionAction.class, _statusFile.lookupContainer());
                                url.addParameter("name", action.getLabel());
                                url.addParameter("rowId", _statusFile.getRowId());
                                ActionButton button = new ActionButton(url, action.getLabel());
                                button.setActionType(ActionButton.Action.LINK);
                                if (!ctx.getViewContext().getUser().hasRootAdminPermission())
                                    button.setDisplayPermission(UpdatePermission.class);

                                elements.add(button);
                            }
                        }
                    }
                }

                Map<String, List<DisplayElement>> containerElements = _providerContainerElements.get(_providerCurrent);
                if (containerElements == null)
                {
                    containerElements = new HashMap<>();
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
            Map<String, List<DisplayElement>> containerElements = _providerContainerElements.get(_providerCurrent);
            if (containerElements == null)
                return null;            
            return containerElements.get(_containerCurrent);
        }
        return super.getList();
    }
}
