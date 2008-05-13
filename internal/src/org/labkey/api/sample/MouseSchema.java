/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SqlDialect;
import org.labkey.api.data.TableInfo;

/**
 * User: migra
 * Date: Nov 17, 2005
 * Time: 2:13:38 PM
 */
public class MouseSchema
{
    private static final String SCHEMA_NAME = "mousemod";

    public static String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public static SqlDialect getDialect()
    {
        return getSchema().getSqlDialect();
    }

    public static TableInfo getMouseModel()
    {
        return getSchema().getTable("MouseModel");
    }

    public static TableInfo getBreedingPair()
    {
        return getSchema().getTable("BreedingPair");
    }

    public static TableInfo getLitter()
    {
        return getSchema().getTable("Litter");
    }

    public static TableInfo getCage()
    {
        return getSchema().getTable("Cage");
    }

    public static TableInfo getMouse()
    {
        return getSchema().getTable("Mouse");
    }

    public static TableInfo getSlide()
    {
        return getSchema().getTable("Slide");
    }

    public static TableInfo getStain()
    {
        return getSchema().getTable("Stain");
    }

    public static TableInfo getSample()
    {
        return getSchema().getTable("Sample");
    }

    public static TableInfo getLocation()
    {
        return getSchema().getTable("Location");
    }

    public static TableInfo getSampleType()
    {
        return getSchema().getTable("SampleType");
    }

    public static TableInfo getMouseTask()
    {
        return getSchema().getTable("MouseTask");
    }

    public static TableInfo getBox()
    {
        return  getSchema().getTable("Box");
    }

    public static TableInfo getInventory()
    {
        return getSchema().getTable("Inventory");
    }

    public static TableInfo getFreezer()
    {
        return getSchema().getTable("Freezer");
    }

    public static TableInfo getMouseSlide()
    {
        return getSchema().getTable("MouseSlide");
    }

    public static TableInfo getMouseSample()
    {
        return getSchema().getTable("MouseSample");
    }

    public static TableInfo getMouseView()
    {
        return getSchema().getTable("MouseView");
    }

    public static TableInfo getIrradDose()
    {
        return getSchema().getTable("IrradDose");
    }

    public static TableInfo getGenotype()
    {
        return getSchema().getTable("Genotype");
    }

    public static TableInfo getMouseStrain()
    {
        return getSchema().getTable("MouseStrain");
    }

    public static TableInfo getTargetGene()
    {
        return getSchema().getTable("TargetGene");
    }

    public static TableInfo getTreatment()
    {
        return getSchema().getTable("Treatment");
    }

}
