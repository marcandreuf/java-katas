package org.mandfer.practices.algorithm;

/**
 * 
 * @author marcandreuf
 */
public class PascalsTriangle {

    public static void main(String[] args){
        PascalsTriangle pt = new PascalsTriangle();
        System.out.print(pt.printPascal(6));
    }

    public String printPascal(int numRows){
        StringBuilder sb = new StringBuilder();
        int num, spaces;
        int maxNumLength = Long.valueOf(Math.round(Math.pow(2d, numRows))*10).toString().length();

        for(int row=0; row<=numRows; row++){
            num=1;
            spaces = calcSpacesBeforeRow(numRows, maxNumLength, row);
            printSpaces(sb, "", spaces);

            for(int col=0; col <= row; col++){
                num = calcCellValue(num, row, col);
                spaces = calcSpacesAfterCellNumber(num, maxNumLength);
                printSpaces(sb, ""+num, spaces);
            }
            sb.append(System.getProperty("line.separator"));
        }
        return sb.toString();
    }

    private int calcSpacesBeforeRow(int numRows, int maxNumLength, int row) {
        return (maxNumLength*(numRows - row))/2;
    }

    private int calcSpacesAfterCellNumber(int num, int maxNumLength) {
        return maxNumLength - String.valueOf(num).toString().length();
    }

    private int calcCellValue(int num, int row, int col) {
        if(col > 0){
            num = num * ((row+1) - col) / col;
        }
        return num;
    }

    private void printSpaces(StringBuilder sb, String num, int spaces){
        sb.append(num);
        for(int i=0;i<spaces;i++){
            sb.append(" ");
        }
    }

}
