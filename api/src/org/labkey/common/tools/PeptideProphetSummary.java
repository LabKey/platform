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
package org.labkey.common.tools;

import net.systemsbiology.regisWeb.pepXML.PeptideprophetSummaryDocument;
import org.apache.xmlbeans.XmlException;

import javax.xml.stream.XMLStreamException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * User: arauch
 * Date: Mar 6, 2006
 * Time: 1:04:29 PM
 */
public class PeptideProphetSummary extends SensitivitySummary
{
    private float[] _fval;
    private float[][] _modelPos;
    private float[][] _modelNeg;
    private float[][] _obs;

    public static PeptideProphetSummary load(SimpleXMLStreamReader parser) throws XMLStreamException
    {
        parser.skipToStart("peptideprophet_summary");

        PeptideprophetSummaryDocument summaryDoc;

        try
        {
            summaryDoc = PeptideprophetSummaryDocument.Factory.parse(parser);
        }
        catch(XmlException e)
        {
            throw new XMLStreamException("Parsing peptide prophet summary", e);
        }

        PeptideprophetSummaryDocument.PeptideprophetSummary summary = summaryDoc.getPeptideprophetSummary();

        return new PeptideProphetSummary(summary);
    }


    private PeptideProphetSummary(PeptideprophetSummaryDocument.PeptideprophetSummary summary)
    {
        PeptideprophetSummaryDocument.PeptideprophetSummary.DistributionPoint[] distribution = summary.getDistributionPointArray();

        _fval = new float[distribution.length];
        _obs = new float[3][distribution.length];
        _modelPos = new float[3][distribution.length];
        _modelNeg = new float[3][distribution.length];

        for (int i=0; i<distribution.length; i++)
        {
            PeptideprophetSummaryDocument.PeptideprophetSummary.DistributionPoint point = distribution[i];

            _fval[i] = point.getFvalue();
            _modelPos[0][i] = point.getModel1PosDistr();
            _modelNeg[0][i] = point.getModel1NegDistr();
            _obs[0][i] = point.getObs1Distr().floatValue();
            _modelPos[1][i] = point.getModel2PosDistr();
            _modelNeg[1][i] = point.getModel2NegDistr();
            _obs[1][i] = point.getObs2Distr().floatValue();
            _modelPos[2][i] = point.getModel3PosDistr();
            _modelNeg[2][i] = point.getModel3NegDistr();
            _obs[2][i] = point.getObs3Distr().floatValue();
        }

        PeptideprophetSummaryDocument.PeptideprophetSummary.RocDataPoint[] roc = summary.getRocDataPointArray();

        _minProb = new float[roc.length];
        _sensitivity = new float[roc.length];
        _error = new float[roc.length];

        for (int i = 0; i < roc.length; i++)
        {
            PeptideprophetSummaryDocument.PeptideprophetSummary.RocDataPoint rocDataPoint = roc[i];

            _minProb[i] = rocDataPoint.getMinProb();
            _sensitivity[i] = rocDataPoint.getSensitivity();
            _error[i] = rocDataPoint.getError();
        }
    }

    public PeptideProphetSummary()
    {
        _obs = new float[3][];
        _modelPos = new float[3][];
        _modelNeg = new float[3][];
    }

    public byte[] getFValSeries()
    {
        return toByteArray(_fval);
    }

    public void setFValSeries(byte[] pepProphetFValSeries)
    {
        _fval = toFloatArray(pepProphetFValSeries);
    }

    public byte[] getObsSeries1()
    {
        return toByteArray(_obs[0]);
    }

    public void setObsSeries1(byte[] pepProphetObsSeries1)
    {
        _obs[0] = toFloatArray(pepProphetObsSeries1);
    }

    public byte[] getObsSeries2()
    {
        return toByteArray(_obs[1]);
    }

    public void setObsSeries2(byte[] pepProphetObsSeries2)
    {
        _obs[1] = toFloatArray(pepProphetObsSeries2);
    }

    public byte[] getObsSeries3()
    {
        return toByteArray(_obs[2]);
    }

    public void setObsSeries3(byte[] pepProphetObsSeries3)
    {
        _obs[2] = toFloatArray(pepProphetObsSeries3);
    }

