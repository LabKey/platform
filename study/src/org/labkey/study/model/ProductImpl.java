/*
 * Copyright (c) 2013-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.study.Product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: cnathe
 * Date: 12/26/13
 */
public class ProductImpl implements Product
{
    private Container _container;
    private int _rowId;
    private String _label;
    private String _role;
    private String _type;
    private List<ProductAntigenImpl> _antigens;
    private List<DoseAndRoute> _doseAndRoutes;

    // from TreatmentProductMap (not serialized with product)
    private String _dose;
    private String _route;

    public ProductImpl()
    {}

    public ProductImpl(Container container, String label, String role)
    {
        _container = container;
        _label = label;
        _role = role;
    }

    public ProductImpl(Container container, String label, String role, String type)
    {
        this(container, label, role);
        _type = type;
    }

    public boolean isNew()
    {
        return _rowId == 0;
    }

    public Object getPrimaryKey()
    {
        return getRowId();
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public String getRole()
    {
        return _role;
    }

    public void setRole(String role)
    {
        _role = role;
    }

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }

    public List<ProductAntigenImpl> getAntigens()
    {
        return _antigens;
    }

    public void setAntigens(List<ProductAntigenImpl> antigens)
    {
        _antigens = antigens;
    }

    public String getDose()
    {
        return _dose;
    }

    public void setDose(String dose)
    {
        _dose = dose;
    }

    public String getRoute()
    {
        return _route;
    }

    public void setRoute(String route)
    {
        _route = route;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public List<DoseAndRoute> getDoseAndRoutes()
    {
        return _doseAndRoutes;
    }

    public void setDoseAndRoutes(List<DoseAndRoute> doseAndRoutes)
    {
        _doseAndRoutes = doseAndRoutes;
    }

    public Map<String, Object> serialize()
    {
        Map<String, Object> props = new HashMap<>();
        props.put("RowId", getRowId());
        props.put("Label", getLabel());
        props.put("Role", getRole());
        props.put("Type", getType());
        return props;
    }

    public static ProductImpl fromJSON(@NotNull JSONObject o, Container container)
    {
        ProductImpl product = new ProductImpl(container, o.getString("Label"), o.getString("Role"), o.getString("Type"));

        if (o.containsKey("RowId"))
            product.setRowId(o.getInt("RowId"));

        if (o.containsKey("Antigens") && o.get("Antigens")  instanceof JSONArray)
        {
            JSONArray antigensJSON = (JSONArray) o.get("Antigens");

            List<ProductAntigenImpl> antigens = new ArrayList<>();
            for (int j = 0; j < antigensJSON.length(); j++)
                antigens.add(ProductAntigenImpl.fromJSON(antigensJSON.getJSONObject(j), container));

            product.setAntigens(antigens);
        }

        if (o.containsKey("DoseAndRoute") && o.get("DoseAndRoute")  instanceof JSONArray)
        {
            JSONArray doseJSON = (JSONArray) o.get("DoseAndRoute");

            List<DoseAndRoute> doseAndRoutes = new ArrayList<>();
            for (int j = 0; j < doseJSON.length(); j++)
                doseAndRoutes.add(DoseAndRoute.fromJSON(doseJSON.getJSONObject(j), container, product.getRowId()));

            product.setDoseAndRoutes(doseAndRoutes);
        }

        return product;
    }
}
