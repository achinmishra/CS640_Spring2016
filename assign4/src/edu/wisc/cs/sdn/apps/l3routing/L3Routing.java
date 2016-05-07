package edu.wisc.cs.sdn.apps.l3routing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionApplyActions;

import edu.wisc.cs.sdn.apps.util.Host;
import edu.wisc.cs.sdn.apps.util.SwitchCommands;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.packet.Ethernet;

public class L3Routing implements IFloodlightModule, IOFSwitchListener, 
		ILinkDiscoveryListener, IDeviceListener
{
	public static int INFINITY = 1000;

	public static final String MODULE_NAME = L3Routing.class.getSimpleName();
	
	// Interface to the logging system
    private static Logger log = LoggerFactory.getLogger(MODULE_NAME);
    
    // Interface to Floodlight core for interacting with connected switches
    private IFloodlightProviderService floodlightProv;

    // Interface to link discovery service
    private ILinkDiscoveryService linkDiscProv;

    // Interface to device manager service
    private IDeviceService deviceProv;
    
    // Switch table in which rules should be installed
    public static byte table;
    
    // Map of hosts to devices
    private Map<IDevice,Host> knownHosts;

	/**
     * Loads dependencies and initializes data structures.
     */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException 
	{
		log.info(String.format("Initializing %s...", MODULE_NAME));
		Map<String,String> config = context.getConfigParams(this);
        table = Byte.parseByte(config.get("table"));
        
		this.floodlightProv = context.getServiceImpl(
				IFloodlightProviderService.class);
        this.linkDiscProv = context.getServiceImpl(ILinkDiscoveryService.class);
        this.deviceProv = context.getServiceImpl(IDeviceService.class);
        
        this.knownHosts = new ConcurrentHashMap<IDevice,Host>();
	}

	/**
     * Subscribes to events and performs other startup tasks.
     */
	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException 
	{
		log.info(String.format("Starting %s...", MODULE_NAME));
		this.floodlightProv.addOFSwitchListener(this);
		this.linkDiscProv.addListener(this);
		this.deviceProv.addListener(this);
		
		/*********************************************************************/
		/* TODO: Initialize variables or perform startup tasks, if necessary */
		
		/*********************************************************************/
	}
	
    /**
     * Get a list of all known hosts in the network.
     */
    private Collection<Host> getHosts()
    { return this.knownHosts.values(); }
	
    /**
     * Get a map of all active switches in the network. Switch DPID is used as
     * the key.
     */
	private Map<Long, IOFSwitch> getSwitches()
    { return floodlightProv.getAllSwitchMap(); }
	
    /**
     * Get a list of all active links in the network.
     */
    private Collection<Link> getLinks()
    { return linkDiscProv.getLinks().keySet(); }

    /**
     * Event handler called when a host joins the network.
     * @param device information about the host
     */
	@Override
	public void deviceAdded(IDevice device) 
	{
		Host host = new Host(device, this.floodlightProv);
		// We only care about a new host if we know its IP
		if (host.getIPv4Address() != null)
		{
			log.info(String.format("Host %s added", host.getName()));
			this.knownHosts.put(device, host);
			
			/*****************************************************************/
			/* TODO: Update routing: add rules to route to new host          */
			installRules(host);			
			/*****************************************************************/
		}
	}

	/**
     * Event handler called when a host is no longer attached to a switch.
     * @param device information about the host
     */
	@Override
	public void deviceRemoved(IDevice device) 
	{
		Host host = this.knownHosts.get(device);
		if (null == host)
		{ return; }
		this.knownHosts.remove(device);
		
		log.info(String.format("Host %s is no longer attached to a switch", 
				host.getName()));
		
		/*********************************************************************/
		/* TODO: Update routing: remove rules to route to host               */
		OFMatch match = new OFMatch();
        	match.setDataLayerType(Ethernet.TYPE_IPv4);
	        match.setNetworkDestination(host.getIPv4Address());
        	for (IOFSwitch sw  : this.getSwitches().values())
		{
                	SwitchCommands.removeRules(sw, this.table, match);
                }

		
		/*********************************************************************/
	}

	/**
     * Event handler called when a host moves within the network.
     * @param device information about the host
     */
	@Override
	public void deviceMoved(IDevice device) 
	{
		Host host = this.knownHosts.get(device);
		if (null == host)
		{
			host = new Host(device, this.floodlightProv);
			this.knownHosts.put(device, host);
		}
		
		if (!host.isAttachedToSwitch())
		{
			this.deviceRemoved(device);
			return;
		}
		log.info(String.format("Host %s moved to s%d:%d", host.getName(),
				host.getSwitch().getId(), host.getPort()));
		
		/*********************************************************************/
		/* TODO: Update routing: change rules to route to host               */
		installRules();	
		/*********************************************************************/
	}
	
    /**
     * Event handler called when a switch joins the network.
     * @param DPID for the switch
     */
	@Override		
	public void switchAdded(long switchId) 
	{
		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
		log.info(String.format("Switch s%d added", switchId));
		
		/*********************************************************************/
		/* TODO: Update routing: change routing rules for all hosts          */
		installRules();	
		/*********************************************************************/
	}

	/**
	 * Event handler called when a switch leaves the network.
	 * @param DPID for the switch
	 */
	@Override
	public void switchRemoved(long switchId) 
	{
		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
		log.info(String.format("Switch s%d removed", switchId));
		
		/*********************************************************************/
		/* TODO: Update routing: change routing rules for all hosts          */
		installRules();	
		/*********************************************************************/
	}

	/**
	 * Event handler called when multiple links go up or down.
	 * @param updateList information about the change in each link's state
	 */
	@Override
	public void linkDiscoveryUpdate(List<LDUpdate> updateList) 
	{
		for (LDUpdate update : updateList)
		{
			// If we only know the switch & port for one end of the link, then
			// the link must be from a switch to a host
			if (0 == update.getDst())
			{
				log.info(String.format("Link s%s:%d -> host updated", 
					update.getSrc(), update.getSrcPort()));
			}
			// Otherwise, the link is between two switches
			else
			{
				log.info(String.format("Link s%s:%d -> s%s:%d updated", 
					update.getSrc(), update.getSrcPort(),
					update.getDst(), update.getDstPort()));
			}
		}
		
		/*********************************************************************/
		/* TODO: Update routing: change routing rules for all hosts          */
		installRules();
		/*********************************************************************/
	}

	/**
	 * Event handler called when link goes up or down.
	 * @param update information about the change in link state
	 */
	@Override
	public void linkDiscoveryUpdate(LDUpdate update) 
	{ this.linkDiscoveryUpdate(Arrays.asList(update)); }
	
	/**
     * Event handler called when the IP address of a host changes.
     * @param device information about the host
     */
	@Override
	public void deviceIPV4AddrChanged(IDevice device) 
	{ this.deviceAdded(device); }

	/**
     * Event handler called when the VLAN of a host changes.
     * @param device information about the host
     */
	@Override
	public void deviceVlanChanged(IDevice device) 
	{ /* Nothing we need to do, since we're not using VLANs */ }
	
	/**
	 * Event handler called when the controller becomes the master for a switch.
	 * @param DPID for the switch
	 */
	@Override
	public void switchActivated(long switchId) 
	{ /* Nothing we need to do, since we're not switching controller roles */ }

	/**
	 * Event handler called when some attribute of a switch changes.
	 * @param DPID for the switch
	 */
	@Override
	public void switchChanged(long switchId) 
	{ /* Nothing we need to do */ }
	
	/**
	 * Event handler called when a port on a switch goes up or down, or is
	 * added or removed.
	 * @param DPID for the switch
	 * @param port the port on the switch whose status changed
	 * @param type the type of status change (up, down, add, remove)
	 */
	@Override
	public void switchPortChanged(long switchId, ImmutablePort port,
			PortChangeType type) 
	{ /* Nothing we need to do, since we'll get a linkDiscoveryUpdate event */ }

	/**
	 * Gets a name for this module.
	 * @return name for this module
	 */
	@Override
	public String getName() 
	{ return this.MODULE_NAME; }

	/**
	 * Check if events must be passed to another module before this module is
	 * notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPrereq(String type, String name) 
	{ return false; }

	/**
	 * Check if events must be passed to another module after this module has
	 * been notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPostreq(String type, String name) 
	{ return false; }
	
    /**
     * Tell the module system which services we provide.
     */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() 
	{ return null; }

	/**
     * Tell the module system which services we implement.
     */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> 
			getServiceImpls() 
	{ return null; }

	/**
     * Tell the module system which modules we depend on.
     */
	@Override
	public Collection<Class<? extends IFloodlightService>> 
			getModuleDependencies() 
	{
		Collection<Class<? extends IFloodlightService >> floodlightService =
	            new ArrayList<Class<? extends IFloodlightService>>();
        floodlightService.add(IFloodlightProviderService.class);
        floodlightService.add(ILinkDiscoveryService.class);
        floodlightService.add(IDeviceService.class);
        return floodlightService;
	}
	
	public Map<Long, Long> buildRoute(IOFSwitch dstSwitch)
	{
		int size = this.getSwitches().size();
		//log.info("number of switches = " + size);
		Collection<Link> localLinks = this.getLinks();
		Map<Long, Integer> weight = new HashMap<Long, Integer>();
		Map<Long, Long> predecessor = new HashMap<Long, Long>();;

		log.info(" ************** Destination switch: " + dstSwitch  + " Switch ID: " + dstSwitch.getId() );

		//Step 1: initialize graph
		/*  for each vertex v in vertices:
		    if v is source then weight[v] := 0
		    else weight[v] := infinity
		    predecessor[v] := null
		 */
		//Destination host being the source, links are bidirectional
		for (IOFSwitch vertex : this.getSwitches().values()) {
			if(vertex.getId() == dstSwitch.getId()){
				weight.put(vertex.getId(), 0);
			}
			else
				weight.put( vertex.getId() , INFINITY);
			predecessor.put(vertex.getId() , (long) -1);
		}

		//log.info("************** Predecessor List: "+ predecessor.entrySet());

		// Step 2: relax edges repeatedly
		/*  for i from 1 to size(vertices)-1:
		    for each edge (u, v) with weight w in edges:
		    if weight[u] + w < weight[v]:
		    weight[v] := weight[u] + w
		    predecessor[v] := u
		 */
		//log.info("# of Links: " + this.getLinks().size());
		//for (Link topoLink : this.getLinks())
		//log.info(" ************** Link between: " + topoLink.getSrc() + " and " + topoLink.getDst());


		for(int i = 1; i < size; i++){
			for (Link topoLink : localLinks) {
				//log.info(" ************** Link between: " + topoLink.getSrc() + " and " + topoLink.getDst());
				if( ( weight.get(topoLink.getSrc()) + 1 ) < weight.get(topoLink.getDst()) ){
					weight.put(topoLink.getDst() , weight.get(topoLink.getSrc()) + 1 );
					predecessor.put(topoLink.getDst() , topoLink.getSrc());
				}
			}
		}

		log.info("************** Predecessor List:: " + predecessor.entrySet());

		// Step 3: check for negative-weight cycles
		/*   for each edge (u, v) with weight w in edges:
		     if weight[u] + w < weight[v]:
		     error "Graph contains a negative-weight cycle"
		     return weight[], predecessor[]
		 */
		for (Link topoLink : localLinks) {
			if( (weight.get(topoLink.getSrc()) + 1 ) < weight.get(topoLink.getDst()) ){
				log.error("Negative cycle detected");
			}
		}
		return predecessor;
	}


	public void installRules(Host host)
	{
		if(host.getSwitch() ==  null)
			return;
		//Build Route
		Map<Long, IOFSwitch> topoSwitches = this.getSwitches();
		//IOFSwitch sw = null;
		Map<Long, Long> predList = buildRoute(host.getSwitch());
		//log.info("********** -> Topology switches:: " + topoSwitches.entrySet());
		log.info("********** -> Route path for " + host.getSwitch().getId() + "*******" + predList.entrySet() );
		//for (Long s : predList.keySet()) {
		//log.info("*******Mapping of switch " +  s + " : " + predList.get(s));
		//}
		for (IOFSwitch sw : topoSwitches.values()){
			installRules(sw, host, predList.get(sw.getId()));
		}
	}

	public void installRules(IOFSwitch sw, Host hst, long dstsw)
	{
		Collection<Link> localLinks = this.getLinks();

		if(sw == null)
			return;
		int ipAddr = -1, port = -1;
		if(dstsw == -1)
		{       
			ipAddr = hst.getIPv4Address();
			port = hst.getPort();
		}
		else
		{
			for (Link lnk : localLinks) {
				if (lnk.getSrc() == sw.getId() && lnk.getDst() == dstsw) {
					ipAddr = hst.getIPv4Address();
					port = lnk.getSrcPort();
				}
			}
		}
		if (ipAddr == -1 || port == -1)
			return;

		//log.info("********** -> Topology links:: " + localLinks.toString()); 

		OFMatch match = new OFMatch();
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkDestination(ipAddr);

		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput actionOutput = new OFActionOutput();
		actionOutput.setPort(port);
		actions.add(actionOutput);

		OFInstructionApplyActions applyActions = new OFInstructionApplyActions(actions);
		List<OFInstruction> instr = new ArrayList<OFInstruction>();
		instr.add(applyActions);

		//log.info("********** -> Instructions:: " + instr + " switch: "+ sw); 
		SwitchCommands.removeRules(sw, this.table , match);
		SwitchCommands.installRule(sw, this.table , SwitchCommands.DEFAULT_PRIORITY , match, instr);
	}
	
	public void installRules()
	{
		Map<Long, IOFSwitch> topoSwitches = this.getSwitches();

		for (IOFSwitch sw : topoSwitches.values()) {
			Map<Long, Long> predList = buildRoute(sw);
			log.info("Route path with source" + sw.getId() + ":: " + predList.entrySet());

			for (IOFSwitch tempsw : topoSwitches.values()) {
				if(this.getHosts() != null){
					for (Host hst : this.getHosts()) {
						if (hst.getSwitch() != null && hst.getSwitch().getId() == sw.getId()) {
							log.info("******** ->" + hst.getSwitch().getId(), sw.getId());
							installRules(tempsw, hst , predList.get(tempsw.getId()));
						}
					}
				}
			}
		}
	}
}
