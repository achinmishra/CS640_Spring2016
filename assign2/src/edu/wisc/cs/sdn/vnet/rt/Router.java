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
			return;   //drop the packet if it is not IPv4
		} else {
			short newChecksum = 0;
			short oldChecksum = 0; 
			RouteEntry routeFound = null;

			IPv4 packetIPv4 = (IPv4) etherPacket.getPayload(); 
			oldChecksum = packetIPv4.getChecksum(); 
			
			packetIPv4.setChecksum((short) 0);
			byte[] newPacketByte = packetIPv4.serialize(); 

			IPv4 newPacketIPv4 = (IPv4) packetIPv4.deserialize(newPacketByte, 0, packetIPv4.getTotalLength()); 

			newChecksum = newPacketIPv4.getChecksum(); 

            		if (newChecksum != oldChecksum){
				return; 
			} else {

				int ttl = (int) packetIPv4.getTtl() ;

				ttl = ttl -1; 
				if (ttl > 0 ){
					packetIPv4.setTtl((byte) ttl); 
					if (this.interfaces.containsValue(packetIPv4.getDestinationAddress())){
						return; 
					} else {

						try{
							routeFound = this.routeTable.lookup(packetIPv4.getDestinationAddress()); 


							if ( routeFound != null)
							{

								packetIPv4.setChecksum((short) 0);
								newPacketByte = packetIPv4.serialize();
								newPacketIPv4 = (IPv4) packetIPv4.deserialize(newPacketByte, 0, packetIPv4.getTotalLength()); //newPacketByte.length); // packetIPv4.getTotalLength());
								newChecksum = newPacketIPv4.getChecksum();

								packetIPv4.setChecksum(newChecksum);

								etherPacket.setPayload(packetIPv4);
								etherPacket.setSourceMACAddress(routeFound.getInterface().getMacAddress().toBytes()); 
								System.out.println(routeFound.getInterface().getMacAddress().toBytes());
								if (routeFound.getGatewayAddress() == 0){
									etherPacket.setDestinationMACAddress( arpCache.lookup(packetIPv4.getDestinationAddress()).getMac().toBytes()); 
								} else {
									etherPacket.setDestinationMACAddress(arpCache.lookup(routeFound.getGatewayAddress()).getMac().toBytes()); 
								}

								System.out.println(routeFound.toString()); 
								packetIPv4 = (IPv4) etherPacket.getPayload(); 
								this.sendPacket(etherPacket, routeFound.getInterface()); 
								return; 
							}
						}
						catch(Exception e)
						{
							e.printStackTrace();
						}
					}

				} else {
					return; 
				}
			}
		}
	}
}
