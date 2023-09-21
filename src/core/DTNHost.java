/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import movement.MovementModel;
import movement.Path;
import routing.MessageRouter;
import routing.util.RoutingInfo;
import java.text.DecimalFormat;
import static core.Constants.DEBUG;

/**
 * A DTN capable host.
 */
public class DTNHost implements Comparable<DTNHost> {
	public static final String SPRAYANDWAIT_NS = "SprayAndWaitRouter";
	public static final String NROF_COPIES = "nrofCopies";

	public static double CONTACTS_TIMEOUT_DURATION = 1500;
	public static double CONECTIVITY_TIMEOUT_DURATION = 20000;

	private static int nextAddress = 0;
	private int address;

	private Coord location; 	// where is the host
	private Coord destination;	// where is it going

	private MessageRouter router;
	private MovementModel movement;
	private Path path;
	private double speed;
	private double nextTimeToMove;
	private String name;
	private List<MessageListener> msgListeners;
	private List<MovementListener> movListeners;
	private List<NetworkInterface> net;
	private ModuleCommunicationBus comBus;
	
	private ConcurrentHashMap<String, Friend> friends;
	private int conectivity;
	private int oldConectivity;
	private double conectivityTimeout;
	private int minConectivity;
	private int maxConectivity;
	private boolean haveMinConectivity, haveMaxConectivity;
	private int numberOfConectivityValues;

	static {
		DTNSim.registerForReset(DTNHost.class.getCanonicalName());
		reset();
	}
	/**
	 * Creates a new DTNHost.
	 * @param msgLs Message listeners
	 * @param movLs Movement listeners
	 * @param groupId GroupID of this host
	 * @param interf List of NetworkInterfaces for the class
	 * @param comBus Module communication bus object
	 * @param mmProto Prototype of the movement model of this host
	 * @param mRouterProto Prototype of the message router of this host
	 */
	public DTNHost(List<MessageListener> msgLs,
			List<MovementListener> movLs,
			String groupId, List<NetworkInterface> interf,
			ModuleCommunicationBus comBus,
			MovementModel mmProto, MessageRouter mRouterProto) {
		this.comBus = comBus;
		this.location = new Coord(0,0);
		this.address = getNextAddress();
		this.name = groupId+address;
		this.net = new ArrayList<NetworkInterface>();

		for (NetworkInterface i : interf) {
			NetworkInterface ni = i.replicate();
			ni.setHost(this);
			net.add(ni);
		}

		// TODO - think about the names of the interfaces and the nodes
		//this.name = groupId + ((NetworkInterface)net.get(1)).getAddress();

		this.msgListeners = msgLs;
		this.movListeners = movLs;

		// create instances by replicating the prototypes
		this.movement = mmProto.replicate();
		this.movement.setComBus(comBus);
		this.movement.setHost(this);
		setRouter(mRouterProto.replicate());

		this.location = movement.getInitialLocation();

		this.nextTimeToMove = movement.nextPathAvailable();
		this.path = null;

		if (movLs != null) { // inform movement listeners about the location
			for (MovementListener l : movLs) {
				l.initialLocation(this, this.location);
			}
		}
				
		this.friends = new ConcurrentHashMap<String, Friend>();
		this.conectivity = 0;
		this.oldConectivity = 0;
		this.minConectivity = 0;
		this.maxConectivity = 0;
		this.haveMinConectivity = false;
		this.haveMinConectivity = false;
		this.numberOfConectivityValues = 0;
		this.setConectivityTimeout(SimClock.getTime());
	}
	
	public void new_contact_between_nodes(DTNHost anotherHost){
		String anotherHostName = anotherHost.getName(); 

		if (isFriend(anotherHostName)){
			if (!friendHasExpiredContactTimeout(anotherHostName)){
				updateFriendsTimesRenewed(anotherHostName);
			}
			updateContactTimeout(anotherHostName);
		}
		else{
			addFriend(anotherHostName);
			updateFriendsTimesRenewed(anotherHostName);
		}
		this.updateTwoFriendsTemporalLocality(anotherHostName);
	}
	
	public double getConectivityTimeout (){
		return SimClock.getTime() - this.conectivityTimeout;
	}
	
	public int getConectivity () {
		return this.oldConectivity;
	}
	
	public void setConectivityTimeout (double conectivityTimeout){
		this.conectivityTimeout = conectivityTimeout;
	}
	
	public void incrementConectivity () {
		this.conectivity+=1;
	}
	
