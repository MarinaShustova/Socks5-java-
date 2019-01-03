import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        if (args.length == 1){
            Integer lport;
            try {
                lport = Integer.parseInt(args[0]);
                try {
                    Forwarder frwd = new Forwarder(lport);
                    frwd.forward();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }catch (NumberFormatException ex){
                System.out.println("You gave incorrect parameter. Server needs a port number it will be bound to.");
                return;
            }
        }
    }
}
