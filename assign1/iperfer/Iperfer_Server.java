import java.net.*;
import java.io.*;

public class Iperfer_Server {
	int port_number;
	
	Iperfer_Server(int PortNumber)
	{
		this.port_number = PortNumber;
	}
	
	public void start()
	{
		ServerSocket serverSocketID;
		Socket clientSocketID;
		long startTime, endTime, latency, sizeRead;
		long receivedKBData = 0;
        byte data[] = new byte[1000];
        double rate;
        try
        {
        		serverSocketID = new ServerSocket(port_number);
                clientSocketID = serverSocketID.accept();
                InputStream is = clientSocketID.getInputStream();
                startTime = System.currentTimeMillis();
                
                while ((sizeRead = is.read(data)) != -1)
                {
                        receivedKBData += sizeRead;
                }
                endTime = System.currentTimeMillis();
                clientSocketID.close();
                serverSocketID.close();
                latency = (endTime - startTime);
                receivedKBData /= 1000;
                rate = (receivedKBData * 8.0) / latency;
                System.out.println("received="+ receivedKBData + " KB rate=" + String.format("%.3f", rate) + " Mbps");
        }
        catch (Exception e)
        {
        	System.err.println("Exception:  " + e);
			e.printStackTrace();
        }

	}
}