	public boolean conectivityTimeoutHasExpired(){
		double conectivityProgress = this.getConectivityTimeout();
		return (conectivityProgress > 0) && (conectivityProgress >= CONECTIVITY_TIMEOUT_DURATION);
	}
	
	public void renewConectivityValue() {
		this.oldConectivity = this.conectivity;
		this.conectivity = 0;
		this.numberOfConectivityValues += 1;

		if (!this.haveMinConectivity || this.oldConectivity < this.minConectivity) {
			this.minConectivity = this.oldConectivity;
			this.haveMinConectivity = true;
		}
		
		if (!this.haveMaxConectivity || this.oldConectivity > this.maxConectivity) {
			this.maxConectivity = this.oldConectivity;
			this.haveMaxConectivity = true;
		}
		
		this.setConectivityTimeout(SimClock.getTime());
	}
	
	public int getMinConectivity (){
		return this.minConectivity;
	}
	
	public int getMaxConectivity (){
		return this.maxConectivity;
	}
	
	public int getNumberOfConectivityValues () {
		return this.numberOfConectivityValues;
	}
	
	void updateFriendsTimesRenewed (String updatedFriend) {
		Friend anotherHostFriend = friends.get(updatedFriend);
		anotherHostFriend.setTimesRenewed(anotherHostFriend.getTimesRenewed() + 1);
		//friends.remove(updatedFriend);
		friends.replace(updatedFriend, anotherHostFriend);
	}
	
	void updateContactTimeout (String updatedFriend) {
		Friend anotherHostFriend = friends.get(updatedFriend);
		//anotherHostFriend = updateContactTimeout(anotherHostFriend);
		anotherHostFriend.setContactTimeout(SimClock.getTime());
		//friends.remove(updatedFriend);
		friends.replace(updatedFriend, anotherHostFriend);
	}
	
	public void addFriend (String friendName){
		Friend friend = this.createNewFriend(friendName);
		friends.put(friendName, friend);
		this.incrementConectivity();
	}
	
	public Friend createNewFriend (String friendName){
		int initialNumberOfCopies = this.getInitialNumberOfCopies();
		return new Friend(initialNumberOfCopies);
	}
	
	public int getInitialNumberOfCopies () {
		Settings snwSettings = new Settings(SPRAYANDWAIT_NS);
		
		return snwSettings.getInt(NROF_COPIES);
	}
	
	public boolean isFriend (String friendName){
		return friends.containsKey(friendName);
	}
	
	public Friend getFriend (String friendName){
		if (friends.containsKey(friendName)){
			return friends.get(friendName);
		}
		return null;
	}
	
	public void removeFriend(String friendName){
		if (friends.containsKey(friendName)){
			friends.remove(friendName);
		}
	}
	
	public double getFriendContactProgress(String friendName){
		if (isFriend(friendName)){
			Friend friend = this.getFriend(friendName);
			if (friend != null){
				return SimClock.getTime() - friend.getContactTimeout();
			}
			return -1;
		}
		return -1;
	}
	
	/*public Friend addNewTimeRenewed(Friend friend){
		Friend newFriend = friend;
		newFriend.setTimesRenewed(newFriend.getTimesRenewed() + 1);
		return newFriend;
	}*/
	
	public boolean friendHasExpiredContactTimeout(String friendName){
		double friendContactProgress = this.getFriendContactProgress(friendName);
		return (friendContactProgress > 0) && (friendContactProgress >= CONTACTS_TIMEOUT_DURATION);
	}
	
	/*public Friend updateContactTimeout(Friend friend){
		Friend newFriend = friend;
		newFriend.setContactTimeout(SimClock.getTime());
		return newFriend;
	}*/
	
	public Set<String> getFriends(){
		return friends.keySet();
	}
		
	public void showListOfFriends(){
	    DecimalFormat df = new DecimalFormat("0.00");
		if (hasFriends()){
			System.out.println("The list of friends of " + this.name + " is (" + friends.size() + " friend/s): ");
			Set<String> keySet = friends.keySet();
			Iterator<String> i = keySet.iterator();
			while (i.hasNext()) {
				String friendName = i.next();
				Friend friend = friends.get(friendName);
				System.out.print(friendName + ": " + df.format(getFriendContactProgress(friendName)) + ", " + friend.getTimesRenewed() + ", " + friend.getTemporalLocality() + " --- ");
		    }
			System.out.println();
		}
		else{
			System.out.println("The host " + this.name + " does not have any friends.");
		}
	}
	
	public boolean hasFriends(){
		return friends.size() > 0;
	}
	
