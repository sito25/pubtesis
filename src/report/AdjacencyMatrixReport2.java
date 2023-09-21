/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package report;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import core.ConnectionListener;
import core.DTNHost;
import core.SimScenario;
import core.World;

/**
 * Generates Graphviz compatible graph from connections.
 * Connections that happen during the warm up period are ignored.
 */
public class AdjacencyMatrixReport2 extends Report implements ConnectionListener {	
	private String HOST_DELIM = "-"; // used in toString()
	private ConcurrentHashMap<String, Integer> cons;

	/**
	 * Constructor.
	 */
	public AdjacencyMatrixReport2() {
		init();
	}

	protected void init() {
		super.init();
		this.cons = new ConcurrentHashMap<String, Integer>();
	}


	public void hostsConnected(DTNHost host1, DTNHost host2) {
		if (isWarmup()) {
			return;
		}

		newEvent();
	
		String anotherHostName = host2.getName();
			
		if (host1.isFriend(anotherHostName)) {
			if (!host1.friendHasExpiredContactTimeout(anotherHostName)) {
				this.setNewConnection(host1, host2);
				this.setNewConnection(host2, host1);
			}
		}
	}

	private void setNewConnection(DTNHost host1, DTNHost host2) {
		int numberOfConectivityValues = host1.getNumberOfConectivityValues();
	
		String key = numberOfConectivityValues + HOST_DELIM + host1 + HOST_DELIM + host2;
		Boolean connectionExists = cons.containsKey(key);
	
		if (!connectionExists) {
			cons.put(key, 0);
		}
		
		int currentConNumber = cons.get(key);
		cons.put(key, currentConNumber + 1);
	}
	
	// 	Nothing to do here..
	public void hostsDisconnected(DTNHost host1, DTNHost host2) {}

	/**
	 * Sets all hosts that should be in the graph at least once
	 * @param hosts Collection of hosts
	 */
	public void setAllHosts(Collection<DTNHost> hosts) {
	}

	public void done() {
		int totalNumberOfConectivityValues=0;
		World world = SimScenario.getInstance().getWorld();
		List<DTNHost> hosts = world.getHosts();
		Iterator<DTNHost> it;
		it = hosts.iterator();
		
		if (it.hasNext()){
			DTNHost host = it.next();
			totalNumberOfConectivityValues = host.getNumberOfConectivityValues();
		}
		else super.done();
			
		for (int i=0; i<totalNumberOfConectivityValues; i++) {
			this.plotAdjacencyMatrix(i);
		}

		super.done();
	}
	
	private void plotAdjacencyMatrix (int n) {
		World world = SimScenario.getInstance().getWorld();
		List<DTNHost> hosts = world.getHosts();
		Iterator<DTNHost> it, it2;
		it = hosts.iterator();
		
		write_without_newline(" ");

		while (it.hasNext()){
			DTNHost host = it.next();
			//write_without_newline(host.getName() + " ");
		}
		write("");
		
		it = hosts.iterator();
		
		while (it.hasNext()){
			DTNHost host = it.next();
			String lHost = host.getName();
						
		//write_without_newline(lHost + " ");
			
			it2 = hosts.iterator();
			
			while (it2.hasNext()){
				DTNHost host2 = it2.next();
				String rHost = host2.getName();
				
				String key = n + HOST_DELIM + lHost + HOST_DELIM + rHost;
				Boolean connectionExists = cons.containsKey(key);

				if (connectionExists) write_without_newline(cons.get(key) + " ");
				else write_without_newline(0 + " ");
			}
			
			write("");
		}
		
		write("");
		write("");
		write("");
	}
}
