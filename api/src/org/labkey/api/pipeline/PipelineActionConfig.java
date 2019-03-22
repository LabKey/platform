/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Metadata about an available {@link PipelineAction} that can be serialized and sent to the UI to let the user see
 * and invoke the desired action.
 * User: klum
 * Date: Jan 9, 2010
 */
public class PipelineActionConfig
{
    private String _id;
    private String _label;
    private displayState _state;
    private List<PipelineActionConfig> _links = Collections.emptyList();

    public enum displayState {
        enabled,
        disabled,
        toolbar,
        admin,
        hidden
    }

    public PipelineActionConfig(String id, String state, String label)
    {
        this(id, displayState.valueOf(state), label, false);
    }

    public PipelineActionConfig(String id, displayState state, String label)
    {
        this(id, state, label, false);
    }

    /**
     *
     * @param id
     * @param state
     * @param label
     * @param isSingleAction - true if this action does not handle multiple subactions, will
     * usually result in a button without a menu
     */
    public PipelineActionConfig(String id, displayState state, String label, boolean isSingleAction)
    {
        _id = id;
        _state = state;
        _label = label;

        if (isSingleAction)
        {
            setLinks(Collections.singletonList(new PipelineActionConfig(id, state, label, false)));
        }
    }

    public String getId()
    {
        return _id;
    }

    public void setId(String id)
    {
        _id = id;
    }

    public displayState getState()
    {
        return _state;
    }

    public void setState(displayState state)
    {
        _state = state;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public List<PipelineActionConfig> getLinks()
    {
        return _links;
    }

    public void setLinks(List<PipelineActionConfig> links)
    {
        _links = links;
    }

    public JSONObject toJSON()
    {
        JSONObject o = createObject(_id, _state.name(), _label);

        o.put("id", _id == null ? "" : _id);
        o.put("display", _state == null ? "" : _state);
        o.put("label", _label == null ? "" : _label);

        if (!_links.isEmpty())
        {
            JSONArray links = new JSONArray();

            for (PipelineActionConfig link : _links)
            {
                links.put(createObject(link.getId(), link.getState().name(), link.getLabel()));
            }
            o.put("links", links);
        }
        return o;
    }

    // merge changes from the specified configuration
    public void update(PipelineActionConfig config)
    {
        _state = config.getState();
        _label = config.getLabel();

        // update or add any links
        for (PipelineActionConfig link : config.getLinks())
        {
            PipelineActionConfig currentLink = getLink(link.getId());
            if (currentLink != null)
            {
                currentLink.setLabel(link.getLabel());
                currentLink.setState(link.getState());
            }
            else
                _links.add(link);
        }
    }

    private PipelineActionConfig getLink(String id)
    {
        for (PipelineActionConfig link : _links)
        {
            if (link.getId().equals(id))
                return link;
        }
        return null;
    }
    
    private JSONObject createObject(String id, String state, String label)
    {
        JSONObject o = new JSONObject();

        o.put("id", id == null ? "" : id);
        o.put("display", state == null ? "" : state);
        o.put("label", label == null ? "" : label);

        return o;
    }

    public static PipelineActionConfig fromJSON(JSONObject o)
    {
        PipelineActionConfig action = createConfig(o);

        if (action != null)
        {
            JSONArray links = o.getJSONArray("links");
            if (links != null)
            {
                List<PipelineActionConfig> subLinks = new ArrayList<>();
                for (int i=0; i < links.length(); i++)
                {
                    JSONObject link = links.getJSONObject(i);
                    PipelineActionConfig subAction = createConfig(link);

                    if (subAction != null)
                        subLinks.add(subAction);
                }
                action.setLinks(subLinks);
            }
        }
        return action;
    }

    private static PipelineActionConfig createConfig(JSONObject o)
    {
        if (o != null)
        {
            String id = o.getString("id");

            if (!StringUtils.isEmpty(id))
                return new PipelineActionConfig(id, o.getString("display"), o.getString("label"));
        }
        return null;
    }
}
