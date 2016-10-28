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
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.study.Treatment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: cnathe
 * Date: 12/27/13
 */
public class TreatmentImpl implements Treatment
{
    private Container _container;
    private int _rowId;
    private String _tempRowId; // used to map new treatment records being inserted to their usage in TreatmentVisitMapImpl
    private String _label;
    private String _description;
    private List<TreatmentProductImpl> _treatmentProducts;
    private List<ProductImpl> _products;

    public TreatmentImpl()
    {}

    public TreatmentImpl(Container container, String label, String description)
    {
        _container = container;
        _label = label;
        _description = description;
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

    private void setTempRowId(String tempRowId)
    {
        _tempRowId = tempRowId;
    }

    public String getTempRowId()
    {
        return _tempRowId;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public List<TreatmentProductImpl> getTreatmentProducts()
    {
        return _treatmentProducts;
    }

    public void setTreatmentProducts(List<TreatmentProductImpl> treatmentProducts)
    {
        _treatmentProducts = treatmentProducts;
    }

    public List<ProductImpl> getProducts()
    {
        return _products;
    }

    public void setProducts(List<ProductImpl> products)
    {
        _products = products;
    }

    public void addProduct(ProductImpl product)
    {
        if (_products == null)
            _products = new ArrayList<>();

        _products.add(product);
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public Map<String, Object> serialize()
    {
        Map<String, Object> props = new HashMap<>();
        props.put("RowId", getRowId());
        props.put("Label", getLabel());
        props.put("Description", getDescription());
        return props;
    }

    public Sort getProductSort()
    {
        // sort the product list to match the manage study products page (i.e. Immunogens before Adjuvants)
        Sort sort = new Sort();
        sort.appendSortColumn(FieldKey.fromParts("ProductId", "Role"), Sort.SortDirection.DESC, false);
        sort.appendSortColumn(FieldKey.fromParts("ProductId", "RowId"), Sort.SortDirection.ASC, false);
        return sort;
    }

    public static TreatmentImpl fromJSON(@NotNull JSONObject o, Container container)
    {
        TreatmentImpl treatment = new TreatmentImpl(container, o.getString("Label"), o.getString("Description"));

        if (o.containsKey("RowId"))
        {
            if (o.get("RowId") instanceof Integer)
                treatment.setRowId(o.getInt("RowId"));
            else
                treatment.setTempRowId(o.getString("RowId"));
        }

        if (o.containsKey("Products") && o.get("Products")  instanceof JSONArray)
        {
            JSONArray productsJSON = (JSONArray) o.get("Products");

            List<TreatmentProductImpl> treatmentProducts = new ArrayList<>();
            for (int j = 0; j < productsJSON.length(); j++)
                treatmentProducts.add(TreatmentProductImpl.fromJSON(productsJSON.getJSONObject(j), container));

            treatment.setTreatmentProducts(treatmentProducts);
        }

        return treatment;
    }

    public boolean isSameTreatmentProductsWith(TreatmentImpl other)
    {
        if (other == null)
            return false;
        if (this.getTreatmentProducts() == null)
        {
            return other.getTreatmentProducts() == null;
        }
        else
        {
            if (other.getTreatmentProducts() == null)
                return false;

            if (this.getTreatmentProducts().size() != other.getTreatmentProducts().size())
                return false;

            boolean hasMismatch = false;
            for (TreatmentProductImpl product : this.getTreatmentProducts())
            {
                boolean foundMatch =false;
                for (TreatmentProductImpl otherProduct : other.getTreatmentProducts())
                {
                    if (product.isSameTreatmentProductWith(otherProduct))
                    {
                        foundMatch = true;
                        break;
                    }
                }
                if (!foundMatch)
                {
                    hasMismatch = true;
                    break;
                }
            }
            return !hasMismatch;
        }
    }
}
