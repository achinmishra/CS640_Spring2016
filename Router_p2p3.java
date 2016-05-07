package edu.wisc.cs.sdn.vnet.rt;

import java.util.*;
import java.nio.ByteBuffer;

import javax.swing.plaf.BorderUIResource.EtchedBorderUIResource;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.MACAddress;

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
					List<Ethernet> ipPacketList = packetQueue.get(ipAddr);
					packetQueue.remove(ipAddr);
					if (ipPacketList != null)
					{
						for (int i = 0; i < ipPacketList.size(); i++)
                                		{
                                        		Ethernet tempPacket = ipPacketList.get(i);
							Iface inIface = packetInterface.get(tempPacket).inIface;
							IPv4 ipPacket = (IPv4)tempPacket.getPayload(); 
							//TODO: send ICMP packet for destination host unreachable
							generateICMPPacke(ipPacket, inIface, 3, 1);
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

			if (arpSet.contains(ipGateWay) == false)
			{
				int attempts = 3;
				while(attempts-- >= 0)
				{
					ArpResend(ipGateWay, outIface, attempts);
					if(attempts != 0)
						try
						{
							wait(1000);
						}
						catch(InterruptedException ie)
						{
							ie.printStackTrace();
						}
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
        { 
		generateICMPPacke(ipPacket, inIface, 11, 0);
		return; 
	}
        
        // Reset checksum now that TTL is decremented
        ipPacket.resetChecksum();
        
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
		//generateICMPPacke(ipPacket, inIface, 3, 1); 
		sendDelayedPacket(etherPacket, inIface, outIface); 
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
        ether.setSourceMACAddress(outIface.getMacAddress().toBytes());
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
        System.arraycopy(padding, 0, icmpByte, 0, 4);
        System.arraycopy(serialized, 0, icmpByte, 4, tocopy);
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
