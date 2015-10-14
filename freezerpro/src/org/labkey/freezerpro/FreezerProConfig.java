/*
 * Copyright (c) 2014-2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.freezerpro;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.util.Pair;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.study.xml.freezerProExport.FreezerProConfigDocument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* Created by klum on 5/27/2014.
*/
public class FreezerProConfig implements SpecimenTransform.ExternalImportConfig
{
    private static Logger _log = Logger.getLogger(FreezerProConfig.class);
    private String _baseServerUrl;
    private String _username;
    private String _password;
    private int _reloadInterval;
    private boolean _enableReload;
    private String _reloadDate;
    private String _metadata;
    private int _reloadUser;

    private String _searchFilterString;
    private boolean _getUserFields;
    private boolean _useAdvancedSearch;
    private boolean _getArchivedSamples;
    private boolean _getSampleLocation;

    private static Map<String, String> COLUMN_MAP;
    private List<Pair<String, List<String>>> _columnFilters = new ArrayList<>();

    private List<Map<String, Object>> _queryFilters = new ArrayList<>();
    private List<String> _udfs = new ArrayList<>();
    private Map<String, String> _columnMap = new CaseInsensitiveHashMap<>();

    public static final String BARCODE_FIELD_NAME = "barcode";
    public static final String SAMPLE_ID_FIELD_NAME = "uid";

    static
    {
        COLUMN_MAP = new CaseInsensitiveHashMap<>();

        /* these are standard fields (sdfs) in freezerPro
            The barcode field can be overridden by a column map field named barcode_tag
            created_at is used as the default value for date_of_draw but can be overridden by a column map field named date_of_draw
         */
        COLUMN_MAP.put("obj_id", BARCODE_FIELD_NAME);
        COLUMN_MAP.put("sample_id", SAMPLE_ID_FIELD_NAME);
        COLUMN_MAP.put("scount", "vials");
        COLUMN_MAP.put("type", "sample type");


        //these are user defined fields and probably shouldn't be used as part of the standard column mapping
        //COLUMN_MAP.put("barcode_tag", BARCODE_FIELD_NAME);
        //COLUMN_MAP.put("box_name", "box");
        //COLUMN_MAP.put("time of draw", "time of draw");
        //COLUMN_MAP.put("date of process", "date of draw");

        // original mapping
/*
        COLUMN_MAP.put("type", "sample type");
        COLUMN_MAP.put("barcode_tag", BARCODE_FIELD_NAME);
        COLUMN_MAP.put("sample_id", SAMPLE_ID_FIELD_NAME);
        COLUMN_MAP.put("scount", "vials");
        COLUMN_MAP.put("created at", "created");
        COLUMN_MAP.put("box_name", "box");
        COLUMN_MAP.put("time of draw", "time of draw");
        COLUMN_MAP.put("date of process", "date of draw");
*/
}

    public enum Options
    {
        url,
        user,
        password,
        reloadInterval,
        enableReload,
        importUserFields,
        reloadDate,
        metadata,
        reloadUser,
    }

    public FreezerProConfig()
    {
        _columnMap.putAll(COLUMN_MAP);
    }

