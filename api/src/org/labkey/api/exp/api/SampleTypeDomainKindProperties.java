package org.labkey.api.exp.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Small class with only the properties and fields (de)serialized to the DomainKindProperties (avoids hassle of pasting @JsonIgnores all over three classes.
 */
public class SampleTypeDomainKindProperties implements Cloneable
{
    public SampleTypeDomainKindProperties()
    {
    }

    public SampleTypeDomainKindProperties(ExpSampleType st)
    {
        if (st != null)
        {
            this.name = st.getName();
            this.nameExpression = st.getNameExpression();
            this.aliquotNameExpression = st.getAliquotNameExpression();
            this.labelColor = st.getLabelColor();
            this.metricUnit = st.getMetricUnit();
            this.domainId = st.getDomain().getTypeId();
            this.rowId = st.getRowId();
            this.lsid = st.getLSID();
            this.description = st.getDescription();
            this.idCols = Collections.emptyList();
            this.autoLinkTargetContainerId = null != st.getAutoLinkTargetContainer() ? st.getAutoLinkTargetContainer().getId() : "";
            this.autoLinkCategory = st.getAutoLinkCategory();
            this.category = st.getCategory();
            if (st.hasIdColumns())
            {
                this.idCols = st.getIdCols().stream().map(col -> col.getPropertyId()).collect(Collectors.toList());
            }

            try
            {
                this.importAliases = st.getImportAliasMap();
            }
            catch (IOException e)
            {
                throw new RuntimeException("Unable to parse parent alias mappings: ", e);
            }
        }
    }

    private String nameExpression;
    private String aliquotNameExpression;
    private String labelColor;
    private String metricUnit;
    private Map<String, String> importAliases;
    private int rowId;
    private int domainId;
    private String lsid;
    private List<Integer> idCols;
    private String autoLinkTargetContainerId;
    private String autoLinkCategory;
    private Integer parentCol;
    private String category;

    //Ignored on import/save, use Domain.name & Domain.description instead
    private String name;
    private String description;

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

    public String getAliquotNameExpression()
    {
        return this.aliquotNameExpression;
    }

    public void setAliquotNameExpression(String nameExpression)
    {
        this.aliquotNameExpression = nameExpression;
    }

    public void setLabelColor(String labelColor)
    {
        this.labelColor = labelColor;
    }

    public String getLabelColor()
    {
        return this.labelColor;
    }

    public String getMetricUnit()
    {
        return metricUnit;
    }

    public void setMetricUnit(String metricUnit)
    {
        this.metricUnit = metricUnit;
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
        return new JSONObject(this);
    }

    public String getAutoLinkTargetContainerId()
    {
        return this.autoLinkTargetContainerId;
    }

    public void setAutoLinkTargetContainerId(String autoLinkTargetContainerId)
    {
        this.autoLinkTargetContainerId = autoLinkTargetContainerId;
    }

    public String getAutoLinkCategory()
    {
        return autoLinkCategory;
    }

    public void setAutoLinkCategory(String autoLinkCategory)
    {
        this.autoLinkCategory = autoLinkCategory;
    }

    public Integer getParentCol()
    {
        return parentCol;
    }

    public void setParentCol(Integer parentCol)
    {
        this.parentCol = parentCol;
    }

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

}
