package com.graphinsight.indicator.util;

/**
 * Date: 2022/7/7
 * Desc:
 */
public class MathUtil {

    public static double trapz(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("x.length != y.length");
        }
        if (y.length == 0) {
            throw new IllegalArgumentException("y.length == 0");
        }
        double value = 0.0;
        double x0 = x[0];
        double y0 = y[0];
        for (int i = 1; i < y.length; i++) {
            double x1 = x[i];
            double y1 = y[i];
            double dx = x1 - x0;
            double ym = y0 + y1;
            value += dx * ym;
            x0 = x1;
            y0 = y1;
        }
        return value / 2.0;
    }


    public static void main(String[] args) {
        // double x = 0.333;
        // double y = 0.124;
        // double m = 0.5;
        // double n = 0.571;
        // double sx = sigmoid(x);
        // double sy = sigmoid(y);
        // double sm = sigmoid(m);
        // double sn = sigmoid(n);
        // System.out.println("x/y = " + x / y + " m/n = " + m / n + " x/y > m/n--" + (x/y > m/n));
        // System.out.println("sx/sy = " + sx / sy + " sm/sn = " + sm / sn +  " sx/sy > sm/sn--" + (sx/sy > sm/sn));
        double[] x_list = new double[]{0.0, 2.0/3.0, 1.0};
        double[] y_list = new double[]{0.0, 0.0, 1};
        System.out.println(trapz(x_list,y_list));



    }

    public static double sigmoid(double target){
        double n = 1.0 + Math.exp(-target);
        return 1.0 / n;
    }
}
