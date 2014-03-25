/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.query.olap;


import org.json.JSONObject;
import org.olap4j.Axis;
import org.olap4j.CellSet;
import org.olap4j.CellSetAxis;
import org.olap4j.CellSetAxisMetaData;
import org.olap4j.OlapConnection;
import org.olap4j.OlapException;
import org.olap4j.Position;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Member;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;


/**
 * User: matthew
 * Date: 10/19/13
 * Time: 8:58 AM
 *
 * a Qube is a wrapper for a Cube or Cube like structure. Each level in the cube will generate a map of sets.
 *
 * The level map is a collection of Member->MemberSet entries where the MemberSet is the collection of key Members that
 * have this 'attribute'.
 *
 * For instance, the key attribute level might be [Subject].[Subject], and the Gender level may have a few members such as
 * [Gender].[Male], [Gender].[Female], [Gender].[Unknown]
 *
 * Each member will have an associated set of participant members.
 *
 * [Gender].[Male] -> {[Subject].[P001], [Subject].[P002]}
 * [Gender].[Female] -> {[Subject].[P003], [Subject].[P004]}
 * [Gender].[Unknown] -> {[Subject].[P005]}
 */

public class Qube
{
    Cube _cube;
    Level _keyLevel;


    public Qube(OlapConnection connection, Cube cube, Level keyLevel) throws OlapException
    {
        _cube = cube;
        _keyLevel = keyLevel;
    }


    public CellSet executeQuery(JSONObject json)
    {
        return null;
    }


    public CellSet executeQuery(QubeQuery expr)
    {
        return null;
    }




}