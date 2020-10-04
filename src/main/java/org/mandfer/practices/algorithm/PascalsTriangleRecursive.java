package org.mandfer.practices.algorithm;

/**
 * @author marcandreuf on 29/10/2014.
 */
public class PascalsTriangleRecursive {


    public static void main(String[] args){
        PascalsTriangleRecursive pt = new PascalsTriangleRecursive();
        pt.printPascal(6);
    }

    public void printPascal(int numRows){
        int cell, row, col;
        int maxNumLength = Long.valueOf(Math.round(Math.pow(2d, numRows))*10).toString().length();

        for(row=1; row<=numRows; row++){
            col=1;

            int counter = (maxNumLength*(numRows - row))/2;
            printSpaces("", counter);

            while(col<=row){
                cell = cellValue(col,row);
                int spaces = maxNumLength - String.valueOf(cell).toString().length();
                printSpaces(""+cell, spaces);
                col++;
            }
            System.out.println("");
        }
    }

    private void printSpaces(String num, int spaces){
        System.out.print(num);
        for(int i=0;i<spaces;i++){
            System.out.print(" ");
        }
    }

    private int cellValue(int column, int row){
        if(column<0){return 0;}
        if(column==1){return 1;}
        if(column > row){return 0;}
        return cellValue(column, row -1) + cellValue(column -1, row -1);
    }


}
