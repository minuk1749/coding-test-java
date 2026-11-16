import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int a = sc.nextInt();
        int b = sc.nextInt();
        int c = sc.nextInt();

        int min = (a<b)?((a<c)?a:b):((b<c)?b:c);

        if(a == min) System.out.print(1 + " ");
        else System.out.print(0 + " ");

        if (a==b && a==c) { // 세 개의 수가 모두 같은 경우
            System.out.print(1);
        } else System.out.print(0);

    }
}