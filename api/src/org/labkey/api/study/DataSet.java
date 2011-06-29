/*
 * Copyright (c) 2009-2011 LabKey Corporation
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

package org.labkey.api.study;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.UnauthorizedException;

import java.sql.SQLException;
import java.util.Set;

/**
 * User: kevink
 * Date: May 27, 2009
 */
public interface DataSet<T extends DataSet> extends StudyEntity, StudyCachable<T>
{
    Set<String> getDefaultFieldNames();

    Domain getDomain();

    String getName();

    String getFileName();

    String getCategory();

    int getDataSetId();

    String getTypeURI();

    String getPropertyURI(String column);

    TableInfo getTableInfo(User user) throws UnauthorizedException;

    TableInfo getTableInfo(User user, boolean checkPermission) throws UnauthorizedException;

    boolean isDemographicData();

    /**
     * @return true if this dataset is backed by assay data within LabKey Server. Note that if a dataset happens
     * to contain assay data but isn't linked to an assay provider in the server, this method will return false. 
     */
    boolean isAssayData();

    ExpProtocol getAssayProtocol();

    Study getStudy();

    Integer getCohortId();

    @Nullable
    Cohort getCohort();

    @Nullable
    String getKeyPropertyName();

    void setKeyPropertyName(String name);

    void save(User user) throws SQLException;

    public boolean canRead(User user);

    public boolean canWrite(User user);

    public Set<Class<? extends Permission>> getPermissions(User user);

    KeyType getKeyType();

    enum KeyType
    {
        SUBJECT(1),
        SUBJECT_VISIT(2),
        SUBJECT_VISIT_OTHER(3);
        
        private int _cardinality;
        KeyType(int cardinality)
        {
            _cardinality = cardinality;
        }
        public int getCardinality()
        {
            return _cardinality;
        }
    }

    enum KeyManagementType
    {
        // Don't rename enums without updating the values in the database too
        None(""), RowId("rowid", "true"), GUID("entityid", "guid");

        private String _serializationName;
        private String[] _serializationAliases;

        private KeyManagementType(String serializationName, String... serializationAliases)
        {
            _serializationName = serializationName;
            _serializationAliases = serializationAliases;
        }

        public String getSerializationName()
        {
            return _serializationName;
        }

        public boolean matches(String name)
        {
            if (_serializationName.equalsIgnoreCase(name))
            {
                return true;
            }
            for (String alias : _serializationAliases)
            {
                if (alias.equalsIgnoreCase(name))
                {
                    return true;
                }
            }
            return false;
        }

        public static KeyManagementType findMatch(String name)
        {
            if (name == null)
            {
                return KeyManagementType.None;
            }
            for (KeyManagementType type : KeyManagementType.values())
            {
                if (type.matches(name))
                {
                    return type;
                }
            }
            throw new IllegalArgumentException("No match for '" + name + "'");
        }
    }
}
