/*
 * Copyright (c) 2004-2007 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.sample;

import java.util.*;
import java.io.File;

import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.exp.IdentifiableBase;
import org.labkey.api.exp.Lsid;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.Attachment;

/**
 * Bean Class for for Mouse.
 *
 * @author <a href="http://boss.bekk.no/boss/middlegen/"/>Middlegen</a>
 */
public class Mouse extends IdentifiableBase implements AttachmentParent
{
    private java.lang.String _ts = null;
    private int _createdBy = 0;
    private java.sql.Timestamp _created = null;
    private int _modifiedBy = 0;
    private java.sql.Timestamp _modified = null;
    private int _mouseId = 0;
    private java.lang.String _container = null;
    private java.lang.String _entityId = null;
    private java.lang.String _mouseNo = null;
    private Integer _toeNo = null;
    private int _modelId = 0;
    private String _modelIdDisplay = null;
    private int _cageId = 0;
    private int litterId = 0;
    private java.lang.String _sex = null;
    private boolean _control = false;
    private java.sql.Timestamp _birthDate = null;
    private java.sql.Timestamp _deathDate = null;
    private java.sql.Timestamp _startDate = null;
    private java.sql.Timestamp _treatmentDate = null;
    private java.lang.String _mouseComments = null;
    private boolean _necropsyComplete = false;
    private boolean _bleedOutComplete = false;
    private Integer _int1 = null;
    private Integer _int2 = null;
    private Integer _int3 = null;
    private Date _date1 = null;
    private Date _date2 = null;
    private String _string1 = null;
    private String _string2 = null;
    private String _string3 = null;
    private String _necropsyAppearance;
    private String _necropsyGrossFindings;

    public Mouse()
    {
    }

    public String getLSID()
    {
        Lsid lsid = new Lsid("MouseModel", "Mouse." + PageFlowUtil.encode(ContainerManager.getForId(_container).getPath()), _mouseNo);
        return lsid.toString();
    }

    public void setLSID(String lsid)
    {
        super.setLSID(lsid);    //To change body of overridden methods use File | Settings | File Templates.
    }

    public void setName(String name)
    {
        super.setName(name);    //To change body of overridden methods use File | Settings | File Templates.
    }

    public String getName()
    {
        return super.getName();    //To change body of overridden methods use File | Settings | File Templates.
    }

    public java.lang.String getTs()
    {
        return _ts;
    }

    public void setTs(java.lang.String ts)
    {
        _ts = ts;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }

    public java.sql.Timestamp getCreated()
    {
        return _created;
    }

    public void setCreated(java.sql.Timestamp created)
    {
        _created = created;
    }

    public int getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(int modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public java.sql.Timestamp getModified()
    {
        return _modified;
    }

    public void setModified(java.sql.Timestamp modified)
    {
        _modified = modified;
    }

    public int getMouseId()
    {
        return _mouseId;
    }

    public void setMouseId(int mouseId)
    {
        _mouseId = mouseId;
    }

    public java.lang.String getContainer()
    {
        return _container;
    }

    public String getContainerId()
    {
        return _container;
    }

    public void setContainer(java.lang.String container)
    {
        _container = container;
    }

    public java.lang.String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(java.lang.String entityId)
    {
        _entityId = entityId;
    }


    // Do nothing
    public void setAttachments(Collection<Attachment> attachments)
    {
    }

    // Do nothing... but required otherwise Table layer will explode on insert
    public Collection<Attachment> getAttachments()
    {
        return null;
    }

    public java.lang.String getMouseNo()
    {
        return _mouseNo;
    }

    public void setMouseNo(java.lang.String mouseNo)
    {
        _mouseNo = mouseNo;
    }

    public Integer getToeNo()
    {
        return _toeNo;
    }

    public void setToeNo(Integer toeNo)
    {
        _toeNo = toeNo;
    }

    public int getModelId()
    {
        return _modelId;
    }

    public void setModelId(int modelId)
    {
        _modelId = modelId;
    }

    public int getCageId()
    {
        return _cageId;
    }

    public void setCageId(int cageId)
    {
        _cageId = cageId;
    }

    public java.lang.String getSex()
    {
        return _sex;
    }

    public void setSex(java.lang.String sex)
    {
        _sex = sex;
    }

    public boolean getControl()
    {
        return _control;
    }

    public void setControl(boolean control)
    {
        _control = control;
    }

    public java.sql.Timestamp getBirthDate()
    {
        return _birthDate;
    }

    public void setBirthDate(java.sql.Timestamp birthDate)
    {
        _birthDate = birthDate;
    }

    public java.sql.Timestamp getDeathDate()
    {
        return _deathDate;
    }

    public void setStartDate(java.sql.Timestamp startDate)
    {
        _startDate = startDate;
    }

    public java.sql.Timestamp getStartDate()
    {
        return _startDate;
    }

    public void setDeathDate(java.sql.Timestamp deathDate)
    {
        _deathDate = deathDate;
    }

    public java.sql.Timestamp getTreatmentDate()
    {
        return _treatmentDate;
    }

    public void setTreatmentDate(java.sql.Timestamp treatmentDate)
    {
        _treatmentDate = treatmentDate;
    }

    public java.lang.String getMouseComments()
    {
        return _mouseComments;
    }

    public void setMouseComments(java.lang.String mouseComments)
    {
        _mouseComments = mouseComments;
    }

    public boolean getNecropsyComplete()
    {
        return _necropsyComplete;
    }

    public void setNecropsyComplete(boolean necropsyComplete)
    {
        _necropsyComplete = necropsyComplete;
    }


    public boolean getBleedOutComplete()
    {
        return _bleedOutComplete;
    }

    public void setBleedOutComplete(boolean bleedOutComplete)
    {
        _bleedOutComplete = bleedOutComplete;
    }

    public Integer getInt1()
    {
        return _int1;
    }

    public void setInt1(Integer _int1)
    {
        this._int1 = _int1;
    }

    public Integer getInt2()
    {
        return _int2;
    }

    public void setInt2(Integer _int2)
    {
        this._int2 = _int2;
    }

    public Integer getInt3()
    {
        return _int3;
    }

    public void setInt3(Integer _int3)
    {
        this._int3 = _int3;
    }

    public Date getDate1()
    {
        return _date1;
    }

    public void setDate1(Date _date1)
    {
        this._date1 = _date1;
    }

    public Date getDate2()
    {
        return _date2;
    }

    public void setDate2(Date _date2)
    {
        this._date2 = _date2;
    }

    public String getString1()
    {
        return _string1;
    }

    public void setString1(String _string1)
    {
        this._string1 = _string1;
    }

    public String getString2()
    {
        return _string2;
    }

    public void setString2(String _string2)
    {
        this._string2 = _string2;
    }

    public String getString3()
    {
        return _string3;
    }

    public void setString3(String _string3)
    {
        this._string3 = _string3;
    }


    public int getLitterId()
    {
        return litterId;
    }

    public void setLitterId(int litterId)
    {
        this.litterId = litterId;
    }

    public String getNecropsyAppearance()
    {
        return _necropsyAppearance;
    }

    public void setNecropsyAppearance(String necropsyAppearance)
    {
        this._necropsyAppearance = necropsyAppearance;
    }

    public String getNecropsyGrossFindings()
    {
        return _necropsyGrossFindings;
    }

    public void setNecropsyGrossFindings(String necropsyGrossFindings)
    {
        this._necropsyGrossFindings = necropsyGrossFindings;
    }
}