	public void updateTwoFriendsTemporalLocality(String to){
		//synchronized(friends){
			//if (hasFriends()){
			//	Iterator<String> i = friends.keySet().iterator();
			//	while (i.hasNext()) {
			//		String friendName = i.next();
			//		if (friendName.compareTo(to) == 0) {
			//			System.out.println(to + ", " + friendName);
		if (friends.get(to) != null) {
			//updateTemporalLocalityFriend(this.getName(), to);
			
			Friend anotherHostFriend = friends.get(to);
			anotherHostFriend.updateTemporalLocality();
			friends.replace(to, anotherHostFriend);
		}
			//		}
			//	}
			//}
		//}
	}
	
	public int getAllowedCopiesToFriend(String to){
		//synchronized(friends){
			//if (hasFriends()){
			//	Iterator<String> i = friends.keySet().iterator();
				//while (i.hasNext()) {
		if (friends.get(to) != null) {
			return (this.friends.get(to)).getNumberOfCopiesAllowed();
		}
					//String friendName = i.next();
					//if (friendName.compareTo(to) == 0) {
					//	return (this.friends.get(friendName)).getNumberOfCopiesAllowed();
					//}
				//}
			//}
		//}
		return this.getInitialNumberOfCopies();
	}
	
	/*public double getTotalTemporalLocality(){
		double totalTemporalLocality = 0.0;
		//synchronized(friends){
			if (hasFriends()){
				Iterator<String> i = friends.keySet().iterator();
				while (i.hasNext()) {
					String friendName = i.next();
					Friend friend = friends.get(friendName);
					double friendTemporalLocality = friend.getTemporalLocality();
					totalTemporalLocality = totalTemporalLocality + friendTemporalLocality;
			    }
				totalTemporalLocality = totalTemporalLocality / friends.size();
			}
		//}
		
		return totalTemporalLocality;
	}*/
	
	/**
	 * Returns a new network interface address and increments the address for
	 * subsequent calls.
	 * @return The next address.
	 */
	private synchronized static int getNextAddress() {
		return nextAddress++;
	}

	/**
	 * Reset the host and its interfaces
	 */
	public static void reset() {
		nextAddress = 0;
	}

	/**
	 * Returns true if this node is actively moving (false if not)
	 * @return true if this node is actively moving (false if not)
	 */
	public boolean isMovementActive() {
		return this.movement.isActive();
	}

	/**
	 * Returns true if this node's radio is active (false if not)
	 * @return true if this node's radio is active (false if not)
	 */
	public boolean isRadioActive() {
		// Radio is active if any of the network interfaces are active.
		for (final NetworkInterface i : this.net) {
			if (i.isActive()) return true;
		}
		return false;
	}

	/**
	 * Set a router for this host
	 * @param router The router to set
	 */
	private void setRouter(MessageRouter router) {
		router.init(this, msgListeners);
		this.router = router;
	}

	/**
	 * Returns the router of this host
	 * @return the router of this host
	 */
	public MessageRouter getRouter() {
		return this.router;
	}

	/**
	 * Returns the network-layer address of this host.
	 */
	public int getAddress() {
		return this.address;
	}

	/**
	 * Returns this hosts's ModuleCommunicationBus
	 * @return this hosts's ModuleCommunicationBus
	 */
	public ModuleCommunicationBus getComBus() {
		return this.comBus;
	}

    /**
	 * Informs the router of this host about state change in a connection
	 * object.
	 * @param con  The connection object whose state changed
	 */
	public void connectionUp(Connection con) {
		this.router.changedConnection(con);
	}

	public void connectionDown(Connection con) {
		this.router.changedConnection(con);
	}

	/**
	 * Returns a copy of the list of connections this host has with other hosts
	 * @return a copy of the list of connections this host has with other hosts
	 */
	public List<Connection> getConnections() {
		List<Connection> lc = new ArrayList<Connection>();

		for (NetworkInterface i : net) {
			lc.addAll(i.getConnections());
		}

		return lc;
	}

	/**
	 * Returns the current location of this host.
	 * @return The location
	 */
	public Coord getLocation() {
		return this.location;
	}

	/**
	 * Returns the Path this node is currently traveling or null if no
	 * path is in use at the moment.
	 * @return The path this node is traveling
	 */
	public Path getPath() {
		return this.path;
	}


	/**
	 * Sets the Node's location overriding any location set by movement model
	 * @param location The location to set
	 */
	public void setLocation(Coord location) {
		this.location = location.clone();
	}

