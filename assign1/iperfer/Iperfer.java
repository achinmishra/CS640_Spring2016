
public class Iperfer {
	public static void main(String[] args)
	{
		int i = 0;
		boolean IsClient = false, IsServer = false;
		String HostName = "";
		int PortNumber = 0;
		long InputTime = 0;
		
		while(i < args.length) //input of arguments can be in any order
		{						//therefore, we must look for -c or -s in whole string
			if(args[i].equals("-c"))
			{
				if (args.length != 7)
				{
			        System.err.println("Error: missing or additional arguments");
			        System.exit(-1);
				}
				IsClient = true;
			}
			else if(args[i].equals("-s"))
			{
				if (args.length != 3)
				{
			        System.err.println("Error: missing or additional arguments");
			        System.exit(-1);
				}
				IsServer = true;
			}
			i++;
		}
		if(IsClient)
		{
			i = 0;
			while(i < args.length)
			{
				char token = args[i].charAt(1);
				switch(token)
				{
					case 'h': HostName = args[i+1];
								i++;
								break;
								
					case 'p': try
								{
									PortNumber = Integer.parseInt(args[i+1]);
								}
								catch (NumberFormatException nfe)
								{
									System.err.println("Error: port number must be an integer");
									System.exit(-1);
								}
								if (PortNumber <= 1024 || PortNumber >= 65535)
								{
									System.err.println("Error: port number must be in the range 1024 to 65535");
									System.exit(-1);
								}

								i++;
								break;
								
					case 't': try
								{
									InputTime = Integer.parseInt(args[i+1]);
								}
								catch (NumberFormatException nfe)
								{
									System.err.println("Error: time must be an integer");
									System.exit(-1);
								}					
								i++;
								break;
								
					default: i++;
				};
			}
		}
		else if (IsServer)
		{
			i = 0;
			while(i < args.length)
			{
				char token = args[i].charAt(1);
				switch(token)
				{				
					case 'p': try
								{
									PortNumber = Integer.parseInt(args[i+1]);
								}
								catch (NumberFormatException nfe)
								{
									System.err.println("Error: port number must be an integer");
									System.exit(-1);
								}
								if (PortNumber <= 1024 || PortNumber >= 65535)
								{
									System.err.println("Error: port number must be in the range 1024 to 65535");
									System.exit(-1);
								}

								i++;
								break;
					default: i++;
				};
			}
		}
		if (IsServer)
		{
			Iperfer_Server ServerInstance = new Iperfer_Server(PortNumber);
			ServerInstance.start();
		}
		if (IsClient)
		{
			Iperfer_Client ClientInstance = new Iperfer_Client(HostName, PortNumber, InputTime);
			ClientInstance.start();
		}
	}
}
