import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();

        for(int i=1; i<=n; i++) {
            if(i%3 == 0) System.out.print(0 + " "); // 3의 배수
            else if ((i%10 == 3||i%10 == 6||i%10 == 9) || (((i-i%10)/10) == 3||(i-i%10)/10 ==6||(i-i%10)/10 ==9)) { // 13 16 19 23 26 29 3*
                System.out.print(0 + " ");
            } else  System.out.print(i + " ");
        }    
    }
}