	/**
	 * Sets the Node's name overriding the default name (groupId + netAddress)
	 * @param name The name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the messages in a collection.
	 * @return Messages in a collection
	 */
	public Collection<Message> getMessageCollection() {
		return this.router.getMessageCollection();
	}

	/**
	 * Returns the number of messages this node is carrying.
	 * @return How many messages the node is carrying currently.
	 */
	public int getNrofMessages() {
		return this.router.getNrofMessages();
	}

	/**
	 * Returns the buffer occupancy percentage. Occupancy is 0 for empty
	 * buffer but can be over 100 if a created message is bigger than buffer
	 * space that could be freed.
	 * @return Buffer occupancy percentage
	 */
	public double getBufferOccupancy() {
		long bSize = router.getBufferSize();
		long freeBuffer = router.getFreeBufferSize();
		return 100*((bSize-freeBuffer)/(bSize * 1.0));
	}

	/**
	 * Returns routing info of this host's router.
	 * @return The routing info.
	 */
	public RoutingInfo getRoutingInfo() {
		return this.router.getRoutingInfo();
	}

	/**
	 * Returns the interface objects of the node
	 */
	public List<NetworkInterface> getInterfaces() {
		return net;
	}

	/**
	 * Find the network interface based on the index
	 */
	public NetworkInterface getInterface(int interfaceNo) {
		NetworkInterface ni = null;
		try {
			ni = net.get(interfaceNo-1);
		} catch (IndexOutOfBoundsException ex) {
			throw new SimError("No such interface: "+interfaceNo +
					" at " + this);
		}
		return ni;
	}

	/**
	 * Find the network interface based on the interfacetype
	 */
	protected NetworkInterface getInterface(String interfacetype) {
		for (NetworkInterface ni : net) {
			if (ni.getInterfaceType().equals(interfacetype)) {
				return ni;
			}
		}
		return null;
	}

	/**
	 * Force a connection event
	 */
	public void forceConnection(DTNHost anotherHost, String interfaceId,
			boolean up) {
		NetworkInterface ni;
		NetworkInterface no;

		if (interfaceId != null) {
			ni = getInterface(interfaceId);
			no = anotherHost.getInterface(interfaceId);

			assert (ni != null) : "Tried to use a nonexisting interfacetype "+interfaceId;
			assert (no != null) : "Tried to use a nonexisting interfacetype "+interfaceId;
		} else {
			ni = getInterface(1);
			no = anotherHost.getInterface(1);

			assert (ni.getInterfaceType().equals(no.getInterfaceType())) :
				"Interface types do not match.  Please specify interface type explicitly";
		}

		if (up) {
			ni.createConnection(no);
		} else {
			ni.destroyConnection(no);
		}
	}

	/**
	 * for tests only --- do not use!!!
	 */
	public void connect(DTNHost h) {
		if (DEBUG) Debug.p("WARNING: using deprecated DTNHost.connect" +
			"(DTNHost) Use DTNHost.forceConnection(DTNHost,null,true) instead");
		forceConnection(h,null,true);
	}

	/**
	 * Updates node's network layer and router.
	 * @param simulateConnections Should network layer be updated too
	 */
	public void update(boolean simulateConnections) {
		if (this.conectivityTimeoutHasExpired()) {
			this.renewConectivityValue();
			//System.out.println(SimClock.getTime() + ", " +  getName() + ", " + getConectivity());
		}
		
		if (!isRadioActive()) {
			// Make sure inactive nodes don't have connections
			tearDownAllConnections();
			return;
		}

		if (simulateConnections) {
			for (NetworkInterface i : net) {
				i.update();
			}
		}
		this.router.update();
	}

	/**
	 * Tears down all connections for this host.
	 */
	private void tearDownAllConnections() {
		for (NetworkInterface i : net) {
			// Get all connections for the interface
			List<Connection> conns = i.getConnections();
			if (conns.size() == 0) continue;

			// Destroy all connections
			List<NetworkInterface> removeList =
				new ArrayList<NetworkInterface>(conns.size());
			for (Connection con : conns) {
				removeList.add(con.getOtherInterface(i));
			}
			for (NetworkInterface inf : removeList) {
				i.destroyConnection(inf);
			}
		}
	}

