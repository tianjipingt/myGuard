import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Calendar;
import java.util.Map;
import java.util.Set;

/**
 * kafka windows 启动器守护进程<br/>
 * windows 版本kafka 经常有文件锁定无法删除bug, 出现后kafka 会异常退出，守护进程，会标记bug目录
 * 并强制删除掉错误目录包括文件，然后重启kafka，保证生产环境持续运行。
 * <br/>
 * addtion:经过观察一段时间，发现有的时候仅仅删除出错的目录与文件，也不能解决问题，只有全部删除log文件才行
 * 所有删除模式添加了delMode参数，设置delAll是，只要出现了错误，守护进程就会在再启动之前强制清空log文件夹。
 * @author jptian
 */
public class KafkaGuard {
	
	private  boolean stop =false;
	
	private  String flag_str ="另一个程序正在使用此文件，进程无法访问。";
	
	private boolean bug_fire =false;
		
	private String bug_dir=null;
	
	private int max_del_try_size = 20;
	
	private static String logFile = ".\\logs\\guard";
	
	private String kafka_logs_dir=null;
	
	public static void main(String[] args) throws Exception {
		final	KafkaGuard guard =new KafkaGuard();
		guard.shutdown(9527);
		guard.start(args);
	}
	
	public static void log(String info) {
		 FileOutputStream fos =null;
		try{
			
			final Calendar cal = Calendar.getInstance();
			
			final int month = cal.get(Calendar.MONTH);
			final String ymd = cal.get(Calendar.YEAR)+ 
						(month<10?"0"+month:(month+"")) + 
							cal.get(Calendar.DAY_OF_MONTH);
			final File f =new File(logFile+"-"+(ymd)+".log");
			if(!f.exists())
			{
				f.getParentFile().mkdirs();
				f.createNewFile();
			}
			
			fos =new FileOutputStream(f,true);
			fos.write(info.getBytes());
			fos.write("\r\n".getBytes());
			fos.flush();
			
		}catch(Exception e)
		{
			e.printStackTrace();
			
		}finally{
			try {
				if(fos!=null)
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
	
	}
	
	/**
	 * -Dguard.kafka_home=E:/kafka_2.13-2.5.0 
	 * <br/>
	 * -Dguard.kafka_logs_dir=E:/kafka-logs
	 * <br/>
	 * -Dguard.server_path=config/server.properties
	 * <br/>
	 * -Dguard.cmd=.\bin\windows\kafka-server-start.bat
	 * <br/>
	 * -Dguard.delMode=delBug(默认值 删除出问题的目录) ,delAll(删除kafka_logs_dir目录下所有的文件)
	 * <br/>
	 * @param args
	 * @throws Exception 
	 */
	public void start(String[] args) throws Exception {
		
		final Map<String,String> envs  = System.getenv();
		/**
		 * 获取环境变量 
		 * new String[]{"JRE_HOME=C:/Program Files/Java/jdk1.8.0_131"}
		 */
		String[] envp =new String[envs.size()];
		
		Set<String> keys  = envs.keySet();
		
		int i=0;
		for(String k:keys)
		{
			envp[i++] = envs.get(k);
		}
		
		/*Properties props =	System.getProperties();
		
		for(Object aK : props.keySet())
		{
			System.out.println("syspros-key:"+aK);
		}*/
		
		String kafka_home = System.getProperty("guard.kafka_home");
		
		if(kafka_home==null || "".equals(kafka_home))
			throw new RuntimeException("kafka_home not found!");
		
		this.kafka_logs_dir = System.getProperty("guard.kafka_logs_dir");
		
		if(this.kafka_logs_dir==null || "".equals(this.kafka_logs_dir))
			throw new RuntimeException("kafka_logs_dir not found!");
		
		File dir =new File(kafka_home);
		
		if(!dir.exists())
			throw new RuntimeException("kafka_home not found!");
		
		if( !dir.canRead())
			throw new RuntimeException("kafka_home do not access !");
		
		File logsdir =new File(this.kafka_logs_dir);
		
		if( !logsdir.canRead())
			throw new RuntimeException("kafka_logs_dir  not found !");
		
		if( !logsdir.canRead())
			throw new RuntimeException("kafka_logs_dir do not access !");
		
		String server_path = System.getProperty("guard.server_path");
		
		if(server_path==null || "".equals(server_path))
			throw new RuntimeException("server_path not found!");
		
		String cmd = System.getProperty("guard.cmd");
		
		String delMode = System.getProperty("guard.delMode");
		
		//默认的bat 无法启动，原因不明
		//String runCmd = " .\\bin\\windows\\zookeeper-server-start.bat "+ server_path;
		
		//启动命令行
		String runCmd = getDefCmd2(kafka_home);
		
		if(cmd!=null && !"".equals(cmd))
			runCmd= cmd;
		
		/**
		 * 只要守护没关闭，会一直重启kafka ，这中间要排除出掉直接按“ctrl+c” 的情况。
		 * 只关闭kafka进程 或者强制kill kafka主进程，守护进程会持续工作
		 */
		while(!stop)
		{
			
			//如果，上次有bug出现，在下次启动之前，强制删除掉，kafka不能清理掉的目录
			if(bug_fire)
			{
				force_delete_bug_dir(delMode);
			}
			
			//删除完错误目录后，重置标记属性
			bug_fire =  false ;
			bug_dir  =  null  ;
			
			//启动进程
			System.out.println("----the begin---- ");
			log("----the begin---- ");
			
			System.out.println("----runCmd begin---- ");
			log("----runCmd begin---- ");
			
			System.out.println(runCmd);
			log(runCmd);
			
			System.out.println("----runCmd end---- ");
			log("----runCmd end---- ");
			
			//启动kafka 
		Process procss = Runtime.getRuntime()
					.exec("cmd /c "+runCmd, envp/*启动环境变量*/, dir/*命令行工作目录*/);
		
		//持续监听控制台输出(一般输出)
		new Thread(){
			public void run() {
				InputStream in = procss.getInputStream();
				
				String b = null;
				
				try {
					BufferedReader rd  = new BufferedReader(
							new  InputStreamReader(in,"GBK"));
					
					while( ( b= rd.readLine() )!= null )
					{	
						//出现bug后，kafka 一般过一段时间后，后自动退出
						check_bug(b);
						System.out.println("一般:"+b);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			private void check_bug(String b) {
				
				if( "".equals(b) || b.length()<flag_str.length())
					return ;
				
				//检查是否出现，bug打印标记
				if(b.indexOf(flag_str)!=-1)
				{
					log("bug info:["+ b +"]\n");
					
					bug_fire = true;
					
					get_err_dir(b);
				}
			}

			private void get_err_dir(String b) {
				
				//java.nio.file.FileSystemException: E:\kafka-logs\nz-0\00000000000000000000.timeindex -> E:\kafka-logs\nz-0\00000000000000000000.timeindex.deleted: 另一个程序正在使用此文件，进程无法访问。
				
				String exception_str="java.nio.file.FileSystemException:";
				
				b = b.substring(0+exception_str.length());
				
				b = b.trim();
				
				int idx_arrow=b.indexOf("->");
				
				String path  = b.substring(0,idx_arrow);
				
				path = path.trim();
				
				File f =new File(path);
				
				System.out.println("bug file @ "+ f.getAbsolutePath());
				
				if(f.getParentFile().isDirectory())
				{	
					//设置bug目录
					bug_dir = f.getParentFile().getAbsolutePath();
					System.out.println("set bug_dir  @ "+ bug_dir);
					log("set bug_dir  @ "+ bug_dir);
				}
				
			};
		}.start();
		
		//监听错误输出，一般是系统级别的错误
		new Thread(){
			public void run() {
				InputStream in = procss.getErrorStream();
				
				String b = null;
				
				try {
					BufferedReader rd  = new BufferedReader(
							new  InputStreamReader(in,"GBK"));
					
					while( ( b= rd.readLine() )!= null )
					{
						System.err.println("错误："+b);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			};
		}.start();
		
		//等待kafka进程退出
		procss.waitFor();
		
		System.out.print("----the end ---- ");
		log("----the end ---- ");
		
	  }
		
		if(this.stop)
		{	
			//先清理缓存
			System.gc();
			//强制退出
			System.exit(0);
		}
	}
	
	/**
	 * 路径中带空格，不能执行，只能用defCmd2 先产bat文件，然后执行bat
	 * @param kafkaHome
	 * @return
	 */
	@Deprecated
	private String getDefCmd(String kafkaHome/*,String kafkaLogDir*/) {
		
		final StringBuffer buf= new StringBuffer();
		
		String JAVA_HOME = System.getenv().get("JAVA_HOME");
		
		if(JAVA_HOME==null || "".equals(JAVA_HOME))
			throw new RuntimeException("java home not found!");
		
		//buf.append(" \"\""+JAVA_HOME+"\\bin\\java\"\" ");
		
		
		buf.append(" \"%JAVA_HOME%\\bin\\java\" ");
		
		buf.append(" -Xmx1G -Xms1G -server -XX:+UseG1GC "
				+ "  -XX:MaxGCPauseMillis=20 ");
		
		buf.append(" -XX:InitiatingHeapOccupancyPercent=35 "
				 + " -XX:+ExplicitGCInvokesConcurrent "
				 + " -Djava.awt.headless=true ");
		
		buf.append(" -Dcom.sun.management.jmxremote "
				 + " -Dcom.sun.management.jmxremote.authenticate=false  "
				 + " -Dcom.sun.management.jmxremote.ssl=false ");
		
		buf.append(" -Dkafka.logs.dir=\""+kafkaHome+"\\logs\" ");
		
		buf.append(" -Dlog4j.configuration=file:"+kafkaHome+"\\config\\log4j.properties ");
		
		final File libs =new File(kafkaHome,"libs");
		
		// eg. -cp " "a.jar" ; "b.jar" ; "c.jar" .. " 
		buf.append(" -cp \"");
		
		if(libs.exists() && libs.isDirectory())
		{	
			final File[] files = libs.listFiles();
			
			for(int i=0;i<files.length;i++)
			{
				File aFile = files[i];
				
				if(i>0)
					buf.append(";");
				
				buf.append("\""+aFile.getAbsolutePath()+"\"");
			}
		}
		
		buf.append("\" ");
		
		buf.append(" kafka.Kafka "+kafkaHome+"\\config\\server.properties ");		
		
		return buf.toString();
	}
	
	/**
	 * 采用bat文件运行
	 * @param kafkaHome
	 * @return
	 * @throws Exception
	 */
private String getDefCmd2(String kafkaHome/*,String kafkaLogDir*/) throws Exception {
		
	String JAVA_HOME = System.getenv().get("JAVA_HOME");
	
	if(JAVA_HOME==null || "".equals(JAVA_HOME))
		throw new RuntimeException("java home not found!");
	
		InputStream input =	KafkaGuard.
					class.getResourceAsStream("kafka-my-start.bat.tpl");
		
		int len = input.available();
		
		byte[] buf =new byte[len] ;
		input.read(buf);
		
		String cmd = new String(buf);
		
		//替换模板文件中的变量
		cmd = cmd.replace("${JAVA_HOME}", JAVA_HOME);
		
		cmd = cmd.replace("${kafka_home}", kafkaHome);
		
		File bat_file =new File(".\\kafka-my-start_gen.bat");
		
		if(!bat_file.exists())
			bat_file.createNewFile();
		
		System.out.println("bat_file:"+bat_file.getAbsolutePath());
		
		FileOutputStream fos = null;
		try{
			 	fos =new FileOutputStream(bat_file);
				
				fos.write(cmd.getBytes());
				fos.flush();
		}finally{
			if(fos!=null)	fos.close();
		}
		
		return ".\\kafka-my-start_gen.bat";
	}
	
	/**
	 * 删除目录下，所有的文件
	 * @param f
	 * @return
	 */
	private boolean delete_child_files(File f)
	{	
		boolean b = true;
		
		if(!f.isDirectory())
			return false;
		
		File[] fiels  = f.listFiles();
		
		for(File af:fiels)
		{	
			b = delete_files(af) && b ; /*递归删除*/
		}
		
		return b;
	}
	
	/**
	 * 删除文件+目录,包括本身
	 * @param f
	 * @return
	 */
	private boolean delete_files(File f)
	{
		boolean b = true;
		
		if(f.isDirectory())
		{
			File[] fiels  = f.listFiles();
			
			for(File af:fiels)
			{	
				b = b && delete_files(af); /*递归删除*/
			}
			
			log("delete dir:"+ f.getAbsolutePath());
			
			b = b && f.delete();
		}else{
			
			log("delete file:"+ f.getAbsolutePath());
			
			b = b && f.delete();
			
		}
		
		return b;
	}
	
	/***
	 * 强制删除错误目录
	 */
	private  void force_delete_bug_dir(String delMode) {
		
		//默认为删除，bug文件
		String path = this.bug_dir;
		
		//在全删除模式，则删除kafka日志目录下面的所有文件。
		if("delAll".equals(delMode))
			path = this.kafka_logs_dir;
		
		final File def =new File(path);
		
		boolean del_ok =false;
		
		//如果删除失败，尝试20次，20次后，抛出异常。
		int max_try = max_del_try_size;
		
		int i  = 0;
		do{
			
			if( i>= max_try)
				throw new RuntimeException("delete bug dir fail ,guard stop! ");
			
			if("delAll".equals(delMode))
				del_ok  = delete_child_files(def);
			else
				del_ok  = delete_files(def);
			
			i++;
			
		}while( !del_ok );
		
	}
	
	/**
	 * 关闭guard 监视器
	 * @author jptian
	 *
	 */
class ShutDownMonitor extends Thread {
		
		private Socket socket;
		
		public ShutDownMonitor(Socket socket) {
			super();
			this.socket = socket;
		}
		
		@Override
		public void run() {
			
			InputStream input = null;
			
			try {
				input = socket.getInputStream();
				
				BufferedReader reader = 
						new BufferedReader(new InputStreamReader(input));
				
				String line = null;
				int try_size = 10;
				int i = 0;
				while((line = reader.readLine())!=null)
				{
					line = line.trim();
					
					//强制退出
					if(i>=try_size)
					{	
						break;
					}
					
					//跳过空格
					if("".equals(line))
					{
						continue;
					}
					
					System.out.print(" get signal : " + line);
					log(" get signal : " + line);
					
					//发现标记，设置关闭信号
					if("guard-shutdown".equals(line))
					{
						KafkaGuard.this.stop= true;
						break;
					} else{
						System.out.print("bad signal !["+line+"]");
						log("bad signal !["+line+"]");
					}
					
					i++;
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			} finally{
				
				//关闭输入，输出流
				
				if(input!=null)
				{
					try {
						input.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
				try {
					if( !this.socket.isClosed() 
								&& this.socket.getOutputStream()!=null)
					{
						this.socket.getOutputStream().close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				try {
					if( !this.socket.isClosed() )
					{
						this.socket.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private  void shutdown(int i) throws Exception {
		
		ServerSocket shutdown_monitor 
			=new ServerSocket(i, 2, 
				InetAddress.getByName("127.0.0.1"));
		
		System.out.println("guard shutdown monitor listen on 127.0.0.1:"+i);
		
		log("guard shutdown monitor listen on 127.0.0.1:"+i);
		
		new Thread(){
			public void run() {
				
				synchronized(this)
				{
					try{
						
						while(!stop)
						{
							//监听关闭请求
							final Socket socket =shutdown_monitor.accept();
							socket.setSoTimeout(200);
							new ShutDownMonitor(socket).start();
							//停0.2 秒,在处理消息之前暂停，以免进入下次accept
							wait(200);
						}
						
						//关闭服务器
						shutdown_monitor.close();
					}catch(Exception e)
					{
						e.printStackTrace();
					}
				}
				
			};
		}.start();
	}
}