    public byte[] getModelPosSeries1()
    {
        return toByteArray(_modelPos[0]);
    }

    public void setModelPosSeries1(byte[] pepProphetModelPosSeries1)
    {
        _modelPos[0] = toFloatArray(pepProphetModelPosSeries1);
    }

    public byte[] getModelPosSeries2()
    {
        return toByteArray(_modelPos[1]);
    }

    public void setModelPosSeries2(byte[] pepProphetModelPosSeries2)
    {
        _modelPos[1] = toFloatArray(pepProphetModelPosSeries2);
    }

    public byte[] getModelPosSeries3()
    {
        return toByteArray(_modelPos[2]);
    }

    public void setModelPosSeries3(byte[] pepProphetModelPosSeries3)
    {
        _modelPos[2] = toFloatArray(pepProphetModelPosSeries3);
    }

    public byte[] getModelNegSeries1()
    {
        return toByteArray(_modelNeg[0]);
    }

    public void setModelNegSeries1(byte[] pepProphetModelNegSeries1)
    {
        _modelNeg[0] = toFloatArray(pepProphetModelNegSeries1);
    }

    public byte[] getModelNegSeries2()
    {
        return toByteArray(_modelNeg[1]);
    }

    public void setModelNegSeries2(byte[] pepProphetModelNegSeries2)
    {
        _modelNeg[1] = toFloatArray(pepProphetModelNegSeries2);
    }

    public byte[] getModelNegSeries3()
    {
        return toByteArray(_modelNeg[2]);
    }

    public void setModelNegSeries3(byte[] pepProphetModelNegSeries3)
    {
        _modelNeg[2] = toFloatArray(pepProphetModelNegSeries3);
    }

    public float[] getFval() {
        return _fval;
    }

    public float[] getObs(int charge) {
        return _obs[charge - 1];
    }

    public float[] getModelPos(int charge) {
        return _modelPos[charge - 1];
    }

    public float[] getModelNeg(int charge) {
        return _modelNeg[charge - 1];
    }

    public float[] getModelTotal(int charge)
    {
        float[] pos = getModelPos(charge);
        float[] neg = getModelNeg(charge);
        float[] total = new float[pos.length];

        for (int i=0; i<total.length; i++)
                total[i] = pos[i] + neg[i];

        return total;
    }

    public double distributionR2(int charge)
    {
        double r2 = -1.0;

        try
        {
            r2 = MatrixUtil.r2(getModelTotal(charge), getObs(charge));
        }
        catch(RuntimeException e)
        {
            // Ignore matrix errors... probably means blank PeptideProphet distribution
        }

        return r2;
    }


    public static float[] toFloatArray(byte[] source)
    {
        if (null == source)
            source = new byte[0];

        ByteBuffer bb = ByteBuffer.wrap(source);
        // Intel native is LITTLE_ENDIAN -- UNDONE: Make this an app-wide constant?

        bb.order(ByteOrder.LITTLE_ENDIAN);
        FloatBuffer fb = bb.asFloatBuffer();
        float[] result = new float[fb.capacity()];
        fb.get(result);
        return result;
    }

    public static byte[] toByteArray(float[] x)
    {
        int floatCount = x.length;
        ByteBuffer bb = ByteBuffer.allocate(floatCount * 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < floatCount; i++)
            bb.putFloat(x[i]);

        return bb.array();
    }

    public static byte[] toByteArray(int[] x)
    {
        int floatCount = x.length;
        ByteBuffer bb = ByteBuffer.allocate(floatCount * 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < floatCount; i++)
            bb.putInt(x[i]);

        return bb.array();
    }

    public static int[] toIntArray(byte[] source)
    {
        if (null == source)
            source = new byte[0];

        ByteBuffer bb = ByteBuffer.wrap(source);
        // Intel native is LITTLE_ENDIAN -- UNDONE: Make this an app-wide constant?

        bb.order(ByteOrder.LITTLE_ENDIAN);
        IntBuffer ib = bb.asIntBuffer();
        int[] result = new int[ib.capacity()];
        ib.get(result);
        return result;
    }
}
