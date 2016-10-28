/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.util;

import Jama.Matrix;

public class MatrixUtil
{
    // Takes Matrix X and Matrix Y and returns Matrix containing b values
    public static Matrix linearRegression(Matrix X, Matrix Y)
    {
        Matrix XTrans = X.transpose();

        return XTrans.times(X).inverse().times(XTrans).times(Y);
    }


    // Java-friendly version that takes an array of arrays of x values, and an array of y values
    // Returns array of b values
    public static double[] linearRegression(double[][] xArray, double[] yArray)
    {
        int pointCount = yArray.length;
        int independentCount = xArray.length;
        double[][] x = new double[pointCount][independentCount + 1];
        double[][] y = new double[pointCount][1];

        // Transpose into new arrays and add column of 1s to x
        for (int i = 0; i < pointCount; i++)
        {
            y[i][0] = yArray[i];
            x[i][0] = 1;

            for (int j = 0; j < independentCount; j++)
                x[i][j + 1] = xArray[j][i];
        }

        return linearRegression(new Matrix(x), new Matrix(y)).getRowPackedCopy();
    }


    // Simple version for single independent variable: y = a + bx
    public static double[] linearRegression(double[] xArray, double[] yArray)
    {
        return linearRegression(new double[][]{xArray}, yArray);
    }


    // Takes Matrix X and Matrix Y and returns Matrix containing R-squared
    public static Matrix r2(Matrix X, Matrix Y)
    {
        Matrix XTrans = X.transpose();
        Matrix YTrans = Y.transpose();

        return YTrans.times(X).times(
                XTrans.times(X).inverse()
        ).times(XTrans).times(Y).times(
                YTrans.times(Y).inverse());
    }


    public static double r2(double[][] xArray, double[] yArray)
    {
        int pointCount = yArray.length;
        int independentCount = xArray.length;

        double[][] x = new double[pointCount][independentCount];
        double[][] y = new double[pointCount][1];
        double[] xTotal = new double[independentCount];
        double[] xAvg = new double[independentCount];
        double yTotal = 0;

        // Calculate average of x columns, y
        for (int i = 0; i < pointCount; i++)
        {
            yTotal += yArray[i];

            for (int j = 0; j < independentCount; j++)
                xTotal[j] += xArray[j][i];
        }

        for (int i = 0; i < independentCount; i++)
            xAvg[i] = xTotal[i] / pointCount;

        double yAvg = yTotal / pointCount;

        // Create deviation score form, transpose into new arrays
        for (int i = 0; i < pointCount; i++)
        {
            y[i][0] = yArray[i] - yAvg;

            for (int j = 0; j < independentCount; j++)
                x[i][j] = xArray[j][i] - xAvg[j];
        }

        return r2(new Matrix(x), new Matrix(y)).get(0, 0);
    }


    // Simple version for single independent variable: y = a + bx
    public static double r2(double[] xArray, double[] yArray)
    {
        return r2(new double[][]{xArray}, yArray);
    }


    public static double r2(float[] xArray, float[] yArray)
    {
        return r2(toDoubleArray(xArray), toDoubleArray(yArray));
    }


    private static double[] toDoubleArray(float[] array)
    {
        double[] dArray = new double[array.length];

        for (int i = 0; i < array.length; i++)
            dArray[i] = array[i];

        return dArray;
    }


    public static double sigma(double[] x, double[] y, double[] b)
    {
        double errorTotal = 0;

        for (int i = 0; i < y.length; i++)
        {
            errorTotal += Math.pow(y[i] - (b[0] + b[1] * x[i]), 2);
        }

        return Math.sqrt(errorTotal / (y.length - 2));
    }
}
