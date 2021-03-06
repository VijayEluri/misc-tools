package net.orfjackal.bugs;

import sun.misc.Unsafe;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.Socket;

public class CrashingSlave {
    public static void main(String[] args) throws Exception {
        int commPort = Integer.parseInt(args[0]);

        Socket socket = new Socket("localhost", commPort);

//        System.out.println("some output");
//        System.err.println("some error output");

        OutputStream out = socket.getOutputStream();
        out.write(42);

        Runtime.getRuntime().halt(1);
//        getUnsafe().putAddress(0, 0);
//        allocationCrash();
    }

    private static void allocationCrash() { // might have worked in some Java 5 or 6 versions, but not anymore
        Object[] o = null;
        while (true) {
            o = new Object[]{o};
        }
    }

    private static Unsafe getUnsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }
}
