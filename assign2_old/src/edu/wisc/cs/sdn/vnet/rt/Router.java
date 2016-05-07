package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import java.nio.ByteBuffer;


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
		/********************************************************************/
				 
		if (etherPacket.getEtherType() !=  Ethernet.TYPE_IPv4){
			System.out.println("check ethernet type");
			return;   //drop the packet if it is not IPv4
		} else {
			short newChecksum = 0;
			short oldChecksum = 0; 
			RouteEntry routeFound = null;

			IPv4 packetIPv4 = (IPv4) etherPacket.getPayload(); 
			oldChecksum = packetIPv4.getChecksum(); 
			
                        System.out.println("check old checksum" + oldChecksum);
System.out.println("new print"); 
//			newChecksum = calculateChecksum(packetIPv4); 
			packetIPv4.setChecksum((short) 0);
			byte[] newPacketByte = packetIPv4.serialize(); 
                        System.out.println("assigned checksum 1");

			IPv4 newPacketIPv4 = (IPv4) packetIPv4.deserialize(newPacketByte, 0, packetIPv4.getTotalLength()); 

                        System.out.println("assigned checksum 0");

			
			newChecksum = newPacketIPv4.getChecksum(); 

                        System.out.println("assigned new checksum" + newChecksum);
			
            		if (newChecksum != oldChecksum){
				System.out.println("old " + oldChecksum + " new " + newChecksum); 
				System.out.println("checksum not equal"); 
				return; 
			} else {
	                        System.out.println("checksum passed");

				int ttl = (int) packetIPv4.getTtl() ;

System.out.println("old ttl is " + ttl); 
				ttl = ttl -1; 
				if (ttl > 0 ){
					                        System.out.println("check ttl" + ttl );
					packetIPv4.setTtl((byte) ttl); 
					if (this.interfaces.containsValue(packetIPv4.getDestinationAddress())){
						System.out.println("interface contains IP "); 
						return; 
					} else {

					                        System.out.println("reset packet");
						try{
							routeFound = this.routeTable.lookup(packetIPv4.getDestinationAddress()); 

	                        System.out.println("found route " + routeFound.toString());

							if ( routeFound != null)
							{

                        System.out.println("new route is not null");
								//           				newChecksum = calculateChecksum(packetIPv4); 
								packetIPv4.setChecksum((short) 0);
								newPacketByte = packetIPv4.serialize();
								newPacketIPv4 = (IPv4) packetIPv4.deserialize(newPacketByte, 0, packetIPv4.getTotalLength()); //newPacketByte.length); // packetIPv4.getTotalLength());
								newChecksum = newPacketIPv4.getChecksum();

System.out.println("new ttl is " + newPacketIPv4.getTtl() + " new checksum is " + newPacketIPv4.getChecksum()); 


System.out.println(newChecksum); 

								packetIPv4.setChecksum(newChecksum);

								etherPacket.setPayload(packetIPv4);
								/*

								   ArpEntry x = arpCache.lookup(routeFound.getInterface().getIpAddress());
								   if(x == null)
								   {
								   System.err.println("ARP lookup failed " + routeFound.toString());
								   System.out.println(routeFound.getInterface().getIpAddress());
								   System.out.println(arpCache.lookup(routeFound.getInterface().getIpAddress())); 
								   }
								   byte[] MACaddr = x.getMac().toBytes();
								   etherPacket.setDestinationMACAddress(MACaddr);

								//								etherPacket.setDestinationMACAddress(arpCache.lookup(routeFound.getInterface().getIpAddress()).getMac().toBytes());
								/*
								ArpEntry x = arpCache.lookup(inIface.getIpAddress());
								if(x == null)
								{
								System.err.println("ARP lookup failed");
								}
								byte[] MACaddr  = x.getMac().toBytes();
								etherPacket.setSourceMACAddress(MACaddr);
								System.out.println(MACaddr.toString()); 
								//								etherPacket.setSourceMACAddress(arpCache.lookup(inIface.getIpAddress()).getMac().toBytes());
								*/
								etherPacket.setSourceMACAddress(routeFound.getInterface().getMacAddress().toBytes()); 
								System.out.println(routeFound.getInterface().getMacAddress().toBytes());
								 								System.out.println(arpCache.lookup(routeFound.getInterface().getIpAddress()).getMac().toBytes());  
								if (routeFound.getGatewayAddress() == 0){
									System.out.println("go to the final destination"); 
									etherPacket.setDestinationMACAddress( arpCache.lookup(packetIPv4.getDestinationAddress()).getMac().toBytes()); 

System.out.println("Destination MAC " + arpCache.lookup(packetIPv4.getDestinationAddress()).getMac()); 

								} else {
									System.out.println("go to the gateway"); 
									etherPacket.setDestinationMACAddress(arpCache.lookup(routeFound.getGatewayAddress()).getMac().toBytes()); 
								}
//								etherPacket.setSourceMACAddress(routeFound.getInterface().getMacAddress().toBytes()); 

								System.out.println(routeFound.toString()); 
System.out.println("Source Mac " + etherPacket.getSourceMAC() + " Destination Mac " + etherPacket.getDestinationMAC()); 
System.out.println("routeFound " +  routeFound.toString() + " the iface is " + routeFound.getInterface().getName()  );
packetIPv4 = (IPv4) etherPacket.getPayload(); 
System.out.println("ttl is " + packetIPv4.getTtl() + " checksum is " + packetIPv4.getChecksum() + " source Mac " + packetIPv4.getSourceAddress() + " Dest Mac " + packetIPv4.getDestinationAddress());  
								this.sendPacket(etherPacket, routeFound.getInterface()); 
//								this.sendPacket(etherPacket,inIface);
								System.out.println("Source MAC " + routeFound.getInterface().getMacAddress()); 
//								System.out.println("Destination MAC " + arpCache.lookup(routeFound.getGatewayAddress()).getMac()); 
								System.out.println("check the MAC address"); 
								return; 
							}
						}
						catch(Exception e)
						{
							System.out.println("check the MAC addr3");
							e.printStackTrace();
						}
					}

				} else {
					System.out.println("check the MAC addr2"); 
					return; 
				}
			}
		}
	}

	private short calculateChecksum(IPv4 packetIPv4){
		byte[] data = new byte[packetIPv4.getTotalLength()];
		int accumulation = 0; 
		short newChecksum = 0; 
		ByteBuffer bb = ByteBuffer.wrap(data);
		byte[] payloadData = null; 

		packetIPv4.setChecksum((short) 0); 
		bb.put((byte) (((packetIPv4.getVersion() & 0xf) << 4) | (packetIPv4.getHeaderLength() & 0xf)));
		bb.put(packetIPv4.getDiffServ());
		bb.putShort(packetIPv4.getTotalLength());
		bb.putShort(packetIPv4.getIdentification());
		bb.putShort((short) (((packetIPv4.getFlags() & 0x7) << 13) | (packetIPv4.getFragmentOffset() & 0x1fff)));
		bb.put(packetIPv4.getTtl());
		bb.put(packetIPv4.getProtocol());
		bb.putShort(packetIPv4.getChecksum());
		bb.putInt(packetIPv4.getSourceAddress());
		bb.putInt(packetIPv4.getDestinationAddress());
		if (packetIPv4.getOptions() != null)
			bb.put(packetIPv4.getOptions());
		/*
		   if (packetIPv4.getPayload() != null){
		   payloadData = packetIPv4.getPayload().serialize();
		   if (payloadData != null )
		   bb.put(payloadData);
		   }
		 */

		for (int i = 0; i < packetIPv4.getHeaderLength() * 2; ++i) {
			accumulation += 0xffff & bb.getShort();
		}
		accumulation = ((accumulation >> 16) & 0xffff)
			+ (accumulation & 0xffff);
		newChecksum = (short) (~accumulation & 0xffff);

		return newChecksum; 
	}


}
