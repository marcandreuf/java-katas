package org.mandfer.practices.algorithm;


/**
 * Study of the solution
 * https://javabypatel.blogspot.com/2016/11/rotate-matrix-by-90-degrees-inplace.html
 */
public class RotateMatrix90DegreeInPlace {

    public static void main(String[] args) {
        new RotateMatrix90DegreeInPlace();
    }

    public RotateMatrix90DegreeInPlace() {
        int[][] matrix = {
                {1, 2, 3, 4},
                {5, 6, 7, 8},
                {9, 10, 11, 12},
                {13, 14, 15, 16}
        };
        printMatrix(matrix);
        rotateMatrixInPlace(matrix);
        printMatrix(matrix);
    }

    public void rotateMatrixInPlace(int[][] matrix) {
        int size = matrix.length - 1;

        for (int firstRowCol = 0; firstRowCol <= (size / 2); firstRowCol++) {

            int lastRowCol = size - firstRowCol;
            for (int itrRowCol = firstRowCol; itrRowCol < lastRowCol; itrRowCol++) {
                int maxRowCol = size - itrRowCol;
                int top_left = matrix[firstRowCol][itrRowCol];

                matrix[firstRowCol][itrRowCol] = matrix[maxRowCol][firstRowCol];
                matrix[maxRowCol][firstRowCol] = matrix[lastRowCol][maxRowCol];
                matrix[lastRowCol][maxRowCol] = matrix[itrRowCol][lastRowCol];
                matrix[itrRowCol][lastRowCol] = top_left;
            }
        }
    }

    private static void printMatrix(int[][] matrix) {
        for (int[] ints : matrix) {
            for (int j = 0; j < matrix[0].length; j++) {
                String digit = (ints[j] < 10 ? " " + ints[j] : "" + ints[j]);
                System.out.print(digit + " ");
            }
            System.out.println();
        }
        System.out.println("-----------\n");
    }


}
