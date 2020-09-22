import java.io.OutputStream;
import java.net.Socket;

/**
 * 关闭守护启动器，防止无限启动kafka 
 * 
 * 2020年9月7日21:23:56
 * 
 * @author jptian
 */
public class StopGuard {
	
	public static void main(String[] args) throws Exception {
		
		if(args.length !=1)
			throw new RuntimeException("bad args!");
		
		connect_and_send_signal(args[0]);
	}
	
	private static boolean connect_and_send_signal(String sig)
				throws Exception {
		
		final Socket socket = new Socket("127.0.0.1", 9527);
		
		OutputStream out  =socket.getOutputStream();
		sig = sig+"\n";
		out.write(sig.getBytes());
		out.flush();
		socket.close();
		
		return false;
	}
	
}
