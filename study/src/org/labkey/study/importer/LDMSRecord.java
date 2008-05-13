/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.study.importer;

import org.labkey.api.exp.api.ExpMaterial;

import java.util.Map;
import java.util.Date;
import java.util.Calendar;

/**
 * User: brittp
 * Date: Jan 26, 2006
 * Time: 4:06:07 PM
 */
public class LDMSRecord
{
    private int _rowId;
    private String _container;
    private Integer _labid;
    private String _clasid;
    private Float _lstudy;
    private Integer _uspeci;
    private Integer _parusp;
    private String _specid;
    private String _guspec;
    private Integer _txtpid;
    private Date _drawd;
    private Float _vidval;
    private String _vidstr;
    private Date _recdt;
    private String _primstr;
    private String _addstr;
    private String _dervstr;
    private String _sec_tp;
    private Float _volume;
    private String _volstr;
    private Integer _stored;
    private Date _stord;
    private Integer _shipfg;
    private Integer _shipno;
    private Date _shipd;
    private Integer _rb_no;
    private Date _recvd;
    private String _condstr;
    private String _sec_id;
    private Float _addtim;
    private Integer _addunt;
    private String _commts;
    private Integer _rlprot;

    public LDMSRecord()
    {

    }

    public LDMSRecord(ExpMaterial material, Map properties)
    {
        _rowId = material.getRowId();
        _container = material.getContainer().getId();
        _labid = (Integer) properties.get("labid");
        _clasid = (String) properties.get("clasid");
        _lstudy = (Float) properties.get("lstudy");
        _uspeci = (Integer) properties.get("uspeci");
        _parusp = (Integer) properties.get("parusp");
        _specid = (String) properties.get("specid");
        _guspec = (String) properties.get("guspec");
        _txtpid = (Integer) properties.get("txtpid");
        _drawd = getDate((Integer) properties.get("drawdy"),
                         (Integer) properties.get("drawdm"),
                         (Integer) properties.get("drawdd"),
                         (Integer) properties.get("drawth"),
                         (Integer) properties.get("drawtm"));
        _vidval = (Float) properties.get("vidval");
        _vidstr = (String) properties.get("vidstr");
        _recdt = getDate((Integer) properties.get("recdty"),
                         (Integer) properties.get("recdtm"),
                         (Integer) properties.get("recdtd"));
        _primstr = (String) properties.get("primstr");
        _addstr = (String) properties.get("addstr");
        _dervstr = (String) properties.get("dervstr");
        _sec_tp = (String) properties.get("sec_tp");
        _volume = (Float) properties.get("volume");
        _volstr = (String) properties.get("volstr");
        _stored = (Integer) properties.get("stored");
        _stord = getDate((Integer) properties.get("stordy"),
                         (Integer) properties.get("stordm"),
                         (Integer) properties.get("stordd"));
        _shipfg = (Integer) properties.get("shipfg");
        _shipno = getInteger((Integer) properties.get("shipno"), true);
        _shipd = getDate((Integer) properties.get("shipdy"),
                         (Integer) properties.get("shipdm"),
                         (Integer) properties.get("shipdd"));
        _rb_no = getInteger((Integer) properties.get("rb_no"), true);
        _recvd = getDate((Integer) properties.get("recvdy"),
                         (Integer) properties.get("recvdm"),
                         (Integer) properties.get("recvdd"));
        _condstr = (String) properties.get("condstr");
        _sec_id = (String) properties.get("sec_id");
        _addtim = (Float) properties.get("addtim");
        _addunt = getInteger((Integer) properties.get("addunt"), true);
        _commts = (String) properties.get("commts");
        _rlprot = (Integer) properties.get("rlprot");
    }

    private Integer getInteger(Integer value, boolean nullOnNegative)
    {
        if (value == null)
            return null;
        if (nullOnNegative && value < 0)
            return null;
        return value;
    }

    private Date getDate(Integer year, Integer month, Integer day)
    {
        return getDate(year, month, day, null, null);
    }

