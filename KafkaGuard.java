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
 * kafka windows �������ػ�����<br/>
 * windows �汾kafka �������ļ������޷�ɾ��bug, ���ֺ�kafka ���쳣�˳����ػ����̣�����bugĿ¼
 * ��ǿ��ɾ��������Ŀ¼�����ļ���Ȼ������kafka����֤���������������С�
 * <br/>
 * addtion:�����۲�һ��ʱ�䣬�����е�ʱ�����ɾ�������Ŀ¼���ļ���Ҳ���ܽ�����⣬ֻ��ȫ��ɾ��log�ļ�����
 * ����ɾ��ģʽ�����delMode����������delAll�ǣ�ֻҪ�����˴����ػ����̾ͻ���������֮ǰǿ�����log�ļ��С�
 * @author jptian
 */
public class KafkaGuard {
	
	private  boolean stop =false;
	
	private  String flag_str ="��һ����������ʹ�ô��ļ��������޷����ʡ�";
	
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
	 * -Dguard.delMode=delBug(Ĭ��ֵ ɾ���������Ŀ¼) ,delAll(ɾ��kafka_logs_dirĿ¼�����е��ļ�)
	 * <br/>
	 * @param args
	 * @throws Exception 
	 */
	public void start(String[] args) throws Exception {
		
		final Map<String,String> envs  = System.getenv();
		/**
		 * ��ȡ�������� 
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
		
		//Ĭ�ϵ�bat �޷�������ԭ����
		//String runCmd = " .\\bin\\windows\\zookeeper-server-start.bat "+ server_path;
		
		//����������
		String runCmd = getDefCmd2(kafka_home);
		
		if(cmd!=null && !"".equals(cmd))
			runCmd= cmd;
		
		/**
		 * ֻҪ�ػ�û�رգ���һֱ����kafka �����м�Ҫ�ų�����ֱ�Ӱ���ctrl+c�� �������
		 * ֻ�ر�kafka���� ����ǿ��kill kafka�����̣��ػ����̻��������
		 */
		while(!stop)
		{
			
			//������ϴ���bug���֣����´�����֮ǰ��ǿ��ɾ������kafka�����������Ŀ¼
			if(bug_fire)
			{
				force_delete_bug_dir(delMode);
			}
			
			//ɾ�������Ŀ¼�����ñ������
			bug_fire =  false ;
			bug_dir  =  null  ;
			
			//��������
			System.out.println("----the begin---- ");
			log("----the begin---- ");
			
			System.out.println("----runCmd begin---- ");
			log("----runCmd begin---- ");
			
			System.out.println(runCmd);
			log(runCmd);
			
			System.out.println("----runCmd end---- ");
			log("----runCmd end---- ");
			
			//����kafka 
		Process procss = Runtime.getRuntime()
					.exec("cmd /c "+runCmd, envp/*������������*/, dir/*�����й���Ŀ¼*/);
		
		//������������̨���(һ�����)
		new Thread(){
			public void run() {
				InputStream in = procss.getInputStream();
				
				String b = null;
				
				try {
					BufferedReader rd  = new BufferedReader(
							new  InputStreamReader(in,"GBK"));
					
					while( ( b= rd.readLine() )!= null )
					{	
						//����bug��kafka һ���һ��ʱ��󣬺��Զ��˳�
						check_bug(b);
						System.out.println("һ��:"+b);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			private void check_bug(String b) {
				
				if( "".equals(b) || b.length()<flag_str.length())
					return ;
				
				//����Ƿ���֣�bug��ӡ���
				if(b.indexOf(flag_str)!=-1)
				{
					log("bug info:["+ b +"]\n");
					
					bug_fire = true;
					
					get_err_dir(b);
				}
			}

			private void get_err_dir(String b) {
				
				//java.nio.file.FileSystemException: E:\kafka-logs\nz-0\00000000000000000000.timeindex -> E:\kafka-logs\nz-0\00000000000000000000.timeindex.deleted: ��һ����������ʹ�ô��ļ��������޷����ʡ�
				
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
					//����bugĿ¼
					bug_dir = f.getParentFile().getAbsolutePath();
					System.out.println("set bug_dir  @ "+ bug_dir);
					log("set bug_dir  @ "+ bug_dir);
				}
				
			};
		}.start();
		
		//�������������һ����ϵͳ����Ĵ���
		new Thread(){
			public void run() {
				InputStream in = procss.getErrorStream();
				
				String b = null;
				
				try {
					BufferedReader rd  = new BufferedReader(
							new  InputStreamReader(in,"GBK"));
					
					while( ( b= rd.readLine() )!= null )
					{
						System.err.println("����"+b);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			};
		}.start();
		
		//�ȴ�kafka�����˳�
		procss.waitFor();
		
		System.out.print("----the end ---- ");
		log("----the end ---- ");
		
	  }
		
		if(this.stop)
		{	
			//��������
			System.gc();
			//ǿ���˳�
			System.exit(0);
		}
	}
	
	/**
	 * ·���д��ո񣬲���ִ�У�ֻ����defCmd2 �Ȳ�bat�ļ���Ȼ��ִ��bat
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
	 * ����bat�ļ�����
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
		
		//�滻ģ���ļ��еı���
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
	 * ɾ��Ŀ¼�£����е��ļ�
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
			b = delete_files(af) && b ; /*�ݹ�ɾ��*/
		}
		
		return b;
	}
	
	/**
	 * ɾ���ļ�+Ŀ¼,��������
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
				b = b && delete_files(af); /*�ݹ�ɾ��*/
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
	 * ǿ��ɾ������Ŀ¼
	 */
	private  void force_delete_bug_dir(String delMode) {
		
		//Ĭ��Ϊɾ����bug�ļ�
		String path = this.bug_dir;
		
		//��ȫɾ��ģʽ����ɾ��kafka��־Ŀ¼����������ļ���
		if("delAll".equals(delMode))
			path = this.kafka_logs_dir;
		
		final File def =new File(path);
		
		boolean del_ok =false;
		
		//���ɾ��ʧ�ܣ�����20�Σ�20�κ��׳��쳣��
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
	 * �ر�guard ������
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
					
					//ǿ���˳�
					if(i>=try_size)
					{	
						break;
					}
					
					//�����ո�
					if("".equals(line))
					{
						continue;
					}
					
					System.out.print(" get signal : " + line);
					log(" get signal : " + line);
					
					//���ֱ�ǣ����ùر��ź�
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
				
				//�ر����룬�����
				
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
							//�����ر�����
							final Socket socket =shutdown_monitor.accept();
							socket.setSoTimeout(200);
							new ShutDownMonitor(socket).start();
							//ͣ0.2 ��,�ڴ�����Ϣ֮ǰ��ͣ����������´�accept
							wait(200);
						}
						
						//�رշ�����
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
