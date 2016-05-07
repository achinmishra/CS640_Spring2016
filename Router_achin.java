package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;

	static byte[] broadcastAddr = new byte[6];

	class InterfacePair
	{
                public Iface inIface;
                public Iface outIface;
                public InterfacePair(Iface inIface, Iface outIface)
		{
                        this.inIface = inIface;
                        this.outIface = outIface;
                }
        }

	Map<Integer, List<Ethernet>> packetQueue = Collections.synchronizedMap(new HashMap<Integer, List<Ethernet>>());
	Map<Ethernet, InterfacePair> packetInterface = Collections.synchronizedMap(new HashMap<Ethernet, InterfacePair>());
	Set<Integer> arpSet = Collections.synchronizedSet(new HashSet<Integer>());	

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		Arrays.fill(broadcastAddr, (byte) 0xFF);
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
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
		
		case Ethernet.TYPE_ARP:
                        manageArpPacketHere(etherPacket, inIface);
                        break;
		// Ignore all other packet types, for now
		}
		
		/********************************************************************/
	}

	private void manageArpPacketHere(Ethernet etherPacket, Iface inIface)
	{
		ARP arpPacket = (ARP) etherPacket.getPayload();
                if(arpPacket.getOpCode() == ARP.OP_REQUEST)
		{
                	manageRequestArp(etherPacket, inIface);
                }
		else if (arpPacket.getOpCode() == ARP.OP_REPLY)
		{
                        manageReplyArp(etherPacket);
                }
	}	

	private void manageRequestArp(Ethernet etherPacket, Iface inIface)
	{
                ARP arpPacket = (ARP) etherPacket.getPayload();
                ByteBuffer bb = ByteBuffer.wrap(arpPacket.getTargetProtocolAddress());
		int destIp = bb.getInt();
                if (inIface.getIpAddress() != destIp)
		{
                        return;
                }
		Ethernet etherReply = new Ethernet();
                ARP arpReply = new ARP();
		
		etherReply.setEtherType(Ethernet.TYPE_ARP);
                etherReply.setSourceMACAddress(inIface.getMacAddress().toBytes());
                etherReply.setDestinationMACAddress(etherPacket.getSourceMACAddress());

                arpReply.setHardwareType(ARP.HW_TYPE_ETHERNET);
                arpReply.setProtocolType(ARP.PROTO_TYPE_IP);
                arpReply.setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH);
                arpReply.setProtocolAddressLength((byte) 4);
                arpReply.setOpCode(ARP.OP_REPLY);
                arpReply.setSenderHardwareAddress(inIface.getMacAddress().toBytes());
                arpReply.setSenderProtocolAddress(inIface.getIpAddress());
                arpReply.setTargetHardwareAddress(arpPacket.getSenderHardwareAddress());
                arpReply.setTargetProtocolAddress(arpPacket.getSenderProtocolAddress());

                etherReply.setPayload(arpReply);
		
                sendPacket(etherReply, inIface);
        }
	
	private void manageReplyArp(Ethernet etherPacket)
	{
		ARP arpPacket = (ARP) etherPacket.getPayload();
		MACAddress macAddr = new MACAddress(arpPacket.getSenderHardwareAddress());
		byte[] macByteAddr = macAddr.toBytes();
		int ipAddr = IPv4.toIPv4Address(arpPacket.getSenderProtocolAddress());
		arpCache.insert(macAddr, ipAddr); //enqueue in the arp cache here
		
		synchronized (packetQueue)
		{
			arpSet.remove(ipAddr);
			List<Ethernet> ipPacketList = packetQueue.get(ipAddr);
			packetQueue.remove(ipAddr);
			if (ipPacketList != null) 
			{
				for (int i = 0; i < ipPacketList.size(); i++)
				{
					Ethernet tempPacket = ipPacketList.get(i);
					Iface outIface = packetInterface.get(tempPacket).outIface;
					tempPacket.setDestinationMACAddress(macByteAddr);
					sendPacket(tempPacket, outIface);
					packetInterface.remove(tempPacket);
				}
			}
		}
	}
	
	private void ArpResend(int ipAddr, Iface outIface, int attempts)
	{
		ArpEntry entry = arpCache.lookup(ipAddr);
		if (entry != null)
		{
			cancel();
			return;
		}
		else
		{
			if (attempts == 0)
			{
				//dropping packets from the queues, maps and other data structures
				synchronized (packetQueue)
				{
					arpSet.remove(ipAddr);
					List<Ethernet> ipPacketList = packetQueue.get(ip);
					packetQueue.remove(ipAddr);
					if (ipPacketList != null)
					{
						for (int i = 0; i < ipPacketList.size(); i++)
                                		{
                                        		Ethernet tempPacket = ipPacketList.get(i);
							//TODO: send ICMP packet for destination host unreachable
							packetInterface.remove(tempPacket);
						}
					}
				}
				return;
			}
			else
			{ //retrying, generating and sending the ARP request here
				Ethernet etherReply = new Ethernet();
				ARP arpReply = new ARP();
				byte[] targetHwAddr = new byte[6];
		                Arrays.fill(targetHwAddr, (byte) 0);
				byte[] ipByte = IPv4.toIPv4AddressBytes(ipAddr);

				etherReply.setEtherType(Ethernet.TYPE_ARP);
				etherReply.setSourceMACAddress(outIface.getMacAddress().toBytes());
				etherReply.setDestinationMACAddress(broadcastAddr);

				arpReply.setHardwareType(ARP.HW_TYPE_ETHERNET);
				arpReply.setProtocolType(ARP.PROTO_TYPE_IP);
				arpReply.setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH);
				arpReply.setProtocolAddressLength((byte) 4);
				arpReply.setOpCode(ARP.OP_REQUEST);
				arpReply.setSenderHardwareAddress(outIface.getMacAddress().toBytes());
				arpReply.setSenderProtocolAddress(outIface.getIpAddress());
				arpReply.setTargetHardwareAddress(targetHwAddr);
				arpReply.setTargetProtocolAddress(ipByte);

				etherReply.setPayload(arpReply);

				sendPacket(etherReply, outIface);

			}
		}
	}

	private void sendDelayedPacket(Ethernet packet, Iface inIface, Iface outIface)
	{
		IPv4 ipAddr = (IPv4) packet.getPayload();
		RouteEntry match = this.routeTable.lookup(ipAddr.getDestinationAddress());
		int ipGateWay = match.getGatewayAddress();
		if (ipGateWay == 0)
		{
			ipGateWay = ipAddr.getDestinationAddress();
		}
		packet.setDestinationMACAddress(broadcastAddr);
		synchronized (packetQueue)
		{
			if (!packetQueue.containsKey(ipGateWay))
			{
				packetQueue.put(ipGateWay, new ArrayList<Ethernet>());
			}

			List<Ethernet> ipPacketList = packetQueue.get(ipGateWay);
			ipPacketList.add(packet);
			packetInterface.put(packet, new InterfacePair(inIface,outIface));

			if (arpSet.contains(ipGateWay) == null)
			{
				int attempts = 3;
				while(attempts-- >= 0)
				{
					ArpResend(ipGateWay, outIface, attempts);
					if(attempts != 0)
						wait(1000);
				}
                        }
                }
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
        { return; }
        
        // Check TTL
        ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
        if (0 == ipPacket.getTtl())
        { return; }
        
        // Reset checksum now that TTL is decremented
        ipPacket.resetChecksum();
        
        // Check if packet is destined for one of router's interfaces
        for (Iface iface : this.interfaces.values())
        {
        	if (ipPacket.getDestinationAddress() == iface.getIpAddress())
        	{ return; }
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
        { return; }

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
        { return; }
        etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
        
        this.sendPacket(etherPacket, outIface);
    }
}
