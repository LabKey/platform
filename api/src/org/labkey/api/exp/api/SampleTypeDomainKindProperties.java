package org.labkey.api.exp.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//TODO rename? SampleTypeDomainDetails?
/**
 * Small class with only the properties and fields (de)serialized to the DomainKindProperties (avoids hassle of pasting @JsonIgnores all over three classes.
 */
public class SampleTypeDomainKindProperties implements DomainKindProperties
{
    public SampleTypeDomainKindProperties()
    {
    }

    public SampleTypeDomainKindProperties(ExpSampleSet ss)
    {
        if (ss != null)
        {
            this.name = ss.getName();
            this.nameExpression = ss.getNameExpression();
            this.domainId = ss.getDomain().getTypeId();
            this.rowId = ss.getRowId();
            this.lsid = ss.getLSID();
            this.description = ss.getDescription();
            this.idCols = new ArrayList<>();
            ss.getIdCols().forEach(col -> this.idCols.add(col.getPropertyId()));  //TODO verify this...

            try
            {
                this.importAliases = ss.getImportAliasMap();
            }
            catch (IOException e)
            {
                throw new RuntimeException("Unable to parse parent alias mappings: ", e);
            }
        }
    }

    private String name;
    private String nameExpression;
    private Map<String, String> importAliases;
    private int rowId;
    private int domainId;
    private String lsid;

    //Ignored on import/save, use Domain.description instead
    private String description;
    private List<Integer> idCols;
    private Integer parentCol;

    public void setIdCols(List<Integer> idCols)
    {
        this.idCols = idCols;
    }

    public List<Integer> getIdCols()
    {
        return this.idCols;
    }


    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getDescription()
    {
        return this.description;
    }

    public void setLsid(String lsid)
    {
        this.lsid = lsid;
    }

    public String getLsid()
    {
        return this.lsid;
    }

    public void setDomainId(int domainId)
    {
        this.domainId = domainId;
    }

    public int getDomainId()
    {
        return this.domainId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public int getRowId()
    {
        return this.rowId;
    }

    public void setImportAliases(Map<String, String> importAliases)
    {
        this.importAliases = importAliases;
    }

    public Map<String, String> getImportAliases()
    {
        return this.importAliases;
    }

    public void setNameExpression(String nameExpression)
    {
        this.nameExpression = nameExpression;
    }

    public String getNameExpression()
    {
        return this.nameExpression;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return this.name;
    }

    @JsonIgnore
    public JSONObject toJSONObject()
    {
        //TODO probably need to do more here... see MassSpecFractionsDomainKind.createDomain
        return new JSONObject(this);
    }

    public Integer getParentCol()
    {
        return parentCol;
    }

    public void setParentCol(Integer parentCol)
    {
        this.parentCol = parentCol;
    }
}