    private Date getDate(Integer year, Integer month, Integer day, Integer hour, Integer minute)
    {
        if (year == null || year.intValue() == -2 ||
                month == null || month.intValue() == -2 ||
                day == null || day.intValue() == -2)
            return null;

        Calendar cal = Calendar.getInstance();
        cal.clear();
        if (hour == null || hour.intValue() == -2 ||
                minute == null || minute.intValue() == -2)
            cal.set(year, month + 1, day);
        else
            cal.set(year, month + 1, day, hour, minute, 0);
        return cal.getTime();
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public String getAddstr()
    {
        return _addstr;
    }

    public void setAddstr(String addstr)
    {
        _addstr = addstr;
    }

    public Float getAddtim()
    {
        return _addtim;
    }

    public void setAddtim(Float addtim)
    {
        _addtim = addtim;
    }

    public Integer getAddunt()
    {
        return _addunt;
    }

    public void setAddunt(Integer addunt)
    {
        _addunt = addunt;
    }

    public String getClasid()
    {
        return _clasid;
    }

    public void setClasid(String clasid)
    {
        _clasid = clasid;
    }

    public String getCommts()
    {
        return _commts;
    }

    public void setCommts(String commts)
    {
        _commts = commts;
    }

    public String getCondstr()
    {
        return _condstr;
    }

    public void setCondstr(String condstr)
    {
        _condstr = condstr;
    }

    public String getDervstr()
    {
        return _dervstr;
    }

    public void setDervstr(String dervstr)
    {
        _dervstr = dervstr;
    }

    public Date getDrawd()
    {
        return _drawd;
    }

    public void setDrawd(Date drawd)
    {
        _drawd = drawd;
    }

    public String getGuspec()
    {
        return _guspec;
    }

    public void setGuspec(String guspec)
    {
        _guspec = guspec;
    }

    public Integer getLabid()
    {
        return _labid;
    }

    public void setLabid(Integer labid)
    {
        _labid = labid;
    }

    public Float getLstudy()
    {
        return _lstudy;
    }

    public void setLstudy(Float lstudy)
    {
        _lstudy = lstudy;
    }

    public Integer getParusp()
    {
        return _parusp;
    }

    public void setParusp(Integer parusp)
    {
        _parusp = parusp;
    }

    public String getPrimstr()
    {
        return _primstr;
    }

    public void setPrimstr(String primstr)
    {
        _primstr = primstr;
    }

    public Integer getRb_no()
    {
        return _rb_no;
    }

    public void setRb_no(Integer rb_no)
    {
        _rb_no = rb_no;
    }

    public Date getRecdt()
    {
        return _recdt;
    }

    public void setRecdt(Date recdt)
    {
        _recdt = recdt;
    }

    public Date getRecvd()
    {
        return _recvd;
    }

    public void setRecvd(Date recvd)
    {
        _recvd = recvd;
    }

    public Integer getRlprot()
    {
        return _rlprot;
    }

    public void setRlprot(Integer rlprot)
    {
        _rlprot = rlprot;
    }

    public String getSec_id()
    {
        return _sec_id;
    }

    public void setSec_id(String sec_id)
    {
        _sec_id = sec_id;
    }

    public String getSec_tp()
    {
        return _sec_tp;
    }

    public void setSec_tp(String sec_tp)
    {
        _sec_tp = sec_tp;
    }

    public Date getShipd()
    {
        return _shipd;
    }

    public void setShipd(Date shipd)
    {
        _shipd = shipd;
    }

    public Integer getShipfg()
    {
        return _shipfg;
    }

    public void setShipfg(Integer shipfg)
    {
        _shipfg = shipfg;
    }

    public Integer getShipno()
    {
        return _shipno;
    }

    public void setShipno(Integer shipno)
    {
        _shipno = shipno;
    }

    public String getSpecid()
    {
        return _specid;
    }

    public void setSpecid(String specid)
    {
        _specid = specid;
    }

    public Date getStord()
    {
        return _stord;
    }

    public void setStord(Date stord)
    {
        _stord = stord;
    }

    public Integer getStored()
    {
        return _stored;
    }

    public void setStored(Integer stored)
    {
        _stored = stored;
    }

    public Integer getTxtpid()
    {
        return _txtpid;
    }

    public void setTxtpid(Integer txtpid)
    {
        _txtpid = txtpid;
    }

    public Integer getUspeci()
    {
        return _uspeci;
    }

    public void setUspeci(Integer uspeci)
    {
        _uspeci = uspeci;
    }

    public String getVidstr()
    {
        return _vidstr;
    }

    public void setVidstr(String vidstr)
    {
        _vidstr = vidstr;
    }

    public Float getVidval()
    {
        return _vidval;
    }

    public void setVidval(Float vidval)
    {
        _vidval = vidval;
    }

    public String getVolstr()
    {
        return _volstr;
    }

    public void setVolstr(String volstr)
    {
        _volstr = volstr;
    }

    public Float getVolume()
    {
        return _volume;
    }

    public void setVolume(Float volume)
    {
        _volume = volume;
    }
}
