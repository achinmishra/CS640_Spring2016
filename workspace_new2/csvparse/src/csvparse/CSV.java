package csvparse;

public class CSV {
	//field
	String IP; 
	int mask; 
	String location; 
	
	//constructor
	CSV(){
		
	}
	
	//method
	String getIP(){
		return this.IP; 
		
	}
	
	int getMask(){
		return this.mask; 
	}
	
	String getLocation(){
		return this.location; 
	}
	
	void setIP(String IP){
		this.IP = IP; 
	}

	void setMask(int Mask){
		this.mask = Mask; 
	}
	
	void setLocation(String Location){
		this.location = Location; 
	}
	
	boolean ifMatch(String IP){
		int inputIP = toIPv4Address(IP); 
		int curIP = toIPv4Address(this.IP); 
		
		//add the inputIP with the mask address
		inputIP = inputIP & this.mask; 
		curIP = curIP & this.mask; 
		if (inputIP == curIP)
			return true; 	
		return false; 
	}
	
    /**
     * Accepts an IPv4 address of the form xxx.xxx.xxx.xxx, ie 192.168.0.1 and
     * returns the corresponding 32 bit integer.
     * @param ipAddress
     * @return
     */
    public static int toIPv4Address(String ipAddress) {
        if (ipAddress == null)
            throw new IllegalArgumentException("Specified IPv4 address must" +
                "contain 4 sets of numerical digits separated by periods");
        String[] octets = ipAddress.split("\\.");
        if (octets.length != 4)
            throw new IllegalArgumentException("Specified IPv4 address must" +
                "contain 4 sets of numerical digits separated by periods");

        int result = 0;
        for (int i = 0; i < 4; ++i) {
            int oct = Integer.valueOf(octets[i]);
            if (oct > 255 || oct < 0)
                throw new IllegalArgumentException("Octet values in specified" +
                        " IPv4 address must be 0 <= value <= 255");
            result |=  oct << ((3-i)*8);
        }
        return result;
    }
}
