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
import org.labkey.api.study.ProductAntigen;

import java.util.HashMap;
import java.util.Map;

/**
 * User: cnathe
 * Date: 12/26/13
 */
public class ProductAntigenImpl implements ProductAntigen
{
    private Container _container;
    private int _rowId;
    private int _productId;
    private String _gene;
    private String _subType;
    private String _genBankId;
    private String _sequence;

    public ProductAntigenImpl()
    {}

    public ProductAntigenImpl(Container container, int productId, String gene, String subType)
    {
        _container = container;
        _productId = productId;
        _gene = gene;
        _subType = subType;
    }

    public ProductAntigenImpl(Container container, String gene, String subType, String genBankId, String sequence)
    {
        this(container, 0, gene, subType);
        _genBankId = genBankId;
        _sequence = sequence;
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

    public int getProductId()
    {
        return _productId;
    }

    public void setProductId(int productId)
    {
        _productId = productId;
    }

    public String getGene()
    {
        return _gene;
    }

    public void setGene(String gene)
    {
        _gene = gene;
    }

    public String getSubType()
    {
        return _subType;
    }

    public void setSubType(String subType)
    {
        _subType = subType;
    }

    public String getGenBankId()
    {
        return _genBankId;
    }

    public void setGenBankId(String genBankId)
    {
        _genBankId = genBankId;
    }

    public String getSequence()
    {
        return _sequence;
    }

    public void setSequence(String sequence)
    {
        _sequence = sequence;
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
        props.put("ProductId", getProductId());
        props.put("Gene", getGene());
        props.put("SubType", getSubType());
        props.put("GenBankId", getGenBankId());
        props.put("Sequence", getSequence());
        return props;
    }

    public static ProductAntigenImpl fromJSON(@NotNull JSONObject o, Container container)
    {
        ProductAntigenImpl antigen = new ProductAntigenImpl(
            container, o.getString("Gene"), o.getString("SubType"),
            o.getString("GenBankId"), o.getString("Sequence")
        );

        if (o.containsKey("RowId"))
            antigen.setRowId(o.getInt("RowId"));

        if (o.containsKey("ProductId"))
            antigen.setProductId(o.getInt("ProductId"));

        return antigen;
    }
}
