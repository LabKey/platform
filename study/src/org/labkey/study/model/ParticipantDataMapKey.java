/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

/**
 * User: Matthew
 * Date: Feb 1, 2006
 * Time: 10:12:34 AM
 */
public class ParticipantDataMapKey implements Comparable
{
    public int datasetId;
    public double sequenceNum;
//    public int objectPropertyId;

    public ParticipantDataMapKey(int datasetId, double sequenceNum) // , int objectPropertyId)
    {
//        this.objectPropertyId = objectPropertyId;
        this.sequenceNum = sequenceNum;
        this.datasetId = datasetId;
    }


    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParticipantDataMapKey that = (ParticipantDataMapKey) o;

        if (datasetId != that.datasetId) return false;
//        if (objectPropertyId != that.objectPropertyId) return false;
        if (Double.compare(that.sequenceNum, sequenceNum) != 0) return false;

        return true;
    }


    public int hashCode()
    {
        int result;
        long temp;
        result = datasetId;
        temp = sequenceNum != +0.0d ? Double.doubleToLongBits(sequenceNum) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
//        result = 31 * result + objectPropertyId;
        return result;
    }


    public int compareTo(Object o)
    {
        ParticipantDataMapKey k = (ParticipantDataMapKey) o;
        if (this.sequenceNum != k.sequenceNum)
            return Double.compare(this.sequenceNum, k.sequenceNum);
//        if (this.datasetId != k.datasetId)
            return this.datasetId - k.datasetId;
//        return this.objectPropertyId - k.objectPropertyId;
    }

    
    @Override
    public String toString()
    {
        return "ParticipantDataMapKey(" + datasetId + "," + sequenceNum + ")";
    }
}
