package edu.wisc.cs.sdn.vnet.sw;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

//Added/Edited by us
import java.util.*;
import net.floodlightcontroller.packet.*;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{	
	class VirtualSwitch {
                Iface intface;
                long time;

                VirtualSwitch(Iface intface, long time) {
                        this.intface = intface;
                        this.time = time;
                }
        }

        Map<MACAddress, VirtualSwitch> forwardingTable = new HashMap<>();

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
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
		long timeSpent = 0;
		VirtualSwitch learnEntry = null;
		MACAddress sourceMAC = etherPacket.getSourceMAC();
		MACAddress destMAC = etherPacket.getDestinationMAC();
		try{
			learnEntry = new VirtualSwitch(inIface, System.currentTimeMillis());
		} catch(Exception e)
		{
			System.err.println("Null Pointer Exception!!!!");
			e.printStackTrace();
		}

		forwardingTable.put(sourceMAC, learnEntry); //add the entry in the hashmap here
		VirtualSwitch getEntry = forwardingTable.get(destMAC);

		if(getEntry != null)
			timeSpent = System.currentTimeMillis() - getEntry.time;
		
		if(getEntry == null || timeSpent > 15000)
		{
			List<Iface> list = new ArrayList<Iface>(interfaces.values());
			for(int i = 0; i < list.size(); i++)
			{
				Iface temp = list.get(i);
				sendPacket(etherPacket, temp); //broadcast to all the entries in the hashmap
			}	
		}
		else
		{
			sendPacket(etherPacket, getEntry.intface);
		}

	}
}
