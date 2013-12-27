package org.labkey.study.model;

import org.labkey.api.study.ProductAntigen;

/**
 * User: cnathe
 * Date: 12/26/13
 */
public class ProductAntigenImpl implements ProductAntigen
{
    private int _rowId;
    private int _productId;
    private String _gene;
    private String _subType;
    private String _genBankId;
    private String _sequence;

    public ProductAntigenImpl()
    {
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
}
