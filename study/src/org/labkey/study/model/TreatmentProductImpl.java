/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.study.TreatmentProduct;

import java.util.HashMap;
import java.util.Map;

/**
 * User: cnathe
 * Date: 12/27/13
 */
public class TreatmentProductImpl implements TreatmentProduct
{
    private Container _container;
    private int _rowId;
    private int _treatmentId;
    private int _productId;
    private String _dose;
    private String _route;

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

    public Map<String, Object> serialize()
    {
        Map<String, Object> props = new HashMap<>();
        props.put("RowId", getRowId());
        props.put("TreatmentId", getTreatmentId());
        props.put("ProductId", getProductId());
        props.put("Dose", getDose());
        props.put("Route", getRoute());
        return props;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }
}
