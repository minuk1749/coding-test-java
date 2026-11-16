import java.util.Scanner;
import java.lang.Math;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int a = sc.nextInt();
        int b = sc.nextInt();
        double ans = Math.floor(((double)(a/b)*Math.pow(10,20))/Math.pow(10,2));

        System.out.println(ans);
    }
}