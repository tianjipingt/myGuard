import java.io.OutputStream;
import java.net.Socket;

/**
 * �ر��ػ�����������ֹ��������kafka 
 * 
 * 2020��9��7��21:23:56
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
