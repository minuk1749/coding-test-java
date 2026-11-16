import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        int a = sc.nextInt();
        int b = sc.nextInt();
        
        while (a<=b) {
            System.out.print(a + " ");

            if(a%2 == 0) //짝수
            a += 3;
            
            else if (a%2 == 1) // 홀수
            a *= 2;
        }
        sc.close();
    }
}