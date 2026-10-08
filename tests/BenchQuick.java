import java.io.*;
/** Minimal UI stand-in for startup timing: one GET_STATE round trip, then SHUTDOWN. */
public class BenchQuick {
    public static void main(String[] a) throws Exception {
        PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(FileDescriptor.out)), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(FileDescriptor.in)));
        out.println("CMD|GET_STATE"); in.readLine();
        out.println("CMD|SHUTDOWN");  in.readLine();
    }
}