	/**
	 * Moves the node towards the next waypoint or waits if it is
	 * not time to move yet
	 * @param timeIncrement How long time the node moves
	 */
	public void move(double timeIncrement) {
		double possibleMovement;
		double distance;
		double dx, dy;

		if (!isMovementActive() || SimClock.getTime() < this.nextTimeToMove) {
			return;
		}
		if (this.destination == null) {
			if (!setNextWaypoint()) {
				return;
			}
		}

		possibleMovement = timeIncrement * speed;
		distance = this.location.distance(this.destination);

		while (possibleMovement >= distance) {
			// node can move past its next destination
			this.location.setLocation(this.destination); // snap to destination
			possibleMovement -= distance;
			if (!setNextWaypoint()) { // get a new waypoint
				this.destination = null; // No more waypoints left, therefore the destination must be null
				return; // no more waypoints left
			}
			distance = this.location.distance(this.destination);
		}

		// move towards the point for possibleMovement amount
		dx = (possibleMovement/distance) * (this.destination.getX() -
				this.location.getX());
		dy = (possibleMovement/distance) * (this.destination.getY() -
				this.location.getY());
		this.location.translate(dx, dy);
	}

	/**
	 * Sets the next destination and speed to correspond the next waypoint
	 * on the path.
	 * @return True if there was a next waypoint to set, false if node still
	 * should wait
	 */
	private boolean setNextWaypoint() {
		if (path == null) {
			path = movement.getPath();
		}

		if (path == null || !path.hasNext()) {
			this.nextTimeToMove = movement.nextPathAvailable();
			this.path = null;
			return false;
		}

		this.destination = path.getNextWaypoint();
		this.speed = path.getSpeed();

		if (this.movListeners != null) {
			for (MovementListener l : this.movListeners) {
				l.newDestination(this, this.destination, this.speed);
			}
		}

		return true;
	}

	/**
	 * Sends a message from this host to another host
	 * @param id Identifier of the message
	 * @param to Host the message should be sent to
	 */
	public void sendMessage(String id, DTNHost to) {
		this.router.sendMessage(id, to);
	}

	/**
	 * Start receiving a message from another host
	 * @param m The message
	 * @param from Who the message is from
	 * @return The value returned by
	 * {@link MessageRouter#receiveMessage(Message, DTNHost)}
	 */
	public int receiveMessage(Message m, DTNHost from) {
		int retVal = this.router.receiveMessage(m, from);

		if (retVal == MessageRouter.RCV_OK) {
			m.addNodeOnPath(this);	// add this node on the messages path
		}

		return retVal;
	}

	/**
	 * Requests for deliverable message from this host to be sent trough a
	 * connection.
	 * @param con The connection to send the messages trough
	 * @return True if this host started a transfer, false if not
	 */
	public boolean requestDeliverableMessages(Connection con) {
		return this.router.requestDeliverableMessages(con);
	}

	/**
	 * Informs the host that a message was successfully transferred.
	 * @param id Identifier of the message
	 * @param from From who the message was from
	 */
	public void messageTransferred(String id, DTNHost from) {
		this.router.messageTransferred(id, from);
	}

	/**
	 * Informs the host that a message transfer was aborted.
	 * @param id Identifier of the message
	 * @param from From who the message was from
	 * @param bytesRemaining Nrof bytes that were left before the transfer
	 * would have been ready; or -1 if the number of bytes is not known
	 */
	public void messageAborted(String id, DTNHost from, int bytesRemaining) {
		this.router.messageAborted(id, from, bytesRemaining);
	}

	/**
	 * Creates a new message to this host's router
	 * @param m The message to create
	 */
	public void createNewMessage(Message m) {
		this.router.createNewMessage(m);
	}

	/**
	 * Deletes a message from this host
	 * @param id Identifier of the message
	 * @param drop True if the message is deleted because of "dropping"
	 * (e.g. buffer is full) or false if it was deleted for some other reason
	 * (e.g. the message got delivered to final destination). This effects the
	 * way the removing is reported to the message listeners.
	 */
	public void deleteMessage(String id, boolean drop) {
		this.router.deleteMessage(id, drop);
	}

	/**
	 * Returns a string presentation of the host.
	 * @return Host's name
	 */
	public String toString() {
		return name;
	}

	/**
	 * Checks if a host is the same as this host by comparing the object
	 * reference
	 * @param otherHost The other host
	 * @return True if the hosts objects are the same object
	 */
	public boolean equals(DTNHost otherHost) {
		return this == otherHost;
	}

	/**
	 * Compares two DTNHosts by their addresses.
	 * @see Comparable#compareTo(Object)
	 */
	public int compareTo(DTNHost h) {
		return this.getAddress() - h.getAddress();
	}
	
	public String getName(){
		return this.name;
	}
	
	public double getLocalityBetweenTwoNodes(String anotherHostName) {
		Friend friend = friends.get(anotherHostName);
		if (friend != null) return friend.getTemporalLocality();
		return 0.0;
	}
}
