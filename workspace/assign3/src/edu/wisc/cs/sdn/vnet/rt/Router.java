package edu.wisc.cs.sdn.vnet.rt;

import java.util.Arrays;
import java.util.List;

import javax.swing.plaf.BorderUIResource.EtchedBorderUIResource;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }


	private RIPv2 routerRIP;   
	
	/**
	 * @return Generate routing table through dynamic RIP 
	 */ 
	public void startRIP(){
		int dstIp; 
		int gwIp = 0;
		int maskIp; 

		//check if a static routing tale is loaded or not
		if (this.getRouteTable() != null ){
			return; 
		}
		//add directly reachable subnets to routing table
		System.out.println("RIP start to initial routing table");
        for (Iface iface : this.interfaces.values())
        {
        	dstIp = iface.getIpAddress() & iface.getSubnetMask(); 
        	maskIp = iface.getSubnetMask();        	
        	this.routeTable.insert(dstIp, gwIp, maskIp, iface);       	
        }
        System.out.println("RIP Routing table: \n" + this.getRouteTable());
        
        //other initial code
        // e.g. setup RIP timeout, setup RIP entries, send request to neighboring routers       	
        //setup the new RIP request msg
        
        //get the initial RIP entries from the routing table
        this.routerRIP = this.routeTable.routerRIPEntry();
        
        //send out rip request to the neighboring routers
        Ethernet ether = new Ethernet(); 
        IPv4 ip = new IPv4(); 
        UDP udpPacket = new UDP(); 
        udpPacket.setDestinationPort((short) 520); 
        udpPacket.setSourcePort((short) 520); 
        ip.setPayload(udpPacket); 
        ether.setPayload(ip); 
        
        for (Iface iface : this.interfaces.values())
        	this.sendPacket(ether, iface); 

	}

    /** Thread for periodic tasks sending RIP */
    private Thread tasksThread;
    
    /*
     * @return Periodically sending out RIP msg if in dynamic mode
     */
    private void runRIP(){
    	//fork a new thread to send RIP info periodically 
    	this.tasksThread = new Thread(); 
    	RIPv2 ripv2Packet = new RIPv2(); 
    	ripv2Packet.setCommand((byte)2);  //RIP response
    	
        //send out rip request to the neighboring routers
        Ethernet ether = new Ethernet(); 
        IPv4 ip = new IPv4(); 
        UDP udpPacket = new UDP(); 
        udpPacket.setPayload(routerRIP); 
        udpPacket.setDestinationPort((short) 520); 
        udpPacket.setSourcePort((short) 520); 
        ip.setPayload(udpPacket); 
        ether.setPayload(ip); 
        
        for (Iface iface : this.interfaces.values())
        	this.sendPacket(ether, iface); 
    }
    
    /*
     * @return Handle RIP package if in dynamic mode
     */
    private void handleRIP(RIPv2 ripv2, int sourceIP, Iface inIface){
    	
    	if (ripv2.getCommand() == RIPv2.COMMAND_REQUEST ){
    		synchronized(this.routerRIP){
    			//reply to the request 
    			Ethernet ether = new Ethernet(); 
    			IPv4 ip = new IPv4(); 
    			UDP udpPacket = new UDP(); 
    			//response to the source port if it is a response
    			udpPacket.setDestinationPort(((UDP)ripv2.getPayload()).getSourcePort());  
    			udpPacket.setSourcePort((short) 520); 
    			udpPacket.setPayload(this.routerRIP); 
    			ip.setPayload(udpPacket); 
    			ether.setPayload(ip); 
            
    			RouteEntry bestMatch = this.routeTable.lookup(sourceIP);

    			// If no entry matched, do nothing
    			//should learn every time a packet comes
    			if (null == bestMatch)
    			{ 
    				return;
    			}

    			// Make sure we don't sent a packet back out the interface it came in
    			Iface outIface = bestMatch.getInterface();
    			if (outIface == inIface)
    			{ return; }       
            
    			this.sendPacket(ether, outIface); 
    		}
    		   		
    	} else if (ripv2.getCommand() == RIPv2.COMMAND_RESPONSE) {
    		//update the entry 
    		RIPv2 arriveRIPv2 = (RIPv2) ((UDP)ripv2.getPayload()).getPayload(); 
    		
    		List<RIPv2Entry> routerRIPList = this.routerRIP.getEntries(); 
    		if (routerRIPList.contains())
    	
    		
    		
    		
    	} else return ;   //drop packet if it dose not have a correct type
    	

    }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */
		
		switch(etherPacket.getEtherType())
		{
		case Ethernet.TYPE_IPv4:
			this.handleIpPacket(etherPacket, inIface);
			break;
		// Ignore all other packet types, for now
		}
		
		/********************************************************************/
	}
	
	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
		
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        System.out.println("Handle IP packet");

        // Verify checksum
        short origCksum = ipPacket.getChecksum();
        ipPacket.resetChecksum();
        byte[] serialized = ipPacket.serialize();
        ipPacket.deserialize(serialized, 0, serialized.length);
        short calcCksum = ipPacket.getChecksum();
        if (origCksum != calcCksum)
        { 
		System.out.println("checksum mismatch, drop packet"); 
		return; }
        
        // Check TTL
        System.out.println("start to handle ttl");
        ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
        System.out.println(ipPacket.getTtl());
        if (0 == ipPacket.getTtl())
        { 
        	//Generate the ICMP message
		System.out.println("Generate the ICMP time exceeded message"); 
        	generateICMPPacke(ipPacket, inIface, 11, 0);
		System.out.println("send out ICMP time exceeded msg"); 
        	//drop the original packet 
        	return; 
        }
        
        // Reset checksum now that TTL is decremented
        ipPacket.resetChecksum();
        
        //determine if it is RIP request or response
        if (ipPacket.getDestinationAddress() == IPv4.toIPv4Address("244.0.0.9")
        		&& ipPacket.getProtocol() == IPv4.PROTOCOL_UDP){
        	UDP udpPacket = (UDP)ipPacket.getPayload(); 
        	if (udpPacket.getDestinationPort() == 520){
        		RIPv2 ripPacket = (RIPv2)udpPacket.getPayload();
        		int sourceIP = ipPacket.getSourceAddress();  
        		handleRIP(ripPacket, sourceIP, inIface);	
        	}    	
        }
        // Check if packet is destined for one of router's interfaces
        for (Iface iface : this.interfaces.values())
        {
        	if (ipPacket.getDestinationAddress() == iface.getIpAddress())
        	{ 
    			//check if it is of TCP or UDP type
    			if (ipPacket.getProtocol() == IPv4.PROTOCOL_TCP || etherPacket.getEtherType() == IPv4.PROTOCOL_UDP){
    				//generate destination port unreachable msg
    				generateICMPPacke(ipPacket, inIface, 3, 3);
    			}
    			if (ipPacket.getProtocol() == IPv4.PROTOCOL_ICMP){
    				//check if an echo reply needed
    				ICMP icmp = (ICMP) ipPacket.getPayload(); 
    				if (icmp.getIcmpType() == 8) {
    					//check if the destination IP matches the router's interfaces
    					//already checked in the upper loop
    					
    					//generate echo msg
    					generateEchoPacke(ipPacket, inIface);					
    				}
    			}	
        		return; 
        	}
        }
		
        // Do route lookup and forward
        this.forwardIpPacket(etherPacket, inIface);
	}

    private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
    {
        // Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
        System.out.println("Forward IP packet");
		
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        int dstAddr = ipPacket.getDestinationAddress();

        // Find matching route table entry 
        RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

        // If no entry matched, do nothing
        if (null == bestMatch)
        { 
        	//generate the Destination net unreachable msg
        	generateICMPPacke(ipPacket, inIface, 3, 0);
        	//drop the original packet
        	return;
        }

        // Make sure we don't sent a packet back out the interface it came in
        Iface outIface = bestMatch.getInterface();
        if (outIface == inIface)
        { return; }

        // Set source MAC address in Ethernet header
        etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

        // If no gateway, then nextHop is IP destination
        int nextHop = bestMatch.getGatewayAddress();
        if (0 == nextHop)
        { nextHop = dstAddr; }

        // Set destination MAC address in Ethernet header
        ArpEntry arpEntry = this.arpCache.lookup(nextHop);
        if (null == arpEntry)
        { 
        	//generate the Destination host unreachable msg
        	generateICMPPacke(ipPacket, inIface, 3, 1);
        	return; 
        }
        etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
        
        this.sendPacket(etherPacket, outIface);
    }
    
    private void generateICMPPacke(IPv4 ipPacket, Iface inIface, int type, int code){
    	Ethernet ether = new Ethernet(); 
    	IPv4 ip = new IPv4(); 
    	ICMP icmp = new ICMP(); 
    	Data data = new Data(); 
    	       	
    	//loop up the destination MAC address
    	//Get source IP header from the incoming package
    	int dstAddr = ipPacket.getSourceAddress(); 
    	//Find matching route table entry
    	RouteEntry bestMatch = this.routeTable.lookup(dstAddr); 
    	// if no entry matched, do nothing (which is not likely)
    	if (null == bestMatch)
    		return; 
    	//We donot need to check if the packet sent back to the router itself, right? 
    	Iface outIface = bestMatch.getInterface(); 

    	int nextHop = bestMatch.getGatewayAddress(); 
    	if (0 == nextHop)
    		nextHop = dstAddr; 
    	ArpEntry arpEntry = this.arpCache.lookup(nextHop); 
    	if (null == arpEntry)
    		return; 
    	
    	
    	//setup the Ethernet header
        ether.setEtherType(Ethernet.TYPE_IPv4); 
        //the outIface is equals to the inIface ? 
        ether.setSourceMACAddress(inIface.getMacAddress().toBytes()); 
//        ether.setSourceMACAddress(outIface.getMacAddress().toBytes()); 
    	ether.setDestinationMACAddress(arpEntry.getMac().toBytes()); 
    	
    	//setup IP header
    	ip.setTtl((byte) 64);
    	ip.setProtocol(IPv4.PROTOCOL_ICMP); 
    	ip.setSourceAddress(inIface.getIpAddress()); 
    	ip.setDestinationAddress(dstAddr); 
    	
    	//setup ICMP header
    	icmp.setIcmpType((byte) type); 
    	icmp.setIcmpCode((byte) code); 
    	
    	//setup ICMP payload
    	byte[] padding = new byte[4];
    	Arrays.fill(padding, (byte) 0);
    	int totalLength = 4 + ipPacket.getHeaderLength() * 4 + 8;
	int tocopy = ipPacket.getHeaderLength() * 4 + 8; 
    	byte[] icmpByte = new byte[totalLength];
	byte[] serialized = ipPacket.serialize();
	Arrays.fill(icmpByte, (byte) 1);
//	System.out.println("totoalLength is " + totalLength + " to copy is " + tocopy);  

//	System.out.println("the size of the IP package is " + serialized.length); 
//	System.out.println("the array length is " + icmpByte.length); 
    	System.arraycopy(padding, 0, icmpByte, 0, 4);
    	System.arraycopy(serialized, 0, icmpByte, 4, tocopy); 
//	System.out.print(icmpByte);  

/*
	for(byte c : icmpByte) {
    		System.out.format("%d ", c);
	}
*/ 	   	
    	data.setData(icmpByte); 
    	
    	//setup the packet
    	icmp.setPayload(data);
    	icmp.resetChecksum();
    	ip.setPayload(icmp); 
    	ip.resetChecksum();
    	ether.setPayload(ip); 
    	ether.resetChecksum();
    	
    	this.sendPacket(ether, outIface); 
    	
    }
    
    private void generateEchoPacke(IPv4 ipPacket, Iface inIface){
    	Ethernet ether = new Ethernet(); 
    	IPv4 ip = new IPv4(); 
    	ICMP icmp = new ICMP(); 
    	       	
    	
    	//loop up the destination MAC address
    	//Get source IP header from the incoming package
    	int dstAddr = ipPacket.getSourceAddress(); 
    	//Find matching route table entry
    	RouteEntry bestMatch = this.routeTable.lookup(dstAddr); 
    	// if no entry matched, do nothing (which is not likely)
    	if (null == bestMatch)
    		return; 
    	//We donot need to check if the packet sent back to the router itself, right? 
    	Iface outIface = bestMatch.getInterface(); 

    	int nextHop = bestMatch.getGatewayAddress(); 
    	if (0 == nextHop)
    		nextHop = dstAddr; 
    	ArpEntry arpEntry = this.arpCache.lookup(nextHop); 
    	if (null == arpEntry) 
    		return; 
    	
    	//setup the Ethernet header
        ether.setEtherType(Ethernet.TYPE_IPv4); 
        //the outIface is equals to the inIface ? 
        ether.setSourceMACAddress(outIface.getMacAddress().toBytes()); 
    	ether.setDestinationMACAddress(arpEntry.getMac().toBytes()); 
    	
    	//setup IP header
    	ip.setTtl((byte) 64);
    	ip.setProtocol(IPv4.PROTOCOL_ICMP); 
    	ip.setSourceAddress(ipPacket.getDestinationAddress()); 
    	ip.setDestinationAddress(dstAddr); 
    	
    	//setup ICMP header
    	icmp.setIcmpType((byte) 0); 
    	icmp.setIcmpCode((byte) 0);     	
    	//setup ICMP pay load
    	ICMP oldICMPPacket = (ICMP) ipPacket.getPayload(); 
    	icmp.setPayload(oldICMPPacket.getPayload()); 
    	icmp.resetChecksum();
    	
    	//setup the packet
    	ip.setPayload(icmp); 
    	ip.resetChecksum();
    	ether.setPayload(ip); 
    	ether.resetChecksum();
    	
    	this.sendPacket(ether, outIface); 
    	
    }
}
