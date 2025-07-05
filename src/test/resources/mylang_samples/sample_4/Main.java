package sample;

public class Main {
    public static void main(String[] args) {
        Test obj = new Test();

        // 1. 最初はデフォルトのv1状態なので、v1のdisplayが呼ばれる
        obj.display();

        // 2. log(String)はv2にしかないので、内部状態がv2に切り替わる
        obj.log(123);

        // 3. 状態がv2に変わったので、今度はv2のdisplayが呼ばれる
        obj.display();
        
        // 4. log(int)はv1にしかないので、内部状態がv1に切り替わる
        obj.log("hello");

        // 5. 状態がv1に戻ったので、再度v1のdisplayが呼ばれる
        obj.display();
    }
}