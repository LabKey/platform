/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.study.model;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.util.Pair;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by klum on 9/23/2016.
 */
public class DoseAndRoute
{
    private Integer _rowId;
    private String _dose;
    private String _route;
    private int _productId;
    private Container _container;

    public enum keys
    {
        RowId,
        Dose,
        Route,
        ProductId
    }

    public boolean isNew()
    {
        return _rowId == null;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public DoseAndRoute(){}

    public DoseAndRoute(String dose, String route, int productId, Container container)
    {
        _dose = dose;
        _route = route;
        _productId = productId;
        _container = container;
    }

    public @Nullable String getLabel()
    {
        if (_dose != null || _route != null)
            return String.format("%s : %s", StringUtils.trimToEmpty(_dose), StringUtils.trimToEmpty(_route));
        else
            return null;
    }

    public String getDose()
    {
        return StringUtils.trimToNull(_dose);
    }

    public void setDose(String dose)
    {
        _dose = dose;
    }

    public String getRoute()
    {
        return StringUtils.trimToNull(_route);
    }

    public void setRoute(String route)
    {
        _route = route;
    }

    public int getProductId()
    {
        return _productId;
    }

    public void setProductId(int productId)
    {
        _productId = productId;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public static DoseAndRoute fromJSON(@NotNull JSONObject o, Container container, int productId)
    {
        String dose = null;
        String route = null;
        if (o.containsKey(keys.Dose.name()))
            dose = o.getString(keys.Dose.name());
        if (o.containsKey(keys.Route.name()))
            route = o.getString(keys.Route.name());

        DoseAndRoute doseAndRoute = new DoseAndRoute(dose, route, productId, container);
        if (o.containsKey(keys.RowId.name()))
            doseAndRoute.setRowId(o.getInt(keys.RowId.name()));
        return doseAndRoute;
    }

    public Map<String, Object> serialize()
    {
        Map<String, Object> props = new HashMap<>();
        props.put(keys.RowId.name(), getRowId());
        props.put(keys.ProductId.name(), getProductId());
        props.put(keys.Dose.name(), getDose());
        props.put(keys.Route.name(), getRoute());

        return props;
    }

    /**
     * Helper to convert the concatenated label into a dose and/or route portion
     * @return Pair object where the key is the dose and the value is the route
     */
    public static @Nullable
    Pair<String, String> parseFromLabel(String label)
    {
        // need to keep the label generation in sync with code in DoseAndRouteTable label expr column
        if (label != null)
        {
            if (label.contains(":"))
            {
                String[] parts = label.split(":");
                if (parts.length == 2)
                {
                    return new Pair<>(
                        StringUtils.trimToNull(parts[0]),
                        StringUtils.trimToNull(parts[1]));
                }
            }
        }
        return null;
    }
}
