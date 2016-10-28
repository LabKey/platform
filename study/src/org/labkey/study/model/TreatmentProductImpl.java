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
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.study.TreatmentProduct;
import org.labkey.api.util.Pair;
import org.labkey.study.StudySchema;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * User: cnathe
 * Date: 12/27/13
 */
public class TreatmentProductImpl implements TreatmentProduct
{
    public static final String PRODUCT_DOSE_DELIMITER = "-#-";
    private Container _container;
    private int _rowId;
    private int _treatmentId;
    private int _productId;
    private String _dose;
    private String _route;
    private String _doseAndRoute;
    private String _productDoseRoute;

    public TreatmentProductImpl()
    {}

    public TreatmentProductImpl(Container container, int treatmentId, int productId)
    {
        _container = container;
        _treatmentId = treatmentId;
        _productId = productId;
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

    public int getTreatmentId()
    {
        return _treatmentId;
    }

    public void setTreatmentId(int treatmentId)
    {
        _treatmentId = treatmentId;
    }

    public int getProductId()
    {
        return _productId;
    }

    public void setProductId(int productId)
    {
        _productId = productId;
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

    public String getDoseAndRoute()
    {
        return _doseAndRoute;
    }

    public void setDoseAndRoute(String doseAndRoute)
    {
        _doseAndRoute = doseAndRoute;
    }

    public Map<String, Object> serialize()
    {
        syncDoseAndRoute();
        Map<String, Object> props = new HashMap<>();
        props.put("RowId", getRowId());
        props.put("TreatmentId", getTreatmentId());
        props.put("ProductId", getProductId());
        props.put("Dose", getDose());
        props.put("Route", getRoute());
        props.put("DoseAndRoute", getDoseAndRoute());
        props.put("ProductDoseRoute", getProductDoseRoute());

        return props;
    }

    /**
     * Keeps the dose, route, and doseAndRoute fields synchronized
     */
    private void syncDoseAndRoute()
    {
        if (getDoseAndRoute() == null && (getDose() != null || getRoute() != null) && getProductId() > 0)
        {
            // get the entry from the DoseAndRoute table so we can serialize the label
            DoseAndRoute doseAndRoute = TreatmentManager.getInstance().getDoseAndRoute(getContainer(), getDose(), getRoute(), getProductId());
            if (doseAndRoute != null)
            {
                setDoseAndRoute(doseAndRoute.getLabel());
                setProductDoseRoute(String.valueOf(getProductId() + PRODUCT_DOSE_DELIMITER + doseAndRoute.getLabel()));
            }
        }
        else if (getDoseAndRoute() != null && getDose() == null && getRoute() == null)
        {
            Pair<String, String> parts = DoseAndRoute.parseFromLabel(getDoseAndRoute());
            if (parts != null)
            {
                DoseAndRoute doseAndRoute = TreatmentManager.getInstance().getDoseAndRoute(getContainer(), parts.getKey(), parts.getValue(), getProductId());
                if (doseAndRoute != null)
                {
                    setDose(doseAndRoute.getDose());
                    setRoute(doseAndRoute.getRoute());
                }
            }
        }
        else if (getDoseAndRoute() == null && getDose() == null && getRoute() == null && getProductId() > 0)
        {
            setProductDoseRoute(String.valueOf(getProductId() + PRODUCT_DOSE_DELIMITER));
        }
    }

    public static TreatmentProductImpl fromJSON(@NotNull JSONObject o, Container container)
    {
        TreatmentProductImpl treatmentProduct = new TreatmentProductImpl();
        //treatmentProduct.setDose(o.getString("Dose"));
        //treatmentProduct.setRoute(o.getString("Route"));
        treatmentProduct.setContainer(container);
        if (o.containsKey("ProductId") && o.get("ProductId") instanceof Integer)
            treatmentProduct.setProductId(o.getInt("ProductId"));
        if (o.containsKey("TreatmentId") && o.get("TreatmentId") instanceof Integer)
            treatmentProduct.setTreatmentId(o.getInt("TreatmentId"));
        if (o.containsKey("RowId"))
            treatmentProduct.setRowId(o.getInt("RowId"));
        if (o.containsKey("DoseAndRoute"))
            treatmentProduct.setDoseAndRoute(o.getString("DoseAndRoute"));
        if (o.containsKey("ProductDoseRoute"))
            treatmentProduct.populateProductDoseRoute(o.getString("ProductDoseRoute"));

        return treatmentProduct;
    }

    public void setProductDoseRoute(String productDoseRoute)
    {
        this._productDoseRoute = productDoseRoute;
    }

    public String getProductDoseRoute()
    {
        return _productDoseRoute;
    }

    private void populateProductDoseRoute(String productDoseRoute)
    {
        if (productDoseRoute == null)
            return;
        setProductDoseRoute(productDoseRoute);
        String[] parts = productDoseRoute.split(PRODUCT_DOSE_DELIMITER);
        setProductId(Integer.parseInt(parts[0]));
        if (parts.length > 1)
            setDoseAndRoute(parts[1]);
    }

    public boolean isSameTreatmentProductWith(TreatmentProductImpl other)
    {
        if (other == null)
            return false;
        if (this.getProductId() != other.getProductId())
            return false;
        if (this.getProductDoseRoute() != null && other.getProductDoseRoute() != null)
            return this.getProductDoseRoute().equals(other.getProductDoseRoute());

        if (this.getDose() != null)
        {
            if (other.getDose() == null || !this.getDose().equals(other.getDose()))
                return false;
        }
        else if (other.getDose() != null)
            return false;

        if (this.getRoute() != null)
        {
            if (other.getRoute() == null || !this.getRoute().equals(other.getRoute()))
                return false;
        }
        else if (other.getRoute() != null)
            return false;

        return true;
    }
}
