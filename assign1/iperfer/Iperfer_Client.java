import java.net.*;
import java.io.*;


public class Iperfer_Client {
	String host_name;
	int port_number;
	long input_time;
	
	Iperfer_Client(String HostName, int PortNumber, long InputTime)
	{
		this.host_name = HostName;
		this.port_number = PortNumber;
		this.input_time = InputTime;
	}
	public void start()// static class uses only static variable- Bazinga :P
	{
		Socket socketID;
		long dataKBSent = 0;
        long start = System.currentTimeMillis();
        long current = 0;
        double rate;
        byte data[] = new byte[1000];
        
		try
		{
			socketID = new Socket(host_name, port_number);
	        OutputStream os = socketID.getOutputStream();
	
	        while (current - start < (input_time * 1000))
	        {
	            os.write(data);
	            os.flush();
	            dataKBSent++;
	            current = System.currentTimeMillis();
	        }
	        os.close();
	        socketID.close();
	        rate = (dataKBSent * 8.0) / (current - start);
            System.out.println("sent="+ dataKBSent + " KB rate=" + String.format("%.3f", rate) + " Mbps");       
		}
		catch (Exception e)
		{
			System.err.println("Exception:  " + e);
			e.printStackTrace();
		}
	}
}
