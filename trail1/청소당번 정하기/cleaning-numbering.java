import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n =sc.nextInt();

        int classroom =0 ;
        int floor = 0;
        int restroom = 0;

        for (int i=0; i<n; i++) {
            if(i % 12 == 11) restroom +=1;
            else if (i % 6 == 5) floor +=1;
            else if (i % 2 == 1) {classroom +=1;}
            else if (i % 3 == 2) floor +=1;
        }
        System.out.print(classroom + " " + floor + " " + restroom);
    }
}