    private void parseConfigMetadata(String metadata)
    {
        if (metadata != null)
        {
            try
            {
                FreezerProConfigDocument doc = FreezerProConfigDocument.Factory.parse(metadata, XmlBeansUtil.getDefaultParseOptions());
                FreezerProConfigDocument.FreezerProConfig config = doc.getFreezerProConfig();

                if (config != null)
                {
                    // import user defined fields
                    _getUserFields = config.getGetUserFields();

                    if (config.isSetFilterString())
                        _searchFilterString = config.getFilterString();

                    Map<String, List<String>> filters = new HashMap<>();
                    FreezerProConfigDocument.FreezerProConfig.ColumnFilters columnFilters = config.getColumnFilters();
                    if (columnFilters != null)
                    {
                        for (FreezerProConfigDocument.FreezerProConfig.ColumnFilters.Filter filter : columnFilters.getFilterArray())
                        {
                            if (filter.getName() != null && filter.getValue() != null)
                            {
                                // filters can support multiple values for the same field
                                if (!filters.containsKey(filter.getName()))
                                {
                                    filters.put(filter.getName(), new ArrayList<String>());
                                }
                                filters.get(filter.getName()).add(filter.getValue());
                            }
                        }
                    }

                    FreezerProConfigDocument.FreezerProConfig.ColumnMap columnMap = config.getColumnMap();
                    if (columnMap != null)
                    {
                        for (FreezerProConfigDocument.FreezerProConfig.ColumnMap.Column col : columnMap.getColumnArray())
                        {
                            if (col.getSourceName() != null && col.getDestName() != null)
                            {
                                if (!COLUMN_MAP.containsKey(col.getSourceName()))
                                    _columnMap.put(col.getSourceName(), col.getDestName());
                                else
                                this.
                                    _log.warn("The column name: " + col.getSourceName() + " is reserved and cannot be remapped. " +
                                            "The configuration specificed to remap to: " + col.getDestName() + " will be ignored.");
                            }
                        }
                    }

                    for (Map.Entry<String, List<String>> entry : filters.entrySet())
                    {
                        _columnFilters.add(new Pair<String, List<String>>(entry.getKey(), entry.getValue()));
                    }

                    FreezerProConfigDocument.FreezerProConfig.AdvancedSearch advancedSearch = config.getAdvancedSearch();
                    if (advancedSearch != null)
                    {
                        _useAdvancedSearch = true;
                        _getArchivedSamples = advancedSearch.getIncludeArchiveSamples();
                        _getSampleLocation = advancedSearch.getIncludeLocation();
                        FreezerProConfigDocument.FreezerProConfig.AdvancedSearch.ReturnUserFields userFields = advancedSearch.getReturnUserFields();
                        if (userFields != null)
                        {
                            for (FreezerProConfigDocument.FreezerProConfig.AdvancedSearch.ReturnUserFields.Field field : userFields.getFieldArray())
                            {
                                if (!_udfs.contains(field.getName()))
                                {
                                    _udfs.add(field.getName());
                                }
                            }
                        }

                        FreezerProConfigDocument.FreezerProConfig.AdvancedSearch.FieldFilters fieldFilters = advancedSearch.getFieldFilters();
                        if (fieldFilters != null)
                        {
                            for (FreezerProConfigDocument.FreezerProConfig.AdvancedSearch.FieldFilters.Filter filter : fieldFilters.getFilterArray())
                            {
                                if (filter.getField() != null && filter.getValue() != null && filter.getOperator() != null)
                                {
                                    Map<String, Object> fieldFilter = new HashMap<>();
                                    fieldFilter.put("type", filter.getType());
                                    fieldFilter.put("field", filter.getField());
                                    fieldFilter.put("op", filter.getOperator());
                                    fieldFilter.put("value", filter.getValue());
                                    _queryFilters.add(fieldFilter);
                                }
                            }
                        }
                    }
                }
            }
            catch (XmlException e)
            {
                _log.error("The FreezerPro metadata XML was malformed. The error was returned: " + e.getMessage());
            }
        }
    }

    public boolean isGetArchivedSamples()
    {
        return _getArchivedSamples;
    }

    public boolean isGetSampleLocation()
    {
        return _getSampleLocation;
    }

    public boolean isUseAdvancedSearch()
    {
        return _useAdvancedSearch;
    }

    public boolean isGetUserFields()
    {
        return _getUserFields;
    }

    public String getSearchFilterString()
    {
        return _searchFilterString;
    }

    public List<String> getUdfs()
    {
        return _udfs;
    }

    public List<Map<String, Object>> getQueryFilters()
    {
        return _queryFilters;
    }

    public List<Pair<String, List<String>>> getColumnFilters()
    {
        return _columnFilters;
    }

    public Map<String, String> getColumnMap()
    {
        return _columnMap;
    }

    public String getBaseServerUrl()
    {
        return _baseServerUrl;
    }

    public void setBaseServerUrl(String baseServerUrl)
    {
        _baseServerUrl = baseServerUrl;
    }

    public String getUsername()
    {
        return _username;
    }

    public void setUsername(String username)
    {
        _username = username;
    }

    public String getPassword()
    {
        return _password;
    }

    public void setPassword(String password)
    {
        _password = password;
    }

    public int getReloadInterval()
    {
        return _reloadInterval;
    }

    public void setReloadInterval(int reloadInterval)
    {
        _reloadInterval = reloadInterval;
    }

    public boolean isEnableReload()
    {
        return _enableReload;
    }

    public void setEnableReload(boolean enableReload)
    {
        _enableReload = enableReload;
    }

    public String getReloadDate()
    {
        return _reloadDate;
    }

    public void setReloadDate(String reloadDate)
    {
        _reloadDate = reloadDate;
    }

    public String getMetadata()
    {
        return _metadata;
    }

    public void setMetadata(String metadata)
    {
        _metadata = metadata;

        if (_metadata != null)
            parseConfigMetadata(_metadata);
    }
}